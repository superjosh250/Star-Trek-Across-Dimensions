package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Pulse Phaser Overload
 *
 * - Ship speed reduced by 50% while system active
 * - Only weapons whose IDs are listed in data/config/UFPSettings.json -> EnginePowerDiversion.valid_weapons_id
 *   receive +150% damage (i.e. 2.5x total).
 * - Projectiles/beams fired while active get visual "enhanced" particles/glow.
 */
public class PulsePhaserOverload extends BaseShipSystemScript {

    private static final float SPEED_MULT = 0.5f;     // 50% speed (i.e. -50%)
    private static final float DAMAGE_MULT = 2.5f;    // +150% damage => 250% total
    private static final Object KEY_JITTER = new Object();
    private static final Color JITTER_UNDER = new Color(140, 80, 255, 140);
    private static final Color JITTER_OVER  = new Color(200, 140, 255, 90);
    private static final Color GLOW_COLOR   = new Color(210, 160, 255, 255);
    private static final String SETTINGS_PATH = "data/config/UFPSettings.json";
    private static final String ROOT_KEY = "EnginePowerDiversion";
    private static final String DATA_KEY = "UFP_PulsePhaserOverload_Data";

    private static class ShipData {
        DamageBoostListener listener;
        boolean listenerAdded = false;
    }

    private static boolean loaded = false;
    private static boolean override = true;
    private static final Set<String> validWeaponIds = new HashSet<>();

