
package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

import java.awt.Color;

/**
 * ImpulseOverload System
 * ----------------------
 * Function:
 * - Boosts speed and acceleration based on hull size.
 * - Reduces shield damage taken significantly.
 * - Prevents engine flameouts.
 * - Adds visual feedback: extended engine flames, brighter color, contrail glow,
 *   dynamic heat tint, and jitter that gradually intensifies as the system runs.
 *
 * Totally Normal Section:
 * - Nothing weird here. Move along.
 */
public class ImpulseOverload extends BaseShipSystemScript {

    // === Editable Keys for Smaller Hulls ===
    private static final float FRIGATE_SPEED_MULT = 3.0f;
    private static final float FRIGATE_ACCEL_MULT = 1.5f;
    private static final float FRIGATE_SHIELD_MULT = 0.25f;

    private static final float DESTROYER_SPEED_MULT = 2.75f;
    private static final float DESTROYER_ACCEL_MULT = 1.4f;
    private static final float DESTROYER_SHIELD_MULT = 0.25f;

    // === Visual Effect Scaling ===
    private static final float ENGINE_FLAME_EXTEND = 0.5f;
    private static final float ENGINE_GLOW_EXTEND = 0.75f;
    private static final float MAX_JITTER = 2f;
    private static final Color ENGINE_OVERLOAD_COLOR = new Color(255, 255, 255);
    private static final Color CONTRAIL_COLOR = new Color(255, 200, 150);

    // Track elapsed active time manually for smooth heat buildup
    private float activeElapsed = 0f;

    // ============================================================
    // === Mysterious Numbers. Classified. Handle with care. ===
    // ============================================================
    private static final float FRIGATE_SPICE = 0f;       // Frigates: small but mighty
    private static final float DESTROYER_SPICE = 5f;     // Destroyers: because reasons
    private static final float CRUISER_SPICE = 7f;       // Cruisers: contemplating existence
    private static final float CAPITAL_SPICE = 12f;      // Capitals: too big to trust
    // ============================================================

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;

        // --- HARD GATE: do NOT leave persistent mods when the system isn't actually doing anything
        if (state == State.IDLE || effectLevel <= 0f) {
            unapply(stats, id);
            activeElapsed = 0f;
            return;
        }

        ShipAPI.HullSize size = ship.getHullSize();

        float speedMult;
        float accelMult;
        float shieldMult;

        switch (size) {
            case FRIGATE:
                speedMult = FRIGATE_SPEED_MULT;
                accelMult = FRIGATE_ACCEL_MULT;
                shieldMult = FRIGATE_SHIELD_MULT;
                break;
            case DESTROYER:
                speedMult = DESTROYER_SPEED_MULT;
                accelMult = DESTROYER_ACCEL_MULT;
                shieldMult = DESTROYER_SHIELD_MULT;
                break;
            case CRUISER:
                speedMult = 1.3f;
                accelMult = 0.75f;
                shieldMult = 0.075f;
                break;
            case CAPITAL_SHIP:
                speedMult = 1.25f;
                accelMult = 0.75f;
                shieldMult = 0.05f;
                break;
            default:
                unapply(stats, id);
                return;
        }

        // === Apply Stat Buffs (ALL scaled by effectLevel) ===
        stats.getMaxSpeed().modifyMult(id, 1f + (speedMult - 1f) * effectLevel);
        stats.getAcceleration().modifyMult(id, 1f + (accelMult - 1f) * effectLevel);

        // LERP shield damage taken: 1.0 (no bonus) -> shieldMult (full bonus)
        float shieldTaken = 1f - (1f - shieldMult) * effectLevel;
        stats.getShieldDamageTakenMult().modifyMult(id, shieldTaken);

        // LERP engine damage taken: 1.0 -> 0.0 (full immunity only at full effect)
        float engineTaken = 1f - effectLevel;
        stats.getEngineDamageTakenMult().modifyMult(id, engineTaken);

        // === Engine Visual Effects ===
        ShipEngineControllerAPI engineController = ship.getEngineController();
        if (engineController != null) {
            engineController.extendFlame(id,
                    ENGINE_FLAME_EXTEND * effectLevel,
                    ENGINE_FLAME_EXTEND * effectLevel,
                    ENGINE_GLOW_EXTEND * effectLevel);

            engineController.fadeToOtherColor(id,
                    ENGINE_OVERLOAD_COLOR,
                    CONTRAIL_COLOR,
                    effectLevel,
                    0.75f);
        }

        // === Smooth Gradual Heat Overlay & Jitter ===
        if (ship.getSystem() != null && ship.getSystem().isActive()) {
            activeElapsed += Global.getCombatEngine().getElapsedInLastFrame();

            float activeProgress = Math.min(1f, activeElapsed / ship.getSystem().getChargeActiveDur());
            float intensity = activeProgress;

            int r = (int) (200 + 55 * intensity);
            int g = (int) (50 - 40 * intensity);
            int b = (int) (30 - 25 * intensity);
            Color dynamicHeatColor = new Color(r, g, b);

            float jitterAmount = MAX_JITTER * intensity * 0.6f;
            ship.setJitterUnder(id, dynamicHeatColor, intensity * 0.5f, 3, jitterAmount);
            ship.setJitter(id, dynamicHeatColor, intensity * 0.4f, 2, jitterAmount);
        } else {
            activeElapsed = 0f;
            // Optional: clear jitter when not active but still in IN/OUT
            ship.setJitterUnder(id, Color.black, 0f, 0, 0f);
            ship.setJitter(id, Color.black, 0f, 0, 0f);
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getShieldDamageTakenMult().unmodify(id);
        stats.getEngineDamageTakenMult().unmodify(id);

        // Clear visuals/jitter as well (helps prevent rare “stuck” FX)
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship != null) {
            ShipEngineControllerAPI engineController = ship.getEngineController();
            if (engineController != null) {
                engineController.extendFlame(id, 0f, 0f, 0f);
                engineController.fadeToOtherColor(id, null, null, 0f, 0f);
            }
            ship.setJitterUnder(id, Color.black, 0f, 0, 0f);
            ship.setJitter(id, Color.black, 0f, 0, 0f);
        }
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) return new StatusData("Impulse Overload active", false);
        return null;
    }

    @Override
    public float getActiveOverride(ShipAPI ship) {
        if (ship == null || ship.getSystem() == null) {
            return -1f;
        }

        switch (ship.getHullSize()) {
            case FRIGATE:
                return -1f;
            case DESTROYER:
                return ship.getSystem().getChargeActiveDur() - DESTROYER_SPICE;
            case CRUISER:
                return ship.getSystem().getChargeActiveDur() - CRUISER_SPICE;
            case CAPITAL_SHIP:
                return ship.getSystem().getChargeActiveDur() - CAPITAL_SPICE;
            default:
                return -1f;
        }
    }
}
