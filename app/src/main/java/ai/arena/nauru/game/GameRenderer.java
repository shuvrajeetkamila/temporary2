package ai.arena.nauru.game;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;

import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import ai.arena.nauru.entities.Player;
import ai.arena.nauru.entities.Vehicle;
import ai.arena.nauru.graphics.Mesh;
import ai.arena.nauru.graphics.MeshBuilder;
import ai.arena.nauru.graphics.ShaderProgram;
import ai.arena.nauru.graphics.Shaders;
import ai.arena.nauru.world.NauruWorld;

/** OpenGL ES 2 renderer with a streaming procedural world and third-person camera. */
public final class GameRenderer implements GLSurfaceView.Renderer {
    private final GameState state;
    private ShaderProgram worldShader;
    private ShaderProgram waterShader;
    private final Mesh cube;
    private final Mesh cylinder;
    private final Mesh disc;

    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] viewProjection = new float[16];
    private final float[] model = new float[16];
    private final float[] baseModel = new float[16];
    private final float[] childModel = new float[16];
    private final float[] identity = new float[16];
    private int width, height;
    private long lastFrameNanos;
    private float elapsed;
    private float smoothedCameraX, smoothedCameraY, smoothedCameraZ;
    private boolean cameraReady;
    private int frameCounter;
    private float fpsTimer;

    private int uWorldViewProjection, uWorldModel, uWorldCamera, uWorldLight;
    private int uWorldSun, uWorldFog, uWorldTint, uWorldAmbient, uWorldFogNear, uWorldFogFar;
    private int uWaterViewProjection, uWaterModel, uWaterCamera, uWaterTime;
    private int uWaterFog, uWaterFogNear, uWaterFogFar;

    public GameRenderer(Context context, GameState state) {
        this.state = state;
        cube = buildCube();
        cylinder = buildCylinder();
        disc = buildDisc();
        Matrix.setIdentityM(identity, 0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearDepthf(1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        worldShader = new ShaderProgram(Shaders.WORLD_VERTEX, Shaders.WORLD_FRAGMENT);
        waterShader = new ShaderProgram(Shaders.WATER_VERTEX, Shaders.WATER_FRAGMENT);
        cacheUniforms();
        state.world.invalidateGl();
        cube.invalidateGl(); cylinder.invalidateGl(); disc.invalidateGl();
        lastFrameNanos = System.nanoTime();
        cameraReady = false;
    }

    private void cacheUniforms() {
        uWorldViewProjection = worldShader.uniform("uViewProjection");
        uWorldModel = worldShader.uniform("uModel");
        uWorldCamera = worldShader.uniform("uCamera");
        uWorldLight = worldShader.uniform("uLightDirection");
        uWorldSun = worldShader.uniform("uSunColor");
        uWorldFog = worldShader.uniform("uFogColor");
        uWorldTint = worldShader.uniform("uTint");
        uWorldAmbient = worldShader.uniform("uAmbient");
        uWorldFogNear = worldShader.uniform("uFogNear");
        uWorldFogFar = worldShader.uniform("uFogFar");
        uWaterViewProjection = waterShader.uniform("uViewProjection");
        uWaterModel = waterShader.uniform("uModel");
        uWaterCamera = waterShader.uniform("uCamera");
        uWaterTime = waterShader.uniform("uTime");
        uWaterFog = waterShader.uniform("uFogColor");
        uWaterFogNear = waterShader.uniform("uFogNear");
        uWaterFogFar = waterShader.uniform("uFogFar");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        GLES20.glViewport(0, 0, this.width, this.height);
        Matrix.perspectiveM(projection, 0, 62f, this.width / (float) this.height, .15f, 12000f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = Math.min(.05f, Math.max(.001f, (now - lastFrameNanos) / 1_000_000_000f));
        lastFrameNanos = now;
        elapsed += dt;
        state.update(dt);
        updateFps(dt);

        float daylight = daylight(state.worldTimeHours);
        float dawn = 1f - Math.min(1f, Math.abs(state.worldTimeHours - 6.5f) / 2f);
        float dusk = 1f - Math.min(1f, Math.abs(state.worldTimeHours - 18f) / 2f);
        float warm = Math.max(0f, Math.max(dawn, dusk));
        float skyR = .025f + daylight * (.26f + warm * .18f);
        float skyG = .055f + daylight * (.52f - warm * .08f);
        float skyB = .095f + daylight * .69f;
        float fogR = skyR * .92f, fogG = skyG * .95f, fogB = skyB;
        GLES20.glClearColor(skyR, skyG, skyB, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        float targetX = state.focusX(), targetY = state.focusY(), targetZ = state.focusZ();
        float horizontal = state.cameraDistance * (float) Math.cos(state.cameraPitch);
        float desiredX = targetX - (float) Math.sin(state.cameraYaw) * horizontal;
        float desiredZ = targetZ - (float) Math.cos(state.cameraYaw) * horizontal;
        float desiredY = targetY + 1.1f + state.cameraDistance * (float) Math.sin(state.cameraPitch);
        float cameraGround = state.world.heightAt(desiredX, desiredZ) + .75f;
        desiredY = Math.max(desiredY, cameraGround);
        if (!cameraReady) {
            smoothedCameraX = desiredX; smoothedCameraY = desiredY; smoothedCameraZ = desiredZ;
            cameraReady = true;
        } else {
            float smooth = 1f - (float) Math.exp(-dt * (state.driving ? 7f : 10f));
            smoothedCameraX += (desiredX - smoothedCameraX) * smooth;
            smoothedCameraY += (desiredY - smoothedCameraY) * smooth;
            smoothedCameraZ += (desiredZ - smoothedCameraZ) * smooth;
        }
        Matrix.setLookAtM(view, 0, smoothedCameraX, smoothedCameraY, smoothedCameraZ,
                targetX, targetY, targetZ, 0, 1, 0);
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0);

        float fogNear = state.quality == 0 ? 620f : state.quality == 1 ? 1050f : 1550f;
        float fogFar = state.quality == 0 ? 2350f : state.quality == 1 ? 3900f : 5450f;

        drawOcean(fogR, fogG, fogB, fogNear, fogFar);
        drawWorld(fogR, fogG, fogB, fogNear, fogFar, daylight, warm);
    }

    private void drawOcean(float fogR, float fogG, float fogB, float fogNear, float fogFar) {
        waterShader.use();
        GLES20.glUniformMatrix4fv(uWaterViewProjection, 1, false, viewProjection, 0);
        GLES20.glUniformMatrix4fv(uWaterModel, 1, false, identity, 0);
        GLES20.glUniform3f(uWaterCamera, smoothedCameraX, smoothedCameraY, smoothedCameraZ);
        GLES20.glUniform1f(uWaterTime, elapsed);
        GLES20.glUniform3f(uWaterFog, fogR, fogG, fogB);
        GLES20.glUniform1f(uWaterFogNear, fogNear);
        GLES20.glUniform1f(uWaterFogFar, fogFar);
        state.world.drawOcean();
    }

    private void drawWorld(float fogR, float fogG, float fogB, float fogNear, float fogFar,
                           float daylight, float warm) {
        worldShader.use();
        GLES20.glUniformMatrix4fv(uWorldViewProjection, 1, false, viewProjection, 0);
        GLES20.glUniform3f(uWorldCamera, smoothedCameraX, smoothedCameraY, smoothedCameraZ);
        float sunAngle = (state.worldTimeHours - 6f) / 12f * (float) Math.PI;
        float sunY = Math.max(.12f, (float) Math.sin(sunAngle));
        GLES20.glUniform3f(uWorldLight, (float) Math.cos(sunAngle) * .44f, sunY,
                (float) Math.sin(sunAngle * .63f) * .35f);
        GLES20.glUniform3f(uWorldSun, .48f + daylight * .62f + warm * .18f,
                .55f + daylight * .48f, .72f + daylight * .28f - warm * .14f);
        GLES20.glUniform3f(uWorldFog, fogR, fogG, fogB);
        GLES20.glUniform1f(uWorldAmbient, .20f + daylight * .20f);
        GLES20.glUniform1f(uWorldFogNear, fogNear);
        GLES20.glUniform1f(uWorldFogFar, fogFar);
        setModelAndTint(identity, 1, 1, 1, 1);
        state.world.drawLand(smoothedCameraX, smoothedCameraZ, state.quality);

        drawVehicle(state.vehicle);
        if (!state.driving) drawPlayer(state.player);
    }

    private void drawVehicle(Vehicle car) {
        Matrix.setIdentityM(baseModel, 0);
        Matrix.translateM(baseModel, 0, car.x, car.y, car.z);
        Matrix.rotateM(baseModel, 0, degrees(car.yaw), 0, 1, 0);
        Matrix.rotateM(baseModel, 0, -degrees(car.pitch), 1, 0, 0);
        Matrix.rotateM(baseModel, 0, degrees(car.roll), 0, 0, 1);

        drawShadow(car.x, state.world.heightAt(car.x, car.z) + .035f, car.z, 1.45f, 2.65f, car.yaw, .32f);
        drawChild(cube, baseModel, 0, .48f, 0, 1.92f, .58f, 4.15f, 0, 0, 0, .08f, .60f, .59f, 1);
        drawChild(cube, baseModel, 0, .91f, -.28f, 1.63f, .54f, 2.15f, 0, 0, 0, .10f, .46f, .49f, 1);
        drawChild(cube, baseModel, 0, .94f, .58f, 1.56f, .44f, .10f, 0, 0, 0, .08f, .15f, .18f, .92f);
        drawChild(cube, baseModel, 0, .94f, -1.18f, 1.56f, .44f, .10f, 0, 0, 0, .08f, .15f, .18f, .92f);
        drawChild(cube, baseModel, 0, .34f, 2.10f, 1.86f, .22f, .16f, 0, 0, 0, .94f, .55f, .16f, 1);
        drawChild(cube, baseModel, 0, .37f, -2.10f, 1.86f, .24f, .16f, 0, 0, 0, .14f, .20f, .22f, 1);
        // Headlights and tail lights remain readable at night without copyrighted styling.
        drawChild(cube, baseModel, -.61f, .57f, 2.13f, .38f, .20f, .08f, 0, 0, 0, 1f, .91f, .55f, 1);
        drawChild(cube, baseModel, .61f, .57f, 2.13f, .38f, .20f, .08f, 0, 0, 0, 1f, .91f, .55f, 1);
        drawChild(cube, baseModel, -.61f, .55f, -2.13f, .35f, .18f, .08f, 0, 0, 0, .92f, .10f, .08f, 1);
        drawChild(cube, baseModel, .61f, .55f, -2.13f, .35f, .18f, .08f, 0, 0, 0, .92f, .10f, .08f, 1);

        float steerDegrees = -degrees(car.steering * .48f);
        drawWheel(baseModel, -1.01f, .05f, 1.35f, steerDegrees, car.wheelSpin);
        drawWheel(baseModel, 1.01f, .05f, 1.35f, steerDegrees, car.wheelSpin);
        drawWheel(baseModel, -1.01f, .05f, -1.34f, 0, car.wheelSpin);
        drawWheel(baseModel, 1.01f, .05f, -1.34f, 0, car.wheelSpin);
    }

    private void drawWheel(float[] parent, float x, float y, float z, float steer, float spin) {
        System.arraycopy(parent, 0, childModel, 0, 16);
        Matrix.translateM(childModel, 0, x, y, z);
        Matrix.rotateM(childModel, 0, steer, 0, 1, 0);
        Matrix.rotateM(childModel, 0, 90, 0, 0, 1);
        Matrix.rotateM(childModel, 0, degrees(spin), 0, 1, 0);
        Matrix.scaleM(childModel, 0, .82f, .44f, .82f);
        setModelAndTint(childModel, .055f, .065f, .07f, 1);
        cylinder.draw();
    }

    private void drawPlayer(Player player) {
        Matrix.setIdentityM(baseModel, 0);
        Matrix.translateM(baseModel, 0, player.x, player.y, player.z);
        Matrix.rotateM(baseModel, 0, degrees(player.yaw), 0, 1, 0);
        float swing = (float) Math.sin(elapsed * 9f) * 24f * player.movementBlend;
        drawShadow(player.x, state.world.heightAt(player.x, player.z) + .03f,
                player.z, .55f, .78f, player.yaw, .28f);
        drawChild(cube, baseModel, 0, 1.25f, 0, .70f, .92f, .38f, 0, 0, 0, .91f, .49f, .18f, 1);
        drawChild(cylinder, baseModel, 0, 1.90f, 0, .56f, .58f, .56f, 0, 0, 0, .38f, .23f, .15f, 1);
        drawChild(cube, baseModel, -.20f, .57f, 0, .27f, .84f, .30f, swing, 0, 0, .14f, .25f, .31f, 1);
        drawChild(cube, baseModel, .20f, .57f, 0, .27f, .84f, .30f, -swing, 0, 0, .14f, .25f, .31f, 1);
        drawChild(cube, baseModel, -.47f, 1.27f, 0, .20f, .80f, .25f, -swing * .8f, 0, 0, .38f, .24f, .16f, 1);
        drawChild(cube, baseModel, .47f, 1.27f, 0, .20f, .80f, .25f, swing * .8f, 0, 0, .38f, .24f, .16f, 1);
    }

    private void drawShadow(float x, float y, float z, float sx, float sz, float yaw, float alpha) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        Matrix.rotateM(model, 0, degrees(yaw), 0, 1, 0);
        Matrix.scaleM(model, 0, sx, 1, sz);
        setModelAndTint(model, .015f, .022f, .025f, alpha);
        GLES20.glDepthMask(false);
        disc.draw();
        GLES20.glDepthMask(true);
    }

    private void drawChild(Mesh mesh, float[] parent, float tx, float ty, float tz,
                           float sx, float sy, float sz, float rotX, float rotY, float rotZ,
                           float r, float g, float b, float a) {
        System.arraycopy(parent, 0, childModel, 0, 16);
        Matrix.translateM(childModel, 0, tx, ty, tz);
        if (rotY != 0) Matrix.rotateM(childModel, 0, rotY, 0, 1, 0);
        if (rotX != 0) Matrix.rotateM(childModel, 0, rotX, 1, 0, 0);
        if (rotZ != 0) Matrix.rotateM(childModel, 0, rotZ, 0, 0, 1);
        Matrix.scaleM(childModel, 0, sx, sy, sz);
        setModelAndTint(childModel, r, g, b, a);
        mesh.draw();
    }

    private void setModelAndTint(float[] matrix, float r, float g, float b, float a) {
        GLES20.glUniformMatrix4fv(uWorldModel, 1, false, matrix, 0);
        GLES20.glUniform4f(uWorldTint, r, g, b, a);
    }

    private void updateFps(float dt) {
        frameCounter++; fpsTimer += dt;
        if (fpsTimer >= .5f) {
            state.fps = frameCounter / fpsTimer;
            frameCounter = 0; fpsTimer = 0;
        }
    }

    public void save() { state.save(); }

    private static float daylight(float hour) {
        float value = (float) Math.sin((hour - 5.5f) / 13f * Math.PI);
        return Math.max(.06f, Math.min(1f, value));
    }

    private static float degrees(float radians) { return radians * 180f / (float) Math.PI; }

    private static Mesh buildCube() {
        MeshBuilder b = new MeshBuilder();
        b.addBox(0, -.5f, 0, 1, 1, 1, 0, 1, 1, 1);
        return b.build();
    }

    private static Mesh buildCylinder() {
        MeshBuilder b = new MeshBuilder();
        b.addCylinder(0, -.5f, 0, .5f, 1f, 10, 1, 1, 1);
        return b.build();
    }

    private static Mesh buildDisc() {
        MeshBuilder b = new MeshBuilder();
        int center = b.vertex(0, 0, 0, 0, 1, 0, 1, 1, 1);
        int sides = 28;
        int[] ring = new int[sides];
        for (int i = 0; i < sides; i++) {
            double a = i * Math.PI * 2 / sides;
            ring[i] = b.vertex((float) Math.cos(a), 0, (float) Math.sin(a), 0, 1, 0, 1, 1, 1);
        }
        for (int i = 0; i < sides; i++) b.triangle(center, ring[i], ring[(i + 1) % sides]);
        return b.build();
    }
}