    private static void loadSettingsIfNeeded() {
        if (loaded) return;
        loaded = true;

        try {
            JSONObject json = Global.getSettings().loadJSON(SETTINGS_PATH);
            JSONObject root = json.optJSONObject(ROOT_KEY);
            if (root == null) return;

            override = root.optBoolean("Override", true);

            JSONArray arr = root.optJSONArray("valid_weapons_id");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String id = arr.optString(i, null);
                    if (id != null && !id.isEmpty()) validWeaponIds.add(id.trim());
                }
            }
        } catch (Exception ex) {

            Global.getLogger(PulsePhaserOverload.class).warn("Failed to load " + SETTINGS_PATH, ex);
        }
    }

    private static ShipData getData(ShipAPI ship) {
        Object existing = ship.getCustomData().get(DATA_KEY);
        if (existing instanceof ShipData) return (ShipData) existing;

        ShipData data = new ShipData();
        ship.setCustomData(DATA_KEY, data);
        return data;
    }

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (!(stats.getEntity() instanceof ShipAPI)) return;
        ShipAPI ship = (ShipAPI) stats.getEntity();

        loadSettingsIfNeeded();
        float mult = 1f - (1f - SPEED_MULT) * effectLevel; // lerp from 1 -> SPEED_MULT
        stats.getMaxSpeed().modifyMult(id, mult);
        stats.getAcceleration().modifyMult(id, mult);
        stats.getDeceleration().modifyMult(id, mult);
        ShipData data = getData(ship);
        if (!data.listenerAdded) {
            data.listener = new DamageBoostListener(ship);
            ship.addListener(data.listener);
            data.listenerAdded = true;
        }

        applyShipVisuals(ship, effectLevel);
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != null) {
            applyShotVisuals(engine, ship, effectLevel);
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        if (!(stats.getEntity() instanceof ShipAPI)) return;
        ShipAPI ship = (ShipAPI) stats.getEntity();
        stats.getMaxSpeed().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getDeceleration().unmodify(id);
        ShipData data = getData(ship);
        if (data.listenerAdded && data.listener != null) {
            ship.removeListener(data.listener);
        }
        data.listenerAdded = false;
        data.listener = null;

        ship.setJitter(KEY_JITTER, new Color(0, 0, 0, 0), 0f, 0, 0f, 0f);
        ship.setJitterUnder(KEY_JITTER, new Color(0, 0, 0, 0), 0f, 0, 0f, 0f);
    }

    private void applyShipVisuals(ShipAPI ship, float effectLevel) {

        float jitterLevel = effectLevel;
        float jitterRange = 8f + 18f * effectLevel;

        ship.setJitterUnder(KEY_JITTER, JITTER_UNDER, jitterLevel, 6, 0f, jitterRange);
        ship.setJitter(KEY_JITTER, JITTER_OVER, jitterLevel, 3, 0f, jitterRange * 0.6f);
        ship.setWeaponGlow(effectLevel, GLOW_COLOR, java.util.EnumSet.allOf(WeaponAPI.WeaponType.class));
    }

    private void applyShotVisuals(CombatEngineAPI engine, ShipAPI ship, float effectLevel) {
        for (Iterator<DamagingProjectileAPI> it = engine.getProjectiles().iterator(); it.hasNext(); ) {
            DamagingProjectileAPI proj = it.next();
            if (proj.getSource() != ship) continue;

            WeaponAPI weapon = proj.getWeapon();
            if (!isWeaponValid(weapon)) continue;

            // Create a faint “glow jitter” around the projectile
            Vector2f p = proj.getLocation();
            Vector2f v = proj.getVelocity();

            float size = 8f + 12f * effectLevel;
            Color c = Misc.setAlpha(GLOW_COLOR, 140);

            engine.addSmoothParticle(
                    p,
                    v,
                    size,
                    0.7f,
                    0.08f,
                    c
            );

            Vector2f randVel = new Vector2f(
                    (float) (v.x * 0.1f + (Math.random() - 0.5) * 40f),
                    (float) (v.y * 0.1f + (Math.random() - 0.5) * 40f)
            );

            engine.addHitParticle(
                    p,
                    randVel,
                    4f + 6f * effectLevel,
                    1f,
                    0.06f,
                    Misc.setAlpha(GLOW_COLOR, 200)
            );
        }

        // --- Beams ---
        for (BeamAPI beam : engine.getBeams()) {
            if (beam.getSource() != ship) continue;

            WeaponAPI weapon = beam.getWeapon();
            if (!isWeaponValid(weapon)) continue;

            // Spawn a few particles along the beam
            Vector2f from = beam.getFrom();
            Vector2f to = beam.getTo();
            if (from == null || to == null) continue;

            int points = 2 + (int) (2 * effectLevel);
            for (int i = 0; i < points; i++) {
                float t = (float) Math.random();
                Vector2f p = new Vector2f(
                        from.x + (to.x - from.x) * t,
                        from.y + (to.y - from.y) * t
                );

                Vector2f drift = new Vector2f(
                        (float) ((Math.random() - 0.5) * 60f),
                        (float) ((Math.random() - 0.5) * 60f)
                );

                engine.addSmoothParticle(
                        p,
                        drift,
                        14f + 18f * effectLevel,
                        0.5f,
                        0.06f,
                        Misc.setAlpha(GLOW_COLOR, 120)
                );
            }
        }
    }

    private boolean isWeaponValid(WeaponAPI weapon) {
        if (weapon == null) return false;
        if (!override) return true; // if Override=false, treat everything as valid (change if you prefer)
        if (validWeaponIds.isEmpty()) return false;
        return validWeaponIds.contains(weapon.getId());
    }

    private class DamageBoostListener implements DamageDealtModifier {
        private final ShipAPI ship;

        DamageBoostListener(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public String modifyDamageDealt(Object param,
                                        CombatEntityAPI target,
                                        DamageAPI damage,
                                        Vector2f point,
                                        boolean shieldHit) {

            if (ship == null || ship.getSystem() == null) return null;
            if (!ship.getSystem().isActive()) return null;

            WeaponAPI weapon = null;
            if (param instanceof DamagingProjectileAPI) {
                weapon = ((DamagingProjectileAPI) param).getWeapon();
            } else if (param instanceof BeamAPI) {
                weapon = ((BeamAPI) param).getWeapon();
            }

            if (!isWeaponValid(weapon)) return null;
            damage.getModifier().modifyMult("UFP_PulsePhaserOverload_Damage", DAMAGE_MULT);
            return "UFP_PulsePhaserOverload_Damage";
        }
    }
}
