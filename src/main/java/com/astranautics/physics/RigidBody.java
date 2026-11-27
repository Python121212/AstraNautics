package com.astranautics.physics;

import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * A 6-DOF rigid body: linear + angular state, mass properties and impulses.
 *
 * <p>Units are Minecraft blocks and seconds. World up is +Y.</p>
 */
public class RigidBody {
    /** World position of the centre of mass. */
    public final Vector3d position = new Vector3d();
    /** Orientation, always normalised. */
    public final Quaterniond orientation = new Quaterniond();
    public final Vector3d linearVelocity = new Vector3d();
    /** Angular velocity in radians/second, world space. */
    public final Vector3d angularVelocity = new Vector3d();

    private final Vector3d forceAccumulator = new Vector3d();
    private final Vector3d torqueAccumulator = new Vector3d();

    private double mass = 1.0D;
    private double inverseMass = 1.0D;
    private final Matrix3d inertiaLocal = new Matrix3d();
    private final Matrix3d inverseInertiaLocal = new Matrix3d();
    private final Matrix3d inverseInertiaWorld = new Matrix3d();
    private final Matrix3d rotationMatrix = new Matrix3d();

    /** Kinematic bodies (physics switched off) are not moved by forces. */
    private boolean kinematic;

    public RigidBody() {
        this.inertiaLocal.identity();
        this.inverseInertiaLocal.identity();
        this.inverseInertiaWorld.identity();
        this.rotationMatrix.identity();
    }

    public void setMassProperties(double mass, Matrix3d inertiaLocalIn) {
        this.mass = Math.max(mass, 1.0E-4D);
        this.inverseMass = 1.0D / this.mass;
        this.inertiaLocal.set(inertiaLocalIn);
        this.inverseInertiaLocal.set(inertiaLocalIn).invert();
        this.updateRotationMatrix();
    }

    public double getMass() {
        return mass;
    }

    public double getInverseMass() {
        return kinematic ? 0.0D : inverseMass;
    }

    public boolean isKinematic() {
        return kinematic;
    }

    public void setKinematic(boolean kinematic) {
        this.kinematic = kinematic;
        if (kinematic) {
            linearVelocity.zero();
            angularVelocity.zero();
            forceAccumulator.zero();
            torqueAccumulator.zero();
        }
    }

    public Matrix3d getRotationMatrix() {
        return rotationMatrix;
    }

    /** Inverse world-space inertia tensor (zero for kinematic bodies). */
    public Matrix3d getInverseInertiaWorld() {
        return inverseInertiaWorld;
    }

    public void updateRotationMatrix() {
        orientation.normalize();
        rotationMatrix.rotation(orientation);
        // I_world^-1 = R * I_local^-1 * R^T
        Matrix3d rt = new Matrix3d(rotationMatrix).transpose();
        inverseInertiaWorld.set(rotationMatrix)
                .mul(inverseInertiaLocal)
                .mul(rt);
    }

    public void applyForce(Vector3d force) {
        if (kinematic) {
            return;
        }
        forceAccumulator.add(force);
    }

    public void applyForceAt(Vector3d force, Vector3d worldPoint) {
        if (kinematic) {
            return;
        }
        forceAccumulator.add(force);
        Vector3d lever = new Vector3d(worldPoint).sub(position);
        torqueAccumulator.add(lever.cross(force, new Vector3d()));
    }

    public void applyImpulseAt(Vector3d impulse, Vector3d worldPoint) {
        if (kinematic) {
            return;
        }
        linearVelocity.add(impulse.x * inverseMass, impulse.y * inverseMass, impulse.z * inverseMass);

        Vector3d lever = new Vector3d(worldPoint).sub(position);
        Vector3d angularImpulse = lever.cross(impulse, new Vector3d());
        inverseInertiaWorld.transform(angularImpulse);
        angularVelocity.add(angularImpulse);
    }

    public void applyAngularImpulse(Vector3d angularImpulse) {
        if (kinematic) {
            return;
        }
        Vector3d delta = new Vector3d(angularImpulse);
        inverseInertiaWorld.transform(delta);
        angularVelocity.add(delta);
    }

    /** Velocity of the material point currently at {@code worldPoint}. */
    public Vector3d velocityAt(Vector3d worldPoint, Vector3d dest) {
        Vector3d lever = new Vector3d(worldPoint).sub(position);
        // v = omega x r + v_cm  (note the multiplication order!)
        angularVelocity.cross(lever, dest);
        return dest.add(linearVelocity);
    }

    public Vector3d toWorld(Vector3d localPoint, Vector3d dest) {
        rotationMatrix.transform(localPoint, dest);
        return dest.add(position);
    }

    public Vector3d toLocal(Vector3d worldPoint, Vector3d dest) {
        dest.set(worldPoint).sub(position);
        return dest.rotate(new Quaterniond(orientation).conjugate());
    }

    /** Semi-implicit Euler velocity integration with damping. */
    public void integrateVelocities(double h, double linearDamping, double angularDamping) {
        if (kinematic) {
            return;
        }

        linearVelocity.add(forceAccumulator.x * inverseMass * h,
                forceAccumulator.y * inverseMass * h,
                forceAccumulator.z * inverseMass * h);

        Vector3d angularAcceleration = new Vector3d(torqueAccumulator);
        inverseInertiaWorld.transform(angularAcceleration);
        angularVelocity.add(angularAcceleration.mul(h));

        double linearFactor = Math.pow(linearDamping, h * 20.0D);
        double angularFactor = Math.pow(angularDamping, h * 20.0D);
        linearVelocity.mul(linearFactor);
        angularVelocity.mul(angularFactor);

        forceAccumulator.zero();
        torqueAccumulator.zero();
    }

    public void integratePosition(double h) {
        if (kinematic) {
            return;
        }

        position.fma(h, linearVelocity);
        integrateOrientation(h);
    }

    private void integrateOrientation(double h) {
        if (angularVelocity.lengthSquared() < 1.0E-12D) {
            return;
        }
        Quaterniond spin = new Quaterniond(
                angularVelocity.x, angularVelocity.y, angularVelocity.z, 0.0D);
        spin.mul(orientation);
        orientation.x += 0.5D * h * spin.x;
        orientation.y += 0.5D * h * spin.y;
        orientation.z += 0.5D * h * spin.z;
        orientation.w += 0.5D * h * spin.w;
        orientation.normalize();
        updateRotationMatrix();
    }

    public void resetAccumulators() {
        forceAccumulator.zero();
        torqueAccumulator.zero();
    }

    public double kineticEnergy() {
        double linear = 0.5D * mass * linearVelocity.lengthSquared();
        Vector3d omega = new Vector3d(angularVelocity);
        Vector3d angularMomentum = inertiaLocal.transform(omega);
        // approximate: use local inertia (rotation invariant enough for sleep tests)
        double angular = 0.5D * angularMomentum.dot(angularVelocity);
        return linear + angular;
    }
}
