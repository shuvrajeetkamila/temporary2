package ai.arena.qatar;

import ai.arena.qatar.environment.QatarEnvironment;
import ai.arena.qatar.input.TouchController;
import ai.arena.qatar.performance.ThermalGovernor;
import ai.arena.qatar.physics.PhysicsHealth;
import ai.arena.qatar.physics.PhysicsWorld;
import ai.arena.qatar.render.QatarWorldRenderer;
import ai.arena.qatar.ui.HudRenderer;
import ai.arena.qatar.world.ModeTransitionPolicy;
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
    private QatarTeleportResolver.Destination pendingMapDestination;
    private PhysicsWorld.Mode pendingMapMode;
    private boolean hasRawMapSelection,mapClosedByTeleport;
    private ThermalGovernor.Profile profile;
    private boolean disposed;
    private float runtimeHealthAccumulator;
    private float teleportSupportSeconds;
    private float teleportFallbackGround;
    private boolean teleportFallbackWater;
    private PhysicsWorld.Mode cadenceMode;
    private float cadenceSeconds,cadenceDeltaSum,cadenceMaximumDelta;
    private int cadenceSamples;

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
        // Android/libGDX already paces the foreground at the thermal profile FPS.
        // Never skip an additional render here: double-throttling 30 Hz into an
        // irregular 15 Hz cadence was the source of the reported speed-amplified jerks.
        float simulationDelta=Math.min(.1f,Math.max(0,delta));climate.update(simulationDelta);recordFrameCadence(simulationDelta);
        processMapInput();
        if(controls.mapOpen()){
            physics.setControls(0,0,0,false);renderer.render(simulationDelta);hud.render();return;
        }
        if(controls.consumeModeRequested())switchModeSafely();
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

    private void recordFrameCadence(float delta){
        PhysicsWorld.Mode current=physics.mode();
        if(cadenceMode!=current){cadenceMode=current;cadenceSeconds=0;cadenceDeltaSum=0;cadenceMaximumDelta=0;cadenceSamples=0;}
        if(delta<=0)return;cadenceSeconds+=delta;cadenceDeltaSum+=delta;cadenceMaximumDelta=Math.max(cadenceMaximumDelta,delta);cadenceSamples++;
        if(cadenceSeconds<5||cadenceSamples==0)return;
        float averageMs=cadenceDeltaSum/cadenceSamples*1000f,maximumMs=cadenceMaximumDelta*1000f,expectedMs=1000f/profile.targetFps;
        boolean smooth=averageMs<=expectedMs*1.65f&&maximumMs<=150f;
        Gdx.app.log("QatarFrameCadence","CADENCE mode="+current+" profile="+profile.label+" samples="+cadenceSamples+" averageMs="+averageMs+" maxMs="+maximumMs+" extraRenderGate=no smooth="+(smooth?"yes":"no"));
        cadenceSeconds=0;cadenceDeltaSum=0;cadenceMaximumDelta=0;cadenceSamples=0;
    }

    private void switchModeSafely(){
        PhysicsWorld.Mode from=physics.mode(),requested=from.next();double requestedX=physics.globalX(),requestedZ=physics.globalZ();
        ModeTransitionPolicy.Placement placement=ModeTransitionPolicy.placementFor(requested);
        physics.switchMode(requested,pack);
        if(placement==ModeTransitionPolicy.Placement.CURRENT_SCREEN){
            // Mode cycling must never force a national stream reset. The boat
            // appears at the current screen position, whether that is land or
            // water; map teleport remains an explicit user action.
            double locationDelta=Math.hypot(physics.globalX()-requestedX,physics.globalZ()-requestedZ);
            renderer.resetCamera();Gdx.app.log("QatarMode","SAFE_TRANSITION from="+from+" to="+requested+" placement=current-screen locationDelta="+locationDelta+" water="+(physics.hasWaterSupport()?"yes":"no")+" streamReset=no movementPacing=preserved");return;
        }
        if(placement==ModeTransitionPolicy.Placement.ROTOR_CLEARANCE){
            renderer.resetCamera();Gdx.app.log("QatarMode","SAFE_TRANSITION from="+from+" to="+requested+" placement=rotor-clearance movementPacing=preserved");return;
        }
        try{
            QatarTeleportResolver.Destination destination=placement==ModeTransitionPolicy.Placement.RUNWAY
                    ?QatarTeleportResolver.resolveRunway(pack,detailPack,requestedX,requestedZ,climate.seaLevelMetres())
                    :QatarTeleportResolver.resolve(pack,detailPack,requested,requestedX,requestedZ,climate.seaLevelMetres());
            detailStreamer.resetForTeleport();streamer.resetForTeleport();
            if(placement==ModeTransitionPolicy.Placement.RUNWAY)physics.placeOnSurface(destination.x(),destination.z(),destination.surfaceHeight(),climate.seaLevelMetres(),false);
            else physics.teleport(destination.x(),destination.z(),destination.surfaceHeight(),climate.seaLevelMetres(),destination.water());
            teleportFallbackGround=destination.surfaceHeight();teleportFallbackWater=destination.water();teleportSupportSeconds=8f;renderer.resetCamera();
            Gdx.app.log("QatarMode","SAFE_TRANSITION from="+from+" to="+requested+" placement="+destination.basis()+" adjustment="+destination.adjustmentMetres()+" water="+(destination.water()?"yes":"no")+" movementPacing=preserved");
        }catch(Throwable error){
            renderer.resetCamera();Gdx.app.error("QatarMode","SAFE_TRANSITION_FALLBACK from="+from+" to="+requested,error);
        }
    }

    private void processMapInput(){
        if(controls.consumeMapOpenedRequested()){
            hud.setMapStatus("Full-screen map active - tap a point, then confirm the final arrival pin");
            Gdx.app.log("QatarTeleport","MAP_OPEN playerX="+physics.globalX()+" playerZ="+physics.globalZ()+" zoom=1.0 fullQatar=yes fullScreen=yes interactive=yes crossClose=yes");
        }
        if(controls.consumeMapClosedRequested()){
            Gdx.app.log("QatarTeleport","MAP_CLOSE gameplay=resumed fullScreenExited=yes teleport="+(mapClosedByTeleport?"yes":"no"));mapClosedByTeleport=false;
        }
        if(controls.consumeMapViewChangedRequested()){
            hud.setMapStatus("View changed by "+controls.mapViewAction()+" - destination pin stays geographically fixed");
            Gdx.app.log("QatarMap","MAP_VIEW action="+controls.mapViewAction()+" zoom="+controls.mapViewport().zoom()+" centerX="+controls.mapViewport().centerX()+" centerZ="+controls.mapViewport().centerZ()+" bounded=yes");
        }
        if(controls.consumeMapSelectionRequested()){
            selectedWorld.set(controls.mapSelectionWorldX(),controls.mapSelectionWorldZ());hasRawMapSelection=true;
            hud.setMapSelection(selectedWorld.x,selectedWorld.y);
            Gdx.app.log("QatarTeleport","MAP_SELECT x="+selectedWorld.x+" z="+selectedWorld.y+" zoom="+controls.mapViewport().zoom()+" gestureSuppressed=yes utmE="+(pack.originEasting+selectedWorld.x)+" utmN="+(pack.originNorthing+selectedWorld.y));
            previewMapDestination();
        }
        if(controls.consumeTeleportRequested())teleportFromMap();
    }

    private void previewMapDestination(){
        hud.setMapStatus("Resolving the exact safe arrival point...");pendingMapDestination=null;pendingMapMode=null;
        try{
            QatarTeleportResolver.Destination destination=QatarTeleportResolver.resolve(pack,detailPack,physics.mode(),selectedWorld.x,selectedWorld.y,climate.seaLevelMetres());
            pendingMapDestination=destination;pendingMapMode=physics.mode();
            hud.setResolvedMapSelection(destination.x(),destination.z(),destination.adjustmentMetres(),destination.basis());
            Gdx.app.log("QatarTeleport","MAP_PREVIEW mode="+physics.mode()+" requestedX="+destination.requestedX()+" requestedZ="+destination.requestedZ()+" finalX="+destination.x()+" finalZ="+destination.z()+" adjustment="+destination.adjustmentMetres()+" visiblePin=yes exactConfirmation=yes");
        }catch(Throwable error){
            hud.setMapStatus("Point unavailable: "+errorSummary(error));Gdx.app.error("QatarTeleport","MAP_PREVIEW_FAILED",error);
        }
    }

    private void teleportFromMap(){
        if(!hud.hasMapSelection()||!hasRawMapSelection){hud.setMapStatus("Select a destination on the map first");return;}
        hud.setMapStatus("Entering the confirmed arrival point...");
        try{
            QatarTeleportResolver.Destination destination=pendingMapDestination!=null&&pendingMapMode==physics.mode()
                    ?pendingMapDestination
                    :QatarTeleportResolver.resolve(pack,detailPack,physics.mode(),selectedWorld.x,selectedWorld.y,climate.seaLevelMetres());
            detailStreamer.resetForTeleport();streamer.resetForTeleport();
            physics.teleport(destination.x(),destination.z(),destination.surfaceHeight(),climate.seaLevelMetres(),destination.water());
            renderer.resetCamera();renderer.showDestinationMarker(destination.x(),destination.z(),destination.surfaceHeight());teleportFallbackGround=destination.surfaceHeight();teleportFallbackWater=destination.water();teleportSupportSeconds=8f;
            hud.setResolvedMapSelection(destination.x(),destination.z(),destination.adjustmentMetres(),destination.basis());
            hud.setMapStatus("Arrived at the exact confirmed safe pin");
            mapClosedByTeleport=true;controls.closeMap();
            Gdx.app.log("QatarTeleport","TELEPORT mode="+physics.mode()+" requestedX="+destination.requestedX()+" requestedZ="+destination.requestedZ()+" x="+destination.x()+" z="+destination.z()+" surface="+destination.surfaceHeight()+" water="+(destination.water()?"yes":"no")+" adjustment="+destination.adjustmentMetres()+" basis="+destination.basis());
        }catch(Throwable error){
            hud.setMapStatus("Teleport unavailable: "+errorSummary(error));
            Gdx.app.error("QatarTeleport","TELEPORT_FAILED",error);
        }
    }

    private static String errorSummary(Throwable error){
        if(error==null)return "UnknownFailure(no throwable)";String message=error.getMessage();
        return error.getClass().getSimpleName()+((message==null||message.trim().isEmpty())?"(no message)":": "+message);
    }

    @Override public void resize(int width,int height){renderer.camera().viewportWidth=width;renderer.camera().viewportHeight=height;renderer.camera().update();renderer.resetCamera();}
    @Override public void pause(){physics.setControls(0,0,0,false);Gdx.graphics.setContinuousRendering(false);Gdx.app.log("QatarLifecycle","PAUSED mode="+physics.mode()+" rendering=suspended");}
    @Override public void resume(){Gdx.graphics.setContinuousRendering(true);Gdx.graphics.requestRendering();Gdx.input.setInputProcessor(controls);applyProfile(ThermalGovernor.profileForAndroidStatus(thermalStatus.getAsInt()));renderer.resetCamera();Gdx.app.log("QatarLifecycle","RESUMED mode="+physics.mode()+" rendering=active");}
    @Override public void dispose(){if(disposed)return;disposed=true;controls.closeMap();Gdx.input.setInputProcessor(null);detailStreamer.dispose();streamer.dispose();hud.dispose();renderer.dispose();physics.dispose();}
}
