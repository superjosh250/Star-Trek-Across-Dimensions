package UFP.data.campaign.industry;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.GenericInstallableItemPlugin;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class SocietyandInfanstructure extends PopulationAndInfrastructure implements MarketImmigrationModifier {

    private boolean initializedStockpile = false;

    // --- Supply / Demand Math Tables ---
    private int getCustomSupplyUnit(int marketSize) {
        return switch (marketSize) {
            case 3 -> 3;
            case 4, 5 -> 4;
            case 6 -> 5;
            case 7, 8 -> 6;
            case 9 -> 7;
            default -> (marketSize >= 10) ? 8 : Math.max(1, marketSize);
        };
    }

    private int getCustomDemandUnit(int marketSize) {
        return switch (marketSize) {
            case 3, 4, 5 -> 3;
            case 6, 7, 8 -> 4;
            case 9 -> 5;
            default -> (marketSize >= 10) ? 6 : Math.max(1, marketSize);
        };
    }

    // --- Dynamic Max Industry Slot Scaling ---
    public static int getMaxIndustriesForSize(int size) {
        if (size <= 3) return 1;
        if (size >= 10) return 8;
        return size - 2;
    }

    @Override
    public int getMaxIndustries() {
        return getMaxIndustriesForSize(market.getSize());
    }

    @Override
    public void apply() {
        modifyStability(this, market, getModId() + "_stab_1");

        super.apply(true);

        int size = market.getSize();
        int supplyUnit = getCustomSupplyUnit(size);
        int demandUnit = getCustomDemandUnit(size);

        // --- HAZARD RATING REDUCTION (-25%) ---
        // Fixed: String suffix avoids index limits entirely
        market.getHazard().modifyFlat(getModId() + "_hazard", -0.25f, getNameForModifier());

        // --- DEMAND SETUP ---
        demand(Commodities.FOOD, demandUnit);
        demand(Commodities.SUPPLIES, demandUnit);
        demand(Commodities.LUXURY_GOODS, 0);
        demand(Commodities.DOMESTIC_GOODS, 0);

        // --- AGGREGATE MODIFIERS ---
        int bonusSupply = 0;

        // AI Core Modifications
        if (aiCoreId != null) {
            switch (aiCoreId) {
                case Commodities.GAMMA_CORE ->
                        market.getAccessibilityMod().modifyFlat(getModId() + "_ai_acc", 0.05f, "Gamma Core");
                case Commodities.ALPHA_CORE -> {
                    bonusSupply += 1;
                    market.getAccessibilityMod().modifyFlat(getModId() + "_ai_acc", 0.05f, "Alpha Core");
                }
                case Commodities.OMEGA_CORE -> {
                    bonusSupply += 2;
                    market.getAccessibilityMod().modifyFlat(getModId() + "_ai_acc", 0.05f, "Omega Core");
                }
            }
        }

        // Special Item Modifications (Dealmaker Holosuite)
        if (special != null && Items.DEALMAKER_HOLOSUITE.equals(special.getId())) {
            bonusSupply += 1;
            market.getAccessibilityMod().modifyFlat(getModId() + "_holosuite_acc", 0.25f, "Dealmaker Holosuite");
            market.getStability().modifyFlat(getModId() + "_holosuite_stab", 1f, "Dealmaker Holosuite");
        }

        // --- CONSOLIDATED SUPPLY APPLY ---
        String[] standardSupplies = {
                Commodities.CREW,
                DimensionsCrossedIDS.CIVILIAN,
                DimensionsCrossedIDS.CADETS,
                Commodities.SUPPLIES
        };
        int finalSupplyValue = supplyUnit + bonusSupply;
        for (String commodity : standardSupplies) {
            supply(commodity, finalSupplyValue);
        }

        // --- OFFICER & ADMIN PROBABILITIES ---
        float totalProb = 0.75f + (0.025f * Math.max(0, size - 3));
        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).modifyFlat(getModId() + "_prob", totalProb);
        market.getStats().getDynamic().getMod(Stats.ADMIN_PROB_MOD).modifyFlat(getModId() + "_prob", totalProb);

        // --- STABILITY & INDUSTRY LIMITS ---
        market.getStability().modifyFlat(getModId() + "_base_stab", 2f, "Base colony stability");

        int maxIndustries = getMaxIndustries();

        market.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES)
                .modifyFlat(getModId() + "_max_ind", maxIndustries, getNameForModifier());

        int numIndustries = Misc.getNumIndustries(market);
        market.getStability().unmodifyFlat("max_industries");

        if (numIndustries > maxIndustries) {
            int over = numIndustries - maxIndustries;
            market.getStability().modifyFlat(
                    getModId() + "_over_ind_penalty",
                    -5f * over,
                    "Industry limit exceeded (" + numIndustries + "/" + maxIndustries + ")"
            );
        } else {
            market.getStability().unmodifyFlat(getModId() + "_over_ind_penalty");
        }

        // --- STOCKPILE SETUP ---
        if (!initializedStockpile && market.isPlayerOwned()) {
            setupInitialStockpile();
            initializedStockpile = true;
        }

        modifyStability2(this, market, getModId() + "_stab_1");
        market.addTransientImmigrationModifier(this);
    }

    @Override
    public void unapply() {
        super.unapply();

        market.getHazard().unmodifyFlat(getModId() + "_hazard");
        market.getAccessibilityMod().unmodifyFlat(getModId() + "_ai_acc");
        market.getAccessibilityMod().unmodifyFlat(getModId() + "_holosuite_acc");
        market.getStability().unmodifyFlat(getModId() + "_holosuite_stab");
        market.getStability().unmodifyFlat(getModId() + "_base_stab");
        market.getStability().unmodifyFlat(getModId() + "_over_ind_penalty");

        market.getStats().getDynamic().getMod(Stats.MAX_INDUSTRIES).unmodifyFlat(getModId() + "_max_ind");

        market.getStats().getDynamic().getMod(Stats.OFFICER_PROB_MOD).unmodifyFlat(getModId() + "_prob");
        market.getStats().getDynamic().getMod(Stats.ADMIN_PROB_MOD).unmodifyFlat(getModId() + "_prob");
    }

    private void setupInitialStockpile() {
        SubmarketAPI stockpile = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
        if (stockpile != null) {
            int demandUnit = getCustomDemandUnit(market.getSize());
            int monthlyAmount = (int) Math.pow(10, demandUnit);

            stockpile.getCargo().addCommodity(Commodities.FOOD, monthlyAmount * 6);
            stockpile.getCargo().addCommodity(Commodities.SUPPLIES, monthlyAmount * 6);
        }
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        super.modifyIncoming(market, incoming);

        if (aiCoreId != null) {
            float bonus = switch (aiCoreId) {
                case Commodities.GAMMA_CORE, Commodities.BETA_CORE, Commodities.ALPHA_CORE -> 1f;
                case Commodities.OMEGA_CORE -> 2.5f;
                default -> 0f;
            };

            if (bonus > 0f) {
                incoming.getWeight().modifyFlat(getModId() + "_ai_growth", bonus, "AI Core management");
            }
        }
    }

    @Override
    public boolean isBuilding() {
        if (building && market.getSize() < 10) {
            return true;
        }
        return super.isBuilding();
    }

    @Override
    public boolean isUpgrading() {
        return super.isUpgrading();
    }

    // --- INSTALLABLE ITEM REGISTRATION ---
    @Override
    public List<InstallableIndustryItemPlugin> getInstallableItems() {
        List<InstallableIndustryItemPlugin> list = new ArrayList<>();

        list.add(new GenericInstallableItemPlugin(this) {
            @Override
            public boolean canBeInstalled(SpecialItemData data) {
                if (data != null && Items.DEALMAKER_HOLOSUITE.equals(data.getId())) {
                    return true;
                }
                return super.canBeInstalled(data);
            }

            @Override
            public boolean isInstallableItem(CargoStackAPI stack) {
                if (stack != null && stack.isSpecialStack()) {
                    SpecialItemData data = stack.getSpecialDataIfSpecial();
                    if (data != null && Items.DEALMAKER_HOLOSUITE.equals(data.getId())) {
                        return true;
                    }
                }
                return super.isInstallableItem(stack);
            }
        });

        return list;
    }

    // --- SPECIAL ITEM VALIDATION ---
    @Override
    public boolean wantsToUseSpecialItem(SpecialItemData data) {
        if (data == null) return false;
        if (special != null && special.getId().equals(data.getId())) return false;
        if (Items.DEALMAKER_HOLOSUITE.equals(data.getId())) return true;
        return super.wantsToUseSpecialItem(data);
    }

    // --- TOOLTIPS WITH AI CORE ICON ---
    @Override
    public void addAICoreSection(TooltipMakerAPI tooltip, String coreId, AICoreDescriptionMode mode) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();

        if (coreId == null) {
            super.addAICoreSection(tooltip, coreId, mode);
            return;
        }

        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(coreId);
        if (spec == null) {
            super.addAICoreSection(tooltip, coreId, mode);
            return;
        }

        TooltipMakerAPI text = tooltip.beginImageWithText(spec.getIconName(), 48f);

        switch (coreId) {
            case Commodities.GAMMA_CORE ->
                    text.addPara("Gamma Core assigned. Reduces demand by %s, increases population growth rate by %s, and accessibility by %s.", 0f, h, "1 unit", "1%", "5%");
            case Commodities.BETA_CORE ->
                    text.addPara("Beta Core assigned. Reduces demand by %s and increases population growth rate by %s.", 0f, h, "1 unit", "1%");
            case Commodities.ALPHA_CORE ->
                    text.addPara("Alpha Core assigned. Reduces demand by %s, increases supply by %s, population growth rate by %s, and accessibility by %s.", 0f, h, "1 unit", "1 unit", "1%", "5%");
            case Commodities.OMEGA_CORE ->
                    text.addPara("Omega Core assigned. Reduces demand by %s, increases supply by %s, population growth rate by %s, and accessibility by %s.", 0f, h, "2 units", "2 units", "2.5%", "5%");
            default -> {
                super.addAICoreSection(tooltip, coreId, mode);
                return;
            }
        }

        tooltip.addImageWithText(opad);
    }

    // --- TOOLTIPS WITH SPECIAL ITEM ICON ---
    @Override
    protected boolean addNonAICoreInstalledItems(IndustryTooltipMode mode, TooltipMakerAPI tooltip, boolean expanded) {
        if (special != null && Items.DEALMAKER_HOLOSUITE.equals(special.getId())) {
            float opad = 10f;
            Color h = Misc.getHighlightColor();

            SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(special.getId());
            if (spec != null) {
                TooltipMakerAPI text = tooltip.beginImageWithText(spec.getIconName(), 48f);
                text.addPara("Installed item: %s", 0f, h, spec.getName());
                text.addPara("Increases supply by %s, accessibility by %s, and stability by %s.",
                        2f, h, "1 unit", "25%", "+1");
                tooltip.addImageWithText(opad);
                return true;
            }
        }
        return super.addNonAICoreInstalledItems(mode, tooltip, expanded);
    }
}