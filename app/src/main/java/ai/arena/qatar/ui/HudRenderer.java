package ai.arena.qatar.ui;

import ai.arena.qatar.environment.QatarEnvironment;
import ai.arena.qatar.input.TouchController;
import ai.arena.qatar.performance.ThermalGovernor;
import ai.arena.qatar.physics.PhysicsWorld;
import ai.arena.qatar.render.QatarWorldRenderer;
import ai.arena.qatar.world.QatarDetailPack;
import ai.arena.qatar.world.QatarDetailStreamer;
import ai.arena.qatar.world.QatarPack;
import ai.arena.qatar.world.WorldStreamer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/** Gameplay HUD plus the touch-selectable, complete-country Qatar map. */
public final class HudRenderer implements Disposable {
    private static final Color[] MUNICIPALITY_COLOURS={
            new Color(.78f,.66f,.47f,1),new Color(.84f,.72f,.52f,1),new Color(.70f,.61f,.43f,1),new Color(.88f,.77f,.57f,1),
            new Color(.73f,.67f,.50f,1),new Color(.81f,.69f,.42f,1),new Color(.68f,.58f,.40f,1),new Color(.90f,.80f,.62f,1)};
    private final QatarPack pack;
    private final QatarDetailPack detailPack;
    private final PhysicsWorld physics;
    private final WorldStreamer streamer;
    private final QatarDetailStreamer detailStreamer;
    private final QatarWorldRenderer renderer;
    private final TouchController controls;
    private final QatarEnvironment climate;
    private ThermalGovernor.Profile performanceProfile=ThermalGovernor.Profile.NORMAL;
    private final ShapeRenderer shapes=new ShapeRenderer();
    private final SpriteBatch sprites=new SpriteBatch();
    private final BitmapFont font=new BitmapFont();
    private final Color maroon=new Color(.54f,.08f,.22f,.90f);
    private final Vector2 mapPoint=new Vector2();
    private boolean hasMapSelection;
    private double mapSelectionX,mapSelectionZ;
    private String mapStatus="Touch anywhere on Qatar, then press TELEPORT";

    public HudRenderer(QatarPack pack,QatarDetailPack detailPack,PhysicsWorld physics,WorldStreamer streamer,QatarDetailStreamer detailStreamer,QatarWorldRenderer renderer,TouchController controls,QatarEnvironment climate){
        this.pack=pack;this.detailPack=detailPack;this.physics=physics;this.streamer=streamer;this.detailStreamer=detailStreamer;this.renderer=renderer;this.controls=controls;this.climate=climate;
        font.getData().setScale(1.02f);
    }
    public void setPerformanceProfile(ThermalGovernor.Profile profile){performanceProfile=profile;}
    public void setMapSelection(double x,double z){hasMapSelection=true;mapSelectionX=x;mapSelectionZ=z;mapStatus="Destination selected - press TELEPORT";}
    public void setMapStatus(String status){mapStatus=status==null?"":status;}
    public boolean hasMapSelection(){return hasMapSelection;}
    public double mapSelectionX(){return mapSelectionX;}
    public double mapSelectionZ(){return mapSelectionZ;}

    public void render(){
        int width=Gdx.graphics.getWidth(),height=Gdx.graphics.getHeight();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.getProjectionMatrix().setToOrtho2D(0,0,width,height);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.02f,.055f,.07f,.72f);shapes.rect(14,height-118,720,104);
        shapes.setColor(maroon);shapes.rect(width-250,height-82,236,62);shapes.rect(width-500,height-82,236,62);
        drawStick(controls.moveOrigin(),controls.moveCurrent(),controls.moving(),height,new Color(.94f,.86f,.66f,.28f));
        drawStick(controls.liftOrigin(),controls.liftCurrent(),controls.lifting(),height,new Color(.54f,.08f,.22f,.32f));
        shapes.end();
        drawMinimap(width,height);

