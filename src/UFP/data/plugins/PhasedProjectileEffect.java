package UFP.data.plugins;

import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.CollisionUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

public class PhasedProjectileEffect implements OnHitEffectPlugin, OnFireEffectPlugin, EveryFrameWeaponEffectPlugin, BeamEffectPlugin {

    // =========================================================
    // ON FIRE EFFECT: Self-Armor / Self-Hull Backfire
    // =========================================================
    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        if (weapon == null || weapon.getShip() == null) return;
        ShipAPI ship = weapon.getShip();

        ArmorGridAPI armorGrid = ship.getArmorGrid();
        float[][] grid = armorGrid.getGrid();
        float maxCellArmor = armorGrid.getMaxArmorInCell();

        float totalCurrentArmor = 0f;
        float totalMaxArmor = 0f;

        for (int x = 0; x < grid.length; x++) {
            for (int y = 0; y < grid[x].length; y++) {
                totalCurrentArmor += armorGrid.getArmorValue(x, y);
                totalMaxArmor += maxCellArmor;
            }
        }

        boolean armorSeverelyDamaged = (totalMaxArmor > 0f) && ((totalCurrentArmor / totalMaxArmor) <= 0.25f);

        if (armorSeverelyDamaged) {
            float maxHp = ship.getMaxHitpoints();
            float hullDamage = maxHp * 0.125f;

            engine.applyDamage(
                    ship,
                    ship.getLocation(),
                    hullDamage,
                    DamageType.HIGH_EXPLOSIVE,
                    0f,
                    true,  // bypassArmor
                    false, // forceHardFlux
                    ship,
                    false
            );
        } else {
            float armorDamagePerCell = maxCellArmor * 0.05f;
            for (int x = 0; x < grid.length; x++) {
                for (int y = 0; y < grid[x].length; y++) {
                    float currentVal = armorGrid.getArmorValue(x, y);
                    float newVal = Math.max(0f, currentVal - armorDamagePerCell);
                    armorGrid.setArmorValue(x, y, newVal);
                }
            }
            ship.syncWithArmorGridState();
        }
    }

    // =========================================================
    // EVERY FRAME WEAPON EFFECT: Phased Trajectory Raycasting
    // =========================================================
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        if (engine == null || weapon == null || engine.isPaused()) return;

        for (DamagingProjectileAPI proj : engine.getProjectiles()) {
            if (proj.getWeapon() != weapon || proj.isFading() || proj.didDamage()) continue;

            Vector2f projLoc = proj.getLocation();
            Vector2f projVel = proj.getVelocity();

            // Next frame step position
            Vector2f nextLoc = new Vector2f(
                    projLoc.x + (projVel.x * amount),
                    projLoc.y + (projVel.y * amount)
            );

            for (ShipAPI ship : engine.getShips()) {
                if (ship == weapon.getShip() || ship.isHulk() || !ship.isAlive()) continue;

                ShieldAPI shield = ship.getShield();

                // 1. Strip collision if projectile step is inside active shield arc
                if (shield != null && shield.isOn() && shield.isWithinArc(projLoc)) {
                    proj.setCollisionClass(CollisionClass.NONE);
                }

                // 2. Raycast against target hull geometry
                float distToShip = Misc.getDistance(projLoc, ship.getLocation());
                if (distToShip <= ship.getCollisionRadius()) {
                    Vector2f hitPoint = CollisionUtils.getCollisionPoint(projLoc, nextLoc, ship);

                    if (hitPoint != null) {
                        // Apply damage directly onto hit coordinate
                        engine.applyDamage(
                                ship,
                                hitPoint,
                                proj.getDamageAmount(),
                                proj.getDamageType(),
                                proj.getEmpAmount(),
                                true,  // bypassShields
                                false, // dealsSoftFlux
                                proj.getSource(),
                                true   // playSound
                        );

                        engine.addHitParticle(hitPoint, projVel, 20f, 1f, 0.25f, getDamageColor(proj.getDamageType()));
                        engine.removeEntity(proj);
                        break;
                    }
                }
            }
        }
    }

    // =========================================================
    // ON HIT EFFECT: Fallback for direct hits
    // =========================================================
    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        if (target == null || !(target instanceof ShipAPI)) return;
        ShipAPI targetShip = (ShipAPI) target;

        if (shieldHit) {
            if (damageResult != null) {
                float shieldDamageTaken = damageResult.getDamageToShields();
                if (shieldDamageTaken > 0f) {
                    targetShip.getFluxTracker().decreaseFlux(shieldDamageTaken);
                }
            }

            engine.applyDamage(
                    targetShip,
                    point,
                    projectile.getDamageAmount(),
                    projectile.getDamageType(),
                    projectile.getEmpAmount(),
                    true,  // bypassShields
                    false,
                    projectile.getSource(),
                    false
            );
        }
    }

    // =========================================================
    // BEAM EFFECT: Direct shield phasing for beam weapons
    // =========================================================
    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        if (beam == null || !beam.didDamageThisFrame()) return;

        CombatEntityAPI target = beam.getDamageTarget();
        if (target instanceof ShipAPI) {
            ShipAPI targetShip = (ShipAPI) target;
            ShieldAPI shield = targetShip.getShield();

            if (shield != null && shield.isOn() && shield.isWithinArc(beam.getTo())) {
                float damagePerSecond = beam.getWeapon().getDerivedStats().getDps();
                float frameDamage = damagePerSecond * amount;

                targetShip.getFluxTracker().decreaseFlux(frameDamage);

                engine.applyDamage(
                        targetShip,
                        beam.getTo(),
                        frameDamage,
                        beam.getWeapon().getDamageType(),
                        0f,
                        true,  // bypassShields
                        false,
                        beam.getSource(),
                        false
                );
            }
        }
    }

    private Color getDamageColor(DamageType type) {
        if (type == null) return Color.WHITE;
        switch (type) {
            case KINETIC:
                return new Color(255, 220, 100);
            case HIGH_EXPLOSIVE:
                return new Color(255, 100, 50);
            case ENERGY:
                return new Color(100, 220, 255);
            case FRAGMENTATION:
                return new Color(200, 200, 200);
            default:
                return Color.WHITE;
        }
    }
}