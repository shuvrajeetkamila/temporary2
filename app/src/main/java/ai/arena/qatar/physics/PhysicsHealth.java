package ai.arena.qatar.physics;

/** Deterministic bounds used by Android smoke tests to reject runaway or underground focus bodies. */
public final class PhysicsHealth {
    private PhysicsHealth(){}

    public static boolean stable(PhysicsWorld.Mode mode,float heightAboveGround,float supportClearance,float speed,float cameraDistance){
        if(mode==null||!Float.isFinite(heightAboveGround)||!Float.isFinite(supportClearance)||!Float.isFinite(speed)||!Float.isFinite(cameraDistance)||speed<0||cameraDistance<5.5f)return false;
        return switch(mode){
            case WALK->supportClearance>.30f&&supportClearance<4f&&speed<12f;
            case CAR->supportClearance>-.50f&&supportClearance<8f&&speed<70f;
            case BOAT->supportClearance>-3f&&supportClearance<10f&&speed<50f;
            case HELICOPTER->heightAboveGround>-2f&&heightAboveGround<1000f&&speed<110f;
            case AEROPLANE->heightAboveGround>-2f&&heightAboveGround<3000f&&speed<200f;
        };
    }
}
