package ai.arena.qatar.render;

import ai.arena.qatar.physics.PhysicsWorld;
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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

import java.util.HashMap;
import java.util.Map;

/** Qatar-specific mobile 3D renderer. All geometry is generated, never imagery. */
public final class QatarWorldRenderer implements WorldStreamer.Listener, Disposable {
    private static final long ATTRS = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
    private final QatarPack pack;
    private final PhysicsWorld physics;
    private final PerspectiveCamera camera;
    private final ModelBatch batch = new ModelBatch();
    private final ModelBuilder builder = new ModelBuilder();
    private final Environment environment = new Environment();
    private final Map<Long, RenderChunk> chunks = new HashMap<>();
    private final Map<Integer, Material> terrainMaterials = new HashMap<>();
    private final Material roadMaterial = diffuse(.20f,.22f,.23f,1);
    private final Material markingMaterial = diffuse(.88f,.82f,.62f,1);
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

    public QatarWorldRenderer(QatarPack pack, PhysicsWorld physics) {
        this.pack=pack; this.physics=physics;
        camera=new PerspectiveCamera(64,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
        camera.near=.15f; camera.far=14_000f;
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight,.40f,.38f,.34f,1));
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
        focusInstance.transform.set(physics.transform());
        batch.render(focusInstance,environment);
        batch.end();
    }

    private void updateTransforms() {
        for (RenderChunk value:chunks.values()) {
            float x=(float)(value.x-physics.originX());
            float z=(float)(value.z-physics.originZ());
            value.instance.transform.setToTranslation(x,0,z);
        }
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
        physics.activateChunk(chunk,pack.chunkMetres);
        Model model=buildChunk(chunk);
        long key=key(chunk.entry().x(),chunk.entry().y());
        chunks.put(key,new RenderChunk(chunk.entry().x()*pack.chunkMetres,chunk.entry().y()*pack.chunkMetres,model,new ModelInstance(model)));
    }

    @Override public void deactivate(QatarPack.WorldChunk chunk) {
        physics.deactivateChunk(chunk);
        RenderChunk removed=chunks.remove(key(chunk.entry().x(),chunk.entry().y()));
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
            int bits=chunk.landRows()[row],start=-1;
            for(int col=0;col<=16;col++){
                boolean filled=col<16&&(bits&(1<<col))!=0;
                if(filled&&start<0)start=col;
                if(!filled&&start>=0){int run=col-start;land.box((start+run/2f)*sub,-.1f,(row+.5f)*sub,run*sub,.2f,sub);start=-1;}
            }
        }
        if(chunk.roads().length>0){
            MeshPartBuilder roads=builder.part("roads",GL20.GL_TRIANGLES,ATTRS,roadMaterial);
            for(QatarPack.Road r:chunk.roads()) roadQuad(roads,r,.13f,r.width());
            MeshPartBuilder marks=builder.part("markings",GL20.GL_TRIANGLES,ATTRS,markingMaterial);
            for(QatarPack.Road r:chunk.roads()) roadQuad(marks,r,.145f,.20f);
        }
        MeshPartBuilder towers=builder.part("towers",GL20.GL_TRIANGLES,ATTRS,towerMaterial);
        MeshPartBuilder villas=builder.part("villas",GL20.GL_TRIANGLES,ATTRS,villaMaterial);
        MeshPartBuilder industry=builder.part("industry",GL20.GL_TRIANGLES,ATTRS,industrialMaterial);
        MeshPartBuilder farms=builder.part("farms",GL20.GL_TRIANGLES,ATTRS,farmMaterial);
        Matrix4 buildingTransform=new Matrix4();
        for(QatarPack.Building b:chunk.buildings()){
            MeshPartBuilder target=switch(b.archetype()){case 1->towers;case 4->industry;case 5->farms;default->villas;};
            target.setVertexTransform(buildingTransform.idt().translate(b.x(),b.height()/2,b.z()).rotate(Vector3.Y,(float)Math.toDegrees(b.yaw())));
            target.box(0,0,0,b.width(),b.height(),b.depth());
        }
        towers.setVertexTransform(null);villas.setVertexTransform(null);industry.setVertexTransform(null);farms.setVertexTransform(null);
        return builder.end();
    }

    private static void roadQuad(MeshPartBuilder part,QatarPack.Road r,float y,float width){
        float dx=r.x2()-r.x1(),dz=r.z2()-r.z1(),length=(float)Math.hypot(dx,dz);
        if(length<.01f)return;
        float px=-dz/length*width/2,pz=dx/length*width/2;
        part.rect(r.x1()+px,y,r.z1()+pz,r.x1()-px,y,r.z1()-pz,r.x2()-px,y,r.z2()-pz,r.x2()+px,y,r.z2()+pz,0,1,0);
    }

    private void rebuildFocusModel(){
        if(focusModel!=null)focusModel.dispose();
        renderedMode=physics.mode();
        Material maroon=diffuse(.45f,.035f,.12f,1),dark=diffuse(.10f,.12f,.13f,1),white=diffuse(.88f,.86f,.78f,1);
        builder.begin();
        switch(renderedMode){
            case WALK->{
                MeshPartBuilder torso=builder.part("body",GL20.GL_TRIANGLES,ATTRS,maroon);
                torso.box(0,1.30f,0,.66f,1.05f,.40f);
                MeshPartBuilder skin=builder.part("skin",GL20.GL_TRIANGLES,ATTRS,white);
                skin.setVertexTransform(new Matrix4().setToTranslation(0,2.08f,0));
                skin.sphere(.50f,.54f,.50f,14,10);
                skin.setVertexTransform(null);
                MeshPartBuilder limbs=builder.part("limbs",GL20.GL_TRIANGLES,ATTRS,dark);
                limbs.box(-.19f,.48f,0,.24f,.98f,.28f);
                limbs.box(.19f,.48f,0,.24f,.98f,.28f);
                limbs.box(-.47f,1.31f,0,.20f,.92f,.24f);
                limbs.box(.47f,1.31f,0,.20f,.92f,.24f);
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

    @Override public void dispose(){for(RenderChunk value:chunks.values())value.model.dispose();chunks.clear();if(focusModel!=null)focusModel.dispose();batch.dispose();}
    private static Material diffuse(float r,float g,float b,float a){return new Material(ColorAttribute.createDiffuse(new Color(r,g,b,a)));}
    private static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
    private record RenderChunk(double x,double z,Model model,ModelInstance instance){}
}
