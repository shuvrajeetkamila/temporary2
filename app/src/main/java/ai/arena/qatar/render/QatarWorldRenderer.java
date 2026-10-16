package ai.arena.qatar.render;

import ai.arena.qatar.environment.QatarEnvironment;
import ai.arena.qatar.performance.ThermalGovernor;
import ai.arena.qatar.physics.PhysicsWorld;
import ai.arena.qatar.world.QatarDetailPack;
import ai.arena.qatar.world.QatarDetailStreamer;
import ai.arena.qatar.world.QatarPack;
import ai.arena.qatar.world.WorldStreamer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ShortArray;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Qatar-specific mobile 3D renderer. All geometry is generated, never imagery. */
public final class QatarWorldRenderer implements WorldStreamer.Listener, QatarDetailStreamer.Listener, Disposable {
    @FunctionalInterface public interface MapRoadVisitor { void road(double x1,double z1,double x2,double z2,int roadClass,float width); }
    private static final long ATTRS = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates;
    private final QatarPack pack;
    private final int detailCellMetres;
    private final PhysicsWorld physics;
    private final QatarEnvironment climate;
    private final PerspectiveCamera camera;
    private final ModelBatch batch = new ModelBatch();
    private final ModelBuilder builder = new ModelBuilder();
    private final Environment environment = new Environment();
    private final ColorAttribute ambientLight=new ColorAttribute(ColorAttribute.AmbientLight,.42f,.40f,.37f,1);
    private final DirectionalLight sun=new DirectionalLight().set(1.0f,.91f,.74f,-.42f,-.82f,-.32f);
    private final DirectionalLight skyFill=new DirectionalLight().set(.32f,.38f,.42f,.58f,-.34f,.74f);
    private final Map<Long, RenderChunk> chunks = new HashMap<>();
    private final Map<Long, QatarPack.WorldChunk> nationalData = new HashMap<>();
    private final Map<Long, RenderChunk> detailCells = new HashMap<>();
    private final Map<Long, QatarDetailPack.DetailChunk> detailData = new HashMap<>();
    private final EarClippingTriangulator footprintTriangulator = new EarClippingTriangulator();
    private final AmbientWorldActivity activity;
    private final QatarLandmarkLayer landmarks;
    private final Texture sandTexture=createSurfaceTexture(0x51f15e,0);
    private final Texture gravelTexture=createSurfaceTexture(0x34a29b,1);
    private final Texture saltTexture=createSurfaceTexture(0x72c41d,2);
    private final Texture urbanTexture=createSurfaceTexture(0x19be73,3);
    private final Texture vegetationTexture=createSurfaceTexture(0x6d231a,4);
    private final Texture stoneFacadeTexture=createFacadeTexture(false);
    private final Texture glassFacadeTexture=createFacadeTexture(true);
    private final Map<Integer, Material> terrainMaterials = new HashMap<>();
    private final Material shoulderMaterial=diffuse(.62f,.59f,.53f,1);
    private final Material roadMaterial=new Material(ColorAttribute.createDiffuse(new Color(.235f,.245f,.25f,1)),ColorAttribute.createSpecular(new Color(.32f,.33f,.34f,1)),FloatAttribute.createShininess(18));
    private final Material pathMaterial=diffuse(.49f,.43f,.34f,1);
    private final Material railMaterial=new Material(ColorAttribute.createDiffuse(new Color(.28f,.28f,.27f,1)),ColorAttribute.createSpecular(new Color(.70f,.68f,.62f,1)),FloatAttribute.createShininess(48));
    private final Material runwayMaterial=diffuse(.14f,.145f,.15f,1);
    private final Material taxiwayMaterial=diffuse(.22f,.225f,.23f,1);
    private final Material markingMaterial=diffuse(.96f,.94f,.84f,1);
    private final Material edgeMarkingMaterial=diffuse(.98f,.98f,.94f,1);
    private final Material reclamationMaterial=diffuse(.67f,.63f,.55f,1);
    private final Material concreteBarrierMaterial=diffuse(.78f,.77f,.72f,1);
    private final Material tunnelMouthMaterial=diffuse(.045f,.048f,.050f,1);
    private final Material streetFurnitureMaterial=diffuse(.20f,.22f,.23f,1);
    private final Material signMaterial=diffuse(.18f,.38f,.42f,1);
    private final Material lampMaterial=new Material(ColorAttribute.createDiffuse(new Color(.95f,.86f,.55f,1)),ColorAttribute.createEmissive(new Color(.32f,.24f,.08f,1)));
    private final Material roofMaterial=diffuse(.74f,.70f,.62f,1);
    private final Material roofDetailMaterial=diffuse(.61f,.61f,.58f,1);
    private final Material facadeTrimMaterial=diffuse(.94f,.89f,.78f,1);
    private final Material windowMaterial=new Material(ColorAttribute.createDiffuse(new Color(.34f,.57f,.63f,1)),ColorAttribute.createSpecular(new Color(.82f,.90f,.92f,1)),ColorAttribute.createEmissive(new Color(.025f,.045f,.05f,1)),FloatAttribute.createShininess(84));
    private final Material limestoneMaterial=facade(.89f,.85f,.77f,stoneFacadeTexture);
    private final Material sandstoneMaterial=facade(.82f,.73f,.62f,stoneFacadeTexture);
    private final Material plasterMaterial=facade(.96f,.94f,.88f,stoneFacadeTexture);
    private final Material towerMaterial=new Material(ColorAttribute.createDiffuse(new Color(.63f,.75f,.77f,1)),TextureAttribute.createDiffuse(glassFacadeTexture),ColorAttribute.createSpecular(new Color(.78f,.84f,.85f,1)),FloatAttribute.createShininess(64));
    private final Material industrialMaterial=facade(.70f,.71f,.68f,stoneFacadeTexture);
    private final Material farmMaterial=diffuse(.62f,.65f,.47f,1);
    private final Material civicMaterial=facade(.92f,.88f,.79f,stoneFacadeTexture);
    private final Material waterMaterial=new Material(ColorAttribute.createDiffuse(new Color(.035f,.30f,.42f,1)),ColorAttribute.createSpecular(new Color(.72f,.88f,.91f,1)),FloatAttribute.createShininess(96));
    private final Material shallowWaterMaterial=new Material(ColorAttribute.createDiffuse(new Color(.075f,.53f,.57f,1)),ColorAttribute.createSpecular(new Color(.72f,.92f,.91f,1)),FloatAttribute.createShininess(80));
    private final Material beachMaterial=diffuse(.91f,.81f,.62f,1);
    private Model focusModel;
    private ModelInstance focusInstance;
    private final Map<String,Node> focusNodes=new HashMap<>();
    private PhysicsWorld.Mode renderedMode;
    private float focusAnimationTime,focusTransition=1f;
    private String fatalStreamingError="";
    private final CameraMotionFilter cameraFilter=new CameraMotionFilter();
    private final Vector3 renderFocus=new Vector3();
    private final Vector3 desiredCamera=new Vector3();
    private final Vector3 desiredTarget=new Vector3();
    private final Matrix4 renderFocusTransform=new Matrix4();
    private final MeshPartBuilder.VertexInfo facadeA=new MeshPartBuilder.VertexInfo(),facadeB=new MeshPartBuilder.VertexInfo(),facadeC=new MeshPartBuilder.VertexInfo(),facadeD=new MeshPartBuilder.VertexInfo();
    private float lastCameraDistance;
    private boolean loggedRealDetail;
    private boolean loggedVisualPipeline;
    private boolean loggedCoastalSupport;
    private long detailMapRevision;
    private ThermalGovernor.Profile performanceProfile=ThermalGovernor.Profile.NORMAL;

