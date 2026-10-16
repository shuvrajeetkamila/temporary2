package ai.arena.qatar.ui;

import ai.arena.qatar.world.QatarDetailPack;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.TimeUtils;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * Debounced, bounded, off-render-thread raster cache for close national-map
 * views. It reads only the 250 m QTOD cells intersecting the viewport and never
 * walks the full 1.44 million-segment data set.
 */
public final class NationalMapRoadCache implements Disposable {
    public static final double DETAIL_MIN_ZOOM=12.0;
    private static final int PIXELS_W=384,PIXELS_H=720;
    private static final int MAX_GRID_CELLS=3_200;
    private static final int MAX_ROADS=120_000;
    private static final long SETTLE_MILLIS=180;

    private final QatarDetailPack detailPack;
    private final ExecutorService worker;
    private final ConcurrentLinkedQueue<Result> completed=new ConcurrentLinkedQueue<>();
    private Future<?> inFlight;
    private NationalMapViewport.Snapshot requested;
    private long requestedAt;
    private long textureRevision=Long.MIN_VALUE,attemptedRevision=Long.MIN_VALUE;
    private int generation;
    private Texture texture;
    private int visibleRoads,visibleCells;
    private String state="OVERVIEW ATLAS";
    private boolean active;
    private volatile boolean disposed;

    public NationalMapRoadCache(QatarDetailPack detailPack){
        this.detailPack=detailPack;
        ThreadFactory factory=task->{Thread thread=new Thread(task,"qatar-precision-map");thread.setDaemon(true);return thread;};
        worker=Executors.newSingleThreadExecutor(factory);
    }

    /** Called once per frame; GPU upload and Pixmap disposal happen here. */
    public void update(NationalMapViewport viewport,boolean shouldBeActive){
        drain(viewport);
        if(!shouldBeActive){
            if(active){active=false;generation++;cancelInFlight();requested=null;state="OVERVIEW ATLAS";}
            return;
        }
        active=true;
        NationalMapViewport.Snapshot snapshot=viewport.snapshot();
        if(snapshot.zoom()<DETAIL_MIN_ZOOM){
            if(requested!=null){generation++;cancelInFlight();requested=null;}
            state="OVERVIEW ATLAS";
            return;
        }
        if(requested==null||requested.revision()!=snapshot.revision()){
            generation++;cancelInFlight();requested=snapshot;requestedAt=TimeUtils.millis();
            state="OFFLINE STREET DETAIL SETTLING";
        }
        if(textureRevision==snapshot.revision()){state="OFFLINE STREET DETAIL READY";return;}
        if(inFlight==null&&attemptedRevision!=requested.revision()&&TimeUtils.timeSinceMillis(requestedAt)>=SETTLE_MILLIS){
            submit(requested,generation);
        }
    }

    public void draw(SpriteBatch batch,NationalMapViewport viewport,float x,float y,float width,float height){
        if(texture!=null&&textureRevision==viewport.revision()&&viewport.zoom()>=DETAIL_MIN_ZOOM){
            batch.draw(texture,x,y,width,height);
        }
    }

    public String status(NationalMapViewport viewport){
        if(textureRevision==viewport.revision()&&viewport.zoom()>=DETAIL_MIN_ZOOM)
            return state+"  |  "+visibleRoads+" ROADS / "+visibleCells+" CELLS";
        return state;
    }

    public boolean ready(NationalMapViewport viewport){return texture!=null&&textureRevision==viewport.revision();}

    private void submit(NationalMapViewport.Snapshot snapshot,int jobGeneration){
        attemptedRevision=snapshot.revision();state="OFFLINE STREET DETAIL LOADING";
        inFlight=worker.submit(()->{
            Result result;
            try{result=raster(snapshot,jobGeneration);}catch(InterruptedException interrupted){return;}
            catch(Throwable error){result=Result.error(snapshot.revision(),jobGeneration,error.getClass().getSimpleName());}
            if(disposed||Thread.currentThread().isInterrupted()){
                if(result.pixmap!=null)result.pixmap.dispose();
            }else completed.offer(result);
        });
    }

