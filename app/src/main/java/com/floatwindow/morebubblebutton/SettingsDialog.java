package com.floatwindow.morebubblebutton;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsDialog {

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    public static void show(Context ctx, Runnable onDismiss) {
        Log.i("MoreBubbleModule", "SettingsDialog.show called, onDismiss=" + (onDismiss != null ? "non-null" : "null"));
        Toast.makeText(ctx, "打开消息气泡设置", Toast.LENGTH_SHORT).show();
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

        // 4. 第二行位置滑动条（X/Y 双轴微调）
        LinearLayout sliderContainer = new LinearLayout(ctx);
        sliderContainer.setOrientation(LinearLayout.VERTICAL);
        sliderContainer.setVisibility(currentMode == 1 ? View.VISIBLE : View.GONE);
        sliderContainer.setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 4));

        int curX = ModuleSettings.getPosX(ctx);
        int curY = ModuleSettings.getPosY(ctx);

        // X 轴
        sliderContainer.addView(createSliderRow(ctx, "X 轴（← 左 | 右 →）",
                curX, (progress) -> {
                    ModuleSettings.setPosX(ctx, progress);
                }));

        // Y 轴
        sliderContainer.addView(createSliderRow(ctx, "Y 轴（↑ 上 | 下 ↓）",
                curY, (progress) -> {
                    ModuleSettings.setPosY(ctx, progress);
                }));

        layout.addView(sliderContainer);

        // 5. 恢复默认按钮
        TextView resetBtn = new TextView(ctx);
        resetBtn.setText("重置位置");
        resetBtn.setTextSize(14);
        resetBtn.setTextColor(0xAAFFFFFF);
        resetBtn.setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10));
        resetBtn.setGravity(Gravity.CENTER);
        resetBtn.setOnClickListener(v -> {
            ModuleSettings.setPosX(ctx, 50);
            ModuleSettings.setPosY(ctx, 50);
            try { ((android.app.AlertDialog) ((View) v.getParent()).getParent().getParent()).dismiss(); } catch (Throwable ignored) {}
            show(ctx, onDismiss);
        });
        layout.addView(resetBtn);

        // 6. 重启启动器按钮
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
            new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    // 获取启动器进程 PID 并杀掉，系统会自动重启
                    android.app.ActivityManager am = (android.app.ActivityManager)
                            v.getContext().getSystemService(Context.ACTIVITY_SERVICE);
                    // 获取当前进程信息
                    android.app.ActivityManager.RunningAppProcessInfo myProc = null;
                    for (android.app.ActivityManager.RunningAppProcessInfo proc : am.getRunningAppProcesses()) {
                        if (proc.processName.contains("nexuslauncher")) {
                            myProc = proc;
                            break;
                        }
                    }
                    if (myProc != null) {
                        android.os.Process.killProcess(myProc.pid);
                    }
                } catch (Throwable ignored) {}
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
                .setPositiveButton("确定", (d, w) -> {
                    Log.i("MoreBubbleModule", "Dialog positive button clicked");
                    MoreBubbleHookModule.applyPositionFromSettings(ctx);
                    if (onDismiss != null) onDismiss.run();
                })
                .setOnDismissListener(d -> {
                    Log.i("MoreBubbleModule", "Dialog dismissed");
                    MoreBubbleHookModule.applyPositionFromSettings(ctx);
                    if (onDismiss != null) onDismiss.run();
                })
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

    private static View createSliderRow(Context ctx, String label, int curValue,
            java.util.function.IntConsumer onChange) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6));

        // 标题行
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView lbl = new TextView(ctx);
        lbl.setText(label);
        lbl.setTextSize(13);
        lbl.setTextColor(0xCCFFFFFF);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView val = new TextView(ctx);
        val.setTextSize(12);
        val.setTextColor(0xAAFFFFFF);
        val.setText(curValue + "%");

        header.addView(lbl);
        header.addView(val);
        row.addView(header);

        // [-] 滑动条 [+] 行
        LinearLayout sliderRow = new LinearLayout(ctx);
        sliderRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView minusBtn = new TextView(ctx);
        minusBtn.setText("−");
        minusBtn.setTextSize(20);
        minusBtn.setTextColor(ctx.getColor(android.R.color.white));
        minusBtn.setPadding(dp(ctx, 12), dp(ctx, 4), dp(ctx, 12), dp(ctx, 4));
        minusBtn.setGravity(Gravity.CENTER);

        SeekBar bar = new SeekBar(ctx);
        bar.setMax(100);
        bar.setProgress(curValue);
        bar.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView plusBtn = new TextView(ctx);
        plusBtn.setText("+");
        plusBtn.setTextSize(20);
        plusBtn.setTextColor(ctx.getColor(android.R.color.white));
        plusBtn.setPadding(dp(ctx, 12), dp(ctx, 4), dp(ctx, 12), dp(ctx, 4));
        plusBtn.setGravity(Gravity.CENTER);

        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) { onChange.accept(progress); val.setText(progress + "%"); }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        minusBtn.setOnClickListener(v -> {
            int next = Math.max(0, bar.getProgress() - 1);
            bar.setProgress(next); onChange.accept(next); val.setText(next + "%");
        });
        plusBtn.setOnClickListener(v -> {
            int next = Math.min(100, bar.getProgress() + 1);
            bar.setProgress(next); onChange.accept(next); val.setText(next + "%");
        });

        sliderRow.addView(minusBtn);
        sliderRow.addView(bar);
        sliderRow.addView(plusBtn);
        row.addView(sliderRow);
        return row;
    }

    private static int dp(Context ctx, int dp) {
        return (int) (dp * ctx.getResources().getDisplayMetrics().density);
    }
}
