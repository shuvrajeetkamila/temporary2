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
import java.util.Map;

/** Qatar-specific mobile 3D renderer. All geometry is generated, never imagery. */
public final class QatarWorldRenderer implements WorldStreamer.Listener, QatarDetailStreamer.Listener, Disposable {
    private static final long ATTRS = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates;
    private final QatarPack pack;
    private final int detailCellMetres;
    private final PhysicsWorld physics;
    private final QatarEnvironment climate;
    private final PerspectiveCamera camera;
    private final ModelBatch batch = new ModelBatch();
    private final ModelBuilder builder = new ModelBuilder();
    private final Environment environment = new Environment();
    private final ColorAttribute ambientLight=new ColorAttribute(ColorAttribute.AmbientLight,.64f,.61f,.55f,1);
    private final ColorAttribute fogColor=new ColorAttribute(ColorAttribute.Fog,.52f,.72f,.80f,1);
    private final DirectionalLight sun=new DirectionalLight().set(1.0f,.91f,.74f,-.42f,-.82f,-.32f);
    private final Map<Long, RenderChunk> chunks = new HashMap<>();
    private final Map<Long, QatarPack.WorldChunk> nationalData = new HashMap<>();
    private final Map<Long, RenderChunk> detailCells = new HashMap<>();
    private final Map<Long, QatarDetailPack.DetailChunk> detailData = new HashMap<>();
    private final EarClippingTriangulator footprintTriangulator = new EarClippingTriangulator();
    private final Texture terrainTexture=createTerrainTexture();
    private final Map<Integer, Material> terrainMaterials = new HashMap<>();
    private final Material shoulderMaterial=diffuse(.52f,.49f,.43f,1);
    private final Material roadMaterial=diffuse(.13f,.145f,.15f,1);
    private final Material pathMaterial=diffuse(.47f,.42f,.34f,1);
    private final Material railMaterial=new Material(ColorAttribute.createDiffuse(new Color(.25f,.25f,.24f,1)),ColorAttribute.createSpecular(new Color(.70f,.68f,.62f,1)),FloatAttribute.createShininess(48));
    private final Material runwayMaterial=diffuse(.105f,.115f,.12f,1);
    private final Material taxiwayMaterial=diffuse(.19f,.20f,.20f,1);
    private final Material markingMaterial=diffuse(.94f,.90f,.72f,1);
    private final Material roofMaterial=diffuse(.61f,.57f,.49f,1);
    private final Material windowMaterial=new Material(ColorAttribute.createDiffuse(new Color(.12f,.30f,.34f,1)),ColorAttribute.createSpecular(new Color(.58f,.69f,.70f,1)),FloatAttribute.createShininess(72));
    private final Material villaMaterial=diffuse(.78f,.72f,.63f,1);
    private final Material towerMaterial=new Material(ColorAttribute.createDiffuse(new Color(.40f,.51f,.54f,1)),ColorAttribute.createSpecular(new Color(.62f,.69f,.70f,1)),FloatAttribute.createShininess(56));
    private final Material industrialMaterial=diffuse(.51f,.53f,.51f,1);
    private final Material farmMaterial=diffuse(.50f,.55f,.38f,1);
    private final Material waterMaterial=new Material(ColorAttribute.createDiffuse(new Color(.035f,.34f,.44f,1)),ColorAttribute.createSpecular(new Color(.72f,.88f,.91f,1)),FloatAttribute.createShininess(96));
    private Model focusModel;
    private ModelInstance focusInstance;
    private PhysicsWorld.Mode renderedMode;
    private String fatalStreamingError="";
    private final Vector3 smoothCamera = new Vector3();
    private boolean cameraInitialized;
    private float lastCameraDistance;
    private boolean loggedRealDetail;
    private ThermalGovernor.Profile performanceProfile=ThermalGovernor.Profile.NORMAL;

