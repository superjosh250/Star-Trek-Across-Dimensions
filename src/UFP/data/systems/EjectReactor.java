package UFP.data.systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class EjectReactor extends BaseShipSystemScript {

    private static final String FALLBACK_WING_ID = "warp_core";
    private static final String FALLBACK_VALID_HULLMOD = "warp_core_standard";

    private static final float LAUNCH_SPEED = 750f;
    private static final float TARGET_CLEARANCE = 1000f;
    private static final float COLLISION_SAFE_TIME = 0.75f;
    private static final float SLOW_AFTER_CLEAR = 0.15f;

    private static final String USED_KEY = "UFP_ejectReactor_used";
    private static final String PER_ACTIVATION_KEY = "UFP_ejectReactor_triggered";

    private static final CoreConfig CFG = CoreConfig.load();

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        if (ship == null || Global.getCombatEngine() == null) return false;
        if (Boolean.TRUE.equals(ship.getCustomData().get(USED_KEY))) return false;
        if (!hasAnyValidHullmod(ship)) return false;
        return super.isUsable(system, ship);
    }

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (!(stats.getEntity() instanceof ShipAPI)) return;
        final ShipAPI ship = (ShipAPI) stats.getEntity();
        final CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || ship == null) return;
        if (Boolean.TRUE.equals(ship.getCustomData().get(USED_KEY))) return;

        if ((state == State.IN || state == State.ACTIVE) && effectLevel > 0f) {
            if (!Boolean.TRUE.equals(ship.getCustomData().get(PER_ACTIVATION_KEY))) {
                if (!hasAnyValidHullmod(ship)) return;
                ship.setCustomData(PER_ACTIVATION_KEY, true);
                ejectCore(ship, engine);
                ship.setCustomData(USED_KEY, true);
                scheduleEndOfCombatCleanup(ship);
                if (ship.getSystem() != null) ship.getSystem().deactivate();
            }
        }
        if (state == State.OUT) ship.getCustomData().remove(PER_ACTIVATION_KEY);
    }

    @Override public void unapply(MutableShipStatsAPI stats, String id) {}

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) return new StatusData("reactor ejected" + (effectLevel > 0f ? "..." : ""), false);
        if (index == 1) return new StatusData("system locked (one-time use)", false);
        return null;
    }

    private void ejectCore(ShipAPI parent, CombatEngineAPI engine) {
        final Vector2f spawn = new Vector2f(parent.getLocation());
        final float facing = parent.getFacing();
        final Vector2f backDir = Misc.getUnitVectorAtDegreeAngle(facing + 180f);
        final Vector2f launchVel = new Vector2f(backDir.x * LAUNCH_SPEED + parent.getVelocity().x,
                backDir.y * LAUNCH_SPEED + parent.getVelocity().y);

        ShipAPI core = spawnCoreEntity(engine, parent.getOwner(), spawn, facing, CFG.wingId);
        if (core == null) return;

        makeHarmless(core, engine);
        core.getVelocity().set(launchVel);

        // Keep your clearance slow-down behavior
        engine.addPlugin(new CoreClearancePlugin(parent, core, parent.getCollisionRadius() + TARGET_CLEARANCE));

        // 🔽 NEW: add the reactor breach controller
        engine.addPlugin(new UFP.data.util.ReactorBreach(
                parent,
                core,
                parent.getCollisionRadius() + TARGET_CLEARANCE // == "1000f from ejecting ship", plus hull radius
        ));
    }

    private ShipAPI spawnCoreEntity(CombatEngineAPI engine, int owner, Vector2f loc, float facing, String wingId) {
        CombatEntityAPI ent = engine.getFleetManager(owner).spawnShipOrWing(wingId, loc, facing, 0f);
        if (ent instanceof ShipAPI) {
            ShipAPI s = (ShipAPI) ent;
            s.setFacing(facing);
            return s;
        }
        return null;
    }

    private void makeHarmless(ShipAPI core, CombatEngineAPI engine) {
        List<WeaponAPI> weapons = core.getAllWeapons();
        if (weapons != null) for (WeaponAPI w : weapons) { try { w.setAmmo(0); w.disable(true); } catch (Throwable ignored) {} }
        core.giveCommand(ShipCommand.HOLD_FIRE, null, 0);
        core.blockCommandForOneFrame(ShipCommand.FIRE);
        core.setHoldFireOneFrame(true);

        final CollisionClass original = core.getCollisionClass();
        core.setCollisionClass(CollisionClass.NONE);
        engine.addPlugin(new BaseEveryFrameCombatPlugin() {
            float t = 0f; boolean done = false;
            @Override public void advance(float amount, List events) {
                if (done || engine.isPaused()) return;
                t += amount;
                if (t >= COLLISION_SAFE_TIME) {
                    try { core.setCollisionClass(CollisionClass.FIGHTER); }
                    catch (Throwable e) { core.setCollisionClass(original); }
                    done = true; engine.removePlugin(this);
                }
            }
        });
    }

    private static class CoreClearancePlugin extends BaseEveryFrameCombatPlugin {
        private final ShipAPI parent, core; private final float requiredDistance; private boolean done = false;
        CoreClearancePlugin(ShipAPI parent, ShipAPI core, float requiredDistance) { this.parent = parent; this.core = core; this.requiredDistance = requiredDistance; }
        @Override public void advance(float amount, List events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null || engine.isPaused() || done) return;
            if (parent == null || !parent.isAlive() || core == null || !core.isAlive()) { done = true; engine.removePlugin(this); return; }
            if (Misc.getDistance(parent.getLocation(), core.getLocation()) >= requiredDistance) {
                Vector2f v = core.getVelocity(); v.scale(SLOW_AFTER_CLEAR); core.getVelocity().set(v);
                done = true; engine.removePlugin(this);
            }
        }
    }

    private void scheduleEndOfCombatCleanup(ShipAPI ship) {
        final ShipAPI parentRef = ship;
        Global.getCombatEngine().addPlugin(new BaseEveryFrameCombatPlugin() {
            boolean done = false;
            @Override public void advance(float amount, List events) {
                CombatEngineAPI engine = Global.getCombatEngine();
                if (engine == null || done || !engine.isCombatOver()) return;
                done = true; engine.removePlugin(this);
                try {
                    FleetMemberAPI fm = parentRef.getFleetMember(); if (fm == null) return;
                    boolean removedAny = false, builtIn = false;
                    for (String modId : CFG.validHullmods) {
                        if (fm.getVariant().hasHullMod(modId)) { fm.getVariant().removeMod(modId); removedAny = true; }
                        if (fm.getVariant().getPermaMods().contains(modId)) { fm.getVariant().removePermaMod(modId); removedAny = true; }
                        if (fm.getVariant().getSMods().contains(modId)) { fm.getVariant().removePermaMod(modId); removedAny = true; }
                        ShipHullSpecAPI hs = fm.getHullSpec(); if (hs != null && hs.isBuiltIn(modId)) builtIn = true;
                    }
                    if (builtIn && Global.getSector() != null) {
                        MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
                        final String KEY = "$UFP_removeBuiltInAfterCombatMembers";
                        @SuppressWarnings("unchecked") Set<String> ids = (Set<String>) mem.get(KEY);
                        if (ids == null) { ids = new HashSet<>(); mem.set(KEY, ids); }
                        ids.add(fm.getId());
                    }
                    if (removedAny) {
                        // TODO: add a removable D-mod to fm (campaign-side integration)
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    private boolean hasAnyValidHullmod(ShipAPI ship) {
        if (ship == null || CFG.validHullmods.isEmpty()) return false;
        for (String id : CFG.validHullmods) {
            if (ship.getVariant() != null) {
                if (ship.getVariant().hasHullMod(id)) return true;
                if (ship.getVariant().getPermaMods().contains(id)) return true;
                if (ship.getVariant().getSMods().contains(id)) return true;
            }
            ShipHullSpecAPI hs = ship.getHullSpec();
            if (hs != null && hs.isBuiltIn(id)) return true;
        }
        return false;
    }

    private static final class CoreConfig {
        final String wingId; final Set<String> validHullmods;
        private CoreConfig(String wingId, Set<String> validHullmods) { this.wingId = wingId; this.validHullmods = validHullmods; }
        static CoreConfig load() {
            String wing = FALLBACK_WING_ID; Set<String> mods = new HashSet<>(); mods.add(FALLBACK_VALID_HULLMOD);
            try {
                JSONObject root = Global.getSettings().getMergedJSON("data/config/UFPSettings.json");
                if (root != null && root.has("CoreEjection")) {
                    JSONObject core = root.getJSONObject("CoreEjection");
                    if (core.optBoolean("Override", true)) {
                        String w = core.optString("wing_id", null); if (w != null && !w.trim().isEmpty()) wing = w.trim();
                        if (core.has("valid_hullmod_ids")) {
                            Object any = core.get("valid_hullmod_ids");
                            if (any instanceof JSONArray) {
                                mods.clear(); JSONArray arr = (JSONArray) any;
                                for (int i = 0; i < arr.length(); i++) { String id = String.valueOf(arr.get(i)).trim(); if (!id.isEmpty()) mods.add(id); }
                            } else if (any instanceof JSONObject) {
                                mods.clear(); JSONObject obj = (JSONObject) any;
                                for (Iterator<String> it = obj.keys(); it.hasNext();) { String id = it.next(); if (id != null && !id.trim().isEmpty()) mods.add(id.trim()); }
                            } else if (any instanceof String) {
                                mods.clear(); String id = ((String) any).trim(); if (!id.isEmpty()) mods.add(id);
                            }
                            if (mods.isEmpty()) mods.add(FALLBACK_VALID_HULLMOD);
                        }
                    }
                }
            } catch (Throwable ignored) {}
            return new CoreConfig(wing, mods);
        }
    }
}