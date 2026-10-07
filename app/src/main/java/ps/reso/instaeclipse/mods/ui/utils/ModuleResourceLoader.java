package ps.reso.instaeclipse.mods.ui.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.XModuleResources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.PathParser;

import java.util.HashMap;
import java.util.Map;

import ps.reso.instaeclipse.Xposed.Module;
import ps.reso.instaeclipse.utils.core.CommonUtils;

/**
 * Robust resource loader for InstaEclipse hooks running inside Instagram.
 *
 * Prevents cross-APK resource collisions where passing raw R.drawable IDs to Instagram's
 * ImageViews would cause Instagram to look up its OWN internal resource table and return
 * random drawables (such as camera filter circles).
 *
 * Tier 1: Try XModuleResources with moduleSourceDir.
 * Tier 2: Try createPackageContext with CommonUtils.MY_PACKAGE_NAME.
 * Tier 3: Pixel-perfect vector glyph fallback constructed directly in code.
 */
public final class ModuleResourceLoader {

    public static final String KEY_HOME = "home";
    public static final String KEY_REEL = "reel";
    public static final String KEY_DIRECT = "direct";
    public static final String KEY_SEARCH = "search";
    public static final String KEY_HEART = "heart";
    public static final String KEY_PROFILE = "profile";
    public static final String KEY_PLUS = "plus";
    public static final String KEY_POST = "post";
    public static final String KEY_STORY = "story";
    public static final String KEY_HIGHLIGHT = "highlight";
    public static final String KEY_LIVE = "live";
    public static final String KEY_AI = "ai";

    private static final Map<String, String> VECTOR_PATHS = new HashMap<>();
    private static final Map<String, Boolean> STROKE_ONLY = new HashMap<>();

    static {
        // Authentic iOS / Instagram Rounded Home (24x24)
        VECTOR_PATHS.put(KEY_HOME, "M3.5,10.2 L10.8,4.1 C11.5,3.5 12.5,3.5 13.2,4.1 L20.5,10.2 C21.1,10.7 21.5,11.5 21.5,12.3 L21.5,18.5 C21.5,19.9 20.4,21 19,21 L5,21 C3.6,21 2.5,19.9 2.5,18.5 L2.5,12.3 C2.5,11.5 2.9,10.7 3.5,10.2 Z M9.5,21 L9.5,15 C9.5,13.9 10.4,13 11.5,13 L12.5,13 C13.6,13 14.5,13.9 14.5,15 L14.5,21");
        STROKE_ONLY.put(KEY_HOME, true);

        // Authentic Instagram Reels Clapper with Play Triangle (24x24)
        VECTOR_PATHS.put(KEY_REEL, "M6.8,3.5 L17.2,3.5 C19.6,3.5 21.5,5.4 21.5,7.8 L21.5,16.2 C21.5,18.6 19.6,20.5 17.2,20.5 L6.8,20.5 C4.4,20.5 2.5,18.6 2.5,16.2 L2.5,7.8 C2.5,5.4 4.4,3.5 6.8,3.5 Z M10.2,9 L15.8,12 L10.2,15 Z M2.6,8.5 L21.4,8.5 M9.2,3.6 L7.4,8.5 M15.8,3.6 L14.0,8.5");
        STROKE_ONLY.put(KEY_REEL, true);

        // Authentic Instagram Paper Plane / Direct / Share (24x24)
        VECTOR_PATHS.put(KEY_DIRECT, "M21.5,2.5 L10.0,14.0 M21.5,2.5 L14.5,21.5 L10.0,14.0 L2.5,9.5 L21.5,2.5 Z");
        STROKE_ONLY.put(KEY_DIRECT, true);

        // Authentic Modern Search Magnifying Glass (24x24)
        VECTOR_PATHS.put(KEY_SEARCH, "M10.5,17 C14.09,17 17,14.09 17,10.5 C17,6.91 14.09,4 10.5,4 C6.91,4 4,6.91 4,10.5 C4,14.09 6.91,17 10.5,17 Z M15.5,15.5 L20.5,20.5");
        STROKE_ONLY.put(KEY_SEARCH, true);

        // Authentic Instagram Rounded Heart (24x24)
        VECTOR_PATHS.put(KEY_HEART, "M12,21.35 L10.55,20.03 C5.4,15.36 2,12.28 2,8.5 C2,5.42 4.42,3 7.5,3 C9.24,3 10.91,3.81 12,5.09 C13.09,3.81 14.76,3 16.5,3 C19.58,3 22,5.42 22,8.5 C22,12.28 18.6,15.36 13.45,20.04 L12,21.35 Z");
        STROKE_ONLY.put(KEY_HEART, true);

        // Authentic Instagram Profile Avatar (24x24)
        VECTOR_PATHS.put(KEY_PROFILE, "M12,11.5 C14.21,11.5 16,9.71 16,7.5 C16,5.29 14.21,3.5 12,3.5 C9.79,3.5 8,5.29 8,7.5 C8,9.71 9.79,11.5 12,11.5 Z M4.5,20.5 C4.5,16.5 8,14.5 12,14.5 C16,14.5 19.5,16.5 19.5,20.5");
        STROKE_ONLY.put(KEY_PROFILE, true);

        // Center Plus (24x24)
        VECTOR_PATHS.put(KEY_PLUS, "M12,5 L12,19 M5,12 L19,12");
        STROKE_ONLY.put(KEY_PLUS, true);

        // Grid Post (24x24)
        VECTOR_PATHS.put(KEY_POST, "M20,4H4C2.9,4 2,4.9 2,6v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V6C22,4.9 21.1,4 20,4zM8,18H4v-4h4V18zM8,12H4V8h4V12zM14,18h-4v-4h4V18zM14,12h-4V8h4V12zM20,18h-4v-4h4V18zM20,12h-4V8h4V12z");

        // Story ring (24x24)
        VECTOR_PATHS.put(KEY_STORY, "M12,2A10,10 0 1,0 22,12A10,10 0 0,0 12,2zM12,20A8,8 0 1,1 20,12A8,8 0 0,1 12,20z");

        // Story Highlight Star (24x24)
        VECTOR_PATHS.put(KEY_HIGHLIGHT, "M12,17.27L18.18,21l-1.64-7.03L22,9.24l-7.19-.61L12,2 9.19,8.63 2,9.24l5.46 4.73L5.82,21z");

        // Live (24x24)
        VECTOR_PATHS.put(KEY_LIVE, "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zm-2,14.5v-9l6,4.5-6,4.5z");

        // AI Sparkle (24x24)
        VECTOR_PATHS.put(KEY_AI, "M19,9l1.25,-2.75L23,5l-2.75,-1.25L19,1l-1.25,2.75L15,5l2.75,1.25L19,9z M11.5,9.5L9,4 6.5,9.5 1,12l5.5,2.5L9,20l2.5,-5.5L17,12l-5.5,-2.5z M19,15l-1.25,2.75L15,19l2.75,1.25L19,23l1.25,-2.75L23,19l-2.75,-1.25L19,15z");
    }

