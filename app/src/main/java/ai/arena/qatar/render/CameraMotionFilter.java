package ai.arena.qatar.render;

import com.badlogic.gdx.math.Vector3;

/**
 * Frame-rate-independent vertical camera damping.
 *
 * Horizontal chase and aim coordinates follow the already interpolated physics
 * pose exactly. Filtering them a second time created elastic lag and made fast
 * modes feel as though they jerked; only terrain/contact-height noise is damped.
 */
public final class CameraMotionFilter {
    private final Vector3 camera=new Vector3();
    private final Vector3 target=new Vector3();
    private boolean initialized;

    public void update(Vector3 desiredCamera,Vector3 desiredTarget,float delta,float cameraResponse,float targetResponse){
        if(!initialized){camera.set(desiredCamera);target.set(desiredTarget);initialized=true;return;}
        float safeDelta=Math.max(0,Math.min(.1f,delta));
        camera.x=desiredCamera.x;camera.z=desiredCamera.z;
        target.x=desiredTarget.x;target.z=desiredTarget.z;
        camera.y+=(desiredCamera.y-camera.y)*exponentialAlpha(cameraResponse,safeDelta);
        target.y+=(desiredTarget.y-target.y)*exponentialAlpha(targetResponse,safeDelta);
    }

    public Vector3 camera(){return camera;}
    public Vector3 target(){return target;}
    public void reset(){initialized=false;}
    public boolean initialized(){return initialized;}

    static float exponentialAlpha(float response,float delta){return 1f-(float)Math.exp(-Math.max(.01f,response)*Math.max(0,delta));}
}
