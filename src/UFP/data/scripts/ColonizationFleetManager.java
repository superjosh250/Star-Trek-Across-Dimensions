package UFP.data.scripts;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;

public class ColonizationFleetManager implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String MEM_KEY_COLONIZATION_RESERVED = "$ufp_colonization_reserved";
    public static final String MEM_KEY_COLONY_FLEET = "$ufp_colony_fleet";
    private static final String SECTOR_MEMORY_KEY = "$ufp_colonization_manager";

    /**
     * Corrected static lifecycle getter using the proper MemoryAPI methods.
     */
    public static ColonizationFleetManager getInstance(String factionId, ColonyCreator colonyCreator) {
        if (Global.getSector() == null) return null;

        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        ColonizationFleetManager manager = null;

        // Correct usage of MemoryAPI methods: contains() and get()
        if (memory.contains(SECTOR_MEMORY_KEY)) {
            manager = (ColonizationFleetManager) memory.get(SECTOR_MEMORY_KEY);
        }

        if (manager == null) {
            manager = new ColonizationFleetManager(factionId, colonyCreator);
            // Correct usage of MemoryAPI method: set()
            memory.set(SECTOR_MEMORY_KEY, manager);
        } else {
            // Re-bind the transient interface callback upon save load
            manager.colonyCreator = colonyCreator;
        }
        return manager;
    }

    public enum OperationState {
        PLANNED,
        EN_ROUTE,
        SETTLING,
        ESTABLISHED,
        FAILED
    }

    public interface ColonyCreator {
        void createColony(PlanetAPI planet);
    }

    public static class ColonyOperation implements Serializable {
        private static final long serialVersionUID = 1L;

        public final String id = UUID.randomUUID().toString();
        public final PlanetAPI target;
        public final MarketAPI sourceMarket;
        public final float plannedTravelDays;
        public final float setupDays;

        public float daysUntilDeparture;
        public float daysUntilArrival;
        public float daysUntilColonization;
        public OperationState state = OperationState.PLANNED;

        // String tracking ID to resolve the engine fleet reference safely across saves
        public String colonyFleetId;

        // Marked transient so the save serializer doesn't panic over raw engine frames
        public transient CampaignFleetAPI colonyFleet;
        public transient ColonizationProtocol_IntelBar intel;

        public ColonyOperation(PlanetAPI target, MarketAPI sourceMarket,
                               float departureDelay, float travelDays, float setupDays) {
            this.target = target;
            this.sourceMarket = sourceMarket;
            this.daysUntilDeparture = departureDelay;
            this.daysUntilArrival = travelDays;
            this.daysUntilColonization = setupDays;
            this.plannedTravelDays = travelDays;
            this.setupDays = setupDays;
        }
    }

    private final String factionId;
    private final List<ColonyOperation> operations = new ArrayList<>();

    // Marked transient to bypass serialization bugs if anonymous classes/lambdas are used
    private transient ColonyCreator colonyCreator;

    public ColonizationFleetManager(String factionId, ColonyCreator colonyCreator) {
        this.factionId = factionId;
        this.colonyCreator = colonyCreator;
    }

    public List<ColonyOperation> getOperations() {
        return operations;
    }

    public boolean isPlanetReserved(PlanetAPI planet) {
        if (planet == null) return false;
        if (planet.getMemoryWithoutUpdate().getBoolean(MEM_KEY_COLONIZATION_RESERVED)) return true;

        for (ColonyOperation op : operations) {
            if (op.target == planet && op.state != OperationState.FAILED && op.state != OperationState.ESTABLISHED) {
                return true;
            }
        }
        return false;
    }

    public int getActiveOperationCount() {
        int count = 0;
        for (ColonyOperation op : operations) {
            if (op.state != OperationState.FAILED && op.state != OperationState.ESTABLISHED) {
                count++;
            }
        }
        return count;
    }

    public ColonyOperation queueColonization(PlanetAPI target, float departureDelayDays, float setupDays) {
        if (target == null || isPlanetReserved(target)) return null;

        MarketAPI source = pickBestSourceMarket(target);
        float travelDays = estimateTravelDays(source, target);

        ColonyOperation op = new ColonyOperation(target, source, departureDelayDays, travelDays, setupDays);
        op.intel = new ColonizationProtocol_IntelBar(op, factionId);

        target.getMemoryWithoutUpdate().set(MEM_KEY_COLONIZATION_RESERVED, true);
        operations.add(op);
        return op;
    }

    public void advance(float days) {
        if (days <= 0f || operations.isEmpty()) return;

        Iterator<ColonyOperation> iter = operations.iterator();
        while (iter.hasNext()) {
            ColonyOperation op = iter.next();

            // Reconnects the transient fleet reference from the loaded save file
            resolveTransientFleet(op);

            switch (op.state) {
                case PLANNED -> advancePlanned(op, days);
                case EN_ROUTE -> advanceEnRoute(op, days);
                case SETTLING -> advanceSettling(op, days);
                case ESTABLISHED, FAILED -> {
                    // retain the intel entry, but no longer process
                }
            }
        }
    }

    private void advancePlanned(ColonyOperation op, float days) {
        op.daysUntilDeparture -= days;
        if (op.daysUntilDeparture > 0f) return;

        CampaignFleetAPI fleet = spawnColonyFleet(op);
        if (fleet == null) {
            failOperation(op, false);
            return;
        }

        op.colonyFleet = fleet;
        op.colonyFleetId = fleet.getId();
        op.state = OperationState.EN_ROUTE;

        if (op.intel != null) op.intel.notifyFleetLaunched();
    }

    private void advanceEnRoute(ColonyOperation op, float days) {
        if (op.colonyFleet == null || !op.colonyFleet.isAlive()) {
            failOperation(op, true);
            return;
        }

        // Keep ticking the estimate down (clamped to 0 for the UI)
        op.daysUntilArrival = Math.max(0f, op.daysUntilArrival - days);

        // ONLY arrive if the physical fleet is actually at the planet!
        if (isFleetAtTarget(op)) {
            beginSettlement(op);
            return;
        }

        // OPTIONAL FAIL-SAFE: If the fleet gets stuck/lost for way too long (e.g., 3x travel time)
        // Fail the operation instead of letting it teleport-colonize.
        if (op.daysUntilArrival <= -op.plannedTravelDays * 2f) {
            if (op.colonyFleet != null) {
                op.colonyFleet.despawn();
            }
            failOperation(op, true);
        }
    }

    private void advanceSettling(ColonyOperation op, float days) {
        if (op.colonyFleet == null || !op.colonyFleet.isAlive()) {
            failOperation(op, true);
            return;
        }

        op.daysUntilColonization -= days;
        if (op.daysUntilColonization > 0f) return;

        if (colonyCreator != null) {
            colonyCreator.createColony(op.target);
        }

        op.state = OperationState.ESTABLISHED;
        op.target.getMemoryWithoutUpdate().unset(MEM_KEY_COLONIZATION_RESERVED);

        if (op.colonyFleet != null && op.colonyFleet.isAlive()) {
            op.colonyFleet.despawn();
        }

        if (op.intel != null) op.intel.notifyEstablished();
    }

    private void beginSettlement(ColonyOperation op) {
        op.state = OperationState.SETTLING;
        op.daysUntilColonization = op.setupDays;

        if (op.colonyFleet != null) {
            op.colonyFleet.clearAssignments();
            op.colonyFleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, op.target, op.setupDays,
                    "establishing a colony on " + op.target.getName());
        }

        if (op.intel != null) op.intel.notifyColonizationStarted();
    }

    private void failOperation(ColonyOperation op, boolean clearReservation) {
        op.state = OperationState.FAILED;

        if (clearReservation && op.target != null) {
            op.target.getMemoryWithoutUpdate().unset(MEM_KEY_COLONIZATION_RESERVED);
        }

        if (op.intel != null) op.intel.notifyFailed();
    }

    private CampaignFleetAPI spawnColonyFleet(ColonyOperation op) {
        MarketAPI source = op.sourceMarket;
        SectorEntityToken sourceEntity = source != null ? source.getPrimaryEntity() : null;

        LocationAPI spawnLocation = sourceEntity != null
                ? sourceEntity.getContainingLocation()
                : Global.getSector().getHyperspace();

        Vector2f spawnLoc = sourceEntity != null
                ? sourceEntity.getLocation()
                : new Vector2f(op.target.getLocation());

        Vector2f locationInHyper = source != null
                ? source.getLocationInHyperspace()
                : op.target.getLocationInHyperspace();

        FleetParamsV3 params = new FleetParamsV3(
                locationInHyper,
                factionId,
                0.75f,
                FleetTypes.SUPPLY_FLEET,
                28f,
                18f,
                6f,
                16f,
                0f,
                4f,
                0f
        );

        params.source = source;
        params.withOfficers = true;
        params.ignoreMarketFleetSizeMult = true;
        params.maxNumShips = 18;

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null) return null;

        fleet.setName("UFP Colony Fleet");
        fleet.getMemoryWithoutUpdate().set(MEM_KEY_COLONY_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE, false);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, false);
        // Makes the fleet drop cargo/be extra enticing if defeated
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_TRADE_FLEET, true);
        // Optional: If you want other factions to actively hunt it down during war
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MISSION_IMPORTANT, true);

        spawnLocation.addEntity(fleet);
        fleet.setLocation(spawnLoc.x, spawnLoc.y);
        fleet.setFacing((float) Math.random() * 360f);

        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, op.target, 999999f,
                "heading to " + op.target.getName() + " for colonization");
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, op.target, 1f,
                "preparing to establish a colony");

        if (sourceEntity != null && sourceEntity.getContainingLocation() == op.target.getContainingLocation()) {
            float dist = Misc.getDistance(sourceEntity, op.target);
            op.daysUntilArrival = Math.max(5f, dist / 2000f);
        }

        return fleet;
    }

    private boolean isFleetAtTarget(ColonyOperation op) {
        if (op.colonyFleet == null || op.target == null) return false;
        if (op.colonyFleet.getContainingLocation() != op.target.getContainingLocation()) return false;

        float dist = Misc.getDistance(op.colonyFleet, op.target);
        return dist <= op.target.getRadius() + 300f;
    }

    private MarketAPI pickBestSourceMarket(PlanetAPI target) {
        MarketAPI best = null;
        float bestDist = Float.MAX_VALUE;

        if (Global.getSector().getEconomy() == null) return null;

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == null || market.getPrimaryEntity() == null) continue;
            if (!factionId.equals(market.getFactionId())) continue;

            float dist = Misc.getDistanceLY(market.getLocationInHyperspace(), target.getLocationInHyperspace());
            if (dist < bestDist) {
                bestDist = dist;
                best = market;
            }
        }

        return best;
    }

    private float estimateTravelDays(MarketAPI source, PlanetAPI target) {
        if (source == null || target == null) return 45f;

        float distLy = Misc.getDistanceLY(source.getLocationInHyperspace(), target.getLocationInHyperspace());
        return Math.max(10f, distLy * 8f);
    }

    /**
     * Looks through current system contexts to map our unique Engine String ID back to
     * a transient live fleet reference upon load.
     */
    private void resolveTransientFleet(ColonyOperation op) {
        if (op.colonyFleet != null || op.colonyFleetId == null) return;

        if (op.target != null && op.target.getContainingLocation() != null) {
            for (CampaignFleetAPI fleet : op.target.getContainingLocation().getFleets()) {
                if (op.colonyFleetId.equals(fleet.getId())) {
                    op.colonyFleet = fleet;
                    return;
                }
            }
        }

        if (op.sourceMarket != null && op.sourceMarket.getPrimaryEntity() != null
                && op.sourceMarket.getPrimaryEntity().getContainingLocation() != null) {
            for (CampaignFleetAPI fleet : op.sourceMarket.getPrimaryEntity().getContainingLocation().getFleets()) {
                if (op.colonyFleetId.equals(fleet.getId())) {
                    op.colonyFleet = fleet;
                    return;
                }
            }
        }

        for (CampaignFleetAPI fleet : Global.getSector().getHyperspace().getFleets()) {
            if (op.colonyFleetId.equals(fleet.getId())) {
                op.colonyFleet = fleet;
                return;
            }
        }
    }
}