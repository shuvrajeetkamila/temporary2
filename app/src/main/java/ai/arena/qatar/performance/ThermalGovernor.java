package ai.arena.qatar.performance;

/** Maps Android thermal status to bounded rendering, streaming, and collision budgets. */
public final class ThermalGovernor {
    public enum Profile {
        NORMAL("NORMAL",30,3,64,2,6,1f),
        WARM("WARM",20,2,32,1,3,.78f),
        HOT("HOT",15,1,16,1,1,.58f);
        public final String label;public final int targetFps,detailRadiusCells,maxDetailCells,activationBudget,buildingCollisionCells;public final float viewDistanceScale;
        Profile(String label,int fps,int radius,int residents,int activation,int collision,float viewScale){this.label=label;targetFps=fps;detailRadiusCells=radius;maxDetailCells=residents;activationBudget=activation;buildingCollisionCells=collision;viewDistanceScale=viewScale;}
    }
    private ThermalGovernor(){}
    public static int effectiveAndroidStatus(int thermalStatus,boolean powerSaveMode){return powerSaveMode?Math.max(2,thermalStatus):thermalStatus;}
    public static Profile profileForAndroidStatus(int status){if(status>=3)return Profile.HOT;if(status>=2)return Profile.WARM;return Profile.NORMAL;}
}
