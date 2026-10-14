package ai.arena.qatar.physics;

import ai.arena.qatar.world.QatarDetailPack;
import ai.arena.qatar.world.QatarPack;
import ai.arena.qatar.world.WorldStreamer;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btBroadphaseInterface;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btCompoundShape;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed-step, force-driven Bullet foundation shared by all five focus modes. */
public final class PhysicsWorld implements Disposable {
    public enum Mode {
        WALK("WALK", WorldStreamer.FocusMode.WALK),
        CAR("4X4", WorldStreamer.FocusMode.CAR),
        BOAT("BOAT", WorldStreamer.FocusMode.BOAT),
        HELICOPTER("HELI", WorldStreamer.FocusMode.HELICOPTER),
        AEROPLANE("PLANE", WorldStreamer.FocusMode.AEROPLANE);
        public final String label;
        public final WorldStreamer.FocusMode streamMode;
        Mode(String label, WorldStreamer.FocusMode streamMode) { this.label=label; this.streamMode=streamMode; }
        public Mode next() { return values()[(ordinal()+1)%values().length]; }
    }

    private static final float FIXED_STEP = 1f / 60f;
    private static final float REBASE_THRESHOLD = 2200f;
    private static final int MAX_DETAIL_COLLISION_CELLS = 6;
    private static final int MAX_BUILDING_COLLIDERS_PER_CELL = 160;
    private final btDefaultCollisionConfiguration configuration;
    private final btCollisionDispatcher dispatcher;
    private final btBroadphaseInterface broadphase;
    private final btSequentialImpulseConstraintSolver solver;
    private final btDiscreteDynamicsWorld world;
    private final Map<Long, ChunkCollision> chunkBodies = new HashMap<>();
    private final Map<Long, ChunkCollision> detailBodies = new HashMap<>();
    private final Map<Long, DetailSource> availableDetail = new HashMap<>();
    private int detailCellMetres;
    private long detailRevision;
    private long collisionRevision=-1;
    private int collisionCellX=Integer.MIN_VALUE,collisionCellY=Integer.MIN_VALUE;
    private final Vector3 tmp = new Vector3();
    private final Vector3 tmp2 = new Vector3();
    private final Matrix4 transform = new Matrix4();
    private btRigidBody focusBody;
    private btCollisionShape focusShape;
    private btRigidBody temporaryFloor;
    private btCollisionShape temporaryFloorShape;
    private Mode mode = Mode.WALK;
    private double originX;
    private double originZ;
    private float accumulator;
    private float headingRadians;
    private float throttle;
    private float steering;
    private float liftControl;
    private boolean action;
    private float temporaryFloorTime;

