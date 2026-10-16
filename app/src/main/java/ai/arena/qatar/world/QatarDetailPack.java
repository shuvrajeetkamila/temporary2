package ai.arena.qatar.world;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;

/** Random-access parser for the 250 m QTOD OpenStreetMap detail pack. */
public final class QatarDetailPack implements AutoCloseable {
    private static final int HEADER_BYTES=64;
    private static final int INDEX_BYTES=32;
    public static final int FORMAT_VERSION=1;

    public final int epsg;
    public final int cellMetres;
    public final int cellCount;
    public final double originEasting,originNorthing,worldWidth,worldHeight;
    public final String epoch,sourceUrl,sourcePbfSha256,compilerIdentity;
    public final long roadWayCount,roadSegmentCount,simpleBuildingCount,relationBuildingCount;
    public final long skippedInvalidCount,outsideCount,metadataCellCount;

    private final SeekableDataSource source;
    private final Map<Long,DetailEntry> entries;

    public QatarDetailPack(SeekableDataSource source)throws IOException{
        this.source=source;
        ByteBuffer header=little(source.readFully(0,HEADER_BYTES));
        requireMagic(header,'Q','T','O','D');
        int version=header.getInt();
        if(version!=FORMAT_VERSION)throw new IOException("Unsupported QTOD version "+version);
        epsg=header.getInt();cellMetres=header.getInt();
        int reservedMaskDimension=header.getInt();
        cellCount=header.getInt();
        originEasting=header.getDouble();originNorthing=header.getDouble();worldWidth=header.getDouble();worldHeight=header.getDouble();
        int metadataBytes=header.getInt(),indexLength=header.getInt();
        if(epsg!=QatarPack.EPSG_UTM_39N||cellMetres<64||cellMetres>1000||reservedMaskDimension!=16)throw new IOException("Unsafe QTOD spatial contract");
        if(cellCount<=0||cellCount>500_000||indexLength!=cellCount*INDEX_BYTES)throw new IOException("Invalid QTOD index size");
        long firstBlock=HEADER_BYTES+(long)metadataBytes+indexLength;
        if(metadataBytes<=0||firstBlock>source.size())throw new IOException("QTOD metadata outside file");
        ByteBuffer metadata=little(source.readFully(HEADER_BYTES,metadataBytes));
        epoch=readString(metadata);sourceUrl=readString(metadata);sourcePbfSha256=readString(metadata);compilerIdentity=readString(metadata);
        if(metadata.remaining()!=7*Long.BYTES)throw new IOException("Unexpected QTOD metadata size");
        roadWayCount=metadata.getLong();roadSegmentCount=metadata.getLong();simpleBuildingCount=metadata.getLong();relationBuildingCount=metadata.getLong();
        skippedInvalidCount=metadata.getLong();outsideCount=metadata.getLong();metadataCellCount=metadata.getLong();
        if(metadataCellCount!=cellCount||roadWayCount<0||roadSegmentCount<0||simpleBuildingCount<0||relationBuildingCount<0)throw new IOException("QTOD metadata/index count mismatch");
        if(!sourcePbfSha256.matches("[0-9a-f]{64}"))throw new IOException("Invalid QTOD source checksum");
        ByteBuffer index=little(source.readFully(HEADER_BYTES+metadataBytes,indexLength));
        Map<Long,DetailEntry> values=new HashMap<>(cellCount*2);long indexedRoads=0,indexedBuildings=0,nextOffset=firstBlock;
        int cellsX=(int)Math.ceil(worldWidth/cellMetres),cellsY=(int)Math.ceil(worldHeight/cellMetres);
        for(int i=0;i<cellCount;i++){
            int x=index.getInt(),y=index.getInt();long offset=index.getLong();int packed=index.getInt(),raw=index.getInt();long crc=Integer.toUnsignedLong(index.getInt());
            int roads=Short.toUnsignedInt(index.getShort()),buildings=Short.toUnsignedInt(index.getShort());
            if(x<0||y<0||x>=cellsX||y>=cellsY)throw new IOException("QTOD cell outside world grid");
            if(offset!=nextOffset||packed<=0||packed>64*1024*1024||raw<=0||raw>64*1024*1024||offset+packed>source.size())throw new IOException("QTOD block outside asset");
            DetailEntry entry=new DetailEntry(x,y,offset,packed,raw,crc,roads,buildings);
            if(values.put(key(x,y),entry)!=null)throw new IOException("Duplicate QTOD cell "+x+","+y);
            nextOffset=offset+packed;indexedRoads+=roads;indexedBuildings+=buildings;
        }
        if(nextOffset!=source.size()||indexedRoads!=roadSegmentCount||indexedBuildings!=simpleBuildingCount+relationBuildingCount)throw new IOException("QTOD index totals mismatch");
        entries=Collections.unmodifiableMap(values);
    }

