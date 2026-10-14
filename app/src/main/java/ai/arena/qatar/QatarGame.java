package ai.arena.qatar;

import ai.arena.qatar.world.PackSourceFactory;
import ai.arena.qatar.world.QatarDetailPack;
import ai.arena.qatar.world.QatarPack;
import ai.arena.qatar.world.QatarSpawnResolver;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;

import java.util.function.IntSupplier;

/** Owns the validated national pack for the complete application lifecycle. */
public final class QatarGame extends Game {
    private final PackSourceFactory nationalSourceFactory;
    private final PackSourceFactory detailSourceFactory;
    private final IntSupplier thermalStatus;
    private QatarPack pack;
    private QatarDetailPack detailPack;

    public QatarGame(PackSourceFactory nationalSourceFactory,PackSourceFactory detailSourceFactory,IntSupplier thermalStatus) {
        this.nationalSourceFactory=nationalSourceFactory;this.detailSourceFactory=detailSourceFactory;this.thermalStatus=thermalStatus;
    }

    @Override public void create() {
        Gdx.graphics.setForegroundFPS(30);
        setScreen(new LoadingScreen(this,nationalSourceFactory,detailSourceFactory));
    }

    void start(QatarPack validatedPack,QatarDetailPack validatedDetailPack,QatarSpawnResolver.Spawn spawn,float groundHeight) {
        pack=validatedPack;detailPack=validatedDetailPack;
        Gdx.app.log("QatarStreaming","PACK_READY epoch="+pack.worldEpoch+" terrainSample="+groundHeight+"m OSM="+detailPack.roadSegmentCount+"/"+(detailPack.simpleBuildingCount+detailPack.relationBuildingCount));
        Gdx.app.log("QatarStreaming","SPAWN_READY x="+spawn.x()+" z="+spawn.z()+" elevation="+groundHeight+" roadClass="+spawn.roadClass()+" basis="+spawn.basis());
        setScreen(new WorldScreen(pack,detailPack,spawn,groundHeight,thermalStatus));
    }

    @Override public void dispose() {
        super.dispose();
        if(detailPack!=null){try{detailPack.close();}catch(Exception ignored){}}
        if(pack!=null){try{pack.close();}catch(Exception ignored){}}
    }
}
