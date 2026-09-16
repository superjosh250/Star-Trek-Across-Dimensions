package UFP.data.campaign.industry;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.GenericInstallableItemPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.HeavyIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

public class Drydock extends HeavyIndustry {

    protected int improvementLevel = 0;

    private int getCustomSupplyUnit(int marketSize) {
        return switch (marketSize) {
            case 3 -> 3;
            case 4, 5 -> 4;
            case 6 -> 5;
            default -> (marketSize >= 7) ? 6 : Math.max(1, marketSize);
        };
    }

    private int getCustomDemandUnit(int marketSize) {
        return switch (marketSize) {
            case 3, 4, 5 -> 3;
            case 6, 7, 8 -> 4;
            default -> (marketSize >= 9) ? 5 : Math.max(1, marketSize);
        };
    }

    @Override
    public void apply() {
        super.apply();

        if (!isFunctional()) return;

        int size = market.getSize();
        int baseSupply = getCustomSupplyUnit(size);
        int baseDemand = getCustomDemandUnit(size);

        int demandReduction = 0;
        int bonusSupply = 0;
        int shipsSupplyExtra = 0;
        int shipBonus = 2;
        float shipQuality = 0.75f;
        float accessibility = 0.125f;

        market.getHazard().modifyFlat(getModId() + "_hazard", -0.05f, getNameForModifier());

        // --- FLEET SIZE MULTIPLIER ---
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT)
                .modifyMult(getModId() + "_fleet_size", 1.75f, getNameForModifier());

        // --- SPECIAL ITEMS ---
        if (special != null) {
            String id = special.getId();
            boolean isCorrupted = Items.CORRUPTED_NANOFORGE.equals(id);
            boolean isPristine = Items.PRISTINE_NANOFORGE.equals(id);

            if (isCorrupted || isPristine) {
                bonusSupply += isPristine ? 2 : 1;
                shipQuality += isPristine ? 0.35f : 0.25f;
                shipBonus += isPristine ? 4 : 2;
            }
        }

        // --- AI CORES ---
        if (aiCoreId != null) {
            demandReduction += 1;

            switch (aiCoreId) {
                case Commodities.GAMMA_CORE ->
                        getUpkeep().modifyMult(getModId() + "_core", 0.90f, "Gamma Core (-10% Upkeep)");
                case Commodities.ALPHA_CORE -> {
                    bonusSupply += 1;
                    getUpkeep().modifyMult(getModId() + "_core", 0.75f, "Alpha Core (-25% Upkeep)");
                }
                case Commodities.OMEGA_CORE -> {
                    bonusSupply += 2;
                    getUpkeep().modifyMult(getModId() + "_core", 0.75f, "Omega Core (-25% Upkeep)");
                    market.getStability().modifyFlat(getModId() + "_core", 1f, "Omega Core (+1 Stability)");
                }
            }
        }

        // --- IMPROVEMENT TIERS ---
        if (improvementLevel >= 2) accessibility += 0.025f;
        if (improvementLevel >= 3) shipsSupplyExtra += 1;
        if (improvementLevel >= 4) {
            shipBonus += 1;
            bonusSupply += 1;
            getUpkeep().modifyMult(getModId() + "_imp4", 0.975f, "Drydock Tier IV (-2.5% Upkeep)");
        }

