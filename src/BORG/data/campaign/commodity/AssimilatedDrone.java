package BORG.data.campaign.commodity;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * Keeps two custom commodities ("drones" and "assimilated_civilians") acting as usable CREW.
 *
 * How it works:
 * - The game only counts the built-in crew counter (CargoAPI.getCrew()).
 * - We "inject" a number of crew equal to drones+civilians so ships/fleets meet crew requirements.
 * - We track injected vs. real crew in player memory, so we do NOT overwrite/erase normal crew.
 * - When the game removes crew (combat losses, accidents etc.), we attribute the loss to injected crew first
 *   and remove an equal number of drones/civilians from cargo (outside of dialogs/markets).
 *
 * NOTE: This does NOT by itself change what cargo *space* the drone/civilian commodities use.
 * To avoid them consuming regular cargo capacity you must set their cargo space in commodities.csv to 0.
 */
public final class AssimilatedDrone {
    public static final String DRONES = "drones";
    public static final String CIVILIANS = "assimilated_civilians";

    private AssimilatedDrone() {}

    /** Call from your ModPlugin (onGameLoad/onNewGame) to enable the sync script. */
    public static void install() {
        if (Global.getSector() == null) return;

        // Prevent duplicate installs
        for (EveryFrameScript script : Global.getSector().getScripts()) {
            if (script instanceof BorgCrewSyncScript) return;
        }

        Global.getSector().addScript(new BorgCrewSyncScript());
    }

    public static class BorgCrewSyncScript implements EveryFrameScript {
        private static final String MEM_INIT = "$borgCrewSyncInit";
        private static final String MEM_REAL = "$borgRealCrew";
        private static final String MEM_INJECTED = "$borgInjectedCrew";

        // throttle when NOT in dialogs; dialogs often pass amount==0, so we handle that separately
        private float timer = 0f;
        private static final float UPDATE_INTERVAL = 0.10f;

        @Override
        public void advance(float amount) {
            if (Global.getCurrentState() != GameState.CAMPAIGN) return;
            if (Global.getSector() == null) return;

            // Detect whether player is in a dialog (market/cargo screen etc.)
            boolean inDialog = Global.getSector().getCampaignUI() != null
                    && Global.getSector().getCampaignUI().isShowingDialog();

            // Throttle only when NOT in a dialog.
            // In dialogs, amount is often 0 so throttling can prevent updates entirely.
            if (!inDialog) {
                timer += amount;
                if (timer < UPDATE_INTERVAL) return;
                timer = 0f;
            }

            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            if (player == null) return;

            CargoAPI cargo = player.getCargo();
            if (cargo == null) return;

            MemoryAPI mem = player.getMemoryWithoutUpdate();

            int totalCrewNow = cargo.getCrew();

            // Initialize memory on first run
            if (!mem.getBoolean(MEM_INIT)) {
                mem.set(MEM_INIT, true);
                mem.set(MEM_REAL, (float) totalCrewNow);
                mem.set(MEM_INJECTED, 0f);
            }

            int memReal = (int) mem.getFloat(MEM_REAL);
            int memInjected = (int) mem.getFloat(MEM_INJECTED);
            if (memReal < 0) memReal = 0;
            if (memInjected < 0) memInjected = 0;

            // Compute available borg "crew power" from commodities
            float borgPowerF = cargo.getCommodityQuantity(DRONES) + cargo.getCommodityQuantity(CIVILIANS);
            int desiredInjected = Math.max(0, (int) Math.floor(borgPowerF));

            // Estimate how much "real crew" exists right now based on previous injected value
            int estimatedRealNow = totalCrewNow - memInjected;
            if (estimatedRealNow < 0) estimatedRealNow = 0;

            if (inDialog) {
                // IMPORTANT FIX:
                // In dialogs, assume changes to vanilla crew are intentional (buy/sell).
                // So accept the new "real crew" level instead of treating it as a loss.
                memReal = estimatedRealNow;
                mem.set(MEM_REAL, (float) memReal);
            } else {
                // Out in campaign: treat reductions in real crew as "loss events".
                // Pay losses from borg commodities first, WITHOUT generating free crew.
                if (estimatedRealNow < memReal) {
                    int loss = memReal - estimatedRealNow;

                    // How many borg units can actually be spent to replace losses?
                    int availableBorg = Math.max(0, desiredInjected); // equals floor(drones+civs)

                    int restore = Math.min(loss, availableBorg);
                    if (restore > 0) {
                        cargo.addCrew(restore);
                        consumeBorg(cargo, restore);
                    }

                    // If borg couldn't cover all losses, real crew baseline must decrease
                    int uncovered = loss - restore;
                    if (uncovered > 0) {
                        memReal = memReal - uncovered;
                        if (memReal < 0) memReal = 0;
                        mem.set(MEM_REAL, (float) memReal);
                    }

                    // Update totalCrewNow after restoration
                    totalCrewNow = cargo.getCrew();
                }
            }

            // Inject exactly desiredInjected as extra crew ON TOP of memReal
            int desiredTotalCrew = memReal + desiredInjected;

            int diff = desiredTotalCrew - totalCrewNow;
            if (diff > 0) {
                cargo.addCrew(diff);
            } else if (diff < 0) {
                cargo.removeCrew(-diff);
            }

            // Persist injected amount
            mem.set(MEM_INJECTED, (float) desiredInjected);

            // Persist real crew as whatever remains after injection is accounted for
            int newTotal = cargo.getCrew();
            int newReal = newTotal - desiredInjected;
            if (newReal < 0) newReal = 0;
            mem.set(MEM_REAL, (float) newReal);
        }

        private void consumeBorg(CargoAPI cargo, int amount) {
            if (amount <= 0) return;

            float drones = cargo.getCommodityQuantity(DRONES);
            float take = Math.min(drones, (float) amount);
            if (take > 0f) {
                cargo.removeCommodity(DRONES, take);
                amount -= (int) take;
            }
            if (amount <= 0) return;

            float civ = cargo.getCommodityQuantity(CIVILIANS);
            take = Math.min(civ, (float) amount);
            if (take > 0f) {
                cargo.removeCommodity(CIVILIANS, take);
            }
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean runWhilePaused() {
            // Needed so the crew bar updates while trading in dialogs/markets.
            return true;
        }
    }
}