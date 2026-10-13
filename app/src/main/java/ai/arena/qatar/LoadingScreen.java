package ai.arena.qatar;

import ai.arena.qatar.world.PackSourceFactory;
import ai.arena.qatar.world.QatarDetailPack;
import ai.arena.qatar.world.QatarPack;
import ai.arena.qatar.world.SeekableDataSource;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Honest startup validation; the player never enters an unverified stale world. */
final class LoadingScreen extends ScreenAdapter {
    private final QatarGame game;
    private final PackSourceFactory nationalSourceFactory;
    private final PackSourceFactory detailSourceFactory;
    private final SpriteBatch batch=new SpriteBatch();
    private final ShapeRenderer shapes=new ShapeRenderer();
    private final BitmapFont font=new BitmapFont();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile float progress=.04f;
    private volatile String status="Opening offline national pack…";
    private volatile String failure="";
    private volatile boolean launched;

    LoadingScreen(QatarGame game,PackSourceFactory nationalSourceFactory,PackSourceFactory detailSourceFactory){
        this.game=game;this.nationalSourceFactory=nationalSourceFactory;this.detailSourceFactory=detailSourceFactory;font.getData().setScale(1.25f);
    }

    @Override public void show(){
        worker.submit(()->{
            SeekableDataSource nationalSource=null,detailSource=null;QatarPack pack=null;QatarDetailPack detailPack=null;
            try{
                nationalSource=nationalSourceFactory.open();progress=.12f;status="Reading compact country index…";
                pack=new QatarPack(nationalSource);nationalSource=null;progress=.30f;status="Validating municipalities, islands and chunks…";
                validateCoverage(pack);
                detailSource=detailSourceFactory.open();progress=.48f;status="Indexing complete offline OpenStreetMap detail…";
                detailPack=new QatarDetailPack(detailSource);detailSource=null;detailPack.verifyCompatible(pack);validateDetail(detailPack);
                progress=.72f;status="Checksum-testing Souq Waqif terrain and OSM cells…";
                QatarPack.Hub start=pack.hub("Souq Waqif");
                QatarPack.ChunkEntry entry=pack.entry(pack.chunkX(start.x()),pack.chunkY(start.z()));
                QatarPack.WorldChunk startup=pack.load(entry);
                if(!startup.entry().hasLand())throw new IllegalStateException("Doha startup chunk has no land");
                QatarDetailPack.DetailEntry detailEntry=detailPack.entry(detailPack.cellX(start.x()),detailPack.cellY(start.z()));
                if(detailEntry==null)throw new IllegalStateException("Souq Waqif OSM detail cell is absent");
                QatarDetailPack.DetailChunk startupDetail=detailPack.load(detailEntry);
                if(startupDetail.roads().length==0||startupDetail.buildings().length==0)throw new IllegalStateException("Souq Waqif OSM detail is empty");
                progress=1;status="Full Qatar terrain and OSM packs indexed. Starting local streamers…";
                final QatarPack readyPack=pack;final QatarDetailPack readyDetail=detailPack;
                Gdx.app.postRunnable(()->{if(!launched){launched=true;game.start(readyPack,readyDetail);dispose();}});
            }catch(Throwable error){
                failure=error.getClass().getSimpleName()+": "+error.getMessage();status="Pack validation failed";
                if(detailPack!=null){try{detailPack.close();}catch(Exception ignored){}}
                else if(detailSource!=null){try{detailSource.close();}catch(Exception ignored){}}
                if(pack!=null){try{pack.close();}catch(Exception ignored){}}
                else if(nationalSource!=null){try{nationalSource.close();}catch(Exception ignored){}}
            }
        });
    }

    private static void validateDetail(QatarDetailPack detail){
        if(detail.cellMetres!=250)throw new IllegalStateException("OSM detail is not partitioned into 250 m cells");
        if(detail.cellCount<10_000)throw new IllegalStateException("OSM detail-cell coverage is unexpectedly sparse");
        if(detail.roadSegmentCount<1_000_000L)throw new IllegalStateException("OSM road coverage is incomplete");
        if(detail.simpleBuildingCount+detail.relationBuildingCount<150_000L)throw new IllegalStateException("OSM building coverage is incomplete");
        if(detail.sourcePbfSha256.length()!=64)throw new IllegalStateException("OSM source checksum is absent");
    }

    private static void validateCoverage(QatarPack pack){
        if(pack.epsg!=QatarPack.EPSG_UTM_39N)throw new IllegalStateException("CRS mismatch");
        if(pack.municipalities.size()!=8)throw new IllegalStateException("Expected all eight municipalities");
        if(pack.nationalPolygons.size()<1)throw new IllegalStateException("Missing ADM0 polygons");
        Set<Integer> represented=new HashSet<>();int land=0,coast=0;
        for(QatarPack.ChunkEntry entry:pack.entries().values())if(entry.hasLand()){represented.add(entry.municipality());land++;if(entry.isCoast())coast++;}
        if(represented.size()!=pack.municipalities.size())throw new IllegalStateException("Municipality chunk coverage incomplete");
        if(land<2500||coast<100)throw new IllegalStateException("Country grid unexpectedly sparse");
        if(pack.chunkCount!=pack.entries().size())throw new IllegalStateException("Index count mismatch");
    }

    @Override public void render(float delta){
        int width=Gdx.graphics.getWidth(),height=Gdx.graphics.getHeight();
        Gdx.gl.glClearColor(.025f,.065f,.08f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        shapes.getProjectionMatrix().setToOrtho2D(0,0,width,height);shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(.54f,.08f,.22f,1);shapes.rect(0,height*.64f,width,height*.36f);
        shapes.setColor(.15f,.18f,.18f,1);shapes.rect(width*.16f,height*.31f,width*.68f,22);
        shapes.setColor(.92f,.78f,.55f,1);shapes.rect(width*.16f,height*.31f,width*.68f*progress,22);shapes.end();
        batch.getProjectionMatrix().setToOrtho2D(0,0,width,height);batch.begin();
        font.setColor(Color.WHITE);font.getData().setScale(2.1f);font.draw(batch,"QATAR: THE PENINSULA",width*.16f,height*.78f);
        font.getData().setScale(1.15f);font.draw(batch,"Full country installed • only nearby chunks become resident",width*.16f,height*.70f);
        font.draw(batch,status,width*.16f,height*.40f);
        font.getData().setScale(.90f);font.setColor(.86f,.83f,.74f,1);font.draw(batch,"EPSG:32639 • 1:1 metres • offline APK • deterministic QTPK + QTOD",width*.16f,height*.23f);
        if(!failure.isEmpty()){font.setColor(Color.SCARLET);font.draw(batch,failure,width*.16f,height*.15f);}
        batch.end();
    }

    @Override public void dispose(){worker.shutdownNow();font.dispose();batch.dispose();shapes.dispose();}
}
