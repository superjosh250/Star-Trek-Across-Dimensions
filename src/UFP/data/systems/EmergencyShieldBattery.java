
package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import UFP.data.util.ShieldHitpointManager;

import java.awt.Color;

public class EmergencyShieldBattery extends BaseShipSystemScript {

    private static final float RESTORE_PERCENT = 0.7f; // Restore 70% of shield HP
    private static final String RESTORE_FLAG_KEY = "emergency_shield_battery_restored";
    private static final String BATTERY_ACTIVE_KEY = "emergency_shield_battery_active";

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null || !ship.isAlive()) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship.getShield() == null) return;

        // Reset flags when idle
        if (state == State.IDLE) {
            ship.removeCustomData(RESTORE_FLAG_KEY);
            ship.removeCustomData(BATTERY_ACTIVE_KEY);
            return;
        }

        // Mark battery as active
        if (state == State.ACTIVE) {
            ship.setCustomData(BATTERY_ACTIVE_KEY, true);
        }

        // Restore shield once when fully charged
        if (state == State.ACTIVE && effectLevel >= 1f && ship.getCustomData().get(RESTORE_FLAG_KEY) == null) {
            float maxHP = ShieldHitpointManager.getMaxShieldHP(ship);
            float restoreAmount = maxHP * RESTORE_PERCENT;

            ShieldHitpointManager.setShieldHP(ship, restoreAmount);
            ship.setCustomData(RESTORE_FLAG_KEY, true);

            if (ShieldHitpointManager.isShieldDisabled(ship)) {
                ship.setCustomData(ShieldHitpointManager.UNIVERSAL_DAMAGE_SCALER_KEY, false);
            }

            if (!ship.getShield().isOn()) {
                ship.getShield().toggleOn();
            }

            engine.addFloatingText(ship.getLocation(),
                    "Emergency Shield: +" + Math.round(restoreAmount) + " SP",
                    20f, Color.CYAN, ship, 0.5f, 1f);
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship != null) {
            ship.removeCustomData(RESTORE_FLAG_KEY);
            ship.removeCustomData(BATTERY_ACTIVE_KEY);
        }
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (state != State.ACTIVE) return null;

        switch (index) {
            case 0:
                return new StatusData("Emergency shield restoration", false);
            case 1:
                return new StatusData("Boosted shield regen active", false);
            default:
                return null;
        }
    }

    public String getInfoText(ShipSystemAPI system) {
        return "Restores 70% shield HP instantly";
    }
}
