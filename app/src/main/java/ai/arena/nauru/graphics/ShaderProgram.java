package ai.arena.nauru.graphics;

import android.opengl.GLES20;
import android.util.Log;

/** Small fail-fast GLSL program wrapper. Attribute locations are stable across every mesh. */
public final class ShaderProgram {
    private static final String TAG = "NauruShader";
    public final int program;

    public ShaderProgram(String vertexSource, String fragmentSource) {
        int vertex = compile(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glBindAttribLocation(program, 0, "aPosition");
        GLES20.glBindAttribLocation(program, 1, "aNormal");
        GLES20.glBindAttribLocation(program, 2, "aColor");
        GLES20.glLinkProgram(program);
        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("Could not link shader: " + log);
        }
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
    }

    private static int compile(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            Log.e(TAG, log);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Could not compile shader: " + log);
        }
        return shader;
    }

    public void use() {
        GLES20.glUseProgram(program);
    }

    public int uniform(String name) {
        return GLES20.glGetUniformLocation(program, name);
    }

    public void delete() {
        GLES20.glDeleteProgram(program);
    }
}
