package BORG.data.hullmods;

import BORG.data.campaign.ids.BorgIDS;
import BORG.data.util.BorgAdaptationMatrix;
import BORG.data.util.SharedKnowledge;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class HiveMind extends BaseHullMod {

    // UI/Flavor constants
    public static final String HIVE_MOD_ID = "borg_hive_mind";

    /**
     * Called when the ship is spawned in combat.
     * Registers the ship with the Adaptation Matrix and updates Hive slot logic.
     */
    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive()) return;

        // 1. Ensure the ship is registered to take and process adaptive damage
        BorgAdaptationMatrix.register(ship);

        // 2. Register this Hull ID as the "Bonus Slot" provider in the Hive
        // We do this every frame or once at start to ensure SharedKnowledge knows which ID to look for.
        Global.getCombatEngine().getCustomData().put(SharedKnowledge.HULL_ID_BONUS_KEY, BorgIDS.BORG_CUBE);

        // 3. Optional: Run HUD rendering if this is the player ship
        if (ship == Global.getCombatEngine().getPlayerShip()) {
            BorgAdaptationMatrix.renderHUD(ship, Global.getCombatEngine());
        }
    }

    /**
     * Provide a description for the hullmod in the ship refit screen.
     */
    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) return "Borg Hive Mind";
        if (index == 1) return "Collectively adapts to incoming weapon signatures.";

        // SharedKnowledge-driven values
        if (index == 2) return Math.round(SharedKnowledge.SHARED_CONTRIBUTION_MULT * 100f) + "%";
        if (index == 3) return "" + SharedKnowledge.BASE_LEARNING_SLOTS;
        if (index == 4) return "+" + SharedKnowledge.BONUS_LEARNING_SLOTS;
        if (index == 5) return "50%"; // RNG lock chance (hardcoded in SharedKnowledge)
        if (index == 6) return "100%";
        if (index == 7) return "" + SharedKnowledge.MAX_SHIPS_IN_LINK;

        return null;
    }



    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        // Logic to restrict this to Borg ships if necessary
        return true;
    }
}