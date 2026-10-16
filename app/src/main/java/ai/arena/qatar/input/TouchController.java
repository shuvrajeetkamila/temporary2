package ai.arena.qatar.input;

import ai.arena.qatar.ui.NationalMapLayout;
import ai.arena.qatar.ui.NationalMapViewport;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;

/** Allocation-free dual-zone controls plus a gesture-safe precision Qatar map. */
public final class TouchController implements InputProcessor {
    private static final int MAX_POINTERS=20;
    private static final float TAP_SLOP=14f;

    private final double worldWidth,worldHeight;
    private final NationalMapViewport mapViewport;
    private int movePointer=-1,liftPointer=-1;
    private final Vector2 moveOrigin=new Vector2(),moveCurrent=new Vector2();
    private final Vector2 liftOrigin=new Vector2(),liftCurrent=new Vector2();
    private boolean action;
    private boolean modeRequested;
    private boolean recoveryRequested;
    private boolean mapOpen,mapOpenedRequested,mapClosedRequested;
    private boolean mapSelectionRequested,teleportRequested,mapViewChangedRequested;
    private String mapViewAction="NONE";
    private float mapSelectionScreenX,mapSelectionScreenY,mapSelectionWorldX,mapSelectionWorldZ;

    private final float[] pointerX=new float[MAX_POINTERS],pointerY=new float[MAX_POINTERS];
    private int mapPrimary=-1,mapSecondary=-1;
    private float mapDownX,mapDownY,lastPrimaryX,lastPrimaryY,lastPinchDistance,lastPinchX,lastPinchY;
    private boolean mapTouchStartedInside,mapGesture;

    public TouchController(double worldWidth,double worldHeight){
        this.worldWidth=worldWidth;this.worldHeight=worldHeight;mapViewport=new NationalMapViewport(worldWidth,worldHeight);
    }

    public float throttle(){return mapOpen?0:clamp((moveOrigin.y-moveCurrent.y)/110f);}
    public float steering(){return mapOpen?0:clamp((moveCurrent.x-moveOrigin.x)/110f);}
    public float lift(){return mapOpen?0:clamp((liftOrigin.y-liftCurrent.y)/110f);}
    public boolean action(){boolean value=!mapOpen&&action;action=false;return value;}
    public boolean consumeModeRequested(){boolean value=modeRequested;modeRequested=false;return value;}
    public boolean consumeRecoveryRequested(){boolean value=recoveryRequested;recoveryRequested=false;return value;}
    public boolean consumeMapOpenedRequested(){boolean value=mapOpenedRequested;mapOpenedRequested=false;return value;}
    public boolean consumeMapClosedRequested(){boolean value=mapClosedRequested;mapClosedRequested=false;return value;}
    public boolean consumeMapSelectionRequested(){boolean value=mapSelectionRequested;mapSelectionRequested=false;return value;}
    public boolean consumeTeleportRequested(){boolean value=teleportRequested;teleportRequested=false;return value;}
    public boolean consumeMapViewChangedRequested(){boolean value=mapViewChangedRequested;mapViewChangedRequested=false;return value;}
    public String mapViewAction(){return mapViewAction;}
    public float mapSelectionScreenX(){return mapSelectionScreenX;}
    /** LibGDX bottom-origin screen coordinate. */
    public float mapSelectionScreenY(){return mapSelectionScreenY;}
    public float mapSelectionWorldX(){return mapSelectionWorldX;}
    public float mapSelectionWorldZ(){return mapSelectionWorldZ;}
    public boolean mapOpen(){return mapOpen;}
    public NationalMapViewport mapViewport(){return mapViewport;}
    public Vector2 moveOrigin(){return moveOrigin;}
    public Vector2 moveCurrent(){return moveCurrent;}
    public Vector2 liftOrigin(){return liftOrigin;}
    public Vector2 liftCurrent(){return liftCurrent;}
    public boolean moving(){return movePointer>=0;}
    public boolean lifting(){return liftPointer>=0;}

    public void closeMap(){
        if(mapOpen){mapOpen=false;mapClosedRequested=true;Gdx.input.setCatchKey(Input.Keys.BACK,false);}
        releasePointers();clearMapPointers();
    }

