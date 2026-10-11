package ai.arena.nauru.graphics;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

/** Generates a seamless multi-frequency material-detail texture without bundled imagery. */
public final class ProceduralTexture {
    private ProceduralTexture() {}

    public static int createMaterialDetail() {
        final int size = 256;
        float[][] coarse = periodicNoise(16, 0x4e415552L);
        float[][] medium = periodicNoise(64, 0x49534c45L);
        Random random = new Random(0x434f52414cL);
        ByteBuffer pixels = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder());
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float c = samplePeriodic(coarse, x / (float) size * 16f, y / (float) size * 16f);
                float m = samplePeriodic(medium, x / (float) size * 64f, y / (float) size * 64f);
                float fine = random.nextFloat();
                float grain = clamp(c * .56f + m * .30f + fine * .14f);
                float aggregate = clamp(m * .55f + fine * .45f);
                float organic = clamp(c * .72f + (float) Math.sin((x + y * .37f) * .19f) * .09f + .12f);
                pixels.put((byte) (grain * 255));
                pixels.put((byte) (aggregate * 255));
                pixels.put((byte) (organic * 255));
                pixels.put((byte) 255);
            }
        }
        pixels.position(0);
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, size, size,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return ids[0];
    }

    private static float[][] periodicNoise(int size, long seed) {
        float[][] values = new float[size][size];
        Random random = new Random(seed);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) values[y][x] = random.nextFloat();
        return values;
    }

    private static float samplePeriodic(float[][] values, float x, float y) {
        int size = values.length;
        int x0 = floorMod((int) Math.floor(x), size), y0 = floorMod((int) Math.floor(y), size);
        int x1 = (x0 + 1) % size, y1 = (y0 + 1) % size;
        float fx = x - (float) Math.floor(x), fy = y - (float) Math.floor(y);
        fx = fx * fx * (3f - 2f * fx); fy = fy * fy * (3f - 2f * fy);
        float a = values[y0][x0] * (1 - fx) + values[y0][x1] * fx;
        float b = values[y1][x0] * (1 - fx) + values[y1][x1] * fx;
        return a * (1 - fy) + b * fy;
    }

    private static int floorMod(int value, int divisor) {
        int result = value % divisor;
        return result < 0 ? result + divisor : result;
    }

    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
}
