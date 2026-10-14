package ai.arena.qatar.physics;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhysicsHealthTest {
    @Test public void acceptsBoundedStateForEveryFocusMode(){
        assertTrue(PhysicsHealth.stable(PhysicsWorld.Mode.WALK,.96f,.96f,4.3f,8f));
        assertTrue(PhysicsHealth.stable(PhysicsWorld.Mode.CAR,.72f,.72f,32f,12f));
        assertTrue(PhysicsHealth.stable(PhysicsWorld.Mode.BOAT,65f,.4f,18f,18f));
        assertTrue(PhysicsHealth.stable(PhysicsWorld.Mode.HELICOPTER,120f,120f,45f,27f));
        assertTrue(PhysicsHealth.stable(PhysicsWorld.Mode.AEROPLANE,800f,800f,120f,38f));
    }

    @Test public void rejectsFallsRunawaysInvalidNumbersAndBrokenCamera(){
        assertFalse(PhysicsHealth.stable(PhysicsWorld.Mode.WALK,-3f,-3f,1f,8f));
        assertFalse(PhysicsHealth.stable(PhysicsWorld.Mode.CAR,.7f,.7f,72f,12f));
        assertFalse(PhysicsHealth.stable(PhysicsWorld.Mode.CAR,-.4f,-.4f,0,12f));
        assertFalse(PhysicsHealth.stable(PhysicsWorld.Mode.BOAT,50f,.4f,55f,18f));
        assertFalse(PhysicsHealth.stable(PhysicsWorld.Mode.BOAT,-2f,-2f,0,18f));
        assertFalse(PhysicsHealth.stable(PhysicsWorld.Mode.HELICOPTER,1200f,1200f,1f,27f));
        assertFalse(PhysicsHealth.stable(PhysicsWorld.Mode.HELICOPTER,-1f,-1f,0,27f));
        assertFalse(PhysicsHealth.stable(PhysicsWorld.Mode.AEROPLANE,100f,100f,210f,38f));
        assertFalse(PhysicsHealth.stable(PhysicsWorld.Mode.AEROPLANE,-18f,-18f,18f,38f));
        assertFalse(PhysicsHealth.stable(PhysicsWorld.Mode.WALK,Float.NaN,1f,0,8f));
        assertFalse(PhysicsHealth.stable(PhysicsWorld.Mode.WALK,1f,1f,0,5.49f));
    }
}
