package UFP.data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class FuelTankPristineExpansion extends BaseHullMod {

    public static final float FUEL_BONUS = 250f;
    public static final float CARGO_BONUS = 75f;
    public static final float MAX_CREW_REDUCTION = 25f;
    public static final float MIN_CREW_REDUCTION = 50f;
    public static final float SUPPLIES_REDUCTION = 25f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getFuelMod().modifyPercent(id, FUEL_BONUS);
        stats.getCargoMod().modifyPercent(id, CARGO_BONUS);
        stats.getMaxCrewMod().modifyPercent(id, -MAX_CREW_REDUCTION);
        stats.getMinCrewMod().modifyPercent(id, -MIN_CREW_REDUCTION);
        stats.getSuppliesPerMonth().modifyPercent(id, -SUPPLIES_REDUCTION);
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship.getHullSize() == HullSize.CRUISER;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship.getHullSize() != HullSize.CRUISER) {
            return "Can only be installed on Cruisers";
        }
        return null;
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0)
            return "Cruisers";
        if (index == 1)
            return "" + (int) FUEL_BONUS + "%";
        if (index == 2)
            return "" + (int) CARGO_BONUS + "%";
        if (index == 3)
            return "" + (int) MAX_CREW_REDUCTION + "%";
        if (index == 4)
            return "" + (int) MIN_CREW_REDUCTION + "%";
        if (index == 5)
            return "" + (int) SUPPLIES_REDUCTION + "%";
        return null;
    }
}