    public QatarWorldRenderer(QatarPack pack,QatarDetailPack detailPack,PhysicsWorld physics,QatarEnvironment climate) {
        this.pack=pack;this.detailCellMetres=detailPack.cellMetres;this.physics=physics;this.climate=climate;
        camera=new PerspectiveCamera(64,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
        camera.near=.15f; camera.far=14_000f;
        environment.set(ambientLight);environment.add(sun);environment.add(skyFill);
        initialiseMaterials();activity=new AmbientWorldActivity(physics,this);landmarks=new QatarLandmarkLayer(pack,physics,this);
        rebuildFocusModel();
    }

    private void initialiseMaterials() {
        terrainMaterials.put(0,textured(.08f,.39f,.48f,saltTexture));
        terrainMaterials.put(1,textured(.73f,.70f,.65f,urbanTexture));
        terrainMaterials.put(2,textured(.82f,.79f,.72f,gravelTexture));
        terrainMaterials.put(3,textured(.87f,.75f,.56f,sandTexture));
        terrainMaterials.put(4,textured(.91f,.90f,.83f,saltTexture));
        terrainMaterials.put(5,textured(.43f,.54f,.37f,vegetationTexture));
        terrainMaterials.put(6,textured(.58f,.63f,.41f,vegetationTexture));
        terrainMaterials.put(7,textured(.64f,.63f,.59f,urbanTexture));
        terrainMaterials.put(8,textured(.74f,.71f,.66f,gravelTexture));
        terrainMaterials.put(9,textured(.92f,.84f,.67f,sandTexture));
    }

    public void render(float delta) {
        if (renderedMode != physics.mode()) rebuildFocusModel();
        updateTransforms();updateFocusAnimation(delta);
        updateCamera(delta);updateAtmosphere();
        Gdx.gl.glViewport(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glClearColor(climate.skyRed(),climate.skyGreen(),climate.skyBlue(),1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
        batch.begin(camera);
        for(RenderChunk chunk:chunks.values()){batch.render(chunk.instance,environment);if(chunk.waterInstance!=null)batch.render(chunk.waterInstance,environment);}
        for (RenderChunk detail:detailCells.values()) batch.render(detail.instance,environment);
        activity.render(batch,environment,detailData,detailMapRevision,performanceProfile,delta,climate.seaLevelMetres());landmarks.render(batch,environment,performanceProfile);
        focusInstance.transform.set(physics.renderTransform(renderFocusTransform));
        float transitionScale=.72f+.28f*smoothStep(focusTransition);focusInstance.transform.scale(transitionScale,transitionScale,transitionScale);
        focusInstance.calculateTransforms();batch.render(focusInstance,environment);
        batch.end();
    }

    private void updateAtmosphere(){
        float ambient=climate.ambient();ambientLight.color.set(ambient*.62f,ambient*.60f,ambient*.56f,1);
        sun.color.set(climate.sunRed()*.78f,climate.sunGreen()*.78f,climate.sunBlue()*.78f,1);
        skyFill.color.set(.18f*ambient,.21f*ambient,.23f*ambient,1);
        float azimuth=(climate.solarHour()-6f)/12f*(float)Math.PI;sun.direction.set(-(float)Math.cos(azimuth)*.48f,-Math.max(.28f,climate.daylight()),-.34f).nor();
        skyFill.direction.set(-sun.direction.x,-.38f,-sun.direction.z).nor();
    }

    private void updateTransforms() {
        for (RenderChunk value:chunks.values()) position(value);
        for (RenderChunk value:detailCells.values()) position(value);
    }

    private void position(RenderChunk value){
        float x=(float)(value.x-physics.originX());
        float z=(float)(value.z-physics.originZ());
        value.instance.transform.setToTranslation(x,0,z);
        if(value.waterInstance!=null)value.waterInstance.transform.setToTranslation(x,climate.seaLevelMetres(),z);
    }

    private void updateCamera(float delta) {
        Vector3 focus=physics.renderPosition(renderFocus);
        float heading=physics.renderHeadingRadians();
        float distance,height,cameraResponse,targetResponse;
        switch(physics.mode()) {
            case WALK -> {distance=8;height=3.7f;cameraResponse=7.2f;targetResponse=10f;camera.far=6500*performanceProfile.viewDistanceScale;}
            case CAR -> {distance=12;height=5.1f;cameraResponse=6.5f;targetResponse=9f;camera.far=9000*performanceProfile.viewDistanceScale;}
            case BOAT -> {boolean afloat=physics.hasWaterSupport();distance=afloat?18:12;height=afloat?7:6;cameraResponse=5.8f;targetResponse=8f;camera.far=11000*performanceProfile.viewDistanceScale;}
            case HELICOPTER -> {boolean airborne=physics.heightAboveGround()>6;distance=airborne?27:12;height=airborne?11:7;cameraResponse=5.2f;targetResponse=7f;camera.far=14000*performanceProfile.viewDistanceScale;}
            case AEROPLANE -> {boolean airborne=physics.heightAboveGround()>6;distance=airborne?38:12;height=airborne?13:7;cameraResponse=4.8f;targetResponse=6.5f;camera.far=16000*performanceProfile.viewDistanceScale;}
            default -> throw new IllegalStateException();
        }
        desiredCamera.set(focus.x-(float)Math.sin(heading)*distance,focus.y+height,focus.z-(float)Math.cos(heading)*distance);
        float wantedGround=terrainHeight(physics.originX()+desiredCamera.x,physics.originZ()+desiredCamera.z);
        desiredCamera.y=Math.max(desiredCamera.y,wantedGround+1.5f);
        float focusGround=terrainHeight(physics.originX()+focus.x,physics.originZ()+focus.z);
        desiredTarget.set(focus.x,Math.max(focus.y+1.2f,focusGround+1.25f),focus.z);
        cameraFilter.update(desiredCamera,desiredTarget,delta,cameraResponse,targetResponse);
        Vector3 target=cameraFilter.target();
        camera.position.set(resolveCamera(cameraFilter.camera(),target));lastCameraDistance=camera.position.dst(target);
        camera.up.set(Vector3.Y);
        camera.lookAt(target);
        camera.update();
    }

    private Vector3 resolveCamera(Vector3 requested,Vector3 target){
        Vector3 candidate=new Vector3(),safe=new Vector3(target).lerp(requested,.04f).add(0,.5f,0);
        for(int step=1;step<=12;step++){
            candidate.set(target).lerp(requested,step/12f);
            double globalX=physics.originX()+candidate.x,globalZ=physics.originZ()+candidate.z;
            if(cameraBlocked(globalX,candidate.y,globalZ))return ensureCameraSeparation(safe,requested,target);
            safe.set(candidate);
        }
        return ensureCameraSeparation(safe,requested,target);
    }

    private Vector3 ensureCameraSeparation(Vector3 current,Vector3 requested,Vector3 target){
        if(current.dst2(target)>=36f)return current;float base=(float)Math.atan2(requested.x-target.x,requested.z-target.z);
        for(float distance:new float[]{10,8,12})for(float turn:new float[]{0,.7f,-.7f,1.4f,-1.4f,3.14f}){
            Vector3 option=new Vector3(target.x+(float)Math.sin(base+turn)*distance,target.y+Math.max(4f,distance*.6f),target.z+(float)Math.cos(base+turn)*distance);
            double globalX=physics.originX()+option.x,globalZ=physics.originZ()+option.z;option.y=Math.max(option.y,terrainHeight(globalX,globalZ)+2f);
            if(!cameraBlocked(globalX,option.y,globalZ))return option;
        }
        // Dense souq metadata can leave no low-angle candidate. Rise above nearby façades rather than returning a known blocked point.
        float emergencyHeight=switch(physics.mode()){case WALK,CAR->14f;case BOAT->17f;case HELICOPTER,AEROPLANE->22f;};
        for(float turn:new float[]{1.57f,-1.57f,3.14f,0}){
            Vector3 option=new Vector3(target.x+(float)Math.sin(base+turn)*8f,target.y+emergencyHeight,target.z+(float)Math.cos(base+turn)*8f);
            double globalX=physics.originX()+option.x,globalZ=physics.originZ()+option.z;option.y=Math.max(option.y,terrainHeight(globalX,globalZ)+emergencyHeight);
            if(!cameraBlocked(globalX,option.y,globalZ))return option;
        }
        return new Vector3(target).add(0,emergencyHeight,8f);
    }

    private boolean cameraBlocked(double globalX,float localY,double globalZ){
        float ground=terrainHeight(globalX,globalZ);if(localY<ground+.55f)return true;
        int cameraCellX=(int)Math.floor(globalX/detailCellMetres),cameraCellZ=(int)Math.floor(globalZ/detailCellMetres);
        for(QatarDetailPack.DetailChunk chunk:detailData.values()){
            if(Math.abs(chunk.entry().x()-cameraCellX)>1||Math.abs(chunk.entry().y()-cameraCellZ)>1)continue;
            double originX=chunk.entry().x()*(double)detailCellMetres,originZ=chunk.entry().y()*(double)detailCellMetres;
            for(QatarPack.Building building:chunk.buildings()){
                double centreX=originX+building.x(),centreZ=originZ+building.z();boolean inside;
                if(building.hasSourceFootprint())inside=insideOrNearFootprint(building.footprint(),globalX-originX,globalZ-originZ,.45);
                else{double dx=globalX-centreX,dz=globalZ-centreZ,cosine=Math.cos(building.yaw()),sine=Math.sin(building.yaw()),along=dx*cosine+dz*sine,across=-dx*sine+dz*cosine;inside=Math.abs(along)<=building.width()/2+.45&&Math.abs(across)<=building.depth()/2+.45;}
                if(!inside)continue;float base=terrainHeight(centreX,centreZ);if(localY>=base-.4f&&localY<=base+building.height()+.8f)return true;
            }
        }
        return false;
    }

    private static boolean insideOrNearFootprint(float[] polygon,double x,double z,double clearance){
        int points=polygon.length/2;if(points<3)return false;boolean inside=false;double clearanceSquared=clearance*clearance;
        for(int i=0,j=points-1;i<points;j=i++){
            double xi=polygon[i*2],zi=polygon[i*2+1],xj=polygon[j*2],zj=polygon[j*2+1],dx=xi-xj,dz=zi-zj,lengthSquared=dx*dx+dz*dz;
            if(lengthSquared>1e-6){double t=Math.max(0,Math.min(1,((x-xj)*dx+(z-zj)*dz)/lengthSquared)),nearestX=xj+t*dx,nearestZ=zj+t*dz;if((x-nearestX)*(x-nearestX)+(z-nearestZ)*(z-nearestZ)<=clearanceSquared)return true;}
            if((zi>z)!=(zj>z)&&x<(xj-xi)*(z-zi)/(zj-zi)+xi)inside=!inside;
        }
        return inside;
    }

    @Override public void activate(QatarPack.WorldChunk chunk) {
        ChunkModels models=buildChunk(chunk);long chunkKey=key(chunk.entry().x(),chunk.entry().y());
        nationalData.put(chunkKey,chunk);
        try{physics.activateChunk(chunk,pack.chunkMetres);}catch(RuntimeException|Error error){nationalData.remove(chunkKey);models.terrain.dispose();if(models.water!=null)models.water.dispose();throw error;}
        chunks.put(chunkKey,new RenderChunk(chunk.entry().x()*pack.chunkMetres,chunk.entry().y()*pack.chunkMetres,models.terrain,new ModelInstance(models.terrain),models.water,models.water==null?null:new ModelInstance(models.water)));
        for(QatarDetailPack.DetailChunk detail:detailData.values())if(parentNationalKey(detail)==chunkKey)rebuildDetail(detail);
    }

    @Override public void deactivate(QatarPack.WorldChunk chunk) {
        physics.deactivateChunk(chunk);long chunkKey=key(chunk.entry().x(),chunk.entry().y());nationalData.remove(chunkKey);
        RenderChunk removed=chunks.remove(chunkKey);
        if(removed!=null){removed.model.dispose();if(removed.waterModel!=null)removed.waterModel.dispose();}
    }

    @Override public void requiredChunkFailed(QatarPack.ChunkEntry entry,Throwable error) {
        fatalStreamingError="Required chunk "+entry.x()+","+entry.y()+" failed: "+error.getMessage();
    }

    private ChunkModels buildChunk(QatarPack.WorldChunk chunk) {
        float size=pack.chunkMetres;builder.begin();
        MeshPartBuilder land=builder.part("terrain-"+chunk.entry().biome(),GL20.GL_TRIANGLES,ATTRS,terrainMaterials.get(chunk.entry().biome()));
        MeshPartBuilder beaches=builder.part("coastal-beach-bands",GL20.GL_TRIANGLES,ATTRS,beachMaterial);float sub=size/16f;
        if(!chunk.entry().hasLand())land.box(size/2,-80,size/2,size,.1f,size);
        for(int row=0;row<16;row++){
            int bits=chunk.landRows()[row];
            for(int col=0;col<16;col++){
                if((bits&(1<<col))==0)continue;float x0=col*sub,x1=(col+1)*sub,z0=row*sub,z1=(row+1)*sub;
                float h00=pack.terrainHeight(chunk,x0,z0),h10=pack.terrainHeight(chunk,x1,z0),h11=pack.terrainHeight(chunk,x1,z1),h01=pack.terrainHeight(chunk,x0,z1);
                terrainTriangle(land,x0,h00,z0,x0,h01,z1,x1,h11,z1);terrainTriangle(land,x1,h11,z1,x1,h10,z0,x0,h00,z0);
                addBeachBands(beaches,chunk,row,col,x0,z0,sub);
            }
        }
        Model terrain=builder.end(),water=null;
        if(!chunk.entry().hasLand()||chunk.entry().isCoast()){
            builder.begin();MeshPartBuilder surface=builder.part("gulf-water-mask-aligned",GL20.GL_TRIANGLES,ATTRS,waterMaterial);MeshPartBuilder shallow=builder.part("gulf-shallow-water-bands",GL20.GL_TRIANGLES,ATTRS,shallowWaterMaterial);float waterSub=size/16f;
            double originX=chunk.entry().x()*(double)pack.chunkMetres,originZ=chunk.entry().y()*(double)pack.chunkMetres;
            // Coast water must never cover cells declared as land. The previous
            // full-chunk plane could visually flood low reclaimed runways and
            // coastal buildings even though their land collision was present.
            for(int row=0;row<16;row++)for(int col=0;col<16;col++){
                if((chunk.landRows()[row]&(1<<col))!=0)continue;
                float x0=col*waterSub,x1=(col+1)*waterSub,z0=row*waterSub,z1=(row+1)*waterSub;
                float h00=staticWave(originX+x0,originZ+z0),h10=staticWave(originX+x1,originZ+z0),h11=staticWave(originX+x1,originZ+z1),h01=staticWave(originX+x0,originZ+z1);
                terrainTriangle(surface,x0,h00,z0,x0,h01,z1,x1,h11,z1);terrainTriangle(surface,x1,h11,z1,x1,h10,z0,x0,h00,z0);
                addShallowWaterBands(shallow,chunk,row,col,x0,z0,waterSub,originX,originZ);
            }
            water=builder.end();
        }
        return new ChunkModels(terrain,water);
    }

    private static float staticWave(double x,double z){return .055f*(float)Math.sin(x*.012+z*.005)+.025f*(float)Math.sin(z*.021-x*.004);}

    private void addBeachBands(MeshPartBuilder part,QatarPack.WorldChunk chunk,int row,int col,float x0,float z0,float size){
        float band=Math.min(42f,size*.14f),x1=x0+size,z1=z0+size;
        if(isWaterCell(chunk,row-1,col))terrainPatch(part,chunk,x0,z0,x1,z0+band,.035f);
        if(isWaterCell(chunk,row+1,col))terrainPatch(part,chunk,x0,z1-band,x1,z1,.035f);
        if(isWaterCell(chunk,row,col-1))terrainPatch(part,chunk,x0,z0,x0+band,z1,.037f);
        if(isWaterCell(chunk,row,col+1))terrainPatch(part,chunk,x1-band,z0,x1,z1,.037f);
    }

    private void terrainPatch(MeshPartBuilder part,QatarPack.WorldChunk chunk,float x0,float z0,float x1,float z1,float offset){
        float h00=pack.terrainHeight(chunk,x0,z0)+offset,h10=pack.terrainHeight(chunk,x1,z0)+offset,h11=pack.terrainHeight(chunk,x1,z1)+offset,h01=pack.terrainHeight(chunk,x0,z1)+offset;
        terrainTriangle(part,x0,h00,z0,x0,h01,z1,x1,h11,z1);terrainTriangle(part,x1,h11,z1,x1,h10,z0,x0,h00,z0);
    }

    private void addShallowWaterBands(MeshPartBuilder part,QatarPack.WorldChunk chunk,int row,int col,float x0,float z0,float size,double originX,double originZ){
        float band=Math.min(95f,size*.24f),x1=x0+size,z1=z0+size;
        if(isLandCell(chunk,row-1,col))waterPatch(part,x0,z0,x1,z0+band,originX,originZ);
        if(isLandCell(chunk,row+1,col))waterPatch(part,x0,z1-band,x1,z1,originX,originZ);
        if(isLandCell(chunk,row,col-1))waterPatch(part,x0,z0,x0+band,z1,originX,originZ);
        if(isLandCell(chunk,row,col+1))waterPatch(part,x1-band,z0,x1,z1,originX,originZ);
    }

    private static boolean isLandCell(QatarPack.WorldChunk chunk,int row,int col){return row>=0&&row<16&&col>=0&&col<16&&(chunk.landRows()[row]&(1<<col))!=0;}
    private static boolean isWaterCell(QatarPack.WorldChunk chunk,int row,int col){return row>=0&&row<16&&col>=0&&col<16&&(chunk.landRows()[row]&(1<<col))==0;}
    private static void waterPatch(MeshPartBuilder part,float x0,float z0,float x1,float z1,double originX,double originZ){
        float h00=staticWave(originX+x0,originZ+z0)+.028f,h10=staticWave(originX+x1,originZ+z0)+.028f,h11=staticWave(originX+x1,originZ+z1)+.028f,h01=staticWave(originX+x0,originZ+z1)+.028f;
        terrainTriangle(part,x0,h00,z0,x0,h01,z1,x1,h11,z1);terrainTriangle(part,x1,h11,z1,x1,h10,z0,x0,h00,z0);
    }


    @Override public void activateDetail(QatarDetailPack.DetailChunk chunk){
        detailData.put(key(chunk.entry().x(),chunk.entry().y()),chunk);detailMapRevision++;rebuildDetail(chunk);
        if(!loggedRealDetail){loggedRealDetail=true;Gdx.app.log("QatarStreaming","OSM_READY cell="+chunk.entry().x()+","+chunk.entry().y()+" roads="+chunk.roads().length+" buildings="+chunk.buildings().length);}
    }

    private void rebuildDetail(QatarDetailPack.DetailChunk chunk){
        Model model=buildDetailCell(chunk);long detailKey=key(chunk.entry().x(),chunk.entry().y());
        RenderChunk old=detailCells.put(detailKey,new RenderChunk(chunk.entry().x()*(double)detailCellMetres,chunk.entry().y()*(double)detailCellMetres,model,new ModelInstance(model),null,null));
        if(old!=null)old.model.dispose();
        physics.registerDetailChunk(chunk,detailCellMetres,nationalData.get(parentNationalKey(chunk)),pack);
    }

    private long parentNationalKey(QatarDetailPack.DetailChunk chunk){
        double x=chunk.entry().x()*(double)detailCellMetres,z=chunk.entry().y()*(double)detailCellMetres;
        return key(pack.chunkX(x),pack.chunkY(z));
    }

    @Override public void deactivateDetail(QatarDetailPack.DetailChunk chunk){
        physics.unregisterDetailChunk(chunk);long detailKey=key(chunk.entry().x(),chunk.entry().y());detailData.remove(detailKey);detailMapRevision++;
        RenderChunk removed=detailCells.remove(detailKey);
        if(removed!=null)removed.model.dispose();
    }

    @Override public void detailCellFailed(QatarDetailPack.DetailEntry entry,Throwable error){
        fatalStreamingError="OSM detail cell "+entry.x()+","+entry.y()+" failed: "+error.getMessage();
    }

    private Model buildDetailCell(QatarDetailPack.DetailChunk chunk){
        builder.begin();
        double cellOriginX=chunk.entry().x()*(double)detailCellMetres,cellOriginZ=chunk.entry().y()*(double)detailCellMetres;
        int renderedJoints=0,renderedMarkingStrips=0,roofDetails=0,coastalPads=0,bridgeBarrierPieces=0,tunnelPortals=0,streetlights=0,directionSigns=0,buildingFoundations=0;
        if(chunk.roads().length>0){
            MeshPartBuilder supports=builder.part("osm-dry-transport-support",GL20.GL_TRIANGLES,ATTRS,reclamationMaterial);
            MeshPartBuilder barriers=builder.part("osm-bridge-barriers",GL20.GL_TRIANGLES,ATTRS,concreteBarrierMaterial);
            MeshPartBuilder tunnelFrames=builder.part("osm-tunnel-portals",GL20.GL_TRIANGLES,ATTRS,concreteBarrierMaterial);
            MeshPartBuilder tunnelMouths=builder.part("osm-tunnel-mouths",GL20.GL_TRIANGLES,ATTRS,tunnelMouthMaterial);
            MeshPartBuilder furniture=builder.part("osm-streetlight-poles",GL20.GL_TRIANGLES,ATTRS,streetFurnitureMaterial);
            MeshPartBuilder lamps=builder.part("osm-streetlight-lamps",GL20.GL_TRIANGLES,ATTRS,lampMaterial);
            MeshPartBuilder signs=builder.part("osm-direction-signs",GL20.GL_TRIANGLES,ATTRS,signMaterial);
            MeshPartBuilder shoulders=builder.part("osm-road-shoulders",GL20.GL_TRIANGLES,ATTRS,shoulderMaterial);
            MeshPartBuilder roads=builder.part("osm-paved-roads",GL20.GL_TRIANGLES,ATTRS,roadMaterial);
            MeshPartBuilder paths=builder.part("osm-paths-tracks",GL20.GL_TRIANGLES,ATTRS,pathMaterial);
            MeshPartBuilder rails=builder.part("osm-rail",GL20.GL_TRIANGLES,ATTRS,railMaterial);
            MeshPartBuilder runways=builder.part("osm-runways",GL20.GL_TRIANGLES,ATTRS,runwayMaterial);
            MeshPartBuilder taxiways=builder.part("osm-taxiways",GL20.GL_TRIANGLES,ATTRS,taxiwayMaterial);
            Map<EndpointKey,RoadJoint> joints=collectRoadJoints(chunk.roads());
            for(int roadIndex=0;roadIndex<chunk.roads().length;roadIndex++){
                QatarPack.Road road=chunk.roads()[roadIndex];
                if(roadNeedsDrySupport(road,cellOriginX,cellOriginZ)){
                    float supportWidth=road.width()+(road.roadClass()==10?8f:road.roadClass()==11?4f:3f);
                    roadQuad(supports,road,cellOriginX,cellOriginZ,.025f,supportWidth);coastalPads++;
                }
                if(road.bridge()&&bridgeBarrierPieces<QatarVisualStyle.MAX_BRIDGE_BARRIER_PIECES_PER_CELL)
                    bridgeBarrierPieces+=addBridgeBarriers(barriers,road,cellOriginX,cellOriginZ,QatarVisualStyle.MAX_BRIDGE_BARRIER_PIECES_PER_CELL-bridgeBarrierPieces);
                if(road.tunnel()&&tunnelPortals+2<=QatarVisualStyle.MAX_TUNNEL_PORTALS_PER_CELL){addTunnelPortals(tunnelFrames,tunnelMouths,road,cellOriginX,cellOriginZ);tunnelPortals+=2;}
                if(streetlights<QatarVisualStyle.MAX_STREETLIGHTS_PER_CELL&&road.roadClass()<=3&&roadLength(road)>=42f&&Math.floorMod(roadIndex*31+chunk.entry().x()*7+chunk.entry().y(),5)==0){
                    addStreetlight(furniture,lamps,road,cellOriginX,cellOriginZ,streetlights);streetlights++;
                }
                if(QatarVisualStyle.roadNeedsShoulder(road))roadQuad(shoulders,road,cellOriginX,cellOriginZ,.105f,QatarVisualStyle.shoulderWidth(road));
                if(road.roadClass()==9){
                    roadQuad(paths,road,cellOriginX,cellOriginZ,.112f,road.width()+1.4f);
                    float railOffset=Math.min(.78f,road.width()*.27f);
                    roadLine(rails,road,cellOriginX,cellOriginZ,.148f,.14f,-railOffset);
                    roadLine(rails,road,cellOriginX,cellOriginZ,.148f,.14f,railOffset);
                    continue;
                }
                MeshPartBuilder target=switch(road.roadClass()){case 7,8->paths;case 10->runways;case 11->taxiways;default->roads;};
                roadQuad(target,road,cellOriginX,cellOriginZ,.13f,road.width());
            }
            for(int pass=0;pass<2&&renderedJoints<QatarVisualStyle.MAX_ROAD_JOINTS_PER_CELL;pass++){
                for(RoadJoint joint:joints.values()){
                    if((pass==0)!=(joint.count>1)||joint.roadClass==9)continue;
                    if(joint.roadClass<=4)roadDisc(shoulders,joint.x,joint.z,cellOriginX,cellOriginZ,.105f,(joint.width+(joint.roadClass<=2?3f:2.2f))*.5f,joint.roadClass,joint.flags);
                    MeshPartBuilder target=switch(joint.roadClass){case 7,8->paths;case 10->runways;case 11->taxiways;default->roads;};
                    roadDisc(target,joint.x,joint.z,cellOriginX,cellOriginZ,.13f,joint.width*.5f,joint.roadClass,joint.flags);
                    renderedJoints++;
                    if(joint.count>1&&joint.roadClass<=4&&directionSigns<QatarVisualStyle.MAX_DIRECTION_SIGNS_PER_CELL&&Math.floorMod(Math.round(joint.x+joint.z),3)==0){addDirectionSign(signs,furniture,joint,cellOriginX,cellOriginZ);directionSigns++;}
                    if(renderedJoints>=QatarVisualStyle.MAX_ROAD_JOINTS_PER_CELL)break;
                }
            }
            MeshPartBuilder marks=builder.part("osm-lane-centre-lines",GL20.GL_TRIANGLES,ATTRS,markingMaterial);
            MeshPartBuilder edgeMarks=builder.part("osm-road-edge-lines",GL20.GL_TRIANGLES,ATTRS,edgeMarkingMaterial);
            for(QatarPack.Road road:chunk.roads()){
                if(renderedMarkingStrips>=QatarVisualStyle.MAX_MARKING_STRIPS_PER_CELL)break;
                if(road.roadClass()<=4&&road.lanes()>=2){
                    int laneBands=Math.min(5,road.lanes());
                    for(int lane=1;lane<laneBands;lane++){
                        float lateral=-road.width()*.5f+road.width()*lane/laneBands;
                        renderedMarkingStrips+=roadDashes(marks,road,cellOriginX,cellOriginZ,.153f,QatarVisualStyle.centreMarkWidth(road),lateral,QatarVisualStyle.dashLength(road),QatarVisualStyle.dashGap(road),QatarVisualStyle.MAX_MARKING_STRIPS_PER_CELL-renderedMarkingStrips);
                    }
                }
                if(road.roadClass()<=2&&road.width()>=7){
                    float edge=Math.max(.5f,road.width()*.5f-.42f);
                    int edgePieces=roadPieceCount(road);
                    if(renderedMarkingStrips+edgePieces*2<=QatarVisualStyle.MAX_MARKING_STRIPS_PER_CELL){
                        renderedMarkingStrips+=roadLine(edgeMarks,road,cellOriginX,cellOriginZ,.154f,.14f,-edge);
                        renderedMarkingStrips+=roadLine(edgeMarks,road,cellOriginX,cellOriginZ,.154f,.14f,edge);
                    }
                }else if(road.roadClass()==10){
                    renderedMarkingStrips+=roadDashes(marks,road,cellOriginX,cellOriginZ,.154f,QatarVisualStyle.centreMarkWidth(road),0,QatarVisualStyle.dashLength(road),QatarVisualStyle.dashGap(road),QatarVisualStyle.MAX_MARKING_STRIPS_PER_CELL-renderedMarkingStrips);
                    float edge=Math.max(1f,road.width()*.5f-1.1f);
                    int edgePieces=roadPieceCount(road);
                    if(renderedMarkingStrips+edgePieces*2<=QatarVisualStyle.MAX_MARKING_STRIPS_PER_CELL){
                        renderedMarkingStrips+=roadLine(edgeMarks,road,cellOriginX,cellOriginZ,.154f,.34f,-edge);
                        renderedMarkingStrips+=roadLine(edgeMarks,road,cellOriginX,cellOriginZ,.154f,.34f,edge);
                    }
                }else if(road.roadClass()==11){
                    int linePieces=roadPieceCount(road);
                    if(renderedMarkingStrips+linePieces<=QatarVisualStyle.MAX_MARKING_STRIPS_PER_CELL)
                        renderedMarkingStrips+=roadLine(marks,road,cellOriginX,cellOriginZ,.154f,.20f,0);
                }
            }
        }
        MeshPartBuilder foundations=builder.part("osm-coastal-building-foundations",GL20.GL_TRIANGLES,ATTRS,reclamationMaterial);
        MeshPartBuilder limestone=builder.part("osm-limestone-facades",GL20.GL_TRIANGLES,ATTRS,limestoneMaterial);
        MeshPartBuilder sandstone=builder.part("osm-sandstone-facades",GL20.GL_TRIANGLES,ATTRS,sandstoneMaterial);
        MeshPartBuilder plaster=builder.part("osm-white-plaster-facades",GL20.GL_TRIANGLES,ATTRS,plasterMaterial);
        MeshPartBuilder towers=builder.part("osm-glass-towers",GL20.GL_TRIANGLES,ATTRS,towerMaterial);
        MeshPartBuilder industry=builder.part("osm-industry",GL20.GL_TRIANGLES,ATTRS,industrialMaterial);
        MeshPartBuilder farms=builder.part("osm-farms",GL20.GL_TRIANGLES,ATTRS,farmMaterial);
        MeshPartBuilder civic=builder.part("osm-civic",GL20.GL_TRIANGLES,ATTRS,civicMaterial);
        MeshPartBuilder roofs=builder.part("osm-roofs",GL20.GL_TRIANGLES,ATTRS,roofMaterial);
        MeshPartBuilder roofEquipment=builder.part("osm-rooftop-equipment",GL20.GL_TRIANGLES,ATTRS,roofDetailMaterial);
        MeshPartBuilder civicRoofDetails=builder.part("osm-mosque-roof-details",GL20.GL_TRIANGLES,ATTRS,civicMaterial);
        MeshPartBuilder facadeTrim=builder.part("osm-facade-trim",GL20.GL_TRIANGLES,ATTRS,facadeTrimMaterial);
        MeshPartBuilder windows=builder.part("osm-facade-windows",GL20.GL_TRIANGLES,ATTRS,windowMaterial);
        for(QatarPack.Building building:chunk.buildings()){
            MeshPartBuilder target=switch(QatarVisualStyle.facadePalette(building)){
                case QatarVisualStyle.FACADE_SANDSTONE->sandstone;
                case QatarVisualStyle.FACADE_WHITE_PLASTER->plaster;
                case QatarVisualStyle.FACADE_GLASS_TOWER->towers;
                case QatarVisualStyle.FACADE_INDUSTRIAL->industry;
                case QatarVisualStyle.FACADE_FARM->farms;
                case QatarVisualStyle.FACADE_CIVIC->civic;
                default->limestone;
            };
            double buildingGlobalX=cellOriginX+building.x(),buildingGlobalZ=cellOriginZ+building.z();
            float measuredBase=terrainHeight(buildingGlobalX,buildingGlobalZ);boolean landMask=nationalLandAt(buildingGlobalX,buildingGlobalZ);
            float baseHeight=CoastalFeaturePolicy.buildingBase(measuredBase,landMask);
            if(CoastalFeaturePolicy.needsBuildingFoundation(measuredBase,landMask)){
                addBuildingFoundation(foundations,building,baseHeight,measuredBase,landMask);buildingFoundations++;
            }
            if(building.hasSourceFootprint())roofDetails+=extrudeFootprint(target,roofs,windows,facadeTrim,roofEquipment,civicRoofDetails,building,baseHeight);
            else{
                target.setVertexTransform(new Matrix4().setToTranslation(building.x(),baseHeight+building.height()/2,building.z()).rotate(Vector3.Y,(float)Math.toDegrees(building.yaw())));
                target.box(0,0,0,building.width(),building.height(),building.depth());target.setVertexTransform(null);
                roofDetails+=addRoofDetails(roofEquipment,civicRoofDetails,building,baseHeight);
            }
        }
        if(!loggedVisualPipeline&&(chunk.roads().length>0||chunk.buildings().length>0)){
            loggedVisualPipeline=true;
            Gdx.app.log("QatarVisual","V09_WORLD_PIPELINE roadSegments="+chunk.roads().length+" joinedCaps="+renderedJoints+" markingStrips="+renderedMarkingStrips+" streetlights="+streetlights+" signs="+directionSigns+" bridgeBarriers="+bridgeBarrierPieces+" tunnelPortals="+tunnelPortals+" buildings="+chunk.buildings().length+" roofDetails="+roofDetails+" bounded=yes");
        }
        if(!loggedCoastalSupport&&(coastalPads>0||buildingFoundations>0)){
            loggedCoastalSupport=true;
            Gdx.app.log("QatarCoast","FEATURE_SUPPORT_READY cell="+chunk.entry().x()+","+chunk.entry().y()+" transportPads="+coastalPads+" buildingFoundations="+buildingFoundations+" highWater="+CoastalFeaturePolicy.DESIGN_HIGH_WATER_METRES+" dryLevel="+CoastalFeaturePolicy.MINIMUM_DRY_FEATURE_METRES+" waterMaskAligned=yes");
        }
        return builder.end();
    }

    private void addBuildingFoundation(MeshPartBuilder foundations,QatarPack.Building building,float baseHeight,float measuredBase,boolean landMask){
        float supportedBottom=landMask&&Float.isFinite(measuredBase)?Math.max(CoastalFeaturePolicy.FOUNDATION_BOTTOM_METRES,measuredBase):CoastalFeaturePolicy.FOUNDATION_BOTTOM_METRES;
        float bottom=Math.min(baseHeight-.08f,supportedBottom);
        if(!building.hasSourceFootprint()){
            float height=Math.max(.10f,baseHeight-bottom);
            foundations.setVertexTransform(new Matrix4().setToTranslation(building.x(),bottom+height*.5f,building.z()).rotate(Vector3.Y,(float)Math.toDegrees(building.yaw())));
            foundations.box(0,0,0,building.width()+1.2f,height,building.depth()+1.2f);foundations.setVertexTransform(null);return;
        }
        float[] polygon=building.footprint();ShortArray triangles=footprintTriangulator.computeTriangles(polygon);
        for(int i=0;i<triangles.size;i+=3){int ia=triangles.get(i)*2,ib=triangles.get(i+1)*2,ic=triangles.get(i+2)*2;
            terrainTriangle(foundations,polygon[ia],baseHeight-.025f,polygon[ia+1],polygon[ib],baseHeight-.025f,polygon[ib+1],polygon[ic],baseHeight-.025f,polygon[ic+1]);
        }
        int points=polygon.length/2;float signedArea=0;for(int i=0;i<points;i++){int j=(i+1)%points;signedArea+=polygon[i*2]*polygon[j*2+1]-polygon[j*2]*polygon[i*2+1];}
        boolean ccw=signedArea>0;
        for(int i=0;i<points;i++){int j=(i+1)%points;float ax=polygon[i*2],az=polygon[i*2+1],bx=polygon[j*2],bz=polygon[j*2+1],dx=bx-ax,dz=bz-az,length=(float)Math.hypot(dx,dz);if(length<.02f)continue;float direction=ccw?1:-1,nx=direction*dz/length,nz=-direction*dx/length;facadeRect(foundations,ax,bottom,az,bx,baseHeight,bz,nx,nz,ccw);}
    }

    private int extrudeFootprint(MeshPartBuilder part,MeshPartBuilder roofs,MeshPartBuilder windows,MeshPartBuilder trim,MeshPartBuilder roofEquipment,MeshPartBuilder civicRoofDetails,QatarPack.Building building,float baseHeight){
        float[] polygon=building.footprint();
        ShortArray triangles=footprintTriangulator.computeTriangles(polygon);
        float height=baseHeight+building.height();
        MeshPartBuilder.VertexInfo a=new MeshPartBuilder.VertexInfo(),b=new MeshPartBuilder.VertexInfo(),c=new MeshPartBuilder.VertexInfo();
        for(int i=0;i<triangles.size;i+=3){
            int ia=triangles.get(i)*2,ib=triangles.get(i+1)*2,ic=triangles.get(i+2)*2;
            a.setPos(polygon[ia],height,polygon[ia+1]).setNor(0,1,0).setUV(polygon[ia]/10f,polygon[ia+1]/10f);
            b.setPos(polygon[ib],height,polygon[ib+1]).setNor(0,1,0).setUV(polygon[ib]/10f,polygon[ib+1]/10f);
            c.setPos(polygon[ic],height,polygon[ic+1]).setNor(0,1,0).setUV(polygon[ic]/10f,polygon[ic+1]/10f);
            roofs.triangle(a,b,c);
        }
        int points=polygon.length/2,windowStride=Math.max(1,(points+23)/24),details=0;float signedArea=0;
        for(int i=0;i<points;i++){int j=(i+1)%points;signedArea+=polygon[i*2]*polygon[j*2+1]-polygon[j*2]*polygon[i*2+1];}
        boolean counterClockwise=signedArea>0;
        for(int i=0;i<points;i++){
            int j=(i+1)%points;float ax=polygon[i*2],az=polygon[i*2+1],bx=polygon[j*2],bz=polygon[j*2+1];
            float dx=bx-ax,dz=bz-az,length=(float)Math.hypot(dx,dz);if(length<.02f)continue;
            float direction=counterClockwise?1:-1,nx=direction*dz/length,nz=-direction*dx/length;
            facadeRect(part,ax,baseHeight,az,bx,height,bz,nx,nz,counterClockwise);
            if(QatarVisualStyle.facadeTrim(building)){
                float trimOffset=.045f,tx1=ax+nx*trimOffset,tz1=az+nz*trimOffset,tx2=bx+nx*trimOffset,tz2=bz+nz*trimOffset;
                facadeRect(trim,tx1,height-.42f,tz1,tx2,height-.12f,tz2,nx,nz,counterClockwise);
                float cornerLength=Math.min(.34f,length*.08f),cornerRatio=cornerLength/length;
                facadeRect(trim,tx1,baseHeight+.22f,tz1,tx1+dx*cornerRatio,height-.44f,tz1+dz*cornerRatio,nx,nz,counterClockwise);
            }
            if(QatarVisualStyle.parapet(building)){
                facadeRect(roofs,ax,height,az,bx,height+.42f,bz,nx,nz,counterClockwise);details++;
            }
            if(i%windowStride==0&&length>=4&&building.height()>=5&&building.archetype()!=5){
                float margin=Math.min(building.archetype()==1?.75f:1.2f,length*.18f),start=margin/length,end=1-start;
                float wx1=ax+dx*start,wz1=az+dz*start,wx2=ax+dx*end,wz2=az+dz*end;
                int totalFloors=Math.max(1,(int)((building.height()-1.5f)/3.2f)),displayed=Math.min(building.archetype()==1?12:8,totalFloors);
                for(int slot=0;slot<displayed;slot++){
                    int floor=displayed==1?0:slot*(totalFloors-1)/(displayed-1);float bottom=baseHeight+1.35f+floor*3.2f,top=Math.min(height-.35f,bottom+1.1f);if(top<=bottom)continue;
                    float offset=.035f;facadeRect(windows,wx1+nx*offset,bottom,wz1+nz*offset,wx2+nx*offset,top,wz2+nz*offset,nx,nz,counterClockwise);
                }
            }
        }
        return details+addRoofDetails(roofEquipment,civicRoofDetails,building,baseHeight);
    }

    private int addRoofDetails(MeshPartBuilder equipment,MeshPartBuilder civic,QatarPack.Building building,float baseHeight){
        float roof=baseHeight+building.height();int details=0;
        if(QatarVisualStyle.rooftopEquipment(building)){
            int hash=Long.hashCode(building.sourceId());float shiftX=((hash&15)-7.5f)/15f*Math.min(2f,building.width()*.12f);float shiftZ=(((hash>>>4)&15)-7.5f)/15f*Math.min(2f,building.depth()*.12f);
            float width=Math.max(1.2f,Math.min(3.8f,building.width()*.18f)),depth=Math.max(1f,Math.min(3.2f,building.depth()*.16f)),height=Math.max(.65f,Math.min(1.5f,building.height()*.055f));
            equipment.box(building.x()+shiftX,roof+height*.5f,building.z()+shiftZ,width,height,depth);details++;
            if(building.height()>=24&&building.width()>=12){
                equipment.box(building.x()-shiftX-width*.65f,roof+height*.42f,building.z()-shiftZ,Math.max(1f,width*.65f),height*.84f,Math.max(.9f,depth*.65f));details++;
            }
        }
        if(QatarVisualStyle.mosqueDetails(building)){
            float span=Math.min(building.width(),building.depth()),domeWidth=Math.max(2.2f,Math.min(8f,span*.42f)),domeHeight=domeWidth*.55f;
            civic.setVertexTransform(new Matrix4().setToTranslation(building.x(),roof+domeHeight*.18f,building.z()));
            civic.sphere(domeWidth,domeHeight,domeWidth,12,7);civic.setVertexTransform(null);details++;
            float minaretHeight=Math.max(5f,Math.min(14f,building.height()*.72f)),diameter=Math.max(.75f,Math.min(1.8f,span*.10f));
            float mx=building.x()+building.width()*.31f,mz=building.z()+building.depth()*.27f;
            civic.setVertexTransform(new Matrix4().setToTranslation(mx,roof+minaretHeight*.5f,mz));
            civic.cylinder(diameter,minaretHeight,diameter,10);civic.setVertexTransform(new Matrix4().setToTranslation(mx,roof+minaretHeight,mz));
            civic.sphere(diameter*1.18f,diameter*.8f,diameter*1.18f,10,5);civic.setVertexTransform(null);details+=2;
        }
        return details;
    }

    private void facadeRect(MeshPartBuilder part,float ax,float bottom,float az,float bx,float top,float bz,float nx,float nz,boolean counterClockwise){
        float u=Math.max(.1f,(float)Math.hypot(bx-ax,bz-az)/7.5f),v=Math.max(.1f,(top-bottom)/4.2f);
        if(counterClockwise){
            facadeA.setPos(bx,bottom,bz).setNor(nx,0,nz).setUV(u,0);facadeB.setPos(ax,bottom,az).setNor(nx,0,nz).setUV(0,0);
            facadeC.setPos(ax,top,az).setNor(nx,0,nz).setUV(0,v);facadeD.setPos(bx,top,bz).setNor(nx,0,nz).setUV(u,v);
        }else{
            facadeA.setPos(ax,bottom,az).setNor(nx,0,nz).setUV(0,0);facadeB.setPos(bx,bottom,bz).setNor(nx,0,nz).setUV(u,0);
            facadeC.setPos(bx,top,bz).setNor(nx,0,nz).setUV(u,v);facadeD.setPos(ax,top,az).setNor(nx,0,nz).setUV(0,v);
        }
        part.rect(facadeA,facadeB,facadeC,facadeD);
    }

    private static void terrainTriangle(MeshPartBuilder part,float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz){
        float ux=bx-ax,uy=by-ay,uz=bz-az,vx=cx-ax,vy=cy-ay,vz=cz-az;
        float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(length<.0001f){nx=0;ny=1;nz=0;}else{nx/=length;ny/=length;nz/=length;}
        MeshPartBuilder.VertexInfo a=new MeshPartBuilder.VertexInfo().setPos(ax,ay,az).setNor(nx,ny,nz).setUV(ax/100f,az/100f);
        MeshPartBuilder.VertexInfo b=new MeshPartBuilder.VertexInfo().setPos(bx,by,bz).setNor(nx,ny,nz).setUV(bx/100f,bz/100f);
        MeshPartBuilder.VertexInfo c=new MeshPartBuilder.VertexInfo().setPos(cx,cy,cz).setNor(nx,ny,nz).setUV(cx/100f,cz/100f);
        part.triangle(a,b,c);
    }

    private Map<EndpointKey,RoadJoint> collectRoadJoints(QatarPack.Road[] roads){
        Map<EndpointKey,RoadJoint> joints=new LinkedHashMap<>();
        for(QatarPack.Road road:roads){
            registerRoadJoint(joints,road.x1(),road.z1(),road);
            registerRoadJoint(joints,road.x2(),road.z2(),road);
        }
        return joints;
    }

    private static void registerRoadJoint(Map<EndpointKey,RoadJoint> joints,float x,float z,QatarPack.Road road){
        int group=road.roadClass()<=6?0:road.roadClass()<=8?1:road.roadClass()==9?2:road.roadClass()==10?3:4;
        EndpointKey key=new EndpointKey(Math.round(x*4),Math.round(z*4),group);
        RoadJoint joint=joints.get(key);
        if(joint==null)joints.put(key,new RoadJoint(x,z,road.width(),road.roadClass(),road.flags()));
        else{joint.count++;joint.width=Math.max(joint.width,road.width());joint.roadClass=Math.min(joint.roadClass,road.roadClass());joint.flags|=road.flags();}
    }

    private boolean roadNeedsDrySupport(QatarPack.Road road,double cellOriginX,double cellOriginZ){
        float x=(road.x1()+road.x2())*.5f,z=(road.z1()+road.z2())*.5f;
        double globalX=cellOriginX+x,globalZ=cellOriginZ+z;
        float measured=detailTerrainHeight(globalX,globalZ,cellOriginX,cellOriginZ);
        return CoastalFeaturePolicy.needsTransportPad(measured,nationalLandAt(globalX,globalZ),road.roadClass(),road.flags());
    }

    private static float roadLength(QatarPack.Road road){return (float)Math.hypot(road.x2()-road.x1(),road.z2()-road.z1());}

    private int addBridgeBarriers(MeshPartBuilder barriers,QatarPack.Road road,double cellOriginX,double cellOriginZ,int remaining){
        float dx=road.x2()-road.x1(),dz=road.z2()-road.z1(),length=(float)Math.hypot(dx,dz);if(length<1||remaining<2)return 0;
        int pieces=Math.min(roadPieceCount(length),remaining/2);float nx=-dz/length,nz=dx/length,lateral=road.width()*.5f+.32f;
        int built=0;
        for(int piece=0;piece<pieces;piece++){
            float a=piece/(float)pieces,b=(piece+1)/(float)pieces,m=(a+b)*.5f,pieceLength=length*(b-a)+.08f;
            float centreX=road.x1()+dx*m,centreZ=road.z1()+dz*m;
            float base=transportHeight(cellOriginX+centreX,cellOriginZ+centreZ,cellOriginX,cellOriginZ,road.roadClass(),road.flags())+.16f;
            float heading=(float)Math.toDegrees(Math.atan2(dx,dz));
            for(float side:new float[]{-1f,1f}){
                barriers.setVertexTransform(new Matrix4().setToTranslation(centreX+nx*lateral,base+.38f,centreZ+nz*lateral).rotate(Vector3.Y,heading));
                barriers.box(0,0,0,.24f,.76f,pieceLength);built++;
            }
        }
        barriers.setVertexTransform(null);return built;
    }

    private void addTunnelPortals(MeshPartBuilder frames,MeshPartBuilder mouths,QatarPack.Road road,double cellOriginX,double cellOriginZ){
        float dx=road.x2()-road.x1(),dz=road.z2()-road.z1(),length=(float)Math.hypot(dx,dz);if(length<1)return;float heading=(float)Math.toDegrees(Math.atan2(dx,dz));
        for(int endpoint=0;endpoint<2;endpoint++){
            float x=endpoint==0?road.x1():road.x2(),z=endpoint==0?road.z1():road.z2();
            float base=transportHeight(cellOriginX+x,cellOriginZ+z,cellOriginX,cellOriginZ,road.roadClass(),road.flags());float half=road.width()*.5f+.6f;
            Matrix4 transform=new Matrix4().setToTranslation(x,base,z).rotate(Vector3.Y,heading+(endpoint==0?0:180));
            frames.setVertexTransform(transform);frames.box(-half,1.65f,0,.55f,3.3f,.65f);frames.box(half,1.65f,0,.55f,3.3f,.65f);frames.box(0,3.25f,0,road.width()+1.75f,.55f,.70f);frames.setVertexTransform(null);
            mouths.setVertexTransform(transform);mouths.box(0,1.42f,.20f,road.width()+.65f,2.65f,.16f);mouths.setVertexTransform(null);
        }
    }

    private void addStreetlight(MeshPartBuilder poles,MeshPartBuilder lamps,QatarPack.Road road,double cellOriginX,double cellOriginZ,int index){
        float dx=road.x2()-road.x1(),dz=road.z2()-road.z1(),length=(float)Math.hypot(dx,dz);if(length<1)return;
        float nx=-dz/length,nz=dx/length,side=(index&1)==0?1f:-1f,lateral=road.width()*.5f+1.35f;
        float x=(road.x1()+road.x2())*.5f+nx*lateral*side,z=(road.z1()+road.z2())*.5f+nz*lateral*side;
        float base=transportHeight(cellOriginX+x,cellOriginZ+z,cellOriginX,cellOriginZ,road.roadClass(),road.flags());
        poles.setVertexTransform(new Matrix4().setToTranslation(x,base+3.25f,z));poles.cylinder(.16f,6.5f,.16f,7);poles.setVertexTransform(null);
        lamps.setVertexTransform(new Matrix4().setToTranslation(x,base+6.55f,z));lamps.box(0,0,0,.72f,.16f,.28f);lamps.setVertexTransform(null);
    }

    private void addDirectionSign(MeshPartBuilder signs,MeshPartBuilder poles,RoadJoint joint,double cellOriginX,double cellOriginZ){
        float base=transportHeight(cellOriginX+joint.x,cellOriginZ+joint.z,cellOriginX,cellOriginZ,joint.roadClass,joint.flags);
        float x=joint.x+joint.width*.5f+1.1f,z=joint.z;
        poles.setVertexTransform(new Matrix4().setToTranslation(x,base+1.45f,z));poles.cylinder(.13f,2.9f,.13f,7);poles.setVertexTransform(null);
        signs.setVertexTransform(new Matrix4().setToTranslation(x,base+2.7f,z));signs.box(0,0,0,1.7f,.72f,.12f);signs.setVertexTransform(null);
    }

    private void roadQuad(MeshPartBuilder part,QatarPack.Road road,double cellOriginX,double cellOriginZ,float offset,float width){
        stripQuad(part,road.x1(),road.z1(),road.x2(),road.z2(),cellOriginX,cellOriginZ,offset,width,road.roadClass(),road.flags());
    }

    private int roadLine(MeshPartBuilder part,QatarPack.Road road,double cellOriginX,double cellOriginZ,float offset,float width,float lateral){
        float dx=road.x2()-road.x1(),dz=road.z2()-road.z1(),length=(float)Math.hypot(dx,dz);if(length<.01f)return 0;
        float nx=-dz/length,nz=dx/length;
        return stripQuad(part,road.x1()+nx*lateral,road.z1()+nz*lateral,road.x2()+nx*lateral,road.z2()+nz*lateral,cellOriginX,cellOriginZ,offset,width,road.roadClass(),road.flags());
    }

    private int roadDashes(MeshPartBuilder part,QatarPack.Road road,double cellOriginX,double cellOriginZ,float offset,float width,float lateral,float dashLength,float gapLength,int remainingBudget){
        float dx=road.x2()-road.x1(),dz=road.z2()-road.z1(),length=(float)Math.hypot(dx,dz);if(length<.2f||remainingBudget<=0)return 0;
        float ux=dx/length,uz=dz/length,nx=-uz,nz=ux,cycle=dashLength+gapLength;
        int dashBudget=Math.min(QatarVisualStyle.MAX_DASHES_PER_SEGMENT,remainingBudget);
        int expected=Math.max(1,(int)Math.ceil(length/cycle));float scale=Math.max(1f,expected/(float)dashBudget);
        dashLength*=scale;gapLength*=scale;cycle=dashLength+gapLength;int dashes=0,strips=0;
        for(float cursor=gapLength*.35f;cursor<length&&dashes<dashBudget;cursor+=cycle){
            float end=Math.min(length,cursor+dashLength);if(end-cursor<.12f)break;
            int pieces=roadPieceCount(end-cursor);if(strips+pieces>remainingBudget)break;
            float x1=road.x1()+ux*cursor+nx*lateral,z1=road.z1()+uz*cursor+nz*lateral;
            float x2=road.x1()+ux*end+nx*lateral,z2=road.z1()+uz*end+nz*lateral;
            strips+=stripQuad(part,x1,z1,x2,z2,cellOriginX,cellOriginZ,offset,width,road.roadClass(),road.flags());dashes++;
        }
        return strips;
    }

    private void roadDisc(MeshPartBuilder part,float x,float z,double cellOriginX,double cellOriginZ,float offset,float radius,int roadClass,int flags){
        float centreHeight=transportHeight(cellOriginX+x,cellOriginZ+z,cellOriginX,cellOriginZ,roadClass,flags)+offset;
        for(int side=0;side<QatarVisualStyle.ROAD_JOINT_DIVISIONS;side++){
            double a=Math.PI*2*side/QatarVisualStyle.ROAD_JOINT_DIVISIONS,b=Math.PI*2*(side+1)/QatarVisualStyle.ROAD_JOINT_DIVISIONS;
            float ax=x+(float)Math.cos(a)*radius,az=z+(float)Math.sin(a)*radius,bx=x+(float)Math.cos(b)*radius,bz=z+(float)Math.sin(b)*radius;
            float ah=transportHeight(cellOriginX+ax,cellOriginZ+az,cellOriginX,cellOriginZ,roadClass,flags)+offset,bh=transportHeight(cellOriginX+bx,cellOriginZ+bz,cellOriginX,cellOriginZ,roadClass,flags)+offset;
            terrainTriangle(part,x,centreHeight,z,ax,ah,az,bx,bh,bz);
        }
    }

    private int stripQuad(MeshPartBuilder part,float x1,float z1,float x2,float z2,double cellOriginX,double cellOriginZ,float offset,float width,int roadClass,int flags){
        float dx=x2-x1,dz=z2-z1,length=(float)Math.hypot(dx,dz);if(length<.01f)return 0;
        int pieces=roadPieceCount(length);
        for(int piece=0;piece<pieces;piece++){
            float a=piece/(float)pieces,b=(piece+1)/(float)pieces;
            stripQuadPiece(part,x1+dx*a,z1+dz*a,x1+dx*b,z1+dz*b,cellOriginX,cellOriginZ,offset,width,roadClass,flags);
        }
        return pieces;
    }

    private static int roadPieceCount(QatarPack.Road road){return roadPieceCount((float)Math.hypot(road.x2()-road.x1(),road.z2()-road.z1()));}
    private static int roadPieceCount(float length){return Math.min(QatarVisualStyle.MAX_ROAD_SUBDIVISIONS,Math.max(1,(int)Math.ceil(length/QatarVisualStyle.ROAD_TERRAIN_STEP_METRES)));}

    private void stripQuadPiece(MeshPartBuilder part,float x1,float z1,float x2,float z2,double cellOriginX,double cellOriginZ,float offset,float width,int roadClass,int flags){
        float dx=x2-x1,dz=z2-z1,length=(float)Math.hypot(dx,dz);if(length<.01f)return;
        float px=-dz/length*width/2,pz=dx/length*width/2;
        float x1a=x1+px,z1a=z1+pz,x1b=x1-px,z1b=z1-pz;
        float x2b=x2-px,z2b=z2-pz,x2a=x2+px,z2a=z2+pz;
        float h1a=transportHeight(cellOriginX+x1a,cellOriginZ+z1a,cellOriginX,cellOriginZ,roadClass,flags)+offset,h1b=transportHeight(cellOriginX+x1b,cellOriginZ+z1b,cellOriginX,cellOriginZ,roadClass,flags)+offset;
        float h2b=transportHeight(cellOriginX+x2b,cellOriginZ+z2b,cellOriginX,cellOriginZ,roadClass,flags)+offset,h2a=transportHeight(cellOriginX+x2a,cellOriginZ+z2a,cellOriginX,cellOriginZ,roadClass,flags)+offset;
        terrainTriangle(part,x1a,h1a,z1a,x2a,h2a,z2a,x2b,h2b,z2b);
        terrainTriangle(part,x2b,h2b,z2b,x1b,h1b,z1b,x1a,h1a,z1a);
    }

    public float terrainHeightAt(double globalX,double globalZ){
        float result=terrainHeightOr(globalX,globalZ,Float.NaN);if(!Float.isFinite(result))return result;
        int cellX=(int)Math.floor(globalX/detailCellMetres),cellZ=(int)Math.floor(globalZ/detailCellMetres);
        for(QatarDetailPack.DetailChunk chunk:detailData.values()){
            if(Math.abs(chunk.entry().x()-cellX)>1||Math.abs(chunk.entry().y()-cellZ)>1)continue;
            double originX=chunk.entry().x()*(double)detailCellMetres,originZ=chunk.entry().y()*(double)detailCellMetres;
            for(QatarPack.Road road:chunk.roads()){
                if(!CoastalFeaturePolicy.isPermanentTransport(road.roadClass())||road.tunnel())continue;
                double distance=pointSegmentDistance(globalX,globalZ,originX+road.x1(),originZ+road.z1(),originX+road.x2(),originZ+road.z2());
                if(distance<=road.width()*.5+1.2)result=Math.max(result,CoastalFeaturePolicy.transportBase(result,nationalLandAt(globalX,globalZ),road.roadClass(),road.flags()));
            }
        }
        return result;
    }
    float rawTerrainHeightAt(double globalX,double globalZ){return terrainHeightOr(globalX,globalZ,0);}
    boolean landMaskAt(double globalX,double globalZ){return nationalLandAt(globalX,globalZ);}
    public boolean waterAt(double globalX,double globalZ){
        int chunkX=pack.chunkX(globalX),chunkY=pack.chunkY(globalZ);QatarPack.WorldChunk chunk=nationalData.get(key(chunkX,chunkY));if(chunk==null)return false;
        if(!chunk.entry().hasLand())return true;float sub=pack.chunkMetres/16f;int column=Math.max(0,Math.min(15,(int)((globalX-chunkX*(double)pack.chunkMetres)/sub)));int row=Math.max(0,Math.min(15,(int)((globalZ-chunkY*(double)pack.chunkMetres)/sub)));
        return (chunk.landRows()[row]&(1<<column))==0;
    }
    public void setPerformanceProfile(ThermalGovernor.Profile profile){performanceProfile=profile;}
    public float cameraDistance(){return lastCameraDistance;}
    public long detailMapRevision(){return detailMapRevision;}
    public void visitVisibleRoads(MapRoadVisitor visitor){
        for(QatarDetailPack.DetailChunk chunk:detailData.values()){
            double originX=chunk.entry().x()*(double)detailCellMetres,originZ=chunk.entry().y()*(double)detailCellMetres;
            for(QatarPack.Road road:chunk.roads())visitor.road(originX+road.x1(),originZ+road.z1(),originX+road.x2(),originZ+road.z2(),road.roadClass(),road.width());
        }
    }

    private float terrainHeight(double globalX,double globalZ){return terrainHeightOr(globalX,globalZ,0);}

    private float detailTerrainHeight(double globalX,double globalZ,double cellOriginX,double cellOriginZ){
        float measured=terrainHeightOr(globalX,globalZ,Float.NaN);if(!Float.isNaN(measured))return measured;
        double inset=.05,clampedX=Math.max(cellOriginX+inset,Math.min(cellOriginX+detailCellMetres-inset,globalX));
        double clampedZ=Math.max(cellOriginZ+inset,Math.min(cellOriginZ+detailCellMetres-inset,globalZ));
        measured=terrainHeightOr(clampedX,clampedZ,Float.NaN);
        if(!Float.isNaN(measured))return measured;
        return terrainHeightOr(cellOriginX+detailCellMetres*.5,cellOriginZ+detailCellMetres*.5,0);
    }

    private float transportHeight(double globalX,double globalZ,double cellOriginX,double cellOriginZ,int roadClass,int flags){
        float measured=detailTerrainHeight(globalX,globalZ,cellOriginX,cellOriginZ);
        return CoastalFeaturePolicy.transportBase(measured,nationalLandAt(globalX,globalZ),roadClass,flags);
    }

    private boolean nationalLandAt(double globalX,double globalZ){
        int chunkX=pack.chunkX(globalX),chunkY=pack.chunkY(globalZ);QatarPack.WorldChunk chunk=nationalData.get(key(chunkX,chunkY));
        if(chunk==null||!chunk.entry().hasLand())return false;
        float sub=pack.chunkMetres/16f;
        int column=Math.max(0,Math.min(15,(int)Math.floor((globalX-chunkX*(double)pack.chunkMetres)/sub)));
        int row=Math.max(0,Math.min(15,(int)Math.floor((globalZ-chunkY*(double)pack.chunkMetres)/sub)));
        return (chunk.landRows()[row]&(1<<column))!=0;
    }

    private float terrainHeightOr(double globalX,double globalZ,float absent){
        int chunkX=pack.chunkX(globalX),chunkY=pack.chunkY(globalZ);
        QatarPack.WorldChunk terrain=nationalData.get(key(chunkX,chunkY));
        if(terrain==null)return absent;
        float localX=(float)(globalX-chunkX*(double)pack.chunkMetres),localZ=(float)(globalZ-chunkY*(double)pack.chunkMetres),sub=pack.chunkMetres/16f;
        int col=Math.max(0,Math.min(15,(int)Math.floor(localX/sub))),row=Math.max(0,Math.min(15,(int)Math.floor(localZ/sub)));float x0=col*sub,z0=row*sub,u=Math.max(0,Math.min(1,(localX-x0)/sub)),v=Math.max(0,Math.min(1,(localZ-z0)/sub));
        float h00=pack.terrainHeight(terrain,x0,z0),h10=pack.terrainHeight(terrain,x0+sub,z0),h11=pack.terrainHeight(terrain,x0+sub,z0+sub),h01=pack.terrainHeight(terrain,x0,z0+sub);
        return v>=u?(1-v)*h00+(v-u)*h01+u*h11:(1-u)*h00+v*h11+(u-v)*h10;
    }

    private void rebuildFocusModel(){
        if(focusModel!=null)focusModel.dispose();focusNodes.clear();
        renderedMode=physics.mode();focusTransition=0;
        Material maroon=new Material(ColorAttribute.createDiffuse(new Color(.48f,.035f,.12f,1)),ColorAttribute.createSpecular(new Color(.45f,.18f,.20f,1)),FloatAttribute.createShininess(32));
        Material pearl=new Material(ColorAttribute.createDiffuse(new Color(.88f,.86f,.78f,1)),ColorAttribute.createSpecular(new Color(.55f,.56f,.53f,1)),FloatAttribute.createShininess(26));
        Material dark=diffuse(.075f,.085f,.09f,1),rubber=diffuse(.035f,.04f,.042f,1),skin=diffuse(.62f,.42f,.29f,1),cloth=diffuse(.16f,.19f,.21f,1);
        Material glass=new Material(ColorAttribute.createDiffuse(new Color(.07f,.18f,.22f,1)),ColorAttribute.createSpecular(new Color(.76f,.86f,.89f,1)),ColorAttribute.createEmissive(new Color(.012f,.025f,.03f,1)),FloatAttribute.createShininess(92));
        Material lights=new Material(ColorAttribute.createDiffuse(new Color(.98f,.88f,.60f,1)),ColorAttribute.createEmissive(new Color(.38f,.27f,.08f,1)));
        Material redLights=new Material(ColorAttribute.createDiffuse(new Color(.85f,.035f,.025f,1)),ColorAttribute.createEmissive(new Color(.35f,.01f,.005f,1)));
        Material wake=diffuse(.66f,.84f,.88f,1);
        builder.begin();
        switch(renderedMode){
            case WALK->{
                MeshPartBuilder pelvis=rigPart("pelvis",cloth,0,-.16f,0);pelvis.box(0,0,0,.48f,.34f,.30f);
                MeshPartBuilder torso=rigPart("torso",maroon,0,.16f,0);torso.setVertexTransform(new Matrix4().setToTranslation(0,.28f,0));torso.capsule(.31f,.82f,14);torso.setVertexTransform(null);
                MeshPartBuilder neck=rigPart("neck",skin,0,.72f,0);neck.cylinder(.20f,.22f,.20f,10);
                MeshPartBuilder head=rigPart("head",skin,0,.98f,0);head.sphere(.39f,.48f,.37f,16,12);
                MeshPartBuilder hair=rigPart("hair",dark,0,1.17f,-.015f);hair.sphere(.40f,.20f,.38f,14,7);
                for(float side:new float[]{-1f,1f}){
                    String prefix=side<0?"left":"right";
                    MeshPartBuilder arm=rigPart(prefix+"-arm",maroon,side*.40f,.50f,0);arm.setVertexTransform(new Matrix4().setToTranslation(0,-.32f,0));arm.capsule(.105f,.72f,10);arm.setVertexTransform(null);
                    MeshPartBuilder hand=rigPart(prefix+"-hand",skin,side*.40f,-.18f,0);hand.sphere(.19f,.24f,.17f,10,7);
                    MeshPartBuilder leg=rigPart(prefix+"-leg",cloth,side*.17f,-.30f,0);leg.setVertexTransform(new Matrix4().setToTranslation(0,-.32f,0));leg.capsule(.14f,.78f,11);leg.setVertexTransform(null);
                    MeshPartBuilder shoe=rigPart(prefix+"-shoe",dark,side*.17f,-.91f,.08f);shoe.box(0,0,0,.27f,.18f,.45f);
                }
            }
            case CAR->{
                MeshPartBuilder chassis=rigPart("car-chassis",maroon,0,0,0);chassis.box(0,-.02f,0,2.08f,.62f,4.38f);chassis.box(0,.33f,1.18f,1.95f,.35f,1.55f);
                MeshPartBuilder cabin=rigPart("car-cabin",glass,0,.58f,-.35f);cabin.box(0,0,0,1.78f,.78f,2.03f);
                MeshPartBuilder roof=rigPart("car-roof",maroon,0,1.02f,-.38f);roof.box(0,0,0,1.88f,.13f,2.15f);
                MeshPartBuilder bumpers=rigPart("car-bumpers",dark,0,-.18f,0);bumpers.box(0,0,2.22f,2.12f,.25f,.18f);bumpers.box(0,0,-2.22f,2.12f,.25f,.18f);
                for(float x:new float[]{-1.08f,1.08f})for(float z:new float[]{-1.42f,1.42f}){
                    String id="wheel-"+(x<0?"l":"r")+(z>0?"f":"r");MeshPartBuilder wheel=rigPart(id,rubber,x,-.43f,z);
                    wheel.setVertexTransform(new Matrix4().rotate(Vector3.Z,90));wheel.cylinder(.82f,.32f,.82f,18);wheel.setVertexTransform(null);
                    MeshPartBuilder hub=rigPart(id+"-hub",pearl,x,-.43f,z);hub.setVertexTransform(new Matrix4().rotate(Vector3.Z,90));hub.cylinder(.36f,.34f,.36f,14);hub.setVertexTransform(null);
                }
                MeshPartBuilder frontLights=rigPart("car-headlights",lights,0,.10f,2.23f);frontLights.box(-.65f,0,0,.46f,.24f,.10f);frontLights.box(.65f,0,0,.46f,.24f,.10f);
                MeshPartBuilder rearLights=rigPart("car-tail-lights",redLights,0,.08f,-2.23f);rearLights.box(-.67f,0,0,.40f,.22f,.10f);rearLights.box(.67f,0,0,.40f,.22f,.10f);
                addPilot(skin,cloth,0,.63f,-.25f);
            }
            case BOAT->{
                MeshPartBuilder hull=rigPart("boat-hull",pearl,0,-.20f,0);hull.setVertexTransform(new Matrix4().setToScale(1f,.43f,1f));hull.sphere(3.05f,1.45f,7.75f,22,11);hull.setVertexTransform(null);
                MeshPartBuilder stripe=rigPart("boat-stripe",maroon,0,.02f,0);stripe.box(0,0,0,2.86f,.20f,5.9f);
                MeshPartBuilder deck=rigPart("boat-deck",pearl,0,.32f,-.18f);deck.box(0,0,0,2.72f,.16f,5.55f);
                MeshPartBuilder cabin=rigPart("boat-cabin",glass,0,.88f,-.75f);cabin.box(0,0,0,2.02f,1.02f,2.35f);
                MeshPartBuilder roof=rigPart("boat-roof",maroon,0,1.44f,-.78f);roof.box(0,0,0,2.22f,.13f,2.55f);
                MeshPartBuilder prop=rigPart("boat-propeller",dark,0,-.42f,-3.72f);prop.box(0,0,0,1.12f,.11f,.15f);prop.box(0,0,0,.11f,1.12f,.15f);
                MeshPartBuilder rudder=rigPart("boat-rudder",maroon,0,-.30f,-3.48f);rudder.box(0,0,0,.10f,.72f,.62f);
                MeshPartBuilder wakePart=rigPart("boat-wake",wake,0,-.48f,-4.4f);wakePart.box(0,0,0,2.6f,.025f,4.8f);
                addPilot(skin,cloth,0,.98f,-.35f);
            }
            case HELICOPTER->{
                MeshPartBuilder fuselage=rigPart("heli-fuselage",maroon,0,.05f,-.55f);fuselage.sphere(2.45f,1.48f,3.65f,22,13);
                MeshPartBuilder cockpit=rigPart("heli-cockpit",glass,0,.18f,1.08f);cockpit.sphere(2.08f,1.20f,1.72f,20,11);
                MeshPartBuilder tail=rigPart("heli-tail",maroon,0,.10f,-2.75f);tail.box(0,0,-.55f,.36f,.34f,3.60f);
                MeshPartBuilder fin=rigPart("heli-fin",maroon,0,.60f,-4.35f);fin.box(0,0,0,.16f,1.60f,.72f);
                MeshPartBuilder skids=rigPart("heli-skids",dark,0,-.68f,-.25f);skids.box(-.88f,0,0,.12f,.12f,3.18f);skids.box(.88f,0,0,.12f,.12f,3.18f);skids.box(0,0,1.05f,2.0f,.10f,.10f);skids.box(0,0,-1.05f,2.0f,.10f,.10f);
                MeshPartBuilder rotor=rigPart("main-rotor",dark,0,1.12f,-.25f);rotor.box(0,0,0,9.8f,.045f,.16f);rotor.box(0,0,0,.16f,.045f,9.8f);
                MeshPartBuilder tailRotor=rigPart("tail-rotor",dark,.25f,.55f,-4.42f);tailRotor.box(0,0,0,.06f,1.75f,.13f);tailRotor.box(0,0,0,.06f,.13f,1.75f);
                MeshPartBuilder beacon=rigPart("heli-beacon",redLights,0,1.02f,-1.0f);beacon.sphere(.20f,.18f,.20f,8,5);
                addPilot(skin,cloth,0,.36f,.72f);
            }
            case AEROPLANE->{
                MeshPartBuilder fuselage=rigPart("plane-fuselage",pearl,0,0,0);fuselage.setVertexTransform(new Matrix4().rotate(Vector3.X,90));fuselage.capsule(.66f,7.85f,20);fuselage.setVertexTransform(null);
                MeshPartBuilder leftWing=rigPart("plane-left-wing",maroon,-2.36f,.02f,.10f);leftWing.box(0,0,0,4.72f,.14f,1.25f);
                MeshPartBuilder rightWing=rigPart("plane-right-wing",maroon,2.36f,.02f,.10f);rightWing.box(0,0,0,4.72f,.14f,1.25f);
                MeshPartBuilder leftElevator=rigPart("plane-left-elevator",maroon,-.91f,.25f,-3.15f);leftElevator.box(0,0,0,1.82f,.11f,.68f);
                MeshPartBuilder rightElevator=rigPart("plane-right-elevator",maroon,.91f,.25f,-3.15f);rightElevator.box(0,0,0,1.82f,.11f,.68f);
                MeshPartBuilder fin=rigPart("plane-fin",maroon,0,.82f,-3.20f);fin.box(0,0,0,.15f,1.75f,.82f);
                MeshPartBuilder cockpit=rigPart("plane-cockpit",glass,0,.50f,2.42f);cockpit.sphere(.90f,.52f,1.30f,15,8);
                MeshPartBuilder prop=rigPart("plane-propeller",dark,0,0,4.12f);prop.box(0,0,0,2.75f,.13f,.10f);prop.box(0,0,0,.13f,2.75f,.10f);
                MeshPartBuilder gear=rigPart("plane-gear",dark,0,-.57f,-.25f);gear.box(-1.25f,0,0,.11f,.75f,.11f);gear.box(1.25f,0,0,.11f,.75f,.11f);gear.box(0,0,2.55f,.10f,.62f,.10f);
                for(float x:new float[]{-1.25f,1.25f}){MeshPartBuilder wheel=rigPart("plane-wheel-"+(x<0?"l":"r"),rubber,x,-.88f,-.25f);wheel.setVertexTransform(new Matrix4().rotate(Vector3.Z,90));wheel.cylinder(.48f,.20f,.48f,14);wheel.setVertexTransform(null);}
                addPilot(skin,cloth,0,.50f,2.15f);
            }
        }
        focusModel=builder.end();focusInstance=new ModelInstance(focusModel);
        collectFocusNodes(focusInstance.nodes);cameraFilter.reset();
        Gdx.app.log("QatarVisual","V09_FOCUS_RIG mode="+renderedMode+" articulated=yes originalProcedural=yes transition=yes speedPreserved=yes");
    }

    private MeshPartBuilder rigPart(String id,Material material,float x,float y,float z){Node node=builder.node();node.id=id;node.translation.set(x,y,z);return builder.part(id,GL20.GL_TRIANGLES,ATTRS,material);}
    private void addPilot(Material skin,Material uniform,float x,float y,float z){MeshPartBuilder body=rigPart("pilot-body",uniform,x,y,z);body.capsule(.20f,.62f,10);MeshPartBuilder head=rigPart("pilot-head",skin,x,y+.48f,z+.04f);head.sphere(.30f,.34f,.30f,11,8);}
    private void collectFocusNodes(Iterable<Node> nodes){for(Node node:nodes){if(node.id!=null)focusNodes.put(node.id,node);for(int i=0;i<node.getChildCount();i++)collectFocusNode(node.getChild(i));}}
    private void collectFocusNode(Node node){if(node.id!=null)focusNodes.put(node.id,node);for(int i=0;i<node.getChildCount();i++)collectFocusNode(node.getChild(i));}
    private Node focusNode(String id){return focusNodes.get(id);}

    private void updateFocusAnimation(float delta){
        focusAnimationTime+=Math.min(.1f,Math.max(0,delta));focusTransition=Math.min(1f,focusTransition+delta*2.7f);
        Vector3 velocity=physics.velocity();float speed=(float)Math.hypot(velocity.x,velocity.z),phase=focusAnimationTime*(2.4f+speed*1.5f);
        switch(renderedMode){
            case WALK->{
                float stride=Math.min(42f,speed*9f)*(float)Math.sin(phase),counter=-stride;
                rotateNode("left-leg",Vector3.X,stride);rotateNode("right-leg",Vector3.X,counter);rotateNode("left-arm",Vector3.X,counter*.75f);rotateNode("right-arm",Vector3.X,stride*.75f);
                Node torso=focusNode("torso");if(torso!=null)torso.translation.y=.16f+Math.min(.045f,speed*.008f)*Math.abs((float)Math.sin(phase*2));
            }
            case CAR->{
                float spin=-focusAnimationTime*speed*75f,steer=physics.visualSteering()*24f;
                for(String id:new String[]{"wheel-lf","wheel-rf","wheel-lr","wheel-rr"}){Node node=focusNode(id);if(node!=null){node.rotation.idt();if(id.endsWith("f"))node.rotation.mul(new com.badlogic.gdx.math.Quaternion(Vector3.Y,steer));node.rotation.mul(new com.badlogic.gdx.math.Quaternion(Vector3.X,spin));}}
            }
            case BOAT->{rotateNode("boat-propeller",Vector3.Z,focusAnimationTime*(220+speed*24));rotateNode("boat-rudder",Vector3.Y,-physics.visualSteering()*28f);Node wake=focusNode("boat-wake");if(wake!=null)wake.scale.set(.5f+Math.min(1f,speed*.08f),1,.25f+Math.min(1.8f,speed*.12f));}
            case HELICOPTER->{rotateNode("main-rotor",Vector3.Y,focusAnimationTime*760f);rotateNode("tail-rotor",Vector3.X,focusAnimationTime*980f);}
            case AEROPLANE->{
                rotateNode("plane-propeller",Vector3.Z,focusAnimationTime*(650+speed*8));float control=physics.visualLiftControl()*13f,roll=physics.visualSteering()*8f;
                rotateNode("plane-left-wing",Vector3.Z,roll);rotateNode("plane-right-wing",Vector3.Z,-roll);rotateNode("plane-left-elevator",Vector3.X,control);rotateNode("plane-right-elevator",Vector3.X,control);
            }
        }
    }

    private void rotateNode(String id,Vector3 axis,float degrees){Node node=focusNode(id);if(node!=null)node.rotation.setFromAxis(axis,degrees);}
    private static float smoothStep(float value){float t=Math.max(0,Math.min(1,value));return t*t*(3-2*t);}

    public void resetCamera(){cameraFilter.reset();}
    public PerspectiveCamera camera(){return camera;}
    public String fatalStreamingError(){return fatalStreamingError;}
    public int renderChunkCount(){return chunks.size();}

    @Override public void dispose(){activity.dispose();landmarks.dispose();for(RenderChunk value:chunks.values()){value.model.dispose();if(value.waterModel!=null)value.waterModel.dispose();}for(RenderChunk value:detailCells.values())value.model.dispose();chunks.clear();nationalData.clear();detailCells.clear();detailData.clear();if(focusModel!=null)focusModel.dispose();sandTexture.dispose();gravelTexture.dispose();saltTexture.dispose();urbanTexture.dispose();vegetationTexture.dispose();stoneFacadeTexture.dispose();glassFacadeTexture.dispose();batch.dispose();}
    private Material textured(float r,float g,float b,Texture texture){return new Material(ColorAttribute.createDiffuse(new Color(r,g,b,1)),TextureAttribute.createDiffuse(texture));}
    private static Material facade(float r,float g,float b,Texture texture){return new Material(ColorAttribute.createDiffuse(new Color(r,g,b,1)),TextureAttribute.createDiffuse(texture));}
    private static Texture createSurfaceTexture(int seed,int style){
        final int size=128;final float turn=(float)(Math.PI*2/size);Pixmap pixmap=new Pixmap(size,size,Pixmap.Format.RGBA8888);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){
            int hash=x*374761393+y*668265263+seed;hash=(hash^(hash>>>13))*1274126177;
            float fine=((hash>>>24)&255)/255f-.5f;
            float broad=.5f+.14f*(float)Math.sin(turn*(2*x+y))+.10f*(float)Math.sin(turn*(x-3*y))+.06f*(float)Math.cos(turn*(5*x+2*y));
            float r,g,b;
            if(style==0){float ripple=.045f*(float)Math.sin(turn*(6*x+2*y))+.022f*(float)Math.sin(turn*(13*x+5*y));float dune=.88f+ripple+fine*.035f;r=dune;g=dune*.93f;b=dune*.78f;}
            else if(style==1){float stone=.84f+(broad-.5f)*.16f+fine*.08f;r=stone;g=stone*.98f;b=stone*.92f;}
            else if(style==2){float crust=.92f+(broad-.5f)*.10f+fine*.025f;float vein=(Math.abs(Math.floorMod(x*7+y*11+seed,47)-23)<1)?.075f:0;r=crust-vein;g=crust*.99f-vein;b=crust*.94f-vein*.7f;}
            else if(style==3){float aggregate=.82f+(broad-.5f)*.11f+fine*.07f;float seam=(Math.floorMod(x+2*y,61)==0)?.04f:0;r=aggregate-seam;g=aggregate*.98f-seam;b=aggregate*.93f-seam;}
            else {float growth=.68f+(broad-.5f)*.22f+fine*.07f;r=growth*.72f;g=growth;b=growth*.55f;}
            pixmap.drawPixel(x,y,Color.rgba8888(clampColour(r),clampColour(g),clampColour(b),1));
        }
        Texture texture=new Texture(pixmap,true);pixmap.dispose();texture.setWrap(Texture.TextureWrap.Repeat,Texture.TextureWrap.Repeat);texture.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);return texture;
    }
    private static Texture createFacadeTexture(boolean glass){
        final int size=64;Pixmap pixmap=new Pixmap(size,size,Pixmap.Format.RGBA8888);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){
            boolean frame=glass?(x<6||x>=58||y<7||y>=57):(x<13||x>=51||y<15||y>=47);float shade=.97f+.025f*(float)Math.sin((x+y)*.16f);
            float r,g,b;
            if(glass){
                if(frame){r=.78f;g=.81f;b=.80f;}else{float reflection=.72f+.10f*(float)Math.sin((x-y)*.08f);r=reflection*.58f;g=reflection*.82f;b=reflection*.90f;}
            }else if(frame){r=shade;g=shade*.99f;b=shade*.96f;}
            else{float reflection=.67f+.08f*(float)Math.sin((x+y)*.09f);r=reflection*.42f;g=reflection*.66f;b=reflection*.74f;}
            pixmap.drawPixel(x,y,Color.rgba8888(clampColour(r),clampColour(g),clampColour(b),1));
        }
        Texture texture=new Texture(pixmap,true);pixmap.dispose();texture.setWrap(Texture.TextureWrap.Repeat,Texture.TextureWrap.Repeat);texture.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);return texture;
    }
    private static double pointSegmentDistance(double px,double pz,double ax,double az,double bx,double bz){double dx=bx-ax,dz=bz-az,length=dx*dx+dz*dz;if(length<1e-9)return Math.hypot(px-ax,pz-az);double t=Math.max(0,Math.min(1,((px-ax)*dx+(pz-az)*dz)/length));return Math.hypot(px-(ax+t*dx),pz-(az+t*dz));}
    private static float clampColour(float value){return Math.max(.05f,Math.min(1f,value));}
    private static Material diffuse(float r,float g,float b,float a){return new Material(ColorAttribute.createDiffuse(new Color(r,g,b,a)));}
    private static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
    private record EndpointKey(int xQuarterMetres,int zQuarterMetres,int group){}
    private static final class RoadJoint{
        final float x,z;float width;int roadClass,flags,count=1;
        RoadJoint(float x,float z,float width,int roadClass,int flags){this.x=x;this.z=z;this.width=width;this.roadClass=roadClass;this.flags=flags;}
    }
    private record ChunkModels(Model terrain,Model water){}
    private record RenderChunk(double x,double z,Model model,ModelInstance instance,Model waterModel,ModelInstance waterInstance){}
}
