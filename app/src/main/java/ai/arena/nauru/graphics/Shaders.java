package ai.arena.nauru.graphics;

public final class Shaders {
    private Shaders() {}

    public static final String WORLD_VERTEX =
            "uniform mat4 uViewProjection;\n" +
            "uniform mat4 uModel;\n" +
            "uniform vec3 uCamera;\n" +
            "attribute vec3 aPosition;\n" +
            "attribute vec3 aNormal;\n" +
            "attribute vec3 aColor;\n" +
            "varying vec3 vNormal;\n" +
            "varying vec3 vColor;\n" +
            "varying float vDistance;\n" +
            "void main(){\n" +
            " vec4 world=uModel*vec4(aPosition,1.0);\n" +
            " vNormal=normalize(mat3(uModel)*aNormal);\n" +
            " vColor=aColor;\n" +
            " vDistance=distance(world.xyz,uCamera);\n" +
            " gl_Position=uViewProjection*world;\n" +
            "}";

    public static final String WORLD_FRAGMENT =
            "precision mediump float;\n" +
            "uniform vec3 uLightDirection;\n" +
            "uniform vec3 uSunColor;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform vec4 uTint;\n" +
            "uniform float uAmbient;\n" +
            "uniform float uFogNear;\n" +
            "uniform float uFogFar;\n" +
            "varying vec3 vNormal;\n" +
            "varying vec3 vColor;\n" +
            "varying float vDistance;\n" +
            "void main(){\n" +
            " float diffuse=max(dot(normalize(vNormal),normalize(uLightDirection)),0.0);\n" +
            " float halfLambert=diffuse*0.72+uAmbient;\n" +
            " vec3 color=vColor*uTint.rgb*(uSunColor*halfLambert);\n" +
            " float fog=clamp((vDistance-uFogNear)/(uFogFar-uFogNear),0.0,1.0);\n" +
            " color=mix(color,uFogColor,fog*fog);\n" +
            " gl_FragColor=vec4(color,uTint.a);\n" +
            "}";

    public static final String WATER_VERTEX =
            "uniform mat4 uViewProjection;\n" +
            "uniform mat4 uModel;\n" +
            "uniform vec3 uCamera;\n" +
            "uniform float uTime;\n" +
            "attribute vec3 aPosition;\n" +
            "attribute vec3 aNormal;\n" +
            "attribute vec3 aColor;\n" +
            "varying float vWave;\n" +
            "varying float vDistance;\n" +
            "void main(){\n" +
            " vec3 p=aPosition;\n" +
            " float w=sin(p.x*0.008+uTime*1.4)*0.28+cos(p.z*0.011-uTime*1.1)*0.22;\n" +
            " p.y+=w; vWave=w; vDistance=distance(p,uCamera);\n" +
            " gl_Position=uViewProjection*vec4(p,1.0);\n" +
            "}";

    public static final String WATER_FRAGMENT =
            "precision mediump float;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform float uFogNear;\n" +
            "uniform float uFogFar;\n" +
            "varying float vWave;\n" +
            "varying float vDistance;\n" +
            "void main(){\n" +
            " vec3 deep=vec3(0.015,0.20,0.28);\n" +
            " vec3 crest=vec3(0.08,0.48,0.57);\n" +
            " vec3 color=mix(deep,crest,clamp(vWave+0.48,0.0,1.0));\n" +
            " float fog=clamp((vDistance-uFogNear)/(uFogFar-uFogNear),0.0,1.0);\n" +
            " gl_FragColor=vec4(mix(color,uFogColor,fog*fog),1.0);\n" +
            "}";
}
