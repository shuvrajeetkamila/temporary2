package ai.arena.qatar.physics;

import com.badlogic.gdx.math.Vector3;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PoseInterpolatorTest {
    @Test public void samplesBetweenCompletedFixedTicksAndSurvivesRebase(){
        PoseInterpolator poses=new PoseInterpolator();Vector3 out=new Vector3();
        poses.reset(new Vector3(0,2,0),0);
        poses.advance(new Vector3(12,2,4),.2f);
        poses.sample(.5f,out);
        assertEquals(6,out.x,.0001f);assertEquals(2,out.z,.0001f);assertEquals(.1f,poses.sampleHeading(.5f),.0001f);
        poses.shift(10,3);poses.sample(.5f,out);
        assertEquals(-4,out.x,.0001f);assertEquals(-1,out.z,.0001f);
    }

    @Test public void headingInterpolationUsesShortestPathAcrossPi(){
        PoseInterpolator poses=new PoseInterpolator();
        poses.reset(Vector3.Zero,(float)Math.toRadians(179));
        poses.advance(Vector3.Zero,(float)Math.toRadians(-179));
        float midpoint=poses.sampleHeading(.5f);
        assertTrue(Math.abs(Math.abs(midpoint)-(float)Math.PI)<.001f);
    }
}
