package com.astranautics.physics;

import org.joml.Vector3d;

/** A single contact point produced by the collision detector. */
public final class Contact {
    public final Vector3d point = new Vector3d();
    public final Vector3d normal = new Vector3d();
    public double penetration;

    public Contact set(Vector3d pointIn, Vector3d normalIn, double penetrationIn) {
        this.point.set(pointIn);
        this.normal.set(normalIn);
        this.penetration = penetrationIn;
        return this;
    }
}
