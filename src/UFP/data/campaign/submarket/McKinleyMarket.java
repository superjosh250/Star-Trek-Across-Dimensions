package UFP.data.campaign.submarket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.CampaignUIAPI.CoreUITradeMode;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.impl.campaign.submarkets.OpenMarketPlugin;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;

public class McKinleyMarket extends BaseSubmarketPlugin {

    public static final String REQUIRED_INDUSTRY_ID = "mcKinleyStation";

    public static final String SHIP_HULL_ID = "fed_galaxy";
    public static final String SHIP_VARIANT_ID = "fed_galaxy";
    public static final String NEBULA_HULL_ID = "fed_nebula";
    public static final String NEBULA_VARIANT_ID = "fed_nebula";


    public static final int SHIPS_PER_MONTH = 10;
    public static final int NEBULA_PER_MONTH = 5;

    public static final float PRICE_MULT = 2.45f;

    public static final float BLUEPRINT_ROTATION_DAYS = 15f;
    public static final int WEAPON_BLUEPRINTS_PER_ROTATION = 3;

    public static final String WEAPON_BP_SPECIAL_ID = "weapon_bp";

    public static final int HULLMOD_SPECS_PER_REFRESH = 6;
    public static final int HULLMOD_MAX_TIER = 4;

    private float sinceBPUpdate = 9999f;

    @Override
    public void init(SubmarketAPI submarket) {
        super.init(submarket);
        this.minSWUpdateInterval = 30f;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        float days = Global.getSector().getClock().convertToDays(amount);
        sinceBPUpdate += days;
    }

    private boolean hasRequiredIndustry() {
        if (market == null) return false;
        Industry ind = market.getIndustry(REQUIRED_INDUSTRY_ID);
        return ind != null && ind.isFunctional();
    }

    @Override
    public boolean isEnabled(CoreUIAPI ui) {
        if (ui == null) return false;
        if (ui.getTradeMode() == CoreUITradeMode.SNEAK) return false;
        return hasRequiredIndustry();
    }

    @Override
    public boolean isOpenMarket() {
        return true;
    }

    @Override
    public void updateCargoPrePlayerInteraction() {
        float seconds = Global.getSector().getClock().convertToSeconds(sinceLastCargoUpdate);
        addAndRemoveStockpiledResources(seconds, false, true, true);
        sinceLastCargoUpdate = 0f;

        if (sinceBPUpdate >= BLUEPRINT_ROTATION_DAYS) {
            sinceBPUpdate = 0f;
            refreshWeaponBlueprints();
        }

        // Monthly refresh for ships/weapons/fighters/hullmods like vanilla submarkets
        if (okToUpdateShipsAndWeapons()) {
            sinceSWUpdate = 0f;

            pruneWeapons(0f);

            int size = market.getSize();
            int weapons = 5 + Math.max(0, size - 1) + 5;
            int fighters = 1 + Math.max(0, (size - 3) / 2) + 2;

            addWeapons(weapons, weapons + 2, 0, market.getFactionId());
            addFighters(fighters, fighters + 2, 0, market.getFactionId());

            refreshHullmodSpecs();

            // Ships: only our fixed offering
            getCargo().getMothballedShips().clear();
            if (hasRequiredIndustry()) {
                addFixedShipsForSale();
                addFixedShipsForSale2();
            }
        }
        getCargo().sort();
    }

    // --- Ships ---
    private void addFixedShipsForSale() {
        float quality = Misc.getShipQuality(market, market.getFactionId());

        for (int i = 0; i < SHIPS_PER_MONTH; i++) {
            if (!tryAddShip(SHIP_VARIANT_ID, quality)) {
                tryAddShip(SHIP_HULL_ID + "_Hull", quality);
            }
        }
    }

    private void addFixedShipsForSale2() {
        float quality = Misc.getShipQuality(market, market.getFactionId());

        for (int i = 0; i < NEBULA_PER_MONTH; i++) {
            if (!tryAddShip(NEBULA_HULL_ID, quality)) {
                tryAddShip(NEBULA_VARIANT_ID + "_Hull", quality);
            }
        }
    }

