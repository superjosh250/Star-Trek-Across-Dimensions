package UFP.data.hullmods;

import com.fs.starfarer.api.combat.*;

public class DuraniumHullPlating extends BaseHullMod {

    // Modifier ID used to uniquely identify stat changes from this hullmod
    private static final String MOD_ID = "duranium_hull";

    // Damage reduction values
    private static final float EMP_RESIST_MULT = 0.5f;             // 50% EMP damage
    private static final float HE_DAMAGE_REDUCTION = 0.10f;        // 10% HE damage
    private static final float ENERGY_DAMAGE_REDUCTION = 0.15f;    // 15% energy damage
    private static final float KINETIC_DAMAGE_REDUCTION = 0.30f;   // 30% kinetic damage

    // Minimum armor percentage required to activate the effect
    private static final float ARMOR_THRESHOLD = 0.4f; // 40%

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        MutableShipStatsAPI stats = ship.getMutableStats();

        // Calculate armor ratio: current armor HP / max armor HP
        float armorRatio = getArmorRatio(ship);

        // Check if shields are inactive or non-existent
        boolean shieldsInactive = ship.getShield() == null || !ship.getShield().isOn();

        // Apply damage reductions only if armor is above 40% and shields are off
        if (armorRatio > ARMOR_THRESHOLD && shieldsInactive) {
            // Apply EMP resistance
            stats.getEmpDamageTakenMult().modifyMult(MOD_ID, EMP_RESIST_MULT);

            // Apply damage type resistances
            stats.getEnergyDamageTakenMult().modifyMult(MOD_ID + "_ENERGY", 1f - ENERGY_DAMAGE_REDUCTION);
            stats.getKineticDamageTakenMult().modifyMult(MOD_ID + "_KINETIC", 1f - KINETIC_DAMAGE_REDUCTION);
            stats.getHighExplosiveDamageTakenMult().modifyMult(MOD_ID + "_HE", 1f - HE_DAMAGE_REDUCTION);
        } else {
            // Remove stat modifications if conditions aren't met
            stats.getEmpDamageTakenMult().unmodify(MOD_ID);
            stats.getEnergyDamageTakenMult().unmodify(MOD_ID + "_ENERGY");
            stats.getKineticDamageTakenMult().unmodify(MOD_ID + "_KINETIC");
            stats.getHighExplosiveDamageTakenMult().unmodify(MOD_ID + "_HE");
        }
    }

    /**
     * Calculates current armor ratio based on the ship's armor grid.
     * @param ship The ship we're evaluating.
     * @return Value between 0.0 and 1.0 representing % of armor remaining.
     */
    private float getArmorRatio(ShipAPI ship) {
        float totalArmor = 0f;

        ArmorGridAPI armorGrid = ship.getArmorGrid();
        float[][] grid = armorGrid.getGrid();
        float maxPerCell = armorGrid.getMaxArmorInCell();
        int cellCount = 0;

        // Sum up all armor in the grid and count total number of cells
        for (float[] row : grid) {
            for (float armor : row) {
                totalArmor += armor;
                cellCount++;
            }
        }

        float maxArmor = maxPerCell * cellCount;
        return maxArmor <= 0f ? 0f : totalArmor / maxArmor;
    }

    /**
     * Supplies hullmod description values shown in the refit screen tooltip.
     */
    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "50%";  // EMP
        if (index == 1) return "10%";  // HE
        if (index == 2) return "15%";  // Energy
        if (index == 3) return "30%";  // Kinetic
        if (index == 4) return "40%";  // Armor threshold
        return null;
    }
}
