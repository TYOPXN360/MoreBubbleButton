package com.floatwindow.morebubblebutton;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * 设置对话框 — 复用启动器主题样式
 */
public class SettingsDialog {

    /**
     * 显示设置对话框
     */
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    public static void show(Context ctx, Runnable onDismiss) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dpToPx(ctx, 20);
        layout.setPadding(padding, dpToPx(ctx, 16), padding, dpToPx(ctx, 8));

        // 1. 任务卡片菜单开关
        Switch menuSwitch = createSwitch(ctx, "任务卡片菜单",
                "在多任务界面点击 app 图标弹出的菜单中显示「消息气泡」",
                ModuleSettings.isMenuEnabled(ctx));
        menuSwitch.setOnCheckedChangeListener((btn, checked) ->
                ModuleSettings.setMenuEnabled(ctx, checked));

        // 2. 底部操作栏开关
        Switch actionBarSwitch = createSwitch(ctx, "底部操作栏",
                "在多任务界面底部显示「消息气泡」按钮",
                ModuleSettings.isActionBarEnabled(ctx));
        actionBarSwitch.setOnCheckedChangeListener((btn, checked) ->
                ModuleSettings.setActionBarEnabled(ctx, checked));

        // 3. 底部按钮位置选择
        TextView positionLabel = new TextView(ctx);
        positionLabel.setText("底部按钮位置");
        positionLabel.setTextSize(16);
        positionLabel.setTextColor(ctx.getColor(android.R.color.white));
        positionLabel.setPadding(0, dpToPx(ctx, 12), 0, dpToPx(ctx, 4));

        String[] positions = {"左侧", "居中", "右侧"};
        int currentPos = ModuleSettings.getBottomPosition(ctx);
        TextView[] positionButtons = new TextView[3];
        LinearLayout positionRow = new LinearLayout(ctx);
        positionRow.setOrientation(LinearLayout.HORIZONTAL);

        for (int i = 0; i < 3; i++) {
            final int pos = i;
            TextView btn = new TextView(ctx);
            btn.setText(positions[i]);
            btn.setTextSize(14);
            btn.setPadding(dpToPx(ctx, 16), dpToPx(ctx, 8), dpToPx(ctx, 16), dpToPx(ctx, 8));
            btn.setTextColor(ctx.getColor(i == currentPos ?
                    android.R.color.holo_blue_light : android.R.color.white));
            btn.setOnClickListener(v -> {
                ModuleSettings.setBottomPosition(ctx, pos);
                for (int j = 0; j < 3; j++) {
                    positionButtons[j].setTextColor(ctx.getColor(j == pos ?
                            android.R.color.holo_blue_light : android.R.color.white));
                }
            });
            positionButtons[i] = btn;
            positionRow.addView(btn);
        }

        layout.addView(menuSwitch);
        layout.addView(actionBarSwitch);
        layout.addView(positionLabel);
        layout.addView(positionRow);

        new AlertDialog.Builder(ctx)
                .setTitle("消息气泡设置")
                .setView(layout)
                .setPositiveButton("确定", (d, w) -> {
                    if (onDismiss != null) onDismiss.run();
                })
                .setOnDismissListener(d -> {
                    if (onDismiss != null) onDismiss.run();
                })
                .show();
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private static Switch createSwitch(Context ctx, String title, String subtitle, boolean checked) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dpToPx(ctx, 8), 0, dpToPx(ctx, 8));

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(ctx.getColor(android.R.color.white));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleView.setLayoutParams(titleLp);

        Switch switchView = new Switch(ctx);
        switchView.setChecked(checked);
        switchView.setTextOn("开");
        switchView.setTextOff("关");

        header.addView(titleView);
        header.addView(switchView);

        TextView subtitleView = new TextView(ctx);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(12);
        subtitleView.setTextColor(0xAAFFFFFF);
        subtitleView.setPadding(0, dpToPx(ctx, 2), 0, 0);

        row.addView(header);
        row.addView(subtitleView);

        // 包装一层，让 Switch 的点击能传递到整个 row
        LinearLayout wrapper = new LinearLayout(ctx);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row);
        wrapper.setOnClickListener(v -> switchView.setChecked(!switchView.isChecked()));

        // 返回一个虚拟 Switch，实际操作 real Switch
        Switch result = new Switch(ctx);
        // 用一个技巧：把 real switch 的引用存到 wrapper 的 tag 里
        wrapper.setTag(switchView);

        // 创建一个代理 Switch
        Switch proxy = new Switch(ctx) {
            @Override
            public void setOnCheckedChangeListener(OnCheckedChangeListener l) {
                switchView.setOnCheckedChangeListener(l);
            }
            @Override
            public void setChecked(boolean checked) {
                switchView.setChecked(checked);
            }
            @Override
            public boolean isChecked() {
                return switchView.isChecked();
            }
        };
        proxy.setChecked(checked);

        // 用 wrapper 替代 proxy 返回
        // 但接口要求返回 View，所以用一个技巧
        // 实际上直接返回 wrapper，然后在外面处理

        // 简化方案：直接返回 row，Switch 独立工作
        return switchView;
    }

    /**
     * 创建带标题和开关的设置项
     */
    public static View createSettingItem(Context ctx, String title, String subtitle,
                                         boolean checked, android.widget.CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dpToPx(ctx, 8), 0, dpToPx(ctx, 8));

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextSize(16);
        titleView.setTextColor(ctx.getColor(android.R.color.white));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleView.setLayoutParams(titleLp);

        @SuppressLint("UseSwitchCompatOrMaterialCode")
        Switch switchView = new Switch(ctx);
        switchView.setChecked(checked);
        switchView.setOnCheckedChangeListener(listener);

        header.addView(titleView);
        header.addView(switchView);

        TextView subtitleView = new TextView(ctx);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(12);
        subtitleView.setTextColor(0xAAFFFFFF);
        subtitleView.setPadding(0, dpToPx(ctx, 2), 0, 0);

        row.addView(header);
        row.addView(subtitleView);

        // 点击整行切换开关
        row.setClickable(true);
        row.setOnClickListener(v -> switchView.setChecked(!switchView.isChecked()));

        return row;
    }

    private static int dpToPx(Context ctx, int dp) {
        return (int) (dp * ctx.getResources().getDisplayMetrics().density);
    }
}
