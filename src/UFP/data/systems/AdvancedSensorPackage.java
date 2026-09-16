
package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.IntervalUtil;

import java.awt.Color;
import java.util.List;
import java.util.Random;

public class AdvancedSensorPackage extends BaseShipSystemScript {

    // ==============================
    // CONFIGURABLE KEYS
    // ==============================
    private static final float WEAPON_RANGE_BONUS = 30f;       // +30% weapon range
    private static final float SIGHT_RADIUS_BONUS = 50f;       // +50% sensor range
    private static final float CRIT_DAMAGE_MULT = 1.3f;        // Critical hit damage multiplier (x1.3)
    private static final float CRIT_CHANCE = 0.25f;            // 25% chance for critical buff
    private static final float CRIT_CHECK_INTERVAL = 10f;      // Check every 10 seconds
    private static final float DECLOAK_CHANCE = 0.10f;         // 10% chance to decloak cloaked ships
    private static final String CLOAK_SYSTEM_ID = "romulan_cloak"; // Cloak system ID to target

    // ==============================
    // INTERNALS
    // ==============================
    private final IntervalUtil critCheckInterval = new IntervalUtil(CRIT_CHECK_INTERVAL, CRIT_CHECK_INTERVAL);
    private final Random random = new Random();

    @Override
    public void apply(MutableShipStatsAPI stats, String id, ShipSystemStatsScript.State state, float effectLevel) {
        CombatEngineAPI engine = Global.getCombatEngine();
        critCheckInterval.advance(engine.getElapsedInLastFrame());

        ShipAPI sourceShip = (ShipAPI) stats.getEntity();
        if (sourceShip == null || !sourceShip.isAlive()) return;

        List<ShipAPI> allShips = engine.getShips();

        // 1. Apply buffs to all allies
        for (ShipAPI ally : allShips) {
            if (ally.getOwner() == sourceShip.getOwner() && ally.isAlive()) {
                ally.getMutableStats().getBallisticWeaponRangeBonus().modifyPercent(id, WEAPON_RANGE_BONUS);
                ally.getMutableStats().getEnergyWeaponRangeBonus().modifyPercent(id, WEAPON_RANGE_BONUS);
                ally.getMutableStats().getMissileWeaponRangeBonus().modifyPercent(id, WEAPON_RANGE_BONUS);
                ally.getMutableStats().getSightRadiusMod().modifyPercent(id, SIGHT_RADIUS_BONUS);
            }
        }

        // 2. Critical hit mechanic
        if (critCheckInterval.intervalElapsed()) {
            boolean applyCrit = random.nextFloat() < CRIT_CHANCE;

            for (ShipAPI ally : allShips) {
                if (ally.getOwner() == sourceShip.getOwner() && ally.isAlive()) {
                    if (applyCrit) {
                        ally.getMutableStats().getBallisticWeaponDamageMult().modifyMult(id, CRIT_DAMAGE_MULT);
                        ally.getMutableStats().getEnergyWeaponDamageMult().modifyMult(id, CRIT_DAMAGE_MULT);
                        ally.getMutableStats().getMissileWeaponDamageMult().modifyMult(id, CRIT_DAMAGE_MULT);

                        engine.addFloatingText(ally.getLocation(), "Critical Damage Applied!", 20f, Color.RED, ally, 1f, 1f);
                    } else {
                        ally.getMutableStats().getBallisticWeaponDamageMult().unmodify(id);
                        ally.getMutableStats().getEnergyWeaponDamageMult().unmodify(id);
                        ally.getMutableStats().getMissileWeaponDamageMult().unmodify(id);
                    }
                }
            }
        }

        // 3. Decloak mechanic
        for (ShipAPI ship : allShips) {
            ShipSystemAPI system = ship.getSystem();
            if (system != null && CLOAK_SYSTEM_ID.equals(system.getId()) && ship.isAlive()) {
                if (random.nextFloat() < DECLOAK_CHANCE) {
                    system.deactivate();
                    engine.addFloatingText(ship.getLocation(), "Decloaked!", 20f, Color.YELLOW, ship, 1f, 1f);
                }
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getBallisticWeaponRangeBonus().unmodify(id);
        stats.getEnergyWeaponRangeBonus().unmodify(id);
        stats.getMissileWeaponRangeBonus().unmodify(id);
        stats.getSightRadiusMod().unmodify(id);
        stats.getBallisticWeaponDamageMult().unmodify(id);
        stats.getEnergyWeaponDamageMult().unmodify(id);
        stats.getMissileWeaponDamageMult().unmodify(id);
    }

    @Override
    public ShipSystemStatsScript.StatusData getStatusData(int index, ShipSystemStatsScript.State state, float effectLevel) {
        if (state == ShipSystemStatsScript.State.ACTIVE) {
            switch (index) {
                case 0: return new ShipSystemStatsScript.StatusData("+" + (int) WEAPON_RANGE_BONUS + "% weapon range", false);
                case 1: return new ShipSystemStatsScript.StatusData("+" + (int) SIGHT_RADIUS_BONUS + "% sensor range", false);
                case 2: return new ShipSystemStatsScript.StatusData("Critical hit chance: " + (int)(CRIT_CHANCE * 100) + "%", false);
                case 3: return new ShipSystemStatsScript.StatusData("Decloak chance: " + (int)(DECLOAK_CHANCE * 100) + "%", false);
            }
        }
        return null;
    }
}
