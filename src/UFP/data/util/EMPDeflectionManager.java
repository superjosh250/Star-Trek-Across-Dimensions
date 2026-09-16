package UFP.data.util;

import UFP.data.logger.ScriptPerformanceReader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

public class EMPDeflectionManager implements DamageTakenModifier {

    public static final float EMP_REFLECT_MULT = 1.0f;
    public static final float EMP_REFLECT_MAX_RANGE = 1200f;
    public static final float EMP_REFLECT_COOLDOWN = 0.15f;

    public static final String EMP_REFLECTED_PROJECTILE_KEY = "ufp_emp_reflected_once";
    public static final String EMP_REFLECT_COOLDOWN_KEY = "ufp_emp_reflect_cd";

    protected final ShipAPI ship;

    public EMPDeflectionManager(ShipAPI ship) {
        this.ship = ship;
    }

    @Override
    public String modifyDamageTaken(Object param, CombatEntityAPI target, DamageAPI damage, Vector2f point, boolean shieldHit) {
        // FAST-FAIL GATE 1: Immediately skip if the shield wasn't the thing hit
        if (!shieldHit || param == null) {
            return null;
        }

        // FAST-FAIL GATE 2: Check if the incoming attack even has EMP before doing anything else
        boolean hasEmp = false;
        if (param instanceof DamagingProjectileAPI) {
            hasEmp = ((DamagingProjectileAPI) param).getEmpAmount() > 0f;
        } else if (param instanceof BeamAPI) {
            BeamAPI beam = (BeamAPI) param;
            if (beam.getWeapon() != null && beam.getWeapon().getDerivedStats() != null) {
                hasEmp = beam.getWeapon().getDerivedStats().getEmpPerSecond() > 0f;
            }
        }

        // If no EMP is detected, exit immediately. Zero performance overhead for standard hits.
        if (!hasEmp) {
            return null;
        }

        // We have a confirmed shield hit with actual EMP damage. Proceed with reflection logic.
        ScriptPerformanceReader.startTrack("EMPDeflectionManager.modifyDamageTaken");
        try {
            if (!(target instanceof ShipAPI) || ship == null || !ship.isAlive()) return null;
            if (ship.getShield() == null || !ship.getShield().isOn()) return null;

            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused()) return null;

            Float cd = (Float) ship.getCustomData().get(EMP_REFLECT_COOLDOWN_KEY);
            if (cd != null && cd > engine.getTotalElapsedTime(false)) return null;

            ShipAPI attacker = null;
            float empIncoming = 0f;

            if (param instanceof DamagingProjectileAPI) {
                DamagingProjectileAPI proj = (DamagingProjectileAPI) param;
                // Don't reflect a projectile that this system already processed
                if (Boolean.TRUE.equals(proj.getCustomData().get(EMP_REFLECTED_PROJECTILE_KEY))) return null;

                empIncoming = proj.getEmpAmount();
                attacker = proj.getSource();
                proj.setCustomData(EMP_REFLECTED_PROJECTILE_KEY, true);

            } else if (param instanceof BeamAPI) {
                BeamAPI beam = (BeamAPI) param;
                attacker = beam.getSource();

                // Calculate the exact amount of EMP delivered during this specific frame tick
                if (beam.getWeapon() != null && beam.getWeapon().getDerivedStats() != null) {
                    float empPerSecond = beam.getWeapon().getDerivedStats().getEmpPerSecond();
                    empIncoming = empPerSecond * engine.getElapsedInLastFrame();
                }
            }

            if (attacker == null || attacker == ship || empIncoming <= 0f) return null;

            // Zaps the EMP right back at the attacker's face
            engine.spawnEmpArc(
                    ship, point != null ? point : ship.getLocation(), ship, attacker,
                    DamageType.ENERGY, 0f, empIncoming * EMP_REFLECT_MULT,
                    EMP_REFLECT_MAX_RANGE, null, 8f,
                    new Color(75, 171, 213, 200), new Color(255, 255, 255, 220)
            );

            ship.setCustomData(EMP_REFLECT_COOLDOWN_KEY, engine.getTotalElapsedTime(false) + EMP_REFLECT_COOLDOWN);

            return null;
        } finally {
            ScriptPerformanceReader.endTrack("EMPDeflectionManager.modifyDamageTaken");
        }
    }
}