package UFP.data.hullmods;

import UFP.data.listeners.BubbleShieldDamageListener;
import UFP.data.plugins.BubbleShieldBarPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.combat.ShieldAPI;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class BubbleShield extends BaseHullMod {

    private static final String CUSTOM_SHIELD_TEXTURE = "graphics/fx/bubbleShield.png";
    private static final Map<String, ShieldData> shieldDataMap = new HashMap<>();

    private static final Color RING_COLOR = new Color(180, 230, 255, 150);
    private static final Color INNER_COLOR = new Color(100, 200, 255, 60);
    private static final Color REGEN_PULSE_COLOR = new Color(100, 200, 255, 120); // Bluish pulse

    public static class ShieldData {
        public float maxShieldHP;
        public float currentShieldHP;
        public boolean depleted = false;
        public float timeShieldOff = 0f; // Tracks how long shield is off
    }

    public static ShieldData getShieldData(ShipAPI ship) {
        return shieldDataMap.get(ship.getId());
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (ship == null || ship.getShield() == null) return;

        // Set custom shield appearance
        ship.getShield().setRadius(ship.getShieldRadiusEvenIfNoShield(), CUSTOM_SHIELD_TEXTURE, CUSTOM_SHIELD_TEXTURE);
        ship.getShield().setRingColor(RING_COLOR);
        ship.getShield().setInnerColor(INNER_COLOR);

        // Set shield raise rate to complete in ~2 seconds
        ship.getMutableStats().getShieldUnfoldRateMult().modifyMult(id, 4f);

        // Remove incompatible hullmods
        ship.getVariant().removeMod(HullMods.OMNI_SHIELD_CONVERSION);
        ship.getVariant().removeMod(HullMods.FRONT_SHIELD_CONVERSION);
        ship.getVariant().removeMod(HullMods.EXTENDED_SHIELDS);

        CombatEngineAPI engine = Global.getCombatEngine();
        if (!engine.getCustomData().containsKey("BUBBLE_SHIELD_BAR_PLUGIN")) {
            engine.addPlugin(new BubbleShieldBarPlugin());
            engine.getCustomData().put("BUBBLE_SHIELD_BAR_PLUGIN", true);
        }

        if (!ship.hasListenerOfClass(BubbleShieldDamageListener.class)) {
            ship.addListener(new BubbleShieldDamageListener(ship));
        }
    }

    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        if (ship == null || !ship.isAlive() || ship.getShield() == null) return;

        ShieldAPI shield = ship.getShield();
        String id = ship.getId();

        // Get or create shield data
        ShieldData data = shieldDataMap.get(id);
        if (data == null) {
            data = new ShieldData();
            data.maxShieldHP = ship.getMaxFlux();
            data.currentShieldHP = data.maxShieldHP;
            shieldDataMap.put(id, data);
        }

        // Handle shield damage from hard flux
        if (shield.isOn() && !data.depleted) {
            float hardFlux = ship.getFluxTracker().getHardFlux();
            if (hardFlux > 0f) {
                data.currentShieldHP -= hardFlux;
                ship.getFluxTracker().decreaseFlux(hardFlux);

                if (data.currentShieldHP <= 0f) {
                    data.currentShieldHP = 0f;
                    data.depleted = true;
                    data.timeShieldOff = 0f; // Prevent regen
                    shield.toggleOff();
                }
            }
        }

        // Prevent reactivation if depleted
        if (data.depleted && shield.isOn()) {
            shield.toggleOff();
        }

        // Regeneration logic - only if not permanently depleted
        if (!shield.isOn() && !data.depleted) {
            if (data.currentShieldHP < data.maxShieldHP) {
                data.timeShieldOff += amount;

                if (data.timeShieldOff >= 15f) {
                    float regenAmount = data.maxShieldHP * 0.1f;
                    data.currentShieldHP += regenAmount;

                    if (data.currentShieldHP >= data.maxShieldHP) {
                        data.currentShieldHP = data.maxShieldHP;
                    }

                    // Radiating Pulse Visual Effect from the Ship's Center
                    float baseSize = ship.getCollisionRadius();
                    Global.getCombatEngine().addSmoothParticle(
                            ship.getLocation(),
                            ship.getVelocity(),
                            baseSize * 5f,
                            1.5f,
                            0.8f,
                            REGEN_PULSE_COLOR
                    );

                    data.timeShieldOff = 0f;
                }
            } else {
                data.timeShieldOff = 0f;
            }
        } else {
            data.timeShieldOff = 0f;
        }
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "Uses flux capacity as shield HP";
        if (index == 1) return "Shield takes damage but generates no flux";
        if (index == 2) return "Shield cannot be reactivated when HP reaches zero";
        if (index == 3) return "Shield does not regenerate once depleted";
        return null;
    }
}
