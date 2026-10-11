package ai.arena.nauru.world;

import android.content.Context;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ai.arena.nauru.R;

/** Compact, offline representation of open Nauru GIS data packaged in the APK. */
public final class NauruMapData {
    private static final int MAX_FEATURES = 100_000;
    private static final double METRES_PER_DEGREE = 111_319.49079327358;

    public final double originLatitude;
    public final double originLongitude;
    public final float[] coastline;
    public final List<Road> roads;
    public final List<Building> buildings;
    public final List<Area> areas;
    public final List<Place> places;

    private final int demWidth;
    private final int demHeight;
    private final double demWest;
    private final double demSouth;
    private final double demEast;
    private final double demNorth;
    private final short[] elevations;
    private final double longitudeScale;

    private NauruMapData(double originLatitude, double originLongitude, float[] coastline,
                         List<Road> roads, List<Building> buildings, List<Area> areas,
                         List<Place> places, Dem dem) {
        this.originLatitude = originLatitude;
        this.originLongitude = originLongitude;
        this.coastline = coastline;
        this.roads = Collections.unmodifiableList(roads);
        this.buildings = Collections.unmodifiableList(buildings);
        this.areas = Collections.unmodifiableList(areas);
        this.places = Collections.unmodifiableList(places);
        demWidth = dem.width;
        demHeight = dem.height;
        demWest = dem.west;
        demSouth = dem.south;
        demEast = dem.east;
        demNorth = dem.north;
        elevations = dem.elevations;
        longitudeScale = METRES_PER_DEGREE * Math.cos(Math.toRadians(originLatitude));
    }

