package UFP.data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class MultiMissionBays extends BaseHullMod {

    public static final float CARGO_BONUS = 30f;
    public static final float FUEL_BONUS = 25f;
    public static final float MAX_CREW_BONUS = 15f;
    public static final float MIN_CREW_REDUCTION = 30f;
    public static final float SURVEY_COST_REDUCTION = 40f;     // flat reduction to survey requirements
    public static final float DEPLOYMENT_SUPPLIES_REDUCTION = 10f;

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getCargoMod().modifyPercent(id, CARGO_BONUS);
        stats.getFuelMod().modifyPercent(id, FUEL_BONUS);
        stats.getMaxCrewMod().modifyPercent(id, MAX_CREW_BONUS);
        stats.getMinCrewMod().modifyPercent(id, -MIN_CREW_REDUCTION);
        stats.getSuppliesPerMonth().modifyPercent(id, -DEPLOYMENT_SUPPLIES_REDUCTION);

        // Campaign-layer dynamic stats for surveying:
        // Reduce the required Heavy Machinery and Supplies for surveys (flat amounts).
        stats.getDynamic().getMod(Stats.getSurveyCostReductionId(Commodities.HEAVY_MACHINERY))
                .modifyFlat(id, SURVEY_COST_REDUCTION);
        stats.getDynamic().getMod(Stats.getSurveyCostReductionId(Commodities.SUPPLIES))
                                .modifyFlat(id, SURVEY_COST_REDUCTION);
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship.getHullSize() == HullSize.CAPITAL_SHIP;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship.getHullSize() != HullSize.CAPITAL_SHIP) {
            return "Can only be installed on Capital ships";
        }
        return null;
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "Capital ships";
        if (index == 1) return "" + (int) CARGO_BONUS + "%";
        if (index == 2) return "" + (int) FUEL_BONUS + "%";
        if (index == 3) return "" + (int) MAX_CREW_BONUS + "%";
        if (index == 4) return "" + (int) MIN_CREW_REDUCTION + "%";
        if (index == 5) return "" + (int) DEPLOYMENT_SUPPLIES_REDUCTION + "%";

        // Add a new description param slot for survey reduction if your hullmod CSV has it.
        if (index == 6) return "" + (int) SURVEY_COST_REDUCTION;

        return null;
    }
}