package UFP.data.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import org.json.JSONObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicRenderPlugin;

import java.awt.*;
import java.util.EnumSet;

public class SupportCoordinationVisualEffectV2 extends BaseCombatLayeredRenderingPlugin {

    private static final float EFFECT_RADIUS = 3750f;

    private ShipAPI ship;
    private SpriteAPI ring1, ring2, ring3, ring4, engineeringSymbol;
    private float angle1 = 0f, angle2 = 0f, angle3 = 0f;

    @Override
    public void init(CombatEntityAPI entity) {
        if (entity instanceof ShipAPI) {
            this.ship = (ShipAPI) entity;

            try {
                JSONObject settings = Global.getSettings().loadJSON("data/config/UFPSettings.json");
                JSONObject spritesExtra = settings.getJSONObject("Sprites_Extra");

                String path1 = spritesExtra.optString("supportSpin1", "graphics/fx/support1.png");
                String path2 = spritesExtra.optString("supportSpin2", "graphics/fx/support2.png");
                String path3 = spritesExtra.optString("supportSpin3", "graphics/fx/support3.png");
                String path4 = spritesExtra.optString("supportSpin4", "graphics/fx/support4.png");
                String path5 = spritesExtra.optString("supportEngineeringSymbol", "graphics/fx/engineeringLogo.png");

                Global.getSettings().loadTexture(path1);
                Global.getSettings().loadTexture(path2);
                Global.getSettings().loadTexture(path3);
                Global.getSettings().loadTexture(path4);
                Global.getSettings().loadTexture(path5);

                ring1 = Global.getSettings().getSprite(path1);
                ring2 = Global.getSettings().getSprite(path2);
                ring3 = Global.getSettings().getSprite(path3);
                ring4 = Global.getSettings().getSprite(path4);
                engineeringSymbol = Global.getSettings().getSprite(path5);
            } catch (Exception e) {
                Global.getLogger(this.getClass()).error("Failed to load support coordination sprites", e);
            }
        }
    }

    @Override
    public void advance(float amount) {
        if (ship == null || !ship.isAlive()) return;

        Boolean active = (Boolean) ship.getCustomData().get("support_coordination_active");
        if (active == null || !active) return;

        angle1 += 60f * amount;
        angle2 -= 90f * amount;
        angle3 += 120f * amount;
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (ship == null || !ship.isAlive()) return;

        Boolean active = (Boolean) ship.getCustomData().get("support_coordination_active");
        if (active == null || !active) return;

        Vector2f loc = ship.getLocation();
        float spriteWidth = ship.getSpriteAPI().getWidth();
        float spriteHeight = ship.getSpriteAPI().getHeight();
        float maxSpriteSize = Math.max(spriteWidth, spriteHeight);
        Vector2f zeroGrowth = new Vector2f(0f, 0f);

        ring1.setSize(EFFECT_RADIUS * 2f, EFFECT_RADIUS * 2f);
        ring2.setSize(maxSpriteSize * 1.75f, maxSpriteSize * 1.75f);
        ring3.setSize(maxSpriteSize * 1.5f, maxSpriteSize * 1.5f);

        if (viewport.isNearViewport(loc, EFFECT_RADIUS * 2f)) {
            MagicRenderPlugin.addObjectspace(ring1, ship, loc, new Vector2f(), new Vector2f(), zeroGrowth,
                    angle1, 60f, true, 0.1f, 0.9f, 0.1f, true, CombatEngineLayers.BELOW_SHIPS_LAYER);

            MagicRenderPlugin.addObjectspace(ring2, ship, loc, new Vector2f(), new Vector2f(), zeroGrowth,
                    angle2, -90f, true, 0.1f, 0.9f, 0.1f, true, CombatEngineLayers.UNDER_SHIPS_LAYER);

            MagicRenderPlugin.addObjectspace(ring3, ship, loc, new Vector2f(), new Vector2f(), zeroGrowth,
                    angle3, 120f, true, 0.1f, 0.9f, 0.1f, true, CombatEngineLayers.ABOVE_SHIPS_LAYER);

            for (ShipAPI otherShip : Global.getCombatEngine().getShips()) {
                if (otherShip == ship || !otherShip.isAlive()) continue;
                if (ship.getOwner() != otherShip.getOwner()) continue;

                float dist = Misc.getDistance(loc, otherShip.getLocation());
                if (dist > EFFECT_RADIUS) continue;

                float angle4 = (Global.getCombatEngine().getTotalElapsedTime(false) * 45f) % 360f;
                float otherSize = Math.max(otherShip.getSpriteAPI().getWidth(), otherShip.getSpriteAPI().getHeight());
                ring4.setSize(otherSize * 2f, otherSize * 2f);
                engineeringSymbol.setSize(otherSize, otherSize);

                MagicRenderPlugin.addObjectspace(ring4, otherShip, otherShip.getLocation(), new Vector2f(), new Vector2f(), zeroGrowth,
                        angle4, 45f, true, 0.1f, 0.9f, 0.1f, true, CombatEngineLayers.ABOVE_SHIPS_LAYER);

                // ✅ Apply proper blending and transparency
                engineeringSymbol.setBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                engineeringSymbol.setNormalBlend();
                engineeringSymbol.setAlphaMult(0.005f);
                engineeringSymbol.setColor(new Color(213, 200, 176, 255)); // light blue tint

                MagicRenderPlugin.addObjectspace(engineeringSymbol, otherShip, otherShip.getLocation(), new Vector2f(), new Vector2f(), zeroGrowth,
                        0f, 0f, true, 1f, 1f, 1f, true, CombatEngineLayers.ABOVE_SHIPS_LAYER);

                // 🔁 Fallback manual render (optional)
                /*
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                engineeringSymbol.setAlphaMult(0.3f);
                engineeringSymbol.setNormalBlend();
                engineeringSymbol.bindTexture();
                engineeringSymbol.renderAtCenter(otherShip.getLocation().x, otherShip.getLocation().y);
                GL11.glDisable(GL11.GL_BLEND);
                */
            }
        }
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(
                CombatEngineLayers.BELOW_SHIPS_LAYER,
                CombatEngineLayers.UNDER_SHIPS_LAYER,
                CombatEngineLayers.ABOVE_SHIPS_LAYER
        );
    }

    @Override
    public float getRenderRadius() {
        return EFFECT_RADIUS;
    }

    @Override
    public boolean isExpired() {
        return ship == null || !ship.isAlive();
    }

    @Override
    public void cleanup() {
        ship = null;
    }

    @Override
    public CombatEntityAPI getEntity() {
        return ship;
    }
}
