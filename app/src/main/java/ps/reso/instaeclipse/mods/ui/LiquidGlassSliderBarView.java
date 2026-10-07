package ps.reso.instaeclipse.mods.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
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
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.mods.ui.utils.ModuleResourceLoader;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.log.ModuleLog;

/**
 * Liquid Glass Floating Slider Navigation Bar
 *
 * Implements the iOS 18 / VisionOS inspired floating liquid glass dock with:
 * - 4-tab interactive pill bar with smooth slider indicator
 * - Horizontal drag/slide gesture support to slide across tabs
 * - Floating circular "+" create button with 45-degree rotation animation
 * - Vertical frosted liquid glass popup creation menu (Reel, Post, Story, Story Highlight, Live, AI)
 */
public class LiquidGlassSliderBarView extends FrameLayout {

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
    private View sliderIndicator;
    private LinearLayout tabsRow;
    private final ImageView[] tabIconViews = new ImageView[4];

    private FrameLayout fabCreate;
    private LiquidGlassDrawable fabDrawable;
    private ImageView fabIcon;

    private FrameLayout scrimOverlay;
    private LinearLayout popupMenu;

    private int selectedIndex = 0;
    private OnTabSelectedListener tabListener;
    private OnCreateActionSelectedListener actionListener;

    private boolean isPreviewMode = false;
    private boolean isMenuOpen = false;
    private boolean isDraggingSlider = false;
    private float touchDownX = 0f;
    private float touchDownY = 0f;
    private int lastHapticIndex = 0;
    private VelocityTracker velocityTracker;

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

    @SuppressLint("ClickableViewAccessibility")
    private void init(Context context) {
        setClipChildren(false);
        setClipToPadding(false);

        // 1. Scrim overlay for popup menu dismiss
        scrimOverlay = new FrameLayout(context);
        scrimOverlay.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        scrimOverlay.setBackgroundColor(Color.TRANSPARENT);
        scrimOverlay.setVisibility(GONE);
        scrimOverlay.setOnClickListener(v -> closeMenu());
        addView(scrimOverlay);

        // 2. Bottom Row Container (Pill + FAB)
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
        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(0, barHeight, 1.0f);
        pillLp.rightMargin = FeatureFlags.liquidGlassShowFab ? dp(10) : 0;
        tabsPill.setLayoutParams(pillLp);
        tabsPillDrawable = new LiquidGlassDrawable(context, FeatureFlags.liquidGlassStyle, FeatureFlags.liquidGlassBorderSheen);
        tabsPill.setBackground(tabsPillDrawable);
        tabsPill.setElevation(dp(12));
        setupPillOutline(tabsPill, barHeight, FeatureFlags.liquidGlassCornerRadius);

        // Slider capsule indicator
        sliderIndicator = new View(context);
        GradientDrawable sliderBg = new GradientDrawable();
        sliderBg.setShape(GradientDrawable.RECTANGLE);
        sliderBg.setCornerRadius(dp(22));
        sliderBg.setColor(0x42FFFFFF); // semi-transparent frosted highlight
        sliderBg.setStroke(dp(1.2f), 0x70FFFFFF); // delicate glowing rim
        sliderIndicator.setBackground(sliderBg);

        int sliderHeight = Math.max(dp(32), barHeight - dp(12));
        FrameLayout.LayoutParams sliderLp = new FrameLayout.LayoutParams(dp(54), sliderHeight);
        sliderLp.gravity = Gravity.CENTER_VERTICAL;
        sliderLp.leftMargin = dp(6);
        sliderIndicator.setLayoutParams(sliderLp);
        tabsPill.addView(sliderIndicator);

        // Tabs Row
        tabsRow = new LinearLayout(context);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        tabsRow.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        tabsRow.setGravity(Gravity.CENTER);

        int[] iconDrawables = {
                R.drawable.ic_home,
                R.drawable.ic_movie,
                R.drawable.ic_heart,
                R.drawable.ic_profile
        };
        String[] fallbackKeys = {
                ModuleResourceLoader.KEY_HOME,
                ModuleResourceLoader.KEY_REEL,
                ModuleResourceLoader.KEY_HEART,
                ModuleResourceLoader.KEY_PROFILE
        };

        for (int i = 0; i < 4; i++) {
            final int index = i;
            FrameLayout tabContainer = new FrameLayout(context);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);
            tabContainer.setLayoutParams(tLp);

            ImageView iv = new ImageView(context);
            int iconSize = dp(24);
            FrameLayout.LayoutParams ivLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
            iv.setLayoutParams(ivLp);
            android.graphics.drawable.Drawable tabIcon = ModuleResourceLoader.loadIcon(
                    context, iconDrawables[i], fallbackKeys[i], i == 0 ? 0xFFFFFFFF : 0xA5FFFFFF);
            iv.setImageDrawable(tabIcon);
            iv.setColorFilter(i == 0 ? 0xFFFFFFFF : 0xA5FFFFFF);
            tabContainer.addView(iv);
            tabIconViews[i] = iv;

            tabContainer.setOnClickListener(v -> selectTab(index, true));
            tabsRow.addView(tabContainer);
        }
        tabsPill.addView(tabsRow);

