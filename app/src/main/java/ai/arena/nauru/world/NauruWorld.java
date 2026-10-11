package ai.arena.nauru.world;

import android.opengl.GLES20;
import android.opengl.Matrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import ai.arena.nauru.graphics.Mesh;
import ai.arena.nauru.graphics.MeshBuilder;

/**
 * Procedural, geographically scaled approximation of Nauru.
 *
 * One world unit is one metre. The coast is about 6 km north-to-south and 4 km
 * east-to-west. All geometry is original and generated at runtime; geographic
 * proportions are intentionally data-driven so surveyed terrain can replace it later.
 */
public final class NauruWorld {
    public static final float ISLAND_SHORT_RADIUS = 2100f;
    public static final float ISLAND_LONG_RADIUS = 3000f;
    public static final float WORLD_ROTATION = 0.16f;
    public static final float OCEAN_LEVEL = 0f;

    private Mesh oceanMesh;
    private Mesh terrainMesh;
    private Mesh shoulderMesh;
    private Mesh roadMesh;
    private Mesh markingMesh;
    private Mesh landmarkMesh;
    private Mesh miningMesh;
    private final List<Sector> sectors = new ArrayList<>();
    private final float[] identity = new float[16];
    private int visibleSectors;

    public NauruWorld() {
        Matrix.setIdentityM(identity, 0);
        buildWorld();
    }

    private void buildWorld() {
        oceanMesh = buildOcean();
        terrainMesh = buildTerrain();
        buildRoadsAndAirport();
        buildLandmarks();
        buildMiningLandscape();
        buildCoastalSectors();
    }

    /** A lightly irregular coast preserves Nauru's compact oval rather than making a generic circle. */
    public static float coastVariation(float theta) {
        return 1f
                + .038f * (float) Math.sin(theta * 3f + .45f)
                + .024f * (float) Math.sin(theta * 5f - .8f)
                + .015f * (float) Math.cos(theta * 2f + .2f);
    }

    public static void worldToLocal(float x, float z, float[] out) {
        float c = (float) Math.cos(WORLD_ROTATION), s = (float) Math.sin(WORLD_ROTATION);
        out[0] = x * c - z * s;
        out[1] = x * s + z * c;
    }

    public static void localToWorld(float localX, float localZ, float[] out) {
        float c = (float) Math.cos(WORLD_ROTATION), s = (float) Math.sin(WORLD_ROTATION);
        out[0] = localX * c + localZ * s;
        out[1] = -localX * s + localZ * c;
    }

    /** Returns normalised distance from island centre; the coastline is approximately 1. */
    public static float coastRatio(float x, float z) {
        float[] p = new float[2];
        worldToLocal(x, z, p);
        float nx = p[0] / ISLAND_SHORT_RADIUS;
        float nz = p[1] / ISLAND_LONG_RADIUS;
        float theta = (float) Math.atan2(nz, nx);
        return (float) Math.sqrt(nx * nx + nz * nz) / coastVariation(theta);
    }

    public static void coastPoint(float theta, float scale, float[] out) {
        float edge = coastVariation(theta) * scale;
        float lx = (float) Math.cos(theta) * ISLAND_SHORT_RADIUS * edge;
        float lz = (float) Math.sin(theta) * ISLAND_LONG_RADIUS * edge;
        localToWorld(lx, lz, out);
    }

