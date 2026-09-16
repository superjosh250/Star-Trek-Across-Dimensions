
package UFP.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import org.json.JSONObject;

import java.awt.Color;

/**
 * Utility class for managing shield hitpoints and related logic.
 * Designed for integration across multiple hullmods and plugins.
 */
public class ShieldHitpointManagerBACKUP {

    private static final String SHIELD_HP_KEY = "universal_shield_hp";
    private static final String SHIELD_HP_MAX_KEY = "universal_shield_hp_max";
    private static final String SHIELD_DISABLED_KEY = "universal_shield_disabled";
    private static final String SHIELD_RESTORE_TIMER_KEY = "universal_shield_restore_timer";
    public static final String UNIVERSAL_DAMAGE_SCALER_KEY = "UniversalDamageScaler";

    /**
     * Initializes shield HP for a ship using its max flux.
     */
    public static void initialize(ShipAPI ship) {
        if (ship == null || ship.getShield() == null) return;

        float maxFlux = ship.getMaxFlux();
        initialize(ship, maxFlux); // ✅ Delegate to custom initializer
    }

    /**
     * Initializes shield HP for a ship with a custom max HP value.
     * Useful for quadrant shields where each drone gets a fraction of parent HP.
     */
    public static void initialize(ShipAPI ship, float maxHP) {
        if (ship == null || ship.getShield() == null) return;

        ship.setCustomData(SHIELD_HP_KEY, maxHP);
        ship.setCustomData(SHIELD_HP_MAX_KEY, maxHP);
        ship.setCustomData(SHIELD_DISABLED_KEY, false);
        ship.setCustomData(SHIELD_RESTORE_TIMER_KEY, 0f);

        Global.getLogger(ShieldHitpointManager.class).info(
                "Initialized shield HP for ship: " + ship.getName() + " with custom HP: " + maxHP
        );
    }

    /**
     * ORIGINAL GLOBAL REGISTRATION (Optional, can be removed for per-drone logic)
     */
    public static void registerDamageInterceptor(ShipAPI ship) {
        if (ship == null || ship.getShield() == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || engine.getListenerManager() == null) {
            Global.getLogger(ShieldHitpointManager.class).warn("ListenerManager not available. Cannot register damage interceptor.");
            return;
        }

        engine.getListenerManager().addListener(new UniversalShieldDamageInterceptor(ship));
    }

    /**
     * Per-drone registration method (Recommended for quadrant shields).
     */
    public static UniversalShieldDamageInterceptor createInterceptor(ShipAPI ship) {
        return new UniversalShieldDamageInterceptor(ship);
    }

    /**
     * Updates shield HP and handles restoration logic.
     */
    public static void update(ShipAPI ship, float amount) {
        if (ship == null || ship.getShield() == null) return;

        ShieldAPI shield = ship.getShield();
        boolean shieldDisabled = isShieldDisabled(ship);

        if (shieldDisabled) {
            if (shield.isOn()) {
                shield.toggleOff();
                Global.getCombatEngine().addFloatingText(
                        ship.getLocation(),
                        "Shield Broken",
                        20f,
                        Color.RED,
                        ship,
                        1f,
                        1f
                );
            }

            float timer = (float) ship.getCustomData().getOrDefault(SHIELD_RESTORE_TIMER_KEY, 0f);
            timer += amount;

            if (timer >= 80f) {
                float restoredHP = getMaxShieldHP(ship) * 0.75f;
                setShieldHP(ship, restoredHP);
                ship.setCustomData(SHIELD_RESTORE_TIMER_KEY, 0f);

                Global.getCombatEngine().addFloatingText(
                        ship.getLocation(),
                        "Shield Restored",
                        20f,
                        Color.GREEN,
                        ship,
                        1f,
                        1f
                );
            } else {
                ship.setCustomData(SHIELD_RESTORE_TIMER_KEY, timer);
            }

            return;
        }

        ship.setCustomData(SHIELD_RESTORE_TIMER_KEY, 0f);

        float currentHP = getShieldHP(ship);
        if (currentHP <= 0f) {
            ship.setCustomData(SHIELD_DISABLED_KEY, true);
            shield.toggleOff();
            Global.getCombatEngine().addFloatingText(
                    ship.getLocation(),
                    "Shield Depleted",
                    20f,
                    Color.RED,
                    ship,
                    1f,
                    1f
            );
        }
    }

