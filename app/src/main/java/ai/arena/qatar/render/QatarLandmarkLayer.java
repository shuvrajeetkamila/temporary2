package ai.arena.qatar.render;

import ai.arena.qatar.performance.ThermalGovernor;
import ai.arena.qatar.physics.PhysicsWorld;
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
import java.util.List;

/** Original, bounded landmark silhouettes for key public gameplay anchors. */
final class QatarLandmarkLayer implements Disposable {
    private static final long ATTRS=VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal|VertexAttributes.Usage.TextureCoordinates;
    static final double LEGTAIFIYA_X=85_085.928385,LEGTAIFIYA_Z=106_707.982144;
    private final PhysicsWorld physics;
    private final QatarWorldRenderer world;
    private final Model metroModel,skylineModel,airportModel,portModel,heritageModel;
    private final List<Placement> placements=new ArrayList<>();

    QatarLandmarkLayer(QatarPack pack,PhysicsWorld physics,QatarWorldRenderer world){
        this.physics=physics;this.world=world;metroModel=buildMetro();skylineModel=buildSkyline();airportModel=buildAirport();portModel=buildPort();heritageModel=buildHeritage();
        placements.add(new Placement("Legtaifiya Metro",LEGTAIFIYA_X,LEGTAIFIYA_Z,new ModelInstance(metroModel)));
        addHub(pack,"West Bay",skylineModel);addHub(pack,"Lusail",skylineModel);addHub(pack,"Hamad International Airport",airportModel);addHub(pack,"Hamad Port",portModel);addHub(pack,"Al Zubarah",heritageModel);
        Gdx.app.log("QatarLandmarks","V09_LANDMARK_LAYER_READY originalModels=5 placements="+placements.size()+" Legtaifiya="+LEGTAIFIYA_X+","+LEGTAIFIYA_Z+" boundedLOD=yes");
    }

    private void addHub(QatarPack pack,String name,Model model){QatarPack.Hub hub=pack.hub(name);placements.add(new Placement(name,hub.x(),hub.z(),new ModelInstance(model)));}

    void render(ModelBatch batch,Environment environment,ThermalGovernor.Profile profile){
        double radius=6500*profile.viewDistanceScale,radius2=radius*radius,focusX=physics.globalX(),focusZ=physics.globalZ();int rendered=0;
        for(Placement placement:placements){double dx=placement.x-focusX,dz=placement.z-focusZ;if(dx*dx+dz*dz>radius2)continue;float measured=world.rawTerrainHeightAt(placement.x,placement.z);float base=CoastalFeaturePolicy.buildingBase(measured,world.landMaskAt(placement.x,placement.z));placement.instance.transform.setToTranslation((float)(placement.x-physics.originX()),base,(float)(placement.z-physics.originZ()));batch.render(placement.instance,environment);if(++rendered>=profile.heroLandmarks)break;}
    }

