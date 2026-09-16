package UFP.data.campaign.industry;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolAssignmentAIV4;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import UFP.data.plugins.StarfleetHQFleets;
import UFP.data.plugins.StarfleetHQFleets.HQDefenseFleetData;

public class StarfleetHQ extends BaseIndustry implements MarketImmigrationModifier, RouteFleetSpawner, FleetEventListener {

    protected int improvementLevel = 0;

    // Ensures this industry does NOT count against the market industry slot limit
    @Override
    public boolean isStructure() {
        return true;
    }

    // --- FACTION & PLAYER BUILDING RESTRICTIONS ---
    @Override
    public boolean isAvailableToBuild() {
        if (!super.isAvailableToBuild()) return false;

        // Players cannot install into their own colonies
        if (market.isPlayerOwned()) return false;

        // ONE per faction restriction
        String factionId = market.getFactionId();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (factionId.equals(m.getFactionId()) && m.hasIndustry(getId())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getUnavailableReason() {
        if (market.isPlayerOwned()) {
            return "Starfleet HQ cannot be built on player-owned colonies.";
        }
        String factionId = market.getFactionId();
        for (MarketAPI m : Global.getSector().getEconomy().getMarketsCopy()) {
            if (factionId.equals(m.getFactionId()) && m.hasIndustry(getId())) {
                return "Only one Starfleet HQ is permitted per faction.";
            }
        }
        return super.getUnavailableReason();
    }

    // --- POPULATION GROWTH (+25) ---
    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        if (isFunctional()) {
            incoming.getWeight().modifyFlat(getModId(0), 25f, getNameForModifier());
        }
    }

    @Override
    public void apply() {

        int size = market.getSize();

        // --- BASE STATS ---
        float accessibility = 0.75f;
        float fleetSize = 0.50f;
        int shipBonus = 4;
        float shipQuality = 0.0f;

        // Apply Stats & Base Income (250,000 Credits)
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).modifyFlat(getModId(0), 1.0f, getNameForModifier());
        market.getStats().getDynamic().getMod(Stats.ADMIN_PROB_MOD).modifyFlat(getModId(0), 0.50f, getNameForModifier());
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyFlat(getModId(0), 250000f, getNameForModifier());

        getIncome().modifyFlat(getModId(0), 250000f, getNameForModifier());

        market.getStability().modifyFlat(getModId(0), 5f, getNameForModifier());
        market.getHazard().modifyFlat(getModId(0), -0.075f, getNameForModifier());

        // Removes Pirate Activity condition if present on the market
        if (market.hasCondition(Conditions.PIRATE_ACTIVITY)) {
            market.removeCondition(Conditions.PIRATE_ACTIVITY);
        }

        // --- DILITHIUM AUGMENTATION INJECTION ---
        Industry refiningPlant = market.getIndustry(DimensionsCrossedIDS.REFINING_PROCESSING_PLANT);
        if (refiningPlant != null && refiningPlant.isFunctional()) {
            refiningPlant.getSupply(DimensionsCrossedIDS.DILITHIUM).getQuantity().modifyFlat(getModId(), 1, getNameForModifier());
        } else {
            for (Industry ind : market.getIndustries()) {
                if (ind != this && ind.isFunctional() && ind.getSupply(DimensionsCrossedIDS.DILITHIUM) != null) {
                    if (ind.getSupply(DimensionsCrossedIDS.DILITHIUM).getQuantity().getModifiedValue() > 0) {
                        ind.getSupply(DimensionsCrossedIDS.DILITHIUM).getQuantity().modifyFlat(getModId(), 1, getNameForModifier());
                        break;
                    }
                }
            }
        }

        // --- IMPROVEMENT TIERS (MAX 4) ---
        if (improvementLevel >= 1) shipBonus += 10;
        if (improvementLevel >= 2) fleetSize += 0.125f;
        if (improvementLevel >= 3) accessibility += 0.20f;
        if (improvementLevel >= 4) shipQuality += 0.20f;

        // --- AI CORE MODIFIERS ---
        boolean reduceMarketDemand = false;
        boolean suppliesCrew = false;
        int aiCoreSupplyBonus = 0;

        if (aiCoreId != null) {
            aiCoreSupplyBonus += 1; // All cores add +1 Supply

            switch (aiCoreId) {
                case Commodities.GAMMA_CORE ->
                        market.getHazard().modifyFlat(getModId(1), -0.20f, getNameForModifier() + " (Gamma Core)");
                case Commodities.ALPHA_CORE -> {
                    market.getHazard().modifyFlat(getModId(1), -0.20f, getNameForModifier() + " (Alpha Core)");
                    reduceMarketDemand = true;
                }
                case Commodities.OMEGA_CORE -> {
                    market.getHazard().modifyFlat(getModId(1), -0.20f, getNameForModifier() + " (Omega Core)");
                    reduceMarketDemand = true;
                    suppliesCrew = true;
                }
            }
        }

        // Apply -1 Demand reduction for all market industries (Alpha / Omega Core)
        if (reduceMarketDemand) {
            for (Industry ind : market.getIndustries()) {
                if (ind.isFunctional()) {
                    ind.getDemandReduction().modifyFlat(getModId(), 1, getNameForModifier() + " (AI Core)");
                }
            }
        }

        // Apply accessibility and fleet size stats
        market.getAccessibilityMod().modifyFlat(getModId(0), accessibility, getNameForModifier());
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyFlat(getModId(0), fleetSize, getNameForModifier());

        if (shipQuality > 0) {
            market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).modifyFlat(getModId(0), shipQuality, getNameForModifier());
        }

