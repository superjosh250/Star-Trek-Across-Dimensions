package data.scripts.starsystems;

import UFP.data.campaign.event.USS_SovereignEvent;
import UFP.data.campaign.ids.DimensionsCrossedIDS;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.util.IntervalUtil;

public class HollandMarket implements SectorGeneratorPlugin {

    private static final String SYSTEM_NAME = "Holland";
    private static final String NEW_HOLLAND_ID = "holland_new_holland";
    private static final String PLATFORM_ID = "holland_nh_platform";

    @Override
    public void generate(SectorAPI sector) {
        StarSystemAPI system = sector.getStarSystem(SYSTEM_NAME);
        if (system == null) return;

        SectorEntityToken platformToken = system.getEntityById(PLATFORM_ID);

        if (platformToken instanceof CustomCampaignEntityAPI) {
            CustomCampaignEntityAPI platform = (CustomCampaignEntityAPI) platformToken;
            if (platform.getMarket() == null) {
                createRandomPlatformMarket(sector, system, platform);
            }
        }

        ensureSovereignManagerRegistered();
    }

    public static void addHollandMarket(SectorAPI sector, PlanetAPI newHolland) {
        MarketAPI holland_market = Global.getFactory().createMarket("new_holland_market", "New Holland", 10);

        newHolland.setFaction(DimensionsCrossedIDS.UFP);
        holland_market.setPrimaryEntity(newHolland);
        holland_market.setFactionId(DimensionsCrossedIDS.UFP);

        holland_market.addCondition(Conditions.POPULATION_10);
        holland_market.addCondition(Conditions.FRONTIER);
        holland_market.addCondition(Conditions.TERRAN);
        holland_market.addCondition(Conditions.HABITABLE);
        holland_market.addCondition(Conditions.MILD_CLIMATE);
        holland_market.addCondition(Conditions.FARMLAND_RICH);
        holland_market.addCondition(Conditions.ORGANICS_ABUNDANT);
        holland_market.addCondition(DimensionsCrossedIDS.DILITHIUM_ORE_TRACE);
        holland_market.addCondition(Conditions.ORE_SPARSE);
        holland_market.addCondition(Conditions.RARE_ORE_ABUNDANT);
        holland_market.addCondition(Conditions.RURAL_POLITY);

        // --- Industries ---
        holland_market.addIndustry(DimensionsCrossedIDS.SOCIETY_POPULATION);
        holland_market.addIndustry(DimensionsCrossedIDS.STARFLEET_OPERATIONS);
        holland_market.addIndustry(DimensionsCrossedIDS.STARBASE_SPACEPORT);
        holland_market.addIndustry(DimensionsCrossedIDS.DRYDOCK_ADVANCED);
        holland_market.addIndustry(Industries.FARMING);
        holland_market.addIndustry(Industries.WAYSTATION);
        holland_market.addIndustry(DimensionsCrossedIDS.MINING_ADVANCED);
        holland_market.addIndustry(DimensionsCrossedIDS.INDUSTRIAL_REPLICATOR);
        holland_market.addIndustry(DimensionsCrossedIDS.REFINING_PROCESSING_PLANT);

        if (holland_market.hasIndustry(DimensionsCrossedIDS.MINING_ADVANCED)) {
            holland_market.getIndustry(DimensionsCrossedIDS.MINING_ADVANCED).setAICoreId(Commodities.ALPHA_CORE);
        }

        if (holland_market.hasIndustry(DimensionsCrossedIDS.STARFLEET_OPERATIONS)) {
            holland_market.getIndustry(DimensionsCrossedIDS.STARFLEET_OPERATIONS).setAICoreId(Commodities.ALPHA_CORE);
            holland_market.getIndustry(DimensionsCrossedIDS.STARFLEET_OPERATIONS)
                    .setSpecialItem(new SpecialItemData(Items.CRYOARITHMETIC_ENGINE, null));
        }

        if (holland_market.hasIndustry(DimensionsCrossedIDS.STARBASE_SPACEPORT)) {
            holland_market.getIndustry(DimensionsCrossedIDS.STARBASE_SPACEPORT)
                    .setSpecialItem(new SpecialItemData(Items.DEALMAKER_HOLOSUITE, null));
        }

        // --- Submarkets ---
        holland_market.addSubmarket(Submarkets.SUBMARKET_OPEN);
        holland_market.addSubmarket(Submarkets.SUBMARKET_BLACK);
        holland_market.addSubmarket(DimensionsCrossedIDS.SUBMARKET_FEDERATION);
        holland_market.addSubmarket(Submarkets.SUBMARKET_STORAGE);

        // Apply + register
        newHolland.setMarket(holland_market);
        sector.getEconomy().addMarket(holland_market, true);
        holland_market.reapplyIndustries();
    }

