package com.floatwindow.bubblemodule;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * BubbleHookModule — 在 Pixel Launcher 最近任务界面添加"消息气泡"按钮
 *
 * 关键点：
 * - NexusOverviewActionsView 是 FrameLayout，子 View 会重叠，必须用 LayoutParams 定位
 * - 清除所有按钮 (ClearAllButton) 不计入溢出阈值
 * - 只在 overview/recents 状态下显示
 */
public class BubbleHookModule extends XposedModule {
    private static final String TAG = "BubbleModule";

    private Object recentsViewInstance;
    private View bubbleButton;
    private View secondRow;
    private ClassLoader mLauncherClassLoader; // 缓存 Launcher 的 classloader

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "onModuleLoaded: " + param.getProcessName()
                + " | API " + getApiVersion());
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

        // 1. Hook RecentsView 构造函数
        try {
            Class<?> recentsViewClass = cl.loadClass(
                    "com.android.quickstep.views.RecentsView");
            hook(recentsViewClass.getConstructor(Context.class)).intercept(chain -> {
                Object ret = chain.proceed();
                recentsViewInstance = chain.getThisObject();
                return ret;
            });
            log(Log.INFO, TAG, "Hooked RecentsView constructor");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook RecentsView", t);
        }

        // 2. Hook OverviewActionsView.onFinishInflate — 注入按钮
        try {
            Class<?> overviewActionsClass = cl.loadClass(
                    "com.android.quickstep.views.OverviewActionsView");
            hook(overviewActionsClass.getMethod("onFinishInflate")).intercept(chain -> {
                Object ret = chain.proceed();
                try {
                    injectBubbleButton(chain.getThisObject(), cl);
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "inject failed", t);
                }
                return ret;
            });
            log(Log.INFO, TAG, "Hooked OverviewActionsView.onFinishInflate OK");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook OverviewActionsView", t);
        }

        // 3. Hook OverviewActionsView.onClick
        try {
            Class<?> overviewActionsClass = cl.loadClass(
                    "com.android.quickstep.views.OverviewActionsView");
            hook(overviewActionsClass.getMethod("onClick", View.class)).intercept(chain -> {
                View v = (View) chain.getArg(0);
                if (v != null && bubbleButton != null && v.getId() == bubbleButton.getId()) {
                    log(Log.INFO, TAG, ">>> Bubble button clicked!");
                    onBubbleButtonClick((View) chain.getThisObject());
                    return null;
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "Hooked OverviewActionsView.onClick OK");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook onClick", t);
        }

        // 4. Hook OverviewActionsView.updateHiddenFlags — 控制可见性
        try {
            Class<?> overviewActionsClass = cl.loadClass(
                    "com.android.quickstep.views.OverviewActionsView");
            hook(overviewActionsClass.getMethod("updateHiddenFlags", int.class, boolean.class))
                    .intercept(chain -> {
                        Object ret = chain.proceed();
                        try {
                            updateBubbleVisibility(chain.getThisObject());
                        } catch (Throwable t) {
                            log(Log.ERROR, TAG, "updateBubbleVisibility failed", t);
                        }
                        return ret;
                    });
            log(Log.INFO, TAG, "Hooked updateHiddenFlags OK");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook updateHiddenFlags", t);
        }

        // 5. Hook RecentsState.hasClearAllButton — 了解 overview 状态
        try {
            Class<?> recentsStateClass = cl.loadClass(
                    "com.android.quickstep.fallback.RecentsState");
            hook(recentsStateClass.getMethod("hasClearAllButton")).intercept(chain -> {
                boolean ret = (boolean) chain.proceed();
                log(Log.INFO, TAG, "hasClearAllButton=" + ret);
                return ret;
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook RecentsState: " + t.getMessage());
        }
    }

    /**
     * 控制气泡按钮可见性 — 只在 overview 状态下显示
     * 注意：原按钮使用 alpha 动画（MultiValueAlpha），不是 setVisibility
     * 所以这里只做日志记录，实际同步由 OnPreDrawListener 完成
     */
    private void updateBubbleVisibility(Object actionsView) {
        if (secondRow == null || bubbleButton == null) return;

        try {
            // 遍历类层次结构找到 mHiddenFlags
            int hiddenFlags = -1;
            Class<?> clazz = actionsView.getClass();
            while (clazz != null) {
                try {
                    java.lang.reflect.Field hiddenField = clazz.getDeclaredField("mHiddenFlags");
                    hiddenField.setAccessible(true);
                    hiddenFlags = hiddenField.getInt(actionsView);
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }

            if (hiddenFlags == -1) return;

            boolean shouldShow = (hiddenFlags == 0);
            log(Log.INFO, TAG, "updateBubbleVisibility: hiddenFlags=" + hiddenFlags
                    + " shouldShow=" + shouldShow
                    + " actionAlpha=" + ((View) actionsView).findViewById(
                            ((View) actionsView).getContext().getResources()
                                    .getIdentifier("action_buttons", "id",
                                            ((View) actionsView).getContext().getPackageName()))
                            .getAlpha());
        } catch (Throwable t) {
            // ignore
        }
    }

    /**
     * 注入"消息气泡"按钮
     */
    @SuppressLint("DiscouragedApi")
    private void injectBubbleButton(Object actionsView, ClassLoader cl) {
        Context ctx = ((View) actionsView).getContext();
        android.content.res.Resources launcherRes = ctx.getResources();
        String launcherPkg = ctx.getPackageName();

        log(Log.INFO, TAG, "injectBubbleButton: pkg=" + launcherPkg);

        // ===== 获取 action_buttons =====
        LinearLayout mActionButtons = null;
        int abId = launcherRes.getIdentifier("action_buttons", "id", launcherPkg);
        if (abId != 0) {
            mActionButtons = ((View) actionsView).findViewById(abId);
        }
        if (mActionButtons == null) {
            log(Log.ERROR, TAG, "action_buttons not found!");
            return;
        }
        log(Log.INFO, TAG, "action_buttons: " + mActionButtons + " children=" + mActionButtons.getChildCount());

        // ===== 统计可见的"原始"按钮数（排除 ClearAllButton 和气泡按钮） =====
        int visibleButtonCount = 0;
        for (int i = 0; i < mActionButtons.getChildCount(); i++) {
            View child = mActionButtons.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            if (!(child instanceof Button)) continue;
            // 排除气泡按钮
            if (child.getTag() != null && "bubble_button".equals(child.getTag())) continue;
            // 排除 ClearAllButton（PLenhanced 模块的）
            if (isClearAllButton(child)) {
                log(Log.INFO, TAG, "  Skipping ClearAllButton: " + child);
                continue;
            }
            visibleButtonCount++;
            log(Log.INFO, TAG, "  Button " + visibleButtonCount + ": "
                    + child.getClass().getSimpleName() + " id=" + Integer.toHexString(child.getId()));
        }
        log(Log.INFO, TAG, "Visible original buttons: " + visibleButtonCount);

        // ===== 创建气泡按钮 =====
        Button btn = createBubbleButton(ctx, launcherRes, launcherPkg);

        // ===== 判断是否需要第二行 =====
        ViewGroup actionsParent = (ViewGroup) actionsView;
        boolean needSecondRow = visibleButtonCount >= 3;

        if (needSecondRow) {
            log(Log.INFO, TAG, "Overflow (" + visibleButtonCount + " >= 3), creating second row");
            ensureSecondRow(actionsParent, btn, launcherRes, launcherPkg);
        } else {
            log(Log.INFO, TAG, "Fits (" + visibleButtonCount + " < 3), adding to action_buttons");
            btn.setTag("bubble_button");
            int spacingId = launcherRes.getIdentifier(
                    "overview_actions_button_spacing", "dimen", launcherPkg);
            ViewGroup.MarginLayoutParams mlp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (spacingId != 0) {
                mlp.setMarginStart(launcherRes.getDimensionPixelSize(spacingId));
            }
            btn.setLayoutParams(mlp);
            mActionButtons.addView(btn);
            bubbleButton = btn;
        }

        log(Log.INFO, TAG, "=== Bubble button ready ===");
    }

    /**
     * 检查是否是 ClearAllButton（PLenhanced 模块的按钮）
     */
    private boolean isClearAllButton(View child) {
        String className = child.getClass().getName();
        // ClearAllButton 的类名
        if (className.contains("ClearAllButton")) return true;
        // PLenhanced 的 ClearAllButton 资源 ID
        if (child.getId() == 0x7f0a0107) return true; // R.id.clear_all
        // 检查 classLoader 中的类名
        try {
            Class<?> clearAllClass = child.getClass().getClassLoader()
                    .loadClass("com.android.quickstep.views.ClearAllButton");
            return clearAllClass.isInstance(child);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 创建气泡按钮 — 使用 Launcher 的完整主题样式
     */
    @SuppressLint("DiscouragedApi")
    private Button createBubbleButton(Context ctx, android.content.res.Resources res, String pkg) {
        // 关键：使用 OverviewActionButton 样式（含圆角背景、文字颜色、padding）
        int overviewBtnStyleId = res.getIdentifier("OverviewActionButton", "style", pkg);
        int overviewBtnBlurStyleId = res.getIdentifier("OverviewActionButton.Blur", "style", pkg);

        // 优先使用 Blur 样式（毛玻璃效果），fallback 到普通样式
        int useStyleId = overviewBtnBlurStyleId != 0 ? overviewBtnBlurStyleId : overviewBtnStyleId;

        log(Log.INFO, TAG, "Using button style: " + useStyleId
                + " (blur=" + overviewBtnBlurStyleId + " normal=" + overviewBtnStyleId + ")");

        Button btn;
        if (useStyleId != 0) {
            // 直接用 Launcher 的 context（已含 overviewActionsContainerStyle 主题）
            btn = new Button(ctx, null, 0, useStyleId);
        } else {
            btn = new Button(ctx);
        }

        btn.setText("消息气泡");
        btn.setContentDescription("消息气泡");
        btn.setId(View.generateViewId());
        btn.setTag("bubble_button");

        // 图标
        int iconId = res.getIdentifier("ic_bubble_button", "drawable", pkg);
        if (iconId == 0) iconId = res.getIdentifier("ic_bubble_bar", "drawable", pkg);
        if (iconId == 0) iconId = res.getIdentifier("bubble_ic_overflow_button", "drawable", pkg);
        if (iconId != 0) {
            android.graphics.drawable.Drawable icon = res.getDrawable(iconId, null);
            if (icon != null) {
                btn.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
            }
        }

        // 点击
        btn.setOnClickListener(v -> {
            View actionsView = (View) btn.getParent().getParent();
            if (actionsView == null) actionsView = (View) btn.getParent();
            onBubbleButtonClick(actionsView);
        });

        return btn;
    }

    /**
     * 确保第二行存在，并将按钮放入其中
     * NexusOverviewActionsView 是 FrameLayout，需要用 FrameLayout.LayoutParams 精确定位
     */
    @SuppressLint("DiscouragedApi")
    private void ensureSecondRow(ViewGroup actionsParent, Button btn,
            android.content.res.Resources res, String pkg) {

        // 查找已有的第二行
        if (secondRow != null && secondRow.getParent() == actionsParent) {
            // 已存在，检查是否需要重新注入
            boolean alreadyHas = false;
            for (int i = 0; i < ((ViewGroup) secondRow).getChildCount(); i++) {
                View child = ((ViewGroup) secondRow).getChildAt(i);
                if (child.getTag() != null && "bubble_button".equals(child.getTag())) {
                    alreadyHas = true;
                    bubbleButton = child;
                    break;
                }
            }
            if (alreadyHas) {
                log(Log.INFO, TAG, "Second row already has bubble button");
                return;
            }
            // 添加按钮到已有的第二行
            ViewGroup.MarginLayoutParams btnMlp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            int spacingId = res.getIdentifier("overview_actions_button_spacing", "dimen", pkg);
            if (spacingId != 0) {
                btnMlp.setMarginStart(res.getDimensionPixelSize(spacingId));
            }
            btn.setLayoutParams(btnMlp);
            ((ViewGroup) secondRow).addView(btn);
            bubbleButton = btn;
            return;
        }

        // 创建新的第二行 LinearLayout（居中）
        LinearLayout newSecondRow = new LinearLayout(
                new android.view.ContextThemeWrapper(actionsParent.getContext(),
                        android.R.style.Theme_DeviceDefault));
        newSecondRow.setTag("bubble_second_row");
        newSecondRow.setOrientation(LinearLayout.HORIZONTAL);
        newSecondRow.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        // 按钮参数
        ViewGroup.MarginLayoutParams btnMlp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int spacingId = res.getIdentifier("overview_actions_button_spacing", "dimen", pkg);
        if (spacingId != 0) {
            btnMlp.setMarginStart(res.getDimensionPixelSize(spacingId));
        }
        btn.setLayoutParams(btnMlp);
        newSecondRow.addView(btn);
        bubbleButton = btn;

        // 关键：用 FrameLayout.LayoutParams 精确定位在 action_buttons 下方
        // 找到 action_buttons 的位置
        int actionButtonsId = res.getIdentifier("action_buttons", "id", pkg);
        View actionButtonsView = actionsParent.findViewById(actionButtonsId);

        FrameLayout.LayoutParams rowLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;

        // 在 FrameLayout 中，用 topMargin 把第二行推到 action_buttons 下方
        if (actionButtonsView != null) {
            actionButtonsView.post(() -> {
                int actionBottom = actionButtonsView.getBottom();
                // 加上 action_buttons 的 top margin（相对于 parent）
                int[] loc = new int[2];
                actionButtonsView.getLocationOnScreen(loc);
                int[] parentLoc = new int[2];
                actionsParent.getLocationOnScreen(parentLoc);
                int relativeBottom = loc[1] - parentLoc[1] + actionButtonsView.getHeight();

                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) newSecondRow.getLayoutParams();
                lp.topMargin = relativeBottom;
                newSecondRow.setLayoutParams(lp);
                log(Log.INFO, TAG, "Second row topMargin=" + relativeBottom);
            });
        }

        // 插入到 actionsParent 中，位于 action_buttons 之后
        int insertIndex = 0;
        if (actionButtonsView != null) {
            insertIndex = actionsParent.indexOfChild(actionButtonsView) + 1;
        }
        actionsParent.addView(newSecondRow, insertIndex, rowLp);
        secondRow = newSecondRow;

        // 用 post 确保布局完成后设置正确的 topMargin + 同步动画
        newSecondRow.post(() -> {
            if (actionButtonsView != null) {
                // 计算 action_buttons 底部相对于 parent 的位置
                int[] loc = new int[2];
                actionButtonsView.getLocationOnScreen(loc);
                int[] parentLoc = new int[2];
                actionsParent.getLocationOnScreen(parentLoc);
                int relativeBottom = loc[1] - parentLoc[1] + actionButtonsView.getHeight();

                // 加上额外间距（overview_actions_top_margin = 24dp）
                int topMarginId = res.getIdentifier("overview_actions_top_margin", "dimen", pkg);
                int extraSpacing = topMarginId != 0 ? res.getDimensionPixelSize(topMarginId) : 24;

                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) newSecondRow.getLayoutParams();
                lp.topMargin = relativeBottom + extraSpacing;
                newSecondRow.setLayoutParams(lp);
                log(Log.INFO, TAG, "Second row topMargin=" + (relativeBottom + extraSpacing));
            }

            // 关键：同步动画 — 在每帧绘制前将 action_buttons 的 alpha 复制到 secondRow
            actionsParent.getViewTreeObserver().addOnPreDrawListener(
                    new android.view.ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            float actionAlpha = actionButtonsView.getAlpha();
                            if (secondRow != null && secondRow.getAlpha() != actionAlpha) {
                                secondRow.setAlpha(actionAlpha);
                            }
                            return true;
                        }
                    });
            log(Log.INFO, TAG, "Added alpha sync listener");
        });
    }

    /**
     * 点击处理 — 触发消息气泡功能
     */
    private void onBubbleButtonClick(View actionsView) {
        Context ctx = actionsView.getContext();
        log(Log.INFO, TAG, "Bubble button clicked!");

        // 方案1: 通过 SystemUiProxy 的 showAppBubble 触发气泡
        if (triggerBubbleViaSystemUiProxy(ctx)) {
            return;
        }

        // 方案2: 通过 Shell Binder 直接调用
        if (triggerBubbleViaShell(ctx)) {
            return;
        }

        // 方案3: 打开气泡通知设置页面
        try {
            android.content.Intent intent = new android.content.Intent(
                    "android.settings.NOTIFICATION_SETTINGS");
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            log(Log.INFO, TAG, "Opened notification settings");
            return;
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Notification settings not available: " + t.getMessage());
        }

        // 兜底：显示说明对话框
        showBubbleDialog(ctx);
    }

    /**
     * 通过 SystemUiProxy.showAppBubble 触发气泡
     * 这是最正统的方式——复用启动器的 SystemUiProxy 单例
     */
    private boolean triggerBubbleViaSystemUiProxy(Context ctx) {
        try {
            // 方案A: 直接通过 Launcher Activity 获取 SystemUiProxy
            android.app.Activity activity = null;
            if (ctx instanceof android.app.Activity) {
                activity = (android.app.Activity) ctx;
            } else if (ctx instanceof android.view.ContextThemeWrapper) {
                android.content.Context base = ((android.view.ContextThemeWrapper) ctx).getBaseContext();
                while (base instanceof android.content.ContextWrapper) {
                    if (base instanceof android.app.Activity) {
                        activity = (android.app.Activity) base;
                        break;
                    }
                    base = ((android.content.ContextWrapper) base).getBaseContext();
                }
            }

            Object systemUiProxy = null;

            // 尝试从 Activity 获取
            if (activity != null) {
                try {
                    // QuickstepLauncher extends Launcher extends Activity
                    // QuickstepLauncher 有 mSystemUiProxy 字段
                    java.lang.reflect.Field proxyField = findField(activity.getClass(), "mSystemUiProxy");
                    if (proxyField != null) {
                        proxyField.setAccessible(true);
                        systemUiProxy = proxyField.get(activity);
                        log(Log.INFO, TAG, "Got SystemUiProxy from Activity field: " + systemUiProxy);
                    }
                } catch (Throwable t) {
                    log(Log.WARN, TAG, "Field access failed: " + t.getMessage());
                }
            }

            // 方案B: 通过 DaggerSingletonObject.INSTANCE.get()
            if (systemUiProxy == null) {
                try {
                    Class<?> proxyClass = mLauncherClassLoader.loadClass("com.android.quickstep.SystemUiProxy");
                    java.lang.reflect.Field instanceField = proxyClass.getDeclaredField("INSTANCE");
                    instanceField.setAccessible(true);
                    Object daggerSingleton = instanceField.get(null);
                    log(Log.INFO, TAG, "DaggerSingleton class: " + daggerSingleton.getClass().getName());

                    // 使用 Application context 而不是 Activity context
                    Context appCtx = ctx.getApplicationContext();
                    log(Log.INFO, TAG, "ApplicationContext: " + appCtx
                            + " class=" + appCtx.getClass().getName());

                    // 调用 DaggerSingletonObject.get(context)
                    java.lang.reflect.Method getMethod = daggerSingleton.getClass().getMethod("get", Context.class);
                    systemUiProxy = getMethod.invoke(daggerSingleton, appCtx);
                    log(Log.INFO, TAG, "Got SystemUiProxy from Dagger: " + systemUiProxy);
                } catch (Throwable t) {
                    log(Log.WARN, TAG, "Dagger access failed: " + t.getClass().getSimpleName()
                            + " - " + t.getMessage());
                    if (t.getCause() != null) {
                        log(Log.WARN, TAG, "  Caused by: " + t.getCause().getClass().getSimpleName()
                                + " - " + t.getCause().getMessage());
                    }
                }
            }

            if (systemUiProxy == null) {
                log(Log.WARN, TAG, "SystemUiProxy is null, all methods failed");
                return false;
            }

            // 构造 Intent
            android.content.Intent bubbleIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            bubbleIntent.setData(Uri.parse("smsto:"));
            bubbleIntent.setPackage("com.google.android.apps.messaging");

            // UserHandle
            Object userHandle = android.os.Process.class.getMethod("myUserHandle").invoke(null);

            // EntryPoint.NOTIFICATION — 在 $VALUES 数组中（不是静态字段）
            Class<?> entryPointClass = mLauncherClassLoader.loadClass("com.android.wm.shell.shared.bubbles.logging.EntryPoint");
            Object entryPoint = null;
            java.lang.reflect.Field valuesField = entryPointClass.getDeclaredField("$VALUES");
            valuesField.setAccessible(true);
            Object[] values = (Object[]) valuesField.get(null);
            for (Object ep : values) {
                if (ep.toString().equals("NOTIFICATION")) {
                    entryPoint = ep;
                    break;
                }
            }
            if (entryPoint == null) {
                log(Log.WARN, TAG, "EntryPoint.NOTIFICATION not found in $VALUES");
                return false;
            }
            log(Log.INFO, TAG, "Found EntryPoint.NOTIFICATION: " + entryPoint);

            // BubbleBarLocation.DEFAULT
            Class<?> locationClass = mLauncherClassLoader.loadClass("com.android.wm.shell.shared.bubbles.BubbleBarLocation");
            Object location = locationClass.getField("DEFAULT").get(null);

            // 调用 showAppBubble — 列出所有方法并匹配
            java.lang.reflect.Method showAppBubble = null;
            for (java.lang.reflect.Method m : systemUiProxy.getClass().getMethods()) {
                if (m.getName().equals("showAppBubble")) {
                    log(Log.INFO, TAG, "Found showAppBubble: " + m.toGenericString());
                    showAppBubble = m;
                    break;
                }
            }

            if (showAppBubble == null) {
                log(Log.WARN, TAG, "showAppBubble method not found on: " + systemUiProxy.getClass().getName());
                // 列出所有 bubble 相关方法
                for (java.lang.reflect.Method m : systemUiProxy.getClass().getMethods()) {
                    if (m.getName().toLowerCase().contains("bubble")) {
                        log(Log.INFO, TAG, "  bubble method: " + m.getName() + " params=" + java.util.Arrays.toString(m.getParameterTypes()));
                    }
                }
                return false;
            }

            log(Log.INFO, TAG, "Invoking showAppBubble with: intent=" + bubbleIntent
                    + " user=" + userHandle + " entry=" + entryPoint + " loc=" + location);
            showAppBubble.invoke(systemUiProxy, bubbleIntent, userHandle, entryPoint, location);
            log(Log.INFO, TAG, "showAppBubble called successfully!");
            showToast(ctx, "消息气泡已触发");
            return true;

        } catch (java.lang.reflect.InvocationTargetException ite) {
            log(Log.ERROR, TAG, "showAppBubble InvocationTarget: " + ite.getTargetException());
            ite.getTargetException().printStackTrace();
            return false;
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "triggerBubbleViaSystemUiProxy failed: " + t.getClass().getSimpleName()
                    + " - " + t.getMessage());
            t.printStackTrace();
            return false;
        }
    }

    /** 在类层次结构中查找字段 */
    private static java.lang.reflect.Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    /** 查找匹配参数类型的方法 */
    private static java.lang.reflect.Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 通过 Shell Binder 直接触发气泡
     */
    private boolean triggerBubbleViaShell(Context ctx) {
        try {
            Class<?> smClass = mLauncherClassLoader.loadClass("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "WindowManagerShell");
            if (binder == null) {
                log(Log.WARN, TAG, "WindowManagerShell not available");
                return false;
            }
            log(Log.INFO, TAG, "Got WindowManagerShell binder");

            android.content.Intent bubbleIntent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
            bubbleIntent.setData(Uri.parse("smsto:"));
            bubbleIntent.setPackage("com.google.android.apps.messaging");

            // UserHandle via Process.myUserHandle()
            Object userHandle = android.os.Process.class.getMethod("myUserHandle").invoke(null);

            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken("com.android.wm.shell.bubbles.IBubbles");
                data.writeTypedObject(bubbleIntent, 0);
                data.writeTypedObject((android.os.Parcelable) userHandle, 0);
                data.writeStrongBinder(null);
                data.writeStrongBinder(null);
                boolean result = binder.transact(14, data, null, android.os.IBinder.FLAG_ONEWAY);
                log(Log.INFO, TAG, "Shell showAppBubble transact result: " + result);
                if (result) {
                    showToast(ctx, "消息气泡已触发 (Shell)");
                }
                return result;
            } finally {
                data.recycle();
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "triggerBubbleViaShell failed: " + t.getMessage());
            return false;
        }
    }

    /**
     * 显示气泡功能说明对话框
     */
    private void showBubbleDialog(Context ctx) {
        new android.app.AlertDialog.Builder(ctx)
                .setTitle("消息气泡")
                .setMessage(
                    "消息气泡功能\n\n" +
                    "• 在屏幕底部角落显示消息气泡\n" +
                    "• 点击气泡展开消息预览\n" +
                    "• 支持拖拽排序和删除\n\n" +
                    "此模块已成功 Hook 到 Launcher\n" +
                    "请确保系统中已有支持气泡的应用（如 Google Messages）"
                )
                .setPositiveButton("确定", null)
                .show();
    }

    private static void showToast(Context ctx, String msg) {
        new android.os.Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
    }
}
