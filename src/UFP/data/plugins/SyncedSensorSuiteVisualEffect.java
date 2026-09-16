package UFP.data.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.json.JSONObject;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicRenderPlugin;

import java.util.EnumSet;

public class SyncedSensorSuiteVisualEffect extends BaseCombatLayeredRenderingPlugin {

    private static final float RANGE = 2250f;

    private ShipAPI ship;
    private SpriteAPI ring1, ring2, ring3, ring4;
    private boolean renderRing4 = false;
    private boolean isPrimaryEmitter = false;
    private float angle1 = 0f, angle2 = 0f, angle3 = 0f, angle4 = 0f;
    private float ring4SpinSpeed = 20f;

    public void setPrimaryEmitter(boolean value) {
        this.isPrimaryEmitter = value;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        if (entity instanceof ShipAPI) {
            this.ship = (ShipAPI) entity;

            try {
                JSONObject settings = Global.getSettings().loadJSON("data/config/UFPSettings.json");
                JSONObject spritesExtra = settings.getJSONObject("Sprites_Extra");

                String path1 = spritesExtra.optString("ringSpin1", "graphics/fx/default_ring1.png");
                String path2 = spritesExtra.optString("ringSpin2", "graphics/fx/default_ring2.png");
                String path3 = spritesExtra.optString("ringSpin3", "graphics/fx/default_ring3.png");

                Global.getSettings().loadTexture(path1);
                Global.getSettings().loadTexture(path2);
                Global.getSettings().loadTexture(path3);

                ring1 = Global.getSettings().getSprite(path1);
                ring2 = Global.getSettings().getSprite(path2);
                ring3 = Global.getSettings().getSprite(path3);

                renderRing4 = spritesExtra.optBoolean("RingSpin4OPTIONAL", true);
                if (renderRing4) {
                    String path4 = spritesExtra.optString("ringSpin4", "graphics/fx/default_ring4.png");
                    ring4SpinSpeed = (float) spritesExtra.optDouble("RingSpin4SpinSpeed", 20f);

                    Global.getSettings().loadTexture(path4);
                    ring4 = Global.getSettings().getSprite(path4);
                }
            } catch (Exception e) {
                Global.getLogger(this.getClass()).error("Failed to load UFPSettings.json or sprite textures", e);
            }
        }
    }

    @Override
    public void advance(float amount) {
        if (ship == null || !ship.isAlive()) return;

        Boolean active = (Boolean) ship.getCustomData().get("synced_sensor_suite_active");
        if (active == null || !active) return;

        angle1 += 75f * amount;
        angle2 -= 90f * amount;
        angle3 += 200f * amount;
        if (renderRing4) {
            angle4 += ring4SpinSpeed * amount;
        }
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (ship == null || !ship.isAlive()) return;

        Boolean active = (Boolean) ship.getCustomData().get("synced_sensor_suite_active");
        if (active == null || !active) return;

        Vector2f loc = ship.getLocation();
        float spriteSize = Math.max(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight());
        float ring1Size = RANGE * 2f;
        float ring2Size = ring1Size * 0.5f;
        float ring3Size = spriteSize * 1.2f;
        Vector2f zeroGrowth = new Vector2f(0f, 0f);

        ring1.setSize(ring1Size, ring1Size);
        ring2.setSize(ring2Size, ring2Size);
        ring3.setSize(ring3Size, ring3Size);
        if (renderRing4 && ring4 != null) {
            ring4.setSize(ring3Size, ring3Size);
        }

        if (isPrimaryEmitter && viewport.isNearViewport(loc, RANGE * 2f)) {
            MagicRenderPlugin.addObjectspace(ring1, ship, loc, new Vector2f(), new Vector2f(), zeroGrowth,
                    angle1, 75f, true, 0.1f, 0.9f, 0.1f, true, CombatEngineLayers.UNDER_SHIPS_LAYER);
        }

        if (isPrimaryEmitter && viewport.isNearViewport(loc, 500f)) {
            MagicRenderPlugin.addObjectspace(ring2, ship, loc, new Vector2f(), new Vector2f(), zeroGrowth,
                    angle2, -90f, true, 0.1f, 0.9f, 0.1f, true, CombatEngineLayers.UNDER_SHIPS_LAYER);
        }

        MagicRenderPlugin.addObjectspace(ring3, ship, loc, new Vector2f(), new Vector2f(), zeroGrowth,
                angle3, 200f, true, 0.1f, 0.9f, 0.1f, true, CombatEngineLayers.UNDER_SHIPS_LAYER);

        if (renderRing4 && ring4 != null) {
            MagicRenderPlugin.addObjectspace(ring4, ship, loc, new Vector2f(), new Vector2f(), zeroGrowth,
                    angle4, ring4SpinSpeed, true, 0.1f, 0.9f, 0.1f, true, CombatEngineLayers.UNDER_SHIPS_LAYER);
        }
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.UNDER_SHIPS_LAYER);
    }

    @Override
    public float getRenderRadius() {
        return ship != null ? RANGE : 0f;
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