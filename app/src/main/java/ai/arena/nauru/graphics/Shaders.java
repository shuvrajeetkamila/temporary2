package ai.arena.nauru.graphics;

/** GLSL ES 1.00 programs used by the mobile physically-inspired rendering path. */
public final class Shaders {
    private Shaders() {}

    public static final String WORLD_VERTEX =
            "uniform mat4 uViewProjection;\n" +
            "uniform mat4 uLightViewProjection;\n" +
            "uniform mat4 uModel;\n" +
            "uniform vec3 uCamera;\n" +
            "attribute vec3 aPosition;\n" +
            "attribute vec3 aNormal;\n" +
            "attribute vec3 aColor;\n" +
            "varying vec3 vNormal;\n" +
            "varying vec3 vColor;\n" +
            "varying vec3 vWorldPosition;\n" +
            "varying vec4 vShadowPosition;\n" +
            "varying float vDistance;\n" +
            "void main(){\n" +
            " vec4 world=uModel*vec4(aPosition,1.0);\n" +
            " vNormal=normalize(mat3(uModel)*aNormal);\n" +
            " vColor=aColor;\n" +
            " vWorldPosition=world.xyz;\n" +
            " vShadowPosition=uLightViewProjection*world;\n" +
            " vDistance=distance(world.xyz,uCamera);\n" +
            " gl_Position=uViewProjection*world;\n" +
            "}";

