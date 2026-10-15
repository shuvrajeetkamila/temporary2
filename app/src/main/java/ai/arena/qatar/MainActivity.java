package ai.arena.qatar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;

import ai.arena.qatar.performance.ThermalGovernor;
import ai.arena.qatar.world.AndroidAssetDataSource;

import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

/** Android entry point with platform thermal status bridged into the portable simulation. */
public final class MainActivity extends AndroidApplication {
    private volatile int thermalStatus;
    private PowerManager powerManager;
    private PowerManager.OnThermalStatusChangedListener thermalListener;
    private volatile boolean powerSaveMode;
    private boolean powerReceiverRegistered;
    private final BroadcastReceiver powerReceiver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){refreshPowerSaveMode();}};

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        powerManager=(PowerManager)getSystemService(Context.POWER_SERVICE);refreshPowerSaveMode();
        IntentFilter powerFilter=new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(powerReceiver,powerFilter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(powerReceiver,powerFilter);
        powerReceiverRegistered=true;
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q&&powerManager!=null){thermalStatus=powerManager.getCurrentThermalStatus();thermalListener=status->thermalStatus=status;powerManager.addThermalStatusListener(thermalListener);}
        AndroidApplicationConfiguration config=new AndroidApplicationConfiguration();
        config.useImmersiveMode=true;config.useGL30=true;config.numSamples=0; // Avoid full-screen MSAA bandwidth/heat on modest phones.
        config.r=8;config.g=8;config.b=8;config.a=8;config.depth=24;config.stencil=0;
        initialize(new QatarGame(
                ()->new AndroidAssetDataSource(getAssets(),"world/qatar-national.qpak"),
                ()->new AndroidAssetDataSource(getAssets(),"world/qatar-osm-current.qod"),
                this::effectiveThermalStatus),config);
    }

    private void refreshPowerSaveMode(){powerSaveMode=powerManager!=null&&powerManager.isPowerSaveMode();}
    private int effectiveThermalStatus(){return ThermalGovernor.effectiveAndroidStatus(thermalStatus,powerSaveMode);}

    @Override protected void onResume(){refreshPowerSaveMode();super.onResume();}

    @Override protected void onDestroy(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q&&powerManager!=null&&thermalListener!=null)powerManager.removeThermalStatusListener(thermalListener);
        if(powerReceiverRegistered){unregisterReceiver(powerReceiver);powerReceiverRegistered=false;}
        super.onDestroy();
    }
}
