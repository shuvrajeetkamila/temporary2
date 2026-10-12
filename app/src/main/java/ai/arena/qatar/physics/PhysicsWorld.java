package ai.arena.qatar.physics;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final btDefaultCollisionConfiguration configuration;
    private final btCollisionDispatcher dispatcher;
    private final btBroadphaseInterface broadphase;
    private final btSequentialImpulseConstraintSolver solver;
    private final btDiscreteDynamicsWorld world;
    private final Map<Long, ChunkCollision> chunkBodies = new HashMap<>();
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

    public PhysicsWorld(double spawnX, double spawnZ) {
        Bullet.init();
        configuration = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(configuration);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, configuration);
        world.setGravity(new Vector3(0, -9.80665f, 0));
        originX = spawnX;
        originZ = spawnZ;
        createTemporaryFloor();
        createFocusBody(mode, 0, 3.0f, 0);
    }

    private void createTemporaryFloor() {
        temporaryFloorShape = new btBoxShape(new Vector3(80, .5f, 80));
        temporaryFloor = body(0, temporaryFloorShape, new Matrix4().setToTranslation(0,-.5f,0));
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

    public void switchMode(Mode requested, QatarPack pack) {
        if (requested == mode) return;
        double targetX, targetZ;
        float altitude;
        if (requested == Mode.AEROPLANE) {
            QatarPack.Hub hub = pack.hub("Hamad International Airport"); targetX=hub.x(); targetZ=hub.z(); altitude=6;
        } else if (requested == Mode.BOAT) {
            QatarPack.Hub hub = pack.hub("Doha Corniche"); targetX=hub.x()+2600; targetZ=hub.z()+700; altitude=.4f;
        } else {
            QatarPack.Hub hub = pack.hub("Doha Corniche"); targetX=hub.x(); targetZ=hub.z(); altitude=requested==Mode.HELICOPTER?4:3;
        }
        mode=requested;
        originX=targetX; originZ=targetZ;
        headingRadians=0;
        clearChunkBodies();
        createFocusBody(requested,0,altitude,0);
        if (temporaryFloor == null) createTemporaryFloor();
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
        for (int row=0;row<16;row++) {
            int bits=chunk.landRows()[row];
            int start=-1;
            for (int col=0;col<=16;col++) {
                boolean filled=col<16 && (bits&(1<<col))!=0;
                if (filled&&start<0) start=col;
                if (!filled&&start>=0) {
                    int run=col-start;
                    btBoxShape shape=new btBoxShape(new Vector3(run*sub/2,.5f,sub/2));
                    children.add(shape);
                    Matrix4 child=new Matrix4().setToTranslation((start+run/2f)*sub,-.5f,(row+.5f)*sub);
                    compound.addChildShape(child,shape); start=-1;
                }
            }
        }
        int collisionBuildings=Math.min(48,chunk.buildings().length);
        for (int i=0;i<collisionBuildings;i++) {
            QatarPack.Building b=chunk.buildings()[i];
            btBoxShape shape=new btBoxShape(new Vector3(b.width()/2,b.height()/2,b.depth()/2));
            children.add(shape);
            Matrix4 child=new Matrix4().setToTranslation(b.x(),b.height()/2,b.z()).rotate(Vector3.Y,(float)Math.toDegrees(b.yaw()));
            compound.addChildShape(child,shape);
        }
        float localX=(float)(chunk.entry().x()*chunkMetres-originX);
        float localZ=(float)(chunk.entry().y()*chunkMetres-originZ);
        btRigidBody body=body(0,compound,new Matrix4().setToTranslation(localX,0,localZ));
        world.addRigidBody(body);
        chunkBodies.put(key,new ChunkCollision(body,compound,children));
    }

    public void deactivateChunk(QatarPack.WorldChunk chunk) {
        ChunkCollision value=chunkBodies.remove(key(chunk.entry().x(),chunk.entry().y()));
        if (value!=null) value.dispose(world);
    }

    private void clearChunkBodies() {
        for (ChunkCollision value:chunkBodies.values()) value.dispose(world);
        chunkBodies.clear();
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

    private static final class ChunkCollision {
        final btRigidBody body; final btCompoundShape compound; final List<btCollisionShape> children;
        ChunkCollision(btRigidBody body,btCompoundShape compound,List<btCollisionShape> children){this.body=body;this.compound=compound;this.children=children;}
        void dispose(btDiscreteDynamicsWorld world){world.removeRigidBody(body);body.dispose();compound.dispose();for(btCollisionShape shape:children)shape.dispose();}
    }
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
    private static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
}
