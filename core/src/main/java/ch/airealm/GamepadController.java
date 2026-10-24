package ch.airealm;

import com.badlogic.gdx.controllers.Controller;

public class GamepadController implements PlayerController {

    private static final float PAD_DEADZONE = 0.12f;
    private static final float LOOK_RATE = 2.5f;

    private final Controller controller;

    public GamepadController(Controller controller) {
        this.controller = controller;
    }

    private float padAxis(float v) {
        if (Math.abs(v) < PAD_DEADZONE) return 0f;
        return (v - Math.signum(v) * PAD_DEADZONE) / (1f - PAD_DEADZONE);
    }

    @Override
    public float getPitchInput() {
        if (controller == null) return 0f;
        return padAxis(controller.getAxis(controller.getMapping().axisLeftY));
    }

    @Override
    public float getRudderInput() {
        if (controller == null) return 0f;
        // Utilisation des axes de gâchettes analogiques (LT / RT)
        float lt = padAxis(controller.getAxis(4));
        float rt = padAxis(controller.getAxis(5));
        if (lt > 0f) return lt;
        if (rt > 0f) return -rt;
        return 0f;
    }

    @Override
    public float getAileronInput() {
        if (controller == null) return 0f;
        return -padAxis(controller.getAxis(controller.getMapping().axisLeftX));
    }

    @Override
    public float getThrottleChange(float dt) {
        if (controller == null) return 0f;
        if (controller.getButton(controller.getMapping().buttonR1)) return 1.5f * dt;
        if (controller.getButton(controller.getMapping().buttonL1)) return -1.5f * dt;
        return 0f;
    }

    @Override
    public boolean isShootPressed() {
        if (controller == null) return false;
        return controller.getButton(controller.getMapping().buttonA);
    }

    @Override
    public float getFreeCamYawChange(float dt) {
        if (controller == null) return 0f;
        float rxp = padAxis(controller.getAxis(controller.getMapping().axisRightX));
        return -rxp * LOOK_RATE * dt;
    }

    @Override
    public float getFreeCamPitchChange(float dt) {
        if (controller == null) return 0f;
        float ryp = padAxis(controller.getAxis(controller.getMapping().axisRightY));
        return -ryp * LOOK_RATE * dt;
    }

    @Override
    public boolean isRecenteringRequested() {
        if (controller == null) return false;
        return controller.getButton(controller.getMapping().buttonRightStick);
    }
}