    public float heightAt(float x, float z) {
        float[] local = new float[2];
        worldToLocal(x, z, local);
        float lx = local[0], lz = local[1];
        float nx = lx / ISLAND_SHORT_RADIUS;
        float nz = lz / ISLAND_LONG_RADIUS;
        float theta = (float) Math.atan2(nz, nx);
        float ratio = (float) Math.sqrt(nx * nx + nz * nz) / coastVariation(theta);

        if (ratio >= 1f) {
            return Math.max(-14f, .7f - (ratio - 1f) * 155f);
        }

        float coastalRipple = noise(x, z) * 1.25f + noise(x * .43f + 91f, z * .43f) * .55f;
        float plateau = smoothStep(.84f, .62f, ratio);

        // The southern airport corridor is deliberately graded flat near real-world scale.
        float airportAlong = Math.abs(lx);
        float airportAcross = Math.abs(lz + 2440f);
        float airportMask = (1f - smoothStep(250f, 430f, airportAcross))
                * (1f - smoothStep(1080f, 1330f, airportAlong));
        plateau *= (1f - airportMask);

        float h = 2.7f + coastalRipple * (1f - plateau * .45f);
        h += plateau * (40f + noise(x * .68f, z * .68f) * 7f);
        h = h * (1f - airportMask) + 3.50f * airportMask;

        // Buada Lagoon: a small inland depression naturally filled by the global water plane.
        float dx = lx + 520f, dz = lz + 270f;
        float lagoonDistance = (float) Math.sqrt(dx * dx + dz * dz);
        if (lagoonDistance < 170f) {
            float lagoonBed = -1.5f + smoothStep(70f, 170f, lagoonDistance) * 8f;
            h = Math.min(h, lagoonBed);
        }
        return h;
    }

    public boolean isWalkableLand(float x, float z) {
        return coastRatio(x, z) < 1.005f && heightAt(x, z) > .15f;
    }

    private static float noise(float x, float z) {
        return (float) (Math.sin(x * .0073 + Math.cos(z * .0041) * 1.7)
                * .55 + Math.sin(z * .0111 - x * .0027) * .28
                + Math.cos((x + z) * .0037) * .17);
    }

    private static float smoothStep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    private Mesh buildOcean() {
        MeshBuilder b = new MeshBuilder();
        int grid = 40;
        float extent = 10500f;
        int[][] ids = new int[grid + 1][grid + 1];
        for (int z = 0; z <= grid; z++) {
            for (int x = 0; x <= grid; x++) {
                float px = -extent + x * extent * 2f / grid;
                float pz = -extent + z * extent * 2f / grid;
                ids[z][x] = b.vertex(px, OCEAN_LEVEL, pz, 0, 1, 0, .03f, .35f, .44f);
            }
        }
        for (int z = 0; z < grid; z++) {
            for (int x = 0; x < grid; x++) {
                b.quad(ids[z][x], ids[z][x + 1], ids[z + 1][x + 1], ids[z + 1][x]);
            }
        }
        return b.build();
    }

    private Mesh buildTerrain() {
        // Dense enough that coastal shelves and mining undulation remain smooth at road speed.
        final int countX = 161, countZ = 181;
        final float minX = -3500f, maxX = 3500f, minZ = -3900f, maxZ = 3900f;
        MeshBuilder b = new MeshBuilder();
        int[][] ids = new int[countZ][countX];
        for (int iz = 0; iz < countZ; iz++) {
            float z = minZ + (maxZ - minZ) * iz / (countZ - 1f);
            for (int ix = 0; ix < countX; ix++) {
                float x = minX + (maxX - minX) * ix / (countX - 1f);
                float y = heightAt(x, z);
                float sample = 12f;
                float nx = heightAt(x - sample, z) - heightAt(x + sample, z);
                float nz = heightAt(x, z - sample) - heightAt(x, z + sample);
                float inv = 1f / (float) Math.sqrt(nx * nx + sample * sample * 4f + nz * nz);
                nx *= inv; float ny = sample * 2f * inv; nz *= inv;

                float ratio = coastRatio(x, z);
                float variation = noise(x * 1.8f, z * 1.8f) * .04f;
                float r, g, blue;
                if (ratio > 1f) {
                    r = .55f + variation; g = .57f + variation; blue = .48f + variation;
                } else if (ratio > .925f) {
                    r = .78f + variation; g = .70f + variation; blue = .49f + variation;
                } else if (ratio > .76f) {
                    r = .25f + variation; g = .48f + variation; blue = .22f + variation;
                } else if (ratio < .68f) {
                    r = .48f + variation; g = .46f + variation; blue = .34f + variation;
                } else {
                    r = .20f + variation; g = .39f + variation; blue = .18f + variation;
                }
                ids[iz][ix] = b.vertex(x, y, z, nx, ny, nz, r, g, blue);
            }
        }
        for (int z = 0; z < countZ - 1; z++) {
            for (int x = 0; x < countX - 1; x++) {
                b.quad(ids[z][x], ids[z][x + 1], ids[z + 1][x + 1], ids[z + 1][x]);
            }
        }
        return b.build();
    }

