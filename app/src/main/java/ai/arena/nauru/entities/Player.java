package ai.arena.nauru.entities;

import ai.arena.nauru.input.InputState;
import ai.arena.nauru.world.NauruWorld;

/** Capsule-like third-person avatar simulation rendered from original low-poly primitives. */
public final class Player {
    public volatile float x, y, z;
    public volatile float yaw;
    public volatile float verticalVelocity;
    public volatile boolean grounded = true;
    public volatile float movementBlend;

    public Player(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
    }

    public void update(float dt, InputState input, float cameraYaw, NauruWorld world) {
        float mx = input.moveX, my = input.moveY;
        float magnitude = Math.min(1f, (float) Math.sqrt(mx * mx + my * my));
        float forwardX = (float) Math.sin(cameraYaw), forwardZ = (float) Math.cos(cameraYaw);
        float rightX = (float) Math.cos(cameraYaw), rightZ = -(float) Math.sin(cameraYaw);
        float dirX = forwardX * my + rightX * mx;
        float dirZ = forwardZ * my + rightZ * mx;
        if (magnitude > .08f) {
            float inv = 1f / Math.max(.001f, (float) Math.sqrt(dirX * dirX + dirZ * dirZ));
            dirX *= inv; dirZ *= inv;
            float targetYaw = (float) Math.atan2(dirX, dirZ);
            yaw = approachAngle(yaw, targetYaw, dt * 10f);
            float speed = input.sprint ? 9.2f : 5.2f;
            float nextX = x + dirX * speed * magnitude * dt;
            float nextZ = z + dirZ * speed * magnitude * dt;
            if (world.isWalkableLand(nextX, nextZ)) {
                x = nextX; z = nextZ;
            }
        }
        movementBlend += (magnitude - movementBlend) * Math.min(1f, dt * 9f);

        if (input.consumeJump() && grounded) {
            verticalVelocity = 6.4f;
            grounded = false;
        }
        float ground = world.heightAt(x, z);
        if (!grounded) {
            verticalVelocity -= 16.5f * dt;
            y += verticalVelocity * dt;
            if (y <= ground) {
                y = ground; verticalVelocity = 0; grounded = true;
            }
        } else {
            y += (ground - y) * Math.min(1f, dt * 14f);
        }
    }

    private static float approachAngle(float current, float target, float amount) {
        float delta = wrapAngle(target - current);
        return current + delta * Math.min(1f, amount);
    }

    private static float wrapAngle(float angle) {
        while (angle > Math.PI) angle -= (float) Math.PI * 2f;
        while (angle < -Math.PI) angle += (float) Math.PI * 2f;
        return angle;
    }
}