    private Result raster(NationalMapViewport.Snapshot view,int jobGeneration)throws Exception{
        int margin=1;
        int minX=Math.max(0,detailPack.cellX(view.minX())-margin);
        int minY=Math.max(0,detailPack.cellY(view.minZ())-margin);
        int maxX=Math.min((int)Math.ceil(detailPack.worldWidth/detailPack.cellMetres)-1,detailPack.cellX(Math.nextDown(view.maxX()))+margin);
        int maxY=Math.min((int)Math.ceil(detailPack.worldHeight/detailPack.cellMetres)-1,detailPack.cellY(Math.nextDown(view.maxZ()))+margin);
        int gridCells=Math.max(0,maxX-minX+1)*Math.max(0,maxY-minY+1);
        if(gridCells>MAX_GRID_CELLS)return Result.error(view.revision(),jobGeneration,"VIEW_TOO_WIDE");

        int estimate=0,cells=0;
        for(int cy=minY;cy<=maxY;cy++)for(int cx=minX;cx<=maxX;cx++){
            QatarDetailPack.DetailEntry entry=detailPack.entry(cx,cy);
            if(entry!=null){estimate+=entry.roadCount();cells++;}
        }
        if(estimate>MAX_ROADS)return Result.error(view.revision(),jobGeneration,"ROAD_BUDGET");
        StrokeBuffer strokes=new StrokeBuffer(Math.max(16,estimate));
        for(int cy=minY;cy<=maxY;cy++)for(int cx=minX;cx<=maxX;cx++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            QatarDetailPack.DetailEntry entry=detailPack.entry(cx,cy);
            if(entry==null)continue;
            detailPack.visitRoads(entry,(sourceId,x1,z1,x2,z2,roadClass,lanes,surface,flags,width)->
                strokes.add(toGlobal(entry.x(),x1,detailPack.cellMetres),toGlobal(entry.y(),z1,detailPack.cellMetres),
                    toGlobal(entry.x(),x2,detailPack.cellMetres),toGlobal(entry.y(),z2,detailPack.cellMetres),roadClass,width));
        }
        Pixmap pixmap=new Pixmap(PIXELS_W,PIXELS_H,Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);pixmap.setColor(0,0,0,0);pixmap.fill();
        pixmap.setBlending(Pixmap.Blending.SourceOver);
        for(int roadClass=11;roadClass>=0;roadClass--){
            drawClass(pixmap,strokes,view,roadClass,true);
            drawClass(pixmap,strokes,view,roadClass,false);
        }
        return new Result(view.revision(),jobGeneration,pixmap,strokes.size,cells,null);
    }

    static float toGlobal(int cell,float local,int cellMetres){return cell*(float)cellMetres+local;}

    private static void drawClass(Pixmap map,StrokeBuffer strokes,NationalMapViewport.Snapshot view,int roadClass,boolean casing){
        setRoadColor(map,roadClass,casing);
        for(int i=0;i<strokes.size;i++){
            if(strokes.roadClass[i]!=roadClass)continue;
            int x1=(int)Math.round((strokes.x1[i]-view.minX())/view.width()*(PIXELS_W-1));
            int y1=(int)Math.round((view.maxZ()-strokes.z1[i])/view.height()*(PIXELS_H-1));
            int x2=(int)Math.round((strokes.x2[i]-view.minX())/view.width()*(PIXELS_W-1));
            int y2=(int)Math.round((view.maxZ()-strokes.z2[i])/view.height()*(PIXELS_H-1));
            float metric=strokes.width[i]/(float)view.width()*PIXELS_W;
            int hierarchy=roadClass<=1?3:roadClass<=4?2:1;
            int diameter=Math.max(hierarchy,Math.min(10,Math.round(metric)));
            if(casing)diameter=Math.min(12,diameter+2);
            thickLine(map,x1,y1,x2,y2,diameter);
        }
    }

