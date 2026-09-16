package UFP.data.util;

import UFP.data.ui.ESM_LibControl;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.awt.Color;

/**
 * Controller and API bridge class for utilizing ESM Energy to restore Shield HP.
 * Fully configurable per-ship via external hullmods, systems, or scripts.
 */
public class ESM_ShieldLib {

    // --- Custom Data Keys ---
    public static final String KEY_RESTORE_ALLOWED     = "UFP_ESM_ShieldRestore_Allowed";
    public static final String KEY_MAX_USES            = "UFP_ESM_ShieldRestore_MaxUses";
    public static final String KEY_USES_REMAINING      = "UFP_ESM_ShieldRestore_UsesRemaining";
    public static final String KEY_COOLDOWN_TIMER      = "UFP_ESM_ShieldRestore_Cooldown";
    public static final String KEY_RECHARGE_TIMER      = "UFP_ESM_ShieldRestore_RechargeTimer";
    public static final String KEY_RECHARGE_INTERVAL   = "UFP_ESM_ShieldRestore_RechargeInterval";

    public static final int UNLIMITED_USES = -1;

    // =========================================================================
    // 1. FRAME TICK / ADVANCE LOGIC
    // =========================================================================

    public static void advance(ShipAPI ship, float dt) {
        if (ship == null || dt <= 0f) return;

        // Master Toggle Check
        if (!ESM_LibControl.isShieldLibEnabled()) return;

        // 1. Advance Cooldown
        float currentCD = getRemainingCooldown(ship);
        if (currentCD > 0f) {
            setCooldown(ship, currentCD - dt);
        }

        // 2. Advance Charge Recharge
        if (!isUnlimitedUses(ship)) {
            float interval = getChargeRechargeInterval(ship);
            int currentUses = getRemainingRestorationUses(ship);
            int maxUses = getMaxRestorationUses(ship);

            if (interval > 0f && currentUses < maxUses) {
                float rechargeTimer = getChargeRechargeTimer(ship) + dt;
                if (rechargeTimer >= interval) {
                    setRemainingRestorationUses(ship, currentUses + 1);
                    rechargeTimer = 0f;
                }
                setChargeRechargeTimer(ship, rechargeTimer);
            }
        }
    }

    // =========================================================================
    // 2. CONTROLLER METHOD
    // =========================================================================

    public static void setRestorationAllowed(ShipAPI ship, boolean allowed) {
        if (ship == null) return;
        ship.setCustomData(KEY_RESTORE_ALLOWED, allowed);
    }

    public static boolean isRestorationAllowed(ShipAPI ship) {
        if (ship == null) return false;
        return (boolean) ship.getCustomData().getOrDefault(KEY_RESTORE_ALLOWED, true);
    }

    // =========================================================================
    // 3. USAGE & RECHARGE CONFIGURATION METHODS
    // =========================================================================

    public static void setUnlimitedUses(ShipAPI ship, boolean unlimited) {
        if (ship == null) return;
        if (unlimited) {
            ship.setCustomData(KEY_MAX_USES, UNLIMITED_USES);
            ship.setCustomData(KEY_USES_REMAINING, UNLIMITED_USES);
        } else {
            setMaxRestorationUses(ship, ESM_LibControl.getShieldRestoreMaxUses());
        }
    }

    public static boolean isUnlimitedUses(ShipAPI ship) {
        if (ship == null) return ESM_LibControl.isShieldRestoreUnlimited();
        if (ESM_LibControl.isShieldRestoreUnlimited()) return true;
        return getMaxRestorationUses(ship) == UNLIMITED_USES;
    }

    public static void setMaxRestorationUses(ShipAPI ship, int maxUses) {
        if (ship == null) return;
        int val = (maxUses < 0) ? UNLIMITED_USES : maxUses;
        ship.setCustomData(KEY_MAX_USES, val);
        ship.setCustomData(KEY_USES_REMAINING, val);
    }

