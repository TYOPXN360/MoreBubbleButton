package com.floatwindow.bubblemodule;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

/**
 * BubbleHookModule — 在 Pixel Launcher 最近任务界面添加"消息气泡"按钮
 *
 * 参考 PLenhanced 的 ClearAllButton.kt 实现模式：
 * - Hook OverviewActionsView.onFinishInflate (基类，不是 NexusOverviewActionsView)
 * - 通过反射获取 mActionButtons 字段
 * - 使用 Launcher 的 OverviewActionButton 样式
 */
public class BubbleHookModule extends XposedModule {
    private static final String TAG = "BubbleModule";

    private Object recentsViewInstance;
    private View bubbleButton;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "onModuleLoaded: " + param.getProcessName()
                + " | API " + getApiVersion());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String pkg = param.getPackageName();
        log(Log.INFO, TAG, "onPackageLoaded: " + pkg);

        // Hook Launcher 进程
        if ("com.google.android.apps.nexuslauncher".equals(pkg)
                || "com.android.launcher3".equals(pkg)) {
            hookLauncher(param);
        }
    }

    private void hookLauncher(PackageLoadedParam param) {
        ClassLoader cl = param.getDefaultClassLoader();
        log(Log.INFO, TAG, "ClassLoader: " + cl);

        // 1. Hook RecentsView 构造函数，保存实例
        try {
            Class<?> recentsViewClass = cl.loadClass(
                    "com.android.quickstep.views.RecentsView");
            hook(recentsViewClass.getConstructor(Context.class)).intercept(chain -> {
                Object ret = chain.proceed();
                recentsViewInstance = chain.getThisObject();
                log(Log.INFO, TAG, "RecentsView instance captured");
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
            log(Log.INFO, TAG, "Loaded OverviewActionsView: " + overviewActionsClass);

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

        // 3. Hook OverviewActionsView.onClick — 拦截点击
        try {
            Class<?> overviewActionsClass = cl.loadClass(
                    "com.android.quickstep.views.OverviewActionsView");
            hook(overviewActionsClass.getMethod("onClick", View.class)).intercept(chain -> {
                View v = (View) chain.getArg(0);
                if (v != null && bubbleButton != null && v.getId() == bubbleButton.getId()) {
                    log(Log.INFO, TAG, ">>> Bubble button clicked!");
                    onBubbleButtonClick((View) chain.getThisObject());
                    return null; // 消费事件
                }
                return chain.proceed();
            });
            log(Log.INFO, TAG, "Hooked OverviewActionsView.onClick OK");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook onClick", t);
        }
    }

    /**
     * 注入"消息气泡"按钮 — 参考 ClearAllButton.kt 的实现
     */
    @SuppressLint("DiscouragedApi")
    private void injectBubbleButton(Object actionsView, ClassLoader cl) {
        Context ctx = ((View) actionsView).getContext();
        Context launcherCtx = ctx;
        android.content.res.Resources launcherRes = ctx.getResources();
        String launcherPkg = ctx.getPackageName();

        log(Log.INFO, TAG, "injectBubbleButton: context=" + launcherCtx
                + ", pkg=" + launcherPkg);

        // 获取 mActionButtons (LinearLayout) — 反射获取
        LinearLayout mActionButtons = null;
        try {
            // 方法1: 通过字段名反射
            java.lang.reflect.Field field = actionsView.getClass().getDeclaredField("mActionButtons");
            field.setAccessible(true);
            mActionButtons = (LinearLayout) field.get(actionsView);
            log(Log.INFO, TAG, "Got mActionButtons via field: " + mActionButtons);
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Field access failed: " + t.getMessage());
        }

        // 方法2: 通过 findViewById
        if (mActionButtons == null) {
            int abId = launcherRes.getIdentifier("action_buttons", "id", launcherPkg);
            log(Log.INFO, TAG, "action_buttons id: " + abId);
            if (abId != 0) {
                mActionButtons = ((View) actionsView).findViewById(abId);
                log(Log.INFO, TAG, "Got mActionButtons via findViewById: " + mActionButtons);
            }
        }

        // 方法3: 遍历 ViewGroup 查找 LinearLayout
        if (mActionButtons == null && actionsView instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) actionsView;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof LinearLayout) {
                    mActionButtons = (LinearLayout) child;
                    log(Log.INFO, TAG, "Found LinearLayout child: " + mActionButtons
                            + " children=" + mActionButtons.getChildCount());
                    break;
                }
            }
        }

        if (mActionButtons == null) {
            log(Log.ERROR, TAG, "All methods failed to find mActionButtons!");
            // 最后手段：打印整个 view hierarchy
            printViewHierarchy((View) actionsView, "");
            return;
        }

        // 创建按钮 — 使用 Launcher 的 ContextThemeWrapper 和 OverviewActionButton 样式
        int themeId = launcherRes.getIdentifier(
                "ThemeControlHighlightWorkspaceColor", "style", launcherPkg);
        int styleId = launcherRes.getIdentifier(
                "OverviewActionButton", "style", launcherPkg);

        log(Log.INFO, TAG, "themeId=" + themeId + " styleId=" + styleId);

        android.view.ContextThemeWrapper contextThemeWrapper =
                new android.view.ContextThemeWrapper(launcherCtx,
                        themeId != 0 ? themeId : android.R.style.Theme_DeviceDefault);

        Button btn;
        if (styleId != 0) {
            btn = new Button(contextThemeWrapper, null, 0, styleId);
        } else {
            btn = new Button(contextThemeWrapper);
        }

        btn.setText("消息气泡");
        btn.setContentDescription("消息气泡");

        // 设置图标
        int iconId = launcherRes.getIdentifier("ic_bubble_button", "drawable", launcherPkg);
        if (iconId == 0) iconId = launcherRes.getIdentifier("ic_bubble_bar", "drawable", launcherPkg);
        if (iconId == 0) iconId = launcherRes.getIdentifier("bubble_ic_overflow_button", "drawable", launcherPkg);
        log(Log.INFO, TAG, "iconId=" + iconId);
        if (iconId != 0) {
            android.graphics.drawable.Drawable icon = launcherRes.getDrawable(iconId, null);
            if (icon != null) {
                btn.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
                log(Log.INFO, TAG, "Set icon from resource id=" + iconId);
            }
        }

        // LayoutParams — 使用 launcher 的 spacing
        int spacingId = launcherRes.getIdentifier(
                "overview_actions_button_spacing", "dimen", launcherPkg);
        btn.setId(View.generateViewId());
        ViewGroup.MarginLayoutParams mlp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (spacingId != 0) {
            mlp.setMarginStart(launcherRes.getDimensionPixelSize(spacingId));
        }
        btn.setLayoutParams(mlp);

        // 复制已有按钮的 background
        for (int i = 0; i < mActionButtons.getChildCount(); i++) {
            View child = mActionButtons.getChildAt(i);
            if (child instanceof Button && child != bubbleButton) {
                android.graphics.drawable.Drawable bg = child.getBackground();
                if (bg != null) {
                    btn.setBackground(bg.getConstantState().newDrawable());
                    log(Log.INFO, TAG, "Copied background from existing button");
                }
                break;
            }
        }

        // 点击事件
        btn.setOnClickListener(v -> onBubbleButtonClick((View) actionsView));

        // 添加到 mActionButtons — 先检查是否已注入
        boolean alreadyInjected = false;
        for (int i = 0; i < mActionButtons.getChildCount(); i++) {
            View child = mActionButtons.getChildAt(i);
            if (child != null && child.getTag() != null
                    && "bubble_button".equals(child.getTag())) {
                alreadyInjected = true;
                log(Log.INFO, TAG, "Bubble button already exists, skipping");
                bubbleButton = child;
                break;
            }
        }

        if (!alreadyInjected) {
            btn.setTag("bubble_button");
            mActionButtons.addView(btn);
            bubbleButton = btn;
        }

        log(Log.INFO, TAG, "=== Bubble button ready! id=" + bubbleButton.getId()
                + " children=" + mActionButtons.getChildCount() + " ===");
    }

    /**
     * 点击处理
     */
    private void onBubbleButtonClick(View actionsView) {
        Context ctx = actionsView.getContext();
        log(Log.INFO, TAG, "Bubble button clicked!");

        // 1. 尝试通过 Shell Binder 触发
        if (triggerBubbleViaShell()) {
            showToast(ctx, "气泡栏已触发 (Shell)");
            return;
        }

        // 2. 尝试启动气泡设置
        try {
            android.content.Intent intent = new android.content.Intent(
                    "android.settings.BUBBLE_NOTIFICATION_SETTINGS");
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            log(Log.INFO, TAG, "Launched bubble settings");
            return;
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Bubble settings not available: " + t.getMessage());
        }

        // 3. 显示对话框
        new android.app.AlertDialog.Builder(ctx)
                .setTitle("消息气泡")
                .setMessage(
                    "消息气泡功能面板\n\n" +
                    "• 在屏幕底部角落显示消息气泡\n" +
                    "• 点击气泡展开消息预览\n" +
                    "• 支持拖拽排序和删除\n\n" +
                    "此模块已成功 Hook 到 Launcher\n" +
                    "最近任务界面操作栏已添加\"消息气泡\"按钮"
                )
                .setPositiveButton("确定", null)
                .show();
    }

    private boolean triggerBubbleViaShell() {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "WindowManagerShell");
            if (binder == null) {
                log(Log.WARN, TAG, "WindowManagerShell not available");
                return false;
            }
            log(Log.INFO, TAG, "Got WindowManagerShell binder");

            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("android.window_shell");
                boolean result = binder.transact(1, data, reply, 0);
                log(Log.INFO, TAG, "Shell transact result: " + result);
                return result;
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Shell trigger failed: " + t.getMessage());
            return false;
        }
    }

    private static void showToast(Context ctx, String msg) {
        new android.os.Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
    }

    /** 打印 View 层级结构（调试用） */
    private void printViewHierarchy(View view, String indent) {
        String info = indent + view.getClass().getSimpleName()
                + " id=" + Integer.toHexString(view.getId())
                + " vis=" + view.getVisibility();
        if (view instanceof ViewGroup) {
            info += " children=" + ((ViewGroup) view).getChildCount();
        }
        if (view instanceof Button) {
            info += " text=" + ((Button) view).getText();
        }
        log(Log.INFO, TAG, info);

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                printViewHierarchy(vg.getChildAt(i), indent + "  ");
            }
        }
    }
}