        sprites.getProjectionMatrix().setToOrtho2D(0,0,width,height);sprites.begin();font.getData().setScale(1.02f);
        font.setColor(Color.WHITE);Vector3 velocity=physics.velocity();String biome=localBiome();
        font.draw(sprites,"QATAR: THE PENINSULA  |  TRUE 1:1 METRES",28,height-37);
        font.draw(sprites,String.format("%s | %.1f m/s | %s | %s %d FPS | %s WIND %.1f",physics.mode().label,velocity.len(),biome,performanceProfile.label,performanceProfile.targetFps,climate.preset().label,climate.windMetresPerSecond()),28,height-66);
        font.draw(sprites,String.format("TERRAIN %d  OSM CELLS %d+%d  |  INSTALLED %,d ROAD SEGMENTS / %,d BUILDINGS",streamer.residentCount(),detailStreamer.residentCount(),detailStreamer.pendingCount(),detailPack.roadSegmentCount,detailPack.simpleBuildingCount+detailPack.relationBuildingCount),28,height-95);
        font.draw(sprites,"SWITCH MODE",width-228,height-46);font.draw(sprites,"RECOVER",width-445,height-46);
        font.setColor(.92f,.88f,.74f,1);font.draw(sprites,"Left control: move / steer     Right control: lift / action",24,30);
        font.draw(sprites,"Offline | OpenStreetMap contributors | terrain Mapzen/USGS/NOAA | geoBoundaries/NE",width-760,30);
        String fatal=renderer.fatalStreamingError();if(!fatal.isEmpty()){font.setColor(Color.SCARLET);font.draw(sprites,fatal,25,height-140);}sprites.end();
        if(controls.mapOpen())drawNationalMapWindow(width,height);
    }

    private void drawStick(Vector2 origin,Vector2 current,boolean active,int height,Color color){
        float ox=active?origin.x:95,oy=active?height-origin.y:105,cx=active?current.x:ox,cy=active?height-current.y:oy;
        if(origin==controls.liftOrigin()&&!active){ox=Gdx.graphics.getWidth()-100;oy=105;cx=ox;cy=oy;}
        shapes.setColor(color);shapes.circle(ox,oy,68,32);shapes.setColor(color.r,color.g,color.b,.70f);shapes.circle(cx,cy,29,24);
    }

    private void drawMinimap(int width,int height){
        NationalMapLayout.Rect frame=NationalMapLayout.minimap(width,height);
        float mapX=frame.x()+8,mapY=frame.y()+8,mapW=frame.width()-16,mapH=frame.height()-16;
        shapes.begin(ShapeRenderer.ShapeType.Filled);shapes.setColor(.02f,.06f,.075f,.82f);shapes.rect(frame.x(),frame.y(),frame.width(),frame.height());
        shapes.setColor(.54f,.08f,.22f,.94f);shapes.rect(mapX,mapY,mapW,25);shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);shapes.setColor(.95f,.84f,.62f,.95f);
        drawNationalOutlines(new NationalMapLayout.Rect(mapX,mapY,mapW,mapH));shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Filled);shapes.setColor(.95f,.15f,.28f,1);
        NationalMapLayout.worldToScreen(new NationalMapLayout.Rect(mapX,mapY,mapW,mapH),physics.globalX(),physics.globalZ(),pack.worldWidth,pack.worldHeight,mapPoint);shapes.circle(mapPoint.x,mapPoint.y,5,16);shapes.end();
        sprites.getProjectionMatrix().setToOrtho2D(0,0,width,height);sprites.begin();font.getData().setScale(.82f);font.setColor(Color.WHITE);font.draw(sprites,"OPEN MAP",mapX+42,mapY+19);sprites.end();
    }

    private void drawNationalMapWindow(int width,int height){
        NationalMapLayout.Layout layout=NationalMapLayout.fullscreen(width,height,pack.worldWidth,pack.worldHeight);
        NationalMapLayout.Rect panel=layout.panel(),map=layout.map(),close=layout.close(),teleport=layout.teleport();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.005f,.014f,.020f,.92f);shapes.rect(0,0,width,height);
        shapes.setColor(.025f,.060f,.072f,1);shapes.rect(panel.x(),panel.y(),panel.width(),panel.height());
        shapes.setColor(.035f,.25f,.34f,1);shapes.rect(map.x(),map.y(),map.width(),map.height());
        for(QatarPack.ChunkEntry entry:pack.entries().values()){
            if(!entry.hasLand())continue;Color colour=MUNICIPALITY_COLOURS[entry.municipality()%MUNICIPALITY_COLOURS.length];shapes.setColor(colour);
            float x=map.x()+(float)(entry.x()*pack.chunkMetres/pack.worldWidth)*map.width();
            float y=map.y()+(float)(entry.y()*pack.chunkMetres/pack.worldHeight)*map.height();
            float w=(float)(pack.chunkMetres/pack.worldWidth)*map.width()+.35f,h=(float)(pack.chunkMetres/pack.worldHeight)*map.height()+.35f;
            shapes.rect(x,y,w,h);
        }
        shapes.setColor(.54f,.08f,.22f,1);shapes.rect(close.x(),close.y(),close.width(),close.height());
        shapes.setColor(hasMapSelection?.54f:.20f,hasMapSelection?.08f:.22f,hasMapSelection?.22f:.24f,1f);shapes.rect(teleport.x(),teleport.y(),teleport.width(),teleport.height());
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);shapes.setColor(.99f,.91f,.70f,1);drawNationalOutlines(map);
        shapes.setColor(.73f,.85f,.86f,.55f);shapes.rect(map.x(),map.y(),map.width(),map.height());shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for(QatarPack.Hub hub:pack.hubs){NationalMapLayout.worldToScreen(map,hub.x(),hub.z(),pack.worldWidth,pack.worldHeight,mapPoint);shapes.setColor(.12f,.16f,.17f,.9f);shapes.circle(mapPoint.x,mapPoint.y,2.4f,10);}
        NationalMapLayout.worldToScreen(map,physics.globalX(),physics.globalZ(),pack.worldWidth,pack.worldHeight,mapPoint);shapes.setColor(.15f,1f,.55f,1);shapes.circle(mapPoint.x,mapPoint.y,6,18);
        if(hasMapSelection){NationalMapLayout.worldToScreen(map,mapSelectionX,mapSelectionZ,pack.worldWidth,pack.worldHeight,mapPoint);shapes.setColor(1f,.08f,.22f,1);shapes.circle(mapPoint.x,mapPoint.y,7,20);shapes.setColor(Color.WHITE);shapes.circle(mapPoint.x,mapPoint.y,2.5f,12);}
        shapes.end();

        sprites.begin();font.getData().setScale(1.25f);font.setColor(Color.WHITE);font.draw(sprites,"QATAR NATIONAL MAP",panel.x()+24,panel.top()-27);
        font.getData().setScale(.88f);font.setColor(.92f,.86f,.69f,1);font.draw(sprites,"Complete installed country | EPSG:32639 | north is up",panel.x()+24,panel.top()-51);
        font.setColor(Color.WHITE);font.draw(sprites,"CLOSE",close.x()+29,close.y()+27);
        font.getData().setScale(1.0f);font.draw(sprites,"TOUCH-TO-TELEPORT",layout.sideX(),panel.top()-98);
        font.getData().setScale(.84f);font.setColor(.83f,.89f,.89f,1);
        font.draw(sprites,"1. Touch a destination on the map",layout.sideX(),panel.top()-132);
        font.draw(sprites,"2. Check the red destination marker",layout.sideX(),panel.top()-158);
        font.draw(sprites,"3. Press TELEPORT, or CLOSE to cancel",layout.sideX(),panel.top()-184);
        font.setColor(.98f,.86f,.64f,1);font.draw(sprites,"Placement for "+physics.mode().label+": "+placementDescription(),layout.sideX(),panel.top()-226);
        if(hasMapSelection){
            font.setColor(Color.WHITE);font.draw(sprites,"Selected local: "+Math.round(mapSelectionX)+" E / "+Math.round(mapSelectionZ)+" N",layout.sideX(),panel.top()-268);
            font.draw(sprites,"UTM 39N: "+Math.round(pack.originEasting+mapSelectionX)+" E",layout.sideX(),panel.top()-294);
            font.draw(sprites,"             "+Math.round(pack.originNorthing+mapSelectionZ)+" N",layout.sideX(),panel.top()-320);
            QatarPack.Hub hub=nearestHub(mapSelectionX,mapSelectionZ);font.setColor(.82f,.89f,.90f,1);font.draw(sprites,"Nearest named area: "+hub.english(),layout.sideX(),panel.top()-354);
        }else{font.setColor(.73f,.80f,.81f,1);font.draw(sprites,"No destination selected yet",layout.sideX(),panel.top()-268);}
        font.setColor(.92f,.88f,.73f,1);font.draw(sprites,mapStatus,layout.sideX(),teleport.top()+28);
        font.getData().setScale(1.06f);font.setColor(Color.WHITE);font.draw(sprites,"TELEPORT",teleport.x()+teleport.width()/2-48,teleport.y()+35);
        font.getData().setScale(.76f);font.setColor(.78f,.86f,.87f,1);font.draw(sprites,"Green = player    Red = selected destination",layout.sideX(),panel.y()+98);
        font.draw(sprites,"CLOSE and Android Back return to gameplay",layout.sideX(),panel.y()+82);
        font.getData().setScale(1.02f);sprites.end();
    }

    private String placementDescription(){
        return switch(physics.mode()){
            case WALK,CAR->"nearest safe land / road";
            case BOAT->"nearest navigable Gulf water";
            case HELICOPTER->"exact point, 60 m clearance";
            case AEROPLANE->"exact point, 120 m clearance";
        };
    }

    private void drawNationalOutlines(NationalMapLayout.Rect map){
        for(float[] polygon:pack.nationalPolygons){
            if(polygon.length<4)continue;
            for(int i=2;i<polygon.length;i+=2){
                float x1=map.x()+polygon[i-2]/(float)pack.worldWidth*map.width(),y1=map.y()+polygon[i-1]/(float)pack.worldHeight*map.height();
                float x2=map.x()+polygon[i]/(float)pack.worldWidth*map.width(),y2=map.y()+polygon[i+1]/(float)pack.worldHeight*map.height();shapes.line(x1,y1,x2,y2);
            }
        }
    }

    private QatarPack.Hub nearestHub(double x,double z){
        QatarPack.Hub result=pack.hubs.get(0);double best=Double.POSITIVE_INFINITY;
        for(QatarPack.Hub hub:pack.hubs){double distance=(hub.x()-x)*(hub.x()-x)+(hub.z()-z)*(hub.z()-z);if(distance<best){best=distance;result=hub;}}
        return result;
    }
    private String localBiome(){QatarPack.ChunkEntry entry=pack.entry(pack.chunkX(physics.globalX()),pack.chunkY(physics.globalZ()));return entry==null?"OUTSIDE PACK":pack.biomes.get(entry.biome());}
    @Override public void dispose(){font.dispose();sprites.dispose();shapes.dispose();}
}
