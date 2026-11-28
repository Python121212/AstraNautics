package com.astranautics.physics;

import com.astranautics.config.AstraConfig;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

/**
 * Sequential-impulse solver for ships against the static world and against each
 * other.
 *
 * <p>One call == one fixed physics substep. Contacts accumulate impulses and the
 * <em>total</em> normal impulse is clamped to be non-negative; clamping each
 * iteration's delta instead makes resting structures slowly gain energy.</p>
 *
 * <p>Contact impulses are carried across steps ({@link ContactCache}) so the solver
 * converges in a handful of iterations — warm starting is the main reason the
 * simulation stays cheap with hundreds of voxels.</p>
 */
public final class ShipPhysics {
    private static final double SLOP = 0.005D;
    private static final double BAUMGARTE = 0.2D;
    private static final double RUBBER_EPSILON = 1.0E-6D;
    /** Impacts slower than this do not bounce, which stops resting bodies from jittering. */
    private static final double RESTITUTION_THRESHOLD = 1.0D;
    /** Below this tangential speed a contact is treated as static friction. */
    private static final double STATIC_FRICTION_EPSILON = 0.02D;
    /** A supporting body slower than this (m/s) is treated as resting and bled off. */
    private static final double REST_LINEAR = 0.05D;
    private static final double REST_ANGULAR = 0.1D;

    private ShipPhysics() {
    }

    public static void step(ServerLevel level, RigidBody body, VoxelBody voxelBody, double h,
                            PhysicsParams params, @Nullable ContactCache cache) {
        step(new LevelCollisionSource(level), body, voxelBody, h, params, cache);
    }

    /** Explicit step used by unit tests and by config-free callers. */
    public static void step(BlockCollisionSource source, RigidBody body, VoxelBody voxelBody, double h,
                            PhysicsParams params, @Nullable ContactCache cache) {
        if (body.isKinematic()) {
            return;
        }

        body.applyForce(new Vector3d(0.0D, -params.gravity * body.getMass(), 0.0D));
        body.integrateVelocities(h, params.linearDamping, params.angularDamping);

        List<Contact> contacts = CollisionDetector.collect(source, body, voxelBody);
        solveContacts(body, null, contacts, h, params, cache);
        body.integratePosition(h);
        applyPositionCorrection(body, contacts);
        settleRestingBody(body, contacts);

        if (cache != null) {
            cache.swap();
        }
    }

    /**
     * Bleeds off the residual velocity a body keeps when it is resting on something.
     *
     * <p>The solver leaves a few mm/s of motion on a body that should be lying still, because
     * the normal impulses and gravity are never balanced to the last bit. Nobody sees the hull
     * itself move, but a hull that stays awake and micro-moves drags anything standing on it
     * along, which reads as the whole view shaking; it also never reaches the idle tick count
     * that puts it to sleep. Bleeding that residue off makes the contact settle for real, after
     * which the sleep logic freezes the body completely.</p>
     *
     * <p>Only purely supporting contact sets are damped: a box tipping over an edge has
     * side-facing contacts and must keep its motion.</p>
     */
    static void settleRestingBody(RigidBody body, List<Contact> contacts) {
        if (contacts.isEmpty()) {
            return;
        }
        for (Contact contact : contacts) {
            if (contact.normal.y < 0.5D) {
                return;
            }
        }
        if (Math.abs(body.linearVelocity.y) < REST_LINEAR) {
            body.linearVelocity.y *= 0.2D;
        }
        if (body.linearVelocity.length() < REST_LINEAR * 1.2D
                && body.angularVelocity.length() < REST_ANGULAR) {
            body.linearVelocity.mul(0.35D);
            body.angularVelocity.mul(0.35D);
        }
    }

    /** Convenience overload for a single pair contact batch without warm starting. */
    public static void solveContacts(RigidBody bodyA, @Nullable RigidBody bodyB,
                                     List<Contact> contacts, double h, PhysicsParams params) {
        solveContacts(bodyA, bodyB, contacts, h, params, null);
    }

    /**
     * @param bodyA   the ship whose voxels produced the contacts
     * @param bodyB   the other body, or {@code null} when colliding with the world
     */
    public static void solveContacts(RigidBody bodyA, @Nullable RigidBody bodyB, List<Contact> contacts,
                                     double h, PhysicsParams params, @Nullable ContactCache cache) {
        if (contacts.isEmpty()) {
            return;
        }

        for (Contact contact : contacts) {
            contact.resetImpulses();
        }

        if (cache != null) {
            warmStart(bodyA, bodyB, contacts, cache);
        }

        for (int iteration = 0; iteration < params.iterations; iteration++) {
            for (Contact contact : contacts) {
                solveNormal(bodyA, bodyB, contact, h, params.restitution);
                solveFriction(bodyA, bodyB, contact, params.friction);
            }
        }

        if (cache != null) {
            for (Contact contact : contacts) {
                cache.put(ContactCache.keyOf(contact.point, contact.normal),
                        contact.normalImpulse, contact.tangentImpulse,
                        contact.tangent.x, contact.tangent.y, contact.tangent.z);
            }
        }
    }