    private void buildRoadsAndAirport() {
        MeshBuilder shoulder = new MeshBuilder();
        MeshBuilder road = new MeshBuilder();
        MeshBuilder marks = new MeshBuilder();
        final int segments = 360;
        float[] p0 = new float[2], p1 = new float[2];
        for (int i = 0; i < segments; i++) {
            float t0 = (float) (i * Math.PI * 2.0 / segments);
            float t1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            coastPoint(t0, .875f, p0);
            coastPoint(t1, .875f, p1);
            addRibbonSegment(shoulder, p0[0], p0[1], p1[0], p1[1], 15.5f, .38f, .34f, .26f, .20f);
            addRibbonSegment(road, p0[0], p0[1], p1[0], p1[1], 10.5f, .115f, .13f, .135f, .28f);
            if ((i & 1) == 0) {
                addRibbonSegment(marks, p0[0], p0[1], p1[0], p1[1], .28f, .91f, .82f, .49f, .34f);
            }
        }

        // Nauru International Airport: approximately 2.15 km, aligned with the southern coast.
        addLocalRectangle(shoulder, 0f, -2440f, 2190f, 58f, 3.60f, .36f, .35f, .31f);
        addLocalRectangle(road, 0f, -2440f, 2160f, 47f, 3.68f, .095f, .105f, .11f);
        // Terminal apron and taxi connection provide a detailed, believable first-play location.
        addLocalRectangle(shoulder, 230f, -2315f, 525f, 215f, 3.57f, .35f, .34f, .30f);
        addLocalRectangle(road, 230f, -2315f, 505f, 197f, 3.66f, .105f, .115f, .12f);
        addLocalRectangle(marks, 230f, -2410f, 92f, .55f, 3.72f, .88f, .76f, .34f);
        for (int bay = -4; bay <= 4; bay++) {
            addLocalRectangle(marks, 230f + bay * 42f, -2230f, 24f, .42f, 3.72f, .90f, .82f, .48f);
        }
        addLocalRectangle(marks, 0f, -2440f, 1940f, .65f, 3.73f, .92f, .92f, .87f);
        for (int i = -8; i <= 8; i++) {
            addLocalRectangle(marks, i * 105f, -2440f, 38f, 1.2f, 3.75f, .95f, .95f, .9f);
        }
        // Threshold bars at either end.
        for (int j = -3; j <= 3; j++) {
            addLocalRectangle(marks, -1015f, -2440f + j * 4.8f, 31f, 2.8f, 3.76f, .95f, .95f, .9f);
            addLocalRectangle(marks, 1015f, -2440f + j * 4.8f, 31f, 2.8f, 3.76f, .95f, .95f, .9f);
        }
        shoulderMesh = shoulder.build();
        roadMesh = road.build();
        markingMesh = marks.build();
    }

    private void addRibbonSegment(MeshBuilder b, float x0, float z0, float x1, float z1,
                                  float width, float r, float g, float blue, float lift) {
        float dx = x1 - x0, dz = z1 - z0;
        float inv = 1f / Math.max(.001f, (float) Math.sqrt(dx * dx + dz * dz));
        float sx = -dz * inv * width * .5f, sz = dx * inv * width * .5f;
        int a = b.vertex(x0 + sx, heightAt(x0 + sx, z0 + sz) + lift, z0 + sz, 0, 1, 0, r, g, blue);
        int bb = b.vertex(x1 + sx, heightAt(x1 + sx, z1 + sz) + lift, z1 + sz, 0, 1, 0, r, g, blue);
        int c = b.vertex(x1 - sx, heightAt(x1 - sx, z1 - sz) + lift, z1 - sz, 0, 1, 0, r, g, blue);
        int d = b.vertex(x0 - sx, heightAt(x0 - sx, z0 - sz) + lift, z0 - sz, 0, 1, 0, r, g, blue);
        b.quad(a, bb, c, d);
    }

