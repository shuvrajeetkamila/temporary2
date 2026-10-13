package ai.arena.qatar.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Selects an OSM road/footway point near a hub that is clear of source building collision. */
public final class QatarSpawnResolver {
    private static final int SEARCH_RADIUS_CELLS=2;
    private static final double BUILDING_CLEARANCE_METRES=3.0;
    private QatarSpawnResolver(){}

    public static Spawn resolve(QatarDetailPack pack,double fallbackX,double fallbackZ)throws IOException{
        int cx=pack.cellX(fallbackX),cy=pack.cellY(fallbackZ);
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
            double candidateX=x1+t*dx,candidateZ=z1+t*dz;
            if(blocked(candidateX,candidateZ,buildings))continue;
            double score=Math.hypot(candidateX-fallbackX,candidateZ-fallbackZ)+classPenalty(road.roadClass());
            if(score<bestScore){bestScore=score;best=new Spawn(candidateX,candidateZ,road.roadClass(),"nearest clear OSM road/footway");}
        }
        return best!=null?best:new Spawn(fallbackX,fallbackZ,-1,"public hub fallback; no clear OSM road candidate");
    }

    private static boolean blocked(double x,double z,List<LocatedBuilding> buildings){
        for(LocatedBuilding located:buildings){
            QatarPack.Building building=located.building;double dx=x-(located.originX+building.x()),dz=z-(located.originZ+building.z());
            double cosine=Math.cos(building.yaw()),sine=Math.sin(building.yaw());
            double along=dx*cosine+dz*sine,across=-dx*sine+dz*cosine;
            if(Math.abs(along)<=building.width()/2+BUILDING_CLEARANCE_METRES&&Math.abs(across)<=building.depth()/2+BUILDING_CLEARANCE_METRES)return true;
        }
        return false;
    }

    private static double classPenalty(int roadClass){
        return switch(roadClass){case 8->0;case 5->5;case 6->8;case 7->12;case 4->15;case 3->25;case 2->40;case 1->60;case 0->80;default->100;};
    }

    private record LocatedChunk(double originX,double originZ,QatarDetailPack.DetailChunk chunk){}
    private record LocatedBuilding(double originX,double originZ,QatarPack.Building building){}
    public record Spawn(double x,double z,int roadClass,String basis){}
}
