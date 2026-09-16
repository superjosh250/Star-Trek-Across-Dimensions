package UFP.data.plugins;

import UFP.data.hullmods.BubbleShield;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import org.lwjgl.opengl.GL11;

public class BubbleShieldBarPlugin extends BaseEveryFrameCombatPlugin {

    private CombatEngineAPI engine;

    /**
     * Called once when this plugin is added to the combat engine.
     */
    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
    }

    /**
     * This method is called every frame to render UI elements in screen space.
     * It draws a health bar for the custom BubbleShield above applicable ships.
     */
    @Override
    public void renderInUICoords(ViewportAPI viewport) {
        for (ShipAPI ship : engine.getShips()) {
            if (!ship.isAlive() || ship.isHulk()) continue;

            // Retrieve shield data for this ship
            BubbleShield.ShieldData data = BubbleShield.getShieldData(ship);
            if (data == null || data.maxShieldHP <= 0f) continue;

            // Convert the ship’s world position to screen coordinates
            float screenX = viewport.convertWorldXtoScreenX(ship.getLocation().x);
            float screenY = viewport.convertWorldYtoScreenY(ship.getLocation().y + ship.getCollisionRadius() + 10f);

            // Define bar dimensions and position
            float barWidth = 60f;
            float barHeight = 6f;
            float x = screenX - barWidth / 2f;
            float y = screenY;

            // Calculate fill percentage of the shield bar
            float fill = data.currentShieldHP / data.maxShieldHP;
            fill = Math.max(0f, Math.min(1f, fill)); // Clamp between 0 and 1

            drawBar(x, y, barWidth, barHeight, fill);
        }
    }

    /**
     * Draws a filled horizontal bar at the given screen-space location.
     * @param x Left position of the bar
     * @param y Top position of the bar
     * @param width Total width of the bar
     * @param height Height of the bar
     * @param fill Fill percentage (0.0 to 1.0)
     */
    private void drawBar(float x, float y, float width, float height, float fill) {
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Background - gray with some transparency
        GL11.glColor4f(0.4f, 0.4f, 0.4f, 0.6f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();

        // Foreground - light blue fill color
        GL11.glColor4f(0.3f, 0.7f, 1f, 0.8f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width * fill, y);
        GL11.glVertex2f(x + width * fill, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }
}