    private boolean tryAddShip(String variantId, float quality) {
        try {
            addShip(variantId, false, quality);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    // --- Commodity rules (exclude drugs/organs) ---
    @Override
    public boolean shouldHaveCommodity(CommodityOnMarketAPI com) {
        String id = com.getId();
        if (Commodities.DRUGS.equals(id)) return false;
        if (Commodities.ORGANS.equals(id)) return false;
        return !market.isIllegal(com);
    }

    @Override
    public int getStockpileLimit(CommodityOnMarketAPI com) {
        // Reuse OpenMarket stockpile logic (stable, vanilla-like)
        float limit = OpenMarketPlugin.getBaseStockpileLimit(com);

        Random random = new Random(
                market.getId().hashCode()
                        + submarket.getSpecId().hashCode()
                        + Global.getSector().getClock().getMonth() * 170000
        );
        limit *= 0.9f + 0.2f * random.nextFloat();

        float sm = market.getStabilityValue() / 10f;
        limit *= (0.25f + 0.75f * sm);

        if (limit < 0) limit = 0;
        return (int) limit;
    }

    // --- Pricing: tariff + ×2.45 total ---
    @Override
    public float getTariff() {
        float baseTariff = market.getTariff().getModifiedValue();
        return (1f + baseTariff) * PRICE_MULT - 1f;
    }

    @Override public String getTariffTextOverride() { return "Tariff & markup"; }
    @Override public String getTariffValueOverride() { return Math.round(getTariff() * 100f) + "%"; }
    @Override public String getTotalTextOverride() { return "Total (incl. markup)"; }
    @Override public String getTotalValueOverride() { return "×" + PRICE_MULT; }

    @Override
    public PlayerEconomyImpactMode getPlayerEconomyImpactMode() {
        return PlayerEconomyImpactMode.PLAYER_SELL_ONLY;
    }

    @Override
    public boolean isMilitaryMarket() {
        return false;
    }

    // --- Weapon blueprint rotation (3 every 15 days) ---
    private void refreshWeaponBlueprints() {
        CargoAPI cargo = getCargo();
        if (cargo == null) return;

        // Remove old weapon blueprint stacks we previously stocked
        cargo.getStacksCopy().forEach(stack -> {
            if (stack != null && stack.isSpecialStack()) {
                SpecialItemData data = stack.getSpecialDataIfSpecial();
                if (data != null && WEAPON_BP_SPECIAL_ID.equals(data.getId())) {
                    cargo.removeStack(stack);
                }
            }
        });

        // Source: faction-known weapons
        FactionAPI faction = submarket.getFaction();
        if (faction == null) faction = market.getFaction();

        Set<String> knownWeapons = new HashSet<>();
        if (faction != null) knownWeapons.addAll(faction.getKnownWeapons());

        // Filter to weapons that actually exist & that the player doesn't already know
        List<String> candidates = new ArrayList<>();
        for (String weaponId : knownWeapons) {
            WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(weaponId);
            if (spec == null) continue;

            // Blueprint only matters if player doesn't already know it
            if (Global.getSector().getPlayerFaction().knowsWeapon(weaponId)) continue;

            candidates.add(weaponId);
        }

        if (candidates.isEmpty()) return;

        // Deterministic rotation seed based on 15-day window
        int year = Global.getSector().getClock().getCycle();
        int month = Global.getSector().getClock().getMonth();
        int day = Global.getSector().getClock().getDay();
        int halfMonthIndex = (day >= 16) ? 1 : 0;

        long seed = (market.getId().hashCode() * 31L)
                ^ (submarket.getSpecId().hashCode() * 131L)
                ^ (year * 1000L + month) * 2L
                ^ halfMonthIndex;

        Random r = new Random(seed);
        Collections.shuffle(candidates, r);

        int toAdd = Math.min(WEAPON_BLUEPRINTS_PER_ROTATION, candidates.size());
        for (int i = 0; i < toAdd; i++) {
            String weaponId = candidates.get(i);
            cargo.addItems(CargoItemType.SPECIAL, new SpecialItemData(WEAPON_BP_SPECIAL_ID, weaponId), 1);
        }
    }

    // --- Hullmod specs (ensure they appear) ---
    private void refreshHullmodSpecs() {
        CargoAPI cargo = getCargo();
        if (cargo == null) return;

        // Remove existing hullmod spec specials we added previously
        cargo.getStacksCopy().forEach(stack -> {
            if (stack != null && stack.isSpecialStack()) {
                SpecialItemData data = stack.getSpecialDataIfSpecial();
                if (data != null && Items.TAG_MODSPEC.equals(data.getId())) {
                    cargo.removeStack(stack);
                }
            }
        });

        FactionAPI faction = submarket.getFaction();
        if (faction == null) faction = market.getFaction();
        if (faction == null) return;

        List<String> hullmods = new ArrayList<>(faction.getKnownHullMods());
        if (hullmods.isEmpty()) return;

        // Seed for deterministic monthly-ish refresh tied to SW update cadence
        long seed = (market.getId().hashCode() * 17L)
                ^ (submarket.getSpecId().hashCode() * 97L)
                ^ (Global.getSector().getClock().getCycle() * 1000L + Global.getSector().getClock().getMonth()) * 13L;
        Random r = new Random(seed);
        Collections.shuffle(hullmods, r);

        int added = 0;
        for (String id : hullmods) {
            if (added >= HULLMOD_SPECS_PER_REFRESH) break;

            HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
            if (spec == null) continue;
            if (spec.isHidden() || spec.isAlwaysUnlocked()) continue;
            if (spec.getTier() > HULLMOD_MAX_TIER) continue;

            cargo.addItems(CargoItemType.SPECIAL, new SpecialItemData(Items.TAG_MODSPEC, id), 1);
            added++;
        }
    }
}