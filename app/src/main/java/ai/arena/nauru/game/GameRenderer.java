package ai.arena.nauru.game;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import ai.arena.nauru.entities.Player;
import ai.arena.nauru.entities.Vehicle;
import ai.arena.nauru.graphics.Mesh;
import ai.arena.nauru.graphics.MeshBuilder;
import ai.arena.nauru.graphics.ProceduralTexture;
import ai.arena.nauru.graphics.ShaderProgram;
import ai.arena.nauru.graphics.Shaders;

/**
 * Mobile physically-inspired renderer: procedural material detail, soft local sun shadows,
 * atmospheric sky, ocean Fresnel/specular lighting and smooth original character/vehicle art.
 */
public final class GameRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "NauruRenderer";
    private static final int SHADOW_SIZE = 1024;

    private final GameState state;
    private ShaderProgram worldShader;
    private ShaderProgram waterShader;
    private ShaderProgram skyShader;
    private ShaderProgram shadowShader;
    private final Mesh cube;
    private final Mesh cylinder;
    private final Mesh sphere;
    private final Mesh disc;
    private final Mesh skyDome;
    private final Mesh carBody;
    private final Mesh carCabin;

    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] viewProjection = new float[16];
    private final float[] lightProjection = new float[16];
    private final float[] lightView = new float[16];
    private final float[] lightViewProjection = new float[16];
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
    private float lightX, lightY, lightZ;
    private boolean firstFrameStarted;
    private boolean firstFrameFinished;

    private int detailTexture;
    private int[] surfaceTextures;
    private int shadowFramebuffer;
    private int shadowTexture;
    private int shadowDepthBuffer;
    private boolean shadowReady;
    private boolean shadowPass;

    private int uWorldViewProjection, uWorldLightViewProjection, uWorldModel, uWorldCamera, uWorldLight;
    private int uWorldSun, uWorldFog, uWorldTint, uWorldAmbient, uWorldFogNear, uWorldFogFar;
    private int uWorldMaterial, uWorldDaylight, uWorldShadows, uWorldDetailTexture, uWorldShadowTexture;
    private int uWorldGrassTexture, uWorldAsphaltTexture, uWorldCoralTexture, uWorldStructureTexture;
    private int uWaterViewProjection, uWaterCamera, uWaterTime, uWaterLight, uWaterSun, uWaterDaylight;
    private int uWaterFog, uWaterFogNear, uWaterFogFar;
    private int uSkyViewProjection, uSkyModel, uSkySun, uSkyDaylight, uSkyTime;
    private int uShadowLightViewProjection, uShadowModel;

    public GameRenderer(Context context, GameState state) {
        this.state = state;
        cube = buildCube();
        cylinder = buildCylinder();
        sphere = buildSphere();
        disc = buildDisc();
        skyDome = buildSkyDome();
        carBody = buildRoundedCarBody();
        carCabin = buildCarCabin();
        Matrix.setIdentityM(identity, 0);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.i(TAG, "surface creation started");
        GLES20.glClearDepthf(1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        worldShader = new ShaderProgram(Shaders.WORLD_VERTEX, Shaders.WORLD_FRAGMENT);
        waterShader = new ShaderProgram(Shaders.WATER_VERTEX, Shaders.WATER_FRAGMENT);
        skyShader = new ShaderProgram(Shaders.SKY_VERTEX, Shaders.SKY_FRAGMENT);
        shadowShader = new ShaderProgram(Shaders.SHADOW_VERTEX, Shaders.SHADOW_FRAGMENT);
        cacheUniforms();
        detailTexture = ProceduralTexture.createMaterialDetail();
        surfaceTextures = ProceduralTexture.createSurfaceSet();
        createShadowTarget();

        state.world.invalidateGl();
        cube.invalidateGl(); cylinder.invalidateGl(); sphere.invalidateGl(); disc.invalidateGl();
        skyDome.invalidateGl(); carBody.invalidateGl(); carCabin.invalidateGl();
        lastFrameNanos = System.nanoTime();
        cameraReady = false;
        firstFrameStarted = false;
        firstFrameFinished = false;
        Log.i(TAG, "surface creation complete; shadowTarget=" + shadowReady);
    }

    private void cacheUniforms() {
        uWorldViewProjection = worldShader.uniform("uViewProjection");
        uWorldLightViewProjection = worldShader.uniform("uLightViewProjection");
        uWorldModel = worldShader.uniform("uModel");
        uWorldCamera = worldShader.uniform("uCamera");
        uWorldLight = worldShader.uniform("uLightDirection");
        uWorldSun = worldShader.uniform("uSunColor");
        uWorldFog = worldShader.uniform("uFogColor");
        uWorldTint = worldShader.uniform("uTint");
        uWorldAmbient = worldShader.uniform("uAmbient");
        uWorldFogNear = worldShader.uniform("uFogNear");
        uWorldFogFar = worldShader.uniform("uFogFar");
        uWorldMaterial = worldShader.uniform("uMaterial");
        uWorldDaylight = worldShader.uniform("uDaylight");
        uWorldShadows = worldShader.uniform("uShadows");
        uWorldDetailTexture = worldShader.uniform("uDetailTexture");
        uWorldShadowTexture = worldShader.uniform("uShadowTexture");
        uWorldGrassTexture = worldShader.uniform("uGrassTexture");
        uWorldAsphaltTexture = worldShader.uniform("uAsphaltTexture");
        uWorldCoralTexture = worldShader.uniform("uCoralTexture");
        uWorldStructureTexture = worldShader.uniform("uStructureTexture");

        uWaterViewProjection = waterShader.uniform("uViewProjection");
        uWaterCamera = waterShader.uniform("uCamera");
        uWaterTime = waterShader.uniform("uTime");
        uWaterLight = waterShader.uniform("uLightDirection");
        uWaterSun = waterShader.uniform("uSunColor");
        uWaterDaylight = waterShader.uniform("uDaylight");
        uWaterFog = waterShader.uniform("uFogColor");
        uWaterFogNear = waterShader.uniform("uFogNear");
        uWaterFogFar = waterShader.uniform("uFogFar");

        uSkyViewProjection = skyShader.uniform("uViewProjection");
        uSkyModel = skyShader.uniform("uModel");
        uSkySun = skyShader.uniform("uSunDirection");
        uSkyDaylight = skyShader.uniform("uDaylight");
        uSkyTime = skyShader.uniform("uTime");

        uShadowLightViewProjection = shadowShader.uniform("uLightViewProjection");
        uShadowModel = shadowShader.uniform("uModel");
    }

    private void createShadowTarget() {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        shadowTexture = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, shadowTexture);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, SHADOW_SIZE, SHADOW_SIZE,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glGenRenderbuffers(1, ids, 0);
        shadowDepthBuffer = ids[0];
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, shadowDepthBuffer);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16,
                SHADOW_SIZE, SHADOW_SIZE);

        GLES20.glGenFramebuffers(1, ids, 0);
        shadowFramebuffer = ids[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, shadowFramebuffer);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, shadowTexture, 0);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                GLES20.GL_RENDERBUFFER, shadowDepthBuffer);
        shadowReady = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
                == GLES20.GL_FRAMEBUFFER_COMPLETE;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        GLES20.glViewport(0, 0, this.width, this.height);
        Matrix.perspectiveM(projection, 0, 60f, this.width / (float) this.height, .15f, 12000f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        boolean traceFirstFrame = !firstFrameStarted;
        if (traceFirstFrame) {
            firstFrameStarted = true;
            Log.i(TAG, "first frame started; quality=" + state.quality);
        }
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
        float fogR = .035f + daylight * (.43f + warm * .10f);
        float fogG = .06f + daylight * (.60f - warm * .07f);
        float fogB = .11f + daylight * .68f;

        updateCamera(dt);
        updateSunAndShadowMatrix();
        if (shadowReady && state.quality > 0) renderShadowMap();
        if (traceFirstFrame) Log.i(TAG, "first frame shadow stage complete");

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glClearColor(fogR, fogG, fogB, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        float fogNear = state.quality == 0 ? 620f : state.quality == 1 ? 1050f : 1550f;
        float fogFar = state.quality == 0 ? 2350f : state.quality == 1 ? 3900f : 5450f;
        float sunR = .72f + daylight * .48f + warm * .24f;
        float sunG = .66f + daylight * .39f;
        float sunB = .66f + daylight * .27f - warm * .16f;

        drawSky(daylight);
        if (traceFirstFrame) Log.i(TAG, "first frame sky complete");
        drawOcean(fogR, fogG, fogB, fogNear, fogFar, daylight, sunR, sunG, sunB);
        if (traceFirstFrame) Log.i(TAG, "first frame ocean complete");
        drawWorld(fogR, fogG, fogB, fogNear, fogFar, daylight, sunR, sunG, sunB);
        if (traceFirstFrame) {
            firstFrameFinished = true;
            Log.i(TAG, "first frame complete");
        }
    }

    private void updateCamera(float dt) {
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
    }

    private void updateSunAndShadowMatrix() {
        float sunAngle = (state.worldTimeHours - 6f) / 12f * (float) Math.PI;
        lightX = (float) Math.cos(sunAngle) * .58f;
        lightY = Math.max(.16f, (float) Math.sin(sunAngle));
        lightZ = (float) Math.sin(sunAngle * .63f) * .38f;
        float inv = 1f / (float) Math.sqrt(lightX * lightX + lightY * lightY + lightZ * lightZ);
        lightX *= inv; lightY *= inv; lightZ *= inv;

        float targetX = state.focusX(), targetZ = state.focusZ();
        float targetY = state.world.heightAt(targetX, targetZ) + 20f;
        float distance = 1050f;
        Matrix.setLookAtM(lightView, 0,
                targetX + lightX * distance, targetY + lightY * distance,
                targetZ + lightZ * distance, targetX, targetY, targetZ, 0, 1, 0);
        Matrix.orthoM(lightProjection, 0, -620f, 620f, -620f, 620f, 8f, 2350f);
        Matrix.multiplyMM(lightViewProjection, 0, lightProjection, 0, lightView, 0);
    }

    private void renderShadowMap() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, shadowFramebuffer);
        GLES20.glViewport(0, 0, SHADOW_SIZE, SHADOW_SIZE);
        GLES20.glClearColor(1, 1, 1, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_BLEND);
        shadowShader.use();
        GLES20.glUniformMatrix4fv(uShadowLightViewProjection, 1, false, lightViewProjection, 0);
        GLES20.glUniformMatrix4fv(uShadowModel, 1, false, identity, 0);
        shadowPass = true;
        state.world.drawLand(smoothedCameraX, smoothedCameraZ, state.quality, -1);
        drawVehicle(state.vehicle);
        if (!state.driving) drawPlayer(state.player);
        shadowPass = false;
        GLES20.glEnable(GLES20.GL_BLEND);
    }

    private void drawSky(float daylight) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);
        skyShader.use();
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, smoothedCameraX, smoothedCameraY, smoothedCameraZ);
        Matrix.scaleM(model, 0, 6000f, 6000f, 6000f);
        GLES20.glUniformMatrix4fv(uSkyViewProjection, 1, false, viewProjection, 0);
        GLES20.glUniformMatrix4fv(uSkyModel, 1, false, model, 0);
        GLES20.glUniform3f(uSkySun, lightX, lightY, lightZ);
        GLES20.glUniform1f(uSkyDaylight, daylight);
        GLES20.glUniform1f(uSkyTime, elapsed);
        skyDome.draw();
        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    private void drawOcean(float fogR, float fogG, float fogB, float fogNear, float fogFar,
                           float daylight, float sunR, float sunG, float sunB) {
        waterShader.use();
        GLES20.glUniformMatrix4fv(uWaterViewProjection, 1, false, viewProjection, 0);
        GLES20.glUniform3f(uWaterCamera, smoothedCameraX, smoothedCameraY, smoothedCameraZ);
        GLES20.glUniform1f(uWaterTime, elapsed);
        GLES20.glUniform3f(uWaterLight, lightX, lightY, lightZ);
        GLES20.glUniform3f(uWaterSun, sunR, sunG, sunB);
        GLES20.glUniform1f(uWaterDaylight, daylight);
        GLES20.glUniform3f(uWaterFog, fogR, fogG, fogB);
        GLES20.glUniform1f(uWaterFogNear, fogNear);
        GLES20.glUniform1f(uWaterFogFar, fogFar);
        state.world.drawOcean();
    }

    private void drawWorld(float fogR, float fogG, float fogB, float fogNear, float fogFar,
                           float daylight, float sunR, float sunG, float sunB) {
        worldShader.use();
        GLES20.glUniformMatrix4fv(uWorldViewProjection, 1, false, viewProjection, 0);
        GLES20.glUniformMatrix4fv(uWorldLightViewProjection, 1, false, lightViewProjection, 0);
        GLES20.glUniform3f(uWorldCamera, smoothedCameraX, smoothedCameraY, smoothedCameraZ);
        GLES20.glUniform3f(uWorldLight, lightX, lightY, lightZ);
        GLES20.glUniform3f(uWorldSun, sunR, sunG, sunB);
        GLES20.glUniform3f(uWorldFog, fogR, fogG, fogB);
        GLES20.glUniform1f(uWorldAmbient, .15f + daylight * .20f);
        GLES20.glUniform1f(uWorldFogNear, fogNear);
        GLES20.glUniform1f(uWorldFogFar, fogFar);
        GLES20.glUniform1f(uWorldDaylight, daylight);
        GLES20.glUniform1f(uWorldShadows, shadowReady && state.quality > 0 ? 1f : 0f);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, detailTexture);
        GLES20.glUniform1i(uWorldDetailTexture, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, shadowTexture);
        GLES20.glUniform1i(uWorldShadowTexture, 1);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, surfaceTextures[0]);
        GLES20.glUniform1i(uWorldGrassTexture, 2);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, surfaceTextures[1]);
        GLES20.glUniform1i(uWorldAsphaltTexture, 3);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE4);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, surfaceTextures[2]);
        GLES20.glUniform1i(uWorldCoralTexture, 4);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE5);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, surfaceTextures[3]);
        GLES20.glUniform1i(uWorldStructureTexture, 5);
        setModelAndTint(identity, 1, 1, 1, 1);
        state.world.drawLand(smoothedCameraX, smoothedCameraZ, state.quality, uWorldMaterial);

        drawVehicle(state.vehicle);
        if (!state.driving) drawPlayer(state.player);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }

    private void drawVehicle(Vehicle car) {
        Matrix.setIdentityM(baseModel, 0);
        Matrix.translateM(baseModel, 0, car.x, car.y, car.z);
        Matrix.rotateM(baseModel, 0, degrees(car.yaw), 0, 1, 0);
        Matrix.rotateM(baseModel, 0, -degrees(car.pitch), 1, 0, 0);
        Matrix.rotateM(baseModel, 0, degrees(car.roll), 0, 0, 1);

        if (!shadowPass) {
            GLES20.glUniform1f(uWorldMaterial, 5f);
            drawShadow(car.x, state.world.heightAt(car.x, car.z) + .035f,
                    car.z, 1.42f, 2.62f, car.yaw, .22f);
        }
        drawMeshAtParent(carBody, baseModel, .035f, .49f, .47f, 1);
        drawMeshAtParent(carCabin, baseModel, .045f, .115f, .145f, .96f);
        drawChild(cube, baseModel, 0, 1.42f, -.15f, 1.18f, .08f, 1.30f,
                0, 0, 0, .035f, .43f, .42f, 1);
        drawChild(cube, baseModel, -.64f, 1.02f, -.16f, .075f, .72f, .10f,
                0, 0, 0, .035f, .43f, .42f, 1);
        drawChild(cube, baseModel, .64f, 1.02f, -.16f, .075f, .72f, .10f,
                0, 0, 0, .035f, .43f, .42f, 1);
        drawChild(cube, baseModel, 0, .72f, -.12f, 1.57f, .055f, 2.34f,
                0, 0, 0, .16f, .68f, .65f, 1);
        drawChild(cube, baseModel, 0, .35f, 2.06f, 1.52f, .18f, .13f,
                0, 0, 0, .065f, .075f, .08f, 1);
        drawChild(cube, baseModel, 0, .37f, -2.06f, 1.55f, .19f, .13f,
                0, 0, 0, .055f, .065f, .07f, 1);
        drawChild(cube, baseModel, -.57f, .54f, 2.08f, .38f, .18f, .07f,
                0, 0, 0, 1f, .88f, .52f, 1);
        drawChild(cube, baseModel, .57f, .54f, 2.08f, .38f, .18f, .07f,
                0, 0, 0, 1f, .88f, .52f, 1);
        drawChild(cube, baseModel, -.58f, .50f, -2.08f, .34f, .16f, .07f,
                0, 0, 0, .90f, .045f, .035f, 1);
        drawChild(cube, baseModel, .58f, .50f, -2.08f, .34f, .16f, .07f,
                0, 0, 0, .90f, .045f, .035f, 1);

        float steerDegrees = -degrees(car.steering * .48f);
        drawWheel(baseModel, -1.00f, .02f, 1.34f, steerDegrees, car.wheelSpin);
        drawWheel(baseModel, 1.00f, .02f, 1.34f, steerDegrees, car.wheelSpin);
        drawWheel(baseModel, -1.00f, .02f, -1.33f, 0, car.wheelSpin);
        drawWheel(baseModel, 1.00f, .02f, -1.33f, 0, car.wheelSpin);
    }

    private void drawWheel(float[] parent, float x, float y, float z, float steer, float spin) {
        System.arraycopy(parent, 0, childModel, 0, 16);
        Matrix.translateM(childModel, 0, x, y, z);
        Matrix.rotateM(childModel, 0, steer, 0, 1, 0);
        Matrix.rotateM(childModel, 0, 90, 0, 0, 1);
        Matrix.rotateM(childModel, 0, degrees(spin), 0, 1, 0);
        Matrix.scaleM(childModel, 0, .84f, .42f, .84f);
        setModelAndTint(childModel, .025f, .029f, .032f, 1);
        cylinder.draw();
        // Contrasting metal hub makes the wheel read as a curved mechanical assembly.
        System.arraycopy(parent, 0, childModel, 0, 16);
        Matrix.translateM(childModel, 0, x + (x < 0 ? -.235f : .235f), y, z);
        Matrix.rotateM(childModel, 0, steer, 0, 1, 0);
        Matrix.rotateM(childModel, 0, 90, 0, 0, 1);
        Matrix.scaleM(childModel, 0, .42f, .45f, .42f);
        setModelAndTint(childModel, .50f, .54f, .55f, 1);
        cylinder.draw();
    }

    private void drawPlayer(Player player) {
        Matrix.setIdentityM(baseModel, 0);
        Matrix.translateM(baseModel, 0, player.x, player.y, player.z);
        Matrix.rotateM(baseModel, 0, degrees(player.yaw), 0, 1, 0);
        float swing = (float) Math.sin(elapsed * 9f) * 24f * player.movementBlend;
        if (!shadowPass) {
            GLES20.glUniform1f(uWorldMaterial, 6f);
            drawShadow(player.x, state.world.heightAt(player.x, player.z) + .03f,
                    player.z, .48f, .66f, player.yaw, .20f);
        }
        drawChild(sphere, baseModel, 0, 1.34f, 0, .70f, .91f, .43f,
                0, 0, 0, .91f, .40f, .12f, 1);
        drawChild(cylinder, baseModel, 0, 1.79f, 0, .22f, .25f, .22f,
                0, 0, 0, .38f, .22f, .14f, 1);
        drawChild(sphere, baseModel, 0, 2.06f, .01f, .50f, .55f, .50f,
                0, 0, 0, .39f, .235f, .155f, 1);
        drawChild(sphere, baseModel, 0, 2.25f, -.035f, .51f, .24f, .51f,
                0, 0, 0, .055f, .035f, .025f, 1);
        drawChild(sphere, baseModel, 0, 2.05f, .27f, .12f, .14f, .14f,
                0, 0, 0, .40f, .235f, .15f, 1);
        drawChild(cube, baseModel, -.09f, 2.11f, .263f, .045f, .035f, .025f,
                0, 0, 0, .025f, .025f, .022f, 1);
        drawChild(cube, baseModel, .09f, 2.11f, .263f, .045f, .035f, .025f,
                0, 0, 0, .025f, .025f, .022f, 1);
        drawChild(cylinder, baseModel, -.20f, .62f, 0, .26f, .84f, .26f,
                swing, 0, 0, .095f, .17f, .23f, 1);
        drawChild(cylinder, baseModel, .20f, .62f, 0, .26f, .84f, .26f,
                -swing, 0, 0, .095f, .17f, .23f, 1);
        drawChild(cylinder, baseModel, -.48f, 1.34f, 0, .20f, .78f, .20f,
                -swing * .8f, 0, 0, .39f, .235f, .155f, 1);
        drawChild(cylinder, baseModel, .48f, 1.34f, 0, .20f, .78f, .20f,
                swing * .8f, 0, 0, .39f, .235f, .155f, 1);
        drawChild(sphere, baseModel, -.48f, .91f, 0, .22f, .25f, .22f,
                0, 0, 0, .40f, .235f, .15f, 1);
        drawChild(sphere, baseModel, .48f, .91f, 0, .22f, .25f, .22f,
                0, 0, 0, .40f, .235f, .15f, 1);
        drawChild(sphere, baseModel, -.20f, .17f, .11f, .31f, .22f, .53f,
                0, 0, 0, .035f, .045f, .05f, 1);
        drawChild(sphere, baseModel, .20f, .17f, .11f, .31f, .22f, .53f,
                0, 0, 0, .035f, .045f, .05f, 1);
    }

    private void drawShadow(float x, float y, float z, float sx, float sz, float yaw, float alpha) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        Matrix.rotateM(model, 0, degrees(yaw), 0, 1, 0);
        Matrix.scaleM(model, 0, sx, 1, sz);
        setModelAndTint(model, .008f, .012f, .014f, alpha);
        GLES20.glDepthMask(false);
        disc.draw();
        GLES20.glDepthMask(true);
    }

    private void drawMeshAtParent(Mesh mesh, float[] parent, float r, float g, float b, float a) {
        setModelAndTint(parent, r, g, b, a);
        mesh.draw();
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
        if (shadowPass) {
            GLES20.glUniformMatrix4fv(uShadowModel, 1, false, matrix, 0);
        } else {
            GLES20.glUniformMatrix4fv(uWorldModel, 1, false, matrix, 0);
            GLES20.glUniform4f(uWorldTint, r, g, b, a);
        }
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
        return Math.max(.035f, Math.min(1f, value));
    }

    private static float degrees(float radians) { return radians * 180f / (float) Math.PI; }

    private static Mesh buildCube() {
        MeshBuilder b = new MeshBuilder();
        b.addBox(0, -.5f, 0, 1, 1, 1, 0, 1, 1, 1);
        return b.build();
    }

    private static Mesh buildCylinder() {
        MeshBuilder b = new MeshBuilder();
        b.addCylinder(0, -.5f, 0, .5f, 1f, 16, 1, 1, 1);
        return b.build();
    }

    private static Mesh buildSphere() {
        MeshBuilder b = new MeshBuilder();
        b.addEllipsoid(0, 0, 0, .5f, .5f, .5f, 8, 12, 1, 1, 1);
        return b.build();
    }

    private static Mesh buildSkyDome() {
        MeshBuilder b = new MeshBuilder();
        b.addEllipsoid(0, 0, 0, 1, 1, 1, 14, 28, 1, 1, 1);
        return b.build();
    }

    private static Mesh buildCarCabin() {
        MeshBuilder builder = new MeshBuilder();
        float[][] p = {
                {-.78f,.67f,.94f},{.78f,.67f,.94f},{.78f,.67f,-1.16f},{-.78f,.67f,-1.16f},
                {-.57f,1.40f,.49f},{.57f,1.40f,.49f},{.57f,1.40f,-.80f},{-.57f,1.40f,-.80f}
        };
        addCabinFace(builder,p,0,1,5,4,0,.48f,.88f);
        addCabinFace(builder,p,1,2,6,5,1,.20f,0);
        addCabinFace(builder,p,2,3,7,6,0,.43f,-.90f);
        addCabinFace(builder,p,3,0,4,7,-1,.20f,0);
        addCabinFace(builder,p,4,5,6,7,0,1,0);
        return builder.build();
    }

    private static void addCabinFace(MeshBuilder builder, float[][] p,
                                     int ia, int ib, int ic, int id,
                                     float nx, float ny, float nz) {
        float inv = 1f / Math.max(.001f, (float) Math.sqrt(nx * nx + ny * ny + nz * nz));
        nx *= inv; ny *= inv; nz *= inv;
        int a = builder.vertex(p[ia][0],p[ia][1],p[ia][2],nx,ny,nz,1,1,1);
        int b = builder.vertex(p[ib][0],p[ib][1],p[ib][2],nx,ny,nz,1,1,1);
        int c = builder.vertex(p[ic][0],p[ic][1],p[ic][2],nx,ny,nz,1,1,1);
        int d = builder.vertex(p[id][0],p[id][1],p[id][2],nx,ny,nz,1,1,1);
        builder.quad(a,b,c,d);
    }

    private static Mesh buildRoundedCarBody() {
        MeshBuilder b = new MeshBuilder();
        float[] z = {-2.08f, -1.77f, -.78f, .78f, 1.72f, 2.08f};
        float[] width = {.58f, .88f, .98f, .98f, .86f, .55f};
        float[] centreY = {.40f, .43f, .45f, .45f, .40f, .36f};
        float[] radiusY = {.22f, .34f, .40f, .40f, .31f, .19f};
        int radial = 12;
        int[][] ring = new int[z.length][radial];
        for (int iz = 0; iz < z.length; iz++) {
            for (int ir = 0; ir < radial; ir++) {
                float a = (float) (ir * Math.PI * 2.0 / radial);
                float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                float px = ca * width[iz];
                float py = centreY[iz] + sa * radiusY[iz];
                float nz = iz == 0 ? -.35f : iz == z.length - 1 ? .35f : 0;
                float inv = 1f / (float) Math.sqrt(ca * ca + sa * sa + nz * nz);
                ring[iz][ir] = b.vertex(px, py, z[iz], ca * inv, sa * inv, nz * inv, 1, 1, 1);
            }
        }
        for (int iz = 0; iz < z.length - 1; iz++) {
            for (int ir = 0; ir < radial; ir++) {
                b.quad(ring[iz][ir], ring[iz][(ir + 1) % radial],
                        ring[iz + 1][(ir + 1) % radial], ring[iz + 1][ir]);
            }
        }
        int back = b.vertex(0, centreY[0], z[0], 0, 0, -1, 1, 1, 1);
        int front = b.vertex(0, centreY[z.length - 1], z[z.length - 1], 0, 0, 1, 1, 1, 1);
        for (int ir = 0; ir < radial; ir++) {
            b.triangle(back, ring[0][(ir + 1) % radial], ring[0][ir]);
            b.triangle(front, ring[z.length - 1][ir], ring[z.length - 1][(ir + 1) % radial]);
        }
        return b.build();
    }

    private static Mesh buildDisc() {
        MeshBuilder b = new MeshBuilder();
        int center = b.vertex(0, 0, 0, 0, 1, 0, 1, 1, 1);
        int sides = 32;
        int[] ring = new int[sides];
        for (int i = 0; i < sides; i++) {
            double a = i * Math.PI * 2 / sides;
            ring[i] = b.vertex((float) Math.cos(a), 0, (float) Math.sin(a), 0, 1, 0, 1, 1, 1);
        }
        for (int i = 0; i < sides; i++) b.triangle(center, ring[i], ring[(i + 1) % sides]);
        return b.build();
    }
}
