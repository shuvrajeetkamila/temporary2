package ai.arena.nauru;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import ai.arena.nauru.game.GameState;
import ai.arena.nauru.game.NauruSurfaceView;
import ai.arena.nauru.input.InputState;
import ai.arena.nauru.ui.GameHudView;
import ai.arena.nauru.world.NauruWorld;

/** Single-activity, dependency-free Android game shell. */
public final class MainActivity extends Activity {
    private NauruSurfaceView surface;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        enterImmersiveMode();

        InputState input = new InputState();
        NauruWorld world = new NauruWorld();
        GameState gameState = new GameState(getApplicationContext(), world, input);
        surface = new NauruSurfaceView(this, gameState);
        GameHudView hud = new GameHudView(this, gameState, input);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.rgb(4, 20, 27));
        root.addView(surface, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(hud, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        if (surface != null) surface.onResume();
    }

    @Override
    protected void onPause() {
        if (surface != null) {
            surface.saveGame();
            surface.onPause();
        }
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }
}
