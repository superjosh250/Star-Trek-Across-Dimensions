package BORG.data.campaign.industry;

import java.awt.Color;

import BORG.data.campaign.ids.BorgIDS;
import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.econ.impl.HeavyIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

public class CollectiveShipyard extends HeavyIndustry {

    @Override
    public void apply() {
        super.apply();

        if (market == null) return;
        int size = market.getSize();
        int stability = (int) market.getStability().getModifiedValue();
        String coreId = getAICoreId();

        // -----------------------------------------------------------------
        // 1. BASE STATS & STABILITY BONUSES
        // -----------------------------------------------------------------
        market.getStability().modifyFlat(getModId(0), 1f, "Collective Shipyard Infrastructure");

        float shipQualityBonus = 1.0f;
        float upkeepMult = 1.0f;

        // Stability-Based Tiered Scaling
        if (stability >= 10) {
            upkeepMult -= 0.25f;
            shipQualityBonus += 0.75f;
        } else if (stability >= 8) {
            upkeepMult -= 0.125f;
            shipQualityBonus += 0.125f;
        } else if (stability >= 7) {
            upkeepMult -= 0.10f;
            shipQualityBonus += 0.075f;
        } else if (stability >= 5) {
            upkeepMult -= 0.08f;
            shipQualityBonus += 0.05f;
        }

        if (upkeepMult < 1.0f) {
            getUpkeep().modifyMult(getModId(1), upkeepMult, "Stability Efficiency Bonus");
        } else {
            getUpkeep().unmodifyMult(getModId(1));
        }

        // -----------------------------------------------------------------
        // 2. DEMAND CALCULATIONS
        // -----------------------------------------------------------------
        int baseDemand = getBaseDemandForSize(size);

        if (getImproveProductionBonus() >= 2) {
            baseDemand = Math.max(0, baseDemand - 1);
        }

        if (Commodities.BETA_CORE.equals(coreId) ||
                Commodities.GAMMA_CORE.equals(coreId) ||
                Commodities.ALPHA_CORE.equals(coreId) ||
                Commodities.OMEGA_CORE.equals(coreId)) {
            baseDemand = Math.max(0, baseDemand - 1);
        }

        demand(Commodities.METALS, baseDemand);
        demand(Commodities.RARE_METALS, baseDemand);
        demand(Commodities.ORGANICS, baseDemand);

        int dilithiumDemand = getDilithiumDemandForSize(size);
        demand(DimensionsCrossedIDS.DILITHIUM, dilithiumDemand);

        // -----------------------------------------------------------------
        // 3. SUPPLY CALCULATIONS
        // -----------------------------------------------------------------
        int baseSupply = getBaseSupplyForSize(size);

        if (getImproveProductionBonus() >= 1) {
            baseSupply += 1;
        }

        if (Commodities.ALPHA_CORE.equals(coreId)) {
            baseSupply += 1;
        } else if (Commodities.OMEGA_CORE.equals(coreId)) {
            baseSupply += 2;
        }

        if (special != null) {
            String itemId = special.getId();
            if (Items.BIOFACTORY_EMBRYO.equals(itemId)) {
                baseSupply += 2;
            } else if (Items.CORRUPTED_NANOFORGE.equals(itemId)) {
                baseSupply += 1;
            } else if (Items.PRISTINE_NANOFORGE.equals(itemId)) {
                baseSupply += 2;
            }
        }

        supply(Commodities.SUPPLIES, baseSupply);
        supply(Commodities.HEAVY_MACHINERY, baseSupply);

        int shipsSupply = baseSupply;
        if (getImproveProductionBonus() >= 4) {
            shipsSupply += 1;
        }
        if (special != null && Items.PRISTINE_NANOFORGE.equals(special.getId())) {
            shipsSupply += 1;
        }
        supply(Commodities.SHIPS, shipsSupply);

        // -----------------------------------------------------------------
        // 4. CYBERNETICS CONVERSION
        // -----------------------------------------------------------------
        int metalsAvail = market.getCommodityData(Commodities.METALS).getAvailable();
        int organicsAvail = market.getCommodityData(Commodities.ORGANICS).getAvailable();

        if (metalsAvail > 0 && organicsAvail > 0) {
            int maxPossibleCybernetics = Math.min(metalsAvail, organicsAvail) + 1;
            supply(BorgIDS.CYBERNETICS, Math.min(baseSupply + 1, maxPossibleCybernetics));
        } else {
            supply(BorgIDS.CYBERNETICS, 0);
        }

        // -----------------------------------------------------------------
        // 5. SPECIAL ITEMS, IMPROVEMENTS & AI CORES STAT MODIFIERS
        // -----------------------------------------------------------------

        // Improvement Tier 1 (+5% Combat Fleet Size)
        if (getImproveProductionBonus() >= 1) {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                    .modifyMult(getModId(4), 1.05f, "Collective Shipyard Tier 1 Improvement");
        } else {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId(4));
        }

