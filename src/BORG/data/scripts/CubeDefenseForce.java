package BORG.data.scripts;

import BORG.data.campaign.ids.BorgIDS;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

public class CubeDefenseForce {

    public static final String CUBE_DEFENSE_FORCE = "collectiveDefenseForce";
    public static final String FLEET_NAME = "Collective Defense Force";
    public static final int PROBE_COUNT = 6;

    /*** Creates a Cube Defense Fleet with either a standard Cube or S3E1 Cube flagship along with 6 Borg Probe support escorts. */
    public static CampaignFleetAPI createFleet(MarketAPI market) {
        String factionId = (market != null) ? market.getFactionId() : "borg";
        return createFleet(factionId, market);
    }

    /*** Overloaded fleet creation for direct faction specification.*/
    public static CampaignFleetAPI createFleet(String factionId, MarketAPI market) {
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(factionId, FLEET_NAME, true);

        // 1. Guaranteed Cube Selection (50/50 between Standard Cube & S3E1 Cube)
        String chosenCubeVariant = (Math.random() < 0.5f) ? BorgIDS.BORG_CUBE : BorgIDS.BORG_CUBE_S3E1;

        FleetMemberAPI flagship = Global.getFactory().createFleetMember(
                FleetMemberType.SHIP,
                chosenCubeVariant
        );
        fleet.getFleetData().addFleetMember(flagship);
        fleet.getFleetData().setFlagship(flagship);

        // 2. Add 6 Borg Probes as Support Escorts
        for (int i = 0; i < PROBE_COUNT; i++) {
            FleetMemberAPI probe = Global.getFactory().createFleetMember(
                    FleetMemberType.SHIP,
                    BorgIDS.BORG_PROBE
            );
            fleet.getFleetData().addFleetMember(probe);
        }

        // 3. Update Fleet Data & View
        fleet.getFleetData().sort();
        fleet.updateCounts();
        fleet.updateFleetView();
        fleet.forceSync();

        // 4. Assign Memory Flags
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_TYPE, CUBE_DEFENSE_FORCE);

        if (market != null) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SOURCE_MARKET, market.getId());
        }

        return fleet;
    }

    /*** Helper to instantiate and spawn the defense fleet directly into a market's containing location. */
    public static CampaignFleetAPI spawnFleetAtMarket(MarketAPI market) {
        if (market == null || market.getContainingLocation() == null) return null;

        CampaignFleetAPI fleet = createFleet(market);
        SectorEntityToken primary = market.getPrimaryEntity();

        market.getContainingLocation().addEntity(fleet);
        fleet.setFacing((float) Math.random() * 360f);

        if (primary != null) {
            fleet.setLocation(primary.getLocation().x, primary.getLocation().y);
        }

        return fleet;
    }
}