package TERRAN.data.scripts;

import TERRAN.data.campaign.ids.TerranIDS;
import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.util.IntervalUtil;

import java.awt.Color;
import java.util.List;

public class TerranOppositionForce implements EveryFrameScript {

    // =========================================================================
    // CONFIGURATION / SPAWN CHANCE OVERRIDES (0.0f = 0%, 1.0f = 100%)
    // =========================================================================
    public static float CHANCE_DEFENDER_RESPONSE = 1.00f; // Default: 100% chance
    public static float CHANCE_UFP_SUPPORT       = 0.75f; // Default: 75% chance
    public static float CHANCE_NON_UFP_ALLIED    = 0.15f; // Default: 15% chance per faction

    // =========================================================================
    // INTERNAL TRACKING
    // =========================================================================
    private final IntervalUtil trackerInterval = new IntervalUtil(2.0f, 4.0f);

    // Fleet-level key to ensure every new subjugation fleet triggers defense once
    private static final String FLEET_DEFENSE_TRIGGERED_KEY = "$terran_opp_response_spawned";

    private static final String REMNANT_STATION_KEY = "$terran_remnant_station_created_";
    private static final String ORIGINAL_FACTION_KEY = "$terran_original_owner_";

    // Opposition Fleet Tracking Memory Keys
    private static final String MEM_OPP_MANAGED = "$terran_opp_managed";
    private static final String MEM_SUBJUGATION_ID = "$terran_opp_subjugation_id";
    private static final String MEM_HOME_ENTITY_ID = "$terran_opp_home_entity_id";
    private static final String MEM_RETURNING_HOME = "$terran_opp_returning_home";

    // Station Entity Options
    private static final String[] REMNANT_STATION_TYPES = {
            Entities.MAKESHIFT_STATION,
            Entities.ORBITAL_HABITAT,
            Entities.STATION_MINING,
            Entities.STATION_RESEARCH
    };

