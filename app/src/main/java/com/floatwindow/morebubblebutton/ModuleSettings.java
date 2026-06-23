package com.floatwindow.morebubblebutton;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 模块设置存储
 */
public class ModuleSettings {
    private static final String PREFS_NAME = "morebubblebutton_settings";
    private static final String KEY_MENU_ENABLED = "menu_enabled";
    private static final String KEY_ACTION_BAR_ENABLED = "action_bar_enabled";
    private static final String KEY_BOTTOM_POSITION = "bottom_position"; // 0=左 1=中 2=右

    private static SharedPreferences getPrefs(Context ctx) {
        // 使用应用私有目录，避免 MODE_WORLD_READABLE 问题
        Context appCtx = ctx.getApplicationContext();
        return appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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

    /**
     * 底部按钮位置：0=左 1=中(默认) 2=右
     */
    public static int getBottomPosition(Context ctx) {
        return getPrefs(ctx).getInt(KEY_BOTTOM_POSITION, 1);
    }

    public static void setBottomPosition(Context ctx, int position) {
        getPrefs(ctx).edit().putInt(KEY_BOTTOM_POSITION, position).apply();
    }
}
