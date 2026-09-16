package UFP.data.hullmods;

import UFP.data.ui.ShieldHPBar_AI;
import UFP.data.ui.ShieldHPStatusBar;
import UFP.data.util.ShieldHitpointManager;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;
import java.util.Iterator;

public class LudditeShields extends FederationShields {

    // =========================================================
    // Keys & Constants (top, as requested)
    // =========================================================
    public static final String LUDDITE_HP_PRESENCE_KEY = "UFP_LUD_SH_HP";
    public static final String LUDDITE_BAR_PLUGIN_KEY = "UFP_LudditeShieldHPBarAI_Registered";
    public static final String EMP_BLOCK_ID = "ufp_luddite_emp_block_when_shield_up";

    // Bar visuals: grass green fill, brown border
    public static final Color LUDDITE_BAR_COLOR = new Color(80, 170, 70, 235);     // grass green
    public static final Color LUDDITE_BORDER_COLOR = new Color(110, 70, 30, 220); // brown border

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (ship == null || ship.getShield() == null) return;

        applyVisuals(ship, ship.getShield());

        ShieldHitpointManager.initialize(ship);
        ShieldHitpointManager.registerDamageInterceptor(ship);

        ship.setCustomData(LUDDITE_HP_PRESENCE_KEY, true);
        ship.setCustomData("universal_shield_hp_bar_color", LUDDITE_BAR_COLOR);
        ship.setCustomData("universal_shield_hp_border_color", LUDDITE_BORDER_COLOR);
        ship.setCustomData(ShieldHPBar_AI.SHOW_SHIELD_HP_BAR_KEY, true);
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive() || ship.getShield() == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        ShieldHitpointManager.update(ship, amount);

        if (!Boolean.TRUE.equals(engine.getCustomData().get(LUDDITE_BAR_PLUGIN_KEY))) {
            engine.addPlugin(new ShieldHPBar_AI());
            engine.getCustomData().put(LUDDITE_BAR_PLUGIN_KEY, true);
        }

        if (ship == engine.getPlayerShip()) {
            ShieldHPStatusBar.render(ship, engine);
        } else {
            ship.setCustomData(ShieldHPBar_AI.SHOW_SHIELD_HP_BAR_KEY, true);
        }

        if (ShieldHitpointManager.isShieldDisabled(ship)) {
            ship.getMutableStats().getEmpDamageTakenMult().unmodify(EMP_BLOCK_ID);
            return;
        }

        if (ship.getShield().isOn()) {
            ship.getMutableStats().getEmpDamageTakenMult().modifyMult(EMP_BLOCK_ID, 0f);
        } else {
            ship.getMutableStats().getEmpDamageTakenMult().unmodify(EMP_BLOCK_ID);
        }
    }


    public String getSelectedShieldStyle() {
        try {
            var settings = Global.getSettings().loadJSON("data/config/UFPSettings.json");
            if (settings.has("Kol_Shields")) {
                var graphics = settings.getJSONObject("Kol_Shields");
                Iterator<String> keys = graphics.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (graphics.optBoolean(key, false) && UFP.data.util.ShieldTextureManager.isValidStyle(key)) {
                        return key;
                    }
                }
            }
        } catch (Exception e) {
            Global.getLogger(this.getClass()).warn("LudditeShields: Failed to load Kol_Shields style.", e);
        }
        // Same fallback as FederationShields
        return "BubbleShield";
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize,
                                          ShipAPI ship, float width, boolean isForModSpec) {
        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec);
    }
}