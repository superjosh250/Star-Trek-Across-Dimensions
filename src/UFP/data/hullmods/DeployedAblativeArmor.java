package UFP.data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import org.lwjgl.util.vector.Vector2f;

public class DeployedAblativeArmor extends BaseHullMod {

    public static final String MODIFIER_ID = "UFP_deployed_ablative_armor";

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (ship == null) return;

        if (!ship.hasListenerOfClass(AblativeArmorDamageListener.class)) {
            ship.addListener(new AblativeArmorDamageListener());
        }
    }

    public static class AblativeArmorDamageListener implements DamageTakenModifier {

        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean outerAP) {
            if (!(target instanceof ShipAPI)) return null;

            ShipAPI ship = (ShipAPI) target;
            DamageType type = damage.getType();

            boolean isArmor = isArmorHit(ship, point);

            float mult = 1.0f;

            if (isArmor) {
                if (type == DamageType.ENERGY) {
                    mult = 0.05f; // 95% reduction
                } else if (type == DamageType.FRAGMENTATION) {
                    mult = 0.01f; // 99% reduction
                } else {
                    mult = 0.25f; // 75% reduction for Kinetic, High Explosive, etc.
                }
            } else {
                if (type == DamageType.ENERGY) {
                    mult = 0.20f; // 80% reduction
                } else if (type == DamageType.FRAGMENTATION) {
                    mult = 0.10f; // 90% reduction
                } else {
                    mult = 0.40f; // 60% reduction for Kinetic, High Explosive, etc.
                }
            }

            damage.getModifier().modifyMult(MODIFIER_ID, mult);
            return MODIFIER_ID;
        }

        private boolean isArmorHit(ShipAPI ship, Vector2f point) {
            if (ship.getArmorGrid() == null) return false;

            // Check specific cell if hit location is provided
            if (point != null) {
                int[] cell = ship.getArmorGrid().getCellAtLocation(point);
                if (cell != null) {
                    float armorValue = ship.getArmorGrid().getArmorValue(cell[0], cell[1]);
                    return armorValue > 0f;
                }
            }

            // Fallback if point is null/out of bounds: check if any armor cell on the ship remains
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

        public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
            if (index == 0) return "95%";
            if (index == 1) return "99%";
            if (index == 2) return "75%";
            if (index == 3) return "80%";
            if (index == 4) return "90%";
            if (index == 5) return "60%";
            return null;
        }
    }
}