package ai.arena.nauru.graphics;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/** Interleaved position/normal/colour mesh backed by GPU vertex and index buffers. */
public final class Mesh {
    public static final int FLOATS_PER_VERTEX = 9;
    private final float[] vertices;
    private final short[] indices;
    private int vertexBuffer;
    private int indexBuffer;

    public Mesh(float[] vertices, short[] indices) {
        this.vertices = vertices;
        this.indices = indices;
    }

    public int vertexCount() {
        return vertices.length / FLOATS_PER_VERTEX;
    }

    public int triangleCount() {
        return indices.length / 3;
    }

    private void upload() {
        if (vertexBuffer != 0) return;
        int[] ids = new int[2];
        GLES20.glGenBuffers(2, ids, 0);
        vertexBuffer = ids[0];
        indexBuffer = ids[1];

        FloatBuffer vb = ByteBuffer.allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vb.put(vertices).position(0);
        ShortBuffer ib = ByteBuffer.allocateDirect(indices.length * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        ib.put(indices).position(0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.length * 4, vb, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indices.length * 2, ib, GLES20.GL_STATIC_DRAW);
    }

    public void draw() {
        upload();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBuffer);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        int stride = FLOATS_PER_VERTEX * 4;
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(1);
        GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, stride, 3 * 4);
        GLES20.glEnableVertexAttribArray(2);
        GLES20.glVertexAttribPointer(2, 3, GLES20.GL_FLOAT, false, stride, 6 * 4);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.length, GLES20.GL_UNSIGNED_SHORT, 0);
    }

    /** Call after an EGL context is recreated. The CPU arrays remain available for re-upload. */
    public void invalidateGl() {
        vertexBuffer = 0;
        indexBuffer = 0;
    }

    public void release() {
        if (vertexBuffer == 0) return;
        int[] ids = {vertexBuffer, indexBuffer};
        GLES20.glDeleteBuffers(2, ids, 0);
        vertexBuffer = 0;
        indexBuffer = 0;
    }
}
