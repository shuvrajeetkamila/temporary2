package ai.arena.qatar;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;

import ai.arena.qatar.world.AndroidAssetDataSource;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

/** Android entry point with platform thermal status bridged into the portable simulation. */
public final class MainActivity extends AndroidApplication {
    private volatile int thermalStatus;
    private PowerManager powerManager;
    private PowerManager.OnThermalStatusChangedListener thermalListener;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q){
            powerManager=(PowerManager)getSystemService(Context.POWER_SERVICE);
            if(powerManager!=null){thermalStatus=powerManager.getCurrentThermalStatus();thermalListener=status->thermalStatus=status;powerManager.addThermalStatusListener(thermalListener);}
        }
        AndroidApplicationConfiguration config=new AndroidApplicationConfiguration();
        config.useImmersiveMode=true;config.useGL30=true;config.numSamples=2;
        config.r=8;config.g=8;config.b=8;config.a=8;config.depth=24;config.stencil=0;
        initialize(new QatarGame(
                ()->new AndroidAssetDataSource(getAssets(),"world/qatar-national.qpak"),
                ()->new AndroidAssetDataSource(getAssets(),"world/qatar-osm-current.qod"),
                ()->thermalStatus),config);
    }

    @Override protected void onDestroy(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q&&powerManager!=null&&thermalListener!=null)powerManager.removeThermalStatusListener(thermalListener);
        super.onDestroy();
    }
}
