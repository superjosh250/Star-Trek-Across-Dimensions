package UFP.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import UFP.data.ui.ShieldHPBar_AI;
import UFP.data.ai.FederationShieldsManagementAI;

import java.awt.Color;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class for managing shield hitpoints and related logic.
 *
 * - Prevents vanilla flux buildup by removing vanilla shield damage
 * - Applies scaled damage to custom shield HP only (via ShieldHPDamageScaler)
 * - Workload optimized: Offloads all CSV data parsing and caching to UFP_CSV_Manager
 */
public class ShieldHitpointManager {

    private static final String SHIELD_HP_KEY = "universal_shield_hp";
    private static final String SHIELD_HP_MAX_KEY = "universal_shield_hp_max";
    private static final String SHIELD_DISABLED_KEY = "universal_shield_disabled";
    private static final String SHIELD_RESTORE_TIMER_KEY = "universal_shield_restore_timer";

    public static final String UNIVERSAL_DAMAGE_SCALER_KEY = "UniversalDamageScaler";

    private static final float RESTORE_TIME = 80f;
    private static final float RESTORE_PERCENT = 0.75f;

    // --------------------------
    // Initialization & lifecycle
    // --------------------------
    public static void initialize(ShipAPI ship) {
        if (ship == null || ship.getShield() == null) return;

        float maxFlux = ship.getMaxFlux();
        float shieldHP = maxFlux;

        if (ship.getHullSpec() != null) {
            float csvHP = UFP_CSV_Manager.getShieldHP(ship.getHullSpec().getHullId());
            // Fallback check for skins or variants relying on base hull settings
            if (csvHP <= 0f && ship.getHullSpec().getBaseHullId() != null) {
                csvHP = UFP_CSV_Manager.getShieldHP(ship.getHullSpec().getBaseHullId());
            }
            if (csvHP > 0f) {
                shieldHP = csvHP;
            }
        }

        ship.setCustomData(SHIELD_HP_KEY, shieldHP);
        ship.setCustomData(SHIELD_HP_MAX_KEY, shieldHP);
        ship.setCustomData(SHIELD_DISABLED_KEY, false);
        ship.setCustomData(SHIELD_RESTORE_TIMER_KEY, 0f);
    }

    /**
     * Configures and registers custom shield HP UI components, states, and bars across custom modules.
     */
    public static void setupShieldHPUI(ShipAPI ship, Color barColor, Color borderColor) {
        if (ship == null) return;

        ShieldHPBar_AI.ShieldBarState container = new ShieldHPBar_AI.ShieldBarState();
        container.isEligible = !ship.isFighter();
        container.showBar = true;
        container.maxHp = getShieldHPForRefit(ship);
        container.currentHp = container.maxHp;
        container.isDisabled = false;

        Map<String, Object> customData = ship.getCustomData();
        customData.put(ShieldHPBar_AI.UFP_BAR_DATA_CONTAINER_KEY, container);
        customData.put("universal_shield_hp_bar_color", barColor);
        customData.put("universal_shield_hp_border_color", borderColor);
        customData.put(ShieldHPBar_AI.SHOW_SHIELD_HP_BAR_KEY, true);
        customData.put(FederationShieldsManagementAI.KEY_ENABLED_ON_SHIP, true);
    }

    /**
     * Standardizes registration for color modifications and visual contextual text highlights.
     */
    public static void setShieldHPHighlightColor(ShipAPI ship, Color highlightColor) {
        if (ship == null) return;
        ship.getCustomData().put("federation_shield_hp_color", highlightColor);
        ship.getCustomData().put("universal_shield_hp_highlight_color", highlightColor);
    }

    public static void registerDamageInterceptor(ShipAPI ship) {
        if (ship == null || ship.getShield() == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.getListenerManager() == null) return;

        engine.getListenerManager().addListener(new UniversalShieldDamageInterceptor(ship));
    }

    public static void update(ShipAPI ship, float amount) {
        if (ship == null || ship.getShield() == null) return;

        ShieldAPI shield = ship.getShield();
        Map<String, Object> customData = ship.getCustomData();
        boolean shieldDisabled = Boolean.TRUE.equals(customData.get(SHIELD_DISABLED_KEY));

        if (shieldDisabled) {
            if (shield.isOn()) {
                shield.toggleOff();
                Global.getCombatEngine().addFloatingText(
                        ship.getLocation(), "Shield Broken", 20f, Color.RED, ship, 1f, 1f);
            }

            Object timerObj = customData.get(SHIELD_RESTORE_TIMER_KEY);
            float timer = (timerObj instanceof Float) ? (Float) timerObj : 0f;
            timer += amount;

            if (timer >= RESTORE_TIME) {
                float restoredHP = getMaxShieldHP(ship) * RESTORE_PERCENT;
                setShieldHP(ship, restoredHP);
                customData.remove(SHIELD_RESTORE_TIMER_KEY);

                Global.getCombatEngine().addFloatingText(
                        ship.getLocation(), "Shield Restored", 20f, Color.GREEN, ship, 1f, 1f);
            } else {
                customData.put(SHIELD_RESTORE_TIMER_KEY, timer);
            }
            return;
        }

        if (customData.containsKey(SHIELD_RESTORE_TIMER_KEY)) {
            customData.remove(SHIELD_RESTORE_TIMER_KEY);
        }

        Object hpObj = customData.get(SHIELD_HP_KEY);
        float currentHP = (hpObj instanceof Float) ? (Float) hpObj : 0f;

        if (currentHP <= 0f) {
            customData.put(SHIELD_DISABLED_KEY, true);
            shield.toggleOff();
            Global.getCombatEngine().addFloatingText(
                    ship.getLocation(), "Shield Depleted", 20f, Color.RED, ship, 1f, 1f);
        }
    }

