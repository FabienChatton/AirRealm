package ch.airealm;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

public class KeyboardController implements PlayerController {

    private final boolean useAlternativeKeys; // Permet de mapper ZQSD pour J1 et Flèches pour J2 si besoin

    public KeyboardController(boolean useAlternativeKeys) {
        this.useAlternativeKeys = useAlternativeKeys;
    }

    @Override
    public float getPitchInput() {
        if (!useAlternativeKeys) {
            if (Gdx.input.isKeyPressed(Input.Keys.W)) return -1f;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) return 1f;
        } else {
            if (Gdx.input.isKeyPressed(Input.Keys.UP)) return -1f;
            if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) return 1f;
        }
        return 0f;
    }

    @Override
    public float getRudderInput() {
        if (!useAlternativeKeys) {
            if (Gdx.input.isKeyPressed(Input.Keys.Q)) return 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.E)) return -1f;
        } else {
            if (Gdx.input.isKeyPressed(Input.Keys.NUMPAD_7)) return 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.NUMPAD_9)) return -1f;
        }
        return 0f;
    }

    @Override
    public float getAileronInput() {
        if (!useAlternativeKeys) {
            if (Gdx.input.isKeyPressed(Input.Keys.A)) return 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) return -1f;
        } else {
            if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) return 1f;
            if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) return -1f;
        }
        return 0f;
    }

    @Override
    public float getThrottleChange(float dt) {
        if (!useAlternativeKeys) {
            if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) return 1.5f * dt;
            if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) return -1.5f * dt;
        } else {
            if (Gdx.input.isKeyPressed(Input.Keys.NUMPAD_8)) return 1.5f * dt;
            if (Gdx.input.isKeyPressed(Input.Keys.NUMPAD_2)) return -1.5f * dt;
        }
        return 0f;
    }

    @Override
    public boolean isShootPressed() {
        return Gdx.input.isKeyPressed(useAlternativeKeys ? Input.Keys.NUMPAD_0 : Input.Keys.SPACE);
    }

    @Override
    public float getFreeCamYawChange(float dt) { return 0f; }

    @Override
    public float getFreeCamPitchChange(float dt) { return 0f; }

    @Override
    public boolean isRecenteringRequested() { return false; }
}