    public DetailEntry entry(int x,int y){return entries.get(key(x,y));}
    public Map<Long,DetailEntry> entries(){return entries;}
    public int cellX(double globalX){return (int)Math.floor(globalX/cellMetres);}
    public int cellY(double globalZ){return (int)Math.floor(globalZ/cellMetres);}

    public DetailChunk load(DetailEntry entry)throws IOException{
        ByteBuffer data=readBlock(entry);
        int x=data.getInt(),y=data.getInt(),roadsCount=data.getInt();
        if(x!=entry.x||y!=entry.y||roadsCount!=entry.roadCount||roadsCount<0||roadsCount>100_000)throw new IOException("QTOD road header mismatch");
        QatarPack.Road[] roads=new QatarPack.Road[roadsCount];
        long[] roadSourceIds=new long[roadsCount];
        for(int i=0;i<roadsCount;i++){
            roadSourceIds[i]=data.getLong();
            float x1=data.getInt()/10f,z1=data.getInt()/10f,x2=data.getInt()/10f,z2=data.getInt()/10f;
            int roadClass=Byte.toUnsignedInt(data.get()),lanes=Byte.toUnsignedInt(data.get());
            int surface=Byte.toUnsignedInt(data.get()),flags=Byte.toUnsignedInt(data.get());
            float width=Short.toUnsignedInt(data.getShort())/10f;
            roads[i]=new QatarPack.Road(x1,z1,x2,z2,roadClass,lanes,width);
        }
        int buildingCount=data.getInt();
        if(buildingCount!=entry.buildingCount||buildingCount<0||buildingCount>100_000)throw new IOException("QTOD building header mismatch");
        QatarPack.Building[] buildings=new QatarPack.Building[buildingCount];
        for(int i=0;i<buildingCount;i++){
            long sourceId=data.getLong();
            float bx=data.getInt()/10f,bz=data.getInt()/10f,width=data.getInt()/10f,depth=data.getInt()/10f,height=data.getInt()/10f;
            float yaw=Short.toUnsignedInt(data.getShort())*(float)Math.PI/65535f;
            int archetype=Byte.toUnsignedInt(data.get()),flags=Byte.toUnsignedInt(data.get()),vertices=Short.toUnsignedInt(data.getShort());
            if(vertices<3||vertices>192||vertices*8L>data.remaining())throw new IOException("Unsafe QTOD footprint vertex count");
            float[] footprint=new float[vertices*2];
            for(int p=0;p<footprint.length;p++)footprint[p]=data.getInt()/10f;
            buildings[i]=new QatarPack.Building(bx,bz,width,depth,height,yaw,archetype,flags,sourceId,footprint);
        }
        if(data.hasRemaining())throw new IOException("Unexpected QTOD payload tail "+data.remaining());
        return new DetailChunk(entry,roads,roadSourceIds,buildings);
    }

