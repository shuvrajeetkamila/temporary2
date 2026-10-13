package ai.arena.qatar;

import ai.arena.qatar.world.PackSourceFactory;
import ai.arena.qatar.world.QatarDetailPack;
import ai.arena.qatar.world.QatarPack;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;

/** Owns the validated national pack for the complete application lifecycle. */
public final class QatarGame extends Game {
    private final PackSourceFactory nationalSourceFactory;
    private final PackSourceFactory detailSourceFactory;
    private QatarPack pack;
    private QatarDetailPack detailPack;

    public QatarGame(PackSourceFactory nationalSourceFactory,PackSourceFactory detailSourceFactory) {
        this.nationalSourceFactory=nationalSourceFactory;this.detailSourceFactory=detailSourceFactory;
    }

    @Override public void create() {
        Gdx.graphics.setForegroundFPS(30);
        setScreen(new LoadingScreen(this,nationalSourceFactory,detailSourceFactory));
    }

    void start(QatarPack validatedPack,QatarDetailPack validatedDetailPack) {
        pack=validatedPack;detailPack=validatedDetailPack;
        setScreen(new WorldScreen(pack,detailPack));
    }

    @Override public void dispose() {
        super.dispose();
        if(detailPack!=null){try{detailPack.close();}catch(Exception ignored){}}
        if(pack!=null){try{pack.close();}catch(Exception ignored){}}
    }
}