    private static Model buildMetro(){
        ModelBuilder b=new ModelBuilder();Material stone=mat(.82f,.78f,.68f),maroon=mat(.52f,.035f,.14f),glass=shiny(.10f,.26f,.30f),steel=mat(.28f,.31f,.32f);b.begin();
        b.part("metro-platform",GL20.GL_TRIANGLES,ATTRS,stone).box(0,.30f,0,34f,.60f,11f);
        MeshPartBuilder canopy=b.part("metro-canopy",GL20.GL_TRIANGLES,ATTRS,maroon);for(float z:new float[]{-4.4f,4.4f})canopy.box(0,5.2f,z,32f,.42f,1.4f);
        MeshPartBuilder columns=b.part("metro-columns",GL20.GL_TRIANGLES,ATTRS,steel);for(float x:new float[]{-14,-7,0,7,14})for(float z:new float[]{-4.3f,4.3f}){columns.setVertexTransform(new Matrix4().setToTranslation(x,2.8f,z));columns.cylinder(.40f,5.2f,.40f,9);}columns.setVertexTransform(null);
        b.part("metro-glazed-entry",GL20.GL_TRIANGLES,ATTRS,glass).box(0,2.25f,0,12f,3.9f,6f);b.part("metro-sign",GL20.GL_TRIANGLES,ATTRS,maroon).box(0,4.7f,5.3f,8f,1.15f,.25f);return b.end();
    }
    private static Model buildSkyline(){ModelBuilder b=new ModelBuilder();Material glass=shiny(.20f,.42f,.48f),stone=mat(.78f,.73f,.64f),maroon=mat(.48f,.035f,.13f);b.begin();float[] x={-18,-7,6,17},h={42,61,50,36};for(int i=0;i<x.length;i++){b.part("tower-"+i,GL20.GL_TRIANGLES,ATTRS,i%2==0?glass:stone).box(x[i],h[i]/2,0,9,h[i],9);b.part("crown-"+i,GL20.GL_TRIANGLES,ATTRS,maroon).box(x[i],h[i]+1.1f,0,9.6f,2.2f,9.6f);}return b.end();}
    private static Model buildAirport(){ModelBuilder b=new ModelBuilder();Material terminal=mat(.77f,.76f,.71f),glass=shiny(.10f,.27f,.31f),maroon=mat(.50f,.035f,.13f);b.begin();b.part("terminal",GL20.GL_TRIANGLES,ATTRS,terminal).box(0,3.2f,0,62f,6.4f,22f);b.part("terminal-glass",GL20.GL_TRIANGLES,ATTRS,glass).box(0,4.3f,11.2f,54f,4.2f,.45f);MeshPartBuilder tower=b.part("control-tower",GL20.GL_TRIANGLES,ATTRS,terminal);tower.setVertexTransform(new Matrix4().setToTranslation(22,14,-15));tower.cylinder(3.2f,28f,3.2f,14);tower.setVertexTransform(null);MeshPartBuilder cab=b.part("control-cab",GL20.GL_TRIANGLES,ATTRS,glass);cab.setVertexTransform(new Matrix4().setToTranslation(22,28,-15));cab.sphere(7f,4f,7f,14,7);cab.setVertexTransform(null);b.part("airport-crown",GL20.GL_TRIANGLES,ATTRS,maroon).box(22,30.2f,-15,7.5f,.6f,7.5f);return b.end();}
    private static Model buildPort(){ModelBuilder b=new ModelBuilder();Material steel=mat(.25f,.29f,.30f),maroon=mat(.52f,.035f,.13f),concrete=mat(.64f,.62f,.57f);b.begin();b.part("port-base",GL20.GL_TRIANGLES,ATTRS,concrete).box(0,.35f,0,48f,.7f,22f);MeshPartBuilder cranes=b.part("port-cranes",GL20.GL_TRIANGLES,ATTRS,steel);for(float x:new float[]{-16,0,16}){cranes.box(x,8,0,1.1f,16,1.1f);cranes.box(x,15.4f,5.5f,1f,1f,12f);cranes.box(x,14.7f,10.8f,9f,.7f,.7f);}b.part("port-containers",GL20.GL_TRIANGLES,ATTRS,maroon).box(0,2.0f,-6,26f,3.2f,5f);return b.end();}
    private static Model buildHeritage(){ModelBuilder b=new ModelBuilder();Material stone=mat(.78f,.68f,.52f),dark=mat(.31f,.22f,.14f);b.begin();b.part("heritage-fort",GL20.GL_TRIANGLES,ATTRS,stone).box(0,3.2f,0,22f,6.4f,22f);MeshPartBuilder towers=b.part("heritage-towers",GL20.GL_TRIANGLES,ATTRS,stone);for(float x:new float[]{-11,11})for(float z:new float[]{-11,11}){towers.setVertexTransform(new Matrix4().setToTranslation(x,4.2f,z));towers.cylinder(5.3f,8.4f,5.3f,12);}towers.setVertexTransform(null);b.part("heritage-door",GL20.GL_TRIANGLES,ATTRS,dark).box(0,2.0f,11.1f,3f,4f,.3f);return b.end();}
    private static Material mat(float r,float g,float b){return new Material(ColorAttribute.createDiffuse(new Color(r,g,b,1)));}
    private static Material shiny(float r,float g,float b){return new Material(ColorAttribute.createDiffuse(new Color(r,g,b,1)),ColorAttribute.createSpecular(new Color(.70f,.79f,.81f,1)),FloatAttribute.createShininess(80));}
    @Override public void dispose(){metroModel.dispose();skylineModel.dispose();airportModel.dispose();portModel.dispose();heritageModel.dispose();placements.clear();}
    private record Placement(String name,double x,double z,ModelInstance instance){}
}
