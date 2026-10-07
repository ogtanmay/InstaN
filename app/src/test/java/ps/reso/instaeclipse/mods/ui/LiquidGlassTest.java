package ps.reso.instaeclipse.mods.ui;

import org.junit.Test;
import static org.junit.Assert.*;

import ps.reso.instaeclipse.utils.feature.FeatureFlags;
import ps.reso.instaeclipse.utils.feature.FeatureManager;
import ps.reso.instaeclipse.utils.feature.FeatureStatusTracker;

public class LiquidGlassTest {

    @Test
    public void testLiquidGlassFlagsAndStyles() {
        FeatureFlags.enableLiquidGlassNavBar = true;
        FeatureFlags.liquidGlassStyle = LiquidGlassDrawable.STYLE_FLOATING_PILL;
        FeatureFlags.liquidGlassBorderSheen = true;

        assertTrue(FeatureFlags.enableLiquidGlassNavBar);
        assertEquals(LiquidGlassDrawable.STYLE_FLOATING_PILL, FeatureFlags.liquidGlassStyle);
        assertTrue(FeatureFlags.liquidGlassBorderSheen);

        FeatureStatusTracker.setHooked("LiquidGlassNavBar");
        FeatureManager.refreshFeatureStatus();
        assertTrue(FeatureStatusTracker.getStatus().containsKey("LiquidGlassNavBar"));
        assertEquals(Boolean.TRUE, FeatureStatusTracker.getStatus().get("LiquidGlassNavBar"));

        FeatureFlags.enableLiquidGlassNavBar = false;
        FeatureManager.refreshFeatureStatus();
        assertFalse(FeatureStatusTracker.getStatus().containsKey("LiquidGlassNavBar"));
    }

    @Test
    public void testStyleConstants() {
        assertEquals(0, LiquidGlassDrawable.STYLE_FLOATING_PILL);
        assertEquals(1, LiquidGlassDrawable.STYLE_DOCKED);
        assertEquals(2, LiquidGlassDrawable.STYLE_AURORA);
        assertEquals(3, LiquidGlassDrawable.STYLE_OBSIDIAN);
        assertEquals(4, LiquidGlassDrawable.STYLE_CRYSTAL_CLEAR);
    }
}
