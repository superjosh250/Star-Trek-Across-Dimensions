package UFP.data.campaign.industry;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import UFP.data.plugins.StarfleetTaskforceType;

public class StarfleetOperations extends MilitaryBase {

    /*** Checks if the market operates on the compressed 3-5 size scale. */
    protected boolean isStationPopulation() {
        return market != null
                && DimensionsCrossedIDS.STATION_POPULATION != null
                && market.hasIndustry(DimensionsCrossedIDS.STATION_POPULATION);
    }

    /**
     * Maps raw market size to an effective calculation size (3 to 10 scale).
     * If STATION_POPULATION is active:
     * - Size <= 3 -> Effective Size 3
     * - Size 4   -> Effective Size 6
     * - Size 5+  -> Effective Size 10 (Maximum)
     */
    protected int getEffectiveMarketSize() {
        if (market == null) return 3;
        int rawSize = market.getSize();
        if (rawSize < 1) rawSize = 3; // Guard against 0 size during Nexerelin market transfers/inits

        if (isStationPopulation()) {
            if (rawSize <= 3) return 3;
            if (rawSize == 4) return 6;
            return 10; // Max out at Size 5 for Station Population
        }
        return Math.max(3, rawSize);
    }

    /**
     * Calculates flat ground defense bonus based on effective market size.
     * Standard Scale (Size 3 to 10): Starts at 12,000 and scales x1.25 per level (up to ~57,220 at size 10).
     * Station Population Scale (Size 3 to 5): Starts at 2,500 at size 3 and scales x1.732 per level to reach 7,500 at size 5.
     */
    protected float getGroundDefensesValue() {
        int effectiveSize = getEffectiveMarketSize();
        float defenseVal;

        if (isStationPopulation()) {
            float baseDefenses = 2500f;
            int steps = Math.min(10, Math.max(3, effectiveSize)) - 3;
            // 2500 * (1.73205^2) = 7500 at effective size 10 (raw size 5)
            defenseVal = (float) (baseDefenses * Math.pow(1.7320508, steps / 3.5));
        } else {
            float baseDefenses = 12000f;
            int steps = Math.min(10, Math.max(3, effectiveSize)) - 3;
            defenseVal = (float) (baseDefenses * Math.pow(1.25, steps));
        }

        if (Float.isNaN(defenseVal) || Float.isInfinite(defenseVal) || defenseVal < 0f) {
            return 12000f;
        }
        return defenseVal;
    }

    // =========================================================
    // INDUSTRY / STRUCTURE CONFIGURATION
    // =========================================================
    @Override
    public boolean isStructure() {
        return true; // Prevents consuming an industry slot
    }

    @Override
    public boolean canImprove() {
        return true;
    }

    public int getMaxImprovementLevel() {
        return 4;
    }