    private void addLocalRectangle(MeshBuilder b, float localCx, float localCz, float length,
                                   float width, float y, float r, float g, float blue) {
        float[] p = new float[2];
        float[][] corners = {{-length / 2, -width / 2}, {length / 2, -width / 2},
                {length / 2, width / 2}, {-length / 2, width / 2}};
        int[] ids = new int[4];
        for (int i = 0; i < 4; i++) {
            localToWorld(localCx + corners[i][0], localCz + corners[i][1], p);
            ids[i] = b.vertex(p[0], y, p[1], 0, 1, 0, r, g, blue);
        }
        b.quad(ids[0], ids[1], ids[2], ids[3]);
    }

    private void buildLandmarks() {
        MeshBuilder b = new MeshBuilder();
        float[] p = new float[2];
        localToWorld(230f, -2180f, p);
        float terminalY = heightAt(p[0], p[1]);
        b.addBox(p[0], terminalY, p[1], 95, 9, 26, -WORLD_ROTATION, .84f, .81f, .70f);
        b.addHipRoof(p[0], terminalY + 9, p[1], 98, 4, 29, -WORLD_ROTATION, .22f, .36f, .40f);
        for (int i = -4; i <= 4; i++) {
            float[] window = new float[2];
            localToWorld(230f + i * 9.2f, -2193.1f, window);
            b.addBox(window[0], terminalY + 2.2f, window[1], 5.6f, 3.7f, .16f,
                    -WORLD_ROTATION, .045f, .19f, .25f);
        }
        float[] entrance = new float[2];
        localToWorld(230f, -2197f, entrance);
        b.addBox(entrance[0], terminalY + 3.4f, entrance[1], 34f, .32f, 6.5f,
                -WORLD_ROTATION, .24f, .34f, .36f);
        for (int side = -1; side <= 1; side += 2) {
            float[] column = new float[2];
            localToWorld(230f + side * 14f, -2198f, column);
            b.addCylinder(column[0], terminalY, column[1], .34f, 3.5f, 10,
                    .70f, .69f, .62f);
        }
        localToWorld(-230f, -2180f, p);
        b.addBox(p[0], heightAt(p[0], p[1]), p[1], 70, 12, 32, -WORLD_ROTATION, .64f, .68f, .66f);
        b.addHipRoof(p[0], heightAt(p[0], p[1]) + 12, p[1], 73, 4, 35, -WORLD_ROTATION, .33f, .37f, .39f);

        // Control tower and original airport beacon.
        localToWorld(355f, -2170f, p);
        float towerY = heightAt(p[0], p[1]);
        b.addBox(p[0], towerY, p[1], 10, 23, 10, -WORLD_ROTATION, .58f, .61f, .57f);
        b.addBox(p[0], towerY + 23, p[1], 16, 5, 16, -WORLD_ROTATION, .18f, .39f, .45f);

        // Apron lighting and landscaped airport edge create human-scale depth at first spawn.
        for (int i = -2; i <= 2; i++) {
            float[] lamp = new float[2];
            localToWorld(230f + i * 92f, -2218f, lamp);
            b.addCylinder(lamp[0], 3.68f, lamp[1], .16f, 10.5f, 8, .31f, .34f, .34f);
            b.addBox(lamp[0], 14.0f, lamp[1], 2.4f, .38f, .55f,
                    -WORLD_ROTATION, .76f, .78f, .72f);
        }
        for (int i = -3; i <= 3; i++) {
            float[] tree = new float[2];
            localToWorld(230f + i * 52f, -2115f - (i & 1) * 12f, tree);
            float treeY = heightAt(tree[0], tree[1]);
            b.addPalm(tree[0], treeY, tree[1], 8.5f + (i & 1) * 1.4f, .82f);
        }

        // Aiwo-inspired west-coast industrial silhouettes and cantilever remnants.
        coastPoint((float) Math.PI, .82f, p);
        float industrialY = heightAt(p[0], p[1]);
        for (int i = -2; i <= 2; i++) {
            b.addBox(p[0] + i * 28, industrialY, p[1] + i * 5, 20, 11 + (i & 1) * 6, 24,
                    .28f, .43f, .34f, .25f);
        }
        b.addCylinder(p[0] - 55, industrialY, p[1] + 25, 11, 17, 12, .52f, .43f, .31f);

        // East-coast harbour marker and modest lighthouse.
        coastPoint(0f, .91f, p);
        float harbourY = heightAt(p[0], p[1]);
        b.addBox(p[0], harbourY, p[1], 58, 7, 22, 0, .48f, .52f, .50f);
        b.addCylinder(p[0] + 36, harbourY, p[1] - 8, 3.5f, 24, 10, .82f, .78f, .64f);
        b.addCylinder(p[0] + 36, harbourY + 24, p[1] - 8, 5f, 3.5f, 10, .17f, .37f, .42f);

        landmarkMesh = b.build();
    }

