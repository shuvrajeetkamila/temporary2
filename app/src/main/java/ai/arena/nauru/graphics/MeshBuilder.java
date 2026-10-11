package ai.arena.nauru.graphics;

import java.util.Arrays;

/** Allocation-light procedural geometry builder used for all original environment art. */
public final class MeshBuilder {
    private float[] vertices = new float[9 * 256];
    private short[] indices = new short[6 * 256];
    private int vertexFloats;
    private int indexCount;

    public int vertex(float x, float y, float z,
                      float nx, float ny, float nz,
                      float r, float g, float b) {
        if (vertexFloats + 9 > vertices.length) {
            vertices = Arrays.copyOf(vertices, vertices.length * 2);
        }
        int index = vertexFloats / 9;
        if (index >= 65535) throw new IllegalStateException("Mesh exceeds 16-bit index limit");
        float[] values = {x, y, z, nx, ny, nz, r, g, b};
        System.arraycopy(values, 0, vertices, vertexFloats, 9);
        vertexFloats += 9;
        return index;
    }

    public void triangle(int a, int b, int c) {
        if (indexCount + 3 > indices.length) indices = Arrays.copyOf(indices, indices.length * 2);
        indices[indexCount++] = (short) a;
        indices[indexCount++] = (short) b;
        indices[indexCount++] = (short) c;
    }

    public void quad(int a, int b, int c, int d) {
        triangle(a, b, c);
        triangle(a, c, d);
    }

    public void addHorizontalQuad(float x0, float z0, float x1, float z1, float y,
                                  float r, float g, float b) {
        int a = vertex(x0, y, z0, 0, 1, 0, r, g, b);
        int c = vertex(x1, y, z1, 0, 1, 0, r, g, b);
        int bb = vertex(x1, y, z0, 0, 1, 0, r, g, b);
        int d = vertex(x0, y, z1, 0, 1, 0, r, g, b);
        triangle(a, bb, c);
        triangle(a, c, d);
    }

    public void addBox(float cx, float baseY, float cz, float sx, float sy, float sz,
                       float yaw, float r, float g, float b) {
        float hx = sx * 0.5f, hz = sz * 0.5f;
        float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
        float[][] local = {
                {-hx, 0, -hz}, {hx, 0, -hz}, {hx, 0, hz}, {-hx, 0, hz},
                {-hx, sy, -hz}, {hx, sy, -hz}, {hx, sy, hz}, {-hx, sy, hz}
        };
        float[][] p = new float[8][3];
        for (int i = 0; i < 8; i++) {
            p[i][0] = cx + local[i][0] * cos - local[i][2] * sin;
            p[i][1] = baseY + local[i][1];
            p[i][2] = cz + local[i][0] * sin + local[i][2] * cos;
        }
        addFace(p, 0, 3, 2, 1, 0, -1, 0, r * .72f, g * .72f, b * .72f);
        addFace(p, 4, 5, 6, 7, 0, 1, 0, r * 1.08f, g * 1.08f, b * 1.08f);
        addFace(p, 0, 1, 5, 4, -sin, 0, -cos, r * .88f, g * .88f, b * .88f);
        addFace(p, 1, 2, 6, 5, cos, 0, sin, r, g, b);
        addFace(p, 2, 3, 7, 6, sin, 0, cos, r * .82f, g * .82f, b * .82f);
        addFace(p, 3, 0, 4, 7, -cos, 0, -sin, r * .94f, g * .94f, b * .94f);
    }

    private void addFace(float[][] p, int ia, int ib, int ic, int id,
                         float nx, float ny, float nz, float r, float g, float b) {
        int a = vertex(p[ia][0], p[ia][1], p[ia][2], nx, ny, nz, r, g, b);
        int bb = vertex(p[ib][0], p[ib][1], p[ib][2], nx, ny, nz, r, g, b);
        int c = vertex(p[ic][0], p[ic][1], p[ic][2], nx, ny, nz, r, g, b);
        int d = vertex(p[id][0], p[id][1], p[id][2], nx, ny, nz, r, g, b);
        quad(a, bb, c, d);
    }

