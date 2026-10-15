package ai.arena.qatar.world;

import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Streams 250 m OSM microcells independently of the 2 km safety terrain. */
public final class QatarDetailStreamer implements Disposable {
    private static final int MAX_IN_FLIGHT_CELLS=12;
    public interface Listener {
        void activateDetail(QatarDetailPack.DetailChunk chunk);
        void deactivateDetail(QatarDetailPack.DetailChunk chunk);
        void detailCellFailed(QatarDetailPack.DetailEntry entry,Throwable error);
    }
    private record Ready(long generation,QatarDetailPack.DetailEntry entry,QatarDetailPack.DetailChunk chunk,Throwable error){}
    private static final class Resident { final QatarDetailPack.DetailChunk chunk;long lastRequired;Resident(QatarDetailPack.DetailChunk chunk,long now){this.chunk=chunk;lastRequired=now;} }

    private final QatarDetailPack pack;
    private final Listener listener;
    private final ExecutorService decoder=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Qatar-OSM-microcell-decoder");t.setDaemon(true);t.setPriority(Thread.NORM_PRIORITY-1);return t;});
    private final Map<Long,Resident> residents=new HashMap<>();
    private final Map<Long,Future<?>> inFlight=new HashMap<>();
    private final ConcurrentLinkedQueue<Ready> ready=new ConcurrentLinkedQueue<>();
    private String lastError="";
    private int radiusCap=3,maxResidentCells=64,activationBudget=2;
    private volatile long generation;

    public QatarDetailStreamer(QatarDetailPack pack,Listener listener){this.pack=pack;this.listener=listener;}

    public void setBudgets(int radiusCells,int residentCells,int activationsPerFrame){radiusCap=Math.max(1,Math.min(3,radiusCells));maxResidentCells=Math.max(8,Math.min(64,residentCells));activationBudget=Math.max(1,Math.min(2,activationsPerFrame));}

    public void update(double globalX,double globalZ,float velocityX,float velocityZ,WorldStreamer.FocusMode mode){
        long now=System.nanoTime();drain(now,activationBudget);
        int cx=pack.cellX(globalX),cy=pack.cellY(globalZ);
        int radius=Math.min(radiusCap,switch(mode){case WALK->2;case CAR->3;case BOAT->3;case HELICOPTER->3;case AEROPLANE->2;});
        float speed=(float)Math.hypot(velocityX,velocityZ);int leadX=0,leadY=0;
        if(speed>3){float lead=switch(mode){case WALK->1;case CAR,BOAT->3;case HELICOPTER->4;case AEROPLANE->6;};leadX=Math.round(velocityX/speed*lead);leadY=Math.round(velocityZ/speed*lead);}
        Set<Long> required=new HashSet<>();List<QatarDetailPack.DetailEntry> missing=new ArrayList<>();
        addDisc(required,missing,cx,cy,radius);
        if(leadX!=0||leadY!=0)addDisc(required,missing,cx+leadX,cy+leadY,Math.max(1,radius-1));
        for(long key:required){Resident resident=residents.get(key);if(resident!=null)resident.lastRequired=now;}
        missing.sort(Comparator.comparingDouble(e->square(e.x()-cx,e.y()-cy)));
        for(QatarDetailPack.DetailEntry entry:missing){
            if(inFlight.size()>=MAX_IN_FLIGHT_CELLS)break;long key=key(entry.x(),entry.y());
            if(residents.containsKey(key)||inFlight.containsKey(key))continue;
            long requestGeneration=generation;
            inFlight.put(key,decoder.submit(()->{try{QatarDetailPack.DetailChunk decoded=pack.load(entry);if(requestGeneration==generation)ready.add(new Ready(requestGeneration,entry,decoded,null));}catch(Throwable error){if(requestGeneration==generation)ready.add(new Ready(requestGeneration,entry,null,error));}}));
        }
        long delay=4_000_000_000L;
        Iterator<Map.Entry<Long,Resident>> iterator=residents.entrySet().iterator();
        while(iterator.hasNext()){
            Map.Entry<Long,Resident> item=iterator.next();
            if(!required.contains(item.getKey())&&now-item.getValue().lastRequired>delay){listener.deactivateDetail(item.getValue().chunk);iterator.remove();}
        }
        if(residents.size()>maxResidentCells){
            List<Map.Entry<Long,Resident>> stale=new ArrayList<>();
            for(Map.Entry<Long,Resident> item:residents.entrySet())if(!required.contains(item.getKey()))stale.add(item);
            stale.sort(Comparator.comparingLong(item->item.getValue().lastRequired));
            for(Map.Entry<Long,Resident> item:stale){
                if(residents.size()<=maxResidentCells)break;
                Resident removed=residents.remove(item.getKey());if(removed!=null)listener.deactivateDetail(removed.chunk);
            }
        }
    }

    private void addDisc(Set<Long> required,List<QatarDetailPack.DetailEntry> missing,int cx,int cy,int radius){
        for(int y=cy-radius;y<=cy+radius;y++)for(int x=cx-radius;x<=cx+radius;x++){
            if(square(x-cx,y-cy)>radius*radius+.75)continue;
            QatarDetailPack.DetailEntry entry=pack.entry(x,y);if(entry==null)continue;long key=key(x,y);
            if(required.add(key)&&!residents.containsKey(key)&&!inFlight.containsKey(key))missing.add(entry);
        }
    }
    private void drain(long now,int activationBudget){
        for(int i=0;i<activationBudget;i++){
            Ready item=ready.poll();if(item==null)break;if(item.generation!=generation)continue;long key=key(item.entry.x(),item.entry.y());inFlight.remove(key);
            if(item.error!=null){lastError=item.entry.x()+","+item.entry.y()+": "+item.error.getMessage();listener.detailCellFailed(item.entry,item.error);}
            else if(!residents.containsKey(key)){
                try{listener.activateDetail(item.chunk);residents.put(key,new Resident(item.chunk,now));}
                catch(Throwable error){lastError=item.entry.x()+","+item.entry.y()+": "+error.getMessage();listener.detailCellFailed(item.entry,error);}
            }
        }
    }
    /** Drops old microcells and ignores decoder completions from before a national teleport. */
    public void resetForTeleport(){
        generation++;for(Future<?> future:inFlight.values())future.cancel(true);inFlight.clear();ready.clear();
        for(Resident resident:new ArrayList<>(residents.values()))listener.deactivateDetail(resident.chunk);
        residents.clear();lastError="";
    }
    public int residentCount(){return residents.size();}
    public int pendingCount(){return inFlight.size()+ready.size();}
    public String lastError(){return lastError;}
    @Override public void dispose(){for(Future<?> f:inFlight.values())f.cancel(true);decoder.shutdownNow();for(Resident r:residents.values())listener.deactivateDetail(r.chunk);residents.clear();inFlight.clear();ready.clear();}
    private static double square(double x,double y){return x*x+y*y;}
    private static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
}
