package BORG.data.plugins;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import BORG.data.util.CuttingBeamUtility;

/**
 * Standard plugin for Borg beams that utilize cutting technology.
 * This class hooks into the Starsector combat engine to apply scaling
 * logic via the CuttingBeamUtility foundation.
 */
public class CuttingBeamEffect implements BeamEffectPlugin {

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        // We only care about applying the effect when the beam is actually
        // hitting a target. The utility handles the null checks internally,
        // but we skip the logic if the beam isn't firing to save cycles.
        if (beam.getBrightness() > 0f) {
            CuttingBeamUtility.applyCuttingEffect(beam);
        }
    }
}