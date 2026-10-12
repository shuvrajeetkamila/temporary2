package ai.arena.qatar;

import android.os.Bundle;

import ai.arena.qatar.world.AndroidAssetDataSource;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

/** Fresh Android entry point; no previous project code or package identity is reused. */
public final class MainActivity extends AndroidApplication {
    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        AndroidApplicationConfiguration config=new AndroidApplicationConfiguration();
        config.useImmersiveMode=true;
        config.useGL30=true;
        config.numSamples=2;
        config.r=8;config.g=8;config.b=8;config.a=8;config.depth=24;config.stencil=0;
        initialize(new QatarGame(()->new AndroidAssetDataSource(getAssets(),"world/qatar-national.qpak")),config);
    }
}
