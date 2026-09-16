package UFP.data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class CargoExpansionMKI extends BaseHullMod {

    public static final float FUEL_CAPACITY_PENALTY = 25f;
    public static final float CARGO_CAPACITY_BONUS = 40f;
    public static final float FUEL_USE_REDUCTION = 5f;

    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getFuelMod().modifyPercent(id, -FUEL_CAPACITY_PENALTY);
        stats.getCargoMod().modifyPercent(id, CARGO_CAPACITY_BONUS);
        stats.getFuelUseMod().modifyMult(id, 1f - (FUEL_USE_REDUCTION * 0.01f));
    }

    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0)
            return "" + (int) CARGO_CAPACITY_BONUS + "%";
        if (index == 1)
            return "" + (int) FUEL_USE_REDUCTION + "%";
        if (index == 2)
            return "" + (int) FUEL_CAPACITY_PENALTY + "%";
        return null;
    }
}
