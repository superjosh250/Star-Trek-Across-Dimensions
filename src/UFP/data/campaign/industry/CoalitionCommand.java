package UFP.data.campaign.industry;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory.PatrolType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolAssignmentAIV4;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

public class CoalitionCommand extends MilitaryBase implements FleetEventListener, RouteFleetSpawner {

    public static final String COLTECH_PATROL_TAG = "$COLTECH_PATROL";
    public static final String LOURD_DEMON_TAG = "$LOURD_DEMON_FORCE";
    public static final String OMEGA_CORE = "omega_core";

    public static final Set<String> ALLOWED_COLTECH_HULLS = new HashSet<>(Arrays.asList(
            DimensionsCrossedIDS.HULL_COLOSSUS_MK3,
            DimensionsCrossedIDS.HULL_COLOSSUS_MK4,
            DimensionsCrossedIDS.HULL_ATLAS_COLTECH,
            DimensionsCrossedIDS.HULL_ATLAS_CARRIER,
            DimensionsCrossedIDS.HULL_ATLAS_COLONY,
            DimensionsCrossedIDS.HULL_ATLAS_MOTHBALLED,
            DimensionsCrossedIDS.HULL_EAGLE_AMBASSADOR,
            DimensionsCrossedIDS.HULL_EAGLE_MK3,
            DimensionsCrossedIDS.HULL_CONQUEST_SOVEREIGN,
            DimensionsCrossedIDS.HULL_CONQUEST_SUPREME,
            DimensionsCrossedIDS.HULL_ANTISEPTIC,
            DimensionsCrossedIDS.HULL_SABOTAUNT,
            DimensionsCrossedIDS.HULL_ONSLAUGHT_GALAXY,
            DimensionsCrossedIDS.HULL_ONSLAUGHT_HELLHOUND,
            DimensionsCrossedIDS.HULL_PARAGON_MONARCH,
            DimensionsCrossedIDS.HULL_PARAGON_SIGNATURE
    ));

    protected float daemonRespawnTimer = 0f;

    @Override
    public void apply() {
        super.apply();

        int size = market.getSize();

        int baseDemand = 3;
        int baseSupply = 3;

        if (size == 4 || size == 5) {
            baseSupply = 4;
        } else if (size == 6) {
            baseSupply = 5;
            baseDemand = 4;
        } else if (size == 7 || size == 8) {
            baseSupply = 6;
            baseDemand = 4;
        } else if (size == 9) {
            baseSupply = 7;
            baseDemand = 5;
        } else if (size >= 10) {
            baseSupply = 8;
            baseDemand = 6;
        }

        demand(Commodities.SUPPLIES, baseDemand);
        demand(Commodities.FUEL, baseDemand);
        demand(Commodities.SHIPS, baseDemand);

        supply(Commodities.CREW, baseSupply);
        supply(Commodities.DRUGS, baseSupply);

        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyFlat(getModId(), 1.0f, getNameForModifier());
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlat(getModId(), 0.40f, getNameForModifier());
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyFlat(getModId(), 0.25f, getNameForModifier());

        if (OMEGA_CORE.equals(aiCoreId)) {
            applyOmegaCoreModifiers();
        }

        if (!isFunctional()) {
            supply.clear();
            unapply();
        }
    }

    @Override
    public void unapply() {
        super.unapply();
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyFlat(getModId());
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(getModId());
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyFlat(getModId());
        unapplyOmegaCoreModifiers();
    }

    protected void applyOmegaCoreModifiers() {
        supplyBonus.modifyFlat(getModId(), 2, "Omega Core");
        demandReduction.modifyFlat(getModId(), 1, "Omega Core");

        market.getStability().modifyFlat(getModId() + "_omega", 1, "Omega Core");
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyPercent(getModId() + "_omega", 75f, "Omega Core");
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyPercent(getModId() + "_omega", 25f, "Omega Core");
    }

    protected void unapplyOmegaCoreModifiers() {
        supplyBonus.unmodifyFlat(getModId());
        demandReduction.unmodifyFlat(getModId());
        market.getStability().unmodifyFlat(getModId() + "_omega");
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyPercent(getModId() + "_omega");
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyPercent(getModId() + "_omega");
    }

