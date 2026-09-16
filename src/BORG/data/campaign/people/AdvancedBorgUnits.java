package BORG.data.campaign.people;

import BORG.data.campaign.ids.BorgIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

public class AdvancedBorgUnits {

    /** Stable person id */
    public static final String PERSON_SAMARA_UNIT_01 = "samara_unit01";

    /** Preferred target market id (as used in your market/entity naming) */
    public static final String MARKET_LOC001_ASSIMILATED_TERRAN = "loc001_assimilated_terran";

    private AdvancedBorgUnits() {
        // utility class
    }

    public static void injectSamaraUnit01_Planet001() {
        injectSamaraUnit01IntoMarket(MARKET_LOC001_ASSIMILATED_TERRAN);
    }

    /**
     * Inject Samara into a market by id.
     * Safe to call multiple times.
     */
    public static void injectSamaraUnit01IntoMarket(String marketId) {
        if (marketId == null) return;

        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null) return;

        ensureSamaraUnit01(market);
    }

    /**
     * Inject Samara into a given market instance.
     * Safe to call multiple times.
     */
    public static void injectSamaraUnit01IntoMarket(MarketAPI market) {
        if (market == null) return;
        ensureSamaraUnit01(market);
    }

    // ----------------------------
    // Internal implementation
    // ----------------------------

    private static void ensureSamaraUnit01(MarketAPI market) {
        ImportantPeopleAPI ip = Global.getSector().getImportantPeople();

        // 1) Reuse existing person if already registered (prevents duplicates)
        PersonAPI samara = ip.getPerson(PERSON_SAMARA_UNIT_01);

        // 2) If not found in Important People, try to find by id in the market's existing people
        if (samara == null) {
            for (PersonAPI p : market.getPeopleCopy()) {
                if (PERSON_SAMARA_UNIT_01.equals(p.getId())) {
                    samara = p;
                    break;
                }
            }
        }

        // 3) If still null, create fresh
        if (samara == null) {
            samara = createSamaraUnit01();
            ip.addPerson(samara); // global persistence
        } else {
            // Ensure core identity stays correct even if something overwrote it
            normalizeSamaraUnit01(samara);
        }

        // 4) Attach to this market
        samara.setMarket(market);

        // Avoid duplicates in market person list
        boolean alreadyInMarket = false;
        for (PersonAPI p : market.getPeopleCopy()) {
            if (PERSON_SAMARA_UNIT_01.equals(p.getId())) {
                alreadyInMarket = true;
                break;
            }
        }
        if (!alreadyInMarket) {
            market.addPerson(samara);
        }

        // --- Admin + Comm Directory patch ---
        // IMPORTANT: Set as admin first (helps with some UI / rebuild edge-cases)
        market.setAdmin(samara);

        // Ensure comm entry exists, is visible, and pinned to top
        // Use personId-based lookup to avoid edge cases with different PersonAPI instances.
        if (market.getCommDirectory().getEntryForPerson(PERSON_SAMARA_UNIT_01) == null) {
            market.getCommDirectory().addPerson(samara, 0);
        } else {
            // Re-pin to top
            market.getCommDirectory().removePerson(samara);
            market.getCommDirectory().addPerson(samara, 0);
        }

        if (market.getCommDirectory().getEntryForPerson(PERSON_SAMARA_UNIT_01) != null) {
            market.getCommDirectory().getEntryForPerson(PERSON_SAMARA_UNIT_01).setHidden(false);
        }
        // --- end patch ---
    }

    private static PersonAPI createSamaraUnit01() {
        PersonAPI p = Global.getFactory().createPerson();
        p.setId(PERSON_SAMARA_UNIT_01);
        normalizeSamaraUnit01(p);
        return p;
    }

    private static void normalizeSamaraUnit01(PersonAPI p) {
        // Faction: ensure borg; if borg faction doesn't exist for some reason,
        // fall back to neutral so it doesn't crash/soft-break.
        String factionId = Global.getSector().getFaction(BorgIDS.BORG) != null ? BorgIDS.BORG : Factions.NEUTRAL;
        p.setFaction(factionId);

        p.setGender(FullName.Gender.FEMALE);
        p.setRankId(Ranks.FACTION_LEADER);
        p.setPostId(Ranks.POST_ADMINISTRATOR);
        p.setImportance(PersonImportance.VERY_HIGH);

        p.getName().setFirst("Samara");
        p.getName().setLast("Unit-01");

        p.setPortraitSprite(Global.getSettings().getSpriteName("characters", "samara"));
        p.setVoice(Voices.VILLAIN);

        // Skill: Industrial Planning
        p.getStats().setSkillLevel(Skills.INDUSTRIAL_PLANNING, 3);
        p.getStats().setLevel(3);

        // Optional: contact tag (kept from your prior setup)
        p.addTag(Tags.CONTACT_MILITARY);
    }
}