package BORG.data.util;

import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.lwjgl.util.vector.Vector2f;

public class CuttingBeamUtility {

    public static final float MULT_ARMOR = 1.75f;
    public static final float MULT_HULL = 1.25f;
    public static final float MULT_SHIELD = 0.75f;
    public static final String MODIFIER_ID = "borg_cutting_beam_foundation";


    public static void applyCuttingEffect(BeamAPI beam) {
        CombatEntityAPI target = beam.getDamageTarget();
        if (target == null || !(target instanceof ShipAPI ship)) {
            beam.getDamage().getModifier().unmodifyMult(MODIFIER_ID);
            return;
        }

        float multiplier = calculateMultiplier(beam, ship);

        beam.getDamage().getModifier().modifyMult(MODIFIER_ID, multiplier);
    }

    private static float calculateMultiplier(BeamAPI beam, ShipAPI ship) {
        Vector2f point = beam.getTo();

        // 1. Shield Check
        if (ship.getShield() != null && ship.getShield().isOn() && ship.getShield().isWithinArc(point)) {
            return MULT_SHIELD;
        }

        // 2. Hull vs Armor Check
        if (ship.getArmorGrid() != null) {
            int[] cell = ship.getArmorGrid().getCellAtLocation(point);
            if (cell != null) {
                float armorValue = ship.getArmorGrid().getArmorValue(cell[0], cell[1]);
                return (armorValue > 0) ? MULT_ARMOR : MULT_HULL;
            }
        }

        return MULT_HULL;
    }
}