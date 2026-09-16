package UFP.data.logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UFP_CargoDebugger implements EveryFrameScript {

    private static final Logger log = Global.getLogger(UFP_CargoDebugger.class);

    private final Set<String> reportedBadIds = new HashSet<>();
    private boolean wasInDialog = false;

    @Override
    public boolean isDone() { return false; }

    @Override
    public boolean runWhilePaused() { return true; }

    @Override
    public void advance(float amount) {
        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();

        if (dialog != null) {
            if (!wasInDialog) {
                wasInDialog = true;
                reportedBadIds.clear();
                log.info("!!! UFP SHIELD AUDIT STARTED (PROTECTING GAME FROM CRASHES) !!!");
            }
            auditActiveCargos(dialog);
        } else {
            if (wasInDialog) {
                wasInDialog = false;
                log.info("!!! UFP SHIELD AUDIT ENDED (DIALOG CLOSED) !!!");
            }
        }
    }

    private void auditActiveCargos(InteractionDialogAPI dialog) {
        // 1. Audit Player Fleet
        if (Global.getSector().getPlayerFleet() != null && Global.getSector().getPlayerFleet().getCargo() != null) {
            auditAndPurgeCargo(Global.getSector().getPlayerFleet().getCargo(), "Player Fleet Cargo");
        }

        // 2. Audit Target Entity
        if (dialog.getInteractionTarget() != null) {
            if (dialog.getInteractionTarget().getCargo() != null) {
                auditAndPurgeCargo(dialog.getInteractionTarget().getCargo(), "Interaction Target (" + dialog.getInteractionTarget().getName() + ")");
            }

            // 3. Audit Market Submarkets
            MarketAPI market = dialog.getInteractionTarget().getMarket();
            if (market != null) {
                for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
                    if (submarket != null && submarket.getCargo() != null) {
                        auditAndPurgeCargo(submarket.getCargo(), "Submarket: " + submarket.getSpecId());
                    }
                }
            }
        }
    }

    private void auditAndPurgeCargo(CargoAPI cargo, String sourceName) {
        // Collect poisoned stacks to delete safely after iteration
        List<CargoStackAPI> deadStacks = new ArrayList<>();

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (stack == null || stack.isNull()) continue;

            if (stack.isSpecialStack()) {
                SpecialItemData data = stack.getSpecialDataIfSpecial();
                if (data != null) {
                    String specId = data.getId();

                    // Target both possible hullmod chip spec IDs
                    if ("mod_spec".equals(specId) || "modspec".equals(specId)) {
                        String hullmodId = data.getData();

                        boolean isPoisoned = false;
                        if (hullmodId == null || hullmodId.isEmpty()) {
                            isPoisoned = true;
                            logErrorOnce(sourceName + "_NULL", "[!!!] " + sourceName + " has a ModSpec with a NULL/EMPTY ID!");
                        } else if (Global.getSettings().getHullModSpec(hullmodId) == null) {
                            isPoisoned = true;
                            logErrorOnce(sourceName + "_" + hullmodId, "[!!!] " + sourceName + " has unregistered/poisoned hullmod ID: '" + hullmodId + "'");
                        }

                        if (isPoisoned) {
                            deadStacks.add(stack);
                        }
                    }
                }
            }
        }

        // --- THE FIX: Purge the poisoned items before the game UI tries to draw them! ---
        if (!deadStacks.isEmpty()) {
            for (CargoStackAPI poison : deadStacks) {
                try {
                    cargo.removeStack(poison);
                    log.warn("  >> [CRASH PREVENTED] Safely vaporized poisoned item: '" + poison.getDisplayName() + "' from " + sourceName);
                } catch (Exception e) {
                    log.error("  >> [ERROR] Failed to vaporize poisoned stack!", e);
                }
            }
        }
    }

    private void logErrorOnce(String key, String message) {
        if (!reportedBadIds.contains(key)) {
            reportedBadIds.add(key);
            log.error(message);
        }
    }
}