    private void buildMiningLandscape() {
        MeshBuilder b = new MeshBuilder();
        Random random = new Random(674201L);
        for (int i = 0; i < 190; i++) {
            float theta = random.nextFloat() * (float) Math.PI * 2f;
            float radius = (float) Math.sqrt(random.nextFloat()) * .61f;
            float[] p = new float[2];
            coastPoint(theta, radius, p);
            // Leave the lagoon and a rough central haul route open.
            float[] local = new float[2];
            worldToLocal(p[0], p[1], local);
            if (Math.hypot(local[0] + 520, local[1] + 270) < 230 || Math.abs(local[0]) < 26) continue;
            float y = heightAt(p[0], p[1]);
            float size = 1.2f + random.nextFloat() * 2.8f;
            b.addPinnacle(p[0], y, p[1], size, 3f + random.nextFloat() * 9f);
        }
        // Abandoned, non-identifiable mining gantry and machinery remnants.
        float[] c = new float[2];
        localToWorld(260, 260, c);
        float y = heightAt(c[0], c[1]);
        b.addBox(c[0], y, c[1], 5, 24, 5, 0, .38f, .29f, .19f);
        b.addBox(c[0] + 40, y, c[1], 5, 24, 5, 0, .38f, .29f, .19f);
        b.addBox(c[0] + 20, y + 20, c[1], 46, 4, 5, 0, .43f, .31f, .19f);
        b.addBox(c[0] + 20, y + 4, c[1] + 22, 22, 6, 10, .4f, .45f, .30f, .18f);
        miningMesh = b.build();
    }

    private void addDetailedHouse(MeshBuilder b, float x, float y, float z,
                                  float sx, float sy, float sz, float yaw,
                                  float r, float g, float blue, Random random) {
        // Foundation, plaster/timber walls and overhanging roof are separate materials by colour.
        b.addBox(x, y, z, sx + .7f, .34f, sz + .7f, yaw, .42f, .40f, .34f);
        b.addBox(x, y + .34f, z, sx, sy, sz, yaw, r, g, blue);
        float roofR = .25f + random.nextFloat() * .28f;
        b.addHipRoof(x, y + sy + .34f, z, sx + 1.25f, 2.15f, sz + 1.25f,
                yaw, roofR, .22f + random.nextFloat() * .12f, .18f);

        float sin = (float) Math.sin(yaw), cos = (float) Math.cos(yaw);
        float forwardX = -sin, forwardZ = cos;
        float sideX = cos, sideZ = sin;
        float faceOffset = sz * .5f + .08f;
        float doorOffset = -sx * .18f;
        float doorX = x + forwardX * faceOffset + sideX * doorOffset;
        float doorZ = z + forwardZ * faceOffset + sideZ * doorOffset;
        b.addBox(doorX, y + .35f, doorZ, Math.min(1.45f, sx * .13f), 2.35f, .16f,
                yaw, .24f, .20f, .15f);
        // Two reflective, recessed windows on the road-facing facade.
        for (int side = -1; side <= 1; side += 2) {
            float offset = side * sx * .25f;
            float wx = x + forwardX * (faceOffset + .015f) + sideX * offset;
            float wz = z + forwardZ * (faceOffset + .015f) + sideZ * offset;
            b.addBox(wx, y + 1.42f, wz, Math.min(2.25f, sx * .20f), 1.35f, .12f,
                    yaw, .055f, .16f, .205f);
            // Pale lintel catches sunlight and gives the opening readable depth.
            b.addBox(wx, y + 2.78f, wz, Math.min(2.48f, sx * .22f), .13f, .18f,
                    yaw, .78f, .77f, .68f);
        }
        // A thin shaded porch canopy, plus a small service unit on varied homes.
        b.addBox(doorX, y + 2.72f, doorZ + forwardZ * .58f,
                Math.min(3.2f, sx * .28f), .14f, 1.35f, yaw, .31f, .34f, .32f);
        if (random.nextBoolean()) {
            float unitX = x - sideX * (sx * .5f + .28f);
            float unitZ = z - sideZ * (sx * .5f + .28f);
            b.addBox(unitX, y + 1.0f, unitZ, .52f, .72f, 1.05f,
                    yaw, .60f, .62f, .57f);
        }
    }

