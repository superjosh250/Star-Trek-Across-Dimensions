package UFP.data.util;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;
import java.util.List;

public class SubsystemTargeting_ENGINE {

    // === Configurable Parameters ===
    private static float slotCooldown = 15f;
    private static float baseDisableDuration = 8f;
    private static float initialChance = 0.05f;
    private static float secondChance = 0.1f;
    private static float thirdChance = 0.15f;

    // === Internal State Tracking ===
    private static final Map<ShipAPI, Map<WeaponSlotAPI, Float>> engineSlotCooldownMap = new HashMap<>();
    private static final Map<ShipAPI, Integer> enginesDisabledMap = new HashMap<>();

    public static void attemptEngineDisable(CombatEngineAPI engine, BeamAPI beam) {
        ShipAPI sourceShip = beam.getWeapon().getShip();
        ShipAPI targetShip = beam.getDamageTarget() instanceof ShipAPI ? (ShipAPI) beam.getDamageTarget() : null;

        if (sourceShip == null || targetShip == null || beam.getBrightness() < 1f) return;

        ShieldAPI shield = targetShip.getShield();
        if (shield != null && shield.isOn() && shield.isWithinArc(beam.getTo())) return;

        float currentTime = engine.getTotalElapsedTime(true);
        WeaponAPI weapon = beam.getWeapon();
        WeaponSlotAPI slot = weapon.getSlot();

        Map<WeaponSlotAPI, Float> slotMap = engineSlotCooldownMap.computeIfAbsent(sourceShip, k -> new HashMap<>());
        Float slotStart = slotMap.get(slot);
        if (slotStart != null && currentTime - slotStart < slotCooldown) return;

        List<ShipEngineControllerAPI.ShipEngineAPI> engines = targetShip.getEngineController().getShipEngines();
        if (engines.isEmpty()) return;

        int alreadyDisabled = enginesDisabledMap.getOrDefault(targetShip, 0);
        if (alreadyDisabled >= engines.size()) return;

        List<ShipEngineControllerAPI.ShipEngineAPI> activeEngines = new ArrayList<>();
        for (ShipEngineControllerAPI.ShipEngineAPI e : engines) {
            if (!e.isDisabled()) activeEngines.add(e);
        }
        if (activeEngines.isEmpty()) return;

        float baseChance = switch (alreadyDisabled) {
            case 0 -> initialChance;
            case 1 -> secondChance;
            case 2 -> thirdChance;
            default -> 0f;
        };

        if (new Random().nextFloat() > baseChance) {
            slotMap.put(slot, currentTime);
            return;
        }

        ShipEngineControllerAPI.ShipEngineAPI engineToDisable = activeEngines.get(new Random().nextInt(activeEngines.size()));
        engineToDisable.disable();

        float disableDuration = baseDisableDuration;
        if (engines.size() == 2) disableDuration += 2f;
        else if (engines.size() == 1) disableDuration += 5f;

        final float finalDisableDuration = disableDuration;

        engine.addPlugin(new EveryFrameCombatPlugin() {
            float timer = finalDisableDuration;

            @Override
            public void advance(float amount, List<InputEventAPI> events) {
                if (engine.isPaused()) return;
                timer -= amount;
                if (timer <= 0f) {
                    engineToDisable.repair();
                    engine.removePlugin(this);
                }
            }

            @Override public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {}
            @Override public void init(CombatEngineAPI engine) {}
            @Override public void renderInWorldCoords(ViewportAPI viewport) {}
            @Override public void renderInUICoords(ViewportAPI viewport) {}
            public boolean isDone() { return false; }
            public boolean runWhilePaused() { return false; }
            public boolean isInCampaign() { return false; }
        });

        engine.addFloatingText(targetShip.getLocation(), "Subsystem Targeting: Engine Disabled", 20f, Color.RED, targetShip, 1f, 1f);

        enginesDisabledMap.put(targetShip, alreadyDisabled + 1);
        slotMap.put(slot, currentTime);
    }

    // === Configurable Setters ===
    public static void setSlotCooldown(float value) { slotCooldown = value; }
    public static void setBaseDisableDuration(float value) { baseDisableDuration = value; }
    public static void setInitialChance(float value) { initialChance = value; }
    public static void setSecondChance(float value) { secondChance = value; }
    public static void setThirdChance(float value) { thirdChance = value; }

    // === Reset Method ===
    public static void reset() {
        engineSlotCooldownMap.clear();
        enginesDisabledMap.clear();
    }
}