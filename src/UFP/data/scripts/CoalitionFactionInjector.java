package UFP.data.scripts;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import UFP.data.campaign.ids.DimensionsCrossedIDS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cleanly injects ColTech hulls into vanilla faction blueprint pools,
 * enforces a maximum limit of 2 ColTech ships per fleet (excluding Independents & Scavengers),
 * and blocks ColTech ships/variants from appearing in Open & Military submarkets.
 */
public class CoalitionFactionInjector implements EveryFrameScript {

    private boolean isDone = false;

    // Fast O(1) Set lookup for ColTech hull verification
    private static final Set<String> COLTECH_HULLS = new HashSet<>(Arrays.asList(
            DimensionsCrossedIDS.HULL_COLOSSUS_MK3,
            DimensionsCrossedIDS.HULL_COLOSSUS_MK4,
            DimensionsCrossedIDS.HULL_ATLAS_COLTECH,
            DimensionsCrossedIDS.HULL_ATLAS_CARRIER,
            DimensionsCrossedIDS.HULL_ATLAS_COLONY,
            DimensionsCrossedIDS.HULL_ATLAS_MOTHBALLED,
            DimensionsCrossedIDS.HULL_EAGLE_AMBASSADOR,
            DimensionsCrossedIDS.HULL_EAGLE_MK3,
            DimensionsCrossedIDS.HULL_CONQUEST_SOVEREIGN,
            DimensionsCrossedIDS.HULL_CONQUEST_SUPREME,
            DimensionsCrossedIDS.HULL_ANTISEPTIC,
            DimensionsCrossedIDS.HULL_SABOTAUNT,
            DimensionsCrossedIDS.HULL_ONSLAUGHT_GALAXY,
            DimensionsCrossedIDS.HULL_ONSLAUGHT_HELLHOUND,
            DimensionsCrossedIDS.HULL_PARAGON_MONARCH,
            DimensionsCrossedIDS.HULL_PARAGON_SIGNATURE
    ));

    @Override
    public boolean isDone() {
        return isDone;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if (isDone) return;

        injectFactionShips();
        isDone = true; // Immediately self-terminates after executing once
    }

