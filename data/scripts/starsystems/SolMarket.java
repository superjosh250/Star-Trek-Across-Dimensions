package data.scripts.starsystems;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

public class SolMarket {

    // =========================================================
    // Earth market setup (Population 10 + conditions + industries)
    // =========================================================
    public static void addEarthMarket(SectorAPI sector, PlanetAPI earth) {
        MarketAPI market = Global.getFactory().createMarket("earth_market", "Earth", 10);

        earth.setFaction("ufp");
        market.setPrimaryEntity(earth);
        market.setFactionId("ufp");

        market.addCondition(Conditions.POPULATION_10);
        market.addCondition(Conditions.REGIONAL_CAPITAL);
        market.addCondition(Conditions.TERRAN);
        market.addCondition(Conditions.HABITABLE);
        market.addCondition(Conditions.MILD_CLIMATE);
        market.addCondition(Conditions.FARMLAND_RICH);
        market.addCondition(Conditions.ORGANICS_ABUNDANT);
        market.addCondition(DimensionsCrossedIDS.DILITHIUM_ORE);
        market.addCondition(Conditions.ORE_ABUNDANT);
        market.addCondition(Conditions.RARE_ORE_MODERATE);
        market.addCondition(Conditions.VOLATILES_DIFFUSE);
        market.addCondition(Conditions.ESTABLISHED_POLITY);

        // --- Industries ---
        market.addIndustry(DimensionsCrossedIDS.SOCIETY_POPULATION);
        market.addIndustry(DimensionsCrossedIDS.STARFLEET_HQ);
        market.addIndustry(DimensionsCrossedIDS.STARFLEET_COMMAND);
        market.addIndustry(DimensionsCrossedIDS.STARFLEET_ACADEMY);
        market.addIndustry(DimensionsCrossedIDS.STARBASE_SPACEPORT);
        market.addIndustry(Industries.FARMING);
        market.addIndustry(DimensionsCrossedIDS.MINING_ADVANCED);
        market.addIndustry(DimensionsCrossedIDS.INDUSTRIAL_REPLICATOR);
        market.addIndustry(DimensionsCrossedIDS.REFINING_PROCESSING_PLANT);
        market.addIndustry(DimensionsCrossedIDS.DRYDOCK);
        market.addIndustry(DimensionsCrossedIDS.DRYDOCK_ADVANCED);
        market.addIndustry(DimensionsCrossedIDS.ORBITALSTATION_SPACEDOCK);


        Industry earthSociety = market.getIndustry(DimensionsCrossedIDS.SOCIETY_POPULATION);
        if (earthSociety != null) {
            earthSociety.setSpecialItem(new SpecialItemData(Items.DEALMAKER_HOLOSUITE, null));
            earthSociety.setAICoreId(Commodities.OMEGA_CORE);
        }

        Industry earthSpaceport = market.getIndustry(DimensionsCrossedIDS.STARBASE_SPACEPORT);
        if (earthSpaceport != null) {
            earthSpaceport.setSpecialItem(new SpecialItemData(Items.DEALMAKER_HOLOSUITE, null));
            earthSpaceport.setAICoreId(Commodities.OMEGA_CORE);
        }

        Industry earthMining = market.getIndustry(DimensionsCrossedIDS.MINING_ADVANCED);
        if (earthMining != null) {
            earthMining.setSpecialItem(new SpecialItemData(Items.PLASMA_DYNAMO, null));
            earthMining.setAICoreId(Commodities.ALPHA_CORE);
        }

        Industry earthReplicator = market.getIndustry(DimensionsCrossedIDS.INDUSTRIAL_REPLICATOR);
        if (earthReplicator != null) {
            earthReplicator.setAICoreId(Commodities.OMEGA_CORE);
        }

        if (market.hasIndustry(DimensionsCrossedIDS.DRYDOCK)) {
            market.getIndustry(DimensionsCrossedIDS.DRYDOCK)
                    .setSpecialItem(new SpecialItemData(Items.PRISTINE_NANOFORGE, null));
            market.getIndustry(DimensionsCrossedIDS.DRYDOCK).setAICoreId(Commodities.ALPHA_CORE);
        }

        if (market.hasIndustry(DimensionsCrossedIDS.DRYDOCK_ADVANCED)) {
            market.getIndustry(DimensionsCrossedIDS.DRYDOCK_ADVANCED)
                    .setSpecialItem(new SpecialItemData(Items.PRISTINE_NANOFORGE, null));
            market.getIndustry(DimensionsCrossedIDS.DRYDOCK_ADVANCED).setAICoreId(Commodities.ALPHA_CORE);
        }

        // --- Submarkets ---
        market.addSubmarket(Submarkets.SUBMARKET_OPEN);
        market.addSubmarket(Submarkets.SUBMARKET_BLACK);
        market.addSubmarket(DimensionsCrossedIDS.SUBMARKET_FEDERATION);
        market.addSubmarket(Submarkets.SUBMARKET_STORAGE);

        // Apply + register
        earth.setMarket(market);
        sector.getEconomy().addMarket(market, true);
        market.reapplyIndustries();
    }

    public static void addLunaMarket(SectorAPI sector, PlanetAPI moon) {
        if (moon == null) return;

        MarketAPI market = Global.getFactory().createMarket("moon_market", moon.getName(), 8);
        market.setFactionId(DimensionsCrossedIDS.UFP);
        market.setPrimaryEntity(moon);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);

