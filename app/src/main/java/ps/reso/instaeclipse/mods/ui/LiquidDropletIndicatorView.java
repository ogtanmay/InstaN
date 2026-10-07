package ps.reso.instaeclipse.mods.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * High-fidelity Liquid Glass Droplet Lens Indicator.
 *
 * Simulates physical fluid glass optics inspired by iOS 27 / VisionOS:
 * - Convex refractive fluid droplet body with multi-stop frosted gradients
 * - Prismatic chromatic dispersion rim (rainbow chromatic aberration fringe)
 * - Specular apex light reflection arc
 * - Dynamic fluid stretching during motion
 */
public class LiquidDropletIndicatorView extends View {

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chromaticRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specularRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerSheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF boundsF = new RectF();
    private final RectF strokeBoundsF = new RectF();
    private final RectF sheenBoundsF = new RectF();
    private final Path clipPath = new Path();

    private int style = LiquidGlassDrawable.STYLE_IOS27_LIQUID;
    private boolean chromaticEnabled = true;
    private float cornerRadius = 24f;
    private int accentColor = 0;

    public LiquidDropletIndicatorView(Context context) {
        super(context);
        init();
    }

    public LiquidDropletIndicatorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bodyPaint.setStyle(Paint.Style.FILL);

        chromaticRimPaint.setStyle(Paint.Style.STROKE);
        chromaticRimPaint.setStrokeWidth(dp(1.6f));

        specularRimPaint.setStyle(Paint.Style.STROKE);
        specularRimPaint.setStrokeWidth(dp(1.2f));

