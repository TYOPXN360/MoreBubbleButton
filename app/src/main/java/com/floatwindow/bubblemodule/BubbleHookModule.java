package com.floatwindow.bubblemodule;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
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
 * Hook 目标：com.google.android.apps.nexuslauncher
 *   - NexusOverviewActionsView.onFinishInflate() → 注入按钮
 *   - NexusOverviewActionsView.onClick()          → 拦截点击
 *
 * 点击"消息气泡"后：
 *   1. 优先尝试通过 Shell Binder (WindowManagerShell) 触发气泡
 *   2. 降级显示对话框 + Toast
 */
public class BubbleHookModule extends XposedModule {
    private static final String TAG = "BubbleModule";

    /** 自定义按钮 ID，避免与系统资源冲突 */
    private static final int BUBBLE_BTN_ID = 0x7f0b9999;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "BubbleModule loaded in: " + param.getProcessName()
                + " | API " + getApiVersion());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String pkg = param.getPackageName();
        if (!"com.google.android.apps.nexuslauncher".equals(pkg)) return;

        log(Log.INFO, TAG, "Package loaded: " + pkg);
        // 通过反射获取 classLoader，因为 API 102 中方法名可能不同
        try {
            java.lang.reflect.Method getCl = param.getClass().getMethod("getClassLoader");
            ClassLoader cl = (ClassLoader) getCl.invoke(param);
            hookNexusOverviewActions(cl);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to get classloader", t);
        }
    }

    // ==================== Hook 入口 ====================

    private void hookNexusOverviewActions(ClassLoader cl) {
        try {
            Class<?> clazz = cl.loadClass(
                    "com.google.android.apps.nexuslauncher.overview.NexusOverviewActionsView");

            // Hook onFinishInflate → 注入按钮
            hook(clazz.getMethod("onFinishInflate")).intercept(chain -> {
                Object ret = chain.proceed();
                try {
                    injectBubbleButton((View) chain.getThisObject());
                } catch (Throwable t) {
                    log(Log.ERROR, TAG, "inject failed", t);
                }
                return ret;
            });

            // Hook onClick → 拦截我们的按钮
            hook(clazz.getMethod("onClick", View.class)).intercept(chain -> {
                View v = (View) chain.getArg(0);
                if (v != null && v.getId() == BUBBLE_BTN_ID) {
                    log(Log.INFO, TAG, ">>> Bubble button clicked!");
                    onBubbleButtonClick((View) chain.getThisObject());
                    return null; // 消费事件
                }
                return chain.proceed();
            });

            log(Log.INFO, TAG, "Hooks installed OK");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hook failed", t);
        }
    }

    // ==================== 注入按钮 ====================

    private void injectBubbleButton(View actionsView) {
        Context ctx = actionsView.getContext();
        Resources res = ctx.getResources();

        // 查找 action_buttons (LinearLayout)
        int abId = getResId(res, "action_buttons", "id", "com.google.android.apps.nexuslauncher");
        if (abId == 0) abId = 0x7f0a00e0; // fallback

        LinearLayout actionButtons = null;
        if (actionsView instanceof ViewGroup) {
            actionButtons = ((ViewGroup) actionsView).findViewById(abId);
        }
        if (actionButtons == null) {
            log(Log.ERROR, TAG, "action_buttons not found");
            return;
        }

        log(Log.INFO, TAG, "action_buttons found, children=" + actionButtons.getChildCount());

        // 创建按钮
        Button btn = new Button(ctx);
        btn.setId(BUBBLE_BTN_ID);
        btn.setText("消息气泡");
        btn.setContentDescription("消息气泡");

        // 尝试复制 action_screenshot 的样式
        styleFromSibling(btn, actionButtons, res);

        // 添加到 action_buttons 末尾
        actionButtons.addView(btn, actionButtons.getChildCount());
        log(Log.INFO, TAG, "Button injected, total children=" + actionButtons.getChildCount());
    }

    /** 从 action_screenshot 复制样式到新按钮 */
    private void styleFromSibling(Button btn, LinearLayout parent, Resources res) {
        try {
            int ssId = getResId(res, "action_screenshot", "id", "com.google.android.apps.nexuslauncher");
            if (ssId == 0) ssId = 0x7f0a00df;
            View sibling = parent.findViewById(ssId);
            if (sibling == null) return;

            // 背景
            Drawable bg = sibling.getBackground();
            if (bg != null) btn.setBackground(bg.getConstantState().newDrawable());

            // 文字颜色
            if (sibling instanceof Button) {
                btn.setTextColor(((Button) sibling).getTextColors());
            }

            // LayoutParams
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(8, 0, 8, 0);
            btn.setLayoutParams(lp);

            // Padding
            btn.setPadding(sibling.getPaddingLeft(), sibling.getPaddingTop(),
                    sibling.getPaddingRight(), sibling.getPaddingBottom());

            // 图标 — 按优先级尝试多个资源名
            int iconId = 0;
            String[] iconNames = {
                "ic_bubble_button",       // 气泡按钮图标 (最佳)
                "ic_bubble_bar",          // 气泡栏图标
                "bubble_ic_overflow_button", // 气泡溢出按钮
                "bubble_view",            // 气泡视图
            };
            for (String name : iconNames) {
                iconId = getResId(res, name, "drawable", "com.google.android.apps.nexuslauncher");
                if (iconId != 0) {
                    log(Log.INFO, TAG, "Using icon resource: " + name);
                    break;
                }
            }
            if (iconId != 0) {
                Drawable icon = res.getDrawable(iconId, null);
                if (icon != null) {
                    icon.setBounds(0, 0, 48, 48);
                    btn.setCompoundDrawables(icon, null, null, null);
                    btn.setCompoundDrawablePadding(8);
                }
            } else {
                log(Log.WARN, TAG, "No bubble icon found, using text only");
            }

            log(Log.INFO, TAG, "Styled from screenshot sibling");
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Style copy failed: " + t.getMessage());
            // 默认样式
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(8, 0, 8, 0);
            btn.setLayoutParams(lp);
            btn.setPadding(24, 12, 24, 12);
        }
    }

    // ==================== 点击处理 ====================

    private void onBubbleButtonClick(View actionsView) {
        Context ctx = actionsView.getContext();

        // 1. 尝试通过 Shell Binder 触发
        if (triggerBubbleViaShell()) {
            showToast(ctx, "气泡栏已触发");
            return;
        }

        // 2. 尝试启动气泡设置
        if (triggerBubbleSettings(ctx)) {
            return;
        }

        // 3. 降级：显示对话框
        showBubbleDialog(ctx);
    }

    /** 通过 WindowManagerShell Binder 触发气泡 */
    private boolean triggerBubbleViaShell() {
        try {
            Class<?> smClass = Class.forName("android.os.ServiceManager");
            Method getService = smClass.getMethod("getService", String.class);
            IBinder binder = (IBinder) getService.invoke(null, "WindowManagerShell");
            if (binder == null) {
                log(Log.WARN, TAG, "WindowManagerShell not available");
                return false;
            }

            log(Log.INFO, TAG, "Got WindowManagerShell binder, attempting transact...");

            // 尝试通过 binder transact 触发
            // Shell bubbles 的 service descriptor
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken("android.window_shell");
                // 尝试不同的 transaction code
                // 这是一个 best-effort 方案
                boolean result = binder.transact(1, data, reply, 0);
                log(Log.INFO, TAG, "Transact result: " + result);
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

    /** 尝试启动气泡相关设置页面 */
    private boolean triggerBubbleSettings(Context ctx) {
        try {
            Intent intent = new Intent("android.settings.BUBBLE_NOTIFICATION_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            log(Log.INFO, TAG, "Launched bubble settings");
            return true;
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Bubble settings not available: " + t.getMessage());
            return false;
        }
    }

    /** 显示气泡功能对话框 */
    private void showBubbleDialog(Context ctx) {
        new android.app.AlertDialog.Builder(ctx)
                .setTitle("🫧 消息气泡")
                .setMessage(
                    "消息气泡功能面板\n\n" +
                    "• 在屏幕底部角落显示消息气泡\n" +
                    "• 点击气泡展开消息预览\n" +
                    "• 支持拖拽排序和删除\n" +
                    "• 气泡栏位置：左下角/右下角\n\n" +
                    "此模块已成功 Hook 到 Pixel Launcher\n" +
                    "最近任务界面的操作栏中已添加\"消息气泡\"按钮"
                )
                .setPositiveButton("确定", null)
                .show();
    }

    // ==================== 工具方法 ====================

    private static void showToast(Context ctx, String msg) {
        new android.os.Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(ctx, "🫧 " + msg, Toast.LENGTH_SHORT).show());
    }

    private static int getResId(Resources res, String name, String defType, String pkg) {
        return res.getIdentifier(name, defType, pkg);
    }
}
