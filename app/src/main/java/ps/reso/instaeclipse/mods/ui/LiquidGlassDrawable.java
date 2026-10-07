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

/**
 * Custom glassmorphism drawable implementing the "Liquid Glass" effect
 * for Instagram's bottom navigation bar.
 *
 * Features:
 * - Multi-stop translucent glass gradient matching iOS 18 / VisionOS glass aesthetics
 * - Specular reflection rim with top-weighted refraction highlight
 * - Inner optical sheen arc simulating convex fluid glass curvature
 * - 5 curated styles: Floating Pill, Docked Glass, Aurora Neon, Obsidian Dark, Crystal Clear
 * - Adaptive light and dark theme palette resolution
 */
public class LiquidGlassDrawable extends Drawable {

    public static final int STYLE_FLOATING_PILL = 0;
    public static final int STYLE_DOCKED = 1;
    public static final int STYLE_AURORA = 2;
    public static final int STYLE_OBSIDIAN = 3;
    public static final int STYLE_CRYSTAL_CLEAR = 4;

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
    private final Path clipPath = new Path();

    private float cornerRadius = 0f;
    private final float[] cornerRadii = new float[8];

    public LiquidGlassDrawable(Context context, int style, boolean showSheen) {
        this.context = context.getApplicationContext();
        this.style = style;
        this.showSheen = showSheen;

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

    private boolean isDarkMode() {
        if (context == null) return true;
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode != Configuration.UI_MODE_NIGHT_NO;
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
            float topRadius = dp(22f);
            cornerRadius = topRadius;
            cornerRadii[0] = topRadius; cornerRadii[1] = topRadius;
            cornerRadii[2] = topRadius; cornerRadii[3] = topRadius;
            cornerRadii[4] = 0f; cornerRadii[5] = 0f;
            cornerRadii[6] = 0f; cornerRadii[7] = 0f;
        } else {
            // Pill shape: rounded by height/2 (capped at 28dp)
            cornerRadius = Math.min(height / 2f, dp(28f));
            for (int i = 0; i < 8; i++) cornerRadii[i] = cornerRadius;
        }

        int[] bodyColors;
        float[] bodyPositions;
        int[] strokeColors;
        float[] strokePositions;

        switch (style) {
            case STYLE_AURORA:
                // Neon magenta/cyan holographic glass
                bodyColors = new int[]{
                        0x607C3AED, // Top vibrant violet
                        0x384F46E5, // Mid electric indigo
                        0x4006B6D4, // Bottom neon cyan
                        0x280284C7
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
                        0x6A1E2028, // Top smoke
                        0x4C12141B,
                        0x550B0C10,
                        0x40060709
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
                            0x32FFFFFF,
                            0x18FFFFFF,
                            0x1C000000,
                            0x25000000
                    };
                } else {
                    bodyColors = new int[]{
                            0x50FFFFFF,
                            0x30FFFFFF,
                            0x25E2E8F0,
                            0x30CBD5E1
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

            case STYLE_DOCKED:
            case STYLE_FLOATING_PILL:
            default:
                if (dark) {
                    // Dark Mode Frosted Glass
                    bodyColors = new int[]{
                            0x4C374151, // Top soft frosted slate
                            0x321F2937,
                            0x38111827,
                            0x420B0F17
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
                    // Light Mode Frosted Glass
                    bodyColors = new int[]{
                            0x90FFFFFF, // Top bright frosted white
                            0x60F8FAFC,
                            0x68EDF2F7,
                            0x75E2E8F0
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
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (boundsF.isEmpty()) return;

        clipPath.reset();
        clipPath.addRoundRect(boundsF, cornerRadii, Path.Direction.CW);

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
        Path strokePath = new Path();
        strokePath.addRoundRect(strokeBoundsF, cornerRadii, Path.Direction.CW);
        canvas.drawPath(strokePath, strokePaint);
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
        return val * context.getResources().getDisplayMetrics().density;
    }
}
