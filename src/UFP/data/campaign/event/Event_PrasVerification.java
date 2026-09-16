package UFP.data.campaign.event;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

public class Event_PrasVerification extends BaseCommandPlugin implements InteractionDialogPlugin {

    public static final String PASSCODE = "1225";
    public static final String MEM_VERIFIED = "$ufp_pras_verified";
    public static final String MEM_VERIFICATION_ATTEMPTED = "$ufp_pras_verificationAttempted";
    public static final String MEM_STAGE = "$ufp_pras_stage";
    public static final String MEM_REWARD_CLAIMED = "$ufp_pras_rewardClaimed";
    public static final String SHIP_REWARD_VARIANT = "fed_defiantPras_private";
    public static final String PRAS_ID = "pras_mr_contact";

    private static final int STAGE_NONE = 0, STAGE_GREETED = 1, STAGE_PIN_REQUESTED = 2;

    protected InteractionDialogAPI dialog;
    protected InteractionDialogPlugin originalPlugin;
    protected Map<String, MemoryAPI> memoryMap;

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params.isEmpty()) return false;

        this.dialog = dialog;
        this.memoryMap = memoryMap;

        PersonAPI pras = Global.getSector().getImportantPeople().getPerson(PRAS_ID);
        if (pras != null && !dialog.getVisualPanel().isShowingPersonInfo(pras)) {
            dialog.getVisualPanel().showPersonInfo(pras);
        }

        String action = params.get(0).getString(memoryMap);
        MemoryAPI mem = memoryMap.get(MemKeys.LOCAL);

        switch (action) {
            case "hello":
                mem.set(MEM_STAGE, STAGE_GREETED);
                mem.set(MEM_VERIFIED, false);
                mem.set(MEM_VERIFICATION_ATTEMPTED, false);
                return true;
            case "yes":
                mem.set(MEM_STAGE, STAGE_PIN_REQUESTED);
                return true;
            case "openInput":
                if (mem.getInt(MEM_STAGE) < STAGE_PIN_REQUESTED) return false;
                dialog.showCustomDialog(520f, 220f, new CodeEntryDialog(dialog));
                return true;
            case "postVerify":
                if (!mem.getBoolean(MEM_VERIFIED)) {
                    spawnAndStartCullingTaskForce(dialog);
                }
                return true;
            case "reward":
                grantRewardShip(dialog, mem);
                return true;
            case "attack":
                spawnAndStartCullingTaskForce(dialog);
                return true;
            case "leave":
            case "no":
                mem.set(MEM_STAGE, STAGE_NONE);
                dialog.dismissAsCancel();
                return true;
            default:
                return false;
        }
    }

    private class CodeEntryDialog implements CustomDialogDelegate {
        private final InteractionDialogAPI parent;
        private TextFieldAPI textField;

        CodeEntryDialog(InteractionDialogAPI parent) { this.parent = parent; }

        @Override
        public void createCustomDialog(com.fs.starfarer.api.ui.CustomPanelAPI panel, CustomDialogCallback callback) {
            TooltipMakerAPI ui = panel.createUIElement(500f, 210f, false);
            ui.addPara("Enter the identity code:", 10f, Misc.getTextColor(), Misc.getHighlightColor(), PASSCODE);

            textField = ui.addTextField(260f, 28f, Fonts.DEFAULT_SMALL, 10f);
            panel.addUIElement(ui).inTL(10f, 10f);

            textField.grabFocus();
        }

        @Override
        public void customDialogConfirm() {
            String input = textField.getText().trim().replaceAll("[^0-9]", "");
            boolean ok = PASSCODE.equals(input);

            MemoryAPI mem = parent.getPlugin().getMemoryMap().get(MemKeys.LOCAL);
            mem.set(MEM_VERIFICATION_ATTEMPTED, true);
            mem.set(MEM_VERIFIED, ok);

            mem.unset("$option");

            if (ok) {
                parent.getPlugin().optionSelected(null, "ufp_pras_verify_success_trigger");
            } else {
                parent.getPlugin().optionSelected(null, "ufp_pras_verify_fail_trigger");
            }
        }

        @Override public void customDialogCancel() {}
        @Override public boolean hasCancelButton() { return true; }
        @Override public String getConfirmText() { return "Submit"; }
        @Override public String getCancelText() { return "Cancel"; }
        @Override public com.fs.starfarer.api.campaign.CustomUIPanelPlugin getCustomPanelPlugin() { return null; }
    }

    private void grantRewardShip(InteractionDialogAPI dialog, MemoryAPI mem) {
        if (mem.getBoolean(MEM_REWARD_CLAIMED)) return;

        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, SHIP_REWARD_VARIANT);
        member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
        Global.getSector().getPlayerFleet().getFleetData().addFleetMember(member);

        mem.set(MEM_REWARD_CLAIMED, true);

        dialog.getTextPanel().addPara("Received ship: " + SHIP_REWARD_VARIANT, Misc.getPositiveHighlightColor());
    }

    private void spawnAndStartCullingTaskForce(InteractionDialogAPI dialog) {
        this.originalPlugin = dialog.getPlugin();

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        LocationAPI location = player != null ? player.getContainingLocation() : null;

        // 1. Create a temporary fleet so campaign proximity scanning doesn't pull in nearby system fleets
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(DimensionsCrossedIDS.UFP, "Culling TaskForce", true);
        if (location != null) {
            fleet.setContainingLocation(location);
            fleet.setLocation(player.getLocation().x, player.getLocation().y);
        }

        addShips(fleet, "fed_sovereign_standard", 8);
        addShips(fleet, "fed_Galaxy", 6);
        addShips(fleet, "fed_ambassador_refit", 4);
        addShips(fleet, "fed_norway", 10);
        addShips(fleet, "fed_intrepid", 6);
        addShips(fleet, "fed_defiant", 16);
        fleet.setCommander(OfficerManagerEvent.createOfficer(Global.getSector().getFaction(DimensionsCrossedIDS.UFP), 20, true));

        // 2. Explicitly configure battle flags to force pure fight engagement
        BattleCreationContext context = new BattleCreationContext(player, FleetGoal.ATTACK, fleet, FleetGoal.ATTACK);
        context.aiRetreatAllowed = false;
        context.fightToTheLast = true;
        context.enemyDeployAll = true;

        // 3. Hijack dialogue plugin handling prior to starting battle
        dialog.setPlugin(this);
        dialog.startBattle(context);
    }

    private void addShips(CampaignFleetAPI fleet, String vid, int count) {
        for (int i = 0; i < count; i++) {
            fleet.getFleetData().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.SHIP, vid));
        }
    }

    // --- InteractionDialogPlugin Implementation ---

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
        if (originalPlugin != null) {
            dialog.setPlugin(originalPlugin);
        }
        dialog.dismissAsCancel();
    }

    @Override public void init(InteractionDialogAPI dialog) { if (originalPlugin != null) originalPlugin.init(dialog); }
    @Override public void optionSelected(String optionText, Object optionData) { if (originalPlugin != null) originalPlugin.optionSelected(optionText, optionData); }
    @Override public void optionMousedOver(String optionText, Object optionData) { if (originalPlugin != null) originalPlugin.optionMousedOver(optionText, optionData); }
    @Override public void advance(float amount) { if (originalPlugin != null) originalPlugin.advance(amount); }
    @Override public Object getContext() { return originalPlugin != null ? originalPlugin.getContext() : null; }
    @Override public Map<String, MemoryAPI> getMemoryMap() { return memoryMap; }
}