        // --- OFFICER SUPPLY CALCULATION ---
        float totalAccessibility = market.getAccessibilityMod().computeEffective(0f);
        int accUnits = (int) (totalAccessibility / 0.20f);
        int officerSupply = accUnits + 1; // Base market size bonus (+1)

        if (size >= 5) officerSupply += 1;
        if (size >= 7) officerSupply += 1;
        if (size >= 8) officerSupply += 1;
        if (size >= 9) officerSupply += 1;

        officerSupply += aiCoreSupplyBonus;

        // --- APPLY SUPPLIES ---
        supply(DimensionsCrossedIDS.STARFLEET_OFFICERS, officerSupply);

        if (suppliesCrew) {
            supply(Commodities.CREW, officerSupply);
        }

        if (shipBonus > 0) {
            supply(1, Commodities.SHIPS, shipBonus, getNameForModifier());
        }

        market.addTransientImmigrationModifier(this);
    }

    // =========================================================
    // ADVANCE TICK & FLEET MANAGEMENT
    // =========================================================
    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (!isFunctional()) return;

        StarfleetHQFleets.checkAndSpawnDefenseForce(this);
    }

    // =========================================================
    // ROUTE FLEET SPAWNER & LISTENER IMPLEMENTATION
    // =========================================================
    @Override
    public CampaignFleetAPI spawnFleet(RouteData route) {
        if (!(route.getCustom() instanceof HQDefenseFleetData)) {
            return null;
        }

        HQDefenseFleetData custom = (HQDefenseFleetData) route.getCustom();
        CampaignFleetAPI fleet = StarfleetHQFleets.createHQFleet(market, custom.fleetName);

        if (fleet == null || fleet.isEmpty()) return null;

        fleet.addEventListener(this);

        market.getContainingLocation().addEntity(fleet);
        fleet.setFacing((float) Math.random() * 360f);
        fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

        fleet.addScript(new PatrolAssignmentAIV4(fleet, route));
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true, 0.3f);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);

        custom.spawnFP = fleet.getFleetPoints();

        return fleet;
    }

    @Override
    public boolean shouldRepeat(RouteData route) {
        return false;
    }

    @Override
    public void reportAboutToBeDespawnedByRouteManager(RouteData route) {
    }

    @Override
    public boolean shouldCancelRouteAfterDelayCheck(RouteData route) {
        return false;
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
    }

    @Override
    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
        if (!isFunctional()) return;

        String sid = market.getId() + "_starfleet_hq_defense";
        RouteData route = RouteManager.getInstance().getRoute(sid, fleet);

        if (route != null && route.getCustom() instanceof HQDefenseFleetData) {
            HQDefenseFleetData custom = (HQDefenseFleetData) route.getCustom();
            boolean wasDestroyed = (reason == FleetDespawnReason.DESTROYED_BY_BATTLE);
            StarfleetHQFleets.notifyFleetDespawned(custom.customTag, wasDestroyed);
        }
    }

    @Override
    public void unapply() {
        super.unapply();

        market.getAccessibilityMod().unmodifyFlat(getModId(0));
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyFlat(getModId(0));
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodifyFlat(getModId(0));
        market.getStats().getDynamic().getMod(Stats.ADMIN_PROB_MOD).unmodifyFlat(getModId(0));
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyFlat(getModId(0));
        market.getStability().unmodifyFlat(getModId(0));
        market.getHazard().unmodifyFlat(getModId(0));
        market.getHazard().unmodifyFlat(getModId(1));
        market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).unmodifyFlat(getModId(0));

        getIncome().unmodifyFlat(getModId(0));

        // Clean up modifications injected into other industries
        for (Industry ind : market.getIndustries()) {
            ind.getDemandReduction().unmodifyFlat(getModId());
            if (ind.getSupply(DimensionsCrossedIDS.DILITHIUM) != null) {
                ind.getSupply(DimensionsCrossedIDS.DILITHIUM).getQuantity().unmodifyFlat(getModId());
            }
        }
    }

    // --- TOOLTIP DISPLAY ---
    @Override
    protected boolean hasPostDemandSection(boolean hasDemand, IndustryTooltipMode mode) {
        return true;
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        if (mode != IndustryTooltipMode.NORMAL || isFunctional()) {
            Color h = Misc.getHighlightColor();
            float opad = 10f;

            tooltip.addPara("Base income: %s credits.", opad, h, "250,000");
            tooltip.addPara("Stability bonus: %s.", 2f, h, "+5");
            tooltip.addPara("Ground defense bonus: %s.", 2f, h, "+250,000");
            tooltip.addPara("Population growth bonus: %s.", 2f, h, "+25");
            tooltip.addPara("Officer / Admin probability: %s / %s.", 2f, h, "+100%", "+50%");
            tooltip.addPara("Suppresses pirate activity in the market.", 2f);

            Industry refiningPlant = market.getIndustry(DimensionsCrossedIDS.REFINING_PROCESSING_PLANT);
            if (refiningPlant != null && refiningPlant.isFunctional()) {
                tooltip.addPara("Augmenting %s: %s Dilithium supply.", 2f, h, "Refining & Processing Plant", "+1");
            }
        }
    }

    @Override
    public boolean canImprove() {
        return improvementLevel < 4;
    }

    public int getImprovementLevel() {
        return improvementLevel;
    }

    public void setImprovementLevel(int level) {
        this.improvementLevel = Math.min(4, Math.max(0, level));
    }
}