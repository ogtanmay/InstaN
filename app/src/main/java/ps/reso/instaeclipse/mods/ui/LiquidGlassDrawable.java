package ps.reso.instaeclipse.mods.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ps.reso.instaeclipse.utils.feature.FeatureFlags;

/**
 * Custom glassmorphism drawable implementing the "Liquid Glass" effect
 * for bottom navigation bars.
 *
 * Features:
 * - Multi-stop translucent glass gradient matching iOS 27 / VisionOS liquid glass aesthetics
 * - Specular reflection rim with top-weighted refraction highlight
 * - Inner optical sheen arc simulating convex fluid glass curvature
 * - 6 curated styles: Floating Pill, Docked Glass, Aurora Neon, Obsidian Dark, Crystal Clear, iOS 27 Liquid Glass
 * - Zero-allocation draw() method to prevent GC pauses on 2GB RAM devices
 */
public class LiquidGlassDrawable extends Drawable {

    public static final int STYLE_FLOATING_PILL = 0;
    public static final int STYLE_DOCKED = 1;
    public static final int STYLE_AURORA = 2;
    public static final int STYLE_OBSIDIAN = 3;
    public static final int STYLE_CRYSTAL_CLEAR = 4;
    public static final int STYLE_IOS27_LIQUID = 5;

    private final Context context;
    private int style;
    private boolean showSheen;

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF boundsF = new RectF();
    private final RectF strokeBoundsF = new RectF();
    private final RectF sheenBoundsF = new RectF();
    private final RectF innerRect = new RectF();

    private final Path clipPath = new Path();
    private final Path strokePath = new Path();
    private final Path innerPath = new Path();

    private float cornerRadius = 0f;
    private final float[] cornerRadii = new float[8];
    private float opacityMultiplier = 1.0f;
    private float customCornerRadius = -1f;

    public LiquidGlassDrawable(Context context, int style, boolean showSheen) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.style = style;
        this.showSheen = showSheen;
        this.opacityMultiplier = Math.max(0.15f, Math.min(1.0f, FeatureFlags.liquidGlassOpacity / 100f));
        this.customCornerRadius = FeatureFlags.liquidGlassCornerRadius;

