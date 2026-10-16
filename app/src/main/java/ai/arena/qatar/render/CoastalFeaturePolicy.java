package ai.arena.qatar.render;

/**
 * Conservative game-rendering clearance for OSM features near Qatar's coarse
 * legal-land mask. This is visual/physics support, never a legal coastline or
 * navigation claim.
 */
public final class CoastalFeaturePolicy {
    /** Authored tide peaks below this value. */
    public static final float DESIGN_HIGH_WATER_METRES=.55f;
    /** Top of permanent reclaimed pads/foundations. */
    public static final float MINIMUM_DRY_FEATURE_METRES=.75f;
    public static final float BRIDGE_CLEARANCE_METRES=4.5f;
    public static final float FOUNDATION_BOTTOM_METRES=.25f;

    private CoastalFeaturePolicy(){}

    public static float buildingBase(float measuredTerrain,boolean landMask){
        if(!Float.isFinite(measuredTerrain))measuredTerrain=-2f;
        return Math.max(measuredTerrain,MINIMUM_DRY_FEATURE_METRES);
    }

    public static float transportBase(float measuredTerrain,boolean landMask,int roadClass,int flags){
        if(!Float.isFinite(measuredTerrain))measuredTerrain=-2f;
        if(isTunnel(flags))return measuredTerrain;
        if(isBridge(flags))return Math.max(measuredTerrain,DESIGN_HIGH_WATER_METRES+BRIDGE_CLEARANCE_METRES);
        if(isPermanentTransport(roadClass))return Math.max(measuredTerrain,MINIMUM_DRY_FEATURE_METRES);
        return measuredTerrain;
    }

    public static boolean needsBuildingFoundation(float measuredTerrain,boolean landMask){
        return !landMask||buildingBase(measuredTerrain,landMask)>measuredTerrain+.04f;
    }

    public static boolean needsTransportPad(float measuredTerrain,boolean landMask,int roadClass,int flags){
        return !isTunnel(flags)&&isPermanentTransport(roadClass)&&(!landMask||transportBase(measuredTerrain,landMask,roadClass,flags)>measuredTerrain+.04f);
    }

    public static boolean isPermanentTransport(int roadClass){return roadClass<=7||roadClass>=9;}
    public static boolean isBridge(int flags){return (flags&1)!=0;}
    public static boolean isTunnel(int flags){return (flags&2)!=0;}
}
