package ai.arena.qatar.ui;

import ai.arena.qatar.input.TouchController;
import ai.arena.qatar.physics.PhysicsWorld;
import ai.arena.qatar.render.QatarWorldRenderer;
import ai.arena.qatar.world.QatarPack;
import ai.arena.qatar.world.WorldStreamer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;

/** In-game evidence HUD: streaming counts, exact scale coordinates, mode, and national minimap. */
public final class HudRenderer implements Disposable {
    private final QatarPack pack;
    private final PhysicsWorld physics;
    private final WorldStreamer streamer;
    private final QatarWorldRenderer renderer;
    private final TouchController controls;
    private final ShapeRenderer shapes=new ShapeRenderer();
    private final SpriteBatch sprites=new SpriteBatch();
    private final BitmapFont font=new BitmapFont();
    private final Color maroon=new Color(.54f,.08f,.22f,.90f);

    public HudRenderer(QatarPack pack,PhysicsWorld physics,WorldStreamer streamer,QatarWorldRenderer renderer,TouchController controls){
        this.pack=pack;this.physics=physics;this.streamer=streamer;this.renderer=renderer;this.controls=controls;
        font.getData().setScale(1.02f);
    }

    public void render(){
        int width=Gdx.graphics.getWidth(),height=Gdx.graphics.getHeight();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.getProjectionMatrix().setToOrtho2D(0,0,width,height);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.02f,.055f,.07f,.72f);shapes.rect(14,height-118,530,104);
        shapes.setColor(maroon);shapes.rect(width-250,height-82,236,62);shapes.rect(width-500,height-82,236,62);
        drawStick(controls.moveOrigin(),controls.moveCurrent(),controls.moving(),height,new Color(.94f,.86f,.66f,.28f));
        drawStick(controls.liftOrigin(),controls.liftCurrent(),controls.lifting(),height,new Color(.54f,.08f,.22f,.32f));
        shapes.end();
        drawMinimap(width,height);

        sprites.getProjectionMatrix().setToOrtho2D(0,0,width,height);sprites.begin();
        font.setColor(Color.WHITE);
        Vector3 velocity=physics.velocity();
        String biome=localBiome();
        font.draw(sprites,"QATAR: THE PENINSULA  •  TRUE 1:1 METRES",28,height-37);
        font.draw(sprites,String.format("%s  |  %.1f m/s  |  %s  |  30 FPS THERMAL CAP",physics.mode().label,velocity.len(),biome),28,height-66);
        font.draw(sprites,String.format("LOCAL CHUNKS %d  PENDING %d  •  INSTALLED NATIONAL INDEX %,d / %,d",streamer.residentCount(),streamer.pendingCount(),pack.chunkCount,pack.chunkCount),28,height-95);
        font.draw(sprites,"SWITCH MODE",width-228,height-46);
        font.draw(sprites,"RECOVER",width-445,height-46);
        font.setColor(.92f,.88f,.74f,1);font.draw(sprites,"Left control: move / steer     Right control: lift / action",24,30);
        font.draw(sprites,"Offline world  •  © geoBoundaries / Natural Earth sources — see Credits",width-690,30);
        String fatal=renderer.fatalStreamingError();
        if(!fatal.isEmpty()){font.setColor(Color.SCARLET);font.draw(sprites,fatal,25,height-140);}
        sprites.end();
    }

    private void drawStick(Vector2 origin,Vector2 current,boolean active,int height,Color color){
        float ox=active?origin.x:95,oy=active?height-origin.y:105;
        float cx=active?current.x:ox,cy=active?height-current.y:oy;
        if(origin==controls.liftOrigin()&&!active){ox=Gdx.graphics.getWidth()-100;oy=105;cx=ox;cy=oy;}
        shapes.setColor(color);shapes.circle(ox,oy,68,32);shapes.setColor(color.r,color.g,color.b,.70f);shapes.circle(cx,cy,29,24);
    }

    private void drawMinimap(int width,int height){
        float mapX=width-190,mapY=height-285,mapW=170,mapH=185;
        shapes.begin(ShapeRenderer.ShapeType.Filled);shapes.setColor(.02f,.06f,.075f,.72f);shapes.rect(mapX-8,mapY-8,mapW+16,mapH+16);shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);shapes.setColor(.95f,.84f,.62f,.95f);
        for(float[] polygon:pack.nationalPolygons){
            for(int i=2;i<polygon.length;i+=2){float x1=mapX+polygon[i-2]/(float)pack.worldWidth*mapW,y1=mapY+polygon[i-1]/(float)pack.worldHeight*mapH;float x2=mapX+polygon[i]/(float)pack.worldWidth*mapW,y2=mapY+polygon[i+1]/(float)pack.worldHeight*mapH;shapes.line(x1,y1,x2,y2);}
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Filled);shapes.setColor(.65f,.03f,.18f,1);shapes.circle(mapX+(float)(physics.globalX()/pack.worldWidth)*mapW,mapY+(float)(physics.globalZ()/pack.worldHeight)*mapH,5,16);shapes.end();
    }

    private String localBiome(){
        QatarPack.ChunkEntry entry=pack.entry(pack.chunkX(physics.globalX()),pack.chunkY(physics.globalZ()));
        return entry==null?"OUTSIDE PACK":pack.biomes.get(entry.biome());
    }
    @Override public void dispose(){font.dispose();sprites.dispose();shapes.dispose();}
}
