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
        float airportMask = (1f - smoothStep(150f, 280f, airportAcross))
                * (1f - smoothStep(1080f, 1330f, airportAlong));
        plateau *= (1f - airportMask);

        float h = 2.7f + coastalRipple * (1f - plateau * .45f);
        h += plateau * (40f + noise(x * .68f, z * .68f) * 7f);

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
        final int countX = 129, countZ = 145;
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
        MeshBuilder road = new MeshBuilder();
        MeshBuilder marks = new MeshBuilder();
        final int segments = 360;
        float[] p0 = new float[2], p1 = new float[2];
        for (int i = 0; i < segments; i++) {
            float t0 = (float) (i * Math.PI * 2.0 / segments);
            float t1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            coastPoint(t0, .875f, p0);
            coastPoint(t1, .875f, p1);
            addRibbonSegment(road, p0[0], p0[1], p1[0], p1[1], 10.5f, .16f, .18f, .18f, .28f);
            if ((i & 1) == 0) {
                addRibbonSegment(marks, p0[0], p0[1], p1[0], p1[1], .28f, .91f, .82f, .49f, .34f);
            }
        }

        // Nauru International Airport: approximately 2.15 km, aligned with the southern coast.
        addLocalRectangle(road, 0f, -2440f, 2160f, 47f, 3.68f, .12f, .14f, .15f);
        addLocalRectangle(marks, 0f, -2440f, 1940f, .65f, 3.73f, .92f, .92f, .87f);
        for (int i = -8; i <= 8; i++) {
            addLocalRectangle(marks, i * 105f, -2440f, 38f, 1.2f, 3.75f, .95f, .95f, .9f);
        }
        // Threshold bars at either end.
        for (int j = -3; j <= 3; j++) {
            addLocalRectangle(marks, -1015f, -2440f + j * 4.8f, 31f, 2.8f, 3.76f, .95f, .95f, .9f);
            addLocalRectangle(marks, 1015f, -2440f + j * 4.8f, 31f, 2.8f, 3.76f, .95f, .95f, .9f);
        }
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
        b.addBox(p[0], terminalY, p[1], 95, 9, 26, -WORLD_ROTATION, .74f, .71f, .61f);
        b.addHipRoof(p[0], terminalY + 9, p[1], 98, 4, 29, -WORLD_ROTATION, .22f, .36f, .40f);
        localToWorld(-230f, -2180f, p);
        b.addBox(p[0], heightAt(p[0], p[1]), p[1], 70, 12, 32, -WORLD_ROTATION, .64f, .68f, .66f);
        b.addHipRoof(p[0], heightAt(p[0], p[1]) + 12, p[1], 73, 4, 35, -WORLD_ROTATION, .33f, .37f, .39f);

        // Control tower and original airport beacon.
        localToWorld(355f, -2170f, p);
        float towerY = heightAt(p[0], p[1]);
        b.addBox(p[0], towerY, p[1], 10, 23, 10, -WORLD_ROTATION, .58f, .61f, .57f);
        b.addBox(p[0], towerY + 23, p[1], 16, 5, 16, -WORLD_ROTATION, .18f, .39f, .45f);

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
                high.addBox(p[0], y, p[1], sx, sy, sz, yaw, color[0], color[1], color[2]);
                high.addHipRoof(p[0], y + sy, p[1], sx + 1, 2.3f, sz + 1, yaw,
                        .30f + random.nextFloat() * .24f, .24f, .19f);
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
                high.addPalm(p[0], y, p[1], 6.5f + random.nextFloat() * 6f, .8f + random.nextFloat() * .5f);
                // A low-cost crossed crown reads as vegetation at distance.
                low.addCylinder(p[0], y, p[1], .2f, 6f, 4, .27f, .25f, .13f);
                low.addBox(p[0], y + 5.5f, p[1], 3.8f, 2.4f, 3.8f, theta, .12f, .39f, .16f);
            }
            sectors.add(new Sector(centre[0], centre[1], high.build(), low.build()));
        }
    }

    public void invalidateGl() {
        oceanMesh.invalidateGl(); terrainMesh.invalidateGl(); roadMesh.invalidateGl();
        markingMesh.invalidateGl(); landmarkMesh.invalidateGl(); miningMesh.invalidateGl();
        for (Sector sector : sectors) sector.invalidateGl();
    }

    public void drawOcean() {
        oceanMesh.draw();
    }

    public void drawLand(float cameraX, float cameraZ, int quality) {
        terrainMesh.draw();
        roadMesh.draw();
        markingMesh.draw();
        landmarkMesh.draw();

        float miningDistance = (float) Math.hypot(cameraX, cameraZ);
        if (miningDistance < (quality == 0 ? 1350f : 2300f)) miningMesh.draw();

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
        localToWorld(518f, -2196f, p);
        return new float[]{p[0], heightAt(p[0], p[1]), p[1]};
    }

    public float[] vehicleSpawnPoint() {
        float[] p = new float[2];
        localToWorld(525f, -2200f, p);
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
