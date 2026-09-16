package UFP.data.plugins;

import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase.PatrolFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory.PatrolType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolAssignmentAIV4;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class StarfleetTaskforceType {

    // =========================================================
    // ENUMS & FLEET DATA STRUCTURES
    // =========================================================
    public enum CustomPatrolType {
        FAST(PatrolType.FAST),
        COMBAT(PatrolType.COMBAT),
        HEAVY(PatrolType.HEAVY),
        TASKFORCE(null);

        private final PatrolType vanillaType;

        CustomPatrolType(PatrolType vanillaType) {
            this.vanillaType = vanillaType;
        }

        public PatrolType getVanillaType() {
            return vanillaType;
        }
    }

    public static class StarfleetPatrolFleetData extends PatrolFleetData {
        public CustomPatrolType customType;

        public StarfleetPatrolFleetData(CustomPatrolType customType) {
            super(customType.getVanillaType() != null ? customType.getVanillaType() : PatrolType.HEAVY);
            this.customType = customType;
        }
    }

    // =========================================================
    // TASKFORCE & ROUTE TRACKING
    // =========================================================
    public static int getTaskforceCount(MilitaryBase industry) {
        if (industry == null || industry.getRouteSourceId() == null) return 0;
        int count = 0;
        for (RouteData data : RouteManager.getInstance().getRoutesForSource(industry.getRouteSourceId())) {
            if (data.getCustom() instanceof StarfleetPatrolFleetData) {
                StarfleetPatrolFleetData custom = (StarfleetPatrolFleetData) data.getCustom();
                if (custom.customType == CustomPatrolType.TASKFORCE) {
                    count++;
                }
            }
        }
        return count;
    }

    // =========================================================
    // ADVANCE TICK & ROUTE CREATION
    // =========================================================
    public static float handleAdvance(MilitaryBase industry, int maxTaskforces, IntervalUtil tracker, float returningPatrolValue, float amount) {
        if (Global.getSector().getEconomy().isSimMode()) return returningPatrolValue;
        if (industry == null || !industry.isFunctional() || tracker == null) return returningPatrolValue;

        MarketAPI market = industry.getMarket();
        if (market == null) return returningPatrolValue;

        float days = Global.getSector().getClock().convertToDays(amount);
        if (Float.isNaN(days) || Float.isInfinite(days) || days < 0f) days = 0f;

        float spawnRate = market.getStats().getDynamic().getStat(Stats.COMBAT_FLEET_SPAWN_RATE_MULT).getModifiedValue();
        if (Float.isNaN(spawnRate) || Float.isInfinite(spawnRate) || spawnRate <= 0f) {
            spawnRate = 1f;
        }

        if (Global.getSector().isInNewGameAdvance()) {
            spawnRate *= 3f;
        }

        float extraTime = 0f;
        if (returningPatrolValue > 0) {
            float interval = tracker.getIntervalDuration();
            extraTime = interval * days;
            returningPatrolValue -= days;
            if (returningPatrolValue < 0) returningPatrolValue = 0;
        }

        tracker.advance(days * spawnRate + extraTime);

        if (DebugFlags.FAST_PATROL_SPAWN) {
            tracker.advance(days * spawnRate * 100f);
        }

        if (tracker.intervalElapsed()) {
            String sid = industry.getRouteSourceId();

            int light = industry.getCount(PatrolType.FAST);
            int totalHeavy = industry.getCount(PatrolType.HEAVY);
            int taskforce = getTaskforceCount(industry);

            // Deduct taskforces from totalHeavy because StarfleetPatrolFleetData inherits PatrolType.HEAVY
            int heavy = Math.max(0, totalHeavy - taskforce);
            int medium = industry.getCount(PatrolType.COMBAT);

            int maxLight = industry.getMaxPatrols(PatrolType.FAST);
            int maxMedium = industry.getMaxPatrols(PatrolType.COMBAT);
            int maxHeavy = industry.getMaxPatrols(PatrolType.HEAVY);

            WeightedRandomPicker<CustomPatrolType> picker = new WeightedRandomPicker<>();
            if (maxTaskforces > taskforce) picker.add(CustomPatrolType.TASKFORCE, (float) (maxTaskforces - taskforce));
            if (maxHeavy > heavy) picker.add(CustomPatrolType.HEAVY, (float) (maxHeavy - heavy));
            if (maxMedium > medium) picker.add(CustomPatrolType.COMBAT, (float) (maxMedium - medium));
            if (maxLight > light) picker.add(CustomPatrolType.FAST, (float) (maxLight - light));

            if (picker.isEmpty()) return returningPatrolValue;

            CustomPatrolType customType = picker.pick();
            if (customType == null) return returningPatrolValue;

            StarfleetPatrolFleetData custom = new StarfleetPatrolFleetData(customType);

            OptionalFleetData extra = new OptionalFleetData(market);
            extra.fleetType = customType == CustomPatrolType.TASKFORCE ? "starfleetTaskforce" : customType.getVanillaType().getFleetType();

            RouteData route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, industry, custom);

            float fp;
            if (customType == CustomPatrolType.TASKFORCE) {
                fp = 150f + route.getRandom().nextFloat() * 100f; // 150 - 250 FP
            } else {
                fp = (float) industry.getPatrolCombatFP(customType.getVanillaType(), route.getRandom());
            }

            if (Float.isNaN(fp) || Float.isInfinite(fp) || fp <= 0f) {
                fp = 150f;
            }

            // Pre-assign spawnFP to prevent zero-division in route tracking listeners
            custom.spawnFP = (int) fp;

            float str = Misc.getAdjustedStrength(fp, market);
            if (Float.isNaN(str) || Float.isInfinite(str) || str <= 0f) {
                str = fp;
            }
            extra.strength = str;

            float patrolDays = 35f + (float) Math.random() * 10f;
            route.addSegment(new RouteSegment(patrolDays, market.getPrimaryEntity()));
        }

        return returningPatrolValue;
    }

    // =========================================================
    // FLEET SPAWNING & SHIP INJECTION
    // =========================================================
    public static CampaignFleetAPI handleSpawnFleet(MilitaryBase industry, RouteData route) {
        if (industry == null || route == null || !(route.getCustom() instanceof StarfleetPatrolFleetData)) {
            return null;
        }

        MarketAPI market = industry.getMarket();
        if (market == null) return null;

        StarfleetPatrolFleetData custom = (StarfleetPatrolFleetData) route.getCustom();
        CustomPatrolType customType = custom.customType;
        Random random = route.getRandom();
        if (random == null) random = new Random();

        CampaignFleetAPI fleet;
        if (customType == CustomPatrolType.TASKFORCE) {
            fleet = createTaskforce(route, market, random);
        } else {
            fleet = industry.createPatrol(customType.getVanillaType(), market.getFactionId(), route, market, null, random);
        }

        if (fleet == null || fleet.isEmpty()) return null;

        // --- ASSIGN DISPLAY NAME BASED ON TYPE ---
        String fleetName;
        if (customType == CustomPatrolType.TASKFORCE) {
            fleetName = random.nextBoolean() ? "Starfleet Taskforce" : "Defense Taskforce";
        } else if (customType == CustomPatrolType.HEAVY) {
            fleetName = "Defense Taskforce";
        } else {
            fleetName = "Starfleet Taskforce";
        }
        fleet.setName(fleetName);

        // Guaranteed Ship Injections
        int size = market.getSize();

        if (customType == CustomPatrolType.HEAVY) {
            addGuaranteedShips(fleet, market, ShipAPI.HullSize.CAPITAL_SHIP, 1, random);
            if (size >= 5) {
                addGuaranteedShips(fleet, market, ShipAPI.HullSize.CRUISER, 1, random);
            }
        } else if (customType == CustomPatrolType.COMBAT) {
            if (size >= 10) {
                addGuaranteedShips(fleet, market, ShipAPI.HullSize.CRUISER, 1, random);
            }
        } else if (customType == CustomPatrolType.TASKFORCE) {
            if (size >= 10) {
                addGuaranteedShips(fleet, market, ShipAPI.HullSize.CAPITAL_SHIP, 2, random);
                addGuaranteedShips(fleet, market, ShipAPI.HullSize.CRUISER, 1, random);
            }
        }

        fleet.addEventListener(industry);

        if (market.getContainingLocation() != null && market.getPrimaryEntity() != null) {
            market.getContainingLocation().addEntity(fleet);
            fleet.setFacing((float) Math.random() * 360f);
            fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);
        }

        fleet.addScript(new PatrolAssignmentAIV4(fleet, route));
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true, 0.3f);

        // --- SAVE SCRUBBING & RE-CALCULATION ---
        // Forces complete recalculation of spawnFP and strength using final fleet total FP (including injected capital hulls)
        float currentTotalFP = fleet.getFleetPoints();
        if (Float.isNaN(currentTotalFP) || Float.isInfinite(currentTotalFP) || currentTotalFP <= 0f) {
            currentTotalFP = 150f;
        }

        // Overwrite legacy/corrupted route state from test saves
        custom.spawnFP = (int) currentTotalFP;

        if (route.getExtra() != null) {
            float adjustedStrength = Misc.getAdjustedStrength(currentTotalFP, market);
            if (Float.isNaN(adjustedStrength) || Float.isInfinite(adjustedStrength) || adjustedStrength <= 0f) {
                adjustedStrength = currentTotalFP;
            }
            route.getExtra().strength = adjustedStrength;
        }

        return fleet;
    }

    public static CampaignFleetAPI createTaskforce(RouteData route, MarketAPI market, Random random) {
        if (random == null) random = new Random();

        float combat = 150f + random.nextFloat() * 100f;
        float tanker = 15f + random.nextFloat() * 10f;
        float freighter = 15f + random.nextFloat() * 10f;

        FleetParamsV3 params = new FleetParamsV3(
                market,
                null,
                market.getFactionId(),
                route == null ? null : route.getQualityOverride(),
                "starfleetTaskforce",
                combat,
                freighter,
                tanker,
                0f, 0f, 0f, 0f
        );
        if (route != null) {
            params.timestamp = route.getTimestamp();
        }
        params.random = random;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        if (!fleet.getFaction().getCustomBoolean(Factions.CUSTOM_PATROLS_HAVE_NO_PATROL_MEMORY_KEY)) {
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        }

        if (fleet.getCommander() != null) {
            fleet.getCommander().setPostId(Ranks.POST_PATROL_COMMANDER);
            fleet.getCommander().setRankId(Ranks.SPACE_ADMIRAL);
        }

        return fleet;
    }

    public static void addGuaranteedShips(CampaignFleetAPI fleet, MarketAPI market, ShipAPI.HullSize hullSize, int count, Random random) {
        if (fleet == null || market == null || count <= 0 || hullSize == null) return;

        // Expanded budget bounds to allow high-FP modded hulls (120 - 175 FP Capitals like Sovereign / Galaxy)
        float allocatedBudget = 200f;
        if (hullSize == ShipAPI.HullSize.CRUISER) {
            allocatedBudget = 100f;
        } else if (hullSize == ShipAPI.HullSize.DESTROYER) {
            allocatedBudget = 50f;
        } else if (hullSize == ShipAPI.HullSize.FRIGATE) {
            allocatedBudget = 25f;
        }

        for (int i = 0; i < count; i++) {
            FleetParamsV3 params = new FleetParamsV3(
                    market,
                    null,
                    market.getFactionId(),
                    null,
                    "patrol",
                    allocatedBudget,
                    0f, 0f, 0f, 0f, 0f, 0f
            );
            params.maxShipSize = hullSize.ordinal();
            params.minShipSize = Math.max(0, hullSize.ordinal() - 1); // Allows adjacent fallbacks if exact size fails
            params.random = random;

            CampaignFleetAPI tempFleet = FleetFactoryV3.createFleet(params);
            if (tempFleet != null && tempFleet.getFleetData() != null && !tempFleet.getFleetData().getMembersListCopy().isEmpty()) {
                boolean added = false;
                // Attempt to pick matching hull size first
                for (FleetMemberAPI member : tempFleet.getFleetData().getMembersListCopy()) {
                    if (member.getHullSpec() != null && member.getHullSpec().getHullSize() == hullSize) {
                        fleet.getFleetData().addFleetMember(member);
                        added = true;
                        break;
                    }
                }
                // Fallback to top member if strict size match was missing
                if (!added) {
                    FleetMemberAPI fallback = tempFleet.getFleetData().getMembersListCopy().get(0);
                    if (fallback != null) {
                        fleet.getFleetData().addFleetMember(fallback);
                    }
                }
            }
        }
    }
}