    public static NauruMapData load(Context context) {
        try {
            Dem dem = loadDem(context);
            try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                    context.getResources().openRawResource(R.raw.nauru_map)))) {
                if (input.readInt() != 0x4e4d4150) throw new IOException("Invalid Nauru map magic"); // NMAP
                int version = input.readInt();
                if (version != 1) throw new IOException("Unsupported Nauru map version " + version);
                double originLatitude = input.readDouble();
                double originLongitude = input.readDouble();
                int coastCount = checkedCount(input.readInt(), "coast points");
                int roadCount = checkedCount(input.readInt(), "roads");
                int buildingCount = checkedCount(input.readInt(), "buildings");
                int areaCount = checkedCount(input.readInt(), "areas");
                int placeCount = checkedCount(input.readInt(), "places");

                float[] coast = readCoordinates(input, coastCount);
                ArrayList<Road> roads = new ArrayList<>(roadCount);
                for (int i = 0; i < roadCount; i++) {
                    int material = input.readUnsignedByte();
                    float width = input.readFloat();
                    roads.add(new Road(material, width, readCoordinates(input, input.readUnsignedShort())));
                }
                ArrayList<Building> buildings = new ArrayList<>(buildingCount);
                for (int i = 0; i < buildingCount; i++) {
                    int category = input.readUnsignedByte();
                    float height = input.readFloat();
                    buildings.add(new Building(category, height,
                            readCoordinates(input, input.readUnsignedShort())));
                }
                ArrayList<Area> areas = new ArrayList<>(areaCount);
                for (int i = 0; i < areaCount; i++) {
                    int category = input.readUnsignedByte();
                    areas.add(new Area(category, readCoordinates(input, input.readUnsignedShort())));
                }
                ArrayList<Place> places = new ArrayList<>(placeCount);
                for (int i = 0; i < placeCount; i++) {
                    int category = input.readUnsignedByte();
                    float x = input.readFloat();
                    float z = input.readFloat();
                    int length = input.readUnsignedShort();
                    byte[] encoded = new byte[length];
                    input.readFully(encoded);
                    places.add(new Place(category, x, z,
                            new String(encoded, java.nio.charset.StandardCharsets.UTF_8)));
                }
                return new NauruMapData(originLatitude, originLongitude, coast,
                        roads, buildings, areas, places, dem);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load packaged Nauru geodata", error);
        }
    }

    private static Dem loadDem(Context context) throws IOException {
        byte[] bytes;
        try (java.io.InputStream source = context.getResources().openRawResource(R.raw.nauru_dem_30m)) {
            bytes = readAll(source);
        }
        ByteBuffer input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (input.remaining() < 48 || input.getInt() != 0x4d45444e) { // bytes NDEM as LE int
            throw new IOException("Invalid Nauru elevation magic");
        }
        int version = input.getInt();
        if (version != 1) throw new IOException("Unsupported elevation version " + version);
        int width = input.getInt();
        int height = input.getInt();
        double west = input.getDouble();
        double south = input.getDouble();
        double east = input.getDouble();
        double north = input.getDouble();
        long sampleCount = (long) width * height;
        if (width < 2 || height < 2 || sampleCount > 4_000_000L
                || input.remaining() != sampleCount * 2L) {
            throw new IOException("Invalid elevation dimensions " + width + "x" + height);
        }
        short[] elevations = new short[(int) sampleCount];
        input.asShortBuffer().get(elevations);
        return new Dem(width, height, west, south, east, north, elevations);
    }

    private static byte[] readAll(java.io.InputStream source) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[16_384];
        int read;
        while ((read = source.read(buffer)) >= 0) output.write(buffer, 0, read);
        return output.toByteArray();
    }

    private static int checkedCount(int count, String label) throws IOException {
        if (count < 0 || count > MAX_FEATURES) throw new IOException("Invalid " + label + ": " + count);
        return count;
    }

    private static float[] readCoordinates(DataInputStream input, int pointCount) throws IOException {
        if (pointCount < 0 || pointCount > 65_535) throw new IOException("Invalid point count");
        float[] points = new float[pointCount * 2];
        for (int i = 0; i < points.length; i++) points[i] = input.readFloat();
        return points;
    }

    /** Bilinear 30 m SRTM sample in local game coordinates. Includes real offshore bathymetry. */
    public float elevationAt(float x, float z) {
        double latitude = originLatitude + z / METRES_PER_DEGREE;
        double longitude = originLongitude + x / longitudeScale;
        double gridX = (longitude - demWest) / (demEast - demWest) * (demWidth - 1);
        double gridY = (latitude - demSouth) / (demNorth - demSouth) * (demHeight - 1);
        gridX = Math.max(0, Math.min(demWidth - 1.001, gridX));
        gridY = Math.max(0, Math.min(demHeight - 1.001, gridY));
        int x0 = (int) gridX, y0 = (int) gridY;
        int x1 = Math.min(demWidth - 1, x0 + 1), y1 = Math.min(demHeight - 1, y0 + 1);
        float fx = (float) (gridX - x0), fy = (float) (gridY - y0);
        float a = elevations[y0 * demWidth + x0] * (1 - fx) + elevations[y0 * demWidth + x1] * fx;
        float b = elevations[y1 * demWidth + x0] * (1 - fx) + elevations[y1 * demWidth + x1] * fx;
        return a * (1 - fy) + b * fy;
    }

    public boolean containsLand(float x, float z) {
        return contains(coastline, x, z);
    }

    public static boolean contains(float[] polygon, float x, float z) {
        boolean inside = false;
        int points = polygon.length / 2;
        for (int i = 0, j = points - 1; i < points; j = i++) {
            float xi = polygon[i * 2], zi = polygon[i * 2 + 1];
            float xj = polygon[j * 2], zj = polygon[j * 2 + 1];
            if ((zi > z) != (zj > z)
                    && x < (xj - xi) * (z - zi) / ((zj - zi) + 0.000001f) + xi) {
                inside = !inside;
            }
        }
        return inside;
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

    public static final class Road {
        public final int material;
        public final float width;
        public final float[] points;
        public final float minX, minZ, maxX, maxZ;
        Road(int material, float width, float[] points) {
            this.material = material; this.width = width; this.points = points;
            float[] bounds = bounds(points);
            minX = bounds[0]; minZ = bounds[1]; maxX = bounds[2]; maxZ = bounds[3];
        }
    }

    public static final class Building {
        public final int category;
        public final float height;
        public final float[] points;
        Building(int category, float height, float[] points) {
            this.category = category; this.height = height; this.points = points;
        }
    }

    public static final class Area {
        public final int category;
        public final float[] points;
        public final float minX, minZ, maxX, maxZ;
        Area(int category, float[] points) {
            this.category = category; this.points = points;
            float[] bounds = bounds(points);
            minX = bounds[0]; minZ = bounds[1]; maxX = bounds[2]; maxZ = bounds[3];
        }
    }

    public static final class Place {
        public final int category;
        public final float x;
        public final float z;
        public final String name;
        Place(int category, float x, float z, String name) {
            this.category = category; this.x = x; this.z = z; this.name = name;
        }
    }

    private static final class Dem {
        final int width, height;
        final double west, south, east, north;
        final short[] elevations;
        Dem(int width, int height, double west, double south, double east, double north,
            short[] elevations) {
            this.width = width; this.height = height;
            this.west = west; this.south = south; this.east = east; this.north = north;
            this.elevations = elevations;
        }
    }
}
