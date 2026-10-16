package ai.arena.qatar.render;

import org.junit.Test;

import static org.junit.Assert.*;

public final class CoastalFeaturePolicyTest {
    @Test public void lowCoastalBuildingsReceiveDryFoundations(){
        assertEquals(.75f,CoastalFeaturePolicy.buildingBase(-2f,false),.0001f);
        assertTrue(CoastalFeaturePolicy.needsBuildingFoundation(-2f,false));
        assertFalse(CoastalFeaturePolicy.needsBuildingFoundation(7.58f,true));
    }

    @Test public void runwaysAndPermanentRoadsRemainAboveDesignTide(){
        float runway=CoastalFeaturePolicy.transportBase(-.76f,true,10,0);
        assertTrue(runway>CoastalFeaturePolicy.DESIGN_HIGH_WATER_METRES);
        assertTrue(CoastalFeaturePolicy.needsTransportPad(-.76f,true,10,0));
        assertEquals(12f,CoastalFeaturePolicy.transportBase(12f,true,5,0),.0001f);
    }

    @Test public void bridgesClearWaterButTunnelsStayAtMeasuredLevel(){
        assertEquals(5.05f,CoastalFeaturePolicy.transportBase(-3f,false,2,1),.0001f);
        assertEquals(-3f,CoastalFeaturePolicy.transportBase(-3f,false,2,2),.0001f);
    }
}
