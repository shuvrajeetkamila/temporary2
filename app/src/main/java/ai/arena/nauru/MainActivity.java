package ai.arena.nauru;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import ai.arena.nauru.game.GameRenderer;
import ai.arena.nauru.game.GameState;
import ai.arena.nauru.game.NauruSurfaceView;
import ai.arena.nauru.input.InputState;
import ai.arena.nauru.ui.GameHudView;
import ai.arena.nauru.world.NauruWorld;

/** Single-activity Android shell with non-blocking GIS/world initialization. */
public final class MainActivity extends Activity {
    private static final String TAG = "NauruActivity";

    private NauruSurfaceView surface;
    private FrameLayout root;
    private TextView loadingStatus;
    private volatile boolean destroyed;
    private volatile boolean resumed;
    private int loadGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        // Respect Android's screen timeout so an unattended game cannot keep heating the device.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        enterImmersiveMode();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(4, 20, 27));
        showLoadingScreen("BUILDING THE 1:25 GRAPHICS WORLD…");
        setContentView(root);
        startWorldLoad();
    }

    private void showLoadingScreen(String message) {
        root.removeAllViews();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(36, 24, 36, 24);

        TextView title = new TextView(this);
        title.setText("NAURU\nTHE COUNTRYSIDE");
        title.setTextColor(Color.rgb(239, 243, 229));
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ProgressBar progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(54, 54);
        progressParams.topMargin = 20;
        panel.addView(progress, progressParams);

        loadingStatus = new TextView(this);
        loadingStatus.setText(message);
        loadingStatus.setTextColor(Color.rgb(99, 226, 190));
        loadingStatus.setTextSize(12);
        loadingStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = 12;
        panel.addView(loadingStatus, statusParams);

        TextView source = new TextView(this);
        source.setText("REAL GEOGRAPHY • HUMAN-SCALE GAMEPLAY • THERMAL ECO MODE\n© OpenStreetMap contributors • SRTM");
        source.setTextColor(Color.rgb(174, 196, 190));
        source.setTextSize(9);
        source.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sourceParams.topMargin = 14;
        panel.addView(source, sourceParams);

        root.addView(panel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void startWorldLoad() {
        final int generation = ++loadGeneration;
        final android.content.Context appContext = getApplicationContext();
        Thread loader = new Thread(() -> {
            long started = System.nanoTime();
            try {
                InputState input = new InputState();
                NauruWorld world = new NauruWorld(appContext);
                GameState gameState = new GameState(appContext, world, input);
                if (getIntent().getBooleanExtra("qa_low", false)) gameState.quality = 0;
                GameRenderer renderer = new GameRenderer(appContext, gameState);
                Log.i(TAG, "compact 1:25 world prepared in "
                        + ((System.nanoTime() - started) / 1_000_000L) + " ms");
                runOnUiThread(() -> attachGame(generation, renderer, gameState, input));
            } catch (Throwable error) {
                Log.e(TAG, "Unable to prepare Nauru world", error);
                runOnUiThread(() -> showLoadFailure(generation, error));
            }
        }, "Nauru-map-loader");
        loader.setPriority(Thread.NORM_PRIORITY - 1);
        loader.start();
    }

    private void attachGame(int generation, GameRenderer renderer,
                            GameState gameState, InputState input) {
        if (destroyed || generation != loadGeneration) return;
        surface = new NauruSurfaceView(this, renderer);
        GameHudView hud = new GameHudView(this, gameState, input);
        root.removeAllViews();
        root.addView(surface, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(hud, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (resumed) surface.onResume();
    }

    private void showLoadFailure(int generation, Throwable error) {
        if (destroyed || generation != loadGeneration) return;
        root.removeAllViews();
        TextView failure = new TextView(this);
        failure.setGravity(Gravity.CENTER);
        failure.setPadding(48, 32, 48, 32);
        failure.setTextColor(Color.WHITE);
        failure.setTextSize(15);
        failure.setBackgroundColor(Color.rgb(4, 20, 27));
        failure.setText("NAURU COULD NOT START\n\n"
                + error.getClass().getSimpleName()
                + "\n\nTAP TO RETRY WITH A FRESH WORLD");
        failure.setOnClickListener(view -> {
            showLoadingScreen("RETRYING WORLD INITIALIZATION…");
            startWorldLoad();
        });
        root.addView(failure, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
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
        resumed = true;
        enterImmersiveMode();
        if (surface != null) surface.onResume();
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (surface != null) {
            surface.saveGame();
            surface.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        loadGeneration++;
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersiveMode();
    }
}
