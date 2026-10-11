package ai.arena.nauru.world;

import android.content.Context;
import android.opengl.GLES20;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import ai.arena.nauru.graphics.Mesh;
import ai.arena.nauru.graphics.MeshBuilder;

/**
 * Offline, map-accurate Nauru world generated from packaged OSM and SRTM data.
 * One OpenGL world unit is one metre; +X is east and +Z is north.
 */
public final class NauruWorld {
    public static final float OCEAN_LEVEL = 0f;
    private static final float RUNWAY_AX = -2259.713f;
    private static final float RUNWAY_AZ = -1823.714f;
    private static final float RUNWAY_BX = -730.693f;
    private static final float RUNWAY_BZ = -3125.139f;
    private static final float RUNWAY_ELEVATION = 7.2f;

    private final NauruMapData map;
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

    public NauruWorld(Context context) {
        map = NauruMapData.load(context.getApplicationContext());
        buildWorld();
    }

    private void buildWorld() {
        oceanMesh = buildOceanAndLagoon();
        terrainMesh = buildTerrain();
        buildMappedRoadsAndAirport();
        buildMappedLandscape();
        buildMappedSectors();
    }

    public float heightAt(float x, float z) {
        float height = map.elevationAt(x, z);
        if (map.containsLand(x, z)) height = Math.max(.45f, height);
        float runwayDistance = distanceToSegment(x, z, RUNWAY_AX, RUNWAY_AZ, RUNWAY_BX, RUNWAY_BZ);
        if (runwayDistance < 150f) {
            float blend = smoothStep(45f, 150f, runwayDistance);
            height = RUNWAY_ELEVATION * (1f - blend) + height * blend;
        }
        return height;
    }

    public boolean isWalkableLand(float x, float z) {
        return map.containsLand(x, z) && areaCategoryAt(x, z) != 0 && heightAt(x, z) > .15f;
    }

    public boolean isNearMappedRoad(float x, float z) {
        for (NauruMapData.Road road : map.roads) {
            if (road.material >= 3) continue;
            float margin = road.width * .5f + 4f;
            if (x < road.minX - margin || x > road.maxX + margin
                    || z < road.minZ - margin || z > road.maxZ + margin) continue;
            for (int i = 0; i + 3 < road.points.length; i += 2) {
                if (distanceToSegment(x, z, road.points[i], road.points[i + 1],
                        road.points[i + 2], road.points[i + 3]) <= margin) return true;
            }
        }
        return false;
    }

    private Mesh buildOceanAndLagoon() {
        MeshBuilder builder = new MeshBuilder();
        int grid = 40;
        float extent = 10_500f;
        int[][] ids = new int[grid + 1][grid + 1];
        for (int z = 0; z <= grid; z++) {
            for (int x = 0; x <= grid; x++) {
                float px = -extent + x * extent * 2f / grid;
                float pz = -extent + z * extent * 2f / grid;
                ids[z][x] = builder.vertex(px, OCEAN_LEVEL, pz, 0, 1, 0, .03f, .35f, .44f);
            }
        }
        for (int z = 0; z < grid; z++) {
            for (int x = 0; x < grid; x++) {
                builder.quad(ids[z][x], ids[z][x + 1], ids[z + 1][x + 1], ids[z + 1][x]);
            }
        }
        for (NauruMapData.Area area : map.areas) {
            if (area.category != 0) continue;
            float[] centre = centroid(area.points);
            float waterHeight = Math.max(.25f, map.elevationAt(centre[0], centre[1]) + .12f);
            builder.addPolygonSurface(area.points, waterHeight, .035f, .31f, .34f);
        }
        return builder.build();
    }

