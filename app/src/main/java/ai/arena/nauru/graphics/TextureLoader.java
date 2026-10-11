package ai.arena.nauru.graphics;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import ai.arena.nauru.R;

/** Uploads original high-resolution albedo art without Android density scaling. */
public final class TextureLoader {
    private TextureLoader() {}

    public static int[] loadPhotorealSurfaceSet(Context context) {
        return new int[] {
                loadRepeatTexture(context, R.drawable.nauru_grass_albedo),
                loadRepeatTexture(context, R.drawable.nauru_asphalt_albedo),
                loadRepeatTexture(context, R.drawable.nauru_coral_ground_albedo)
        };
    }

    private static int loadRepeatTexture(Context context, int resource) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resource, options);
        if (bitmap == null) throw new IllegalStateException("Unable to decode material texture " + resource);
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        if (extensions != null && extensions.contains("GL_EXT_texture_filter_anisotropic")) {
            float[] maximum = new float[1];
            GLES20.glGetFloatv(0x84FF, maximum, 0); // GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, 0x84FE, Math.min(4f, maximum[0]));
        }
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        bitmap.recycle();
        return ids[0];
    }
}
