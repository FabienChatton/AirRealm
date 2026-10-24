package ch.airealm;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.ModelLoader;
import com.badlogic.gdx.controllers.ControllerAdapter;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.*;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

public class Main extends ApplicationAdapter {

    private static final float INITIAL_SPEED = 100f;
    private static final int MAX_BULLETS = 48;
    private static final float BULLET_SPEED = 300f;
    private static final float BULLET_SIZE = 0.5f;
    private static final float BULLET_LIFETIME = 4f;
    private static final float FIRE_COOLDOWN = 0.12f;
    private static final float BULLET_RESTITUTION = 0.7f;
    private static final float LOOK_RECENTER = 8f;
    private static final float MAX_LOOK_PITCH = MathUtils.PI / 3f;
    private static final float SKY_RADIUS = 2500f;

    private static final Color SKY_COLOR = new Color(0.45f, 0.7f, 1f, 1f);
    private static final Color GROUND_COLOR = new Color(0.3f, 0.55f, 0.25f, 1f);
    private static final Color BULLET_COLOR = Color.RED;

    private btDiscreteDynamicsWorld world;
    private btDefaultCollisionConfiguration collisionConfiguration;
    private btCollisionDispatcher dispatcher;
    private btDbvtBroadphase broadphase;
    private btSequentialImpulseConstraintSolver solver;

    private btCollisionShape groundShape;
    private btRigidBody groundBody;

    // Caméras dédiées à chaque joueur pour le split-screen
    private PerspectiveCamera cameraP1;
    private PerspectiveCamera cameraP2;

    private ModelBatch batch;
    private Environment environment;
    private Environment skyEnvironment;
    private ShapeRenderer shapeRenderer;

    private Model skyModel;
    private ModelInstance skyInstance;
    private Texture skyTexture;

    // Stages / HUDs séparés pour les deux joueurs
    private Stage stageP1;
    private Stage stageP2;
    private Label infoLabelP1;
    private Label infoLabelP2;

    private Model spitModel;
    private Model groundModel;
    private ModelInstance groundInstance;

    // Joueurs
    private Airplane player1;
    private Airplane player2;
    private InputManager inputManager;

    // Mitrailleuse
    private Model bulletModel;
    private final btRigidBody[] bulletBodies = new btRigidBody[MAX_BULLETS];
    private final btSphereShape[] bulletShapes = new btSphereShape[MAX_BULLETS];
    private final ModelInstance[] bulletInstances = new ModelInstance[MAX_BULLETS];
    private final float[] bulletLife = new float[MAX_BULLETS];
    private float fireCooldownP1;
    private float fireCooldownP2;

    // Freecam séparée pour P1 et P2
    private float freeCamYawP1, freeCamPitchP1;
    private float freeCamYawP2, freeCamPitchP2;

    private final Quaternion scratchRot = new Quaternion();
    private final Vector3 scratchA = new Vector3();
    private final Vector3 scratchB = new Vector3();
    private final Vector3 scratchC = new Vector3();
    private final Matrix4 transformMatrix = new Matrix4();

    @Override
    public void create() {
        Bullet.init();

        BitmapFont bitmapFont = new BitmapFont();

        // UI / HUD Joueur 1
        stageP1 = new Stage(new ScreenViewport());
        infoLabelP1 = new Label(null, new Label.LabelStyle(bitmapFont, Color.WHITE));
        infoLabelP1.setPosition(10, 80);
        stageP1.addActor(infoLabelP1);

        // UI / HUD Joueur 2
        stageP2 = new Stage(new ScreenViewport());
        infoLabelP2 = new Label(null, new Label.LabelStyle(bitmapFont, Color.WHITE));
        infoLabelP2.setPosition(10, 80);
        stageP2.addActor(infoLabelP2);

        // Initialisation des deux caméras
        int halfWidth = Gdx.graphics.getWidth() / 2;
        int height = Gdx.graphics.getHeight();

        cameraP1 = new PerspectiveCamera(70f, halfWidth, height);
        cameraP1.near = 0.1f;
        cameraP1.far = 5000f;
        cameraP1.update();

        cameraP2 = new PerspectiveCamera(70f, halfWidth, height);
        cameraP2.near = 0.1f;
        cameraP2.far = 5000f;
        cameraP2.update();

        shapeRenderer = new ShapeRenderer();

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        DirectionalLight sun = new DirectionalLight().set(1f, 1f, 1f, -1f, -0.8f, -0.2f);
        environment.add(sun);

        batch = new ModelBatch();

        ModelBuilder modelBuilder = new ModelBuilder();
        ModelLoader loader = new ObjLoader();
        spitModel = loader.loadModel(Gdx.files.internal("spit.obj"));

        groundModel = modelBuilder.createBox(
            100000f, 1f, 100000f,
            new Material(ColorAttribute.createDiffuse(GROUND_COLOR)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
        );
        groundInstance = new ModelInstance(groundModel);
        groundInstance.transform.setToTranslation(0f, -0.5f, 0f);

        skyTexture = new Texture(Gdx.files.internal("pizzo_pernice_puresky_1k.png"));
        skyModel = buildSkySphere(SKY_RADIUS, skyTexture);
        skyInstance = new ModelInstance(skyModel);
        skyEnvironment = new Environment();
        skyEnvironment.set(new ColorAttribute(ColorAttribute.AmbientLight, 1f, 1f, 1f, 1f));

        collisionConfiguration = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfiguration);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfiguration);
        world.setGravity(new Vector3(0f, -9.81f, 0f));

