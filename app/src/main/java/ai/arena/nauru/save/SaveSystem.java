package ai.arena.nauru.save;

import android.content.Context;
import android.content.SharedPreferences;

import ai.arena.nauru.game.GameState;

/** Tiny versioned checkpoint save; deliberately independent of renderer and scene code. */
public final class SaveSystem {
    private static final String FILE = "nauru_countryside_save_v1";
    private final SharedPreferences preferences;

    public SaveSystem(Context context) {
        preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean hasSave() { return preferences.getBoolean("valid", false); }

    public Data load() {
        Data d = new Data();
        d.playerX = preferences.getFloat("player_x", 0);
        d.playerZ = preferences.getFloat("player_z", 0);
        d.vehicleX = preferences.getFloat("vehicle_x", 0);
        d.vehicleZ = preferences.getFloat("vehicle_z", 0);
        d.vehicleYaw = preferences.getFloat("vehicle_yaw", 0);
        d.time = preferences.getFloat("time", 8.25f);
        d.quality = preferences.getInt("quality", 1);
        return d;
    }

    public void save(GameState state) {
        preferences.edit()
                .putBoolean("valid", true)
                .putFloat("player_x", state.player.x)
                .putFloat("player_z", state.player.z)
                .putFloat("vehicle_x", state.vehicle.x)
                .putFloat("vehicle_z", state.vehicle.z)
                .putFloat("vehicle_yaw", state.vehicle.yaw)
                .putFloat("time", state.worldTimeHours)
                .putInt("quality", state.quality)
                .apply();
    }

    public static final class Data {
        public float playerX, playerZ;
        public float vehicleX, vehicleZ, vehicleYaw;
        public float time;
        public int quality;
    }
}
