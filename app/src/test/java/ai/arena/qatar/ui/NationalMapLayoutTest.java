package ai.arena.qatar.ui;

import com.badlogic.gdx.math.Vector2;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NationalMapLayoutTest {
    @Test public void fullCountryProjectionRoundTripsTouchCoordinates(){
        double width=110_000,height=206_000;
        NationalMapLayout.Layout layout=NationalMapLayout.fullscreen(1280,720,width,height);
        Vector2 screen=new Vector2(),world=new Vector2();
        NationalMapLayout.worldToScreen(layout.map(),72_345,154_321,width,height,screen);
        assertTrue(layout.map().contains(screen.x,screen.y));
        NationalMapLayout.screenToWorld(layout.map(),screen.x,screen.y,width,height,world);
        assertEquals(72_345,world.x,.05);assertEquals(154_321,world.y,.05);
        assertFalse(layout.map().contains(layout.close().x(),layout.close().y()));
        assertFalse(layout.map().contains(layout.teleport().x(),layout.teleport().y()));
    }

    @Test public void northIsHigherOnMapAndMinimapIsLargeEnoughForTouch(){
        NationalMapLayout.Layout layout=NationalMapLayout.fullscreen(1280,720,110_000,206_000);
        Vector2 south=new Vector2(),north=new Vector2();
        NationalMapLayout.worldToScreen(layout.map(),55_000,20_000,110_000,206_000,south);
        NationalMapLayout.worldToScreen(layout.map(),55_000,190_000,110_000,206_000,north);
        assertTrue(north.y>south.y);
        NationalMapLayout.Rect mini=NationalMapLayout.minimap(1280,720);
        assertTrue(mini.width()>170&&mini.height()>180);
    }
}
