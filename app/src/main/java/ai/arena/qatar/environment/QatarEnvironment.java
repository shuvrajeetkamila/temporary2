package ai.arena.qatar.environment;

/** Deterministic Qatar daylight, wind, visibility, and Gulf tide simulation.
 * Values are authored gameplay conditions, never live weather or navigation data. */
public final class QatarEnvironment {
    public enum WeatherPreset {
        CLEAR("CLEAR",4.5f,.04f,18_000f,.08f),
        SHAMAL("SHAMAL",13.0f,.18f,9_000f,.20f),
        DUST("DUST",9.0f,.58f,3_200f,.12f),
        RAIN("RAIN",7.0f,.32f,6_000f,.30f);
        public final String label;public final float windMetresPerSecond,haze,visibilityMetres,wetness;
        WeatherPreset(String label,float wind,float haze,float visibility,float wetness){this.label=label;this.windMetresPerSecond=wind;this.haze=haze;this.visibilityMetres=visibility;this.wetness=wetness;}
    }

    private static final float DAY_SECONDS=2_880f;
    private static final float TIDE_SECONDS=1_260f;
    private float elapsedSeconds;
    private float solarHour=9.25f;
    private WeatherPreset preset=WeatherPreset.CLEAR;

    public void update(float deltaSeconds){float delta=Math.max(0,Math.min(.25f,deltaSeconds));elapsedSeconds+=delta;solarHour=(9.25f+elapsedSeconds/DAY_SECONDS*24f)%24f;}
    public WeatherPreset preset(){return preset;}
    public void setPreset(WeatherPreset value){if(value==null)throw new IllegalArgumentException("weather preset");preset=value;}
    public float solarHour(){return solarHour;}
    public float daylight(){return clamp((float)Math.sin((solarHour-6f)/12f*Math.PI),0,1);}
    public float seaLevelMetres(){return .05f+.42f*(float)Math.sin(elapsedSeconds/TIDE_SECONDS*Math.PI*2+.35f);}
    public float windMetresPerSecond(){return preset.windMetresPerSecond;}
    public float windX(){return preset.windMetresPerSecond*.82f;}
    public float windZ(){return preset.windMetresPerSecond*.57f;}
    public float visibilityMetres(){return preset.visibilityMetres;}
    public float ambient(){return .43f+daylight()*.24f-preset.haze*.10f;}
    public float skyRed(){return .47f+preset.haze*.24f;}
    public float skyGreen(){return .69f-preset.haze*.20f;}
    public float skyBlue(){return .79f-preset.haze*.29f;}
    public float sunRed(){return 1f;}
    public float sunGreen(){return .84f+preset.haze*.08f;}
    public float sunBlue(){return .65f+preset.haze*.12f;}
    public float elapsedSeconds(){return elapsedSeconds;}
    private static float clamp(float value,float min,float max){return Math.max(min,Math.min(max,value));}
}