    public static int getMaxRestorationUses(ShipAPI ship) {
        if (ship == null) return ESM_LibControl.getShieldRestoreMaxUses();
        return (int) ship.getCustomData().getOrDefault(KEY_MAX_USES, ESM_LibControl.getShieldRestoreMaxUses());
    }

    public static int getRemainingRestorationUses(ShipAPI ship) {
        if (ship == null) return 0;
        if (isUnlimitedUses(ship)) return UNLIMITED_USES;

        if (!ship.getCustomData().containsKey(KEY_USES_REMAINING)) {
            int max = getMaxRestorationUses(ship);
            ship.setCustomData(KEY_USES_REMAINING, max);
            return max;
        }
        return (int) ship.getCustomData().get(KEY_USES_REMAINING);
    }

    public static void setRemainingRestorationUses(ShipAPI ship, int uses) {
        if (ship == null || isUnlimitedUses(ship)) return;
        ship.setCustomData(KEY_USES_REMAINING, Math.max(0, uses));
    }

    public static void setChargeRechargeInterval(ShipAPI ship, float seconds) {
        if (ship == null) return;
        ship.setCustomData(KEY_RECHARGE_INTERVAL, Math.max(0f, seconds));
        ship.setCustomData(KEY_RECHARGE_TIMER, 0f);
    }

    public static float getChargeRechargeInterval(ShipAPI ship) {
        if (ship == null) return ESM_LibControl.getShieldRestoreRechargeInterval();
        return (float) ship.getCustomData().getOrDefault(KEY_RECHARGE_INTERVAL, ESM_LibControl.getShieldRestoreRechargeInterval());
    }

    private static float getChargeRechargeTimer(ShipAPI ship) {
        if (ship == null) return 0f;
        return (float) ship.getCustomData().getOrDefault(KEY_RECHARGE_TIMER, 0f);
    }

    private static void setChargeRechargeTimer(ShipAPI ship, float timer) {
        if (ship == null) return;
        ship.setCustomData(KEY_RECHARGE_TIMER, Math.max(0f, timer));
    }

    // =========================================================================
    // 4. COOLDOWN & TIMING METHODS
    // =========================================================================

    public static float getRemainingCooldown(ShipAPI ship) {
        if (ship == null) return 0f;
        return (float) ship.getCustomData().getOrDefault(KEY_COOLDOWN_TIMER, 0f);
    }

    public static boolean isOnCooldown(ShipAPI ship) {
        return getRemainingCooldown(ship) > 0f;
    }

    public static void setCooldown(ShipAPI ship, float seconds) {
        if (ship == null) return;
        ship.setCustomData(KEY_COOLDOWN_TIMER, Math.max(0f, seconds));
    }

    // =========================================================================
    // 5. HELPER: DYNAMIC ENERGY COST CALCULATION
    // =========================================================================

    /**
     * Calculates energy cost dynamically based on LunaSettings configuration
     * (Percent of max energy vs flat value).
     */
    public static float calculateEnergyCost(EnergySystemManager esm) {
        if (esm == null) return 0f;
        if (ESM_LibControl.isEnergyCostPercentBased()) {
            return esm.getMaxEnergy() * ESM_LibControl.getShieldRestoreCostPercent();
        } else {
            return ESM_LibControl.getShieldRestoreCostFlat();
        }
    }

    // =========================================================================
    // 6. RESTORATION METHODS
    // =========================================================================

    /**
     * Convenience Overload: Restores shields using current global default LunaSettings parameters.
     */
    public static boolean restoreShieldsByPercentage(ShipAPI ship) {
        if (ship == null) return false;
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return false;

        EnergySystemManager esm = EnergySystemManager.getOrCreateTracker(engine, ship);
        float calculatedCost = calculateEnergyCost(esm);

        return restoreShieldsByPercentage(
                ship,
                ESM_LibControl.getShieldRestoreDefaultHpPercent(),
                calculatedCost,
                ESM_LibControl.getShieldRestoreDefaultCooldown()
        );
    }

