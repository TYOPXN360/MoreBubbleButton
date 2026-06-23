package com.floatwindow.morebubblebutton;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 设置对话框
 */
public class SettingsDialog {

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    public static void show(Context ctx, Runnable onDismiss) {
        // 每次调用都创建全新的 View，避免 parent 冲突
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

        // 3. 底部按钮位置
        TextView posLabel = new TextView(ctx);
        posLabel.setText("底部按钮位置");
        posLabel.setTextSize(16);
        posLabel.setTextColor(ctx.getColor(android.R.color.white));
        posLabel.setPadding(0, dp(ctx, 12), 0, dp(ctx, 4));
        layout.addView(posLabel);

        String[] positions = {"左侧", "居中", "右侧"};
        int cur = ModuleSettings.getBottomPosition(ctx);
        LinearLayout posRow = new LinearLayout(ctx);
        posRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView[] posBtns = new TextView[3];
        for (int i = 0; i < 3; i++) {
            final int p = i;
            TextView b = new TextView(ctx);
            b.setText(positions[i]);
            b.setTextSize(14);
            b.setPadding(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 8));
            b.setTextColor(ctx.getColor(i == cur ? android.R.color.holo_blue_light : android.R.color.white));
            b.setOnClickListener(v -> {
                ModuleSettings.setBottomPosition(ctx, p);
                for (int j = 0; j < 3; j++)
                    posBtns[j].setTextColor(ctx.getColor(j == p ?
                            android.R.color.holo_blue_light : android.R.color.white));
            });
            posBtns[i] = b;
            posRow.addView(b);
        }
        layout.addView(posRow);

        new AlertDialog.Builder(ctx)
                .setTitle("消息气泡设置")
                .setView(layout)
                .setPositiveButton("确定", (d, w) -> { if (onDismiss != null) onDismiss.run(); })
                .setOnDismissListener(d -> { if (onDismiss != null) onDismiss.run(); })
                .show();
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private static View createRow(Context ctx, String title, String subtitle,
            boolean checked, android.widget.CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(ctx, 8), 0, dp(ctx, 8));

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

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
