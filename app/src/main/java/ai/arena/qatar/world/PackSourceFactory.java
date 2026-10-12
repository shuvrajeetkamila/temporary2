package ai.arena.qatar.world;

import java.io.IOException;

@FunctionalInterface
public interface PackSourceFactory {
    SeekableDataSource open() throws IOException;
}
