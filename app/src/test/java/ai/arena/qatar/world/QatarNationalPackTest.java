package ai.arena.qatar.world;

import org.junit.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class QatarNationalPackTest {
    private static Path packPath(){
        Path direct=Path.of("src/main/assets/world/qatar-national.qpak");
        return Files.exists(direct)?direct:Path.of("app/src/main/assets/world/qatar-national.qpak");
    }

    @Test public void completeCountryIndexAndAllMunicipalitiesArePackaged() throws Exception {
        try(QatarPack pack=new QatarPack(new FileSource(packPath()))){
            assertEquals(32639,pack.epsg);
            assertEquals(2000,pack.chunkMetres);
            assertEquals(5665,pack.chunkCount);
            assertEquals(8,pack.municipalities.size());
            assertEquals(7,pack.nationalPolygons.size());
            assertTrue(pack.worldWidth>100_000);
            assertTrue(pack.worldHeight>190_000);
            Set<Integer> represented=new HashSet<>();
            int land=0,coast=0,water=0;
            for(QatarPack.ChunkEntry entry:pack.entries().values()){
                if(entry.hasLand()){land++;represented.add(entry.municipality());if(entry.isCoast())coast++;}else water++;
            }
            assertEquals(8,represented.size());
            assertTrue(land>3000);
            assertTrue(coast>400);
            assertTrue(water>2000);
        }
    }

    @Test public void everyRegionalHubHasAChecksummedLocalChunk() throws Exception {
        try(QatarPack pack=new QatarPack(new FileSource(packPath()))){
            assertEquals(25,pack.hubs.size());assertEquals("2026-10-14-srtm-osm-v0.3",pack.worldEpoch);
            int minimum=Integer.MAX_VALUE,maximum=Integer.MIN_VALUE;
            for(QatarPack.Hub hub:pack.hubs){
                QatarPack.ChunkEntry entry=pack.entry(pack.chunkX(hub.x()),pack.chunkY(hub.z()));
                assertNotNull("Missing chunk at "+hub.english(),entry);
                QatarPack.WorldChunk chunk=pack.load(entry);
                assertEquals(entry.x(),chunk.entry().x());
                assertEquals(81,chunk.terrainCm().length);
                assertEquals(16,chunk.landRows().length);
                for(short elevation:chunk.terrainCm()){minimum=Math.min(minimum,elevation);maximum=Math.max(maximum,elevation);}
            }
            assertTrue("Measured terrain must vary across Qatar",maximum-minimum>1_000);
        }
    }

    @Test public void installedPackMatchesDeterministicChecksum() throws Exception {
        byte[] bytes=Files.readAllBytes(packPath());
        StringBuilder value=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))value.append(String.format("%02x",b));
        Path checksums=Files.exists(Path.of("data/qatar/SHA256SUMS"))?Path.of("data/qatar/SHA256SUMS"):Path.of("../data/qatar/SHA256SUMS");
        String declared=Files.readAllLines(checksums).stream().filter(line->line.endsWith("app/src/main/assets/world/qatar-national.qpak")).findFirst().orElseThrow().split("\\s+")[0];
        assertEquals(declared,value.toString());
    }

    private static final class FileSource implements SeekableDataSource {
        private final RandomAccessFile file;
        FileSource(Path path)throws IOException{file=new RandomAccessFile(path.toFile(),"r");}
        @Override public long size(){try{return file.length();}catch(IOException e){throw new RuntimeException(e);}}
        @Override public synchronized byte[] readFully(long offset,int length)throws IOException{
            if(offset<0||length<0||offset+length>file.length())throw new EOFException();
            byte[] data=new byte[length];file.seek(offset);file.readFully(data);return data;
        }
        @Override public void close()throws IOException{file.close();}
    }
}
