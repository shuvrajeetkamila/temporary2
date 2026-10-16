package ai.arena.qatar.ui;

/**
 * Pure, deterministic camera for the expanded national map. World Z is north
 * and therefore increases toward the top of the map. The camera always remains
 * inside the packaged Qatar world rectangle.
 */
public final class NationalMapViewport {
    public static final double MIN_ZOOM=1.0;
    public static final double MAX_ZOOM=64.0;

    private final double worldWidth;
    private final double worldHeight;
    private double centerX;
    private double centerZ;
    private double zoom;
    private long revision;

    public NationalMapViewport(double worldWidth,double worldHeight){
        if(!(worldWidth>0.0)||!(worldHeight>0.0)) throw new IllegalArgumentException("World dimensions must be positive");
        this.worldWidth=worldWidth;
        this.worldHeight=worldHeight;
        centerX=worldWidth*0.5;
        centerZ=worldHeight*0.5;
        zoom=MIN_ZOOM;
    }

    public double worldWidth(){return worldWidth;}
    public double worldHeight(){return worldHeight;}
    public double centerX(){return centerX;}
    public double centerZ(){return centerZ;}
    public double zoom(){return zoom;}
    public double visibleWidth(){return worldWidth/zoom;}
    public double visibleHeight(){return worldHeight/zoom;}
    public double minX(){return centerX-visibleWidth()*0.5;}
    public double maxX(){return centerX+visibleWidth()*0.5;}
    public double minZ(){return centerZ-visibleHeight()*0.5;}
    public double maxZ(){return centerZ+visibleHeight()*0.5;}
    public long revision(){return revision;}

    /** Restores the complete-country view. */
    public void reset(){
        update(worldWidth*0.5,worldHeight*0.5,MIN_ZOOM);
    }

    /** Zooms around the centre of the current view. */
    public void zoomBy(double factor){
        zoomAtWorld(zoom*factor,centerX,centerZ);
    }

    /**
     * Changes zoom while keeping the world point beneath the normalized map
     * anchor fixed. Anchor (0,0) is south-west; (1,1) is north-east.
     */
    public void zoomAt(double requestedZoom,double anchorX,double anchorY){
        double ax=clamp(anchorX,0.0,1.0);
        double ay=clamp(anchorY,0.0,1.0);
        zoomAtWorld(requestedZoom,
            minX()+ax*visibleWidth(),
            minZ()+ay*visibleHeight(),ax,ay);
    }

    /** Changes zoom around a world coordinate at its current screen location. */
    public void zoomAtWorld(double requestedZoom,double worldX,double worldZ){
        double ax=(worldX-minX())/visibleWidth();
        double ay=(worldZ-minZ())/visibleHeight();
        zoomAtWorld(requestedZoom,worldX,worldZ,ax,ay);
    }

    private void zoomAtWorld(double requestedZoom,double worldX,double worldZ,double anchorX,double anchorY){
        double nextZoom=clamp(requestedZoom,MIN_ZOOM,MAX_ZOOM);
        double nextWidth=worldWidth/nextZoom;
        double nextHeight=worldHeight/nextZoom;
        double nextCenterX=worldX+(0.5-anchorX)*nextWidth;
        double nextCenterZ=worldZ+(0.5-anchorY)*nextHeight;
        update(nextCenterX,nextCenterZ,nextZoom);
    }

    /**
     * Pans by a finger's screen displacement. Dragging the finger east/north
     * moves map content east/north, so the camera centre moves oppositely.
     */
    public void panByScreen(double deltaPixelsX,double deltaPixelsY,double mapPixelsWidth,double mapPixelsHeight){
        if(!(mapPixelsWidth>0.0)||!(mapPixelsHeight>0.0)) return;
        update(centerX-deltaPixelsX/mapPixelsWidth*visibleWidth(),
            centerZ-deltaPixelsY/mapPixelsHeight*visibleHeight(),zoom);
    }

    /** Centres the current zoom on a world point, constrained to world bounds. */
    public void centerOn(double worldX,double worldZ){update(worldX,worldZ,zoom);}

    public double screenToWorldX(double screenX,double mapX,double mapWidth){
        if(!(mapWidth>0.0)) return centerX;
        return clamp(minX()+(screenX-mapX)/mapWidth*visibleWidth(),0.0,worldWidth);
    }

    public double screenToWorldZ(double screenY,double mapY,double mapHeight){
        if(!(mapHeight>0.0)) return centerZ;
        return clamp(minZ()+(screenY-mapY)/mapHeight*visibleHeight(),0.0,worldHeight);
    }

    public float worldToScreenX(double worldX,float mapX,float mapWidth){
        return (float)(mapX+(worldX-minX())/visibleWidth()*mapWidth);
    }

    public float worldToScreenY(double worldZ,float mapY,float mapHeight){
        return (float)(mapY+(worldZ-minZ())/visibleHeight()*mapHeight);
    }

    public boolean contains(double worldX,double worldZ){
        return worldX>=minX()&&worldX<=maxX()&&worldZ>=minZ()&&worldZ<=maxZ();
    }

    public Snapshot snapshot(){
        return new Snapshot(centerX,centerZ,zoom,minX(),minZ(),maxX(),maxZ(),revision);
    }

    private void update(double requestedCenterX,double requestedCenterZ,double requestedZoom){
        double nextZoom=clamp(requestedZoom,MIN_ZOOM,MAX_ZOOM);
        double halfWidth=worldWidth/nextZoom*0.5;
        double halfHeight=worldHeight/nextZoom*0.5;
        double nextX=clamp(requestedCenterX,halfWidth,worldWidth-halfWidth);
        double nextZ=clamp(requestedCenterZ,halfHeight,worldHeight-halfHeight);
        if(Math.abs(nextX-centerX)>1e-7||Math.abs(nextZ-centerZ)>1e-7||Math.abs(nextZoom-zoom)>1e-9){
            centerX=nextX;centerZ=nextZ;zoom=nextZoom;revision++;
        }
    }

    private static double clamp(double value,double low,double high){return Math.max(low,Math.min(high,value));}

    /** Immutable hand-off for asynchronous cartography. */
    public record Snapshot(double centerX,double centerZ,double zoom,double minX,double minZ,double maxX,double maxZ,long revision){
        public double width(){return maxX-minX;}
        public double height(){return maxZ-minZ;}
    }
}
