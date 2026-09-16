package TERRAN.data.scripts;

import TERRAN.data.campaign.ids.TerranIDS;
import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class TerranSubjugationColonization implements EveryFrameScript {

    private final IntervalUtil colonyGrowthInterval = new IntervalUtil(15.0f, 15.0f);
    private transient boolean transientSaveRepaired = false;

    private static final Set<String> ORE_CONDITIONS = new HashSet<>(Arrays.asList(
            Conditions.ORE_SPARSE, Conditions.ORE_MODERATE, Conditions.ORE_ABUNDANT,
            Conditions.ORE_RICH, Conditions.ORE_ULTRARICH, Conditions.RARE_ORE_SPARSE,
            Conditions.RARE_ORE_MODERATE, Conditions.RARE_ORE_ABUNDANT,
            Conditions.RARE_ORE_RICH, Conditions.RARE_ORE_ULTRARICH
    ));

    private static final Set<String> FARMLAND_CONDITIONS = new HashSet<>(Arrays.asList(
            Conditions.FARMLAND_POOR, Conditions.FARMLAND_ADEQUATE,
            Conditions.FARMLAND_BOUNTIFUL, Conditions.FARMLAND_RICH
    ));

    @Override public boolean isDone() { return false; }
    @Override public boolean runWhilePaused() { return false; }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.isInNewGameAdvance()) return;

        if (!transientSaveRepaired) {
            repairSectorMarketLinks();
            transientSaveRepaired = true;
        }

        float days = sector.getClock().convertToDays(amount);
        colonyGrowthInterval.advance(days);
        if (colonyGrowthInterval.intervalElapsed()) {
            repairSectorMarketLinks();
            updateSubjugatedColonies();
        }
    }

    /**
     * Scans active markets to repair missing primary entity links and purge
     * orphaned/null references from connected entity sets cleanly.
     */
    public static void repairSectorMarketLinks() {
        try {
            SectorAPI sector = Global.getSector();
            if (sector == null || sector.getEconomy() == null) return;

            List<MarketAPI> activeMarkets = sector.getEconomy().getMarketsCopy();
            if (activeMarkets == null) return;

            Set<MarketAPI> activeMarketSet = new HashSet<>(activeMarkets);

            for (MarketAPI market : activeMarkets) {
                if (market == null) continue;

                SectorEntityToken primary = market.getPrimaryEntity();
                if (primary != null && primary.getMarket() != market) {
                    primary.setMarket(market);
                }

                Set<SectorEntityToken> connected = market.getConnectedEntities();
                if (connected != null) {
                    connected.removeIf(entity -> entity == null || entity.getMarket() == null || !activeMarketSet.contains(entity.getMarket()));
                }
            }

            for (LocationAPI loc : sector.getAllLocations()) {
                if (loc == null) continue;
                for (SectorEntityToken entity : loc.getAllEntities()) {
                    if (entity != null && entity.getMarket() != null && !activeMarketSet.contains(entity.getMarket())) {
                        entity.setMarket(null);
                    }
                }
            }
        } catch (Throwable t) {
            Global.getLogger(TerranSubjugationColonization.class).error("Error during sector market link repair scan", t);
        }
    }

    /**
     * Initializes and converts a world while preserving existing planet conditions cleanly.
     */
    public static void establishTerranColony(PlanetAPI planet, CampaignFleetAPI fleet) {
        if (planet == null) return;

        MarketAPI oldMarket = planet.getMarket();
        List<String> preservedConditions = new ArrayList<>();

        if (oldMarket != null) {
            oldMarket.getMemoryWithoutUpdate().set(DecivTracker.NO_DECIV_KEY, true);
            cleanupRoutesAndRaids(oldMarket, planet);
            detachEntitiesAndStations(oldMarket, planet);
            preservedConditions = collectPreservedConditions(oldMarket);
            Global.getSector().getEconomy().removeMarket(oldMarket);
        }

        MarketAPI market = Global.getFactory().createMarket("terran_subjugated_" + planet.getId(), planet.getName(), 3);

        planet.setFaction(TerranIDS.TERRAN);
        planet.setMarket(market);
        market.setPrimaryEntity(planet);
        market.setFactionId(TerranIDS.TERRAN);
        market.setSize(3);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);

        if (oldMarket != null) {
            oldMarket.setPrimaryEntity(planet);
        }

        if (!market.getConnectedEntities().contains(planet)) {
            market.getConnectedEntities().add(planet);
        }

        for (String condId : preservedConditions) {
            if (!market.hasCondition(condId)) {
                market.addCondition(condId);
            }
        }

        market.addCondition(Conditions.POPULATION_3);
        market.addCondition(Conditions.DECIVILIZED_SUBPOP);

        market.addIndustry(DimensionsCrossedIDS.SOCIETY_POPULATION);
        market.addIndustry(DimensionsCrossedIDS.STARBASE_SPACEPORT);
        market.addIndustry(Industries.WAYSTATION);

        market.addSubmarket(Submarkets.SUBMARKET_OPEN);
        market.addSubmarket(Submarkets.SUBMARKET_BLACK);
        market.addSubmarket(Submarkets.SUBMARKET_STORAGE);

        Global.getSector().getEconomy().addMarket(market, true);
        market.reapplyIndustries();
        market.reapplyConditions();

        Global.getSector().getCampaignUI().addMessage(
                "The Terran Empire has fully pacified " + planet.getName() + " and established a permanent military colony!",
                Color.GREEN
        );

        Global.getSector().getMemoryWithoutUpdate().unset(TerranSubjugationFleet.TARGET_WORLD_MEM_KEY);
        convertSubjugationFleetToPatrol(fleet, market);
    }

    private static void cleanupRoutesAndRaids(MarketAPI oldMarket, PlanetAPI planet) {
        // 1. Remove active EveryFrameScripts (e.g. BlockadeWrapperIntel) targeting oldMarket/planet
        try {
            SectorAPI sector = Global.getSector();
            List<EveryFrameScript> scriptsToRemove = new ArrayList<>();
            for (EveryFrameScript script : sector.getScripts()) {
                if (script != null && scriptInvolvesMarketOrPlanet(script, oldMarket, planet)) {
                    scriptsToRemove.add(script);
                }
            }
            for (EveryFrameScript script : scriptsToRemove) {
                sector.removeScript(script);
                sector.removeTransientScript(script);
            }
        } catch (Throwable t) {
            Global.getLogger(TerranSubjugationColonization.class).warn("Failed to purge sector scripts for old market", t);
        }

        // 2. Reflectively fetch protected routes list from RouteManager
        List<RouteManager.RouteData> allRoutes = getAllRoutesSafe();
        for (RouteManager.RouteData route : allRoutes) {
            if (route != null && route.getMarket() == oldMarket) {
                RouteManager.getInstance().removeRoute(route);
            }
        }

        // 3. Safely cancel active raids/intel involving oldMarket / planet
        try {
            List<IntelInfoPlugin> activeIntel = Global.getSector().getIntelManager().getIntel();
            if (activeIntel != null) {
                for (IntelInfoPlugin intel : new ArrayList<>(activeIntel)) {
                    if (intel == null || !intelInvolvesMarketOrPlanet(intel, oldMarket, planet)) continue;

                    if (intel instanceof RaidIntel) {
                        RaidIntel raid = (RaidIntel) intel;
                        List<RaidIntel.RaidStage> stages = getRaidStagesSafe(raid);
                        if (stages != null) {
                            for (RaidIntel.RaidStage stage : stages) {
                                repairStageRoutes(stage, oldMarket);
                            }
                        }
                    }
                    try {
                        Global.getSector().getIntelManager().removeIntel(intel);
                    } catch (Throwable t) {
                        // Suppress uninitialized lateinit exceptions during forced removal
                    }
                }
            }
        } catch (Throwable t) {
            Global.getLogger(TerranSubjugationColonization.class).warn("Failed to wrap up active raid intel prior to market swap", t);
        }
    }

    private static boolean scriptInvolvesMarketOrPlanet(EveryFrameScript script, MarketAPI oldMarket, PlanetAPI planet) {
        if (script instanceof RaidIntel) {
            return raidInvolvesMarketOrPlanet((RaidIntel) script, oldMarket, planet);
        }
        Object targetObj = invokeGetterSafe(script, "getTarget");
        Object marketObj = invokeGetterSafe(script, "getMarket");
        return targetObj == oldMarket || targetObj == planet || marketObj == oldMarket;
    }

    private static boolean intelInvolvesMarketOrPlanet(IntelInfoPlugin intel, MarketAPI oldMarket, PlanetAPI planet) {
        if (intel instanceof RaidIntel) {
            return raidInvolvesMarketOrPlanet((RaidIntel) intel, oldMarket, planet);
        }
        Object targetObj = invokeGetterSafe(intel, "getTarget");
        Object marketObj = invokeGetterSafe(intel, "getMarket");
        return targetObj == oldMarket || targetObj == planet || marketObj == oldMarket;
    }

    @SuppressWarnings("unchecked")
    private static List<RouteManager.RouteData> getAllRoutesSafe() {
        try {
            Field routesField = RouteManager.class.getDeclaredField("routes");
            routesField.setAccessible(true);
            Object result = routesField.get(RouteManager.getInstance());
            if (result instanceof List) {
                return new ArrayList<>((List<RouteManager.RouteData>) result);
            }
        } catch (Throwable t) {
            Global.getLogger(TerranSubjugationColonization.class).warn("Failed to reflectively access RouteManager routes", t);
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static List<RaidIntel.RaidStage> getRaidStagesSafe(RaidIntel raid) {
        try {
            Field stagesField = RaidIntel.class.getDeclaredField("stages");
            stagesField.setAccessible(true);
            Object result = stagesField.get(raid);
            if (result instanceof List) {
                return (List<RaidIntel.RaidStage>) result;
            }
        } catch (Throwable t) {
            // Fallback for custom subclasses
        }
        return null;
    }

    private static void repairStageRoutes(RaidIntel.RaidStage stage, MarketAPI oldMarket) {
        if (stage == null) return;
        Object routesObj = invokeGetterSafe(stage, "getRoutes");
        if (routesObj == null) {
            routesObj = getFieldValueSafe(stage, "routes");
        }
        if (routesObj instanceof Collection) {
            for (Object item : (Collection<?>) routesObj) {
                if (item instanceof RouteManager.RouteData) {
                    RouteManager.RouteData r = (RouteManager.RouteData) item;
                    if (r.getMarket() == null || r.getMarket().getPrimaryEntity() == null) {
                        setRouteMarketSafe(r, oldMarket);
                    }
                }
            }
        }
    }

    private static void setRouteMarketSafe(RouteManager.RouteData route, MarketAPI market) {
        try {
            Field marketField = RouteManager.RouteData.class.getDeclaredField("market");
            marketField.setAccessible(true);
            marketField.set(route, market);
        } catch (Throwable t) {
            Global.getLogger(TerranSubjugationColonization.class).warn("Failed to set RouteData market field reflectively", t);
        }
    }

    private static void detachEntitiesAndStations(MarketAPI oldMarket, PlanetAPI planet) {
        LocationAPI location = oldMarket.getContainingLocation();
        if (location == null) return;

        List<SectorEntityToken> connectedEntities = new ArrayList<>(oldMarket.getConnectedEntities());
        for (SectorEntityToken entity : connectedEntities) {
            if (entity != null && !(entity instanceof PlanetAPI)) {
                entity.setMarket(null);
                location.removeEntity(entity);
            }
        }
        oldMarket.getConnectedEntities().clear();

        for (SectorEntityToken station : new ArrayList<>(location.getEntitiesWithTag(Tags.STATION))) {
            if (station != null && !(station instanceof PlanetAPI)) {
                if (station.getMarket() == oldMarket || station.getOrbitFocus() == planet) {
                    station.setMarket(null);
                    location.removeEntity(station);
                }
            }
        }
    }

    private static List<String> collectPreservedConditions(MarketAPI oldMarket) {
        List<String> preserved = new ArrayList<>();
        for (MarketConditionAPI cond : oldMarket.getConditions()) {
            if (cond == null || cond.getId() == null) continue;
            String condId = cond.getId();

            if (condId.startsWith("population_") ||
                    condId.equals(Conditions.PATHER_CELLS) ||
                    condId.equals(Conditions.PIRATE_ACTIVITY) ||
                    condId.equals(Conditions.DECIVILIZED) ||
                    condId.equals(Conditions.DECIVILIZED_SUBPOP)) {
                continue;
            }

            if (cond.getSpec() != null && cond.getSpec().isPlanetary()) {
                preserved.add(condId);
            }
        }
        return preserved;
    }

    private static boolean raidInvolvesMarketOrPlanet(RaidIntel raid, MarketAPI oldMarket, PlanetAPI planet) {
        if (raid == null) return false;

        if (raid.getSystem() != null && planet != null && raid.getSystem() == planet.getContainingLocation()) {
            if (invokeGetterSafe(raid, "getMarket") == oldMarket) return true;
        }

        if (raid.getAssembleStage() != null) {
            Object sourcesObj = invokeGetterSafe(raid.getAssembleStage(), "getSources");
            if (sourcesObj instanceof Collection && ((Collection<?>) sourcesObj).contains(oldMarket)) {
                return true;
            }
        }

        String[] getters = {"getTarget", "getTargetMarket", "getMarket", "getSource", "getSourceMarket", "getFrom", "getMarketFrom"};
        for (String getter : getters) {
            Object obj = invokeGetterSafe(raid, getter);
            if (obj == oldMarket || obj == planet) return true;
        }

        return false;
    }

    private static Object invokeGetterSafe(Object obj, String methodName) {
        if (obj == null) return null;
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                Method m = clazz.getDeclaredMethod(methodName);
                m.setAccessible(true);
                return m.invoke(obj);
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static Object getFieldValueSafe(Object obj, String fieldName) {
        if (obj == null) return null;
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                Field f = clazz.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }

    private static void convertSubjugationFleetToPatrol(CampaignFleetAPI fleet, MarketAPI newMarket) {
        if (fleet == null) return;
        MemoryAPI fleetMem = fleet.getMemoryWithoutUpdate();

        fleetMem.unset(TerranSubjugationFleet.FLEET_IDENTIFIER_KEY);
        fleetMem.unset(MemFlags.MEMORY_KEY_MISSION_IMPORTANT);
        fleetMem.unset(MemFlags.MEMORY_KEY_WAR_FLEET);
        fleetMem.unset(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED);
        fleetMem.unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);

        fleetMem.set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        fleet.setName("Terran Colonial Defense Patrol");

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, newMarket.getPrimaryEntity(), 9999f, "patrolling the system to protect " + newMarket.getName());
    }

    private void updateSubjugatedColonies() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == null || !TerranIDS.TERRAN.equals(market.getFactionId())) continue;
            if (market.getId() == null || !market.getId().startsWith("terran_subjugated_")) continue;

            PlanetAPI planet = market.getPlanetEntity();
            if (planet == null) continue;

            boolean updated = tryGrowColony(market, planet);
            if (tryExpandInfrastructure(market, planet)) {
                updated = true;
            }

            if (updated) {
                market.reapplyIndustries();
                market.reapplyConditions();
            }
        }
    }

    private boolean tryGrowColony(MarketAPI market, PlanetAPI planet) {
        if (market.getSize() >= 10 || Math.random() >= 0.025f) return false;

        int oldSize = market.getSize();
        CoreImmigrationPluginImpl.increaseMarketSize(market);
        int newSize = market.getSize();

        if (newSize > oldSize) {
            ListenerUtil.reportColonySizeChanged(market, oldSize);
            Global.getSector().getCampaignUI().addMessage(
                    "The Terran colony on " + planet.getName() + " has expanded to Size " + newSize + "!",
                    Color.GREEN
            );
            return true;
        }
        return false;
    }

    private boolean tryExpandInfrastructure(MarketAPI market, PlanetAPI planet) {
        float income = market.getGrossIncome();
        int size = market.getSize();

        if (!market.hasIndustry(DimensionsCrossedIDS.MINING_ADVANCED) && hasAnyCondition(market, ORE_CONDITIONS)) {
            return buildStructure(market, planet, DimensionsCrossedIDS.MINING_ADVANCED, "Advanced Mining Network");
        }
        if (!market.hasIndustry(Industries.FARMING) && hasAnyCondition(market, FARMLAND_CONDITIONS)) {
            return buildStructure(market, planet, Industries.FARMING, "Farming Co-ops");
        }
        if (market.hasIndustry(DimensionsCrossedIDS.MINING_ADVANCED) && !market.hasIndustry(DimensionsCrossedIDS.REFINING_PROCESSING_PLANT)) {
            return buildStructure(market, planet, DimensionsCrossedIDS.REFINING_PROCESSING_PLANT, "Advanced Refining Plant");
        }

        if (size >= 4) {
            if (buildStructureIfMissing(market, planet, DimensionsCrossedIDS.SALVAGE_OPS, "Terran Salvage Operations")) return true;
            if (buildStructureIfMissing(market, planet, DimensionsCrossedIDS.ORBITALSTATION_SPACEDOCK, "Orbital Station Spacedock")) return true;
            if (buildStructureIfMissing(market, planet, DimensionsCrossedIDS.DRYDOCK, "Starfleet Drydock")) return true;
        }
        if (size >= 5) {
            if (buildStructureIfMissing(market, planet, DimensionsCrossedIDS.UNIVERSITY, "Imperial University")) return true;
        }
        if (size >= 6) {
            if (buildStructureIfMissing(market, planet, DimensionsCrossedIDS.DRYDOCK_ADVANCED, "Advanced Drydock Facility")) return true;
            if (buildStructureIfMissing(market, planet, DimensionsCrossedIDS.R_N_D, "Imperial Research & Development Labs")) return true;
            if (income >= 50000) {
                if (buildStructureIfMissing(market, planet, DimensionsCrossedIDS.INDUSTRIAL_REPLICATOR, "Industrial Replicator Complex")) return true;
                if (buildStructureIfMissing(market, planet, DimensionsCrossedIDS.STARFLEET_COMMAND, "Starfleet Command Headquarters")) return true;
            }
        }
        if (size >= 7) {
            if (buildStructureIfMissing(market, planet, DimensionsCrossedIDS.STARFLEET_HQ, "Starfleet High Command HQ")) return true;
        }
        if (size >= 8) {
            if (buildStructureIfMissing(market, planet, DimensionsCrossedIDS.STARFLEET_ACADEMY, "Starfleet Academy Campus")) return true;
            if (buildStructureIfMissing(market, planet, DimensionsCrossedIDS.COALITION_PATROL, "Coalition Defense Patrol Command")) return true;
        }

        return false;
    }

    private boolean buildStructureIfMissing(MarketAPI market, PlanetAPI planet, String industryId, String displayName) {
        if (!market.hasIndustry(industryId)) {
            return buildStructure(market, planet, industryId, displayName);
        }
        return false;
    }

    private boolean buildStructure(MarketAPI market, PlanetAPI planet, String industryId, String displayName) {
        if (market.hasIndustry(industryId)) return false;

        IndustrySpecAPI spec = Global.getSettings().getIndustrySpec(industryId);
        if (spec != null && spec.hasTag(Industries.TAG_INDUSTRY)) {
            if (Misc.getNumIndustries(market) >= Misc.getMaxIndustries(market)) {
                return false;
            }
        }

        market.addIndustry(industryId);
        if (market.hasIndustry(industryId)) {
            market.getIndustry(industryId).startBuilding();
        }

        Global.getSector().getCampaignUI().addMessage(
                "The Terran military administration on " + planet.getName() + " began construction of a " + displayName + "!",
                Color.CYAN
        );
        return true;
    }

    private boolean hasAnyCondition(MarketAPI market, Set<String> conditions) {
        for (String condition : conditions) {
            if (market.hasCondition(condition)) return true;
        }
        return false;
    }

    public static int getModIndustryCount(MarketAPI market) {
        int count = 0;
        for (String indId : DimensionsCrossedIDS.ALL_INDUSTRIES) {
            if (market.hasIndustry(indId)) count++;
        }
        return count;
    }

    public static boolean hasAllModIndustries(MarketAPI market) {
        for (String indId : DimensionsCrossedIDS.ALL_INDUSTRIES) {
            if (!market.hasIndustry(indId)) return false;
        }
        return true;
    }
}