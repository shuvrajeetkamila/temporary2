package ai.arena.nauru.world;

import android.content.Context;
import android.opengl.GLES20;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import ai.arena.nauru.graphics.Mesh;
import ai.arena.nauru.graphics.MeshBuilder;

/**
 * Rebuilt, thermally efficient 1:25 game-world interpretation of real Nauru GIS data.
 * Island distances are compressed while people, vehicles, roads and selected architecture
 * retain believable human scale. Minor overlapping map objects are deliberately culled.
 */
public final class NauruWorld {
    public static final float OCEAN_LEVEL = 0f;
    public static final float WORLD_SCALE = 1f / 25f;
    private static final float HEIGHT_SCALE = .20f;

    private static final float REAL_RUNWAY_AX = -2259.713f;
    private static final float REAL_RUNWAY_AZ = -1823.714f;
    private static final float REAL_RUNWAY_BX = -730.693f;
    private static final float REAL_RUNWAY_BZ = -3125.139f;
    private static final float RUNWAY_AX = REAL_RUNWAY_AX * WORLD_SCALE;
    private static final float RUNWAY_AZ = REAL_RUNWAY_AZ * WORLD_SCALE;
    private static final float RUNWAY_BX = REAL_RUNWAY_BX * WORLD_SCALE;
    private static final float RUNWAY_BZ = REAL_RUNWAY_BZ * WORLD_SCALE;
    private static final float RUNWAY_ELEVATION = 7.2f * HEIGHT_SCALE;

    private final NauruMapData source;
    private final float[] compactCoastline;
    private final List<NauruMapData.Road> compactRoads = new ArrayList<>();
    private final List<Sector> sectors = new ArrayList<>();

    private Mesh oceanMesh;
    private Mesh terrainMesh;
    private Mesh shoulderMesh;
    private Mesh roadMesh;
    private Mesh trackMesh;
    private Mesh markingMesh;
    private Mesh mappedSurfaceMesh;
    private Mesh solarMesh;
    private Mesh miningMesh;
    private int visibleSectors;
    private int renderedBuildingCount;
    private int renderedSolarCount;

    public NauruWorld(Context context) {
        source = NauruMapData.load(context.getApplicationContext());
        compactCoastline = compactGlobalPolygon(source.coastline);
        selectCompactRoadNetwork();
        buildWorld();
    }

    private void buildWorld() {
        oceanMesh = buildOceanAndLagoon();
        terrainMesh = buildTerrain();
        buildRoadsAndAirport();
        buildInfrastructureLandscape();
        buildRepresentativeSectors();
    }

    private static float game(float realMetres) { return realMetres * WORLD_SCALE; }
    private static float real(float gameMetres) { return gameMetres / WORLD_SCALE; }

    public float heightAt(float x, float z) {
        float height = source.elevationAt(real(x), real(z)) * HEIGHT_SCALE;
        if (source.containsLand(real(x), real(z))) height = Math.max(.12f, height);
        float runwayDistance = distanceToSegment(x, z, RUNWAY_AX, RUNWAY_AZ, RUNWAY_BX, RUNWAY_BZ);
        if (runwayDistance < 8.5f) {
            float blend = smoothStep(2.8f, 8.5f, runwayDistance);
            height = RUNWAY_ELEVATION * (1f - blend) + height * blend;
        }
        return height;
    }

    public boolean isWalkableLand(float x, float z) {
        float realX = real(x), realZ = real(z);
        return source.containsLand(realX, realZ)
                && areaCategoryAtReal(realX, realZ) != 0 && heightAt(x, z) > .04f;
    }

    public boolean isNearMappedRoad(float x, float z) {
        for (NauruMapData.Road road : compactRoads) {
            if (road.material >= 3) continue;
            float margin = road.width * .5f + 1.2f;
            if (x < road.minX - margin || x > road.maxX + margin
                    || z < road.minZ - margin || z > road.maxZ + margin) continue;
            for (int i = 0; i + 3 < road.points.length; i += 2) {
                if (distanceToSegment(x, z, road.points[i], road.points[i + 1],
                        road.points[i + 2], road.points[i + 3]) <= margin) return true;
            }
        }
        return false;
    }

