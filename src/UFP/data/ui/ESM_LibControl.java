package UFP.data.ui;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import com.fs.starfarer.api.Global;
import org.lwjgl.input.Keyboard;

public class ESM_LibControl implements LunaSettingsListener {

    private static String cachedModId = null;

    // --- Core ESM Settings ---
    public static final String FIELD_ZERO_FLUX = "ufp_zeroFluxGeneration";
    public static final String FIELD_SHIELD_ENERGY = "ufp_shieldEnergyRequirement";

    // --- ShieldLib Master Toggle ---
    public static final String FIELD_ENABLE_SHIELD_LIB = "ufp_enableShieldLib";

    // --- ShieldLib Configuration Settings ---
    public static final String FIELD_SHIELD_RESTORE_KEYBIND = "ufp_shieldRestoreKeybind";
    public static final String FIELD_SHIELD_RESTORE_MAX_USES = "ufp_shieldRestoreMaxUses";
    public static final String FIELD_SHIELD_RESTORE_UNLIMITED = "ufp_shieldRestoreUnlimited";
    public static final String FIELD_SHIELD_RESTORE_RECHARGE_INTERVAL = "ufp_shieldRestoreRechargeInterval";
    public static final String FIELD_SHIELD_RESTORE_DEFAULT_COOLDOWN = "ufp_shieldRestoreDefaultCooldown";
    public static final String FIELD_SHIELD_RESTORE_DEFAULT_HP_PERCENT = "ufp_shieldRestoreDefaultHpPercent";

    // --- Energy Cost Mode Settings ---
    public static final String FIELD_SHIELD_RESTORE_COST_IS_PERCENT = "ufp_shieldRestoreCostIsPercent";
    public static final String FIELD_SHIELD_RESTORE_COST_PERCENT    = "ufp_shieldRestoreCostPercent";
    public static final String FIELD_SHIELD_RESTORE_COST_FLAT       = "ufp_shieldRestoreCostFlat";

    // --- Cached Core States ---
    private static boolean cachedZeroFlux = false;
    private static boolean cachedShieldEnergy = false;
    private static boolean initialized = false;

    // --- Cached ShieldLib States ---
    private static boolean cachedEnableShieldLib = true;
    private static int cachedKeybind = Keyboard.KEY_NUMPAD0; // Keycode 82
    private static int cachedMaxUses = 4;
    private static boolean cachedUnlimitedUses = false;
    private static float cachedRechargeInterval = 0f;
    private static float cachedDefaultCooldown = 25f;
    private static float cachedDefaultHpPercent = 0.25f;

    // --- Cached Energy Cost States ---
    private static boolean cachedCostIsPercent = true;
    private static float cachedCostPercent = 0.25f;
    private static float cachedCostFlat = 500f;

    /**
     * Dynamically finds active Mod ID ('UFP-DEV' or 'UFP').
     */
    public static String getModId() {
        if (cachedModId != null) return cachedModId;
        try {
            if (Global.getSettings() != null && Global.getSettings().getModManager() != null) {
                if (Global.getSettings().getModManager().isModEnabled("UFP-DEV")) {
                    cachedModId = "UFP-DEV";
                } else {
                    cachedModId = "UFP";
                }
            } else {
                cachedModId = "UFP";
            }
        } catch (Throwable e) {
            cachedModId = "UFP";
        }
        return cachedModId;
    }

    public static void init() {
        if (initialized) return;
        try {
            ESM_LibControl listener = new ESM_LibControl();
            LunaSettings.addSettingsListener(listener);
            syncSettings();
            initialized = true;

            Global.getLogger(ESM_LibControl.class).info("[ESM_LibControl] Initialized for Mod ID: " + getModId());
        } catch (Throwable e) {
            Global.getLogger(ESM_LibControl.class).error("Failed to initialize LunaSettings listener", e);
        }
    }

