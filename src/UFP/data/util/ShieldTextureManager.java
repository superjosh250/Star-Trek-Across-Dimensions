package UFP.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;

public class ShieldTextureManager {

    private static final Logger LOG = Global.getLogger(ShieldTextureManager.class);

    private static final Map<String, String[]> SHIELD_TEXTURES = new HashMap<>();
    private static final Set<String> VALID_STYLES = new HashSet<>();
    private static final String DEFAULT_STYLE = "BubbleShield";
    private static final String[] DEFAULT_TEXTURES = new String[]{
            "graphics/fx/bubble_shield.png", "graphics/fx/bubble_shield.png"
    };

    static {
        SHIELD_TEXTURES.put("BubbleShield16", new String[]{
                "graphics/fx/bubble_shield16.png", "graphics/fx/bubble_shield32_ring.png"
        });
        SHIELD_TEXTURES.put("BubbleShield32", new String[]{
                "graphics/fx/bubble_shield32.png", "graphics/fx/bubble_shield32_ring.png"
        });
        SHIELD_TEXTURES.put("BubbleShield64", new String[]{
                "graphics/fx/bubble_shield64.png", "graphics/fx/bubble_shield64_ring.png"
        });
        SHIELD_TEXTURES.put("BubbleShield128", new String[]{
                "graphics/fx/bubble_shield128.png", "graphics/fx/bubble_shield128.png"
        });
        SHIELD_TEXTURES.put("BubbleShield192", new String[]{
                "graphics/fx/bubble_shield192.png", "graphics/fx/bubble_shield192.png"
        });
        SHIELD_TEXTURES.put("BubbleShield256", new String[]{
                "graphics/fx/bubble_shield256.png", "graphics/fx/bubble_shield256.png"
        });
        SHIELD_TEXTURES.put("BubbleShield320", new String[]{
                "graphics/fx/bubble_shield320.png", "graphics/fx/bubble_shield320.png"
        });
        SHIELD_TEXTURES.put("BubbleShield384", new String[]{
                "graphics/fx/bubble_shield384.png", "graphics/fx/bubble_shield384.png"
        });
        SHIELD_TEXTURES.put("BubbleShield448", new String[]{
                "graphics/fx/bubble_shield448.png", "graphics/fx/bubble_shield448.png"
        });
        SHIELD_TEXTURES.put("BubbleShield512", new String[]{
                "graphics/fx/bubble_shield512.png", "graphics/fx/bubble_shield512_ring.png"
        });
        SHIELD_TEXTURES.put("BubbleShield536", new String[]{
                "graphics/fx/bubble_shield536.png", "graphics/fx/bubble_shield536_ring.png"
        });
        SHIELD_TEXTURES.put("BubbleShield576", new String[]{
                "graphics/fx/bubble_shield576.png", "graphics/fx/bubble_shield576_ring.png"
        });
        SHIELD_TEXTURES.put("BubbleShield640", new String[]{
                "graphics/fx/bubble_shield640.png", "graphics/fx/bubble_shield640_ring.png"
        });
        SHIELD_TEXTURES.put("BubbleShield704", new String[]{
                "graphics/fx/bubble_shield704.png", "graphics/fx/bubble_shield704_ring.png"
        });
        SHIELD_TEXTURES.put("BubbleShield768", new String[]{
                "graphics/fx/bubble_shield768.png", "graphics/fx/bubble_shield768_ring.png"
        });

        SHIELD_TEXTURES.put(DEFAULT_STYLE, DEFAULT_TEXTURES);

        preloadAndValidateTextures();
    }

    private static void preloadAndValidateTextures() {
        for (Map.Entry<String, String[]> entry : SHIELD_TEXTURES.entrySet()) {
            String style = entry.getKey();
            String inner = entry.getValue()[0];
            String ring = entry.getValue()[1];

            try {
                Global.getSettings().loadTexture(inner);
                Global.getSettings().loadTexture(ring);

                SpriteAPI innerSprite = Global.getSettings().getSprite(inner);
                SpriteAPI ringSprite = Global.getSettings().getSprite(ring);

                boolean innerValid = innerSprite != null && innerSprite.getTextureId() > 0;
                boolean ringValid = ringSprite != null && ringSprite.getTextureId() > 0;

                if (innerValid && ringValid) {
                    VALID_STYLES.add(style);
                    LOG.info("✅ Shield style '" + style + "' validated: " + inner + ", " + ring);
                } else {
                    LOG.warn("⚠️ Shield style '" + style + "' failed validation: " + inner + ", " + ring);
                }
            } catch (Exception e) {
                LOG.error("❌ Exception while validating shield style '" + style + "'", e);
            }
        }
    }

    public static void reloadActiveShieldTextures() {
        VALID_STYLES.clear();
        preloadAndValidateTextures();
        LOG.info("Shield textures reloaded successfully.");
    }

    public static boolean isValidStyle(String style) {
        return VALID_STYLES.contains(style);
    }

    public static String[] getTextures(String style) {
        if (isValidStyle(style)) {
            return SHIELD_TEXTURES.get(style);
        }
        return DEFAULT_TEXTURES;
    }

    public static Set<String> getValidStyles() {
        return Collections.unmodifiableSet(VALID_STYLES);
    }

    public static String[] getDefaultTextures() {
        return DEFAULT_TEXTURES;
    }

    public static String getDefaultStyle() {
        return DEFAULT_STYLE;
    }
}