    public QatarWorldRenderer(QatarPack pack,QatarDetailPack detailPack,PhysicsWorld physics,QatarEnvironment climate) {
        this.pack=pack;this.detailCellMetres=detailPack.cellMetres;this.physics=physics;this.climate=climate;
        camera=new PerspectiveCamera(64,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
        camera.near=.15f; camera.far=14_000f;
        environment.set(ambientLight);environment.set(fogColor);environment.add(sun);
        initialiseMaterials();
        rebuildFocusModel();
    }

    private void initialiseMaterials() {
        terrainMaterials.put(0,textured(.08f,.39f,.48f));
        terrainMaterials.put(1,textured(.66f,.58f,.46f));
        terrainMaterials.put(2,textured(.74f,.68f,.54f));
        terrainMaterials.put(3,textured(.82f,.67f,.42f));
        terrainMaterials.put(4,textured(.79f,.77f,.66f));
        terrainMaterials.put(5,textured(.37f,.48f,.31f));
        terrainMaterials.put(6,textured(.52f,.53f,.32f));
        terrainMaterials.put(7,textured(.56f,.52f,.45f));
        terrainMaterials.put(8,textured(.66f,.61f,.51f));
        terrainMaterials.put(9,textured(.88f,.79f,.60f));
    }

    public void render(float delta) {
        if (renderedMode != physics.mode()) rebuildFocusModel();
        updateTransforms();
        updateCamera(delta);updateAtmosphere();
        Gdx.gl.glViewport(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glClearColor(climate.skyRed(),climate.skyGreen(),climate.skyBlue(),1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
        batch.begin(camera);
        for(RenderChunk chunk:chunks.values()){batch.render(chunk.instance,environment);if(chunk.waterInstance!=null)batch.render(chunk.waterInstance,environment);}
        for (RenderChunk detail:detailCells.values()) batch.render(detail.instance,environment);
        focusInstance.transform.set(physics.transform());
        batch.render(focusInstance,environment);
        batch.end();
    }

    private void updateAtmosphere(){
        float ambient=climate.ambient();ambientLight.color.set(ambient,ambient*.96f,ambient*.88f,1);
        fogColor.color.set(climate.skyRed(),climate.skyGreen(),climate.skyBlue(),1);
        sun.color.set(climate.sunRed(),climate.sunGreen(),climate.sunBlue(),1);
        float azimuth=(climate.solarHour()-6f)/12f*(float)Math.PI;sun.direction.set(-(float)Math.cos(azimuth)*.48f,-Math.max(.28f,climate.daylight()),-.34f).nor();
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
        Vector3 focus=physics.position();
        float heading=physics.headingRadians();
        float distance,height;
        switch(physics.mode()) {
            case WALK -> {distance=8;height=3.7f;camera.far=6500*performanceProfile.viewDistanceScale;}
            case CAR -> {distance=12;height=5.1f;camera.far=9000*performanceProfile.viewDistanceScale;}
            case BOAT -> {distance=18;height=7;camera.far=11000*performanceProfile.viewDistanceScale;}
            case HELICOPTER -> {distance=27;height=11;camera.far=14000*performanceProfile.viewDistanceScale;}
            case AEROPLANE -> {distance=38;height=13;camera.far=16000*performanceProfile.viewDistanceScale;}
            default -> throw new IllegalStateException();
        }
        Vector3 wanted=new Vector3(focus.x-(float)Math.sin(heading)*distance,focus.y+height,focus.z-(float)Math.cos(heading)*distance);
        float wantedGround=terrainHeight(physics.originX()+wanted.x,physics.originZ()+wanted.z);wanted.y=Math.max(wanted.y,wantedGround+1.5f);
        if(!cameraInitialized){smoothCamera.set(wanted);cameraInitialized=true;} else smoothCamera.lerp(wanted,Math.min(1,delta*4.5f));
        float focusGround=terrainHeight(physics.globalX(),physics.globalZ());Vector3 target=new Vector3(focus.x,Math.max(focus.y+1.2f,focusGround+1.25f),focus.z);camera.position.set(resolveCamera(smoothCamera,target));lastCameraDistance=camera.position.dst(target);
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
        if(current.dst2(target)>=36f)return current;float base=(float)Math.atan2(requested.x-target.x,requested.z-target.z);Vector3 forced=null;
        for(float distance:new float[]{10,8,12})for(float turn:new float[]{0,.7f,-.7f,1.4f,-1.4f,3.14f}){
            Vector3 option=new Vector3(target.x+(float)Math.sin(base+turn)*distance,target.y+Math.max(4f,distance*.6f),target.z+(float)Math.cos(base+turn)*distance);
            double globalX=physics.originX()+option.x,globalZ=physics.originZ()+option.z;option.y=Math.max(option.y,terrainHeight(globalX,globalZ)+2f);if(forced==null)forced=new Vector3(option);
            if(!cameraBlocked(globalX,option.y,globalZ))return option;
        }
        // A distant elevated fallback is safer than collapsing into the focus model when imperfect OSM envelopes overlap every probe.
        return forced==null?current:forced;
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
        MeshPartBuilder land=builder.part("terrain-"+chunk.entry().biome(),GL20.GL_TRIANGLES,ATTRS,terrainMaterials.get(chunk.entry().biome()));float sub=size/16f;
        if(!chunk.entry().hasLand())land.box(size/2,-80,size/2,size,.1f,size);
        for(int row=0;row<16;row++){
            int bits=chunk.landRows()[row];
            for(int col=0;col<16;col++){
                if((bits&(1<<col))==0)continue;float x0=col*sub,x1=(col+1)*sub,z0=row*sub,z1=(row+1)*sub;
                float h00=pack.terrainHeight(chunk,x0,z0),h10=pack.terrainHeight(chunk,x1,z0),h11=pack.terrainHeight(chunk,x1,z1),h01=pack.terrainHeight(chunk,x0,z1);
                terrainTriangle(land,x0,h00,z0,x0,h01,z1,x1,h11,z1);terrainTriangle(land,x1,h11,z1,x1,h10,z0,x0,h00,z0);
            }
        }
        Model terrain=builder.end(),water=null;
        if(!chunk.entry().hasLand()||chunk.entry().isCoast()){
            builder.begin();MeshPartBuilder surface=builder.part("gulf-water",GL20.GL_TRIANGLES,ATTRS,waterMaterial);float waterSub=size/8f;
            double originX=chunk.entry().x()*(double)pack.chunkMetres,originZ=chunk.entry().y()*(double)pack.chunkMetres;
            for(int row=0;row<8;row++)for(int col=0;col<8;col++){
                float x0=col*waterSub,x1=(col+1)*waterSub,z0=row*waterSub,z1=(row+1)*waterSub;
                float h00=staticWave(originX+x0,originZ+z0),h10=staticWave(originX+x1,originZ+z0),h11=staticWave(originX+x1,originZ+z1),h01=staticWave(originX+x0,originZ+z1);
                terrainTriangle(surface,x0,h00,z0,x0,h01,z1,x1,h11,z1);terrainTriangle(surface,x1,h11,z1,x1,h10,z0,x0,h00,z0);
            }
            water=builder.end();
        }
        return new ChunkModels(terrain,water);
    }

    private static float staticWave(double x,double z){return .055f*(float)Math.sin(x*.012+z*.005)+.025f*(float)Math.sin(z*.021-x*.004);}


    @Override public void activateDetail(QatarDetailPack.DetailChunk chunk){
        detailData.put(key(chunk.entry().x(),chunk.entry().y()),chunk);rebuildDetail(chunk);
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
        physics.unregisterDetailChunk(chunk);long detailKey=key(chunk.entry().x(),chunk.entry().y());detailData.remove(detailKey);
        RenderChunk removed=detailCells.remove(detailKey);
        if(removed!=null)removed.model.dispose();
    }

    @Override public void detailCellFailed(QatarDetailPack.DetailEntry entry,Throwable error){
        fatalStreamingError="OSM detail cell "+entry.x()+","+entry.y()+" failed: "+error.getMessage();
    }

    private Model buildDetailCell(QatarDetailPack.DetailChunk chunk){
        builder.begin();
        double cellOriginX=chunk.entry().x()*(double)detailCellMetres,cellOriginZ=chunk.entry().y()*(double)detailCellMetres;
        if(chunk.roads().length>0){
            MeshPartBuilder shoulders=builder.part("osm-road-shoulders",GL20.GL_TRIANGLES,ATTRS,shoulderMaterial);
            MeshPartBuilder roads=builder.part("osm-paved-roads",GL20.GL_TRIANGLES,ATTRS,roadMaterial);
            MeshPartBuilder paths=builder.part("osm-paths-tracks",GL20.GL_TRIANGLES,ATTRS,pathMaterial);
            MeshPartBuilder rails=builder.part("osm-rail",GL20.GL_TRIANGLES,ATTRS,railMaterial);
            MeshPartBuilder runways=builder.part("osm-runways",GL20.GL_TRIANGLES,ATTRS,runwayMaterial);
            MeshPartBuilder taxiways=builder.part("osm-taxiways",GL20.GL_TRIANGLES,ATTRS,taxiwayMaterial);
            for(QatarPack.Road road:chunk.roads()){
                if(road.roadClass()<=4)roadQuad(shoulders,road,cellOriginX,cellOriginZ,.105f,road.width()+2.2f);
                MeshPartBuilder target=switch(road.roadClass()){case 7,8->paths;case 9->rails;case 10->runways;case 11->taxiways;default->roads;};
                roadQuad(target,road,cellOriginX,cellOriginZ,.13f,road.width());
            }
            MeshPartBuilder marks=builder.part("osm-centre-lines",GL20.GL_TRIANGLES,ATTRS,markingMaterial);
            for(QatarPack.Road road:chunk.roads())if(road.roadClass()<=4||road.roadClass()==10)roadQuad(marks,road,cellOriginX,cellOriginZ,.15f,road.roadClass()==10?.55f:.16f);
        }
        MeshPartBuilder towers=builder.part("osm-towers",GL20.GL_TRIANGLES,ATTRS,towerMaterial);
        MeshPartBuilder villas=builder.part("osm-villas",GL20.GL_TRIANGLES,ATTRS,villaMaterial);
        MeshPartBuilder industry=builder.part("osm-industry",GL20.GL_TRIANGLES,ATTRS,industrialMaterial);
        MeshPartBuilder farms=builder.part("osm-farms",GL20.GL_TRIANGLES,ATTRS,farmMaterial);
        MeshPartBuilder roofs=builder.part("osm-roofs",GL20.GL_TRIANGLES,ATTRS,roofMaterial);
        MeshPartBuilder windows=builder.part("osm-facade-windows",GL20.GL_TRIANGLES,ATTRS,windowMaterial);
        for(QatarPack.Building building:chunk.buildings()){
            MeshPartBuilder target=switch(building.archetype()){case 1->towers;case 4->industry;case 5->farms;default->villas;};
            float baseHeight=terrainHeight(cellOriginX+building.x(),cellOriginZ+building.z());
            if(building.hasSourceFootprint())extrudeFootprint(target,roofs,windows,building,baseHeight);
            else target.box(building.x(),baseHeight+building.height()/2,building.z(),building.width(),building.height(),building.depth());
        }
        return builder.end();
    }

    private void extrudeFootprint(MeshPartBuilder part,MeshPartBuilder roofs,MeshPartBuilder windows,QatarPack.Building building,float baseHeight){
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
        int points=polygon.length/2,windowStride=Math.max(1,(points+23)/24);float signedArea=0;
        for(int i=0;i<points;i++){int j=(i+1)%points;signedArea+=polygon[i*2]*polygon[j*2+1]-polygon[j*2]*polygon[i*2+1];}
        boolean counterClockwise=signedArea>0;
        for(int i=0;i<points;i++){
            int j=(i+1)%points;float ax=polygon[i*2],az=polygon[i*2+1],bx=polygon[j*2],bz=polygon[j*2+1];
            float dx=bx-ax,dz=bz-az,length=(float)Math.hypot(dx,dz);if(length<.02f)continue;
            float direction=counterClockwise?1:-1,nx=direction*dz/length,nz=-direction*dx/length;
            facadeRect(part,ax,baseHeight,az,bx,height,bz,nx,nz,counterClockwise);
            if(i%windowStride==0&&length>=4&&building.height()>=5&&building.archetype()!=5){
                float margin=Math.min(1.2f,length*.18f),start=margin/length,end=1-start;
                float wx1=ax+dx*start,wz1=az+dz*start,wx2=ax+dx*end,wz2=az+dz*end;
                int totalFloors=Math.max(1,(int)((building.height()-1.5f)/3.2f)),displayed=Math.min(8,totalFloors);
                for(int slot=0;slot<displayed;slot++){
                    int floor=displayed==1?0:slot*(totalFloors-1)/(displayed-1);float bottom=baseHeight+1.35f+floor*3.2f,top=Math.min(height-.35f,bottom+1.1f);if(top<=bottom)continue;
                    float offset=.035f;facadeRect(windows,wx1+nx*offset,bottom,wz1+nz*offset,wx2+nx*offset,top,wz2+nz*offset,nx,nz,counterClockwise);
                }
            }
        }
    }

    private static void facadeRect(MeshPartBuilder part,float ax,float bottom,float az,float bx,float top,float bz,float nx,float nz,boolean counterClockwise){
        if(counterClockwise)part.rect(bx,bottom,bz,ax,bottom,az,ax,top,az,bx,top,bz,nx,0,nz);
        else part.rect(ax,bottom,az,bx,bottom,bz,bx,top,bz,ax,top,az,nx,0,nz);
    }

    private static void terrainTriangle(MeshPartBuilder part,float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz){
        float ux=bx-ax,uy=by-ay,uz=bz-az,vx=cx-ax,vy=cy-ay,vz=cz-az;
        float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(length<.0001f){nx=0;ny=1;nz=0;}else{nx/=length;ny/=length;nz/=length;}
        MeshPartBuilder.VertexInfo a=new MeshPartBuilder.VertexInfo().setPos(ax,ay,az).setNor(nx,ny,nz).setUV(ax/25f,az/25f);
        MeshPartBuilder.VertexInfo b=new MeshPartBuilder.VertexInfo().setPos(bx,by,bz).setNor(nx,ny,nz).setUV(bx/25f,bz/25f);
        MeshPartBuilder.VertexInfo c=new MeshPartBuilder.VertexInfo().setPos(cx,cy,cz).setNor(nx,ny,nz).setUV(cx/25f,cz/25f);
        part.triangle(a,b,c);
    }

    private void roadQuad(MeshPartBuilder part,QatarPack.Road r,double cellOriginX,double cellOriginZ,float offset,float width){
        float dx=r.x2()-r.x1(),dz=r.z2()-r.z1(),length=(float)Math.hypot(dx,dz);
        if(length<.01f)return;
        float px=-dz/length*width/2,pz=dx/length*width/2;
        float x1a=r.x1()+px,z1a=r.z1()+pz,x1b=r.x1()-px,z1b=r.z1()-pz;
        float x2b=r.x2()-px,z2b=r.z2()-pz,x2a=r.x2()+px,z2a=r.z2()+pz;
        float h1a=terrainHeight(cellOriginX+x1a,cellOriginZ+z1a)+offset,h1b=terrainHeight(cellOriginX+x1b,cellOriginZ+z1b)+offset;
        float h2b=terrainHeight(cellOriginX+x2b,cellOriginZ+z2b)+offset,h2a=terrainHeight(cellOriginX+x2a,cellOriginZ+z2a)+offset;
        terrainTriangle(part,x1a,h1a,z1a,x2a,h2a,z2a,x2b,h2b,z2b);
        terrainTriangle(part,x2b,h2b,z2b,x1b,h1b,z1b,x1a,h1a,z1a);
    }

    public float terrainHeightAt(double globalX,double globalZ){return terrainHeightOr(globalX,globalZ,Float.NaN);}
    public boolean waterAt(double globalX,double globalZ){
        int chunkX=pack.chunkX(globalX),chunkY=pack.chunkY(globalZ);QatarPack.WorldChunk chunk=nationalData.get(key(chunkX,chunkY));if(chunk==null)return false;
        if(!chunk.entry().hasLand())return true;float sub=pack.chunkMetres/16f;int column=Math.max(0,Math.min(15,(int)((globalX-chunkX*(double)pack.chunkMetres)/sub)));int row=Math.max(0,Math.min(15,(int)((globalZ-chunkY*(double)pack.chunkMetres)/sub)));
        return (chunk.landRows()[row]&(1<<column))==0;
    }
    public void setPerformanceProfile(ThermalGovernor.Profile profile){performanceProfile=profile;}
    public float cameraDistance(){return lastCameraDistance;}

    private float terrainHeight(double globalX,double globalZ){return terrainHeightOr(globalX,globalZ,0);}

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
        if(focusModel!=null)focusModel.dispose();
        renderedMode=physics.mode();
        Material maroon=diffuse(.45f,.035f,.12f,1),dark=diffuse(.10f,.12f,.13f,1),white=diffuse(.88f,.86f,.78f,1),glass=new Material(ColorAttribute.createDiffuse(new Color(.08f,.18f,.21f,1)),ColorAttribute.createSpecular(new Color(.7f,.8f,.82f,1)),FloatAttribute.createShininess(80));
        builder.begin();
        switch(renderedMode){
            case WALK->{
                MeshPartBuilder torso=builder.part("body",GL20.GL_TRIANGLES,ATTRS,maroon);
                torso.setVertexTransform(new Matrix4().setToTranslation(0,1.34f,0));
                torso.sphere(.72f,1.12f,.46f,14,10);torso.setVertexTransform(null);
                MeshPartBuilder skin=builder.part("skin",GL20.GL_TRIANGLES,ATTRS,white);
                skin.setVertexTransform(new Matrix4().setToTranslation(0,2.08f,0));
                skin.sphere(.50f,.54f,.50f,16,12);skin.setVertexTransform(null);
                MeshPartBuilder limbs=builder.part("limbs",GL20.GL_TRIANGLES,ATTRS,dark);
                limbs.setVertexTransform(new Matrix4().setToTranslation(-.19f,.52f,0));limbs.capsule(.13f,1.04f,10);
                limbs.setVertexTransform(new Matrix4().setToTranslation(.19f,.52f,0));limbs.capsule(.13f,1.04f,10);
                limbs.setVertexTransform(new Matrix4().setToTranslation(-.47f,1.32f,0));limbs.capsule(.11f,.94f,10);
                limbs.setVertexTransform(new Matrix4().setToTranslation(.47f,1.32f,0));limbs.capsule(.11f,.94f,10);
                limbs.setVertexTransform(null);
            }
            case CAR->{
                builder.part("4x4-body",GL20.GL_TRIANGLES,ATTRS,maroon).box(0,.72f,0,2.05f,.82f,4.35f);
                MeshPartBuilder cabin=builder.part("4x4-glass-cabin",GL20.GL_TRIANGLES,ATTRS,glass);cabin.box(0,1.35f,-.30f,1.72f,.82f,2.18f);
                MeshPartBuilder wheels=builder.part("4x4-wheels",GL20.GL_TRIANGLES,ATTRS,dark);
                for(float x:new float[]{-1.04f,1.04f})for(float z:new float[]{-1.38f,1.38f}){wheels.setVertexTransform(new Matrix4().setToTranslation(x,.48f,z).rotate(Vector3.Z,90));wheels.cylinder(.68f,.30f,.68f,16);}wheels.setVertexTransform(null);
            }
            case BOAT->{
                MeshPartBuilder hull=builder.part("hull",GL20.GL_TRIANGLES,ATTRS,white);hull.setVertexTransform(new Matrix4().setToTranslation(0,.38f,0));hull.sphere(3.15f,1.20f,7.9f,20,10);hull.setVertexTransform(null);
                builder.part("deck",GL20.GL_TRIANGLES,ATTRS,maroon).box(0,.92f,-.25f,2.65f,.18f,5.0f);builder.part("cabin",GL20.GL_TRIANGLES,ATTRS,glass).box(0,1.55f,-.65f,2.0f,1.15f,2.45f);
            }
            case HELICOPTER->{
                MeshPartBuilder fuselage=builder.part("fuselage",GL20.GL_TRIANGLES,ATTRS,maroon);fuselage.setVertexTransform(new Matrix4().setToTranslation(0,.62f,-.55f));fuselage.sphere(2.55f,1.55f,3.7f,20,12);fuselage.setVertexTransform(null);
                builder.part("cockpit",GL20.GL_TRIANGLES,ATTRS,glass).box(0,.82f,1.05f,2.10f,1.02f,1.55f);builder.part("tail",GL20.GL_TRIANGLES,ATTRS,maroon).box(0,.66f,-3.05f,.38f,.38f,3.45f);builder.part("rotor",GL20.GL_TRIANGLES,ATTRS,dark).box(0,1.58f,-.3f,9f,.055f,.18f);
            }
            case AEROPLANE->{
                MeshPartBuilder body=builder.part("airframe",GL20.GL_TRIANGLES,ATTRS,white);body.setVertexTransform(new Matrix4().setToTranslation(0,.52f,0).rotate(Vector3.X,90));body.capsule(.67f,7.7f,18);body.setVertexTransform(null);
                builder.part("wing",GL20.GL_TRIANGLES,ATTRS,maroon).box(0,.48f,.15f,9.0f,.14f,1.32f);builder.part("tailplane",GL20.GL_TRIANGLES,ATTRS,maroon).box(0,.78f,-3.05f,3.5f,.12f,.70f);builder.part("cockpit",GL20.GL_TRIANGLES,ATTRS,glass).box(0,.78f,2.55f,.92f,.48f,1.18f);
            }
        }
        focusModel=builder.end();focusInstance=new ModelInstance(focusModel);
    }

    public PerspectiveCamera camera(){return camera;}
    public String fatalStreamingError(){return fatalStreamingError;}
    public int renderChunkCount(){return chunks.size();}

    @Override public void dispose(){for(RenderChunk value:chunks.values()){value.model.dispose();if(value.waterModel!=null)value.waterModel.dispose();}for(RenderChunk value:detailCells.values())value.model.dispose();chunks.clear();nationalData.clear();detailCells.clear();detailData.clear();if(focusModel!=null)focusModel.dispose();terrainTexture.dispose();batch.dispose();}
    private Material textured(float r,float g,float b){return new Material(ColorAttribute.createDiffuse(new Color(r,g,b,1)),TextureAttribute.createDiffuse(terrainTexture));}
    private static Texture createTerrainTexture(){
        Pixmap pixmap=new Pixmap(64,64,Pixmap.Format.RGBA8888);
        for(int y=0;y<64;y++)for(int x=0;x<64;x++){int hash=x*374761393+y*668265263;hash=(hash^(hash>>>13))*1274126177;float grain=.80f+((hash>>>24)&255)/255f*.18f;float ripple=.025f*(float)Math.sin(x*.72+y*.31);float value=Math.max(.72f,Math.min(1f,grain+ripple));pixmap.drawPixel(x,y,Color.rgba8888(value,value*.98f,value*.92f,1));}
        Texture texture=new Texture(pixmap);pixmap.dispose();texture.setWrap(Texture.TextureWrap.Repeat,Texture.TextureWrap.Repeat);texture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);return texture;
    }
    private static Material diffuse(float r,float g,float b,float a){return new Material(ColorAttribute.createDiffuse(new Color(r,g,b,a)));}
    private static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
    private record ChunkModels(Model terrain,Model water){}
    private record RenderChunk(double x,double z,Model model,ModelInstance instance,Model waterModel,ModelInstance waterInstance){}
}
