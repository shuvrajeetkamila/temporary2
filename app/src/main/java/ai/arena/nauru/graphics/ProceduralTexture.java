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

    /** Grass, asphalt, coral/sand and weathered construction surfaces, all seamless and original. */
    public static int[] createSurfaceSet() {
        int[] textures = new int[4];
        for (int type = 0; type < textures.length; type++) textures[type] = createSurface(type);
        return textures;
    }

    private static int createSurface(int type) {
        final int size = 256;
        float[][] coarse = periodicNoise(16, 0x53455246L + type * 97L);
        float[][] medium = periodicNoise(64, 0x4d41544cL + type * 131L);
        Random random = new Random(0x54455854L + type * 8191L);
        ByteBuffer pixels = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder());
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float c = samplePeriodic(coarse, x / (float) size * 16f, y / (float) size * 16f);
                float m = samplePeriodic(medium, x / (float) size * 64f, y / (float) size * 64f);
                float f = random.nextFloat();
                float r, g, b;
                if (type == 0) { // dense tropical grass with occasional sun-dried blades
                    float blade = (float) Math.pow(Math.max(0, Math.sin(x * .73 + y * .17 + m * 8)), 12);
                    float dry = f > .987f ? .32f : 0f;
                    r = .105f + c * .12f + blade * .09f + dry;
                    g = .255f + c * .25f + m * .08f + blade * .24f + dry * .58f;
                    b = .075f + c * .095f + blade * .055f + dry * .12f;
                } else if (type == 1) { // aggregate asphalt, not a flat grey sheet
                    float stone = f > .994f ? .10f + f * .07f : 0f;
                    float tar = .085f + c * .060f + m * .025f;
                    r = tar + stone; g = tar * 1.03f + stone; b = tar * 1.07f + stone;
                } else if (type == 2) { // pale coral sand and limestone grit
                    float ripple = .5f + .5f * (float) Math.sin((x + y * .18f) * .16f + c * 3f);
                    r = .47f + c * .22f + ripple * .07f + f * .035f;
                    g = .40f + c * .19f + ripple * .06f + f * .03f;
                    b = .275f + c * .13f + ripple * .045f + f * .025f;
                } else { // weathered concrete/corrugated construction finish
                    float streak = .5f + .5f * (float) Math.sin(x * .105f + c * 2.4f);
                    r = .38f + c * .19f + m * .05f + streak * .035f;
                    g = .39f + c * .18f + m * .045f + streak * .03f;
                    b = .36f + c * .16f + m * .04f + streak * .025f;
                }
                pixels.put((byte) (clamp(r) * 255));
                pixels.put((byte) (clamp(g) * 255));
                pixels.put((byte) (clamp(b) * 255));
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