    public static final String WORLD_FRAGMENT =
            "precision highp float;\n" +
            "uniform vec3 uLightDirection;\n" +
            "uniform vec3 uSunColor;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform vec3 uCamera;\n" +
            "uniform vec4 uTint;\n" +
            "uniform float uAmbient;\n" +
            "uniform float uFogNear;\n" +
            "uniform float uFogFar;\n" +
            "uniform float uMaterial;\n" +
            "uniform float uDaylight;\n" +
            "uniform float uShadows;\n" +
            "uniform sampler2D uDetailTexture;\n" +
            "uniform sampler2D uShadowTexture;\n" +
            "uniform sampler2D uGrassTexture;\n" +
            "uniform sampler2D uAsphaltTexture;\n" +
            "uniform sampler2D uCoralTexture;\n" +
            "uniform sampler2D uStructureTexture;\n" +
            "varying vec3 vNormal;\n" +
            "varying vec3 vColor;\n" +
            "varying vec3 vWorldPosition;\n" +
            "varying vec4 vShadowPosition;\n" +
            "varying float vDistance;\n" +
            "float unpackDepth(vec4 rgba){\n" +
            " return dot(rgba,vec4(0.0000000596046,0.0000152588,0.00390625,1.0));\n" +
            "}\n" +
            "float shadowVisibility(vec3 normal,vec3 light){\n" +
            " if(uShadows<0.5)return 1.0;\n" +
            " vec3 p=vShadowPosition.xyz/vShadowPosition.w; p=p*0.5+0.5;\n" +
            " if(p.x<=0.002||p.x>=0.998||p.y<=0.002||p.y>=0.998||p.z>=1.0)return 1.0;\n" +
            " float bias=max(0.00028,0.00072*(1.0-max(dot(normal,light),0.0)));\n" +
            " vec2 t=vec2(0.00098); float sum=0.0;\n" +
            " sum+=step(p.z-bias,unpackDepth(texture2D(uShadowTexture,p.xy+vec2(-t.x,-t.y))));\n" +
            " sum+=step(p.z-bias,unpackDepth(texture2D(uShadowTexture,p.xy+vec2(t.x,-t.y))));\n" +
            " sum+=step(p.z-bias,unpackDepth(texture2D(uShadowTexture,p.xy+vec2(-t.x,t.y))));\n" +
            " sum+=step(p.z-bias,unpackDepth(texture2D(uShadowTexture,p.xy+vec2(t.x,t.y))));\n" +
            " return 0.28+0.72*(sum*0.25);\n" +
            "}\n" +
            "void main(){\n" +
            " vec3 n=normalize(vNormal); vec3 l=normalize(uLightDirection);\n" +
            " vec3 v=normalize(uCamera-vWorldPosition); vec3 h=normalize(l+v);\n" +
            " vec3 base=vColor*uTint.rgb; float roughness=0.78;\n" +
            " vec4 detailA=texture2D(uDetailTexture,vWorldPosition.xz*0.034);\n" +
            " vec4 detailB=texture2D(uDetailTexture,vWorldPosition.xz*0.31);\n" +
            " vec2 surfaceUv=vWorldPosition.xz*0.18;\n" +
            " if(uMaterial<0.5){\n" +
            "   float organic=detailA.r*0.62+detailB.g*0.38;\n" +
            "   if(base.g>base.r*1.10){\n" +
            "     vec3 grass=texture2D(uGrassTexture,surfaceUv).rgb;\n" +
            "     base=mix(base*(0.72+organic*0.42),grass,0.74);\n" +
            "   }else{\n" +
            "     vec3 coral=texture2D(uCoralTexture,surfaceUv*0.72).rgb;\n" +
            "     base=mix(base*(0.78+organic*0.30),coral,0.62);\n" +
            "   }\n" +
            "   n=normalize(n+vec3(detailB.r-detailB.g,0.0,detailB.b-detailB.r)*0.13); roughness=0.92;\n" +
            " }else if(uMaterial<1.5){\n" +
            "   vec3 asphalt=texture2D(uAsphaltTexture,surfaceUv*1.28).rgb;\n" +
            "   base=mix(base,asphalt,0.82);\n" +
            "   n=normalize(n+vec3(detailB.r-detailB.g,0.0,detailB.b-detailB.r)*0.055); roughness=0.84;\n" +
            " }else if(uMaterial<2.5){ base*=0.93+detailB.r*0.10; roughness=0.58;\n" +
            " }else if(uMaterial<3.5){\n" +
            "   bool isFoliage=base.g>base.r*1.32 && base.g>base.b*1.22;\n" +
            "   if(isFoliage){ vec3 foliage=texture2D(uGrassTexture,surfaceUv*.65).rgb; base=mix(base,foliage,0.38); }\n" +
            "   else { vec3 wall=texture2D(uStructureTexture,surfaceUv*.52).rgb; base*=0.58+wall*0.86; }\n" +
            "   roughness=isFoliage?0.74:0.68;\n" +
            " }else if(uMaterial<4.5){\n" +
            "   vec3 coral=texture2D(uCoralTexture,surfaceUv*.92).rgb;\n" +
            "   float strata=0.76+0.18*sin(vWorldPosition.y*2.8+detailA.r*4.0);\n" +
            "   base=mix(base,coral,0.64)*strata; n=normalize(n+vec3(detailB.r-detailB.g,0.0,detailB.b-detailB.r)*.18); roughness=0.94;\n" +
            " }else if(uMaterial<5.5){ base*=0.96+detailB.r*0.035; roughness=0.18;\n" +
            " }else { base*=0.91+detailA.r*0.12; roughness=0.72; }\n" +
            " float ndl=max(dot(n,l),0.0); float shadow=shadowVisibility(n,l);\n" +
            " vec3 skyAmbient=mix(vec3(0.055,0.075,0.12),uFogColor,0.55);\n" +
            " float hemi=0.48+0.52*max(n.y,0.0);\n" +
            " vec3 ambient=base*(skyAmbient*(0.72+uAmbient)+vec3(0.035))*hemi;\n" +
            " vec3 diffuse=base*uSunColor*ndl*shadow*0.92;\n" +
            " float specPower=mix(96.0,7.0,roughness);\n" +
            " float fresnel=0.035+0.15*pow(1.0-max(dot(n,v),0.0),5.0);\n" +
            " float spec=pow(max(dot(n,h),0.0),specPower)*(1.0-roughness)*shadow;\n" +
            " vec3 color=ambient+diffuse+uSunColor*spec*(0.32+fresnel);\n" +
            " float fog=clamp((vDistance-uFogNear)/(uFogFar-uFogNear),0.0,1.0);\n" +
            " color=mix(color,uFogColor,fog*fog*(3.0-2.0*fog));\n" +
            " color=color/(color+vec3(0.72));\n" +
            " color=pow(max(color,vec3(0.0)),vec3(0.4545));\n" +
            " gl_FragColor=vec4(color,uTint.a);\n" +
            "}";

