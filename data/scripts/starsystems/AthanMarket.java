package data.scripts.starsystems;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

import UFP.data.campaign.people.GeordiLaForge;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorGeneratorPlugin;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

public class AthanMarket implements SectorGeneratorPlugin {

    private static final String SYSTEM_NAME = "Athan";
    private static final String ATHAN_PRIME_ID = "athan_prime";
    private static final String MUSEUM_ID = "starfleet_museum";
    private static final String ORBITAL_COMPLEX_ID = "athan_orbital_complex";

    @Override
    public void generate(SectorAPI sector) {
        StarSystemAPI system = sector.getStarSystem(SYSTEM_NAME);
        if (system == null) return;

        // 2. Starfleet Museum Market
        SectorEntityToken museumToken = system.getEntityById(MUSEUM_ID);
        if (museumToken instanceof CustomCampaignEntityAPI) {
            CustomCampaignEntityAPI museum = (CustomCampaignEntityAPI) museumToken;
            if (museum.getMarket() == null) {
                createMuseumMarket(sector, system, museum);
            }
        }

        // 3. Orbital Complex Market
        SectorEntityToken complexToken = system.getEntityById(ORBITAL_COMPLEX_ID);
        if (complexToken instanceof CustomCampaignEntityAPI) {
            CustomCampaignEntityAPI complex = (CustomCampaignEntityAPI) complexToken;
            if (complex.getMarket() == null) {
                createOrbitalComplexMarket(sector, system, complex);
            }
        }
    }

    private void createMuseumMarket(SectorAPI sector, StarSystemAPI system, CustomCampaignEntityAPI museum) {
        int size = 5;

        MarketAPI market = Global.getFactory().createMarket("market_starfleet_museum", "Spacedock One", size);

        market.setPrimaryEntity(museum);
        market.setFactionId(DimensionsCrossedIDS.UFP);
        market.setSize(size);
        market.setPlayerOwned(false);
        market.setFreePort(false);

        museum.setFaction(DimensionsCrossedIDS.UFP);

        // --- Industries ---
        market.addIndustry(DimensionsCrossedIDS.STATION_POPULATION);
        market.addIndustry(DimensionsCrossedIDS.STARBASE_SPACEPORT);
        market.addIndustry(Industries.COMMERCE);
        market.addIndustry(DimensionsCrossedIDS.WAYSTATION);
        market.addIndustry(DimensionsCrossedIDS.ORBITALSTATION_SPACEDOCK_MUSEUM);
        market.addIndustry(DimensionsCrossedIDS.DRYDOCK_ADVANCED);
        market.addIndustry(DimensionsCrossedIDS.STARFLEET_OPERATIONS);

        // --- Market Conditions ---
        market.addCondition(Conditions.ESTABLISHED_POLITY);
        market.addCondition(Conditions.OUTPOST);
        market.addCondition(DimensionsCrossedIDS.STARBASE_HABITAT);
        market.addCondition(Conditions.POPULATION_5);

        // --- Submarkets ---
        market.addSubmarket(Submarkets.SUBMARKET_OPEN);
        market.addSubmarket(DimensionsCrossedIDS.SUBMARKET_FEDERATION);
        market.addSubmarket(Submarkets.SUBMARKET_STORAGE);

        // --- Installations ---
        installAICoreIfPossible(market, DimensionsCrossedIDS.DRYDOCK_ADVANCED, Commodities.ALPHA_CORE);
        installSpecialItemIfPossible(market, DimensionsCrossedIDS.DRYDOCK_ADVANCED, Items.PRISTINE_NANOFORGE);

        museum.setMarket(market);
        sector.getEconomy().addMarket(market, true);

        // Add Geordi to the Comm Directory upon market generation
        GeordiLaForge.create(market);

        // Connect to Athan Prime
        SectorEntityToken primaryPlanet = system.getEntityById(ATHAN_PRIME_ID);
        if (primaryPlanet != null) {
            market.getConnectedEntities().add(primaryPlanet);
        }
    }

    private void createOrbitalComplexMarket(SectorAPI sector, StarSystemAPI system, CustomCampaignEntityAPI complex) {
        int size = 5;

        MarketAPI market = Global.getFactory().createMarket("market_athan_orbital_complex", "Orbital Station 275", size);

        market.setPrimaryEntity(complex);
        market.setFactionId(DimensionsCrossedIDS.UFP);
        market.setSize(size);
        market.setPlayerOwned(false);
        market.setFreePort(false);

        complex.setFaction(DimensionsCrossedIDS.UFP);

        // --- Industries ---
        market.addIndustry(DimensionsCrossedIDS.STATION_POPULATION);
        market.addIndustry(DimensionsCrossedIDS.STARBASE_SPACEPORT);
        market.addIndustry(Industries.COMMERCE);
        market.addIndustry(DimensionsCrossedIDS.WAYSTATION);
        market.addIndustry(DimensionsCrossedIDS.R_N_D);
        market.addIndustry(DimensionsCrossedIDS.STARFLEET_OPERATIONS);
        market.addIndustry(DimensionsCrossedIDS.REFINING_PROCESSING_PLANT);

        // --- Market Conditions ---
        market.addCondition(Conditions.ESTABLISHED_POLITY);
        market.addCondition(Conditions.INDUSTRIAL_POLITY);
        market.addCondition(DimensionsCrossedIDS.STARBASE_HABITAT);
        market.addCondition(Conditions.POPULATION_5);

        // --- Submarkets ---
        market.addSubmarket(Submarkets.SUBMARKET_OPEN);
        market.addSubmarket(Submarkets.SUBMARKET_BLACK);
        market.addSubmarket(DimensionsCrossedIDS.SUBMARKET_FEDERATION);
        market.addSubmarket(Submarkets.SUBMARKET_STORAGE);

        // --- Installations ---
        installAICoreIfPossible(market, DimensionsCrossedIDS.R_N_D, Commodities.ALPHA_CORE);
        installAICoreIfPossible(market, DimensionsCrossedIDS.STARFLEET_OPERATIONS, Commodities.ALPHA_CORE);

        complex.setMarket(market);
        sector.getEconomy().addMarket(market, true);

        // Connect to Athan Prime
        SectorEntityToken primaryPlanet = system.getEntityById(ATHAN_PRIME_ID);
        if (primaryPlanet != null) {
            market.getConnectedEntities().add(primaryPlanet);
        }
    }

    private void installAICoreIfPossible(MarketAPI market, String industryId, String aiCoreId) {
        Industry ind = market.getIndustry(industryId);
        if (ind == null) return;
        if (ind.canInstallAICores()) {
            ind.setAICoreId(aiCoreId);
        }
    }

    private void installSpecialItemIfPossible(MarketAPI market, String industryId, String itemId) {
        Industry ind = market.getIndustry(industryId);
        if (ind == null) return;
        ind.setSpecialItem(new SpecialItemData(itemId, null));
    }
}