package UFP.data.systems;

import UFP.data.util.StarshipSeparation;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class EnterpriseDSeparation extends BaseShipSystemScript {

    private static final String REQUIRED_HULL_ID = "fed_enterpriseD";
    private static final String SAUCER_VARIANT_ID = "fed_enterpriseD_saucer";
    private static final String STARDRIVE_VARIANT_ID = "fed_enterpriseD_stardrive";

    private static final String PENALTY_APPLIED_KEY = "UFP_EnterpriseDSeparation_IncompatiblePenaltyApplied";
    private static final String SHIELD_LOCK_ID = "UFP_EnterpriseDSeparation_ShieldLock";

    // Local offset vectors derived from module slot coordinates
    private static final Vector2f OFFSET_SAUCER = new Vector2f(134f, 0f);      // WS 022
    private static final Vector2f OFFSET_STARDRIVE = new Vector2f(-117.5f, 0f);  // WS 021

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null || !ship.isAlive()) return;

        // Station module slot anchor guard
        if (ship.getParentStation() != null || ship.getStationSlot() != null) return;

        // Maintain shield lock if penalty was applied
        if (Boolean.TRUE.equals(ship.getCustomData().get(PENALTY_APPLIED_KEY))) {
            enforceShieldLock(ship);
        }

        if (state != State.ACTIVE || effectLevel < 1f) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        String hullId = ship.getHullSpec().getHullId();

        // Incompatible hull gate
        if (!REQUIRED_HULL_ID.equals(hullId)) {
            punishIncompatibleActivation(ship, engine);
            return;
        }

        // Build separation specs (Bottom-to-Top render order)
        List<StarshipSeparation.SeparationSpec> specs = new ArrayList<>(2);

        // 1. Spawn Stardrive FIRST so it renders BELOW
        specs.add(new StarshipSeparation.SeparationSpec(STARDRIVE_VARIANT_ID, OFFSET_STARDRIVE, true));

        // 2. Spawn Saucer SECOND so it renders ON TOP
        specs.add(new StarshipSeparation.SeparationSpec(SAUCER_VARIANT_ID, OFFSET_SAUCER, false));

        // Delegate execution and collision safety to utility
        StarshipSeparation.separate(engine, ship, specs);
    }

    private void punishIncompatibleActivation(ShipAPI ship, CombatEngineAPI engine) {
        if (Boolean.TRUE.equals(ship.getCustomData().get(PENALTY_APPLIED_KEY))) {
            enforceShieldLock(ship);
            return;
        }

        ship.setCustomData(PENALTY_APPLIED_KEY, Boolean.TRUE);

        stripAllArmor(ship);
        ship.setHitpoints(Math.max(1f, ship.getMaxHitpoints() * 0.25f));
        enforceShieldLock(ship);

        if (engine != null) {
            Color bad = Misc.getNegativeHighlightColor();
            engine.addFloatingText(ship.getLocation(), "SEPARATION FAILURE", 30f, bad, ship, 1f, 2f);

            if (engine.getPlayerShip() == ship && engine.getCombatUI() != null) {
                engine.getCombatUI().addMessage(1, ship, bad, "Incompatible hull: catastrophic separation failure");
            }
        }
    }

    private void enforceShieldLock(ShipAPI ship) {
        if (ship == null) return;
        ship.getMutableStats().getShieldUnfoldRateMult().modifyMult(SHIELD_LOCK_ID, 0f);
        if (ship.getShield() != null && ship.getShield().isOn()) {
            ship.getShield().toggleOff();
        }
    }

    private void stripAllArmor(ShipAPI ship) {
        ArmorGridAPI grid = ship.getArmorGrid();
        if (grid == null) return;

        float[][] armor = grid.getGrid();
        for (int x = 0; x < armor.length; x++) {
            for (int y = 0; y < armor[0].length; y++) {
                grid.setArmorValue(x, y, 0f);
            }
        }
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        if (ship == null || !ship.isAlive()) return false;
        return ship.getParentStation() == null && ship.getStationSlot() == null;
    }

    @Override
    public int getUsesOverride(ShipAPI ship) { return 1; }

    @Override
    public float getActiveOverride(ShipAPI ship) { return 1f; }

    @Override
    public float getInOverride(ShipAPI ship) { return 0.5f; }

    @Override
    public float getOutOverride(ShipAPI ship) { return 0.5f; }

    @Override
    public float getRegenOverride(ShipAPI ship) { return 0f; }

    @Override
    public String getDisplayNameOverride(State state, float effectLevel) { return "Saucer Separation"; }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        if (ship == null || !ship.isAlive()) return null;
        if (ship.getParentStation() != null || ship.getStationSlot() != null) return "Moored to Station";
        return REQUIRED_HULL_ID.equals(ship.getHullSpec().getHullId()) ? "Ready to Separate" : "Incompatible Hull";
    }

    @Override
    public ShipSystemStatsScript.StatusData getStatusData(int index, State state, float effectLevel) {
        return index == 0 ? new StatusData("Ready to Separate...", false) : null;
    }
}