package ai.arena.nauru.entities;

import ai.arena.nauru.input.InputState;
import ai.arena.nauru.world.NauruWorld;

/** Stable arcade vehicle physics for the original electric 4x4 "Reef Runner". */
public final class Vehicle {
    public volatile float x, y, z;
    public volatile float yaw;
    public volatile float speed;
    public volatile float steering;
    public volatile float wheelSpin;
    public volatile float pitch;
    public volatile float roll;
    public volatile boolean occupied;

    private float lastSafeX, lastSafeZ;

    public Vehicle(float x, float y, float z, float yaw) {
        this.x = x; this.y = y; this.z = z; this.yaw = yaw;
        lastSafeX = x; lastSafeZ = z;
    }

    public void update(float dt, InputState input, NauruWorld world) {
        float targetSteering = Math.max(-1f, Math.min(1f, input.moveX));
        steering += (targetSteering - steering) * Math.min(1f, dt * 7.5f);

        boolean nearRoad = world.isNearMappedRoad(x, z);
        float traction = nearRoad ? 1f : .72f;
        float acceleration = 8.4f * traction;
        if (input.throttle > .02f) speed += acceleration * input.throttle * dt;

        if (input.brake > .02f) {
            if (speed > .6f) speed -= 15f * input.brake * dt;
            else speed -= 5.4f * input.brake * dt;
        }
        float rolling = nearRoad ? .23f : .62f;
        speed -= speed * rolling * dt;
        if (input.handbrake) speed -= speed * Math.min(.86f, dt * 3.6f);
        speed = Math.max(-9f, Math.min(29f, speed));
        if (Math.abs(speed) < .025f && input.throttle == 0 && input.brake == 0) speed = 0;

        float steerAngle = steering * .52f;
        float grip = input.handbrake ? .52f : 1f;
        yaw += (speed / 2.75f) * (float) Math.tan(steerAngle) * grip * dt;
        float forwardX = (float) Math.sin(yaw), forwardZ = (float) Math.cos(yaw);
        float nextX = x + forwardX * speed * dt;
        float nextZ = z + forwardZ * speed * dt;
        if (world.isWalkableLand(nextX, nextZ)) {
            x = nextX; z = nextZ;
            lastSafeX = x; lastSafeZ = z;
        } else {
            speed *= -.18f;
            x = lastSafeX; z = lastSafeZ;
        }

        float front = world.heightAt(x + forwardX * 1.5f, z + forwardZ * 1.5f);
        float rear = world.heightAt(x - forwardX * 1.5f, z - forwardZ * 1.5f);
        float rightX = (float) Math.cos(yaw), rightZ = -(float) Math.sin(yaw);
        float right = world.heightAt(x + rightX, z + rightZ);
        float left = world.heightAt(x - rightX, z - rightZ);
        float targetPitch = (float) Math.atan2(front - rear, 3f);
        float targetRoll = (float) Math.atan2(left - right, 2f);
        pitch += (targetPitch - pitch) * Math.min(1f, dt * 7f);
        roll += (targetRoll - roll) * Math.min(1f, dt * 7f);
        float ground = (front + rear + right + left) * .25f;
        y += (ground + .48f - y) * Math.min(1f, dt * 10f);
        wheelSpin += speed * dt / .42f;
    }

    public float speedKph() { return Math.abs(speed) * 3.6f; }
}
