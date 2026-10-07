package ps.reso.instaeclipse.mods.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.FrameLayout;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.utils.dialog.DialogUtils;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Liquid Glass Navigation Bar Hook
 *
 * Replaces Instagram's traditional bottom bar with the crystal clear floating liquid glass
 * slider navigation bar featuring Home, Search, Reels, Messages, and Profile tabs.
 */
public class LiquidGlassNavBarHook {

    private static final Set<Activity> watchedActivities =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final WeakHashMap<Activity, LiquidGlassSliderBarView> sSliderBars =
            new WeakHashMap<>();
    private static final WeakHashMap<Activity, View> sNativeNavBars =
            new WeakHashMap<>();

    public void install(ClassLoader classLoader) {
        try {
            FeatureStatusTracker.setHooked("LiquidGlassNavBar");
            ModuleLog.line("(InstaEclipse | LiquidGlass): Hook installed");
        } catch (Throwable t) {
            ModuleLog.line("(InstaEclipse | LiquidGlass): install error: " + t.getMessage());
        }
    }

    public static void watchActivity(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (activity.getPackageName().equals("ps.reso.instaeclipse") || activity instanceof ps.reso.instaeclipse.MainActivity) {
            return;
        }
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
        View nativeNavBar = findBottomNavBar(activity);
        if (nativeNavBar != null) {
            sNativeNavBars.put(activity, nativeNavBar);
        }

        if (FeatureFlags.enableLiquidGlassNavBar) {
            attachSliderBar(activity, nativeNavBar);
        } else {
            detachSliderBar(activity);
        }
    }

    private static void attachSliderBar(Activity activity, View nativeNavBar) {
        Window window = activity.getWindow();
        if (window != null) {
            window.setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 29) {
                window.setNavigationBarContrastEnforced(false);
            }
        }

        // Hide native navigation bar visually (keep it in layout tree so clicks work)
        if (nativeNavBar != null && nativeNavBar.getVisibility() != View.INVISIBLE) {
            nativeNavBar.setVisibility(View.INVISIBLE);
        }

        // Hide divider shadows
        hideDividers(activity);

        ViewGroup decor = (ViewGroup) (activity.getWindow() != null ? activity.getWindow().getDecorView() : null);
        if (decor == null) return;

        LiquidGlassSliderBarView existing = sSliderBars.get(activity);
        if (existing == null) {
            LiquidGlassSliderBarView sliderBar = new LiquidGlassSliderBarView(activity);
            sliderBar.setId(R.id.tag_liquid_glass_applied);

            // Tab click handling
            sliderBar.setOnTabSelectedListener(index -> clickNativeTab(activity, index));

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            decor.addView(sliderBar, lp);
            sliderBar.bringToFront();
            sSliderBars.put(activity, sliderBar);
            existing = sliderBar;
        } else {
            existing.bringToFront();
            existing.applyConfiguration(
                    FeatureFlags.liquidGlassStyle,
                    FeatureFlags.liquidGlassOpacity,
                    FeatureFlags.liquidGlassWidthMargin,
                    FeatureFlags.liquidGlassHeight,
                    FeatureFlags.liquidGlassCornerRadius,
                    FeatureFlags.liquidGlassBorderSheen,
                    false
            );
        }

