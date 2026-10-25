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

public class Main extends ApplicationAdapter {

    private static final float INITIAL_SPEED = 100f;
    private static final float SKY_RADIUS = 2500f;

    private static final Color SKY_COLOR = new Color(0.45f, 0.7f, 1f, 1f);
    private static final Color GROUND_COLOR = new Color(0.3f, 0.55f, 0.25f, 1f);

    private btDiscreteDynamicsWorld world;
    private btDefaultCollisionConfiguration collisionConfiguration;
    private btCollisionDispatcher dispatcher;
    private btDbvtBroadphase broadphase;
    private btSequentialImpulseConstraintSolver solver;

    private btCollisionShape groundShape;
    private btRigidBody groundBody;

    private ModelBatch batch;
    private Environment environment;
    private Environment skyEnvironment;
    private ShapeRenderer shapeRenderer;

    private Model skyModel;
    private ModelInstance skyInstance;
    private Texture skyTexture;

    private Model spitModel;
    private Model groundModel;
    private ModelInstance groundInstance;

    private Airplane player1;
    private Airplane player2;
    private PlayerView viewP1;
    private PlayerView viewP2;
    private InputManager inputManager;
    private BulletManager bulletManager;

    private final Quaternion scratchRot = new Quaternion();
    private final Matrix4 transformMatrix = new Matrix4();

    @Override
    public void create() {
        Bullet.init();
        BitmapFont bitmapFont = new BitmapFont();

        int halfWidth = Gdx.graphics.getWidth() / 2;
        int height = Gdx.graphics.getHeight();

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
            5000f, 1f, 5000f,
            new Material(ColorAttribute.createDiffuse(GROUND_COLOR)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
        );
        groundInstance = new ModelInstance(groundModel);
        groundInstance.transform.setToTranslation(0f, -0.5f, 0f);

        skyTexture = new Texture(Gdx.files.internal("pizzo_pernice_puresky_1k_2.png"));
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

        // Joueur 1
        Matrix4 p1Transform = new Matrix4().idt().translate(0, 500f, 0f);
        p1Transform.rotate(Vector3.Y, 180f);
        player1 = new Airplane(spitModel, new SpitFireFlightModel(), world, p1Transform, -INITIAL_SPEED);

        // Joueur 2
        Matrix4 p2Transform = new Matrix4().idt().translate(0, 1000f, 1000f);
        p2Transform.rotate(Vector3.Y, 0f);
        player2 = new Airplane(spitModel, new SpitFireFlightModel(), world, p2Transform, INITIAL_SPEED);


        viewP1 = new PlayerView(player1, player2, bitmapFont, halfWidth, height);
        viewP2 = new PlayerView(player2, player1, bitmapFont, halfWidth, height);

        // Gestionnaire de tirs
        bulletManager = new BulletManager(modelBuilder, world);

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

        // Mises à jour entrées et vues des joueurs
        viewP1.update(inputManager.getPlayer1Input(), dt);
        viewP2.update(inputManager.getPlayer2Input(), dt);

        // Orientations P1
        player1.getBody().getWorldTransform(transformMatrix);
        transformMatrix.getRotation(scratchRot);
        Vector3 forwardP1 = new Vector3(0f, 0f, -1f).mul(scratchRot);
        Vector3 upP1 = new Vector3(0f, 1f, 0f).mul(scratchRot);

        // Orientations P2
        player2.getBody().getWorldTransform(transformMatrix);
        transformMatrix.getRotation(scratchRot);
        Vector3 forwardP2 = new Vector3(0f, 0f, -1f).mul(scratchRot);
        Vector3 upP2 = new Vector3(0f, 1f, 0f).mul(scratchRot);


        if (viewP1.canShoot(inputManager.getPlayer1Input())) {
            bulletManager.shoot(player1, forwardP1, true);
        }
        if (viewP2.canShoot(inputManager.getPlayer2Input())) {
            bulletManager.shoot(player2, forwardP2, false);
        }

        // Simulation physique
        world.stepSimulation(dt, 5, 1f / 60f);
        bulletManager.update(dt, player1, player2);

        // Mise à jour des caméras
        viewP1.updateCamera(forwardP1, upP1);
        viewP2.updateCamera(forwardP2, upP2);

        // Rendu Split-Screen
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        int halfW = screenW / 2;

        // Rendu Joueur 1 (Gauche)
        Gdx.gl.glViewport(0, 0, halfW, screenH);
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(0, 0, halfW, screenH);

        renderPlayer(viewP1);

        viewP1.drawHUD("P1", forwardP1, shapeRenderer);

        // Rendu Joueur 2 (Droite)
        Gdx.gl.glViewport(halfW, 0, halfW, screenH);
        Gdx.gl.glScissor(halfW, 0, halfW, screenH);

        renderPlayer(viewP2);

        viewP2.drawHUD("P2", forwardP2, shapeRenderer);

        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glViewport(0, 0, screenW, screenH);
    }

    private void renderPlayer(PlayerView viewP2) {
        Gdx.gl.glClearColor(SKY_COLOR.r, SKY_COLOR.g, SKY_COLOR.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        batch.begin(viewP2.getCamera());
        skyInstance.transform.setToTranslation(viewP2.getCamera().position.x, viewP2.getCamera().position.y, viewP2.getCamera().position.z);
        batch.render(skyInstance, skyEnvironment);
        batch.render(player1.getInstance(), environment);
        batch.render(player2.getInstance(), environment);
        bulletManager.render(batch, environment);
        batch.render(groundInstance, environment);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        int halfWidth = width / 2;
        viewP1.updateViewport(halfWidth, height, 0);
        viewP2.updateViewport(halfWidth, height, halfWidth);
    }

    @Override
    public void dispose() {
        batch.dispose();
        spitModel.dispose();
        groundModel.dispose();
        skyModel.dispose();
        skyTexture.dispose();

        bulletManager.dispose();
        world.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfiguration.dispose();

        player1.dispose(world);
        player2.dispose(world);

        shapeRenderer.dispose();
        viewP1.dispose();
        viewP2.dispose();
    }
}
