package com.floatwindow.bubblemodule;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class BubbleHookModule extends XposedModule {
    private static final String TAG = "BubbleModule";
    private Object recentsViewInstance;
    private View bubbleButton;
    private View secondRow;
    private ClassLoader mLauncherClassLoader;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "onModuleLoaded: " + param.getProcessName() + " | API " + getApiVersion());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String pkg = param.getPackageName();
        log(Log.INFO, TAG, "onPackageLoaded: " + pkg);
        if ("com.google.android.apps.nexuslauncher".equals(pkg)
                || "com.android.launcher3".equals(pkg)) {
            hookLauncher(param);
        }
    }

    private void hookLauncher(PackageLoadedParam param) {
        ClassLoader cl = param.getDefaultClassLoader();
        mLauncherClassLoader = cl;

        // Hook RecentsView 构造函数 — 尝试多种签名
        try {
            Class<?> clazz = cl.loadClass("com.android.quickstep.views.RecentsView");
            boolean hooked = false;
            for (java.lang.reflect.Constructor<?> ctor : clazz.getConstructors()) {
                if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0] == Context.class) {
                    hook(ctor).intercept(chain -> {
                        Object ret = chain.proceed();
                        recentsViewInstance = chain.getThisObject();
                        log(Log.INFO, TAG, "RecentsView instance captured: " + recentsViewInstance);
                        return ret;
                    });
                    hooked = true;
                    break;
                }
            }
            if (!hooked) {
                // fallback: hook the first single-arg constructor
                for (java.lang.reflect.Constructor<?> ctor : clazz.getConstructors()) {
                    if (ctor.getParameterCount() == 1) {
                        hook(ctor).intercept(chain -> {
                            Object ret = chain.proceed();
                            recentsViewInstance = chain.getThisObject();
                            log(Log.INFO, TAG, "RecentsView instance captured (fallback): " + recentsViewInstance);
                            return ret;
                        });
                        hooked = true;
                        break;
                    }
                }
            }
            if (!hooked) log(Log.WARN, TAG, "RecentsView constructor not found");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook RecentsView", t);
        }

        // Hook OverviewActionsView.onFinishInflate — 注入按钮
        try {
            Class<?> clazz = cl.loadClass("com.android.quickstep.views.OverviewActionsView");
            hook(clazz.getMethod("onFinishInflate")).intercept(chain -> {
                Object ret = chain.proceed();
                try { injectBubbleButton(chain.getThisObject(), cl); }
                catch (Throwable t) { log(Log.ERROR, TAG, "inject failed", t); }
                return ret;
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook onFinishInflate", t);
        }

        // Hook OverviewActionsView.onClick
        try {
            Class<?> clazz = cl.loadClass("com.android.quickstep.views.OverviewActionsView");
            hook(clazz.getMethod("onClick", View.class)).intercept(chain -> {
                View v = (View) chain.getArg(0);
                if (v != null && bubbleButton != null && v.getId() == bubbleButton.getId()) {
                    onBubbleButtonClick((View) chain.getThisObject());
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook onClick", t);
        }

        // Hook updateHiddenFlags — 控制可见性
        try {
            Class<?> clazz = cl.loadClass("com.android.quickstep.views.OverviewActionsView");
            hook(clazz.getMethod("updateHiddenFlags", int.class, boolean.class)).intercept(chain -> {
                Object ret = chain.proceed();
                updateBubbleVisibility(chain.getThisObject());
                return ret;
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook updateHiddenFlags: " + t.getMessage());
        }
    }

    // ==================== 按钮注入 ====================

    @SuppressLint("DiscouragedApi")
    private void injectBubbleButton(Object actionsView, ClassLoader cl) {
        Context ctx = ((View) actionsView).getContext();
        android.content.res.Resources res = ctx.getResources();
        String pkg = ctx.getPackageName();

        // 获取 action_buttons
        LinearLayout mActionButtons = null;
        int abId = res.getIdentifier("action_buttons", "id", pkg);
        if (abId != 0) mActionButtons = ((View) actionsView).findViewById(abId);
        if (mActionButtons == null) {
            log(Log.ERROR, TAG, "action_buttons not found!");
            return;
        }

        // 统计可见的原始按钮（排除 ClearAllButton 和气泡按钮）
        int visibleButtonCount = 0;
        for (int i = 0; i < mActionButtons.getChildCount(); i++) {
            View child = mActionButtons.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE || !(child instanceof Button)) continue;
            if (child.getTag() != null && "bubble_button".equals(child.getTag())) continue;
            if (isClearAllButton(child)) continue;
            visibleButtonCount++;
        }

        Button btn = createBubbleButton(ctx, res, pkg);
        ViewGroup actionsParent = (ViewGroup) actionsView;

        if (visibleButtonCount >= 3) {
            ensureSecondRow(actionsParent, btn, res, pkg);
        } else {
            btn.setTag("bubble_button");
            ViewGroup.MarginLayoutParams mlp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int spacingId = res.getIdentifier("overview_actions_button_spacing", "dimen", pkg);
            if (spacingId != 0) mlp.setMarginStart(res.getDimensionPixelSize(spacingId));
            btn.setLayoutParams(mlp);
            mActionButtons.addView(btn);
            bubbleButton = btn;
        }
    }

    private boolean isClearAllButton(View child) {
        try {
            Class<?> clazz = mLauncherClassLoader.loadClass("com.android.quickstep.views.ClearAllButton");
            return clazz.isInstance(child);
        } catch (Throwable t) { return false; }
    }

    @SuppressLint("DiscouragedApi")
    private Button createBubbleButton(Context ctx, android.content.res.Resources res, String pkg) {
        int styleId = res.getIdentifier("OverviewActionButton.Blur", "style", pkg);
        if (styleId == 0) styleId = res.getIdentifier("OverviewActionButton", "style", pkg);

        Button btn = (styleId != 0) ? new Button(ctx, null, 0, styleId) : new Button(ctx);
        btn.setText("消息气泡");
        btn.setContentDescription("消息气泡");
        btn.setId(View.generateViewId());
        btn.setTag("bubble_button");

        int iconId = res.getIdentifier("ic_bubble_button", "drawable", pkg);
        if (iconId == 0) iconId = res.getIdentifier("ic_bubble_bar", "drawable", pkg);
        if (iconId != 0) {
            android.graphics.drawable.Drawable icon = res.getDrawable(iconId, null);
            if (icon != null) btn.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        }
        btn.setOnClickListener(v -> onBubbleButtonClick((View) btn.getParent().getParent()));
        return btn;
    }

    @SuppressLint("DiscouragedApi")
    private void ensureSecondRow(ViewGroup actionsParent, Button btn,
            android.content.res.Resources res, String pkg) {
        Context ctx = actionsParent.getContext();
        // 已有第二行且已有气泡按钮
        if (secondRow != null && secondRow.getParent() == actionsParent) {
            for (int i = 0; i < ((ViewGroup) secondRow).getChildCount(); i++) {
                View child = ((ViewGroup) secondRow).getChildAt(i);
                if (child.getTag() != null && "bubble_button".equals(child.getTag())) {
                    bubbleButton = child;
                    return;
                }
            }
            ViewGroup.MarginLayoutParams btnMlp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int spacingId = res.getIdentifier("overview_actions_button_spacing", "dimen", pkg);
            if (spacingId != 0) btnMlp.setMarginStart(res.getDimensionPixelSize(spacingId));
            btn.setLayoutParams(btnMlp);
            ((ViewGroup) secondRow).addView(btn);
            bubbleButton = btn;
            return;
        }

        LinearLayout newSecondRow = new LinearLayout(ctx);
        newSecondRow.setTag("bubble_second_row");
        newSecondRow.setOrientation(LinearLayout.HORIZONTAL);
        newSecondRow.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        ViewGroup.MarginLayoutParams btnMlp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int spacingId = res.getIdentifier("overview_actions_button_spacing", "dimen", pkg);
        if (spacingId != 0) btnMlp.setMarginStart(res.getDimensionPixelSize(spacingId));
        btn.setLayoutParams(btnMlp);
        newSecondRow.addView(btn);
        bubbleButton = btn;

        int insertIndex = 0;
        int abId = res.getIdentifier("action_buttons", "id", pkg);
        View actionButtonsView = actionsParent.findViewById(abId);
        if (actionButtonsView != null) insertIndex = actionsParent.indexOfChild(actionButtonsView) + 1;

        FrameLayout.LayoutParams rowLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        actionsParent.addView(newSecondRow, insertIndex, rowLp);
        secondRow = newSecondRow;

        // 定位到 action_buttons 下方 + 同步 alpha 动画
        newSecondRow.post(() -> {
            if (actionButtonsView != null) {
                int[] loc = new int[2], parentLoc = new int[2];
                actionButtonsView.getLocationOnScreen(loc);
                actionsParent.getLocationOnScreen(parentLoc);
                int relativeBottom = loc[1] - parentLoc[1] + actionButtonsView.getHeight();
                int topMarginId = res.getIdentifier("overview_actions_top_margin", "dimen", pkg);
                int extraSpacing = topMarginId != 0 ? res.getDimensionPixelSize(topMarginId) : 24;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) newSecondRow.getLayoutParams();
                lp.topMargin = relativeBottom + extraSpacing;
                newSecondRow.setLayoutParams(lp);
            }
            // 每帧同步 alpha
            actionsParent.getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override public boolean onPreDraw() {
                            if (secondRow != null && actionButtonsView != null)
                                secondRow.setAlpha(actionButtonsView.getAlpha());
                            return true;
                        }
                    });
        });
    }

    private void updateBubbleVisibility(Object actionsView) {
        // 仅日志，实际同步由 OnPreDrawListener 完成
    }

    // ==================== 气泡触发逻辑 ====================

    /**
     * 点击"消息气泡" → 将当前选中的任务变为消息气泡
     */
    private void onBubbleButtonClick(View actionsView) {
        Context ctx = actionsView.getContext();
        log(Log.INFO, TAG, "Bubble button clicked!");

        // 从 View 层级向上查找 RecentsView
        Object recentsView = recentsViewInstance;
        if (recentsView == null) {
            recentsView = findRecentsViewFromHierarchy(actionsView);
        }

        if (recentsView == null) {
            log(Log.WARN, TAG, "RecentsView not found");
            showToast(ctx, "无法获取最近任务视图");
            return;
        }

        try {
            Method getCurrentPageTaskView = findMethod(recentsView.getClass(), "getCurrentPageTaskView");
            if (getCurrentPageTaskView == null) { log(Log.WARN, TAG, "getCurrentPageTaskView not found"); return; }
            Object taskView = getCurrentPageTaskView.invoke(recentsView);
            if (taskView == null) { log(Log.WARN, TAG, "No current task view"); showToast(ctx, "没有选中的任务"); return; }

            Method getTaskContainers = findMethod(taskView.getClass(), "getTaskContainers");
            if (getTaskContainers == null) { log(Log.WARN, TAG, "getTaskContainers not found"); return; }
            List<?> taskContainers = (List<?>) getTaskContainers.invoke(taskView);
            if (taskContainers == null || taskContainers.isEmpty()) { log(Log.WARN, TAG, "No task containers"); return; }

            Object taskContainer = taskContainers.get(0);
            Method getTask = findMethod(taskContainer.getClass(), "getTask");
            if (getTask == null) { log(Log.WARN, TAG, "getTask not found"); return; }
            Object task = getTask.invoke(taskContainer);
            if (task == null) { log(Log.WARN, TAG, "Task is null"); return; }

            Object taskKey = getField(task, "key");
            Intent taskIntent = (Intent) getField(taskKey, "baseIntent");
            int userId = getField(taskKey, "userId") != null ? (int) getField(taskKey, "userId") : 0;

            log(Log.INFO, TAG, "Current task: intent=" + taskIntent + " userId=" + userId);
            if (taskIntent == null) { showToast(ctx, "无法获取任务信息"); return; }

            bubbleCurrentTask(ctx, taskIntent, userId);

        } catch (Throwable t) {
            log(Log.ERROR, TAG, "onBubbleButtonClick failed: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * 从 View 层级向上查找 RecentsView 实例
     */
    private Object findRecentsViewFromHierarchy(View view) {
        try {
            Class<?> recentsViewClass = mLauncherClassLoader.loadClass("com.android.quickstep.views.RecentsView");
            View current = view;
            while (current != null) {
                if (recentsViewClass.isInstance(current)) {
                    log(Log.INFO, TAG, "Found RecentsView from hierarchy: " + current);
                    recentsViewInstance = current;
                    return current;
                }
                if (current.getParent() instanceof View) {
                    current = (View) current.getParent();
                } else {
                    break;
                }
            }
            // 尝试从 DecorView 的 rootView 查找
            View rootView = view.getRootView();
            if (rootView != null) {
                Object result = findRecentsViewInTree(rootView, recentsViewClass);
                if (result != null) {
                    recentsViewInstance = result;
                    return result;
                }
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "findRecentsViewFromHierarchy failed: " + t.getMessage());
        }
        return null;
    }

    /**
     * 递归搜索 View 树查找 RecentsView
     */
    private Object findRecentsViewInTree(View view, Class<?> targetClass) {
        if (targetClass.isInstance(view)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                Object result = findRecentsViewInTree(vg.getChildAt(i), targetClass);
                if (result != null) return result;
            }
        }
        return null;
    }

    /**
     * 通过 SystemUiProxy.showAppBubble 将任务变为气泡
     */
    private boolean bubbleCurrentTask(Context ctx, Intent taskIntent, int userId) {
        try {
            // 获取 SystemUiProxy
            Class<?> proxyClass = mLauncherClassLoader.loadClass("com.android.quickstep.SystemUiProxy");
            Object daggerSingleton = proxyClass.getField("INSTANCE").get(null);
            Object systemUiProxy = daggerSingleton.getClass().getMethod("get", Context.class)
                    .invoke(daggerSingleton, ctx.getApplicationContext());
            if (systemUiProxy == null) { log(Log.WARN, TAG, "SystemUiProxy is null"); return false; }

            // Intent — 确保有 package
            Intent bubbleIntent = new Intent(taskIntent);
            if (bubbleIntent.getPackage() == null && bubbleIntent.getComponent() != null)
                bubbleIntent.setPackage(bubbleIntent.getComponent().getPackageName());

            // UserHandle.of(userId)
            Object userHandle = mLauncherClassLoader.loadClass("android.os.UserHandle")
                    .getMethod("of", int.class).invoke(null, userId);

            // EntryPoint.NOTIFICATION
            Class<?> epClass = mLauncherClassLoader.loadClass(
                    "com.android.wm.shell.shared.bubbles.logging.EntryPoint");
            Object[] epValues = (Object[]) epClass.getDeclaredField("$VALUES").get(null);
            Object entryPoint = null;
            for (Object ep : epValues) if ("NOTIFICATION".equals(ep.toString())) { entryPoint = ep; break; }

            // 查找 showAppBubble 方法并调用
            for (Method m : systemUiProxy.getClass().getMethods()) {
                if (m.getName().equals("showAppBubble")) {
                    log(Log.INFO, TAG, "Calling showAppBubble: " + bubbleIntent);
                    m.invoke(systemUiProxy, bubbleIntent, userHandle, entryPoint, null);
                    log(Log.INFO, TAG, "showAppBubble succeeded!");
                    showToast(ctx, "消息气泡已触发");
                    // 立即退出多任务界面
                    dismissOverview(ctx);
                    return true;
                }
            }
            log(Log.WARN, TAG, "showAppBubble not found");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "bubbleCurrentTask failed: " + t.getMessage());
        }
        return false;
    }

    /**
     * 退出多任务界面
     */
    private void dismissOverview(Context ctx) {
        try {
            // 方法1: 通过 RecentsView 调用 finishRecentsAnimation
            if (recentsViewInstance != null) {
                Method finishRecents = findMethod(recentsViewInstance.getClass(), "finishRecentsAnimation", boolean.class, boolean.class);
                if (finishRecents != null) {
                    finishRecents.invoke(recentsViewInstance, true, false);
                    log(Log.INFO, TAG, "dismissed overview via finishRecentsAnimation");
                    return;
                }
                // 尝试 onDismiss
                Method onDismiss = findMethod(recentsViewInstance.getClass(), "onDismiss");
                if (onDismiss != null) {
                    onDismiss.invoke(recentsViewInstance);
                    log(Log.INFO, TAG, "dismissed overview via onDismiss");
                    return;
                }
            }

            // 方法2: 通过 am 命令发送 HOME
            android.os.Handler handler = new android.os.Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                try {
                    Runtime.getRuntime().exec(new String[]{"input", "keyevent", "KEYCODE_HOME"});
                    log(Log.INFO, TAG, "dismissed overview via HOME keyevent");
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "HOME keyevent failed: " + t.getMessage());
                }
            }, 300);

        } catch (Throwable t) {
            log(Log.ERROR, TAG, "dismissOverview failed: " + t.getMessage());
        }
    }

    // ==================== 工具方法 ====================

    private static Object getField(Object obj, String name) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable t) { return null; }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        while (clazz != null) {
            try { return clazz.getDeclaredMethod(name, params); }
            catch (NoSuchMethodException e) { clazz = clazz.getSuperclass(); }
        }
        return null;
    }

    private static void showToast(Context ctx, String msg) {
        new android.os.Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
    }
}
