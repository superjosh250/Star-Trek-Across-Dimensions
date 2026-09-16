
package UFP.data.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicRender;
import org.json.JSONObject;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class SelfDestructVisual {

    private static final String FALLBACK_PATH = "graphics/fx/ring_self_destruct.png";
    private static final String[] RING_IDS = {
            "sd_ring1", "sd_ring2", "sd_ring3", "sd_ring4", "sd_ring5", "sd_ring6"
    };

    private static final Map<String, String> ringTextures = new HashMap<>();

    private static float logTimer = 0f; // For throttled logging
    private static final float LOG_INTERVAL = 30f; // seconds
    private static boolean initialLogDone = false; // Track first log per activation

    static {
        preloadTextures();
    }

    /**
     * Preload textures for all rings from UFPSettings.json or fallback.
     */
    private static void preloadTextures() {
        try {
            JSONObject settings = Global.getSettings().getMergedJSONForMod("data/config/UFPSettings.json", "UFP");
            JSONObject sprites = null;
            if (settings != null && settings.has("graphics")) {
                JSONObject graphics = settings.getJSONObject("graphics");
                if (graphics.has("sprites")) {
                    sprites = graphics.getJSONObject("sprites");
                }
            }

            for (String ringId : RING_IDS) {
                String path = FALLBACK_PATH;
                if (sprites != null && sprites.has(ringId)) {
                    path = sprites.getString(ringId);
                }
                ringTextures.put(ringId, path);
                try {
                    Global.getSettings().loadTexture(path);
                    Global.getLogger(SelfDestructVisual.class).info("Preloaded texture for " + ringId + ": " + path);
                } catch (Exception e) {
                    Global.getLogger(SelfDestructVisual.class).warn("Failed to preload texture for " + ringId + ": " + path, e);
                }
            }
        } catch (Exception e) {
            Global.getLogger(SelfDestructVisual.class).warn("Failed to load UFPSettings.json, using fallback textures.", e);
            for (String ringId : RING_IDS) {
                ringTextures.put(ringId, FALLBACK_PATH);
            }
        }
    }

    /**
     * Draw danger rings during countdown with radar effect.
     * Transparent parts of the PNG softly tint the background, while ring pixels remain solid.
     * Debug particle rendering is commented out for now but kept for future diagnostics.
     */
    public static void drawDangerRings(ShipAPI ship, float timeElapsed) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship == null) return;

        Vector2f loc = ship.getLocation();

        // ✅ Initial log
        if (!initialLogDone) {
            Global.getLogger(SelfDestructVisual.class).info(
                    String.format("Self-destruct activated for ship: %s | Location: %s", ship.getName(), loc)
            );
            initialLogDone = true;
        }

        // ✅ Danger gradient: inner rings red → outer rings gray
        Color[] baseColors = {
                new Color(255, 0, 0),    // Ring 1: Bright red
                new Color(200, 50, 50),  // Ring 2: Muted red
                new Color(150, 75, 75),  // Ring 3: Darker red
                new Color(120, 90, 90),  // Ring 4: Fading toward gray
                new Color(90, 90, 90),   // Ring 5: Grayish tone
                new Color(60, 60, 60)    // Ring 6: Almost gray
        };

        for (int i = 0; i < RING_IDS.length; i++) {
            float ringSize = (i + 1) * 1000f;
            float pulseSize = 1f + 0.05f * (float) Math.sin(timeElapsed * 6f);
            Vector2f size = new Vector2f(ringSize * pulseSize, ringSize * pulseSize);

            // ✅ Radar effect: pulsing alpha
            int baseAlpha = 50; // Transparent tint for radar
            int pulseAlpha = (int)(baseAlpha + 20 * Math.sin(timeElapsed * (2f + i))); // Dynamic alpha
            Color ringColor = new Color(
                    baseColors[i].getRed(),
                    baseColors[i].getGreen(),
                    baseColors[i].getBlue(),
                    Math.min(255, Math.max(0, pulseAlpha)) // Clamp alpha
            );

            String texturePath = ringTextures.get(RING_IDS[i]);
            SpriteAPI sprite;
            try {
                sprite = Global.getSettings().getSprite(texturePath);
            } catch (Exception e) {
                Global.getLogger(SelfDestructVisual.class).warn("Failed to load sprite for " + RING_IDS[i] + ": " + texturePath, e);
                continue;
            }

            // ✅ Render each ring using singleframe()
            MagicRender.singleframe(
                    sprite,
                    loc,
                    size,
                    0f, // rotation
                    ringColor,
                    false // normal blending for subtle radar tint
            );

            /**
             * DEBUG PARTICLE CODE (COMMENTED OUT):
             * This section adds a particle at the edge of each ring for diagnostic purposes.
             * Uncomment if you need to visually verify ring radius alignment.
             *
             * float radiusOffset = ringSize / 2f;
             * Vector2f particleLoc = new Vector2f(loc.x + radiusOffset, loc.y);
             * engine.addSmoothParticle(
             *         particleLoc,
             *         new Vector2f(0, 0),
             *         150f,
             *         1.5f,
             *         3f,
             *         ringColor
             * );
             */
        }

        // ✅ Throttled summary log every 30s
        logTimer += Global.getCombatEngine().getElapsedInLastFrame();
        if (logTimer >= LOG_INTERVAL) {
            Global.getLogger(SelfDestructVisual.class).info(
                    String.format("Self-destruct visual update | ShipLoc: %s | Rings: %d", loc, RING_IDS.length)
            );
            logTimer = 0f;
        }
    }

    /**
     * Reset initial log flag when system deactivates.
     */
    public static void resetLogging() {
        initialLogDone = false;
        logTimer = 0f;
    }
}