        strokePaint.setStyle(Paint.Style.STROKE);
        sheenPaint.setStyle(Paint.Style.FILL);
        innerGlowPaint.setStyle(Paint.Style.STROKE);
    }

    public void updateStyle(int style, boolean showSheen) {
        this.style = style;
        this.showSheen = showSheen;
        invalidateShader();
        invalidateSelf();
    }

    public void setOpacity(float opacity) {
        this.opacityMultiplier = Math.max(0.15f, Math.min(1.0f, opacity));
        invalidateShader();
        invalidateSelf();
    }

    public void setCustomCornerRadius(float radiusDp) {
        this.customCornerRadius = radiusDp;
        invalidateShader();
        invalidateSelf();
    }

    public void update(int style, boolean showSheen, float opacity, float radiusDp) {
        this.style = style;
        this.showSheen = showSheen;
        this.opacityMultiplier = Math.max(0.15f, Math.min(1.0f, opacity));
        this.customCornerRadius = radiusDp;
        invalidateShader();
        invalidateSelf();
    }

    private boolean isDarkMode() {
        if (context == null) return true;
        try {
            int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return nightMode != Configuration.UI_MODE_NIGHT_NO;
        } catch (Throwable ignored) {
            return true;
        }
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        boundsF.set(bounds);
        invalidateShader();
    }

    private void invalidateShader() {
        if (boundsF.isEmpty()) return;

        float width = boundsF.width();
        float height = boundsF.height();
        boolean dark = isDarkMode();

        float strokeWidth = dp(1.35f);
        strokePaint.setStrokeWidth(strokeWidth);
        float halfStroke = strokeWidth / 2f;
        strokeBoundsF.set(boundsF.left + halfStroke, boundsF.top + halfStroke,
                boundsF.right - halfStroke, boundsF.bottom - halfStroke);

        // Corner radii setup
        if (style == STYLE_DOCKED) {
            float topRadius = customCornerRadius > 0 ? dp(customCornerRadius) : dp(22f);
            cornerRadius = topRadius;
            cornerRadii[0] = topRadius; cornerRadii[1] = topRadius;
            cornerRadii[2] = topRadius; cornerRadii[3] = topRadius;
            cornerRadii[4] = 0f; cornerRadii[5] = 0f;
            cornerRadii[6] = 0f; cornerRadii[7] = 0f;
        } else {
            // Pill shape: rounded by customCornerRadius or height/2
            cornerRadius = customCornerRadius > 0 ? dp(customCornerRadius) : Math.min(height / 2f, dp(28f));
            for (int i = 0; i < 8; i++) cornerRadii[i] = cornerRadius;
        }

        // Pre-build paths once when bounds/radii change (zero per-frame allocations)
        clipPath.reset();
        clipPath.addRoundRect(boundsF, cornerRadii, Path.Direction.CW);

        strokePath.reset();
        strokePath.addRoundRect(strokeBoundsF, cornerRadii, Path.Direction.CW);

        innerRect.set(boundsF);
        float inset = dp(1.8f);
        innerRect.inset(inset, inset);
        innerPath.reset();
        innerPath.addRoundRect(innerRect, cornerRadii, Path.Direction.CW);

        int[] bodyColors;
        float[] bodyPositions;
        int[] strokeColors;
        float[] strokePositions;

        switch (style) {
            case STYLE_AURORA:
                // Neon magenta/cyan holographic glass
                bodyColors = new int[]{
                        0x557C3AED, // Top vibrant violet
                        0x324F46E5, // Mid electric indigo
                        0x3806B6D4, // Bottom neon cyan
                        0x220284C7
                };
                bodyPositions = new float[]{0.0f, 0.45f, 0.85f, 1.0f};
                strokeColors = new int[]{
                        0xD5C084FC, // Bright lavender-pink sheen
                        0x80818CF8,
                        0x5022D3EE,
                        0x7038BDF8
                };
                strokePositions = new float[]{0.0f, 0.35f, 0.7f, 1.0f};
                break;

            case STYLE_OBSIDIAN:
                // Stealth smoky obsidian glass
                bodyColors = new int[]{
                        0x5E1E2028, // Top smoke
                        0x4012141B,
                        0x460B0C10,
                        0x35060709
                };
                bodyPositions = new float[]{0.0f, 0.4f, 0.8f, 1.0f};
                strokeColors = new int[]{
                        0x606B7280, // Subtle titanium specular rim
                        0x354B5563,
                        0x20374151,
                        0x281F2937
                };
                strokePositions = new float[]{0.0f, 0.3f, 0.7f, 1.0f};
                break;

            case STYLE_CRYSTAL_CLEAR:
                // Ultra minimal high-refraction clear liquid glass
                if (dark) {
                    bodyColors = new int[]{
                            0x28FFFFFF,
                            0x14FFFFFF,
                            0x14000000,
                            0x20000000
                    };
                } else {
                    bodyColors = new int[]{
                            0x42FFFFFF,
                            0x24FFFFFF,
                            0x1CE2E8F0,
                            0x26CBD5E1
                    };
                }
                bodyPositions = new float[]{0.0f, 0.35f, 0.75f, 1.0f};
                strokeColors = new int[]{
                        0x90FFFFFF,
                        0x40FFFFFF,
                        0x18FFFFFF,
                        0x25FFFFFF
                };
                strokePositions = new float[]{0.0f, 0.25f, 0.7f, 1.0f};
                break;

            case STYLE_IOS27_LIQUID:
                // Ultra-futuristic iOS 27 Liquid Glass with crystal optical translucency and chromatic dispersion
                if (dark) {
                    bodyColors = new int[]{
                            0x2A1E293B, // Ultra-translucent crystal sapphire slate (16% opacity)
                            0x180F172A, // Mid-depth fluid glass refraction (9% opacity)
                            0x10020617, // Deep inner volume refraction (6% opacity)
                            0x1E1E1B4B  // Subtle chromatic iridescent bottom base (12% opacity)
                    };
                    bodyPositions = new float[]{0.0f, 0.35f, 0.70f, 1.0f};
                    strokeColors = new int[]{
                            0xFAFFFFFF, // Pure diamond apex specular point (98% white)
                            0xB8C4B5FD, // Prismatic violet-lavender dispersion
                            0x8538BDF8, // Refractive cyan caustic highlight
                            0x7E818CF8  // Subsurface ambient rim
                    };
                    strokePositions = new float[]{0.0f, 0.28f, 0.65f, 1.0f};
                } else {
                    bodyColors = new int[]{
                            0x32FFFFFF, // Pure crystal fluid glass (19% opacity - crystal clear!)
                            0x1CF8FAFC, // Liquid water reflection (11% opacity)
                            0x14E2E8F0, // Translucent optical glass (8% opacity)
                            0x1EE0E7FF  // Delicate sky-violet refraction base (12% opacity)
                    };
                    bodyPositions = new float[]{0.0f, 0.35f, 0.70f, 1.0f};
                    strokeColors = new int[]{
                            0xFAFFFFFF, // Diamond apex specular reflection (98% white)
                            0xB8E0E7FF, // Prismatic sky-indigo dispersion
                            0x8538BDF8, // Caustic light refraction
                            0x88A5B4FC  // Delicate iridescent edge
                    };
                    strokePositions = new float[]{0.0f, 0.28f, 0.65f, 1.0f};
                }
                break;

            case STYLE_DOCKED:
            case STYLE_FLOATING_PILL:
            default:
                if (dark) {
                    bodyColors = new int[]{
                            0x3A374151, // Soft translucent slate
                            0x261F2937,
                            0x2C111827,
                            0x320B0F17
                    };
                    bodyPositions = new float[]{0.0f, 0.4f, 0.75f, 1.0f};
                    strokeColors = new int[]{
                            0x95FFFFFF, // Bright specular rim at top edge
                            0x4594A3B8,
                            0x2064748B,
                            0x35475569
                    };
                    strokePositions = new float[]{0.0f, 0.3f, 0.75f, 1.0f};
                } else {
                    bodyColors = new int[]{
                            0x70FFFFFF, // Top bright translucent white
                            0x48F8FAFC,
                            0x50EDF2F7,
                            0x5AE2E8F0
                    };
                    bodyPositions = new float[]{0.0f, 0.35f, 0.7f, 1.0f};
                    strokeColors = new int[]{
                            0xC5FFFFFF, // Bright white specular reflection
                            0x70E2E8F0,
                            0x40CBD5E1,
                            0x5594A3B8
                    };
                    strokePositions = new float[]{0.0f, 0.3f, 0.75f, 1.0f};
                }
                break;
        }

        if (opacityMultiplier < 0.99f) {
            for (int i = 0; i < bodyColors.length; i++) {
                int a = (bodyColors[i] >>> 24);
                int newA = Math.round(a * opacityMultiplier);
                bodyColors[i] = (newA << 24) | (bodyColors[i] & 0x00FFFFFF);
            }
        }

        LinearGradient bodyShader = new LinearGradient(
                boundsF.centerX(), boundsF.top,
                boundsF.centerX(), boundsF.bottom,
                bodyColors, bodyPositions, Shader.TileMode.CLAMP
        );
        bodyPaint.setShader(bodyShader);

        LinearGradient strokeShader = new LinearGradient(
                strokeBoundsF.centerX(), strokeBoundsF.top,
                strokeBoundsF.centerX(), strokeBoundsF.bottom,
                strokeColors, strokePositions, Shader.TileMode.CLAMP
        );
        strokePaint.setShader(strokeShader);

        // Optical Sheen line along top curvature
        float sheenHeight = dp(1.8f);
        sheenBoundsF.set(boundsF.left + dp(8f), boundsF.top + dp(1.2f),
                boundsF.right - dp(8f), boundsF.top + dp(1.2f) + sheenHeight);

        int sheenAlpha = dark ? 0x65 : 0x88;
        int sheenColor = (sheenAlpha << 24) | 0x00FFFFFF;
        LinearGradient sheenShader = new LinearGradient(
                sheenBoundsF.left, sheenBoundsF.centerY(),
                sheenBoundsF.right, sheenBoundsF.centerY(),
                new int[]{0x00FFFFFF, sheenColor, sheenColor, 0x00FFFFFF},
                new float[]{0.0f, 0.25f, 0.75f, 1.0f},
                Shader.TileMode.CLAMP
        );
        sheenPaint.setShader(sheenShader);

        innerGlowPaint.setStyle(Paint.Style.STROKE);
        innerGlowPaint.setStrokeWidth(dp(1.0f));
        innerGlowPaint.setColor(dark ? 0x24FFFFFF : 0x45FFFFFF);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (boundsF.isEmpty()) return;

        // 1. Draw Liquid Glass Body
        canvas.drawPath(clipPath, bodyPaint);

        // 2. Draw Top Specular Curved Sheen Reflection
        if (showSheen && !sheenBoundsF.isEmpty()) {
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawRoundRect(sheenBoundsF, dp(1f), dp(1f), sheenPaint);
            canvas.restore();
        }

        // 3. Draw Specular Refraction Rim (Border)
        canvas.drawPath(strokePath, strokePaint);

        // 4. Draw Inner Volumetric Caustics / Prismatic Glow for iOS 27
        if (style == STYLE_IOS27_LIQUID && !innerRect.isEmpty()) {
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawPath(innerPath, innerGlowPaint);
            canvas.restore();
        }
    }

    @Override
    public void setAlpha(int alpha) {
        bodyPaint.setAlpha(alpha);
        strokePaint.setAlpha(alpha);
        sheenPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        bodyPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
        sheenPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private float dp(float val) {
        float density = 2.0f;
        if (context != null) {
            try {
                density = context.getResources().getDisplayMetrics().density;
            } catch (Throwable ignored) {}
        }
        return val * density;
    }
}
