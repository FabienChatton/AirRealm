package ch.airealm;

public interface PlayerController {
    float getPitchInput();
    float getRudderInput();
    float getAileronInput();
    float getThrottleChange(float dt);
    boolean isShootPressed();
    float getFreeCamYawChange(float dt);
    float getFreeCamPitchChange(float dt);
    boolean isRecenteringRequested();
}
