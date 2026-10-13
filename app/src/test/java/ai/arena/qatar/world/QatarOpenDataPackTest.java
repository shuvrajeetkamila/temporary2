package ai.arena.qatar.world;

import org.junit.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;

import static org.junit.Assert.*;

public class QatarOpenDataPackTest {
    private static Path locate(String relative){Path direct=Path.of("src/main/assets/world",relative);return Files.exists(direct)?direct:Path.of("app/src/main/assets/world",relative);}

    @Test public void completeOsmDerivativeIsIndexedInMicrocells()throws Exception{
        try(QatarDetailPack detail=new QatarDetailPack(new FileSource(locate("qatar-osm-current.qod")))){
            assertEquals(32639,detail.epsg);assertEquals(250,detail.cellMetres);
            assertTrue(detail.cellCount>10_000);assertEquals(detail.cellCount,detail.entries().size());
            assertTrue(detail.roadWayCount>150_000);assertTrue(detail.roadSegmentCount>1_000_000);
            assertTrue(detail.simpleBuildingCount+detail.relationBuildingCount>150_000);
            long indexedRoads=detail.entries().values().stream().mapToLong(QatarDetailPack.DetailEntry::roadCount).sum();
            long indexedBuildings=detail.entries().values().stream().mapToLong(QatarDetailPack.DetailEntry::buildingCount).sum();
            assertEquals(detail.roadSegmentCount,indexedRoads);assertEquals(detail.simpleBuildingCount+detail.relationBuildingCount,indexedBuildings);
        }
    }

    @Test public void denseAndRepresentativeCellsDecodeWithSourceFootprints()throws Exception{
        try(QatarDetailPack detail=new QatarDetailPack(new FileSource(locate("qatar-osm-current.qod")))){
            QatarDetailPack.DetailEntry densest=detail.entries().values().stream().max(Comparator.comparingInt(e->e.roadCount()+e.buildingCount())).orElseThrow();
            QatarDetailPack.DetailChunk chunk=detail.load(densest);
            assertEquals(densest.roadCount(),chunk.roads().length);assertEquals(densest.buildingCount(),chunk.buildings().length);
            for(QatarPack.Building building:chunk.buildings()){assertTrue(building.sourceId()!=0);assertTrue(building.hasSourceFootprint());}
            int checked=0;
            for(QatarDetailPack.DetailEntry entry:detail.entries().values())if((checked++%997)==0){QatarDetailPack.DetailChunk sample=detail.load(entry);assertNotNull(sample.roads());assertNotNull(sample.buildings());}
        }
    }

    @Test public void nationalAndDetailCoordinateContractsMatch()throws Exception{
        try(QatarPack national=new QatarPack(new FileSource(locate("qatar-national.qpak")));
            QatarDetailPack detail=new QatarDetailPack(new FileSource(locate("qatar-osm-current.qod")))){
            detail.verifyCompatible(national);QatarPack.Hub souq=national.hub("Souq Waqif");
            QatarDetailPack.DetailEntry entry=detail.entry(detail.cellX(souq.x()),detail.cellY(souq.z()));assertNotNull(entry);
            QatarDetailPack.DetailChunk chunk=detail.load(entry);assertTrue(chunk.roads().length>0);assertTrue(chunk.buildings().length>0);
            QatarSpawnResolver.Spawn spawn=QatarSpawnResolver.resolve(detail,souq.x(),souq.z());
            assertTrue(spawn.roadClass()>=0&&spawn.roadClass()<9);
            assertTrue(Math.hypot(spawn.x()-souq.x(),spawn.z()-souq.z())<100);
            assertTrue("Souq hub is inside a mapped footprint; resolver must move it",Math.hypot(spawn.x()-souq.x(),spawn.z()-souq.z())>3);
        }
    }

    private static final class FileSource implements SeekableDataSource{
        private final RandomAccessFile file;FileSource(Path path)throws IOException{file=new RandomAccessFile(path.toFile(),"r");}
        @Override public long size(){try{return file.length();}catch(IOException e){throw new RuntimeException(e);}}
        @Override public synchronized byte[] readFully(long offset,int length)throws IOException{if(offset<0||length<0||offset+length>file.length())throw new EOFException();byte[] data=new byte[length];file.seek(offset);file.readFully(data);return data;}
        @Override public void close()throws IOException{file.close();}
    }
}
