package ps.reso.instaeclipse.ui.theme;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.util.List;

import ps.reso.instaeclipse.R;
import ps.reso.instaeclipse.mods.ui.LiquidGlassSliderBarView;
import ps.reso.instaeclipse.mods.ui.theme.IgThemePalette;
import ps.reso.instaeclipse.mods.ui.theme.ThemePreset;
import ps.reso.instaeclipse.mods.ui.theme.ThemePresets;
import ps.reso.instaeclipse.mods.ui.theme.ThemeSettingsHelper;
import ps.reso.instaeclipse.utils.feature.FeatureFlags;

public class ThemeCustomizerActivity extends AppCompatActivity implements AdvancedColorPickerDialog.Listener {

    private static final String CACHE_NAME = "instaeclipse_cache";
    private static final String KEY_ENABLED = "customThemeEnabled";
    private static final String KEY_PRESET_ID = "themePresetId";
    private static final String KEY_PALETTE_JSON = "themePaletteJson";

    private static final String KEY_NAV_ENABLED = "enableLiquidGlassNavBar";
    private static final String KEY_NAV_STYLE = "liquidGlassStyle";
    private static final String KEY_NAV_LAYOUT = "liquidGlassNavLayout";
    private static final String KEY_NAV_CHROMATIC = "liquidGlassChromaticLens";
    private static final String KEY_NAV_OPACITY = "liquidGlassOpacity";
    private static final String KEY_NAV_MARGIN = "liquidGlassWidthMargin";
    private static final String KEY_NAV_HEIGHT = "liquidGlassHeight";
    private static final String KEY_NAV_RADIUS = "liquidGlassCornerRadius";
    private static final String KEY_NAV_SHEEN = "liquidGlassBorderSheen";
    private static final String KEY_NAV_FAB = "liquidGlassShowFab";
    private static final String KEY_NAV_QUICK_ACTIONS = "enableLiquidGlassQuickActions";

    private static final int[] SLOT_LABELS = {
            R.string.theme_slot_background, R.string.theme_slot_surface, R.string.theme_slot_primary_text,
            R.string.theme_slot_secondary_text, R.string.theme_slot_accent, R.string.theme_slot_button,
            R.string.theme_slot_icon, R.string.theme_slot_glyph, R.string.theme_slot_divider,
            R.string.theme_slot_border, R.string.theme_slot_status_bar, R.string.theme_slot_navigation,
            R.string.theme_slot_link, R.string.theme_slot_error, R.string.theme_slot_destructive
    };
    private static final String STATE_NAV_EXPANDED = "nav_expanded";
    private static final String STATE_PRESETS_EXPANDED = "presets_expanded";
    private static final String STATE_CUSTOM_EXPANDED = "custom_expanded";

    private MaterialSwitch enableSwitch;
    private View navContent;
    private ImageView navExpandIcon;
    private MaterialSwitch navEnableSwitch;
    private LiquidGlassSliderBarView navPreviewSlider;
    private TextView navPreviewTag;
    private ImageView navPreviewBgImage;
    private View navPreviewWpPastel;
    private View navPreviewWpOcean;
    private View navLayoutCard;
    private TextView navLayoutText;
    private View navStyleCard;
    private TextView navStyleText;
    private TextView navOpacityLabel;
    private SeekBar navOpacitySlider;
    private TextView navMarginLabel;
    private SeekBar navMarginSlider;
    private TextView navHeightLabel;
    private SeekBar navHeightSlider;
    private TextView navRadiusLabel;
    private SeekBar navRadiusSlider;
    private MaterialSwitch navSheenSwitch;
    private MaterialSwitch navChromaticSwitch;
    private MaterialSwitch navFabSwitch;
    private MaterialSwitch navQuickActionsSwitch;
    private MaterialButton navResetButton;

    private View presetsContent;
    private View customContent;
    private ImageView presetsExpandIcon;
    private ImageView customExpandIcon;
    private PresetAdapter presetAdapter;
    private ColorSlotAdapter slotAdapter;
    private IgThemePalette workingPalette;
    private String pendingSlotKey;
    private boolean customMode;
    private boolean navExpanded = true;
    private boolean presetsExpanded = true;
    private boolean customExpanded = true;
    private int selectedPresetId = 1;