    public PhysicsWorld(double spawnX,double spawnZ,float groundHeight) {
        Bullet.init();
        configuration = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(configuration);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, configuration);
        world.setGravity(new Vector3(0, -9.80665f, 0));
        originX = spawnX;
        originZ = spawnZ;
        createTemporaryFloor(groundHeight);
        createFocusBody(mode,0,groundHeight+3.0f,0);
    }

    private void createTemporaryFloor(float surfaceY) {
        temporaryFloorShape = new btBoxShape(new Vector3(80, .5f, 80));
        temporaryFloor = body(0, temporaryFloorShape, new Matrix4().setToTranslation(0,surfaceY-.5f,0));
        world.addRigidBody(temporaryFloor);
    }

    private btRigidBody body(float mass, btCollisionShape shape, Matrix4 at) {
        Vector3 inertia = new Vector3();
        if (mass > 0) shape.calculateLocalInertia(mass, inertia);
        btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(mass, null, shape, inertia);
        btRigidBody result = new btRigidBody(info);
        info.dispose();
        result.setWorldTransform(at);
        return result;
    }

    private void createFocusBody(Mode requested, float x, float y, float z) {
        if (focusBody != null) {
            world.removeRigidBody(focusBody);
            focusBody.dispose();
            focusShape.dispose();
        }
        float mass;
        switch (requested) {
            case WALK -> { focusShape = new com.badlogic.gdx.physics.bullet.collision.btCapsuleShape(.43f, 1.05f); mass = 78f; }
            case CAR -> { focusShape = new btBoxShape(new Vector3(1.0f,.72f,2.15f)); mass = 1850f; }
            case BOAT -> { focusShape = new btBoxShape(new Vector3(1.55f,.65f,3.9f)); mass = 2900f; }
            case HELICOPTER -> { focusShape = new btBoxShape(new Vector3(1.25f,.75f,3.0f)); mass = 1250f; }
            case AEROPLANE -> { focusShape = new btBoxShape(new Vector3(4.4f,.65f,3.7f)); mass = 920f; }
            default -> throw new IllegalStateException();
        }
        focusBody = body(mass, focusShape, new Matrix4().setToTranslation(x,y,z).rotate(Vector3.Y, (float)Math.toDegrees(headingRadians)));
        focusBody.setDamping(requested == Mode.WALK ? .35f : .08f, requested == Mode.WALK ? .9f : .18f);
        if (requested == Mode.WALK) focusBody.setAngularFactor(Vector3.Y);
        focusBody.setActivationState(4); // DISABLE_DEACTIVATION
        world.addRigidBody(focusBody);
    }

    public void setControls(float throttle, float steering, float lift, boolean action) {
        this.throttle = clamp(throttle,-1,1);
        this.steering = clamp(steering,-1,1);
        this.liftControl = clamp(lift,-1,1);
        this.action = action;
    }

    public void switchMode(Mode requested,QatarPack pack) {
        if(requested==mode)return;
        Vector3 current=position();Vector3 velocity=velocity();mode=requested;
        float lift=requested==Mode.HELICOPTER?2.5f:requested==Mode.AEROPLANE?1.5f:.8f;
        createFocusBody(requested,current.x,current.y+lift,current.z);
        focusBody.setLinearVelocity(velocity.scl(requested==Mode.WALK?.35f:1));
        temporaryFloorTime=0;
    }

    public void step(float frameDelta) {
        float delta = Math.min(frameDelta,.1f);
        accumulator += delta;
        int steps=0;
        while (accumulator >= FIXED_STEP && steps < 5) {
            applyModeForces(FIXED_STEP);
            world.stepSimulation(FIXED_STEP,0,FIXED_STEP);
            accumulator -= FIXED_STEP;
            steps++;
        }
        temporaryFloorTime += delta;
        if (temporaryFloor != null && temporaryFloorTime > 3f && !chunkBodies.isEmpty()) {
            world.removeRigidBody(temporaryFloor); temporaryFloor.dispose(); temporaryFloor=null;
            temporaryFloorShape.dispose(); temporaryFloorShape=null;
        }
        maybeRebase();
        recoverIfInvalid();
        action=false;
    }

    private void applyModeForces(float dt) {
        Vector3 velocity = focusBody.getLinearVelocity();
        Vector3 forward = tmp.set((float)Math.sin(headingRadians),0,(float)Math.cos(headingRadians));
        Vector3 right = tmp2.set(forward.z,0,-forward.x);
        switch (mode) {
            case WALK -> {
                headingRadians -= steering * dt * 2.1f;
                float targetSpeed = action ? 7.0f : 4.3f;
                Vector3 desired = new Vector3(forward).scl(throttle*targetSpeed);
                Vector3 horizontal = new Vector3(velocity.x,0,velocity.z);
                focusBody.applyCentralForce(desired.sub(horizontal).scl(78f*7f));
                if (action && Math.abs(velocity.y)<.45f && position().y<2.1f) focusBody.applyCentralImpulse(new Vector3(0,265,0));
            }
            case CAR -> {
                float speedForward=velocity.dot(forward);
                headingRadians -= steering * dt * (1.15f/(1f+Math.abs(speedForward)*.025f)) * Math.signum(speedForward==0?1:speedForward);
                focusBody.applyCentralForce(new Vector3(forward).scl(throttle*9000f));
                float lateral=velocity.dot(right);
                focusBody.applyCentralForce(new Vector3(right).scl(-lateral*1850f*2.6f));
                focusBody.applyCentralForce(new Vector3(velocity.x,0,velocity.z).scl(-35f));
                alignYaw();
            }
            case BOAT -> {
                headingRadians -= steering*dt*(.45f+Math.min(1.2f,velocity.len()*.04f));
                float waterError=.4f-position().y;
                focusBody.applyCentralForce(new Vector3(0,2900f*(9.80665f+waterError*3.5f-velocity.y*1.7f),0));
                focusBody.applyCentralForce(new Vector3(forward).scl(Math.max(0,throttle)*7200f + Math.min(0,throttle)*3000f));
                focusBody.applyCentralForce(new Vector3(velocity).scl(-240f));
                alignYaw();
            }
            case HELICOPTER -> {
                headingRadians -= steering*dt*1.1f;
                float collective=1.0f+liftControl*.55f;
                focusBody.applyCentralForce(new Vector3(0,1250f*9.80665f*collective,0));
                focusBody.applyCentralForce(new Vector3(forward).scl(throttle*3400f));
                focusBody.applyCentralForce(new Vector3(velocity).scl(-55f));
                alignYaw();
            }
            case AEROPLANE -> {
                headingRadians -= steering*dt*.32f;
                float airspeed=Math.max(0,velocity.dot(forward));
                focusBody.applyCentralForce(new Vector3(forward).scl(Math.max(0,throttle)*4200f));
                float lift=Math.min(920f*9.80665f*2.2f,airspeed*airspeed*.48f);
                focusBody.applyCentralForce(new Vector3(0,lift*(1f+liftControl*.35f),0));
                focusBody.applyCentralForce(new Vector3(velocity).scl(-.48f*velocity.len()));
                alignYaw();
            }
        }
    }

    private void alignYaw() {
        Matrix4 matrix=focusBody.getWorldTransform();
        Vector3 position=matrix.getTranslation(new Vector3());
        matrix.idt().setToTranslation(position).rotate(Vector3.Y,(float)Math.toDegrees(headingRadians));
        focusBody.setWorldTransform(matrix);
    }

    private void maybeRebase() {
        Vector3 p=position();
        if (Math.max(Math.abs(p.x),Math.abs(p.z)) < REBASE_THRESHOLD) return;
        float dx=Math.round(p.x/500f)*500f, dz=Math.round(p.z/500f)*500f;
        shiftBody(focusBody,dx,dz);
        if (temporaryFloor != null) shiftBody(temporaryFloor,dx,dz);
        for (ChunkCollision chunk : chunkBodies.values()) shiftBody(chunk.body,dx,dz);
        for (ChunkCollision detail : detailBodies.values()) shiftBody(detail.body,dx,dz);
        originX += dx; originZ += dz;
    }

    private void shiftBody(btRigidBody body,float dx,float dz) {
        Matrix4 at=body.getWorldTransform();
        Vector3 p=at.getTranslation(new Vector3());
        at.setTranslation(p.x-dx,p.y,p.z-dz);
        body.setWorldTransform(at);
        body.activate();
    }

    private void recoverIfInvalid() {
        Vector3 p=position();
        if (!Float.isFinite(p.x)||!Float.isFinite(p.y)||!Float.isFinite(p.z)||p.y < -80) {
            Matrix4 at=new Matrix4().setToTranslation(0,mode==Mode.BOAT?.5f:5f,0).rotate(Vector3.Y,(float)Math.toDegrees(headingRadians));
            focusBody.setWorldTransform(at); focusBody.setLinearVelocity(Vector3.Zero); focusBody.setAngularVelocity(Vector3.Zero); focusBody.activate();
        }
    }

    public void activateChunk(QatarPack.WorldChunk chunk, int chunkMetres) {
        long key=key(chunk.entry().x(),chunk.entry().y());
        if (chunkBodies.containsKey(key)||!chunk.entry().hasLand()) return;
        btCompoundShape compound=new btCompoundShape();
        List<btCollisionShape> children=new ArrayList<>();
        float sub=chunkMetres/16f;
        for(int row=0;row<16;row++){
            int bits=chunk.landRows()[row],start=-1;float sumHeight=0,previousHeight=0;
            for(int col=0;col<=16;col++){
                boolean filled=col<16&&(bits&(1<<col))!=0;
                float height=filled?sampleTerrain(chunk,chunkMetres,(col+.5f)*sub,(row+.5f)*sub):0;
                boolean split=filled&&start>=0&&Math.abs(height-previousHeight)>.40f;
                if((!filled||split)&&start>=0){
                    int run=col-start;float top=sumHeight/run;
                    btBoxShape shape=new btBoxShape(new Vector3(run*sub/2,.75f,sub/2));children.add(shape);
                    Matrix4 child=new Matrix4().setToTranslation((start+run/2f)*sub,top-.75f,(row+.5f)*sub);
                    compound.addChildShape(child,shape);start=-1;sumHeight=0;
                }
                if(filled){if(start<0)start=col;sumHeight+=height;previousHeight=height;}
            }
        }
        float localX=(float)(chunk.entry().x()*chunkMetres-originX);
        float localZ=(float)(chunk.entry().y()*chunkMetres-originZ);
        btRigidBody body=body(0,compound,new Matrix4().setToTranslation(localX,0,localZ));
        world.addRigidBody(body);
        chunkBodies.put(key,new ChunkCollision(body,compound,children,chunk.entry().x()*chunkMetres,chunk.entry().y()*chunkMetres));
    }

    private void relocateChunkBodies() {
        for (ChunkCollision chunk : chunkBodies.values()) relocate(chunk);
        for (ChunkCollision detail : detailBodies.values()) relocate(detail);
    }

    private void relocate(ChunkCollision chunk){
        chunk.body.setWorldTransform(new Matrix4().setToTranslation((float)(chunk.globalX-originX),0,(float)(chunk.globalZ-originZ)));
        chunk.body.activate();
    }

    public void deactivateChunk(QatarPack.WorldChunk chunk) {
        ChunkCollision value=chunkBodies.remove(key(chunk.entry().x(),chunk.entry().y()));
        if (value!=null) value.dispose(world);
    }

    /** Registers decoded visual detail without immediately creating thousands of Bullet shapes. */
    public void registerDetailChunk(QatarDetailPack.DetailChunk chunk,int cellMetres,QatarPack.WorldChunk terrain,QatarPack pack){
        if(detailCellMetres!=0&&detailCellMetres!=cellMetres)throw new IllegalArgumentException("Detail cell-size mismatch");
        detailCellMetres=cellMetres;long detailKey=key(chunk.entry().x(),chunk.entry().y());
        availableDetail.put(detailKey,new DetailSource(chunk,terrain,pack));detailRevision++;
        ChunkCollision previous=detailBodies.remove(detailKey);if(previous!=null)previous.dispose(world);
    }

    public void unregisterDetailChunk(QatarDetailPack.DetailChunk chunk){
        long key=key(chunk.entry().x(),chunk.entry().y());availableDetail.remove(key);detailRevision++;
        ChunkCollision value=detailBodies.remove(key);if(value!=null)value.dispose(world);
    }

    /** Keeps collision around the focus only, with hard cell and footprint caps for mobile thermals. */
    public void syncDetailCollisions(double focusGlobalX,double focusGlobalZ){
        if(detailCellMetres<=0)return;
        int focusCellX=(int)Math.floor(focusGlobalX/detailCellMetres),focusCellY=(int)Math.floor(focusGlobalZ/detailCellMetres);
        if(collisionRevision==detailRevision&&focusCellX==collisionCellX&&focusCellY==collisionCellY)return;
        collisionRevision=detailRevision;collisionCellX=focusCellX;collisionCellY=focusCellY;
        double radius=detailCellMetres*1.45,radiusSquared=radius*radius;
        List<DetailCandidate> candidates=new ArrayList<>();
        for(Map.Entry<Long,DetailSource> item:availableDetail.entrySet()){
            QatarDetailPack.DetailEntry entry=item.getValue().chunk().entry();
            double minX=entry.x()*(double)detailCellMetres,minZ=entry.y()*(double)detailCellMetres;
            double dx=axisDistance(focusGlobalX,minX,minX+detailCellMetres),dz=axisDistance(focusGlobalZ,minZ,minZ+detailCellMetres);
            double distanceSquared=dx*dx+dz*dz;
            if(distanceSquared<=radiusSquared)candidates.add(new DetailCandidate(item.getKey(),item.getValue(),distanceSquared));
        }
        candidates.sort(Comparator.comparingDouble(DetailCandidate::distanceSquared));
        Set<Long> wanted=new HashSet<>();
        for(int i=0;i<Math.min(MAX_DETAIL_COLLISION_CELLS,candidates.size());i++){
            DetailCandidate candidate=candidates.get(i);wanted.add(candidate.key());
            if(!detailBodies.containsKey(candidate.key()))activateDetailCollision(candidate.source(),focusGlobalX,focusGlobalZ);
        }
        List<Long> departing=new ArrayList<>();for(long key:detailBodies.keySet())if(!wanted.contains(key))departing.add(key);
        for(long key:departing){ChunkCollision value=detailBodies.remove(key);if(value!=null)value.dispose(world);}
    }

    private void activateDetailCollision(DetailSource detail,double focusGlobalX,double focusGlobalZ){
        QatarDetailPack.DetailChunk chunk=detail.chunk();QatarPack.Building[] source=chunk.buildings();if(source.length==0)return;
        List<QatarPack.Building> buildings=new ArrayList<>(source.length);
        for(QatarPack.Building building:source)if(building.width()>=.5f&&building.depth()>=.5f&&building.height()>=.5f)buildings.add(building);
        final double cellX=chunk.entry().x()*(double)detailCellMetres,cellZ=chunk.entry().y()*(double)detailCellMetres;
        if(buildings.size()>MAX_BUILDING_COLLIDERS_PER_CELL){
            buildings.sort(Comparator.comparingDouble(building->square(cellX+building.x()-focusGlobalX,cellZ+building.z()-focusGlobalZ)));
            buildings=buildings.subList(0,MAX_BUILDING_COLLIDERS_PER_CELL);
        }
        btCompoundShape compound=new btCompoundShape();List<btCollisionShape> children=new ArrayList<>(buildings.size());
        for(QatarPack.Building building:buildings){
            btBoxShape shape=new btBoxShape(new Vector3(building.width()/2,building.height()/2,building.depth()/2));children.add(shape);
            float base=detail.terrain()==null?0:sampleTerrain(detail.terrain(),detail.pack().chunkMetres,
                    (float)(cellX+building.x()-detail.terrain().entry().x()*(double)detail.pack().chunkMetres),
                    (float)(cellZ+building.z()-detail.terrain().entry().y()*(double)detail.pack().chunkMetres));
            Matrix4 child=new Matrix4().setToTranslation(building.x(),base+building.height()/2,building.z()).rotate(Vector3.Y,(float)Math.toDegrees(building.yaw()));
            compound.addChildShape(child,shape);
        }
        if(children.isEmpty()){compound.dispose();return;}
        btRigidBody body=body(0,compound,new Matrix4().setToTranslation((float)(cellX-originX),0,(float)(cellZ-originZ)));
        world.addRigidBody(body);detailBodies.put(key(chunk.entry().x(),chunk.entry().y()),new ChunkCollision(body,compound,children,cellX,cellZ));
    }

    private void clearChunkBodies() {
        for (ChunkCollision value:chunkBodies.values()) value.dispose(world);
        for (ChunkCollision value:detailBodies.values()) value.dispose(world);
        chunkBodies.clear();detailBodies.clear();availableDetail.clear();
    }

    /** Explicit user recovery is the only normal gameplay teleport. */
    public void recover() {
        Matrix4 at=new Matrix4().setToTranslation(0,mode==Mode.BOAT?.5f:5f,0).rotate(Vector3.Y,(float)Math.toDegrees(headingRadians));
        focusBody.setWorldTransform(at);
        focusBody.setLinearVelocity(Vector3.Zero);
        focusBody.setAngularVelocity(Vector3.Zero);
        focusBody.clearForces();
        focusBody.activate();
    }

    public Vector3 position() { return focusBody.getWorldTransform().getTranslation(new Vector3()); }
    public Vector3 velocity() { return new Vector3(focusBody.getLinearVelocity()); }
    public Matrix4 transform() { return new Matrix4(focusBody.getWorldTransform()); }
    public double globalX() { return originX + position().x; }
    public double globalZ() { return originZ + position().z; }
    public double originX() { return originX; }
    public double originZ() { return originZ; }
    public float headingRadians() { return headingRadians; }
    public Mode mode() { return mode; }

    @Override public void dispose() {
        clearChunkBodies();
        if (temporaryFloor!=null) { world.removeRigidBody(temporaryFloor); temporaryFloor.dispose(); temporaryFloorShape.dispose(); }
        if (focusBody!=null) { world.removeRigidBody(focusBody); focusBody.dispose(); focusShape.dispose(); }
        world.dispose(); solver.dispose(); broadphase.dispose(); dispatcher.dispose(); configuration.dispose();
    }

    private record DetailSource(QatarDetailPack.DetailChunk chunk,QatarPack.WorldChunk terrain,QatarPack pack){}
    private record DetailCandidate(long key,DetailSource source,double distanceSquared){}

    private static final class ChunkCollision {
        final btRigidBody body; final btCompoundShape compound; final List<btCollisionShape> children;
        final double globalX,globalZ;
        ChunkCollision(btRigidBody body,btCompoundShape compound,List<btCollisionShape> children,double globalX,double globalZ){this.body=body;this.compound=compound;this.children=children;this.globalX=globalX;this.globalZ=globalZ;}
        void dispose(btDiscreteDynamicsWorld world){world.removeRigidBody(body);body.dispose();compound.dispose();for(btCollisionShape shape:children)shape.dispose();}
    }
    private static float sampleTerrain(QatarPack.WorldChunk chunk,int chunkMetres,float localX,float localZ){
        float interval=chunkMetres/8f,gx=Math.max(0,Math.min(8,localX/interval)),gz=Math.max(0,Math.min(8,localZ/interval));
        int x0=Math.min(7,(int)Math.floor(gx)),z0=Math.min(7,(int)Math.floor(gz)),x1=Math.min(8,x0+1),z1=Math.min(8,z0+1);float tx=gx-x0,tz=gz-z0;short[] v=chunk.terrainCm();
        float a=v[z0*9+x0]*(1-tx)+v[z0*9+x1]*tx,b=v[z1*9+x0]*(1-tx)+v[z1*9+x1]*tx;return(a*(1-tz)+b*tz)/100f;
    }
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
    private static double axisDistance(double value,double min,double max){return value<min?min-value:value>max?value-max:0;}
    private static double square(double x,double y){return x*x+y*y;}
    private static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
}
