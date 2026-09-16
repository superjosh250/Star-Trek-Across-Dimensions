package BORG.data.scripts;

import BORG.data.campaign.ids.BorgIDS;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class AssimilationTaskforce {

    public static final String ASSIMILATION_TASKFORCE = "assimilationTaskforce";
    public static final String FLEET_NAME = "Assimilation Taskforce";
    public static final float BASE_TASKFORCE_FP = 250f;

    /*** Calculates total fleet points scaled by the market's COMBAT_FLEET_SIZE_MULT modifier. */
    public static float getCalculatedFleetPoints(MarketAPI market) {
        if (market == null) return BASE_TASKFORCE_FP;

        float mult = market.getStats().getDynamic()
                .getStat(Stats.COMBAT_FLEET_SIZE_MULT)
                .getModifiedValue();

        return BASE_TASKFORCE_FP * mult;
    }

    /*** Generates an Assimilation Taskforce fleet with a guaranteed Borg Cube flagship and escort support populated up to the calculated fleet point total. */
    public static CampaignFleetAPI createTaskforce(MarketAPI market) {
        if (market == null) return null;

        float totalFP = getCalculatedFleetPoints(market);

        // 1. Build support fleet using FleetParamsV3
        FleetParamsV3 params = new FleetParamsV3(
                market,
                market.getLocationInHyperspace(),
                market.getFactionId(),
                null, // fleet size mult handled via target FP
                ASSIMILATION_TASKFORCE,
                totalFP, // combatPts
                0f, 0f, 0f, 0f, 0f, 0f // freighters, tankers, personnel
        );
        params.ignoreMarketFleetSizeMult = true;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);

        // Explicitly set campaign fleet name
        fleet.setName(FLEET_NAME);

        // 2. Select flagship (50/50 between BORG_CUBE and BORG_CUBE_S3E1)
        WeightedRandomPicker<String> cubePicker = new WeightedRandomPicker<>();
        cubePicker.add(BorgIDS.BORG_CUBE, 1.0f);
        cubePicker.add(BorgIDS.BORG_CUBE_S3E1, 1.0f);

        String chosenCubeId = cubePicker.pick();

        // 3. Instantiate and attach guaranteed flagship
        FleetMemberAPI flagship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, chosenCubeId);
        fleet.getFleetData().addFleetMember(flagship);
        fleet.getFleetData().setFlagship(flagship);

        // 4. Force fleet data refresh & layout update
        fleet.getFleetData().sort();
        fleet.updateCounts();
        fleet.updateFleetView();

        return fleet;
    }
}