    private int navStyle = 5;
    private int navLayout = 0;
    private boolean navChromatic = true;
    private int navOpacity = 80;
    private int navMargin = 14;
    private int navHeight = 56;
    private int navRadius = 28;
    private boolean navSheen = true;
    private boolean navFab = true;
    private boolean navQuickActions = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_theme_customizer);

        MaterialToolbar toolbar = findViewById(R.id.theme_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        enableSwitch = findViewById(R.id.theme_enable_switch);
        presetsContent = findViewById(R.id.theme_presets_content);
        customContent = findViewById(R.id.theme_custom_content);
        presetsExpandIcon = findViewById(R.id.theme_presets_expand_icon);
        customExpandIcon = findViewById(R.id.theme_custom_expand_icon);
        RecyclerView presetList = findViewById(R.id.theme_preset_list);
        RecyclerView colorSlots = findViewById(R.id.theme_color_slots);
        MaterialButton resetButton = findViewById(R.id.theme_reset_custom);

        if (savedInstanceState != null) {
            navExpanded = savedInstanceState.getBoolean(STATE_NAV_EXPANDED, true);
            presetsExpanded = savedInstanceState.getBoolean(STATE_PRESETS_EXPANDED, true);
            customExpanded = savedInstanceState.getBoolean(STATE_CUSTOM_EXPANDED, true);
        }
        navContent = findViewById(R.id.theme_nav_content);
        navExpandIcon = findViewById(R.id.theme_nav_expand_icon);
        setupCollapsibleSection(findViewById(R.id.theme_nav_header), navContent, navExpandIcon, navExpanded);
        setupCollapsibleSection(findViewById(R.id.theme_presets_header), presetsContent, presetsExpandIcon, presetsExpanded);
        setupCollapsibleSection(findViewById(R.id.theme_custom_header), customContent, customExpandIcon, customExpanded);

        reloadPaletteState();
        boolean enabled = cache().getBoolean(KEY_ENABLED, false);
        enableSwitch.setChecked(enabled);
        enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> persist());

        initNavBarSection();

        presetAdapter = new PresetAdapter(ThemePresets.all());
        presetList.setLayoutManager(new GridLayoutManager(this, 2));
        presetList.setAdapter(presetAdapter);

        slotAdapter = new ColorSlotAdapter();
        colorSlots.setLayoutManager(new LinearLayoutManager(this));
        colorSlots.setAdapter(slotAdapter);

        resetButton.setOnClickListener(v -> {
            workingPalette = ThemePresets.getById(1).palette.copy();
            customMode = true;
            selectedPresetId = 0;
            presetAdapter.notifyDataSetChanged();
            slotAdapter.notifyDataSetChanged();
            updateNavPreview();
            persist();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadPaletteState();
        if (presetAdapter != null) presetAdapter.notifyDataSetChanged();
        if (slotAdapter != null) slotAdapter.notifyDataSetChanged();
        updateNavPreview();
    }

    private void initNavBarSection() {
        navEnableSwitch = findViewById(R.id.theme_nav_enable_switch);
        navPreviewSlider = findViewById(R.id.theme_nav_preview_slider);
        navPreviewTag = findViewById(R.id.theme_nav_preview_tag);
        navPreviewBgImage = findViewById(R.id.theme_nav_preview_bg_image);
        navPreviewWpPastel = findViewById(R.id.theme_nav_preview_wp_pastel);
        navPreviewWpOcean = findViewById(R.id.theme_nav_preview_wp_ocean);
        navLayoutCard = findViewById(R.id.theme_nav_layout_card);
        navLayoutText = findViewById(R.id.theme_nav_layout_text);
        navStyleCard = findViewById(R.id.theme_nav_style_card);
        navStyleText = findViewById(R.id.theme_nav_style_text);
        navOpacityLabel = findViewById(R.id.theme_nav_opacity_label);
        navOpacitySlider = findViewById(R.id.theme_nav_opacity_slider);
        navMarginLabel = findViewById(R.id.theme_nav_margin_label);
        navMarginSlider = findViewById(R.id.theme_nav_margin_slider);
        navHeightLabel = findViewById(R.id.theme_nav_height_label);
        navHeightSlider = findViewById(R.id.theme_nav_height_slider);
        navRadiusLabel = findViewById(R.id.theme_nav_radius_label);
        navRadiusSlider = findViewById(R.id.theme_nav_radius_slider);
        navSheenSwitch = findViewById(R.id.theme_nav_sheen_switch);
        navChromaticSwitch = findViewById(R.id.theme_nav_chromatic_switch);
        navFabSwitch = findViewById(R.id.theme_nav_fab_switch);
        navQuickActionsSwitch = findViewById(R.id.theme_nav_quick_actions_switch);
        navResetButton = findViewById(R.id.theme_nav_reset);

        // Load values
        boolean navEnabled = cache().getBoolean(KEY_NAV_ENABLED, FeatureFlags.enableLiquidGlassNavBar);
        navStyle = cache().getInt(KEY_NAV_STYLE, FeatureFlags.liquidGlassStyle);
        navLayout = cache().getInt(KEY_NAV_LAYOUT, FeatureFlags.liquidGlassNavLayout);
        navChromatic = cache().getBoolean(KEY_NAV_CHROMATIC, FeatureFlags.liquidGlassChromaticLens);
        navOpacity = cache().getInt(KEY_NAV_OPACITY, FeatureFlags.liquidGlassOpacity);
        navMargin = cache().getInt(KEY_NAV_MARGIN, FeatureFlags.liquidGlassWidthMargin);
        navHeight = cache().getInt(KEY_NAV_HEIGHT, FeatureFlags.liquidGlassHeight);
        navRadius = cache().getInt(KEY_NAV_RADIUS, FeatureFlags.liquidGlassCornerRadius);
        navSheen = cache().getBoolean(KEY_NAV_SHEEN, FeatureFlags.liquidGlassBorderSheen);
        navFab = cache().getBoolean(KEY_NAV_FAB, FeatureFlags.liquidGlassShowFab);
        navQuickActions = cache().getBoolean(KEY_NAV_QUICK_ACTIONS, FeatureFlags.enableLiquidGlassQuickActions);

        navEnableSwitch.setChecked(navEnabled);
        navEnableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            FeatureFlags.enableLiquidGlassNavBar = isChecked;
            persistNavBoolean(KEY_NAV_ENABLED, isChecked);
        });

        if (navPreviewSlider != null) {
            navPreviewSlider.setPreviewMode(true);
            navPreviewSlider.setThemePalette(activePalette());
        }

        // Preview wallpaper switchers
        if (navPreviewWpPastel != null) {
            navPreviewWpPastel.setOnClickListener(v -> {
                if (navPreviewBgImage != null) {
                    navPreviewBgImage.setImageResource(R.drawable.preview_pastel_bg);
                }
            });
        }
        if (navPreviewWpOcean != null) {
            navPreviewWpOcean.setOnClickListener(v -> {
                if (navPreviewBgImage != null) {
                    navPreviewBgImage.setImageResource(R.drawable.preview_ocean_bg);
                }
            });
        }

        if (navLayoutCard != null) {
            navLayoutCard.setOnClickListener(v -> showNavLayoutPicker());
        }

        if (navStyleCard != null) {
            navStyleCard.setOnClickListener(v -> showNavStylePicker());
        }

        // Opacity
        navOpacitySlider.setProgress(navOpacity);
        updateOpacityLabel();
        navOpacitySlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                navOpacity = Math.max(10, progress);
                updateOpacityLabel();
                updateNavPreview();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                FeatureFlags.liquidGlassOpacity = navOpacity;
                persistNavInt(KEY_NAV_OPACITY, navOpacity);
            }
        });

        // Margin / Width
        navMarginSlider.setProgress(navMargin);
        updateMarginLabel();
        navMarginSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                navMargin = progress;
                updateMarginLabel();
                updateNavPreview();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                FeatureFlags.liquidGlassWidthMargin = navMargin;
                persistNavInt(KEY_NAV_MARGIN, navMargin);
            }
        });

        // Height (48 - 68)
        navHeightSlider.setProgress(Math.max(0, Math.min(20, navHeight - 48)));
        updateHeightLabel();
        navHeightSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                navHeight = 48 + progress;
                updateHeightLabel();
                updateNavPreview();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                FeatureFlags.liquidGlassHeight = navHeight;
                persistNavInt(KEY_NAV_HEIGHT, navHeight);
            }
        });

        // Corner Radius (8 - 34)
        navRadiusSlider.setProgress(Math.max(0, Math.min(26, navRadius - 8)));
        updateRadiusLabel();
        navRadiusSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                navRadius = 8 + progress;
                updateRadiusLabel();
                updateNavPreview();
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                FeatureFlags.liquidGlassCornerRadius = navRadius;
                persistNavInt(KEY_NAV_RADIUS, navRadius);
            }
        });

        // Switches
        navSheenSwitch.setChecked(navSheen);
        navSheenSwitch.setOnCheckedChangeListener((bv, isChecked) -> {
            navSheen = isChecked;
            FeatureFlags.liquidGlassBorderSheen = isChecked;
            persistNavBoolean(KEY_NAV_SHEEN, isChecked);
            updateNavPreview();
        });

        if (navChromaticSwitch != null) {
            navChromaticSwitch.setChecked(navChromatic);
            navChromaticSwitch.setOnCheckedChangeListener((bv, isChecked) -> {
                navChromatic = isChecked;
                FeatureFlags.liquidGlassChromaticLens = isChecked;
                persistNavBoolean(KEY_NAV_CHROMATIC, isChecked);
                updateNavPreview();
            });
        }

        navFabSwitch.setChecked(navFab);
        navFabSwitch.setOnCheckedChangeListener((bv, isChecked) -> {
            navFab = isChecked;
            FeatureFlags.liquidGlassShowFab = isChecked;
            persistNavBoolean(KEY_NAV_FAB, isChecked);
            updateNavPreview();
        });

        navQuickActionsSwitch.setChecked(navQuickActions);
        navQuickActionsSwitch.setOnCheckedChangeListener((bv, isChecked) -> {
            navQuickActions = isChecked;
            FeatureFlags.enableLiquidGlassQuickActions = isChecked;
            persistNavBoolean(KEY_NAV_QUICK_ACTIONS, isChecked);
        });

        // Reset
        navResetButton.setOnClickListener(v -> resetNavDefaults());

        updateNavPreview();
    }

    private void updateOpacityLabel() {
        if (navOpacityLabel != null) {
            navOpacityLabel.setText(getString(R.string.liquid_glass_opacity, navOpacity));
        }
    }

    private void updateMarginLabel() {
        if (navMarginLabel != null) {
            navMarginLabel.setText(getString(R.string.liquid_glass_margin_width, navMargin));
        }
    }

    private void updateHeightLabel() {
        if (navHeightLabel != null) {
            navHeightLabel.setText(getString(R.string.liquid_glass_height, navHeight));
        }
    }

    private void updateRadiusLabel() {
        if (navRadiusLabel != null) {
            navRadiusLabel.setText(getString(R.string.liquid_glass_corner_radius, navRadius));
        }
    }

    private void updateNavPreview() {
        if (navPreviewSlider != null) {
            navPreviewSlider.setLayout(navLayout);
            navPreviewSlider.applyConfiguration(navStyle, navOpacity, navMargin, navHeight, navRadius, navSheen, navFab);
            navPreviewSlider.setThemePalette(activePalette());
        }
        if (navLayoutText != null) {
            navLayoutText.setText(getNavLayoutName(navLayout));
        }
        if (navStyleText != null) {
            navStyleText.setText(getNavStyleName(navStyle));
        }
        if (navPreviewTag != null) {
            navPreviewTag.setText(getNavStyleName(navStyle));
        }
    }

    private String getNavLayoutName(int layout) {
        if (layout == LiquidGlassSliderBarView.LAYOUT_REELS_SEARCH) {
            return getString(R.string.liquid_glass_layout_reels_search);
        }
        return getString(R.string.liquid_glass_layout_center_create);
    }

    private void showNavLayoutPicker() {
        String[] labels = {
                getString(R.string.liquid_glass_layout_center_create),
                getString(R.string.liquid_glass_layout_reels_search)
        };
        final int[] values = { LiquidGlassSliderBarView.LAYOUT_CENTER_CREATE, LiquidGlassSliderBarView.LAYOUT_REELS_SEARCH };
        int currentIndex = (navLayout == LiquidGlassSliderBarView.LAYOUT_REELS_SEARCH) ? 1 : 0;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.liquid_glass_layout)
                .setSingleChoiceItems(labels, currentIndex, (dialog, which) -> {
                    navLayout = values[which];
                    FeatureFlags.liquidGlassNavLayout = navLayout;
                    persistNavInt(KEY_NAV_LAYOUT, navLayout);
                    updateNavPreview();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String getNavStyleName(int style) {
        switch (style) {
            case 5: return getString(R.string.liquid_glass_style_ios27);
            case 1: return getString(R.string.liquid_glass_style_docked);
            case 2: return getString(R.string.liquid_glass_style_aurora);
            case 3: return getString(R.string.liquid_glass_style_obsidian);
            case 4: return getString(R.string.liquid_glass_style_crystal);
            case 0:
            default: return getString(R.string.liquid_glass_style_floating);
        }
    }

    private void showNavStylePicker() {
        String[] labels = {
                getString(R.string.liquid_glass_style_ios27),
                getString(R.string.liquid_glass_style_floating),
                getString(R.string.liquid_glass_style_docked),
                getString(R.string.liquid_glass_style_aurora),
                getString(R.string.liquid_glass_style_obsidian),
                getString(R.string.liquid_glass_style_crystal)
        };
        final int[] values = { 5, 0, 1, 2, 3, 4 };
        int currentIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == navStyle) {
                currentIndex = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.liquid_glass_style)
                .setSingleChoiceItems(labels, currentIndex, (dialog, which) -> {
                    navStyle = values[which];
                    FeatureFlags.liquidGlassStyle = navStyle;
                    persistNavInt(KEY_NAV_STYLE, navStyle);
                    updateNavPreview();
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void resetNavDefaults() {
        navStyle = 5;
        navLayout = 0;
        navChromatic = true;
        navOpacity = 80;
        navMargin = 14;
        navHeight = 56;
        navRadius = 28;
        navSheen = true;
        navFab = true;
        navQuickActions = true;

        FeatureFlags.liquidGlassStyle = navStyle;
        FeatureFlags.liquidGlassNavLayout = navLayout;
        FeatureFlags.liquidGlassChromaticLens = navChromatic;
        FeatureFlags.liquidGlassOpacity = navOpacity;
        FeatureFlags.liquidGlassWidthMargin = navMargin;
        FeatureFlags.liquidGlassHeight = navHeight;
        FeatureFlags.liquidGlassCornerRadius = navRadius;
        FeatureFlags.liquidGlassBorderSheen = navSheen;
        FeatureFlags.liquidGlassShowFab = navFab;
        FeatureFlags.enableLiquidGlassQuickActions = navQuickActions;

        SharedPreferences.Editor editor = cache().edit();
        editor.putInt(KEY_NAV_STYLE, navStyle);
        editor.putInt(KEY_NAV_LAYOUT, navLayout);
        editor.putBoolean(KEY_NAV_CHROMATIC, navChromatic);
        editor.putInt(KEY_NAV_OPACITY, navOpacity);
        editor.putInt(KEY_NAV_MARGIN, navMargin);
        editor.putInt(KEY_NAV_HEIGHT, navHeight);
        editor.putInt(KEY_NAV_RADIUS, navRadius);
        editor.putBoolean(KEY_NAV_SHEEN, navSheen);
        editor.putBoolean(KEY_NAV_FAB, navFab);
        editor.putBoolean(KEY_NAV_QUICK_ACTIONS, navQuickActions);
        editor.commit();
        makeCacheWorldReadable();

        persistNavInt(KEY_NAV_STYLE, navStyle);
        persistNavInt(KEY_NAV_LAYOUT, navLayout);
        persistNavBoolean(KEY_NAV_CHROMATIC, navChromatic);
        persistNavInt(KEY_NAV_OPACITY, navOpacity);
        persistNavInt(KEY_NAV_MARGIN, navMargin);
        persistNavInt(KEY_NAV_HEIGHT, navHeight);
        persistNavInt(KEY_NAV_RADIUS, navRadius);
        persistNavBoolean(KEY_NAV_SHEEN, navSheen);
        persistNavBoolean(KEY_NAV_FAB, navFab);
        persistNavBoolean(KEY_NAV_QUICK_ACTIONS, navQuickActions);

        navOpacitySlider.setProgress(navOpacity);
        navMarginSlider.setProgress(navMargin);
        navHeightSlider.setProgress(navHeight - 48);
        navRadiusSlider.setProgress(navRadius - 14);
        navSheenSwitch.setChecked(navSheen);
        if (navChromaticSwitch != null) navChromaticSwitch.setChecked(navChromatic);
        navFabSwitch.setChecked(navFab);
        navQuickActionsSwitch.setChecked(navQuickActions);

        updateOpacityLabel();
        updateMarginLabel();
        updateHeightLabel();
        updateRadiusLabel();
        updateNavPreview();

        Toast.makeText(this, R.string.liquid_glass_reset_confirm, Toast.LENGTH_SHORT).show();
    }

    private void persistNavBoolean(String key, boolean value) {
        cache().edit().putBoolean(key, value).commit();
        makeCacheWorldReadable();
        Intent intent = new Intent("ps.reso.instaeclipse.ACTION_UPDATE_PREF");
        intent.putExtra("key", key);
        intent.putExtra("value", value);
        sendBroadcast(intent);
    }

    private void persistNavInt(String key, int value) {
        cache().edit().putInt(key, value).commit();
        makeCacheWorldReadable();
        Intent intent = new Intent("ps.reso.instaeclipse.ACTION_UPDATE_PREF_INT");
        intent.putExtra("key", key);
        intent.putExtra("value", value);
        sendBroadcast(intent);
    }

    private SharedPreferences cache() {
        return getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE);
    }

    private void reloadPaletteState() {
        selectedPresetId = cache().getInt(KEY_PRESET_ID, 1);
        String paletteJson = cache().getString(KEY_PALETTE_JSON, "");
        customMode = ThemeSettingsHelper.isCustomMode(selectedPresetId);
        workingPalette = ThemeSettingsHelper.resolveEffectivePalette(selectedPresetId, paletteJson);
    }

    private IgThemePalette activePalette() {
        if (!customMode && selectedPresetId > 0) return ThemePresets.getById(selectedPresetId).palette;
        return workingPalette;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_NAV_EXPANDED, navExpanded);
        outState.putBoolean(STATE_PRESETS_EXPANDED, presetsExpanded);
        outState.putBoolean(STATE_CUSTOM_EXPANDED, customExpanded);
    }

    private void setupCollapsibleSection(View header, View content, ImageView icon, boolean expanded) {
        setSectionExpanded(content, icon, expanded);
        header.setOnClickListener(v -> {
            if (content == navContent) {
                navExpanded = !navExpanded;
                setSectionExpanded(content, icon, navExpanded);
            } else if (content == presetsContent) {
                presetsExpanded = !presetsExpanded;
                setSectionExpanded(content, icon, presetsExpanded);
            } else {
                customExpanded = !customExpanded;
                setSectionExpanded(content, icon, customExpanded);
            }
        });
    }

    private void setSectionExpanded(View content, ImageView icon, boolean expanded) {
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        icon.setImageResource(expanded ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        icon.setContentDescription(getString(expanded ? R.string.theme_collapse_section : R.string.theme_expand_section));
    }

    private static String formatColorHex(int color) {
        if (Color.alpha(color) == 255) return String.format("#%06X", 0xFFFFFF & color);
        return String.format("#%08X", color);
    }

    /** Persists to the companion app's local cache and syncs to Instagram, matching every
     *  other setting's broadcast convention in FeaturesFragment. */
    private void persist() {
        boolean enabled = enableSwitch.isChecked();
        int presetId = customMode ? 0 : selectedPresetId;
        String paletteJson = workingPalette.copy().toJson();

        SharedPreferences.Editor editor = cache().edit();
        editor.putBoolean(KEY_ENABLED, enabled);
        editor.putInt(KEY_PRESET_ID, presetId);
        editor.putString(KEY_PALETTE_JSON, paletteJson);
        editor.commit();
        makeCacheWorldReadable();

        Intent enabledIntent = new Intent("ps.reso.instaeclipse.ACTION_UPDATE_PREF");
        enabledIntent.putExtra("key", KEY_ENABLED);
        enabledIntent.putExtra("value", enabled);
        sendBroadcast(enabledIntent);

        Intent presetIntent = new Intent("ps.reso.instaeclipse.ACTION_UPDATE_PREF_INT");
        presetIntent.putExtra("key", KEY_PRESET_ID);
        presetIntent.putExtra("value", presetId);
        sendBroadcast(presetIntent);

        Intent paletteIntent = new Intent("ps.reso.instaeclipse.ACTION_UPDATE_PREF_STRING");
        paletteIntent.putExtra("key", KEY_PALETTE_JSON);
        paletteIntent.putExtra("value", paletteJson);
        sendBroadcast(paletteIntent);

        updateNavPreview();
        Toast.makeText(this, R.string.theme_saved, Toast.LENGTH_SHORT).show();
    }

    private void makeCacheWorldReadable() {
        try {
            File file = new File(getApplicationInfo().dataDir + "/shared_prefs/" + CACHE_NAME + ".xml");
            file.setReadable(true, false);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onColorPicked(int color) {
        if (pendingSlotKey == null) return;
        workingPalette.set(pendingSlotKey, color);
        customMode = true;
        selectedPresetId = 0;
        if (!enableSwitch.isChecked()) enableSwitch.setChecked(true);
        presetAdapter.notifyDataSetChanged();
        slotAdapter.notifyDataSetChanged();
        updateNavPreview();
        persist();
        pendingSlotKey = null;
    }

    private void selectPreset(ThemePreset preset) {
        selectedPresetId = preset.id;
        customMode = false;
        workingPalette = preset.palette.copy();
        if (!enableSwitch.isChecked()) enableSwitch.setChecked(true);
        presetAdapter.notifyDataSetChanged();
        slotAdapter.notifyDataSetChanged();
        updateNavPreview();
        persist();
    }

    private void openPicker(String slotKey, int color, String label) {
        pendingSlotKey = slotKey;
        AdvancedColorPickerDialog.newInstance(label, color).show(getSupportFragmentManager(), "colorPicker");
    }

    private String slotLabel(int position) {
        if (position < 0 || position >= SLOT_LABELS.length) return "";
        return getString(SLOT_LABELS[position]);
    }

    private void bindPreview(LinearLayout container, int[] colors) {
        IgThemePalette.bindCardPreview(this, container, colors);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class PresetAdapter extends RecyclerView.Adapter<PresetAdapter.Holder> {
        private final List<ThemePreset> presets;

        PresetAdapter(List<ThemePreset> presets) {
            this.presets = presets;
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_theme_preset, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            ThemePreset preset = presets.get(position);
            holder.name.setText(ThemePresets.getDisplayName(holder.itemView.getContext(), preset.id));
            bindPreview(holder.preview, preset.palette.previewColors());
            boolean selected = !customMode && preset.id == selectedPresetId;
            int stroke = selected
                    ? MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorPrimary)
                    : MaterialColors.getColor(holder.card, com.google.android.material.R.attr.colorOutline);
            holder.card.setStrokeColor(stroke);
            holder.card.setStrokeWidth(selected ? dp(2) : dp(1));
            holder.card.setOnClickListener(v -> selectPreset(preset));
        }

        @Override
        public int getItemCount() {
            return presets.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final LinearLayout preview;
            final TextView name;

            Holder(View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.theme_preset_card);
                preview = itemView.findViewById(R.id.theme_preset_preview);
                name = itemView.findViewById(R.id.theme_preset_name);
            }
        }
    }

    private class ColorSlotAdapter extends RecyclerView.Adapter<ColorSlotAdapter.Holder> {

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_theme_color_slot, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            String key = IgThemePalette.SLOT_KEYS[position];
            int color = activePalette().get(key);
            holder.label.setText(slotLabel(position));
            holder.hex.setText(formatColorHex(color));
            GradientDrawable swatch = new GradientDrawable();
            swatch.setCornerRadius(dp(10));
            swatch.setColor(color);
            holder.swatch.setBackground(swatch);
            holder.itemView.setOnClickListener(v -> {
                if (!customMode && selectedPresetId > 0) {
                    workingPalette = ThemePresets.getById(selectedPresetId).palette.copy();
                }
                openPicker(key, color, slotLabel(position));
            });
        }

        @Override
        public int getItemCount() {
            return IgThemePalette.SLOT_KEYS.length;
        }

        class Holder extends RecyclerView.ViewHolder {
            final View swatch;
            final TextView label;
            final TextView hex;

            Holder(View itemView) {
                super(itemView);
                swatch = itemView.findViewById(R.id.theme_slot_swatch);
                label = itemView.findViewById(R.id.theme_slot_label);
                hex = itemView.findViewById(R.id.theme_slot_hex);
            }
        }
    }
}
