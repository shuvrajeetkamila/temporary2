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
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.TimeUtils;

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
    private final Pixmap minimapPixmap=new Pixmap(256,256,Pixmap.Format.RGBA8888);
    private final Texture minimapStreetTexture=new Texture(minimapPixmap);
    private final Texture nationalRoadAtlas=new Texture(Gdx.files.internal("ui/qatar-road-atlas.png"));
    private final TextureRegion nationalRoadRegion=new TextureRegion(nationalRoadAtlas);
    private final NationalMapRoadCache nationalRoads;
    private long minimapRoadRevision=-1,lastMinimapBuildMillis;
    private double minimapCentreX,minimapCentreZ;
    private float minimapRadius=-1;
    private boolean hasMapSelection;
    private double mapSelectionX,mapSelectionZ;
    private String mapStatus="Tap Qatar to place a pin, then confirm teleport";
    private String mapPlacementDetail="pending confirmation";

    public HudRenderer(QatarPack pack,QatarDetailPack detailPack,PhysicsWorld physics,WorldStreamer streamer,QatarDetailStreamer detailStreamer,QatarWorldRenderer renderer,TouchController controls,QatarEnvironment climate){
        this.pack=pack;this.detailPack=detailPack;this.physics=physics;this.streamer=streamer;this.detailStreamer=detailStreamer;this.renderer=renderer;this.controls=controls;this.climate=climate;
        nationalRoads=new NationalMapRoadCache(detailPack);
        font.getData().setScale(1.02f);minimapStreetTexture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);nationalRoadAtlas.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
        Gdx.app.log("QatarMap","STREET_ATLAS_READY source=offline-OSM segments="+detailPack.roadSegmentCount+" localMinimap=vector-cache");
    }
    public void setPerformanceProfile(ThermalGovernor.Profile profile){performanceProfile=profile;}
    public void setMapSelection(double x,double z){hasMapSelection=true;mapSelectionX=x;mapSelectionZ=z;mapPlacementDetail="pending confirmation";mapStatus="Pin fixed - press CONFIRM TELEPORT";}
    public void setResolvedMapSelection(double x,double z,double adjustment,String basis){
        hasMapSelection=true;mapSelectionX=x;mapSelectionZ=z;
        mapPlacementDetail=adjustment<1?"exact safe point ("+basis+")":Math.round(adjustment)+" m to final safe point ("+basis+")";
        mapStatus="Final arrival pin fixed - press CONFIRM TELEPORT";
    }
    public void setMapStatus(String status){mapStatus=status==null?"":status;}
    public boolean hasMapSelection(){return hasMapSelection;}
    public double mapSelectionX(){return mapSelectionX;}
    public double mapSelectionZ(){return mapSelectionZ;}

    public void render(){
        int width=Gdx.graphics.getWidth(),height=Gdx.graphics.getHeight();
        nationalRoads.update(controls.mapViewport(),controls.mapOpen());
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
        String fatal=renderer.fatalStreamingError();if(!fatal.isEmpty()){font.setColor(Color.SCARLET);font.draw(sprites,fatal,25,height-140);}
        String detailWarning=renderer.detailStreamingWarning();if(!detailWarning.isEmpty()){font.setColor(Color.ORANGE);font.draw(sprites,detailWarning,25,height-(fatal.isEmpty()?140:166));}sprites.end();
        if(controls.mapOpen())drawNationalMapWindow(width,height);
    }

    private void drawStick(Vector2 origin,Vector2 current,boolean active,int height,Color color){
        float ox=active?origin.x:95,oy=active?height-origin.y:105,cx=active?current.x:ox,cy=active?height-current.y:oy;
        if(origin==controls.liftOrigin()&&!active){ox=Gdx.graphics.getWidth()-100;oy=105;cx=ox;cy=oy;}
        shapes.setColor(color);shapes.circle(ox,oy,68,32);shapes.setColor(color.r,color.g,color.b,.70f);shapes.circle(cx,cy,29,24);
    }

    private void drawMinimap(int width,int height){
        NationalMapLayout.Rect frame=NationalMapLayout.minimap(width,height);float radius=minimapRadiusForMode();
        updateMinimapStreetCache(radius);
        float roadSize=160,roadX=frame.x()+(frame.width()-roadSize)/2,roadY=frame.y()+33,buttonX=frame.x()+8,buttonY=frame.y()+8,buttonW=frame.width()-16;
        shapes.begin(ShapeRenderer.ShapeType.Filled);shapes.setColor(.02f,.06f,.075f,.88f);shapes.rect(frame.x(),frame.y(),frame.width(),frame.height());shapes.end();
        sprites.getProjectionMatrix().setToOrtho2D(0,0,width,height);sprites.begin();sprites.setColor(Color.WHITE);sprites.draw(minimapStreetTexture,roadX,roadY,roadSize,roadSize);sprites.end();
        float markerX=roadX+(float)((physics.globalX()-minimapCentreX+minimapRadius)/(minimapRadius*2))*roadSize;
        float markerY=roadY+(float)((physics.globalZ()-minimapCentreZ+minimapRadius)/(minimapRadius*2))*roadSize;
        markerX=Math.max(roadX+5,Math.min(roadX+roadSize-5,markerX));markerY=Math.max(roadY+5,Math.min(roadY+roadSize-5,markerY));
        boolean destinationVisible=hasMapSelection&&Math.abs(mapSelectionX-minimapCentreX)<=minimapRadius&&Math.abs(mapSelectionZ-minimapCentreZ)<=minimapRadius;
        float destinationX=destinationVisible?roadX+(float)((mapSelectionX-minimapCentreX+minimapRadius)/(minimapRadius*2))*roadSize:0;
        float destinationY=destinationVisible?roadY+(float)((mapSelectionZ-minimapCentreZ+minimapRadius)/(minimapRadius*2))*roadSize:0;
        float heading=physics.renderHeadingRadians(),tipX=markerX+(float)Math.sin(heading)*10,tipY=markerY+(float)Math.cos(heading)*10;
        float leftX=markerX+(float)Math.sin(heading+2.45f)*7,leftY=markerY+(float)Math.cos(heading+2.45f)*7;
        float rightX=markerX+(float)Math.sin(heading-2.45f)*7,rightY=markerY+(float)Math.cos(heading-2.45f)*7;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if(destinationVisible){shapes.setColor(1f,.08f,.22f,.38f);shapes.circle(destinationX,destinationY,13,22);shapes.setColor(1f,.90f,.72f,1);shapes.circle(destinationX,destinationY,4,14);}
        shapes.setColor(.08f,.12f,.13f,.9f);shapes.circle(markerX,markerY,7,16);shapes.setColor(1f,.06f,.20f,1);shapes.triangle(tipX,tipY,leftX,leftY,rightX,rightY);
        shapes.setColor(.54f,.08f,.22f,.96f);shapes.rect(buttonX,buttonY,buttonW,25);shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);shapes.setColor(.95f,.84f,.62f,.95f);shapes.rect(roadX,roadY,roadSize,roadSize);
        if(destinationVisible){shapes.setColor(1f,.12f,.24f,1);shapes.circle(destinationX,destinationY,11,24);shapes.line(destinationX-15,destinationY,destinationX-7,destinationY);shapes.line(destinationX+7,destinationY,destinationX+15,destinationY);}
        shapes.end();
        sprites.begin();font.getData().setScale(.72f);font.setColor(Color.WHITE);font.draw(sprites,"STREETS  |  FULLSCREEN MAP",buttonX+8,buttonY+18);sprites.end();
    }

    private float minimapRadiusForMode(){return switch(physics.mode()){case WALK->550f;case CAR,BOAT->700f;case HELICOPTER,AEROPLANE->850f;};}

    private void updateMinimapStreetCache(float radius){
        long revision=renderer.detailMapRevision(),now=TimeUtils.millis();double dx=physics.globalX()-minimapCentreX,dz=physics.globalZ()-minimapCentreZ;
        boolean moved=minimapRadius<0||dx*dx+dz*dz>radius*radius*.0225;
        boolean changed=revision!=minimapRoadRevision&&now-lastMinimapBuildMillis>=700;
        if(!moved&&Math.abs(radius-minimapRadius)<1&&!changed)return;
        minimapCentreX=physics.globalX();minimapCentreZ=physics.globalZ();minimapRadius=radius;minimapRoadRevision=revision;lastMinimapBuildMillis=now;
        int blocks=16,blockSize=256/blocks;
        for(int row=0;row<blocks;row++)for(int column=0;column<blocks;column++){
            double x=minimapCentreX+((column+.5)/blocks*2-1)*radius,z=minimapCentreZ+(1-(row+.5)/blocks*2)*radius;
            minimapPixmap.setColor(renderer.waterAt(x,z)?Color.rgba8888(.53f,.76f,.82f,1):Color.rgba8888(.84f,.80f,.69f,1));
            minimapPixmap.fillRectangle(column*blockSize,row*blockSize,blockSize,blockSize);
        }
        renderer.visitVisibleRoads((x1,z1,x2,z2,roadClass,roadWidth)->rasterMinimapRoad(x1,z1,x2,z2,roadClass,roadWidth,true));
        renderer.visitVisibleRoads((x1,z1,x2,z2,roadClass,roadWidth)->rasterMinimapRoad(x1,z1,x2,z2,roadClass,roadWidth,false));
        minimapStreetTexture.draw(minimapPixmap,0,0);
    }

    private void rasterMinimapRoad(double x1,double z1,double x2,double z2,int roadClass,float roadWidth,boolean casing){
        double minimumX=minimapCentreX-minimapRadius,maximumX=minimapCentreX+minimapRadius,minimumZ=minimapCentreZ-minimapRadius,maximumZ=minimapCentreZ+minimapRadius;
        if(Math.max(x1,x2)<minimumX||Math.min(x1,x2)>maximumX||Math.max(z1,z2)<minimumZ||Math.min(z1,z2)>maximumZ)return;
        x1=Math.max(minimumX,Math.min(maximumX,x1));x2=Math.max(minimumX,Math.min(maximumX,x2));z1=Math.max(minimumZ,Math.min(maximumZ,z1));z2=Math.max(minimumZ,Math.min(maximumZ,z2));
        int px1=(int)Math.round((x1-minimumX)/(maximumX-minimumX)*255),px2=(int)Math.round((x2-minimumX)/(maximumX-minimumX)*255);
        int py1=255-(int)Math.round((z1-minimumZ)/(maximumZ-minimumZ)*255),py2=255-(int)Math.round((z2-minimumZ)/(maximumZ-minimumZ)*255);
        int pixels=Math.max(1,Math.min(6,Math.round(roadWidth/(minimapRadius*2)*256)));
        if(casing){if(roadClass>=7&&roadClass!=9)return;minimapPixmap.setColor(Color.rgba8888(.38f,.36f,.32f,1));drawPixmapLine(px1,py1,px2,py2,Math.min(7,pixels+2));return;}
        minimapPixmap.setColor(minimapRoadColour(roadClass));drawPixmapLine(px1,py1,px2,py2,pixels);
    }

    private void drawPixmapLine(int x1,int y1,int x2,int y2,int width){
        int half=width/2;if(Math.abs(x2-x1)>=Math.abs(y2-y1))for(int offset=-half;offset<=half;offset++)minimapPixmap.drawLine(x1,y1+offset,x2,y2+offset);
        else for(int offset=-half;offset<=half;offset++)minimapPixmap.drawLine(x1+offset,y1,x2+offset,y2);
    }

    private static int minimapRoadColour(int roadClass){return switch(roadClass){
        case 0,1->Color.rgba8888(.83f,.27f,.16f,1);case 2,3->Color.rgba8888(.95f,.55f,.20f,1);case 4->Color.rgba8888(.98f,.76f,.35f,1);
        case 5,6->Color.rgba8888(.98f,.97f,.92f,1);case 7,8->Color.rgba8888(.62f,.53f,.39f,1);case 9->Color.rgba8888(.20f,.22f,.23f,1);default->Color.rgba8888(.63f,.65f,.64f,1);
    };}

    private void drawNationalMapWindow(int width,int height){
        NationalMapViewport view=controls.mapViewport();
        NationalMapLayout.Layout layout=NationalMapLayout.fullscreen(width,height,pack.worldWidth,pack.worldHeight);
        NationalMapLayout.Rect panel=layout.panel(),map=layout.map(),close=layout.close(),teleport=layout.teleport();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.018f,.045f,.056f,1);shapes.rect(0,0,width,height);
        shapes.setColor(.025f,.060f,.072f,1);shapes.rect(panel.x(),panel.y(),panel.width(),panel.height());
        shapes.setColor(.014f,.032f,.040f,1);shapes.rect(layout.sideX()-18,0,width-layout.sideX()+18,height);
        shapes.setColor(.035f,.25f,.34f,1);shapes.rect(map.x(),map.y(),map.width(),map.height());
        for(QatarPack.ChunkEntry entry:pack.entries().values()){
            if(!entry.hasLand())continue;
            double x1=entry.x()*(double)pack.chunkMetres,z1=entry.y()*(double)pack.chunkMetres;
            double x2=Math.min(pack.worldWidth,x1+pack.chunkMetres),z2=Math.min(pack.worldHeight,z1+pack.chunkMetres);
            if(x2<view.minX()||x1>view.maxX()||z2<view.minZ()||z1>view.maxZ())continue;
            Color colour=MUNICIPALITY_COLOURS[entry.municipality()%MUNICIPALITY_COLOURS.length];shapes.setColor(colour);
            float sx1=Math.max(map.x(),view.worldToScreenX(x1,map.x(),map.width()));
            float sy1=Math.max(map.y(),view.worldToScreenY(z1,map.y(),map.height()));
            float sx2=Math.min(map.right(),view.worldToScreenX(x2,map.x(),map.width()));
            float sy2=Math.min(map.top(),view.worldToScreenY(z2,map.y(),map.height()));
            if(sx2>sx1&&sy2>sy1)shapes.rect(sx1,sy1,sx2-sx1+.35f,sy2-sy1+.35f);
        }
        shapes.setColor(.54f,.08f,.22f,1);shapes.rect(close.x(),close.y(),close.width(),close.height());
        shapes.setColor(.11f,.15f,.17f,1);shapes.rect(layout.zoomOut().x(),layout.zoomOut().y(),layout.zoomOut().width(),layout.zoomOut().height());shapes.rect(layout.zoomIn().x(),layout.zoomIn().y(),layout.zoomIn().width(),layout.zoomIn().height());
        shapes.setColor(.10f,.27f,.31f,1);shapes.rect(layout.viewAll().x(),layout.viewAll().y(),layout.viewAll().width(),layout.viewAll().height());
        shapes.setColor(hasMapSelection?.54f:.20f,hasMapSelection?.08f:.22f,hasMapSelection?.22f:.24f,1f);shapes.rect(teleport.x(),teleport.y(),teleport.width(),teleport.height());
        shapes.end();

        float u1=(float)(view.minX()/pack.worldWidth),u2=(float)(view.maxX()/pack.worldWidth);
        float v1=(float)(1.0-view.maxZ()/pack.worldHeight),v2=(float)(1.0-view.minZ()/pack.worldHeight);
        nationalRoadRegion.setRegion(u1,v1,u2,v2);
        sprites.begin();sprites.setColor(Color.WHITE);
        // The low-resolution country atlas is only a loading/overview layer.
        // Once close-view QTOD vectors are ready, remove the magnified atlas so
        // its pixels cannot obscure the newly rasterized street geometry.
        if(!nationalRoads.ready(view))sprites.draw(nationalRoadRegion,map.x(),map.y(),map.width(),map.height());
        nationalRoads.draw(sprites,view,map.x(),map.y(),map.width(),map.height());sprites.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);shapes.setColor(.99f,.91f,.70f,1);drawNationalOutlines(map,view);
        shapes.setColor(.73f,.85f,.86f,.72f);shapes.rect(map.x(),map.y(),map.width(),map.height());
        float crossX=close.x()+close.width()/2,crossY=close.y()+close.height()/2,crossRadius=close.width()*.22f;
        shapes.setColor(Color.WHITE);shapes.line(crossX-crossRadius,crossY-crossRadius,crossX+crossRadius,crossY+crossRadius);shapes.line(crossX-crossRadius,crossY+crossRadius,crossX+crossRadius,crossY-crossRadius);shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for(QatarPack.Hub hub:pack.hubs)if(view.contains(hub.x(),hub.z())){
            mapPoint.set(view.worldToScreenX(hub.x(),map.x(),map.width()),view.worldToScreenY(hub.z(),map.y(),map.height()));
            shapes.setColor(.12f,.16f,.17f,.9f);shapes.circle(mapPoint.x,mapPoint.y,view.zoom()>8?3.2f:2.4f,10);
        }
        if(view.contains(physics.globalX(),physics.globalZ())){
            mapPoint.set(view.worldToScreenX(physics.globalX(),map.x(),map.width()),view.worldToScreenY(physics.globalZ(),map.y(),map.height()));
            shapes.setColor(.15f,1f,.55f,1);shapes.circle(mapPoint.x,mapPoint.y,6,18);
        }
        if(hasMapSelection&&view.contains(mapSelectionX,mapSelectionZ)){
            mapPoint.set(view.worldToScreenX(mapSelectionX,map.x(),map.width()),view.worldToScreenY(mapSelectionZ,map.y(),map.height()));
            shapes.setColor(1f,.08f,.22f,1);shapes.circle(mapPoint.x,mapPoint.y,9,24);shapes.setColor(Color.WHITE);shapes.circle(mapPoint.x,mapPoint.y,3,14);
        }
        shapes.end();
        if(hasMapSelection&&view.contains(mapSelectionX,mapSelectionZ)){
            mapPoint.set(view.worldToScreenX(mapSelectionX,map.x(),map.width()),view.worldToScreenY(mapSelectionZ,map.y(),map.height()));
            shapes.begin(ShapeRenderer.ShapeType.Line);shapes.setColor(1f,.90f,.90f,1);shapes.circle(mapPoint.x,mapPoint.y,13,28);shapes.line(mapPoint.x-17,mapPoint.y,mapPoint.x-7,mapPoint.y);shapes.line(mapPoint.x+7,mapPoint.y,mapPoint.x+17,mapPoint.y);shapes.line(mapPoint.x,mapPoint.y-17,mapPoint.x,mapPoint.y-7);shapes.line(mapPoint.x,mapPoint.y+7,mapPoint.x,mapPoint.y+17);shapes.end();
        }

        sprites.begin();font.getData().setScale(1.25f);font.setColor(Color.WHITE);font.draw(sprites,"FULL-SCREEN QATAR PRECISION MAP",panel.x()+24,panel.top()-27);
        font.getData().setScale(.88f);font.setColor(.92f,.86f,.69f,1);font.draw(sprites,"Complete packaged Qatar | offline OpenStreetMap vectors | EPSG:32639 | north up",panel.x()+24,panel.top()-51);
        font.getData().setScale(.68f);font.setColor(.90f,.92f,.91f,1);font.draw(sprites,"RETURN TO GAME",close.x()-26,close.y()-7);
        font.getData().setScale(1.15f);font.draw(sprites,"-",layout.zoomOut().x()+19,layout.zoomOut().y()+28);font.draw(sprites,"+",layout.zoomIn().x()+18,layout.zoomIn().y()+29);
        font.getData().setScale(.78f);font.draw(sprites,"VIEW ALL QATAR",layout.viewAll().x()+15,layout.viewAll().y()+26);

        font.getData().setScale(1.0f);font.draw(sprites,"PRECISE DESTINATION",layout.sideX(),panel.top()-98);
        font.getData().setScale(.82f);font.setColor(.83f,.89f,.89f,1);
        font.draw(sprites,"Pinch to zoom  |  drag to pan",layout.sideX(),panel.top()-130);
        font.draw(sprites,"Tap or long-press to place the fixed red pin",layout.sideX(),panel.top()-155);
        font.draw(sprites,"CONFIRM enters the point  |  X / Back returns",layout.sideX(),panel.top()-180);
        font.setColor(.98f,.86f,.64f,1);
        font.draw(sprites,String.format(java.util.Locale.ROOT,"ZOOM x%.2f  |  VIEW %.1f x %.1f km",view.zoom(),view.visibleWidth()/1000.0,view.visibleHeight()/1000.0),layout.sideX(),panel.top()-218);
        font.draw(sprites,String.format(java.util.Locale.ROOT,"SCALE ~%.1f m/pixel  |  %s",view.visibleWidth()/map.width(),nationalRoads.status(view)),layout.sideX(),panel.top()-243);
        font.setColor(.98f,.86f,.64f,1);font.draw(sprites,"Placement for "+physics.mode().label+": "+placementDescription(),layout.sideX(),panel.top()-278);
        if(hasMapSelection){
            font.setColor(Color.WHITE);font.draw(sprites,"Selected local: "+Math.round(mapSelectionX)+" E / "+Math.round(mapSelectionZ)+" N",layout.sideX(),panel.top()-315);
            font.draw(sprites,"UTM 39N: "+Math.round(pack.originEasting+mapSelectionX)+" E",layout.sideX(),panel.top()-340);
            font.draw(sprites,"             "+Math.round(pack.originNorthing+mapSelectionZ)+" N",layout.sideX(),panel.top()-365);
            QatarPack.Hub hub=nearestHub(mapSelectionX,mapSelectionZ);font.setColor(.82f,.89f,.90f,1);font.draw(sprites,"Nearest named area: "+hub.english(),layout.sideX(),panel.top()-395);
            font.draw(sprites,"Safe placement: "+mapPlacementDetail,layout.sideX(),panel.top()-420);
            if(!view.contains(mapSelectionX,mapSelectionZ)){font.setColor(.98f,.54f,.42f,1);font.draw(sprites,"Pin is outside this view - VIEW ALL reveals it",layout.sideX(),panel.top()-449);}
        }else{font.setColor(.73f,.80f,.81f,1);font.draw(sprites,"No destination selected yet",layout.sideX(),panel.top()-315);}
        font.setColor(.92f,.88f,.73f,1);font.draw(sprites,mapStatus,layout.sideX(),teleport.top()+28);
        font.getData().setScale(1.06f);font.setColor(Color.WHITE);font.draw(sprites,"CONFIRM TELEPORT",teleport.x()+teleport.width()/2-88,teleport.y()+35);
        font.getData().setScale(.72f);font.setColor(.78f,.86f,.87f,1);font.draw(sprites,"Overview atlas + bounded close-view vectors; no network or satellite imagery",layout.sideX(),teleport.top()+55);
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

    private void drawNationalOutlines(NationalMapLayout.Rect map,NationalMapViewport view){
        for(float[] polygon:pack.nationalPolygons){
            if(polygon.length<4)continue;
            for(int i=2;i<polygon.length;i+=2){
                float x1=view.worldToScreenX(polygon[i-2],map.x(),map.width()),y1=view.worldToScreenY(polygon[i-1],map.y(),map.height());
                float x2=view.worldToScreenX(polygon[i],map.x(),map.width()),y2=view.worldToScreenY(polygon[i+1],map.y(),map.height());
                drawClippedLine(map,x1,y1,x2,y2);
            }
        }
    }

    private void drawClippedLine(NationalMapLayout.Rect map,float x1,float y1,float x2,float y2){
        int c1=clipCode(map,x1,y1),c2=clipCode(map,x2,y2);
        for(int guard=0;guard<8;guard++){
            if((c1|c2)==0){shapes.line(x1,y1,x2,y2);return;}
            if((c1&c2)!=0)return;
            int code=c1!=0?c1:c2;float x=0,y=0;
            if((code&8)!=0){x=x1+(x2-x1)*(map.top()-y1)/(y2-y1);y=map.top();}
            else if((code&4)!=0){x=x1+(x2-x1)*(map.y()-y1)/(y2-y1);y=map.y();}
            else if((code&2)!=0){y=y1+(y2-y1)*(map.right()-x1)/(x2-x1);x=map.right();}
            else {y=y1+(y2-y1)*(map.x()-x1)/(x2-x1);x=map.x();}
            if(code==c1){x1=x;y1=y;c1=clipCode(map,x1,y1);}else{x2=x;y2=y;c2=clipCode(map,x2,y2);}
        }
    }

    private static int clipCode(NationalMapLayout.Rect map,float x,float y){
        int code=0;if(x<map.x())code|=1;else if(x>map.right())code|=2;if(y<map.y())code|=4;else if(y>map.top())code|=8;return code;
    }

    private QatarPack.Hub nearestHub(double x,double z){
        QatarPack.Hub result=pack.hubs.get(0);double best=Double.POSITIVE_INFINITY;
        for(QatarPack.Hub hub:pack.hubs){double distance=(hub.x()-x)*(hub.x()-x)+(hub.z()-z)*(hub.z()-z);if(distance<best){best=distance;result=hub;}}
        return result;
    }
    private String localBiome(){QatarPack.ChunkEntry entry=pack.entry(pack.chunkX(physics.globalX()),pack.chunkY(physics.globalZ()));return entry==null?"OUTSIDE PACK":pack.biomes.get(entry.biome());}
    @Override public void dispose(){nationalRoads.dispose();font.dispose();sprites.dispose();shapes.dispose();minimapStreetTexture.dispose();minimapPixmap.dispose();nationalRoadAtlas.dispose();}
}
