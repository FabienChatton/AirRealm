package ch.airealm;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.assets.loaders.ModelLoader;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerListener;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.Bullet;
import com.badlogic.gdx.physics.bullet.collision.btBoxShape;
import com.badlogic.gdx.physics.bullet.collision.btSphereShape;
import com.badlogic.gdx.physics.bullet.collision.btCollisionShape;
import com.badlogic.gdx.physics.bullet.collision.btDefaultCollisionConfiguration;
import com.badlogic.gdx.physics.bullet.collision.btCollisionDispatcher;
import com.badlogic.gdx.physics.bullet.collision.btDbvtBroadphase;
import com.badlogic.gdx.physics.bullet.dynamics.btSequentialImpulseConstraintSolver;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody.btRigidBodyConstructionInfo;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class Main extends ApplicationAdapter {

    // Paramètres de réglage — modèle de vol (masse = 1 : forces = accélérations)
    private static final float PLANE_SIZE = 10f;
    private static final float INITIAL_SPEED = 100;  // départ 360 km/h (vitesse d'équilibre : 1g à incidence 0)
    private static final float THRUST = 8f;           // poussée max (m/s²)
    private static final float WING_COEF = 0.0049f;    // 0.5*rho*S/m : échelle portance/traînée
    private static final float CL0 = 0.2f;           // portance à incidence nulle
    private static final float CL_SLOPE = 10f;      // dCL/dalpha (par rad)
    private static final float ALPHA_STALL = 0.262f;  // incidence de décrochage (~15 deg)
    private static final float STALL_DROP = 3.0f;     // chute de CL au-delà du décrochage (par rad)
    private static final float CD0 = 0.02f;           // traînée parasite
    private static final float CD_INDUCED = 0.002f;    // traînée induite (k*CL^2)
    private static final float STALL_DRAG = 0.008f;     // traînée de décrochage (par rad au-delà)
    private static final float PITCH_EFF = 0.008f;     // moment de cabrage (gouvernail)
    private static final float RUDDER_EFF = 0.005f;    // moment de lacet (gouvernail de queue)
    private static final float CM_ALPHA = -0.030f;     // stabilité longitudinale (par rad) — négatif = stable
    private static final float CN_BETA = -0.003f;     // vent-en-poupe : remet le nez dans l'écoulement
    private static final float CN_A = 0.002f;        // lacet de traînée des ailerons (auto-coordination)
    private static final float DAMP = 0.016f;         // amortissement aéro cabrage/lacet (par vitesse angulaire)

    // Roulis : taux continu au maintien A/D, le bank est conservé à la libération (comportement avion réel)
    private static final float ROLL_RATE = 1.5f;       // taux de roulis établi (rad/s, ~86 deg/s)
    private static final float ROLL_DAMP = 3.3f;       // amortissement de roulis (réponse du taux ~0,2 s)

    // Structure
    private static final float GRAVITY = 9.81f;        // g (m/s²)
    private static final float MAX_G = 6f;             // limite de charge (Spitfire)

    // Adversaire : cube en patrouille circulaire ("tour du monde") pour tester le dogfight
    private static final float ENEMY_RADIUS = 1000f;   // rayon du circuit (m)
    private static final float ENEMY_SPEED = 100f;     // m/s (~360 km/h : même croisière que le joueur)
    private static final float ENEMY_CENTER_X = 1000f; // centre du circuit (position de départ du joueur)
    private static final float ENEMY_CENTER_Z = 1000f;
    private static final float ENEMY_ALTITUDE = 1000f; // altitude de la patrouille (m)
    private static final float ENEMY_BANK = (float) Math.atan((double) ENEMY_SPEED * ENEMY_SPEED / (GRAVITY * ENEMY_RADIUS)); // bank coordonné du virage

    // Mitrailleuse : Espace, les balles rebondissent sur la cible (et le sol)
    private static final int MAX_BULLETS = 24;          // pool de balles (actives + inertes)
    private static final float BULLET_SPEED = 300f;     // vitesse de départ (m/s)
    private static final float BULLET_SIZE = 0.5f;      // côté du cube balle
    private static final float BULLET_LIFETIME = 4f;    // durée de vie (s)
    private static final float FIRE_COOLDOWN = 0.12f;   // cadence (s entre deux tirs)
    private static final float BULLET_RESTITUTION = 0.7f; // rebond des balles sur la cible

    private static final float PAD_DEADZONE = 0.12f;          // zone morte des sticks
    private static final float LOOK_RATE = 2.5f;              // vitesse de la freecam (rad/s)
    private static final float LOOK_RECENTER = 8f;            // vitesse de recentrage (rad/s)
    private static final float MAX_LOOK_PITCH = MathUtils.PI / 3f; // ±60 deg de pitch de la vue

    private static final Color SKY_COLOR = new Color(0.45f, 0.7f, 1f, 1f);
    private static final Color GROUND_COLOR = new Color(0.3f, 0.55f, 0.25f, 1f);
    private static final Color PLANE_COLOR = new Color(0.9f, 0.3f, 0.15f, 1f);
    private static final Color ENEMY_COLOR = new Color(0.15f, 0.35f, 0.85f, 1f);
    private static final Color BULLET_COLOR = new Color(1f, 0.85f, 0.2f, 1f);

    private btDynamicsWorld world;
    private btDefaultCollisionConfiguration collisionConfiguration;
    private btCollisionDispatcher dispatcher;
    private btDbvtBroadphase broadphase;
    private btSequentialImpulseConstraintSolver solver;

    private btRigidBody planeBody;
    private btRigidBodyConstructionInfo planeInfo;
    private btCollisionShape planeShape;
    private btCollisionShape groundShape;
    private btRigidBody groundBody;

    private PerspectiveCamera camera;
    private ModelBatch batch;
    private Environment environment;
    private Environment skyEnvironment;
    private ShapeRenderer shapeRenderer;

    // Ciel : sphère UV "faces intérieures" qui suit la caméra
    private static final float SKY_RADIUS = 2500f;
    private Model skyModel;
    private ModelInstance skyInstance;
    private Texture skyTexture;

    private Stage stage;
    private Label infoLabel;

    private Model planeModel;
    private Model groundModel;
    private ModelInstance planeInstance;
    private ModelInstance groundInstance;

    // Adversaire : mouvement cinématique le long du cercle + corps statique (les balles rebondissent dessus)
    private Model enemyModel;
    private ModelInstance enemyInstance;
    private btRigidBody enemyBody;
    private btBoxShape enemyShape;
    private float enemyAngle = -MathUtils.PI / 2f;    // départ devant le joueur
    private final Vector3 enemyPos = new Vector3();
    private final Vector3 enemyRight = new Vector3();
    private final Vector3 enemyUp = new Vector3();
    private final Vector3 enemyBack = new Vector3();
    private float throttle;

    // Mitrailleuse : pool de balles (sphères dynamiques + cubes graphiques), Espace pour tirer
    private Model bulletModel;
    private final btRigidBody[] bulletBodies = new btRigidBody[MAX_BULLETS];
    private final btSphereShape[] bulletShapes = new btSphereShape[MAX_BULLETS];
    private final ModelInstance[] bulletInstances = new ModelInstance[MAX_BULLETS];
    private final float[] bulletLife = new float[MAX_BULLETS];   // <= 0 : balle inerte
    private float fireCooldown;

    // Freecam (stick droit de la manette) : offsets caméra autour du plan, recentrés au centre du stick
    private float freeCamYaw;
    private float freeCamPitch;

    // Objets temporaires pour éviter les allocations par frame (GC pressure)
    private final Quaternion scratchRot = new Quaternion();
    private final Vector3 scratchA = new Vector3();
    private final Vector3 scratchB = new Vector3();
    private final Vector3 scratchC = new Vector3();
    private final Matrix4 transformMatrix = new Matrix4();

    @Override
    public void create () {
        // 1. Initialisation obligatoire de Bullet
        Bullet.init();

        BitmapFont bitmapFont = new BitmapFont();
        stage = new Stage();
        infoLabel = new Label(null, new Label.LabelStyle(bitmapFont, Color.WHITE));
        infoLabel.setPosition(10, 80);
        stage.addActor(infoLabel);


        camera = new PerspectiveCamera(70f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0f, 18f, 40f);
        camera.lookAt(0f, 10f, 0f);
        camera.near = 0.1f;
        camera.far = 5000f;
        camera.update();

        shapeRenderer = new ShapeRenderer();

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        DirectionalLight sun = new DirectionalLight().set(1f, 1f, 1f, -1f, -0.8f, -0.2f);
        environment.add(sun);

        batch = new ModelBatch();

        // Création des modèles 3D graphiques via ModelBuilder
        ModelBuilder modelBuilder = new ModelBuilder();

        planeModel = modelBuilder.createBox(
            PLANE_SIZE, PLANE_SIZE, PLANE_SIZE,
            new Material(ColorAttribute.createDiffuse(PLANE_COLOR)),
            com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal
        );
        planeInstance = new ModelInstance(planeModel);

        enemyModel = modelBuilder.createBox(
            PLANE_SIZE, PLANE_SIZE, PLANE_SIZE,
            new Material(ColorAttribute.createDiffuse(ENEMY_COLOR)),
            com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal
        );
        enemyInstance = new ModelInstance(enemyModel);


//        ModelLoader loader = new ObjLoader();
//        groundModel = loader.loadModel(Gdx.files.internal("dnisland.obj"));
        groundModel = modelBuilder.createBox(
            100000f, 1f, 100000f,
            new Material(ColorAttribute.createDiffuse(GROUND_COLOR)),
            com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal
        );
        groundInstance = new ModelInstance(groundModel);
        groundInstance.transform.setToTranslation(0f, -0.5f, 0f);

        // Ciel : panorama equirectangulaire appliqué en UV sur une sphère (face intérieure),
        // rendu AVANT le monde : le monde gagne au test de profondeur, la sphère ne se voit
        // que là où le sol/avion n'ont pas encore écrit le buffer de profondeur.
        skyTexture = new Texture(Gdx.files.internal("pizzo_pernice_puresky_1k.png"));
        skyModel = buildSkySphere(SKY_RADIUS, skyTexture);
        skyInstance = new ModelInstance(skyModel);
        skyEnvironment = new Environment();
        skyEnvironment.set(new ColorAttribute(ColorAttribute.AmbientLight, 1f, 1f, 1f, 1f));

        // Configuration du monde physique Bullet
        collisionConfiguration = new btDefaultCollisionConfiguration();
        dispatcher = new btCollisionDispatcher(collisionConfiguration);
        broadphase = new btDbvtBroadphase();
        solver = new btSequentialImpulseConstraintSolver();
        world = new btDiscreteDynamicsWorld(dispatcher, broadphase, solver, collisionConfiguration);
        world.setGravity(new Vector3(0f, -9.81f, 0f));

        // Sol physique (Boîte statique)
        groundShape = new btBoxShape(new Vector3(5000f, 0.5f, 5000f));
        groundBody = new btRigidBody(0f, null, groundShape);
        groundBody.setWorldTransform(groundInstance.transform);
        world.addRigidBody(groundBody);

        // Avion physique (Boîte dynamique)
        planeShape = new btBoxShape(new Vector3(1f, 1f, 1f));
        Vector3 localInertia = new Vector3(0f, 0f, 0f);
        planeShape.calculateLocalInertia(1f, localInertia);

        transformMatrix.idt().translate(1000f, 1000f, 1000f);
        planeInfo = new btRigidBodyConstructionInfo(1f, null, planeShape, localInertia);
        planeInfo.setLinearDamping(0.05f);
        planeInfo.setAngularDamping(0.2f);

        planeBody = new btRigidBody(planeInfo);
        planeBody.setWorldTransform(transformMatrix);
        planeBody.setLinearVelocity(new Vector3(0f, 0f, -INITIAL_SPEED));  // départ lancé vers l'avant (-Z)
        world.addRigidBody(planeBody);

        // Cible : corps statique du cube adverse, position synchronisée chaque frame dans render()
        enemyShape = new btBoxShape(new Vector3(PLANE_SIZE / 2f, PLANE_SIZE / 2f, PLANE_SIZE / 2f));
        enemyBody = new btRigidBody(0f, null, enemyShape);
        enemyBody.setRestitution(BULLET_RESTITUTION);
        world.addRigidBody(enemyBody);

        // Mitrailleuse : pool de balles (sphères dynamiques, rebond élastique), inertes sous le sol
        bulletModel = modelBuilder.createBox(
            BULLET_SIZE, BULLET_SIZE, BULLET_SIZE,
            new Material(ColorAttribute.createDiffuse(BULLET_COLOR)),
            com.badlogic.gdx.graphics.VertexAttributes.Usage.Position | com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal
        );
        for (int i = 0; i < MAX_BULLETS; i++) {
            bulletInstances[i] = new ModelInstance(bulletModel);
            bulletShapes[i] = new btSphereShape(BULLET_SIZE / 2f);
            Vector3 bulletInertia = new Vector3(0f, 0f, 0f);
            bulletShapes[i].calculateLocalInertia(1f, bulletInertia);
            btRigidBodyConstructionInfo info = new btRigidBodyConstructionInfo(0.05f, null, bulletShapes[i], bulletInertia);
            info.setLinearDamping(0f);
            info.setAngularDamping(1f);
            bulletBodies[i] = new btRigidBody(info);
            bulletBodies[i].setRestitution(BULLET_RESTITUTION);
            bulletBodies[i].setFriction(0.05f);
            bulletBodies[i].setWorldTransform(new Matrix4().setTranslation(0f, -1000f, 0f));
            world.addRigidBody(bulletBodies[i]);
        }
    }

    // Sphère UV "ciel" : mêmes positions qu'une sphère classique mais triangles inversés
    // (winding horaire vu de l'extérieur) pour que les faces intérieures soient visibles,
    // et normales pointant vers le centre. UV equirectangulaire : u = 1 - lon/2π, v = 1 - lat/π,
    // ce qui correspond directement à un panorama 2:1 (type Poly Haven).
    private Model buildSkySphere (float radius, Texture skyTexture) {
        int segX = 32, segY = 16;

        ModelBuilder modelBuilder = new ModelBuilder();
        modelBuilder.begin();

        // On crée directement la partie du modèle avec le ModelBuilder
        Material material = new Material(TextureAttribute.createDiffuse(skyTexture), IntAttribute.createCullFace(GL20.GL_NONE));
        MeshPartBuilder meshPartBuilder = modelBuilder.part(
            "sky",
            GL20.GL_TRIANGLES,
            VertexAttribute.Position().usage | VertexAttribute.Normal().usage | VertexAttribute.TexCoords(0).usage,
            material
        );

        // Construction de la sphère inversée (faces intérieures)
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

                // Normales inversées vers l'intérieur
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

                // Winding inversé pour l'intérieur
                meshPartBuilder.triangle(a, c, b);
                meshPartBuilder.triangle(d, c, b);
            }
        }

        Model model = modelBuilder.end();
        // model.manage(skyTexture); // Permet de s'assurer que la texture sera nettoyée à la suppression du modèle
        return model;
    }

    @Override
    public void render () {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);

        // W/S cabrage (W = cabré/montée, S = piqué), A/D bank (virage), Q/E lacet (pointage), Shift gaz
        float pitchIn = 0f, rudderIn = 0f, aileronIn = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) pitchIn = -1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) pitchIn = 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) aileronIn = 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) aileronIn = -1f;
        if (Gdx.input.isKeyPressed(Input.Keys.Q)) rudderIn = 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.E)) rudderIn = -1f;
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)) throttle = Math.clamp(throttle + 1.5f * dt, 0, 1);
        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) throttle = Math.clamp(throttle - 1.5f * dt, 0, 1);

        // Axes du fuselage dans le monde (scratchB/scratchC sont réutilisés par la caméra)
        Vector3 forward = scratchB.set(0f, 0f, -1f).mul(scratchRot);
        Vector3 up = scratchC.set(0f, 1f, 0f).mul(scratchRot);

        if (Controllers.getControllers().size > 0) {
            Controller controller = Controllers.getCurrent();
            if (controller != null) {
                float lx = padAxis(controller.getAxis(controller.getMapping().axisLeftX));
                float ly = padAxis(controller.getAxis(controller.getMapping().axisLeftY));
                if (lx != 0f) aileronIn = -lx;
                if (ly != 0f) pitchIn = ly;

                int lt = controller.getButton(controller.getMapping().buttonL2) ? 1 : 0;
                int rt = controller.getButton(controller.getMapping().buttonR2) ? 1 : 0;
                if (lt > PAD_DEADZONE) rudderIn = lt;
                if (rt > PAD_DEADZONE) rudderIn = -rt;
                if (controller.getButton(controller.getMapping().buttonL1)) throttle = Math.clamp(throttle - 1.5f * dt, 0, 1);
                if (controller.getButton(controller.getMapping().buttonR1)) throttle = Math.clamp(throttle + 1.5f * dt, 0, 1);
                if (controller.getButton(controller.getMapping().buttonA)) shoot(forward);

                float rxp = padAxis(controller.getAxis(controller.getMapping().axisRightX));
                float ryp = padAxis(controller.getAxis(controller.getMapping().axisRightY));
                if (rxp != 0f || ryp != 0f) {
                    freeCamYaw -= rxp * LOOK_RATE * dt;
                    freeCamPitch = MathUtils.clamp(freeCamPitch - ryp * LOOK_RATE * dt, -MAX_LOOK_PITCH, MAX_LOOK_PITCH);
                    if (freeCamYaw > MathUtils.PI) freeCamYaw -= 2f * MathUtils.PI;
                    if (freeCamYaw < -MathUtils.PI) freeCamYaw += 2f * MathUtils.PI;
                } else if (controller.getButton(controller.getMapping().buttonRightStick)){
                    freeCamYaw = approachZero(freeCamYaw, LOOK_RECENTER * dt);
                    freeCamPitch = approachZero(freeCamPitch, LOOK_RECENTER * dt);
                }
            }
        }

        planeBody.getWorldTransform(transformMatrix);
        transformMatrix.getRotation(scratchRot);

        // Poussée (gaz) vers l'avant local (-Z) : la traînée limite naturellement la vitesse
        if (throttle > 0) {
            planeBody.applyCentralForce(scratchA.set(forward).scl(THRUST * throttle));
        }

        // Mitrailleuse : Espace, cadence limitée, tir depuis le nez avec la vitesse de l'avion
        fireCooldown -= dt;
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            shoot(forward);
        }

        // --- Modèle de vol : forces et moments aéro (masse = 1 : forces = accélérations) ---
        Vector3 velWorld = planeBody.getLinearVelocity();
        float speed = velWorld.len();
        if (speed > 0.01f) {
            float q = WING_COEF * speed * speed;   // dynamique dynamique : 0.5*rho*S/m * v²

            // Incidence (alpha) et glissement (beta) : vitesse projetée sur les axes du fuselage
            float alpha = (float) Math.atan2(-up.dot(velWorld), forward.dot(velWorld));
            scratchA.set(forward).crs(up);   // axe "droite" du fuselage (monde)
            float beta = (float) Math.asin(MathUtils.clamp(scratchA.dot(velWorld) / speed, -1f, 1f));

            // Portance : CL linéaire en incidence, plateau puis chute au-delà du décrochage
            float cl;
            if (alpha <= ALPHA_STALL) {
                cl = CL0 + CL_SLOPE * alpha;
            } else {
                cl = CL0 + CL_SLOPE * ALPHA_STALL - STALL_DROP * (alpha - ALPHA_STALL);
            }

            // Limite de charge structurelle : plafonne la portance à MAX_G
            float clMax = MAX_G * GRAVITY / q;
            if (cl > clMax) cl = clMax;

            // Traînée : parasite + induite (+ traînée de décrochage)
            float cd = CD0 + CD_INDUCED * cl * cl;
            if (alpha > ALPHA_STALL) cd += STALL_DRAG * (alpha - ALPHA_STALL);
            planeBody.applyCentralForce(scratchA.set(velWorld).scl(-q * cd / speed));

            // Portance : le long du "haut" du fuselage, projetée perpendiculairement à l'écoulement
            scratchA.set(up);
            float proj = up.dot(velWorld) / (speed * speed);
            scratchA.x -= velWorld.x * proj;
            scratchA.y -= velWorld.y * proj;
            scratchA.z -= velWorld.z * proj;
            float perpLen = scratchA.len();
            if (perpLen > 0.001f) {
                scratchA.scl(q * cl / perpLen);
                planeBody.applyCentralForce(scratchA);
            }

            // Moments de gouverne (repère fuselage : x=cabrage, y=lacet, z=roulis) :
            // cabrage = gouvernail + stabilité, lacet = gouvernail de queue + vent-en-poupe + auto-coordination
            float pitchMom = q * (PITCH_EFF * pitchIn + CM_ALPHA * alpha);
            float yawMom = q * (RUDDER_EFF * rudderIn + CN_BETA * beta + CN_A * aileronIn);

            // Roulis : taux continu (A = gauche, D = droite), le bank est conservé à la libération
            Vector3 omegaWorld = planeBody.getAngularVelocity();
            float omegaZbody = omegaWorld.dot(scratchA.set(0f, 0f, 1f).mul(scratchRot));   // taux de roulis (repère fuselage)
            float rollMom = ROLL_RATE * ROLL_DAMP * aileronIn - ROLL_DAMP * omegaZbody;

            // Torques en repère monde + amortissement aéro cabrage/lacet (roulis déjà amorti ci-dessus)
            scratchA.set(pitchMom, yawMom, rollMom).mul(scratchRot);   // fuselage -> monde (x=cabrage, y=lacet, z=roulis)
            scratchA.x -= DAMP * q * omegaWorld.x;
            scratchA.y -= DAMP * q * omegaWorld.y;
            planeBody.applyTorque(scratchA);
        }

        // Adversaire : "tour du monde" (cercle de rayon ENEMY_RADIUS), bank coordonné dans le virage.
        // Avant le step : son corps statique est en place pour les collisions des balles.
        enemyAngle += ENEMY_SPEED / ENEMY_RADIUS * dt;
        float ca = (float) Math.cos(enemyAngle);
        float sa = (float) Math.sin(enemyAngle);
        enemyPos.set(ENEMY_CENTER_X + ENEMY_RADIUS * ca, ENEMY_ALTITUDE, ENEMY_CENTER_Z + ENEMY_RADIUS * sa);
        // Cap = tangente au cercle : fwd = (-sin, 0, cos). Right non banké = fwd × upMonde, pointe vers le centre.
        float cb = (float) Math.cos(ENEMY_BANK);
        float sb = (float) Math.sin(ENEMY_BANK);
        float rx = -ca, rz = -sa;
        enemyRight.set(rx * cb, -sb, rz * cb);      // right' = right·cb − upMonde·sb
        enemyUp.set(rx * sb, cb, rz * sb);          // up'   = right·sb + upMonde·cb (bank vers l'intérieur)
        enemyBack.set(sa, 0f, -ca);                 // local +Z = arrière (le nez du modèle est en -Z)
        enemyInstance.transform.set(enemyRight, enemyUp, enemyBack, enemyPos);
        enemyBody.setWorldTransform(enemyInstance.transform);

        // Mise à jour de la simulation physique
        world.stepSimulation(dt, 5, 1f / 60f);

        // Synchronisation de la matrice graphique avec la physique
        planeBody.getWorldTransform(planeInstance.transform);

        // Balles : durée de vie, désactivation loin sous le sol, sync des mesh
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

        Vector3 planePos = new Vector3();
        planeInstance.transform.getTranslation(planePos);

        // Position de la caméra fixée dans le cockpit (légèrement au-dessus du centre)
        Vector3 cockpitPos = planePos.cpy().add(up.cpy().scl(0.5f));
        camera.position.set(cockpitPos);

        // Direction du regard de base (vers l'avant de l'avion)
        Vector3 lookDir = forward.cpy().scl(20f);

        // Rotation du regard avec le stick droit (Freecam à l'intérieur du cockpit)
        if (Math.abs(freeCamYaw) > 0.0001f || Math.abs(freeCamPitch) > 0.0001f) {
            float cy = (float) Math.cos(freeCamYaw);
            float sy = (float) Math.sin(freeCamYaw);
            float x = lookDir.x * cy + lookDir.z * sy;
            float z = lookDir.z * cy - lookDir.x * sy;
            lookDir.x = x;
            lookDir.z = z;

            Vector3 rightAxis = scratchA.set(forward).crs(up);
            float cp = (float) Math.cos(freeCamPitch);
            float sp = (float) Math.sin(freeCamPitch);
            Vector3 cross = rightAxis.crs(lookDir);
            lookDir.scl(cp).add(cross.scl(sp));
        }

        Vector3 lookTarget = cockpitPos.cpy().add(lookDir);
        camera.lookAt(lookTarget);
        camera.up.set(up);
        camera.update();

        // Rendu graphique
        Gdx.gl.glClearColor(SKY_COLOR.r, SKY_COLOR.g, SKY_COLOR.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        batch.begin(camera);
        // Le ciel suit la caméra (translation seule : pas de rotation, cap stable)
        skyInstance.transform.setToTranslation(camera.position.x, camera.position.y, camera.position.z);
        batch.render(skyInstance, skyEnvironment);
        batch.render(planeInstance, environment);
        batch.render(enemyInstance, environment);
        for (int i = 0; i < MAX_BULLETS; i++) {
            if (bulletLife[i] > 0f) batch.render(bulletInstances[i], environment);
        }
        batch.render(groundInstance, environment);
        batch.end();

        // Données de vol pour le HUD : vitesse, altitude, taux de montée, assiette, cap, alerte STALL
        Vector3 velNow = planeBody.getLinearVelocity();
        float climbRate = velNow.y;
        float alphaNow = (float) Math.atan2(-up.dot(velNow), forward.dot(velNow));   // incidence (alerte STALL)
        Vector3 right = scratchA.set(1f, 0f, 0f).mul(scratchRot);
        float pitchDeg = MathUtils.radiansToDegrees * MathUtils.atan2(forward.y, (float) Math.sqrt(forward.x * forward.x + forward.z * forward.z));
        float rollDeg = MathUtils.radiansToDegrees * MathUtils.asin(MathUtils.clamp(right.y, -1f, 1f));
        float headingDeg = MathUtils.radiansToDegrees * MathUtils.atan2(forward.x, -forward.z);
        if (headingDeg < 0f) headingDeg += 360f;

        infoLabel.setText(
            "SPD " + String.format("%6.1f m/s, %6.1f km/h", speed, speed * 3.6f) + "\n" +
            "THROTTLE " + String.format("%6.1f", throttle) + "\n" +
            "ALT " + String.format("%6.1f m", planePos.y) + "\n" +
            "CLB " + String.format("%+6.1f m/s", climbRate) + "\n" +
            "PIT " + String.format("%6.1f deg", pitchDeg) + "\n" +
            "ROL " + String.format("%6.1f deg", rollDeg) + "\n" +
            "HDG " + String.format("%5.0f deg", headingDeg) + "\n" +
            "SPACE: tir\n"
            + (alphaNow > ALPHA_STALL ? "!!! STALL !!!" : ""));

        // --- Rendu du réticule holographique ancré sur l'axe de l'avion ---
        // On place un point virtuel 200 mètres devant le nez de l'avion
        Vector3 reticleWorldPos = planePos.cpy().add(forward.cpy().scl(200f));
        Vector3 screenPos = new Vector3(reticleWorldPos);
        camera.project(screenPos); // Convertit la position 3D du monde en coordonnées écran 2D

        // On l'affiche uniquement si le nez de l'avion est devant la caméra (z entre 0 et 1)
        if (screenPos.z >= 0f && screenPos.z <= 1f) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

            shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

            float reticuleX = screenPos.x;
            float reticuleY = screenPos.y;

            // Couleur cyan holographique lumineuse
            shapeRenderer.setColor(0f, 0.9f, 1f, 0.75f);

            // Cercle extérieur et point central
            shapeRenderer.circle(reticuleX, reticuleY, 25f);
            shapeRenderer.circle(reticuleX, reticuleY, 3f);

            // Lignes du réticule (gauche, droite, bas, haut)
            shapeRenderer.line(reticuleX - 40f, reticuleY, reticuleX - 12f, reticuleY);
            shapeRenderer.line(reticuleX + 12f, reticuleY, reticuleX + 40f, reticuleY);
            shapeRenderer.line(reticuleX, reticuleY - 40f, reticuleX, reticuleY - 12f);
            shapeRenderer.line(reticuleX, reticuleY + 12f, reticuleX, reticuleY + 40f);

            shapeRenderer.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
        stage.draw();
    }

    private void shoot(Vector3 forward) {
        if (fireCooldown > 0f) return;
        fireCooldown = FIRE_COOLDOWN;
        for (int i = 0; i < MAX_BULLETS; i++) {
            if (bulletLife[i] > 0f) continue;
            bulletLife[i] = BULLET_LIFETIME;
            transformMatrix.getTranslation(scratchA);
            scratchA.x += forward.x * 3f;
            scratchA.y += forward.y * 3f;
            scratchA.z += forward.z * 3f;
            bulletBodies[i].setWorldTransform(new Matrix4().setTranslation(scratchA.x, scratchA.y, scratchA.z));
            scratchA.set(0f, 0f, 0f);
            bulletBodies[i].setAngularVelocity(scratchA);
            Vector3 vel = planeBody.getLinearVelocity();
            scratchA.set(
                vel.x + forward.x * BULLET_SPEED,
                vel.y + forward.y * BULLET_SPEED,
                vel.z + forward.z * BULLET_SPEED);
            bulletBodies[i].setLinearVelocity(scratchA);
            break;
        }
    }

    // Stick manette avec zone morte : 0 dans la zone, sinon normalisé sur -1..1
    private float padAxis (float v) {
        float d = PAD_DEADZONE;
        if (Math.abs(v) < d) return 0f;
        return (v - Math.signum(v) * d) / (1f - d);
    }

    // Rapprochement vers zéro à vitesse constante (recentrage de la freecam au stick centré)
    private float approachZero (float v, float step) {
        if (v > step) return v - step;
        if (v < -step) return v + step;
        return 0f;
    }

    @Override
    public void resize (int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    @Override
    public void dispose () {
        batch.dispose();
        planeModel.dispose();
        enemyModel.dispose();
        bulletModel.dispose();
        groundModel.dispose();
        // for (MeshPart part : skyModel.meshes) part.mesh.dispose();
        skyModel.dispose();
        skyTexture.dispose();

        world.dispose();
        solver.dispose();
        broadphase.dispose();
        dispatcher.dispose();
        collisionConfiguration.dispose();

        for (int i = 0; i < MAX_BULLETS; i++) {
            bulletBodies[i].dispose();
            bulletShapes[i].dispose();
        }
        enemyBody.dispose();
        enemyShape.dispose();
        groundShape.dispose();
        planeShape.dispose();
        planeInfo.dispose();
        groundBody.dispose();
        planeBody.dispose();
        shapeRenderer.dispose();
    }
}
