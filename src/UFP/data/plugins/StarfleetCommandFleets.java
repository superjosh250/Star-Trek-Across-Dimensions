package UFP.data.plugins;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import TERRAN.data.campaign.ids.TerranIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase.PatrolFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory.PatrolType;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolAssignmentAIV4;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc;

import UFP.data.campaign.ids.DimensionsCrossedIDS;


public class StarfleetCommandFleets {

    public static final String TAG_GALAXY_WING = "GALAXY_WING_TASKFORCE";
    public static final String TAG_STARFLEET_TASKFORCE_ONE = "STARFLEET_TASKFORCE_ONE";
    public static final String TAG_TERRAN_GALAXY_WING = "TERRAN_GALAXY_WING";
    public static final String TAG_IMPERIAL_FLEET_ONE = "IMPERIAL_FLEET_ONE";

    // Mod ID constant from mod_info.json
    public static final String TERRAN_EMPIRE_MOD_ID = "terran_empire";

    // Track respawn cooldown timers in days
    private static float galaxyWingRespawnTimer = 0f;
    private static float taskforceOneRespawnTimer = 0f;
    private static float terranGalaxyWingRespawnTimer = 0f;
    private static float imperialFleetOneRespawnTimer = 0f;

    // Faction permissions map: FactionID -> Set of allowed fleet tags
    private static final Map<String, Set<String>> allowedFleetTypesByFaction = new HashMap<>();

    // =========================================================
    // MOD ENABLED CHECK & SKIP METHOD
    // =========================================================
    public static boolean isTerranEmpireActive() {
        if (Global.getSettings().getModManager().isModEnabled(TERRAN_EMPIRE_MOD_ID)) {
            return true;
        }

        try {
            return Global.getSettings().getModManager().isModEnabled(DimensionsCrossedIDS.STAD_TERRAN);
        } catch (Throwable t) {
            return false;
        }
    }

    // =========================================================
    // FACTION PERMISSION & ALLOW LIST METHODS
    // =========================================================
    public static void setFleetTypeAllowedForFaction(String factionId, String customTag, boolean allowed) {
        if (factionId == null || customTag == null) return;

        Set<String> allowedTags = allowedFleetTypesByFaction.computeIfAbsent(factionId, k -> new HashSet<>());
        if (allowed) {
            allowedTags.add(customTag);
        } else {
            allowedTags.remove(customTag);
        }
    }

    public static boolean canSpawnFleetType(String factionId, String customTag) {
        if (factionId == null || customTag == null) return false;

        if ((TAG_TERRAN_GALAXY_WING.equals(customTag) || TAG_IMPERIAL_FLEET_ONE.equals(customTag)) && !isTerranEmpireActive()) {
            return false;
        }

        if (!allowedFleetTypesByFaction.containsKey(factionId)) {
            return true; // Unrestricted by default
        }
        return allowedFleetTypesByFaction.get(factionId).contains(customTag);
    }

