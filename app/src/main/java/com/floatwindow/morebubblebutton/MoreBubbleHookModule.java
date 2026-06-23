package com.floatwindow.morebubblebutton;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class MoreBubbleHookModule extends XposedModule {
    private static final String TAG = "MoreBubbleModule";
    private Object recentsViewInstance;
    private View bubbleButton;
    private View secondRow;
    private ClassLoader mLauncherClassLoader;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "MoreBubbleModule: " + param.getProcessName() + " | API " + getApiVersion());
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String pkg = param.getPackageName();
        if (!"com.google.android.apps.nexuslauncher".equals(pkg)
                && !"com.android.launcher3".equals(pkg)) return;
        mLauncherClassLoader = param.getDefaultClassLoader();
        hookLauncher(param);
    }

    private void hookLauncher(PackageLoadedParam param) {
        ClassLoader cl = mLauncherClassLoader;

        // Hook OverviewActionsView.onFinishInflate
        try {
            hook(cl.loadClass("com.android.quickstep.views.OverviewActionsView")
                    .getMethod("onFinishInflate")).intercept(chain -> {
                Object ret = chain.proceed();
                try {
                    Context ctx = ((View) chain.getThisObject()).getContext();
                    if (ModuleSettings.isActionBarEnabled(ctx))
                        injectBubbleButton(chain.getThisObject(), cl);
                } catch (Throwable t) { log(Log.ERROR, TAG, "inject failed", t); }
                return ret;
            });
        } catch (Throwable t) { log(Log.ERROR, TAG, "Hook onFinishInflate: " + t.getMessage()); }

        // Hook OverviewActionsView.onClick
        try {
            hook(cl.loadClass("com.android.quickstep.views.OverviewActionsView")
                    .getMethod("onClick", View.class)).intercept(chain -> {
                View v = (View) chain.getArg(0);
                if (v != null && bubbleButton != null && v.getId() == bubbleButton.getId()) {
                    onBubbleButtonClick((View) chain.getThisObject());
                    return null;
                }
                return chain.proceed();
            });
        } catch (Throwable t) { log(Log.ERROR, TAG, "Hook onClick: " + t.getMessage()); }

        // Hook TaskMenuView.addMenuOptions
        try {
            hook(cl.loadClass("com.android.quickstep.views.TaskMenuView")
                    .getDeclaredMethod("addMenuOptions")).intercept(chain -> {
                chain.proceed();
                try {
                    Context ctx = ((View) chain.getThisObject()).getContext();
                    if (ModuleSettings.isMenuEnabled(ctx))
                        addBubbleMenuOption(chain.getThisObject(), cl);
                } catch (Throwable t) { log(Log.ERROR, TAG, "addBubbleMenuOption: " + t.getMessage()); }
                return null;
            });
        } catch (Throwable t) { log(Log.ERROR, TAG, "Hook addMenuOptions: " + t.getMessage()); }

        // Hook LauncherSettingsFragment.onCreatePreferences — 注入设置
        try {
            hook(cl.loadClass("com.android.launcher3.settings.SettingsActivity$LauncherSettingsFragment")
                    .getMethod("onCreatePreferences", Bundle.class, String.class)).intercept(chain -> {
                chain.proceed();
                try { injectSettingsPreferences(chain.getThisObject(), cl); }
                catch (Throwable t) { log(Log.ERROR, TAG, "injectSettings: " + t.getMessage()); }
                return null;
            });
        } catch (Throwable t) { log(Log.ERROR, TAG, "Hook SettingsActivity: " + t.getMessage()); }
    }

    // ==================== 设置注入（参考 PLEnhanced LauncherSettings.kt） ====================

    private void injectSettingsPreferences(Object fragment, ClassLoader cl) {
        try {
            Object screen = findMethod(fragment.getClass(), "getPreferenceScreen").invoke(fragment);
            if (screen == null) return;
            Context ctx = (Context) findMethod(fragment.getClass(), "requireContext").invoke(fragment);
            Class<?> prefCls = cl.loadClass("androidx.preference.Preference");

            // 查找 OnPreferenceClickListener 接口
            Class<?> clickCls = null;
            String clickField = null;
            for (java.lang.reflect.Method m : prefCls.getMethods()) {
                if (m.getName().equals("setOnPreferenceClickListener") && m.getParameterCount() == 1) {
                    clickCls = m.getParameterTypes()[0]; break;
                }
            }
            if (clickCls == null) {
                for (java.lang.reflect.Field f : prefCls.getDeclaredFields()) {
                    if (f.getName().contains("OnClickListener") || f.getName().contains("clickListener")) {
                        clickCls = f.getType(); clickField = f.getName(); break;
                    }
                }
            }

            // 创建一个"消息气泡"Preference
            Object pref = prefCls.getDeclaredConstructor(Context.class, AttributeSet.class, int.class, int.class)
                    .newInstance(ctx, null, android.R.attr.preferenceStyle, 0);
            setKey(pref, "pref_more_bubble", prefCls);
            callM(pref, "setTitle", "消息气泡");
            callM(pref, "setSummary", "自定义消息气泡按钮的显示和位置");

            // 点击打开设置对话框
            if (clickCls != null) {
                Object listener = java.lang.reflect.Proxy.newProxyInstance(
                        clickCls.getClassLoader(), new Class[]{clickCls},
                        (p, m, a) -> {
                            if ("onPreferenceClick".equals(m.getName())) {
                                SettingsDialog.show(ctx, null);
                                return true;
                            }
                            return false;
                        });
                if (clickField != null) {
                    java.lang.reflect.Field f = prefCls.getDeclaredField(clickField);
                    f.setAccessible(true); f.set(pref, listener);
                } else {
                    prefCls.getMethod("setOnPreferenceClickListener", clickCls).invoke(pref, listener);
                }
            }

            addPref(screen, pref, cl);
            log(Log.INFO, TAG, "Settings entry injected");
        } catch (Throwable t) { log(Log.ERROR, TAG, "injectSettings: " + t.getMessage()); }
    }

    private Object newPref(Class<?> cls, Context ctx, String key, String title, String summary) {
        try {
            Object p = cls.getDeclaredConstructor(Context.class, AttributeSet.class, int.class, int.class)
                    .newInstance(ctx, null, android.R.attr.preferenceStyle, 0);
            setKey(p, key, cls);
            callM(p, "setTitle", title);
            if (summary != null) callM(p, "setSummary", summary);
            return p;
        } catch (Throwable t) { log(Log.WARN, TAG, "newPref: " + t.getMessage()); return null; }
    }

    private Object newSwitch(ClassLoader cl, Context ctx, String key, String title, String summary, boolean def) {
        try {
            // 使用 Preference 基类构造函数（已验证可用）
            Class<?> prefCls = cl.loadClass("androidx.preference.Preference");
            Object p = prefCls.getDeclaredConstructor(Context.class, AttributeSet.class, int.class, int.class)
                    .newInstance(ctx, null, android.R.attr.preferenceStyle, 0);
            setKey(p, key, prefCls);
            callM(p, "setTitle", title);
            callM(p, "setSummary", summary);
            // 设置 SwitchPreference 的 persistent 属性
            try { prefCls.getMethod("setPersistent", boolean.class).invoke(p, true); } catch (Throwable ignored) {}
            // 设置默认值
            try { prefCls.getMethod("setDefaultValue", Object.class).invoke(p, def); } catch (Throwable ignored) {}
            return p;
        } catch (Throwable t) { log(Log.WARN, TAG, "newSwitch: " + t.getMessage()); return null; }
    }

    private void setKey(Object p, String key, Class<?> cls) {
        try { cls.getMethod("setKey", String.class).invoke(p, key); }
        catch (Throwable t) { try { java.lang.reflect.Field f = cls.getDeclaredField("mKey");
            f.setAccessible(true); f.set(p, key); } catch (Throwable ignored) {} }
    }

    private void callM(Object p, String m, String arg) {
        try { p.getClass().getMethod(m, CharSequence.class).invoke(p, arg); }
        catch (Throwable ignored) {}
    }

    private void addPref(Object screen, Object pref, ClassLoader cl) {
        try { cl.loadClass("androidx.preference.PreferenceScreen")
                .getMethod("addPreference", cl.loadClass("androidx.preference.Preference"))
                .invoke(screen, pref); }
        catch (Throwable t) { log(Log.WARN, TAG, "addPref: " + t.getMessage()); }
    }

    private String getPosText(Context ctx) {
        int p = ModuleSettings.getBottomPosition(ctx);
        return p == 0 ? "左侧" : p == 2 ? "右侧" : "居中";
    }

    private void showPosDialog(Context ctx, Object posPref) {
        // 位置设置现在在 SettingsDialog 中处理
    }

    // ==================== 操作栏按钮 ====================

    @SuppressLint("DiscouragedApi")
    private void injectBubbleButton(Object actionsView, ClassLoader cl) {
        Context ctx = ((View) actionsView).getContext();
        android.content.res.Resources res = ctx.getResources();
        String pkg = ctx.getPackageName();
        ViewGroup actionsParent = (ViewGroup) actionsView;

        Button btn = createBubbleButton(ctx, res, pkg);
        int positionMode = ModuleSettings.getPositionMode(ctx);
        if (positionMode == 0) {
            addToActionButtons(actionsParent, btn, res, pkg);
        } else {
            ensureSecondRow(actionsParent, btn, res, pkg);
        }
    }

    /**
     * 模式0：跟随原按钮 — 直接加到 action_buttons 末尾
     */
    private void addToActionButtons(ViewGroup actionsParent, Button btn,
            android.content.res.Resources res, String pkg) {
        // 如果之前在第二行，先移除
        if (secondRow != null) {
            if (bubbleButton != null) ((ViewGroup) secondRow).removeView(bubbleButton);
            if (((ViewGroup) secondRow).getChildCount() == 0) {
                actionsParent.removeView(secondRow);
            }
            secondRow = null;
        }

        int abId = res.getIdentifier("action_buttons", "id", pkg);
        LinearLayout actionButtons = (LinearLayout) actionsParent.findViewById(abId);
        if (actionButtons == null) return;

        // 去重
        for (int i = 0; i < actionButtons.getChildCount(); i++) {
            View child = actionButtons.getChildAt(i);
            if (child.getTag() != null && "bubble_button".equals(child.getTag().toString())) {
                bubbleButton = child;
                return;
            }
        }

        // 添加到末尾
        ViewGroup.MarginLayoutParams mlp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int spId = res.getIdentifier("overview_actions_button_spacing", "dimen", pkg);
        if (spId != 0) mlp.setMarginStart(res.getDimensionPixelSize(spId));
        btn.setLayoutParams(mlp);
        actionButtons.addView(btn);
        bubbleButton = btn;

        // 捕获 RecentsView（从 actionsParent 的 parent 链查找）
        if (recentsViewInstance == null) {
            try {
                Class<?> rvCls = mLauncherClassLoader.loadClass(
                        "com.android.quickstep.views.RecentsView");
                View cur = actionsParent;
                while (cur != null) {
                    if (rvCls.isInstance(cur)) {
                        recentsViewInstance = cur;
                        log(Log.INFO, TAG, "Captured RecentsView in follow mode: " + cur);
                        break;
                    }
                    cur = (cur.getParent() instanceof View) ? (View) cur.getParent() : null;
                }
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Capture RecentsView failed: " + t.getMessage());
            }
        }

        log(Log.INFO, TAG, "Bubble button added to action_buttons (follow mode)");
    }

    private boolean isClearAllButton(View child) {
        try { return mLauncherClassLoader.loadClass("com.android.quickstep.views.ClearAllButton").isInstance(child); }
        catch (Throwable t) { return false; }
    }

    @SuppressLint("DiscouragedApi")
    private Button createBubbleButton(Context ctx, android.content.res.Resources res, String pkg) {
        int styleId = res.getIdentifier("OverviewActionButton.Blur", "style", pkg);
        if (styleId == 0) styleId = res.getIdentifier("OverviewActionButton", "style", pkg);
        Button btn = (styleId != 0) ? new Button(ctx, null, 0, styleId) : new Button(ctx);
        btn.setText("消息气泡");
        btn.setContentDescription("消息气泡");
        btn.setId(View.generateViewId());
        btn.setTag("bubble_button");

        int iconId = res.getIdentifier("ic_bubble_button", "drawable", pkg);
        if (iconId == 0) iconId = res.getIdentifier("ic_bubble_bar", "drawable", pkg);
        if (iconId != 0) {
            android.graphics.drawable.Drawable icon = res.getDrawable(iconId, null);
            if (icon != null) btn.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        }

        btn.setOnClickListener(v -> onBubbleButtonClick((View) btn.getParent().getParent()));
        btn.setOnLongClickListener(v -> {
            SettingsDialog.show(v.getContext(), () -> {
                if (!ModuleSettings.isActionBarEnabled(v.getContext()) && secondRow != null)
                    secondRow.setVisibility(View.GONE);
                else if (ModuleSettings.isActionBarEnabled(v.getContext()) && secondRow != null)
                    secondRow.setVisibility(View.VISIBLE);
            });
            return true;
        });
        return btn;
    }

    @SuppressLint("DiscouragedApi")
    private void ensureSecondRow(ViewGroup actionsParent, Button btn,
            android.content.res.Resources res, String pkg) {
        Context ctx = actionsParent.getContext();

        if (secondRow != null && secondRow.getParent() == actionsParent) {
            for (int i = 0; i < ((ViewGroup) secondRow).getChildCount(); i++) {
                if (((ViewGroup) secondRow).getChildAt(i).getTag() != null
                        && "bubble_button".equals(((ViewGroup) secondRow).getChildAt(i).getTag().toString())) {
                    bubbleButton = ((ViewGroup) secondRow).getChildAt(i);
                    return;
                }
            }
            ViewGroup.MarginLayoutParams mlp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int spId = res.getIdentifier("overview_actions_button_spacing", "dimen", pkg);
            if (spId != 0) mlp.setMarginStart(res.getDimensionPixelSize(spId));
            btn.setLayoutParams(mlp);
            ((ViewGroup) secondRow).addView(btn);
            bubbleButton = btn;
            return;
        }

        LinearLayout newSecondRow = new LinearLayout(ctx);
        newSecondRow.setTag("bubble_second_row");
        newSecondRow.setOrientation(LinearLayout.HORIZONTAL);

        // 使用 X/Y 坐标定位
        int posX = ModuleSettings.getPosX(ctx);
        int posY = ModuleSettings.getPosY(ctx);
        // posX: 0=左对齐 50=居中 100=右对齐
        if (posX < 33) {
            newSecondRow.setGravity(android.view.Gravity.START);
        } else if (posX > 66) {
            newSecondRow.setGravity(android.view.Gravity.END);
        } else {
            newSecondRow.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        }

        ViewGroup.MarginLayoutParams btnMlp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int spId = res.getIdentifier("overview_actions_button_spacing", "dimen", pkg);
        if (spId != 0) btnMlp.setMarginStart(res.getDimensionPixelSize(spId));
        btn.setLayoutParams(btnMlp);
        newSecondRow.addView(btn);
        bubbleButton = btn;

        int insertIndex = 0;
        int abId = res.getIdentifier("action_buttons", "id", pkg);
        View abv = actionsParent.findViewById(abId);
        if (abv != null) insertIndex = actionsParent.indexOfChild(abv) + 1;

        FrameLayout.LayoutParams rowLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParent.addView(newSecondRow, insertIndex, rowLp);
        secondRow = newSecondRow;

        newSecondRow.post(() -> {
            if (abv != null) {
                int[] loc = new int[2], pLoc = new int[2];
                abv.getLocationOnScreen(loc);
                actionsParent.getLocationOnScreen(pLoc);

                int abTop = loc[1] - pLoc[1];
                int btnH = newSecondRow.getHeight();
                int parentH = actionsParent.getHeight();

                // 获取设备参数
                android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                float density = dm.density;

                // 默认间距 = overview_actions_top_margin（约 24dp）
                int topId = res.getIdentifier("overview_actions_top_margin", "dimen", pkg);
                int defaultSpacing = topId != 0 ? res.getDimensionPixelSize(topId) : (int)(24 * density);

                // posY 0~100 映射：
                // 0%   = action_buttons 正上方
                // 50%  = action_buttons 正下方（默认间距）
                // 100% = parent 底部
                float yMin = Math.max(0, abTop - btnH);  // 上方
                float yMax = Math.max(0, parentH - btnH); // 底部
                float yDefault = abTop + abv.getHeight() + defaultSpacing; // 默认位置（下方间距）

                // 0~50% 映射到 [yMin, yDefault]
                // 50~100% 映射到 [yDefault, yMax]
                float targetY;
                if (posY <= 50) {
                    targetY = yMin + (posY / 50f) * (yDefault - yMin);
                } else {
                    targetY = yDefault + ((posY - 50) / 50f) * (yMax - yDefault);
                }
                targetY = Math.max(0, Math.min(targetY, yMax));

                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) newSecondRow.getLayoutParams();
                lp.topMargin = (int) targetY;
                newSecondRow.setLayoutParams(lp);
                log(Log.INFO, TAG, "Y=" + posY + " margin=" + (int) targetY
                        + " range=[" + (int) yMin + "," + (int) yMax + "]"
                        + " abTop=" + abTop + " abH=" + abv.getHeight()
                        + " btnH=" + btnH + " parentH=" + parentH
                        + " parentLoc=" + pLoc[1] + " abLoc=" + loc[1]
                        + " density=" + density);
            }
            actionsParent.getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override public boolean onPreDraw() {
                            if (secondRow != null && abv != null)
                                secondRow.setAlpha(abv.getAlpha());
                            return true;
                        }
                    });
        });
    }

    private void updateBubbleVisibility(Object av) {}

    // ==================== 菜单项注入 ====================

    @SuppressLint("DiscouragedApi")
    private void addBubbleMenuOption(Object menuView, ClassLoader cl) {
        try {
            Context ctx = ((View) menuView).getContext();
            android.content.res.Resources res = ctx.getResources();
            String pkg = ctx.getPackageName();

            ViewGroup optionLayout = (ViewGroup) findMethod(menuView.getClass(), "getOptionLayout").invoke(menuView);
            Object taskContainer = findMethod(menuView.getClass(), "getTaskContainer").invoke(menuView);
            if (optionLayout == null || taskContainer == null) return;

            // 捕获 RecentsView
            try {
                Object tv = findMethod(menuView.getClass(), "getTaskView").invoke(menuView);
                if (tv != null) {
                    recentsViewInstance = findMethod(tv.getClass(), "getRecentsView").invoke(tv);
                    log(Log.INFO, TAG, "Captured RecentsView: " + recentsViewInstance);
                }
            } catch (Throwable ignored) {}

            ViewGroup menuItem = (ViewGroup) android.view.LayoutInflater.from(ctx)
                    .inflate(res.getIdentifier("task_view_menu_option", "layout", pkg), optionLayout, false);

            int bgId = res.getIdentifier("app_chip_menu_item_bg", "drawable", pkg);
            if (bgId != 0) menuItem.setBackground(res.getDrawable(bgId, ctx.getTheme()));

            int iconId = res.getIdentifier("ic_bubble_button", "drawable", pkg);
            if (iconId == 0) iconId = res.getIdentifier("ic_bubble_bar", "drawable", pkg);
            View iconView = menuItem.findViewById(res.getIdentifier("icon", "id", pkg));
            if (iconView != null && iconId != 0) {
                android.graphics.drawable.Drawable icon = res.getDrawable(iconId, ctx.getTheme());
                int tintId = res.getIdentifier("materialColorOnSurface", "color", pkg);
                if (tintId != 0) icon.setTint(res.getColor(tintId, ctx.getTheme()));
                iconView.setBackground(icon);
            }

            View tv = menuItem.findViewById(res.getIdentifier("text", "id", pkg));
            if (tv instanceof android.widget.TextView)
                ((android.widget.TextView) tv).setText("消息气泡");

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) menuItem.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            menuItem.setLayoutParams(lp);

            menuItem.setOnClickListener(v -> {
                try {
                    Object task = invokeGetter(taskContainer, "getTask");
                    if (task == null) return;
                    Object key = getField(task, "key");
                    Intent intent = (Intent) getField(key, "baseIntent");
                    int userId = getField(key, "userId") != null ? (int) getField(key, "userId") : 0;
                    if (intent != null) {
                        findMethod(menuView.getClass(), "close", boolean.class).invoke(menuView, true);
                        bubbleCurrentTask(ctx, intent, userId);
                        new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> dismissOverview(ctx), 200);
                    }
                } catch (Throwable t) { log(Log.ERROR, TAG, "menu click: " + t.getMessage()); }
            });

            optionLayout.addView(menuItem);
        } catch (Throwable t) { log(Log.ERROR, TAG, "addBubbleMenuOption: " + t.getMessage()); }
    }

    // ==================== 气泡触发 ====================

    private void onBubbleButtonClick(View actionsView) {
        Context ctx = actionsView.getContext();
        Object rv = recentsViewInstance;
        if (rv == null) rv = findRecentsViewFromHierarchy(actionsView);
        if (rv == null) { log(Log.WARN, TAG, "RecentsView not found"); return; }

        try {
            Object tv = findMethod(rv.getClass(), "getCurrentPageTaskView").invoke(rv);
            if (tv == null) return;
            List<?> tc = (List<?>) findMethod(tv.getClass(), "getTaskContainers").invoke(tv);
            if (tc == null || tc.isEmpty()) return;

            Object task = findMethod(tc.get(0).getClass(), "getTask").invoke(tc.get(0));
            Object key = getField(task, "key");
            Intent intent = (Intent) getField(key, "baseIntent");
            int userId = getField(key, "userId") != null ? (int) getField(key, "userId") : 0;
            if (intent == null) return;

            bubbleCurrentTask(ctx, intent, userId);
            new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> dismissOverview(ctx), 200);
        } catch (Throwable t) { log(Log.ERROR, TAG, "onBubbleButtonClick: " + t.getMessage()); }
    }

    private boolean bubbleCurrentTask(Context ctx, Intent taskIntent, int userId) {
        try {
            Class<?> proxyCls = mLauncherClassLoader.loadClass("com.android.quickstep.SystemUiProxy");
            Object ds = proxyCls.getField("INSTANCE").get(null);
            Object proxy = ds.getClass().getMethod("get", Context.class).invoke(ds, ctx.getApplicationContext());
            if (proxy == null) return false;

            Intent bIntent = new Intent(taskIntent);
            if (bIntent.getPackage() == null && bIntent.getComponent() != null)
                bIntent.setPackage(bIntent.getComponent().getPackageName());

            Object userHandle = mLauncherClassLoader.loadClass("android.os.UserHandle")
                    .getMethod("of", int.class).invoke(null, userId);

            Class<?> epCls = mLauncherClassLoader.loadClass("com.android.wm.shell.shared.bubbles.logging.EntryPoint");
            Object ep = null;
            for (Object e : (Object[]) epCls.getDeclaredField("$VALUES").get(null))
                if ("NOTIFICATION".equals(e.toString())) { ep = e; break; }

            for (Method m : proxy.getClass().getMethods())
                if (m.getName().equals("showAppBubble")) {
                    m.invoke(proxy, bIntent, userHandle, ep, null);
                    log(Log.INFO, TAG, "showAppBubble OK");
                    return true;
                }
        } catch (Throwable t) { log(Log.ERROR, TAG, "bubbleCurrentTask: " + t.getMessage()); }
        return false;
    }

    private Object findRecentsViewFromHierarchy(View view) {
        try {
            Class<?> rvCls = mLauncherClassLoader.loadClass("com.android.quickstep.views.RecentsView");
            // 先从 parent 链查找
            View cur = view;
            while (cur != null) {
                if (rvCls.isInstance(cur)) { recentsViewInstance = cur; return cur; }
                cur = (cur.getParent() instanceof View) ? (View) cur.getParent() : null;
            }
            // parent 链找不到，从 rootView 递归搜索
            View root = view.getRootView();
            if (root != null) {
                cur = findInTree(root, rvCls);
                if (cur != null) { recentsViewInstance = cur; return cur; }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private View findInTree(View view, Class<?> cls) {
        if (cls.isInstance(view)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View f = findInTree(vg.getChildAt(i), cls);
                if (f != null) return f;
            }
        }
        return null;
    }

    private void dismissOverview(Context ctx) {
        try {
            if (recentsViewInstance != null) {
                Object sm = findMethod(recentsViewInstance.getClass(), "getStateManager").invoke(recentsViewInstance);
                if (sm != null) {
                    findMethod(sm.getClass(), "moveToRestState").invoke(sm);
                    log(Log.INFO, TAG, "dismissed via moveToRestState");
                    return;
                }
            }
            Runtime.getRuntime().exec(new String[]{"am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"});
            log(Log.INFO, TAG, "dismissed via am start HOME");
        } catch (Throwable t) { log(Log.ERROR, TAG, "dismiss: " + t.getMessage()); }
    }

    // ==================== 工具方法 ====================

    private static Object getField(Object obj, String name) {
        try { java.lang.reflect.Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true); return f.get(obj); }
        catch (Throwable t) { return null; }
    }

    private static Object invokeGetter(Object obj, String method) {
        try { Method m = findMethod(obj.getClass(), method); return m != null ? m.invoke(obj) : null; }
        catch (Throwable t) { return null; }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        while (clazz != null) {
            try { Method m = clazz.getDeclaredMethod(name, params); m.setAccessible(true); return m; }
            catch (NoSuchMethodException e) { clazz = clazz.getSuperclass(); }
        }
        return null;
    }

    private static void showToast(Context ctx, String msg) {
        new android.os.Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
    }
}
