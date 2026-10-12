package ai.arena.qatar;

import ai.arena.qatar.world.PackSourceFactory;
import ai.arena.qatar.world.QatarPack;

import com.badlogic.gdx.Game;

/** Owns the validated national pack for the complete application lifecycle. */
public final class QatarGame extends Game {
    private final PackSourceFactory sourceFactory;
    private QatarPack pack;

    public QatarGame(PackSourceFactory sourceFactory) { this.sourceFactory=sourceFactory; }

    @Override public void create() { setScreen(new LoadingScreen(this,sourceFactory)); }

    void start(QatarPack validatedPack) {
        pack=validatedPack;
        setScreen(new WorldScreen(pack));
    }

    @Override public void dispose() {
        super.dispose();
        if(pack!=null){try{pack.close();}catch(Exception ignored){}}
    }
}
