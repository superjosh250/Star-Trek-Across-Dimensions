package BORG.data.campaign.industry;

import java.awt.Color;

import BORG.data.campaign.ids.BorgIDS;
import BORG.data.scripts.AssimilationTaskforce;
import BORG.data.scripts.CubeDefenseForce;
import UFP.data.campaign.ids.DimensionsCrossedIDS;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MutableCommodityQuantity;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory.PatrolType;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

public class AssimilationDispatch extends MilitaryBase {

    public static final String CUBE_DEFENSE_SOURCE = "CUBE_DEFENSE";

    protected IntervalUtil taskforceTracker = new IntervalUtil(
            Global.getSettings().getFloat("averagePatrolSpawnInterval") * 0.7f,
            Global.getSettings().getFloat("averagePatrolSpawnInterval") * 1.3f
    );

    @Override
    public void apply() {
        super.apply();

        if (market == null) return;

        // Strip default MilitaryBase fleet size multiplier (removes the unwanted +250% fleet size line)
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(getModId());

        int size = market.getSize();

        // 1. BASE STATS & MARKET MODIFIERS
        market.getStability().modifyFlat(getModId(0), 1f, "Assimilation Dispatch Base");
        market.getAccessibilityMod().modifyFlat(getModId(1), 0.125f, "Assimilation Dispatch Base");
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).modifyFlat(getModId(2), 0.125f, "Assimilation Dispatch Base");

        float groundDefenseFlat = 25000f;
        float groundDefenseMult = 1.0f;
        float shipQualityBonus = 0f;

        // Base Special Item Bonus (+1 Stability if any special item installed)
        if (special != null) {
            market.getStability().modifyFlat(getModId(3), 1f, "Specialized Module Operating");
        } else {
            market.getStability().unmodifyFlat(getModId(3));
        }

        // 2. AI CORES & UPKEEP MODIFIERS
        String coreId = getAICoreId();
        if (Commodities.GAMMA_CORE.equals(coreId) || Commodities.ALPHA_CORE.equals(coreId) || Commodities.OMEGA_CORE.equals(coreId)) {
            getUpkeep().modifyMult(getModId(4), 0.75f, coreId + " Core");
        } else {
            getUpkeep().unmodifyMult(getModId(4));
        }

        // 3. IMPROVEMENT TIERS (MAX 4)
        int improveTier = getImproveProductionBonus();

        // 4. DEMAND & SUPPLY CALCULATIONS
        int baseDemand = getBaseDemandForSize(size);
        int baseSupply = getBaseSupplyForSize(size);

        // Tier 1 Improvement (+2 Supply)
        if (improveTier >= 1) {
            baseSupply += 2;
        }

        // AI Core Demand & Supply Modifications
        if (coreId != null && !coreId.isEmpty()) {
            baseDemand = Math.max(0, baseDemand - 1);
            if (Commodities.ALPHA_CORE.equals(coreId) || Commodities.OMEGA_CORE.equals(coreId)) {
                baseSupply += 1;
            }
        }

        // Special Items Mods & Demands
        if (special != null) {
            String itemId = special.getId();
            if (Items.DRONE_REPLICATOR.equals(itemId)) {
                groundDefenseMult += 2.50f; // Intended +250% Ground Defense modifier
                baseDemand = Math.max(0, baseDemand - 2);
            } else if (Items.CRYOARITHMETIC_ENGINE.equals(itemId)) {
                boolean isHot = false;
                boolean isVeryHot = false;

                for (MarketConditionAPI mc : market.getConditions()) {
                    if ("hot".equals(mc.getId())) isHot = true;
                    if ("very_hot".equals(mc.getId())) isVeryHot = true;
                }

                if (isVeryHot) {
                    shipQualityBonus += 1.25f;
                    baseDemand = Math.max(0, baseDemand - 2);
                    groundDefenseMult += 0.075f;
                } else if (isHot) {
                    shipQualityBonus += 1.00f;
                    baseDemand = Math.max(0, baseDemand - 2);
                } else {
                    shipQualityBonus += 0.75f;
                    baseDemand = Math.max(0, baseDemand - 1);
                }
            }
        }