    public void addHipRoof(float cx, float y, float cz, float sx, float height, float sz,
                           float yaw, float r, float g, float b) {
        float hx = sx * .56f, hz = sz * .56f;
        float cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
        float[][] p = new float[5][3];
        float[][] local = {{-hx, -hz}, {hx, -hz}, {hx, hz}, {-hx, hz}};
        for (int i = 0; i < 4; i++) {
            p[i][0] = cx + local[i][0] * cos - local[i][1] * sin;
            p[i][1] = y;
            p[i][2] = cz + local[i][0] * sin + local[i][1] * cos;
        }
        p[4][0] = cx; p[4][1] = y + height; p[4][2] = cz;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            float ux = p[j][0] - p[i][0], uy = p[j][1] - p[i][1], uz = p[j][2] - p[i][2];
            float vx = p[4][0] - p[i][0], vy = p[4][1] - p[i][1], vz = p[4][2] - p[i][2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float inv = 1f / Math.max(.001f, (float) Math.sqrt(nx * nx + ny * ny + nz * nz));
            int a = vertex(p[i][0], p[i][1], p[i][2], nx * inv, ny * inv, nz * inv, r, g, b);
            int bb = vertex(p[j][0], p[j][1], p[j][2], nx * inv, ny * inv, nz * inv, r, g, b);
            int c = vertex(p[4][0], p[4][1], p[4][2], nx * inv, ny * inv, nz * inv, r * 1.05f, g * 1.05f, b * 1.05f);
            triangle(a, bb, c);
        }
    }

    public void addCylinder(float cx, float baseY, float cz, float radius, float height,
                            int sides, float r, float g, float b) {
        int bottomCenter = vertex(cx, baseY, cz, 0, -1, 0, r * .7f, g * .7f, b * .7f);
        int topCenter = vertex(cx, baseY + height, cz, 0, 1, 0, r, g, b);
        for (int i = 0; i < sides; i++) {
            double a0 = i * Math.PI * 2.0 / sides;
            double a1 = (i + 1) * Math.PI * 2.0 / sides;
            float x0 = cx + (float) Math.cos(a0) * radius, z0 = cz + (float) Math.sin(a0) * radius;
            float x1 = cx + (float) Math.cos(a1) * radius, z1 = cz + (float) Math.sin(a1) * radius;
            int b0 = vertex(x0, baseY, z0, (float) Math.cos(a0), 0, (float) Math.sin(a0), r, g, b);
            int b1 = vertex(x1, baseY, z1, (float) Math.cos(a1), 0, (float) Math.sin(a1), r, g, b);
            int t1 = vertex(x1, baseY + height, z1, (float) Math.cos(a1), 0, (float) Math.sin(a1), r * 1.04f, g * 1.04f, b * 1.04f);
            int t0 = vertex(x0, baseY + height, z0, (float) Math.cos(a0), 0, (float) Math.sin(a0), r * 1.04f, g * 1.04f, b * 1.04f);
            quad(b0, b1, t1, t0);
            triangle(bottomCenter, b1, b0);
            triangle(topCenter, t0, t1);
        }
    }

    public void addPalm(float x, float y, float z, float height, float scale) {
        addCylinder(x, y, z, .18f * scale, height, 6, .34f, .23f, .12f);
        float top = y + height;
        addCylinder(x, top - .2f, z, .52f * scale, .65f * scale, 7, .28f, .48f, .15f);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI * .25;
            float dx = (float) Math.cos(a), dz = (float) Math.sin(a);
            float sideX = -dz * .34f * scale, sideZ = dx * .34f * scale;
            float len = (3.0f + (i % 3) * .28f) * scale;
            int p0 = vertex(x - sideX, top + .32f, z - sideZ, 0, 1, 0, .12f, .48f, .18f);
            int p1 = vertex(x + sideX, top + .32f, z + sideZ, 0, 1, 0, .18f, .62f, .22f);
            int p2 = vertex(x + dx * len, top - .75f * scale, z + dz * len, 0, 1, 0, .08f, .38f, .13f);
            triangle(p0, p1, p2);
            triangle(p2, p1, p0);
        }
    }

    public void addPinnacle(float x, float y, float z, float radius, float height) {
        int tip = vertex(x + radius * .12f, y + height, z - radius * .08f, 0, 1, 0, .62f, .58f, .47f);
        int sides = 5;
        for (int i = 0; i < sides; i++) {
            double a0 = i * Math.PI * 2 / sides;
            double a1 = (i + 1) * Math.PI * 2 / sides;
            float x0 = x + (float) Math.cos(a0) * radius;
            float z0 = z + (float) Math.sin(a0) * radius;
            float x1 = x + (float) Math.cos(a1) * radius;
            float z1 = z + (float) Math.sin(a1) * radius;
            int p0 = vertex(x0, y, z0, (float) Math.cos(a0), .25f, (float) Math.sin(a0), .55f, .52f, .43f);
            int p1 = vertex(x1, y, z1, (float) Math.cos(a1), .25f, (float) Math.sin(a1), .67f, .63f, .52f);
            triangle(p0, p1, tip);
        }
    }

    public Mesh build() {
        return new Mesh(Arrays.copyOf(vertices, vertexFloats), Arrays.copyOf(indices, indexCount));
    }
}
