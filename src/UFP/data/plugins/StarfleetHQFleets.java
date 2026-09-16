package UFP.data.plugins;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase.PatrolFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory.PatrolType;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

public class StarfleetHQFleets {

    public static final String TAG_STARFLEET_HQ_DEFENSE = "STARFLEET_HQ_DEFENSE_FORCE";
    public static final String FLEET_NAME = "Starfleet Capital Battleforce";

    // Respawn cooldown timer in days
    private static float respawnTimer = 0f;

    // =========================================================
    // ADVANCE TICK & COOLDOWN MANAGEMENT
    // =========================================================
    public static void advance(float amount) {
        if (Global.getSector().getEconomy().isSimMode()) return;

        float days = Global.getSector().getClock().convertToDays(amount);

        if (respawnTimer > 0) {
            respawnTimer -= days;
            if (respawnTimer < 0) respawnTimer = 0;
        }

        // Iterate over markets with STARFLEET_HQ installed to handle spawning
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.hasIndustry(DimensionsCrossedIDS.STARFLEET_HQ)) {
                Industry hq = market.getIndustry(DimensionsCrossedIDS.STARFLEET_HQ);
                if (hq != null && hq.isFunctional()) {
                    checkAndSpawnDefenseForce(hq);
                }
            }
        }
    }

    // =========================================================
    // SPAWN CHECKER
    // =========================================================
    public static void checkAndSpawnDefenseForce(Industry industry) {
        MarketAPI market = industry.getMarket();
        String sid = market.getId() + "_starfleet_hq_defense";

        if (respawnTimer <= 0 && !isFleetActive(sid)) {
            spawnDefenseForce(industry, sid, market);
        }
    }

    private static boolean isFleetActive(String sid) {
        for (RouteData route : RouteManager.getInstance().getRoutesForSource(sid)) {
            if (route.getCustom() instanceof HQDefenseFleetData) {
                HQDefenseFleetData data = (HQDefenseFleetData) route.getCustom();
                if (TAG_STARFLEET_HQ_DEFENSE.equals(data.customTag)) {
                    return true; // Fleet is alive or route exists
                }
            }
        }
        return false;
    }

    // =========================================================
    // ROUTE CREATOR
    // =========================================================
    private static void spawnDefenseForce(Industry industry, String sid, MarketAPI market) {
        if (!(industry instanceof RouteFleetSpawner)) return;

        String chosenName = Math.random() < 0.5 ? FLEET_NAME : "Defense Taskforce";
        HQDefenseFleetData custom = new HQDefenseFleetData(TAG_STARFLEET_HQ_DEFENSE, chosenName);
        OptionalFleetData extra = new OptionalFleetData(market);
        extra.fleetType = "starfleetHQDefenseForce";

        RouteData route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, (RouteFleetSpawner) industry, custom);

        // 45-day patrol segment
        route.addSegment(new RouteSegment(45f, market.getPrimaryEntity()));
    }

    // =========================================================
    // FLEET COMPOSITION BUILDER
    // =========================================================
    public static CampaignFleetAPI createHQFleet(MarketAPI market, String fleetName) {
        Map<String, Integer> ships = new HashMap<>();
        ships.put(DimensionsCrossedIDS.SOVEREIGN, 4);
        ships.put(DimensionsCrossedIDS.GALAXY, 2);
        ships.put(DimensionsCrossedIDS.GALAXY_CARRIER, 2);
        ships.put(DimensionsCrossedIDS.NEBULA, 4);
        ships.put(DimensionsCrossedIDS.NEBULA_SENSOR, 4);
        ships.put(DimensionsCrossedIDS.AMBASSADOR, 6);
        ships.put(DimensionsCrossedIDS.EXCELSIOR, 5);
        ships.put(DimensionsCrossedIDS.INTREPID, 2);
        ships.put(DimensionsCrossedIDS.SABER, 3);
        ships.put(DimensionsCrossedIDS.VENTURE, 12);

        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(market.getFactionId(), fleetName, true);

        // Add standard fleet composition
        for (Map.Entry<String, Integer> entry : ships.entrySet()) {
            String variantId = entry.getKey();
            int count = entry.getValue();
            for (int i = 0; i < count; i++) {
                FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
                fleet.getFleetData().addFleetMember(member);
            }
        }

        // Add "USS Sovereign"
        FleetMemberAPI ussSovereign = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.SOVEREIGN);
        ussSovereign.setShipName("USS Sovereign");
        fleet.getFleetData().addFleetMember(ussSovereign);

        // Add "USS Galaxy"
        FleetMemberAPI ussGalaxy = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.GALAXY);
        ussGalaxy.setShipName("USS Galaxy");
        fleet.getFleetData().addFleetMember(ussGalaxy);

        fleet.getCommander().setPostId(Ranks.POST_PATROL_COMMANDER);
        fleet.getCommander().setRankId(Ranks.SPACE_ADMIRAL);

        return fleet;
    }

    public static void notifyFleetDespawned(String customTag, boolean wasDestroyed) {
        if (TAG_STARFLEET_HQ_DEFENSE.equals(customTag)) {
            // Respawn in 5 days if destroyed in combat, or 7 days if despawned (e.g., completed patrol)
            respawnTimer = wasDestroyed ? 5f : 7f;
        }
    }

    // =========================================================
    // CUSTOM FLEET DATA CLASS
    // =========================================================
    public static class HQDefenseFleetData extends PatrolFleetData {
        public String customTag;
        public String fleetName;

        public HQDefenseFleetData(String customTag, String fleetName) {
            super(PatrolType.HEAVY);
            this.customTag = customTag;
            this.fleetName = fleetName;
        }
    }
}