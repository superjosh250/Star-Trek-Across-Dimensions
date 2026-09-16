package UFP.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class UFP_CSV_Manager {

    private static final String ESM_CSV_PATH = "data/config/UFPConfig/esm_data.csv";
    private static final String SHIELD_HP_CSV_PATH = "data/config/UFPConfig/shield_data.csv";
    private static final String SHIELD_GRAPHICS_CSV_PATH = "data/config/UFPConfig/shield_graphics.csv";
    private static final String DAMAGE_DATA_CSV_PATH = "data/config/UFPConfig/damage_data.csv";
    private static final String LOGGER_SETTINGS_CSV_PATH = "data/config/UFPConfig/logger_settings.csv";

    private static final Map<String, ESMDataEntry> ESM_CACHE = new HashMap<>();
    private static final Map<String, Float> SHIELD_HP_CACHE = new HashMap<>();
    private static final Map<String, Float> DAMAGE_MULT_BY_WEAPON_CACHE = new HashMap<>();
    private static final Map<String, Float> DAMAGE_MULT_BY_PROJECTILE_CACHE = new HashMap<>();

    // ColTech Specific Damage Overrides
    private static final Map<String, Float> COLTECH_MULT_BY_WEAPON_CACHE = new HashMap<>();
    private static final Map<String, Float> COLTECH_MULT_BY_PROJECTILE_CACHE = new HashMap<>();

    // Logger Settings Cache
    private static final Map<String, Boolean> LOGGER_TOGGLES_CACHE = new HashMap<>();

    private static String cachedFedShieldStyle = "BubbleShield";
    private static String cachedTerranShieldStyle = "BubbleShield";
    private static String cachedColTechShieldStyle = "BubbleShield";
    private static String cachedRomTLEShieldStyle = "BubbleShield64";

    private static boolean isLoaded = false;

    public static class ESMDataEntry {
        public final float mainPwr;
        public final float pwrRecharge;
        public final float auxPwr;
        public final float emergencyPwr;
        public final float specialEnergy;

        public ESMDataEntry(float mainPwr, float pwrRecharge, float auxPwr, float emergencyPwr, float specialEnergy) {
            this.mainPwr = Math.max(0f, mainPwr);
            this.pwrRecharge = Math.max(0f, pwrRecharge);
            this.auxPwr = Math.max(0f, auxPwr);
            this.emergencyPwr = Math.max(0f, emergencyPwr);
            this.specialEnergy = Math.max(0f, specialEnergy);
        }
    }

    public static void loadAllCSVData() {
        if (isLoaded) return;
        isLoaded = true;

        for (ModSpecAPI mod : Global.getSettings().getModManager().getEnabledModsCopy()) {
            String modId = mod.getId();

            // --- ESM Config Block ---
            try {
                JSONArray esmConfig = Global.getSettings().getMergedSpreadsheetDataForMod("hull_id", ESM_CSV_PATH, modId);
                for (int i = 0; i < esmConfig.length(); i++) {
                    JSONObject row = esmConfig.getJSONObject(i);
                    String hullId = row.optString("hull_id");
                    if (hullId == null || hullId.isEmpty()) continue;

                    float mainPwr = (float) row.optDouble("main_pwr", 0.0);
                    float pwrRecharge = (float) row.optDouble("pwr_recharge", 0.0);
                    float auxPwr = (float) row.optDouble("aux_pwr", 0.0);
                    float emergencyPwr = (float) row.optDouble("emergency_pwr", 0.0);
                    float specialEnergy = (float) row.optDouble("special_energy", 0.0);

                    ESM_CACHE.put(hullId, new ESMDataEntry(mainPwr, pwrRecharge, auxPwr, emergencyPwr, specialEnergy));
                }
            } catch (Exception ignored) {}

            // --- Shield Data Block ---
            try {
                JSONArray shieldConfig = Global.getSettings().getMergedSpreadsheetDataForMod("hull_id", SHIELD_HP_CSV_PATH, modId);
                for (int i = 0; i < shieldConfig.length(); i++) {
                    JSONObject row = shieldConfig.getJSONObject(i);
                    String hullId = row.optString("hull_id");
                    if (hullId == null || hullId.isEmpty()) continue;

                    float hp = (float) row.optDouble("shield hitpoints", 0.0);
                    if (hp > 0f) {
                        SHIELD_HP_CACHE.put(hullId, hp);
                    }
                }
            } catch (Exception ignored) {}

            // --- Damage Multipliers Block ---
            try {
                JSONArray damageConfig = Global.getSettings().getMergedSpreadsheetDataForMod("weapon_id", DAMAGE_DATA_CSV_PATH, modId);
                for (int i = 0; i < damageConfig.length(); i++) {
                    JSONObject row = damageConfig.getJSONObject(i);

                    String weaponId = row.optString("weapon_id", "").trim();
                    String projectileId = row.optString("projectile_id", "").trim();

                    // Standard Shield HP Multipliers
                    float mult = (float) row.optDouble("shield_hp_mult", -1.0);
                    if (mult >= 0f) {
                        if (!weaponId.isEmpty()) DAMAGE_MULT_BY_WEAPON_CACHE.put(weaponId, mult);
                        if (!projectileId.isEmpty()) DAMAGE_MULT_BY_PROJECTILE_CACHE.put(projectileId, mult);
                    }

                    // ColTech Override Multipliers
                    float ctMult = (float) row.optDouble("colTech_override", -1.0);
                    if (ctMult >= 0f) {
                        if (!weaponId.isEmpty()) COLTECH_MULT_BY_WEAPON_CACHE.put(weaponId, ctMult);
                        if (!projectileId.isEmpty()) COLTECH_MULT_BY_PROJECTILE_CACHE.put(projectileId, ctMult);
                    }
                }
            } catch (Exception ignored) {}

            // --- Shield Graphics Block ---
            try {
                JSONArray graphicsConfig = Global.getSettings().getMergedSpreadsheetDataForMod("shieldGraphic_id", SHIELD_GRAPHICS_CSV_PATH, modId);
                for (int i = 0; i < graphicsConfig.length(); i++) {
                    JSONObject row = graphicsConfig.getJSONObject(i);
                    String graphicId = row.optString("shieldGraphic_id");
                    if (graphicId == null || graphicId.isEmpty()) continue;

                    if (row.optBoolean("Fed_Shields", false)) cachedFedShieldStyle = graphicId;
                    if (row.optBoolean("Terran_Shields", false)) cachedTerranShieldStyle = graphicId;
                    if (row.optBoolean("ColTech_Shields", false)) cachedColTechShieldStyle = graphicId;
                    if (row.optBoolean("RomTLE_Shields", false)) cachedRomTLEShieldStyle = graphicId;
                }
            } catch (Exception ignored) {}

            // --- Logger Settings Block ---
            try {
                JSONArray loggerConfig = Global.getSettings().getMergedSpreadsheetDataForMod("logger_id", LOGGER_SETTINGS_CSV_PATH, modId);
                for (int i = 0; i < loggerConfig.length(); i++) {
                    JSONObject row = loggerConfig.getJSONObject(i);
                    String loggerId = row.optString("logger_id", "").trim().toLowerCase(Locale.ROOT);
                    if (loggerId.isEmpty() || loggerId.startsWith("#")) continue;

                    String boolStr = row.optString("boolean", "").trim().toLowerCase(Locale.ROOT);
                    boolean isEnabled = !"false".equals(boolStr);

                    LOGGER_TOGGLES_CACHE.put(loggerId, isEnabled);
                }
            } catch (Exception ignored) {}
        }
    }

    public static void forceReload() {
        isLoaded = false;
        ESM_CACHE.clear();
        SHIELD_HP_CACHE.clear();
        DAMAGE_MULT_BY_WEAPON_CACHE.clear();
        DAMAGE_MULT_BY_PROJECTILE_CACHE.clear();
        COLTECH_MULT_BY_WEAPON_CACHE.clear();
        COLTECH_MULT_BY_PROJECTILE_CACHE.clear();
        LOGGER_TOGGLES_CACHE.clear();
        loadAllCSVData();
    }

    public static boolean isLoggingEnabled(String loggerId) {
        loadAllCSVData();
        if (loggerId == null) return false;

        String lowerKey = loggerId.toLowerCase(Locale.ROOT);
        if (LOGGER_TOGGLES_CACHE.containsKey(lowerKey)) {
            return LOGGER_TOGGLES_CACHE.get(lowerKey);
        }

        for (Map.Entry<String, Boolean> entry : LOGGER_TOGGLES_CACHE.entrySet()) {
            if (lowerKey.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return false;
    }

    public static ESMDataEntry getEsmData(String hullId) {
        loadAllCSVData();
        if (hullId == null) return null;

        ESMDataEntry entry = ESM_CACHE.get(hullId);
        if (entry != null) return entry;

        try {
            ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
            if (spec != null && spec.getBaseHullId() != null) {
                return ESM_CACHE.get(spec.getBaseHullId());
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static Map<String, Float> getShieldHPMap() {
        loadAllCSVData();
        return SHIELD_HP_CACHE;
    }

    public static float getShieldHP(String hullId) {
        loadAllCSVData();
        Float hp = SHIELD_HP_CACHE.get(hullId);
        return hp != null ? hp : 0f;
    }

    public static Map<String, Float> getWeaponDamageMultMap() {
        loadAllCSVData();
        return DAMAGE_MULT_BY_WEAPON_CACHE;
    }

    public static Map<String, Float> getProjectileDamageMultMap() {
        loadAllCSVData();
        return DAMAGE_MULT_BY_PROJECTILE_CACHE;
    }

    public static Map<String, Float> getColTechWeaponMultMap() {
        loadAllCSVData();
        return COLTECH_MULT_BY_WEAPON_CACHE;
    }

    public static Map<String, Float> getColTechProjectileMultMap() {
        loadAllCSVData();
        return COLTECH_MULT_BY_PROJECTILE_CACHE;
    }

    public static String getSelectedShieldStyle() {
        loadAllCSVData();
        return cachedFedShieldStyle;
    }

    public static String getTerranShieldStyle() {
        loadAllCSVData();
        return cachedTerranShieldStyle;
    }

    public static String getColTechShieldStyle() {
        loadAllCSVData();
        return cachedColTechShieldStyle;
    }

    public static String getRomTLEShieldStyle() {
        loadAllCSVData();
        return cachedRomTLEShieldStyle;
    }
}