    public static void setShieldHP(ShipAPI ship, float hp) {
        if (ship == null) return;

        float maxHP = getMaxShieldHP(ship);
        hp = Math.max(0f, Math.min(hp, maxHP));

        ship.setCustomData(SHIELD_HP_KEY, hp);

        if (hp > 0f && isShieldDisabled(ship)) {
            ship.setCustomData(SHIELD_DISABLED_KEY, false);
            Global.getLogger(ShieldHitpointManager.class).info("Shield re-enabled for ship: " + ship.getName());
        }
    }

    public static void reset(ShipAPI ship) {
        if (ship == null) return;

        float maxHP = getMaxShieldHP(ship);
        ship.setCustomData(SHIELD_HP_KEY, maxHP);
        ship.setCustomData(SHIELD_DISABLED_KEY, false);
        ship.setCustomData(SHIELD_RESTORE_TIMER_KEY, 0f);

        Global.getLogger(ShieldHitpointManager.class).info("Shield HP reset for ship: " + ship.getName());
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

    /**
     * Damage interceptor for HP-based shields.
     */
    public static class UniversalShieldDamageInterceptor implements DamageListener {
        private final ShipAPI ship;

        public UniversalShieldDamageInterceptor(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
            if (target != ship) return;
            if (result.getDamageToShields() <= 0f || ship.getShield() == null || !ship.isAlive() || !ship.getShield().isOn())
                return;

            if (!ship.getCustomData().containsKey(SHIELD_HP_KEY)) return;
            if (source == ship) return;

            float hpDamage = result.getDamageToShields();

            // Apply damage scaling from UFPSettings.json
            String scalerKey = (String) ship.getCustomData().get(UNIVERSAL_DAMAGE_SCALER_KEY);
            if (scalerKey != null) {
                try {
                    JSONObject settings = Global.getSettings().loadJSON("data/config/UFPSettings.json");
                    if (settings.has(scalerKey)) {
                        JSONObject scalerData = settings.getJSONObject(scalerKey);
                        String weaponId = null;

                        if (source instanceof DamagingProjectileAPI) {
                            DamagingProjectileAPI proj = (DamagingProjectileAPI) source;
                            if (proj.getWeapon() != null) weaponId = proj.getWeapon().getId();
                        } else if (source instanceof BeamAPI) {
                            BeamAPI beam = (BeamAPI) source;
                            if (beam.getWeapon() != null) weaponId = beam.getWeapon().getId();
                        }

                        if (weaponId != null && scalerData.has(weaponId)) {
                            float scale = (float) scalerData.optDouble(weaponId, 1.0);
                            hpDamage *= scale;
                        }
                    }
                } catch (Exception e) {
                    Global.getLogger(ShieldHitpointManager.class).error("Error applying damage scaling from UFPSettings.json", e);
                }
            }

            // Apply HP reduction
            float currentHP = getShieldHP(ship);
            setShieldHP(ship, currentHP - hpDamage);

            // Reduce flux proportionally
            float currFlux = ship.getFluxTracker().getCurrFlux();
            float hardFlux = ship.getFluxTracker().getHardFlux();
            ship.getFluxTracker().setCurrFlux(Math.max(0f, currFlux - hpDamage));
            ship.getFluxTracker().setHardFlux(Math.max(0f, hardFlux - hpDamage));

            // Feedback text
            Global.getCombatEngine().addFloatingText(
                    ship.getLocation(),
                    "-" + Math.round(hpDamage) + " Shield HP",
                    12f,
                    new Color(255, 255, 255, 230),
                    ship,
                    0.5f,
                    0.5f
            );

            // Disable shield if HP depleted
            if (getShieldHP(ship) <= 0f && !isShieldDisabled(ship)) {
                ship.setCustomData(SHIELD_DISABLED_KEY, true);
                ship.getShield().toggleOff();
                Global.getCombatEngine().addFloatingText(
                        ship.getLocation(),
                        "Shield Depleted",
                        20f,
                        Color.RED,
                        ship,
                        1f,
                        1f
                );
            }
        }
    }
}

