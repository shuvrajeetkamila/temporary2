package ai.arena.qatar.input;

import ai.arena.qatar.physics.PhysicsWorld;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;

/** Allocation-free dual-zone touch controls with explicit mode and recovery actions. */
public final class TouchController implements InputProcessor {
    private int movePointer=-1,liftPointer=-1;
    private final Vector2 moveOrigin=new Vector2(),moveCurrent=new Vector2();
    private final Vector2 liftOrigin=new Vector2(),liftCurrent=new Vector2();
    private boolean action;
    private boolean modeRequested;
    private boolean recoveryRequested;

    public float throttle(){return clamp((moveOrigin.y-moveCurrent.y)/110f);}
    public float steering(){return clamp((moveCurrent.x-moveOrigin.x)/110f);}
    public float lift(){return clamp((liftOrigin.y-liftCurrent.y)/110f);}
    public boolean action(){boolean value=action;action=false;return value;}
    public boolean consumeModeRequested(){boolean value=modeRequested;modeRequested=false;return value;}
    public boolean consumeRecoveryRequested(){boolean value=recoveryRequested;recoveryRequested=false;return value;}
    public Vector2 moveOrigin(){return moveOrigin;}
    public Vector2 moveCurrent(){return moveCurrent;}
    public Vector2 liftOrigin(){return liftOrigin;}
    public Vector2 liftCurrent(){return liftCurrent;}
    public boolean moving(){return movePointer>=0;}
    public boolean lifting(){return liftPointer>=0;}

    @Override public boolean touchDown(int screenX,int screenY,int pointer,int button){
        float width=Gdx.graphics.getWidth(),height=Gdx.graphics.getHeight();
        if(screenY<95&&screenX>width-260){modeRequested=true;return true;}
        if(screenY<95&&screenX>width-520){recoveryRequested=true;return true;}
        if(screenX<width*.48f&&screenY>height*.30f&&movePointer<0){movePointer=pointer;moveOrigin.set(screenX,screenY);moveCurrent.set(moveOrigin);return true;}
        if(screenX>=width*.48f&&screenY>height*.30f&&liftPointer<0){liftPointer=pointer;liftOrigin.set(screenX,screenY);liftCurrent.set(liftOrigin);action=true;return true;}
        return false;
    }
    @Override public boolean touchDragged(int screenX,int screenY,int pointer){
        if(pointer==movePointer){moveCurrent.set(screenX,screenY);limit(moveCurrent,moveOrigin,120);return true;}
        if(pointer==liftPointer){liftCurrent.set(screenX,screenY);limit(liftCurrent,liftOrigin,120);return true;}
        return false;
    }
    @Override public boolean touchUp(int screenX,int screenY,int pointer,int button){
        if(pointer==movePointer){movePointer=-1;moveCurrent.set(moveOrigin);return true;}
        if(pointer==liftPointer){liftPointer=-1;liftCurrent.set(liftOrigin);return true;}
        return false;
    }
    private static void limit(Vector2 point,Vector2 origin,float radius){if(point.dst2(origin)>radius*radius)point.sub(origin).nor().scl(radius).add(origin);}
    private static float clamp(float value){return Math.max(-1,Math.min(1,value));}
    @Override public boolean keyDown(int keycode){return false;}
    @Override public boolean keyUp(int keycode){return false;}
    @Override public boolean keyTyped(char character){return false;}
    @Override public boolean mouseMoved(int screenX,int screenY){return false;}
    @Override public boolean scrolled(float amountX,float amountY){return false;}
    @Override public boolean touchCancelled(int screenX,int screenY,int pointer,int button){return touchUp(screenX,screenY,pointer,button);}
}
