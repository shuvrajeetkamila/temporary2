package ai.arena.qatar.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Locks the QTOD cell-local to national-map coordinate contract. */
public final class NationalMapRoadCacheTest {
    @Test public void cellLocalRoadCoordinatesBecomeGlobalMetres(){
        assertEquals(52_137.5f,NationalMapRoadCache.toGlobal(208,137.5f,250),0.001f);
        assertEquals(103_249.9f,NationalMapRoadCache.toGlobal(412,249.9f,250),0.001f);
    }
}
