package ai.arena.nauru.game;

import android.content.Context;
import android.opengl.GLSurfaceView;

/** EGL host kept separate from simulation so Android lifecycle changes do not reset the world. */
public final class NauruSurfaceView extends GLSurfaceView {
    private final GameRenderer gameRenderer;

    public NauruSurfaceView(Context context, GameRenderer renderer) {
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setPreserveEGLContextOnPause(true);
        gameRenderer = renderer;
        setRenderer(gameRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public void saveGame() { gameRenderer.save(); }
}