    private void selectCompactRoadNetwork() {
        int minorIndex = 0;
        for (NauruMapData.Road road : source.roads) {
            boolean essential = road.material == 0 || road.material >= 4;
            boolean keepMinor = road.material == 1 && (minorIndex++ % 3 == 0);
            boolean keepTrack = road.material == 2 && (minorIndex++ % 5 == 0);
            boolean keepPath = road.material == 3 && (minorIndex++ % 11 == 0);
            if (!essential && !keepMinor && !keepTrack && !keepPath) continue;
            float width;
            if (road.material == 4) width = 15f;
            else if (road.material == 5) width = 4.2f;
            else if (road.material == 6) width = 2.5f;
            else if (road.material == 0) width = 6.4f;
            else if (road.material == 1) width = 4.2f;
            else if (road.material == 2) width = 2.5f;
            else width = 1.25f;
            compactRoads.add(new NauruMapData.Road(road.material, width,
                    compactGlobalPolygon(road.points)));
        }
    }

    private Mesh buildOceanAndLagoon() {
        MeshBuilder builder = new MeshBuilder();
        int grid = 28;
        float extent = 430f;
        int[][] ids = new int[grid + 1][grid + 1];
        for (int z = 0; z <= grid; z++) {
            for (int x = 0; x <= grid; x++) {
                float px = -extent + x * extent * 2f / grid;
                float pz = -extent + z * extent * 2f / grid;
                ids[z][x] = builder.vertex(px, OCEAN_LEVEL, pz, 0, 1, 0, .025f, .31f, .40f);
            }
        }
        for (int z = 0; z < grid; z++) {
            for (int x = 0; x < grid; x++) {
                builder.quad(ids[z][x], ids[z][x + 1], ids[z + 1][x + 1], ids[z + 1][x]);
            }
        }
        for (NauruMapData.Area area : source.areas) {
            if (area.category != 0) continue;
            float[] polygon = compactGlobalPolygon(area.points);
            float[] centre = centroid(polygon);
            float waterHeight = Math.max(.08f, heightAt(centre[0], centre[1]) + .045f);
            builder.addPolygonSurface(polygon, waterHeight, .025f, .28f, .32f);
        }
        return builder.build();
    }

    private Mesh buildTerrain() {
        final int countX = 121, countZ = 121;
        final float minX = -145f, maxX = 150f, minZ = -155f, maxZ = 130f;
        MeshBuilder builder = new MeshBuilder();
        int[][] ids = new int[countZ][countX];
        for (int iz = 0; iz < countZ; iz++) {
            float z = minZ + (maxZ - minZ) * iz / (countZ - 1f);
            for (int ix = 0; ix < countX; ix++) {
                float x = minX + (maxX - minX) * ix / (countX - 1f);
                float y = heightAt(x, z);
                float sample = 1.2f;
                float nx = heightAt(x - sample, z) - heightAt(x + sample, z);
                float nz = heightAt(x, z - sample) - heightAt(x, z + sample);
                float inv = 1f / Math.max(.001f,
                        (float) Math.sqrt(nx * nx + sample * sample * 4f + nz * nz));
                nx *= inv; float ny = sample * 2f * inv; nz *= inv;
                float realX = real(x), realZ = real(z);
                boolean land = source.containsLand(realX, realZ);
                int area = land ? areaCategoryAtReal(realX, realZ) : -1;
                float[] color = terrainColor(land, area, y, x, z);
                ids[iz][ix] = builder.vertex(x, y, z, nx, ny, nz,
                        color[0], color[1], color[2]);
            }
        }
        for (int z = 0; z < countZ - 1; z++) {
            for (int x = 0; x < countX - 1; x++) {
                builder.quad(ids[z][x], ids[z][x + 1], ids[z + 1][x + 1], ids[z + 1][x]);
            }
        }
        return builder.build();
    }

