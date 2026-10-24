package ch.airealm;

import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;

public interface FlightModel {
    void update(btRigidBody body, float throttle, float pitchIn, float rudderIn, float aileronIn, float dt);
    float getSpeed();
    float getClimbRate();
    float getPitch();
    float getRoll();
    float getHeading();
    boolean isStalling();
}
