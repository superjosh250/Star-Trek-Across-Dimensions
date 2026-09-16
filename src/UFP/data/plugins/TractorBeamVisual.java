
package UFP.data.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.util.MagicAnim;

import java.awt.Color;
import java.util.List;

/**
 * Tractor beam visual effect with MagicAnim for smooth BeamAPI-like animation.
 * Rendering moved to renderInWorldCoords() for proper display.
 * Logging fires once per activation.
 */
public class TractorBeamVisual implements EveryFrameCombatPlugin {

    private final ShipAPI source;
    private final ShipAPI target;

    // Texture IDs from settings.json under "fx"
    private static final String CORE_TEXTURE_ID = "tractorBeam";
    private static final String FRINGE_TEXTURE_ID = "beamPhaser";

    // BeamAPI-like parameters
    private static final float PIXELS_PER_TEXEL = 256f;
    private static final float SCROLL_SPEED = 1024f;

    // Logging
    private static final org.apache.log4j.Logger log = Global.getLogger(TractorBeamVisual.class);
    private boolean wasActive = false;

    // Cached values for rendering
    private Vector2f from;
    private Vector2f to;
    private float widthIn;
    private float widthOut;
    private float angle;
    private float range;
    private float scrollOffset;
    private Color coreColor = new Color(100, 200, 255, 255);
    private Color fringeColor = new Color(50, 150, 255, 200);

    public TractorBeamVisual(ShipAPI source, ShipAPI target) {
        this.source = source;
        this.target = target;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (Global.getCombatEngine().isPaused()) return;
        if (source == null || target == null) return;

        boolean hasEmitter = source.getVariant().getHullSpec().getAllWeaponSlotsCopy().stream()
                .anyMatch(slot -> slot.isSystemSlot() && slot.getId() != null && slot.getId().contains("Tractor_Emitter"));
        if (!hasEmitter) return;

        // Find emitter position
        from = null;
        for (WeaponSlotAPI slot : source.getVariant().getHullSpec().getAllWeaponSlotsCopy()) {
            if (slot.isSystemSlot() && slot.getId() != null && slot.getId().contains("Tractor_Emitter")) {
                from = slot.computePosition(source);
                break;
            }
        }
        if (from == null) return;

        to = target.getLocation();
        angle = VectorUtils.getAngle(from, to);
        range = Vector2f.sub(to, from, null).length();

        float pulseFactor = MagicAnim.smoothReturnToRange(Global.getCombatEngine().getTotalElapsedTime(false), 0f, 2f, 1f, 1.2f);
        widthIn = 15f * pulseFactor;
        widthOut = 60f * pulseFactor;

        scrollOffset = MagicAnim.cycle(Global.getCombatEngine().getTotalElapsedTime(false) * SCROLL_SPEED, 0f, PIXELS_PER_TEXEL);

        boolean isActive = source.getSystem() != null && source.getSystem().isActive();

        if (isActive) {
            // Impact glow
            float impactSize = MagicAnim.smoothReturnToRange(Global.getCombatEngine().getTotalElapsedTime(false), 0f, 1f, 30f, 50f);
            Global.getCombatEngine().addSmoothParticle(to, new Vector2f(0, 0), impactSize, 1f, 0.3f, new Color(150, 220, 255, 255));

            // Log ONCE per activation
            if (!wasActive) {
                String timestamp = String.format("%.2f", Global.getCombatEngine().getTotalElapsedTime(false));
                String systemName = source.getSystem() != null ? source.getSystem().getDisplayName() : "Unknown System";

                log.info(String.format(
                        "[%s] Tractor Beam Activated | Source='%s' (HullID=%s) Target='%s' (HullID=%s) | System='%s' | From=(%.1f, %.1f) To=(%.1f, %.1f) | WidthIn=%.1f WidthOut=%.1f Range=%.1f",
                        timestamp,
                        source.getName(), source.getHullSpec().getHullId(),
                        target.getName(), target.getHullSpec().getHullId(),
                        systemName,
                        from.x, from.y, to.x, to.y,
                        widthIn, widthOut, range
                ));
                wasActive = true;
            }
        } else {
            wasActive = false;
        }
    }

