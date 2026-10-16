package ai.arena.qatar.render;

import ai.arena.qatar.performance.ThermalGovernor;
import ai.arena.qatar.physics.PhysicsWorld;
import ai.arena.qatar.world.QatarDetailPack;
import ai.arena.qatar.world.QatarPack;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Bounded visual traffic and pedestrian activity constrained to resident OSM
 * routes. Actors are deliberately non-colliding in this preproduction layer.
 */
final class AmbientWorldActivity implements Disposable {
    private static final long ATTRS=VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal|VertexAttributes.Usage.TextureCoordinates;
    private static final int MAX_CARS=10,MAX_PEDESTRIANS=8,MAX_PARKED=3,MAX_BOATS=2,MAX_AIRCRAFT=2;
    private final PhysicsWorld physics;
    private final QatarWorldRenderer world;
    private final Model carModel,pedestrianModel,boatModel,aircraftModel;
    private final ModelInstance[] cars=instances(MAX_CARS+MAX_PARKED),pedestrians=instances(MAX_PEDESTRIANS),boats=instances(MAX_BOATS),aircraft=instances(MAX_AIRCRAFT);
    private final List<Route> carRoutes=new ArrayList<>(),walkRoutes=new ArrayList<>(),runwayRoutes=new ArrayList<>();
    private long routeRevision=Long.MIN_VALUE;
    private float elapsed,lastRouteBuildTime=-10f;
    private boolean logged;

    AmbientWorldActivity(PhysicsWorld physics,QatarWorldRenderer world){
        this.physics=physics;this.world=world;
        carModel=buildCar();pedestrianModel=buildPedestrian();boatModel=buildBoat();aircraftModel=buildAircraft();
        fill(cars,carModel);fill(pedestrians,pedestrianModel);fill(boats,boatModel);fill(aircraft,aircraftModel);
    }

    void render(ModelBatch batch,Environment environment,Map<Long,QatarDetailPack.DetailChunk> details,long revision,ThermalGovernor.Profile profile,float delta,float seaLevel){
        elapsed+=Math.min(.1f,Math.max(0,delta));
        if(routeRevision!=revision&&(carRoutes.isEmpty()||elapsed-lastRouteBuildTime>=.8f)){rebuildRoutes(details,revision);lastRouteBuildTime=elapsed;}
        int carCap=Math.min(MAX_CARS,profile.ambientCars),pedestrianCap=Math.min(MAX_PEDESTRIANS,profile.ambientPedestrians);
        int parkedCap=switch(profile){case NORMAL->MAX_PARKED;case WARM->2;case HOT->0;};
        int boatCap=Math.min(MAX_BOATS,profile.ambientBoats);
        int aircraftCap=switch(profile){case NORMAL,WARM->MAX_AIRCRAFT;case HOT->0;};
        for(int i=0;i<Math.min(carCap,carRoutes.size());i++){Route route=carRoutes.get(i);placeMoving(cars[i],route,elapsed*(7.5f+i%3*1.2f),.52f,0);batch.render(cars[i],environment);}
        for(int i=0;i<Math.min(parkedCap,Math.max(0,carRoutes.size()-carCap));i++){Route route=carRoutes.get(carCap+i);placeStatic(cars[carCap+i],route,.17f+(i*.29f)% .70f,.50f,route.width*.5f+1.1f);batch.render(cars[carCap+i],environment);}
        for(int i=0;i<Math.min(pedestrianCap,walkRoutes.size());i++){Route route=walkRoutes.get(i);placeMoving(pedestrians[i],route,elapsed*(1.15f+i%2*.25f),.92f,route.width*.5f+.65f);batch.render(pedestrians[i],environment);}
        renderBoats(batch,environment,boatCap,seaLevel);
        for(int i=0;i<Math.min(aircraftCap,runwayRoutes.size());i++){placeStatic(aircraft[i],runwayRoutes.get(i),.22f+i*.31f,.68f,0);batch.render(aircraft[i],environment);}
        if(!logged&&(!carRoutes.isEmpty()||!walkRoutes.isEmpty())){logged=true;Gdx.app.log("QatarActivity","V09_ACTIVITY_READY trafficRoutes="+carRoutes.size()+" pedestrianRoutes="+walkRoutes.size()+" runwayRoutes="+runwayRoutes.size()+" caps="+MAX_CARS+"/"+MAX_PEDESTRIANS+"/"+MAX_BOATS+" nonColliding=yes thermalBounded=yes");}
    }

