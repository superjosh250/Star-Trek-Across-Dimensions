package UFP.data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.combat.listeners.DamageListener;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashSet;
import java.util.Set;

public class TorpedoLauncher extends BaseHullMod {

    private static final String TORPEDO_ID = "photonTorpedo_standard";

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (!ship.hasListenerOfClass(TorpedoDamageDealtModifier.class)) {
            ship.addListener(new TorpedoDamageDealtModifier());
        }
    }


    public static class TorpedoDamageDealtModifier implements DamageDealtModifier {

        private static final float ENERGY_VS_SHIELDS_MULT = 0.7f;
        private final Set<DamagingProjectileAPI> handled = new HashSet<>();

        @Override
        public String modifyDamageDealt(Object param,
                                        CombatEntityAPI target,
                                        DamageAPI damage,
                                        Vector2f point,
                                        boolean shieldHit) {

            if (!(param instanceof DamagingProjectileAPI)) return null;

            DamagingProjectileAPI proj = (DamagingProjectileAPI) param;

            if (proj.getWeapon() == null || !TORPEDO_ID.equals(proj.getWeapon().getId())) return null;

            if (shieldHit && !handled.contains(proj)) {
                handled.add(proj);

                float originalDamage = proj.getDamageAmount();

                // Apply energy damage to shields manually
                Global.getCombatEngine().applyDamage(
                        target,
                        point,
                        originalDamage * ENERGY_VS_SHIELDS_MULT,
                        DamageType.ENERGY,
                        0f,
                        false,
                        false,
                        proj.getSource()
                );

                // Cancel original damage
                damage.setDamage(0f);
            }

            return null;
        }
    }



    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "photon torpedoes";
        if (index == 1) return "deal ENERGY damage to shields";
        if (index == 2) return "70% of their normal damage";
        return null;
    }
}
