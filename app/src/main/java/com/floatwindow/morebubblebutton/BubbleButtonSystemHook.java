package com.floatwindow.morebubblebutton;

import android.app.Notification;
import android.content.Context;
import android.util.Log;
import android.view.View;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class BubbleButtonSystemHook extends XposedModule {
    private static final String TAG = "BubbleButtonSystemHook";
    private ClassLoader mClassLoader;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        Log.i(TAG, "onModuleLoaded: " + param.getProcessName());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!"com.android.systemui".equals(param.getPackageName())) return;
        mClassLoader = param.getDefaultClassLoader();
        Log.i(TAG, "Hooking SystemUI...");
        hookShouldShowBubbleButton();
        hookRankingCanBubble();
        hookBubbleClickListener();
    }

    // ========== 1. shouldShowBubbleButton → 对非常驻通知返回 true ==========

    private void hookShouldShowBubbleButton() {
        try {
            Class<?> clazz = mClassLoader.loadClass(
                    "com.android.systemui.statusbar.notification.row.NotificationContentView");
            hook(clazz.getMethod("shouldShowBubbleButton")).intercept(chain -> {
                try {
                    Context ctx = ((View) chain.getThisObject()).getContext();
                    if (!ModuleSettings.isSystemUiBubbleEnabled(ctx)) return chain.proceed();
                } catch (Throwable ignored) {}

                boolean original = (boolean) chain.proceed();
                if (original) return true;

                try {
                    Object contentView = chain.getThisObject();
                    Object row = getField(contentView, "mContainingNotification");
                    if (row == null) return false;
                    Object adapter = getField(row, "mEntryAdapter");
                    if (adapter == null) return false;
                    Object sbn = invoke(adapter, "getSbn");
                    if (sbn == null) return false;
                    Notification notif = (Notification) invoke(sbn, "getNotification");
                    if (notif == null) return false;

                    boolean isForeground = (notif.flags & 0x40) != 0;
                    if (isForeground) return false;

                    java.lang.reflect.Field be = findField(contentView.getClass(), "mBubblesEnabledForUser");
                    if (be != null) { be.setAccessible(true); if (!be.getBoolean(contentView)) return false; }
                    return true;
                } catch (Throwable t) { return false; }
            });
            Log.i(TAG, "Hooked shouldShowBubbleButton OK");
        } catch (Throwable t) { Log.e(TAG, "Hook shouldShowBubbleButton: " + t.getMessage()); }
    }

    // ========== 2. Ranking.canBubble → 让系统认为支持气泡 ==========

    private void hookRankingCanBubble() {
        try {
            Class<?> cls = mClassLoader.loadClass("android.service.notification.NotificationListenerService$Ranking");
            java.lang.reflect.Method m = findMethod(cls, "canBubble");
            if (m != null) {
                hook(m).intercept(chain -> {
                    if ((boolean) chain.proceed()) return true;
                    try {
                        Object ranking = chain.getThisObject();
                        java.lang.reflect.Field f = findField(ranking.getClass(), "mFlags");
                        if (f != null) { f.setAccessible(true); if ((f.getInt(ranking) & 0x40) != 0) return false; }
                        return true;
                    } catch (Throwable t) { return false; }
                });
                Log.i(TAG, "Hooked Ranking.canBubble OK");
            }
        } catch (Throwable t) { Log.w(TAG, "Hook Ranking.canBubble: " + t.getMessage()); }
    }

    // ========== 3. 设置 mBubbleClickListener ==========

    private void hookBubbleClickListener() {
        try {
            Class<?> binderClass = mClassLoader.loadClass(
                    "com.android.systemui.statusbar.notification.collection.inflation.NotificationRowBinderImpl");

            java.lang.reflect.Method target = null;
            for (java.lang.reflect.Method m : binderClass.getDeclaredMethods()) {
                if (m.getParameterCount() >= 3 && m.getParameterTypes()[2].getName().contains("ExpandableNotificationRow")) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                Log.w(TAG, "bind method not found, listing all:");
                for (java.lang.reflect.Method m : binderClass.getDeclaredMethods())
                    Log.w(TAG, "  " + m.getName() + "(" + java.util.Arrays.toString(m.getParameterTypes()) + ")");
                return;
            }
            Log.i(TAG, "Target: " + target.getName() + " " + java.util.Arrays.toString(target.getParameterTypes()));

            hook(target).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    Object row = chain.getArg(2);
                    if (row == null) return result;

                    java.lang.reflect.Field clickField = findField(row.getClass(), "mBubbleClickListener");
                    if (clickField == null) return result;
                    clickField.setAccessible(true);

                    Object entry = getField(row, "mEntry");
                    if (entry == null) return result;

                    Object sbn = getField(entry, "mSbn");
                    if (sbn == null) return result;
                    Notification notif = (Notification) invoke(sbn, "getNotification");
                    if (notif == null) return result;

                    // 跳过常驻通知
                    if ((notif.flags & 0x40) != 0) return result;

                    // 创建点击监听器
                    final Object finalEntry = entry;
                    final Object finalRow = row;
                    final Notification finalNotif = notif;
                    clickField.set(row, (View.OnClickListener) v -> {
                        try {
                            // 设置 bubble metadata
                            java.lang.reflect.Field metaField = findField(finalEntry.getClass(), "mBubbleMetadata");
                            if (metaField != null) {
                                metaField.setAccessible(true);
                                if (metaField.get(finalEntry) == null && finalNotif.contentIntent != null) {
                                    Notification.BubbleMetadata.Builder builder =
                                            new Notification.BubbleMetadata.Builder();
                                    builder.setIntent(finalNotif.contentIntent);
                                    builder.setDeleteIntent(finalNotif.deleteIntent);
                                    metaField.set(finalEntry, builder.build());
                                    Log.i(TAG, "Set bubble metadata");
                                }
                            }
                            // 调用 onNotificationBubbleIconClicked
                            Object adapter = getField(finalRow, "mEntryAdapter");
                            if (adapter != null) invoke(adapter, "onNotificationBubbleIconClicked");
                        } catch (Throwable t) {
                            Log.e(TAG, "Bubble click error: " + t.getMessage(), t);
                        }
                    });

                    // 更新 UI
                    updateBubbleUI(row, "mPrivateLayout", "mExpandedChild");
                    updateBubbleUI(row, "mPrivateLayout", "mHeadsUpChild");
                    Log.d(TAG, "Set mBubbleClickListener");
                } catch (Throwable t) { Log.w(TAG, "hookBubbleClick: " + t.getMessage()); }
                return result;
            });
            Log.i(TAG, "Hooked NotificationRowBinderImpl OK");
        } catch (Throwable t) { Log.e(TAG, "Hook RowBinder: " + t.getMessage()); }
    }

    private void updateBubbleUI(Object row, String layoutField, String childField) {
        try {
            Object layout = getField(row, layoutField);
            if (layout == null) return;
            java.lang.reflect.Method ab = findMethod(layout.getClass(), "applyBubbleAction", View.class);
            if (ab == null) return;
            Object child = getField(layout, childField);
            if (child != null) ab.invoke(layout, child);
        } catch (Throwable ignored) {}
    }

    // ========== 工具方法 ==========

    private static Object getField(Object obj, String name) {
        try { java.lang.reflect.Field f = obj.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(obj); }
        catch (Throwable t) { return null; }
    }
    private static Object invoke(Object obj, String method) {
        try { java.lang.reflect.Method m = findMethod(obj.getClass(), method); return m != null ? m.invoke(obj) : null; }
        catch (Throwable t) { return null; }
    }
    private static java.lang.reflect.Field findField(Class<?> c, String n) {
        while (c != null) { try { return c.getDeclaredField(n); } catch (NoSuchFieldException e) { c = c.getSuperclass(); } } return null;
    }
    private static java.lang.reflect.Method findMethod(Class<?> c, String n, Class<?>... p) {
        while (c != null) { try { java.lang.reflect.Method m = c.getDeclaredMethod(n, p); m.setAccessible(true); return m; } catch (NoSuchMethodException e) { c = c.getSuperclass(); } } return null;
    }
}
