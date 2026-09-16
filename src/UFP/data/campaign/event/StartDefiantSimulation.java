package UFP.data.campaign.event;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class StartDefiantSimulation extends BaseCommandPlugin implements InteractionDialogPlugin {

    private static final String SHIP_NAME_PLAYER = "USS Defiant";
    private static final String SHIP_NAME_ENEMY = "USS Lakota";

    protected InteractionDialogAPI dialog;
    protected InteractionDialogPlugin originalPlugin;
    protected Map<String, MemoryAPI> memoryMap;
    protected CampaignFleetAPI simEnemyFleet;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        this.dialog = dialog;
        this.memoryMap = memoryMap;
        this.originalPlugin = dialog.getPlugin();

        MemoryAPI globalMemory = Global.getSector().getMemoryWithoutUpdate();
        MemoryAPI targetMemory = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMemoryWithoutUpdate() : null;

        clearFlags(globalMemory, targetMemory);

        if (playerOwnsDefiant()) {
            setFlag("$ufp_has_defiant", true, globalMemory, targetMemory);
            FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
            return true;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        LocationAPI location = playerFleet != null ? playerFleet.getContainingLocation() : null;

        CampaignFleetAPI simPlayerFleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Simulation Fleet", true);
        if (location != null) {
            simPlayerFleet.setContainingLocation(location);
            simPlayerFleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        }

        FleetMemberAPI playerShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.USS_DEFIANT);
        playerShip.setShipName(SHIP_NAME_PLAYER);
        simPlayerFleet.getFleetData().addFleetMember(playerShip);
        simPlayerFleet.getFleetData().setFlagship(playerShip);

        simEnemyFleet = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, "Target Vessel", true);
        if (location != null) {
            simEnemyFleet.setContainingLocation(location);
            simEnemyFleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        }

        FleetMemberAPI enemyShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.USS_LAKOTA);
        enemyShip.setShipName(SHIP_NAME_ENEMY);

        PersonAPI captain = Global.getFactory().createPerson();
        captain.setPersonality(Personalities.RECKLESS);
        enemyShip.setCaptain(captain);

        simEnemyFleet.getFleetData().addFleetMember(enemyShip);

        BattleCreationContext context = new BattleCreationContext(simPlayerFleet, FleetGoal.ATTACK, simEnemyFleet, FleetGoal.ATTACK);
        context.aiRetreatAllowed = false;
        context.fightToTheLast = true;
        context.enemyDeployAll = true;

        dialog.setPlugin(this);
        dialog.startBattle(context);

        return true;
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
        dialog.setPlugin(originalPlugin);

        MemoryAPI globalMemory = Global.getSector().getMemoryWithoutUpdate();
        MemoryAPI targetMemory = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMemoryWithoutUpdate() : null;

        boolean defeatedLakota = false;

        if (battleResult != null) {
            if (battleResult.getLoserResult() != null && battleResult.getLoserResult().getFleet() == simEnemyFleet) {
                defeatedLakota = !battleResult.getLoserResult().getDestroyed().isEmpty()
                        || !battleResult.getLoserResult().getDisabled().isEmpty();
            } else if (battleResult.getWinnerResult() != null && battleResult.getWinnerResult().getFleet() == simEnemyFleet) {
                defeatedLakota = !battleResult.getWinnerResult().getDestroyed().isEmpty()
                        || !battleResult.getWinnerResult().getDisabled().isEmpty();
            }
        }

        if (defeatedLakota) {
            FleetMemberAPI rewardShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.USS_DEFIANT);
            rewardShip.setShipName(SHIP_NAME_PLAYER);
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(rewardShip);
            setFlag("$ufp_sim_defiant_won", true, globalMemory, targetMemory);
        } else {
            setFlag("$ufp_sim_defiant_failed", true, globalMemory, targetMemory);
        }

        FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
    }

    private boolean playerOwnsDefiant() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return false;

        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member == null) continue;
            if (DimensionsCrossedIDS.USS_DEFIANT.equalsIgnoreCase(member.getHullId())) return true;
            if (member.getVariant() != null && DimensionsCrossedIDS.USS_DEFIANT.equalsIgnoreCase(member.getVariant().getHullVariantId())) return true;
            if (SHIP_NAME_PLAYER.equalsIgnoreCase(member.getShipName())) return true;
        }
        return false;
    }

    private void setFlag(String key, boolean value, MemoryAPI globalMem, MemoryAPI targetMem) {
        if (globalMem != null) globalMem.set(key, value);
        if (targetMem != null) targetMem.set(key, value);
    }

    private void clearFlags(MemoryAPI globalMem, MemoryAPI targetMem) {
        String[] keys = {"$ufp_has_defiant", "$ufp_sim_defiant_won", "$ufp_sim_defiant_failed"};
        for (String key : keys) {
            if (globalMem != null) globalMem.unset(key);
            if (targetMem != null) targetMem.unset(key);
        }
    }

    @Override public void init(InteractionDialogAPI dialog) { if (originalPlugin != null) originalPlugin.init(dialog); }
    @Override public void optionSelected(String optionText, Object optionData) { if (originalPlugin != null) originalPlugin.optionSelected(optionText, optionData); }
    @Override public void optionMousedOver(String optionText, Object optionData) { if (originalPlugin != null) originalPlugin.optionMousedOver(optionText, optionData); }
    @Override public void advance(float amount) { if (originalPlugin != null) originalPlugin.advance(amount); }
    @Override public Object getContext() { return originalPlugin != null ? originalPlugin.getContext() : null; }
    @Override public Map<String, MemoryAPI> getMemoryMap() { return memoryMap; }
}