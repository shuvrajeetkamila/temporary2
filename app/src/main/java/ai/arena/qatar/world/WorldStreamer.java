package ai.arena.qatar.world;

import com.badlogic.gdx.utils.Disposable;

import java.io.IOException;
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
import java.util.concurrent.atomic.AtomicInteger;

/** Bounded one-worker national chunk streamer; GL/Bullet activation stays on render thread. */
public final class WorldStreamer implements Disposable {
    private static final int MAX_RESIDENT_CHUNKS=64;
    public enum FocusMode { WALK, CAR, BOAT, HELICOPTER, AEROPLANE }

    public interface Listener {
        void activate(QatarPack.WorldChunk chunk);
        void deactivate(QatarPack.WorldChunk chunk);
        void requiredChunkFailed(QatarPack.ChunkEntry entry, Throwable error);
    }

    private record Ready(QatarPack.ChunkEntry entry, QatarPack.WorldChunk chunk, Throwable error) { }
    private static final class Resident {
        final QatarPack.WorldChunk chunk;
        long lastRequiredNanos;
        Resident(QatarPack.WorldChunk chunk, long now) { this.chunk = chunk; this.lastRequiredNanos = now; }
    }

    private final QatarPack pack;
    private final Listener listener;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Qatar-qpak-decoder");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final Map<Long, Resident> residents = new HashMap<>();
    private final Map<Long, Future<?>> inFlight = new HashMap<>();
    private final ConcurrentLinkedQueue<Ready> ready = new ConcurrentLinkedQueue<>();
    private final AtomicInteger decodedCount = new AtomicInteger();
    private volatile String lastError = "";
    private int requestedCount;

    public WorldStreamer(QatarPack pack, Listener listener) {
        this.pack = pack;
        this.listener = listener;
    }

    /** Called on the render/simulation owner thread. */
    public void update(double globalX, double globalZ, float velocityX, float velocityZ, FocusMode mode) {
        long now = System.nanoTime();
        drainReady(now, 3);
        int cx = pack.chunkX(globalX), cy = pack.chunkY(globalZ);
        int radius = switch (mode) {
            case WALK -> 1;
            case CAR, BOAT -> 2;
            case HELICOPTER -> 2;
            case AEROPLANE -> 3;
        };
        float speed = (float)Math.hypot(velocityX, velocityZ);
        int leadX = 0, leadY = 0;
        if (speed > 3f) {
            float lead = switch (mode) {
                case WALK -> 0.25f;
                case CAR, BOAT -> 1.25f;
                case HELICOPTER -> 1.75f;
                case AEROPLANE -> 2.5f;
            };
            leadX = Math.round(velocityX / speed * lead);
            leadY = Math.round(velocityZ / speed * lead);
        }
        Set<Long> required = new HashSet<>();
        List<QatarPack.ChunkEntry> missing = new ArrayList<>();
        addDisc(required, missing, cx, cy, radius);
        if (leadX != 0 || leadY != 0) addDisc(required, missing, cx + leadX, cy + leadY, Math.max(1, radius - 1));
        for (long key : required) {
            Resident value = residents.get(key);
            if (value != null) value.lastRequiredNanos = now;
        }
        missing.sort(Comparator.comparingDouble(e -> square(e.x() - cx, e.y() - cy)));
        int maxInFlight = mode == FocusMode.AEROPLANE ? 18 : 10;
        for (QatarPack.ChunkEntry entry : missing) {
            if (inFlight.size() >= maxInFlight) break;
            long key = key(entry.x(), entry.y());
            if (residents.containsKey(key) || inFlight.containsKey(key)) continue;
            requestedCount++;
            Future<?> future = io.submit(() -> {
                try {
                    ready.add(new Ready(entry, pack.load(entry), null));
                } catch (IOException | RuntimeException error) {
                    ready.add(new Ready(entry, null, error));
                }
            });
            inFlight.put(key, future);
        }
        long evictionDelay = mode == FocusMode.WALK ? 4_000_000_000L : 6_000_000_000L;
        Iterator<Map.Entry<Long, Resident>> iterator = residents.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Resident> item = iterator.next();
            if (!required.contains(item.getKey()) && now - item.getValue().lastRequiredNanos > evictionDelay) {
                listener.deactivate(item.getValue().chunk);
                iterator.remove();
            }
        }
        if(residents.size()>MAX_RESIDENT_CHUNKS){
            List<Map.Entry<Long,Resident>> stale=new ArrayList<>();
            for(Map.Entry<Long,Resident> item:residents.entrySet())if(!required.contains(item.getKey()))stale.add(item);
            stale.sort(Comparator.comparingLong(item->item.getValue().lastRequiredNanos));
            for(Map.Entry<Long,Resident> item:stale){
                if(residents.size()<=MAX_RESIDENT_CHUNKS)break;
                Resident removed=residents.remove(item.getKey());if(removed!=null)listener.deactivate(removed.chunk);
            }
        }
    }

    private void addDisc(Set<Long> required, List<QatarPack.ChunkEntry> missing, int cx, int cy, int radius) {
        for (int y = cy-radius; y <= cy+radius; y++) {
            for (int x = cx-radius; x <= cx+radius; x++) {
                if (square(x-cx,y-cy) > radius*radius + 0.75) continue;
                QatarPack.ChunkEntry entry = pack.entry(x,y);
                if (entry == null) continue;
                long key = key(x,y);
                if (required.add(key) && !residents.containsKey(key) && !inFlight.containsKey(key)) missing.add(entry);
            }
        }
    }

    private void drainReady(long now, int uploadBudget) {
        for (int i = 0; i < uploadBudget; i++) {
            Ready item = ready.poll();
            if (item == null) break;
            long key = key(item.entry.x(), item.entry.y());
            inFlight.remove(key);
            if (item.error != null) {
                lastError = item.entry.x() + "," + item.entry.y() + ": " + item.error.getMessage();
                listener.requiredChunkFailed(item.entry, item.error);
            } else if (!residents.containsKey(key)) {
                try {
                    listener.activate(item.chunk);
                    residents.put(key,new Resident(item.chunk,now));
                    decodedCount.incrementAndGet();
                } catch (Throwable error) {
                    lastError=item.entry.x()+","+item.entry.y()+": "+error.getMessage();
                    listener.requiredChunkFailed(item.entry,error);
                }
            }
        }
    }

    public int residentCount() { return residents.size(); }
    public int pendingCount() { return inFlight.size() + ready.size(); }
    public int requestedCount() { return requestedCount; }
    public int decodedCount() { return decodedCount.get(); }
    public String lastError() { return lastError; }

    @Override
    public void dispose() {
        for (Future<?> future : inFlight.values()) future.cancel(true);
        io.shutdownNow();
        for (Resident resident : residents.values()) listener.deactivate(resident.chunk);
        residents.clear();
        inFlight.clear();
        ready.clear();
    }

    private static long key(int x, int y) { return ((long)x << 32) ^ (y & 0xffffffffL); }
    private static double square(double x, double y) { return x*x+y*y; }
}
