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
        // Standard Material Home (24x24)
        VECTOR_PATHS.put(KEY_HOME, "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z");

        // Reels / Movie clapperboard (24x24)
        VECTOR_PATHS.put(KEY_REEL, "M18,4l2,4h-3l-2,-4h-2l2,4h-3l-2,-4H8l2,4H7L5,4H4C2.9,4 2.01,4.9 2.01,6L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V4H18z");

        // Heart / Activity (24x24)
        VECTOR_PATHS.put(KEY_HEART, "M16.5,3c-1.74,0 -3.41,0.81 -4.5,2.09C10.91,3.81 9.24,3 7.5,3C4.42,3 2,5.42 2,8.5c0,3.78 3.4,6.86 8.55,11.54L12,21.35l1.45,-1.32C18.6,15.36 22,12.28 22,8.5C22,5.42 19.58,3 16.5,3z");

        // Profile Avatar (24x24)
        VECTOR_PATHS.put(KEY_PROFILE, "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,6c1.93,0 3.5,1.57 3.5,3.5S13.93,13 12,13s-3.5,-1.57 -3.5,-3.5S10.07,6 12,6zM12,20c-2.46,0 -4.68,-1 -6.3,-2.63 0.25,-1.82 3.82,-2.87 6.3,-2.87s6.05,1.05 6.3,2.87C16.68,19 14.46,20 12,20z");

        // Plus FAB (24x24)
        VECTOR_PATHS.put(KEY_PLUS, "M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z");

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
            this.basePath = PathParser.createPathFromPathData(pathData);
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
