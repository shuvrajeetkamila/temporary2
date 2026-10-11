package ai.arena.nauru.game;

import android.content.Context;

import ai.arena.nauru.entities.Player;
import ai.arena.nauru.entities.Vehicle;
import ai.arena.nauru.input.InputState;
import ai.arena.nauru.save.SaveSystem;
import ai.arena.nauru.world.NauruWorld;

/** Owns simulation state; Android views only submit input and read immutable-style public telemetry. */
public final class GameState {
    public final NauruWorld world;
    public final Player player;
    public final Vehicle vehicle;
    public final InputState input;
    private final SaveSystem saveSystem;

    public volatile boolean driving;
    public volatile float cameraYaw;
    public volatile float cameraPitch = .28f;
    public volatile float cameraDistance = 8f;
    public volatile float worldTimeHours = 8.25f;
    public volatile float fps;
    public volatile int quality = 1;
    public volatile String notification = "LEFT STICK TO MOVE • SWIPE TO LOOK";
    public volatile float notificationSeconds = 7f;
    public volatile long distanceTravelledMetres;

    private float cameraInputCooldown;
    private float saveTimer;
    private float lastVehicleX, lastVehicleZ;

    public GameState(Context context, NauruWorld world, InputState input) {
        this.world = world;
        this.input = input;
        this.saveSystem = new SaveSystem(context);
        float[] spawn = world.spawnPoint();
        float[] vehicleSpawn = world.vehicleSpawnPoint();
        float playerX = spawn[0], playerZ = spawn[2];
        float carX = vehicleSpawn[0], carZ = vehicleSpawn[2], carYaw = world.runwayYaw();

        if (saveSystem.hasSave()) {
            SaveSystem.Data saved = saveSystem.load();
            if (world.isWalkableLand(saved.playerX, saved.playerZ)) {
                playerX = saved.playerX; playerZ = saved.playerZ;
            }
            if (world.isWalkableLand(saved.vehicleX, saved.vehicleZ)) {
                carX = saved.vehicleX; carZ = saved.vehicleZ; carYaw = saved.vehicleYaw;
            }
            worldTimeHours = saved.time;
            quality = Math.max(0, Math.min(2, saved.quality));
            notification = "WELCOME BACK TO NAURU";
            notificationSeconds = 4f;
        }
        player = new Player(playerX, world.heightAt(playerX, playerZ), playerZ);
        vehicle = new Vehicle(carX, world.heightAt(carX, carZ) + .48f, carZ, carYaw);
        cameraYaw = carYaw + (float) Math.PI;
        lastVehicleX = carX; lastVehicleZ = carZ;
    }

    public void update(float dt) {
        float[] camera = input.consumeCameraDelta();
        if (Math.abs(camera[0]) + Math.abs(camera[1]) > .01f) {
            cameraYaw -= camera[0] * .0041f;
            cameraPitch = clamp(cameraPitch - camera[1] * .0028f, -.04f, .78f);
            cameraInputCooldown = 2.8f;
        }
        if (Math.abs(camera[2]) > .01f) {
            cameraDistance = clamp(cameraDistance + camera[2] * .012f,
                    driving ? 7f : 4.5f, driving ? 18f : 12f);
        }
        cameraInputCooldown -= dt;

        if (input.consumeQualityChange()) {
            quality = (quality + 1) % 3;
            showNotification("GRAPHICS: " + qualityName().toUpperCase(), 2.5f);
        }

        if (input.consumeAction()) {
            if (driving) exitVehicle(); else tryEnterVehicle();
        }

        if (driving) {
            vehicle.occupied = true;
            vehicle.update(dt, input, world);
            player.x = vehicle.x; player.y = vehicle.y; player.z = vehicle.z;
            float travelled = (float) Math.hypot(vehicle.x - lastVehicleX, vehicle.z - lastVehicleZ);
            if (travelled < 20f) distanceTravelledMetres += (long) travelled;
            lastVehicleX = vehicle.x; lastVehicleZ = vehicle.z;
            if (cameraInputCooldown <= 0 && Math.abs(vehicle.speed) > 2f) {
                float desired = vehicle.speed >= 0 ? vehicle.yaw : vehicle.yaw + (float) Math.PI;
                cameraYaw = approachAngle(cameraYaw, desired, dt * .8f);
            }
        } else {
            vehicle.occupied = false;
            player.update(dt, input, cameraYaw, world);
            lastVehicleX = vehicle.x; lastVehicleZ = vehicle.z;
        }

        worldTimeHours += dt * .02f;
        if (worldTimeHours >= 24f) worldTimeHours -= 24f;
        if (notificationSeconds > 0) notificationSeconds -= dt;
        saveTimer += dt;
        if (saveTimer > 30f) { saveTimer = 0; save(); }
    }

    private void tryEnterVehicle() {
        float distance = (float) Math.hypot(player.x - vehicle.x, player.z - vehicle.z);
        if (distance <= 7f) {
            driving = true;
            input.clearContinuous();
            cameraDistance = Math.max(cameraDistance, 10f);
            cameraYaw = vehicle.yaw;
            showNotification("REEF RUNNER • READY", 3f);
        } else {
            showNotification(distance < 35f ? "MOVE CLOSER TO THE REEF RUNNER" : "FIND THE TEAL VEHICLE ON THE MAP", 3f);
        }
    }

    private void exitVehicle() {
        float rightX = (float) Math.cos(vehicle.yaw), rightZ = -(float) Math.sin(vehicle.yaw);
        float px = vehicle.x + rightX * 2.7f, pz = vehicle.z + rightZ * 2.7f;
        if (!world.isWalkableLand(px, pz)) {
            px = vehicle.x - rightX * 2.7f; pz = vehicle.z - rightZ * 2.7f;
        }
        player.x = px; player.z = pz; player.y = world.heightAt(px, pz);
        player.verticalVelocity = 0; player.grounded = true;
        driving = false;
        input.clearContinuous();
        cameraDistance = Math.min(cameraDistance, 8f);
        showNotification("EXPLORE ON FOOT", 2.5f);
    }

    public boolean isNearVehicle() {
        return !driving && Math.hypot(player.x - vehicle.x, player.z - vehicle.z) < 7f;
    }

    public float focusX() { return driving ? vehicle.x : player.x; }
    public float focusY() { return driving ? vehicle.y + 1.1f : player.y + 1.35f; }
    public float focusZ() { return driving ? vehicle.z : player.z; }

    public String qualityName() {
        return quality == 0 ? "Low" : quality == 1 ? "Medium" : "High";
    }

    public String locationName() { return world.locationName(focusX(), focusZ()); }

    public String clockText() {
        int hour = (int) worldTimeHours;
        int minute = (int) ((worldTimeHours - hour) * 60f);
        return String.format(java.util.Locale.US, "%02d:%02d", hour, minute);
    }

    public void showNotification(String text, float seconds) {
        notification = text; notificationSeconds = seconds;
    }

    public void save() { saveSystem.save(this); }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float approachAngle(float current, float target, float amount) {
        float delta = target - current;
        while (delta > Math.PI) delta -= (float) Math.PI * 2f;
        while (delta < -Math.PI) delta += (float) Math.PI * 2f;
        return current + delta * Math.min(1f, amount);
    }
}
