package UFP.data.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import UFP.data.logger.ScriptPerformanceReader;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ShieldHPDamageScaler {

    public static boolean CONVERT_SHIELD_FLUX_TO_RAW_DAMAGE = false;
    public static boolean INCLUDE_OVERMAX_SHIELD_DAMAGE = false;
    public static float DEFAULT_MULT = 1f;

    public static final String FALLBACK_DPS_KEY  = "defaultDamagePerSecond";
    public static final String FALLBACK_SHOT_KEY = "defaultDamagePerShot";

    private static final Map<String, Float> runtimeWeaponOverrides = new HashMap<>();
    private static final Map<String, Float> runtimeProjectileOverrides = new HashMap<>();
    private enum DamageKind { BEAM, PROJECTILE, UNKNOWN }

    private ShieldHPDamageScaler() { }

    public static void loadIfNeeded() {
        ScriptPerformanceReader.startTrack("ShieldHPDamageScaler.loadIfNeeded");
        ScriptPerformanceReader.countOperation("ShieldHPDamageScaler.loadIfNeeded");
        try {
            UFP_CSV_Manager.loadAllCSVData();
        } finally {
            ScriptPerformanceReader.endTrack("ShieldHPDamageScaler.loadIfNeeded");
        }
    }

    public static void reload() {
        ScriptPerformanceReader.startTrack("ShieldHPDamageScaler.reload");
        ScriptPerformanceReader.countOperation("ShieldHPDamageScaler.reload");
        try {
            UFP_CSV_Manager.forceReload();
        } finally {
            ScriptPerformanceReader.endTrack("ShieldHPDamageScaler.reload");
        }
    }

    public static void setShieldHpMult(String weaponId, float mult) {
        if (weaponId == null) return;
        runtimeWeaponOverrides.put(weaponId.trim(), mult);
    }

    public static void clearShieldHpMult(String weaponId) {
        if (weaponId == null) return;
        runtimeWeaponOverrides.remove(weaponId.trim());
    }

    public static void setShieldHpMultForProjectile(String projectileId, float mult) {
        if (projectileId == null) return;
        runtimeProjectileOverrides.put(projectileId.trim(), mult);
    }

    public static void clearShieldHpMultForProjectile(String projectileId) {
        if (projectileId == null) return;
        runtimeProjectileOverrides.remove(projectileId.trim());
    }

    public static void clearAllOverrides() {
        runtimeWeaponOverrides.clear();
        runtimeProjectileOverrides.clear();
    }

    public static Map<String, Float> getAllWeaponMultipliersView() {
        loadIfNeeded();
        Map<String, Float> merged = new HashMap<>(UFP_CSV_Manager.getWeaponDamageMultMap());
        merged.putAll(runtimeWeaponOverrides);
        return Collections.unmodifiableMap(merged);
    }

    public static Map<String, Float> getAllProjectileMultipliersView() {
        loadIfNeeded();
        Map<String, Float> merged = new HashMap<>(UFP_CSV_Manager.getProjectileDamageMultMap());
        merged.putAll(runtimeProjectileOverrides);
        return Collections.unmodifiableMap(merged);
    }

    public static float computeShieldHpDamage(Object source, ShipAPI targetShip, ApplyDamageResultAPI result) {
        ScriptPerformanceReader.startTrack("ShieldHPDamageScaler.computeShieldHpDamage_ResultAPI");
        ScriptPerformanceReader.countOperation("ShieldHPDamageScaler.computeShieldHpDamage_ResultAPI");
        try {
            if (result == null) return 0f;

            float shieldDamage = result.getDamageToShields();
            if (INCLUDE_OVERMAX_SHIELD_DAMAGE) {
                shieldDamage += Math.max(0f, result.getOverMaxDamageToShields());
            }

            return computeShieldHpDamage(source, targetShip, shieldDamage);
        } finally {
            ScriptPerformanceReader.endTrack("ShieldHPDamageScaler.computeShieldHpDamage_ResultAPI");
        }
    }

    public static float computeShieldHpDamage(Object source, ShipAPI targetShip, float shieldDamage) {
        ScriptPerformanceReader.startTrack("ShieldHPDamageScaler.computeShieldHpDamage_Raw");
        ScriptPerformanceReader.countOperation("ShieldHPDamageScaler.computeShieldHpDamage_Raw");
        try {
            if (shieldDamage <= 0f) return 0f;

            loadIfNeeded();

            float raw = shieldDamage;
            if (CONVERT_SHIELD_FLUX_TO_RAW_DAMAGE && targetShip != null) {
                ShieldAPI shield = targetShip.getShield();
                if (shield != null) {
                    float fluxPerPoint = shield.getFluxPerPointOfDamage();
                    if (fluxPerPoint > 0f) raw = raw / fluxPerPoint;
                }
            }

            String weaponId = extractWeaponId(source);
            if ((weaponId == null || weaponId.trim().isEmpty()) && targetShip != null) {
                weaponId = inferWeaponIdFromEngine(source, targetShip);
            }

            String projectileId = extractProjectileSpecId(source);
            if ((projectileId == null || projectileId.trim().isEmpty()) && targetShip != null) {
                projectileId = inferProjectileSpecIdFromEngine(source, targetShip);
            }

            DamageKind kind = inferDamageKind(source, targetShip);
            MultResult mr = resolveMultiplier(weaponId, projectileId, kind);
            raw *= mr.mult;

            Global.getLogger(ShieldHPDamageScaler.class).info(
                    "[Scaler] wid=" + (weaponId == null ? "-" : weaponId) +
                            " pid=" + (projectileId == null ? "-" : projectileId) +
                            " kind=" + kind +
                            " usedKey=" + mr.usedKey +
                            " mult=" + mr.mult +
                            " in=" + shieldDamage
            );

            return Math.max(0f, raw);
        } finally {
            ScriptPerformanceReader.endTrack("ShieldHPDamageScaler.computeShieldHpDamage_Raw");
        }
    }

    private static final class MultResult {
        final float mult;
        final String usedKey;
        MultResult(float mult, String usedKey) { this.mult = mult; this.usedKey = usedKey; }
    }

    private static MultResult resolveMultiplier(String weaponId, String projectileId, DamageKind kind) {
        ScriptPerformanceReader.startTrack("ShieldHPDamageScaler.resolveMultiplier");
        ScriptPerformanceReader.countOperation("ShieldHPDamageScaler.resolveMultiplier");
        try {
            Float w = lookupWeaponMult(weaponId);
            if (w != null) return new MultResult(w, "weapon:" + weaponId);

            Float p = lookupProjectileMult(projectileId);
            if (p != null) return new MultResult(p, "proj:" + projectileId);

            if (kind == DamageKind.BEAM) {
                Float fdps = lookupWeaponMult(FALLBACK_DPS_KEY);
                if (fdps != null) return new MultResult(fdps, "fallback:" + FALLBACK_DPS_KEY);
            } else if (kind == DamageKind.PROJECTILE) {
                Float fshot = lookupWeaponMult(FALLBACK_SHOT_KEY);
                if (fshot != null) return new MultResult(fshot, "fallback:" + FALLBACK_SHOT_KEY);
            } else {
                Float fdps = lookupWeaponMult(FALLBACK_DPS_KEY);
                if (fdps != null) return new MultResult(fdps, "fallback:" + FALLBACK_DPS_KEY);
                Float fshot = lookupWeaponMult(FALLBACK_SHOT_KEY);
                if (fshot != null) return new MultResult(fshot, "fallback:" + FALLBACK_SHOT_KEY);
            }

            return new MultResult(DEFAULT_MULT, "default");
        } finally {
            ScriptPerformanceReader.endTrack("ShieldHPDamageScaler.resolveMultiplier");
        }
    }

    private static Float lookupWeaponMult(String weaponId) {
        ScriptPerformanceReader.startTrack("ShieldHPDamageScaler.lookupWeaponMult");
        ScriptPerformanceReader.countOperation("ShieldHPDamageScaler.lookupWeaponMult");
        try {
            if (weaponId == null) return null;
            String k = weaponId.trim();
            if (k.isEmpty()) return null;

            Float ov = runtimeWeaponOverrides.get(k);
            if (ov != null) return ov;

            return UFP_CSV_Manager.getWeaponDamageMultMap().get(k);
        } finally {
            ScriptPerformanceReader.endTrack("ShieldHPDamageScaler.lookupWeaponMult");
        }
    }

    private static Float lookupProjectileMult(String projectileId) {
        ScriptPerformanceReader.startTrack("ShieldHPDamageScaler.lookupProjectileMult");
        ScriptPerformanceReader.countOperation("ShieldHPDamageScaler.lookupProjectileMult");
        try {
            if (projectileId == null) return null;
            String k = projectileId.trim();
            if (k.isEmpty()) return null;

            Float ov = runtimeProjectileOverrides.get(k);
            if (ov != null) return ov;

            return UFP_CSV_Manager.getProjectileDamageMultMap().get(k);
        } finally {
            ScriptPerformanceReader.endTrack("ShieldHPDamageScaler.lookupProjectileMult");
        }
    }

    public static String extractWeaponId(Object source) {
        ScriptPerformanceReader.startTrack("ShieldHPDamageScaler.extractWeaponId");
        ScriptPerformanceReader.countOperation("ShieldHPDamageScaler.extractWeaponId");
        try {
            WeaponAPI weapon = extractWeapon(source);
            if (weapon == null) return null;

            try {
                WeaponSpecAPI spec = weapon.getSpec();
                if (spec != null) return spec.getWeaponId();
            } catch (Throwable ignored) { }

            try {
                return weapon.getId();
            } catch (Throwable ignored) { }

            return null;
        } finally {
            ScriptPerformanceReader.endTrack("ShieldHPDamageScaler.extractWeaponId");
        }
    }

    public static String extractProjectileSpecId(Object source) {
        ScriptPerformanceReader.startTrack("ShieldHPDamageScaler.extractProjectileSpecId");
        ScriptPerformanceReader.countOperation("ShieldHPDamageScaler.extractProjectileSpecId");
        try {
            if (source instanceof DamagingProjectileAPI) {
                try {
                    return ((DamagingProjectileAPI) source).getProjectileSpecId();
                } catch (Throwable ignored) { }
            }
            return null;
        } finally {
            ScriptPerformanceReader.endTrack("ShieldHPDamageScaler.extractProjectileSpecId");
        }
    }

    private static WeaponAPI extractWeapon(Object source) {
        if (source == null) return null;

        if (source instanceof BeamAPI) {
            return ((BeamAPI) source).getWeapon();
        }
        if (source instanceof DamagingProjectileAPI) {
            return ((DamagingProjectileAPI) source).getWeapon();
        }
        if (source instanceof WeaponAPI) {
            return (WeaponAPI) source;
        }
        return null;
    }

    private static DamageKind inferDamageKind(Object source, ShipAPI targetShip) {
        ScriptPerformanceReader.startTrack("ShieldHPDamageScaler.inferDamageKind");
        ScriptPerformanceReader.countOperation("ShieldHPDamageScaler.inferDamageKind");
        try {
            if (source instanceof BeamAPI) return DamageKind.BEAM;
            if (source instanceof DamagingProjectileAPI) return DamageKind.PROJECTILE;

            WeaponAPI w = extractWeapon(source);
            if (w != null && w.getSpec() != null) {
                try {
                    return w.getSpec().isBeam() ? DamageKind.BEAM : DamageKind.PROJECTILE;
                } catch (Throwable ignored) { }
            }

            if (targetShip != null) {
                DamageKind k = inferDamageKindFromEngine(source, targetShip);
                if (k != DamageKind.UNKNOWN) return k;
            }

            return DamageKind.UNKNOWN;
        } finally {
            ScriptPerformanceReader.endTrack("ShieldHPDamageScaler.inferDamageKind");
        }
    }

    private static DamageKind inferDamageKindFromEngine(Object source, ShipAPI targetShip) {
        ScriptPerformanceReader.startTrack("ShieldHPDamageScaler.inferDamageKindFromEngine");
        ScriptPerformanceReader.countOperation("ShieldHPDamageScaler.inferDamageKindFromEngine");
        try {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || targetShip == null) return DamageKind.UNKNOWN;

            ShipAPI attacker = (source instanceof ShipAPI) ? (ShipAPI) source : null;

            for (BeamAPI b : engine.getBeams()) {
                if (b == null) continue;
                if (b.getDamageTarget() != targetShip) continue;
                if (attacker != null && b.getSource() != attacker) continue;
                return DamageKind.BEAM;
            }

            for (DamagingProjectileAPI p : engine.getProjectiles()) {
                if (p == null) continue;
                if (p.getDamageTarget() != targetShip) continue;
                if (attacker != null && p.getSource() != attacker) continue;
                return DamageKind.PROJECTILE;
            }

            return DamageKind.UNKNOWN;
        } finally {
            ScriptPerformanceReader.endTrack("ShieldHPDamageScaler.inferDamageKindFromEngine");
        }
    }

    private static String inferWeaponIdFromEngine(Object source, ShipAPI targetShip) {
        ScriptPerformanceReader.startTrack("ShieldHPDamageScaler.inferWeaponIdFromEngine");
        ScriptPerformanceReader.countOperation("ShieldHPDamageScaler.inferWeaponIdFromEngine");
        try {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || targetShip == null) return null;

            ShipAPI attacker = (source instanceof ShipAPI) ? (ShipAPI) source : null;

            for (BeamAPI b : engine.getBeams()) {
                if (b == null) continue;
                if (b.getDamageTarget() != targetShip) continue;
                if (attacker != null && b.getSource() != attacker) continue;

                WeaponAPI w = b.getWeapon();
                if (w != null && w.getSpec() != null) return w.getSpec().getWeaponId();
            }

            for (DamagingProjectileAPI p : engine.getProjectiles()) {
                if (p == null) continue;
                if (p.getDamageTarget() != targetShip) continue;
                if (attacker != null && p.getSource() != attacker) continue;

                WeaponAPI w = p.getWeapon();
                if (w != null && w.getSpec() != null) return w.getSpec().getWeaponId();
            }

            return null;
        } finally {
            ScriptPerformanceReader.endTrack("ShieldHPDamageScaler.inferWeaponIdFromEngine");
        }
    }

    private static String inferProjectileSpecIdFromEngine(Object source, ShipAPI targetShip) {
        ScriptPerformanceReader.startTrack("ShieldHPDamageScaler.inferProjectileSpecIdFromEngine");
        ScriptPerformanceReader.countOperation("ShieldHPDamageScaler.inferProjectileSpecIdFromEngine");
        try {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || targetShip == null) return null;

            ShipAPI attacker = (source instanceof ShipAPI) ? (ShipAPI) source : null;

            for (DamagingProjectileAPI p : engine.getProjectiles()) {
                if (p == null) continue;
                if (p.getDamageTarget() != targetShip) continue;
                if (attacker != null && p.getSource() != attacker) continue;
                String pid = p.getProjectileSpecId();
                if (pid != null && !pid.trim().isEmpty()) return pid.trim();
            }

            return null;
        } finally {
            ScriptPerformanceReader.endTrack("ShieldHPDamageScaler.inferProjectileSpecIdFromEngine");
        }
    }
}