package ai.arena.qatar.world;

import ai.arena.qatar.physics.PhysicsWorld;
import org.junit.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QatarTeleportResolverTest {
    private static Path packPath(){Path direct=Path.of("src/main/assets/world/qatar-national.qpak");return Files.exists(direct)?direct:Path.of("app/src/main/assets/world/qatar-national.qpak");}

    @Test public void allMovementFamiliesReceiveModeSafeNationalDestinations()throws Exception{
        try(QatarPack pack=new QatarPack(new FileSource(packPath()))){
            QatarTeleportResolver.Destination ground=QatarTeleportResolver.resolve(pack,null,PhysicsWorld.Mode.WALK,1,1,.2f);
            assertFalse(ground.water());assertTrue(ground.x()>=0&&ground.x()<pack.worldWidth);assertTrue(Float.isFinite(ground.surfaceHeight()));

            QatarPack.Hub education=pack.hub("Education City");
            QatarTeleportResolver.Destination boat=QatarTeleportResolver.resolve(pack,null,PhysicsWorld.Mode.BOAT,education.x(),education.z(),.2f);
            assertTrue(boat.water());assertTrue(boat.adjustmentMetres()>0);assertEquals(.2f,boat.surfaceHeight(),.001f);

            QatarTeleportResolver.Destination aircraft=QatarTeleportResolver.resolve(pack,null,PhysicsWorld.Mode.AEROPLANE,education.x(),education.z(),.2f);
            assertEquals(education.x(),aircraft.x(),.01);assertEquals(education.z(),aircraft.z(),.01);assertFalse(aircraft.water());
        }
    }

    private static final class FileSource implements SeekableDataSource {
        private final RandomAccessFile file;
        FileSource(Path path)throws IOException{file=new RandomAccessFile(path.toFile(),"r");}
        @Override public long size(){try{return file.length();}catch(IOException error){throw new RuntimeException(error);}}
        @Override public synchronized byte[] readFully(long offset,int length)throws IOException{if(offset<0||length<0||offset+length>file.length())throw new EOFException();byte[] data=new byte[length];file.seek(offset);file.readFully(data);return data;}
        @Override public void close()throws IOException{file.close();}
    }
}
