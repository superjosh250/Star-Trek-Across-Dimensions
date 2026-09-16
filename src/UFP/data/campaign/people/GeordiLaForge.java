package UFP.data.campaign.people;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

public class GeordiLaForge {

    public static final String PERSON_ID = "ufp_geordi_la_forge";

    public static PersonAPI create(MarketAPI market) {
        PersonAPI geordi = Global.getSector().getImportantPeople().getPerson(PERSON_ID);
        if (geordi != null) return geordi;

        geordi = Global.getFactory().createPerson();
        geordi.setId(PERSON_ID);
        geordi.setName(new FullName("Geordi", "La Forge", FullName.Gender.MALE));
        geordi.setFaction(DimensionsCrossedIDS.UFP);
        geordi.setPortraitSprite("graphics/portraits/characters/geordi.png");

        // Proper Rank and Post IDs
        geordi.setRankId(DimensionsCrossedIDS.STARFLEET_COMMODORE);             // Sets military rank to Commodore
        geordi.setPostId(Ranks.POST_STATION_COMMANDER);     // Sets station duty post

        if (market != null) {
            geordi.setMarket(market);
            market.addPerson(geordi);
            market.getCommDirectory().addPerson(geordi);
        }

        Global.getSector().getImportantPeople().addPerson(geordi);
        return geordi;
    }
}