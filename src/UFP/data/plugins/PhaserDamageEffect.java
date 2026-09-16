package UFP.data.plugins;

import com.fs.starfarer.api.combat.*;
import UFP.data.util.SubsystemTargeting_WEAPON;
import com.fs.starfarer.api.loading.WeaponSlotAPI;

public class PhaserDamageEffect implements BeamEffectPlugin {

    private static final float SLOT_COOLDOWN = 15f;
    private static final float GLOBAL_COOLDOWN = 30f;
    private static final float INITIAL_CHANCE = 0.10f;
    private static final float SECOND_CHANCE = 0.15f;
    private static final float THIRD_CHANCE = 0.20f;
    private static final float SUBSEQUENT_CHANCE = 0.05f;
    private static final int MAX_DISABLED_WEAPONS = 3;

    private static boolean configured = false;

    private void configureSubsystemTargeting() {
        if (configured) return;

        SubsystemTargeting_WEAPON.setSlotCooldown(SLOT_COOLDOWN);
        SubsystemTargeting_WEAPON.setGlobalCooldown(GLOBAL_COOLDOWN);
        SubsystemTargeting_WEAPON.setInitialChance(INITIAL_CHANCE);
        SubsystemTargeting_WEAPON.setSecondChance(SECOND_CHANCE);
        SubsystemTargeting_WEAPON.setThirdChance(THIRD_CHANCE);
        SubsystemTargeting_WEAPON.setSubsequentChance(SUBSEQUENT_CHANCE);
        SubsystemTargeting_WEAPON.setMaxDisabledWeapons(MAX_DISABLED_WEAPONS);

        configured = true;
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {
        // Core frame guard: completely skip updates on paused frames or short intervals
        if (engine == null || engine.isPaused() || amount <= 0f) return;

        configureSubsystemTargeting();

        // High speed structural checks before doing complex work
        CombatEntityAPI targetEntity = beam.getDamageTarget();
        if (!(targetEntity instanceof ShipAPI)) return;

        ShipAPI sourceShip = beam.getSource();
        ShipAPI targetShip = (ShipAPI) targetEntity;
        if (sourceShip == null) return;

        WeaponAPI weapon = beam.getWeapon();
        if (weapon == null) return;
        WeaponSlotAPI slot = weapon.getSlot();
        if (slot == null) return;

        SubsystemTargeting_WEAPON.tryDisableWeapon(engine, sourceShip, targetShip, slot, weapon);
    }
}