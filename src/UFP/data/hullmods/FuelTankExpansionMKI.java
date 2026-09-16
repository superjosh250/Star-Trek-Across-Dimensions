package UFP.data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class FuelTankExpansionMKI extends BaseHullMod {

    public static final float FUEL_BONUS = 20f;
    public static final float CARGO_PENALTY = 15f;
    public static final float MAX_CREW_PENALTY = 30f;
    public static final float MIN_CREW_REDUCTION = 15f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getFuelMod().modifyPercent(id, FUEL_BONUS);
        stats.getCargoMod().modifyPercent(id, -CARGO_PENALTY);
        stats.getMaxCrewMod().modifyPercent(id, -MAX_CREW_PENALTY);
        stats.getMinCrewMod().modifyPercent(id, -MIN_CREW_REDUCTION);
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0)
            return "" + (int) FUEL_BONUS + "%";
        if (index == 1)
            return "" + (int) CARGO_PENALTY + "%";
        if (index == 2)
            return "" + (int) MAX_CREW_PENALTY + "%";
        if (index == 3)
            return "" + (int) MIN_CREW_REDUCTION + "%";
        return null;
    }
}
