package ai.arena.nauru.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

/**
 * EGL host with explicit thermal-aware frame pacing. The old continuous render loop could run
 * at 60–120 FPS even when nothing changed; this rebuilt version targets 30 FPS and automatically
 * drops to 20 FPS in battery-saver or elevated thermal states.
 */
public final class NauruSurfaceView extends GLSurfaceView {
    private final GameRenderer gameRenderer;
    private final Handler frameHandler = new Handler(Looper.getMainLooper());
    private final PowerManager powerManager;
    private boolean pacing;
    private long nextPowerSampleMillis;
    private long cachedFrameIntervalMillis = 33L;

    private final Runnable frameTick = new Runnable() {
        @Override public void run() {
            if (!pacing) return;
            requestRender();
            frameHandler.postDelayed(this, preferredIntervalMillis());
        }
    };

    public NauruSurfaceView(Context context, GameRenderer renderer) {
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(true);
        gameRenderer = renderer;
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        setRenderer(gameRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    private long preferredIntervalMillis() {
        long now = SystemClock.uptimeMillis();
        if (now < nextPowerSampleMillis) return cachedFrameIntervalMillis;
        nextPowerSampleMillis = now + 2_000L;

        boolean limited = powerManager != null && powerManager.isPowerSaveMode();
        boolean severe = false;
        if (powerManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int thermalStatus = powerManager.getCurrentThermalStatus();
            limited |= thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE;
            severe = thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE;
        }
        gameRenderer.setThermalLimited(limited);
        // 30 FPS normally, 20 FPS in ECO, and 15 FPS only under severe thermal pressure.
        cachedFrameIntervalMillis = severe ? 67L : limited ? 50L : 33L;
        return cachedFrameIntervalMillis;
    }

    @Override public void onResume() {
        super.onResume();
        pacing = true;
        frameHandler.removeCallbacks(frameTick);
        frameHandler.post(frameTick);
    }

    @Override public void onPause() {
        pacing = false;
        frameHandler.removeCallbacks(frameTick);
        super.onPause();
    }

    @Override protected void onDetachedFromWindow() {
        pacing = false;
        frameHandler.removeCallbacks(frameTick);
        super.onDetachedFromWindow();
    }

    public void saveGame() { gameRenderer.save(); }
}
