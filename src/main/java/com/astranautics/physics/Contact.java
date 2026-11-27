package com.astranautics.physics;

import org.joml.Vector3d;

/** A single contact point produced by the collision detector. */
public final class Contact {
    public final Vector3d point = new Vector3d();
    public final Vector3d normal = new Vector3d();
    public double penetration;

    /** Accumulated normal impulse (clamped to >= 0 across solver iterations). */
    public double normalImpulse;
    /** Accumulated friction impulse. */
    public double tangentImpulse;
    /** Restitution is only applied on the first iteration of a solve. */
    public boolean restitutionApplied;
    /** Friction direction, frozen on the first iteration so the solver does not drift. */
    public final Vector3d tangent = new Vector3d(1.0D, 0.0D, 0.0D);
    public boolean tangentInitialised;

    public Contact set(Vector3d pointIn, Vector3d normalIn, double penetrationIn) {
        this.point.set(pointIn);
        this.normal.set(normalIn);
        this.penetration = penetrationIn;
        resetImpulses();
        return this;
    }

    public void resetImpulses() {
        this.normalImpulse = 0.0D;
        this.tangentImpulse = 0.0D;
        this.restitutionApplied = false;
        this.tangentInitialised = false;
    }
}