    /**
     * Safely retrieves a ship's maximum shield capacity inside UI/Refit/Hullmod contexts.
     * Prevents NullPointerExceptions or 0 values when queried outside active combat instances.
     */
    public static float getShieldHPForRefit(ShipAPI ship) {
        if (ship == null) return 0f;

        // If active in combat and completely initialized, fetch live setting
        if (ship.getCustomData().containsKey(SHIELD_HP_MAX_KEY)) {
            return (float) ship.getCustomData().get(SHIELD_HP_MAX_KEY);
        }

        // If viewed in the refit screen, look up from cached map definitions
        if (ship.getHullSpec() != null) {
            float csvHP = UFP_CSV_Manager.getShieldHP(ship.getHullSpec().getHullId());
            if (csvHP <= 0f && ship.getHullSpec().getBaseHullId() != null) {
                csvHP = UFP_CSV_Manager.getShieldHP(ship.getHullSpec().getBaseHullId());
            }
            if (csvHP > 0f) return csvHP;
        }

        // Base engine fallback
        return ship.getMaxFlux();
    }

    public static void setShieldHP(ShipAPI ship, float hp) {
        if (ship == null) return;

        float maxHP = getMaxShieldHP(ship);
        hp = Math.max(0f, Math.min(hp, maxHP));
        ship.setCustomData(SHIELD_HP_KEY, hp);

        if (hp > 0f && isShieldDisabled(ship)) {
            ship.setCustomData(SHIELD_DISABLED_KEY, false);
        }
    }

    public static void reset(ShipAPI ship) {
        if (ship == null) return;

        float maxHP = getMaxShieldHP(ship);
        ship.setCustomData(SHIELD_HP_KEY, maxHP);
        ship.setCustomData(SHIELD_DISABLED_KEY, false);
        ship.setCustomData(SHIELD_RESTORE_TIMER_KEY, 0f);
    }

    public static float getShieldHP(ShipAPI ship) {
        return (float) ship.getCustomData().getOrDefault(SHIELD_HP_KEY, 0f);
    }

    public static float getMaxShieldHP(ShipAPI ship) {
        return (float) ship.getCustomData().getOrDefault(SHIELD_HP_MAX_KEY, 0f);
    }

    public static boolean isShieldDisabled(ShipAPI ship) {
        return (boolean) ship.getCustomData().getOrDefault(SHIELD_DISABLED_KEY, false);
    }

    // ----------------------
    // Damage Interception
    // ----------------------
    private static class UniversalShieldDamageInterceptor implements DamageListener {
        private final ShipAPI ship;

        public UniversalShieldDamageInterceptor(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
            if (target != ship) return;
            if (result == null) return;
            if (result.getDamageToShields() <= 0f) return;
            if (ship.getShield() == null || !ship.isAlive() || !ship.getShield().isOn()) return;

            if (!ship.getCustomData().containsKey(SHIELD_HP_KEY)) return;
            if (source == ship) return;

            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) return;

            float rawEngineShieldDamage = result.getDamageToShields();
            float rawEngineOverMax = 0f;

            if (ShieldHPDamageScaler.INCLUDE_OVERMAX_SHIELD_DAMAGE) {
                rawEngineOverMax = Math.max(0f, result.getOverMaxDamageToShields());
            }

            float rawEngineTotalShieldDamage = rawEngineShieldDamage + rawEngineOverMax;
            float hpInputShieldDamage = rawEngineTotalShieldDamage;

            float hpDamage = ShieldHPDamageScaler.computeShieldHpDamage(source, ship, hpInputShieldDamage);
            setShieldHP(ship, getShieldHP(ship) - hpDamage);

            result.setDamageToShields(0f);

            FluxTrackerAPI flux = ship.getFluxTracker();
            if (flux != null) {
                float rollback = rawEngineTotalShieldDamage;
                flux.setCurrFlux(Math.max(0f, flux.getCurrFlux() - rollback));
                flux.setHardFlux(Math.max(0f, flux.getHardFlux() - rollback));
            }

            String txt = String.format(Locale.ROOT, "-%.2f Shield HP", hpDamage);

            engine.addFloatingText(
                    ship.getLocation(),
                    txt,
                    12f,
                    new Color(255, 255, 255, 230),
                    ship,
                    0.5f,
                    0.5f
            );

            if (getShieldHP(ship) <= 0f && !isShieldDisabled(ship)) {
                ship.setCustomData(SHIELD_DISABLED_KEY, true);
                ship.getShield().toggleOff();
                engine.addFloatingText(
                        ship.getLocation(), "Shield Depleted", 20f, Color.RED, ship, 1f, 1f);
            }
        }
    }
}