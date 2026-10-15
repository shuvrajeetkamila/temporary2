package ai.arena.qatar;

import ai.arena.qatar.environment.QatarEnvironment;
import ai.arena.qatar.input.TouchController;
import ai.arena.qatar.performance.ThermalGovernor;
import ai.arena.qatar.physics.PhysicsHealth;
import ai.arena.qatar.physics.PhysicsWorld;
import ai.arena.qatar.render.QatarWorldRenderer;
import ai.arena.qatar.ui.HudRenderer;
import ai.arena.qatar.ui.NationalMapLayout;
import ai.arena.qatar.world.QatarDetailPack;
import ai.arena.qatar.world.QatarDetailStreamer;
import ai.arena.qatar.world.QatarPack;
import ai.arena.qatar.world.QatarSpawnResolver;
import ai.arena.qatar.world.QatarTeleportResolver;
import ai.arena.qatar.world.WorldStreamer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.math.Vector2;
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
    private final Vector2 selectedWorld=new Vector2();
    private ThermalGovernor.Profile profile;
    private boolean disposed;
    private float frameAccumulator;
    private float runtimeHealthAccumulator;
    private float teleportSupportSeconds;
    private float teleportFallbackGround;
    private boolean teleportFallbackWater;

    WorldScreen(QatarPack pack,QatarDetailPack detailPack,QatarSpawnResolver.Spawn spawn,float groundHeight,IntSupplier thermalStatus){
        this.pack=pack;this.detailPack=detailPack;this.thermalStatus=thermalStatus;
        physics=new PhysicsWorld(spawn.x(),spawn.z(),groundHeight);
        renderer=new QatarWorldRenderer(pack,detailPack,physics,climate);
        streamer=new WorldStreamer(pack,renderer);
        detailStreamer=new QatarDetailStreamer(detailPack,renderer);
        controls=new TouchController(pack.worldWidth,pack.worldHeight);
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
        processMapInput();
        if(controls.mapOpen()){
            physics.setControls(0,0,0,false);renderer.render(simulationDelta);hud.render();return;
        }
        if(controls.consumeModeRequested())physics.switchMode(physics.mode().next(),pack);
        physics.setControls(controls.throttle(),controls.steering(),controls.lift(),controls.action());
        Vector3 velocity=physics.velocity();double focusX=physics.globalX(),focusZ=physics.globalZ();
        streamer.update(focusX,focusZ,velocity.x,velocity.z,physics.mode().streamMode);
        detailStreamer.update(focusX,focusZ,velocity.x,velocity.z,physics.mode().streamMode);
        float streamedGround=renderer.terrainHeightAt(focusX,focusZ);
        if(Float.isFinite(streamedGround))physics.setGroundReference(streamedGround);else if(teleportSupportSeconds>0)physics.setGroundReference(teleportFallbackGround);
        boolean streamedWater=renderer.waterAt(focusX,focusZ);
        boolean water=streamedWater||(teleportSupportSeconds>0&&teleportFallbackWater&&!Float.isFinite(streamedGround));
        physics.setEnvironment(climate.seaLevelMetres(),climate.windX(),climate.windZ(),water);
        if(controls.consumeRecoveryRequested())physics.recover();
        physics.syncDetailCollisions(focusX,focusZ);physics.step(simulationDelta);renderer.render(simulationDelta);hud.render();
        teleportSupportSeconds=Math.max(0,teleportSupportSeconds-simulationDelta);
        runtimeHealthAccumulator+=simulationDelta;if(runtimeHealthAccumulator>=10f){
            runtimeHealthAccumulator=0;Vector3 stateVelocity=physics.velocity();float height=physics.heightAboveGround(),support=physics.supportClearance(),speed=stateVelocity.len(),cameraDistance=renderer.cameraDistance();
            boolean stable=PhysicsHealth.stable(physics.mode(),height,support,speed,cameraDistance);
            Gdx.app.log("QatarRuntime","STATE mode="+physics.mode()+" heightAboveGround="+height+" supportClearance="+support+" water="+(physics.hasWaterSupport()?"yes":"no")+" speed="+speed+" cameraDistance="+cameraDistance+" stable="+(stable?"yes":"no"));
        }
    }

    private void processMapInput(){
        if(controls.consumeMapOpenedRequested()){
            hud.setMapStatus("Touch anywhere on Qatar, then press TELEPORT");
            Gdx.app.log("QatarTeleport","MAP_OPEN playerX="+physics.globalX()+" playerZ="+physics.globalZ());
        }
        if(controls.consumeMapClosedRequested())Gdx.app.log("QatarTeleport","MAP_CLOSE gameplay=resumed");
        if(controls.consumeMapSelectionRequested()){
            NationalMapLayout.Layout layout=NationalMapLayout.fullscreen(Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),pack.worldWidth,pack.worldHeight);
            NationalMapLayout.screenToWorld(layout.map(),controls.mapSelectionScreenX(),controls.mapSelectionScreenY(),pack.worldWidth,pack.worldHeight,selectedWorld);
            hud.setMapSelection(selectedWorld.x,selectedWorld.y);
            Gdx.app.log("QatarTeleport","MAP_SELECT x="+selectedWorld.x+" z="+selectedWorld.y+" utmE="+(pack.originEasting+selectedWorld.x)+" utmN="+(pack.originNorthing+selectedWorld.y));
        }
        if(controls.consumeTeleportRequested())teleportFromMap();
    }

    private void teleportFromMap(){
        if(!hud.hasMapSelection()){hud.setMapStatus("Select a destination on the map first");return;}
        hud.setMapStatus("Resolving safe destination from installed offline data...");
        try{
            QatarTeleportResolver.Destination destination=QatarTeleportResolver.resolve(pack,detailPack,physics.mode(),hud.mapSelectionX(),hud.mapSelectionZ(),climate.seaLevelMetres());
            detailStreamer.resetForTeleport();streamer.resetForTeleport();
            physics.teleport(destination.x(),destination.z(),destination.surfaceHeight(),climate.seaLevelMetres(),destination.water());
            renderer.resetCamera();teleportFallbackGround=destination.surfaceHeight();teleportFallbackWater=destination.water();teleportSupportSeconds=8f;
            hud.setMapSelection(destination.x(),destination.z());
            hud.setMapStatus(destination.adjustmentMetres()<1?"Teleported to selected location":"Teleported safely (adjusted "+Math.round(destination.adjustmentMetres())+" m)");
            controls.closeMap();
            Gdx.app.log("QatarTeleport","TELEPORT mode="+physics.mode()+" requestedX="+destination.requestedX()+" requestedZ="+destination.requestedZ()+" x="+destination.x()+" z="+destination.z()+" surface="+destination.surfaceHeight()+" water="+(destination.water()?"yes":"no")+" adjustment="+destination.adjustmentMetres()+" basis="+destination.basis());
        }catch(Throwable error){
            hud.setMapStatus("Teleport unavailable: "+error.getMessage());
            Gdx.app.error("QatarTeleport","TELEPORT_FAILED",error);
        }
    }

    @Override public void resize(int width,int height){renderer.camera().viewportWidth=width;renderer.camera().viewportHeight=height;renderer.camera().update();renderer.resetCamera();}
    @Override public void pause(){physics.setControls(0,0,0,false);Gdx.graphics.setContinuousRendering(false);Gdx.app.log("QatarLifecycle","PAUSED mode="+physics.mode()+" rendering=suspended");}
    @Override public void resume(){Gdx.graphics.setContinuousRendering(true);Gdx.graphics.requestRendering();Gdx.input.setInputProcessor(controls);applyProfile(ThermalGovernor.profileForAndroidStatus(thermalStatus.getAsInt()));renderer.resetCamera();Gdx.app.log("QatarLifecycle","RESUMED mode="+physics.mode()+" rendering=active");}
    @Override public void dispose(){if(disposed)return;disposed=true;controls.closeMap();Gdx.input.setInputProcessor(null);detailStreamer.dispose();streamer.dispose();hud.dispose();renderer.dispose();physics.dispose();}
}