    public static final String WATER_VERTEX =
            "uniform mat4 uViewProjection;\n" +
            "uniform vec3 uCamera;\n" +
            "uniform float uTime;\n" +
            "attribute vec3 aPosition;\n" +
            "attribute vec3 aNormal;\n" +
            "attribute vec3 aColor;\n" +
            "varying vec3 vWorldPosition;\n" +
            "varying vec3 vNormal;\n" +
            "varying float vWave;\n" +
            "varying float vDistance;\n" +
            "void main(){\n" +
            " vec3 p=aPosition;\n" +
            " float a=p.x*0.0082+uTime*1.25; float b=p.z*0.011-uTime*0.93;\n" +
            " float c=(p.x+p.z)*0.017+uTime*1.62;\n" +
            " float w=sin(a)*0.26+cos(b)*0.20+sin(c)*0.075; p.y+=w;\n" +
            " float dx=cos(a)*0.002132+cos(c)*0.001275;\n" +
            " float dz=-sin(b)*0.00220+cos(c)*0.001275;\n" +
            " vNormal=normalize(vec3(-dx*42.0,1.0,-dz*42.0));\n" +
            " vWorldPosition=p; vWave=w; vDistance=distance(p,uCamera);\n" +
            " gl_Position=uViewProjection*vec4(p,1.0);\n" +
            "}";

    public static final String WATER_FRAGMENT =
            "precision highp float;\n" +
            "uniform vec3 uFogColor;\n" +
            "uniform vec3 uCamera;\n" +
            "uniform vec3 uLightDirection;\n" +
            "uniform vec3 uSunColor;\n" +
            "uniform float uFogNear;\n" +
            "uniform float uFogFar;\n" +
            "uniform float uDaylight;\n" +
            "varying vec3 vWorldPosition;\n" +
            "varying vec3 vNormal;\n" +
            "varying float vWave;\n" +
            "varying float vDistance;\n" +
            "void main(){\n" +
            " vec3 n=normalize(vNormal); vec3 v=normalize(uCamera-vWorldPosition);\n" +
            " vec3 l=normalize(uLightDirection); vec3 h=normalize(l+v);\n" +
            " float lx=vWorldPosition.x*0.9872-vWorldPosition.z*0.1593;\n" +
            " float lz=vWorldPosition.x*0.1593+vWorldPosition.z*0.9872;\n" +
            " float coast=sqrt((lx/2100.0)*(lx/2100.0)+(lz/3000.0)*(lz/3000.0));\n" +
            " float shallow=1.0-smoothstep(1.02,1.42,coast);\n" +
            " vec3 deep=vec3(0.006,0.115,0.18); vec3 reef=vec3(0.018,0.38,0.40);\n" +
            " vec3 base=mix(deep,reef,shallow*0.78);\n" +
            " float fresnel=0.025+0.70*pow(1.0-max(dot(n,v),0.0),4.2);\n" +
            " vec3 reflected=uFogColor*(0.72+uDaylight*0.48);\n" +
            " float sunGlint=pow(max(dot(n,h),0.0),180.0)*uDaylight;\n" +
            " float foam=smoothstep(0.42,0.54,vWave)*(0.15+shallow*0.85);\n" +
            " vec3 color=mix(base,reflected,fresnel)+uSunColor*sunGlint*1.35+vec3(foam*0.32);\n" +
            " float fog=clamp((vDistance-uFogNear)/(uFogFar-uFogNear),0.0,1.0);\n" +
            " color=mix(color,uFogColor,fog*fog); color=color/(color+vec3(0.68));\n" +
            " color=pow(max(color,vec3(0.0)),vec3(0.4545));\n" +
            " gl_FragColor=vec4(color,1.0);\n" +
            "}";

