package ai.arena.qatar.environment;

import ai.arena.qatar.performance.ThermalGovernor;
import org.junit.Test;
import static org.junit.Assert.*;

public class QatarEnvironmentTest {
    @Test public void authoredTideWindAndDaylightStayBounded(){
        QatarEnvironment environment=new QatarEnvironment();float minimum=Float.MAX_VALUE,maximum=-Float.MAX_VALUE;
        for(int i=0;i<20_000;i++){environment.update(.1f);minimum=Math.min(minimum,environment.seaLevelMetres());maximum=Math.max(maximum,environment.seaLevelMetres());assertTrue(environment.daylight()>=0&&environment.daylight()<=1);}
        assertTrue(minimum>=-.38f);assertTrue(maximum<=.48f);assertTrue(maximum-minimum>.7f);
        environment.setPreset(QatarEnvironment.WeatherPreset.DUST);assertTrue(environment.visibilityMetres()<4_000);assertTrue(environment.windMetresPerSecond()>8);
    }
    @Test public void androidThermalStatusesDownshiftMonotonically(){
        assertEquals(30,ThermalGovernor.profileForAndroidStatus(0).targetFps);
        assertEquals(20,ThermalGovernor.profileForAndroidStatus(2).targetFps);
        assertEquals(15,ThermalGovernor.profileForAndroidStatus(3).targetFps);
        assertEquals(15,ThermalGovernor.profileForAndroidStatus(6).targetFps);
        assertTrue(ThermalGovernor.Profile.HOT.maxDetailCells<ThermalGovernor.Profile.NORMAL.maxDetailCells);
    }
}