    /**
     * Streams one cell's road vectors without allocating building or road
     * objects. This is used by the asynchronous precision-map rasterizer and
     * still validates the complete compressed cell payload and its CRC.
     */
    public void visitRoads(DetailEntry entry,RoadVisitor visitor)throws IOException{
        ByteBuffer data=readBlock(entry);
        int x=data.getInt(),y=data.getInt(),roadsCount=data.getInt();
        if(x!=entry.x||y!=entry.y||roadsCount!=entry.roadCount||roadsCount<0||roadsCount>100_000)throw new IOException("QTOD road header mismatch");
        for(int i=0;i<roadsCount;i++){
            long sourceId=data.getLong();
            float x1=data.getInt()/10f,z1=data.getInt()/10f,x2=data.getInt()/10f,z2=data.getInt()/10f;
            int roadClass=Byte.toUnsignedInt(data.get()),lanes=Byte.toUnsignedInt(data.get());
            int surface=Byte.toUnsignedInt(data.get()),flags=Byte.toUnsignedInt(data.get());
            float width=Short.toUnsignedInt(data.getShort())/10f;
            visitor.road(sourceId,x1,z1,x2,z2,roadClass,lanes,surface,flags,width);
        }
        int buildingCount=data.getInt();
        if(buildingCount!=entry.buildingCount||buildingCount<0||buildingCount>100_000)throw new IOException("QTOD building header mismatch");
        for(int i=0;i<buildingCount;i++){
            if(data.remaining()<34)throw new EOFException("Truncated QTOD building");
            data.position(data.position()+32);
            int vertices=Short.toUnsignedInt(data.getShort());
            if(vertices<3||vertices>192||vertices*8L>data.remaining())throw new IOException("Unsafe QTOD footprint vertex count");
            data.position(data.position()+vertices*8);
        }
        if(data.hasRemaining())throw new IOException("Unexpected QTOD payload tail "+data.remaining());
    }

    private ByteBuffer readBlock(DetailEntry entry)throws IOException{
        if(entry==null)throw new IOException("Missing QTOD cell");
        byte[] packed=source.readFully(entry.offset,entry.packedLength);
        ByteArrayOutputStream output=new ByteArrayOutputStream(entry.rawLength);
        try(InflaterInputStream inflater=new InflaterInputStream(new ByteArrayInputStream(packed))){
            byte[] buffer=new byte[8192];int read;
            while((read=inflater.read(buffer))>=0){if(read>0)output.write(buffer,0,read);if(output.size()>entry.rawLength)throw new IOException("QTOD inflation overflow");}
        }
        byte[] raw=output.toByteArray();
        if(raw.length!=entry.rawLength)throw new IOException("QTOD raw length mismatch");
        CRC32 crc=new CRC32();crc.update(raw);if(crc.getValue()!=entry.crc32)throw new IOException("QTOD checksum mismatch");
        ByteBuffer data=little(raw);requireMagic(data,'Q','D','E','T');
        return data;
    }

    public void verifyCompatible(QatarPack national)throws IOException{
        if(epsg!=national.epsg||Math.abs(originEasting-national.originEasting)>.01||Math.abs(originNorthing-national.originNorthing)>.01||
                Math.abs(worldWidth-national.worldWidth)>.01||Math.abs(worldHeight-national.worldHeight)>.01)throw new IOException("QTOD and national QTPK coordinate contracts differ");
    }

    @Override public void close()throws IOException{source.close();}
    private static ByteBuffer little(byte[] bytes){return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);}
    private static long key(int x,int y){return ((long)x<<32)^(y&0xffffffffL);}
    private static String readString(ByteBuffer data)throws IOException{if(data.remaining()<2)throw new EOFException();int n=Short.toUnsignedInt(data.getShort());if(n>data.remaining())throw new EOFException();byte[] b=new byte[n];data.get(b);return new String(b,StandardCharsets.UTF_8);}
    private static void requireMagic(ByteBuffer data,char a,char b,char c,char d)throws IOException{if(data.remaining()<4||data.get()!=(byte)a||data.get()!=(byte)b||data.get()!=(byte)c||data.get()!=(byte)d)throw new IOException("Invalid detail-pack magic");}

    @FunctionalInterface public interface RoadVisitor{
        void road(long sourceId,float x1,float z1,float x2,float z2,int roadClass,int lanes,int surface,int flags,float width);
    }
    public record DetailEntry(int x,int y,long offset,int packedLength,int rawLength,long crc32,int roadCount,int buildingCount){}
    public record DetailChunk(DetailEntry entry,QatarPack.Road[] roads,long[] roadSourceIds,QatarPack.Building[] buildings){}
}
