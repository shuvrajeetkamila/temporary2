package ai.arena.nauru.world;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Host-side startup regression for the packaged GIS data and rebuilt compact world. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public final class NauruWorldStartupTest {
    @Test
    public void packagedMapBuildsPlayableThermalEfficientOneToTwentyFiveWorld() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        NauruWorld world = new NauruWorld(context);

        assertEquals("island coordinates must use the selected 1:25 scale",
                1f / 25f, NauruWorld.WORLD_SCALE, 0.000001f);
        assertTrue("detailed real coastline expected", world.coastlineCoordinates().length >= 500);
        assertTrue("selected road network should retain important routes",
                world.mapRoads().size() >= 180 && world.mapRoads().size() <= 220);
        assertTrue("source map must still contain all mapped structures",
                world.mappedBuildingCount() >= 2750);
        assertTrue("representative architecture must be retained but aggressively culled",
                world.renderedBuildingCount() >= 180
                        && world.renderedBuildingCount() <= 310
                        && world.renderedBuildingCount() < world.mappedBuildingCount());
        assertTrue("representative mapped solar infrastructure expected",
                world.renderedSolarCount() >= 170 && world.renderedSolarCount() <= 190);
        assertTrue("mapped land cover expected", world.mappedAreaCount() >= 650);
        assertTrue("mapped labels expected", world.mappedPlaceCount() >= 50);

        float maximumCoastCoordinate = 0f;
        for (float coordinate : world.coastlineCoordinates()) {
            maximumCoastCoordinate = Math.max(maximumCoastCoordinate, Math.abs(coordinate));
        }
        assertTrue("compressed island must fit the compact game world",
                maximumCoastCoordinate > 90f && maximumCoastCoordinate < 160f);

        float[] player = world.spawnPoint();
        float[] vehicle = world.vehicleSpawnPoint();
        assertTrue("player must spawn on mapped land", world.isWalkableLand(player[0], player[2]));
        assertTrue("vehicle must spawn on mapped land", world.isWalkableLand(vehicle[0], vehicle[2]));
        assertTrue("vertically compressed airport elevation should be playable",
                player[1] > .5f && player[1] < 4f);
        assertTrue("human-scale vehicle should be beside player",
                Math.hypot(player[0] - vehicle[0], player[2] - vehicle[2]) < 12f);
        assertEquals("Nauru International Airport",
                world.locationName(player[0], player[2]));
        assertTrue(world.scaleReadout().contains("1:25"));
    }
}