    // =========================================================
    // MAIN APPLY METHOD
    // =========================================================
    @Override
    public void apply() {
        super.apply();

        if (market == null || market.getStats() == null || !isFunctional()) return;

        int effectiveSize = getEffectiveMarketSize();

        // -----------------------------------------------------
        // 1. Demand & Supply Calculation
        // -----------------------------------------------------
        int baseDemand;
        int baseSupply;

        if (effectiveSize <= 3) {
            baseDemand = 3;
            baseSupply = 3;
        } else if (effectiveSize <= 5) {
            baseDemand = 3;
            baseSupply = 4;
        } else if (effectiveSize == 6) {
            baseDemand = 4;
            baseSupply = 5;
        } else if (effectiveSize <= 8) {
            baseDemand = 4;
            baseSupply = 6;
        } else if (effectiveSize == 9) {
            baseDemand = 5;
            baseSupply = 7;
        } else { // Size 10+
            baseDemand = 6;
            baseSupply = 8;
        }

        int officerAndDilithiumDemand = effectiveSize >= 10 ? 3 : (effectiveSize >= 6 ? 2 : 1);

        // Apply Improvements to Demand & Supply
        int demandMod = (isImproved() && getMaxImprovementLevel() >= 1) ? -1 : 0;
        int supplyMod = (isImproved() && getMaxImprovementLevel() >= 2) ? 1 : 0;

        demand(Commodities.SUPPLIES, Math.max(0, baseDemand + demandMod));
        demand(Commodities.FUEL, Math.max(0, baseDemand + demandMod));
        demand(Commodities.SHIPS, Math.max(0, baseDemand + demandMod));
        if (DimensionsCrossedIDS.STARFLEET_OFFICERS != null) {
            demand(DimensionsCrossedIDS.STARFLEET_OFFICERS, Math.max(0, officerAndDilithiumDemand + demandMod));
        }
        if (DimensionsCrossedIDS.DILITHIUM != null) {
            demand(DimensionsCrossedIDS.DILITHIUM, Math.max(0, officerAndDilithiumDemand + demandMod));
        }

        supply(Commodities.CREW, baseSupply + supplyMod);
        supply(Commodities.MARINES, baseSupply + supplyMod);

        // -----------------------------------------------------
        // 2. Patrol Counts
        // -----------------------------------------------------
        int light = 1, medium = 0, heavy = 0;

        if (effectiveSize == 3) {
            light = 1; medium = 0; heavy = 0;
        } else if (effectiveSize == 4) {
            light = 2; medium = 1; heavy = 0;
        } else if (effectiveSize == 5) {
            light = 2; medium = 2; heavy = 0;
        } else if (effectiveSize == 6) {
            light = 3; medium = 3; heavy = 2;
        } else if (effectiveSize == 7) {
            light = 3; medium = 3; heavy = 3;
        } else if (effectiveSize == 8 || effectiveSize == 9) {
            light = 4; medium = 3; heavy = 4;
        } else if (effectiveSize >= 10) { // Size 5 Station Population lands here (4/6/4)
            light = 4; medium = 6; heavy = 4;
        }

        // Tier 3 Improvement: +1 Light & +1 Medium patrols
        if (isImproved() && getMaxImprovementLevel() >= 3) {
            light += 1;
            medium += 1;
        }

        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_LIGHT_MOD).modifyFlat(getModId(), light);
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_MEDIUM_MOD).modifyFlat(getModId(), medium);
        market.getStats().getDynamic().getMod(Stats.PATROL_NUM_HEAVY_MOD).modifyFlat(getModId(), heavy);

        // -----------------------------------------------------
        // 3. Stats, Ground Defenses & Officer Probabilities
        // -----------------------------------------------------
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).modifyFlat(getModId(), 0.25f);
        market.getStats().getDynamic().getMod(Stats.OFFICER_IS_MERC_PROB_MOD).modifyFlat(getModId(), 1.0f);

        // Apply dynamic ground defense bonus scaled by market size
        float groundDefenseValue = getGroundDefensesValue();
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD)
                .modifyFlat(getModId(), groundDefenseValue, getNameForModifier());

        // Apply base fleet size multiplier
        float fleetSizeMult = 1.0f + (effectiveSize - 3) * 0.1f;
        if (Float.isNaN(fleetSizeMult) || Float.isInfinite(fleetSizeMult) || fleetSizeMult <= 0f) {
            fleetSizeMult = 1.0f;
        }
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                .modifyFlat(getModId(), fleetSizeMult, getNameForModifier());

        // Tier 4 Improvement: +4 Ship Quality
        if (isImproved() && getMaxImprovementLevel() >= 4) {
            market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlat(getModId() + "_imp4", 4.0f, getNameForModifier() + " (Improvement)");
        }

        // -----------------------------------------------------
        // 4. AI Core Modifiers
        // -----------------------------------------------------
        if (aiCoreId != null && Commodities.OMEGA_CORE.equals(aiCoreId)) {
            applyOmegaCoreModifiers();
        }
    }

    @Override
    public void unapply() {
        super.unapply();

        if (market == null || market.getStats() == null) return;

        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodifyFlat(getModId());
        market.getStats().getDynamic().getMod(Stats.OFFICER_IS_MERC_PROB_MOD).unmodifyFlat(getModId());
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(getModId() + "_imp4");
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyFlat(getModId());

        // Clean up potential legacy fleet size modifiers from saved states
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(getModId());
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(getModId() + "_imp4");
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(getModId() + "_omega");

        unapplyOmegaCoreModifiers();
    }

    // =========================================================
    // TASKFORCE & PATROL DELEGATION LOGIC
    // =========================================================
    public int getMaxTaskforces() {
        int effectiveSize = getEffectiveMarketSize();
        if (effectiveSize < 7) return 0;
        if (effectiveSize == 7) return 1;
        if (effectiveSize == 8 || effectiveSize == 9) return 2;
        return 4; // effectiveSize >= 10 (Station Pop size 5 gets 4)
    }

    public int getTaskforceCount() {
        return StarfleetTaskforceType.getTaskforceCount(this);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (Float.isNaN(amount) || Float.isInfinite(amount) || amount <= 0f) return;
        if (Float.isNaN(returningPatrolValue) || Float.isInfinite(returningPatrolValue)) {
            returningPatrolValue = 0f;
        }
        if (tracker == null) return;

        returningPatrolValue = StarfleetTaskforceType.handleAdvance(this, getMaxTaskforces(), tracker, returningPatrolValue, amount);
    }

    @Override
    public CampaignFleetAPI spawnFleet(RouteData route) {
        if (route == null) return super.spawnFleet(route);
        CampaignFleetAPI fleet = StarfleetTaskforceType.handleSpawnFleet(this, route);
        if (fleet != null) {
            return fleet;
        }
        return super.spawnFleet(route);
    }

    // =========================================================
    // OMEGA CORE LOGIC
    // =========================================================
    protected void applyOmegaCoreModifiers() {
        if (market == null || market.getStats() == null) return;

        demandReduction.modifyFlat(getModId(), 2, "Omega Core");
        supplyBonus.modifyFlat(getModId(), 2, "Omega Core");

        getUpkeep().modifyMult(getModId(), 0.75f, "Omega Core");
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlat(getModId() + "_omega", 2.0f, "Omega Core");

        market.getStability().modifyFlat(getModId() + "_omega", 2, "Omega Core");
        getIncome().modifyFlat(getModId() + "_omega", 2500, "Omega Core");
        market.getAccessibilityMod().modifyFlat(getModId() + "_omega", 0.025f, "Omega Core");
    }

    protected void unapplyOmegaCoreModifiers() {
        if (market == null || market.getStats() == null) return;

        demandReduction.unmodifyFlat(getModId());
        supplyBonus.unmodifyFlat(getModId());
        getUpkeep().unmodifyMult(getModId());
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(getModId() + "_omega");
        market.getStability().unmodifyFlat(getModId() + "_omega");
        getIncome().unmodifyFlat(getModId() + "_omega");
        market.getAccessibilityMod().unmodifyFlat(getModId() + "_omega");
    }

    // =========================================================
    // OMEGA CORE DESCRIPTION & TOOLTIPS
    // =========================================================
    protected void addAICoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        if (Commodities.OMEGA_CORE.equals(aiCoreId)) {
            addOmegaCoreDescription(tooltip, mode);
        } else {
            super.addAICoreSection(tooltip, mode);
        }
    }

    protected void addOmegaCoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();

        String pre = "Omega-level AI core currently assigned. ";
        if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            pre = "Omega-level AI core. ";
        }

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            CommoditySpecAPI coreSpec = aiCoreId != null ? Global.getSettings().getCommoditySpec(aiCoreId) : null;
            String iconPath = coreSpec != null ? coreSpec.getIconName() : null;
            TooltipMakerAPI text = iconPath != null ? tooltip.beginImageWithText(iconPath, 48) : tooltip;
            text.addPara(pre + "Reduces upkeep cost by %s. Reduces demand by %s units. " +
                            "Increases supply by %s units. " +
                            "Grants +%s ship quality bonus, +%s stability, +%s accessibility, and %s credits income.",
                    0f, highlight,
                    "25%", "2", "2", "2.0", "2", "2.5%", "2,500");
            if (iconPath != null) {
                tooltip.addImageWithText(opad);
            }
            return;
        }

        tooltip.addPara(pre + "Reduces upkeep cost by %s. Reduces demand by %s units. " +
                        "Increases supply by %s units. " +
                        "Grants +%s ship quality bonus, +%s stability, +%s accessibility, and %s credits income.",
                opad, highlight,
                "25%", "2", "2", "2.0", "2", "2.5%", "2,500");
    }

    public List<String> getOmegaCoreEffects() {
        return java.util.Arrays.asList(
                "-2 Demand",
                "+2 Supply",
                "-25% Upkeep Cost",
                "+2 Ship Quality/Bonus",
                "+2 Stability",
                "+2,500 Credits Income",
                "+2.5% Accessibility"
        );
    }

    // =========================================================
    // IMPROVEMENT DESCRIPTION FOR UI TOOLTIPS
    // =========================================================
    @Override
    public void addImproveDesc(TooltipMakerAPI info, ImprovementDescriptionMode mode) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();

        info.addPara("Level 1: Reduces demand by %s unit.", 0f, highlight, "1");
        info.addPara("Level 2: Increases supply by %s unit.", 0f, highlight, "1");
        info.addPara("Level 3: Increases launched patrols by %s light and %s medium.", 0f, highlight, "1", "1");
        info.addPara("Level 4: Increases ship quality bonus by +%s.", 0f, highlight, "4.0");

        info.addSpacer(opad);
        super.addImproveDesc(info, mode);
    }
}