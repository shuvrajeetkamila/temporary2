package ai.arena.qatar.world;

import ai.arena.qatar.physics.PhysicsWorld;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves a national-map touch to a mode-appropriate, collision-supported destination. */
public final class QatarTeleportResolver {
    private static final double CELL_EDGE_MARGIN_METRES=2.0;
    private QatarTeleportResolver(){}

    public static Destination resolve(QatarPack pack,QatarDetailPack detailPack,PhysicsWorld.Mode mode,
                                      double requestedX,double requestedZ,float seaLevel)throws IOException{
        double x=clamp(requestedX,0,Math.nextDown(pack.worldWidth));
        double z=clamp(requestedZ,0,Math.nextDown(pack.worldHeight));
        Map<Long,QatarPack.WorldChunk> cache=new HashMap<>();
        Sample destination;
        String basis;
        if(mode==PhysicsWorld.Mode.HELICOPTER||mode==PhysicsWorld.Mode.AEROPLANE){
            destination=sampleExact(pack,x,z,seaLevel,cache);
            basis=destination.land?"exact map point above terrain":"exact map point above Gulf water";
        }else if(mode==PhysicsWorld.Mode.BOAT){
            Sample exact=sampleExact(pack,x,z,seaLevel,cache);
            destination=exact.land?nearestSurface(pack,x,z,false,seaLevel,cache):exact;
            basis=exact.land?"nearest navigable Gulf water":"exact navigable Gulf point";
        }else{
            Sample exact=sampleExact(pack,x,z,seaLevel,cache);
            destination=exact.land?exact:nearestSurface(pack,x,z,true,seaLevel,cache);
            basis=exact.land?"selected retained land":"nearest retained Qatar land";
            if(detailPack!=null){
                QatarSpawnResolver.Spawn road=QatarSpawnResolver.resolve(detailPack,pack,destination.x,destination.z);
                Sample roadSample=sampleExact(pack,road.x(),road.z(),seaLevel,cache);
                if(roadSample.land){destination=roadSample;basis=road.basis();}
            }
        }
        double adjustment=Math.hypot(destination.x-x,destination.z-z);
        return new Destination(x,z,destination.x,destination.z,destination.surface,!destination.land,adjustment,basis);
    }

    private static Sample sampleExact(QatarPack pack,double x,double z,float seaLevel,Map<Long,QatarPack.WorldChunk> cache)throws IOException{
        int chunkX=pack.chunkX(x),chunkZ=pack.chunkY(z);QatarPack.ChunkEntry entry=pack.entry(chunkX,chunkZ);
        if(entry==null)return nearestSurface(pack,x,z,false,seaLevel,cache);
        if(!entry.hasLand())return new Sample(x,z,seaLevel,false,null);
        QatarPack.WorldChunk chunk=load(pack,entry,cache);
        float sub=pack.chunkMetres/16f;
        int column=Math.max(0,Math.min(15,(int)Math.floor((x-chunkX*(double)pack.chunkMetres)/sub)));
        int row=Math.max(0,Math.min(15,(int)Math.floor((z-chunkZ*(double)pack.chunkMetres)/sub)));
        boolean land=(chunk.landRows()[row]&(1<<column))!=0;
        float height=land?pack.terrainHeight(chunk,(float)(x-chunkX*(double)pack.chunkMetres),(float)(z-chunkZ*(double)pack.chunkMetres)):seaLevel;
        return new Sample(x,z,height,land,chunk);
    }

