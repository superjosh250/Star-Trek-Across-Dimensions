package UFP.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class UFPTextureManager {

    private static final String SETTINGS_PATH = "data/config/UFPSettings.json";
    private static final String SPRITES_EXTRA_KEY = "Sprites_Extra";
    private static final Map<String, SpriteAPI> loadedSprites = new HashMap<>();

    public static void preloadTextures() {
        try {
            JSONObject json = Global.getSettings().loadJSON(SETTINGS_PATH);
            if (json != null && json.has(SPRITES_EXTRA_KEY)) {
                JSONObject spritesExtra = json.getJSONObject(SPRITES_EXTRA_KEY);
                Iterator<String> keys = spritesExtra.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String path = spritesExtra.optString(key, null);
                    if (path != null) {
                        try {
                            SpriteAPI sprite = Global.getSettings().getSprite(path);
                            loadedSprites.put(key, sprite);
                            Global.getLogger(UFPTextureManager.class).info("Preloaded texture: " + key + " -> " + path);
                        } catch (Exception e) {
                            Global.getLogger(UFPTextureManager.class).warn("Failed to preload texture: " + key + " -> " + path);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Global.getLogger(UFPTextureManager.class).error("Failed to load UFPSettings.json for texture preloading.", e);
        }
    }

    public static SpriteAPI getSprite(String key) {
        return loadedSprites.get(key);
    }
}