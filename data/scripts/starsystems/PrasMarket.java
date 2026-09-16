package data.scripts.starsystems;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.campaign.PersonImportance;

import java.util.Arrays;
import java.util.List;

public class PrasMarket {

    // Unified ID across Java and rules.csv
    public static final String PRAS_CONTACT_ID = "pras_mr_contact";

    /**
     * Creates and registers the market for Mr. Pras' station.
     */
    public static MarketAPI addMarketToPrasStation(CustomCampaignEntityAPI prasStation2) {
        if (prasStation2 == null) return null;

        String marketId = prasStation2.getId() + "_market";
        MarketAPI market = Global.getFactory().createMarket(marketId, prasStation2.getName(), 5);

        market.setFactionId(DimensionsCrossedIDS.UFP);
        market.setPrimaryEntity(prasStation2);
        prasStation2.setMarket(market);

        market.setFreePort(true);

        // Submarkets
        market.addSubmarket(Submarkets.SUBMARKET_OPEN);
        market.addSubmarket(DimensionsCrossedIDS.SUBMARKET_PRAS);
        market.addSubmarket(Submarkets.GENERIC_MILITARY);
        market.addSubmarket(Submarkets.SUBMARKET_BLACK);
        market.addSubmarket(Submarkets.SUBMARKET_STORAGE);

        // Market conditions
        addConditionSafe(market, DimensionsCrossedIDS.DILITHIUM_ORE_ABUNDANT);
        addConditionSafe(market, Conditions.HABITABLE);
        addConditionSafe(market, Conditions.MILD_CLIMATE);
        addConditionSafe(market, Conditions.ESTABLISHED_POLITY);
        addConditionSafe(market, Conditions.FREE_PORT);
        addConditionSafe(market, Conditions.REGIONAL_CAPITAL);
        addConditionSafe(market, Conditions.STEALTH_MINEFIELDS);
        addConditionSafe(market, Conditions.POPULATION_5);

        // Industries
        List<String> industries = Arrays.asList(
                DimensionsCrossedIDS.STATION_POPULATION,
                DimensionsCrossedIDS.STARBASE_SPACEPORT,
                DimensionsCrossedIDS.PRAS_FLEETCOMMAND,
                DimensionsCrossedIDS.ORBITALSTATION_PRAS_MANSION_II,
                Industries.WAYSTATION,
                Industries.COMMERCE,
                DimensionsCrossedIDS.DRYDOCK,
                DimensionsCrossedIDS.DRYDOCK_ADVANCED,
                DimensionsCrossedIDS.UNIVERSITY,
                DimensionsCrossedIDS.R_N_D
        );
        for (String indId : industries) {
            market.addIndustry(indId);
        }

        // Alpha Core in every industry
        for (String indId : industries) {
            Industry ind = market.getIndustry(indId);
            if (ind != null) {
                ind.setAICoreId(Commodities.ALPHA_CORE);
            }
        }

        // Install special items
        setItemIfPresent(market, DimensionsCrossedIDS.DRYDOCK, Items.CORRUPTED_NANOFORGE);
        setItemIfPresent(market, DimensionsCrossedIDS.DRYDOCK_ADVANCED, Items.CORRUPTED_NANOFORGE);
        setItemIfPresent(market, Industries.COMMERCE, Items.DEALMAKER_HOLOSUITE);
        setItemIfPresent(market, DimensionsCrossedIDS.STARBASE_SPACEPORT, Items.PLASMA_DYNAMO);
        setItemIfPresent(market, DimensionsCrossedIDS.PRAS_FLEETCOMMAND, Items.CRYOARITHMETIC_ENGINE);

        // Register market with economy
        EconomyAPI econ = Global.getSector().getEconomy();
        econ.addMarket(market, true);

        // Ensure connected entity set includes the station itself (safe default)
        market.getConnectedEntities().add(prasStation2);

        // Force Mr. Pras (pras_mr_contact) as station admin and directory leader
        forcePrasAdminVanillaStyle(market);

        return market;
    }

    /**
     * Vanilla-style market admin creation/assignment:
     * - Uses stable person id: pras_mr_contact
     * - Persisted in ImportantPeople
     * - market.setAdmin(person)
     * - market.addPerson(person)
     * - commDirectory.addPerson(person, 0)
     */
    public static void forcePrasAdminVanillaStyle(MarketAPI market) {
        if (market == null) return;

        ImportantPeopleAPI ip = Global.getSector().getImportantPeople();
        PersonAPI admin = ip.getPerson(PRAS_CONTACT_ID);

        if (admin == null) {
            admin = Global.getFactory().createPerson();
            admin.setId(PRAS_CONTACT_ID);

            admin.setFaction(market.getFactionId());
            admin.setGender(FullName.Gender.MALE);

            admin.getName().setFirst("Administrator");
            admin.getName().setLast("Pras");

            admin.setRankId(Ranks.ARISTOCRAT);
            admin.setPostId(Ranks.POST_STATION_COMMANDER);
            admin.setImportance(PersonImportance.VERY_HIGH);

            admin.setPortraitSprite(Global.getSettings().getSpriteName("characters", "pras"));

            admin.getStats().setLevel(10);
            admin.getStats().setSkillLevel("industrial_planning", 3);

            ip.addPerson(admin);
        } else {
            admin.setFaction(market.getFactionId());
            admin.setPostId(Ranks.POST_STATION_COMMANDER);
            admin.getStats().setSkillLevel("industrial_planning", 3);
        }

        // Remove old/stale admin if present
        PersonAPI old = market.getAdmin();
        if (old != null && old != admin) {
            market.getCommDirectory().removePerson(old);
        }

        // Assign as sole admin and primary comm directory entry
        market.setAdmin(admin);
        market.addPerson(admin);
        market.getCommDirectory().removePerson(admin);
        market.getCommDirectory().addPerson(admin, 0);

        Global.getLogger(PrasMarket.class).info(
                "FORCED Pras admin: market=" + market.getId() +
                        " admin=" + market.getAdmin().getId() +
                        " (" + market.getAdmin().getNameString() + ")"
        );
    }

    private static void addConditionSafe(MarketAPI market, String conditionId) {
        if (market == null) return;
        if (conditionId == null || conditionId.isBlank()) return;

        try {
            market.addCondition(conditionId);
        } catch (Throwable ignored) {
            // If a condition id doesn't exist in this build/mod stack, skip silently.
        }
    }

    private static void setItemIfPresent(MarketAPI market, String industryId, String itemId) {
        if (market == null) return;
        if (industryId == null || itemId == null) return;

        Industry ind = market.getIndustry(industryId);
        if (ind == null) return;

        try {
            ind.setSpecialItem(new SpecialItemData(itemId, null));
        } catch (Throwable ignored) {
            // Some industries may reject items depending on their plugin/spec.
        }
    }
}