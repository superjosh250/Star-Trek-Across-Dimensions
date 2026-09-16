package UFP.data.plugins;

import org.lwjgl.util.vector.Vector2f;

/**
 * Stores UI bar positions in memory for reuse across plugins.
 */
public class UIPositionMemory {
    public static Vector2f shieldBarPosition = null;

    public static void setShieldBarPosition(float x, float y) {
        shieldBarPosition = new Vector2f(x, y);
    }

    public static Vector2f getShieldBarPosition() {
        return shieldBarPosition;
    }
}
