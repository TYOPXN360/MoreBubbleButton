package com.floatwindow.morebubblebutton;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class SettingsDialog {

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    public static void show(Context ctx, Runnable onDismiss) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 20);
        layout.setPadding(pad, dp(ctx, 16), pad, dp(ctx, 8));

        // 1. 任务卡片菜单开关
        layout.addView(createRow(ctx, "任务卡片菜单",
                "在多任务界面点击 app 图标弹出的菜单中显示「消息气泡」",
                ModuleSettings.isMenuEnabled(ctx),
                (btn, checked) -> ModuleSettings.setMenuEnabled(ctx, checked)));

        // 2. 底部操作栏开关
        layout.addView(createRow(ctx, "底部操作栏",
                "在多任务界面底部显示「消息气泡」按钮",
                ModuleSettings.isActionBarEnabled(ctx),
                (btn, checked) -> ModuleSettings.setActionBarEnabled(ctx, checked)));

        // 3. 位置模式选择
        layout.addView(createSectionLabel(ctx, "按钮位置"));

        int currentMode = ModuleSettings.getPositionMode(ctx);
        String[] modes = {"跟随原按钮", "第二行"};
        LinearLayout modeRow = new LinearLayout(ctx);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        modeRow.setGravity(Gravity.CENTER);
        TextView[] modeBtns = new TextView[2];
        for (int i = 0; i < 2; i++) {
            final int m = i;
            TextView b = createModeBtn(ctx, modes[i], i == currentMode);
            modeRow.addView(b);
            modeBtns[i] = b;
        }
        layout.addView(modeRow);

        // 4. 第二行位置滑动条
        LinearLayout sliderContainer = new LinearLayout(ctx);
        sliderContainer.setOrientation(LinearLayout.VERTICAL);
        sliderContainer.setVisibility(currentMode == 1 ? View.VISIBLE : View.GONE);
        sliderContainer.setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 4));

        sliderContainer.addView(createSectionLabel(ctx, "位置：左 ← → 右"));

        TextView sliderValue = new TextView(ctx);
        sliderValue.setTextSize(12);
        sliderValue.setTextColor(0xAAFFFFFF);
        sliderValue.setPadding(dp(ctx, 8), 0, 0, dp(ctx, 4));
        sliderContainer.addView(sliderValue);

        SeekBar seekBar = new SeekBar(ctx);
        seekBar.setMax(100);
        int curGravity = ModuleSettings.getSecondRowGravity(ctx);
        seekBar.setProgress(gravityToProgress(curGravity));
        sliderValue.setText(gravityToText(curGravity));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    int g = progressToGravity(progress);
                    ModuleSettings.setSecondRowGravity(ctx, g);
                    sliderValue.setText(gravityToText(g));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        sliderContainer.addView(seekBar);
        layout.addView(sliderContainer);

        // 重启启动器按钮
        TextView restartBtn = new TextView(ctx);
        restartBtn.setText("重启启动器");
        restartBtn.setTextSize(15);
        restartBtn.setTextColor(ctx.getColor(android.R.color.holo_red_light));
        restartBtn.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 16), dp(ctx, 12));
        restartBtn.setGravity(Gravity.CENTER);
        // 只给重启按钮加背景框
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(ctx, 8));
        bg.setColor(0x22FFFFFF);
        bg.setStroke(dp(ctx, 1), 0x44FFFFFF);
        restartBtn.setBackground(bg);
        restartBtn.setOnClickListener(v -> {
            Context appCtx = v.getContext().getApplicationContext();
            new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    // 使用 Runtime.exec 调用 am 命令
                    Process p = Runtime.getRuntime().exec(new String[]{"/system/bin/am", "force-stop", "com.google.android.apps.nexuslauncher"});
                    p.waitFor();
                } catch (Throwable t) {
                    // 降级：直接杀进程
                    try {
                        android.os.Process.killProcess(android.os.Process.myPid());
                    } catch (Throwable ignored) {}
                }
            }, 300);
        });
        layout.addView(restartBtn);

        // 模式切换联动
        for (int i = 0; i < 2; i++) {
            final int m = i;
            modeBtns[i].setOnClickListener(v -> {
                ModuleSettings.setPositionMode(ctx, m);
                for (int j = 0; j < 2; j++)
                    modeBtns[j].setSelected(j == m);
                sliderContainer.setVisibility(m == 1 ? View.VISIBLE : View.GONE);
            });
        }

        new AlertDialog.Builder(ctx)
                .setTitle("消息气泡设置")
                .setView(layout)
                .setPositiveButton("确定", (d, w) -> { if (onDismiss != null) onDismiss.run(); })
                .setOnDismissListener(d -> { if (onDismiss != null) onDismiss.run(); })
                .show();
    }

    /** 给 View 添加圆角背景框 */
    private static void applyBackground(Context ctx, View view) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(ctx, 8));
        bg.setColor(0x22FFFFFF);
        bg.setStroke(dp(ctx, 1), 0x44FFFFFF);
        view.setBackground(bg);
    }

    private static TextView createSectionLabel(Context ctx, String text) {
        TextView label = new TextView(ctx);
        label.setText(text);
        label.setTextSize(14);
        label.setTextColor(0xAAFFFFFF);
        label.setPadding(0, dp(ctx, 10), 0, dp(ctx, 4));
        return label;
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private static View createRow(Context ctx, String title, String subtitle,
            boolean checked, android.widget.CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(ctx, 12), dp(ctx, 8), dp(ctx, 12), dp(ctx, 8));

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextSize(16);
        t.setTextColor(ctx.getColor(android.R.color.white));
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(ctx);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(listener);

        header.addView(t);
        header.addView(sw);

        TextView s = new TextView(ctx);
        s.setText(subtitle);
        s.setTextSize(12);
        s.setTextColor(0xAAFFFFFF);
        s.setPadding(0, dp(ctx, 2), 0, 0);

        row.addView(header);
        row.addView(s);
        row.setClickable(true);
        row.setOnClickListener(v -> sw.setChecked(!sw.isChecked()));
        return row;
    }

    private static TextView createModeBtn(Context ctx, String text, boolean selected) {
        TextView b = new TextView(ctx);
        b.setText(text);
        b.setTextSize(14);
        b.setPadding(dp(ctx, 20), dp(ctx, 10), dp(ctx, 20), dp(ctx, 10));
        b.setGravity(Gravity.CENTER);
        b.setSelected(selected);
        return b;
    }

    private static int progressToGravity(int progress) {
        if (progress < 33) return 0;
        if (progress < 66) return 1;
        return 2;
    }

    private static int gravityToProgress(int gravity) {
        if (gravity == 0) return 0;
        if (gravity == 2) return 100;
        return 50;
    }

    private static String gravityToText(int gravity) {
        if (gravity == 0) return "← 左侧";
        if (gravity == 2) return "右侧 →";
        return "居中";
    }

    private static int dp(Context ctx, int dp) {
        return (int) (dp * ctx.getResources().getDisplayMetrics().density);
    }
}
