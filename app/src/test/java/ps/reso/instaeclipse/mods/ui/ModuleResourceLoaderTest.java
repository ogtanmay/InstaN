package ps.reso.instaeclipse.mods.ui;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import ps.reso.instaeclipse.mods.ui.utils.ModuleResourceLoader;

public class ModuleResourceLoaderTest {

    @Test
    public void testKeyConstants() {
        assertEquals("home", ModuleResourceLoader.KEY_HOME);
        assertEquals("reel", ModuleResourceLoader.KEY_REEL);
        assertEquals("heart", ModuleResourceLoader.KEY_HEART);
        assertEquals("profile", ModuleResourceLoader.KEY_PROFILE);
        assertEquals("plus", ModuleResourceLoader.KEY_PLUS);
        assertEquals("post", ModuleResourceLoader.KEY_POST);
        assertEquals("story", ModuleResourceLoader.KEY_STORY);
        assertEquals("highlight", ModuleResourceLoader.KEY_HIGHLIGHT);
        assertEquals("live", ModuleResourceLoader.KEY_LIVE);
        assertEquals("ai", ModuleResourceLoader.KEY_AI);
    }
}
