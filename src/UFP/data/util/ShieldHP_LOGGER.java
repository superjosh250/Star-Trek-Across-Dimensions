package UFP.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.input.InputEventAPI;
import org.apache.log4j.Logger;
import org.json.JSONObject;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Combat-time logger for custom shield HP behavior.
 * Respects UFPSettings.json configurations to prevent accidental performance leaks.
 */
public class ShieldHP_LOGGER extends BaseEveryFrameCombatPlugin implements DamageListener {

    private static final Logger LOG = Global.getLogger(ShieldHP_LOGGER.class);
    private static final String SETTINGS_PATH = "data/config/UFPSettings.json";

    // --------------------
    // Toggle / verbosity (Controlled dynamically by JSON now)
    // --------------------
    public static boolean ENABLED = true;
    public static boolean BEAMS_ONLY = false;
    public static boolean LOG_SHIPS_WITHOUT_CUSTOM_HP = false;
    public static boolean INCLUDE_OVERMAX_IN_TOTAL = true;
    public static boolean LOG_NON_BEAM_EVENTS = true;
    public static float PER_SHIP_MIN_LOG_INTERVAL = 0f;

    // --------------------
    // Runtime state
    // --------------------
    private CombatEngineAPI engine;
    private boolean attachedAsListener = false;
    private boolean configLoaded = false;

