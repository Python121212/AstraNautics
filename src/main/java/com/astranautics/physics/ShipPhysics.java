package com.astranautics.physics;

import com.astranautics.config.AstraConfig;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

import java.util.List;

/**
 * Sequential-impulse rigid body solver for one ship against the static world.
 *
 * <p>One call == one fixed physics substep. Ships are solved one at a time; ship
 * vs ship contacts and entity contacts are planned for a follow-up iteration.</p>
 */
public final class ShipPhysics {
    private static final int SOLVER_ITERATIONS = 6;
    private static final double SLOP = 0.005D;
    private static final double BAUMGARTE = 0.2D;
    private static final double RUBBER_EPSILON = 1.0E-6D;

    private ShipPhysics() {
    }

    public static void step(ServerLevel level, RigidBody body, ShipStructure structure, double h) {
        if (body.isKinematic()) {
            return;
        }

        double gravity = AstraConfig.GRAVITY.get();
        double friction = AstraConfig.FRICTION.get();
        double restitution = AstraConfig.RESTITUTION.get();

        // Gravity acts on the centre of mass.
        body.applyForce(new Vector3d(0.0D, -gravity * body.getMass(), 0.0D));
        body.integrateVelocities(h,
                AstraConfig.LINEAR_DAMPING.get(),
                AstraConfig.ANGULAR_DAMPING.get());

        List<Contact> contacts = CollisionDetector.collect(level, body, structure);
        solveContacts(body, contacts, h, friction, restitution);
        body.integratePosition(h);
        applyPositionCorrection(body, contacts);
    }

    static void solveContacts(RigidBody body, List<Contact> contacts, double h,
                                      double friction, double restitution) {
        if (contacts.isEmpty()) {
            return;
        }

        Vector3d relativeVelocity = new Vector3d();
        Vector3d lever = new Vector3d();
        Vector3d leverCrossNormal = new Vector3d();
        Vector3d angularTerm = new Vector3d();
        Vector3d impulse = new Vector3d();
        Vector3d tangential = new Vector3d();

        for (int iteration = 0; iteration < SOLVER_ITERATIONS; iteration++) {
            for (Contact contact : contacts) {
                body.velocityAt(contact.point, relativeVelocity);

                double normalVelocity = relativeVelocity.dot(contact.normal);
                boolean approaching = normalVelocity < 0.0D;
                boolean penetrating = contact.penetration > SLOP;
                if (!approaching && !penetrating) {
                    continue;
                }

                lever.set(contact.point).sub(body.position);
                lever.cross(contact.normal, leverCrossNormal);
                body.getInverseInertiaWorld().transform(leverCrossNormal, angularTerm);
                angularTerm.cross(lever, angularTerm);

                double effectiveMass = body.getInverseMass() + angularTerm.dot(contact.normal);
                if (effectiveMass < RUBBER_EPSILON) {
                    continue;
                }

                double bias = BAUMGARTE / h * Math.max(contact.penetration - SLOP, 0.0D);
                double target = -(1.0D + (approaching ? restitution : 0.0D)) * normalVelocity + bias;
                double normalImpulse = target / effectiveMass;
                if (normalImpulse <= 0.0D) {
                    continue;
                }

                impulse.set(contact.normal).mul(normalImpulse);
                body.applyImpulseAt(impulse, contact.point);

                // Coulomb friction along the tangential direction.
                body.velocityAt(contact.point, relativeVelocity);
                tangential.set(relativeVelocity)
                        .sub(new Vector3d(contact.normal).mul(relativeVelocity.dot(contact.normal)));
                double tangentialSpeed = tangential.length();
                if (tangentialSpeed < RUBBER_EPSILON) {
                    continue;
                }
                tangential.div(tangentialSpeed);

                lever.set(contact.point).sub(body.position);
                lever.cross(tangential, leverCrossNormal);
                body.getInverseInertiaWorld().transform(leverCrossNormal, angularTerm);
                angularTerm.cross(lever, angularTerm);
                double tangentialMass = body.getInverseMass() + angularTerm.dot(tangential);
                if (tangentialMass < RUBBER_EPSILON) {
                    continue;
                }

                double frictionImpulse = Math.min(tangentialSpeed / tangentialMass,
                        friction * normalImpulse);
                impulse.set(tangential).mul(-frictionImpulse);
                body.applyImpulseAt(impulse, contact.point);
            }
        }
    }

    /** Small positional push so ships do not slowly sink into the ground. */
    static void applyPositionCorrection(RigidBody body, List<Contact> contacts) {
        double deepest = 0.0D;
        Vector3d deepestNormal = null;
        for (Contact contact : contacts) {
            if (contact.penetration > deepest) {
                deepest = contact.penetration;
                deepestNormal = contact.normal;
            }
        }
        if (deepestNormal == null || deepest <= SLOP) {
            return;
        }
        double correction = Math.min(deepest - SLOP, 0.1D) * 0.6D;
        body.position.fma(correction, deepestNormal);
    }
}