    /**
     * Option A: Restores shield HP by a PERCENTAGE of max shield HP (e.g. 0.25f = 25%).
     */
    public static boolean restoreShieldsByPercentage(ShipAPI ship, float percentage, float energyCost, float cooldownSeconds) {
        if (ship == null) return false;
        float maxHP = ShieldHitpointManager.getMaxShieldHP(ship);
        float hpAmount = maxHP * Math.max(0f, percentage);
        return executeShieldRestoration(ship, hpAmount, energyCost, cooldownSeconds, String.format("+%d%% Shield HP (ESM)", (int)(percentage * 100)));
    }

    /**
     * Option B: Restores shield HP using a MULTIPLICATION FACTOR applied to base HP.
     */
    public static boolean restoreShieldsByMultiplier(ShipAPI ship, float baseValue, float multiplier, float energyCost, float cooldownSeconds) {
        float hpAmount = Math.max(0f, baseValue * multiplier);
        return executeShieldRestoration(ship, hpAmount, energyCost, cooldownSeconds, String.format("+%.0f Shield HP (ESM)", hpAmount));
    }

    /**
     * Option C: Restores shield HP by a STATIC / FLAT numerical value.
     */
    public static boolean restoreShieldsByFlatValue(ShipAPI ship, float flatHPAmount, float energyCost, float cooldownSeconds) {
        return executeShieldRestoration(ship, flatHPAmount, energyCost, cooldownSeconds, String.format("+%.0f Shield HP (ESM)", flatHPAmount));
    }

    // =========================================================================
    // INTERNAL CORE EXECUTION ENGINE
    // =========================================================================

    private static boolean executeShieldRestoration(ShipAPI ship, float hpToRestore, float energyCost, float cooldownSeconds, String floatText) {
        // 0. Master Toggle Check
        if (!ESM_LibControl.isShieldLibEnabled()) {
            return false;
        }

        CombatEngineAPI engine = Global.getCombatEngine();
        if (ship == null || engine == null || !ship.isAlive() || ship.getShield() == null) {
            return false;
        }

        // --- ENSURE SHIELD HITPOINT MANAGER IS INITIALIZED ---
        if (ShieldHitpointManager.getMaxShieldHP(ship) <= 0f) {
            ShieldHitpointManager.initialize(ship);
        }

        // 1. Controller validation check
        if (!isRestorationAllowed(ship)) return false;

        // 2. Cooldown check
        if (isOnCooldown(ship)) return false;

        // 3. Usage limit check
        boolean unlimited = isUnlimitedUses(ship);
        int uses = getRemainingRestorationUses(ship);
        if (!unlimited && uses <= 0) return false;

        // 4. Check if shield is already full
        float currentHP = ShieldHitpointManager.getShieldHP(ship);
        float maxHP = ShieldHitpointManager.getMaxShieldHP(ship);
        if (currentHP >= maxHP) return false;

        // 5. Energy validation via ESM
        EnergySystemManager esm = EnergySystemManager.getOrCreateTracker(engine, ship);
        if (esm == null || esm.isZeroedOut() || esm.getCurrEnergy() < energyCost) {
            return false;
        }

        // --- EXECUTE RESTORATION ---
        if (esm.consumeEnergy(energyCost)) {
            if (!unlimited) {
                setRemainingRestorationUses(ship, uses - 1);
            }

            setCooldown(ship, cooldownSeconds);

            float newHP = Math.min(maxHP, currentHP + hpToRestore);
            ShieldHitpointManager.setShieldHP(ship, newHP);

            engine.addFloatingText(
                    ship.getLocation(),
                    floatText,
                    14f,
                    new Color(0, 255, 200, 240),
                    ship,
                    0.6f,
                    0.6f
            );

            return true;
        }

        return false;
    }
}