        // --- APPLY STAT MODIFIERS ---
        market.getAccessibilityMod().modifyFlat(getModId() + "_access", accessibility, getNameForModifier());
        market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).modifyFlat(getModId() + "_quality", shipQuality, getNameForModifier());

        // --- DEMANDS ---
        int finalMetalsDemand = Math.max(0, baseDemand - demandReduction);
        int finalDilithiumDemand = Math.max(0, baseSupply - demandReduction);

        demand(Commodities.METALS, finalMetalsDemand);
        demand(Commodities.RARE_METALS, finalMetalsDemand);
        demand(DimensionsCrossedIDS.DILITHIUM, finalDilithiumDemand);

        // --- DEFICIT HANDLING & PRODUCTION MATH ---
        Pair<String, Integer> maxDeficit = getMaxDeficit(
                Commodities.METALS,
                Commodities.RARE_METALS,
                DimensionsCrossedIDS.DILITHIUM
        );
        int deficit = (maxDeficit != null) ? maxDeficit.two : 0;

        int generalSupply = Math.max(1, baseSupply + bonusSupply - deficit);
        int shipsSupply = Math.max(1, baseSupply + bonusSupply + shipsSupplyExtra - deficit);

        // --- SUPPLIES ---
        supply(Commodities.SUPPLIES, generalSupply);
        supply(Commodities.HEAVY_MACHINERY, generalSupply);
        supply(Commodities.HAND_WEAPONS, generalSupply);
        supply(Commodities.SHIPS, shipsSupply);

        if (shipBonus > 0) {
            supply(1, Commodities.SHIPS, shipBonus, getNameForModifier());
        }
    }

    @Override
    public void unapply() {
        super.unapply();
        market.getHazard().unmodifyFlat(getModId() + "_hazard");
        market.getAccessibilityMod().unmodifyFlat(getModId() + "_access");
        market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).unmodifyFlat(getModId() + "_quality");
        market.getStability().unmodifyFlat(getModId() + "_core");

        getUpkeep().unmodifyMult(getModId() + "_core");
        getUpkeep().unmodifyMult(getModId() + "_imp4");

        // Clean up fleet size modifier
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodifyMult(getModId() + "_fleet_size");

        // Clean up potential legacy fleet size modifiers from saved states
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(getModId());
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(getModId() + "_imp4");
        market.getStats().getDynamic().getMod(Stats.COMBAT_FLEET_SIZE_MULT).unmodify(getModId() + "_core");
    }

    @Override
    public List<InstallableIndustryItemPlugin> getInstallableItems() {
        List<InstallableIndustryItemPlugin> list = new ArrayList<>();
        List<InstallableIndustryItemPlugin> superList = super.getInstallableItems();
        if (superList != null) list.addAll(superList);
        list.add(new GenericInstallableItemPlugin(this));
        return list;
    }

    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        if (data == null) return false;
        if (special != null && special.getId().equals(data.getId())) return false;

        String id = data.getId();
        if (Items.CORRUPTED_NANOFORGE.equals(id) || Items.PRISTINE_NANOFORGE.equals(id)) {
            return true;
        }

        return super.wantsToUseSpecialItem(data);
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

    @Override
    public void addImproveDesc(TooltipMakerAPI info, ImprovementDescriptionMode mode) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();

        info.addPara("Level 2: Increases accessibility by %s.", 0f, highlight, "+2.5%");
        info.addPara("Level 3: Increases ship commodity production by %s.", 0f, highlight, "+1 unit");
        info.addPara("Level 4: +1 flat ship production and reduces upkeep by %s.", 0f, highlight, "2.5%");

        info.addSpacer(opad);
        super.addImproveDesc(info, mode);
    }

    @Override
    public void addAICoreSection(TooltipMakerAPI tooltip, String coreId, AICoreDescriptionMode mode) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(coreId);
        if (spec == null) {
            super.addAICoreSection(tooltip, coreId, mode);
            return;
        }

        TooltipMakerAPI text = tooltip.beginImageWithText(spec.getIconName(), 48f);

        switch (coreId) {
            case Commodities.BETA_CORE ->
                    text.addPara("Beta Core assigned. Reduces demand by %s.", 0f, h, "1 unit");
            case Commodities.GAMMA_CORE ->
                    text.addPara("Gamma Core assigned. Reduces demand by %s and upkeep by %s.", 0f, h, "1 unit", "10%");
            case Commodities.ALPHA_CORE ->
                    text.addPara("Alpha Core assigned. Reduces demand by %s, increases supply by %s, and reduces upkeep by %s.", 0f, h, "1 unit", "1 unit", "25%");
            case Commodities.OMEGA_CORE ->
                    text.addPara("Omega Core assigned. Reduces demand by %s, increases supply by %s, reduces upkeep by %s, and adds %s stability.", 0f, h, "1 unit", "2 units", "25%", "+1");
            default -> {
                super.addAICoreSection(tooltip, coreId, mode);
                return;
            }
        }

        tooltip.addImageWithText(opad);
    }

    @Override
    protected boolean addNonAICoreInstalledItems(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        if (special != null) {
            float opad = 10f;
            Color h = Misc.getHighlightColor();
            String id = special.getId();

            SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(id);
            if (spec != null) {
                TooltipMakerAPI text = tooltip.beginImageWithText(spec.getIconName(), 48f);

                if (Items.CORRUPTED_NANOFORGE.equals(id)) {
                    text.addPara("Installed item: %s", 0f, h, spec.getName());
                    text.addPara("Increases supply by %s, ship quality by %s, and ship bonus by %s.",
                            2f, h, "1 unit", "25%", "+2");
                    tooltip.addImageWithText(opad);
                    return true;
                } else if (Items.PRISTINE_NANOFORGE.equals(id)) {
                    text.addPara("Installed item: %s", 0f, h, spec.getName());
                    text.addPara("Increases supply by %s, ship quality by %s, and ship bonus by %s.",
                            2f, h, "2 units", "35%", "+4");
                    tooltip.addImageWithText(opad);
                    return true;
                }
            }
        }
        return super.addNonAICoreInstalledItems(mode, tooltip, expanded);
    }
}