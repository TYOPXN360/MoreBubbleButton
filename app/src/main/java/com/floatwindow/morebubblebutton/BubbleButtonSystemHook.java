package com.floatwindow.morebubblebutton;

import android.app.Notification;
import android.content.Context;
import android.util.Log;
import android.view.View;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * BubbleButtonSystemHook — 为所有应用的通知横幅添加消息气泡按钮
 *
 * 方案：
 *   1. Hook shouldShowBubbleButton() → 对非常驻通知返回 true
 *   2. 点击时设置 BubbleMetadata 再调用 onNotificationBubbleIconClicked
 */
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
        hookBubbleClickListener();
    }

    // ========== 1. 使气泡按钮对所有非常驻通知显示 ==========

    private void hookShouldShowBubbleButton() {
        try {
            Class<?> clazz = mClassLoader.loadClass(
                    "com.android.systemui.statusbar.notification.row.NotificationContentView");
            hook(clazz.getMethod("shouldShowBubbleButton")).intercept(chain -> {
                // 开关检查
                try {
                    Context ctx = ((View) chain.getThisObject()).getContext();
                    if (!ModuleSettings.isSystemUiBubbleEnabled(ctx)) {
                        return chain.proceed();
                    }
                } catch (Throwable ignored) {}

                // 原始条件已满足
                boolean original = (boolean) chain.proceed();
                if (original) return true;

                // 非常驻通知 → 返回 true
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
                    boolean isForegroundSpecial = (notif.flags & 0x42) != 0;
                    if (isForeground || isForegroundSpecial) return false;

                    // 检查用户是否启用了气泡
                    java.lang.reflect.Field bubblesEnabled = findField(contentView.getClass(), "mBubblesEnabledForUser");
                    if (bubblesEnabled != null) {
                        bubblesEnabled.setAccessible(true);
                        if (!bubblesEnabled.getBoolean(contentView)) return false;
                    }

                    return true;
                } catch (Throwable t) {
                    Log.w(TAG, "shouldShowBubbleButton hook error: " + t.getMessage());
                    return false;
                }
            });
            Log.i(TAG, "Hooked shouldShowBubbleButton OK");
        } catch (Throwable t) {
            Log.e(TAG, "Hook shouldShowBubbleButton failed: " + t.getMessage());
        }
    }

    // ========== 2. 点击时设置 BubbleMetadata 再调用原逻辑 ==========

    private void hookBubbleClickListener() {
        try {
            Class<?> binderClass = mClassLoader.loadClass(
                    "com.android.systemui.statusbar.notification.collection.inflation.NotificationRowBinderImpl");

            // 同时 hook canBubble 让系统认为通知支持气泡
            hookRankingCanBubble();

            java.lang.reflect.Method target = null;
            for (java.lang.reflect.Method m : binderClass.getDeclaredMethods()) {
                if (m.getParameterCount() > 0 && m.getParameterTypes()[0].getName().contains("NotificationEntry")) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                Log.w(TAG, "bind method not found");
                return;
            }

            hook(target).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    int argCount = chain.getArgs().size();
                    if (argCount < 2) return result;
                    Object row = chain.getArg(1);
                    if (row == null) return result;

                    java.lang.reflect.Field clickField = findField(row.getClass(), "mBubbleClickListener");
                    if (clickField == null) return result;
                    clickField.setAccessible(true);
                    Object clickListener = clickField.get(row);
                    // 总是替换原始监听器（原监听器无法处理无 metadata 的通知）
                    // if (clickListener != null) return result;

                    // 获取 NotificationEntry
                    Object entry = getField(row, "mEntry");
                    if (entry == null) return result;

                    // 创建点击监听器
                    View.OnClickListener listener = v -> {
                        try {
                            // 获取 NotificationEntry 的 mSbn
                            Object entrySbn = getField(entry, "mSbn");
                            if (entrySbn == null) return;
                            Notification notif = (Notification) invoke(entrySbn, "getNotification");
                            if (notif == null) return;

                            // 设置 bubble metadata（如果还没有）
                            java.lang.reflect.Field metaField = findField(entry.getClass(), "mBubbleMetadata");
                            if (metaField != null) {
                                metaField.setAccessible(true);
                                Object existingMeta = metaField.get(entry);
                                if (existingMeta == null && notif.contentIntent != null) {
                                    // 创建 BubbleMetadata
                                    Notification.BubbleMetadata.Builder builder =
                                            new Notification.BubbleMetadata.Builder();
                                    builder.setIntent(notif.contentIntent);
                                    builder.setDeleteIntent(notif.deleteIntent);
                                    Notification.BubbleMetadata meta = builder.build();
                                    metaField.set(entry, meta);
                                    Log.i(TAG, "Set bubble metadata");
                                }
                            }

                            // 调用 onNotificationBubbleIconClicked
                            Object adapter = getField(row, "mEntryAdapter");
                            if (adapter != null) {
                                invoke(adapter, "onNotificationBubbleIconClicked");
                            }
                        } catch (Throwable t) {
                            Log.e(TAG, "Bubble click error: " + t.getMessage());
                        }
                    };

                    clickField.set(row, listener);

                    // 更新 UI
                    updateBubbleUI(row, "mPrivateLayout", "mExpandedChild");
                    updateBubbleUI(row, "mPrivateLayout", "mHeadsUpChild");

                    Log.d(TAG, "Set mBubbleClickListener");
                } catch (Throwable t) {
                    Log.w(TAG, "hookBubbleClickListener error: " + t.getMessage());
                }
                return result;
            });
            Log.i(TAG, "Hooked NotificationRowBinderImpl OK");
        } catch (Throwable t) {
            Log.e(TAG, "Hook NotificationRowBinderImpl failed: " + t.getMessage());
        }
    }

    private void updateBubbleUI(Object row, String layoutField, String childField) {
        try {
            Object layout = getField(row, layoutField);
            if (layout == null) return;
            java.lang.reflect.Method applyBubble = findMethod(layout.getClass(), "applyBubbleAction", View.class);
            if (applyBubble == null) return;
            Object child = getField(layout, childField);
            if (child != null) applyBubble.invoke(layout, child);
        } catch (Throwable ignored) {}
    }

    /**
     * Hook Ranking.canBubble() — 让系统认为所有非常驻通知都支持气泡
     */
    private void hookRankingCanBubble() {
        try {
            Class<?> rankingClass = mClassLoader.loadClass(
                    "android.service.notification.NotificationListenerService$Ranking");
            java.lang.reflect.Method canBubble = findMethod(rankingClass, "canBubble");
            if (canBubble != null) {
                hook(canBubble).intercept(chain -> {
                    boolean result = (boolean) chain.proceed();
                    if (result) return true;
                    // 非常驻通知 → 返回 true
                    try {
                        Object ranking = chain.getThisObject();
                        // 检查 notification flags
                        java.lang.reflect.Field flagsField = findField(ranking.getClass(), "mFlags");
                        if (flagsField != null) {
                            flagsField.setAccessible(true);
                            int flags = flagsField.getInt(ranking);
                            boolean isForeground = (flags & 0x40) != 0;
                            if (isForeground) return false;
                        }
                        return true;
                    } catch (Throwable t) {
                        return false;
                    }
                });
                Log.i(TAG, "Hooked Ranking.canBubble OK");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Hook Ranking.canBubble failed: " + t.getMessage());
        }
    }

    // ========== 工具方法 ==========

    private static Object getField(Object obj, String name) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable t) { return null; }
    }

    private static Object invoke(Object obj, String method) {
        try {
            java.lang.reflect.Method m = findMethod(obj.getClass(), method);
            return m != null ? m.invoke(obj) : null;
        } catch (Throwable t) { return null; }
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try { return clazz.getDeclaredField(name); }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        return null;
    }

    private static java.lang.reflect.Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        while (clazz != null) {
            try {
                java.lang.reflect.Method m = clazz.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) { clazz = clazz.getSuperclass(); }
        }
        return null;
    }
}
