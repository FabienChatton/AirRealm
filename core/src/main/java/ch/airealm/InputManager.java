package ch.airealm;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.utils.Array;

public class InputManager {

    private PlayerController player1Input;
    private PlayerController player2Input;

    public InputManager() {
        updateControllers();
    }

    public void updateControllers() {
        Array<Controller> connectedControllers = Controllers.getControllers();
        int count = connectedControllers.size;

        if (count == 0) {
            player1Input = new KeyboardController(false);
            player2Input = new KeyboardController(true);
        } else if (count == 1) {
            player1Input = new GamepadController(connectedControllers.get(0));
            player2Input = new KeyboardController(false);
        } else {
            player1Input = new GamepadController(connectedControllers.get(0));
            player2Input = new GamepadController(connectedControllers.get(1));
        }
    }

    public PlayerController getPlayer1Input() {
        return player1Input;
    }

    public PlayerController getPlayer2Input() {
        return player2Input;
    }
}
