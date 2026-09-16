package UFP.data.campaign.industry;

import java.awt.Color;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.Waystation;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class Waystation_Starfleet extends Waystation {

    protected int improveLevel = 0;

    @Override
    public void apply() {
        super.apply();

        int size = market.getSize();
        int baseDemandUnit = getBaseDemandUnit(size);
        int baseSupplyUnit = getBaseSupplyUnit(size);
        int suppliesAndDilithiumDemand = getSuppliesAndDilithiumDemand(size);

        // -----------------------------------------------------------------
        // DEMAND: CREW, SUPPLIES, FUEL, DILITHIUM
        // -----------------------------------------------------------------
        int dilithiumDeficit = getMaxDeficit(DimensionsCrossedIDS.DILITHIUM).two;
        int fuelDemand = baseDemandUnit + (dilithiumDeficit > 0 ? dilithiumDeficit : 0);

        demand(Commodities.FUEL, fuelDemand);
        demand(Commodities.CREW, baseDemandUnit);
        demand(Commodities.SUPPLIES, suppliesAndDilithiumDemand);
        demand(DimensionsCrossedIDS.DILITHIUM, suppliesAndDilithiumDemand);

        // -----------------------------------------------------------------
        // BASE MODIFIERS: Accessibility (Scales with Market Size)
        // -----------------------------------------------------------------
        String desc = getNameForModifier();
        float baseAccess = getBaseAccessibility(size);
        market.getAccessibilityMod().modifyFlat(getModId() + "_base_access", baseAccess, desc);

        // -----------------------------------------------------------------
        // CREW DEFICIT OFFSET (CIVILIAN & CADETS)
        // -----------------------------------------------------------------
        int crewDeficit = getMaxDeficit(Commodities.CREW).two;
        int effectiveCrewDeficit = crewDeficit;

        if (crewDeficit > 0) {
            demand(DimensionsCrossedIDS.CIVILIAN, crewDeficit);
            demand(DimensionsCrossedIDS.CADETS, crewDeficit);

            int civDeficit = getMaxDeficit(DimensionsCrossedIDS.CIVILIAN).two;
            int cadetDeficit = getMaxDeficit(DimensionsCrossedIDS.CADETS).two;

            // Offset crew deficit if Civilians and Cadets are available
            int maxDeficitOffset = Math.max(civDeficit, cadetDeficit);
            effectiveCrewDeficit = Math.min(crewDeficit, maxDeficitOffset);
        }

        // -----------------------------------------------------------------
        // SUPPLY: SUPPLIES, STARFLEET_OFFICERS
        // -----------------------------------------------------------------
        int extraSupply = (improveLevel >= 4 ? 1 : 0) + (DimensionsCrossedIDS.OMEGA_CORE.equals(aiCoreId) ? 2 : 0);

        supply(Commodities.SUPPLIES, baseSupplyUnit + extraSupply);

        int officerSupply = Math.max(0, baseSupplyUnit - effectiveCrewDeficit) + extraSupply;
        supply(DimensionsCrossedIDS.STARFLEET_OFFICERS, officerSupply);

        // -----------------------------------------------------------------
        // APPLY IMPROVEMENTS & AI CORE
        // -----------------------------------------------------------------
        applyImprovementModifiers();

        if (DimensionsCrossedIDS.OMEGA_CORE.equals(aiCoreId)) {
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

        market.getAccessibilityMod().unmodifyFlat(getModId() + "_base_access");
        market.getAccessibilityMod().unmodifyFlat(getModId() + "_imp_access");
        market.getAccessibilityMod().unmodifyFlat(getModId() + "_omega_access");

        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(getModId() + "_omega_quality");
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).unmodifyPercent(getModId() + "_omega_defenses");

        market.getHazard().unmodifyFlat(getModId() + "_omega_hazard");
        getUpkeep().unmodifyMult(getModId() + "_omega_upkeep");
    }

    // -----------------------------------------------------------------
    // BUILD AVAILABILITY REQUIREMENTS
    // -----------------------------------------------------------------
    @Override
    public boolean isAvailableToBuild() {
        return market.hasIndustry(DimensionsCrossedIDS.SOCIETY_POPULATION) ||
                market.hasIndustry(DimensionsCrossedIDS.STATION_POPULATION) ||
                market.hasIndustry(DimensionsCrossedIDS.STARBASE_SPACEPORT);
    }

    @Override
    public String getUnavailableReason() {
        return "Requires Society Population, Station Population, or Starbase Spaceport";
    }

    // -----------------------------------------------------------------
    // HELPER SCALING TABLES
    // -----------------------------------------------------------------
    protected float getBaseAccessibility(int size) {
        if (size <= 3) return 0.25f;
        if (size == 4) return 0.35f;
        if (size == 5) return 0.45f;
        if (size == 6) return 0.50f;
        if (size == 7) return 0.55f;
        if (size == 8) return 0.65f;
        if (size == 9) return 0.70f;
        return 0.75f; // Size 10+
    }

    protected int getBaseSupplyUnit(int size) {
        if (size <= 3) return 3;
        if (size <= 5) return 4;
        if (size <= 6) return 5;
        if (size <= 8) return 6;
        if (size == 9) return 7;
        return 8; // 10+
    }

    protected int getBaseDemandUnit(int size) {
        if (size <= 5) return 3;
        if (size <= 8) return 4;
        if (size == 9) return 5;
        return 6; // 10+
    }

    protected int getSuppliesAndDilithiumDemand(int size) {
        if (size < 6) return 1;
        if (size < 8) return 2;
        if (size < 10) return 3;
        return 4; // 10+
    }

    // -----------------------------------------------------------------
    // IMPROVEMENT SYSTEM (Up to 4 times)
    // -----------------------------------------------------------------
    @Override
    public boolean canImprove() {
        return improveLevel < 4;
    }

    public void improve() {
        if (canImprove()) {
            improveLevel++;
            setImproved(true);
        }
    }

    protected void applyImprovementModifiers() {
        if (improveLevel >= 1) {
            int demRed = (improveLevel >= 2) ? 2 : 1;
            demandReduction.modifyFlat(getModId() + "_imp_demand", demRed, getImprovementsDescForModifiers());
        }

        // Cumulative accessibility stacking: Tier 1 (+12.5%) + Tier 3 (+20.0%)
        float impAccess = 0f;
        if (improveLevel >= 1) impAccess += 0.125f;
        if (improveLevel >= 3) impAccess += 0.200f;

        if (impAccess > 0f) {
            market.getAccessibilityMod().modifyFlat(
                    getModId() + "_imp_access", impAccess, getImprovementsDescForModifiers() + " (" + getNameForModifier() + ")"
            );
        }
    }

    @Override
    public void addImproveDesc(TooltipMakerAPI info, ImprovementDescriptionMode mode) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();

        if (mode == ImprovementDescriptionMode.INDUSTRY_TOOLTIP) {
            info.addPara("Current improvement tier: %s / 4", 0f, highlight, "" + improveLevel);
            if (improveLevel >= 1) info.addPara("• Tier 1: Reduced demand by 1 unit and +12.5%% accessibility.", 0f, highlight);
            if (improveLevel >= 2) info.addPara("• Tier 2: Reduced demand by an additional 1 unit.", 0f, highlight);
            if (improveLevel >= 3) info.addPara("• Tier 3: Accessibility increased by an additional +20%%.", 0f, highlight);
            if (improveLevel >= 4) info.addPara("• Tier 4: Base supply increased by +1 unit.", 0f, highlight);
        } else {
            info.addPara("Can be improved up to 4 times (Demand reduction, Accessibility, Supply). Current level: %s / 4.", 0f, highlight, "" + improveLevel);
        }

        info.addSpacer(opad);
        super.addImproveDesc(info, mode);
    }

    // -----------------------------------------------------------------
    // OMEGA CORE SUPPORT & DESCRIPTION
    // -----------------------------------------------------------------
    protected void applyOmegaCoreModifiers() {
        demandReduction.modifyFlat(getModId() + "_omega_demand", 2, "Omega core");
        getUpkeep().modifyMult(getModId() + "_omega_upkeep", 0.75f, "Omega core"); // -25% upkeep
        market.getAccessibilityMod().modifyFlat(getModId() + "_omega_access", 0.25f, "Omega core"); // +25% accessibility
        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlat(getModId() + "_omega_quality", 0.04f, "Omega core"); // +4% ship quality
        market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).modifyPercent(getModId() + "_omega_defenses", 7.5f, "Omega core"); // +7.5% ground defenses
        market.getHazard().modifyFlat(getModId() + "_omega_hazard", -0.25f, "Omega core"); // -25% hazard rating
    }

    protected void addAICoreDescription(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        if (DimensionsCrossedIDS.OMEGA_CORE.equals(aiCoreId)) {
            float opad = 10f;
            Color highlight = Misc.getHighlightColor();

            String pre = (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP)
                    ? "Omega-level AI core. " : "Omega-level AI core currently assigned. ";

            String descText = pre + "Reduces demand by %s, increases supply by %s, reduces upkeep by %s, " +
                    "increases accessibility by %s, provides +%s ship bonus, " +
                    "+%s ground defenses, and reduces hazard rating by %s.";

            if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
                CommoditySpecAPI coreSpec = Global.getSettings().getCommoditySpec(aiCoreId);
                TooltipMakerAPI text = tooltip.beginImageWithText(coreSpec.getIconName(), 48);
                text.addPara(descText, 0f, highlight, "2 units", "2 units", "25%", "25%", "4%", "7.5%", "25%");
                tooltip.addImageWithText(opad);
            } else {
                tooltip.addPara(descText, opad, highlight, "2 units", "2 units", "25%", "25%", "4%", "7.5%", "25%");
            }
            return;
        }

        super.addAICoreSection(tooltip, mode);
    }
}