        // Gesture slider support: Drag horizontally to slide between tabs
        tabsPill.setOnTouchListener((v, event) -> handlePillTouchEvent(event));

        bottomRow.addView(tabsPill);

        // 2B. Liquid Glass Circular "+" Button (FAB)
        fabCreate = new FrameLayout(context);
        LinearLayout.LayoutParams fabLp = new LinearLayout.LayoutParams(barHeight, barHeight);
        fabCreate.setLayoutParams(fabLp);
        fabDrawable = new LiquidGlassDrawable(context, FeatureFlags.liquidGlassStyle, FeatureFlags.liquidGlassBorderSheen);
        fabCreate.setBackground(fabDrawable);
        fabCreate.setElevation(dp(12));
        fabCreate.setVisibility(FeatureFlags.liquidGlassShowFab ? VISIBLE : GONE);
        setupCircleOutline(fabCreate, barHeight);

        fabIcon = new ImageView(context);
        FrameLayout.LayoutParams fabIconLp = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER);
        fabIcon.setLayoutParams(fabIconLp);
        android.graphics.drawable.Drawable plusIcon = ModuleResourceLoader.loadIcon(
                context, R.drawable.ic_plus, ModuleResourceLoader.KEY_PLUS, 0xFFFFFFFF);
        fabIcon.setImageDrawable(plusIcon);
        fabIcon.setColorFilter(0xFFFFFFFF);
        fabCreate.addView(fabIcon);

        fabCreate.setOnClickListener(v -> toggleMenu());
        bottomRow.addView(fabCreate);

        addView(bottomRow);

        // Window insets listener: elevate bottom dock above system navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            if (navBottom == 0) {
                navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            }
            updateBottomMargin(navBottom);
            return insets;
        });

        // 3. Popup Creation Menu (Liquid Glass card aligned above the FAB)
        buildPopupMenu(context);
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
                pillLp.rightMargin = showFab ? dp(10) : 0;
                tabsPill.setLayoutParams(pillLp);
            }
            setupPillOutline(tabsPill, barHeightPx, cornerRadius);
            if (tabsPillDrawable != null) {
                tabsPillDrawable.update(style, borderSheen, opacity / 100f, cornerRadius);
            }
        }

        if (sliderIndicator != null) {
            int sliderHeight = Math.max(dp(32), barHeightPx - dp(12));
            ViewGroup.LayoutParams slp = sliderIndicator.getLayoutParams();
            if (slp != null) {
                slp.height = sliderHeight;
                sliderIndicator.setLayoutParams(slp);
            }
            if (sliderIndicator.getBackground() instanceof GradientDrawable) {
                GradientDrawable sliderBg = (GradientDrawable) sliderIndicator.getBackground();
                sliderBg.setCornerRadius(cornerRadius > 0 ? dp(cornerRadius - 4) : (sliderHeight / 2f));
            }
        }

        if (fabCreate != null) {
            fabCreate.setVisibility(showFab ? VISIBLE : GONE);
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

    public void setThemePalette(ps.reso.instaeclipse.mods.ui.theme.IgThemePalette palette) {
        if (palette == null) return;
        try {
            int accent = palette.accent;
            if (sliderIndicator != null && sliderIndicator.getBackground() instanceof GradientDrawable) {
                GradientDrawable gd = (GradientDrawable) sliderIndicator.getBackground();
                int tintBg = (0x35 << 24) | (accent & 0x00FFFFFF);
                int tintStroke = (0x95 << 24) | (accent & 0x00FFFFFF);
                gd.setColor(tintBg);
                gd.setStroke(dp(1.3f), tintStroke);
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

        int menuWidth = dp(190);
        LayoutParams pLp = new LayoutParams(menuWidth, LayoutParams.WRAP_CONTENT);
        pLp.gravity = Gravity.BOTTOM | Gravity.END;
        pLp.rightMargin = dp(14);
        pLp.bottomMargin = dp(80); // floating right above the "+" button
        popupMenu.setLayoutParams(pLp);
        popupMenu.setPivotX(menuWidth - dp(28));
        popupMenu.setPivotY(dp(200));

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

        // Items matching the user screenshot: Reel, Post, Story, Story highlight, Live, AI
        addMenuItem(context, R.drawable.ic_movie, ModuleResourceLoader.KEY_REEL, "Reel", "reel");
        addMenuItem(context, R.drawable.ic_grid_post, ModuleResourceLoader.KEY_POST, "Post", "post");
        addMenuItem(context, R.drawable.ic_story_dashed, ModuleResourceLoader.KEY_STORY, "Story", "story");
        addMenuItem(context, R.drawable.ic_story_highlight, ModuleResourceLoader.KEY_HIGHLIGHT, "Story highlight", "highlight");
        addMenuItem(context, R.drawable.ic_live, ModuleResourceLoader.KEY_LIVE, "Live", "live");
        addMenuItem(context, R.drawable.ic_sparkle, ModuleResourceLoader.KEY_AI, "AI", "ai");

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
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.setClickable(true);
        row.setFocusable(true);

        GradientDrawable rowRipple = new GradientDrawable();
        rowRipple.setShape(GradientDrawable.RECTANGLE);
        rowRipple.setCornerRadius(dp(14));
        rowRipple.setColor(Color.TRANSPARENT);
        row.setBackground(rowRipple);

        ImageView iv = new ImageView(context);
        int iconSize = dp(20);
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        ivLp.rightMargin = dp(14);
        iv.setLayoutParams(ivLp);
        android.graphics.drawable.Drawable itemIcon = ModuleResourceLoader.loadIcon(
                context, iconRes, fallbackKey, 0xFFFFFFFF);
        iv.setImageDrawable(itemIcon);
        iv.setColorFilter(0xFFFFFFFF);
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
        if (index < 0 || index >= 4) return;
        selectedIndex = index;

        for (int i = 0; i < 4; i++) {
            boolean isSel = (i == index);
            tabIconViews[i].setColorFilter(isSel ? 0xFFFFFFFF : 0x90FFFFFF);
            tabIconViews[i].animate()
                    .scaleX(isSel ? 1.15f : 1.0f)
                    .scaleY(isSel ? 1.15f : 1.0f)
                    .setDuration(200)
                    .start();
        }

        updateSliderPosition(index, animate);

        if (tabListener != null && !isPreviewMode) {
            tabListener.onTabSelected(index);
        }
    }

    public void syncSelectedTab(int index) {
        if (index < 0 || index >= 4 || index == selectedIndex) return;
        selectedIndex = index;
        for (int i = 0; i < 4; i++) {
            boolean isSel = (i == index);
            tabIconViews[i].setColorFilter(isSel ? 0xFFFFFFFF : 0x90FFFFFF);
        }
        updateSliderPosition(index, true);
    }

    private void updateSliderPosition(int index, boolean animate) {
        tabsPill.post(() -> {
            int totalW = tabsPill.getWidth();
            if (totalW <= 0) return;
            float tabW = totalW / 4f;
            float sliderW = tabW - dp(10);

            ViewGroup.LayoutParams lp = sliderIndicator.getLayoutParams();
            if (lp.width != (int) sliderW) {
                lp.width = (int) sliderW;
                sliderIndicator.setLayoutParams(lp);
            }

            float targetX = (index * tabW) + dp(5);
            if (animate) {
                sliderIndicator.animate()
                        .translationX(targetX)
                        .setDuration(240)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else {
                sliderIndicator.setTranslationX(targetX);
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
        float tabW = totalW / 4f;

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
                    float sliderW = tabW - dp(10);
                    float curX = event.getX() - (sliderW / 2f);
                    float clampedX = Math.max(dp(4), Math.min(totalW - sliderW - dp(4), curX));
                    sliderIndicator.setTranslationX(clampedX);

                    int hoveredIndex = (int) (event.getX() / tabW);
                    hoveredIndex = Math.max(0, Math.min(3, hoveredIndex));
                    if (hoveredIndex != lastHapticIndex) {
                        lastHapticIndex = hoveredIndex;
                        triggerHapticFeedback();
                        // Dynamically update tab icon visual states during slide
                        for (int k = 0; k < 4; k++) {
                            boolean isHovered = (k == hoveredIndex);
                            tabIconViews[k].setColorFilter(isHovered ? 0xFFFFFFFF : 0x90FFFFFF);
                            tabIconViews[k].setScaleX(isHovered ? 1.15f : 1.0f);
                            tabIconViews[k].setScaleY(isHovered ? 1.15f : 1.0f);
                        }
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (isDraggingSlider) {
                    int finalIndex = (int) (event.getX() / tabW);
                    finalIndex = Math.max(0, Math.min(3, finalIndex));
                    selectTab(finalIndex, true);
                    isDraggingSlider = false;
                } else {
                    int clickedIndex = (int) (event.getX() / tabW);
                    clickedIndex = Math.max(0, Math.min(3, clickedIndex));
                    selectTab(clickedIndex, true);
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                updateSliderPosition(selectedIndex, true);
                isDraggingSlider = false;
                return true;
        }
        return false;
    }

    private void toggleMenu() {
        if (isMenuOpen) {
            closeMenu();
        } else {
            openMenu();
        }
    }

    private void openMenu() {
        isMenuOpen = true;
        scrimOverlay.setVisibility(VISIBLE);
        popupMenu.setVisibility(VISIBLE);

        // Rotate '+' icon to '✕'
        fabIcon.animate()
                .rotation(45f)
                .setDuration(260)
                .setInterpolator(new OvershootInterpolator())
                .start();

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
        fabIcon.animate()
                .rotation(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();

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
