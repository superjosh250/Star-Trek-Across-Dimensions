package data.scripts.starsystems;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.util.Misc;

// ADD THESE IMPORTS:
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;

public class VirelliaMarket {

    // ------------------------------------------------------------
    // IMPORTANT PERSON CONFIG
    // ------------------------------------------------------------
    public static final String VIRELLIA_MARKET_ID = "vir_custom_2_market";
    public static final String PERSON_DAEMON_LOURD_ID = "vir_daemon_lourd";

    public static void addMarkets() {
        StarSystemAPI system = Global.getSector().getStarSystem("Virellia");
        if (system == null) return;

        SectorEntityToken entity = system.getEntityById("vir_custom_2");
        if (entity == null) return;

        // ------------------------------------------------------------
        // Create market
        // ------------------------------------------------------------
        MarketAPI market = Global.getFactory().createMarket(
                VIRELLIA_MARKET_ID,
                entity.getName(),
                10
        );
        market.setPrimaryEntity(entity);
        market.setFactionId(Factions.INDEPENDENT);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
        market.setHidden(false);

        // ------------------------------------------------------------
        // CONDITIONS
        // ------------------------------------------------------------
        market.addCondition(Conditions.HABITABLE);
        market.addCondition(Conditions.MILD_CLIMATE);
        market.addCondition(Conditions.OUTPOST);
        market.addCondition(Conditions.POPULATION_10);
        market.addCondition(Conditions.VICE_DEMAND);
        market.addCondition(Conditions.RUINS_VAST);
        market.addCondition(Conditions.REGIONAL_CAPITAL);

        // ------------------------------------------------------------
        // INDUSTRIES
        // ------------------------------------------------------------
        market.addIndustry(Industries.POPULATION);
        market.addIndustry(Industries.WAYSTATION);
        market.addIndustry(Industries.MEGAPORT);
        market.addIndustry(Industries.COMMERCE);
        market.addIndustry(Industries.HEAVYBATTERIES);
        market.addIndustry(Industries.STARFORTRESS_HIGH);
        market.addIndustry(Industries.TECHMINING);
        market.addIndustry(DimensionsCrossedIDS.R_N_D);
        market.addIndustry(DimensionsCrossedIDS.UNIVERSITY);
        market.addIndustry(DimensionsCrossedIDS.COALITION_PATROL);
        market.addIndustry(Industries.CRYOREVIVAL);
        market.addIndustry(Industries.CRYOSANCTUM);

        // ------------------------------------------------------------
        // SUBMARKETS
        // ------------------------------------------------------------
        market.addSubmarket(Submarkets.SUBMARKET_OPEN);
        market.addSubmarket(Submarkets.GENERIC_MILITARY);
        market.addSubmarket(DimensionsCrossedIDS.SUBMARKET_COALITION);
        market.addSubmarket(Submarkets.SUBMARKET_BLACK);
        market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
        market.addSubmarket(Submarkets.LOCAL_RESOURCES);

        // ------------------------------------------------------------
        // Finalize
        // ------------------------------------------------------------
        entity.setMarket(market);
        Global.getSector().getEconomy().addMarket(market, true);

        // ✅ INJECT AS SOON AS THE MARKET EXISTS (new game / new economy)
        ensureDaemonLourd(market);
    }

    public static void injectDaemonLourdIntoMarketId(String marketId) {
        if (marketId == null) return;
        if (Global.getSector() == null || Global.getSector().getEconomy() == null) return;
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null) return;
        ensureDaemonLourd(market);
    }

    private static void ensureDaemonLourd(MarketAPI market) {
        if (market == null) return;

        ImportantPeopleAPI ip = Global.getSector().getImportantPeople();

        // 1) Reuse existing person if already registered
        PersonAPI daemon = ip.getPerson(PERSON_DAEMON_LOURD_ID);

        // 2) If not found in Important People, try to find by id in market people
        if (daemon == null) {
            for (PersonAPI p : market.getPeopleCopy()) {
                if (PERSON_DAEMON_LOURD_ID.equals(p.getId())) {
                    daemon = p;
                    break;
                }
            }
        }

        // 3) If still null, create fresh + register globally
        if (daemon == null) {
            daemon = createDaemonLourd();
            ip.addPerson(daemon); // global persistence
        } else {
            normalizeDaemonLourd(daemon);
        }

        // 4) Attach to this market
        daemon.setMarket(market);

        // Avoid duplicates in market person list
        boolean alreadyInMarket = false;
        for (PersonAPI p : market.getPeopleCopy()) {
            if (PERSON_DAEMON_LOURD_ID.equals(p.getId())) {
                alreadyInMarket = true;
                break;
            }
        }
        if (!alreadyInMarket) {
            market.addPerson(daemon);
        }

        // 5) Make admin (and ensure comm directory entry is present & visible)
        market.setAdmin(daemon);

        // Pin to top of comm directory; use id-based lookup to avoid instance edge cases
        if (market.getCommDirectory().getEntryForPerson(PERSON_DAEMON_LOURD_ID) == null) {
            market.getCommDirectory().addPerson(daemon, 0);
        } else {
            // Re-pin to top
            market.getCommDirectory().removePerson(daemon);
            market.getCommDirectory().addPerson(daemon, 0);
        }

        if (market.getCommDirectory().getEntryForPerson(PERSON_DAEMON_LOURD_ID) != null) {
            market.getCommDirectory().getEntryForPerson(PERSON_DAEMON_LOURD_ID).setHidden(false);
        }
    }

    private static PersonAPI createDaemonLourd() {
        PersonAPI p = Global.getFactory().createPerson();
        p.setId(PERSON_DAEMON_LOURD_ID);
        normalizeDaemonLourd(p);
        return p;
    }

    private static void normalizeDaemonLourd(PersonAPI p) {
        // Market is independent in your setup
        p.setFaction(Factions.INDEPENDENT);

        // Name
        p.setGender(FullName.Gender.MALE);
        p.getName().setFirst("Daemon");
        p.getName().setLast("Lourd");

        // "Admin / Station Commander"
        // Ranks constants include SPACE_COMMANDER and POST_STATION_COMMANDER
        p.setRankId(Ranks.SPACE_COMMANDER);
        p.setPostId(Ranks.POST_STATION_COMMANDER);

        // Make them show up as a major figure
        p.setImportance(PersonImportance.VERY_HIGH);

        // Optional: if you want a specific portrait/voice, uncomment & set valid ids
        // p.setPortraitSprite(Global.getSettings().getSpriteName("characters", "your_portrait_id"));
        // p.setVoice(Voices.SOLDIER);

        // Optional: contact tags (purely optional)
        // p.addTag(Tags.CONTACT_MILITARY);
    }
}