    private final Map<ShipAPI, Float> lastObservedCustomHp = new IdentityHashMap<>();
    private final Set<ShipAPI> loggedMaxForShip = Collections.newSetFromMap(new IdentityHashMap<ShipAPI, Boolean>());
    private final Map<ShipAPI, Float> lastLogAtCombatTime = new IdentityHashMap<>();

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        // Don't rely on init for settings load due to Starsector lifecycle deprecation safety [2]
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) engine = Global.getCombatEngine();
        if (engine == null) return;

        // Load settings once when combat spins up
        if (!configLoaded) {
            loadJsonSettings();
        }

        if (!ENABLED) return;

        ensureAttached();
    }

    /**
     * Safely reads configuration values from UFPSettings.json exactly once per battle setup.
     */
    private void loadJsonSettings() {
        configLoaded = true;
        try {
            JSONObject settings = Global.getSettings().loadJSON(SETTINGS_PATH);
            if (settings.has("ShieldHP_Logging")) {
                JSONObject logConfig = settings.getJSONObject("ShieldHP_Logging");

                // Parse strings as booleans based on user configuration mapping layout
                boolean mainLogActive = !"false".equalsIgnoreCase(logConfig.optString("damage_data_log", "false"));

                if (!mainLogActive) {
                    ENABLED = false;
                    LOG.info("[ShieldHP_LOGGER] Disabled via UFPSettings.json config preference.");
                    return;
                }

                // Map auxiliary settings flags safely
                ENABLED = true;
                LOG_NON_BEAM_EVENTS = !"false".equalsIgnoreCase(logConfig.optString("per_hit_log", "true"));
                BEAMS_ONLY = "false".equalsIgnoreCase(logConfig.optString("per_hit_log", "true")); // if per-hit is off, focus beams

                boolean rateLimitActive = !"false".equalsIgnoreCase(logConfig.optString("rate_limit", "false"));
                if (rateLimitActive) {
                    try {
                        PER_SHIP_MIN_LOG_INTERVAL = Float.parseFloat(logConfig.optString("min_interval_sec", "0.25"));
                    } catch (NumberFormatException e) {
                        PER_SHIP_MIN_LOG_INTERVAL = 0.25f;
                    }
                } else {
                    PER_SHIP_MIN_LOG_INTERVAL = 0f;
                }

                LOG.info("[ShieldHP_LOGGER] Active and tracking data metrics with adjusted verbosity flags.");
            }
        } catch (Exception e) {
            LOG.warn("[ShieldHP_LOGGER] Configuration file not found or corrupted. Defaulting to safe standard debugging values.", e);
            ENABLED = false; // Turn off by default if file can't confirm state
        }
    }

    private void ensureAttached() {
        if (attachedAsListener) return;
        if (engine == null || engine.getListenerManager() == null) return;

        engine.getListenerManager().addListener(this);
        attachedAsListener = true;
        LOG.info("[ShieldHP_LOGGER] Attached DamageListener to combat engine.");
    }

    @Override
    public void reportDamageApplied(Object source, CombatEntityAPI target, ApplyDamageResultAPI result) {
        // PERFORMANCE CRITICAL GUARD: Instantly dump out if logging is disabled before executing math or allocations
        if (!ENABLED || !configLoaded) return;
        if (!(target instanceof ShipAPI) || result == null) return;

        ShipAPI ship = (ShipAPI) target;
        if (!ship.isAlive()) return;

        ShieldAPI shield = ship.getShield();
        if (shield == null) return;

        boolean isBeam = (source instanceof BeamAPI);
        if (BEAMS_ONLY && !isBeam) return;

        float engineShieldDmg = safe(result.getDamageToShields());
        float engineOverMax = INCLUDE_OVERMAX_IN_TOTAL ? safe(result.getOverMaxDamageToShields()) : 0f;
        float engineTotalShieldDmg = engineShieldDmg + Math.max(0f, engineOverMax);
        if (engineTotalShieldDmg <= 0f) return;

        if (!isBeam && !LOG_NON_BEAM_EVENTS) return;

        float customMax = ShieldHitpointManager.getMaxShieldHP(ship);
        float customNow = ShieldHitpointManager.getShieldHP(ship);

        if (customMax <= 0f && !LOG_SHIPS_WITHOUT_CUSTOM_HP) return;

        // Log max HP profile once per deployment cycle
        if (customMax > 0f && !loggedMaxForShip.contains(ship)) {
            loggedMaxForShip.add(ship);
            LOG.info(String.format(Locale.ROOT,
                    "[ShieldHP_LOGGER] Ship=%s hullId=%s owner=%d | CustomShieldHPMax=%.2f | fluxPerPoint=%.3f | shieldUpkeep=%.3f",
                    safeShipName(ship), safeHullId(ship), ship.getOwner(),
                    customMax, safe(shield.getFluxPerPointOfDamage()), safe(shield.getUpkeep())
            ));
        }

        // Throttle calculation
        float t = getCombatTime();
        if (PER_SHIP_MIN_LOG_INTERVAL > 0f) {
            Float lastT = lastLogAtCombatTime.get(ship);
            if (lastT != null && (t - lastT) < PER_SHIP_MIN_LOG_INTERVAL) {
                lastObservedCustomHp.put(ship, customNow);
                return;
            }
            lastLogAtCombatTime.put(ship, t);
        }

        Float prev = lastObservedCustomHp.get(ship);
        float observedDelta = (prev != null) ? Math.max(0f, prev - customNow) : 0f;
        lastObservedCustomHp.put(ship, customNow);

        String weaponId = ShieldHPDamageScaler.extractWeaponId(source);
        String weaponName = extractWeaponName(source);
        String weaponType = extractWeaponType(source);

        float expectedHpDamage = ShieldHPDamageScaler.computeShieldHpDamage(source, ship, engineTotalShieldDmg);
        boolean shieldOn = shield.isOn();
        float fluxPerPoint = safe(shield.getFluxPerPointOfDamage());

        LOG.info(String.format(Locale.ROOT,
                "[ShieldHP_LOGGER] t=%.3f beam=%s | Ship=%s hullId=%s owner=%d shieldOn=%s " +
                        "| CustomHP=%.2f/%.2f obsDelta=%.4f " +
                        "| EngineShield=%.4f overMax=%.4f total=%.4f fluxPerPoint=%.3f " +
                        "| ExpectedHpDamage=%.4f " +
                        "| WeaponId=%s WeaponName=%s WeaponType=%s",
                t, Boolean.toString(isBeam),
                safeShipName(ship), safeHullId(ship), ship.getOwner(), Boolean.toString(shieldOn),
                customNow, customMax, observedDelta,
                engineShieldDmg, engineOverMax, engineTotalShieldDmg, fluxPerPoint,
                expectedHpDamage,
                nullToDash(weaponId), nullToDash(weaponName), nullToDash(weaponType)
        ));
    }

    // --------------------
    // Helpers
    // --------------------

    private float getCombatTime() {
        if (engine == null) engine = Global.getCombatEngine();
        if (engine == null) return 0f;
        try {
            return engine.getTotalElapsedTime(false);
        } catch (Throwable ignored) {
            return 0f;
        }
    }

    private static float safe(float v) {
        return (Float.isNaN(v) || Float.isInfinite(v)) ? 0f : v;
    }

    private static String safeShipName(ShipAPI ship) {
        try {
            String n = ship.getName();
            if (n != null && !n.trim().isEmpty()) return n.trim();
        } catch (Throwable ignored) { }
        try {
            return ship.getHullSpec().getHullName();
        } catch (Throwable ignored) { }
        return "UNKNOWN_SHIP";
    }

    private static String safeHullId(ShipAPI ship) {
        try {
            if (ship.getHullSpec() != null) return ship.getHullSpec().getHullId();
        } catch (Throwable ignored) { }
        return "UNKNOWN_HULL_ID";
    }

    private static String nullToDash(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
    }

    private static String extractWeaponName(Object source) {
        WeaponAPI w = extractWeapon(source);
        if (w == null) return null;
        try {
            if (w.getSpec() != null) return w.getSpec().getWeaponName();
        } catch (Throwable ignored) { }
        try {
            return w.getDisplayName();
        } catch (Throwable ignored) { }
        return null;
    }

    private static String extractWeaponType(Object source) {
        WeaponAPI w = extractWeapon(source);
        if (w == null) return null;
        try {
            WeaponAPI.WeaponType t = w.getType();
            return t != null ? t.name() : null;
        } catch (Throwable ignored) { }
        return null;
    }

    private static WeaponAPI extractWeapon(Object source) {
        if (source == null) return null;
        try {
            if (source instanceof BeamAPI) {
                return ((BeamAPI) source).getWeapon();
            }
            if (source instanceof DamagingProjectileAPI) {
                return ((DamagingProjectileAPI) source).getWeapon();
            }
            if (source instanceof WeaponAPI) {
                return (WeaponAPI) source;
            }
        } catch (Throwable ignored) { }
        return null;
    }
}