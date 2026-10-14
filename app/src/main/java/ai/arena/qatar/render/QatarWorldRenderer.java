package ai.arena.qatar.render;

import ai.arena.qatar.physics.PhysicsWorld;
import ai.arena.qatar.world.QatarDetailPack;
import ai.arena.qatar.world.QatarDetailStreamer;
import ai.arena.qatar.world.QatarPack;
import ai.arena.qatar.world.WorldStreamer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
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
    private static final long ATTRS = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
    private final QatarPack pack;
    private final int detailCellMetres;
    private final PhysicsWorld physics;
    private final PerspectiveCamera camera;
    private final ModelBatch batch = new ModelBatch();
    private final ModelBuilder builder = new ModelBuilder();
    private final Environment environment = new Environment();
    private final Map<Long, RenderChunk> chunks = new HashMap<>();
    private final Map<Long, QatarPack.WorldChunk> nationalData = new HashMap<>();
    private final Map<Long, RenderChunk> detailCells = new HashMap<>();
    private final Map<Long, QatarDetailPack.DetailChunk> detailData = new HashMap<>();
    private final EarClippingTriangulator footprintTriangulator = new EarClippingTriangulator();
    private final Map<Integer, Material> terrainMaterials = new HashMap<>();
    private final Material roadMaterial = diffuse(.16f,.18f,.19f,1);
    private final Material pathMaterial = diffuse(.48f,.46f,.42f,1);
    private final Material markingMaterial = diffuse(.88f,.82f,.62f,1);
    private final Material windowMaterial = new Material(ColorAttribute.createDiffuse(new Color(.08f,.20f,.24f,1)),ColorAttribute.createSpecular(new Color(.45f,.60f,.62f,1)));
    private final Material villaMaterial = diffuse(.72f,.66f,.56f,1);
    private final Material towerMaterial = new Material(ColorAttribute.createDiffuse(new Color(.28f,.42f,.48f,1)), ColorAttribute.createSpecular(new Color(.55f,.63f,.65f,1)));
    private final Material industrialMaterial = diffuse(.46f,.49f,.48f,1);
    private final Material farmMaterial = diffuse(.52f,.55f,.40f,1);
    private final Material waterMaterial = new Material(ColorAttribute.createDiffuse(new Color(.035f,.40f,.53f,.78f)), new BlendingAttribute(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA,.78f));
    private Model focusModel;
    private ModelInstance focusInstance;
    private PhysicsWorld.Mode renderedMode;
    private String fatalStreamingError="";
    private final Vector3 smoothCamera = new Vector3();
    private boolean cameraInitialized;
    private boolean loggedRealDetail;

    public QatarWorldRenderer(QatarPack pack,QatarDetailPack detailPack,PhysicsWorld physics) {
        this.pack=pack;this.detailCellMetres=detailPack.cellMetres;this.physics=physics;
        camera=new PerspectiveCamera(64,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
        camera.near=.15f; camera.far=14_000f;
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight,.56f,.53f,.48f,1));
        environment.add(new DirectionalLight().set(1.0f,.91f,.74f,-.42f,-.82f,-.32f));
        initialiseMaterials();
        rebuildFocusModel();
    }

    private void initialiseMaterials() {
        terrainMaterials.put(0,diffuse(.08f,.39f,.48f,1));
        terrainMaterials.put(1,diffuse(.62f,.55f,.44f,1));
        terrainMaterials.put(2,diffuse(.71f,.65f,.51f,1));
        terrainMaterials.put(3,diffuse(.80f,.65f,.39f,1));
        terrainMaterials.put(4,diffuse(.77f,.75f,.64f,1));
        terrainMaterials.put(5,diffuse(.35f,.46f,.30f,1));
        terrainMaterials.put(6,diffuse(.50f,.51f,.31f,1));
        terrainMaterials.put(7,diffuse(.54f,.50f,.43f,1));
        terrainMaterials.put(8,diffuse(.64f,.59f,.49f,1));
        terrainMaterials.put(9,diffuse(.86f,.77f,.58f,1));
    }

    public void render(float delta) {
        if (renderedMode != physics.mode()) rebuildFocusModel();
        updateTransforms();
        updateCamera(delta);
        Gdx.gl.glViewport(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glClearColor(.52f,.72f,.80f,1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
        batch.begin(camera);
        for (RenderChunk chunk:chunks.values()) batch.render(chunk.instance,environment);
        for (RenderChunk detail:detailCells.values()) batch.render(detail.instance,environment);
        focusInstance.transform.set(physics.transform());
        batch.render(focusInstance,environment);
        batch.end();
    }

    private void updateTransforms() {
        for (RenderChunk value:chunks.values()) position(value);
        for (RenderChunk value:detailCells.values()) position(value);
    }

    private void position(RenderChunk value){
        float x=(float)(value.x-physics.originX());
        float z=(float)(value.z-physics.originZ());
        value.instance.transform.setToTranslation(x,0,z);
    }

    private void updateCamera(float delta) {
        Vector3 focus=physics.position();
        float heading=physics.headingRadians();
        float distance,height;
        switch(physics.mode()) {
            case WALK -> {distance=8;height=3.7f;camera.far=6500;}
            case CAR -> {distance=12;height=5.1f;camera.far=9000;}
            case BOAT -> {distance=18;height=7;camera.far=11000;}
            case HELICOPTER -> {distance=27;height=11;camera.far=14000;}
            case AEROPLANE -> {distance=38;height=13;camera.far=16000;}
            default -> throw new IllegalStateException();
        }
        Vector3 wanted=new Vector3(focus.x-(float)Math.sin(heading)*distance,focus.y+height,focus.z-(float)Math.cos(heading)*distance);
        if(!cameraInitialized){smoothCamera.set(wanted);cameraInitialized=true;} else smoothCamera.lerp(wanted,Math.min(1,delta*4.5f));
        camera.position.set(smoothCamera);
        camera.up.set(Vector3.Y);
        camera.lookAt(focus.x,focus.y+1.2f,focus.z);
        camera.update();
    }

    @Override public void activate(QatarPack.WorldChunk chunk) {
        Model model=buildChunk(chunk);long chunkKey=key(chunk.entry().x(),chunk.entry().y());
        nationalData.put(chunkKey,chunk);
        try{physics.activateChunk(chunk,pack.chunkMetres);}catch(RuntimeException|Error error){nationalData.remove(chunkKey);model.dispose();throw error;}
        chunks.put(chunkKey,new RenderChunk(chunk.entry().x()*pack.chunkMetres,chunk.entry().y()*pack.chunkMetres,model,new ModelInstance(model)));
        for(QatarDetailPack.DetailChunk detail:detailData.values())if(parentNationalKey(detail)==chunkKey)rebuildDetail(detail);
    }

    @Override public void deactivate(QatarPack.WorldChunk chunk) {
        physics.deactivateChunk(chunk);long chunkKey=key(chunk.entry().x(),chunk.entry().y());nationalData.remove(chunkKey);
        RenderChunk removed=chunks.remove(chunkKey);
        if(removed!=null) removed.model.dispose();
    }

    @Override public void requiredChunkFailed(QatarPack.ChunkEntry entry,Throwable error) {
        fatalStreamingError="Required chunk "+entry.x()+","+entry.y()+" failed: "+error.getMessage();
    }

    private Model buildChunk(QatarPack.WorldChunk chunk) {
        builder.begin();
        float size=pack.chunkMetres;
        MeshPartBuilder water=builder.part("water",GL20.GL_TRIANGLES,ATTRS,waterMaterial);
        water.box(size/2,-1.35f,size/2,size,.35f,size);
        MeshPartBuilder land=builder.part("terrain-"+chunk.entry().biome(),GL20.GL_TRIANGLES,ATTRS,terrainMaterials.get(chunk.entry().biome()));
        float sub=size/16f;
        for(int row=0;row<16;row++){
            int bits=chunk.landRows()[row];
            for(int col=0;col<16;col++){
                if((bits&(1<<col))==0)continue;
                float x0=col*sub,x1=(col+1)*sub,z0=row*sub,z1=(row+1)*sub;
                float h00=pack.terrainHeight(chunk,x0,z0),h10=pack.terrainHeight(chunk,x1,z0),h11=pack.terrainHeight(chunk,x1,z1),h01=pack.terrainHeight(chunk,x0,z1);
                terrainTriangle(land,x0,h00,z0,x0,h01,z1,x1,h11,z1);
                terrainTriangle(land,x1,h11,z1,x1,h10,z0,x0,h00,z0);
            }
        }
        return builder.end();
    }

    @Override public void activateDetail(QatarDetailPack.DetailChunk chunk){
        detailData.put(key(chunk.entry().x(),chunk.entry().y()),chunk);rebuildDetail(chunk);
        if(!loggedRealDetail){loggedRealDetail=true;Gdx.app.log("QatarStreaming","OSM_READY cell="+chunk.entry().x()+","+chunk.entry().y()+" roads="+chunk.roads().length+" buildings="+chunk.buildings().length);}
    }

    private void rebuildDetail(QatarDetailPack.DetailChunk chunk){
        Model model=buildDetailCell(chunk);long detailKey=key(chunk.entry().x(),chunk.entry().y());
        RenderChunk old=detailCells.put(detailKey,new RenderChunk(chunk.entry().x()*(double)detailCellMetres,chunk.entry().y()*(double)detailCellMetres,model,new ModelInstance(model)));
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
            MeshPartBuilder roads=builder.part("osm-paved-roads",GL20.GL_TRIANGLES,ATTRS,roadMaterial);
            MeshPartBuilder paths=builder.part("osm-paths-tracks",GL20.GL_TRIANGLES,ATTRS,pathMaterial);
            for(QatarPack.Road road:chunk.roads())roadQuad(road.roadClass()==7||road.roadClass()==8?paths:roads,road,cellOriginX,cellOriginZ,.13f,road.width());
            MeshPartBuilder marks=builder.part("osm-centre-lines",GL20.GL_TRIANGLES,ATTRS,markingMaterial);
            for(QatarPack.Road road:chunk.roads())if(road.roadClass()<=4)roadQuad(marks,road,cellOriginX,cellOriginZ,.145f,.16f);
        }
        MeshPartBuilder towers=builder.part("osm-towers",GL20.GL_TRIANGLES,ATTRS,towerMaterial);
        MeshPartBuilder villas=builder.part("osm-villas",GL20.GL_TRIANGLES,ATTRS,villaMaterial);
        MeshPartBuilder industry=builder.part("osm-industry",GL20.GL_TRIANGLES,ATTRS,industrialMaterial);
        MeshPartBuilder farms=builder.part("osm-farms",GL20.GL_TRIANGLES,ATTRS,farmMaterial);
        MeshPartBuilder windows=builder.part("osm-facade-windows",GL20.GL_TRIANGLES,ATTRS,windowMaterial);
        for(QatarPack.Building building:chunk.buildings()){
            MeshPartBuilder target=switch(building.archetype()){case 1->towers;case 4->industry;case 5->farms;default->villas;};
            float baseHeight=terrainHeight(cellOriginX+building.x(),cellOriginZ+building.z());
            if(building.hasSourceFootprint())extrudeFootprint(target,windows,building,baseHeight);
            else target.box(building.x(),baseHeight+building.height()/2,building.z(),building.width(),building.height(),building.depth());
        }
        return builder.end();
    }

    private void extrudeFootprint(MeshPartBuilder part,MeshPartBuilder windows,QatarPack.Building building,float baseHeight){
        float[] polygon=building.footprint();
        ShortArray triangles=footprintTriangulator.computeTriangles(polygon);
        float height=baseHeight+building.height();
        MeshPartBuilder.VertexInfo a=new MeshPartBuilder.VertexInfo(),b=new MeshPartBuilder.VertexInfo(),c=new MeshPartBuilder.VertexInfo();
        for(int i=0;i<triangles.size;i+=3){
            int ia=triangles.get(i)*2,ib=triangles.get(i+1)*2,ic=triangles.get(i+2)*2;
            a.setPos(polygon[ia],height,polygon[ia+1]).setNor(0,1,0);
            b.setPos(polygon[ib],height,polygon[ib+1]).setNor(0,1,0);
            c.setPos(polygon[ic],height,polygon[ic+1]).setNor(0,1,0);
            part.triangle(a,b,c);
        }
        int points=polygon.length/2;float signedArea=0;
        for(int i=0;i<points;i++){int j=(i+1)%points;signedArea+=polygon[i*2]*polygon[j*2+1]-polygon[j*2]*polygon[i*2+1];}
        boolean counterClockwise=signedArea>0;
        for(int i=0;i<points;i++){
            int j=(i+1)%points;float ax=polygon[i*2],az=polygon[i*2+1],bx=polygon[j*2],bz=polygon[j*2+1];
            float dx=bx-ax,dz=bz-az,length=(float)Math.hypot(dx,dz);if(length<.02f)continue;
            float direction=counterClockwise?1:-1,nx=direction*dz/length,nz=-direction*dx/length;
            facadeRect(part,ax,baseHeight,az,bx,height,bz,nx,nz,counterClockwise);
            if(points<=24&&length>=4&&building.height()>=5&&building.archetype()!=5){
                float margin=Math.min(1.2f,length*.18f),start=margin/length,end=1-start;
                float wx1=ax+dx*start,wz1=az+dz*start,wx2=ax+dx*end,wz2=az+dz*end;
                int floors=Math.min(4,Math.max(1,(int)((building.height()-1.5f)/3.2f)));
                for(int floor=0;floor<floors;floor++){
                    float bottom=baseHeight+1.35f+floor*3.2f,top=Math.min(height-.35f,bottom+1.1f);if(top<=bottom)continue;
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
        MeshPartBuilder.VertexInfo a=new MeshPartBuilder.VertexInfo().setPos(ax,ay,az).setNor(nx,ny,nz);
        MeshPartBuilder.VertexInfo b=new MeshPartBuilder.VertexInfo().setPos(bx,by,bz).setNor(nx,ny,nz);
        MeshPartBuilder.VertexInfo c=new MeshPartBuilder.VertexInfo().setPos(cx,cy,cz).setNor(nx,ny,nz);
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

    private float terrainHeight(double globalX,double globalZ){return terrainHeightOr(globalX,globalZ,0);}

    private float terrainHeightOr(double globalX,double globalZ,float absent){
        int chunkX=pack.chunkX(globalX),chunkY=pack.chunkY(globalZ);
        QatarPack.WorldChunk terrain=nationalData.get(key(chunkX,chunkY));
        if(terrain==null)return absent;
        return pack.terrainHeight(terrain,(float)(globalX-chunkX*(double)pack.chunkMetres),(float)(globalZ-chunkY*(double)pack.chunkMetres));
    }

    private void rebuildFocusModel(){
        if(focusModel!=null)focusModel.dispose();
        renderedMode=physics.mode();
        Material maroon=diffuse(.45f,.035f,.12f,1),dark=diffuse(.10f,.12f,.13f,1),white=diffuse(.88f,.86f,.78f,1);
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
            case CAR->{builder.part("4x4",GL20.GL_TRIANGLES,ATTRS,maroon).box(0,.7f,0,2f,1.25f,4.3f);builder.part("cabin",GL20.GL_TRIANGLES,ATTRS,dark).box(0,1.45f,-.25f,1.75f,.8f,2.2f);}
            case BOAT->{builder.part("hull",GL20.GL_TRIANGLES,ATTRS,white).box(0,.4f,0,3.1f,1.1f,7.8f);builder.part("cabin",GL20.GL_TRIANGLES,ATTRS,maroon).box(0,1.35f,-.5f,2.1f,1.4f,2.7f);}
            case HELICOPTER->{builder.part("fuselage",GL20.GL_TRIANGLES,ATTRS,maroon).box(0,.5f,0,2.5f,1.5f,5.6f);builder.part("rotor",GL20.GL_TRIANGLES,ATTRS,dark).box(0,1.55f,0,9f,.05f,.18f);}
            case AEROPLANE->{builder.part("body",GL20.GL_TRIANGLES,ATTRS,white).box(0,.4f,0,1.35f,1.3f,7.4f);builder.part("wing",GL20.GL_TRIANGLES,ATTRS,maroon).box(0,.45f,.25f,8.8f,.12f,1.25f);}
        }
        focusModel=builder.end();focusInstance=new ModelInstance(focusModel);
    }

    public PerspectiveCamera camera(){return camera;}
    public String fatalStreamingError(){return fatalStreamingError;}
    public int renderChunkCount(){return chunks.size();}

    @Override public void dispose(){for(RenderChunk value:chunks.values())value.model.dispose();for(RenderChunk value:detailCells.values())value.model.dispose();chunks.clear();nationalData.clear();detailCells.clear();detailData.clear();if(focusModel!=null)focusModel.dispose();batch.dispose();}
    private static Material diffuse(float r,float g,float b,float a){return new Material(ColorAttribute.createDiffuse(new Color(r,g,b,a)));}
    private static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
    private record RenderChunk(double x,double z,Model model,ModelInstance instance){}
}
