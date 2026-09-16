package UFP.data.plugins;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

public class DisruptorDamageEffect implements OnHitEffectPlugin, BeamEffectPlugin {

    public static final float SHIELD_MULT = 1.25f;          // +25% damage to shields
    public static final float HULL_ARMOR_MULT = 0.80f;      // -20% damage to hull/armor
    public static final float PENETRATION_CHANCE = 0.10f;   // 10% chance to bypass shield

    private final IntervalUtil beamPenetrationInterval = new IntervalUtil(0.2f, 0.2f);

    private static final Color PENETRATION_TEXT_COLOR = new Color(0, 255, 180, 230);
    private static final Color PENETRATION_FLASH_COLOR = new Color(100, 255, 200, 255);

    // =========================================================================
    // PROJECTILE LOGIC (OnHitEffectPlugin)
    // =========================================================================
    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI result, CombatEngineAPI engine) {
        if (target == null || engine == null || projectile == null) return;

        float baseDamage = projectile.getDamageAmount();

        if (shieldHit) {
            boolean penetrated = (float) Math.random() < PENETRATION_CHANCE;

            if (penetrated) {
                float penDamage = baseDamage * HULL_ARMOR_MULT;
                engine.applyDamage(
                        target,
                        point,
                        penDamage,
                        projectile.getDamageType(),
                        projectile.getEmpAmount(),
                        false,
                        false,
                        projectile.getSource(),
                        true // bypassShields = true
                );

                engine.addFloatingText(point, "SHIELD BYPASS!", 18f, PENETRATION_TEXT_COLOR, target, 1f, 0.5f);
                engine.spawnExplosion(point, target.getVelocity(), PENETRATION_FLASH_COLOR, 30f, 0.3f);
            } else {
                float extraShieldDamage = baseDamage * (SHIELD_MULT - 1.0f);
                engine.applyDamage(
                        target,
                        point,
                        extraShieldDamage,
                        projectile.getDamageType(),
                        0f,
                        false,
                        false,
                        projectile.getSource(),
                        false
                );
            }
        }
    }

    // =========================================================================
    // BEAM LOGIC (BeamEffectPlugin)
    // =========================================================================
    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        if (engine == null || beam == null || beam.getDamageTarget() == null) return;

        CombatEntityAPI target = beam.getDamageTarget();
        Vector2f hitPoint = beam.getTo();

        // Validate shield collision via target ship shield arc
        boolean shieldHit = false;
        if (target instanceof ShipAPI) {
            ShipAPI ship = (ShipAPI) target;
            if (ship.getShield() != null && ship.getShield().isOn() && ship.getShield().isWithinArc(hitPoint)) {
                shieldHit = true;
            }
        }

        if (shieldHit) {
            beam.getDamage().getModifier().unmodify("disruptor_hull_nerf");
            beam.getDamage().getModifier().modifyMult("disruptor_shield_buff", SHIELD_MULT);

            beamPenetrationInterval.advance(amount);
            if (beamPenetrationInterval.intervalElapsed()) {
                if ((float) Math.random() < PENETRATION_CHANCE) {
                    float tickDamage = beam.getDamage().getDamage() * beamPenetrationInterval.getIntervalDuration() * HULL_ARMOR_MULT;

                    engine.applyDamage(
                            target,
                            hitPoint,
                            tickDamage,
                            beam.getDamage().getType(),
                            0f,
                            false,
                            false,
                            beam.getSource(),
                            true // bypassShields = true
                    );

                    engine.addFloatingText(hitPoint, "BYPASS!", 14f, PENETRATION_TEXT_COLOR, target, 0.8f, 0.4f);
                }
            }
        } else {
            beam.getDamage().getModifier().unmodify("disruptor_shield_buff");
            beam.getDamage().getModifier().modifyMult("disruptor_hull_nerf", HULL_ARMOR_MULT);
        }
    }
}