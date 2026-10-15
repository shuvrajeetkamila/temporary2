package ai.arena.qatar.render;

import com.badlogic.gdx.math.Vector3;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CameraMotionFilterTest {
    @Test public void exponentialResponseIsFrameRateIndependent(){
        CameraMotionFilter thirty=new CameraMotionFilter(),sixty=new CameraMotionFilter();
        Vector3 origin=new Vector3(),destination=new Vector3(100,20,30);
        thirty.update(origin,origin,1f/30f,6,8);sixty.update(origin,origin,1f/60f,6,8);
        for(int i=0;i<30;i++)thirty.update(destination,destination,1f/30f,6,8);
        for(int i=0;i<60;i++)sixty.update(destination,destination,1f/60f,6,8);
        assertEquals(thirty.camera().x,sixty.camera().x,.02f);
        assertEquals(thirty.target().y,sixty.target().y,.02f);
    }

    @Test public void highSpeedVerticalPhysicsNoiseIsAttenuatedWithoutStoppingForwardMotion(){
        CameraMotionFilter filter=new CameraMotionFilter();Vector3 desiredCamera=new Vector3(),target=new Vector3();
        float maximumFilteredVerticalStep=0,previousY=0;
        for(int frame=0;frame<120;frame++){
            float noise=(frame&1)==0?.18f:-.18f;
            desiredCamera.set(frame*2f,8+noise,-20);target.set(frame*2f,2+noise,0);
            filter.update(desiredCamera,target,1f/30f,5,7);
            if(frame>5)maximumFilteredVerticalStep=Math.max(maximumFilteredVerticalStep,Math.abs(filter.target().y-previousY));
            previousY=filter.target().y;
        }
        assertTrue("target should continue tracking fast forward motion",filter.target().x>210);
        assertTrue("alternating contact noise should be damped",maximumFilteredVerticalStep<.18f);
    }
}