    @Override public boolean isDone() { return false; }
    @Override public boolean runWhilePaused() { return false; }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.isInNewGameAdvance()) return;

        float days = sector.getClock().convertToDays(amount);
        trackerInterval.advance(days);

        if (trackerInterval.intervalElapsed()) {
            coordinateDefensesAndAllies();
            manageActiveOppositionFleets();
            handleFallenWorldRemnants();
        }
    }

    /**
     * Identifies active subjugation fleets and launches defense + supporting fleets.
     * Fires per subjugation fleet launch rather than locking the planet forever.
     */
    private void coordinateDefensesAndAllies() {
        for (LocationAPI loc : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI fleet : loc.getFleets()) {
                if (!fleet.isAlive()) continue;

                // Check for active Terran Subjugation Fleet
                if (fleet.getMemoryWithoutUpdate().contains(TerranSubjugationFleet.FLEET_IDENTIFIER_KEY)) {

                    // Check if this specific subjugation fleet has already triggered defenses
                    if (fleet.getMemoryWithoutUpdate().getBoolean(FLEET_DEFENSE_TRIGGERED_KEY)) continue;

                    String targetId = fleet.getMemoryWithoutUpdate().getString(TerranSubjugationFleet.FLEET_TARGET_ID_KEY);
                    if (targetId == null) continue;

                    SectorEntityToken entity = Global.getSector().getEntityById(targetId);
                    if (!(entity instanceof PlanetAPI)) continue;

                    PlanetAPI targetPlanet = (PlanetAPI) entity;
                    MarketAPI targetMarket = targetPlanet.getMarket();
                    if (targetMarket == null) continue;

                    String defenderFactionId = targetMarket.getFactionId();
                    if (defenderFactionId.equals(TerranIDS.TERRAN) || defenderFactionId.equals(Factions.NEUTRAL)) continue;

                    // Tag this fleet so it doesn't repeatedly spawn responses during its lifetime
                    fleet.getMemoryWithoutUpdate().set(FLEET_DEFENSE_TRIGGERED_KEY, true);

                    // Save original owner faction ID for remnant station creation if planet falls
                    Global.getSector().getMemoryWithoutUpdate().set(ORIGINAL_FACTION_KEY + targetPlanet.getId(), defenderFactionId);

                    // 1. Dispatch Defender Interception Fleet (Always 100% via CHANCE_DEFENDER_RESPONSE = 1.00f)
                    if (Math.random() < CHANCE_DEFENDER_RESPONSE) {
                        spawnDefenderFleet(targetPlanet, defenderFactionId, fleet);
                    }

                    // 2. Dispatch Allied Support Fleets (Controlled by CHANCE_UFP_SUPPORT & CHANCE_NON_UFP_ALLIED)
                    spawnSupportFleets(targetPlanet, defenderFactionId, fleet);
                }
            }
        }
    }

    /**
     * Defense fleet from the defender faction's strongest market.
     */
    private void spawnDefenderFleet(PlanetAPI targetPlanet, String defenderFactionId, CampaignFleetAPI subjugationFleet) {
        MarketAPI source = getStrongestMarketForFaction(defenderFactionId);
        SectorEntityToken spawnPoint = source != null ? source.getPrimaryEntity() : targetPlanet;

        CampaignFleetAPI defenderFleet = createFleet(defenderFactionId, source, 350f, "Interception Taskforce");
        if (defenderFleet == null) return;

        configureFleetAndAddIntel(defenderFleet, targetPlanet, subjugationFleet, spawnPoint);

        spawnPoint.getContainingLocation().addEntity(defenderFleet);
        defenderFleet.setLocation(spawnPoint.getLocation().x + 50f, spawnPoint.getLocation().y + 50f);

        Global.getSector().getCampaignUI().addMessage(
                "DEFENSIVE RESPONSE: " + defenderFleet.getName() + " mobilized to protect " + targetPlanet.getName() + "!",
                Color.ORANGE
        );
    }

    /**
     * Spawns support fleets from enemy factions and UFP according to quick key percentages, excluding PIRATES.
     */
    private void spawnSupportFleets(PlanetAPI targetPlanet, String defenderFactionId, CampaignFleetAPI subjugationFleet) {
        FactionAPI terranFaction = Global.getSector().getFaction(TerranIDS.TERRAN);

        // UFP Support Check
        if (Math.random() < CHANCE_UFP_SUPPORT) {
            FactionAPI ufpFaction = Global.getSector().getFaction(DimensionsCrossedIDS.UFP);
            if (ufpFaction != null && !DimensionsCrossedIDS.UFP.equals(defenderFactionId)) {
                spawnSupportFleetFromFaction(DimensionsCrossedIDS.UFP, targetPlanet, subjugationFleet, "UFP Expeditionary Fleet");
            }
        }

        // Other Hostile Enemy Factions Check
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            String fId = faction.getId();

            // Exclude Terrans, Defenders, PIRATES, Neutral, and UFP
            if (fId.equals(TerranIDS.TERRAN)
                    || fId.equals(defenderFactionId)
                    || fId.equals(Factions.PIRATES)
                    || fId.equals(Factions.NEUTRAL)
                    || fId.equals(DimensionsCrossedIDS.UFP)) {
                continue;
            }

            // Check if faction is hostile to Terrans
            if (terranFaction.isHostileTo(faction) || faction.isHostileTo(terranFaction)) {
                if (Math.random() < CHANCE_NON_UFP_ALLIED) {
                    spawnSupportFleetFromFaction(fId, targetPlanet, subjugationFleet, faction.getDisplayName() + " Support Fleet");
                }
            }
        }
    }

    private void spawnSupportFleetFromFaction(String factionId, PlanetAPI targetPlanet, CampaignFleetAPI subjugationFleet, String fleetName) {
        MarketAPI source = getStrongestMarketForFaction(factionId);
        SectorEntityToken spawnPoint = source != null ? source.getPrimaryEntity() : targetPlanet;

        CampaignFleetAPI supportFleet = createFleet(factionId, source, 250f, fleetName);
        if (supportFleet == null) return;

        configureFleetAndAddIntel(supportFleet, targetPlanet, subjugationFleet, spawnPoint);

        spawnPoint.getContainingLocation().addEntity(supportFleet);
        supportFleet.setLocation(spawnPoint.getLocation().x + 80f, supportFleet.getLocation().y + 80f);

        Global.getSector().getCampaignUI().addMessage(
                "ALLIED REINFORCEMENTS: " + supportFleet.getName() + " en route to engage Terran forces at " + targetPlanet.getName() + "!",
                Color.CYAN
        );
    }

    /**
     * Enforces aggressive navigation, assigns fleet memory tags, calculates 15% bounty, and attaches Intel.
     */
    private void configureFleetAndAddIntel(CampaignFleetAPI fleet, PlanetAPI targetPlanet, CampaignFleetAPI subjugationFleet, SectorEntityToken spawnPoint) {
        // Keeps transit smooth without sidetracks
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_FIGHT_TO_THE_LAST, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, false);

        // Store management tracking keys
        fleet.getMemoryWithoutUpdate().set(MEM_OPP_MANAGED, true);
        fleet.getMemoryWithoutUpdate().set(MEM_SUBJUGATION_ID, subjugationFleet.getId());
        if (spawnPoint != null) {
            fleet.getMemoryWithoutUpdate().set(MEM_HOME_ENTITY_ID, spawnPoint.getId());
        }

        fleet.clearAssignments();
        // 1. Cross-system pathing to target planet
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, targetPlanet, 1000f, "rushing to defend " + targetPlanet.getName());
        // 2. Hold station at planet until target fleet arrives
        fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, targetPlanet, 1000f, "holding defense perimeter at " + targetPlanet.getName());

        // Calculate 15% fleet bounty value
        float bounty15Percent = calculate15PercentFleetWorth(fleet);

        // Register individual Intel via newly extracted class
        TerranOppositionIntel intel = new TerranOppositionIntel(fleet, targetPlanet, bounty15Percent);
        Global.getSector().getIntelManager().addIntel(intel);
    }

    /**
     * Periodically updates fleet behaviors:
     * - Ensures fleets hold orbit at the planet until the invasion fleet enters the target system.
     * - Only triggers aggressive interception once in the same in-system location.
     * - Sends fleets home to despawn once the invasion force is destroyed.
     */
    private void manageActiveOppositionFleets() {
        for (LocationAPI loc : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI fleet : loc.getFleets()) {
                if (!fleet.isAlive() || !fleet.getMemoryWithoutUpdate().getBoolean(MEM_OPP_MANAGED)) continue;

                if (fleet.getMemoryWithoutUpdate().getBoolean(MEM_RETURNING_HOME)) continue;

                String subjugationId = fleet.getMemoryWithoutUpdate().getString(MEM_SUBJUGATION_ID);
                CampaignFleetAPI subjugationFleet = null;

                if (subjugationId != null) {
                    SectorEntityToken entity = Global.getSector().getEntityById(subjugationId);
                    if (entity instanceof CampaignFleetAPI) {
                        subjugationFleet = (CampaignFleetAPI) entity;
                    }
                }

                // If the subjugation fleet is defeated or despawned -> return home and despawn
                if (subjugationFleet == null || !subjugationFleet.isAlive() || subjugationFleet.getContainingLocation() == null) {
                    fleet.getMemoryWithoutUpdate().set(MEM_RETURNING_HOME, true);
                    fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
                    fleet.clearAssignments();

                    SectorEntityToken homeEntity = null;
                    String homeId = fleet.getMemoryWithoutUpdate().getString(MEM_HOME_ENTITY_ID);
                    if (homeId != null) {
                        homeEntity = Global.getSector().getEntityById(homeId);
                    }

                    if (homeEntity != null) {
                        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, homeEntity, 1000f, "returning to home base");
                    } else {
                        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, fleet.getContainingLocation().createToken(0, 0), 1000f, "disbanding after successful defense");
                    }
                    continue;
                }

                // Identify target planet from the subjugation fleet
                SectorEntityToken targetEntity = null;
                String targetId = subjugationFleet.getMemoryWithoutUpdate().getString(TerranSubjugationFleet.FLEET_TARGET_ID_KEY);
                if (targetId != null) {
                    targetEntity = Global.getSector().getEntityById(targetId);
                }

                LocationAPI fleetLoc = fleet.getContainingLocation();
                LocationAPI enemyLoc = subjugationFleet.getContainingLocation();

                // Check: Are both fleets in the SAME IN-SYSTEM LOCATION (and explicitly not in Hyperspace)?
                boolean inSameStarSystem = fleetLoc != null && !fleetLoc.isHyperspace() && fleetLoc == enemyLoc;

                if (inSameStarSystem) {
                    // Unset ignore flag so campaign collisions trigger battle interactions
                    fleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
                    fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);

                    FleetAssignmentDataAPI currentAssignment = fleet.getCurrentAssignment();
                    if (currentAssignment == null || currentAssignment.getAssignment() != FleetAssignment.INTERCEPT) {
                        fleet.clearAssignments();
                        fleet.addAssignment(FleetAssignment.INTERCEPT, subjugationFleet, 9999f, "engaging Terran subjugation force");
                    }
                } else {
                    // En route or waiting at target world -> keep transit flags set so fleet isn't distracted
                    fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);

                    // Refresh defensive assignment at the planet to prevent wandering or abandonment
                    if (targetEntity != null) {
                        FleetAssignmentDataAPI currentAssignment = fleet.getCurrentAssignment();
                        if (currentAssignment == null ||
                                (currentAssignment.getAssignment() != FleetAssignment.GO_TO_LOCATION &&
                                        currentAssignment.getAssignment() != FleetAssignment.DEFEND_LOCATION &&
                                        currentAssignment.getAssignment() != FleetAssignment.ORBIT_PASSIVE)) {

                            fleet.clearAssignments();
                            fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, targetEntity, 9999f, "holding defensive perimeter at " + targetEntity.getName());
                        }
                    }
                }
            }
        }
    }

    private float calculate15PercentFleetWorth(CampaignFleetAPI fleet) {
        if (fleet == null) return 0f;
        float totalValue = 0f;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            totalValue += member.getBaseValue();
        }
        float bounty = totalValue * 0.15f;
        return Math.max(5000f, Math.round(bounty / 1000f) * 1000f);
    }

    /**
     * Checks if a planet has fallen and spawns a remnant station in-system.
     */
    private void handleFallenWorldRemnants() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == null || !market.getFactionId().equals(TerranIDS.TERRAN)) continue;
            if (!market.getId().startsWith("terran_subjugated_")) continue;

            PlanetAPI planet = market.getPlanetEntity();
            if (planet == null) continue;

            String remnantKey = REMNANT_STATION_KEY + planet.getId();

            if (!Global.getSector().getMemoryWithoutUpdate().getBoolean(remnantKey)) {
                Global.getSector().getMemoryWithoutUpdate().set(remnantKey, true);
                spawnRemnantStation(planet);
            }
        }
    }

    private void spawnRemnantStation(PlanetAPI targetPlanet) {
        StarSystemAPI system = targetPlanet.getStarSystem();
        if (system == null) return;

        String stationType = REMNANT_STATION_TYPES[(int) (Math.random() * REMNANT_STATION_TYPES.length)];

        String savedFactionKey = ORIGINAL_FACTION_KEY + targetPlanet.getId();
        String formerFaction = Global.getSector().getMemoryWithoutUpdate().contains(savedFactionKey) ?
                Global.getSector().getMemoryWithoutUpdate().getString(savedFactionKey) : Factions.INDEPENDENT;

        SectorEntityToken centerPoint = system.getCenter() != null ? system.getCenter() : targetPlanet;
        float orbitDistance = 2000f + (float) (Math.random() * 1500f);

        SectorEntityToken stationEntity = system.addCustomEntity(
                "remnant_station_" + targetPlanet.getId(),
                targetPlanet.getName() + " Remnant Station",
                stationType,
                formerFaction
        );
        stationEntity.setCircularOrbit(centerPoint, (float) (Math.random() * 360f), orbitDistance, 100f);

        int stationSize = 3 + (int) (Math.random() * 3);

        MarketAPI stationMarket = Global.getFactory().createMarket(
                "remnant_market_" + targetPlanet.getId(),
                stationEntity.getName(),
                stationSize
        );

        stationMarket.setPrimaryEntity(stationEntity);
        stationMarket.setFactionId(formerFaction);
        stationMarket.setSize(stationSize);
        stationMarket.setSurveyLevel(MarketAPI.SurveyLevel.FULL);

        stationEntity.setMarket(stationMarket);

        stationMarket.addCondition("population_" + stationSize);
        stationMarket.addIndustry(Industries.POPULATION);
        stationMarket.addIndustry(Industries.SPACEPORT);
        stationMarket.addIndustry(Industries.WAYSTATION);
        stationMarket.addIndustry(Industries.BATTLESTATION_MID);

        stationMarket.addSubmarket(Submarkets.SUBMARKET_OPEN);
        stationMarket.addSubmarket(Submarkets.SUBMARKET_BLACK);
        stationMarket.addSubmarket(Submarkets.SUBMARKET_STORAGE);

        Global.getSector().getEconomy().addMarket(stationMarket, true);
        stationMarket.reapplyIndustries();
        stationMarket.reapplyConditions();

        Global.getSector().getCampaignUI().addMessage(
                "REMNANT STATION FORMED: Displaced survivors from " + targetPlanet.getName() + " established " + stationEntity.getName() + "!",
                Color.YELLOW
        );
    }

    private CampaignFleetAPI createFleet(String factionId, MarketAPI source, float fp, String nameSuffix) {
        FleetParamsV3 params = new FleetParamsV3(
                source != null ? source.getLocationInHyperspace() : null,
                factionId,
                1.1f,
                FleetTypes.PATROL_LARGE,
                fp,
                0f, 0f, 0f, 0f, 0f, 0f
        );
        params.source = source;
        params.withOfficers = true;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet != null) {
            FactionAPI faction = Global.getSector().getFaction(factionId);
            String factionName = faction != null ? faction.getDisplayName() : "";
            fleet.setName(factionName + " " + nameSuffix);
        }
        return fleet;
    }

    private MarketAPI getStrongestMarketForFaction(String factionId) {
        MarketAPI bestMarket = null;
        int maxMarketSize = -1;

        List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
        for (MarketAPI market : markets) {
            if (market != null && market.getFactionId().equals(factionId) && market.getPrimaryEntity() != null) {
                if (market.getSize() > maxMarketSize) {
                    maxMarketSize = market.getSize();
                    bestMarket = market;
                }
            }
        }
        return bestMarket;
    }
}