package ai.arena.qatar.world;

import ai.arena.qatar.physics.PhysicsWorld;

/** Defines automatic placement only for mode cycling; explicit map teleport is separate. */
public final class ModeTransitionPolicy {
    public enum Placement { CURRENT_SCREEN, ROTOR_CLEARANCE, SAFE_GROUND, RUNWAY }

    private ModeTransitionPolicy(){}

    public static Placement placementFor(PhysicsWorld.Mode mode){
        return switch(mode){
            case BOAT -> Placement.CURRENT_SCREEN;
            case HELICOPTER -> Placement.ROTOR_CLEARANCE;
            case AEROPLANE -> Placement.RUNWAY;
            case WALK,CAR -> Placement.SAFE_GROUND;
        };
    }

    public static boolean resetsStreams(PhysicsWorld.Mode mode){
        return placementFor(mode)==Placement.SAFE_GROUND||placementFor(mode)==Placement.RUNWAY;
    }
}