    private Mesh buildTerrain() {
        final int countX = 161, countZ = 161;
        final float minX = -3500f, maxX = 3650f, minZ = -3800f, maxZ = 3150f;
        MeshBuilder builder = new MeshBuilder();
        int[][] ids = new int[countZ][countX];
        for (int iz = 0; iz < countZ; iz++) {
            float z = minZ + (maxZ - minZ) * iz / (countZ - 1f);
            for (int ix = 0; ix < countX; ix++) {
                float x = minX + (maxX - minX) * ix / (countX - 1f);
                float y = heightAt(x, z);
                float sample = 16f;
                float nx = heightAt(x - sample, z) - heightAt(x + sample, z);
                float nz = heightAt(x, z - sample) - heightAt(x, z + sample);
                float inv = 1f / Math.max(.001f,
                        (float) Math.sqrt(nx * nx + sample * sample * 4f + nz * nz));
                nx *= inv; float ny = sample * 2f * inv; nz *= inv;
                boolean land = map.containsLand(x, z);
                int area = land ? areaCategoryAt(x, z) : -1;
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
        float variation = ((float) Math.sin(x * .013f + z * .021f)
                + (float) Math.sin(z * .037f - x * .017f)) * .018f;
        if (!land) {
            if (height > -18f) return new float[]{.55f + variation, .55f + variation, .43f + variation};
            return new float[]{.20f, .28f, .29f};
        }
        switch (area) {
            case 0: return new float[]{.38f, .43f, .30f};
            case 1: return new float[]{.12f + variation, .36f + variation, .13f};
            case 2: return new float[]{.20f + variation, .45f + variation, .18f};
            case 3: return new float[]{.55f + variation, .50f + variation, .38f + variation};
            case 4: return new float[]{.76f + variation, .69f + variation, .49f + variation};
            case 5: return new float[]{.27f + variation, .47f + variation, .24f};
            case 6: return new float[]{.12f, .13f, .135f};
            case 7: return new float[]{.24f, .37f, .22f};
            default:
                if (height > 28f) return new float[]{.43f + variation, .42f + variation, .31f};
                return new float[]{.19f + variation, .44f + variation, .18f};
        }
    }

    private void buildMappedRoadsAndAirport() {
        MeshBuilder shoulders = new MeshBuilder();
        MeshBuilder paved = new MeshBuilder();
        MeshBuilder tracks = new MeshBuilder();
        MeshBuilder markings = new MeshBuilder();
        MeshBuilder mappedSurfaces = new MeshBuilder();

        for (NauruMapData.Road road : map.roads) {
            MeshBuilder target = road.material <= 1 || road.material >= 4 ? paved : tracks;
            float[] roadColor = road.material == 6
                    ? new float[]{.38f, .40f, .39f}
                    : road.material >= 4
                    ? new float[]{.085f, .095f, .10f}
                    : road.material <= 1
                    ? new float[]{.115f, .125f, .13f}
                    : new float[]{.39f, .34f, .25f};
            for (int i = 0; i + 3 < road.points.length; i += 2) {
                float x0 = road.points[i], z0 = road.points[i + 1];
                float x1 = road.points[i + 2], z1 = road.points[i + 3];
                boolean hasShoulder = road.material <= 1 || road.material == 4 || road.material == 5;
                if (hasShoulder) {
                    addRibbonSegment(shoulders, x0, z0, x1, z1,
                            road.width + (road.material == 4 ? 5f : 2.2f),
                            .36f, .33f, .25f, .08f);
                }
                addRibbonSegment(target, x0, z0, x1, z1, road.width,
                        roadColor[0], roadColor[1], roadColor[2], .14f);
                if (road.material == 4) {
                    addDashedMarkings(markings, x0, z0, x1, z1);
                } else if (road.material == 0 && road.width >= 7f) {
                    addRoadCentreMarkings(markings, x0, z0, x1, z1);
                }
            }
        }
        for (NauruMapData.Area area : map.areas) {
            if (area.category != 6) continue;
            float[] centre = centroid(area.points);
            mappedSurfaces.addPolygonSurface(area.points, heightAt(centre[0], centre[1]) + .16f,
                    .105f, .115f, .12f);
        }
        shoulderMesh = shoulders.build();
        roadMesh = paved.build();
        trackMesh = tracks.build();
        markingMesh = markings.build();
        mappedSurfaceMesh = mappedSurfaces.build();
    }

    private void addRibbonSegment(MeshBuilder builder, float x0, float z0, float x1, float z1,
                                  float width, float r, float g, float b, float lift) {
        float dx = x1 - x0, dz = z1 - z0;
        float inv = 1f / Math.max(.001f, (float) Math.sqrt(dx * dx + dz * dz));
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

    private void addDashedMarkings(MeshBuilder builder, float x0, float z0, float x1, float z1) {
        float dx = x1 - x0, dz = z1 - z0;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 1f) return;
        int segments = Math.max(1, (int) (length / 34f));
        for (int i = 0; i < segments; i += 2) {
            float t0 = i / (float) segments;
            float t1 = Math.min(1f, (i + .72f) / segments);
            addRibbonSegment(builder, x0 + dx * t0, z0 + dz * t0,
                    x0 + dx * t1, z0 + dz * t1, .72f, .93f, .93f, .88f, .23f);
        }
    }

    private void addRoadCentreMarkings(MeshBuilder builder, float x0, float z0,
                                       float x1, float z1) {
        float dx = x1 - x0, dz = z1 - z0;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        if (length < 5f) return;
        for (float start = 0; start < length; start += 14f) {
            float end = Math.min(length, start + 6.5f);
            float t0 = start / length, t1 = end / length;
            addRibbonSegment(builder, x0 + dx * t0, z0 + dz * t0,
                    x0 + dx * t1, z0 + dz * t1,
                    .18f, .86f, .76f, .38f, .22f);
        }
    }

    private void buildMappedLandscape() {
        MeshBuilder mining = new MeshBuilder();
        MeshBuilder solar = new MeshBuilder();
        Random random = new Random(0x4e41555255L);
        int pinnacles = 0;
        for (NauruMapData.Area area : map.areas) {
            if (area.category == 7) {
                float[] centre = centroid(area.points);
                solar.addPolygonSurface(area.points, heightAt(centre[0], centre[1]) + .72f,
                        .055f, .15f, .20f);
                continue;
            }
            if (area.category != 3) continue;
            float boxArea = Math.max(0, area.maxX - area.minX) * Math.max(0, area.maxZ - area.minZ);
            int requested = Math.min(55, Math.max(1, (int) (boxArea / 22_000f)));
            for (int attempt = 0, placed = 0; attempt < requested * 8 && placed < requested; attempt++) {
                float x = area.minX + random.nextFloat() * (area.maxX - area.minX);
                float z = area.minZ + random.nextFloat() * (area.maxZ - area.minZ);
                if (!NauruMapData.contains(area.points, x, z) || !map.containsLand(x, z)) continue;
                float radius = 1.1f + random.nextFloat() * 2.4f;
                mining.addPinnacle(x, heightAt(x, z), z, radius, 2.8f + random.nextFloat() * 7f);
                placed++; pinnacles++;
            }
        }
        // Mapped Aiwo cantilever area receives recognizable industrial silhouettes.
        float cantileverX = -2609.5f, cantileverZ = -697.9f;
        float base = heightAt(cantileverX, cantileverZ);
        mining.addBox(cantileverX, base, cantileverZ, 7f, 26f, 7f, 0,
                .40f, .31f, .20f);
        mining.addBox(cantileverX + 47f, base, cantileverZ - 7f, 7f, 23f, 7f, 0,
                .40f, .31f, .20f);
        mining.addBox(cantileverX + 23f, base + 20f, cantileverZ - 3f,
                55f, 4f, 6f, -.12f, .45f, .34f, .21f);
        solarMesh = solar.build();
        miningMesh = mining.build();
    }

    private void buildMappedSectors() {
        LinkedHashMap<Long, SectorDraft> drafts = new LinkedHashMap<>();
        int buildingIndex = 0;
        for (NauruMapData.Building building : map.buildings) {
            float[] centre = centroid(building.points);
            if (!map.containsLand(centre[0], centre[1])) continue;
            SectorDraft draft = sectorFor(drafts, centre[0], centre[1]);
            float base = heightAt(centre[0], centre[1]);
            int hash = buildingIndex++ * 1103515245 + 12345;
            float[][] residential = {{.72f,.64f,.50f},{.68f,.73f,.65f},{.72f,.53f,.42f},
                    {.54f,.68f,.68f},{.79f,.74f,.62f},{.68f,.67f,.60f}};
            float[] bounds = bounds(building.points);
            float sizeX = Math.max(.35f, bounds[2] - bounds[0]);
            float sizeZ = Math.max(.35f, bounds[3] - bounds[1]);
            if (building.category == 3) {
                float radius = Math.max(.55f, (sizeX + sizeZ) * .25f);
                draft.high.addCylinder(centre[0], base, centre[1], radius,
                        building.height, 14, .56f, .58f, .56f);
                draft.low.addCylinder(centre[0], base, centre[1], radius,
                        building.height, 8, .49f, .51f, .50f);
                continue;
            }
            if (building.category == 4) {
                float radius = Math.max(.11f, Math.min(.32f, (sizeX + sizeZ) * .22f));
                draft.high.addCylinder(centre[0], base, centre[1], radius,
                        building.height, 8, .35f, .38f, .37f);
                draft.low.addCylinder(centre[0], base, centre[1], radius,
                        building.height, 5, .31f, .33f, .32f);
                continue;
            }
            float[] color;
            if (building.category == 1) color = new float[]{.49f, .52f, .50f};
            else if (building.category == 2) color = new float[]{.74f, .72f, .64f};
            else color = residential[(hash >>> 16 & 0x7fffffff) % residential.length];
            boolean facadeDetails = building.category == 2 || (hash & 3) != 0;
            draft.high.addFootprintBuilding(building.points, base, building.height,
                    color[0], color[1], color[2], facadeDetails);
            draft.low.addBox((bounds[0] + bounds[2]) * .5f, base,
                    (bounds[1] + bounds[3]) * .5f,
                    Math.max(2f, sizeX), building.height,
                    Math.max(2f, sizeZ), 0,
                    color[0] * .88f, color[1] * .88f, color[2] * .88f);
        }

        Random random = new Random(674201L);
        int vegetation = 0;
        for (int attempt = 0; attempt < 5000 && vegetation < 460; attempt++) {
            float x = -2850f + random.nextFloat() * 6000f;
            float z = -3350f + random.nextFloat() * 6100f;
            if (!map.containsLand(x, z) || distanceToSegment(x, z,
                    RUNWAY_AX, RUNWAY_AZ, RUNWAY_BX, RUNWAY_BZ) < 175f) continue;
            int area = areaCategoryAt(x, z);
            if (area == 0 || area == 3 || area == 4 || area == 6 || area == 7) continue;
            if (area == 5 && random.nextFloat() < .72f) continue;
            float coastDistance = distanceToCoast(x, z);
            if (area != 1 && area != 2 && coastDistance > 420f && random.nextFloat() < .62f) continue;
            SectorDraft draft = sectorFor(drafts, x, z);
            float y = heightAt(x, z);
            float scale = .74f + random.nextFloat() * .48f;
            boolean palm = coastDistance < 360f || random.nextFloat() < .68f;
            if (palm) {
                draft.high.addPalm(x, y, z, 7.5f + random.nextFloat() * 6f, scale);
            } else {
                draft.high.addTropicalTree(x, y, z, 7f + random.nextFloat() * 5f, scale);
            }
            draft.low.addCylinder(x, y, z, .18f, 5.8f, 5, .28f, .23f, .13f);
            draft.low.addEllipsoid(x, y + 6.1f, z, 2.0f * scale, 1.35f * scale,
                    2.0f * scale, 4, 6, .09f, .34f, .12f);
            vegetation++;
        }
        for (SectorDraft draft : drafts.values()) {
            sectors.add(new Sector(draft.x, draft.z, draft.high.build(), draft.low.build()));
        }
    }

    private SectorDraft sectorFor(Map<Long, SectorDraft> drafts, float x, float z) {
        final float size = 850f;
        int cellX = (int) Math.floor((x + 4250f) / size);
        int cellZ = (int) Math.floor((z + 4250f) / size);
        long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
        SectorDraft draft = drafts.get(key);
        if (draft == null) {
            draft = new SectorDraft(-4250f + (cellX + .5f) * size,
                    -4250f + (cellZ + .5f) * size);
            drafts.put(key, draft);
        }
        return draft;
    }

    private int areaCategoryAt(float x, float z) {
        int result = -1;
        for (NauruMapData.Area area : map.areas) {
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
        float[] coast = map.coastline;
        for (int i = 0; i < coast.length; i += 2) {
            int j = (i + 2) % coast.length;
            best = Math.min(best, distanceToSegment(x, z,
                    coast[i], coast[i + 1], coast[j], coast[j + 1]));
        }
        return best;
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

    public void drawOcean() {
        oceanMesh.draw();
    }

    public void drawLand(float cameraX, float cameraZ, int quality, int materialUniform) {
        setMaterial(materialUniform, 0); terrainMesh.draw();
        setMaterial(materialUniform, 0); trackMesh.draw();
        setMaterial(materialUniform, 0); shoulderMesh.draw();
        setMaterial(materialUniform, 1); roadMesh.draw();
        setMaterial(materialUniform, 1); mappedSurfaceMesh.draw();
        setMaterial(materialUniform, 2); markingMesh.draw();
        setMaterial(materialUniform, 5); solarMesh.draw();

        if (Math.hypot(cameraX, cameraZ) < (quality == 0 ? 1750f : 3000f)) {
            setMaterial(materialUniform, 4); miningMesh.draw();
        }

        setMaterial(materialUniform, 3);
        visibleSectors = 0;
        float highRange = quality == 0 ? 620f : quality == 1 ? 920f : 1250f;
        float lowRange = quality == 0 ? 1300f : quality == 1 ? 1950f : 2700f;
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
        if (distanceToSegment(x, z, RUNWAY_AX, RUNWAY_AZ, RUNWAY_BX, RUNWAY_BZ) < 280f) {
            return "Nauru International Airport";
        }
        NauruMapData.Place nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        for (NauruMapData.Place place : map.places) {
            if (place.category != 0) continue;
            float distance = (float) Math.hypot(place.x - x, place.z - z);
            if (distance < nearestDistance) { nearest = place; nearestDistance = distance; }
        }
        return nearest == null ? "Nauru" : nearest.name + " District";
    }

    public float[] spawnPoint() {
        float x = -1757.95f, z = -2128.36f;
        return new float[]{x, heightAt(x, z), z};
    }

    public float[] vehicleSpawnPoint() {
        float x = -1752.38f, z = -2122.80f;
        return new float[]{x, heightAt(x, z) + .15f, z};
    }

    public float runwayYaw() {
        return (float) Math.atan2(RUNWAY_BX - RUNWAY_AX, RUNWAY_BZ - RUNWAY_AZ);
    }

    public String scaleReadout() {
        return String.format(Locale.US, "1 unit = 1 m • mapped %.1f km island", 5.7f);
    }

    public float[] coastlineCoordinates() { return map.coastline; }
    public List<NauruMapData.Road> mapRoads() { return map.roads; }
    public int mappedBuildingCount() { return map.buildings.size(); }
    public int mappedAreaCount() { return map.areas.size(); }
    public int mappedPlaceCount() { return map.places.size(); }

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
