package ai.arena.qatar.ui;

import com.badlogic.gdx.math.Vector2;

/** Shared geometry and exact world/screen conversion for the touchable Qatar map. */
public final class NationalMapLayout {
    private NationalMapLayout(){}

    public static Rect minimap(int width,int height){
        return new Rect(width-198,height-293,186,201);
    }

    public static Layout fullscreen(int width,int height,double worldWidth,double worldHeight){
        // The precision map is a true edge-to-edge mode, not a floating dialog.
        // Controls retain safe insets while every background pixel belongs to it.
        Rect panel=new Rect(0,0,width,height);
        float topBar=Math.max(66,height*.10f),bottomBar=Math.max(66,height*.09f),mapAreaWidth=width*.55f;
        float maximumMapWidth=Math.max(120,mapAreaWidth-32);
        float maximumMapHeight=Math.max(160,height-topBar-bottomBar);
        float aspect=(float)(worldWidth/worldHeight);
        float mapHeight=maximumMapHeight,mapWidth=mapHeight*aspect;
        if(mapWidth>maximumMapWidth){mapWidth=maximumMapWidth;mapHeight=mapWidth/aspect;}
        float mapX=(mapAreaWidth-mapWidth)/2;
        float mapY=bottomBar+(maximumMapHeight-mapHeight)/2;
        Rect map=new Rect(mapX,mapY,mapWidth,mapHeight);
        float closeSize=Math.max(48,Math.min(58,height*.075f));
        Rect close=new Rect(width-closeSize-16,height-closeSize-16,closeSize,closeSize);
        float sideX=mapAreaWidth+28,sideWidth=Math.max(170,width-sideX-24);
        Rect teleport=new Rect(sideX,18,sideWidth,58);
        Rect zoomOut=new Rect(map.x,16,52,40);
        Rect zoomIn=new Rect(map.x+60,16,52,40);
        Rect viewAll=new Rect(map.x+120,16,Math.max(132,map.width-120),40);
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
