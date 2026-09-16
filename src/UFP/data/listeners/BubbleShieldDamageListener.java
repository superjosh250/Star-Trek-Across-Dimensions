package UFP.data.listeners;

import UFP.data.hullmods.BubbleShield;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import org.lwjgl.util.vector.Vector2f;

import java.util.*;

public class BubbleShieldDamageListener implements DamageTakenModifier {

    private final ShipAPI targetShip;

    // Full immunity (0% damage)
    private static final Set<String> IGNORED_WEAPON_IDS = new HashSet<>(Arrays.asList(
            "lightmg", "lightdualmg", "heavymg", "hephag", "lightac", "lightag", "irpulse", "irpulse_fighter",
            "lrpdlaser", "mininglaser", "pdlaser", "pulselaser", "hil", "shredder", "hammer", "hammer_single",
            "hammerrack", "hurricane", "locust", "autopulse"
    ));

    // Partial damage: weapon ID ➜ percent of base damage (0.0–1.0)
    private static final Map<String, Float> PARTIAL_DAMAGE_WEAPONS = new HashMap<>();

    static {
        // 1% damage
        addPartialDamage(0.01f, "lightneedler", "heavymortar", "autopulse", "annihilator_fighter", "sabot_fighter",
                "sabot_single", "sabot", "hydra", "hydra_payload", "lightac");

        // 2% damage
        addPartialDamage(0.02f, "heavyneedler", "lightdualac", "annihilator", "railgun");

        // 5%
        addPartialDamage(0.05f, "railgun", "mark9", "heavyac");

        // 10%
        addPartialDamage(0.1f, "hellbore", "guardian", "harpoon", "harpoon_single", "atropos", "atropos_single",
                 "heavyburst", "multineedler", "arbalest");

        // 20%
        addPartialDamage(0.2f, "lightmortar", "squall", "irautolance");

        // 25%
        addPartialDamage(0.25f, "gauss", "miningblaster");

        // 30%
        addPartialDamage(0.3f, "flak", "dualflak");

        // 40%
        addPartialDamage(0.4f, "heavymauler", "devastator", "hveldriver");

        // 50%
        addPartialDamage(0.5f, "phasebeam", "salamanderpod", "pilum_large");

        // 70%
        addPartialDamage(0.7f, "reaper", "cyclone");

        // 75%
        addPartialDamage(0.75f, "mjolnir");

        // Add more tiers if needed...
    }

    private static void addPartialDamage(float multiplier, String... ids) {
        for (String id : ids) {
            PARTIAL_DAMAGE_WEAPONS.put(id, multiplier);
        }
    }

    public BubbleShieldDamageListener(ShipAPI targetShip) {
        this.targetShip = targetShip;
    }

    @Override
    public String modifyDamageTaken(Object param,
                                    CombatEntityAPI target,
                                    DamageAPI damage,
                                    Vector2f point,
                                    boolean shieldHit) {

        if (!(target instanceof ShipAPI) || !shieldHit || target != targetShip) return null;

        ShipAPI ship = (ShipAPI) target;
        if (BubbleShield.getShieldData(ship) == null) return null;

        if (param instanceof DamagingProjectileAPI) {
            DamagingProjectileAPI proj = (DamagingProjectileAPI) param;
            WeaponAPI weapon = proj.getWeapon();

            if (weapon != null) {
                String weaponId = weapon.getId();

                // Blocked weapons
                if (IGNORED_WEAPON_IDS.contains(weaponId)) {
                    damage.getModifier().modifyMult("bubble_shield_block", 0f);
                    return "bubble_shield_block";
                }

                // Partial damage weapons
                if (PARTIAL_DAMAGE_WEAPONS.containsKey(weaponId)) {
                    float multiplier = PARTIAL_DAMAGE_WEAPONS.get(weaponId);
                    damage.getModifier().modifyMult("bubble_shield_partial", multiplier);
                    return "bubble_shield_partial";
                }
            }
        }

        return null;
    }
}
