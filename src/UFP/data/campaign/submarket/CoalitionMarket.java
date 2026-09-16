package UFP.data.campaign.submarket;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI.CoreUITradeMode;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class CoalitionMarket extends BaseSubmarketPlugin {


    private static final float REFRESH_DAYS = 15f;
    private static final int MIN_SHIPS = 7;
    private static final int MAX_SHIPS = 15;

    private static final int MIN_WEAPON_SLOTS = 10;
    private static final int MAX_WEAPON_SLOTS = 18;

    // Access gating
    private static final RepLevel MIN_STANDING_TO_ACCESS = RepLevel.FAVORABLE;

    // Hulls sold as "bare" hull listings: "<hullId>_Hull"
    private static final String[] HULL_POOL = new String[] {
            DimensionsCrossedIDS.HULL_ONSLAUGHT_HELLHOUND,
            DimensionsCrossedIDS.HULL_ONSLAUGHT_GALAXY,
            DimensionsCrossedIDS.HULL_PARAGON_SIGNATURE,
            DimensionsCrossedIDS.HULL_PARAGON_MONARCH,
            DimensionsCrossedIDS.HULL_SABOTAUNT,
            DimensionsCrossedIDS.HULL_ANTISEPTIC,
            DimensionsCrossedIDS.HULL_CONQUEST_SUPREME,
            DimensionsCrossedIDS.HULL_CONQUEST_SOVEREIGN,
            DimensionsCrossedIDS.HULL_COLOSSUS_MK3,
            DimensionsCrossedIDS.HULL_COLOSSUS_MK4,
            DimensionsCrossedIDS.HULL_EAGLE_AMBASSADOR,
            DimensionsCrossedIDS.HULL_EAGLE_MK3,
            DimensionsCrossedIDS.HULL_ATLAS_COLTECH,
            DimensionsCrossedIDS.HULL_ATLAS_MOTHBALLED,
            DimensionsCrossedIDS.HULL_ATLAS_CARRIER,
            DimensionsCrossedIDS.HULL_ATLAS_COLONY,
            DimensionsCrossedIDS.HULL_COLTECH_FUELTANKER,
    };

    // Prefit variants sold as-is
    private static final String[] VARIANT_POOL = new String[] {
            DimensionsCrossedIDS.PARAGON_MONARCH,
            DimensionsCrossedIDS.COLOSSUS_MK3,
            DimensionsCrossedIDS.COLOSSUS_MK4,
            DimensionsCrossedIDS.ATLAS_COLTECH,
            DimensionsCrossedIDS.ATLAS_CARRIER,
            DimensionsCrossedIDS.ATLAS_COLONY,
            DimensionsCrossedIDS.ATLAS_MOTHBALLED,
            DimensionsCrossedIDS.COLTECH_FUELTANKER,
            DimensionsCrossedIDS.EAGLE_MK3,
            DimensionsCrossedIDS.EAGLE_AMBASSADOR,
            DimensionsCrossedIDS.CONQUEST_SOVEREIGN,
            DimensionsCrossedIDS.CONQUEST_SUPREME,
            DimensionsCrossedIDS.ANTISEPTIC,
            DimensionsCrossedIDS.SABOTAUNT,
            DimensionsCrossedIDS.ONSLAUGHT_GALAXY,
            DimensionsCrossedIDS.ONSLAUGHT_HELLHOUND,
            DimensionsCrossedIDS.PARAGON_MONARCH,
            DimensionsCrossedIDS.PARAGON_SIGNATURE,
    };

    // (Optional) allow-lists retained for clarity/future logic
    private static final Set<String> ALLOWED_VARIANTS = new HashSet<String>();
    private static final Set<String> ALLOWED_HULLS = new HashSet<String>();
    static {
        for (String h : HULL_POOL) ALLOWED_HULLS.add(h);
        for (String v : VARIANT_POOL) ALLOWED_VARIANTS.add(v);
    }

    @Override
    public void init(SubmarketAPI submarket) {
        super.init(submarket);

        // Force refresh cadence
        this.minSWUpdateInterval = REFRESH_DAYS;
        this.sinceSWUpdate = REFRESH_DAYS + 1f;       // populate immediately once
        this.sinceLastCargoUpdate = REFRESH_DAYS + 1f;
    }

    @Override
    public String getName() {
        return "Coalition Market";
    }

    @Override
    public void updateCargoPrePlayerInteraction() {
        // We're controlling our own inventories; reset cargo timer.
        sinceLastCargoUpdate = 0f;

        // Only refresh when allowed by BaseSubmarketPlugin timer gate
        if (!okToUpdateShipsAndWeapons()) return;
        sinceSWUpdate = 0f;

        // ======================
        // SHIPS (7–15)
        // ======================
        getCargo().getMothballedShips().clear();

        int totalShips = MIN_SHIPS + itemGenRandom.nextInt(MAX_SHIPS - MIN_SHIPS + 1);

        int maxHullSlots = Math.min(totalShips - 2, 7);
        int hullCount = 2 + itemGenRandom.nextInt(Math.max(1, maxHullSlots - 1));
        int variantCount = totalShips - hullCount;

        List<String> shipPicks = new ArrayList<String>(totalShips);

        for (int i = 0; i < hullCount; i++) {
            String hullId = HULL_POOL[itemGenRandom.nextInt(HULL_POOL.length)];
            shipPicks.add(hullId + "_Hull");
        }
        for (int i = 0; i < variantCount; i++) {
            shipPicks.add(VARIANT_POOL[itemGenRandom.nextInt(VARIANT_POOL.length)]);
        }

        java.util.Collections.shuffle(shipPicks, new Random(itemGenRandom.nextLong()));

        for (String id : shipPicks) {
            try {
                addShip(id, false, Misc.getShipQuality(market, submarket.getFaction().getId()));
            } catch (Throwable t) {
                Global.getLogger(CoalitionMarket.class).warn("Skipping missing ship/variant: " + id, t);
            }
        }

        // ======================
        // WEAPONS (10–18 stacks)
        // ======================
        pruneWeapons(0f);

        int weaponSlots = MIN_WEAPON_SLOTS + itemGenRandom.nextInt(MAX_WEAPON_SLOTS - MIN_WEAPON_SLOTS + 1);

        WeightedRandomPicker<WeaponSpecAPI> weaponPicker = new WeightedRandomPicker<>(itemGenRandom);

        for (WeaponSpecAPI spec : Global.getSettings().getAllWeaponSpecs()) {
            if (spec == null) continue;

            if (!spec.hasTag(DimensionsCrossedIDS.TAG_COALITION_TECH)) continue;

            // - Exclude system/hidden weapons
            if (spec.getAIHints().contains(WeaponAPI.AIHints.SYSTEM)) continue;
            if (spec.hasTag(Tags.WEAPON_NO_SELL)) continue;

            weaponPicker.add(spec, 1f);
        }

        Set<String> usedWeaponIds = new HashSet<String>();
        int added = 0;

        while (added < weaponSlots && !weaponPicker.isEmpty()) {
            WeaponSpecAPI spec = weaponPicker.pickAndRemove();
            if (spec == null) break;

            String wid = spec.getWeaponId();
            if (usedWeaponIds.contains(wid)) continue;
            usedWeaponIds.add(wid);

            int count = 1;
            switch (spec.getSize()) {
                case LARGE:  count = 1; break;
                case MEDIUM: count = 2; break;
                case SMALL:  count = 3; break;
            }
            count = Math.max(1, count + itemGenRandom.nextInt(2) - itemGenRandom.nextInt(2));

            getCargo().addWeapons(wid, count);
            added++;
        }

        // COMMODITIES (always available)
        refreshCommodity(DimensionsCrossedIDS.DILITHIUM, 60, 160);
        refreshCommodity(DimensionsCrossedIDS.SCIENTIST, 25, 90);
        refreshCommodity(Commodities.SUPPLIES, 200, 600);
        refreshCommodity(Commodities.FUEL, 300, 900);

        getCargo().sort();

        exportSelectedVariantsToOtherFactions();
    }

    private void refreshCommodity(String commodityId, int min, int max) {
        CargoAPI cargo = getCargo();
        try {
            float existing = cargo.getCommodityQuantity(commodityId);
            if (existing > 0) cargo.removeCommodity(commodityId, existing);

            int amount = min + itemGenRandom.nextInt(Math.max(1, (max - min + 1)));
            cargo.addCommodity(commodityId, amount);
        } catch (Throwable t) {
            Global.getLogger(CoalitionMarket.class).warn("Skipping missing/invalid commodity id: " + commodityId, t);
        }
    }

    private void exportSelectedVariantsToOtherFactions() {
        // Intentionally empty hook.
    }

    // ------------------------------------------------------------
    // Player access & legality (ship purchase gating like military)
    // ------------------------------------------------------------

    @Override
    public boolean isEnabled(CoreUIAPI ui) {
        if (ui.getTradeMode() == CoreUITradeMode.SNEAK) return false;
        RepLevel level = submarket.getFaction().getRelationshipLevel(Global.getSector().getFaction(Factions.PLAYER));
        return level.isAtWorst(MIN_STANDING_TO_ACCESS);
    }

    @Override
    public String getTooltipAppendix(CoreUIAPI ui) {
        if (!isEnabled(ui)) {
            return "Requires: " + submarket.getFaction().getDisplayName() + " - " +
                    MIN_STANDING_TO_ACCESS.getDisplayName().toLowerCase();
        }
        if (ui.getTradeMode() == CoreUITradeMode.SNEAK) {
            return "Requires: proper docking authorization";
        }
        return null;
    }

    @Override
    public Highlights getTooltipAppendixHighlights(CoreUIAPI ui) {
        String appendix = getTooltipAppendix(ui);
        if (appendix == null) return null;
        Highlights h = new Highlights();
        h.setText(appendix);
        h.setColors(Misc.getNegativeHighlightColor());
        return h;
    }

    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        RepLevel req = getRequiredLevelAssumingLegal(member, action);
        if (req == null) return false;

        RepLevel level = submarket.getFaction().getRelationshipLevel(Global.getSector().getFaction(Factions.PLAYER));
        return !level.isAtWorst(req);
    }

    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        RepLevel req = getRequiredLevelAssumingLegal(member, action);
        if (req != null) {
            RepLevel level = submarket.getFaction().getRelationshipLevel(Global.getSector().getFaction(Factions.PLAYER));
            if (!level.isAtWorst(req)) {
                return "Req: " + submarket.getFaction().getDisplayName() + " - " +
                        req.getDisplayName().toLowerCase();
            }
        }
        if (action == TransferAction.PLAYER_BUY) return "Illegal to buy";
        return "Illegal to sell";
    }

    @Override
    public Highlights getIllegalTransferTextHighlights(FleetMemberAPI member, TransferAction action) {
        RepLevel req = getRequiredLevelAssumingLegal(member, action);
        if (req == null) return null;

        Color c = Misc.getNegativeHighlightColor();
        Highlights h = new Highlights();
        RepLevel level = submarket.getFaction().getRelationshipLevel(Global.getSector().getFaction(Factions.PLAYER));

        if (!level.isAtWorst(req)) {
            h.append("Req: " + submarket.getFaction().getDisplayName() + " - " +
                    req.getDisplayName().toLowerCase(), c);
        }
        return h;
    }

    private RepLevel getRequiredLevelAssumingLegal(FleetMemberAPI member, TransferAction action) {
        if (action != TransferAction.PLAYER_BUY) return null;

        int fp = member.getFleetPointCost();
        HullSize size = member.getHullSpec().getHullSize();

        if (size == HullSize.CAPITAL_SHIP && fp > 15) return RepLevel.COOPERATIVE;
        if (size == HullSize.CRUISER && fp > 10) return RepLevel.FRIENDLY;
        if (size == HullSize.DESTROYER && fp > 5) return RepLevel.WELCOMING;
        return RepLevel.FAVORABLE;
    }

    @Override
    public PlayerEconomyImpactMode getPlayerEconomyImpactMode() {
        return PlayerEconomyImpactMode.PLAYER_SELL_ONLY;
    }
}