    private static void thickLine(Pixmap map,int x1,int y1,int x2,int y2,int diameter){
        int radius=Math.max(0,(diameter-1)/2);
        if(radius==0){map.drawLine(x1,y1,x2,y2);return;}
        for(int oy=-radius;oy<=radius;oy++)for(int ox=-radius;ox<=radius;ox++)
            if(ox*ox+oy*oy<=radius*radius+radius)map.drawLine(x1+ox,y1+oy,x2+ox,y2+oy);
    }

    private static void setRoadColor(Pixmap map,int roadClass,boolean casing){
        if(casing){
            if(roadClass>=5)map.setColor(.20f,.25f,.27f,.78f);
            else map.setColor(.17f,.20f,.22f,.88f);
            return;
        }
        switch(roadClass){
            case 0 -> map.setColor(.98f,.69f,.24f,.98f);
            case 1 -> map.setColor(.98f,.79f,.39f,.98f);
            case 2 -> map.setColor(.96f,.88f,.68f,.98f);
            case 3 -> map.setColor(.88f,.88f,.83f,.96f);
            case 4 -> map.setColor(.72f,.75f,.72f,.92f);
            case 5 -> map.setColor(.84f,.84f,.80f,.90f);
            case 6 -> map.setColor(.76f,.78f,.74f,.86f);
            case 7 -> map.setColor(.65f,.59f,.45f,.80f);
            case 8 -> map.setColor(.38f,.40f,.41f,.92f);
            case 9 -> map.setColor(.38f,.40f,.41f,.94f);
            case 10 -> map.setColor(.94f,.91f,.79f,.96f);
            case 11 -> map.setColor(.84f,.82f,.73f,.92f);
            default -> map.setColor(.70f,.72f,.68f,.76f);
        }
    }

    private void drain(NationalMapViewport viewport){
        Result result,last=null;
        while((result=completed.poll())!=null){
            if(last!=null&&last.pixmap!=null)last.pixmap.dispose();
            last=result;
        }
        if(last==null)return;
        inFlight=null;
        if(last.generation!=generation||requested==null||last.revision!=requested.revision()){
            if(last.pixmap!=null)last.pixmap.dispose();return;
        }
        if(last.error!=null){state="DETAIL UNAVAILABLE: "+last.error;return;}
        if(texture!=null)texture.dispose();
        texture=new Texture(last.pixmap,false);texture.setFilter(Texture.TextureFilter.Linear,Texture.TextureFilter.Linear);
        last.pixmap.dispose();textureRevision=last.revision;visibleRoads=last.roads;visibleCells=last.cells;
        state="OFFLINE STREET DETAIL READY";
        Gdx.app.log("QatarMap","PRECISION_ATLAS_READY zoom="+String.format(java.util.Locale.ROOT,"%.2f",viewport.zoom())+" cells="+visibleCells+" roads="+visibleRoads+" bounded=true");
    }

    private void cancelInFlight(){
        if(inFlight!=null){inFlight.cancel(true);inFlight=null;}
    }

    @Override public void dispose(){
        disposed=true;generation++;cancelInFlight();worker.shutdownNow();
        Result result;while((result=completed.poll())!=null)if(result.pixmap!=null)result.pixmap.dispose();
        if(texture!=null){texture.dispose();texture=null;}
    }

    private record Result(long revision,int generation,Pixmap pixmap,int roads,int cells,String error){
        static Result error(long revision,int generation,String error){return new Result(revision,generation,null,0,0,error);}
    }

    private static final class StrokeBuffer{
        final float[] x1,z1,x2,z2,width;final byte[] roadClass;int size;
        StrokeBuffer(int capacity){x1=new float[capacity];z1=new float[capacity];x2=new float[capacity];z2=new float[capacity];width=new float[capacity];roadClass=new byte[capacity];}
        void add(float ax,float az,float bx,float bz,int type,float metres){
            if(size>=x1.length)return;
            x1[size]=ax;z1[size]=az;x2[size]=bx;z2[size]=bz;width[size]=metres;roadClass[size]=(byte)Math.max(0,Math.min(11,type));size++;
        }
    }
}
