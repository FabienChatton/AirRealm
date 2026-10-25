package ch.airealm;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.physics.bullet.dynamics.btRigidBody;

public class SpitFireFlightModel implements FlightModel {
    private static final float THRUST = 8f; // Accélération max (m/s²) fournie par le moteur à pleine poussée
    private static final float WING_COEF = 0.0049f; // Échelle de la portance : accél. lift ≈ coef × CL × V²
    private static final float CL0 = 0.2f; // Coefficient de portance à angle d'incidence nul (avion trimmé)
    private static final float CL_SLOPE = 10f; // Pente de la courbe de portance : dCL/dα (portance gagnée par inc. d'α)
    private static final float ALPHA_STALL = 0.262f; // Angle d'incidence (rad) au-delà duquel l'aile décroche (stall)
    private static final float STALL_DROP = 3.0f; // Perte de portance au-delà du stall (CL chute de 3.0 par rad de surinc. → chute du nez)
    // drag
    private static final float CD0 = 0.02f; // Traînée parasite de base (frottement, traînée du fuselage)
    private static final float CD_INDUCED = 0.0002f; // Traînée induite par la portance (∝ CL²) : plus tu portes, plus tu traînes
    private static final float STALL_DRAG = 0.008f; // Traînée supplémentaire ajoutée en régime de décrochage (le nez freine)
    // input responsive
    private static final float PITCH_EFF = 0.008f; // Efficacité de la profondeur : à quelle vitesse l'input fait cabrer/piquer
    private static final float RUDDER_EFF = 0.005f; // Efficacité du safran (dérive) : input → rotation en lacet
    private static final float ROLL_RATE = 1.5f;
    private static final float ROLL_DAMP = 3.3f;
    // stabilité
    private static final float CM_ALPHA = -0.030f; // Stabilité longitudinale : moment par rad d'incidence (négatif = stable, l'avion revient vers zéro d'incidence)
    private static final float CN_BETA = -0.003f; // Stabilité directionnelle : moment par rad de glissement latéral (effet "pointe de flèche")
    private static final float CN_A = 0.002f; // Contribution du safran au lacet (efficacité directionnelle de la dérive)
    private static final float DAMP = 0.016f; // Amortissement général des rotations (évite les oscillations/cabrage instable)

    private static final float GRAVITY = 9.81f;
    private static final float MAX_G = 6f;

    private float speed;
    private float climbRate;
    private float pitch;
    private float roll;
    private float heading;
    private boolean isStalling;

    private final Vector3 scratchA = new Vector3();
    private final Vector3 scratchB = new Vector3();
    private final Vector3 scratchC = new Vector3();
    private final Quaternion scratchRot = new Quaternion();
    private final Matrix4 transformMatrix = new Matrix4();

    @Override
    public void update(btRigidBody body, float throttle, float pitchIn, float rudderIn, float aileronIn, float dt) {
        body.getWorldTransform(transformMatrix);
        transformMatrix.getRotation(scratchRot);

        Vector3 forward = scratchB.set(0f, 0f, -1f).mul(scratchRot);
        Vector3 up = scratchC.set(0f, 1f, 0f).mul(scratchRot);
        Vector3 right = scratchA.set(1f, 0f, 0f).mul(scratchRot);

        // Poussée moteur
        if (throttle > 0) {
            body.applyCentralForce(scratchA.set(forward).scl(THRUST * throttle));
        }

        Vector3 velWorld = body.getLinearVelocity();
        speed = velWorld.len();
        climbRate = velWorld.y;

        if (speed > 0.01f) {
            float q = WING_COEF * speed * speed;

            float alpha = (float) Math.atan2(-up.dot(velWorld), forward.dot(velWorld));
            scratchA.set(forward).crs(up);
            float beta = (float) Math.asin(MathUtils.clamp(scratchA.dot(velWorld) / speed, -1f, 1f));

            isStalling = (alpha > ALPHA_STALL);

            // Portance (CL)
            float cl;
            if (!isStalling) {
                cl = CL0 + CL_SLOPE * alpha;
            } else {
                cl = CL0 + CL_SLOPE * ALPHA_STALL - STALL_DROP * (alpha - ALPHA_STALL);
            }

            float clMax = MAX_G * GRAVITY / q;
            if (cl > clMax) cl = clMax;

            // Traînée
            float cd = CD0 + CD_INDUCED * cl * cl;
            if (isStalling) cd += STALL_DRAG * (alpha - ALPHA_STALL);
            body.applyCentralForce(scratchA.set(velWorld).scl(-q * cd / speed));

            // Portance vectorielle
            scratchA.set(up);
            float proj = up.dot(velWorld) / (speed * speed);
            scratchA.x -= velWorld.x * proj;
            scratchA.y -= velWorld.y * proj;
            scratchA.z -= velWorld.z * proj;
            float perpLen = scratchA.len();
            if (perpLen > 0.001f) {
                scratchA.scl(q * cl / perpLen);
                body.applyCentralForce(scratchA);
            }

            // Moments
            float pitchMom = q * (PITCH_EFF * pitchIn + CM_ALPHA * alpha);
            float yawMom = q * (RUDDER_EFF * rudderIn + CN_BETA * beta + CN_A * aileronIn);

            Vector3 omegaWorld = body.getAngularVelocity();
            float omegaZbody = omegaWorld.dot(scratchA.set(0f, 0f, 1f).mul(scratchRot));
            float rollMom = ROLL_RATE * ROLL_DAMP * aileronIn - ROLL_DAMP * omegaZbody;

            scratchA.set(pitchMom, yawMom, rollMom).mul(scratchRot);
            scratchA.x -= DAMP * q * omegaWorld.x;
            scratchA.y -= DAMP * q * omegaWorld.y;
            body.applyTorque(scratchA);
        } else {
            isStalling = false;
        }

        // Calcul des angles du HUD en degrés
        pitch = MathUtils.radiansToDegrees * MathUtils.atan2(forward.y, (float) Math.sqrt(forward.x * forward.x + forward.z * forward.z));
        roll = MathUtils.radiansToDegrees * MathUtils.asin(MathUtils.clamp(right.y, -1f, 1f));
        heading = MathUtils.radiansToDegrees * MathUtils.atan2(forward.x, -forward.z);
        if (heading < 0f) heading += 360f;
    }

    @Override public float getSpeed() { return speed; }
    @Override public float getClimbRate() { return climbRate; }
    @Override public float getPitch() { return pitch; }
    @Override public float getRoll() { return roll; }
    @Override public float getHeading() { return heading; }
    @Override public boolean isStalling() { return isStalling; }
}