    @Override
    public void renderInWorldCoords(ViewportAPI viewport) {
        if (source == null || target == null) return;
        if (source.getSystem() == null || !source.getSystem().isActive()) return;
        if (from == null || to == null) return;

        renderScrollingConeBeam(from, to, widthIn, widthOut, coreColor, fringeColor, angle, range, scrollOffset);
    }

    private void renderScrollingConeBeam(Vector2f from, Vector2f to, float widthIn, float widthOut,
                                         Color coreColor, Color fringeColor, float angle, float range, float scrollOffset) {
        float angleRad = (float) Math.toRadians(angle);
        Vector2f dir = new Vector2f((float) Math.cos(angleRad), (float) Math.sin(angleRad));
        Vector2f perp = new Vector2f(-dir.y, dir.x);

        Vector2f srcLeft = new Vector2f(from.x + perp.x * (widthIn / 2), from.y + perp.y * (widthIn / 2));
        Vector2f srcRight = new Vector2f(from.x - perp.x * (widthIn / 2), from.y - perp.y * (widthIn / 2));
        Vector2f tgtLeft = new Vector2f(to.x + perp.x * (widthOut / 2), to.y + perp.y * (widthOut / 2));
        Vector2f tgtRight = new Vector2f(to.x - perp.x * (widthOut / 2), to.y - perp.y * (widthOut / 2));

        // Core texture
        Global.getSettings().getSprite("fx", CORE_TEXTURE_ID).bindTexture();
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(coreColor.getRed() / 255f, coreColor.getGreen() / 255f, coreColor.getBlue() / 255f, coreColor.getAlpha() / 255f);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(scrollOffset / PIXELS_PER_TEXEL, 0f);
        GL11.glVertex2f(srcLeft.x, srcLeft.y);
        GL11.glTexCoord2f(scrollOffset / PIXELS_PER_TEXEL, 1f);
        GL11.glVertex2f(srcRight.x, srcRight.y);
        GL11.glTexCoord2f((scrollOffset + range) / PIXELS_PER_TEXEL, 1f);
        GL11.glVertex2f(tgtRight.x, tgtRight.y);
        GL11.glTexCoord2f((scrollOffset + range) / PIXELS_PER_TEXEL, 0f);
        GL11.glVertex2f(tgtLeft.x, tgtLeft.y);
        GL11.glEnd();

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();

        // Fringe texture
        Global.getSettings().getSprite("fx", FRINGE_TEXTURE_ID).bindTexture();
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(fringeColor.getRed() / 255f, fringeColor.getGreen() / 255f, fringeColor.getBlue() / 255f, fringeColor.getAlpha() / 255f);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(scrollOffset / PIXELS_PER_TEXEL, 0f);
        GL11.glVertex2f(srcLeft.x, srcLeft.y);
        GL11.glTexCoord2f(scrollOffset / PIXELS_PER_TEXEL, 1f);
        GL11.glVertex2f(srcRight.x, srcRight.y);
        GL11.glTexCoord2f((scrollOffset + range) / PIXELS_PER_TEXEL, 1f);
        GL11.glVertex2f(tgtRight.x, tgtRight.y);
        GL11.glTexCoord2f((scrollOffset + range) / PIXELS_PER_TEXEL, 0f);
        GL11.glVertex2f(tgtLeft.x, tgtLeft.y);
        GL11.glEnd();

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    @Override
    public void init(CombatEngineAPI engine) {
    }

    @Override
    public void renderInUICoords(ViewportAPI viewport) {
    }

    @Override
    public void processInputPreCoreControls(float amount, List<InputEventAPI> events) {
    }
}