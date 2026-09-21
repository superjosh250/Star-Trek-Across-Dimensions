package UFP.data.hullmods;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import org.lwjgl.util.vector.Vector2f;

public class DeployedAblativeArmor extends BaseHullMod {

    public static final String MODIFIER_ID = "UFP_deployed_ablative_armor";

    /*** Checks if a ship or variant is the USS Voyager (Armored) or one of its variants.*/
    public static boolean isCompatibleShip(ShipAPI ship) {
        if (ship == null || ship.getHullSpec() == null) return false;
        String hullId = ship.getHullSpec().getHullId();
        String baseHullId = ship.getHullSpec().getBaseHullId();

        return DimensionsCrossedIDS.HULL_USS_VOYAGER_ARMORED.equals(hullId) ||
                DimensionsCrossedIDS.HULL_USS_VOYAGER_ARMORED.equals(baseHullId);
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return isCompatibleShip(ship);
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return "Can only be installed on the USS Voyager (Armored).";
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // 1. Force shield arc to 0 degrees (disables raising shields in combat and refit)
        stats.getShieldArcBonus().modifyMult(id, 0f);

        // 2. Apply movement penalties if forced onto an incompatible ship
        if (stats.getVariant() != null && stats.getVariant().getHullSpec() != null) {
            String hullId = stats.getVariant().getHullSpec().getHullId();
            String baseHullId = stats.getVariant().getHullSpec().getBaseHullId();

            boolean isCompatible = DimensionsCrossedIDS.HULL_USS_VOYAGER_ARMORED.equals(hullId) ||
                    DimensionsCrossedIDS.HULL_USS_VOYAGER_ARMORED.equals(baseHullId);

            if (!isCompatible) {
                // Cut max speed, acceleration, deceleration, and turn rates in half (50%)
                stats.getMaxSpeed().modifyMult(id, 0.5f);
                stats.getAcceleration().modifyMult(id, 0.5f);
                stats.getDeceleration().modifyMult(id, 0.5f);
                stats.getTurnAcceleration().modifyMult(id, 0.5f);
                stats.getMaxTurnRate().modifyMult(id, 0.5f);
            }
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (ship == null) return;

        // Force shield type to NONE if a shield object exists
        if (ship.getShield() != null) {
            ship.getShield().setType(ShieldType.NONE);
        }

        if (!ship.hasListenerOfClass(AblativeArmorDamageListener.class)) {
            ship.addListener(new AblativeArmorDamageListener());
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "95%";
        if (index == 1) return "99%";
        if (index == 2) return "75%";
        if (index == 3) return "80%";
        if (index == 4) return "90%";
        if (index == 5) return "60%";
        return null;
    }

    public static class AblativeArmorDamageListener implements DamageTakenModifier {

        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean outerAP) {
            if (!(target instanceof ShipAPI)) return null;

            ShipAPI ship = (ShipAPI) target;
            DamageType type = damage.getType();

            boolean isArmor = isArmorHit(ship, point);
            boolean isCompatible = isCompatibleShip(ship);

            float mult = 1.0f;

            if (isArmor) {
                // Keep standard ablative armor protection for armor hits on ALL ships
                if (type == DamageType.ENERGY) {
                    mult = 0.05f; // 95% reduction
                } else if (type == DamageType.FRAGMENTATION) {
                    mult = 0.01f; // 99% reduction
                } else {
                    mult = 0.25f; // 75% reduction for Kinetic, High Explosive, etc.
                }
            } else {
                // Hull damage handling
                if (!isCompatible) {
                    // Incompatible ship penalty: 2.5x damage straight to hull
                    mult = 2.5f;
                } else {
                    // Compatible ship hull damage reductions
                    if (type == DamageType.ENERGY) {
                        mult = 0.20f; // 80% reduction
                    } else if (type == DamageType.FRAGMENTATION) {
                        mult = 0.10f; // 90% reduction
                    } else {
                        mult = 0.40f; // 60% reduction for Kinetic, High Explosive, etc.
                    }
                }
            }

            damage.getModifier().modifyMult(MODIFIER_ID, mult);
            return MODIFIER_ID;
        }

        private boolean isArmorHit(ShipAPI ship, Vector2f point) {
            if (ship.getArmorGrid() == null) return false;

            if (point != null) {
                int[] cell = ship.getArmorGrid().getCellAtLocation(point);
                if (cell != null) {
                    float armorValue = ship.getArmorGrid().getArmorValue(cell[0], cell[1]);
                    return armorValue > 0f;
                }
            }

            float[][] grid = ship.getArmorGrid().getGrid();
            if (grid != null) {
                for (int x = 0; x < grid.length; x++) {
                    for (int y = 0; y < grid[x].length; y++) {
                        if (grid[x][y] > 0f) {
                            return true;
                        }
                    }
                }
            }

            return false;
        }
    }
}