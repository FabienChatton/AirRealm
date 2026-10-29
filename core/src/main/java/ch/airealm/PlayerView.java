package ch.airealm;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class PlayerView {
    private final Airplane airplane;
    private final Airplane enemyAirplane;
    private final PerspectiveCamera camera;
    private final Stage stage;
    private final Label infoLabel;
    private final BitmapFont font;

    private float freeCamYaw = 0f;
    private float freeCamPitch = 0f;
    private float fireCooldown = 0f;

    private static final float MAX_LOOK_PITCH = MathUtils.PI / 3f;
    private static final float LOOK_RECENTER = 8f;
    private static final float FIRE_COOLDOWN = 0.12f;

    private int viewWidth;
    private int viewHeight;

    public PlayerView(Airplane airplane, Airplane enemyAirplane, BitmapFont font, int width, int height) {
        this.airplane = airplane;
        this.enemyAirplane = enemyAirplane;
        this.viewWidth = width;
        this.viewHeight = height;

        this.camera = new PerspectiveCamera(70f, width, height);
        this.camera.near = 0.1f;
        this.camera.far = 5000f;
        this.camera.update();

        this.stage = new Stage(new ScreenViewport());
        this.infoLabel = new Label(null, new Label.LabelStyle(font, Color.WHITE));
        this.stage.addActor(infoLabel);
        this.font = font;
    }

    public void update(PlayerController input, float dt) {
        if (airplane.isDead()) {
            return;
        }

        airplane.setThrottle(airplane.getThrottle() + input.getThrottleChange(dt));
        airplane.update(input.getPitchInput(), input.getRudderInput(), input.getAileronInput(), dt);

        float yawDelta = input.getFreeCamYawChange(dt);
        float pitchDelta = input.getFreeCamPitchChange(dt);
        if (yawDelta != 0f || pitchDelta != 0f) {
            freeCamYaw += yawDelta;
            freeCamPitch = MathUtils.clamp(freeCamPitch + pitchDelta, -MAX_LOOK_PITCH, MAX_LOOK_PITCH);
            if (freeCamYaw > MathUtils.PI) freeCamYaw -= 2f * MathUtils.PI;
            if (freeCamYaw < -MathUtils.PI) freeCamYaw += 2f * MathUtils.PI;
        } else if (input.isRecenteringRequested()) {
            freeCamYaw = approachZero(freeCamYaw, LOOK_RECENTER * dt);
            freeCamPitch = approachZero(freeCamPitch, LOOK_RECENTER * dt);
        }

        if (fireCooldown > 0f) fireCooldown -= dt;
    }

    public boolean canShoot(PlayerController input) {
        if (airplane.isDead()) return false;
        if (input.isShootPressed() && fireCooldown <= 0f) {
            fireCooldown = FIRE_COOLDOWN;
            return true;
        }
        return false;
    }

    public void updateCamera(Vector3 forward, Vector3 up) {
        Vector3 planePos = new Vector3();
        airplane.getPosition(planePos);
        Vector3 cockpitPos = planePos.cpy().add(up.cpy().scl(0.7f));
        camera.position.set(cockpitPos);

        Vector3 lookDir = forward.cpy();
        if (Math.abs(freeCamYaw) > 0.0001f || Math.abs(freeCamPitch) > 0.0001f) {
            Vector3 rightLocal = new Vector3(forward).crs(up).nor();
            lookDir.rotate(up, MathUtils.radiansToDegrees * freeCamYaw);
            lookDir.rotate(rightLocal, MathUtils.radiansToDegrees * freeCamPitch);
        }
        camera.lookAt(cockpitPos.cpy().add(lookDir.scl(20f)));
        camera.up.set(up);
        camera.update();
    }

    public void updateViewport(int width, int height, int screenX) {
        this.viewWidth = width;
        this.viewHeight = height;
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        stage.getViewport().update(width, height, true);
        stage.getViewport().setScreenBounds(screenX, 0, width, height);
    }

    public void drawHUD(String prefix, Vector3 forward, ShapeRenderer shapeRenderer) {
        Vector3 planePos = new Vector3();
        airplane.getPosition(planePos);

        if (airplane.isDead()) {
            infoLabel.setText("=== MORT ! ===");
            infoLabel.setPosition(viewWidth / 2f - 50f, viewHeight / 2f);
            stage.draw();
            return;
        }

        // HUD normal en haut à gauche de la vue
        infoLabel.setPosition(10, 80);
        Vector3 velNow = airplane.getBody().getLinearVelocity();
        float speed = velNow.len();

        infoLabel.setText(
                prefix + "\n" +
                "SPD " + String.format("%6.1f m/s, %6.1f km/h", speed, speed * 3.6f) + "\n" +
                "THROTTLE " + String.format("%6.1f", airplane.getThrottle()) + "\n" +
                "ALT " + String.format("%6.1f m", planePos.y) + "\n" +
                "CLB " + String.format("%+6.1f m/s", airplane.getFlightModel().getClimbRate()) + "\n" +
                "PIT " + String.format("%6.1f deg", airplane.getFlightModel().getPitch()) + "\n" +
                "ROL " + String.format("%6.1f deg", airplane.getFlightModel().getRoll()) + "\n" +
                "HDG " + String.format("%5.0f deg", airplane.getFlightModel().getHeading()) + "\n" +
                (airplane.getFlightModel().isStalling() ? "!!! STALL !!!" : ""));

        Vector3 reticleWorldPos = planePos.cpy().add(forward.cpy().scl(200f));
        Vector3 reticuleScreenPos = new Vector3(reticleWorldPos);
        camera.project(reticuleScreenPos);

        if (reticuleScreenPos.z >= 0f && reticuleScreenPos.z <= 1f) {
            Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
            Gdx.gl.glBlendFunc(Gdx.gl.GL_SRC_ALPHA, Gdx.gl.GL_ONE_MINUS_SRC_ALPHA);

            shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            float rxp = reticuleScreenPos.x / 2f;
            float ryp = reticuleScreenPos.y;

            shapeRenderer.setColor(Color.YELLOW);
            shapeRenderer.circle(rxp, ryp, 25f);
            shapeRenderer.circle(rxp, ryp, 3f);
            shapeRenderer.line(rxp - 40f, ryp, rxp - 12f, ryp);
            shapeRenderer.line(rxp + 12f, ryp, rxp + 40f, ryp);
            shapeRenderer.line(rxp, ryp - 40f, rxp, ryp - 12f);
            shapeRenderer.line(rxp, ryp + 12f, rxp, ryp + 40f);
            shapeRenderer.end();
            Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
        }


        if (enemyAirplane != null && !enemyAirplane.isDead()) {
            Vector3 enemyPos = new Vector3();
            enemyAirplane.getPosition(enemyPos);
            Vector3 enemyScreenPos = new Vector3(enemyPos);
            camera.project(enemyScreenPos);

            enemyScreenPos.x /= 2f;

            if (enemyScreenPos.z >= 0f && enemyScreenPos.z <= 1f) {
                Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
                Gdx.gl.glBlendFunc(Gdx.gl.GL_SRC_ALPHA, Gdx.gl.GL_ONE_MINUS_SRC_ALPHA);
                shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
                shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                shapeRenderer.setColor(Color.ORANGE);

                // Trait reliant le centre de l'écran à l'ennemi
                shapeRenderer.line(reticuleScreenPos.x / 2, reticuleScreenPos.y, enemyScreenPos.x, enemyScreenPos.y);

                // Cercle autour de l'ennemi
                shapeRenderer.circle(enemyScreenPos.x, enemyScreenPos.y, 15f);

                stage.getBatch().begin();
                float dstEnemyPos = planePos.dst(enemyPos);
                String dstEnemyPosString = String.format("%6.1f", dstEnemyPos);
                int charWidth = 8;
                float dstPosX = enemyScreenPos.x - (charWidth * dstEnemyPosString.length() / 2f);
                font.draw(stage.getBatch(), dstEnemyPosString, dstPosX, enemyScreenPos.y + 30);
                stage.getBatch().end();
                shapeRenderer.end();
                Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
            }
        }


        stage.draw();
    }

    private float approachZero(float v, float step) {
        if (v > step) return v - step;
        if (v < -step) return v + step;
        return 0f;
    }

    public PerspectiveCamera getCamera() { return camera; }

    public void dispose() {
        stage.dispose();
    }
}