        innerSheenPaint.setStyle(Paint.Style.FILL);
    }

    public void setStyle(int style) {
        this.style = style;
        updateShaders();
        invalidate();
    }

    public void setChromaticEnabled(boolean enabled) {
        this.chromaticEnabled = enabled;
        updateShaders();
        invalidate();
    }

    public void setCornerRadius(float radiusPx) {
        this.cornerRadius = radiusPx;
        invalidate();
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        updateShaders();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        boundsF.set(0, 0, w, h);
        float halfStroke = dp(0.8f);
        strokeBoundsF.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke);
        updateShaders();
    }

    private void updateShaders() {
        if (boundsF.isEmpty()) return;

        float width = boundsF.width();
        float height = boundsF.height();

        // 1. Body Liquid Shader
        int[] bodyColors;
        float[] bodyPositions = new float[]{0.0f, 0.45f, 0.85f, 1.0f};

        switch (style) {
            case LiquidGlassDrawable.STYLE_OBSIDIAN:
                // Dark Smoky Obsidian Lens matching row 3 & 4 of reference
                bodyColors = new int[]{
                        0x6E334155, // Frosted slate apex
                        0x4D1E293B,
                        0x3E0F172A,
                        0x55020617
                };
                break;

            case LiquidGlassDrawable.STYLE_AURORA:
                bodyColors = new int[]{
                        0x6A8B5CF6,
                        0x4C6366F1,
                        0x3D06B6D4,
                        0x320284C7
                };
                break;

            case LiquidGlassDrawable.STYLE_CRYSTAL_CLEAR:
                bodyColors = new int[]{
                        0x44FFFFFF,
                        0x22FFFFFF,
                        0x18E2E8F0,
                        0x28CBD5E1
                };
                break;

            case LiquidGlassDrawable.STYLE_IOS27_LIQUID:
            default:
                // Ultra-translucent iOS 27 Fluid Crystal Lens
                if (accentColor != 0) {
                    int tint = (accentColor & 0x00FFFFFF);
                    bodyColors = new int[]{
                            0x58FFFFFF,
                            (0x38 << 24) | tint,
                            (0x28 << 24) | tint,
                            0x34E0E7FF
                    };
                } else {
                    bodyColors = new int[]{
                            0x56FFFFFF, // Diamond apex frost
                            0x32F8FAFC, // Translucent fluid core
                            0x2CE2E8F0, // Refractive optical volume
                            0x3AE0E7FF  // Prismatic sky-violet caustic base
                    };
                }
                break;
        }

        LinearGradient bodyShader = new LinearGradient(
                boundsF.centerX(), boundsF.top,
                boundsF.centerX(), boundsF.bottom,
                bodyColors, bodyPositions, Shader.TileMode.CLAMP
        );
        bodyPaint.setShader(bodyShader);

        // 2. Chromatic Dispersion Rim (Rainbow caustic fringe as seen in reference image)
        int[] chromaticColors;
        float[] chromaticPositions = new float[]{0.0f, 0.22f, 0.50f, 0.78f, 1.0f};

        if (chromaticEnabled && style == LiquidGlassDrawable.STYLE_IOS27_LIQUID) {
            // Iridescent prismatic spectrum: Pink -> Amber -> Diamond White -> Cyan -> Violet
            chromaticColors = new int[]{
                    0xF8F472B6, // Warm Magenta/Pink dispersion on left edge
                    0xFAFBBF24, // Warm Amber/Gold caustic
                    0xFFFFFFFF, // Pure diamond specular point at top center
                    0xF538BDF8, // Cool Neon Sky Blue caustic on right edge
                    0xEE818CF8  // Cool Electric Indigo dispersion
            };
        } else if (style == LiquidGlassDrawable.STYLE_OBSIDIAN) {
            // Titanium specular rim with subtle bluish refraction
            chromaticColors = new int[]{
                    0x9094A3B8,
                    0x7064748B,
                    0xDDFFFFFF,
                    0x6064748B,
                    0x8594A3B8
            };
        } else {
            // Clean Diamond Specular Rim
            chromaticColors = new int[]{
                    0x95FFFFFF,
                    0x60FFFFFF,
                    0xFAFFFFFF,
                    0x60FFFFFF,
                    0x95FFFFFF
            };
        }

        LinearGradient chromaticShader = new LinearGradient(
                strokeBoundsF.left, strokeBoundsF.centerY(),
                strokeBoundsF.right, strokeBoundsF.centerY(),
                chromaticColors, chromaticPositions, Shader.TileMode.CLAMP
        );
        chromaticRimPaint.setShader(chromaticShader);

        // 3. Inner Convex Sheen Arc
        float sheenHeight = dp(2.2f);
        sheenBoundsF.set(boundsF.left + dp(6f), boundsF.top + dp(1.5f),
                boundsF.right - dp(6f), boundsF.top + dp(1.5f) + sheenHeight);

        LinearGradient sheenShader = new LinearGradient(
                sheenBoundsF.left, sheenBoundsF.centerY(),
                sheenBoundsF.right, sheenBoundsF.centerY(),
                new int[]{0x00FFFFFF, 0xAAFFFFFF, 0xAAFFFFFF, 0x00FFFFFF},
                new float[]{0.0f, 0.25f, 0.75f, 1.0f},
                Shader.TileMode.CLAMP
        );
        innerSheenPaint.setShader(sheenShader);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (boundsF.isEmpty()) return;

        float r = cornerRadius > 0 ? cornerRadius : (boundsF.height() / 2f);
        clipPath.reset();
        clipPath.addRoundRect(boundsF, r, r, Path.Direction.CW);

        // 1. Draw Liquid Glass Body
        canvas.drawRoundRect(boundsF, r, r, bodyPaint);

        // 2. Draw Top Convex Sheen Arc
        if (!sheenBoundsF.isEmpty()) {
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawRoundRect(sheenBoundsF, dp(1.2f), dp(1.2f), innerSheenPaint);
            canvas.restore();
        }

        // 3. Draw Chromatic Dispersion Rim
        float strokeR = Math.max(0, r - dp(0.8f));
        canvas.drawRoundRect(strokeBoundsF, strokeR, strokeR, chromaticRimPaint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
