package ai.arena.qatar.physics;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * One-fixed-tick-behind interpolation for render poses.
 *
 * Bullet remains authoritative. Rendering samples between the two most recent
 * completed 60 Hz states so 15/20/30 Hz Android presentation never follows a
 * staircase of raw physics transforms.
 */
public final class PoseInterpolator {
    private final Vector3 previous=new Vector3();
    private final Vector3 current=new Vector3();
    private final Vector3 scratch=new Vector3();
    private float previousHeading;
    private float currentHeading;
    private boolean initialized;

    public void reset(Vector3 position,float headingRadians){
        previous.set(position);current.set(position);
        previousHeading=headingRadians;currentHeading=headingRadians;initialized=true;
    }

    public void advance(Vector3 position,float headingRadians){
        if(!initialized){reset(position,headingRadians);return;}
        previous.set(current);previousHeading=currentHeading;
        current.set(position);currentHeading=headingRadians;
    }

    /** Corrects a post-step terrain guard without discarding the prior tick. */
    public void correctCurrent(Vector3 position,float headingRadians){
        if(!initialized){reset(position,headingRadians);return;}
        current.set(position);currentHeading=headingRadians;
    }

    /** Keeps both history samples in the same local frame after a floating-origin shift. */
    public void shift(float x,float z){
        previous.x-=x;previous.z-=z;current.x-=x;current.z-=z;
    }

    public Vector3 sample(float alpha,Vector3 out){
        float t=clamp01(alpha);
        return out.set(previous).lerp(current,t);
    }

    public float sampleHeading(float alpha){
        float difference=wrapRadians(currentHeading-previousHeading);
        return wrapRadians(previousHeading+difference*clamp01(alpha));
    }

    public Matrix4 sampleTransform(float alpha,Matrix4 out){
        Vector3 position=sample(alpha,scratch);
        return out.idt().setToTranslation(position).rotate(Vector3.Y,(float)Math.toDegrees(sampleHeading(alpha)));
    }

    private static float clamp01(float value){return Math.max(0,Math.min(1,value));}
    private static float wrapRadians(float value){
        while(value>(float)Math.PI)value-=(float)(Math.PI*2);
        while(value<-(float)Math.PI)value+=(float)(Math.PI*2);
        return value;
    }
}
