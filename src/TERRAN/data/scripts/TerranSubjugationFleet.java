package TERRAN.data.scripts;

import TERRAN.data.campaign.ids.TerranIDS;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class TerranSubjugationFleet implements EveryFrameScript {

    public static final float INITIAL_CHECK_DAYS = 90.0f;
    public static final float ANNUAL_CHECK_DAYS = 365.0f;
    public static final float RETRY_CHECK_DAYS = 25.0f;

    public static final int ADDED_CAPITAL_SHIPS = 3;
    public static final float BASE_FLEET_POINTS = 500f;
    public static final float STABILITY_DRAIN_PER_ATTACK = 2.0f;
    public static final float ATTACK_COOLDOWN_DAYS = 4.0f;
    public static final float GROUND_SUBJUGATION_DAYS = 20.0f;

    // Memory Keys
    public static final String TARGET_WORLD_MEM_KEY = "$terran_subjugation_current_target";
    public static final String FLEET_TARGET_ID_KEY = "$terran_subjugation_fleet_target_id";
    public static final String FLEET_IDENTIFIER_KEY = "$is_terran_subjugation_fleet";
    public static final String FLEET_RETREAT_KEY = "$terran_fleet_retreating";
    public static final String FLEET_COOLDOWN_KEY = "$terran_attack_cooldown";
    public static final String ORIGINAL_SOURCE_KEY = "$terran_fleet_source_market_id";
    public static final String INITIAL_FP_KEY = "$terran_fleet_initial_fp";

    // Operations Phase Keys
    public static final String SUBJUGATION_PHASE_KEY = "$terran_subjugation_phase";
    public static final String GROUND_SUBJUGATION_PROGRESS = "$terran_ground_subjugation_progress";
    public static final String REWARD_AMOUNT_KEY = "$terran_fleet_reward_amount";

    private final IntervalUtil annualInterval = new IntervalUtil(INITIAL_CHECK_DAYS, INITIAL_CHECK_DAYS);
    private final IntervalUtil retryInterval = new IntervalUtil(RETRY_CHECK_DAYS, RETRY_CHECK_DAYS);

    @Override public boolean isDone() { return false; }
    @Override public boolean runWhilePaused() { return false; }

    @Override
    public void advance(float amount) {
        SectorAPI sector = Global.getSector();
        if (sector == null || sector.isInNewGameAdvance()) return;

        float days = sector.getClock().convertToDays(amount);

        processActiveFleets(days);

        if (getActiveSubjugationFleets().isEmpty()) {
            PlanetAPI currentTarget = getLockedTarget();

            if (currentTarget != null && !currentTarget.getFaction().equals(TerranIDS.TERRAN)) {
                // FIX: Continuous $noDeciv protection while waiting for retry launch
                if (currentTarget.getMarket() != null) {
                    currentTarget.getMarket().getMemoryWithoutUpdate().set(DecivTracker.NO_DECIV_KEY, true);
                }
                retryInterval.advance(days);
                if (retryInterval.intervalElapsed()) {
                    triggerSubjugationLaunch(currentTarget);
                }
            } else {
                annualInterval.advance(days);
                if (annualInterval.intervalElapsed()) {
                    annualInterval.setInterval(ANNUAL_CHECK_DAYS, ANNUAL_CHECK_DAYS);

                    PlanetAPI newTarget = pickNewTarget();
                    if (newTarget != null) {
                        triggerSubjugationLaunch(newTarget);

                    }
                }
            }
        }
    }

    private void processActiveFleets(float days) {
        for (CampaignFleetAPI fleet : getActiveSubjugationFleets()) {
            MemoryAPI fleetMem = fleet.getMemoryWithoutUpdate();

            if (handleFleetRetreat(fleet, fleetMem)) {
                continue;
            }

            PlanetAPI target = getFleetTarget(fleet);
            if (target == null) continue;

            MarketAPI targetMarket = target.getMarket();

            // Prevent DecivTracker from processing this market during active siege
            if (targetMarket != null) {
                targetMarket.getMemoryWithoutUpdate().set(DecivTracker.NO_DECIV_KEY, true);
            }

            if (!fleetMem.contains(SUBJUGATION_PHASE_KEY)) {
                fleetMem.set(SUBJUGATION_PHASE_KEY, "BOMBARDMENT");
            }

            String phase = fleetMem.getString(SUBJUGATION_PHASE_KEY);

            if (phase.equals("BOMBARDMENT")) {
                if (isFleetAtTarget(fleet, target)) {
                    // Fleet has arrived at the target planet -> switch to besieging orbit
                    if (fleet.getCurrentAssignment() == null || fleet.getCurrentAssignment().getAssignment() != FleetAssignment.ORBIT_PASSIVE) {
                        fleet.clearAssignments();
                        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target, 9999f, "besieging " + target.getName());
                    }
                    processBombardmentPhase(fleet, target, targetMarket, fleetMem, days);
                } else {
                    // Fleet is en route (in hyperspace or entering system)
                    if (fleet.getCurrentAssignment() == null || fleet.getCurrentAssignment().getAssignment() != FleetAssignment.GO_TO_LOCATION) {
                        fleet.clearAssignments();
                        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, target, 1000f, "traveling to subjugate " + target.getName());
                    }
                }
            } else if (phase.equals("GROUND_SUBJUGATION")) {
                if (fleet.getCurrentAssignment() == null || fleet.getCurrentAssignment().getAssignment() != FleetAssignment.ORBIT_PASSIVE) {
                    fleet.clearAssignments();
                    fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target, 9999f, "conducting ground subjugation operations on " + target.getName());
                }
                processGroundSubjugationPhase(fleet, target, fleetMem, days);
            }
        }
    }

    private boolean handleFleetRetreat(CampaignFleetAPI fleet, MemoryAPI fleetMem) {
        if (!fleetMem.getBoolean(FLEET_RETREAT_KEY)) return false;

        String sourceMarketId = fleetMem.getString(ORIGINAL_SOURCE_KEY);
        MarketAPI source = Global.getSector().getEconomy().getMarket(sourceMarketId);

        if (source != null && source.getPrimaryEntity() != null) {
            float dist = Misc.getDistance(fleet, source.getPrimaryEntity());
            if (dist < 300f) {
                fleet.despawn();
            }
        } else if (fleet.getContainingLocation() == Global.getSector().getHyperspace()) {
            fleet.despawn();
        }
        return true;
    }

    private void processBombardmentPhase(CampaignFleetAPI fleet, PlanetAPI target, MarketAPI targetMarket, MemoryAPI fleetMem, float days) {
        if (targetMarket != null && targetMarket.isInEconomy() && !targetMarket.getFactionId().equals(TerranIDS.TERRAN)) {

            float currentFP = fleet.getFleetPoints();
            float initialFP = fleetMem.getFloat(INITIAL_FP_KEY);

            if (initialFP > 0 && (currentFP / initialFP) < 0.45f) {
                Global.getSector().getCampaignUI().addMessage(
                        "Defensive fire from " + target.getName() + " forced the Terran Subjugation Fleet into full retreat!",
                        Color.CYAN
                );
                sendFleetHome(fleet);
                return;
            }

            float cooldown = fleetMem.getFloat(FLEET_COOLDOWN_KEY) - days;
            if (cooldown <= 0) {
                executeOrbitalAttack(fleet, target);
                fleetMem.set(FLEET_COOLDOWN_KEY, ATTACK_COOLDOWN_DAYS);
            } else {
                fleetMem.set(FLEET_COOLDOWN_KEY, cooldown);
            }
            return;
        }

        // Defenses broken or neutral market -> start ground subjugation
        if (targetMarket == null || !targetMarket.isInEconomy() || targetMarket.getFactionId().equals(Factions.NEUTRAL) || targetMarket.getFactionId().equals(Factions.DERELICT)) {
            fleetMem.set(SUBJUGATION_PHASE_KEY, "GROUND_SUBJUGATION");
            fleetMem.set(GROUND_SUBJUGATION_PROGRESS, 0f);

            fleet.clearAssignments();
            fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target, 9999f, "conducting ground subjugation operations on " + target.getName());

            Global.getSector().getCampaignUI().addMessage(
                    "The Terran Subjugation Fleet has initiated Ground Pacification on " + target.getName() + "!",
                    Color.YELLOW
            );
        }
    }

    private void processGroundSubjugationPhase(CampaignFleetAPI fleet, PlanetAPI target, MemoryAPI fleetMem, float days) {
        float progress = fleetMem.getFloat(GROUND_SUBJUGATION_PROGRESS) + days;
        fleetMem.set(GROUND_SUBJUGATION_PROGRESS, progress);

        float percent = Math.min((progress / GROUND_SUBJUGATION_DAYS) * 100f, 100f);

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target, 9999f,
                String.format("pacifying %s (Ground Subjugation: %d%%)", target.getName(), (int) percent));

        if (progress >= GROUND_SUBJUGATION_DAYS) {
            TerranSubjugationColonization.establishTerranColony(target, fleet);
        }
    }

    private PlanetAPI getFleetTarget(CampaignFleetAPI fleet) {
        MemoryAPI fleetMem = fleet.getMemoryWithoutUpdate();
        if (fleetMem.contains(FLEET_TARGET_ID_KEY)) {
            String targetId = fleetMem.getString(FLEET_TARGET_ID_KEY);
            SectorEntityToken entity = Global.getSector().getEntityById(targetId);
            if (entity instanceof PlanetAPI) {
                return (PlanetAPI) entity;
            }
        }
        return getLockedTarget();
    }

    private PlanetAPI getLockedTarget() {
        MemoryAPI sectorMem = Global.getSector().getMemoryWithoutUpdate();
        if (sectorMem.contains(TARGET_WORLD_MEM_KEY)) {
            String targetId = sectorMem.getString(TARGET_WORLD_MEM_KEY);
            SectorEntityToken entity = Global.getSector().getEntityById(targetId);
            if (entity instanceof PlanetAPI) {
                PlanetAPI planet = (PlanetAPI) entity;
                if (planet.getFaction().getId().equals(TerranIDS.TERRAN)) {
                    sectorMem.unset(TARGET_WORLD_MEM_KEY);
                    return null;
                }
                return planet;
            }
        }
        return null;
    }

    private PlanetAPI pickNewTarget() {
        WeightedRandomPicker<PlanetAPI> picker = new WeightedRandomPicker<>();
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (PlanetAPI planet : system.getPlanets()) {
                MarketAPI market = planet.getMarket();
                if (market != null && market.isInEconomy()
                        && !market.getFactionId().equals(TerranIDS.TERRAN)
                        && !market.getFactionId().equals(Factions.NEUTRAL)
                        && market.getSize() >= 3) {
                    picker.add(planet);
                }
                if (planet.getContainingLocation() != null && planet.getContainingLocation().getId().equals("Terra")) {
                    continue; // Never auto-target planets in the capital system
                }
            }
        }
        PlanetAPI newTarget = picker.pick();
        if (newTarget != null) {
            Global.getSector().getMemoryWithoutUpdate().set(TARGET_WORLD_MEM_KEY, newTarget.getId());
            if (newTarget.getMarket() != null) {
                newTarget.getMarket().getMemoryWithoutUpdate().set(DecivTracker.NO_DECIV_KEY, true);
            }
        }
        return newTarget;
    }

    private void triggerSubjugationLaunch(PlanetAPI target) {
        if (target == null) return;

        MarketAPI source = pickTerranSource();
        SectorEntityToken sourceEntity = source != null ? source.getPrimaryEntity() : null;
        if (sourceEntity == null) return;

        LocationAPI spawnLocation = sourceEntity.getContainingLocation();
        Vector2f spawnLoc = sourceEntity.getLocation();

        FleetParamsV3 params = new FleetParamsV3(
                source.getLocationInHyperspace(),
                TerranIDS.TERRAN,
                1.0f,
                FleetTypes.PATROL_LARGE,
                BASE_FLEET_POINTS,
                0f, 0f, 0f, 0f, 0f, 0f
        );
        params.source = source;
        params.withOfficers = true;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null) return;

        FactionAPI faction = Global.getSector().getFaction(TerranIDS.TERRAN);
        for (int i = 0; i < ADDED_CAPITAL_SHIPS; i++) {
            List<ShipRolePick> picks = faction.pickShip(
                    ShipRoles.COMBAT_CAPITAL,
                    new FactionAPI.ShipPickParams(FactionAPI.ShipPickMode.PRIORITY_THEN_ALL)
            );
            if (picks != null && !picks.isEmpty()) {
                String variantId = picks.get(0).variantId;
                fleet.getFleetData().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId));
            }
        }

        fleet.getFleetData().sort();
        fleet.inflateIfNeeded();
        fleet.setName("Terran Subjugation Fleet");

        float totalFP = fleet.getFleetPoints();
        float dynamicReward = calculateFleetBounty(fleet, target.getMarket());

        MemoryAPI fleetMem = fleet.getMemoryWithoutUpdate();
        fleetMem.set(FLEET_IDENTIFIER_KEY, true);
        fleetMem.set(FLEET_TARGET_ID_KEY, target.getId());
        fleetMem.set(ORIGINAL_SOURCE_KEY, source.getId());
        fleetMem.set(FLEET_COOLDOWN_KEY, ATTACK_COOLDOWN_DAYS);
        fleetMem.set(INITIAL_FP_KEY, totalFP);
        fleetMem.set(REWARD_AMOUNT_KEY, dynamicReward);

        fleetMem.set(MemFlags.MEMORY_KEY_MISSION_IMPORTANT, true);
        fleetMem.set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        fleetMem.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        fleetMem.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleetMem.set(MemFlags.FLEET_BUSY, true);
        fleetMem.set(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE, true);

        spawnLocation.addEntity(fleet);
        fleet.setLocation(spawnLoc.x, spawnLoc.y);

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, target, 1000f, "traveling to subjugate " + target.getName());

        fleet.addEventListener(new SubjugationBattleListener());

        Global.getSector().getIntelManager().addIntel(new TerranSubjugationIntel(fleet, target, sourceEntity, dynamicReward));
    }

    private float calculateFleetBounty(CampaignFleetAPI fleet, MarketAPI targetMarket) {
        float fp = fleet.getFleetPoints();
        float baseBounty = fp * 3500f;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.isCapital()) baseBounty += 45000f;
            else if (member.isCruiser()) baseBounty += 20000f;
        }

        if (targetMarket != null) {
            baseBounty += targetMarket.getSize() * 30000f;
        }
        return Math.round(baseBounty / 5000f) * 5000f;
    }

    private void executeOrbitalAttack(CampaignFleetAPI fleet, PlanetAPI target) {
        MarketAPI market = target.getMarket();
        if (market == null) return;

        // Force $noDeciv lock on this market during bombardment
        market.getMemoryWithoutUpdate().set(DecivTracker.NO_DECIV_KEY, true);

        float groundDefenseRating = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).computeEffective(0f);
        float defenseMitigation = Math.min(groundDefenseRating / 500000f, 0.65f);

        float currentMod = 0f;
        if (market.getStability().getFlatStatMod("terran_subjugation_bombardment") != null) {
            currentMod = market.getStability().getFlatStatMod("terran_subjugation_bombardment").value;
        }

        float effectiveDrain = STABILITY_DRAIN_PER_ATTACK * (1.0f - defenseMitigation);
        market.getStability().modifyFlat("terran_subjugation_bombardment", currentMod - effectiveDrain, "Terran Bombardment");
        market.reapplyIndustries();

        if (market.hasIndustry(Industries.ORBITALSTATION) || market.hasIndustry(Industries.HEAVYBATTERIES)) {
            List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
            if (!members.isEmpty()) {
                FleetMemberAPI victim = members.get((int) (Math.random() * members.size()));
                if (!victim.isCapital()) {
                    fleet.getFleetData().removeFleetMember(victim);
                }
            }
        }

        Global.getSector().getCampaignUI().addMessage(
                "Terran fleet bombarding " + target.getName() + "! Target stability: " + (int) market.getStabilityValue(),
                Color.RED
        );

        // Transition directly to Ground Subjugation before DecivTracker can trigger
        if (market.getStabilityValue() <= 1f) {
            MemoryAPI fleetMem = fleet.getMemoryWithoutUpdate();
            fleetMem.set(SUBJUGATION_PHASE_KEY, "GROUND_SUBJUGATION");
            fleetMem.set(GROUND_SUBJUGATION_PROGRESS, 0f);

            fleet.clearAssignments();
            fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target, 9999f, "conducting ground subjugation operations on " + target.getName());

            Global.getSector().getCampaignUI().addMessage(
                    "Defenses on " + target.getName() + " have collapsed! The Terran Subjugation Fleet has initiated Ground Pacification!",
                    Color.YELLOW
            );
        }
    }

    public static void sendFleetHome(CampaignFleetAPI fleet) {
        MemoryAPI fleetMem = fleet.getMemoryWithoutUpdate();
        fleetMem.set(FLEET_RETREAT_KEY, true);

        // Clean up target market protection safely if assault fails
        if (fleetMem.contains(FLEET_TARGET_ID_KEY)) {
            String targetId = fleetMem.getString(FLEET_TARGET_ID_KEY);
            SectorEntityToken entity = Global.getSector().getEntityById(targetId);
            if (entity instanceof PlanetAPI) {
                PlanetAPI targetPlanet = (PlanetAPI) entity;
                if (targetPlanet.getMarket() != null) {
                    MarketAPI market = targetPlanet.getMarket();
                    // FIX: Restore stability modifiers BEFORE clearing $noDeciv
                    market.getStability().unmodifyFlat("terran_subjugation_bombardment");
                    market.reapplyIndustries();
                    market.reapplyConditions();

                    // Only unset $noDeciv if stability is safely above 0
                    if (market.getStabilityValue() > 0) {
                        market.getMemoryWithoutUpdate().unset(DecivTracker.NO_DECIV_KEY);
                    }
                }
            }
        }

        String sourceId = fleetMem.getString(ORIGINAL_SOURCE_KEY);
        MarketAPI source = Global.getSector().getEconomy().getMarket(sourceId);
        if (source != null && source.getPrimaryEntity() != null) {
            fleet.clearAssignments();
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, source.getPrimaryEntity(), 9999f, "returning home to Terran space");
        } else {
            fleet.despawn();
        }
    }

    private boolean isFleetAtTarget(CampaignFleetAPI fleet, PlanetAPI target) {
        if (fleet.getContainingLocation() != target.getContainingLocation()) return false;
        return Misc.getDistance(fleet, target) <= target.getRadius() + 800f;
    }

    private MarketAPI pickTerranSource() {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market != null && market.getFactionId().equals(TerranIDS.TERRAN) && market.getPrimaryEntity() != null) {
                return market;
            }
        }
        return null;
    }

    private List<CampaignFleetAPI> getActiveSubjugationFleets() {
        List<CampaignFleetAPI> fleets = new ArrayList<>();
        for (LocationAPI loc : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI fleet : loc.getFleets()) {
                if (fleet.getMemoryWithoutUpdate().contains(FLEET_IDENTIFIER_KEY)) {
                    fleets.add(fleet);
                }
            }
        }
        return fleets;
    }

    private static class SubjugationBattleListener implements FleetEventListener {
        @Override
        public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {}

        @Override
        public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
            if (battle == null) return;

            boolean playerInvolved = battle.isPlayerInvolved();
            boolean fleetDestroyed = !fleet.isAlive() || fleet.getFleetData().getMembersListCopy().isEmpty();

            if (playerInvolved) {
                Object playerSide = battle.getSideFor(Global.getSector().getPlayerFleet());
                Object fleetSide = battle.getSideFor(fleet);

                if (playerSide != null && fleetSide != null && playerSide != fleetSide) {
                    notifyIntelOfOutcome(fleet, fleetDestroyed, true);
                    if (!fleetDestroyed) {
                        sendFleetHome(fleet);
                    }
                }
            } else if (fleetDestroyed) {
                notifyIntelOfOutcome(fleet, true, false);
            }
        }

        private void notifyIntelOfOutcome(CampaignFleetAPI fleet, boolean completelyWipedOut, boolean playerWon) {
            for (IntelInfoPlugin intelPlugin : Global.getSector().getIntelManager().getIntel(TerranSubjugationIntel.class)) {
                if (intelPlugin instanceof TerranSubjugationIntel) {
                    TerranSubjugationIntel intel = (TerranSubjugationIntel) intelPlugin;
                    if (intel.getFleet() == fleet) {
                        if (completelyWipedOut && playerWon) {
                            intel.completeMissionPlayerDefeated();
                        } else if (completelyWipedOut) {
                            intel.resolveWithoutPlayer();
                        } else {
                            intel.triggerRetreat();
                        }
                    }
                }
            }
        }
    }
}