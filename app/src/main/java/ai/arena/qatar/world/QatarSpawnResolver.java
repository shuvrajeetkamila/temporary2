package ai.arena.qatar.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Selects an OSM road/footway point near a hub that is clear of source building collision. */
public final class QatarSpawnResolver {
    private static final int SEARCH_RADIUS_CELLS=2;
    private static final double MINIMUM_BUILDING_CLEARANCE_METRES=3.0;
    private static final double PREFERRED_CAMERA_CLEARANCE_METRES=12.0;
    private QatarSpawnResolver(){}

    public static Spawn resolve(QatarDetailPack pack,QatarPack national,double fallbackX,double fallbackZ)throws IOException{
        int cx=pack.cellX(fallbackX),cy=pack.cellY(fallbackZ);
        Map<Long,QatarPack.WorldChunk> terrainChunks=new HashMap<>();
        List<LocatedChunk> chunks=new ArrayList<>();
        List<LocatedBuilding> buildings=new ArrayList<>();
        for(int y=cy-SEARCH_RADIUS_CELLS;y<=cy+SEARCH_RADIUS_CELLS;y++)for(int x=cx-SEARCH_RADIUS_CELLS;x<=cx+SEARCH_RADIUS_CELLS;x++){
            QatarDetailPack.DetailEntry entry=pack.entry(x,y);if(entry==null)continue;
            QatarDetailPack.DetailChunk chunk=pack.load(entry);double originX=x*(double)pack.cellMetres,originZ=y*(double)pack.cellMetres;
            chunks.add(new LocatedChunk(originX,originZ,chunk));
            for(QatarPack.Building building:chunk.buildings())buildings.add(new LocatedBuilding(originX,originZ,building));
        }
        Spawn best=null;double bestScore=Double.POSITIVE_INFINITY;
        for(LocatedChunk located:chunks)for(QatarPack.Road road:located.chunk.roads()){
            if(road.roadClass()>=9)continue; // Never spawn a pedestrian on rail, runway, taxiway, or apron geometry.
            double x1=located.originX+road.x1(),z1=located.originZ+road.z1();
            double x2=located.originX+road.x2(),z2=located.originZ+road.z2();
            double dx=x2-x1,dz=z2-z1,lengthSquared=dx*dx+dz*dz;if(lengthSquared<1.0)continue;
            double t=((fallbackX-x1)*dx+(fallbackZ-z1)*dz)/lengthSquared;t=Math.max(.15,Math.min(.85,t));
            double centreX=x1+t*dx,centreZ=z1+t*dz,length=Math.sqrt(lengthSquared),perpendicularX=-dz/length,perpendicularZ=dx/length;
            double roadside=road.roadClass()==8?0:road.width()/2+1.4;
            for(int side:roadside==0?new int[]{0}:new int[]{-1,1,0}){
                double candidateX=centreX+perpendicularX*roadside*side,candidateZ=centreZ+perpendicularZ*roadside*side;
                if(!hasRetainedTerrainClearance(candidateX,candidateZ,national,terrainChunks))continue;
                if(blocked(candidateX,candidateZ,buildings,MINIMUM_BUILDING_CLEARANCE_METRES))continue;
                double centrePenalty=side==0&&roadside>0?14:0;
                double cameraPenalty=blocked(candidateX,candidateZ,buildings,PREFERRED_CAMERA_CLEARANCE_METRES)?35:0;
                double score=Math.hypot(candidateX-fallbackX,candidateZ-fallbackZ)+classPenalty(road.roadClass())+centrePenalty+cameraPenalty;
                if(score<bestScore){bestScore=score;best=new Spawn(candidateX,candidateZ,road.roadClass(),side==0?"nearest clear OSM footway/road":"nearest clear OSM roadside");}
            }
        }
        return best!=null?best:new Spawn(fallbackX,fallbackZ,-1,"public hub fallback; no clear OSM road candidate");
    }

    private static boolean hasRetainedTerrainClearance(double x,double z,QatarPack pack,Map<Long,QatarPack.WorldChunk> cache)throws IOException{
        if(!isPackagedLand(x,z,pack,cache))return false;
        for(double radius:new double[]{30,75})for(int step=0;step<8;step++){double angle=step*Math.PI/4;if(!isPackagedLand(x+Math.cos(angle)*radius,z+Math.sin(angle)*radius,pack,cache))return false;}
        return true;
    }

    private static boolean isPackagedLand(double x,double z,QatarPack pack,Map<Long,QatarPack.WorldChunk> cache)throws IOException{
        int chunkX=pack.chunkX(x),chunkY=pack.chunkY(z);QatarPack.ChunkEntry entry=pack.entry(chunkX,chunkY);if(entry==null||!entry.hasLand())return false;
        long key=((long)chunkX<<32)^(chunkY&0xffffffffL);QatarPack.WorldChunk chunk=cache.get(key);
        if(chunk==null){chunk=pack.load(entry);cache.put(key,chunk);}
        double localX=x-chunkX*(double)pack.chunkMetres,localZ=z-chunkY*(double)pack.chunkMetres;
        int maskX=Math.max(0,Math.min(15,(int)Math.floor(localX/pack.chunkMetres*16)));
        int maskZ=Math.max(0,Math.min(15,(int)Math.floor(localZ/pack.chunkMetres*16)));
        return (chunk.landRows()[maskZ]&(1<<maskX))!=0;
    }

    private static boolean blocked(double x,double z,List<LocatedBuilding> buildings,double clearance){
        for(LocatedBuilding located:buildings){
            QatarPack.Building building=located.building;double dx=x-(located.originX+building.x()),dz=z-(located.originZ+building.z());
            double cosine=Math.cos(building.yaw()),sine=Math.sin(building.yaw());
            double along=dx*cosine+dz*sine,across=-dx*sine+dz*cosine;
            if(Math.abs(along)<=building.width()/2+clearance&&Math.abs(across)<=building.depth()/2+clearance)return true;
        }
        return false;
    }

    private static double classPenalty(int roadClass){
        // Prefer an ordinary visible street; pedestrian paths remain a safe fallback in dense souqs.
        return switch(roadClass){case 5->0;case 6->3;case 4->10;case 3->20;case 7->25;case 2->35;case 1->60;case 0->80;case 8->100;default->120;};
    }

    private record LocatedChunk(double originX,double originZ,QatarDetailPack.DetailChunk chunk){}
    private record LocatedBuilding(double originX,double originZ,QatarPack.Building building){}
    public record Spawn(double x,double z,int roadClass,String basis){}
}
