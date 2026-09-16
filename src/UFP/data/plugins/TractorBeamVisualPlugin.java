package UFP.data.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.EnumSet;

public class TractorBeamVisualPlugin extends BaseCombatLayeredRenderingPlugin {

    private static final org.apache.log4j.Logger log = Global.getLogger(TractorBeamVisualPlugin.class);

    private final ShipAPI source;
    private ShipAPI target;
    private boolean expired = false;

    public TractorBeamVisualPlugin(ShipAPI source, ShipAPI target) {
        this.source = source;
        this.target = target;
        log.info("TractorBeamVisualPlugin initialized for source: " + source.getName() + ", target: " + target.getName());
    }

    @Override
    public void advance(float amount) {
        if (source == null || !source.isAlive()) {
            expired = true;
            return;
        }

        ShipSystemAPI system = source.getSystem();
        if (system == null || system.getState() == ShipSystemAPI.SystemState.IDLE
                || system.getState() == ShipSystemAPI.SystemState.COOLDOWN) {
            expired = true;
            return;
        }

        target = source.getShipTarget();
        if (target == null || !target.isAlive()) {
            expired = true;
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (expired || source == null || target == null) return;

        // === Safety Check: Ensure at least one emitter slot exists ===
        WeaponSlotAPI slotFore = source.getHullSpec().getWeaponSlot("TractorEmitter");
        WeaponSlotAPI slotAft = source.getHullSpec().getWeaponSlot("TractorEmitterAFT");

        if (slotFore == null && slotAft == null) {
            log.warn("No valid emitter slots found on ship: " + source.getName() + ". Tractor beam visual plugin will expire.");
            expired = true;
            return;
        }

        Vector2f to = new Vector2f(target.getLocation());
        Vector2f fromShip = new Vector2f(source.getLocation());
        Vector2f toTarget = Vector2f.sub(to, fromShip, null);
        toTarget.normalise();

        // Ship's facing direction as a unit vector
        float shipFacing = source.getFacing();
        Vector2f shipDir = new Vector2f((float)Math.cos(Math.toRadians(shipFacing)), (float)Math.sin(Math.toRadians(shipFacing)));

        // Dot product to determine front/back
        float dot = Vector2f.dot(shipDir, toTarget);

        // Choose emitter slot based on dot product
        String emitterSlotId = dot >= 0 ? "TractorEmitter" : "TractorEmitterAFT";
        WeaponSlotAPI emitterSlot = source.getHullSpec().getWeaponSlot(emitterSlotId);

        Vector2f from = (emitterSlot != null && emitterSlot.isSystemSlot())
                ? emitterSlot.computePosition(source)
                : fromShip;

        log.info("Beam endpoint set to target center: " + to);

        Vector2f beamVector = Vector2f.sub(to, from, null);
        float length = beamVector.length();
        float angle = (float) Math.toDegrees(Math.atan2(beamVector.y, beamVector.x));
        Vector2f midPoint = new Vector2f((from.x + to.x) / 2f, (from.y + to.y) / 2f);

        if (length < 5f) {
            log.warn("Beam length too short, skipping render.");
            return;
        }

        float time = Global.getCombatEngine().getTotalElapsedTime(false);

        // === Cone Effect Parameters ===
        float baseBeamWidth = 0.1f;
        float tipBeamWidth = 85f;
        float baseGlowWidth = 0.1f;
        float tipGlowWidth = 85f;

        // === Segmentation Parameters ===
        int segments = 8;
        float segmentLength = length / segments;
        float overlapFactor = 1.3f;

        // === Texture Scrolling ===
        float scrollSpeed = 64f;
        float texOffset = (1f - (time * scrollSpeed) % 1f);

        // === Core Beam Sprite ===
        SpriteAPI beamSprite = Global.getSettings().getSprite("fx", "tractorBeam");
        beamSprite.bindTexture();
        beamSprite.setAdditiveBlend();

        for (int i = 0; i < segments; i++) {
            float progress = (float) i / segments;
            float beamWidth = baseBeamWidth + (tipBeamWidth - baseBeamWidth) * progress;

            Vector2f segmentPos = new Vector2f(
                    from.x + beamVector.x * ((float)i + 0.5f) / segments,
                    from.y + beamVector.y * ((float)i + 0.5f) / segments
            );

            float flicker = 0.95f + 0.05f * (float)Math.random();
            beamSprite.setAlphaMult(flicker);
            beamSprite.setColor(new Color(122, 232, 220, 255));
            beamSprite.setSize(segmentLength * overlapFactor, beamWidth);
            beamSprite.setAngle(angle);

            float segmentTexOffset = (texOffset + ((float)i / segments)) % 1f;
            beamSprite.setTexX(segmentTexOffset);
            beamSprite.renderAtCenter(segmentPos.x, segmentPos.y);
        }

        // === Glow Layer ===
        SpriteAPI glowSprite = Global.getSettings().getSprite("fx", "tractorBeamCore");
        glowSprite.bindTexture();
        glowSprite.setAdditiveBlend();

        float pulse = 0.8f + 0.2f * (float)Math.sin(time * 3f);
        glowSprite.setAlphaMult(pulse);

        float glowWidth = baseGlowWidth + (tipGlowWidth - baseGlowWidth) * Math.min(length / 1000f, 1f);
        glowSprite.setColor(new Color(122, 232, 220, 180));
        glowSprite.setSize(length, glowWidth);
        glowSprite.setAngle(angle);
        glowSprite.renderAtCenter(midPoint.x, midPoint.y);

        // === Sparkling Particles Along Glow ===
        Color sparkleColor = new Color(255, 255, 255, 204);
        int sparkleCount = 3;

        for (int i = 0; i < sparkleCount; i++) {
            float sparkleProgress = (float)Math.random();
            Vector2f basePos = new Vector2f(
                    from.x + beamVector.x * sparkleProgress,
                    from.y + beamVector.y * sparkleProgress
            );

            Vector2f perp = new Vector2f(-beamVector.y, beamVector.x);
            perp.normalise();

            float offsetAmount = (float)(Math.random() - 0.5f) * glowWidth;
            Vector2f offset = new Vector2f(perp.x * offsetAmount, perp.y * offsetAmount);

            Vector2f sparklePos = Vector2f.add(basePos, offset, null);

            Vector2f sparkleVel = new Vector2f(
                    (float)Math.random() * 20f - 10f,
                    (float)Math.random() * 20f - 10f
            );

            Global.getCombatEngine().addSmoothParticle(
                    sparklePos,
                    sparkleVel,
                    6f + (float)Math.random() * 4f,
                    1f,
                    0.4f + (float)Math.random() * 0.3f,
                    sparkleColor
            );
        }

        viewport.setEverythingNearViewport(true);

        log.info("Rendering cone-shaped tractor beam from " + source.getName() + " to " + target.getName());
        log.info("Beam midpoint world coords: " + midPoint);
        log.info("Viewport culling disabled for tractor beam rendering.");
    }












    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
    }

    @Override
    public boolean isExpired() {
        return expired;
    }

    public boolean runWhilePaused() {
        return false;
    }
}
