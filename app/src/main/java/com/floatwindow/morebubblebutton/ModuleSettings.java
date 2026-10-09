package com.floatwindow.morebubblebutton;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

public class ModuleSettings {
    public static final String PREFS_NAME = "morebubblebutton_settings";
    public static final String KEY_MENU_ENABLED = "menu_enabled";
    public static final String KEY_ACTION_BAR_ENABLED = "action_bar_enabled";
    public static final String KEY_POSITION_MODE = "position_mode"; // 0=跟随原按钮 1=第二行
    public static final String KEY_POS_X = "pos_x"; // 0-100, 50=居中
    public static final String KEY_POS_Y = "pos_y"; // 0-100, 50=居中
    public static final String KEY_SYSTEMUI_BUBBLE_ENABLED = "systemui_bubble_enabled";
    public static final String KEY_OPEN_MODE = "open_mode"; // 0=Bubble 1=Freeform
    public static final String KEY_POPUP_PRESENTATION = "popup_presentation"; // 0=按钮 1=下滑横条
    public static final String KEY_SWIPE_HANDLE_LENGTH = "swipe_handle_length_dp";
    public static final String KEY_SWIPE_HANDLE_THICKNESS = "swipe_handle_thickness_dp";
    public static final String KEY_SWIPE_HANDLE_BOTTOM_MARGIN = "swipe_handle_bottom_margin_dp";

    public static final int OPEN_MODE_BUBBLE = 0;
    public static final int OPEN_MODE_FREEFORM = 1;
    public static final int POPUP_PRESENTATION_BUBBLE_BUTTON = 0;
    public static final int POPUP_PRESENTATION_SWIPE_HANDLE = 1;
    public static final int DEFAULT_SWIPE_HANDLE_LENGTH_DP = 56;
    public static final int DEFAULT_SWIPE_HANDLE_THICKNESS_DP = 7;
    public static final int DEFAULT_SWIPE_HANDLE_BOTTOM_MARGIN_DP = 3;
    private static final Uri SETTINGS_URI = SettingsProvider.CONTENT_URI;
    private static final long REMOTE_CACHE_MS = 250L;
    private static volatile long sRemoteCacheAt;
    private static volatile Map<String, String> sRemoteCache;

    private static SharedPreferences getPrefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static boolean isModuleContext(Context ctx) {
        return "com.floatwindow.morebubblebutton".equals(ctx.getApplicationContext().getPackageName());
    }