        // Submarkets & Conditions...
        market.addSubmarket(Submarkets.SUBMARKET_OPEN);
        market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
        market.addSubmarket(Submarkets.SUBMARKET_BLACK);
        market.addSubmarket(DimensionsCrossedIDS.SUBMARKET_FEDERATION);

        market.addCondition(Conditions.POPULATION_8);
        market.addCondition(Conditions.HABITABLE);
        market.addCondition(DimensionsCrossedIDS.TERRAFORMED);
        market.addCondition(DimensionsCrossedIDS.DILITHIUM_ORE);
        market.addCondition(Conditions.ORE_RICH);
        market.addCondition(Conditions.RARE_ORE_ABUNDANT);
        market.addCondition(Conditions.ORGANICS_PLENTIFUL);

        // 1. Add standard industries
        market.addIndustry(DimensionsCrossedIDS.SOCIETY_POPULATION);
        market.addIndustry(DimensionsCrossedIDS.STARBASE_SPACEPORT);
        market.addIndustry(DimensionsCrossedIDS.WAYSTATION);
        market.addIndustry(DimensionsCrossedIDS.STARFLEET_OPERATIONS);
        market.addIndustry(DimensionsCrossedIDS.DRYDOCK_ADVANCED);
        market.addIndustry(DimensionsCrossedIDS.MINING_ADVANCED);
        market.addIndustry(DimensionsCrossedIDS.REFINING_PROCESSING_PLANT);
        market.addIndustry(DimensionsCrossedIDS.INDUSTRIAL_REPLICATOR);


        // 2. Attach AI Cores & Special Items

        // AI Core on Spaceport
        Industry spaceport = market.getIndustry(DimensionsCrossedIDS.STARBASE_SPACEPORT);
        if (spaceport != null) {
            spaceport.setAICoreId(Commodities.ALPHA_CORE);
        }

        // Special Item & AI Core on Mining
        Industry mining = market.getIndustry(DimensionsCrossedIDS.MINING_ADVANCED);
        if (mining != null) {
            mining.setSpecialItem(new SpecialItemData(Items.CATALYTIC_CORE, null));
            mining.setAICoreId(Commodities.BETA_CORE);
        }

        // 3. Register Market
        moon.setMarket(market);
        moon.setFaction(DimensionsCrossedIDS.UFP);

        Global.getSector().getEconomy().addMarket(market, true);
        market.reapplyConditions();
        market.reapplyIndustries();
    }

    public static void addMarsMarket(SectorAPI sector, PlanetAPI mars) {
        if (mars == null) return;

        MarketAPI market = Global.getFactory().createMarket("mars_market", mars.getName(), 7);
        market.setFactionId(DimensionsCrossedIDS.UFP);
        market.setPrimaryEntity(mars);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);

        // Submarkets & Conditions...
        market.addSubmarket(Submarkets.SUBMARKET_OPEN);
        market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
        market.addSubmarket(Submarkets.SUBMARKET_BLACK);
        market.addSubmarket(DimensionsCrossedIDS.SUBMARKET_FEDERATION);

        market.addCondition(Conditions.POPULATION_7);
        market.addCondition(Conditions.HABITABLE);
        market.addCondition(DimensionsCrossedIDS.TERRAFORMING);
        market.addCondition(DimensionsCrossedIDS.DILITHIUM_ORE);
        market.addCondition(Conditions.ORE_ULTRARICH);
        market.addCondition(Conditions.RARE_ORE_ULTRARICH);
        market.addCondition(Conditions.VOLATILES_TRACE);
        market.addCondition(Conditions.ORGANICS_COMMON);

        // 1. Add standard industries
        market.addIndustry(DimensionsCrossedIDS.SOCIETY_POPULATION);
        market.addIndustry(DimensionsCrossedIDS.STARBASE_SPACEPORT);
        market.addIndustry(DimensionsCrossedIDS.WAYSTATION);
        market.addIndustry(DimensionsCrossedIDS.STARFLEET_OPERATIONS);
        market.addIndustry(DimensionsCrossedIDS.DRYDOCK_ADVANCED);
        market.addIndustry(DimensionsCrossedIDS.MINING_ADVANCED);
        market.addIndustry(DimensionsCrossedIDS.REFINING_PROCESSING_PLANT);


        // 2. Attach AI Cores & Special Items

        // AI Core on Spaceport
        Industry spaceport = market.getIndustry(DimensionsCrossedIDS.STARBASE_SPACEPORT);
        if (spaceport != null) {
            spaceport.setAICoreId(Commodities.ALPHA_CORE);
        }

        // Special Item & AI Core on Mining
        Industry mining = market.getIndustry(DimensionsCrossedIDS.MINING_ADVANCED);
        if (mining != null) {
            mining.setSpecialItem(new SpecialItemData(Items.CATALYTIC_CORE, null));
            mining.setAICoreId(Commodities.BETA_CORE);
        }

        // 3. Register Market
        mars.setMarket(market);
        mars.setFaction(DimensionsCrossedIDS.UFP);

        Global.getSector().getEconomy().addMarket(market, true);
        market.reapplyConditions();
        market.reapplyIndustries();
    }
}