        // Keep slider tab indicator synced with Instagram's active tab
        syncWithNativeTab(activity, existing);
    }

    private static void detachSliderBar(Activity activity) {
        LiquidGlassSliderBarView sliderBar = sSliderBars.remove(activity);
        if (sliderBar != null) {
            ViewGroup parent = (ViewGroup) sliderBar.getParent();
            if (parent != null) parent.removeView(sliderBar);
        }

        View nativeNavBar = sNativeNavBars.get(activity);
        if (nativeNavBar != null) {
            nativeNavBar.setVisibility(View.VISIBLE);
        }
    }

    private static void clickNativeTab(Activity activity, int index) {
        Resources res = activity.getResources();
        String pkg = activity.getPackageName();
        int tabId = 0;

        switch (index) {
            case LiquidGlassSliderBarView.TAB_HOME: // 0: Home
                clickFirstMatching(activity, "feed_tab", "home_tab");
                break;
            case LiquidGlassSliderBarView.TAB_SEARCH: // 1: Search
                clickFirstMatching(activity, "search_tab");
                break;
            case LiquidGlassSliderBarView.TAB_REELS: // 2: Reels
                clickFirstMatching(activity, "clips_tab");
                break;
            case LiquidGlassSliderBarView.TAB_MESSAGES: // 3: Messages / Direct
                tabId = res.getIdentifier("direct_tab", "id", pkg);
                if (tabId != 0 && activity.findViewById(tabId) != null) {
                    activity.findViewById(tabId).performClick();
                } else {
                    clickFirstMatching(activity, "action_bar_inbox_button", "inbox_button", "direct_tab");
                }
                break;
            case LiquidGlassSliderBarView.TAB_PROFILE: // 4: Profile
                clickFirstMatching(activity, "profile_tab", "user_tab");
                break;
        }
    }

    private static void clickFirstMatching(Activity activity, String... ids) {
        Resources res = activity.getResources();
        String pkg = activity.getPackageName();
        for (String idName : ids) {
            int id = res.getIdentifier(idName, "id", pkg);
            if (id != 0) {
                View v = activity.findViewById(id);
                if (v != null) {
                    v.performClick();
                    return;
                }
            }
        }
    }

    private static void syncWithNativeTab(Activity activity, LiquidGlassSliderBarView sliderBar) {
        Resources res = activity.getResources();
        String pkg = activity.getPackageName();

        int feedId = res.getIdentifier("feed_tab", "id", pkg);
        int searchId = res.getIdentifier("search_tab", "id", pkg);
        int clipsId = res.getIdentifier("clips_tab", "id", pkg);
        int directId = res.getIdentifier("direct_tab", "id", pkg);
        int profileId = res.getIdentifier("profile_tab", "id", pkg);

        if (feedId != 0 && isSelected(activity, feedId)) {
            sliderBar.syncSelectedTab(LiquidGlassSliderBarView.TAB_HOME);
        } else if (searchId != 0 && isSelected(activity, searchId)) {
            sliderBar.syncSelectedTab(LiquidGlassSliderBarView.TAB_SEARCH);
        } else if (clipsId != 0 && isSelected(activity, clipsId)) {
            sliderBar.syncSelectedTab(LiquidGlassSliderBarView.TAB_REELS);
        } else if (directId != 0 && isSelected(activity, directId)) {
            sliderBar.syncSelectedTab(LiquidGlassSliderBarView.TAB_MESSAGES);
        } else if (profileId != 0 && isSelected(activity, profileId)) {
            sliderBar.syncSelectedTab(LiquidGlassSliderBarView.TAB_PROFILE);
        }
    }

    private static boolean isSelected(Activity activity, int id) {
        View v = activity.findViewById(id);
        return v != null && v.isSelected();
    }

    public static void launchCreation(Activity activity, String action) {
        if (activity == null) return;
        String pkg = activity.getPackageName();
        Resources res = activity.getResources();

        try {
            if ("ai".equals(action)) {
                DialogUtils.showEclipseOptionsDialog(activity);
                return;
            }

            if ("post".equals(action)) {
                int shareId = res.getIdentifier("share_tab", "id", pkg);
                if (shareId != 0) {
                    View v = activity.findViewById(shareId);
                    if (v != null) { v.performClick(); return; }
                }
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            if ("reel".equals(action)) {
                intent.setData(Uri.parse("instagram://reels_camera"));
            } else if ("story".equals(action)) {
                intent.setData(Uri.parse("instagram://story-camera"));
            } else if ("live".equals(action)) {
                intent.setData(Uri.parse("instagram://live_camera"));
            } else {
                intent.setData(Uri.parse("instagram://camera"));
            }
            intent.setPackage(pkg);
            activity.startActivity(intent);
        } catch (Throwable t) {
            try {
                Intent fallback = new Intent();
                fallback.setComponent(new ComponentName(pkg, "com.instagram.creation.activity.MediaCaptureActivity"));
                fallback.setPackage(pkg);
                activity.startActivity(fallback);
            } catch (Throwable ignored) {
                try {
                    int searchTabId = res.getIdentifier("search_tab", "id", pkg);
                    if (searchTabId != 0) {
                        View s = activity.findViewById(searchTabId);
                        if (s != null) s.performClick();
                    }
                } catch (Throwable ignored2) {}
            }
        }
    }

    private static View findBottomNavBar(Activity activity) {
        Resources res = activity.getResources();
        String pkg = activity.getPackageName();

        String[] possibleBarIds = {
                "tab_bar", "tab_bar_container", "bottom_navigation_bar",
                "main_tab_bar", "igds_tab_bar", "clips_tab_bar_background"
        };
        for (String idName : possibleBarIds) {
            int id = res.getIdentifier(idName, "id", pkg);
            if (id != 0) {
                View v = activity.findViewById(id);
                if (v != null) return v;
            }
        }

        // Discover via child tabs
        String[] tabIds = {"search_tab", "clips_tab", "feed_tab", "profile_tab", "news_tab", "direct_tab"};
        for (String tabIdName : tabIds) {
            int id = res.getIdentifier(tabIdName, "id", pkg);
            if (id != 0) {
                View tabView = activity.findViewById(id);
                if (tabView != null && tabView.getParent() instanceof ViewGroup) {
                    ViewGroup tabRow = (ViewGroup) tabView.getParent();
                    if (tabRow.getParent() instanceof ViewGroup parentContainer) {
                        int h = parentContainer.getHeight();
                        int screenH = activity.getResources().getDisplayMetrics().heightPixels;
                        int[] loc = new int[2];
                        parentContainer.getLocationOnScreen(loc);
                        if (loc[1] + h >= screenH - dpToPx(activity, 40) && h <= dpToPx(activity, 110) && h >= dpToPx(activity, 30)) {
                            return parentContainer;
                        }
                    }
                    return tabRow;
                }
            }
        }

        return null;
    }

    private static void hideDividers(Activity activity) {
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
