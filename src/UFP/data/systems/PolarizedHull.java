
package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.json.JSONObject;

public class PolarizedHull extends BaseShipSystemScript {

    // Visual effect settings
    private static final String OVERLAY_KEY = "hullPolarizedOverlay";
    private static final float BASE_OPACITY = 0.8f;
    private static final float ROTATION_SPEED = 30f;
    private static final float PULSE_SPEED = 3f;
    private static final float PULSE_INTENSITY = 0.25f;

    // Stat modifiers
    private static final float ARMOR_DAMAGE_MULT = 0.25f; // 75% less damage
    private static final float HULL_DAMAGE_MULT = 0.6f;   // 40% less damage

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (stats == null) return;
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null || !ship.isAlive()) return;

        ShipSystemAPI.SystemState systemState = ship.getSystem().getState();

        // Remove overlay and bonuses when system is OUT or COOLDOWN
        if (systemState == ShipSystemAPI.SystemState.OUT || systemState == ShipSystemAPI.SystemState.COOLDOWN) {
            ship.setExtraOverlay(null);
            ship.setExtraOverlayShadowOpacity(0f);
            unapply(stats, id);
            return;
        }

        // Apply overlay and bonuses when system is IN or ACTIVE
        if (systemState == ShipSystemAPI.SystemState.IN || systemState == ShipSystemAPI.SystemState.ACTIVE) {
            String overlayPath = getOverlayPathFromSettings();
            ship.setExtraOverlay(overlayPath);
            ship.setExtraOverlayMatchHullColor(false);

            // Animate overlay
            float elapsed = Global.getCombatEngine().getTotalElapsedTime(false);
            float angleOffset = (elapsed * ROTATION_SPEED) % 360f;
            float pulse = 1f + PULSE_INTENSITY * (float) Math.sin(elapsed * PULSE_SPEED);
            float opacity = BASE_OPACITY * effectLevel * pulse;

            ship.setExtraOverlayAngleOffset(angleOffset);
            ship.setExtraOverlayShadowOpacity(opacity);

            // Apply damage reduction bonuses scaled by effectLevel
            stats.getArmorDamageTakenMult().modifyMult(id, 1f - (1f - ARMOR_DAMAGE_MULT) * effectLevel);
            stats.getHullDamageTakenMult().modifyMult(id, 1f - (1f - HULL_DAMAGE_MULT) * effectLevel);
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        if (stats == null) return;
        stats.getArmorDamageTakenMult().unmodify(id);
        stats.getHullDamageTakenMult().unmodify(id);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0 && (state == State.IN || state == State.ACTIVE)) {
            return new StatusData("Hull polarized: armor -75%, hull -40%", false);
        }
        return null;
    }

    private String getOverlayPathFromSettings() {
        try {
            JSONObject settings = Global.getSettings().getSettingsJSON();
            JSONObject graphics = settings.getJSONObject("graphics");
            JSONObject fx = graphics.getJSONObject("fx");
            return fx.getString(OVERLAY_KEY);
        } catch (Exception e) {
            Global.getLogger(PolarizedHull.class).warn("Failed to load overlay path from settings.json", e);
            return "graphics/fx/hex_overlay.png"; // fallback
        }
    }
}
