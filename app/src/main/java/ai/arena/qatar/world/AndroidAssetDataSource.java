package ai.arena.qatar.world;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Positional reader over an uncompressed APK asset. Gradle's noCompress rule for
 * .qpak is mandatory; openFd deliberately fails if packaging regresses.
 */
public final class AndroidAssetDataSource implements SeekableDataSource {
    private final AssetFileDescriptor descriptor;
    private final FileInputStream stream;
    private final FileChannel channel;
    private final long startOffset;
    private final long length;

    public AndroidAssetDataSource(AssetManager assets, String assetPath) throws IOException {
        descriptor = assets.openFd(assetPath);
        startOffset = descriptor.getStartOffset();
        length = descriptor.getLength();
        stream = new FileInputStream(descriptor.getFileDescriptor());
        channel = stream.getChannel();
        if (length <= 64) {
            close();
            throw new IOException("Qatar pack is truncated: " + length);
        }
    }

    @Override public long size() { return length; }

    @Override
    public byte[] readFully(long offset, int requested) throws IOException {
        if (offset < 0 || requested < 0 || offset + requested > length) {
            throw new EOFException("Read outside qpak: offset=" + offset + " length=" + requested + " file=" + length);
        }
        byte[] result = new byte[requested];
        ByteBuffer target = ByteBuffer.wrap(result);
        long absolute = startOffset + offset;
        while (target.hasRemaining()) {
            int count = channel.read(target, absolute + target.position());
            if (count < 0) throw new EOFException("Unexpected qpak EOF");
            if (count == 0) Thread.yield();
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        try { channel.close(); } finally {
            try { stream.close(); } finally { descriptor.close(); }
        }
    }
}
