package com.astranautics.physics;

import com.astranautics.config.AstraConfig;

/** Immutable solver tuning, snapshot from the config at the start of a tick. */
public final class PhysicsParams {
    public final double gravity;
    public final double friction;
    public final double restitution;
    public final double linearDamping;
    public final double angularDamping;
    public final int iterations;

    public PhysicsParams(double gravity, double friction, double restitution,
                         double linearDamping, double angularDamping, int iterations) {
        this.gravity = gravity;
        this.friction = friction;
        this.restitution = restitution;
        this.linearDamping = linearDamping;
        this.angularDamping = angularDamping;
        this.iterations = Math.max(1, iterations);
    }

    public static PhysicsParams fromConfig() {
        return new PhysicsParams(
                AstraConfig.GRAVITY.get(),
                AstraConfig.FRICTION.get(),
                AstraConfig.RESTITUTION.get(),
                AstraConfig.LINEAR_DAMPING.get(),
                AstraConfig.ANGULAR_DAMPING.get(),
                AstraConfig.SOLVER_ITERATIONS.get());
    }

    /** Default tuning used by the tests and by callers that have no config loaded. */
    public static PhysicsParams defaults() {
        return new PhysicsParams(32.0D, 0.6D, 0.05D, 0.999D, 0.995D, 4);
    }
}