    private void createRandomPlatformMarket(SectorAPI sector, StarSystemAPI system, CustomCampaignEntityAPI platform) {

        int size = 5;

        MarketAPI market = Global.getFactory().createMarket(
                "market_holland_platform",
                "Starbase 12",
                size
        );

        market.setPrimaryEntity(platform);
        market.setFactionId(DimensionsCrossedIDS.UFP);
        market.setSize(size);
        market.setPlayerOwned(false);
        market.setFreePort(false);

        platform.setFaction(DimensionsCrossedIDS.UFP);

        // Industries
        market.addIndustry(DimensionsCrossedIDS.STATION_POPULATION);
        market.addIndustry(DimensionsCrossedIDS.STARBASE_SPACEPORT);
        market.addIndustry(Industries.COMMERCE);
        market.addIndustry(DimensionsCrossedIDS.WAYSTATION);
        market.addIndustry(DimensionsCrossedIDS.R_N_D);
        market.addIndustry(DimensionsCrossedIDS.DRYDOCK_ADVANCED);
        market.addIndustry(DimensionsCrossedIDS.INDUSTRIAL_REPLICATOR);
        market.addIndustry(DimensionsCrossedIDS.ORBITALSTATION_SPACEDOCK);
        market.addIndustry(DimensionsCrossedIDS.STARFLEET_OPERATIONS);

        // Market conditions
        market.addCondition(Conditions.ESTABLISHED_POLITY);
        market.addCondition(Conditions.INDUSTRIAL_POLITY);
        market.addCondition(Conditions.OUTPOST);
        market.addCondition(Conditions.HABITABLE);
        market.addCondition(Conditions.MILD_CLIMATE);
        market.addCondition(DimensionsCrossedIDS.STARBASE_HABITAT);
        market.addCondition(Conditions.POPULATION_5);

        // Submarkets
        if (!market.hasSubmarket(Submarkets.SUBMARKET_OPEN)) market.addSubmarket(Submarkets.SUBMARKET_OPEN);
        if (!market.hasSubmarket(DimensionsCrossedIDS.SUBMARKET_FEDERATION)) market.addSubmarket(DimensionsCrossedIDS.SUBMARKET_FEDERATION);
        if (!market.hasSubmarket(Submarkets.SUBMARKET_STORAGE)) market.addSubmarket(Submarkets.SUBMARKET_STORAGE);

        // Install AI cores + pristine nanoforge
        installAICoreIfPossible(market, DimensionsCrossedIDS.DRYDOCK, Commodities.ALPHA_CORE);
        installAICoreIfPossible(market, DimensionsCrossedIDS.DRYDOCK_ADVANCED, Commodities.ALPHA_CORE);

        installSpecialItemIfPossible(market, DimensionsCrossedIDS.DRYDOCK, Items.PRISTINE_NANOFORGE);
        installSpecialItemIfPossible(market, DimensionsCrossedIDS.DRYDOCK_ADVANCED, Items.PRISTINE_NANOFORGE);

        platform.setMarket(market);
        Global.getSector().getEconomy().addMarket(market, true);

        // Connect to New Holland
        SectorEntityToken nhToken = system.getEntityById(NEW_HOLLAND_ID);
        if (nhToken != null) {
            market.getConnectedEntities().add(nhToken);
        }
    }

    private static void ensureSovereignManagerRegistered() {
        boolean hasScript = Global.getSector().hasScript(SovereignPatrolManager.class);
        boolean hasListener = false;
        for (CampaignEventListener l : Global.getSector().getAllListeners()) {
            if (l instanceof SovereignPatrolManager) {
                hasListener = true;
                break;
            }
        }

        if (!hasScript || !hasListener) {
            SovereignPatrolManager manager = new SovereignPatrolManager();
            if (!hasScript) Global.getSector().addScript(manager);
            if (!hasListener) Global.getSector().addListener(manager);
        }
    }

    private void installAICoreIfPossible(MarketAPI market, String industryId, String aiCoreId) {
        Industry ind = market.getIndustry(industryId);
        if (ind == null) return;
        if (ind.canInstallAICores()) {
            ind.setAICoreId(aiCoreId);
        }
    }

