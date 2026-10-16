package ai.arena.qatar.world;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;

/** Parser and random-access decoder for QTPK v1. */
public final class QatarPack implements AutoCloseable {
    public static final int FORMAT_VERSION = 1;
    public static final int EPSG_UTM_39N = 32639;
    private static final int HEADER_BYTES = 64;
    private static final int INDEX_ENTRY_BYTES = 32;

    public final int epsg;
    public final int chunkMetres;
    public final int maskDimension;
    public final int chunkCount;
    public final double originEasting;
    public final double originNorthing;
    public final double worldWidth;
    public final double worldHeight;
    public final String worldEpoch;
    public final byte[] sourceDigest;
    public final List<Municipality> municipalities;
    public final List<String> biomes;
    public final List<Hub> hubs;
    public final List<float[]> nationalPolygons;

    private final SeekableDataSource source;
    private final Map<Long, ChunkEntry> entries;

    public QatarPack(SeekableDataSource source) throws IOException {
        this.source = source;
        ByteBuffer header = little(source.readFully(0, HEADER_BYTES));
        requireMagic(header, 'Q', 'T', 'P', 'K');
        int version = header.getInt();
        if (version != FORMAT_VERSION) throw new IOException("Unsupported QTPK version " + version);
        epsg = header.getInt();
        chunkMetres = header.getInt();
        maskDimension = header.getInt();
        chunkCount = header.getInt();
        originEasting = header.getDouble();
        originNorthing = header.getDouble();
        worldWidth = header.getDouble();
        worldHeight = header.getDouble();
        int metadataBytes = header.getInt();
        int indexBytes = header.getInt();
        if (epsg != EPSG_UTM_39N) throw new IOException("Qatar pack CRS is not EPSG:32639");
        if (chunkMetres < 128 || chunkMetres > 8192 || maskDimension != 16) throw new IOException("Unsafe qpak dimensions");
        if (chunkCount <= 0 || chunkCount > 100_000 || indexBytes != chunkCount * INDEX_ENTRY_BYTES) throw new IOException("Invalid qpak index size");
        long firstBlock = HEADER_BYTES + (long) metadataBytes + indexBytes;
        if (metadataBytes <= 0 || firstBlock > source.size()) throw new IOException("QTPK metadata/index outside file");

        ByteBuffer metadata = little(source.readFully(HEADER_BYTES, metadataBytes));
        worldEpoch = readString(metadata);
        sourceDigest = new byte[32];
        metadata.get(sourceDigest);
        int municipalityCount = unsignedShort(metadata.getShort());
        List<Municipality> municipalityList = new ArrayList<>(municipalityCount);
        for (int i = 0; i < municipalityCount; i++) {
            municipalityList.add(new Municipality(i, readString(metadata), readString(metadata), readString(metadata)));
        }
        municipalities = Collections.unmodifiableList(municipalityList);
        int biomeCount = unsignedShort(metadata.getShort());
        List<String> biomeList = new ArrayList<>(biomeCount);
        for (int i = 0; i < biomeCount; i++) biomeList.add(readString(metadata));
        biomes = Collections.unmodifiableList(biomeList);
        int hubCount = unsignedShort(metadata.getShort());
        List<Hub> hubList = new ArrayList<>(hubCount);
        for (int i = 0; i < hubCount; i++) {
            String english = readString(metadata);
            String arabic = readString(metadata);
            String kind = readString(metadata);
            hubList.add(new Hub(i, english, arabic, kind, metadata.getDouble(), metadata.getDouble(), metadata.getDouble(), metadata.getDouble()));
        }
        hubs = Collections.unmodifiableList(hubList);
        int polygonCount = unsignedShort(metadata.getShort());
        List<float[]> polygons = new ArrayList<>(polygonCount);
        for (int i = 0; i < polygonCount; i++) {
            int points = metadata.getInt();
            if (points < 4 || points > 2_000_000 || points * 8L > metadata.remaining()) throw new IOException("Unsafe national polygon");
            float[] coordinates = new float[points * 2];
            for (int p = 0; p < coordinates.length; p++) coordinates[p] = metadata.getFloat();
            polygons.add(coordinates);
        }
        nationalPolygons = Collections.unmodifiableList(polygons);
        if (metadata.hasRemaining()) throw new IOException("Unexpected QTPK metadata tail " + metadata.remaining());

        ByteBuffer index = little(source.readFully(HEADER_BYTES + metadataBytes, indexBytes));
        Map<Long, ChunkEntry> entryMap = new HashMap<>(chunkCount * 2);
        for (int i = 0; i < chunkCount; i++) {
            int x = index.getInt();
            int y = index.getInt();
            long offset = index.getLong();
            int packedLength = index.getInt();
            int rawLength = index.getInt();
            long crc = Integer.toUnsignedLong(index.getInt());
            int municipality = Byte.toUnsignedInt(index.get());
            int biome = Byte.toUnsignedInt(index.get());
            int flags = Byte.toUnsignedInt(index.get());
            index.get();
            if (offset < firstBlock || packedLength <= 0 || rawLength <= 0 || offset + packedLength > source.size()) throw new IOException("Invalid chunk block bounds");
            if (municipality >= municipalities.size() || biome >= biomes.size()) throw new IOException("Invalid chunk classification");
            ChunkEntry value = new ChunkEntry(x, y, offset, packedLength, rawLength, crc, municipality, biome, flags);
            if (entryMap.put(key(x, y), value) != null) throw new IOException("Duplicate chunk " + x + "," + y);
        }
        entries = Collections.unmodifiableMap(entryMap);
    }