    @Override public boolean touchDown(int screenX,int screenY,int pointer,int button){
        int width=Gdx.graphics.getWidth(),height=Gdx.graphics.getHeight();float bottomY=height-screenY;
        remember(pointer,screenX,bottomY);
        if(mapOpen)return mapTouchDown(screenX,bottomY,pointer,width,height);
        if(NationalMapLayout.minimap(width,height).contains(screenX,bottomY)){
            releasePointers();clearMapPointers();mapViewport.reset();mapOpen=true;mapOpenedRequested=true;Gdx.input.setCatchKey(Input.Keys.BACK,true);return true;
        }
        // These gameplay touch zones are deliberately unchanged from the
        // user-confirmed v0.7/v0.8 smooth movement implementation.
        if(screenY<95&&screenX>width-260){modeRequested=true;return true;}
        if(screenY<95&&screenX>width-520){recoveryRequested=true;return true;}
        if(screenX<width*.48f&&screenY>height*.30f&&movePointer<0){movePointer=pointer;moveOrigin.set(screenX,screenY);moveCurrent.set(moveOrigin);return true;}
        if(screenX>=width*.48f&&screenY>height*.30f&&liftPointer<0){liftPointer=pointer;liftOrigin.set(screenX,screenY);liftCurrent.set(liftOrigin);action=true;return true;}
        return false;
    }

    private boolean mapTouchDown(float x,float y,int pointer,int width,int height){
        NationalMapLayout.Layout layout=NationalMapLayout.fullscreen(width,height,worldWidth,worldHeight);
        if(mapPrimary<0){
            if(layout.close().contains(x,y)){closeMap();return true;}
            if(layout.teleport().contains(x,y)){teleportRequested=true;return true;}
            if(layout.zoomIn().contains(x,y)){changeZoom(mapViewport.zoom()*2.0,"ZOOM_IN");return true;}
            if(layout.zoomOut().contains(x,y)){changeZoom(mapViewport.zoom()*.5,"ZOOM_OUT");return true;}
            if(layout.viewAll().contains(x,y)){
                long revision=mapViewport.revision();mapViewport.reset();
                if(revision!=mapViewport.revision())markMapViewChanged("VIEW_ALL_QATAR");
                return true;
            }
        }
        if(!layout.map().contains(x,y)||!validPointer(pointer))return true;
        if(mapPrimary<0){
            mapPrimary=pointer;mapDownX=lastPrimaryX=x;mapDownY=lastPrimaryY=y;mapTouchStartedInside=true;mapGesture=false;
        }else if(pointer!=mapPrimary&&mapSecondary<0){
            mapSecondary=pointer;mapGesture=true;beginPinch();
        }
        return true;
    }

    @Override public boolean touchDragged(int screenX,int screenY,int pointer){
        float bottomY=Gdx.graphics.getHeight()-screenY;remember(pointer,screenX,bottomY);
        if(mapOpen)return dragMap(screenX,bottomY,pointer);
        if(pointer==movePointer){moveCurrent.set(screenX,screenY);limit(moveCurrent,moveOrigin,120);return true;}
        if(pointer==liftPointer){liftCurrent.set(screenX,screenY);limit(liftCurrent,liftOrigin,120);return true;}
        return false;
    }

