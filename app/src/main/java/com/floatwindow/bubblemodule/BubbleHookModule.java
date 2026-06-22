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

        // Hook TaskOverlayFactory.getEnabledShortcuts — 在任务菜单中注入"消息气泡"
        try {
            Class<?> overlayFactoryClass = cl.loadClass("com.android.quickstep.TaskOverlayFactory");
            Class<?> taskViewClass = cl.loadClass("com.android.quickstep.views.TaskView");
            Class<?> taskContainerClass = cl.loadClass("com.android.quickstep.views.TaskContainer");
            hook(overlayFactoryClass.getMethod("getEnabledShortcuts", taskViewClass, taskContainerClass))
                    .intercept(chain -> {
                        java.util.List result = (java.util.List) chain.proceed();
                        try {
                            result = injectBubbleShortcut(result, chain.getArg(0), chain.getArg(1), cl);
                        } catch (Throwable t) {
                            log(Log.ERROR, TAG, "injectBubbleShortcut failed", t);
                        }
                        return result;
                    });
            log(Log.INFO, TAG, "Hooked TaskOverlayFactory.getEnabledShortcuts OK");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook getEnabledShortcuts: " + t.getMessage());
        }

        // Hook TaskMenuView.addMenuOptions — 在菜单中添加"消息气泡"按钮
        try {
            Class<?> menuViewClass = cl.loadClass("com.android.quickstep.views.TaskMenuView");
            hook(menuViewClass.getDeclaredMethod("addMenuOptions")).intercept(chain -> {
                chain.proceed(); // 先执行原始菜单添加
                try {
                    addBubbleMenuOption(chain.getThisObject(), cl);
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "addBubbleMenuOption failed", t);
                }
                return null;
            });
            log(Log.INFO, TAG, "Hooked TaskMenuView.addMenuOptions OK");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook addMenuOptions: " + t.getMessage());
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

    // ==================== 任务菜单气泡选项 ====================

    /**
     * 在 TaskMenuView 中添加"消息气泡"菜单选项
     */
    @SuppressLint("DiscouragedApi")
    private void addBubbleMenuOption(Object menuView, ClassLoader cl) {
        try {
            Context ctx = ((View) menuView).getContext();
            android.content.res.Resources res = ctx.getResources();
            String pkg = ctx.getPackageName();

            // 获取 optionLayout
            java.lang.reflect.Method getOptionLayout = findMethod(menuView.getClass(), "getOptionLayout");
            if (getOptionLayout == null) { log(Log.WARN, TAG, "getOptionLayout not found"); return; }
            ViewGroup optionLayout = (ViewGroup) getOptionLayout.invoke(menuView);
            if (optionLayout == null) { log(Log.WARN, TAG, "optionLayout is null"); return; }

            // 获取 TaskContainer
            java.lang.reflect.Method getTaskContainer = findMethod(menuView.getClass(), "getTaskContainer");
            Object taskContainer = getTaskContainer != null ? getTaskContainer.invoke(menuView) : null;
            if (taskContainer == null) { log(Log.WARN, TAG, "taskContainer is null"); return; }

            // inflate 菜单项布局 — 与原菜单完全一致
            int layoutId = res.getIdentifier("task_view_menu_option", "layout", pkg);
            if (layoutId == 0) { log(Log.WARN, TAG, "layout not found"); return; }

            ViewGroup menuItem = (ViewGroup) android.view.LayoutInflater.from(ctx)
                    .inflate(layoutId, optionLayout, false);

            // 设置背景 — 与原菜单一致（带 hover/pressed 高亮）
            int bgId = res.getIdentifier("app_chip_menu_item_bg", "drawable", pkg);
            if (bgId != 0) {
                menuItem.setBackground(res.getDrawable(bgId, ctx.getTheme()));
            }

            // 设置图标 — icon 是 View，用 setBackground
            int iconId = res.getIdentifier("icon", "id", pkg);
            View iconView = iconId != 0 ? menuItem.findViewById(iconId) : null;
            if (iconView != null) {
                int bubbleIconId = res.getIdentifier("ic_bubble_button", "drawable", pkg);
                if (bubbleIconId == 0) bubbleIconId = res.getIdentifier("ic_bubble_bar", "drawable", pkg);
                if (bubbleIconId != 0) {
                    android.graphics.drawable.Drawable icon = res.getDrawable(bubbleIconId, ctx.getTheme());
                    int tintColorId = res.getIdentifier("materialColorOnSurface", "color", pkg);
                    if (tintColorId != 0) icon.setTint(res.getColor(tintColorId, ctx.getTheme()));
                    iconView.setBackground(icon);
                }
            }

            // 设置文字
            int textId = res.getIdentifier("text", "id", pkg);
            View textView = textId != 0 ? menuItem.findViewById(textId) : null;
            if (textView instanceof android.widget.TextView) {
                ((android.widget.TextView) textView).setText("消息气泡");
            }

            // 设置布局参数 — 与原菜单一致：width=MATCH_PARENT, height=WRAP_CONTENT
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) menuItem.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            menuItem.setLayoutParams(lp);

            // 点击事件 — 触发气泡 + 关闭菜单 + 退出多任务
            menuItem.setOnClickListener(v -> {
                log(Log.INFO, TAG, "Bubble menu option clicked!");
                try {
                    // 1. 关闭菜单
                    java.lang.reflect.Method closeMethod = findMethod(
                            menuView.getClass(), "close", boolean.class);
                    if (closeMethod != null) {
                        closeMethod.invoke(menuView, true);
                        log(Log.INFO, TAG, "Menu closed");
                    }

                    // 2. 获取任务并触发气泡
                    Object task = invokeGetter(taskContainer, "getTask");
                    if (task == null) return;
                    Object taskKey = getField(task, "key");
                    Intent taskIntent = (Intent) getField(taskKey, "baseIntent");
                    int userId = getField(taskKey, "userId") != null ? (int) getField(taskKey, "userId") : 0;
                    if (taskIntent != null) {
                        bubbleCurrentTask(ctx, taskIntent, userId);
                    }

                    // 3. 退出多任务
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        dismissOverview(ctx);
                    }, 200);

                } catch (Throwable t) { log(Log.ERROR, TAG, "click failed: " + t.getMessage()); }
            });

            // 在注入时捕获 RecentsView 实例
            try {
                java.lang.reflect.Method getTaskViewM = findMethod(menuView.getClass(), "getTaskView");
                Object tv = getTaskViewM != null ? getTaskViewM.invoke(menuView) : null;
                if (tv != null) {
                    java.lang.reflect.Method getRecentsViewM = findMethod(tv.getClass(), "getRecentsView");
                    if (getRecentsViewM != null) {
                        recentsViewInstance = getRecentsViewM.invoke(tv);
                        log(Log.INFO, TAG, "Captured RecentsView: " + recentsViewInstance);
                    }
                }
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Capture RecentsView failed: " + t.getMessage());
            }

            optionLayout.addView(menuItem);
            log(Log.INFO, TAG, "Bubble menu added: width=" + lp.width + " height=" + lp.height);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "addBubbleMenuOption failed: " + t.getMessage());
        }
    }

    private static Object invokeGetter(Object obj, String methodName) {
        try {
            Method m = findMethod(obj.getClass(), methodName);
            return m != null ? m.invoke(obj) : null;
        } catch (Throwable t) { return null; }
    }

    /**
     * Hook TaskOverlayFactory.getEnabledShortcuts 的占位方法
     * 实际注入在 addBubbleMenuOption 中完成
     */
    private java.util.List<?> injectBubbleShortcut(java.util.List<?> shortcuts, Object taskView, Object taskContainer, ClassLoader cl) {
        return shortcuts;
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
     * 退出多任务界面 — 直接调用 RecentsView.finishRecentsAnimation
     */
    private void dismissOverview(Context ctx) {
        try {
            // 方案1: 从 View 层级查找 RecentsView（从菜单的 parent 链向上查找）
            Object rv = recentsViewInstance;
            if (rv == null) {
                try {
                    Class<?> recentsViewClass = mLauncherClassLoader.loadClass(
                            "com.android.quickstep.views.RecentsView");
                    // 从 ctx 的所有 View 中递归查找
                    if (ctx instanceof android.content.ContextWrapper) {
                        Context base = ctx;
                        while (base instanceof android.content.ContextWrapper) {
                            if (base instanceof android.app.Activity) {
                                android.view.View contentView = ((android.app.Activity) base).findViewById(android.R.id.content);
                                if (contentView != null) {
                                    rv = findRecentsViewInTree(contentView, recentsViewClass);
                                    if (rv != null) recentsViewInstance = rv;
                                }
                                break;
                            }
                            base = ((android.content.ContextWrapper) base).getBaseContext();
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (rv != null) {
                // 方案1: moveToRestState
                try {
                    Method getStateManager = findMethod(rv.getClass(), "getStateManager");
                    if (getStateManager != null) {
                        Object sm = getStateManager.invoke(rv);
                        if (sm != null) {
                            Method moveToRest = findMethod(sm.getClass(), "moveToRestState");
                            if (moveToRest != null) {
                                moveToRest.invoke(sm);
                                log(Log.INFO, TAG, "dismissed via moveToRestState");
                                return;
                            }
                        }
                    }
                } catch (Throwable t) {
                    log(Log.WARN, TAG, "moveToRestState failed: " + t.getMessage());
                }

                // 方案2: finishRecentsAnimation
                try {
                    Method finishRecents = findMethod(rv.getClass(), "finishRecentsAnimation",
                            boolean.class, boolean.class, Runnable.class);
                    if (finishRecents != null) {
                        finishRecents.invoke(rv, true, false, null);
                        log(Log.INFO, TAG, "dismissed via finishRecentsAnimation");
                        return;
                    }
                } catch (Throwable t) {
                    log(Log.WARN, TAG, "finishRecentsAnimation failed: " + t.getMessage());
                }
            }

            // 方案3: 通过 Launcher 的状态管理器
            try {
                Class<?> launcherClass = mLauncherClassLoader.loadClass("com.android.launcher3.Launcher");
                // 遍历 Activity 栈找到 Launcher
                android.app.ActivityManager am = (android.app.ActivityManager) ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE);
                java.util.List<android.app.ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(10);
                for (android.app.ActivityManager.RunningTaskInfo task : tasks) {
                    if (task.topActivity != null && task.topActivity.getClassName().contains("Launcher")) {
                        // 找到 Launcher Activity，获取其 state manager
                        log(Log.INFO, TAG, "Found Launcher task: " + task.topActivity);
                        break;
                    }
                }
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Launcher search failed: " + t.getMessage());
            }

            // 方案4: am start HOME — 兜底
            Runtime.getRuntime().exec(new String[]{
                    "am", "start", "-a", "android.intent.action.MAIN",
                    "-c", "android.intent.category.HOME"});
            log(Log.INFO, TAG, "dismissed via am start HOME");

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

    private static java.lang.reflect.Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try { return clazz.getDeclaredField(name); }
            catch (NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        while (clazz != null) {
            try {
                Method m = clazz.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) { clazz = clazz.getSuperclass(); }
        }
        return null;
    }

    private static void showToast(Context ctx, String msg) {
        new android.os.Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
    }
}
