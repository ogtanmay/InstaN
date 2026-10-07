package ps.reso.instaeclipse.mods.ui;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.Window;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Liquid Glass Navigation Bar Hook
 *
 * Transforms Instagram's bottom navigation bar into a translucent, frosted liquid glass
 * floating dock with specular rim highlights, optical curvature sheen, and elevation.
 */
public class LiquidGlassNavBarHook {

    private static final Set<Activity> watchedActivities =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final WeakHashMap<View, OriginalNavBarState> sOriginalStates =
            new WeakHashMap<>();

    private static class OriginalNavBarState {
        Drawable background;
        int leftMargin;
        int rightMargin;
        int topMargin;
        int bottomMargin;
        float elevation;
        boolean clipToOutline;
        ViewOutlineProvider outlineProvider;
    }

    public void install(ClassLoader classLoader) {
        try {
            FeatureStatusTracker.setHooked("LiquidGlassNavBar");
            ModuleLog.line("(InstaEclipse | LiquidGlass): Hook initialized");
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | LiquidGlass): install error: " + t.getMessage());
        }
    }

    public static void watchActivity(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        String name = activity.getClass().getName();
        if (!name.contains("InstagramMainActivity") && !name.contains("MainActivity")) return;

        activity.runOnUiThread(() -> {
            try {
                applyOrRestore(activity);

                if (!watchedActivities.contains(activity)) {
                    watchedActivities.add(activity);
                    final View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
                    if (decor != null) {
                        decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                if (activity.isFinishing()) {
                                    decor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                                    return;
                                }
                                applyOrRestore(activity);
                            }
                        });
                    }
                }
            } catch (Throwable t) {
                ModuleLog.line("(InstaEclipse | LiquidGlass): watchActivity error: " + t.getMessage());
            }
        });
    }

    public static void refreshCurrentActivity() {
        Activity act = UIHookManager.getCurrentActivity();
        if (act != null) {
            act.runOnUiThread(() -> applyOrRestore(act));
        }
    }

    private static void applyOrRestore(Activity activity) {
        View navBar = findBottomNavBar(activity);
        if (navBar == null) return;

        if (FeatureFlags.enableLiquidGlassNavBar) {
            applyLiquidGlass(activity, navBar);
        } else {
            restoreOriginal(activity, navBar);
        }
    }

    private static View findBottomNavBar(Activity activity) {
        Resources res = activity.getResources();
        String pkg = activity.getPackageName();

        // 1. Direct standard IDs
        String[] possibleBarIds = {
                "tab_bar", "tab_bar_container", "bottom_navigation_bar",
                "main_tab_bar", "igds_tab_bar", "clips_tab_bar_background"
        };
        for (String idName : possibleBarIds) {
            int id = res.getIdentifier(idName, "id", pkg);
            if (id != 0) {
                View v = activity.findViewById(id);
                if (v != null && v.getVisibility() == View.VISIBLE) {
                    return v;
                }
            }
        }

        // 2. Discover via child tab icons (search_tab, clips_tab, feed_tab, profile_tab)
        String[] tabIds = {"search_tab", "clips_tab", "feed_tab", "profile_tab", "news_tab", "direct_tab"};
        for (String tabIdName : tabIds) {
            int id = res.getIdentifier(tabIdName, "id", pkg);
            if (id != 0) {
                View tabView = activity.findViewById(id);
                if (tabView != null && tabView.getParent() instanceof ViewGroup) {
                    ViewGroup tabRow = (ViewGroup) tabView.getParent();
                    // If tabRow is contained inside a dedicated container wrapper at the bottom:
                    if (tabRow.getParent() instanceof ViewGroup) {
                        ViewGroup parentContainer = (ViewGroup) tabRow.getParent();
                        int height = parentContainer.getHeight();
                        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
                        int[] loc = new int[2];
                        parentContainer.getLocationOnScreen(loc);
                        if (loc[1] + height >= screenHeight - dpToPx(activity, 40) && height <= dpToPx(activity, 110) && height >= dpToPx(activity, 40)) {
                            return parentContainer;
                        }
                    }
                    return tabRow;
                }
            }
        }

        // 3. Fallback: Search view tree for a ViewGroup anchored to the bottom
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decor instanceof ViewGroup) {
            return scanForBottomBar((ViewGroup) decor, activity);
        }

        return null;
    }

    private static View scanForBottomBar(ViewGroup root, Activity activity) {
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int minBottom = screenHeight - dpToPx(activity, 30);
        int minH = dpToPx(activity, 44);
        int maxH = dpToPx(activity, 96);

        java.util.ArrayDeque<ViewGroup> queue = new java.util.ArrayDeque<>();
        queue.push(root);

        View bestCandidate = null;
        while (!queue.isEmpty()) {
            ViewGroup parent = queue.pop();
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) continue;
                int h = child.getHeight();
                int[] loc = new int[2];
                child.getLocationOnScreen(loc);
                int bottom = loc[1] + h;

                if (bottom >= minBottom && h >= minH && h <= maxH && child instanceof ViewGroup vg) {
                    if (vg.getChildCount() >= 3 && vg.getChildCount() <= 6) {
                        bestCandidate = child;
                    }
                }
                if (child instanceof ViewGroup vg) {
                    queue.push(vg);
                }
            }
        }
        return bestCandidate;
    }

    private static void applyLiquidGlass(Activity activity, View navBar) {
        Window window = activity.getWindow();
        if (window != null) {
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) {
                window.setNavigationBarContrastEnforced(false);
            }
        }

        // Save original layout & background state if not saved yet
        if (!sOriginalStates.containsKey(navBar)) {
            OriginalNavBarState state = new OriginalNavBarState();
            state.background = navBar.getBackground();
            ViewGroup.LayoutParams lp = navBar.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams mlp) {
                state.leftMargin = mlp.leftMargin;
                state.rightMargin = mlp.rightMargin;
                state.topMargin = mlp.topMargin;
                state.bottomMargin = mlp.bottomMargin;
            }
            state.elevation = navBar.getElevation();
            state.clipToOutline = navBar.getClipToOutline();
            state.outlineProvider = navBar.getOutlineProvider();
            sOriginalStates.put(navBar, state);
        }

        // 1. Hide hairline dividers/shadows
        hideDividers(activity, navBar);

        // 2. Set Liquid Glass Drawable
        Drawable currentBg = navBar.getBackground();
        if (!(currentBg instanceof LiquidGlassDrawable)) {
            LiquidGlassDrawable glass = new LiquidGlassDrawable(
                    activity,
                    FeatureFlags.liquidGlassStyle,
                    FeatureFlags.liquidGlassBorderSheen
            );
            navBar.setBackground(glass);
        } else {
            ((LiquidGlassDrawable) currentBg).updateStyle(
                    FeatureFlags.liquidGlassStyle,
                    FeatureFlags.liquidGlassBorderSheen
            );
        }

        // 3. Clear any solid backgrounds on child tabs so glass shines through
        if (navBar instanceof ViewGroup vg) {
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child.getBackground() != null && !(child.getBackground() instanceof LiquidGlassDrawable)) {
                    child.setBackground(new ColorDrawable(Color.TRANSPARENT));
                }
            }
        }

        // 4. Style margins and outline according to selected glass style
        boolean isFloating = (FeatureFlags.liquidGlassStyle != LiquidGlassDrawable.STYLE_DOCKED);
        ViewGroup.LayoutParams lp = navBar.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams mlp) {
            int targetHMargin = isFloating ? dpToPx(activity, 14) : 0;
            int targetBMargin = isFloating ? dpToPx(activity, 10) : 0;
            if (mlp.leftMargin != targetHMargin || mlp.rightMargin != targetHMargin || mlp.bottomMargin != targetBMargin) {
                mlp.leftMargin = targetHMargin;
                mlp.rightMargin = targetHMargin;
                mlp.bottomMargin = targetBMargin;
                navBar.setLayoutParams(mlp);
            }
        }

        float targetElevation = isFloating ? dpToPx(activity, 12) : dpToPx(activity, 6);
        navBar.setElevation(targetElevation);

        final boolean finalFloating = isFloating;
        navBar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                if (w <= 0 || h <= 0) return;
                if (finalFloating) {
                    float radius = Math.min(h / 2f, dpToPx(activity, 28));
                    outline.setRoundRect(0, 0, w, h, radius);
                } else {
                    float radius = dpToPx(activity, 22);
                    outline.setRoundRect(0, 0, w, h + (int) radius, radius);
                }
            }
        });
        navBar.setClipToOutline(true);

        navBar.setTag(R.id.tag_liquid_glass_applied, true);
    }

    private static void restoreOriginal(Activity activity, View navBar) {
        if (!Boolean.TRUE.equals(navBar.getTag(R.id.tag_liquid_glass_applied))) return;

        OriginalNavBarState state = sOriginalStates.get(navBar);
        if (state != null) {
            navBar.setBackground(state.background);
            ViewGroup.LayoutParams lp = navBar.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams mlp) {
                mlp.leftMargin = state.leftMargin;
                mlp.rightMargin = state.rightMargin;
                mlp.topMargin = state.topMargin;
                mlp.bottomMargin = state.bottomMargin;
                navBar.setLayoutParams(mlp);
            }
            navBar.setElevation(state.elevation);
            navBar.setClipToOutline(state.clipToOutline);
            navBar.setOutlineProvider(state.outlineProvider);
        } else {
            navBar.setBackground(null);
        }
        navBar.setTag(R.id.tag_liquid_glass_applied, null);
    }

    private static void hideDividers(Activity activity, View navBar) {
        Resources res = activity.getResources();
        String pkg = activity.getPackageName();
        String[] dividerIds = {"tab_bar_shadow", "tab_bar_divider", "action_bar_shadow"};
        for (String divName : dividerIds) {
            int id = res.getIdentifier(divName, "id", pkg);
            if (id != 0) {
                View div = activity.findViewById(id);
                if (div != null) div.setVisibility(View.GONE);
            }
        }
    }

    private static int dpToPx(Activity act, float dp) {
        return Math.round(dp * act.getResources().getDisplayMetrics().density);
    }
}