        // Apply Standard Demands
        demand(Commodities.SUPPLIES, baseDemand);
        demand(Commodities.FUEL, baseDemand);
        demand(Commodities.SHIPS, baseDemand);

        // Dilithium Demand Scaling
        int dilithiumDemand = getDilithiumDemandForSize(size);
        demand(DimensionsCrossedIDS.DILITHIUM, dilithiumDemand);

        // Tier 2 Improvement (+1 Supplies demand override)
        if (improveTier >= 2) {
            MutableCommodityQuantity currentSupplies = getDemand(Commodities.SUPPLIES);
            int currentSuppliesDemand = (currentSupplies != null && currentSupplies.getQuantity() != null)
                    ? currentSupplies.getQuantity().getModifiedInt()
                    : baseDemand;
            demand(Commodities.SUPPLIES, currentSuppliesDemand + 1);
        }

        // Apply Supplies Output
        supply(Commodities.CREW, baseSupply);
        supply(Commodities.MARINES, baseSupply);
        supply(BorgIDS.DRONES, baseSupply);
        supply(BorgIDS.ASSIMILATED_CIVILIANS, baseSupply);

        // 5. STATS REGISTRATION
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
                .modifyFlat(getModId(6), shipQualityBonus, "Assimilation Dispatch Modules");

        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyFlat(getModId(7), groundDefenseFlat, "Assimilation Dispatch Defenses");

        if (groundDefenseMult > 1.0f) {
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                    .modifyMult(getModId(8), groundDefenseMult, "Assimilation Dispatch Systems");
        } else {
            market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(getModId(8));
        }