    private void buildCoastalSectors() {
        final int sectorCount = 12;
        for (int sectorIndex = 0; sectorIndex < sectorCount; sectorIndex++) {
            float centreTheta = (float) ((sectorIndex + .5) * Math.PI * 2.0 / sectorCount);
            float[] centre = new float[2];
            coastPoint(centreTheta, .84f, centre);
            MeshBuilder high = new MeshBuilder();
            MeshBuilder low = new MeshBuilder();
            Random random = new Random(1009L + sectorIndex * 8191L);

            for (int i = 0; i < 20; i++) {
                float theta = (float) ((sectorIndex + .08 + random.nextFloat() * .84)
                        * Math.PI * 2.0 / sectorCount);
                float scale = .79f + random.nextFloat() * .075f;
                float[] p = new float[2];
                coastPoint(theta, scale, p);
                float[] local = new float[2];
                worldToLocal(p[0], p[1], local);
                if (local[1] < -2000 && Math.abs(local[0]) < 1320) continue;
                float y = heightAt(p[0], p[1]);
                float sx = 11 + random.nextFloat() * 17;
                float sz = 9 + random.nextFloat() * 13;
                float sy = 4.2f + random.nextFloat() * 3.8f;
                float yaw = theta + (float) Math.PI * .5f;
                float[][] palette = {{.72f,.62f,.47f},{.68f,.75f,.67f},{.73f,.54f,.43f},{.50f,.67f,.69f},{.78f,.73f,.61f}};
                float[] color = palette[random.nextInt(palette.length)];
                addDetailedHouse(high, p[0], y, p[1], sx, sy, sz, yaw,
                        color[0], color[1], color[2], random);
                low.addBox(p[0], y, p[1], sx, sy + 1.4f, sz, yaw,
                        color[0] * .9f, color[1] * .9f, color[2] * .9f);
            }

            for (int i = 0; i < 32; i++) {
                float theta = (float) ((sectorIndex + random.nextFloat()) * Math.PI * 2.0 / sectorCount);
                float scale = .72f + random.nextFloat() * .24f;
                float[] p = new float[2];
                coastPoint(theta, scale, p);
                float[] local = new float[2];
                worldToLocal(p[0], p[1], local);
                if (local[1] < -2110 && Math.abs(local[0]) < 1250) continue;
                float y = heightAt(p[0], p[1]);
                if (y < .4f) continue;
                float treeScale = .78f + random.nextFloat() * .48f;
                if (i % 4 == 0) {
                    high.addTropicalTree(p[0], y, p[1], 7.5f + random.nextFloat() * 4f, treeScale);
                } else {
                    high.addPalm(p[0], y, p[1], 7.5f + random.nextFloat() * 6f, treeScale);
                }
                // Rounded distant crowns avoid the old block-shaped vegetation silhouette.
                low.addCylinder(p[0], y, p[1], .19f, 6f, 5, .27f, .25f, .13f);
                low.addEllipsoid(p[0], y + 6.1f, p[1], 2.2f, 1.5f, 2.2f,
                        4, 6, .10f, .36f, .13f);
            }
            sectors.add(new Sector(centre[0], centre[1], high.build(), low.build()));
        }
    }

