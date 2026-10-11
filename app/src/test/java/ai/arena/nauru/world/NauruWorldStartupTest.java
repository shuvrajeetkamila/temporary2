package ai.arena.nauru.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Host-side startup regression test for packaged GIS data and CPU world generation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public final class NauruWorldStartupTest {
    @Test
    public void packagedRealMapBuildsAPlayableAirportSpawn() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        NauruWorld world = new NauruWorld(context);

        assertTrue("detailed real coastline expected", world.coastlineCoordinates().length >= 500);
        assertTrue("mapped road and maritime network expected", world.mapRoads().size() >= 570);
        assertTrue("mapped buildings and infrastructure expected", world.mappedBuildingCount() >= 2750);
        assertTrue("mapped land cover and solar panels expected", world.mappedAreaCount() >= 650);
        assertTrue("mapped labels expected", world.mappedPlaceCount() >= 50);

        float[] player = world.spawnPoint();
        float[] vehicle = world.vehicleSpawnPoint();
        assertTrue("player must spawn on mapped land", world.isWalkableLand(player[0], player[2]));
        assertTrue("vehicle must spawn on mapped land", world.isWalkableLand(vehicle[0], vehicle[2]));
        assertTrue("airport elevation should be plausible", player[1] > 4f && player[1] < 15f);
        assertTrue("vehicle should be beside player",
                Math.hypot(player[0] - vehicle[0], player[2] - vehicle[2]) < 12f);
        assertEquals("Nauru International Airport",
                world.locationName(player[0], player[2]));
    }
}