    private static Map<String, String> getRemoteSettings(Context ctx) {
        if (isModuleContext(ctx)) return null;
        long now = android.os.SystemClock.uptimeMillis();
        Map<String, String> cached = sRemoteCache;
        if (cached != null && now - sRemoteCacheAt < REMOTE_CACHE_MS) return cached;
        Cursor cursor = null;
        try {
            cursor = ctx.getContentResolver().query(SETTINGS_URI, null, null, null, null);
            if (cursor == null) return cached;
            Map<String, String> map = new HashMap<>();
            int keyIndex = cursor.getColumnIndex("key");
            int valueIndex = cursor.getColumnIndex("value");
            while (cursor.moveToNext()) {
                map.put(cursor.getString(keyIndex), cursor.getString(valueIndex));
            }
            sRemoteCache = map;
            sRemoteCacheAt = now;
            return map;
        } catch (Throwable ignored) {
            return cached;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public static void invalidateRemoteCache() {
        sRemoteCacheAt = 0;
        sRemoteCache = null;
    }

    private static boolean getBoolean(Context ctx, String key, boolean defValue) {
        Map<String, String> remote = getRemoteSettings(ctx);
        if (remote != null && remote.containsKey(key)) return "1".equals(remote.get(key));
        return getPrefs(ctx).getBoolean(key, defValue);
    }

    private static int getInt(Context ctx, String key, int defValue) {
        Map<String, String> remote = getRemoteSettings(ctx);
        if (remote != null && remote.containsKey(key)) {
            try { return Integer.parseInt(remote.get(key)); } catch (Throwable ignored) {}
        }
        return getPrefs(ctx).getInt(key, defValue);
    }

    public static boolean isMenuEnabled(Context ctx) {
        return getBoolean(ctx, KEY_MENU_ENABLED, true);
    }
    public static void setMenuEnabled(Context ctx, boolean v) {
        getPrefs(ctx).edit().putBoolean(KEY_MENU_ENABLED, v).apply();
    }

    public static boolean isActionBarEnabled(Context ctx) {
        return getBoolean(ctx, KEY_ACTION_BAR_ENABLED, true);
    }
    public static void setActionBarEnabled(Context ctx, boolean v) {
        getPrefs(ctx).edit().putBoolean(KEY_ACTION_BAR_ENABLED, v).apply();
    }

    /** 0=跟随原按钮  1=第二行 */
    public static int getPositionMode(Context ctx) {
        return getInt(ctx, KEY_POSITION_MODE, 0);
    }
    public static void setPositionMode(Context ctx, int v) {
        getPrefs(ctx).edit().putInt(KEY_POSITION_MODE, v).apply();
    }

    /** X 轴位置 0-100，50=居中（已含图标偏移补偿） */
    public static int getPosX(Context ctx) {
        return getInt(ctx, KEY_POS_X, 50);
    }
    public static void setPosX(Context ctx, int v) {
        getPrefs(ctx).edit().putInt(KEY_POS_X, clampPercent(v)).apply();
    }

    /** Y 轴位置 0-100，50=居中 */
    public static int getPosY(Context ctx) {
        return getInt(ctx, KEY_POS_Y, 50);
    }
    public static void setPosY(Context ctx, int v) {
        getPrefs(ctx).edit().putInt(KEY_POS_Y, clampPercent(v)).apply();
    }

    /** 通知横幅气泡按钮开关 */
    public static boolean isSystemUiBubbleEnabled(Context ctx) {
        return getBoolean(ctx, KEY_SYSTEMUI_BUBBLE_ENABLED, true);
    }
    public static void setSystemUiBubbleEnabled(Context ctx, boolean v) {
        getPrefs(ctx).edit().putBoolean(KEY_SYSTEMUI_BUBBLE_ENABLED, v).apply();
    }

    /** 打开方式：0=Bubble，1=系统 Freeform 窗口。 */
    public static int getOpenMode(Context ctx) {
        int value = getInt(ctx, KEY_OPEN_MODE, OPEN_MODE_BUBBLE);
        return value == OPEN_MODE_FREEFORM ? OPEN_MODE_FREEFORM : OPEN_MODE_BUBBLE;
    }
    public static void setOpenMode(Context ctx, int value) {
        getPrefs(ctx).edit().putInt(KEY_OPEN_MODE,
                value == OPEN_MODE_FREEFORM ? OPEN_MODE_FREEFORM : OPEN_MODE_BUBBLE).apply();
    }

    /** Heads-up 中显示原生 Bubble 按钮，或显示可下滑的横条。 */
    public static int getPopupPresentation(Context ctx) {
        int value = getInt(ctx, KEY_POPUP_PRESENTATION, POPUP_PRESENTATION_BUBBLE_BUTTON);
        return value == POPUP_PRESENTATION_SWIPE_HANDLE
                ? POPUP_PRESENTATION_SWIPE_HANDLE : POPUP_PRESENTATION_BUBBLE_BUTTON;
    }
    public static void setPopupPresentation(Context ctx, int value) {
        getPrefs(ctx).edit().putInt(KEY_POPUP_PRESENTATION,
                value == POPUP_PRESENTATION_SWIPE_HANDLE
                        ? POPUP_PRESENTATION_SWIPE_HANDLE : POPUP_PRESENTATION_BUBBLE_BUTTON).apply();
    }

    public static int getSwipeHandleLength(Context ctx) {
        return clamp(getInt(ctx, KEY_SWIPE_HANDLE_LENGTH,
                DEFAULT_SWIPE_HANDLE_LENGTH_DP), 32, 96);
    }

    public static void setSwipeHandleLength(Context ctx, int value) {
        getPrefs(ctx).edit().putInt(KEY_SWIPE_HANDLE_LENGTH,
                clamp(value, 32, 96)).apply();
    }

    public static int getSwipeHandleThickness(Context ctx) {
        return clamp(getInt(ctx, KEY_SWIPE_HANDLE_THICKNESS,
                DEFAULT_SWIPE_HANDLE_THICKNESS_DP), 3, 14);
    }

    public static void setSwipeHandleThickness(Context ctx, int value) {
        getPrefs(ctx).edit().putInt(KEY_SWIPE_HANDLE_THICKNESS,
                clamp(value, 3, 14)).apply();
    }

    public static int getSwipeHandleBottomMargin(Context ctx) {
        return clamp(getInt(ctx, KEY_SWIPE_HANDLE_BOTTOM_MARGIN,
                DEFAULT_SWIPE_HANDLE_BOTTOM_MARGIN_DP), 0, 32);
    }

    public static void setSwipeHandleBottomMargin(Context ctx, int value) {
        getPrefs(ctx).edit().putInt(KEY_SWIPE_HANDLE_BOTTOM_MARGIN,
                clamp(value, 0, 32)).apply();
    }

    private static int clampPercent(int v) {
        return Math.max(0, Math.min(100, v));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // 兼容旧接口
    public static int getBottomPosition(Context ctx) {
        return getPositionMode(ctx) == 1 ? 1 : 0;
    }
}
