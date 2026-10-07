package ps.reso.instaeclipse.mods.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.mods.ui.theme.IgThemePalette;
import ps.reso.instaeclipse.mods.ui.utils.ModuleResourceLoader;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;

/**
 * Liquid Glass Floating Slider Navigation Bar (iOS 27 Edition)
 *
 * Implements the 5-tab floating liquid glass dock matching the authentic reference design:
 * - Direct & Center Create (+) Layout (Row 1 & 2) or Reels & Search Layout (Row 3 & 4)
 * - Dynamic Liquid Droplet Lens with Prismatic Chromatic Aberration Rim
 * - Convex Refractive Magnification of active and hovered icons
 * - Fluid drag/slide gesture physics with tactile haptics
 * - Integrated or external '+' button with 45-degree rotation
 * - Frosted Liquid Glass creation popup menu (Reel, Post, Story, Story Highlight, Live, AI)
 */
public class LiquidGlassSliderBarView extends FrameLayout {

    public static final int LAYOUT_CENTER_CREATE = 0;
    public static final int LAYOUT_REELS_SEARCH = 1;

    public interface OnTabSelectedListener {
        void onTabSelected(int index);
    }

    public interface OnCreateActionSelectedListener {
        void onCreateAction(String action);
    }

    public static class SlideablePillLayout extends FrameLayout {
        private float downX = 0f;
        private float downY = 0f;
        private boolean isDragging = false;
        private final int touchSlop;
        private LiquidGlassSliderBarView host;