    public static void syncSettings() {
        try {
            String modId = getModId();

            // Core ESM Toggles
            Boolean zeroFluxVal = LunaSettings.getBoolean(modId, FIELD_ZERO_FLUX);
            cachedZeroFlux = (zeroFluxVal != null) ? zeroFluxVal : false;

            Boolean shieldEnergyVal = LunaSettings.getBoolean(modId, FIELD_SHIELD_ENERGY);
            cachedShieldEnergy = (shieldEnergyVal != null) ? shieldEnergyVal : false;

            // ShieldLib Master Toggle
            Boolean enableShieldLibVal = LunaSettings.getBoolean(modId, FIELD_ENABLE_SHIELD_LIB);
            cachedEnableShieldLib = (enableShieldLibVal != null) ? enableShieldLibVal : true;

            // Keycode Setting (Reads current LWJGL key integer set in LunaLib UI)
            Integer keybindVal = LunaSettings.getInt(modId, FIELD_SHIELD_RESTORE_KEYBIND);
            cachedKeybind = (keybindVal != null) ? keybindVal : Keyboard.KEY_NUMPAD0;

            // Usage & Cooldown Settings
            Integer maxUsesVal = LunaSettings.getInt(modId, FIELD_SHIELD_RESTORE_MAX_USES);
            cachedMaxUses = (maxUsesVal != null) ? Math.max(0, maxUsesVal) : 4;

            Boolean unlimitedVal = LunaSettings.getBoolean(modId, FIELD_SHIELD_RESTORE_UNLIMITED);
            cachedUnlimitedUses = (unlimitedVal != null) ? unlimitedVal : false;

            Float rechargeVal = LunaSettings.getFloat(modId, FIELD_SHIELD_RESTORE_RECHARGE_INTERVAL);
            cachedRechargeInterval = (rechargeVal != null) ? Math.max(0f, rechargeVal) : 0f;

            Float cdVal = LunaSettings.getFloat(modId, FIELD_SHIELD_RESTORE_DEFAULT_COOLDOWN);
            cachedDefaultCooldown = (cdVal != null) ? Math.max(0f, cdVal) : 25f;

            Float hpPctVal = LunaSettings.getFloat(modId, FIELD_SHIELD_RESTORE_DEFAULT_HP_PERCENT);
            cachedDefaultHpPercent = (hpPctVal != null) ? Math.max(0f, hpPctVal) : 0.25f;

            // Energy Cost Calculation Settings
            Boolean costIsPercentVal = LunaSettings.getBoolean(modId, FIELD_SHIELD_RESTORE_COST_IS_PERCENT);
            cachedCostIsPercent = (costIsPercentVal != null) ? costIsPercentVal : true;

            Float costPctVal = LunaSettings.getFloat(modId, FIELD_SHIELD_RESTORE_COST_PERCENT);
            cachedCostPercent = (costPctVal != null) ? Math.max(0f, costPctVal) : 0.25f;

            Float costFlatVal = LunaSettings.getFloat(modId, FIELD_SHIELD_RESTORE_COST_FLAT);
            cachedCostFlat = (costFlatVal != null) ? Math.max(0f, costFlatVal) : 500f;

            Global.getLogger(ESM_LibControl.class).info(
                    "[ESM_LibControl] Synced LunaSettings [" + modId + "] -> Keycode: "
                            + cachedKeybind + " (" + Keyboard.getKeyName(cachedKeybind) + ")");

        } catch (Throwable e) {
            Global.getLogger(ESM_LibControl.class).error("[ESM_LibControl] Error querying LunaSettings", e);
        }
    }

    @Override
    public void settingsChanged(String modId) {
        if (getModId().equalsIgnoreCase(modId)) {
            syncSettings();
        }
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    public static boolean isZeroFluxGenerationEnabled() {
        if (!initialized) init();
        return cachedZeroFlux;
    }

    public static boolean isShieldEnergyRequirementEnabled() {
        if (!initialized) init();
        return cachedShieldEnergy;
    }

    public static boolean isShieldLibEnabled() {
        if (!initialized) init();
        return cachedEnableShieldLib;
    }

    public static int getShieldRestoreKeybind() {
        if (!initialized) init();
        return cachedKeybind;
    }

    public static int getShieldRestoreMaxUses() {
        if (!initialized) init();
        return cachedMaxUses;
    }

    public static boolean isShieldRestoreUnlimited() {
        if (!initialized) init();
        return cachedUnlimitedUses;
    }

    public static float getShieldRestoreRechargeInterval() {
        if (!initialized) init();
        return cachedRechargeInterval;
    }

    public static float getShieldRestoreDefaultCooldown() {
        if (!initialized) init();
        return cachedDefaultCooldown;
    }

    public static float getShieldRestoreDefaultHpPercent() {
        if (!initialized) init();
        return cachedDefaultHpPercent;
    }

    public static boolean isEnergyCostPercentBased() {
        if (!initialized) init();
        return cachedCostIsPercent;
    }

    public static float getShieldRestoreCostPercent() {
        if (!initialized) init();
        return cachedCostPercent;
    }

    public static float getShieldRestoreCostFlat() {
        if (!initialized) init();
        return cachedCostFlat;
    }
}