        if (!isFunctional()) {
            supply.clear();
            unapply();
        }
    }

    @Override
    public void unapply() {
        super.unapply();
        if (market == null) return;

        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(getModId());
        market.getStability().unmodifyFlat(getModId(0));
        market.getAccessibilityMod().unmodifyFlat(getModId(1));
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodifyFlat(getModId(2));
        market.getStability().unmodifyFlat(getModId(3));
        getUpkeep().unmodifyMult(getModId(4));

        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(getModId(6));
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyFlat(getModId(7));
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyMult(getModId(8));
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (Global.getSector().getEconomy().isSimMode() || !isFunctional()) return;

        // 1. Maintain 3 Cube Defense Fleets
        checkCubeDefenseFleets();

        // 2. Separate Assimilation Taskforce spawning tracker logic
        float days = Global.getSector().getClock().convertToDays(amount);
        float spawnRate = market.getStats().getDynamic().getStat(Stats.COMBAT_FLEET_SPAWN_RATE_MULT).getModifiedValue();

        if (Global.getSector().isInNewGameAdvance()) spawnRate *= 3f;

        taskforceTracker.advance(days * spawnRate);

        if (taskforceTracker.intervalElapsed()) {
            int currentTaskforces = getAssimilationTaskforceCount();
            int maxTaskforces = getMaxAssimilationTaskforces();

            if (currentTaskforces < maxTaskforces) {
                OptionalFleetData extra = new OptionalFleetData(market);
                extra.fleetType = AssimilationTaskforce.ASSIMILATION_TASKFORCE;

                PatrolFleetData custom = new PatrolFleetData(PatrolType.HEAVY);

                RouteData route = RouteManager.getInstance().addRoute(getRouteSourceId(), market, Misc.genRandomSeed(), extra, this, custom);
                extra.strength = AssimilationTaskforce.getCalculatedFleetPoints(market);

                float patrolDays = 35f + (float) Math.random() * 10f;
                route.addSegment(new RouteSegment(patrolDays, market.getPrimaryEntity()));
            }
        }
    }

    private void checkCubeDefenseFleets() {
        int existingCubes = 0;

        for (RouteData route : RouteManager.getInstance().getRoutesForSource(CUBE_DEFENSE_SOURCE)) {
            if (!route.isExpired()) {
                existingCubes++;
            }
        }

        while (existingCubes < 3) {
            OptionalFleetData extra = new OptionalFleetData(market);
            extra.fleetType = PatrolType.HEAVY.getFleetType();

            RouteData route = RouteManager.getInstance().addRoute(
                    CUBE_DEFENSE_SOURCE,
                    market,
                    Misc.genRandomSeed(),
                    extra,
                    this,
                    new PatrolFleetData(PatrolType.HEAVY)
            );
            route.addSegment(new RouteSegment(99999f, market.getPrimaryEntity()));

            spawnFleet(route);
            existingCubes++;
        }
    }

    @Override
    public CampaignFleetAPI spawnFleet(RouteData route) {
        if (CUBE_DEFENSE_SOURCE.equals(route.getSource())) {
            CampaignFleetAPI fleet = CubeDefenseForce.createFleet(market);

            fleet.addEventListener(this);

            market.getContainingLocation().addEntity(fleet);
            fleet.setFacing((float) Math.random() * 360f);
            if (market.getPrimaryEntity() != null) {
                fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);
            }

            fleet.addScript(new com.fs.starfarer.api.impl.campaign.fleets.PatrolAssignmentAIV4(fleet, route));
            return fleet;
        }

        if (route.getExtra() != null && AssimilationTaskforce.ASSIMILATION_TASKFORCE.equals(route.getExtra().fleetType)) {
            CampaignFleetAPI fleet = AssimilationTaskforce.createTaskforce(market);

            if (fleet == null || fleet.isEmpty()) return null;

            fleet.addEventListener(this);

            market.getContainingLocation().addEntity(fleet);
            fleet.setFacing((float) Math.random() * 360f);
            if (market.getPrimaryEntity() != null) {
                fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);
            }

            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SOURCE_MARKET, market.getId());
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_TYPE, AssimilationTaskforce.ASSIMILATION_TASKFORCE);

            fleet.addScript(new com.fs.starfarer.api.impl.campaign.fleets.PatrolAssignmentAIV4(fleet, route));
            return fleet;
        }

        return super.spawnFleet(route);
    }

    public int getMaxAssimilationTaskforces() {
        int size = market.getSize();
        if (size < 5) return 0;
        if (size <= 5) return 1;
        if (size <= 7) return 2;
        if (size <= 9) return 3;
        return 4;
    }

    public int getAssimilationTaskforceCount() {
        int count = 0;
        for (RouteData data : RouteManager.getInstance().getRoutesForSource(getRouteSourceId())) {
            if (data.getExtra() != null && AssimilationTaskforce.ASSIMILATION_TASKFORCE.equals(data.getExtra().fleetType)) {
                count++;
            }
        }
        return count;
    }

    private int getBaseDemandForSize(int size) {
        switch (size) {
            case 1: case 2: case 3:
            case 4: case 5:
                return 3;
            case 6: case 7: case 8:
                return 4;
            case 9:
                return 5;
            default:
                return 6;
        }
    }

    private int getBaseSupplyForSize(int size) {
        switch (size) {
            case 1: case 2: case 3:
                return 3;
            case 4: case 5:
                return 4;
            case 6:
                return 5;
            case 7: case 8:
                return 6;
            case 9:
                return 7;
            default:
                return 8;
        }
    }

    private int getDilithiumDemandForSize(int size) {
        if (size <= 5) return 1;
        if (size <= 8) return 2;
        return 3;
    }

    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        if (data == null) return false;
        String id = data.getId();
        return Items.DRONE_REPLICATOR.equals(id) || Items.CRYOARITHMETIC_ENGINE.equals(id) || super.wantsToUseSpecialItem(data);
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        super.addPostDemandSection(tooltip, hasDemand, mode);

        float opad = 10f;
        Color h = Misc.getHighlightColor();

        if (special != null) {
            String itemId = special.getId();
            if (Items.DRONE_REPLICATOR.equals(itemId)) {
                tooltip.addPara("Drone Replicator installed: +250%% ground defense, -2 demand.", opad, h, "+250%", "-2");
            } else if (Items.CRYOARITHMETIC_ENGINE.equals(itemId)) {
                tooltip.addPara("Cryoarithmetic Engine installed: Provides ship quality and demand bonuses based on planetary temperature.", opad, h);
            }
        }
    }
}