package UFP.data.scripts;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class UFP_ColonizationProtocol implements EveryFrameScript {

    private static final String FACTION_ID = DimensionsCrossedIDS.UFP;
    private static final String MEM_KEY_COLONIZED_BY_UFP_PROTOCOL = "$ufp_colonized_by_protocol";
    private static final String MEM_KEY_COLONIZATION_RESERVED = ColonizationFleetManager.MEM_KEY_COLONIZATION_RESERVED;

    private final IntervalUtil interval = new IntervalUtil(25f, 35f);
    private static final float COLONIZE_CHANCE_PER_CHECK = 0.12f;
    private static final int MAX_DYNAMIC_UFP_COLONIES = 8;

    private static final float COLONY_FLEET_DEPARTURE_DELAY_DAYS = 20f;
    private static final float COLONY_SETUP_DAYS = 12f;

    private ColonizationFleetManager fleetManager;

    private static final Set<String> VIABLE_TYPES = new HashSet<>(Arrays.asList(
            "terran", "terran-eccentric", "jungle", "water", "arid", "tundra", "desert", "desert1", "barren-desert",
            DimensionsCrossedIDS.PLANET_EARTH,
            DimensionsCrossedIDS.PLANET_TERRAFORMING_MOON, DimensionsCrossedIDS.PLANET_TERRAFORMED_MOON,
            DimensionsCrossedIDS.PLANET_TERRAFORMING_MARS, DimensionsCrossedIDS.PLANET_TERRAFORMED_MARS
    ));

    @Override public boolean isDone() { return false; }
    @Override public boolean runWhilePaused() { return false; }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.isInNewGameAdvance()) return;

        float days = sector.getClock().convertToDays(amount);

        // FIX 1: Fetch and safely link the fleet manager at the absolute START of the frame.
        // Using the MemoryAPI lifecycle method prevents Save/Load Null pointers.
        if (fleetManager == null) {
            fleetManager = ColonizationFleetManager.getInstance(FACTION_ID, this::createUfpColony);
        }
        if (fleetManager == null) return;

        // Progress the fleet operations tracker framework
        fleetManager.advance(days);

        // Advance timing intervals once per frame execution
        interval.advance(days);

        // Safe to run now because fleetManager is guaranteed to exist
        if (!interval.intervalElapsed() || sector.getFaction(FACTION_ID) == null ||
                (countDynamicUfpColonies() + fleetManager.getActiveOperationCount()) >= MAX_DYNAMIC_UFP_COLONIES) {
            return;
        }

        WeightedRandomPicker<PlanetAPI> picker = new WeightedRandomPicker<>();
        for (StarSystemAPI system : sector.getStarSystems()) {
            if (system == null) continue;
            for (PlanetAPI planet : system.getPlanets()) {
                if (isEligibleCandidate(planet)) {
                    picker.add(planet, getColonizationWeight(planet.getTypeId()));
                }
            }
        }

        if (!picker.isEmpty() && (float) Math.random() <= COLONIZE_CHANCE_PER_CHECK) {
            PlanetAPI target = picker.pick();
            if (target != null) {
                // FIX 2: Removed immediate createUfpColony invocation.
                // The fleet manager will call this via callback ONLY when the fleet safely arrives!
                fleetManager.queueColonization(target, COLONY_FLEET_DEPARTURE_DELAY_DAYS, COLONY_SETUP_DAYS);
            }
        }
    }

    private boolean isEligibleCandidate(PlanetAPI planet) {
        if (planet == null || planet.isStar()) return false;

        // Match against valid planet type configuration ids
        if (!VIABLE_TYPES.contains(planet.getTypeId())) return false;

        if (planet.getMemoryWithoutUpdate().getBoolean(MEM_KEY_COLONIZED_BY_UFP_PROTOCOL)
                || planet.getMemoryWithoutUpdate().getBoolean(MEM_KEY_COLONIZATION_RESERVED)
                || (fleetManager != null && fleetManager.isPlanetReserved(planet))) {
            return false;
        }

        MarketAPI market = planet.getMarket();
        return market != null && market.getSurveyLevel() == MarketAPI.SurveyLevel.FULL &&
                !(market.isInEconomy() && market.getPrimaryEntity() == planet && market.getSize() >= 3);
    }

    private float getColonizationWeight(String typeId) {
        if (typeId == null) return 0f;
        return switch (typeId) {
            case "terran", DimensionsCrossedIDS.PLANET_EARTH -> 12f;
            case "terran-eccentric" -> 11f;
            case "jungle" -> 10f;
            case DimensionsCrossedIDS.PLANET_TERRAFORMING_MOON, DimensionsCrossedIDS.PLANET_TERRAFORMED_MOON,
                 DimensionsCrossedIDS.PLANET_TERRAFORMING_MARS, DimensionsCrossedIDS.PLANET_TERRAFORMED_MARS -> 8f;
            case "arid" -> 6f;
            case "water" -> 5f;
            case "tundra", "desert", "desert1" -> 4f;
            case "barren-desert" -> 2f;
            default -> 0f;
        };
    }

    private int countDynamicUfpColonies() {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getEconomy() == null) return 0;
        int count = 0;
        for (MarketAPI market : sector.getEconomy().getMarketsCopy()) {
            if (market != null && FACTION_ID.equals(market.getFactionId()) &&
                    market.getMemoryWithoutUpdate().getBoolean(MEM_KEY_COLONIZED_BY_UFP_PROTOCOL)) {
                count++;
            }
        }
        return count;
    }

    private void createUfpColony(PlanetAPI planet) {
        MarketAPI market = planet.getMarket();
        boolean newMarket = false;

        if (market == null || !market.isInEconomy()) {
            market = Global.getFactory().createMarket("ufp_colony_" + planet.getId(), planet.getName(), 3);
            newMarket = true;
        }

        market.setPrimaryEntity(planet);
        market.setFactionId(FACTION_ID);
        market.setPlayerOwned(false);
        market.setFreePort(false);
        market.setHidden(false);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        market.setSize(3);

        planet.setFaction(FACTION_ID);
        planet.setMarket(market);

        addConditionIfMissing(market, Conditions.POPULATION_3);
        addConditionIfMissing(market, Conditions.OUTPOST);
        addConditionIfMissing(market, Conditions.FRONTIER);

        applyPlanetFlavorConditions(market, planet.getTypeId());

        addIndustryIfMissing(market, DimensionsCrossedIDS.SOCIETY_POPULATION);
        addIndustryIfMissing(market, DimensionsCrossedIDS.STARBASE_SPACEPORT);
        addIndustryIfMissing(market, isAgriculturalWorld(planet.getTypeId()) ? Industries.FARMING : DimensionsCrossedIDS.MINING_ADVANCED);
        addIndustryIfMissing(market, Industries.WAYSTATION);

        addSubmarketIfMissing(market, Submarkets.SUBMARKET_OPEN);
        addSubmarketIfMissing(market, Submarkets.SUBMARKET_BLACK);
        addSubmarketIfMissing(market, Submarkets.SUBMARKET_STORAGE);
        addSubmarketIfMissing(market, DimensionsCrossedIDS.SUBMARKET_FEDERATION);

        market.getMemoryWithoutUpdate().set(MEM_KEY_COLONIZED_BY_UFP_PROTOCOL, true);
        planet.getMemoryWithoutUpdate().set(MEM_KEY_COLONIZED_BY_UFP_PROTOCOL, true);

        if (newMarket || !market.isInEconomy()) {
            Global.getSector().getEconomy().addMarket(market, true);
        }

        market.reapplyIndustries();
        Global.getLogger(UFP_ColonizationProtocol.class).info("UFP established a colony on: " + planet.getName());
    }

    private void applyPlanetFlavorConditions(MarketAPI market, String typeId) {
        if (typeId == null) return;
        switch (typeId) {
            case "terran", DimensionsCrossedIDS.PLANET_EARTH -> {
                addConditionIfMissing(market, Conditions.HABITABLE);
                addConditionIfMissing(market, Conditions.MILD_CLIMATE);
                addConditionIfMissing(market, Conditions.FARMLAND_ADEQUATE);
            }
            case "terran-eccentric" -> {
                addConditionIfMissing(market, Conditions.HABITABLE);
                addConditionIfMissing(market, Conditions.FARMLAND_POOR);
            }
            case "jungle", "water" -> {
                addConditionIfMissing(market, Conditions.HABITABLE);
                addConditionIfMissing(market, Conditions.ORGANICS_COMMON);
                addConditionIfMissing(market, Conditions.FARMLAND_ADEQUATE);
            }
            case DimensionsCrossedIDS.PLANET_TERRAFORMING_MOON, DimensionsCrossedIDS.PLANET_TERRAFORMING_MARS -> {
                addConditionIfMissing(market, DimensionsCrossedIDS.TERRAFORMING);
                addConditionIfMissing(market, Conditions.HABITABLE);
            }
            case DimensionsCrossedIDS.PLANET_TERRAFORMED_MOON, DimensionsCrossedIDS.PLANET_TERRAFORMED_MARS -> {
                addConditionIfMissing(market, DimensionsCrossedIDS.TERRAFORMED);
                addConditionIfMissing(market, Conditions.HABITABLE);
            }
            case "arid" -> {
                addConditionIfMissing(market, Conditions.HABITABLE);
                addConditionIfMissing(market, Conditions.FARMLAND_POOR);
            }
            case "tundra", "desert", "desert1" -> addConditionIfMissing(market, Conditions.HABITABLE);
            case "barren-desert" -> addConditionIfMissing(market, Conditions.ORE_SPARSE);
        }
    }

    private boolean isAgriculturalWorld(String typeId) {
        if (typeId == null) return false;
        return switch (typeId) {
            case "terran", "terran-eccentric", "jungle", "water", "arid", DimensionsCrossedIDS.PLANET_EARTH -> true;
            default -> false;
        };
    }

    private void addConditionIfMissing(MarketAPI market, String conditionId) {
        if (market != null && conditionId != null && !market.hasCondition(conditionId)) market.addCondition(conditionId);
    }

    private void addIndustryIfMissing(MarketAPI market, String industryId) {
        if (market != null && industryId != null && !market.hasIndustry(industryId)) market.addIndustry(industryId);
    }

    private void addSubmarketIfMissing(MarketAPI market, String submarketId) {
        if (market != null && submarketId != null && !market.hasSubmarket(submarketId)) market.addSubmarket(submarketId);
    }
}