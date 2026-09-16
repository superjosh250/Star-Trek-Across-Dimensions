package UFP.data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class CargoExpansionMKII extends BaseHullMod {

    public static final float CARGO_CAPACITY_BONUS = 50f;
    public static final float FUEL_USE_PENALTY = 15f;

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getCargoMod().modifyPercent(id, CARGO_CAPACITY_BONUS);
        stats.getFuelUseMod().modifyPercent(id, FUEL_USE_PENALTY);
    }

    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0)
            return "" + (int) CARGO_CAPACITY_BONUS + "%";
        if (index == 1)
            return "" + (int) FUEL_USE_PENALTY + "%";
        return null;
    }
}
