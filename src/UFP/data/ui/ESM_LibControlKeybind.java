package UFP.data.ui;

import UFP.data.util.ESM_ShieldLib;
import UFP.data.util.ShieldHitpointManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.List;

public class ESM_LibControlKeybind extends BaseEveryFrameCombatPlugin {

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        CombatEngineAPI engine = Global.getCombatEngine();

        // 1. Core validation
        if (engine == null || engine.isPaused()) return;
        if (!ESM_LibControl.isShieldLibEnabled()) return;

        ShipAPI playerShip = engine.getPlayerShip();
        if (playerShip == null || !playerShip.isAlive() || playerShip.getShield() == null) {
            return;
        }

        // 2. Advance ShieldLib timers (cooldowns & charge recharges)
        ESM_ShieldLib.advance(playerShip, amount);

        int boundKey = ESM_LibControl.getShieldRestoreKeybind();
        if (boundKey == Keyboard.KEY_NONE || events == null) return;

        // 3. Process frame input events
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;

            if (event.isKeyDownEvent()) {
                int eventKey = event.getEventValue();

                // Match configured keycode OR handle NUMPAD0 with Num Lock OFF (KEY_INSERT)
                boolean isMatch = (eventKey == boundKey) ||
                        (boundKey == Keyboard.KEY_NUMPAD0 && eventKey == Keyboard.KEY_INSERT);

                if (isMatch) {
                    event.consume();

                    // Attempt restoration
                    boolean success = ESM_ShieldLib.restoreShieldsByPercentage(playerShip);

                    // Floating text diagnostics if restoration was rejected
                    if (!success) {
                        float currentHP = ShieldHitpointManager.getShieldHP(playerShip);
                        float maxHP = ShieldHitpointManager.getMaxShieldHP(playerShip);

                        if (currentHP >= maxHP) {
                            engine.addFloatingText(playerShip.getLocation(), "Shields Full", 12f, Color.YELLOW, playerShip, 0.3f, 0.3f);
                        } else if (ESM_ShieldLib.isOnCooldown(playerShip)) {
                            engine.addFloatingText(playerShip.getLocation(), "On Cooldown", 12f, Color.YELLOW, playerShip, 0.3f, 0.3f);
                        } else if (ESM_ShieldLib.getRemainingRestorationUses(playerShip) <= 0 && !ESM_ShieldLib.isUnlimitedUses(playerShip)) {
                            engine.addFloatingText(playerShip.getLocation(), "No Charges Left", 12f, Color.RED, playerShip, 0.3f, 0.3f);
                        } else {
                            engine.addFloatingText(playerShip.getLocation(), "Insufficient ESM Power", 12f, Color.RED, playerShip, 0.3f, 0.3f);
                        }
                    }
                    break;
                }
            }
        }
    }
}