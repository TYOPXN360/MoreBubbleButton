package com.floatwindow.morebubblebutton;

import android.content.Context;
import android.content.SharedPreferences;

public class ModuleSettings {
    private static final String PREFS_NAME = "morebubblebutton_settings";
    private static final String KEY_MENU_ENABLED = "menu_enabled";
    private static final String KEY_ACTION_BAR_ENABLED = "action_bar_enabled";
    private static final String KEY_POSITION_MODE = "position_mode"; // 0=跟随原按钮 1=第二行
    private static final String KEY_POS_X = "pos_x"; // 0-100, 50=居中
    private static final String KEY_POS_Y = "pos_y"; // 0-100, 50=居中

    private static SharedPreferences getPrefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isMenuEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean(KEY_MENU_ENABLED, true);
    }
    public static void setMenuEnabled(Context ctx, boolean v) {
        getPrefs(ctx).edit().putBoolean(KEY_MENU_ENABLED, v).apply();
    }

    public static boolean isActionBarEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean(KEY_ACTION_BAR_ENABLED, true);
    }
    public static void setActionBarEnabled(Context ctx, boolean v) {
        getPrefs(ctx).edit().putBoolean(KEY_ACTION_BAR_ENABLED, v).apply();
    }

    /** 0=跟随原按钮  1=第二行 */
    public static int getPositionMode(Context ctx) {
        return getPrefs(ctx).getInt(KEY_POSITION_MODE, 0);
    }
    public static void setPositionMode(Context ctx, int v) {
        getPrefs(ctx).edit().putInt(KEY_POSITION_MODE, v).apply();
    }

    /** X 轴位置 0-100，50=居中（已含图标偏移补偿） */
    public static int getPosX(Context ctx) {
        return getPrefs(ctx).getInt(KEY_POS_X, 50);
    }
    public static void setPosX(Context ctx, int v) {
        getPrefs(ctx).edit().putInt(KEY_POS_X, v).apply();
    }

    /** Y 轴位置 0-100，50=居中 */
    public static int getPosY(Context ctx) {
        return getPrefs(ctx).getInt(KEY_POS_Y, 50);
    }
    public static void setPosY(Context ctx, int v) {
        getPrefs(ctx).edit().putInt(KEY_POS_Y, v).apply();
    }

    // 兼容旧接口
    public static int getBottomPosition(Context ctx) {
        return getPositionMode(ctx) == 1 ? 1 : 0;
    }
}