        // Special Items Combat Fleet Size Multipliers
        if (special != null) {
            String itemId = special.getId();
            if (Items.BIOFACTORY_EMBRYO.equals(itemId)) {
                market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyMult(getModId(6), 1.15f, "Biofactory Embryo");
                shipQualityBonus += 0.25f;
            } else if (Items.CORRUPTED_NANOFORGE.equals(itemId)) {
                market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyMult(getModId(6), 1.25f, "Corrupted Nanoforge");
                shipQualityBonus += 0.50f;
            } else if (Items.PRISTINE_NANOFORGE.equals(itemId)) {
                market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyMult(getModId(6), 1.50f, "Pristine Nanoforge");
                shipQualityBonus += 0.75f;
            } else {
                market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId(6));
            }
        } else {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId(6));
        }

        // AI Core Fleet Size Multipliers
        if (Commodities.ALPHA_CORE.equals(coreId)) {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyMult(getModId(7), 1.15f, "Alpha Core Directives");
        } else if (Commodities.OMEGA_CORE.equals(coreId)) {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).modifyMult(getModId(7), 1.25f, "Omega Core Directives");
            shipQualityBonus += 0.25f;
        } else {
            market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId(7));
        }

        // Improvement Tier 3 (+12.5% Accessibility)
        if (getImproveProductionBonus() >= 3) {
            market.getAccessibilityMod().modifyFlat(getModId(2), 0.125f, "Collective Shipyard Tier 3");
        } else {
            market.getAccessibilityMod().unmodifyFlat(getModId(2));
        }

        // Improvement Tier 4 (-10% Hazard Rating)
        if (getImproveProductionBonus() >= 4) {
            market.getHazard().modifyFlat(getModId(3), -0.10f, "Collective Shipyard Tier 4 Optimization");
        } else {
            market.getHazard().unmodifyFlat(getModId(3));
        }

        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD)
                .modifyFlat(getModId(8), shipQualityBonus, "Collective Shipyard Manufacturing");
    }

    @Override
    public void unapply() {
        super.unapply();

        if (market == null) return;

        market.getStability().unmodifyFlat(getModId(0));
        getUpkeep().unmodifyMult(getModId(1));
        market.getAccessibilityMod().unmodifyFlat(getModId(2));
        market.getHazard().unmodifyFlat(getModId(3));

        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId(4));
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId(6));
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId(7));

        market.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).unmodifyFlat(getModId(8));
    }

    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        if (data == null) return false;
        String id = data.getId();
        return Items.BIOFACTORY_EMBRYO.equals(id) ||
                Items.CORRUPTED_NANOFORGE.equals(id) ||
                Items.PRISTINE_NANOFORGE.equals(id) ||
                super.wantsToUseSpecialItem(data);
    }

    @Override
    public void addAICoreSection(TooltipMakerAPI tooltip, AICoreDescriptionMode mode) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        String coreId = getAICoreId();

        if (coreId == null) return;

        if (mode == AICoreDescriptionMode.INDUSTRY_TOOLTIP) {
            if (Commodities.GAMMA_CORE.equals(coreId)) {
                tooltip.addPara("Gamma Core assigned: -1 demand.", opad, h, "-1");
            } else if (Commodities.ALPHA_CORE.equals(coreId)) {
                tooltip.addPara("Alpha Core assigned: -1 demand, +1 supply, x1.15 combat fleet size.", opad, h, "-1", "+1", "x1.15");
            } else if (Commodities.OMEGA_CORE.equals(coreId)) {
                tooltip.addPara("Omega Core assigned: -1 demand, +2 supply, x1.25 combat fleet size, +25%% ship quality.", opad, h, "-1", "+2", "x1.25", "+25%");
            } else if (Commodities.BETA_CORE.equals(coreId)) {
                tooltip.addPara("Beta Core assigned: -1 demand.", opad, h, "-1");
            }
        } else if (mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_LIST || mode == AICoreDescriptionMode.MANAGE_CORE_DIALOG_INSTALLED) {
            if (Commodities.GAMMA_CORE.equals(coreId)) {
                tooltip.addPara("Reduces commodity demand by 1 unit.", opad, h, "1");
            } else if (Commodities.ALPHA_CORE.equals(coreId)) {
                tooltip.addPara("Reduces demand by 1 unit, increases supply by 1 unit, and increases combat fleet size by x1.15.", opad, h, "1", "1", "x1.15");
            } else if (Commodities.OMEGA_CORE.equals(coreId)) {
                tooltip.addPara("Reduces demand by 1 unit, increases supply by 2 units, increases combat fleet size by x1.25, and ship quality by 25%%.", opad, h, "1", "2", "x1.25", "25%");
            } else if (Commodities.BETA_CORE.equals(coreId)) {
                tooltip.addPara("Reduces commodity demand by 1 unit.", opad, h, "1");
            }
        } else if (mode == AICoreDescriptionMode.MANAGE_CORE_TOOLTIP) {
            if (Commodities.GAMMA_CORE.equals(coreId)) {
                tooltip.addPara("Gamma Core: -1 demand.", opad, h, "-1");
            } else if (Commodities.ALPHA_CORE.equals(coreId)) {
                tooltip.addPara("Alpha Core: -1 demand, +1 supply, x1.15 combat fleet size.", opad, h, "-1", "+1", "x1.15");
            } else if (Commodities.OMEGA_CORE.equals(coreId)) {
                tooltip.addPara("Omega Core: -1 demand, +2 supply, x1.25 combat fleet size, +25%% ship quality.", opad, h, "-1", "+2", "x1.25", "+25%");
            } else if (Commodities.BETA_CORE.equals(coreId)) {
                tooltip.addPara("Beta Core: -1 demand.", opad, h, "-1");
            }
        }
    }

    @Override
    protected void addPostDemandSection(TooltipMakerAPI tooltip, boolean hasDemand, IndustryTooltipMode mode) {
        super.addPostDemandSection(tooltip, hasDemand, mode);

        float opad = 10f;
        Color h = Misc.getHighlightColor();

        if (special != null) {
            String itemId = special.getId();
            if (Items.BIOFACTORY_EMBRYO.equals(itemId)) {
                tooltip.addPara("Biofactory Embryo installed: +2 production, x1.15 combat fleet size, and +25%% ship quality.", opad, h, "+2", "x1.15", "+25%");
            } else if (Items.CORRUPTED_NANOFORGE.equals(itemId)) {
                tooltip.addPara("Corrupted Nanoforge installed: +1 production, x1.25 combat fleet size, and +50%% ship quality.", opad, h, "+1", "x1.25", "+50%");
            } else if (Items.PRISTINE_NANOFORGE.equals(itemId)) {
                tooltip.addPara("Pristine Nanoforge installed: +2 production, +1 Ships supply, x1.50 combat fleet size, and +75%% ship quality.", opad, h, "+2", "+1", "x1.50", "+75%");
            }
        }
    }

    private int getBaseDemandForSize(int size) {
        if (size <= 5) return 3;
        if (size <= 8) return 4;
        if (size == 9) return 5;
        return 6;
    }

    private int getBaseSupplyForSize(int size) {
        if (size <= 3) return 3;
        if (size <= 5) return 4;
        if (size <= 6) return 5;
        if (size <= 8) return 6;
        if (size == 9) return 7;
        return 8;
    }

    private int getDilithiumDemandForSize(int size) {
        if (size <= 4) return 1;
        if (size <= 8) return 2;
        if (size == 9) return 3;
        return 4;
    }
}