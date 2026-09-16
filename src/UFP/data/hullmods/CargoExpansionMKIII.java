package UFP.data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class CargoExpansionMKIII extends BaseHullMod {

    public static final float CARGO_CAPACITY_BONUS = 75f;
    public static final float FUEL_CAPACITY_BONUS = 20f;
    public static final float FUEL_USE_MULT = 0.4f; // 60% reduction
    public static final float CREW_REQ_MULT = 0.1f; // 90% reduction
    public static final float MAX_CREW_MULT = 0.1f; // 90% reduction
    public static final float ARMOR_MULT = 0.4f; // 60% reduction
    public static final float HULL_BONUS = 25f;

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getCargoMod().modifyPercent(id, CARGO_CAPACITY_BONUS);
        stats.getFuelMod().modifyPercent(id, FUEL_CAPACITY_BONUS);
        stats.getFuelUseMod().modifyMult(id, FUEL_USE_MULT);

        stats.getMinCrewMod().modifyMult(id, CREW_REQ_MULT);
        stats.getMaxCrewMod().modifyMult(id, MAX_CREW_MULT);

        stats.getArmorBonus().modifyMult(id, ARMOR_MULT);
        stats.getHullBonus().modifyPercent(id, HULL_BONUS);

        stats.getNumFighterBays().modifyMult(id, 0f);

        // Note: "Ship sells commodities for +15% of base price" is not implemented here
        // as it requires campaign-level economy listeners, notship stats.
    }

    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0)
            return "" + (int) CARGO_CAPACITY_BONUS + "%";
        if (index == 1)
            return "" + (int) FUEL_CAPACITY_BONUS + "%";
        if (index == 2)
            return "" + (int) HULL_BONUS + "%";
        if (index == 3)
            return "60%"; // Fuel use reduction
        if (index == 4)
            return "90%"; // Crew reduction
        if (index == 5)
            return "60%"; // Armor reduction
        return null;
    }
}
