package BORG.data.campaign.industry;

import java.awt.Color;

import BORG.data.campaign.ids.BorgIDS;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class TechnologyAssembler extends BaseIndustry {

    protected float daysElapsed = 0f;

    @Override
    public void apply() {
        super.apply(true);

        if (market == null) return;
        int size = market.getSize();

        // -----------------------------------------------------------------
        // 1. AI CORE FUNCTIONALITY REQUIREMENT
        // -----------------------------------------------------------------
        String coreId = getAICoreId();
        boolean hasValidCore = Commodities.ALPHA_CORE.equals(coreId) ||
                Commodities.BETA_CORE.equals(coreId) ||
                Commodities.GAMMA_CORE.equals(coreId) ||
                Commodities.OMEGA_CORE.equals(coreId);

        if (!hasValidCore) {
            // Industry cannot function without a required AI Core installed
            supply.clear();
            unapply();
            return;
        }

        // -----------------------------------------------------------------
        // 2. IMPROVEMENT TIERS (MAX 4)
        // -----------------------------------------------------------------
        int demandReduction = 0;
        int extraSupply = 0;

        int improveTier = getImproveProductionBonus();
        if (improveTier >= 1) demandReduction += 1; // Tier 1: -1 Demand
        if (improveTier >= 2) demandReduction += 1; // Tier 2: -1 Demand
        if (improveTier >= 3) demandReduction += 1; // Tier 3: -1 Demand
        if (improveTier >= 4) {                      // Tier 4: +1 Supply, +1 Stability
            extraSupply += 1;
            market.getStability().modifyFlat(getModId(0), 1f, "Technology Assembler Improvements");
        } else {
            market.getStability().unmodifyFlat(getModId(0));
        }

        // -----------------------------------------------------------------
        // 3. AI CORES MODIFIERS
        // -----------------------------------------------------------------
        float extraAccessibility = 0f;
        if (Commodities.OMEGA_CORE.equals(coreId)) {
            demandReduction += 1;
            extraSupply += 2;
            market.getStability().modifyFlat(getModId(1), 1f, "Omega Core");
            extraAccessibility += 0.125f;
            getIncome().modifyMult(getModId(2), 1.025f, "Omega Core");
            getUpkeep().modifyMult(getModId(3), 0.70f, "Omega Core");
        } else {
            market.getStability().unmodifyFlat(getModId(1));
            getIncome().unmodifyMult(getModId(2));
            applyStandardAICoreModifiers(coreId);
        }

        // -----------------------------------------------------------------
        // 4. SPECIAL ITEMS
        // -----------------------------------------------------------------
        float hazardMod = 0f;
        if (special != null) {
            String itemId = special.getId();
            if (Items.CORRUPTED_NANOFORGE.equals(itemId)) {
                extraSupply += 1;
            } else if (Items.PRISTINE_NANOFORGE.equals(itemId)) {
                extraSupply += 3;
                hazardMod -= 0.25f;
            } else if (Items.BIOFACTORY_EMBRYO.equals(itemId)) {
                extraSupply += 1;
                demandReduction += 1;
                hazardMod += 0.07f;
            }
        }

        if (hazardMod != 0f) {
            market.getHazard().modifyFlat(getModId(4), hazardMod, "Technology Assembler Item");
        } else {
            market.getHazard().unmodifyFlat(getModId(4));
        }

        if (extraAccessibility > 0f) {
            market.getAccessibilityMod().modifyFlat(getModId(5), extraAccessibility, "Omega Core");
        } else {
            market.getAccessibilityMod().unmodifyFlat(getModId(5));
        }

        // -----------------------------------------------------------------
        // 5. DEMAND & SUPPLY CALCULATIONS
        // -----------------------------------------------------------------
        int baseDemand = getBaseDemandForSize(size);
        int baseSupply = getBaseSupplyForSize(size);

        int finalDemand = Math.max(0, baseDemand - demandReduction);
        int finalSupply = baseSupply + extraSupply;

        // Apply Demands
        demand(Commodities.METALS, finalDemand);
        demand(Commodities.RARE_METALS, finalDemand);
        demand(Commodities.ORGANICS, finalDemand);

        // Special Demand: At least 1 unit of Drones OR Assimilated Civilians
        demand(BorgIDS.DRONES, 1);
        demand(BorgIDS.ASSIMILATED_CIVILIANS, 1);

        // Supply Output
        supply(Commodities.AI_CORES, finalSupply);

        if (!isFunctional()) {
            supply.clear();
            unapply();
        }
    }

    private void applyStandardAICoreModifiers(String coreId) {
        if (Commodities.ALPHA_CORE.equals(coreId)) {
            getUpkeep().modifyMult(getModId(3), 0.75f, "Alpha Core");
        } else if (Commodities.BETA_CORE.equals(coreId)) {
            getUpkeep().modifyMult(getModId(3), 0.75f, "Beta Core");
        } else if (Commodities.GAMMA_CORE.equals(coreId)) {
            getUpkeep().modifyMult(getModId(3), 0.75f, "Gamma Core");
        } else {
            getUpkeep().unmodifyMult(getModId(3));
        }
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (market == null || !isFunctional() || isDisrupted()) return;

        // Advance monthly timer (30 days)
        float days = Global.getSector().getClock().convertToDays(amount);
        daysElapsed += days;

        if (daysElapsed >= 30f) {
            daysElapsed -= 30f;
            performSubmarketInjections();
        }
    }

    // -----------------------------------------------------------------
    // SUBMARKET INJECTION LOGIC
    // -----------------------------------------------------------------
    private void performSubmarketInjections() {
        // If basic demand is not met, AI Cores are not injected
        if (getMaxDeficit(Commodities.METALS).two > 0 ||
                getMaxDeficit(Commodities.RARE_METALS).two > 0 ||
                getMaxDeficit(Commodities.ORGANICS).two > 0) {
            return;
        }

        // Check special demand requirement (needs at least 1 unit of Drones OR Assimilated Civilians)
        int droneDeficit = getMaxDeficit(BorgIDS.DRONES).two;
        int civilianDeficit = getMaxDeficit(BorgIDS.ASSIMILATED_CIVILIANS).two;
        if (droneDeficit > 0 && civilianDeficit > 0) {
            return;
        }

        // 1. Inject into SUBMARKET_OPEN (3 Beta Cores every 30 days)
        SubmarketAPI openMarket = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
        if (openMarket != null && openMarket.getCargo() != null) {
            openMarket.getCargo().addCommodity(Commodities.BETA_CORE, 3);
        }

        // 2. Inject into GENERIC_MILITARY (1 of each core type every 30 days)
        SubmarketAPI militaryMarket = market.getSubmarket(Submarkets.GENERIC_MILITARY);
        if (militaryMarket != null && militaryMarket.getCargo() != null) {
            CargoAPI cargo = militaryMarket.getCargo();

            int militaryAmount = 1;
            // Biofactory Embryo adds +1 extra injection to GENERIC_MILITARY
            if (special != null && Items.BIOFACTORY_EMBRYO.equals(special.getId())) {
                militaryAmount += 1;
            }

            cargo.addCommodity(Commodities.GAMMA_CORE, militaryAmount);
            cargo.addCommodity(Commodities.BETA_CORE, militaryAmount);
            cargo.addCommodity(Commodities.ALPHA_CORE, militaryAmount);
            cargo.addCommodity(Commodities.OMEGA_CORE, militaryAmount);
        }
    }

    @Override
    public void unapply() {
        super.unapply();
        if (market == null) return;

        market.getStability().unmodifyFlat(getModId(0));
        market.getStability().unmodifyFlat(getModId(1));
        getIncome().unmodifyMult(getModId(2));
        getUpkeep().unmodifyMult(getModId(3));
        market.getHazard().unmodifyFlat(getModId(4));
        market.getAccessibilityMod().unmodifyFlat(getModId(5));
    }

    // -----------------------------------------------------------------
    // BUILD PREREQUISITE & FACTION CHECKS
    // -----------------------------------------------------------------
    @Override
    public boolean isAvailableToBuild() {
        if (market == null) return false;

        // Check Faction/Commission Requirement
        FactionAPI faction = market.getFaction();
        boolean isBorgFaction = faction != null && BorgIDS.BORG.equals(faction.getId());

        FactionAPI playerFaction = Global.getSector().getPlayerFaction();
        boolean isPlayerCommissionedByBorg = playerFaction != null && BorgIDS.BORG.equals(playerFaction.getId());

        if (!isBorgFaction && !isPlayerCommissionedByBorg) {
            return false;
        }

        // Check Resource Assembler Requirement
        return market.hasIndustry(BorgIDS.RESOURCE_ASSEMBLER) &&
                market.getIndustry(BorgIDS.RESOURCE_ASSEMBLER).isFunctional();
    }

    @Override
    public String getUnavailableReason() {
        FactionAPI faction = market != null ? market.getFaction() : null;
        boolean isBorgFaction = faction != null && BorgIDS.BORG.equals(faction.getId());
        FactionAPI playerFaction = Global.getSector().getPlayerFaction();
        boolean isPlayerCommissionedByBorg = playerFaction != null && BorgIDS.BORG.equals(playerFaction.getId());

        if (!isBorgFaction && !isPlayerCommissionedByBorg) {
            return "Can only be built by the Borg Collective or players commissioned by the Borg.";
        }

        if (market != null && (!market.hasIndustry(BorgIDS.RESOURCE_ASSEMBLER) || !market.getIndustry(BorgIDS.RESOURCE_ASSEMBLER).isFunctional())) {
            return "Requires a functional Resource Assembler installed on this market.";
        }

        return super.getUnavailableReason();
    }

    // -----------------------------------------------------------------
    // LOOKUP TABLES
    // -----------------------------------------------------------------
    private int getBaseDemandForSize(int size) {
        if (size <= 5) return 3;
        if (size <= 8) return 4;
        if (size == 9) return 5;
        return 6; // 10+
    }

    private int getBaseSupplyForSize(int size) {
        if (size <= 3) return 3;
        if (size <= 5) return 4;
        if (size == 6) return 5;
        if (size <= 8) return 6;
        if (size == 9) return 7;
        return 8; // 10+
    }

    // -----------------------------------------------------------------
    // SPECIAL ITEMS & TOOLTIPS
    // -----------------------------------------------------------------
    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        if (data == null) return false;
        String id = data.getId();
        return Items.CORRUPTED_NANOFORGE.equals(id) ||
                Items.PRISTINE_NANOFORGE.equals(id) ||
                Items.BIOFACTORY_EMBRYO.equals(id) ||
                super.wantsToUseSpecialItem(data);
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        super.addPostDemandSection(tooltip, hasDemand, mode);

        float opad = 10f;
        Color h = Misc.getHighlightColor();

        String coreId = getAICoreId();
        boolean hasValidCore = Commodities.ALPHA_CORE.equals(coreId) ||
                Commodities.BETA_CORE.equals(coreId) ||
                Commodities.GAMMA_CORE.equals(coreId) ||
                Commodities.OMEGA_CORE.equals(coreId);

        if (!hasValidCore) {
            tooltip.addPara("Requires an installed AI Core (Gamma, Beta, Alpha, or Omega) to operate.", Misc.getNegativeHighlightColor(), opad);
        }

        if (special != null) {
            String id = special.getId();
            if (Items.CORRUPTED_NANOFORGE.equals(id)) {
                tooltip.addPara("Corrupted Nanoforge installed: +1 Supply.", opad, h, "+1");
            } else if (Items.PRISTINE_NANOFORGE.equals(id)) {
                tooltip.addPara("Pristine Nanoforge installed: +3 Supply, -25%% Hazard Rating.", opad, h, "+3", "-25%");
            } else if (Items.BIOFACTORY_EMBRYO.equals(id)) {
                tooltip.addPara("Biofactory Embryo installed: +1 Supply, -1 Demand, +1 Military Submarket AI Core injection, +7%% Hazard Rating.", opad, h, "+1", "-1", "+1", "+7%");
            }
        }
    }
}