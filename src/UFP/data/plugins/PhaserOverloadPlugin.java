package UFP.data.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.Misc;

import java.util.HashMap;
import java.util.Map;

public class PhaserOverloadPlugin extends BaseCombatLayeredRenderingPlugin {

    private static final String PHASER_ID = "phaser_typeX";
    private static final float DAMAGE_BONUS = 0.50f;  // 50% damage increase for Phaser Overload
    private static final float OVERLOAD_INTERVAL = 30f;  // Seconds between overloads
    private static final float OVERLOAD_DURATION = 5f;   // Duration for the damage boost
    private static final String FLOATING_TEXT = "Phaser Overload!";  // Text displayed when damage boost occurs

    private final Map<ShipAPI, Float> timer = new HashMap<>();
    private final Map<ShipAPI, Float> activeTimers = new HashMap<>();

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused()) return;

        for (ShipAPI ship : engine.getShips()) {
            if (ship.isAlive() && ship.getVariant().getHullMods().contains("galaxywarpcore")) {
                float elapsed = timer.getOrDefault(ship, 0f) + amount;
                if (elapsed >= OVERLOAD_INTERVAL) {
                    applyDamageBoost(ship, engine);
                    elapsed = 0f;
                }
                timer.put(ship, elapsed);

                // Check if the damage boost is active, and reset after duration
                if (activeTimers.containsKey(ship)) {
                    float activeTime = activeTimers.get(ship) + amount;
                    if (activeTime >= OVERLOAD_DURATION) {
                        removeDamageBoost(ship);
                        activeTimers.remove(ship);
                    } else {
                        activeTimers.put(ship, activeTime);
                    }
                }
            }
        }
    }

    private void applyDamageBoost(ShipAPI ship, CombatEngineAPI engine) {
        MutableShipStatsAPI stats = ship.getMutableStats();

        // Apply damage boost for energy weapons (phaser_typeX)
        stats.getEnergyWeaponDamageMult().modifyMult(PHASER_ID, 1f + DAMAGE_BONUS);

        // Display floating text to indicate Phaser Overload
        engine.addFloatingText(ship.getLocation(), FLOATING_TEXT, 2f, Misc.getTextColor(), ship, 0f, 0f);

        // Start timer for damage boost effect
        activeTimers.put(ship, 0f);
    }

    private void removeDamageBoost(ShipAPI ship) {
        MutableShipStatsAPI stats = ship.getMutableStats();

        // Remove the damage multiplier
        stats.getEnergyWeaponDamageMult().unmodify(PHASER_ID);
    }

    @Override
    public float getRenderRadius() {
        return 10000f;
    }
}
