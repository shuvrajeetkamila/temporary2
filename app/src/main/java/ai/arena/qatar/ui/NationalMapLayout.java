package ai.arena.qatar.ui;

import com.badlogic.gdx.math.Vector2;

/** Shared geometry and exact world/screen conversion for the touchable Qatar map. */
public final class NationalMapLayout {
    private NationalMapLayout(){}

    public static Rect minimap(int width,int height){
        return new Rect(width-198,height-293,186,201);
    }

    public static Layout fullscreen(int width,int height,double worldWidth,double worldHeight){
        float marginX=Math.max(24,width*.035f),marginY=Math.max(18,height*.035f);
        Rect panel=new Rect(marginX,marginY,width-marginX*2,height-marginY*2);
        float topBar=68,bottomBar=82,mapAreaWidth=panel.width*.56f;
        float maximumMapWidth=Math.max(120,mapAreaWidth-34);
        float maximumMapHeight=Math.max(160,panel.height-topBar-bottomBar);
        float aspect=(float)(worldWidth/worldHeight);
        float mapHeight=maximumMapHeight,mapWidth=mapHeight*aspect;
        if(mapWidth>maximumMapWidth){mapWidth=maximumMapWidth;mapHeight=mapWidth/aspect;}
        float mapX=panel.x+(mapAreaWidth-mapWidth)/2;
        float mapY=panel.y+bottomBar+(maximumMapHeight-mapHeight)/2;
        Rect map=new Rect(mapX,mapY,mapWidth,mapHeight);
        Rect close=new Rect(panel.right()-134,panel.top()-58,112,40);
        float sideX=panel.x+mapAreaWidth+34,sideWidth=Math.max(170,panel.right()-sideX-28);
        Rect teleport=new Rect(sideX,panel.y+20,sideWidth,54);
        Rect zoomOut=new Rect(map.x,map.y-58,52,40);
        Rect zoomIn=new Rect(map.x+60,map.y-58,52,40);
        Rect viewAll=new Rect(map.x+120,map.y-58,Math.max(132,map.width-120),40);
        return new Layout(panel,map,close,teleport,zoomOut,zoomIn,viewAll,sideX,sideWidth);
    }

    public static Vector2 worldToScreen(Rect map,double worldX,double worldZ,double worldWidth,double worldHeight,Vector2 out){
        float x=(float)(Math.max(0,Math.min(worldWidth,worldX))/worldWidth);
        float z=(float)(Math.max(0,Math.min(worldHeight,worldZ))/worldHeight);
        return out.set(map.x+x*map.width,map.y+z*map.height);
    }

    public static Vector2 screenToWorld(Rect map,float screenX,float screenY,double worldWidth,double worldHeight,Vector2 out){
        float x=Math.max(0,Math.min(1,(screenX-map.x)/map.width));
        float z=Math.max(0,Math.min(1,(screenY-map.y)/map.height));
        return out.set((float)(x*worldWidth),(float)(z*worldHeight));
    }

    public record Rect(float x,float y,float width,float height){
        public float right(){return x+width;}
        public float top(){return y+height;}
        public boolean contains(float px,float py){return px>=x&&px<=right()&&py>=y&&py<=top();}
    }
    public record Layout(Rect panel,Rect map,Rect close,Rect teleport,Rect zoomOut,Rect zoomIn,Rect viewAll,float sideX,float sideWidth){}
}