    private void injectFactionShips() {
        if (Global.getSector() == null) return;

        // --- KNIGHTS OF LUDD (KOL) ---
        injectHulls(Factions.KOL, new String[][]{
                {DimensionsCrossedIDS.HULL_ANTISEPTIC, "1.0"},
                {DimensionsCrossedIDS.HULL_SABOTAUNT, "0.8"},
                {DimensionsCrossedIDS.HULL_EAGLE_MK3, "0.5"},
                {DimensionsCrossedIDS.HULL_ATLAS_COLONY, "0.6"}
        });

        // --- PIRATES ---
        injectHulls(Factions.PIRATES, new String[][]{
                {DimensionsCrossedIDS.HULL_COLOSSUS_MK3, "1.0"},
                {DimensionsCrossedIDS.HULL_COLOSSUS_MK4, "1.0"},
                {DimensionsCrossedIDS.HULL_ATLAS_MOTHBALLED, "0.8"},
                {DimensionsCrossedIDS.HULL_SABOTAUNT, "0.6"},
                {DimensionsCrossedIDS.HULL_ONSLAUGHT_HELLHOUND, "0.3"}
        });

        // --- LUDDIC PATH ---
        injectHulls(Factions.LUDDIC_PATH, new String[][]{
                {DimensionsCrossedIDS.HULL_COLOSSUS_MK3, "1.0"},
                {DimensionsCrossedIDS.HULL_COLOSSUS_MK4, "0.8"},
                {DimensionsCrossedIDS.HULL_SABOTAUNT, "0.7"},
                {DimensionsCrossedIDS.HULL_ONSLAUGHT_HELLHOUND, "0.2"}
        });

        // --- LUDDIC CHURCH ---
        injectHulls(Factions.LUDDIC_CHURCH, new String[][]{
                {DimensionsCrossedIDS.HULL_ATLAS_COLONY, "1.0"},
                {DimensionsCrossedIDS.HULL_COLOSSUS_MK3, "0.5"},
                {DimensionsCrossedIDS.HULL_ANTISEPTIC, "0.4"},
                {DimensionsCrossedIDS.HULL_SABOTAUNT, "0.3"}
        });

        // --- INDEPENDENT & SCAVENGERS ---
        String[] indyFactions = {Factions.INDEPENDENT, Factions.SCAVENGERS};
        for (String factionId : indyFactions) {
            injectHulls(factionId, new String[][]{
                    {DimensionsCrossedIDS.HULL_COLOSSUS_MK3, "0.8"},
                    {DimensionsCrossedIDS.HULL_COLOSSUS_MK4, "0.8"},
                    {DimensionsCrossedIDS.HULL_ATLAS_COLTECH, "0.7"},
                    {DimensionsCrossedIDS.HULL_ATLAS_CARRIER, "0.5"},
                    {DimensionsCrossedIDS.HULL_ATLAS_COLONY, "0.6"},
                    {DimensionsCrossedIDS.HULL_ATLAS_MOTHBALLED, "0.4"},
                    {DimensionsCrossedIDS.HULL_EAGLE_AMBASSADOR, "0.4"},
                    {DimensionsCrossedIDS.HULL_EAGLE_MK3, "0.4"},
                    {DimensionsCrossedIDS.HULL_CONQUEST_SOVEREIGN, "0.3"},
                    {DimensionsCrossedIDS.HULL_CONQUEST_SUPREME, "0.3"},
                    {DimensionsCrossedIDS.HULL_ANTISEPTIC, "0.3"},
                    {DimensionsCrossedIDS.HULL_SABOTAUNT, "0.3"},
                    {DimensionsCrossedIDS.HULL_ONSLAUGHT_GALAXY, "0.2"},
                    {DimensionsCrossedIDS.HULL_ONSLAUGHT_HELLHOUND, "0.2"},
                    {DimensionsCrossedIDS.HULL_PARAGON_MONARCH, "0.15"},
                    {DimensionsCrossedIDS.HULL_PARAGON_SIGNATURE, "0.1"}
            });
        }

        // --- TRI-TACHYON ---
        injectHulls(Factions.TRITACHYON, new String[][]{
                {DimensionsCrossedIDS.HULL_ATLAS_COLTECH, "1.0"},
                {DimensionsCrossedIDS.HULL_ATLAS_CARRIER, "0.8"},
                {DimensionsCrossedIDS.HULL_PARAGON_MONARCH, "0.3"},
                {DimensionsCrossedIDS.HULL_PARAGON_SIGNATURE, "0.2"}
        });

        // --- PERSEAN LEAGUE ---
        injectHulls(Factions.PERSEAN, new String[][]{
                {DimensionsCrossedIDS.HULL_EAGLE_AMBASSADOR, "0.8"},
                {DimensionsCrossedIDS.HULL_EAGLE_MK3, "0.6"},
                {DimensionsCrossedIDS.HULL_CONQUEST_SUPREME, "0.4"},
                {DimensionsCrossedIDS.HULL_CONQUEST_SOVEREIGN, "0.3"}
        });

        // --- HEGEMONY ---
        injectHulls(Factions.HEGEMONY, new String[][]{
                {DimensionsCrossedIDS.HULL_EAGLE_MK3, "0.7"},
                {DimensionsCrossedIDS.HULL_ANTISEPTIC, "0.5"},
                {DimensionsCrossedIDS.HULL_ONSLAUGHT_GALAXY, "0.3"}
        });

        // --- DIKTAT & LION'S GUARD ---
        String[] diktatFactions = {Factions.DIKTAT, Factions.LIONS_GUARD};
        for (String factionId : diktatFactions) {
            injectHulls(factionId, new String[][]{
                    {DimensionsCrossedIDS.HULL_EAGLE_AMBASSADOR, "0.7"},
                    {DimensionsCrossedIDS.HULL_CONQUEST_SOVEREIGN, "0.4"}
            });
        }
    }

    /**
     * Helper to batch add ships to a faction pool and clear cache.
     */
    private void injectHulls(String factionId, String[][] hullData) {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        if (faction == null) return;

        Map<String, Float> hullFreq = faction.getHullFrequency();

        for (String[] data : hullData) {
            String hullId = data[0];
            float freq = Float.parseFloat(data[1]);

            if (!faction.knowsShip(hullId)) {
                faction.addKnownShip(hullId, true);
            }
            if (hullFreq != null) {
                hullFreq.put(hullId, freq);
            }
        }

        faction.clearShipRoleCache();
    }