    private void rebuildRoutes(Map<Long,QatarDetailPack.DetailChunk> details,long revision){
        routeRevision=revision;carRoutes.clear();walkRoutes.clear();runwayRoutes.clear();
        List<Route> allCars=new ArrayList<>(),allWalks=new ArrayList<>(),allRunways=new ArrayList<>();
        for(QatarDetailPack.DetailChunk chunk:details.values()){
            double ox=chunk.entry().x()*250.0,oz=chunk.entry().y()*250.0;QatarPack.Road[] roads=chunk.roads();long[] ids=chunk.roadSourceIds();
            for(int i=0;i<roads.length;i++){
                QatarPack.Road road=roads[i];float length=(float)Math.hypot(road.x2()-road.x1(),road.z2()-road.z1());if(length<8)continue;
                long hash=mix(ids[i]^(chunk.entry().x()*73856093L)^(chunk.entry().y()*19349663L)^i);
                Route route=new Route(ox+road.x1(),oz+road.z1(),ox+road.x2(),oz+road.z2(),road.width(),hash);
                if(road.roadClass()<=6&&!road.tunnel()&&Math.floorMod(hash,5)<2)allCars.add(route);
                if(road.roadClass()>=5&&road.roadClass()<=8&&Math.floorMod(hash,4)==0)allWalks.add(route);
                if(road.roadClass()==10)allRunways.add(route);
            }
        }
        Comparator<Route> order=Comparator.comparingLong(Route::hash);
        allCars.sort(order);allWalks.sort(order);allRunways.sort(order);
        carRoutes.addAll(allCars.subList(0,Math.min(96,allCars.size())));walkRoutes.addAll(allWalks.subList(0,Math.min(64,allWalks.size())));runwayRoutes.addAll(allRunways.subList(0,Math.min(8,allRunways.size())));
    }

    private void placeMoving(ModelInstance instance,Route route,float metres,float lift,float lateral){
        double length=Math.hypot(route.x2-route.x1,route.z2-route.z1);float phase=(float)((Math.floorMod(route.hash,10000)/10000.0+metres/Math.max(1,length))%1.0);
        place(instance,route,phase,lift,lateral);
    }

    private void placeStatic(ModelInstance instance,Route route,float phase,float lift,float lateral){place(instance,route,phase,lift,lateral);}

    private void place(ModelInstance instance,Route route,float t,float lift,float lateral){
        double dx=route.x2-route.x1,dz=route.z2-route.z1,length=Math.max(.01,Math.hypot(dx,dz));double nx=-dz/length,nz=dx/length;
        double globalX=route.x1+dx*t+nx*lateral,globalZ=route.z1+dz*t+nz*lateral;
        float terrain=world.terrainHeightAt(globalX,globalZ);if(!Float.isFinite(terrain))terrain=0;
        float heading=(float)Math.toDegrees(Math.atan2(dx,dz));instance.transform.setToTranslation((float)(globalX-physics.originX()),terrain+lift,(float)(globalZ-physics.originZ())).rotate(Vector3.Y,heading);
    }

    private void renderBoats(ModelBatch batch,Environment environment,int cap,float seaLevel){
        if(cap<=0)return;double focusX=physics.globalX(),focusZ=physics.globalZ();int rendered=0;
        for(int candidate=0;candidate<12&&rendered<cap;candidate++){
            double angle=elapsed*.035+candidate*2.39996,radius=180+candidate*43;double x=focusX+Math.sin(angle)*radius,z=focusZ+Math.cos(angle)*radius;
            if(!world.waterAt(x,z))continue;ModelInstance boat=boats[rendered++];boat.transform.setToTranslation((float)(x-physics.originX()),seaLevel+.35f,(float)(z-physics.originZ())).rotate(Vector3.Y,(float)Math.toDegrees(angle)+90);
            batch.render(boat,environment);
        }
    }

