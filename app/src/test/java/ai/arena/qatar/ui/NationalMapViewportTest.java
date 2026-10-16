package ai.arena.qatar.ui;

import org.junit.Test;

import static org.junit.Assert.*;

public final class NationalMapViewportTest {
    private static final double W=110_000.0;
    private static final double H=206_000.0;

    @Test public void initialAndResetViewsAllQatar(){
        NationalMapViewport view=new NationalMapViewport(W,H);
        assertEquals(1.0,view.zoom(),0.0);
        assertEquals(0.0,view.minX(),1e-7);
        assertEquals(W,view.maxX(),1e-7);
        assertEquals(0.0,view.minZ(),1e-7);
        assertEquals(H,view.maxZ(),1e-7);
        view.zoomAt(16.0,0.7,0.3);
        view.panByScreen(90,-50,300,600);
        view.reset();
        assertEquals(1.0,view.zoom(),0.0);
        assertEquals(W*0.5,view.centerX(),1e-7);
        assertEquals(H*0.5,view.centerZ(),1e-7);
    }

    @Test public void pinchPreservesWorldPointUnderAnchor(){
        NationalMapViewport view=new NationalMapViewport(W,H);
        double anchorX=0.72,anchorY=0.31;
        double beforeX=view.minX()+anchorX*view.visibleWidth();
        double beforeZ=view.minZ()+anchorY*view.visibleHeight();
        view.zoomAt(8.0,anchorX,anchorY);
        assertEquals(beforeX,view.minX()+anchorX*view.visibleWidth(),1e-7);
        assertEquals(beforeZ,view.minZ()+anchorY*view.visibleHeight(),1e-7);
    }

    @Test public void zoomAndPanAreBoundedToPackagedWorld(){
        NationalMapViewport view=new NationalMapViewport(W,H);
        view.zoomAt(10.0,0.5,0.5);
        view.panByScreen(1_000_000,1_000_000,300,600);
        assertEquals(0.0,view.minX(),1e-7);
        assertEquals(0.0,view.minZ(),1e-7);
        view.panByScreen(-1_000_000,-1_000_000,300,600);
        assertEquals(W,view.maxX(),1e-7);
        assertEquals(H,view.maxZ(),1e-7);
        view.zoomAt(1_000.0,0.5,0.5);
        assertEquals(NationalMapViewport.MAX_ZOOM,view.zoom(),0.0);
        assertTrue(view.minX()>=0.0&&view.maxX()<=W);
        assertTrue(view.minZ()>=0.0&&view.maxZ()<=H);
    }

    @Test public void screenWorldRoundTripUsesCurrentViewport(){
        NationalMapViewport view=new NationalMapViewport(W,H);
        view.zoomAt(12.0,0.65,0.58);
        view.panByScreen(-43,27,320,600);
        float mapX=120,mapY=80,mapWidth=320,mapHeight=600;
        double worldX=view.screenToWorldX(311,mapX,mapWidth);
        double worldZ=view.screenToWorldZ(417,mapY,mapHeight);
        assertEquals(311.0,view.worldToScreenX(worldX,mapX,mapWidth),1e-3);
        assertEquals(417.0,view.worldToScreenY(worldZ,mapY,mapHeight),1e-3);
    }

    @Test public void revisionChangesOnlyWhenCameraChanges(){
        NationalMapViewport view=new NationalMapViewport(W,H);
        long initial=view.revision();
        view.reset();
        assertEquals(initial,view.revision());
        view.zoomBy(2.0);
        assertTrue(view.revision()>initial);
    }
}
