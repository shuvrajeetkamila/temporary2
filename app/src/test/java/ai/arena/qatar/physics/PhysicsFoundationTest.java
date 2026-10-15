package ai.arena.qatar.physics;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.math.Vector3;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** Proves the packaged gdx-bullet API path and force-driven fixed step on CI. */
public class PhysicsFoundationTest {
    @Test public void bulletWorldStepsForceDrivenHumanFoundationWithoutInvalidState() {
        HeadlessApplication app=new HeadlessApplication(new ApplicationAdapter(){},new HeadlessApplicationConfiguration());
        try {
            PhysicsWorld physics=new PhysicsWorld(0,0,0);
            try {
                for(int i=0;i<180;i++){
                    physics.setControls(.55f,.18f,0,false);
                    physics.step(1f/60f);
                }
                Vector3 position=physics.position();
                Vector3 velocity=physics.velocity();
                assertTrue(Float.isFinite(position.x)&&Float.isFinite(position.y)&&Float.isFinite(position.z));
                assertTrue(Float.isFinite(velocity.x)&&Float.isFinite(velocity.y)&&Float.isFinite(velocity.z));
                physics.recover();
                assertTrue(physics.position().y>0);
                physics.teleport(80_000,150_000,12,.25f,false);
                assertTrue(Math.abs(physics.globalX()-80_000)<.01);
                assertTrue(Math.abs(physics.globalZ()-150_000)<.01);
                for(int i=0;i<30;i++)physics.step(1f/30f);
                Vector3 rendered=physics.renderPosition(new Vector3());
                assertTrue(Float.isFinite(rendered.x)&&Float.isFinite(rendered.y)&&Float.isFinite(rendered.z));
            } finally {
                physics.dispose();
            }
        } finally {
            app.exit();
        }
    }
}
