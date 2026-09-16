package UFP.data.hullmods;

import UFP.data.ui.ESM_StatusBar;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

public class StandardWarpCore extends BaseHullMod {

    private static final String STATUS_BAR_KEY = "UFP_ESM_STATUS_BAR";

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship != engine.getPlayerShip() || !ship.isAlive()) {
            return;
        }

        // HUD Registration cleanly handles fallback rendering safely
        if (!engine.getCustomData().containsKey(STATUS_BAR_KEY)) {
            ESM_StatusBar renderer = new ESM_StatusBar();
            engine.addLayeredRenderingPlugin(renderer);
            engine.getCustomData().put(STATUS_BAR_KEY, renderer);
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        return null;
    }
}