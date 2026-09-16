package UFP.data.campaign.event;

import UFP.data.campaign.ids.DimensionsCrossedIDS;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
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

public class USS_SovereignEvent extends BaseCommandPlugin implements InteractionDialogPlugin {

    public static final String KEY_RECRUITED = "$uss_sovereign_recruited";
    public static final String KEY_SIM_WON = "$uss_sovereign_sim_won";
    public static final String KEY_SIM_FAILED = "$uss_sovereign_sim_failed";

    protected InteractionDialogAPI dialog;
    protected InteractionDialogPlugin originalPlugin;
    protected Map<String, MemoryAPI> memoryMap;
    protected CampaignFleetAPI simEnemyFleet;

    public static boolean isRecruited() {
        return Global.getSector().getMemoryWithoutUpdate().getBoolean(KEY_RECRUITED);
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;

        this.dialog = dialog;
        this.memoryMap = memoryMap;
        this.originalPlugin = dialog.getPlugin();

        MemoryAPI globalMemory = Global.getSector().getMemoryWithoutUpdate();
        MemoryAPI targetMemory = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMemoryWithoutUpdate() : null;

        clearFlags(globalMemory, targetMemory);

        if (isRecruited()) {
            FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
            return true;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        LocationAPI location = playerFleet != null ? playerFleet.getContainingLocation() : null;

        // 1. Build Temporary Player Fleet (1x USS Sovereign)
        CampaignFleetAPI simPlayerFleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, "Simulation Fleet", true);
        if (location != null) {
            simPlayerFleet.setContainingLocation(location);
            simPlayerFleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        }

        FleetMemberAPI playerShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.USS_SOVEREIGN);
        playerShip.setShipName("Sovereign");
        simPlayerFleet.getFleetData().addFleetMember(playerShip);
        simPlayerFleet.getFleetData().setFlagship(playerShip);

        // 2. Build Temporary Enemy Fleet (2x Melak)
        simEnemyFleet = Global.getFactory().createEmptyFleet(Factions.NEUTRAL, "Simulation Targets", true);
        if (location != null) {
            simEnemyFleet.setContainingLocation(location);
            simEnemyFleet.setLocation(playerFleet.getLocation().x, playerFleet.getLocation().y);
        }

        for (int i = 0; i < 2; i++) {
            FleetMemberAPI enemy = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.MELAK_MISSION);
            PersonAPI captain = Global.getFactory().createPerson();
            captain.setPersonality(Personalities.RECKLESS);
            enemy.setCaptain(captain);
            simEnemyFleet.getFleetData().addFleetMember(enemy);
        }

        // 3. Setup Isolated Custom Battle Context
        BattleCreationContext context = new BattleCreationContext(simPlayerFleet, FleetGoal.ATTACK, simEnemyFleet, FleetGoal.ATTACK);
        context.aiRetreatAllowed = false;
        context.fightToTheLast = true;
        context.enemyDeployAll = true;

        // Route dialogue callbacks through this plugin
        dialog.setPlugin(this);
        dialog.startBattle(context);

        return true;
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
        // Restore standard interaction dialog handler
        dialog.setPlugin(originalPlugin);

        MemoryAPI globalMemory = Global.getSector().getMemoryWithoutUpdate();
        MemoryAPI targetMemory = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getMemoryWithoutUpdate() : null;

        boolean won = false;

        if (battleResult != null) {
            if (battleResult.getLoserResult() != null && battleResult.getLoserResult().getFleet() == simEnemyFleet) {
                won = true;
            } else if (battleResult.getWinnerResult() != null && battleResult.getWinnerResult().getFleet() != simEnemyFleet) {
                won = true;
            }
        }

        if (won) {
            setFlag(KEY_RECRUITED, true, globalMemory, targetMemory);
            setFlag(KEY_SIM_WON, true, globalMemory, targetMemory);

            // Strip event target tag from Starbase 12 so standard market dialog takes back control
            StarSystemAPI system = Global.getSector().getStarSystem("Holland");
            if (system != null) {
                SectorEntityToken platform = system.getEntityById("holland_nh_platform");
                if (platform != null) {
                    platform.getMemoryWithoutUpdate().unset("$uss_sovereign_event_target");
                    platform.getMemoryWithoutUpdate().unset(KEY_SIM_FAILED);
                    platform.getMemoryWithoutUpdate().unset(KEY_SIM_WON);
                }
            }
            if (targetMemory != null) {
                targetMemory.unset("$uss_sovereign_event_target");
                targetMemory.unset(KEY_SIM_FAILED);
                targetMemory.unset(KEY_SIM_WON);
            }

            // Reward Player with flagship
            FleetMemberAPI rewardShip = Global.getFactory().createFleetMember(FleetMemberType.SHIP, DimensionsCrossedIDS.USS_SOVEREIGN);
            rewardShip.setShipName("USS Sovereign");
            Global.getSector().getPlayerFleet().getFleetData().addFleetMember(rewardShip);
        } else {
            // Set failure flags if player lost or retreated
            setFlag(KEY_SIM_FAILED, true, globalMemory, targetMemory);
        }

        // Clear button panel to stop stale options from duplicating on UI return
        if (dialog.getOptionPanel() != null) {
            dialog.getOptionPanel().clearOptions();
        }

        // Broadcast to rules.csv to trigger sovereign_sim_won or sovereign_sim_failed
        FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
    }

    private void setFlag(String key, boolean value, MemoryAPI globalMem, MemoryAPI targetMem) {
        if (globalMem != null) globalMem.set(key, value);
        if (targetMem != null) targetMem.set(key, value);
    }

    private void clearFlags(MemoryAPI globalMem, MemoryAPI targetMem) {
        String[] keys = {KEY_SIM_WON, KEY_SIM_FAILED};
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