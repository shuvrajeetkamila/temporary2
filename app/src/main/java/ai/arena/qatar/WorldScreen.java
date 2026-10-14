package ai.arena.qatar;

import ai.arena.qatar.environment.QatarEnvironment;
import ai.arena.qatar.input.TouchController;
import ai.arena.qatar.performance.ThermalGovernor;
import ai.arena.qatar.physics.PhysicsWorld;
import ai.arena.qatar.render.QatarWorldRenderer;
import ai.arena.qatar.ui.HudRenderer;
import ai.arena.qatar.world.QatarDetailPack;
import ai.arena.qatar.world.QatarDetailStreamer;
import ai.arena.qatar.world.QatarPack;
import ai.arena.qatar.world.QatarSpawnResolver;
import ai.arena.qatar.world.WorldStreamer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.math.Vector3;

import java.util.function.IntSupplier;

/** Main simulation screen. Full national data stays installed; local chunks stream around this focus. */
final class WorldScreen extends ScreenAdapter {
    private final QatarPack pack;
    private final QatarDetailPack detailPack;
    private final PhysicsWorld physics;
    private final QatarEnvironment climate=new QatarEnvironment();
    private final QatarWorldRenderer renderer;
    private final WorldStreamer streamer;
    private final QatarDetailStreamer detailStreamer;
    private final TouchController controls;
    private final HudRenderer hud;
    private final IntSupplier thermalStatus;
    private ThermalGovernor.Profile profile;
    private boolean disposed;
    private float frameAccumulator;
    private float runtimeHealthAccumulator;

    WorldScreen(QatarPack pack,QatarDetailPack detailPack,QatarSpawnResolver.Spawn spawn,float groundHeight,IntSupplier thermalStatus){
        this.pack=pack;this.detailPack=detailPack;this.thermalStatus=thermalStatus;
        physics=new PhysicsWorld(spawn.x(),spawn.z(),groundHeight);
        renderer=new QatarWorldRenderer(pack,detailPack,physics,climate);
        streamer=new WorldStreamer(pack,renderer);
        detailStreamer=new QatarDetailStreamer(detailPack,renderer);
        controls=new TouchController();
        hud=new HudRenderer(pack,detailPack,physics,streamer,detailStreamer,renderer,controls,climate);
        applyProfile(ThermalGovernor.profileForAndroidStatus(thermalStatus.getAsInt()));
        Gdx.app.log("QatarEnvironment","ENV_READY presets=CLEAR,SHAMAL,DUST,RAIN tide=dynamic wind=enabled");
        Gdx.input.setInputProcessor(controls);
    }

    private void applyProfile(ThermalGovernor.Profile requested){
        if(profile==requested)return;profile=requested;Gdx.graphics.setForegroundFPS(profile.targetFps);
        detailStreamer.setBudgets(profile.detailRadiusCells,profile.maxDetailCells,profile.activationBudget);
        physics.setDetailColliderBudget(profile.buildingCollisionCells);renderer.setPerformanceProfile(profile);hud.setPerformanceProfile(profile);
        Gdx.app.log("QatarThermal","PROFILE "+profile.label+" fps="+profile.targetFps+" detailRadius="+profile.detailRadiusCells);
    }

    @Override public void render(float delta){
        applyProfile(ThermalGovernor.profileForAndroidStatus(thermalStatus.getAsInt()));
        float targetFrameSeconds=1f/profile.targetFps;frameAccumulator+=Math.min(delta,.1f);if(frameAccumulator<targetFrameSeconds)return;
        float simulationDelta=Math.min(.1f,frameAccumulator);frameAccumulator%=targetFrameSeconds;climate.update(simulationDelta);
        if(controls.consumeModeRequested())physics.switchMode(physics.mode().next(),pack);
        physics.setControls(controls.throttle(),controls.steering(),controls.lift(),controls.action());
        Vector3 velocity=physics.velocity();double focusX=physics.globalX(),focusZ=physics.globalZ();
        streamer.update(focusX,focusZ,velocity.x,velocity.z,physics.mode().streamMode);
        detailStreamer.update(focusX,focusZ,velocity.x,velocity.z,physics.mode().streamMode);
        physics.setGroundReference(renderer.terrainHeightAt(focusX,focusZ));
        physics.setEnvironment(climate.seaLevelMetres(),climate.windX(),climate.windZ(),renderer.waterAt(focusX,focusZ));
        if(controls.consumeRecoveryRequested())physics.recover();
        physics.syncDetailCollisions(focusX,focusZ);physics.step(simulationDelta);renderer.render(simulationDelta);hud.render();
        runtimeHealthAccumulator+=simulationDelta;if(runtimeHealthAccumulator>=10f){runtimeHealthAccumulator=0;Vector3 stateVelocity=physics.velocity();float height=physics.heightAboveGround(),speed=stateVelocity.len();boolean stable=Float.isFinite(height)&&Float.isFinite(speed)&&(physics.mode()!=PhysicsWorld.Mode.WALK||(height>-2&&height<12&&speed<12));Gdx.app.log("QatarRuntime","STATE mode="+physics.mode()+" heightAboveGround="+height+" speed="+speed+" stable="+(stable?"yes":"no"));}
    }

    @Override public void resize(int width,int height){renderer.camera().viewportWidth=width;renderer.camera().viewportHeight=height;renderer.camera().update();}
    @Override public void pause(){physics.setControls(0,0,0,false);}
    @Override public void resume(){Gdx.input.setInputProcessor(controls);applyProfile(ThermalGovernor.profileForAndroidStatus(thermalStatus.getAsInt()));}
    @Override public void dispose(){if(disposed)return;disposed=true;Gdx.input.setInputProcessor(null);detailStreamer.dispose();streamer.dispose();hud.dispose();renderer.dispose();physics.dispose();}
}
