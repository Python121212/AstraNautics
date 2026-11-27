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
 *
 * <p>The solver accumulates per-contact impulses and clamps the <em>total</em>
 * normal impulse to be non-negative. Clamping each iteration's delta instead
 * makes resting structures slowly gain energy ("popcorning").</p>
 */
public final class ShipPhysics {
    private static final int SOLVER_ITERATIONS = 8;
    private static final double SLOP = 0.005D;
    private static final double BAUMGARTE = 0.2D;
    private static final double RUBBER_EPSILON = 1.0E-6D;
    /** Impacts slower than this do not bounce, which stops resting bodies from jittering. */
    private static final double RESTITUTION_THRESHOLD = 1.0D;

    private ShipPhysics() {
    }

    public static void step(ServerLevel level, RigidBody body, ShipStructure structure, double h) {
        if (body.isKinematic()) {
            return;
        }

        double gravity = AstraConfig.GRAVITY.get();

        body.applyForce(new Vector3d(0.0D, -gravity * body.getMass(), 0.0D));
        body.integrateVelocities(h,
                AstraConfig.LINEAR_DAMPING.get(),
                AstraConfig.ANGULAR_DAMPING.get());

        List<Contact> contacts = CollisionDetector.collect(level, body, structure);
        solveContacts(body, contacts, h, AstraConfig.FRICTION.get(), AstraConfig.RESTITUTION.get());
        body.integratePosition(h);
        applyPositionCorrection(body, contacts);
    }

    static void solveContacts(RigidBody body, List<Contact> contacts, double h,
                              double friction, double restitution) {
        if (contacts.isEmpty()) {
            return;
        }

        for (Contact contact : contacts) {
            contact.resetImpulses();
        }

        for (int iteration = 0; iteration < SOLVER_ITERATIONS; iteration++) {
            for (Contact contact : contacts) {
                solveNormal(body, contact, h, restitution);
                solveFriction(body, contact, friction);
            }
        }
    }

    private static void solveNormal(RigidBody body, Contact contact, double h, double restitution) {
        Vector3d velocity = body.velocityAt(contact.point, new Vector3d());
        double normalVelocity = velocity.dot(contact.normal);

        double effectiveMass = effectiveMass(body, contact, contact.normal);
        if (effectiveMass < RUBBER_EPSILON) {
            return;
        }

        double bias = BAUMGARTE / h * Math.max(contact.penetration - SLOP, 0.0D);
        double bounce = 0.0D;
        if (!contact.restitutionApplied && normalVelocity < -RESTITUTION_THRESHOLD) {
            bounce = restitution;
            contact.restitutionApplied = true;
        }

        double delta = (-(1.0D + bounce) * normalVelocity + bias) / effectiveMass;
        double previous = contact.normalImpulse;
        contact.normalImpulse = Math.max(0.0D, previous + delta);
        delta = contact.normalImpulse - previous;

        if (delta != 0.0D) {
            body.applyImpulseAt(new Vector3d(contact.normal).mul(delta), contact.point);
        }
    }

    private static void solveFriction(RigidBody body, Contact contact, double friction) {
        if (friction <= 0.0D) {
            return;
        }

        Vector3d velocity = body.velocityAt(contact.point, new Vector3d());
        Vector3d tangential = new Vector3d(velocity)
                .sub(new Vector3d(contact.normal).mul(velocity.dot(contact.normal)));

        double speed = tangential.length();
        if (speed < RUBBER_EPSILON) {
            return;
        }
        tangential.div(speed);

        double effectiveMass = effectiveMass(body, contact, tangential);
        if (effectiveMass < RUBBER_EPSILON) {
            return;
        }

        double delta = -speed / effectiveMass;
        double maxFriction = friction * contact.normalImpulse;
        double previous = contact.tangentImpulse;
        contact.tangentImpulse = Math.max(-maxFriction, Math.min(maxFriction, previous + delta));
        delta = contact.tangentImpulse - previous;

        if (delta != 0.0D) {
            body.applyImpulseAt(new Vector3d(tangential).mul(delta), contact.point);
        }
    }

    private static double effectiveMass(RigidBody body, Contact contact, Vector3d direction) {
        Vector3d lever = new Vector3d(contact.point).sub(body.position);
        Vector3d leverCrossDirection = lever.cross(direction, new Vector3d());
        Vector3d angular = new Vector3d(leverCrossDirection);
        body.getInverseInertiaWorld().transform(angular);
        angular.cross(lever, angular);
        return body.getInverseMass() + angular.dot(direction);
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
