package com.floatwindow.morebubblebutton;

import android.content.Context;
import android.content.SharedPreferences;

public class ModuleSettings {
    private static final String PREFS_NAME = "morebubblebutton_settings";
    private static final String KEY_MENU_ENABLED = "menu_enabled";
    private static final String KEY_ACTION_BAR_ENABLED = "action_bar_enabled";
    private static final String KEY_POSITION_MODE = "position_mode"; // 0=跟随原按钮 1=第二行
    private static final String KEY_SECOND_ROW_GRAVITY = "second_row_gravity"; // 0=左 1=中 2=右

    private static SharedPreferences getPrefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isMenuEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean(KEY_MENU_ENABLED, true);
    }

    public static void setMenuEnabled(Context ctx, boolean enabled) {
        getPrefs(ctx).edit().putBoolean(KEY_MENU_ENABLED, enabled).apply();
    }

    public static boolean isActionBarEnabled(Context ctx) {
        return getPrefs(ctx).getBoolean(KEY_ACTION_BAR_ENABLED, true);
    }

    public static void setActionBarEnabled(Context ctx, boolean enabled) {
        getPrefs(ctx).edit().putBoolean(KEY_ACTION_BAR_ENABLED, enabled).apply();
    }

    /** 0=跟随原按钮(同一行)  1=第二行 */
    public static int getPositionMode(Context ctx) {
        return getPrefs(ctx).getInt(KEY_POSITION_MODE, 0);
    }

    public static void setPositionMode(Context ctx, int mode) {
        getPrefs(ctx).edit().putInt(KEY_POSITION_MODE, mode).apply();
    }

    /** 第二行位置：0=左 1=中 2=右（仅 position_mode=1 时生效） */
    public static int getSecondRowGravity(Context ctx) {
        return getPrefs(ctx).getInt(KEY_SECOND_ROW_GRAVITY, 1);
    }

    public static void setSecondRowGravity(Context ctx, int gravity) {
        getPrefs(ctx).edit().putInt(KEY_SECOND_ROW_GRAVITY, gravity).apply();
    }

    // 兼容旧接口
    public static int getBottomPosition(Context ctx) {
        return getPositionMode(ctx) == 1 ? getSecondRowGravity(ctx) : 0;
    }
}
