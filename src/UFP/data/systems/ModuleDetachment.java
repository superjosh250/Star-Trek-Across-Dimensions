package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.List;

public class ModuleDetachment extends BaseShipSystemScript {

    private static final Color JITTER_COLOR = new Color(255, 100, 50, 150);
    private static final Color JITTER_UNDER_COLOR = new Color(255, 150, 50, 200);

    // Guard Keys matching ShieldMount and ImpulseEngineMounts
    private static final String SHIELD_VALIDATION_CACHE_KEY = "ufp_shieldmount_is_valid_cache";
    private static final String SHIELD_FLATTENED_KEY = "ufp_shield_disabled_flattened";
    private static final String IMPULSE_HULLMOD_ID = "impulse_engine_mount";
    private static final String DETACHED_FLAG = "UFP_IsDetachedModule";

    private boolean executed = false;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null) return;

        if (state == State.IN || state == State.ACTIVE) {
            ship.setJitter(id, JITTER_COLOR, effectLevel, 3, 5f);
            ship.setJitterUnder(id, JITTER_UNDER_COLOR, effectLevel, 5, 10f);
        }

        if (state == State.ACTIVE && !executed) {
            detachModules(ship);
            executed = true;
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        executed = false;
    }

    private void detachModules(final ShipAPI parentShip) {
        List<ShipAPI> childModules = parentShip.getChildModulesCopy();
        if (childModules == null || childModules.isEmpty()) return;

        final CombatEngineAPI engine = Global.getCombatEngine();

        for (final ShipAPI module : childModules) {
            if (module != null && module.isAlive()) {
                // Guard 1: Skip if already processed or detached
                if (Boolean.TRUE.equals(module.getCustomData().get(DETACHED_FLAG))) {
                    continue;
                }

                // Guard 2: Mark detachment flags and save parent reference
                module.getCustomData().put(DETACHED_FLAG, true);
                module.getCustomData().put("UFP_OriginalParentStation", parentShip);

                // 1. Sever parent-station linkages
                module.setStationSlot(null);
                module.setParentStation(null);
                module.setStation(false);

                // Guard 3: ShieldMount protection - bypass shield validation and clear flattened state
                module.getCustomData().put(SHIELD_VALIDATION_CACHE_KEY, true);
                module.getCustomData().remove(SHIELD_FLATTENED_KEY);

                // Guard 4: ImpulseEngineMounts protection - unmodify thruster penalties & inject baseline mobility
                MutableShipStatsAPI stats = module.getMutableStats();
                if (stats != null) {
                    stats.getMaxSpeed().unmodify(IMPULSE_HULLMOD_ID);
                    stats.getAcceleration().unmodify(IMPULSE_HULLMOD_ID);
                    stats.getMaxTurnRate().unmodify(IMPULSE_HULLMOD_ID);

                    // Ensure minimum viable mobility for detached sub-modules
                    if (stats.getMaxSpeed().getBaseValue() < 30f) {
                        stats.getMaxSpeed().modifyFlat("UFP_Detached_Mobility", 40f);
                    }
                    if (stats.getAcceleration().getBaseValue() < 20f) {
                        stats.getAcceleration().modifyFlat("UFP_Detached_Mobility", 30f);
                    }
                    if (stats.getMaxTurnRate().getBaseValue() < 10f) {
                        stats.getMaxTurnRate().modifyFlat("UFP_Detached_Mobility", 20f);
                    }

                    // Inject vanilla flux stats for AI ships (since ESM is player-only)
                    if (stats.getFluxCapacity().getBaseValue() <= 0f) {
                        stats.getFluxCapacity().modifyFlat("UFP_AI_Detached_Flux", 4000f);
                    }
                    if (stats.getFluxDissipation().getBaseValue() <= 0f) {
                        stats.getFluxDissipation().modifyFlat("UFP_AI_Detached_Flux", 400f);
                    }
                }

                // 2. Assign standard autonomous Ship AI
                ShipAIConfig config = new ShipAIConfig();
                config.alwaysStrafeOffensively = true;
                config.backingOffWhileNotVentingAllowed = true;

                if (parentShip.getCaptain() != null && parentShip.getCaptain().getPersonalityAPI() != null) {
                    config.personalityOverride = parentShip.getCaptain().getPersonalityAPI().getId();
                } else {
                    config.personalityOverride = "steady";
                }

                ShipAIPlugin newAI = Global.getSettings().createDefaultShipAI(module, config);
                module.setShipAI(newAI);

                // 3. Assign captain personality for fallback AI checks
                if (module.getCaptain() == null || module.getCaptain().isDefault()) {
                    com.fs.starfarer.api.characters.PersonAPI captain = Global.getFactory().createPerson();
                    captain.setPersonality(config.personalityOverride);
                    module.setCaptain(captain);
                }

                // 4. Dynamic detachment markers
                module.getMutableStats().getDynamic().getStat("is_detached_module").modifyFlat("UFP_Detachment", 1f);

                if (engine != null) {
                    module.getCustomData().put("UFP_DetachedTimestamp", engine.getTotalElapsedTime(false));
                }

                // 5. Physics ejection and temporary collision shutdown
                module.setCollisionClass(CollisionClass.NONE);

                Vector2f pushDir = Vector2f.sub(module.getLocation(), parentShip.getLocation(), new Vector2f());
                if (pushDir.lengthSquared() > 0f) {
                    pushDir.normalise();
                } else {
                    pushDir.set(0f, 1f);
                }

                Vector2f.add(module.getVelocity(), (Vector2f) new Vector2f(pushDir).scale(150f), module.getVelocity());

                // 6. Restore collision once clear
                if (engine != null) {
                    engine.addPlugin(new BaseEveryFrameCombatPlugin() {
                        @Override
                        public void advance(float amount, List<InputEventAPI> events) {
                            if (!module.isAlive() || !engine.isEntityInPlay(module)) {
                                engine.removePlugin(this);
                                return;
                            }

                            float requiredDistance = parentShip.getCollisionRadius() + module.getCollisionRadius();
                            float currentDistanceSq = Misc.getDistanceSq(module.getLocation(), parentShip.getLocation());

                            if (!parentShip.isAlive() || currentDistanceSq > (requiredDistance * requiredDistance)) {
                                module.setCollisionClass(CollisionClass.SHIP);
                                engine.removePlugin(this);
                            }
                        }
                    });
                }
            }
        }
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0 && (state == State.IN || state == State.ACTIVE)) {
            return new StatusData("Detaching Module Anchors...", false);
        }
        return null;
    }
}