    public void invalidateGl() {
        oceanMesh.invalidateGl(); terrainMesh.invalidateGl(); shoulderMesh.invalidateGl(); roadMesh.invalidateGl();
        markingMesh.invalidateGl(); landmarkMesh.invalidateGl(); miningMesh.invalidateGl();
        for (Sector sector : sectors) sector.invalidateGl();
    }

    public void drawOcean() {
        oceanMesh.draw();
    }

    /** Draws streamed world categories while selecting their physically-inspired material class. */
    public void drawLand(float cameraX, float cameraZ, int quality, int materialUniform) {
        setMaterial(materialUniform, 0); terrainMesh.draw();
        setMaterial(materialUniform, 0); shoulderMesh.draw();
        setMaterial(materialUniform, 1); roadMesh.draw();
        setMaterial(materialUniform, 2); markingMesh.draw();
        setMaterial(materialUniform, 3); landmarkMesh.draw();

        float miningDistance = (float) Math.hypot(cameraX, cameraZ);
        if (miningDistance < (quality == 0 ? 1350f : 2300f)) {
            setMaterial(materialUniform, 4); miningMesh.draw();
        }

        setMaterial(materialUniform, 3);
        visibleSectors = 0;
        float highRange = quality == 0 ? 650f : quality == 1 ? 1050f : 1450f;
        float lowRange = quality == 0 ? 1450f : quality == 1 ? 2150f : 3000f;
        for (Sector sector : sectors) {
            float dx = sector.x - cameraX, dz = sector.z - cameraZ;
            float distance = (float) Math.sqrt(dx * dx + dz * dz);
            if (distance < highRange) {
                sector.high.draw(); visibleSectors++;
            } else if (distance < lowRange) {
                sector.low.draw(); visibleSectors++;
            }
        }
    }

    private static void setMaterial(int uniform, int material) {
        if (uniform >= 0) GLES20.glUniform1f(uniform, material);
    }

    public int getVisibleSectors() {
        return visibleSectors;
    }

    public String locationName(float x, float z) {
        float[] local = new float[2];
        worldToLocal(x, z, local);
        float ratio = coastRatio(x, z);
        if (local[1] < -2010 && Math.abs(local[0]) < 1350) return "Nauru International Airport";
        if (Math.hypot(local[0] + 520, local[1] + 270) < 260) return "Buada Lagoon";
        if (ratio < .7f) return "Topside Mining Lands";
        float theta = (float) Math.atan2(local[1] / ISLAND_LONG_RADIUS, local[0] / ISLAND_SHORT_RADIUS);
        if (theta > -.45 && theta < .48) return "Anibare Coast";
        if (theta >= .48 && theta < 1.45) return "Ijuw–Anabar";
        if (theta >= 1.45 && theta < 2.35) return "Ewa–Anetan";
        if (theta >= 2.35 || theta < -2.65) return "Aiwo District";
        if (theta >= -2.65 && theta < -1.78) return "Boe–Yaren";
        return "Meneng Coast";
    }

    /** Suggested first-spawn position beside the runway and the Reef Runner. */
    public float[] spawnPoint() {
        float[] p = new float[2];
        localToWorld(340f, -2300f, p);
        return new float[]{p[0], heightAt(p[0], p[1]), p[1]};
    }

    public float[] vehicleSpawnPoint() {
        float[] p = new float[2];
        localToWorld(335f, -2296f, p);
        return new float[]{p[0], heightAt(p[0], p[1]) + .15f, p[1]};
    }

    public float runwayYaw() {
        // Vehicle forward is (sin(yaw), cos(yaw)); this points along positive local X.
        return (float) (Math.PI * .5 + WORLD_ROTATION);
    }

    public String scaleReadout() {
        return String.format(Locale.US, "1 unit = 1 m • %.1f km island", 6.0f);
    }

    private static final class Sector {
        final float x, z;
        final Mesh high, low;
        Sector(float x, float z, Mesh high, Mesh low) {
            this.x = x; this.z = z; this.high = high; this.low = low;
        }
        void invalidateGl() { high.invalidateGl(); low.invalidateGl(); }
    }
}
