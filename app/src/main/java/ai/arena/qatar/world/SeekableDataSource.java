package ai.arena.qatar.world;

import java.io.Closeable;
import java.io.IOException;

/** Random-access source used so the full national pack never enters heap at once. */
public interface SeekableDataSource extends Closeable {
    long size();
    byte[] readFully(long offset, int length) throws IOException;
}
