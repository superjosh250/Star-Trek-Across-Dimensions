package BORG.data.plugins;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import org.lwjgl.util.vector.Vector2f;

import BORG.data.util.PlasmaBurnEffect;

public final class PlasmaBeamEffect implements BeamEffectPlugin, OnHitEffectPlugin {

    private final PlasmaBurnEffect delegate = new PlasmaBurnEffect();


    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        delegate.advance(amount, engine, beam);
    }


    @Override
    public void onHit(DamagingProjectileAPI projectile,
                      CombatEntityAPI target,
                      Vector2f point,
                      boolean shieldHit,
                      ApplyDamageResultAPI damageResult,
                      CombatEngineAPI engine) {
        // PlasmaBurnEffect already has hit handling logic, but note:
        // your current PlasmaBurnEffect includes an older 5-arg onHit stub too.
        // We explicitly call the 5-arg version it defines with shieldHit+engine
        // to ensure the actual behavior triggers.
        delegate.onHit(projectile, target, point, shieldHit, engine);
    }
}
