package ch.airealm;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.collision.btSphereShape;
import com.badlogic.gdx.physics.bullet.dynamics.btDiscreteDynamicsWorld;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;

public class BulletManager {
    private static final int MAX_BULLETS = 48;
    private static final float BULLET_SPEED = 300f;
    private static final float BULLET_SIZE = 0.5f;
    private static final float BULLET_LIFETIME = 4f;
    private static final float BULLET_RESTITUTION = 0.7f;
    private static final float HIT_DISTANCE = 6f; // Seuil de distance pour "1 balle = mort"

    private Model bulletModel;
    private final btRigidBody[] bulletBodies = new btRigidBody[MAX_BULLETS];
    private final btSphereShape[] bulletShapes = new btSphereShape[MAX_BULLETS];
    private final ModelInstance[] bulletInstances = new ModelInstance[MAX_BULLETS];
    private final float[] bulletLife = new float[MAX_BULLETS];
    private final boolean[] isP1Bullet = new boolean[MAX_BULLETS]; // Permet de savoir qui a tiré

    private final Vector3 scratchA = new Vector3();
    private final Vector3 scratchB = new Vector3();
    private final Vector3 bulletPos = new Vector3();
    private final Vector3 planePos = new Vector3();

    public BulletManager(ModelBuilder modelBuilder, btDiscreteDynamicsWorld world) {
        bulletModel = modelBuilder.createBox(
            BULLET_SIZE, BULLET_SIZE, BULLET_SIZE,
            new Material(ColorAttribute.createDiffuse(Color.RED)),
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
            bulletBodies[i].setCcdMotionThreshold(0.1f);
            bulletBodies[i].setCcdSweptSphereRadius(BULLET_SIZE / 2f);

            bulletBodies[i].setRestitution(BULLET_RESTITUTION);
            bulletBodies[i].setFriction(0.05f);
            bulletBodies[i].setWorldTransform(new Matrix4().setTranslation(0f, -1000f, 0f));
            world.addRigidBody(bulletBodies[i]);
        }
    }

    public void update(float dt, Airplane player1, Airplane player2) {
        for (int i = 0; i < MAX_BULLETS; i++) {
            if (bulletLife[i] <= 0f) continue;
            bulletLife[i] -= dt;
            if (bulletLife[i] <= 0f) {
                despawnBullet(i);
                continue;
            }
            bulletBodies[i].getWorldTransform(bulletInstances[i].transform);
            bulletInstances[i].transform.getTranslation(bulletPos);


            if (isP1Bullet[i] && !player2.isDead()) {
                player2.getPosition(planePos);
                if (bulletPos.dst(planePos) < HIT_DISTANCE) {
                    player2.setDead(true);
                    despawnBullet(i);
                }
            } else if (!isP1Bullet[i] && !player1.isDead()) {
                player1.getPosition(planePos);
                if (bulletPos.dst(planePos) < HIT_DISTANCE) {
                    player1.setDead(true);
                    despawnBullet(i);
                }
            }
        }
    }

    private void despawnBullet(int i) {
        bulletLife[i] = 0f;
        scratchA.set(0f, 0f, 0f);
        bulletBodies[i].setLinearVelocity(scratchA);
        bulletBodies[i].setAngularVelocity(scratchA);
        bulletBodies[i].setWorldTransform(new Matrix4().setTranslation(0f, -1000f, 0f));
    }

    public void shoot(Airplane shooter, Vector3 forward, boolean player1Shot) {
        for (int i = 0; i < MAX_BULLETS; i++) {
            if (bulletLife[i] > 0f) continue;
            bulletLife[i] = BULLET_LIFETIME;
            isP1Bullet[i] = player1Shot;

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

    public void render(ModelBatch batch, com.badlogic.gdx.graphics.g3d.Environment environment) {
        for (int i = 0; i < MAX_BULLETS; i++) {
            if (bulletLife[i] > 0f) batch.render(bulletInstances[i], environment);
        }
    }

    public void dispose() {
        bulletModel.dispose();
        for (int i = 0; i < MAX_BULLETS; i++) {
            bulletBodies[i].dispose();
            bulletShapes[i].dispose();
        }
    }
}