        public SlideablePillLayout(@NonNull Context context) {
            super(context);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        public void setHost(LiquidGlassSliderBarView host) {
            this.host = host;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    isDragging = false;
                    if (host != null) {
                        host.onPillTouchDown(ev.getX(), ev.getY());
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = Math.abs(ev.getX() - downX);
                    float dy = Math.abs(ev.getY() - downY);
                    if (dx > touchSlop && dx > dy) {
                        isDragging = true;
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    break;
            }
            return isDragging;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (host != null) {
                return host.handlePillTouchEvent(ev);
            }
            return super.onTouchEvent(ev);
        }
    }

    private LinearLayout bottomRow;
    private FrameLayout tabsPill;
    private LiquidGlassDrawable tabsPillDrawable;
    private LiquidDropletIndicatorView dropletIndicator;
    private LinearLayout tabsRow;
    private final FrameLayout[] tabContainers = new FrameLayout[5];
    private final ImageView[] tabIconViews = new ImageView[5];

    private FrameLayout fabCreate;
    private LiquidGlassDrawable fabDrawable;
    private ImageView fabIcon;

    private FrameLayout scrimOverlay;
    private LinearLayout popupMenu;

    private int selectedIndex = 0;
    private int currentLayout = LAYOUT_CENTER_CREATE;
    private OnTabSelectedListener tabListener;
    private OnCreateActionSelectedListener actionListener;

    private boolean isPreviewMode = false;
    private boolean isMenuOpen = false;
    private boolean isDraggingSlider = false;
    private float touchDownX = 0f;
    private float touchDownY = 0f;
    private int lastHapticIndex = 0;

    public LiquidGlassSliderBarView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public LiquidGlassSliderBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void setPreviewMode(boolean isPreview) {
        this.isPreviewMode = isPreview;
    }

    public boolean isPreviewMode() {
        return isPreviewMode;
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.tabListener = listener;
    }

    public void setOnCreateActionSelectedListener(OnCreateActionSelectedListener listener) {
        this.actionListener = listener;
    }

    public int getCurrentLayout() {
        return currentLayout;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void init(Context context) {
        setClipChildren(false);
        setClipToPadding(false);

        currentLayout = FeatureFlags.liquidGlassNavLayout;

        // 1. Scrim overlay for popup menu dismiss
        scrimOverlay = new FrameLayout(context);
        scrimOverlay.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        scrimOverlay.setBackgroundColor(Color.TRANSPARENT);
        scrimOverlay.setVisibility(GONE);
        scrimOverlay.setOnClickListener(v -> closeMenu());
        addView(scrimOverlay);

        // 2. Bottom Row Container (Pill + optional FAB)
        bottomRow = new LinearLayout(context);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        bottomRow.setClipChildren(false);
        bottomRow.setClipToPadding(false);

        int barHeight = dp(FeatureFlags.liquidGlassHeight > 0 ? FeatureFlags.liquidGlassHeight : 56);
        int sideMargin = dp(FeatureFlags.liquidGlassWidthMargin >= 0 ? FeatureFlags.liquidGlassWidthMargin : 14);
        LayoutParams rowLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowLp.gravity = Gravity.BOTTOM;
        rowLp.leftMargin = sideMargin;
        rowLp.rightMargin = sideMargin;
        rowLp.bottomMargin = dp(16);
        bottomRow.setLayoutParams(rowLp);

        // 2A. Liquid Glass Tabs Pill
        SlideablePillLayout pillLayout = new SlideablePillLayout(context);
        pillLayout.setHost(this);
        tabsPill = pillLayout;

        boolean showExternalFab = (currentLayout == LAYOUT_REELS_SEARCH && FeatureFlags.liquidGlassShowFab);
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(0, barHeight, 1.0f);
        pillLp.rightMargin = showExternalFab ? dp(10) : 0;
        tabsPill.setLayoutParams(pillLp);

        tabsPillDrawable = new LiquidGlassDrawable(context, FeatureFlags.liquidGlassStyle, FeatureFlags.liquidGlassBorderSheen);
        tabsPill.setBackground(tabsPillDrawable);
        tabsPill.setElevation(dp(12));
        setupPillOutline(tabsPill, barHeight, FeatureFlags.liquidGlassCornerRadius);

        // High-Fidelity Liquid Droplet Lens Indicator (with Chromatic Dispersion Rim)
        dropletIndicator = new LiquidDropletIndicatorView(context);
        dropletIndicator.setStyle(FeatureFlags.liquidGlassStyle);
        dropletIndicator.setChromaticEnabled(FeatureFlags.liquidGlassChromaticLens);
        dropletIndicator.setCornerRadius(dp(22));

        int dropletHeight = Math.max(dp(36), barHeight - dp(10));
        FrameLayout.LayoutParams dropletLp = new FrameLayout.LayoutParams(dp(52), dropletHeight);
        dropletLp.gravity = Gravity.CENTER_VERTICAL;
        dropletLp.leftMargin = dp(4);
        dropletIndicator.setLayoutParams(dropletLp);
        tabsPill.addView(dropletIndicator);

        // 5-Tab Row Container
        tabsRow = new LinearLayout(context);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        tabsRow.setGravity(Gravity.CENTER);

        buildTabItems(context);
        tabsPill.addView(tabsRow);

        // Pill touch listener
        tabsPill.setOnTouchListener((v, event) -> handlePillTouchEvent(event));
        bottomRow.addView(tabsPill);

        // 2B. External Floating FAB (used in Reels & Search layout when enabled)
        fabCreate = new FrameLayout(context);
        LinearLayout.LayoutParams fabLp = new LinearLayout.LayoutParams(barHeight, barHeight);
        fabCreate.setLayoutParams(fabLp);
        fabDrawable = new LiquidGlassDrawable(context, FeatureFlags.liquidGlassStyle, FeatureFlags.liquidGlassBorderSheen);
        fabCreate.setBackground(fabDrawable);
        fabCreate.setElevation(dp(12));
        fabCreate.setVisibility(showExternalFab ? VISIBLE : GONE);
        setupCircleOutline(fabCreate, barHeight);

        fabIcon = new ImageView(context);
        FrameLayout.LayoutParams fabIconLp = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER);
        fabIcon.setLayoutParams(fabIconLp);
        android.graphics.drawable.Drawable plusIcon = ModuleResourceLoader.loadIcon(
                context, R.drawable.ic_plus, ModuleResourceLoader.KEY_PLUS, 0xFFFFFFFF);
        fabIcon.setImageDrawable(plusIcon);
        fabCreate.addView(fabIcon);

        fabCreate.setOnClickListener(v -> toggleMenu(fabCreate));
        bottomRow.addView(fabCreate);

        addView(bottomRow);

        // Insets listener
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            if (navBottom == 0) {
                navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            }
            updateBottomMargin(navBottom);
            return insets;
        });