    private float[] terrainColor(boolean land, int area, float height, float x, float z) {
        float variation = ((float) Math.sin(x * .42f + z * .23f)
                + (float) Math.sin(z * .61f - x * .31f)) * .018f;
        if (!land) {
            if (height > -.7f) return new float[]{.55f + variation, .55f + variation, .43f + variation};
            return new float[]{.17f, .25f, .28f};
        }
        switch (area) {
            case 0: return new float[]{.36f, .42f, .29f};
            case 1: return new float[]{.11f + variation, .34f + variation, .12f};
            case 2: return new float[]{.19f + variation, .43f + variation, .17f};
            case 3: return new float[]{.54f + variation, .49f + variation, .37f + variation};
            case 4: return new float[]{.75f + variation, .68f + variation, .48f + variation};
            case 5: return new float[]{.27f + variation, .46f + variation, .23f};
            case 6: return new float[]{.12f, .13f, .135f};
            case 7: return new float[]{.23f, .36f, .21f};
            default:
                if (height > 5.5f) return new float[]{.42f + variation, .41f + variation, .30f};
                return new float[]{.18f + variation, .42f + variation, .17f};
        }
    }

    private void buildRoadsAndAirport() {
        MeshBuilder shoulders = new MeshBuilder();
        MeshBuilder paved = new MeshBuilder();
        MeshBuilder tracks = new MeshBuilder();
        MeshBuilder markings = new MeshBuilder();
        MeshBuilder surfaces = new MeshBuilder();

        for (NauruMapData.Road road : compactRoads) {
            MeshBuilder target = road.material <= 1 || road.material >= 4 ? paved : tracks;
            float[] color = road.material == 6 ? new float[]{.38f, .40f, .39f}
                    : road.material >= 4 ? new float[]{.075f, .085f, .092f}
                    : road.material <= 1 ? new float[]{.105f, .115f, .12f}
                    : new float[]{.39f, .34f, .25f};
            for (int i = 0; i + 3 < road.points.length; i += 2) {
                float x0 = road.points[i], z0 = road.points[i + 1];
                float x1 = road.points[i + 2], z1 = road.points[i + 3];
                boolean shoulder = road.material <= 1 || road.material == 4 || road.material == 5;
                if (shoulder) {
                    addRibbon(shoulders, x0, z0, x1, z1,
                            road.width + (road.material == 4 ? 2.5f : 1.5f),
                            .35f, .32f, .24f, .025f);
                }
                addRibbon(target, x0, z0, x1, z1, road.width,
                        color[0], color[1], color[2], .05f);
                if (road.material == 4) addDashedLine(markings, x0, z0, x1, z1,
                        .42f, 5.5f, 4.5f, .93f, .93f, .88f);
                else if (road.material == 0) addDashedLine(markings, x0, z0, x1, z1,
                        .14f, 3.5f, 4f, .86f, .75f, .37f);
            }
        }
        for (NauruMapData.Area area : source.areas) {
            if (area.category != 6) continue;
            float[] polygon = compactGlobalPolygon(area.points);
            float[] centre = centroid(polygon);
            surfaces.addPolygonSurface(polygon, heightAt(centre[0], centre[1]) + .055f,
                    .10f, .11f, .115f);
        }
        shoulderMesh = shoulders.build();
        roadMesh = paved.build();
        trackMesh = tracks.build();
        markingMesh = markings.build();
        mappedSurfaceMesh = surfaces.build();
    }

    private void addRibbon(MeshBuilder builder, float x0, float z0, float x1, float z1,
                           float width, float r, float g, float b, float lift) {
        float dx = x1 - x0, dz = z1 - z0;
        float lengthSquared = dx * dx + dz * dz;
        if (lengthSquared < .0225f) return; // sub-15 cm GIS fragments add fill cost but no visible road.
        float inv = 1f / (float) Math.sqrt(lengthSquared);
        float sx = -dz * inv * width * .5f, sz = dx * inv * width * .5f;
        int a = builder.vertex(x0 + sx, heightAt(x0 + sx, z0 + sz) + lift, z0 + sz,
                0, 1, 0, r, g, b);
        int bb = builder.vertex(x1 + sx, heightAt(x1 + sx, z1 + sz) + lift, z1 + sz,
                0, 1, 0, r, g, b);
        int c = builder.vertex(x1 - sx, heightAt(x1 - sx, z1 - sz) + lift, z1 - sz,
                0, 1, 0, r, g, b);
        int d = builder.vertex(x0 - sx, heightAt(x0 - sx, z0 - sz) + lift, z0 - sz,
                0, 1, 0, r, g, b);
        builder.quad(a, bb, c, d);
    }

