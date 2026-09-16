package TERRAN.data.plugins;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase.PatrolFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory.PatrolType;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolAssignmentAIV4;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc;

import TERRAN.data.campaign.ids.TerranIDS;

public class ImperialCapitalFleetPatrol {

    public static final String TAG_IMPERIAL_CAPITAL_DEFENSE = "IMPERIAL_CAPITAL_DEFENSE_FORCE";
    public static final String FLEET_NAME = "Imperial Suppression Battleforce";

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

        // Iterate over markets with IMPERIAL_CAPITAL installed to handle spawning
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.hasIndustry(TerranIDS.IMPERIAL_CAPITAL)) {
                Industry capital = market.getIndustry(TerranIDS.IMPERIAL_CAPITAL);
                if (capital != null && capital.isFunctional()) {
                    checkAndSpawnDefenseForce(capital);
                }
            }
        }
    }

    // =========================================================
    // SPAWN CHECKER
    // =========================================================
    public static void checkAndSpawnDefenseForce(Industry industry) {
        MarketAPI market = industry.getMarket();
        String sid = market.getId() + "_imperial_capital_defense";

        // Only 1 of this fleet can exist at a time
        if (respawnTimer <= 0 && !isFleetActive(sid)) {
            spawnDefenseForce(industry, sid, market);
        }
    }

    private static boolean isFleetActive(String sid) {
        for (RouteData route : RouteManager.getInstance().getRoutesForSource(sid)) {
            if (route.getCustom() instanceof ImperialCapitalFleetData) {
                ImperialCapitalFleetData data = (ImperialCapitalFleetData) route.getCustom();
                if (TAG_IMPERIAL_CAPITAL_DEFENSE.equals(data.customTag)) {
                    return true; // Fleet is alive or route is active
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

        ImperialCapitalFleetData custom = new ImperialCapitalFleetData(TAG_IMPERIAL_CAPITAL_DEFENSE, FLEET_NAME);
        OptionalFleetData extra = new OptionalFleetData(market);
        extra.fleetType = "imperialCapitalDefenseForce";

        RouteData route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, (RouteFleetSpawner) industry, custom);

        // Fleet patrols for 30 days before despawning
        route.addSegment(new RouteSegment(30f, market.getPrimaryEntity()));
    }

    // =========================================================
    // FLEET COMPOSITION BUILDER
    // =========================================================
    public static CampaignFleetAPI createImperialCapitalFleet(MarketAPI market, String fleetName) {
        Map<String, Integer> ships = new HashMap<>();
        ships.put(TerranIDS.SOVEREIGN, 2);
        ships.put(TerranIDS.GALAXY, 2);
        ships.put(TerranIDS.AMBASSADOR, 10);
        ships.put(TerranIDS.CONSTELLATION, 4);
        ships.put(TerranIDS.EXCELSIOR, 2);
        ships.put(TerranIDS.MIRANDA, 12);
        ships.put(TerranIDS.NOVA, 6);
        ships.put(TerranIDS.DEFIANT, 4);

        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(market.getFactionId(), fleetName, true);

        // Add ships to fleet
        for (Map.Entry<String, Integer> entry : ships.entrySet()) {
            String variantId = entry.getKey();
            int count = entry.getValue();
            for (int i = 0; i < count; i++) {
                FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
                fleet.getFleetData().addFleetMember(member);
            }
        }

        // Set Commander
        fleet.getCommander().setPostId(Ranks.POST_PATROL_COMMANDER);
        fleet.getCommander().setRankId(Ranks.SPACE_ADMIRAL);

        // Add 6 Officers to unassigned ships
        int officersAdded = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (officersAdded >= 6) break;
            if (member.isFlagship() || member.getCaptain() != null && !member.getCaptain().isDefault()) continue;

            PersonAPI officer = OfficerManagerEvent.createOfficer(fleet.getFaction(), 3, true);
            member.setCaptain(officer);
            fleet.getFleetData().addOfficer(officer);
            officersAdded++;
        }

        // Apply standard combat aggression behavior without overriding faction reputation
        applyAggressiveBehavior(fleet);

        return fleet;
    }

    private static void applyAggressiveBehavior(CampaignFleetAPI fleet) {
        // Keeps combat tactic pursuit aggressive against actual enemies, without overriding market/player reputation
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
    }

    // =========================================================
    // FLEET INITIALIZATION & SPATIAL PLACEMENT
    // =========================================================
    public static void initAndSpawnFleet(CampaignFleetAPI fleet, RouteData route, MarketAPI market, Industry industry, ImperialCapitalFleetData custom) {
        if (fleet == null || fleet.isEmpty()) return;

        if (industry instanceof FleetEventListener) {
            fleet.addEventListener((FleetEventListener) industry);
        }

        market.getContainingLocation().addEntity(fleet);

        fleet.setFacing((float) Math.random() * 360f);
        fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

        fleet.addScript(new PatrolAssignmentAIV4(fleet, route));
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);

        custom.spawnFP = fleet.getFleetPoints();
    }

    // =========================================================
    // DESPAWN & RESPAWN TIMER NOTIFIER
    // =========================================================
    public static void notifyFleetDespawned(String customTag, boolean wasDestroyed) {
        if (TAG_IMPERIAL_CAPITAL_DEFENSE.equals(customTag)) {
            // 7-day cooldown if destroyed in combat, 5 days if despawned for shore leave
            respawnTimer = wasDestroyed ? 7f : 5f;
        }
    }

    // =========================================================
    // CUSTOM FLEET DATA CLASS
    // =========================================================
    public static class ImperialCapitalFleetData extends PatrolFleetData {
        public String customTag;
        public String fleetName;

        public ImperialCapitalFleetData(String customTag, String fleetName) {
            super(PatrolType.HEAVY);
            this.customTag = customTag;
            this.fleetName = fleetName;
        }
    }
}