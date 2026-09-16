package UFP.data.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.magiclib.util.MagicUI;

// Import the ESM classes
import UFP.data.util.EnergySystemManager;

import java.awt.Color;
import java.util.EnumSet;

public class ESM_StatusBar extends BaseCombatLayeredRenderingPlugin {

    @Override
    public void init(CombatEntityAPI entity) {
        Global.getLogger(getClass()).info("ESM_StatusBar initialized");
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {

        if (layer != CombatEngineLayers.JUST_BELOW_WIDGETS) {
            return;
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || !engine.isUIShowingHUD()) {
            return;
        }

        ShipAPI ship = engine.getPlayerShip();
        if (ship == null || !ship.isAlive()) {
            return;
        }

        // Fetch the player tracker instance via its centralized snapshot
        EnergySystemManager esm = EnergySystemManager.getPlayerTracker(engine, ship);
        if (esm == null) {
            return;
        }

        float maxEnergy = esm.getMaxEnergy();
        if (maxEnergy <= 0f) {
            return;
        }

        // Map UI variables strictly to ESM values instead of flux
        float energyLevel = esm.getEnergyLevel();
        int displayPercentage = (int) (energyLevel * 100f);

        // Draw custom UI status bar using MagicUI components cleanly without the testing background mask
        MagicUI.drawInterfaceStatusBar(
                ship,
                energyLevel,               // Fill fraction (0.0f to 1.0f)
                new Color(90, 90, 101, 255),   // Custom Energy Color
                Color.WHITE,               // Text color
                0f,                        // Hard flux replacement value
                "PWR",                     // UI Label tag string
                displayPercentage          // Integer value shown next to bar
        );
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.JUST_BELOW_WIDGETS);
    }

    @Override
    public float getRenderRadius() {
        return 999999f;
    }

    @Override
    public boolean isExpired() {
        return false;
    }
}