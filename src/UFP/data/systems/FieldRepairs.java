package UFP.data.systems;

import UFP.data.util.ShieldHitpointManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.input.InputEventAPI;

import java.awt.Color;
import java.util.List;

/**
 * Field Repairs
 * - ACTIVE: Allies within 4000 gain -10% armor damage taken + 75 HP/sec hull repair + green flicker glow.
 * - ACTIVE (Self): repair missing armor cells by 10% (once per activation),
 *                 -15% hull damage taken, +15% shield HP (once per activation),
 *                 +5% speed/accel/turn stats.
 * - Each activation: adds 5 supplies to a pending end-of-combat deduction bucket.
 */
public class FieldRepairs extends BaseShipSystemScript {

    // Aura
    private static final float AURA_RANGE = 4000f;
    private static final float ALLY_ARMOR_DMG_TAKEN_MULT = 0.90f; // 10% less armor damage taken
    private static final float ALLY_HULL_REPAIR_PER_SEC = 75f;

    // Self buffs
    private static final float SELF_ARMOR_REPAIR_PCT_MISSING = 0.10f; // restore 10% of missing armor per cell (once/activation)
    private static final float SELF_HULL_DMG_TAKEN_MULT = 0.85f;      // 15% less hull damage taken
    private static final float SELF_SHIELD_RESTORE_PCT = 0.15f;       // +15% shield HP (once per activation)
    private static final float SELF_MOBILITY_PCT = 5f;                // +5% speed/accel/turn

    // Supplies
    private static final int SUPPLIES_PER_ACTIVATION = 5;

    // Visuals
    private static final Color GLOW_COLOR = new Color(60, 255, 120, 120);
    private static final float GLOW_RANGE = 10f; // jitter range

    // CustomData keys
    private static final String KEY_ACTIVATION_LATCH = "ufp_fieldrepairs_activated";
    private static final String KEY_PENDING_SUPPLIES = "ufp_fieldrepairs_pending_supplies";
    private static final String KEY_DEDUCTOR_ADDED   = "ufp_fieldrepairs_deductor_added";
    private static final String KEY_DEDUCTED         = "ufp_fieldrepairs_deducted";

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI source = (ShipAPI) stats.getEntity();
        if (source == null) return;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;

        // Ensure the end-of-combat deductor plugin exists once per combat
        ensureDeductorPlugin(engine);

        // Unique modifier id per source ship to prevent collisions between multiple FieldRepairs users
        final String modId = id + "_" + source.getId();

        // Only apply effects while system is truly ACTIVE.
        if (state != State.ACTIVE) {
            clearSelfBonuses(source, modId);
            clearAuraBonuses(engine, modId);

            // Reset per-activation latch
            source.getCustomData().remove(KEY_ACTIVATION_LATCH);
            return;
        }

        // Frame time; used for "per second" effects
        // getElapsedInLastFrame() is the standard way to avoid frame-rate dependence for per-second math. [1](https://starsector.fandom.com/wiki/Modding_troubleshooting)[2](https://jaghaimo.github.io/starsector-api/interfacecom_1_1fs_1_1starfarer_1_1api_1_1combat_1_1CombatEngineAPI.html)
        float dt = engine.getElapsedInLastFrame();
        if (dt <= 0f) dt = 0f;

        // First frame of ACTIVE = "activation" event (run once)
        if (!source.getCustomData().containsKey(KEY_ACTIVATION_LATCH)) {
            source.setCustomData(KEY_ACTIVATION_LATCH, Boolean.TRUE);

            // Queue supplies for end-of-combat deduction
            addPendingSupplies(engine, SUPPLIES_PER_ACTIVATION);

            // SELF: repair missing armor cells by 10% (once per activation)
            restoreMissingArmorCells(source, SELF_ARMOR_REPAIR_PCT_MISSING);

            // SELF: restore shields by 15% (once per activation) using your custom ShieldHitpointManager
            restoreSelfShieldBurst(source);
        }

        // Apply self buffs while ACTIVE (continuous)
        applySelfBonuses(source, modId);

        // ✅ SELF: Hull repair 75 hp/sec while ACTIVE
        repairHull(source, dt * ALLY_HULL_REPAIR_PER_SEC);