        groundShape = new btBoxShape(new Vector3(5000f, 0.5f, 5000f));
        groundBody = new btRigidBody(0f, null, groundShape);
        groundBody.setWorldTransform(groundInstance.transform);
        world.addRigidBody(groundBody);

        // Initialisation du gestionnaire de manettes / clavier
        inputManager = new InputManager();
        Controllers.addListener(new ControllerAdapter() {
            @Override
            public void connected(com.badlogic.gdx.controllers.Controller controller) {
                inputManager.updateControllers();
            }

            @Override
            public void disconnected(com.badlogic.gdx.controllers.Controller controller) {
                inputManager.updateControllers();
            }
        });

        Matrix4 p1Pos = new Matrix4().idt().translate(0, 1000f, 0f);
        p1Pos.rotate(Vector3.Y, 180);
        player1 = new Airplane(spitModel, new SpitFireFlightModel(), world, p1Pos, -INITIAL_SPEED);

        Matrix4 p2Pos = new Matrix4().idt().translate(0, 1000f, 1000f);
        p1Pos.rotate(Vector3.Y, 0);
        player2 = new Airplane(spitModel, new SpitFireFlightModel(), world, p2Pos, INITIAL_SPEED);

        // Balles
        bulletModel = modelBuilder.createBox(
            BULLET_SIZE, BULLET_SIZE, BULLET_SIZE,
            new Material(ColorAttribute.createDiffuse(BULLET_COLOR)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
        );
        for (int i = 0; i < MAX_BULLETS; i++) {
            bulletInstances[i] = new ModelInstance(bulletModel);
            bulletShapes[i] = new btSphereShape(BULLET_SIZE / 2f);
            Vector3 bulletInertia = new Vector3(0f, 0f, 0f);
            bulletShapes[i].calculateLocalInertia(1f, bulletInertia);
            btRigidBody.btRigidBodyConstructionInfo info = new btRigidBody.btRigidBodyConstructionInfo(0.05f, null, bulletShapes[i], bulletInertia);
            info.setLinearDamping(0f);
            info.setAngularDamping(1f);
            bulletBodies[i] = new btRigidBody(info);
            bulletBodies[i].setRestitution(BULLET_RESTITUTION);
            bulletBodies[i].setFriction(0.05f);
            bulletBodies[i].setWorldTransform(new Matrix4().setTranslation(0f, -1000f, 0f));
            world.addRigidBody(bulletBodies[i]);
        }

        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private Model buildSkySphere(float radius, Texture skyTexture) {
        int segX = 32, segY = 16;
        ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();

        Material material = new Material(TextureAttribute.createDiffuse(skyTexture), IntAttribute.createCullFace(GL20.GL_NONE));
        MeshPartBuilder meshPartBuilder = modelBuilder.part(
            "sky",
            GL20.GL_TRIANGLES,
            VertexAttribute.Position().usage | VertexAttribute.Normal().usage | VertexAttribute.TexCoords(0).usage,
            material
        );

        for (int iy = 0; iy <= segY; iy++) {
            float theta = iy * MathUtils.PI / segY;
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);
            float v = 1f - (float) iy / segY;

            for (int ix = 0; ix <= segX; ix++) {
                float phi = ix * 2f * MathUtils.PI / segX;
                float x = -sinTheta * (float) Math.cos(phi);
                float y = cosTheta;
                float z = sinTheta * (float) Math.sin(phi);

                meshPartBuilder.vertex(
                    new Vector3(x * radius, y * radius, z * radius),
                    new Vector3(-x, -y, -z),
                    null,
                    new com.badlogic.gdx.math.Vector2(1f - (float) ix / segX, v)
                );
            }
        }

        for (int iy = 0; iy < segY; iy++) {
            for (int ix = 0; ix < segX; ix++) {
                short a = (short) (iy * (segX + 1) + ix);
                short b = (short) (a + 1);
                short c = (short) (a + (segX + 1));
                short d = (short) (c + 1);

                meshPartBuilder.triangle(a, c, b);
                meshPartBuilder.triangle(d, c, b);
            }
        }

        return modelBuilder.end();
    }

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);

        PlayerController inputP1 = inputManager.getPlayer1Input();
        PlayerController inputP2 = inputManager.getPlayer2Input();

        // Mises à jour des gaz
        player1.setThrottle(player1.getThrottle() + inputP1.getThrottleChange(dt));
        player2.setThrottle(player2.getThrottle() + inputP2.getThrottleChange(dt));

        // Mises à jour physiques des avions
        player1.update(inputP1.getPitchInput(), inputP1.getRudderInput(), inputP1.getAileronInput(), dt);
        player2.update(inputP2.getPitchInput(), inputP2.getRudderInput(), inputP2.getAileronInput(), dt);

        // Orientations locales P1
        player1.getBody().getWorldTransform(transformMatrix);
        transformMatrix.getRotation(scratchRot);
        Vector3 forwardP1 = scratchB.set(0f, 0f, -1f).mul(scratchRot);
        Vector3 upP1 = scratchC.set(0f, 1f, 0f).mul(scratchRot);

        // Orientations locales P2
        player2.getBody().getWorldTransform(transformMatrix);
        transformMatrix.getRotation(scratchRot);
        Vector3 forwardP2 = scratchA.set(0f, 0f, -1f).mul(scratchRot);
        Vector3 upP2 = new Vector3(0f, 1f, 0f).mul(scratchRot);

        // Gestion tirs
        fireCooldownP1 -= dt;
        fireCooldownP2 -= dt;

        if (inputP1.isShootPressed() && fireCooldownP1 <= 0f) {
            fireCooldownP1 = FIRE_COOLDOWN;
            shoot(player1, forwardP1);
        }
        if (inputP2.isShootPressed() && fireCooldownP2 <= 0f) {
            fireCooldownP2 = FIRE_COOLDOWN;
            shoot(player2, forwardP2);
        }

        // Freecam P1
        float yawDeltaP1 = inputP1.getFreeCamYawChange(dt);
        float pitchDeltaP1 = inputP1.getFreeCamPitchChange(dt);
        if (yawDeltaP1 != 0f || pitchDeltaP1 != 0f) {
            freeCamYawP1 += yawDeltaP1;
            freeCamPitchP1 = MathUtils.clamp(freeCamPitchP1 + pitchDeltaP1, -MAX_LOOK_PITCH, MAX_LOOK_PITCH);
            if (freeCamYawP1 > MathUtils.PI) freeCamYawP1 -= 2f * MathUtils.PI;
            if (freeCamYawP1 < -MathUtils.PI) freeCamYawP1 += 2f * MathUtils.PI;
        } else if (inputP1.isRecenteringRequested()) {
            freeCamYawP1 = approachZero(freeCamYawP1, LOOK_RECENTER * dt);
            freeCamPitchP1 = approachZero(freeCamPitchP1, LOOK_RECENTER * dt);
        }

        // Freecam P2
        float yawDeltaP2 = inputP2.getFreeCamYawChange(dt);
        float pitchDeltaP2 = inputP2.getFreeCamPitchChange(dt);
        if (yawDeltaP2 != 0f || pitchDeltaP2 != 0f) {
            freeCamYawP2 += yawDeltaP2;
            freeCamPitchP2 = MathUtils.clamp(freeCamPitchP2 + pitchDeltaP2, -MAX_LOOK_PITCH, MAX_LOOK_PITCH);
            if (freeCamYawP2 > MathUtils.PI) freeCamYawP2 -= 2f * MathUtils.PI;
            if (freeCamYawP2 < -MathUtils.PI) freeCamYawP2 += 2f * MathUtils.PI;
        } else if (inputP2.isRecenteringRequested()) {
            freeCamYawP2 = approachZero(freeCamYawP2, LOOK_RECENTER * dt);
            freeCamPitchP2 = approachZero(freeCamPitchP2, LOOK_RECENTER * dt);
        }

        world.stepSimulation(dt, 5, 1f / 60f);

        for (int i = 0; i < MAX_BULLETS; i++) {
            if (bulletLife[i] <= 0f) continue;
            bulletLife[i] -= dt;
            if (bulletLife[i] <= 0f) {
                scratchA.set(0f, 0f, 0f);
                bulletBodies[i].setLinearVelocity(scratchA);
                bulletBodies[i].setAngularVelocity(scratchA);
                bulletBodies[i].setWorldTransform(new Matrix4().setTranslation(0f, -1000f, 0f));
                continue;
            }
            bulletBodies[i].getWorldTransform(bulletInstances[i].transform);
        }

        // --- Mise à jour de la Caméra P1 ---
        Vector3 planePosP1 = new Vector3();
        player1.getPosition(planePosP1);
        Vector3 cockpitPosP1 = planePosP1.cpy().add(upP1.cpy().scl(0.7f));
        cameraP1.position.set(cockpitPosP1);

        Vector3 lookDirP1 = forwardP1.cpy();
        if (Math.abs(freeCamYawP1) > 0.0001f || Math.abs(freeCamPitchP1) > 0.0001f) {
            Vector3 rightLocal = scratchA.set(forwardP1).crs(upP1).nor();
            lookDirP1.rotate(upP1, MathUtils.radiansToDegrees * freeCamYawP1);
            lookDirP1.rotate(rightLocal, MathUtils.radiansToDegrees * freeCamPitchP1);
        }
        cameraP1.lookAt(cockpitPosP1.cpy().add(lookDirP1.scl(20f)));
        cameraP1.up.set(upP1);
        cameraP1.update();

        // --- Mise à jour de la Caméra P2 ---
        Vector3 planePosP2 = new Vector3();
        player2.getPosition(planePosP2);
        Vector3 cockpitPosP2 = planePosP2.cpy().add(upP2.cpy().scl(0.7f));
        cameraP2.position.set(cockpitPosP2);

        Vector3 lookDirP2 = forwardP2.cpy();
        if (Math.abs(freeCamYawP2) > 0.0001f || Math.abs(freeCamPitchP2) > 0.0001f) {
            Vector3 rightLocal = scratchA.set(forwardP2).crs(upP2).nor();
            lookDirP2.rotate(upP2, MathUtils.radiansToDegrees * freeCamYawP2);
            lookDirP2.rotate(rightLocal, MathUtils.radiansToDegrees * freeCamPitchP2);
        }
        cameraP2.lookAt(cockpitPosP2.cpy().add(lookDirP2.scl(20f)));
        cameraP2.up.set(upP2);
        cameraP2.update();

        // Nettoyage de l'écran global
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        int halfW = screenW / 2;

        // ==========================================
        // RENDU JOUEUR 1 (GAUCHE)
        // ==========================================
        Gdx.gl.glViewport(0, 0, halfW, screenH);
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(0, 0, halfW, screenH);

        Gdx.gl.glClearColor(SKY_COLOR.r, SKY_COLOR.g, SKY_COLOR.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        batch.begin(cameraP1);
        skyInstance.transform.setToTranslation(cameraP1.position.x, cameraP1.position.y, cameraP1.position.z);
        batch.render(skyInstance, skyEnvironment);
        batch.render(player1.getInstance(), environment);
        batch.render(player2.getInstance(), environment);
        for (int i = 0; i < MAX_BULLETS; i++) {
            if (bulletLife[i] > 0f) batch.render(bulletInstances[i], environment);
        }
        batch.render(groundInstance, environment);
        batch.end();

        renderHUD(player1, "P1", infoLabelP1, stageP1, cameraP1, forwardP1, planePosP1);

        // ==========================================
        // RENDU JOUEUR 2 (DROITE)
        // ==========================================
        Gdx.gl.glViewport(halfW, 0, halfW, screenH);
        Gdx.gl.glScissor(halfW, 0, halfW, screenH);

        Gdx.gl.glClearColor(SKY_COLOR.r, SKY_COLOR.g, SKY_COLOR.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        batch.begin(cameraP2);
        skyInstance.transform.setToTranslation(cameraP2.position.x, cameraP2.position.y, cameraP2.position.z);
        batch.render(skyInstance, skyEnvironment);
        batch.render(player1.getInstance(), environment);
        batch.render(player2.getInstance(), environment);
        for (int i = 0; i < MAX_BULLETS; i++) {
            if (bulletLife[i] > 0f) batch.render(bulletInstances[i], environment);
        }
        batch.render(groundInstance, environment);
        batch.end();

        renderHUD(player2, "P2", infoLabelP2, stageP2, cameraP2, forwardP2, planePosP2);

        // Réinitialisation du viewport global pour éviter les effets de bord
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glViewport(0, 0, screenW, screenH);
    }

    private void renderHUD(Airplane player, String labelPrefix, Label label, Stage stage, PerspectiveCamera cam, Vector3 forward, Vector3 planePos) {
        Vector3 velNow = player.getBody().getLinearVelocity();
        float speed = velNow.len();

        label.setText(
            labelPrefix + " SPD " + String.format("%6.1f m/s, %6.1f km/h", speed, speed * 3.6f) + "\n" +
                "THROTTLE " + String.format("%6.1f", player.getThrottle()) + "\n" +
                "ALT " + String.format("%6.1f m", planePos.y) + "\n" +
                "CLB " + String.format("%+6.1f m/s", player.getFlightModel().getClimbRate()) + "\n" +
                "PIT " + String.format("%6.1f deg", player.getFlightModel().getPitch()) + "\n" +
                "ROL " + String.format("%6.1f deg", player.getFlightModel().getRoll()) + "\n" +
                "HDG " + String.format("%5.0f deg", player.getFlightModel().getHeading()) + "\n" +
                (player.getFlightModel().isStalling() ? "!!! STALL !!!" : ""));

        // Réticule
        Vector3 reticleWorldPos = planePos.cpy().add(forward.cpy().scl(200f));
        Vector3 screenPos = new Vector3(reticleWorldPos);
        cam.project(screenPos);

        if (screenPos.z >= 0f && screenPos.z <= 1f) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            float rxp = screenPos.x / 2f;
            float ryp = screenPos.y;

            shapeRenderer.setColor(Color.YELLOW);
            shapeRenderer.circle(rxp, ryp, 25f);
            shapeRenderer.circle(rxp, ryp, 3f);
            shapeRenderer.line(rxp - 40f, ryp, rxp - 12f, ryp);
            shapeRenderer.line(rxp + 12f, ryp, rxp + 40f, ryp);
            shapeRenderer.line(rxp, ryp - 40f, rxp, ryp - 12f);
            shapeRenderer.line(rxp, ryp + 12f, rxp, ryp + 40f);
            shapeRenderer.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
        stage.draw();
    }

    private void shoot(Airplane shooter, Vector3 forward) {
        for (int i = 0; i < MAX_BULLETS; i++) {
            if (bulletLife[i] > 0f) continue;
            bulletLife[i] = BULLET_LIFETIME;
            shooter.getPosition(scratchA);
            scratchA.add(scratchB.set(forward).scl(3f));
            bulletBodies[i].setWorldTransform(new Matrix4().setTranslation(scratchA));
            scratchA.set(0f, 0f, 0f);
            bulletBodies[i].setAngularVelocity(scratchA);
            Vector3 vel = shooter.getBody().getLinearVelocity();
            bulletBodies[i].setLinearVelocity(scratchA.set(
                vel.x + forward.x * BULLET_SPEED,
                vel.y + forward.y * BULLET_SPEED,
                vel.z + forward.z * BULLET_SPEED));
            break;
        }
    }

    private float approachZero(float v, float step) {
        if (v > step) return v - step;
        if (v < -step) return v + step;
        return 0f;
    }

    @Override
    public void resize(int width, int height) {
        int halfWidth = width / 2;

        cameraP1.viewportWidth = halfWidth;
        cameraP1.viewportHeight = height;
        cameraP1.update();

        cameraP2.viewportWidth = halfWidth;
        cameraP2.viewportHeight = height;
        cameraP2.update();

        stageP1.getViewport().update(halfWidth, height, true);
        stageP2.getViewport().update(halfWidth, height, true);
    }

    @Override
    public void dispose() {
        batch.dispose();
        spitModel.dispose();
        bulletModel.dispose();
        groundModel.dispose();
        skyModel.dispose();
        skyTexture.dispose();

        world.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfiguration.dispose();

        player1.dispose(world);
        player2.dispose(world);

        for (int i = 0; i < MAX_BULLETS; i++) {
            bulletBodies[i].dispose();
            bulletShapes[i].dispose();
        }
        groundShape.dispose();
        groundBody.dispose();
        shapeRenderer.dispose();
        stageP1.dispose();
        stageP2.dispose();
    }
}