    private void addDashedLine(MeshBuilder builder, float x0, float z0, float x1, float z1,
                               float width, float dash, float gap, float r, float g, float b) {
        float dx = x1 - x0, dz = z1 - z0;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 1f) return;
        for (float start = 0; start < length; start += dash + gap) {
            float end = Math.min(length, start + dash);
            float t0 = start / length, t1 = end / length;
            addRibbon(builder, x0 + dx * t0, z0 + dz * t0,
                    x0 + dx * t1, z0 + dz * t1, width, r, g, b, .075f);
        }
    }

    private void buildInfrastructureLandscape() {
        MeshBuilder mining = new MeshBuilder();
        MeshBuilder solar = new MeshBuilder();
        Random random = new Random(0x4e41555255L);
        int solarIndex = 0;
        for (NauruMapData.Area area : source.areas) {
            if (area.category == 7) {
                if ((solarIndex++ & 1) != 0) continue;
                float[] panel = compactObjectPolygon(area.points, .72f, 1.7f, 4.8f);
                float[] centre = centroid(panel);
                solar.addPolygonSurface(panel, heightAt(centre[0], centre[1]) + .38f,
                        .045f, .13f, .18f);
                renderedSolarCount++;
                continue;
            }
            if (area.category != 3) continue;
            float realArea = Math.max(0, area.maxX - area.minX) * Math.max(0, area.maxZ - area.minZ);
            int requested = Math.min(14, Math.max(1, (int) (realArea / 75_000f)));
            for (int attempt = 0, placed = 0; attempt < requested * 8 && placed < requested; attempt++) {
                float rx = area.minX + random.nextFloat() * (area.maxX - area.minX);
                float rz = area.minZ + random.nextFloat() * (area.maxZ - area.minZ);
                if (!NauruMapData.contains(area.points, rx, rz)) continue;
                float x = game(rx), z = game(rz);
                mining.addPinnacle(x, heightAt(x, z), z,
                        .55f + random.nextFloat() * .8f, 1.3f + random.nextFloat() * 3f);
                placed++;
            }
        }
        float cantileverX = game(-2609.5f), cantileverZ = game(-697.9f);
        float base = heightAt(cantileverX, cantileverZ);
        mining.addBox(cantileverX, base, cantileverZ, 2.2f, 13f, 2.2f, 0,
                .40f, .31f, .20f);
        mining.addBox(cantileverX + 8f, base, cantileverZ - 1.5f, 2.2f, 11f, 2.2f, 0,
                .40f, .31f, .20f);
        mining.addBox(cantileverX + 4f, base + 9.5f, cantileverZ - .7f,
                10.5f, 1.5f, 1.8f, -.12f, .45f, .34f, .21f);
        solarMesh = solar.build();
        miningMesh = mining.build();
    }

    private void buildRepresentativeSectors() {
        LinkedHashMap<Long, SectorDraft> drafts = new LinkedHashMap<>();
        Set<Long> occupied = new HashSet<>();
        int accepted = 0;
        int[] passes = {2, 1, 3, 4, 0};
        for (int wantedCategory : passes) {
            int index = 0;
            for (NauruMapData.Building building : source.buildings) {
                int hash = index++ * 1103515245 + 12345;
                if (building.category != wantedCategory) continue;
                float[] realCentre = centroid(building.points);
                float x = game(realCentre[0]), z = game(realCentre[1]);
                if (!isWalkableLand(x, z)) continue;
                if (wantedCategory == 0 && accepted >= 310) break;
                int cellX = (int) Math.floor((x + 160f) / 6.2f);
                int cellZ = (int) Math.floor((z + 165f) / 6.2f);
                boolean essential = wantedCategory == 2 || wantedCategory == 4;
                if (!essential && isOccupiedNearby(occupied, cellX, cellZ,
                        wantedCategory == 1 || wantedCategory == 3 ? 1 : 0)) continue;
                if (wantedCategory == 0 && distanceToSegment(x, z,
                        RUNWAY_AX, RUNWAY_AZ, RUNWAY_BX, RUNWAY_BZ) < 12f) continue;
                occupied.add(cellKey(cellX, cellZ));

                SectorDraft draft = sectorFor(drafts, x, z);
                float base = heightAt(x, z);
                float[] footprint = compactObjectPolygon(building.points,
                        wantedCategory == 1 ? .48f : .62f, 3.2f,
                        wantedCategory == 1 ? 15f : 11f);
                float[] footprintBounds = bounds(footprint);
                float sizeX = Math.max(.3f, footprintBounds[2] - footprintBounds[0]);
                float sizeZ = Math.max(.3f, footprintBounds[3] - footprintBounds[1]);

                if (wantedCategory == 3) {
                    float radius = Math.max(.7f, Math.min(4f, (sizeX + sizeZ) * .25f));
                    draft.high.addCylinder(x, base, z, radius,
                            Math.max(3.5f, building.height * .72f), 14, .56f, .58f, .56f);
                    draft.low.addCylinder(x, base, z, radius,
                            Math.max(3.5f, building.height * .72f), 8, .49f, .51f, .50f);
                } else if (wantedCategory == 4) {
                    draft.high.addCylinder(x, base, z, .12f,
                            Math.max(5f, building.height), 7, .35f, .38f, .37f);
                    draft.low.addCylinder(x, base, z, .11f,
                            Math.max(5f, building.height), 5, .31f, .33f, .32f);
                } else {
                    float[][] palette = {{.72f,.64f,.50f},{.68f,.73f,.65f},{.72f,.53f,.42f},
                            {.54f,.68f,.68f},{.79f,.74f,.62f},{.68f,.67f,.60f}};
                    float[] color = wantedCategory == 1 ? new float[]{.49f,.52f,.50f}
                            : wantedCategory == 2 ? new float[]{.74f,.72f,.64f}
                            : palette[(hash >>> 16 & 0x7fffffff) % palette.length];
                    float height = Math.max(3.4f, Math.min(12f, building.height));
                    boolean facade = wantedCategory == 2 || (hash & 1) == 0;
                    draft.high.addFootprintBuilding(footprint, base, height,
                            color[0], color[1], color[2], facade);
                    draft.low.addBox(x, base, z, Math.max(2.2f, sizeX), height,
                            Math.max(2.2f, sizeZ), 0,
                            color[0] * .88f, color[1] * .88f, color[2] * .88f);
                }
                accepted++;
            }
        }
        renderedBuildingCount = accepted;

        Random random = new Random(674201L);
        int vegetation = 0;
        for (int attempt = 0; attempt < 2800 && vegetation < 185; attempt++) {
            float x = -122f + random.nextFloat() * 244f;
            float z = -132f + random.nextFloat() * 235f;
            if (!isWalkableLand(x, z) || distanceToSegment(x, z,
                    RUNWAY_AX, RUNWAY_AZ, RUNWAY_BX, RUNWAY_BZ) < 10f) continue;
            int cellX = (int) Math.floor((x + 160f) / 6.2f);
            int cellZ = (int) Math.floor((z + 165f) / 6.2f);
            if (isOccupiedNearby(occupied, cellX, cellZ, 0)) continue;
            int area = areaCategoryAtReal(real(x), real(z));
            if (area == 0 || area == 3 || area == 4 || area == 6 || area == 7) continue;
            if (area == 5 && random.nextFloat() < .72f) continue;
            SectorDraft draft = sectorFor(drafts, x, z);
            float y = heightAt(x, z), scale = .70f + random.nextFloat() * .38f;
            boolean palm = distanceToCoast(x, z) < 15f || random.nextFloat() < .62f;
            if (palm) draft.high.addPalm(x, y, z, 6.5f + random.nextFloat() * 4.5f, scale);
            else draft.high.addTropicalTree(x, y, z, 6.5f + random.nextFloat() * 4f, scale);
            occupied.add(cellKey(cellX, cellZ));
            draft.low.addCylinder(x, y, z, .16f, 5f, 5, .27f, .23f, .13f);
            draft.low.addEllipsoid(x, y + 5.4f, z, 1.7f * scale, 1.2f * scale,
                    1.7f * scale, 4, 6, .09f, .33f, .12f);
            vegetation++;
        }
        for (SectorDraft draft : drafts.values()) {
            sectors.add(new Sector(draft.x, draft.z, draft.high.build(), draft.low.build()));
        }
    }

    private boolean isOccupiedNearby(Set<Long> occupied, int cellX, int cellZ, int radius) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (occupied.contains(cellKey(cellX + dx, cellZ + dz))) return true;
            }
        }
        return false;
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private SectorDraft sectorFor(Map<Long, SectorDraft> drafts, float x, float z) {
        final float size = 44f;
        int cellX = (int) Math.floor((x + 176f) / size);
        int cellZ = (int) Math.floor((z + 176f) / size);
        long key = cellKey(cellX, cellZ);
        SectorDraft draft = drafts.get(key);
        if (draft == null) {
            draft = new SectorDraft(-176f + (cellX + .5f) * size,
                    -176f + (cellZ + .5f) * size);
            drafts.put(key, draft);
        }
        return draft;
    }

    private int areaCategoryAtReal(float x, float z) {
        int result = -1;
        for (NauruMapData.Area area : source.areas) {
            if (x < area.minX || x > area.maxX || z < area.minZ || z > area.maxZ) continue;
            if (!NauruMapData.contains(area.points, x, z)) continue;
            if (area.category == 0 || area.category == 6 || area.category == 7) return area.category;
            if (area.category == 3 || result < 0) result = area.category;
            else if (area.category == 4 && result != 3) result = 4;
            else if (result != 3 && result != 4) result = area.category;
        }
        return result;
    }

    private float distanceToCoast(float x, float z) {
        float best = Float.MAX_VALUE;
        for (int i = 0; i < compactCoastline.length; i += 2) {
            int j = (i + 2) % compactCoastline.length;
            best = Math.min(best, distanceToSegment(x, z,
                    compactCoastline[i], compactCoastline[i + 1],
                    compactCoastline[j], compactCoastline[j + 1]));
        }
        return best;
    }

    private static float[] compactGlobalPolygon(float[] realPoints) {
        float[] result = new float[realPoints.length];
        for (int i = 0; i < realPoints.length; i++) result[i] = game(realPoints[i]);
        return result;
    }

    /** Compresses locations but keeps an object's local footprint at believable human scale. */
    private static float[] compactObjectPolygon(float[] realPoints, float localScale,
                                                float minimumSize, float maximumSize) {
        float[] realCentre = centroid(realPoints);
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < realPoints.length; i += 2) {
            minX = Math.min(minX, realPoints[i]); maxX = Math.max(maxX, realPoints[i]);
            minZ = Math.min(minZ, realPoints[i + 1]); maxZ = Math.max(maxZ, realPoints[i + 1]);
        }
        float largest = Math.max(maxX - minX, maxZ - minZ);
        float smallest = Math.max(.1f, Math.min(maxX - minX, maxZ - minZ));
        float scale = localScale;
        if (largest * scale > maximumSize) scale = maximumSize / largest;
        if (smallest * scale < minimumSize) scale = Math.min(maximumSize / largest, minimumSize / smallest);
        float centreX = game(realCentre[0]), centreZ = game(realCentre[1]);
        float[] result = new float[realPoints.length];
        for (int i = 0; i < realPoints.length; i += 2) {
            result[i] = centreX + (realPoints[i] - realCentre[0]) * scale;
            result[i + 1] = centreZ + (realPoints[i + 1] - realCentre[1]) * scale;
        }
        return result;
    }

    private static float distanceToSegment(float x, float z, float x0, float z0,
                                           float x1, float z1) {
        float dx = x1 - x0, dz = z1 - z0;
        float denominator = dx * dx + dz * dz;
        float t = denominator < .0001f ? 0f
                : Math.max(0f, Math.min(1f, ((x - x0) * dx + (z - z0) * dz) / denominator));
        return (float) Math.hypot(x - (x0 + dx * t), z - (z0 + dz * t));
    }

    private static float[] centroid(float[] points) {
        float x = 0, z = 0;
        int count = points.length / 2;
        for (int i = 0; i < points.length; i += 2) { x += points[i]; z += points[i + 1]; }
        return new float[]{x / Math.max(1, count), z / Math.max(1, count)};
    }

    private static float[] bounds(float[] points) {
        float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < points.length; i += 2) {
            minX = Math.min(minX, points[i]); maxX = Math.max(maxX, points[i]);
            minZ = Math.min(minZ, points[i + 1]); maxZ = Math.max(maxZ, points[i + 1]);
        }
        return new float[]{minX, minZ, maxX, maxZ};
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = Math.max(0f, Math.min(1f, (value - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    public void invalidateGl() {
        oceanMesh.invalidateGl(); terrainMesh.invalidateGl(); shoulderMesh.invalidateGl();
        roadMesh.invalidateGl(); trackMesh.invalidateGl(); markingMesh.invalidateGl();
        mappedSurfaceMesh.invalidateGl(); solarMesh.invalidateGl(); miningMesh.invalidateGl();
        for (Sector sector : sectors) sector.invalidateGl();
    }

    public void drawOcean() { oceanMesh.draw(); }

    public void drawLand(float cameraX, float cameraZ, int quality, int materialUniform) {
        setMaterial(materialUniform, 0); terrainMesh.draw();
        setMaterial(materialUniform, 0); trackMesh.draw();
        setMaterial(materialUniform, 0); shoulderMesh.draw();
        setMaterial(materialUniform, 1); roadMesh.draw();
        setMaterial(materialUniform, 1); mappedSurfaceMesh.draw();
        setMaterial(materialUniform, 2); markingMesh.draw();
        setMaterial(materialUniform, 5); solarMesh.draw();
        setMaterial(materialUniform, 4); miningMesh.draw();

        setMaterial(materialUniform, 3);
        visibleSectors = 0;
        float highRange = quality == 0 ? 42f : quality == 1 ? 62f : 82f;
        float lowRange = quality == 0 ? 82f : quality == 1 ? 112f : 150f;
        for (Sector sector : sectors) {
            float distance = (float) Math.hypot(sector.x - cameraX, sector.z - cameraZ);
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

    public int getVisibleSectors() { return visibleSectors; }

    public String locationName(float x, float z) {
        if (distanceToSegment(x, z, RUNWAY_AX, RUNWAY_AZ, RUNWAY_BX, RUNWAY_BZ) < 12f) {
            return "Nauru International Airport";
        }
        NauruMapData.Place nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        for (NauruMapData.Place place : source.places) {
            if (place.category != 0) continue;
            float distance = (float) Math.hypot(game(place.x) - x, game(place.z) - z);
            if (distance < nearestDistance) { nearest = place; nearestDistance = distance; }
        }
        return nearest == null ? "Nauru" : nearest.name + " District";
    }

    public float[] spawnPoint() {
        float x = game(-1757.95f), z = game(-2128.36f);
        return new float[]{x, heightAt(x, z), z};
    }

    public float[] vehicleSpawnPoint() {
        float x = game(-1757.95f) + 5.2f, z = game(-2128.36f) + 1.1f;
        if (!isWalkableLand(x, z)) { x = game(-1752.38f); z = game(-2122.80f); }
        return new float[]{x, heightAt(x, z) + .15f, z};
    }

    public float runwayYaw() {
        return (float) Math.atan2(RUNWAY_BX - RUNWAY_AX, RUNWAY_BZ - RUNWAY_AZ);
    }

    public String scaleReadout() { return "1:25 island • human-scale gameplay"; }

    public float[] coastlineCoordinates() { return compactCoastline; }
    public List<NauruMapData.Road> mapRoads() { return compactRoads; }
    public int mappedBuildingCount() { return source.buildings.size(); }
    public int renderedBuildingCount() { return renderedBuildingCount; }
    public int mappedAreaCount() { return source.areas.size(); }
    public int renderedSolarCount() { return renderedSolarCount; }
    public int mappedPlaceCount() { return source.places.size(); }
    public float minimapRadiusMetres() { return 145f; }

    private static final class SectorDraft {
        final float x, z;
        final MeshBuilder high = new MeshBuilder();
        final MeshBuilder low = new MeshBuilder();
        SectorDraft(float x, float z) { this.x = x; this.z = z; }
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