    private void installSpecialItemIfPossible(MarketAPI market, String industryId, String itemId) {
        Industry ind = market.getIndustry(industryId);
        if (ind == null) return;
        ind.setSpecialItem(new SpecialItemData(itemId, null));
    }

    // --- Lightweight Sovereign Patrol Manager ---
    public static class SovereignPatrolManager extends BaseCampaignEventListener implements EveryFrameScript {

        private final IntervalUtil tracker = new IntervalUtil(1f, 1f); // Check daily
        private float respawnTimerDays = 7f; // Set to 7f so it spawns on day 1
        private CampaignFleetAPI sovereignFleet = null; // Direct reference for 0ms CPU lookup

        public SovereignPatrolManager() {
            super(false);
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public void reportPlayerEngagement(EngagementResultAPI result) {
            if (!USS_SovereignEvent.isRecruited()) return;

            CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
            if (playerFleet == null) return;

            boolean sovereignAlive = false;
            for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
                if (DimensionsCrossedIDS.USS_SOVEREIGN.equals(member.getHullId())) {
                    sovereignAlive = true;
                    break;
                }
            }

            if (!sovereignAlive) {
                Global.getSector().getMemoryWithoutUpdate().unset(USS_SovereignEvent.KEY_RECRUITED);
                respawnTimerDays = 7f; // Triggers respawn on next advance cycle
            }
        }

        @Override
        public void advance(float amount) {
            // Stop managing patrol if Sovereign has already been recruited and is alive
            if (USS_SovereignEvent.isRecruited()) {
                if (sovereignFleet != null && sovereignFleet.isAlive()) {
                    sovereignFleet.despawn();
                    sovereignFleet = null;
                }
                return;
            }

            float days = Global.getSector().getClock().convertToDays(amount);
            tracker.advance(days);
            if (!tracker.intervalElapsed()) return;

            // Check direct reference health
            if (sovereignFleet != null && (!sovereignFleet.isAlive() || sovereignFleet.getContainingLocation() == null || sovereignFleet.isDespawning())) {
                sovereignFleet = null;
            }

            if (sovereignFleet == null) {
                respawnTimerDays += tracker.getIntervalDuration();
                if (respawnTimerDays >= 7f) {
                    StarSystemAPI system = Global.getSector().getStarSystem(SYSTEM_NAME);
                    if (system == null) return;

                    SectorEntityToken platform = system.getEntityById(PLATFORM_ID);
                    if (platform == null) return;

                    sovereignFleet = spawnSovereignFleet(system, platform);
                    respawnTimerDays = 0f;
                }
            } else {
                respawnTimerDays = 0f;
                // Re-assign patrol if assignments got cleared
                if (sovereignFleet.getAI() != null && sovereignFleet.getAI().getCurrentAssignment() == null) {
                    SectorEntityToken platform = sovereignFleet.getContainingLocation().getEntityById(PLATFORM_ID);
                    if (platform != null) {
                        sovereignFleet.addAssignment(FleetAssignment.PATROL_SYSTEM, platform, 100000f, "Patrolling Holland");
                    }
                }
            }
        }

        public static CampaignFleetAPI spawnSovereignFleet(StarSystemAPI system, SectorEntityToken platform) {
            CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(DimensionsCrossedIDS.UFP, "USS Sovereign", false);

            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.USS_SOVEREIGN);
            fleet.getFleetData().addFleetMember(member);
            fleet.getFleetData().setFlagship(member);

            // Set Combat Readiness to 100% (or max CR)
            member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());

            // Provide required crew and supplies so CR does not decay
            fleet.getCargo().addCrew((int) member.getMinCrew());
            fleet.getCargo().addSupplies(200f);

            // Scope event target flag to the patrol fleet & commander so hailing the fleet directly triggers dialogue
            fleet.getMemoryWithoutUpdate().set("$uss_sovereign_event_target", true);
            if (fleet.getCommander() != null) {
                fleet.getCommander().getMemoryWithoutUpdate().set("$uss_sovereign_event_target", true);
            }

            fleet.getMemoryWithoutUpdate().set("$ignoreSpottedPlayerExtraRadius", true);

            system.addEntity(fleet);
            fleet.setLocation(platform.getLocation().x, platform.getLocation().y);
            fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, platform, 100000f, "Patrolling Holland");

            fleet.getFleetData().setSyncNeeded();
            fleet.getFleetData().syncIfNeeded();

            return fleet;
        }
    }
}