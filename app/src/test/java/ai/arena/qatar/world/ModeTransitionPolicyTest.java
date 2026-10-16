package ai.arena.qatar.world;

import ai.arena.qatar.physics.PhysicsWorld;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModeTransitionPolicyTest {
    @Test public void boatModeAppearsAtCurrentScreenLocationWithoutStreamReset(){
        assertEquals(ModeTransitionPolicy.Placement.CURRENT_SCREEN,ModeTransitionPolicy.placementFor(PhysicsWorld.Mode.BOAT));
        assertFalse(ModeTransitionPolicy.resetsStreams(PhysicsWorld.Mode.BOAT));
    }

    @Test public void remainingModesKeepTheirSafePlacementContracts(){
        assertEquals(ModeTransitionPolicy.Placement.ROTOR_CLEARANCE,ModeTransitionPolicy.placementFor(PhysicsWorld.Mode.HELICOPTER));
        assertEquals(ModeTransitionPolicy.Placement.RUNWAY,ModeTransitionPolicy.placementFor(PhysicsWorld.Mode.AEROPLANE));
        assertEquals(ModeTransitionPolicy.Placement.SAFE_GROUND,ModeTransitionPolicy.placementFor(PhysicsWorld.Mode.WALK));
        assertEquals(ModeTransitionPolicy.Placement.SAFE_GROUND,ModeTransitionPolicy.placementFor(PhysicsWorld.Mode.CAR));
        assertFalse(ModeTransitionPolicy.resetsStreams(PhysicsWorld.Mode.HELICOPTER));
        assertTrue(ModeTransitionPolicy.resetsStreams(PhysicsWorld.Mode.AEROPLANE));
        assertTrue(ModeTransitionPolicy.resetsStreams(PhysicsWorld.Mode.WALK));
        assertTrue(ModeTransitionPolicy.resetsStreams(PhysicsWorld.Mode.CAR));
    }
}