    /**
     * Call this inside ModPlugin.onGameLoad() to ensure injections and listeners apply seamlessly.
     */
    public static void onGameLoad() {
        Global.getSector().addTransientScript(new CoalitionFactionInjector());

        // Add permanent listener to cap ColTech hulls per spawned fleet
        if (!Global.getSector().getListenerManager().hasListenerOfClass(ColTechFleetCapListener.class)) {
            Global.getSector().getListenerManager().addListener(new ColTechFleetCapListener(), true);
        }

        // Add permanent listener to block ColTech hulls from appearing in Open & Military submarkets
        if (!Global.getSector().getListenerManager().hasListenerOfClass(ColTechMarketBlockerListener.class)) {
            Global.getSector().getListenerManager().addListener(new ColTechMarketBlockerListener(), true);
        }
    }

    /**
     * Listener that automatically trims ColTech ships down to a maximum of 2 when a non-Independent fleet spawns.
     */
    public static class ColTechFleetCapListener implements FleetEventListener {

        public void reportFleetSpawned(CampaignFleetAPI fleet) {
            if (fleet == null || fleet.getFaction() == null) return;

            String factionId = fleet.getFaction().getId();

            // Exclude Independents and Scavengers from the cap restriction
            if (Factions.INDEPENDENT.equals(factionId) || Factions.SCAVENGERS.equals(factionId)) {
                return;
            }

            int colTechCount = 0;
            List<FleetMemberAPI> toRemove = new ArrayList<>();

            for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
                if (member == null || member.getHullSpec() == null) continue;

                String baseHullId = member.getHullSpec().getBaseHullId();
                String hullId = member.getHullId();

                if (COLTECH_HULLS.contains(hullId) || COLTECH_HULLS.contains(baseHullId)) {
                    colTechCount++;

                    // Keep maximum of 2; remove excess ships (except flagships)
                    if (colTechCount > 2) {
                        if (!member.isFlagship()) {
                            toRemove.add(member);
                        }
                    }
                }
            }

            for (FleetMemberAPI member : toRemove) {
                fleet.getFleetData().removeFleetMember(member);
            }
        }

        @Override
        public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) {
            // Unused
        }

        @Override
        public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
            // Unused
        }
    }

    /**
     * Listener that strips ColTech ships/variants from SUBMARKET_OPEN and GENERIC_MILITARY submarkets
     * on every monthly economy tick update.
     */
    public static class ColTechMarketBlockerListener implements EconomyTickListener {

        @Override
        public void reportEconomyTick(int iterIndex) {
            if (Global.getSector() == null) return;

            for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
                if (market == null) continue;

                // Check and clean SUBMARKET_OPEN
                SubmarketAPI openMarket = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
                if (openMarket != null && openMarket.getCargo() != null) {
                    pruneColTechShips(openMarket);
                }

                // Check and clean GENERIC_MILITARY
                SubmarketAPI militaryMarket = market.getSubmarket(Submarkets.GENERIC_MILITARY);
                if (militaryMarket != null && militaryMarket.getCargo() != null) {
                    pruneColTechShips(militaryMarket);
                }
            }
        }

        @Override
        public void reportEconomyMonthEnd() {

        }

        public void reportEconomyMonth() {
            // Unused
        }

        private void pruneColTechShips(SubmarketAPI submarket) {
            List<FleetMemberAPI> toRemove = new ArrayList<>();

            for (FleetMemberAPI member : submarket.getCargo().getMothballedShips().getMembersListCopy()) {
                if (member == null || member.getHullSpec() == null) continue;

                String hullId = member.getHullId();
                String baseHullId = member.getHullSpec().getBaseHullId();

                if (COLTECH_HULLS.contains(hullId) || COLTECH_HULLS.contains(baseHullId)) {
                    toRemove.add(member);
                }
            }

            for (FleetMemberAPI member : toRemove) {
                submarket.getCargo().getMothballedShips().removeFleetMember(member);
            }
        }
    }
}