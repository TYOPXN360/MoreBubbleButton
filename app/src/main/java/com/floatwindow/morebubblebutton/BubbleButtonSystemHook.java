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
 * 原始逻辑（SMS 有气泡按钮的原因）：
 *   NotificationContentView.shouldShowBubbleButton() 检查：
 *   1. mBubblesEnabledForUser — 系统设置中是否启用了气泡
 *   2. getPeopleNotificationType() >= 2 — 是否为消息类应用
 *   3. getBubbleMetadata() != null — 通知是否有气泡元数据
 *
 *   只有同时满足三个条件，气泡按钮才会显示。
 *   SMS 应用满足所有条件，其他应用通常不满足条件2和3。
 *
 * 我们的方案：
 *   1. Hook shouldShowBubbleButton() → 对非常驻通知返回 true
 *   2. 确保 mBubbleClickListener 始终被设置（原逻辑在无 clickIntent 时设为 null）
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
        Log.i(TAG, "Hooking SystemUI notification bubble logic...");
        hookShouldShowBubbleButton();
        hookNotificationBubbleClickListener();
    }

    /**
     * Hook NotificationContentView.shouldShowBubbleButton()
     */
    private void hookShouldShowBubbleButton() {
        try {
            Class<?> contentViewClass = mClassLoader.loadClass(
                    "com.android.systemui.statusbar.notification.row.NotificationContentView");

            hook(contentViewClass.getMethod("shouldShowBubbleButton")).intercept(chain -> {
                // 检查开关是否启用
                try {
                    Context ctx = ((View) chain.getThisObject()).getContext();
                    if (!ModuleSettings.isSystemUiBubbleEnabled(ctx)) {
                        return chain.proceed(); // 未启用，走原逻辑
                    }
                } catch (Throwable ignored) {}

                // 先检查原始条件，如果已经满足就直接返回
                boolean originalResult = (boolean) chain.proceed();
                if (originalResult) return true;

                // 获取通知 entry
                try {
                    Object contentView = chain.getThisObject();
                    java.lang.reflect.Field rowField = findField(contentViewClass, "mContainingNotification");
                    if (rowField == null) return false;
                    rowField.setAccessible(true);
                    Object row = rowField.get(contentView);
                    if (row == null) return false;

                    java.lang.reflect.Method getAdapter = findMethod(row.getClass(), "mEntryAdapter");
                    if (getAdapter == null) {
                        // 直接通过字段访问
                        java.lang.reflect.Field adapterField = findField(row.getClass(), "mEntryAdapter");
                        if (adapterField == null) return false;
                        adapterField.setAccessible(true);
                        Object adapter = adapterField.get(row);
                        if (adapter == null) return false;
                        getAdapter = adapter.getClass().getMethod("getSbn");
                    }

                    // 获取 StatusBarNotification
                    Object sbn = null;
                    Object adapter = null;
                    java.lang.reflect.Field adapterField = findField(row.getClass(), "mEntryAdapter");
                    if (adapterField != null) {
                        adapterField.setAccessible(true);
                        adapter = adapterField.get(row);
                    }
                    if (adapter != null) {
                        java.lang.reflect.Method getSbn = findMethod(adapter.getClass(), "getSbn");
                        if (getSbn != null) {
                            sbn = getSbn.invoke(adapter);
                        }
                    }
                    if (sbn == null) return false;

                    // 获取 Notification 对象
                    java.lang.reflect.Method getNotification = findMethod(sbn.getClass(), "getNotification");
                    if (getNotification == null) return false;
                    Notification notification = (Notification) getNotification.invoke(sbn);
                    if (notification == null) return false;

                    // 排除常驻通知 (FLAG_FOREGROUND_SERVICE = 0x40)
                    int flags = notification.flags;
                    boolean isForegroundService = (flags & 0x40) != 0;
                    boolean isForegroundServiceSpecial = (flags & 0x42) != 0; // FOREGROUND_SERVICE | FOREGROUND_SERVICE_SPECIAL

                    if (isForegroundService || isForegroundServiceSpecial) {
                        return false; // 常驻通知不显示气泡按钮
                    }

                    // 检查用户是否启用了气泡
                    java.lang.reflect.Field bubblesEnabledField = findField(contentViewClass, "mBubblesEnabledForUser");
                    if (bubblesEnabledField != null) {
                        bubblesEnabledField.setAccessible(true);
                        boolean enabled = bubblesEnabledField.getBoolean(contentView);
                        if (!enabled) return false;
                    }

                    // 非常驻通知，允许显示气泡按钮
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

    /**
     * Hook NotificationRowBinderImpl 的通知绑定方法
     *
     * 原逻辑：当通知没有 contentIntent 和 fullScreenIntent 且不是 bubble 时，
     *         mBubbleClickListener 被设为 null，导致气泡按钮点击无反应。
     *
     * 新逻辑：即使通知没有 clickIntent，也保留 mBubbleClickListener。
     */
    private void hookNotificationBubbleClickListener() {
        try {
            Class<?> binderClass = mClassLoader.loadClass(
                    "com.android.systemui.statusbar.notification.collection.inflation.NotificationRowBinderImpl");

            // 找到设置 mBubbleClickListener 的方法
            // 这个方法是 private，在 bindRow 中被调用
            // 我们 hook 整个 bindRow 或者更简单地 hook mBubbleClickListener 的 setter

            // 实际上，原代码中 mBubbleClickListener 是直接赋值的，没有 setter
            // 我们需要 hook 绑定过程，在 mBubbleClickListener = null 之后重新设置它

            // 方案：hook NotificationRowBinderImpl 的 bindNotificationRow 方法
            // 通过 hook，确保 mBubbleClickListener 始终被设置

            // 查找目标方法
            java.lang.reflect.Method targetMethod = null;
            for (java.lang.reflect.Method m : binderClass.getDeclaredMethods()) {
                if (m.getParameterCount() > 0 && m.getParameterTypes()[0].getName().contains("NotificationEntry")) {
                    targetMethod = m;
                    break;
                }
            }

            if (targetMethod == null) {
                Log.w(TAG, "Could not find bindNotificationRow method");
                return;
            }

            hook(targetMethod).intercept(chain -> {
                Object result = chain.proceed();

                try {
                    // 获取参数中的 ExpandableNotificationRow
                    int argCount = chain.getArgs().size();
                    if (argCount >= 2) {
                        Object row = chain.getArg(1); // 通常是第二个参数
                        if (row == null) return result;

                        // 检查 mBubbleClickListener 是否为 null
                        java.lang.reflect.Field clickListenerField = findField(row.getClass(), "mBubbleClickListener");
                        if (clickListenerField != null) {
                            clickListenerField.setAccessible(true);
                            Object clickListener = clickListenerField.get(row);

                            if (clickListener == null) {
                                // mBubbleClickListener 为 null，需要重新设置
                                // 创建一个 lambda 来处理气泡点击
                                // 但我们需要 NotificationClicker$$ExternalSyntheticLambda0
                                // 简化方案：直接用原 row 的 entry 调用 onNotificationBubbleIconClicked

                                // 获取 mEntryAdapter
                                java.lang.reflect.Field adapterField = findField(row.getClass(), "mEntryAdapter");
                                if (adapterField != null) {
                                    adapterField.setAccessible(true);
                                    Object adapter = adapterField.get(row);
                                    if (adapter != null) {
                                        // 创建点击监听器
                                        final Object finalRow = row;
                                        final Object finalAdapter = adapter;
                                        View.OnClickListener listener = v -> {
                                            try {
                                                java.lang.reflect.Method onClickMethod = findMethod(
                                                        finalAdapter.getClass(), "onNotificationBubbleIconClicked");
                                                if (onClickMethod != null) {
                                                    onClickMethod.invoke(finalAdapter);
                                                }
                                            } catch (Throwable t) {
                                                Log.e(TAG, "Bubble click handler error: " + t.getMessage());
                                            }
                                        };

                                        clickListenerField.set(row, listener);

                                        // 更新 UI
                                        java.lang.reflect.Field privateLayoutField = findField(row.getClass(), "mPrivateLayout");
                                        if (privateLayoutField != null) {
                                            privateLayoutField.setAccessible(true);
                                            Object privateLayout = privateLayoutField.get(row);
                                            if (privateLayout != null) {
                                                java.lang.reflect.Method applyBubble = findMethod(
                                                        privateLayout.getClass(), "applyBubbleAction", View.class);
                                                if (applyBubble != null) {
                                                    java.lang.reflect.Field expandedField = findField(
                                                            privateLayout.getClass(), "mExpandedChild");
                                                    if (expandedField != null) {
                                                        expandedField.setAccessible(true);
                                                        Object expandedChild = expandedField.get(privateLayout);
                                                        if (expandedChild != null) {
                                                            applyBubble.invoke(privateLayout, expandedChild);
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 同样处理 mHeadsUpChild
                                        java.lang.reflect.Field privateLayoutField2 = findField(row.getClass(), "mPrivateLayout");
                                        if (privateLayoutField2 != null) {
                                            privateLayoutField2.setAccessible(true);
                                            Object privateLayout = privateLayoutField2.get(row);
                                            if (privateLayout != null) {
                                                java.lang.reflect.Method applyBubble = findMethod(
                                                        privateLayout.getClass(), "applyBubbleAction", View.class);
                                                if (applyBubble != null) {
                                                    java.lang.reflect.Field headsUpField = findField(
                                                            privateLayout.getClass(), "mHeadsUpChild");
                                                    if (headsUpField != null) {
                                                        headsUpField.setAccessible(true);
                                                        Object headsUpChild = headsUpField.get(privateLayout);
                                                        if (headsUpChild != null) {
                                                            applyBubble.invoke(privateLayout, headsUpChild);
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Log.d(TAG, "Set mBubbleClickListener for notification");
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "hookNotificationBubbleClickListener error: " + t.getMessage());
                }

                return result;
            });

            Log.i(TAG, "Hooked NotificationRowBinderImpl OK");
        } catch (Throwable t) {
            Log.e(TAG, "Hook NotificationRowBinderImpl failed: " + t.getMessage());
        }
    }

    // 工具方法
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