    @Override
    protected void addRightAfterDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addRightAfterDescriptionSection(tooltip, mode);
        if (OMEGA_CORE.equals(aiCoreId)) {
            addOmegaCoreDescription(tooltip);
        }
    }

    protected void addOmegaCoreDescription(TooltipMakerAPI tooltip) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();
        CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);

        if (coreSpec != null) {
            TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48);
            text.addPara("Omega-level AI core assigned. Increases supply by %s, reduces demand by %s. Increases stability by %s, fleet size by %s, ground defenses by %s, and grants +1 Patrol.",
                    0f, highlight, "+2", "1", "+1", "+75%", "+25%");
            tooltip.addImageWithText(opad);
        }
    }

    @Override
    public void buildingFinished() {
        super.buildingFinished();
        ensureDaemonForceExists();
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector().getEconomy().isSimMode() || !isFunctional()) return;

        super.advance(amount);

        float days = Global.getSector().getClock().convertToDays(amount);

        if (daemonRespawnTimer > 0) {
            daemonRespawnTimer -= days;
        } else if (!isDaemonForceActive() && daemonRespawnTimer <= 0) {
            spawnLourdDemonForce();
        }

        int currentPatrols = getColTechPatrolCount();
        int maxPatrols = getMaxColTechPatrols();

        if (currentPatrols < maxPatrols) {
            spawnColTechPatrol();
        }
    }

    public boolean isDaemonForceActive() {
        String daemonSource = getDaemonRouteSourceId();
        if (!RouteManager.getInstance().getRoutesForSource(daemonSource).isEmpty()) {
            return true;
        }

        if (market != null && market.getContainingLocation() != null) {
            for (CampaignFleetAPI fleet : market.getContainingLocation().getFleets()) {
                if (fleet.getMemoryWithoutUpdate().getBoolean(LOURD_DEMON_TAG)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected void ensureDaemonForceExists() {
        if (!isDaemonForceActive() && daemonRespawnTimer <= 0) {
            spawnLourdDemonForce();
        }
    }

    public int getMaxColTechPatrols() {
        int size = market.getSize();
        int max = 1;
        if (size >= 10) max = 4;
        else if (size == 9) max = 3;
        else if (size >= 6) max = 2;

        if (OMEGA_CORE.equals(aiCoreId)) {
            max += 1;
        }
        return max;
    }

    public int getColTechPatrolCount() {
        int count = 0;
        for (RouteData data : RouteManager.getInstance().getRoutesForSource(getRouteSourceId())) {
            if (data.getExtra() != null && COLTECH_PATROL_TAG.equals(data.getExtra().fleetType)) {
                count++;
            }
        }
        return count;
    }

    private SectorEntityToken getTargetEntity() {
        if (market.getPrimaryEntity() != null) {
            return market.getPrimaryEntity();
        } else if (market.getPlanetEntity() != null) {
            return market.getPlanetEntity();
        } else if (market.getConnectedEntities() != null && !market.getConnectedEntities().isEmpty()) {
            return market.getConnectedEntities().iterator().next();
        }
        return null;
    }

    protected String getDaemonRouteSourceId() {
        return getRouteSourceId() + "_daemon";
    }

    @Override
    public CampaignFleetAPI spawnFleet(RouteData route) {
        Random random = route.getRandom();
        if (random == null) random = new Random();

        String fleetType = route.getExtra() != null ? route.getExtra().fleetType : null;

        boolean isDaemon = LOURD_DEMON_TAG.equals(fleetType);
        boolean isColTech = COLTECH_PATROL_TAG.equals(fleetType);

        if (isDaemon) {
            return createLourdDemonFleet(route);
        } else if (isColTech) {
            return createColTechPatrolFleet(route, random);
        } else {
            return super.spawnFleet(route);
        }
    }

    protected void spawnColTechPatrol() {
        SectorEntityToken entity = getTargetEntity();
        if (entity == null) return;

        String sid = getRouteSourceId();
        OptionalFleetData extra = new OptionalFleetData(market);
        extra.fleetType = COLTECH_PATROL_TAG;

        PatrolFleetData customData = new PatrolFleetData(PatrolType.COMBAT);
        customData.spawnFP = 120;

        RouteData route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, this, customData);

        extra.strength = (float) getPatrolCombatFP(PatrolType.COMBAT, route.getRandom()) + 75f;
        extra.strength = Misc.getAdjustedStrength(extra.strength, market);

        float patrolDays = 35f + (float) Math.random() * 10f;

        RouteSegment segment = new RouteSegment(patrolDays, entity, COLTECH_PATROL_TAG);
        route.addSegment(segment);
    }

    protected void spawnLourdDemonForce() {
        SectorEntityToken entity = getTargetEntity();
        if (entity == null) return;

        String sid = getDaemonRouteSourceId();
        OptionalFleetData extra = new OptionalFleetData(market);
        extra.fleetType = LOURD_DEMON_TAG;

        PatrolFleetData customData = new PatrolFleetData(PatrolType.HEAVY);
        customData.spawnFP = 300;

        RouteData route = RouteManager.getInstance().addRoute(sid, market, Misc.genRandomSeed(), extra, this, customData);

        RouteSegment segment = new RouteSegment(45f, entity, LOURD_DEMON_TAG);
        route.addSegment(segment);
    }

    private CampaignFleetAPI createColTechPatrolFleet(RouteData route, Random random) {
        SectorEntityToken entity = getTargetEntity();
        if (entity == null) return null;

        FleetParamsV3 params = new FleetParamsV3(
                market,
                null,
                market.getFactionId(),
                route.getQualityOverride(),
                COLTECH_PATROL_TAG,
                120f,
                0f, 0f, 0f, 0f, 0f, 0.40f
        );
        params.random = random;
        params.timestamp = route.getTimestamp();

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setName("Coalition Research Force");
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SOURCE_MARKET, market.getId());
        fleet.getMemoryWithoutUpdate().set(COLTECH_PATROL_TAG, true);

        enforceColTechFleetRoster(fleet);

        fleet.addEventListener(this);

        market.getContainingLocation().addEntity(fleet);
        fleet.setLocation(entity.getLocation().x, entity.getLocation().y);
        fleet.setFacing((float) Math.random() * 360f);

        fleet.addScript(new PatrolAssignmentAIV4(fleet, route));

        return fleet;
    }

    private CampaignFleetAPI createLourdDemonFleet(RouteData route) {
        SectorEntityToken entity = getTargetEntity();
        if (entity == null) return null;

        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(market.getFactionId(), "Daemon Personal Battlegroup", true);

        addVariantsToFleet(fleet, DimensionsCrossedIDS.ONSLAUGHT_HELLHOUND, 8);
        addVariantsToFleet(fleet, DimensionsCrossedIDS.CONQUEST_SOVEREIGN, 2);
        addVariantsToFleet(fleet, DimensionsCrossedIDS.EAGLE_AMBASSADOR, 4);
        addVariantsToFleet(fleet, DimensionsCrossedIDS.CONQUEST_SUPREME, 2);
        addVariantsToFleet(fleet, DimensionsCrossedIDS.ONSLAUGHT_GALAXY, 6);

        fleet.getMemoryWithoutUpdate().set(LOURD_DEMON_TAG, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SOURCE_MARKET, market.getId());

        FactionAPI faction = market.getFaction();
        for (FactionAPI other : Global.getSector().getAllFactions()) {
            if (faction.getRelationship(other.getId()) < 0.10f) {
                fleet.getMemoryWithoutUpdate().set("$hostile_" + other.getId(), true);
            }
        }

        fleet.addEventListener(this);

        market.getContainingLocation().addEntity(fleet);
        fleet.setLocation(entity.getLocation().x, entity.getLocation().y);
        fleet.setFacing((float) Math.random() * 360f);

        fleet.addScript(new PatrolAssignmentAIV4(fleet, route));

        return fleet;
    }

    private void addVariantsToFleet(CampaignFleetAPI fleet, String variantId, int count) {
        for (int i = 0; i < count; i++) {
            fleet.getFleetData().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId));
        }
    }

    private void enforceColTechFleetRoster(CampaignFleetAPI fleet) {
        List<FleetMemberAPI> toRemove = new ArrayList<>();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            String baseHullId = member.getHullSpec().getBaseHullId();
            String hullId = member.getHullId();

            if (!ALLOWED_COLTECH_HULLS.contains(hullId) && !ALLOWED_COLTECH_HULLS.contains(baseHullId)) {
                toRemove.add(member);
            }
        }
        for (FleetMemberAPI member : toRemove) {
            fleet.getFleetData().removeFleetMember(member);
        }
    }

    @Override
    public boolean shouldCancelRouteAfterDelayCheck(RouteData route) {
        return false;
    }

    @Override
    public boolean shouldRepeat(RouteData route) {
        return false;
    }

    @Override
    public void reportAboutToBeDespawnedByRouteManager(RouteData route) {
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        // Intercept Daemon Force despawn to prevent MilitaryBase superclass from throwing NullPointerException
        if (fleet != null && fleet.getMemoryWithoutUpdate().contains(LOURD_DEMON_TAG)) {
            if (reason == FleetDespawnReason.DESTROYED_BY_BATTLE) {
                daemonRespawnTimer = 12f;
            } else {
                daemonRespawnTimer = 7f;
            }
            return; // Exit early so MilitaryBase does not attempt to lookup standard patrol route custom data
        }

        // Standard patrols continue through vanilla despawn handler
        super.reportFleetDespawnedToListener(fleet, reason, param);
    }
}