    private static Model buildCar(){
        ModelBuilder b=new ModelBuilder();Material body=mat(.20f,.38f,.52f),dark=mat(.045f,.05f,.052f),glass=shiny(.07f,.17f,.20f);b.begin();
        b.part("ambient-car-body",GL20.GL_TRIANGLES,ATTRS,body).box(0,0,0,1.55f,.56f,3.45f);b.part("ambient-car-cabin",GL20.GL_TRIANGLES,ATTRS,glass).box(0,.47f,-.20f,1.30f,.58f,1.62f);
        MeshPartBuilder wheels=b.part("ambient-car-wheels",GL20.GL_TRIANGLES,ATTRS,dark);for(float x:new float[]{-.78f,.78f})for(float z:new float[]{-1.05f,1.05f}){wheels.setVertexTransform(new Matrix4().setToTranslation(x,-.27f,z).rotate(Vector3.Z,90));wheels.cylinder(.48f,.20f,.48f,10);}wheels.setVertexTransform(null);return b.end();
    }
    private static Model buildPedestrian(){ModelBuilder b=new ModelBuilder();b.begin();Material cloth=mat(.45f,.18f,.16f),skin=mat(.60f,.42f,.30f),dark=mat(.08f,.09f,.10f);b.part("ambient-ped-body",GL20.GL_TRIANGLES,ATTRS,cloth).capsule(.20f,.82f,8);MeshPartBuilder head=b.part("ambient-ped-head",GL20.GL_TRIANGLES,ATTRS,skin);head.setVertexTransform(new Matrix4().setToTranslation(0,.58f,0));head.sphere(.30f,.34f,.30f,9,6);head.setVertexTransform(null);MeshPartBuilder legs=b.part("ambient-ped-legs",GL20.GL_TRIANGLES,ATTRS,dark);for(float x:new float[]{-.10f,.10f}){legs.setVertexTransform(new Matrix4().setToTranslation(x,-.53f,0));legs.capsule(.07f,.52f,7);}legs.setVertexTransform(null);return b.end();}
    private static Model buildBoat(){ModelBuilder b=new ModelBuilder();b.begin();Material hull=mat(.82f,.84f,.80f),trim=mat(.48f,.04f,.13f);MeshPartBuilder h=b.part("ambient-boat-hull",GL20.GL_TRIANGLES,ATTRS,hull);h.setVertexTransform(new Matrix4().setToScale(1,.45f,1));h.sphere(1.5f,.72f,3.8f,14,8);h.setVertexTransform(null);b.part("ambient-boat-deck",GL20.GL_TRIANGLES,ATTRS,trim).box(0,.28f,-.2f,1.35f,.15f,2.45f);return b.end();}
    private static Model buildAircraft(){ModelBuilder b=new ModelBuilder();b.begin();Material white=mat(.84f,.84f,.80f),trim=mat(.50f,.04f,.13f);MeshPartBuilder fuselage=b.part("ambient-aircraft",GL20.GL_TRIANGLES,ATTRS,white);fuselage.setVertexTransform(new Matrix4().rotate(Vector3.X,90));fuselage.capsule(.38f,4.4f,12);fuselage.setVertexTransform(null);b.part("ambient-wing",GL20.GL_TRIANGLES,ATTRS,trim).box(0,0,0,5.2f,.10f,.70f);return b.end();}

    private static Material mat(float r,float g,float b){return new Material(ColorAttribute.createDiffuse(new Color(r,g,b,1)));}
    private static Material shiny(float r,float g,float b){return new Material(ColorAttribute.createDiffuse(new Color(r,g,b,1)),ColorAttribute.createSpecular(new Color(.65f,.75f,.78f,1)),FloatAttribute.createShininess(70));}
    private static ModelInstance[] instances(int size){return new ModelInstance[size];}
    private static void fill(ModelInstance[] target,Model model){for(int i=0;i<target.length;i++)target[i]=new ModelInstance(model);}
    private static long mix(long value){value^=value>>>33;value*=0xff51afd7ed558ccdL;value^=value>>>33;value*=0xc4ceb9fe1a85ec53L;return value^(value>>>33);}
    @Override public void dispose(){carModel.dispose();pedestrianModel.dispose();boatModel.dispose();aircraftModel.dispose();carRoutes.clear();walkRoutes.clear();runwayRoutes.clear();}
    private record Route(double x1,double z1,double x2,double z2,float width,long hash){}
}