    private ModuleResourceLoader() {}

    @Nullable
    public static Drawable loadIcon(Context context, int resId, String fallbackKey) {
        return loadIcon(context, resId, fallbackKey, Color.WHITE);
    }

    @Nullable
    public static Drawable loadIcon(Context context, int resId, String fallbackKey, int tintColor) {
        if (context != null) {
            // Case 1: Running in companion app itself
            if (CommonUtils.MY_PACKAGE_NAME.equals(context.getPackageName())) {
                try {
                    Drawable d = ContextCompat.getDrawable(context, resId);
                    if (d != null) return tint(d, tintColor);
                } catch (Throwable ignored) {}
            }

            // Case 2: Running in Instagram via Xposed with moduleSourceDir
            if (Module.moduleSourceDir != null) {
                try {
                    @SuppressLint("UseCompatLoadingForDrawables")
                    Drawable d = XModuleResources.createInstance(Module.moduleSourceDir, null)
                            .getDrawable(resId, null);
                    if (d != null) return tint(d, tintColor);
                } catch (Throwable ignored) {}
            }

            // Case 3: Running in Instagram via createPackageContext
            try {
                Context modCtx = context.createPackageContext(CommonUtils.MY_PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY);
                Drawable d = ContextCompat.getDrawable(modCtx, resId);
                if (d != null) return tint(d, tintColor);
            } catch (Throwable ignored) {}
        }

        // Case 4: Self-contained vector fallback (100% reliable)
        return createFallbackGlyph(fallbackKey, tintColor);
    }

    @Nullable
    public static Drawable createFallbackGlyph(String key, int color) {
        String pathData = VECTOR_PATHS.get(key);
        if (pathData == null) return null;
        boolean stroke = Boolean.TRUE.equals(STROKE_ONLY.get(key));
        return new VectorGlyphDrawable(pathData, color, stroke);
    }

    private static Drawable tint(Drawable d, int color) {
        Drawable m = d.mutate();
        m.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        return m;
    }

    public static class VectorGlyphDrawable extends Drawable {
        private final Path basePath;
        private final Path scaledPath = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color;
        private final boolean isStroke;

        public VectorGlyphDrawable(String pathData, int color, boolean isStroke) {
            Path p = null;
            try {
                p = PathParser.createPathFromPathData(pathData);
            } catch (Throwable ignored) {}
            this.basePath = p;
            this.color = color;
            this.isStroke = isStroke;
            paint.setColor(color);
            if (isStroke) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.0f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
            } else {
                paint.setStyle(Paint.Style.FILL);
            }
        }

        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            super.onBoundsChange(bounds);
            if (bounds.isEmpty() || basePath == null) return;
            Matrix matrix = new Matrix();
            float scaleX = bounds.width() / 24f;
            float scaleY = bounds.height() / 24f;
            matrix.setScale(scaleX, scaleY);
            matrix.postTranslate(bounds.left, bounds.top);
            scaledPath.reset();
            basePath.transform(matrix, scaledPath);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (scaledPath.isEmpty()) return;
            canvas.drawPath(scaledPath, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        public void setColor(int color) {
            this.color = color;
            paint.setColor(color);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return 72; // default ~24dp at 3x
        }

        @Override
        public int getIntrinsicHeight() {
            return 72;
        }
    }
}
