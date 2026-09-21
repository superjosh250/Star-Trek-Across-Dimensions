package UFP.data.systems;

import java.awt.Color;
import java.nio.DoubleBuffer;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.lazywizard.lazylib.CollisionUtils;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import UFP.data.campaign.ids.DimensionsCrossedIDS;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class DeployableArmor extends BaseShipSystemScript {

    private static final Map<String, String> ARMOR_TRANSFORMATION_MAP = new HashMap<>();
    private static final Map<String, String> REVERSE_TRANSFORMATION_MAP = new HashMap<>();

    private static final String ANIM_ACTIVE_KEY = "UFP_DeployableArmor_Active";
    public static final String ORIGINAL_FORM_KEY = "UFP_OriginalShipFormId";
    public static final String LOCKOUT_TIMESTAMP_KEY = "UFP_DeployableArmor_LockoutUntil";

    public static final float LOCKOUT_DURATION = 25.0f; // 25 seconds lockout after transformation

    static {
        registerTransformation(DimensionsCrossedIDS.USS_VOYAGER, DimensionsCrossedIDS.USS_VOYAGER_ARMORED);
        registerTransformation(DimensionsCrossedIDS.INTREPID, DimensionsCrossedIDS.USS_VOYAGER_ARMORED);
    }

    public static void registerTransformation(String baseFormId, String armoredFormId) {
        ARMOR_TRANSFORMATION_MAP.put(baseFormId, armoredFormId);
        REVERSE_TRANSFORMATION_MAP.put(armoredFormId, baseFormId);
    }

    public static boolean validityTest(String baseFormId) {
        if (baseFormId == null) return false;
        return ARMOR_TRANSFORMATION_MAP.containsKey(baseFormId);
    }

    public static String getArmoredTarget(String baseFormId) {
        return ARMOR_TRANSFORMATION_MAP.get(baseFormId);
    }

    public static String getOriginalForm(ShipAPI ship) {
        if (ship == null) return null;

        if (ship.getCustomData().containsKey(ORIGINAL_FORM_KEY)) {
            return (String) ship.getCustomData().get(ORIGINAL_FORM_KEY);
        }

        String hullId = ship.getHullSpec() != null ? ship.getHullSpec().getHullId() : null;
        if (REVERSE_TRANSFORMATION_MAP.containsKey(hullId)) {
            return REVERSE_TRANSFORMATION_MAP.get(hullId);
        }

        String variantId = ship.getVariant() != null ? ship.getVariant().getHullVariantId() : null;
        if (REVERSE_TRANSFORMATION_MAP.containsKey(variantId)) {
            return REVERSE_TRANSFORMATION_MAP.get(variantId);
        }

        return null;
    }

    public static boolean isLockedOut(ShipAPI ship, CombatEngineAPI engine) {
        if (ship == null || engine == null) return false;
        if (!ship.getCustomData().containsKey(LOCKOUT_TIMESTAMP_KEY)) return false;

        Object lockoutVal = ship.getCustomData().get(LOCKOUT_TIMESTAMP_KEY);
        if (lockoutVal instanceof Float) {
            return engine.getTotalElapsedTime(false) < (Float) lockoutVal;
        }
        return false;
    }

    public static boolean revertToOriginalForm(ShipAPI ship, float duration) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (ship == null || engine == null) return false;
        if (isLockedOut(ship, engine)) return false;

        String originalTarget = getOriginalForm(ship);
        if (originalTarget == null) return false;

        if (!ship.getCustomData().containsKey(ANIM_ACTIVE_KEY)) {
            ship.getCustomData().put(ANIM_ACTIVE_KEY, true);
            ArmorUnravelRenderer renderer = new ArmorUnravelRenderer(ship, originalTarget, duration, true, engine);
            engine.addLayeredRenderingPlugin(renderer);
            return true;
        }
        return false;
    }

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (stats == null || !(stats.getEntity() instanceof ShipAPI)) return;

        ShipAPI ship = (ShipAPI) stats.getEntity();
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null || isLockedOut(ship, engine)) return;

        String baseId = ship.getHullSpec() != null ? ship.getHullSpec().getHullId() : null;
        if (!validityTest(baseId) && ship.getVariant() != null) {
            baseId = ship.getVariant().getHullVariantId();
        }

        // Deploying Armor (Fore -> Aft)
        if (validityTest(baseId)) {
            String targetVariantId = getArmoredTarget(baseId);
            if (!ship.getCustomData().containsKey(ANIM_ACTIVE_KEY) && (state == State.IN || state == State.ACTIVE)) {
                ship.getCustomData().put(ANIM_ACTIVE_KEY, true);
                ArmorUnravelRenderer renderer = new ArmorUnravelRenderer(ship, targetVariantId, 2.0f, false, engine);
                engine.addLayeredRenderingPlugin(renderer);
            }
        }
        // Reverting Armor (Aft -> Fore)
        else if (getOriginalForm(ship) != null) {
            if (!ship.getCustomData().containsKey(ANIM_ACTIVE_KEY) && (state == State.IN || state == State.ACTIVE)) {
                revertToOriginalForm(ship, 2.0f);
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        if (stats == null || !(stats.getEntity() instanceof ShipAPI)) return;
        ShipAPI ship = (ShipAPI) stats.getEntity();
        ship.getCustomData().remove(ANIM_ACTIVE_KEY);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("Deploying/Reverting Ablative Armor", false);
        }
        return null;
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        if (ship == null) return false;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine != null && isLockedOut(ship, engine)) {
            return false;
        }

        String hullId = ship.getHullSpec() != null ? ship.getHullSpec().getHullId() : null;
        String variantId = ship.getVariant() != null ? ship.getVariant().getHullVariantId() : null;
        return validityTest(hullId) || validityTest(variantId) || getOriginalForm(ship) != null;
    }

    // -----------------------------------------------------------------
    // LAYERED OPENGL STENCIL RENDERER
    // -----------------------------------------------------------------

    public static class ArmorUnravelRenderer extends BaseCombatLayeredRenderingPlugin {
        private final ShipAPI ship;
        private final String targetVariantId;
        private final float duration;
        private final boolean isReverting;
        private final CombatEngineAPI engine;
        private float elapsed = 0f;
        private boolean expired = false;
        private boolean swapped = false;
        private SpriteAPI overlaySprite = null;

        public ArmorUnravelRenderer(ShipAPI ship, String targetVariantId, float duration, boolean isReverting, CombatEngineAPI engine) {
            this.ship = ship;
            this.targetVariantId = targetVariantId;
            this.duration = duration;
            this.isReverting = isReverting;
            this.engine = engine;

            try {
                ShipHullSpecAPI hullSpec = null;
                ShipVariantAPI variant = Global.getSettings().getVariant(targetVariantId);
                if (variant != null) {
                    hullSpec = variant.getHullSpec();
                } else {
                    hullSpec = Global.getSettings().getHullSpec(targetVariantId);
                }

                if (hullSpec != null) {
                    this.overlaySprite = Global.getSettings().getSprite(hullSpec.getSpriteName());
                }
            } catch (Exception ignored) {}
        }

        @Override
        public void advance(float amount) {
            if (expired || ship == null || !engine.isEntityInPlay(ship)) {
                expired = true;
                return;
            }

            elapsed += amount;
            float progress = Math.min(1.0f, elapsed / duration);

            float facingRad = (float) Math.toRadians(ship.getFacing());
            float radius = ship.getCollisionRadius();
            Vector2f center = ship.getLocation();

            Vector2f forwardVector = new Vector2f((float) Math.cos(facingRad), (float) Math.sin(facingRad));
            Vector2f rightVector = new Vector2f(-forwardVector.y, forwardVector.x);

            Vector2f sweepPoint;
            if (!isReverting) {
                Vector2f frontNose = new Vector2f(
                        center.x + forwardVector.x * radius,
                        center.y + forwardVector.y * radius
                );
                sweepPoint = new Vector2f(
                        frontNose.x - forwardVector.x * (progress * radius * 2.0f),
                        frontNose.y - forwardVector.y * (progress * radius * 2.0f)
                );
            } else {
                Vector2f aftTail = new Vector2f(
                        center.x - forwardVector.x * radius,
                        center.y - forwardVector.y * radius
                );
                sweepPoint = new Vector2f(
                        aftTail.x + forwardVector.x * (progress * radius * 2.0f),
                        aftTail.y + forwardVector.y * (progress * radius * 2.0f)
                );
            }

            if (engine.getViewport().isNearViewport(center, radius * 2.0f)) {
                float beamWidth = radius * 1.2f;
                int particleSteps = 30;
                for (int i = -particleSteps / 2; i <= particleSteps / 2; i++) {
                    float offset = (i / (float)(particleSteps / 2)) * (beamWidth / 2f);
                    Vector2f particleLoc = new Vector2f(
                            sweepPoint.x + rightVector.x * offset,
                            sweepPoint.y + rightVector.y * offset
                    );

                    if (CollisionUtils.isPointWithinBounds(particleLoc, ship)) {
                        engine.addHitParticle(
                                particleLoc,
                                ship.getVelocity(),
                                5f + (float) Math.random() * 6f,
                                0.8f,
                                0.12f,
                                new Color(180, 230, 255, 230)
                        );
                    }
                }
            }

            if (progress >= 1.0f && !swapped) {
                swapped = true;
                executeShipSwap(ship, targetVariantId, isReverting, engine);
                expired = true;
            }
        }

        @Override
        public void render(CombatEngineLayers layer, ViewportAPI viewport) {
            if (expired || ship == null || overlaySprite == null || layer != CombatEngineLayers.ABOVE_SHIPS_LAYER) return;

            SpriteAPI baseSprite = ship.getSpriteAPI();
            if (baseSprite == null) return;

            float progress = Math.min(1.0f, elapsed / duration);
            float facingRad = (float) Math.toRadians(ship.getFacing());
            float radius = ship.getCollisionRadius();
            Vector2f center = ship.getLocation();

            Vector2f forwardVector = new Vector2f((float) Math.cos(facingRad), (float) Math.sin(facingRad));

            Vector2f sweepPoint;
            DoubleBuffer clipBuffer = BufferUtils.createDoubleBuffer(4);

            if (!isReverting) {
                Vector2f frontNose = new Vector2f(
                        center.x + forwardVector.x * radius,
                        center.y + forwardVector.y * radius
                );
                sweepPoint = new Vector2f(
                        frontNose.x - forwardVector.x * (progress * radius * 2.0f),
                        frontNose.y - forwardVector.y * (progress * radius * 2.0f)
                );
                double dVal = -(forwardVector.x * sweepPoint.x + forwardVector.y * sweepPoint.y);
                clipBuffer.put(new double[]{ forwardVector.x, forwardVector.y, 0.0, dVal }).flip();
            } else {
                Vector2f aftTail = new Vector2f(
                        center.x - forwardVector.x * radius,
                        center.y - forwardVector.y * radius
                );
                sweepPoint = new Vector2f(
                        aftTail.x + forwardVector.x * (progress * radius * 2.0f),
                        aftTail.y + forwardVector.y * (progress * radius * 2.0f)
                );
                double dVal = forwardVector.x * sweepPoint.x + forwardVector.y * sweepPoint.y;
                clipBuffer.put(new double[]{ -forwardVector.x, -forwardVector.y, 0.0, dVal }).flip();
            }

            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glPushMatrix();

            GL11.glEnable(GL11.GL_STENCIL_TEST);
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT);
            GL11.glStencilFunc(GL11.GL_ALWAYS, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE);

            GL11.glColorMask(false, false, false, false);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.05f);

            baseSprite.setAngle(ship.getFacing() - 90f);
            baseSprite.setAlphaMult(1.0f);
            baseSprite.renderAtCenter(center.x, center.y);

            GL11.glColorMask(true, true, true, true);
            GL11.glStencilFunc(GL11.GL_EQUAL, 1, 0xFF);
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP);

            GL11.glEnable(GL11.GL_CLIP_PLANE0);
            GL11.glClipPlane(GL11.GL_CLIP_PLANE0, clipBuffer);

            overlaySprite.setAngle(ship.getFacing() - 90f);
            overlaySprite.setAlphaMult(1.0f);
            overlaySprite.renderAtCenter(center.x, center.y);

            GL11.glDisable(GL11.GL_CLIP_PLANE0);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }

        @Override
        public EnumSet<CombatEngineLayers> getActiveLayers() {
            return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_LAYER);
        }

        @Override
        public float getRenderRadius() {
            return 100000f;
        }

        @Override
        public boolean isExpired() {
            return expired;
        }
    }

    public static ShipAPI executeShipSwap(ShipAPI originalShip, String targetVariantId, boolean isReverting, CombatEngineAPI engine) {
        if (originalShip == null || targetVariantId == null || engine == null) return null;

        boolean isPlayer = (engine.getPlayerShip() == originalShip);
        PersonAPI captain = originalShip.getCaptain();
        FleetMemberAPI member = originalShip.getFleetMember();
        int owner = originalShip.getOwner();

        String previousFormId = originalShip.getHullSpec() != null ? originalShip.getHullSpec().getHullId() : null;
        if (originalShip.getVariant() != null && previousFormId == null) {
            previousFormId = originalShip.getVariant().getHullVariantId();
        }

        Vector2f location = new Vector2f(originalShip.getLocation());
        Vector2f velocity = new Vector2f(originalShip.getVelocity());
        float facing = originalShip.getFacing();
        float angularVelocity = originalShip.getAngularVelocity();

        float hullFraction = originalShip.getHitpoints() / originalShip.getMaxHitpoints();
        float fluxFraction = originalShip.getMaxFlux() > 0 ? originalShip.getCurrFlux() / originalShip.getMaxFlux() : 0f;
        float hardFluxFraction = originalShip.getHardFluxLevel();

        CombatFleetManagerAPI manager = engine.getFleetManager(owner);
        if (manager == null) return null;

        // 1. Remove old ship from FleetManager tracking (frees DP and clears deployment screen)
        manager.removeDeployed(originalShip, false);

        // 2. Spawn new ship on battle grid (allocates DP for new variant)
        ShipAPI newShip = manager.spawnShipOrWing(
                targetVariantId,
                location,
                facing,
                0f
        );

        if (newShip != null) {
            newShip.getVelocity().set(velocity);
            newShip.setAngularVelocity(angularVelocity);
            newShip.setHitpoints(Math.max(1f, newShip.getMaxHitpoints() * hullFraction));
            newShip.getFluxTracker().setCurrFlux(newShip.getMaxFlux() * fluxFraction);
            newShip.getFluxTracker().setHardFlux(newShip.getMaxFlux() * hardFluxFraction);

            // Copy proportional armor grid damage
            ArmorGridAPI oldGrid = originalShip.getArmorGrid();
            ArmorGridAPI newGrid = newShip.getArmorGrid();
            if (oldGrid != null && newGrid != null && oldGrid.getGrid() != null && newGrid.getGrid() != null) {
                float[][] oldData = oldGrid.getGrid();
                float[][] newData = newGrid.getGrid();
                int xBounds = Math.min(oldData.length, newData.length);
                int yBounds = Math.min(oldData[0].length, newData[0].length);

                float maxArmorOld = oldGrid.getMaxArmorInCell();

                for (int x = 0; x < xBounds; x++) {
                    for (int y = 0; y < yBounds; y++) {
                        float ratio = maxArmorOld > 0 ? oldData[x][y] / maxArmorOld : 1.0f;
                        newData[x][y] = newData[x][y] * ratio;
                    }
                }
            }

            // Re-bind captain and fleet member reference
            if (captain != null) {
                newShip.setCaptain(captain);
            }
            if (member != null) {
                newShip.setFleetMember(member);
            }

            // Copy transformation tracking keys
            if (!isReverting) {
                String originalForm = (String) originalShip.getCustomData().get(ORIGINAL_FORM_KEY);
                if (originalForm == null) originalForm = previousFormId;
                newShip.getCustomData().put(ORIGINAL_FORM_KEY, originalForm);
            } else {
                newShip.getCustomData().remove(ORIGINAL_FORM_KEY);
            }

            // Apply lockout to custom data AND ship system instance
            float currentCombatTime = engine.getTotalElapsedTime(false);
            newShip.getCustomData().put(LOCKOUT_TIMESTAMP_KEY, currentCombatTime + LOCKOUT_DURATION);
            if (newShip.getSystem() != null) {
                newShip.getSystem().setCooldownRemaining(LOCKOUT_DURATION);
            }

            if (isPlayer) {
                engine.setPlayerShipExternal(newShip);
            }

            // 3. Remove physical entity from visual combat grid
            originalShip.setCollisionClass(CollisionClass.NONE);
            originalShip.getCustomData().remove(ANIM_ACTIVE_KEY);
            engine.removeEntity(originalShip);
        }

        return newShip;
    }
}