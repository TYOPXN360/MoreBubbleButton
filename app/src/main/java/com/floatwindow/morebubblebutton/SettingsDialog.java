package com.floatwindow.morebubblebutton;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
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
        TextView modeLabel = new TextView(ctx);
        modeLabel.setText("按钮位置");
        modeLabel.setTextSize(16);
        modeLabel.setTextColor(ctx.getColor(android.R.color.white));
        modeLabel.setPadding(0, dp(ctx, 12), 0, dp(ctx, 4));
        layout.addView(modeLabel);

        int currentMode = ModuleSettings.getPositionMode(ctx);

        // 模式选项：跟随原按钮 / 第二行
        String[] modes = {"跟随原按钮", "第二行"};
        LinearLayout modeRow = new LinearLayout(ctx);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView[] modeBtns = new TextView[2];
        for (int i = 0; i < 2; i++) {
            final int m = i;
            TextView b = new TextView(ctx);
            b.setText(modes[i]);
            b.setTextSize(14);
            b.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8));
            b.setTextColor(ctx.getColor(i == currentMode ? android.R.color.holo_blue_light : android.R.color.white));
            modeRow.addView(b);
            modeBtns[i] = b;
        }
        layout.addView(modeRow);

        // 4. 第二行位置滑动条（仅第二行模式下显示）
        LinearLayout sliderContainer = new LinearLayout(ctx);
        sliderContainer.setOrientation(LinearLayout.VERTICAL);
        sliderContainer.setVisibility(currentMode == 1 ? View.VISIBLE : View.GONE);

        TextView sliderLabel = new TextView(ctx);
        sliderLabel.setText("位置：左 ← → 右");
        sliderLabel.setTextSize(14);
        sliderLabel.setTextColor(ctx.getColor(android.R.color.white));
        sliderLabel.setPadding(0, dp(ctx, 8), 0, dp(ctx, 4));
        sliderContainer.addView(sliderLabel);

        TextView sliderValue = new TextView(ctx);
        sliderValue.setTextSize(12);
        sliderValue.setTextColor(0xAAFFFFFF);
        sliderContainer.addView(sliderValue);

        SeekBar seekBar = new SeekBar(ctx);
        seekBar.setMax(100);
        int curGravity = ModuleSettings.getSecondRowGravity(ctx);
        seekBar.setProgress(gravityToProgress(curGravity));
        sliderValue.setText(gravityToText(ctx, curGravity));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    ModuleSettings.setSecondRowGravity(ctx, progressToGravity(progress));
                    sliderValue.setText(gravityToText(ctx, progressToGravity(progress)));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        sliderContainer.addView(seekBar);
        layout.addView(sliderContainer);

        // 设置模式切换联动
        for (int i = 0; i < 2; i++) {
            final int m = i;
            modeBtns[i].setOnClickListener(v -> {
                ModuleSettings.setPositionMode(ctx, m);
                for (int j = 0; j < 2; j++)
                    modeBtns[j].setTextColor(ctx.getColor(j == m ?
                            android.R.color.holo_blue_light : android.R.color.white));
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

    /** progress 0-100 → gravity: 0=左 1=中 2=右 */
    private static int progressToProgress(int progress) {
        if (progress < 33) return 0;
        if (progress < 66) return 1;
        return 2;
    }

    private static int progressToGravity(int progress) {
        if (progress < 33) return 0; // 左
        if (progress < 66) return 1; // 中
        return 2; // 右
    }

    private static int gravityToProgress(int gravity) {
        if (gravity == 0) return 0;
        if (gravity == 2) return 100;
        return 50;
    }

    private static String gravityToText(Context ctx, int gravity) {
        if (gravity == 0) return "← 左侧";
        if (gravity == 2) return "右侧 →";
        return "居中";
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private static View createRow(Context ctx, String title, String subtitle,
            boolean checked, android.widget.CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(ctx, 8), 0, dp(ctx, 8));

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

    private static int dp(Context ctx, int dp) {
        return (int) (dp * ctx.getResources().getDisplayMetrics().density);
    }
}