        // 3. Popup Creation Menu (iOS 27 Liquid Glass card)
        buildPopupMenu(context);

        // Initial tab selection
        post(() -> selectTab(0, false));
    }

    private void buildTabItems(Context context) {
        tabsRow.removeAllViews();

        int[] iconDrawables;
        String[] fallbackKeys;

        if (currentLayout == LAYOUT_CENTER_CREATE) {
            // Direct & Center Create (+) Layout matching Top Screenshot:
            // [0] Home, [1] Direct/Share, [2] Create (+), [3] Heart, [4] Profile
            iconDrawables = new int[]{
                    R.drawable.ic_home,
                    R.drawable.ic_direct,
                    R.drawable.ic_plus,
                    R.drawable.ic_heart,
                    R.drawable.ic_profile
            };
            fallbackKeys = new String[]{
                    ModuleResourceLoader.KEY_HOME,
                    ModuleResourceLoader.KEY_DIRECT,
                    ModuleResourceLoader.KEY_PLUS,
                    ModuleResourceLoader.KEY_HEART,
                    ModuleResourceLoader.KEY_PROFILE
            };
        } else {
            // Reels, Direct & Search Layout matching Bottom Screenshot:
            // [0] Home, [1] Reels, [2] Direct/Share, [3] Search, [4] Profile
            iconDrawables = new int[]{
                    R.drawable.ic_home,
                    R.drawable.ic_reels,
                    R.drawable.ic_direct,
                    R.drawable.ic_search,
                    R.drawable.ic_profile
            };
            fallbackKeys = new String[]{
                    ModuleResourceLoader.KEY_HOME,
                    ModuleResourceLoader.KEY_REEL,
                    ModuleResourceLoader.KEY_DIRECT,
                    ModuleResourceLoader.KEY_SEARCH,
                    ModuleResourceLoader.KEY_PROFILE
            };
        }

        for (int i = 0; i < 5; i++) {
            final int index = i;
            FrameLayout tabContainer = new FrameLayout(context);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);
            tabContainer.setLayoutParams(tLp);

            ImageView iv = new ImageView(context);
            int iconSize = dp(23);
            FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
            iv.setLayoutParams(ivLp);

            android.graphics.drawable.Drawable tabIcon = ModuleResourceLoader.loadIcon(
                    context, iconDrawables[i], fallbackKeys[i], 0xFFFFFFFF);
            iv.setImageDrawable(tabIcon);
            iv.setAlpha(i == selectedIndex ? 1.0f : 0.88f);
            iv.setScaleX(i == selectedIndex ? 1.22f : 1.0f);
            iv.setScaleY(i == selectedIndex ? 1.22f : 1.0f);

            tabContainer.addView(iv);
            tabContainers[i] = tabContainer;
            tabIconViews[i] = iv;

            tabContainer.setOnClickListener(v -> onTabClicked(index));
            tabsRow.addView(tabContainer);
        }
    }

    private void onTabClicked(int index) {
        if (currentLayout == LAYOUT_CENTER_CREATE && index == 2) {
            // Center '+' button tapped!
            if (FeatureFlags.enableLiquidGlassQuickActions) {
                toggleMenu(tabContainers[2]);
            } else if (isPreviewMode) {
                toggleMenu(tabContainers[2]);
            } else if (actionListener != null) {
                actionListener.onCreateAction("camera");
            }
            return;
        }

        selectTab(index, true);
    }

    public void setLayout(int layoutMode) {
        if (this.currentLayout != layoutMode) {
            this.currentLayout = layoutMode;
            FeatureFlags.liquidGlassNavLayout = layoutMode;
            buildTabItems(getContext());

            boolean showExternalFab = (currentLayout == LAYOUT_REELS_SEARCH && FeatureFlags.liquidGlassShowFab);
            if (tabsPill != null) {
                LinearLayout.LayoutParams pillLp = (LinearLayout.LayoutParams) tabsPill.getLayoutParams();
                if (pillLp != null) {
                    pillLp.rightMargin = showExternalFab ? dp(10) : 0;
                    tabsPill.setLayoutParams(pillLp);
                }
            }
            if (fabCreate != null) {
                fabCreate.setVisibility(showExternalFab ? VISIBLE : GONE);
            }

            post(() -> selectTab(Math.min(selectedIndex, 4), false));
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        int navHeight = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsets insets = getRootWindowInsets();
            if (insets != null) {
                navHeight = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
            }
        }
        if (navHeight == 0) {
            navHeight = getNavigationBarHeight(getContext());
        }
        updateBottomMargin(navHeight);
    }

    public void updateBottomMargin(int navBarHeightPx) {
        if (bottomRow == null) return;
        LayoutParams lp = (LayoutParams) bottomRow.getLayoutParams();
        if (lp != null) {
            int targetMargin = navBarHeightPx > 0 ? (navBarHeightPx + dp(10)) : dp(16);
            if (lp.bottomMargin != targetMargin) {
                lp.bottomMargin = targetMargin;
                bottomRow.setLayoutParams(lp);
            }
        }
        if (popupMenu != null) {
            LayoutParams pLp = (LayoutParams) popupMenu.getLayoutParams();
            if (pLp != null) {
                int barHeight = dp(FeatureFlags.liquidGlassHeight > 0 ? FeatureFlags.liquidGlassHeight : 56);
                int baseMargin = navBarHeightPx > 0 ? (navBarHeightPx + dp(10)) : dp(16);
                int targetMenuBottom = baseMargin + barHeight + dp(14);
                if (pLp.bottomMargin != targetMenuBottom) {
                    pLp.bottomMargin = targetMenuBottom;
                    popupMenu.setLayoutParams(pLp);
                }
            }
        }
    }

    private static int getNavigationBarHeight(Context context) {
        try {
            int resourceId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (resourceId > 0) {
                return context.getResources().getDimensionPixelSize(resourceId);
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    public void applyConfiguration(int style, int opacity, int widthMargin, int height, int cornerRadius, boolean borderSheen, boolean showFab) {
        int barHeightPx = dp(height);
        int marginPx = dp(widthMargin);
        boolean showExternalFab = (currentLayout == LAYOUT_REELS_SEARCH && showFab);

        if (bottomRow != null) {
            LayoutParams rowLp = (LayoutParams) bottomRow.getLayoutParams();
            if (rowLp != null) {
                rowLp.leftMargin = marginPx;
                rowLp.rightMargin = marginPx;
                bottomRow.setLayoutParams(rowLp);
            }
        }

        if (tabsPill != null) {
            LinearLayout.LayoutParams pillLp = (LinearLayout.LayoutParams) tabsPill.getLayoutParams();
            if (pillLp != null) {
                pillLp.height = barHeightPx;
                pillLp.rightMargin = showExternalFab ? dp(10) : 0;
                tabsPill.setLayoutParams(pillLp);
            }
            setupPillOutline(tabsPill, barHeightPx, cornerRadius);
            if (tabsPillDrawable != null) {
                tabsPillDrawable.update(style, borderSheen, opacity / 100f, cornerRadius);
            }
        }

        if (dropletIndicator != null) {
            int dropletHeight = Math.max(dp(34), barHeightPx - dp(10));
            ViewGroup.LayoutParams dlp = dropletIndicator.getLayoutParams();
            if (dlp != null) {
                dlp.height = dropletHeight;
                dropletIndicator.setLayoutParams(dlp);
            }
            dropletIndicator.setStyle(style);
            dropletIndicator.setChromaticEnabled(FeatureFlags.liquidGlassChromaticLens);
            dropletIndicator.setCornerRadius(cornerRadius > 0 ? dp(cornerRadius - 4) : (dropletHeight / 2f));
        }

        if (fabCreate != null) {
            fabCreate.setVisibility(showExternalFab ? VISIBLE : GONE);
            LinearLayout.LayoutParams fabLp = (LinearLayout.LayoutParams) fabCreate.getLayoutParams();
            if (fabLp != null) {
                fabLp.width = barHeightPx;
                fabLp.height = barHeightPx;
                fabCreate.setLayoutParams(fabLp);
            }
            setupCircleOutline(fabCreate, barHeightPx);
            if (fabDrawable != null) {
                fabDrawable.update(style, borderSheen, opacity / 100f, cornerRadius);
            }
        }

        updateSliderPosition(selectedIndex, false);
        requestLayout();
        invalidate();
    }

    public void setThemePalette(IgThemePalette palette) {
        if (palette == null) return;
        try {
            if (dropletIndicator != null) {
                dropletIndicator.setAccentColor(palette.accent);
            }
        } catch (Throwable ignored) {}
    }

    private void setupPillOutline(View view, int height, int cornerRadiusDp) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                int w = v.getWidth();
                int h = v.getHeight();
                if (w <= 0 || h <= 0) return;
                float r = cornerRadiusDp > 0 ? dp(cornerRadiusDp) : (h / 2f);
                outline.setRoundRect(0, 0, w, h, Math.min(r, h / 2f));
            }
        });
        view.setClipToOutline(true);
    }

    private void setupCircleOutline(View view, int size) {
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline outline) {
                int s = Math.min(v.getWidth(), v.getHeight());
                if (s <= 0) return;
                outline.setRoundRect(0, 0, s, s, s / 2f);
            }
        });
        view.setClipToOutline(true);
    }

    private void buildPopupMenu(Context context) {
        popupMenu = new LinearLayout(context);
        popupMenu.setOrientation(LinearLayout.VERTICAL);
        popupMenu.setBackground(new LiquidGlassDrawable(context, LiquidGlassDrawable.STYLE_FLOATING_PILL, true));
        popupMenu.setElevation(dp(16));
        popupMenu.setPadding(dp(8), dp(10), dp(8), dp(10));

        int menuWidth = dp(196);
        LayoutParams pLp = new LayoutParams(menuWidth, LayoutParams.WRAP_CONTENT);
        pLp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        pLp.bottomMargin = dp(84);
        popupMenu.setLayoutParams(pLp);
        popupMenu.setPivotX(menuWidth / 2f);
        popupMenu.setPivotY(dp(220));

        popupMenu.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int w = view.getWidth();
                int h = view.getHeight();
                if (w <= 0 || h <= 0) return;
                outline.setRoundRect(0, 0, w, h, dp(26));
            }
        });
        popupMenu.setClipToOutline(true);

        addMenuItem(context, R.drawable.ic_reels, ModuleResourceLoader.KEY_REEL, "Reel", "reel");
        addMenuItem(context, R.drawable.ic_grid_post, ModuleResourceLoader.KEY_POST, "Post", "post");
        addMenuItem(context, R.drawable.ic_story_dashed, ModuleResourceLoader.KEY_STORY, "Story", "story");
        addMenuItem(context, R.drawable.ic_story_highlight, ModuleResourceLoader.KEY_HIGHLIGHT, "Story highlight", "highlight");
        addMenuItem(context, R.drawable.ic_live, ModuleResourceLoader.KEY_LIVE, "Live", "live");
        addMenuItem(context, R.drawable.ic_sparkle, ModuleResourceLoader.KEY_AI, "AI Studio", "ai");

        popupMenu.setVisibility(GONE);
        popupMenu.setScaleX(0.7f);
        popupMenu.setScaleY(0.7f);
        popupMenu.setAlpha(0f);
        addView(popupMenu);
    }

    private void addMenuItem(Context context, int iconRes, String fallbackKey, String title, String actionKey) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        row.setClickable(true);
        row.setFocusable(true);

        ImageView iv = new ImageView(context);
        int iconSize = dp(20);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        ivLp.rightMargin = dp(14);
        iv.setLayoutParams(ivLp);
        android.graphics.drawable.Drawable itemIcon = ModuleResourceLoader.loadIcon(
                context, iconRes, fallbackKey, 0xFFFFFFFF);
        iv.setImageDrawable(itemIcon);
        row.addView(iv);

        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(tv);

        row.setOnClickListener(v -> {
            closeMenu();
            if (isPreviewMode) {
                triggerHapticFeedback();
            } else if (actionListener != null) {
                actionListener.onCreateAction(actionKey);
            }
        });

        popupMenu.addView(row);
    }

    public void selectTab(int index, boolean animate) {
        if (index < 0 || index >= 5) return;
        selectedIndex = index;

        // Animate Convex Refractive Magnification of active and non-active icons
        for (int i = 0; i < 5; i++) {
            boolean isSel = (i == index);
            if (tabIconViews[i] != null) {
                float targetScale = isSel ? 1.22f : 1.0f;
                float targetAlpha = isSel ? 1.0f : 0.86f;

                if (animate) {
                    tabIconViews[i].animate()
                            .scaleX(targetScale)
                            .scaleY(targetScale)
                            .alpha(targetAlpha)
                            .setDuration(220)
                            .setInterpolator(new OvershootInterpolator(1.4f))
                            .start();
                } else {
                    tabIconViews[i].setScaleX(targetScale);
                    tabIconViews[i].setScaleY(targetScale);
                    tabIconViews[i].setAlpha(targetAlpha);
                }
            }
        }

        updateSliderPosition(index, animate);

        if (tabListener != null && !isPreviewMode) {
            tabListener.onTabSelected(index);
        }
    }

    public void syncSelectedTab(int index) {
        if (index < 0 || index >= 5 || index == selectedIndex) return;
        selectTab(index, true);
    }

    private void updateSliderPosition(int index, boolean animate) {
        tabsPill.post(() -> {
            int totalW = tabsPill.getWidth();
            if (totalW <= 0) return;
            float tabW = totalW / 5f;
            float dropletW = tabW - dp(8);

            ViewGroup.LayoutParams lp = dropletIndicator.getLayoutParams();
            if (lp.width != (int) dropletW) {
                lp.width = (int) dropletW;
                dropletIndicator.setLayoutParams(lp);
            }

            float targetX = (index * tabW) + dp(4);
            if (animate) {
                dropletIndicator.animate()
                        .translationX(targetX)
                        .scaleX(1.0f)
                        .setDuration(260)
                        .setInterpolator(new OvershootInterpolator(1.1f))
                        .start();
            } else {
                dropletIndicator.setTranslationX(targetX);
                dropletIndicator.setScaleX(1.0f);
            }
        });
    }

    public void onPillTouchDown(float x, float y) {
        touchDownX = x;
        touchDownY = y;
        isDraggingSlider = false;
        lastHapticIndex = selectedIndex;
    }

    private boolean handlePillTouchEvent(MotionEvent event) {
        int totalW = tabsPill.getWidth();
        if (totalW <= 0) return false;
        float tabW = totalW / 5f;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                touchDownY = event.getY();
                isDraggingSlider = false;
                lastHapticIndex = selectedIndex;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(event.getX() - touchDownX);
                if (dx > dp(8)) {
                    isDraggingSlider = true;
                }
                if (isDraggingSlider) {
                    float dropletW = tabW - dp(8);
                    float curX = event.getX() - (dropletW / 2f);
                    float clampedX = Math.max(dp(3), Math.min(totalW - dropletW - dp(3), curX));
                    dropletIndicator.setTranslationX(clampedX);

                    // Fluid elastic stretching during drag
                    dropletIndicator.setScaleX(1.14f);

                    int hoveredIndex = (int) (event.getX() / tabW);
                    hoveredIndex = Math.max(0, Math.min(4, hoveredIndex));
                    if (hoveredIndex != lastHapticIndex) {
                        lastHapticIndex = hoveredIndex;
                        triggerHapticFeedback();

                        // Magnify icon under the lens in real-time
                        for (int k = 0; k < 5; k++) {
                            boolean isHovered = (k == hoveredIndex);
                            if (tabIconViews[k] != null) {
                                tabIconViews[k].setScaleX(isHovered ? 1.22f : 1.0f);
                                tabIconViews[k].setScaleY(isHovered ? 1.22f : 1.0f);
                                tabIconViews[k].setAlpha(isHovered ? 1.0f : 0.86f);
                            }
                        }
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                dropletIndicator.animate().scaleX(1.0f).setDuration(160).start();
                if (isDraggingSlider) {
                    int finalIndex = (int) (event.getX() / tabW);
                    finalIndex = Math.max(0, Math.min(4, finalIndex));
                    onTabClicked(finalIndex);
                    isDraggingSlider = false;
                } else {
                    int clickedIndex = (int) (event.getX() / tabW);
                    clickedIndex = Math.max(0, Math.min(4, clickedIndex));
                    onTabClicked(clickedIndex);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                dropletIndicator.animate().scaleX(1.0f).setDuration(160).start();
                updateSliderPosition(selectedIndex, true);
                isDraggingSlider = false;
                return true;
        }
        return false;
    }

    public void toggleMenu(View anchorView) {
        if (isMenuOpen) {
            closeMenu();
        } else {
            openMenu(anchorView);
        }
    }

    private void openMenu(View anchorView) {
        isMenuOpen = true;
        scrimOverlay.setVisibility(VISIBLE);

        // Position popup right above anchor
        if (anchorView != null && popupMenu != null) {
            int[] loc = new int[2];
            anchorView.getLocationInWindow(loc);
            int[] myLoc = new int[2];
            getLocationInWindow(myLoc);

            int anchorCenterX = loc[0] - myLoc[0] + (anchorView.getWidth() / 2);
            LayoutParams pLp = (LayoutParams) popupMenu.getLayoutParams();
            if (pLp != null) {
                int menuW = dp(196);
                pLp.gravity = Gravity.BOTTOM | Gravity.START;
                pLp.leftMargin = Math.max(dp(12), Math.min(getWidth() - menuW - dp(12), anchorCenterX - (menuW / 2)));
                popupMenu.setLayoutParams(pLp);
            }
        }

        popupMenu.setVisibility(VISIBLE);

        // Rotate '+' icon to '✕'
        if (currentLayout == LAYOUT_CENTER_CREATE && tabIconViews[2] != null) {
            tabIconViews[2].animate()
                    .rotation(45f)
                    .setDuration(260)
                    .setInterpolator(new OvershootInterpolator())
                    .start();
        } else if (fabIcon != null) {
            fabIcon.animate()
                    .rotation(45f)
                    .setDuration(260)
                    .setInterpolator(new OvershootInterpolator())
                    .start();
        }

        popupMenu.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(260)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();

        triggerHapticFeedback();
    }

    public void closeMenu() {
        if (!isMenuOpen) return;
        isMenuOpen = false;
        scrimOverlay.setVisibility(GONE);

        // Rotate '✕' icon back to '+'
        if (currentLayout == LAYOUT_CENTER_CREATE && tabIconViews[2] != null) {
            tabIconViews[2].animate()
                    .rotation(0f)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else if (fabIcon != null) {
            fabIcon.animate()
                    .rotation(0f)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        popupMenu.animate()
                .alpha(0f)
                .scaleX(0.7f)
                .scaleY(0.7f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        popupMenu.setVisibility(GONE);
                        popupMenu.animate().setListener(null);
                    }
                })
                .start();
    }

    public boolean isMenuOpen() {
        return isMenuOpen;
    }

    private void triggerHapticFeedback() {
        try {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Throwable ignored) {}
    }

    private int dp(float val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
}