    public ChunkEntry entry(int x, int y) { return entries.get(key(x, y)); }
    public Map<Long, ChunkEntry> entries() { return entries; }

    public WorldChunk load(ChunkEntry entry) throws IOException {
        if (entry == null) throw new IOException("Missing required chunk");
        byte[] packed = source.readFully(entry.offset, entry.packedLength);
        ByteArrayOutputStream output = new ByteArrayOutputStream(entry.rawLength);
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(packed))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inflater.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, read);
                if (output.size() > entry.rawLength) throw new IOException("Inflated chunk exceeds declared size");
            }
        }
        byte[] raw = output.toByteArray();
        if (raw.length != entry.rawLength) throw new IOException("Chunk raw length mismatch");
        CRC32 crc = new CRC32(); crc.update(raw);
        if (crc.getValue() != entry.crc32) throw new IOException("Chunk checksum mismatch " + entry.x + "," + entry.y);
        ByteBuffer data = little(raw);
        requireMagic(data, 'Q', 'C', 'H', 'K');
        int x = data.getInt(), y = data.getInt();
        long seed = Integer.toUnsignedLong(data.getInt());
        int municipality = Byte.toUnsignedInt(data.get());
        int biome = Byte.toUnsignedInt(data.get());
        int flags = Byte.toUnsignedInt(data.get()); data.get();
        if (x != entry.x || y != entry.y || municipality != entry.municipality || biome != entry.biome || flags != entry.flags) {
            throw new IOException("Chunk/index identity mismatch");
        }
        int[] landRows = new int[16];
        for (int i = 0; i < 16; i++) landRows[i] = unsignedShort(data.getShort());
        short[] terrainCm = new short[81];
        for (int i = 0; i < terrainCm.length; i++) terrainCm[i] = data.getShort();
        int roadCount = unsignedShort(data.getShort());
        if (roadCount > 20_000) throw new IOException("Unsafe road count");
        Road[] roads = new Road[roadCount];
        for (int i = 0; i < roadCount; i++) {
            roads[i] = new Road(data.getInt() / 10f, data.getInt() / 10f, data.getInt() / 10f, data.getInt() / 10f,
                    Byte.toUnsignedInt(data.get()), Byte.toUnsignedInt(data.get()), unsignedShort(data.getShort()) / 10f);
        }
        int buildingCount = unsignedShort(data.getShort());
        if (buildingCount > 20_000) throw new IOException("Unsafe building count");
        Building[] buildings = new Building[buildingCount];
        for (int i = 0; i < buildingCount; i++) {
            buildings[i] = new Building(unsignedShort(data.getShort())/10f, unsignedShort(data.getShort())/10f,
                    unsignedShort(data.getShort())/10f, unsignedShort(data.getShort())/10f,
                    unsignedShort(data.getShort())/10f, unsignedShort(data.getShort()) * (float)Math.PI / 65535f,
                    Byte.toUnsignedInt(data.get()), Byte.toUnsignedInt(data.get()), 0L, null);
        }
        if (data.hasRemaining()) throw new IOException("Unexpected chunk payload tail " + data.remaining());
        return new WorldChunk(entry, seed, landRows, terrainCm, roads, buildings);
    }

    /** Bilinear 9x9 terrain sample in metres for coordinates local to a 2 km chunk. */
    public float terrainHeight(WorldChunk chunk,float localX,float localZ) {
        float interval=chunkMetres/8f;
        float gx=Math.max(0,Math.min(8,localX/interval)),gz=Math.max(0,Math.min(8,localZ/interval));
        int x0=Math.min(7,(int)Math.floor(gx)),z0=Math.min(7,(int)Math.floor(gz));
        int x1=Math.min(8,x0+1),z1=Math.min(8,z0+1);float tx=gx-x0,tz=gz-z0;
        short[] values=chunk.terrainCm();
        float a=values[z0*9+x0]*(1-tx)+values[z0*9+x1]*tx;
        float b=values[z1*9+x0]*(1-tx)+values[z1*9+x1]*tx;
        return (a*(1-tz)+b*tz)/100f;
    }

    public int chunkX(double globalX) { return (int)Math.floor(globalX / chunkMetres); }
    public int chunkY(double globalZ) { return (int)Math.floor(globalZ / chunkMetres); }

    public Hub hub(String englishName) {
        for (Hub hub : hubs) if (hub.english.equals(englishName)) return hub;
        throw new IllegalArgumentException("Unknown Qatar hub " + englishName);
    }

    @Override public void close() throws IOException { source.close(); }

    private static ByteBuffer little(byte[] bytes) { return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); }
    private static int unsignedShort(short value) { return Short.toUnsignedInt(value); }
    private static long key(int x, int y) { return ((long)x << 32) ^ (y & 0xffffffffL); }
    private static void requireMagic(ByteBuffer data, char a, char b, char c, char d) throws IOException {
        if (data.remaining() < 4 || data.get() != (byte)a || data.get() != (byte)b || data.get() != (byte)c || data.get() != (byte)d) throw new IOException("Invalid qpak magic");
    }
    private static String readString(ByteBuffer data) throws IOException {
        if (data.remaining() < 2) throw new EOFException("Missing string length");
        int size = unsignedShort(data.getShort());
        if (size > data.remaining()) throw new EOFException("String outside qpak metadata");
        byte[] bytes = new byte[size]; data.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public record Municipality(int id, String iso, String english, String arabic) { }
    public record Hub(int id, String english, String arabic, String kind, double latitude, double longitude, double x, double z) { }
    public record ChunkEntry(int x, int y, long offset, int packedLength, int rawLength, long crc32,
                             int municipality, int biome, int flags) {
        public boolean hasLand() { return (flags & 1) != 0; }
        public boolean isCoast() { return (flags & 2) != 0; }
    }
    public record Road(float x1,float z1,float x2,float z2,int roadClass,int lanes,float width,int surface,int flags) {
        public Road(float x1,float z1,float x2,float z2,int roadClass,int lanes,float width){this(x1,z1,x2,z2,roadClass,lanes,width,0,0);}
        public boolean bridge(){return (flags&1)!=0;}
        public boolean tunnel(){return (flags&2)!=0;}
        public boolean oneWay(){return (flags&4)!=0;}
    }
    public record Building(float x, float z, float width, float depth, float height, float yaw,
                           int archetype, int flags, long sourceId, float[] footprint) {
        public boolean hasSourceFootprint() { return footprint != null && footprint.length >= 6; }
    }
    public record WorldChunk(ChunkEntry entry, long seed, int[] landRows, short[] terrainCm, Road[] roads, Building[] buildings) { }
}
