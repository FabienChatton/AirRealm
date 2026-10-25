package ch.airealm;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.dynamics.btDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;

public class Airplane {
    private final ModelInstance instance;
    private final btRigidBody body;
    private final btBoxShape shape;
    private final FlightModel flightModel;
    private boolean isDead;

    private float throttle = 0.8f;

    public Airplane(Model model, FlightModel flightModel, btDynamicsWorld world, Matrix4 initialPosition, float initialSpeed) {
        this.flightModel = flightModel;
        this.instance = new ModelInstance(model);

        shape = new btBoxShape(new Vector3(1f, 1f, 1f));
        Vector3 localInertia = new Vector3(0f, 0f, 0f);
        shape.calculateLocalInertia(1f, localInertia);

        btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(1f, null, shape, localInertia);
        info.setLinearDamping(0.05f);
        info.setAngularDamping(0.2f);

        body = new btRigidBody(info);
        body.setWorldTransform(initialPosition);
        body.setLinearVelocity(new Vector3(0f, 0f, -initialSpeed));

        world.addRigidBody(body);
    }

    public void update(float pitchIn, float rudderIn, float aileronIn, float dt) {
        flightModel.update(body, throttle, pitchIn, rudderIn, aileronIn, dt);
        body.getWorldTransform(instance.transform);
    }

    public void setThrottle(float throttle) {
        this.throttle = Math.clamp(throttle, 0f, 1f);
    }

    public boolean isDead() { return isDead; }
    public void setDead(boolean dead) {
        this.isDead = dead;
        if (dead) {
            this.throttle = 0f;
            body.setLinearVelocity(Vector3.Zero);
            body.setAngularVelocity(Vector3.Zero);
        }
    }

    public float getThrottle() { return throttle; }
    public ModelInstance getInstance() { return instance; }
    public btRigidBody getBody() { return body; }
    public FlightModel getFlightModel() { return flightModel; }

    public void getPosition(Vector3 out) {
        instance.transform.getTranslation(out);
    }

    public void dispose(btDynamicsWorld world) {
        world.removeRigidBody(body);
        body.dispose();
        shape.dispose();
    }
}