        // Apply aura to allies in range; remove from those out of range
        applyAura(engine, source, modId, dt);

        // Optional status text
        engine.maintainStatusForPlayerShip(
                id,
                "graphics/icons/hullsys/repair_field.png",
                "Field Repairs",
                "Allies in 4000: -10% armor dmg, +75 hull/s | Self: +75 hull/s, armor repair burst, mobility, shield burst",
                false
        );
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI source = (ShipAPI) stats.getEntity();
        CombatEngineAPI engine = Global.getCombatEngine();
        if (source == null || engine == null) return;

        final String modId = id + "_" + source.getId();

        clearSelfBonuses(source, modId);
        clearAuraBonuses(engine, modId);

        source.getCustomData().remove(KEY_ACTIVATION_LATCH);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (state != State.ACTIVE) return null;
        if (index == 0) return new StatusData("Allies in 4000: -10% armor dmg, +75 hull/sec", false);
        if (index == 1) return new StatusData("Self: +75 hull/sec, +5% mobility, -15% hull dmg, +15% shield (on activation), +10% missing armor repaired", false);
        return null;
    }

    // ----------------------------
    // Aura application
    // ----------------------------

    private void applyAura(CombatEngineAPI engine, ShipAPI source, String modId, float dt) {
        float now = engine.getTotalElapsedTime(false);

        // Flicker: on for 1s, off for 1s
        boolean glowOn = (((int) now) % 2) == 0;

        for (ShipAPI other : engine.getShips()) {
            if (other == null) continue;
            if (other.isHulk() || !other.isAlive()) {
                unapplyAuraFromShip(other, modId);
                continue;
            }

            if (other.getOwner() == source.getOwner() && other != source && inRange(source, other, AURA_RANGE)) {
                // -10% armor damage taken
                other.getMutableStats().getArmorDamageTakenMult().modifyMult(modId, ALLY_ARMOR_DMG_TAKEN_MULT);

                // Hull repair: 75 hp/sec
                repairHull(other, dt * ALLY_HULL_REPAIR_PER_SEC);

                // Visual glow only on affected ships
                if (glowOn) {
                    other.setJitterUnder(modId, GLOW_COLOR, 1f, 5, 0f, GLOW_RANGE);
                }
            } else {
                unapplyAuraFromShip(other, modId);
            }
        }
    }

    private void unapplyAuraFromShip(ShipAPI ship, String modId) {
        if (ship == null) return;
        ship.getMutableStats().getArmorDamageTakenMult().unmodify(modId);
    }

    private void clearAuraBonuses(CombatEngineAPI engine, String modId) {
        for (ShipAPI ship : engine.getShips()) {
            if (ship == null) continue;
            unapplyAuraFromShip(ship, modId);
        }
    }

    private boolean inRange(ShipAPI a, ShipAPI b, float range) {
        float dx = a.getLocation().x - b.getLocation().x;
        float dy = a.getLocation().y - b.getLocation().y;
        return (dx * dx + dy * dy) <= (range * range);
    }

    private void repairHull(ShipAPI target, float amount) {
        if (target == null) return;
        if (amount <= 0f) return;

        float hp = target.getHitpoints();
        float max = target.getMaxHitpoints();
        if (hp >= max) return;

        target.setHitpoints(Math.min(max, hp + amount));
    }

    // ----------------------------
    // Self bonuses (continuous while ACTIVE)
    // ----------------------------

    private void applySelfBonuses(ShipAPI self, String modId) {
        MutableShipStatsAPI s = self.getMutableStats();

        // Hull damage taken reduced by 15%
        s.getHullDamageTakenMult().modifyMult(modId, SELF_HULL_DMG_TAKEN_MULT);

        // Mobility +5%
        s.getMaxSpeed().modifyPercent(modId, SELF_MOBILITY_PCT);
        s.getAcceleration().modifyPercent(modId, SELF_MOBILITY_PCT);
        s.getDeceleration().modifyPercent(modId, SELF_MOBILITY_PCT);
        s.getTurnAcceleration().modifyPercent(modId, SELF_MOBILITY_PCT);
        s.getMaxTurnRate().modifyPercent(modId, SELF_MOBILITY_PCT);
    }

    private void clearSelfBonuses(ShipAPI self, String modId) {
        if (self == null) return;
        MutableShipStatsAPI s = self.getMutableStats();

        s.getHullDamageTakenMult().unmodify(modId);

        s.getMaxSpeed().unmodify(modId);
        s.getAcceleration().unmodify(modId);
        s.getDeceleration().unmodify(modId);
        s.getTurnAcceleration().unmodify(modId);
        s.getMaxTurnRate().unmodify(modId);
    }

    // ----------------------------
    // Self: armor grid repair (once per activation)
    // ----------------------------

    private void restoreMissingArmorCells(ShipAPI ship, float pctMissing) {
        if (ship == null) return;

        ArmorGridAPI grid = ship.getArmorGrid();
        if (grid == null) return;

        float[][] cells = grid.getGrid();
        if (cells == null || cells.length == 0 || cells[0].length == 0) return;

        float maxCell = grid.getMaxArmorInCell();
        if (maxCell <= 0f) return;

        float totalAdded = 0f;

        int w = cells.length;
        int h = cells[0].length;

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                float cur = grid.getArmorValue(x, y);
                float missing = Math.max(0f, maxCell - cur);
                if (missing <= 0f) continue;

                float add = missing * pctMissing;
                float next = Math.min(maxCell, cur + add);

                if (next > cur) {
                    grid.setArmorValue(x, y, next);
                    totalAdded += (next - cur);
                }
            }
        }

        if (totalAdded > 0f && Global.getCombatEngine() != null) {
            Global.getCombatEngine().addFloatingText(
                    ship.getLocation(),
                    String.format("+Armor repaired"),
                    12f,
                    new Color(120, 255, 160, 220),
                    ship,
                    0.5f,
                    0.5f
            );
        }
    }

    // ----------------------------
    // Self: shield burst (once per activation)
    // ----------------------------

    private void restoreSelfShieldBurst(ShipAPI self) {
        if (self == null || self.getShield() == null) return;

        float max = ShieldHitpointManager.getMaxShieldHP(self);
        float cur = ShieldHitpointManager.getShieldHP(self);
        float restore = max * SELF_SHIELD_RESTORE_PCT;

        ShieldHitpointManager.setShieldHP(self, cur + restore);
    }

    // ----------------------------
    // Supplies: end-of-combat deduction
    // ----------------------------

    private void addPendingSupplies(CombatEngineAPI engine, int amount) {
        if (amount <= 0) return;
        Object o = engine.getCustomData().get(KEY_PENDING_SUPPLIES);
        int pending = (o instanceof Integer) ? (Integer) o : 0;
        engine.getCustomData().put(KEY_PENDING_SUPPLIES, pending + amount);
    }

    private void ensureDeductorPlugin(CombatEngineAPI engine) {
        if (Boolean.TRUE.equals(engine.getCustomData().get(KEY_DEDUCTOR_ADDED))) return;
        engine.getCustomData().put(KEY_DEDUCTOR_ADDED, Boolean.TRUE);
        engine.addPlugin(new SuppliesDeductorPlugin());
    }

    private static class SuppliesDeductorPlugin implements EveryFrameCombatPlugin {
        @Override public void init(CombatEngineAPI engine) { }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            CombatEngineAPI engine = Global.getCombatEngine();
            if (engine == null) return;

            if (!engine.isCombatOver()) return;
            if (Boolean.TRUE.equals(engine.getCustomData().get(KEY_DEDUCTED))) return;

            engine.getCustomData().put(KEY_DEDUCTED, Boolean.TRUE);

            Object o = engine.getCustomData().get(KEY_PENDING_SUPPLIES);
            int pending = (o instanceof Integer) ? (Integer) o : 0;
            if (pending <= 0) return;

            if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return;

            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            if (cargo == null) return;

            float have = cargo.getSupplies();
            float take = Math.min(have, pending);
            if (take > 0) cargo.removeSupplies(take);
        }

        @Override public void processInputPreCoreControls(float amount, List<InputEventAPI> events) { }
        @Override public void renderInWorldCoords(ViewportAPI viewport) { }
        @Override public void renderInUICoords(ViewportAPI viewport) { }
    }
}