    private boolean dragMap(float x,float y,int pointer){
        if(pointer!=mapPrimary&&pointer!=mapSecondary)return true;
        NationalMapLayout.Layout layout=NationalMapLayout.fullscreen(Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),worldWidth,worldHeight);
        if(mapPrimary>=0&&mapSecondary>=0){
            float px=pointerX[mapPrimary],py=pointerY[mapPrimary],sx=pointerX[mapSecondary],sy=pointerY[mapSecondary];
            float focusX=(px+sx)*.5f,focusY=(py+sy)*.5f,distance=(float)Math.hypot(px-sx,py-sy);
            long revision=mapViewport.revision();
            mapViewport.panByScreen(focusX-lastPinchX,focusY-lastPinchY,layout.map().width(),layout.map().height());
            if(lastPinchDistance>1&&distance>1){
                double anchorX=(focusX-layout.map().x())/layout.map().width(),anchorY=(focusY-layout.map().y())/layout.map().height();
                mapViewport.zoomAt(mapViewport.zoom()*distance/lastPinchDistance,anchorX,anchorY);
            }
            lastPinchDistance=distance;lastPinchX=focusX;lastPinchY=focusY;
            if(revision!=mapViewport.revision())markMapViewChanged("PINCH");
            return true;
        }
        if(pointer==mapPrimary){
            float dx=x-lastPrimaryX,dy=y-lastPrimaryY;
            if(Math.hypot(x-mapDownX,y-mapDownY)>TAP_SLOP)mapGesture=true;
            if(mapGesture){
                long revision=mapViewport.revision();mapViewport.panByScreen(dx,dy,layout.map().width(),layout.map().height());
                if(revision!=mapViewport.revision())markMapViewChanged("PAN");
            }
            lastPrimaryX=x;lastPrimaryY=y;
        }
        return true;
    }

    @Override public boolean touchUp(int screenX,int screenY,int pointer,int button){
        float bottomY=Gdx.graphics.getHeight()-screenY;remember(pointer,screenX,bottomY);
        if(!mapOpen){
            if(pointer==movePointer){movePointer=-1;moveCurrent.set(moveOrigin);return true;}
            if(pointer==liftPointer){liftPointer=-1;liftCurrent.set(liftOrigin);return true;}
            return false;
        }
        if(pointer==mapSecondary){
            mapSecondary=-1;mapGesture=true;
            if(mapPrimary>=0){lastPrimaryX=mapDownX=pointerX[mapPrimary];lastPrimaryY=mapDownY=pointerY[mapPrimary];}
            return true;
        }
        if(pointer==mapPrimary&&mapSecondary>=0){
            mapPrimary=mapSecondary;mapSecondary=-1;mapGesture=true;
            lastPrimaryX=mapDownX=pointerX[mapPrimary];lastPrimaryY=mapDownY=pointerY[mapPrimary];
            return true;
        }
        if(pointer==mapPrimary){
            NationalMapLayout.Layout layout=NationalMapLayout.fullscreen(Gdx.graphics.getWidth(),Gdx.graphics.getHeight(),worldWidth,worldHeight);
            if(mapTouchStartedInside&&!mapGesture&&layout.map().contains(screenX,bottomY)){
                mapSelectionScreenX=screenX;mapSelectionScreenY=bottomY;
                mapSelectionWorldX=(float)mapViewport.screenToWorldX(screenX,layout.map().x(),layout.map().width());
                mapSelectionWorldZ=(float)mapViewport.screenToWorldZ(bottomY,layout.map().y(),layout.map().height());
                mapSelectionRequested=true;
            }
            mapPrimary=-1;mapTouchStartedInside=false;mapGesture=false;
        }
        return true;
    }

    @Override public boolean touchCancelled(int screenX,int screenY,int pointer,int button){
        if(pointer==movePointer){movePointer=-1;moveCurrent.set(moveOrigin);}
        if(pointer==liftPointer){liftPointer=-1;liftCurrent.set(liftOrigin);}
        if(pointer==mapPrimary||pointer==mapSecondary)clearMapPointers();
        return true;
    }

    private void beginPinch(){
        float px=pointerX[mapPrimary],py=pointerY[mapPrimary],sx=pointerX[mapSecondary],sy=pointerY[mapSecondary];
        lastPinchX=(px+sx)*.5f;lastPinchY=(py+sy)*.5f;lastPinchDistance=(float)Math.hypot(px-sx,py-sy);
    }
    private void changeZoom(double zoom,String action){long revision=mapViewport.revision();mapViewport.zoomAt(zoom,.5,.5);if(revision!=mapViewport.revision())markMapViewChanged(action);}
    private void markMapViewChanged(String action){mapViewAction=action;mapViewChangedRequested=true;}
    private void remember(int pointer,float x,float y){if(validPointer(pointer)){pointerX[pointer]=x;pointerY[pointer]=y;}}
    private static boolean validPointer(int pointer){return pointer>=0&&pointer<MAX_POINTERS;}
    private void clearMapPointers(){mapPrimary=mapSecondary=-1;mapTouchStartedInside=false;mapGesture=false;}
    private void releasePointers(){movePointer=-1;liftPointer=-1;moveCurrent.set(moveOrigin);liftCurrent.set(liftOrigin);action=false;}
    private static void limit(Vector2 point,Vector2 origin,float radius){if(point.dst2(origin)>radius*radius)point.sub(origin).nor().scl(radius).add(origin);}
    private static float clamp(float value){return Math.max(-1,Math.min(1,value));}

    @Override public boolean keyDown(int keycode){
        if(mapOpen&&(keycode==Input.Keys.BACK||keycode==Input.Keys.ESCAPE)){closeMap();return true;}
        if(mapOpen&&keycode==Input.Keys.EQUALS){changeZoom(mapViewport.zoom()*2,"KEY_ZOOM_IN");return true;}
        if(mapOpen&&keycode==Input.Keys.MINUS){changeZoom(mapViewport.zoom()*.5,"KEY_ZOOM_OUT");return true;}
        if(mapOpen&&keycode==Input.Keys.HOME){long revision=mapViewport.revision();mapViewport.reset();if(revision!=mapViewport.revision())markMapViewChanged("KEY_VIEW_ALL");return true;}
        return false;
    }
    @Override public boolean keyUp(int keycode){return false;}
    @Override public boolean keyTyped(char character){return false;}
    @Override public boolean mouseMoved(int screenX,int screenY){return false;}
    @Override public boolean scrolled(float amountX,float amountY){return false;}
}