    public static final String SKY_VERTEX =
            "uniform mat4 uViewProjection;\n" +
            "uniform mat4 uModel;\n" +
            "attribute vec3 aPosition;\n" +
            "attribute vec3 aNormal;\n" +
            "attribute vec3 aColor;\n" +
            "varying vec3 vDirection;\n" +
            "void main(){\n" +
            " vDirection=normalize(aPosition);\n" +
            " vec4 clip=uViewProjection*uModel*vec4(aPosition,1.0);\n" +
            " gl_Position=clip.xyww;\n" +
            "}";

    public static final String SKY_FRAGMENT =
            "precision highp float;\n" +
            "uniform vec3 uSunDirection;\n" +
            "uniform float uDaylight;\n" +
            "uniform float uTime;\n" +
            "varying vec3 vDirection;\n" +
            "float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}\n" +
            "float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}\n" +
            "void main(){\n" +
            " vec3 d=normalize(vDirection); float horizon=pow(1.0-max(d.y,0.0),3.0);\n" +
            " vec3 nightTop=vec3(0.004,0.009,0.028), nightHorizon=vec3(0.035,0.06,0.10);\n" +
            " vec3 dayTop=vec3(0.055,0.28,0.62), dayHorizon=vec3(0.53,0.72,0.79);\n" +
            " vec3 color=mix(mix(nightTop,nightHorizon,horizon),mix(dayTop,dayHorizon,horizon),uDaylight);\n" +
            " vec2 cp=d.xz/max(0.16,d.y+0.28)*1.45+vec2(uTime*0.0025,uTime*0.0011);\n" +
            " float cloud=noise(cp)+noise(cp*2.07)*0.5+noise(cp*4.11)*0.25;\n" +
            " cloud=smoothstep(0.93,1.34,cloud)*(smoothstep(-0.08,0.16,d.y));\n" +
            " color=mix(color,vec3(0.82,0.87,0.88),cloud*uDaylight*0.62);\n" +
            " float sun=pow(max(dot(d,normalize(uSunDirection)),0.0),720.0);\n" +
            " float glow=pow(max(dot(d,normalize(uSunDirection)),0.0),14.0);\n" +
            " color+=vec3(1.0,0.55,0.19)*glow*uDaylight*0.23+vec3(1.0,0.90,0.60)*sun*2.8;\n" +
            " vec2 starCell=floor((d.xz/(abs(d.y)+0.3))*420.0);\n" +
            " float stars=step(0.9968,hash(starCell))*smoothstep(0.05,0.38,d.y)*(1.0-uDaylight);\n" +
            " color+=vec3(stars);\n" +
            " gl_FragColor=vec4(pow(max(color,vec3(0.0)),vec3(0.4545)),1.0);\n" +
            "}";

    public static final String SHADOW_VERTEX =
            "uniform mat4 uLightViewProjection;\n" +
            "uniform mat4 uModel;\n" +
            "attribute vec3 aPosition;\n" +
            "attribute vec3 aNormal;\n" +
            "attribute vec3 aColor;\n" +
            "void main(){gl_Position=uLightViewProjection*uModel*vec4(aPosition,1.0);}";

    public static final String SHADOW_FRAGMENT =
            "precision highp float;\n" +
            "vec4 packDepth(float depth){\n" +
            " const vec4 shift=vec4(16777216.0,65536.0,256.0,1.0);\n" +
            " const vec4 mask=vec4(0.0,0.00390625,0.00390625,0.00390625);\n" +
            " vec4 rgba=fract(depth*shift); rgba-=rgba.xxyz*mask; return rgba;\n" +
            "}\n" +
            "void main(){gl_FragColor=packDepth(gl_FragCoord.z);}";
}
