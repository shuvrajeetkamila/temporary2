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
            } finally {
                physics.dispose();
            }
        } finally {
            app.exit();
        }
    }
}