    private static Sample nearestSurface(QatarPack pack,double requestedX,double requestedZ,boolean land,float seaLevel,
                                         Map<Long,QatarPack.WorldChunk> cache)throws IOException{
        List<ScoredEntry> ordered=new ArrayList<>(pack.entries().size());
        for(QatarPack.ChunkEntry entry:pack.entries().values()){
            if(land&&!entry.hasLand())continue;
            if(!land&&entry.hasLand()&&!entry.isCoast())continue;
            double minX=entry.x()*(double)pack.chunkMetres,minZ=entry.y()*(double)pack.chunkMetres;
            ordered.add(new ScoredEntry(entry,distanceToRectangleSquared(requestedX,requestedZ,minX,minZ,minX+pack.chunkMetres,minZ+pack.chunkMetres)));
        }
        ordered.sort(Comparator.comparingDouble(ScoredEntry::minimumDistanceSquared));
        Candidate best=null;
        float sub=pack.chunkMetres/16f;
        for(ScoredEntry scored:ordered){
            if(best!=null&&scored.minimumDistanceSquared>best.distanceSquared+1e-6)break;
            QatarPack.ChunkEntry entry=scored.entry;
            double chunkX=entry.x()*(double)pack.chunkMetres,chunkZ=entry.y()*(double)pack.chunkMetres;
            if(!land&&!entry.hasLand()){
                double candidateX=clampInterior(requestedX,chunkX,chunkX+pack.chunkMetres);
                double candidateZ=clampInterior(requestedZ,chunkZ,chunkZ+pack.chunkMetres);
                best=closer(best,new Candidate(candidateX,candidateZ,square(candidateX-requestedX,candidateZ-requestedZ),entry,null));
                continue;
            }
            QatarPack.WorldChunk chunk=load(pack,entry,cache);
            for(int row=0;row<16;row++)for(int column=0;column<16;column++){
                boolean cellLand=(chunk.landRows()[row]&(1<<column))!=0;
                if(cellLand!=land)continue;
                double minX=chunkX+column*sub,minZ=chunkZ+row*sub;
                double candidateX=clampInterior(requestedX,minX,minX+sub);
                double candidateZ=clampInterior(requestedZ,minZ,minZ+sub);
                best=closer(best,new Candidate(candidateX,candidateZ,square(candidateX-requestedX,candidateZ-requestedZ),entry,chunk));
            }
        }
        if(best==null)throw new IOException(land?"No retained Qatar land destination exists":"No navigable Qatar water destination exists");
        float height;
        if(land){
            QatarPack.WorldChunk chunk=best.chunk!=null?best.chunk:load(pack,best.entry,cache);
            height=pack.terrainHeight(chunk,(float)(best.x-best.entry.x()*(double)pack.chunkMetres),(float)(best.z-best.entry.y()*(double)pack.chunkMetres));
        }else height=seaLevel;
        return new Sample(best.x,best.z,height,land,best.chunk);
    }

    private static Candidate closer(Candidate previous,Candidate candidate){
        return previous==null||candidate.distanceSquared<previous.distanceSquared?candidate:previous;
    }

    private static QatarPack.WorldChunk load(QatarPack pack,QatarPack.ChunkEntry entry,Map<Long,QatarPack.WorldChunk> cache)throws IOException{
        long key=((long)entry.x()<<32)^(entry.y()&0xffffffffL);
        QatarPack.WorldChunk chunk=cache.get(key);
        if(chunk==null){chunk=pack.load(entry);cache.put(key,chunk);}
        return chunk;
    }

    private static double clampInterior(double value,double min,double max){
        double margin=Math.min(CELL_EDGE_MARGIN_METRES,(max-min)*.1);
        return clamp(value,min+margin,max-margin);
    }
    private static double distanceToRectangleSquared(double x,double z,double minX,double minZ,double maxX,double maxZ){
        double dx=x<minX?minX-x:x>maxX?x-maxX:0,dz=z<minZ?minZ-z:z>maxZ?z-maxZ:0;
        return dx*dx+dz*dz;
    }
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
    private static double square(double x,double z){return x*x+z*z;}

    private record ScoredEntry(QatarPack.ChunkEntry entry,double minimumDistanceSquared){}
    private record Candidate(double x,double z,double distanceSquared,QatarPack.ChunkEntry entry,QatarPack.WorldChunk chunk){}
    private record Sample(double x,double z,float surface,boolean land,QatarPack.WorldChunk chunk){}

    public record Destination(double requestedX,double requestedZ,double x,double z,float surfaceHeight,
                              boolean water,double adjustmentMetres,String basis){}
}