    private static void warmStart(RigidBody bodyA, @Nullable RigidBody bodyB,
                                  List<Contact> contacts, ContactCache cache) {
        for (Contact contact : contacts) {
            long key = ContactCache.keyOf(contact.point, contact.normal);
            double[] cached = cache.get(key);
            if (cached == null) {
                continue;
            }
            double normalImpulse = cached[0];
            double tangentImpulse = cached[1];
            if (normalImpulse == 0.0D && tangentImpulse == 0.0D) {
                continue;
            }

            Vector3d impulse = new Vector3d(contact.normal).mul(normalImpulse);
            if (tangentImpulse != 0.0D) {
                contact.tangent.set(cached[2], cached[3], cached[4]);
                if (contact.tangent.lengthSquared() > RUBBER_EPSILON) {
                    contact.tangentInitialised = true;
                    impulse.fma(tangentImpulse, contact.tangent);
                }
            }

            bodyA.applyImpulseAt(impulse, contact.point);
            if (bodyB != null) {
                bodyB.applyImpulseAt(new Vector3d(impulse).negate(), contact.point);
            }
            contact.normalImpulse = normalImpulse;
            contact.tangentImpulse = contact.tangentInitialised ? tangentImpulse : 0.0D;
        }
    }

    private static void solveNormal(RigidBody bodyA, @Nullable RigidBody bodyB, Contact contact,
                                    double h, double restitution) {
        Vector3d relative = relativeVelocity(bodyA, bodyB, contact.point);
        double normalVelocity = relative.dot(contact.normal);

        double effectiveMass = effectiveMass(bodyA, bodyB, contact, contact.normal);
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
            Vector3d impulse = new Vector3d(contact.normal).mul(delta);
            bodyA.applyImpulseAt(impulse, contact.point);
            if (bodyB != null) {
                bodyB.applyImpulseAt(new Vector3d(impulse).negate(), contact.point);
            }
        }
    }

    private static void solveFriction(RigidBody bodyA, @Nullable RigidBody bodyB, Contact contact,
                                      double friction) {
        if (friction <= 0.0D) {
            return;
        }

        Vector3d relative = relativeVelocity(bodyA, bodyB, contact.point);
        Vector3d tangential = new Vector3d(relative)
                .sub(new Vector3d(contact.normal).mul(relative.dot(contact.normal)));

        // Freeze the friction direction on the first solver iteration: letting it
        // chase the velocity every iteration makes resting structures slide away.
        if (!contact.tangentInitialised) {
            if (tangential.length() < STATIC_FRICTION_EPSILON) {
                return;
            }
            contact.tangent.set(tangential).normalize();
            contact.tangentInitialised = true;
        }

        double speed = relative.dot(contact.tangent);
        if (Math.abs(speed) < RUBBER_EPSILON) {
            return;
        }

        double effectiveMass = effectiveMass(bodyA, bodyB, contact, contact.tangent);
        if (effectiveMass < RUBBER_EPSILON) {
            return;
        }

        double delta = -speed / effectiveMass;
        double maxFriction = friction * contact.normalImpulse;
        double previous = contact.tangentImpulse;
        contact.tangentImpulse = Math.max(-maxFriction, Math.min(maxFriction, previous + delta));
        delta = contact.tangentImpulse - previous;

        if (delta != 0.0D) {
            Vector3d impulse = new Vector3d(contact.tangent).mul(delta);
            bodyA.applyImpulseAt(impulse, contact.point);
            if (bodyB != null) {
                bodyB.applyImpulseAt(new Vector3d(impulse).negate(), contact.point);
            }
        }
    }

    private static Vector3d relativeVelocity(RigidBody bodyA, @Nullable RigidBody bodyB, Vector3d point) {
        Vector3d velocity = bodyA.velocityAt(point, new Vector3d());
        if (bodyB != null) {
            velocity.sub(bodyB.velocityAt(point, new Vector3d()));
        }
        return velocity;
    }

    private static double effectiveMass(RigidBody bodyA, @Nullable RigidBody bodyB,
                                        Contact contact, Vector3d direction) {
        double mass = bodyA.getInverseMass() + angularTerm(bodyA, contact.point, direction);
        if (bodyB != null) {
            mass += bodyB.getInverseMass() + angularTerm(bodyB, contact.point, direction);
        }
        return mass;
    }

    private static double angularTerm(RigidBody body, Vector3d point, Vector3d direction) {
        Vector3d lever = new Vector3d(point).sub(body.position);
        Vector3d leverCrossDirection = lever.cross(direction, new Vector3d());
        Vector3d angular = new Vector3d(leverCrossDirection);
        body.getInverseInertiaWorld().transform(angular);
        angular.cross(lever, angular);
        return angular.dot(direction);
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