    // =========================================================
    // ADVANCE TICK & COOLDOWN MANAGEMENT
    // =========================================================
    public static void advance(float amount) {
        if (Global.getSector().getEconomy().isSimMode()) return;

        float days = Global.getSector().getClock().convertToDays(amount);

        if (galaxyWingRespawnTimer > 0) {
            galaxyWingRespawnTimer -= days;
            if (galaxyWingRespawnTimer < 0) galaxyWingRespawnTimer = 0;
        }

        if (taskforceOneRespawnTimer > 0) {
            taskforceOneRespawnTimer -= days;
            if (taskforceOneRespawnTimer < 0) taskforceOneRespawnTimer = 0;
        }

        if (terranGalaxyWingRespawnTimer > 0) {
            terranGalaxyWingRespawnTimer -= days;
            if (terranGalaxyWingRespawnTimer < 0) terranGalaxyWingRespawnTimer = 0;
        }

        if (imperialFleetOneRespawnTimer > 0) {
            imperialFleetOneRespawnTimer -= days;
            if (imperialFleetOneRespawnTimer < 0) imperialFleetOneRespawnTimer = 0;
        }

        // Iterate over markets with STARFLEET_COMMAND installed to handle spawning
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market.hasIndustry(DimensionsCrossedIDS.STARFLEET_COMMAND)) {
                Industry command = market.getIndustry(DimensionsCrossedIDS.STARFLEET_COMMAND);
                if (command != null && command.isFunctional()) {
                    checkAndSpawnSpecialFleets(command);
                }
            }
        }
    }

    // =========================================================
    // SPAWN CHECKER
    // =========================================================
    public static void checkAndSpawnSpecialFleets(Industry industry) {
        MarketAPI market = industry.getMarket();
        String factionId = market.getFactionId();
        String sid = market.getId() + "_starfleet_command_special";

        // 1. Spawn Galaxy Wing One (Max 1 globally across sector)
        if (canSpawnFleetType(factionId, TAG_GALAXY_WING) && isIndustryAllowedToSpawn(industry, TAG_GALAXY_WING)) {
            if (galaxyWingRespawnTimer <= 0 && !isGlobalFleetActive(TAG_GALAXY_WING)) {
                spawnGalaxyWingOne(industry, sid, market);
            }
        }

        // 2. Spawn Starfleet Capital Taskforce (Max 1 globally across sector)
        if (canSpawnFleetType(factionId, TAG_STARFLEET_TASKFORCE_ONE) && isIndustryAllowedToSpawn(industry, TAG_STARFLEET_TASKFORCE_ONE)) {
            if (taskforceOneRespawnTimer <= 0 && !isGlobalFleetActive(TAG_STARFLEET_TASKFORCE_ONE)) {
                spawnStarfleetCapitalTaskforce(industry, sid, market);
            }
        }

        // --- TERRAN EMPIRE MOD CHECK GUARD ---
        if (!isTerranEmpireActive()) {
            return; // Skip evaluation and spawning for Terran Empire fleets entirely
        }

        // 3. Spawn Terran Galaxy Wing (Max 1 globally across sector)
        if (canSpawnFleetType(factionId, TAG_TERRAN_GALAXY_WING) && isIndustryAllowedToSpawn(industry, TAG_TERRAN_GALAXY_WING)) {
            if (terranGalaxyWingRespawnTimer <= 0 && !isGlobalFleetActive(TAG_TERRAN_GALAXY_WING)) {
                spawnTerranGalaxyWing(industry, sid, market);
            }
        }

        // 4. Spawn Imperial Fleet One (Max 1 globally across sector)
        if (canSpawnFleetType(factionId, TAG_IMPERIAL_FLEET_ONE) && isIndustryAllowedToSpawn(industry, TAG_IMPERIAL_FLEET_ONE)) {
            if (imperialFleetOneRespawnTimer <= 0 && !isGlobalFleetActive(TAG_IMPERIAL_FLEET_ONE)) {
                spawnImperialFleetOne(industry, sid, market);
            }
        }
    }

    public static boolean isIndustryAllowedToSpawn(Industry industry, String customTag) {
        if (industry instanceof SpecialFleetSelector) {
            return ((SpecialFleetSelector) industry).shouldSpawnFleetType(customTag);
        }
        return true;
    }

    /**
     * Checks globally across all markets for active routes with the given tag.
     * Guarantees maximum 1 fleet instance across the entire sector.
     */
    private static boolean isGlobalFleetActive(String customTag) {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            String sid = market.getId() + "_starfleet_command_special";
            for (RouteManager.RouteData route : RouteManager.getInstance().getRoutesForSource(sid)) {
                if (route.getCustom() instanceof SpecialTaskforceFleetData) {
                    SpecialTaskforceFleetData data = (SpecialTaskforceFleetData) route.getCustom();
                    if (customTag.equals(data.customTag)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // =========================================================
    // ROUTE CREATORS
    // =========================================================
    private static void spawnGalaxyWingOne(Industry industry, String sid, MarketAPI market) {
        if (!(industry instanceof RouteFleetSpawner)) return;

        SpecialTaskforceFleetData custom = new SpecialTaskforceFleetData(TAG_GALAXY_WING, "Galaxy Wing One");
        RouteManager.OptionalFleetData extra = new RouteManager.OptionalFleetData(market);
        extra.fleetType = "galaxyWingTaskforce";

        RouteManager.RouteData route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, (RouteFleetSpawner) industry, custom);
        route.addSegment(new RouteManager.RouteSegment(30f, market.getPrimaryEntity()));

        CampaignFleetAPI fleet = createGalaxyWingOneFleet(market);
        initAndSpawnFleet(fleet, route, market, industry, custom);
    }

    private static void spawnStarfleetCapitalTaskforce(Industry industry, String sid, MarketAPI market) {
        if (!(industry instanceof RouteFleetSpawner)) return;

        SpecialTaskforceFleetData custom = new SpecialTaskforceFleetData(TAG_STARFLEET_TASKFORCE_ONE, "Starfleet Capital Taskforce");
        RouteManager.OptionalFleetData extra = new RouteManager.OptionalFleetData(market);
        extra.fleetType = "starfleetCapitalTaskforce";

        RouteManager.RouteData route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, (RouteFleetSpawner) industry, custom);
        route.addSegment(new RouteManager.RouteSegment(30f, market.getPrimaryEntity()));

        CampaignFleetAPI fleet = createCapitalTaskforceFleet(market);
        initAndSpawnFleet(fleet, route, market, industry, custom);
    }

    private static void spawnTerranGalaxyWing(Industry industry, String sid, MarketAPI market) {
        if (!(industry instanceof RouteFleetSpawner) || !isTerranEmpireActive()) return;

        SpecialTaskforceFleetData custom = new SpecialTaskforceFleetData(TAG_TERRAN_GALAXY_WING, "Terran Galaxy Wing");
        RouteManager.OptionalFleetData extra = new RouteManager.OptionalFleetData(market);
        extra.fleetType = "terranGalaxyWing";

        RouteManager.RouteData route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, (RouteFleetSpawner) industry, custom);
        route.addSegment(new RouteManager.RouteSegment(30f, market.getPrimaryEntity()));

        CampaignFleetAPI fleet = createTerranGalaxyWingFleet(market);
        initAndSpawnFleet(fleet, route, market, industry, custom);
    }

    private static void spawnImperialFleetOne(Industry industry, String sid, MarketAPI market) {
        if (!(industry instanceof RouteFleetSpawner) || !isTerranEmpireActive()) return;

        SpecialTaskforceFleetData custom = new SpecialTaskforceFleetData(TAG_IMPERIAL_FLEET_ONE, "Imperial Fleet One");
        RouteManager.OptionalFleetData extra = new RouteManager.OptionalFleetData(market);
        extra.fleetType = "imperialFleetOne";

        RouteManager.RouteData route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, (RouteFleetSpawner) industry, custom);
        route.addSegment(new RouteManager.RouteSegment(30f, market.getPrimaryEntity()));

        CampaignFleetAPI fleet = createImperialFleetOneFleet(market);
        initAndSpawnFleet(fleet, route, market, industry, custom);
    }

    // =========================================================
    // FLEET COMPOSITION BUILDERS
    // =========================================================
    private static CampaignFleetAPI createGalaxyWingOneFleet(MarketAPI market) {
        Map<String, Integer> ships = new HashMap<>();
        ships.put(DimensionsCrossedIDS.GALAXY, 8);
        ships.put(DimensionsCrossedIDS.GALAXY_CARRIER, 2);
        ships.put(DimensionsCrossedIDS.NEBULA, 4);
        ships.put(DimensionsCrossedIDS.NEBULA_SENSOR, 2);
        ships.put(DimensionsCrossedIDS.VENTURE, 8);

        return buildFleetFromVariantMap("Galaxy Wing One", market, ships);
    }

    private static CampaignFleetAPI createCapitalTaskforceFleet(MarketAPI market) {
        Map<String, Integer> ships = new HashMap<>();
        ships.put(DimensionsCrossedIDS.SOVEREIGN, 2);
        ships.put(DimensionsCrossedIDS.GALAXY, 4);
        ships.put(DimensionsCrossedIDS.AMBASSADOR, 2);
        ships.put(DimensionsCrossedIDS.EXCELSIOR, 4);
        ships.put(DimensionsCrossedIDS.INTREPID, 2);
        ships.put(DimensionsCrossedIDS.STEAMRUNNER, 4);
        ships.put(DimensionsCrossedIDS.MIRANDA, 5);
        ships.put(DimensionsCrossedIDS.VENTURE, 8);

        return buildFleetFromVariantMap("Starfleet Capital Taskforce", market, ships);
    }

    public static CampaignFleetAPI createTerranGalaxyWingFleet(MarketAPI market) {
        if (!isTerranEmpireActive()) return null;

        Map<String, Integer> ships = new HashMap<>();
        ships.put(TerranIDS.GALAXY, 10);
        ships.put(TerranIDS.NEBULA, 6);
        ships.put(TerranIDS.EXCELSIOR, 2);
        ships.put(TerranIDS.SABER, 4);
        ships.put(DimensionsCrossedIDS.VENTURE, 10);

        return buildFleetFromVariantMap("Terran Galaxy Wing", market, ships);
    }

    public static CampaignFleetAPI createImperialFleetOneFleet(MarketAPI market) {
        if (!isTerranEmpireActive()) return null;

        Map<String, Integer> ships = new HashMap<>();
        ships.put(TerranIDS.SOVEREIGN, 5);
        ships.put(TerranIDS.GALAXY, 4);
        ships.put(TerranIDS.NEBULA, 8);
        ships.put(TerranIDS.INTREPID, 2);
        ships.put(TerranIDS.EXCELSIOR, 6);
        ships.put(TerranIDS.SABER, 6);
        ships.put(TerranIDS.DEFIANT, 2);

        return buildFleetFromVariantMap("Imperial Fleet One", market, ships);
    }

    private static CampaignFleetAPI buildFleetFromVariantMap(String fleetName, MarketAPI market, Map<String, Integer> ships) {
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(market.getFactionId(), fleetName, true);

        for (Map.Entry<String, Integer> entry : ships.entrySet()) {
            String variantId = entry.getKey();
            int count = entry.getValue();
            for (int i = 0; i < count; i++) {
                FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
                fleet.getFleetData().addFleetMember(member);
            }
        }

        fleet.getCommander().setPostId(Ranks.POST_PATROL_COMMANDER);
        fleet.getCommander().setRankId(Ranks.SPACE_ADMIRAL);

        return fleet;
    }

    // =========================================================
    // FLEET INITIALIZATION & DESPAWN LISTENERS
    // =========================================================
    private static void initAndSpawnFleet(CampaignFleetAPI fleet, RouteManager.RouteData route, MarketAPI market, Industry industry, SpecialTaskforceFleetData custom) {
        if (fleet == null || fleet.isEmpty()) return;

        if (industry instanceof FleetEventListener) {
            fleet.addEventListener((FleetEventListener) industry);
        }

        market.getContainingLocation().addEntity(fleet);

        fleet.setFacing((float) Math.random() * 360f);
        fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

        fleet.addScript(new PatrolAssignmentAIV4(fleet, route));
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true, 0.3f);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);

        custom.spawnFP = fleet.getFleetPoints();
    }

    public static void notifyFleetDespawned(String customTag) {
        if (TAG_GALAXY_WING.equals(customTag)) {
            galaxyWingRespawnTimer = 5f;
        } else if (TAG_STARFLEET_TASKFORCE_ONE.equals(customTag)) {
            taskforceOneRespawnTimer = 5f;
        } else if (TAG_TERRAN_GALAXY_WING.equals(customTag)) {
            terranGalaxyWingRespawnTimer = 5f;
        } else if (TAG_IMPERIAL_FLEET_ONE.equals(customTag)) {
            imperialFleetOneRespawnTimer = 5f;
        }
    }

    // =========================================================
    // INTERFACE FOR INDUSTRY OVERRIDES
    // =========================================================
    public interface SpecialFleetSelector {
        boolean shouldSpawnFleetType(String customTag);
    }

    // =========================================================
    // CUSTOM FLEET DATA CLASS
    // =========================================================
    public static class SpecialTaskforceFleetData extends PatrolFleetData {
        public String customTag;
        public String fleetName;

        public SpecialTaskforceFleetData(String customTag, String fleetName) {
            super(PatrolType.HEAVY);
            this.customTag = customTag;
            this.fleetName = fleetName;
        }
    }
}