package ai.arena.qatar.input;

import ai.arena.qatar.ui.NationalMapLayout;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;

/** Allocation-free dual-zone controls plus a modal, touch-selectable national map. */
public final class TouchController implements InputProcessor {
    private final double worldWidth,worldHeight;
    private int movePointer=-1,liftPointer=-1;
    private final Vector2 moveOrigin=new Vector2(),moveCurrent=new Vector2();
    private final Vector2 liftOrigin=new Vector2(),liftCurrent=new Vector2();
    private boolean action;
    private boolean modeRequested;
    private boolean recoveryRequested;
    private boolean mapOpen,mapOpenedRequested,mapClosedRequested;
    private boolean mapSelectionRequested,teleportRequested;
    private float mapSelectionScreenX,mapSelectionScreenY;

    public TouchController(double worldWidth,double worldHeight){this.worldWidth=worldWidth;this.worldHeight=worldHeight;}

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
    public float mapSelectionScreenX(){return mapSelectionScreenX;}
    /** LibGDX bottom-origin screen coordinate. */
    public float mapSelectionScreenY(){return mapSelectionScreenY;}
    public boolean mapOpen(){return mapOpen;}
    public Vector2 moveOrigin(){return moveOrigin;}
    public Vector2 moveCurrent(){return moveCurrent;}
    public Vector2 liftOrigin(){return liftOrigin;}
    public Vector2 liftCurrent(){return liftCurrent;}
    public boolean moving(){return movePointer>=0;}
    public boolean lifting(){return liftPointer>=0;}

    public void closeMap(){
        if(mapOpen){mapOpen=false;mapClosedRequested=true;Gdx.input.setCatchKey(Input.Keys.BACK,false);}
        releasePointers();
    }

    @Override public boolean touchDown(int screenX,int screenY,int pointer,int button){
        int width=Gdx.graphics.getWidth(),height=Gdx.graphics.getHeight();float bottomY=height-screenY;
        if(mapOpen){
            NationalMapLayout.Layout layout=NationalMapLayout.fullscreen(width,height,worldWidth,worldHeight);
            if(layout.close().contains(screenX,bottomY)){closeMap();return true;}
            if(layout.teleport().contains(screenX,bottomY)){teleportRequested=true;return true;}
            if(layout.map().contains(screenX,bottomY)){
                mapSelectionScreenX=screenX;mapSelectionScreenY=bottomY;mapSelectionRequested=true;return true;
            }
            return true;
        }
        if(NationalMapLayout.minimap(width,height).contains(screenX,bottomY)){
            releasePointers();mapOpen=true;mapOpenedRequested=true;Gdx.input.setCatchKey(Input.Keys.BACK,true);return true;
        }
        if(screenY<95&&screenX>width-260){modeRequested=true;return true;}
        if(screenY<95&&screenX>width-520){recoveryRequested=true;return true;}
        if(screenX<width*.48f&&screenY>height*.30f&&movePointer<0){movePointer=pointer;moveOrigin.set(screenX,screenY);moveCurrent.set(moveOrigin);return true;}
        if(screenX>=width*.48f&&screenY>height*.30f&&liftPointer<0){liftPointer=pointer;liftOrigin.set(screenX,screenY);liftCurrent.set(liftOrigin);action=true;return true;}
        return false;
    }
    @Override public boolean touchDragged(int screenX,int screenY,int pointer){
        if(mapOpen)return true;
        if(pointer==movePointer){moveCurrent.set(screenX,screenY);limit(moveCurrent,moveOrigin,120);return true;}
        if(pointer==liftPointer){liftCurrent.set(screenX,screenY);limit(liftCurrent,liftOrigin,120);return true;}
        return false;
    }
    @Override public boolean touchUp(int screenX,int screenY,int pointer,int button){
        if(pointer==movePointer){movePointer=-1;moveCurrent.set(moveOrigin);return true;}
        if(pointer==liftPointer){liftPointer=-1;liftCurrent.set(liftOrigin);return true;}
        return mapOpen;
    }
    private void releasePointers(){
        movePointer=-1;liftPointer=-1;moveCurrent.set(moveOrigin);liftCurrent.set(liftOrigin);action=false;
    }
    private static void limit(Vector2 point,Vector2 origin,float radius){if(point.dst2(origin)>radius*radius)point.sub(origin).nor().scl(radius).add(origin);}
    private static float clamp(float value){return Math.max(-1,Math.min(1,value));}
    @Override public boolean keyDown(int keycode){
        if(mapOpen&&(keycode==Input.Keys.BACK||keycode==Input.Keys.ESCAPE)){closeMap();return true;}
        return false;
    }
    @Override public boolean keyUp(int keycode){return false;}
    @Override public boolean keyTyped(char character){return false;}
    @Override public boolean mouseMoved(int screenX,int screenY){return false;}
    @Override public boolean scrolled(float amountX,float amountY){return false;}
    @Override public boolean touchCancelled(int screenX,int screenY,int pointer,int button){return touchUp(screenX,screenY,pointer,button);}
}
