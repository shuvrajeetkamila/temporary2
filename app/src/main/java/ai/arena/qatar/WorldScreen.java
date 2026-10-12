package ai.arena.qatar;

import ai.arena.qatar.input.TouchController;
import ai.arena.qatar.physics.PhysicsWorld;
import ai.arena.qatar.render.QatarWorldRenderer;
import ai.arena.qatar.ui.HudRenderer;
import ai.arena.qatar.world.QatarPack;
import ai.arena.qatar.world.WorldStreamer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.math.Vector3;

/** Main simulation screen. Full national data stays installed; local chunks stream around this focus. */
final class WorldScreen extends ScreenAdapter {
    private final QatarPack pack;
    private final PhysicsWorld physics;
    private final QatarWorldRenderer renderer;
    private final WorldStreamer streamer;
    private final TouchController controls;
    private final HudRenderer hud;
    private boolean disposed;
    private float frameAccumulator;
    private static final float TARGET_FRAME_SECONDS=1f/30f;

    WorldScreen(QatarPack pack){
        this.pack=pack;
        // Souq Waqif is safely inland; the Corniche anchor itself resolves to
        // bay water at the current 125 m coast-mask resolution.
        QatarPack.Hub spawn=pack.hub("Souq Waqif");
        physics=new PhysicsWorld(spawn.x(),spawn.z());
        renderer=new QatarWorldRenderer(pack,physics);
        streamer=new WorldStreamer(pack,renderer);
        controls=new TouchController();
        hud=new HudRenderer(pack,physics,streamer,renderer,controls);
        Gdx.input.setInputProcessor(controls);
    }

    @Override public void render(float delta){
        // Explicit 30 Hz foreground budget: high-refresh phones do not render the
        // same mobile scene at 60/90/120 Hz and become needlessly hot.
        frameAccumulator+=Math.min(delta,.1f);
        if(frameAccumulator<TARGET_FRAME_SECONDS)return;
        float simulationDelta=Math.min(.1f,frameAccumulator);
        frameAccumulator%=TARGET_FRAME_SECONDS;
        if(controls.consumeModeRequested())physics.switchMode(physics.mode().next(),pack);
        if(controls.consumeRecoveryRequested())physics.recover();
        physics.setControls(controls.throttle(),controls.steering(),controls.lift(),controls.action());
        Vector3 velocity=physics.velocity();
        streamer.update(physics.globalX(),physics.globalZ(),velocity.x,velocity.z,physics.mode().streamMode);
        physics.step(simulationDelta);
        renderer.render(simulationDelta);
        hud.render();
    }

    @Override public void resize(int width,int height){renderer.camera().viewportWidth=width;renderer.camera().viewportHeight=height;renderer.camera().update();}
    @Override public void pause(){physics.setControls(0,0,0,false);}
    @Override public void resume(){Gdx.input.setInputProcessor(controls);}

    @Override public void dispose(){
        if(disposed)return;disposed=true;
        Gdx.input.setInputProcessor(null);
        streamer.dispose();hud.dispose();renderer.dispose();physics.dispose();
    }
}
