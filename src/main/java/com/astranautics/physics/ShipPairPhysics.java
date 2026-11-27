package com.astranautics.physics;

import com.astranautics.entity.GravityShip;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Ship-vs-ship contacts.
 *
 * <p>Broad phase: the two ships' bounding boxes. Narrow phase: for every surface
 * voxel of ship A, look up the voxels of ship B nearby (uniform grid) and run the
 * OBB/OBB test. Contact normals point from B towards A.</p>
 */
public final class ShipPairPhysics {
    private static final int MAX_CONTACTS = 24;

    private ShipPairPhysics() {
    }

    public static void resolve(PhysicsParams params, double h,
                               GravityShip shipA, VoxelSpatialIndex indexA,
                               GravityShip shipB, VoxelSpatialIndex indexB) {
        RigidBody bodyA = shipA.getBody();
        RigidBody bodyB = shipB.getBody();
        if (bodyA.isKinematic() && bodyB.isKinematic()) {
            return;
        }
        if (!shipA.getBoundingBox().inflate(1.0D).intersects(shipB.getBoundingBox())) {
            return;
        }

        List<Contact> contacts = new ArrayList<>();
        Obb obbA = new Obb();
        Obb obbB = new Obb();
        Vector3d normal = new Vector3d();
        double[] penetration = new double[1];
        Vector3d contactPoint = new Vector3d();
        List<VoxelSpatialIndex.Entry> nearby = new ArrayList<>();

        for (VoxelSpatialIndex.Entry entryA : indexA.entries()) {
            nearby.clear();
            indexB.query(entryA.center, 1.6D, nearby);
            if (nearby.isEmpty()) {
                continue;
            }
            obbA.set(entryA.center, bodyA.getRotationMatrix(), 0.5D, 0.5D, 0.5D);

            for (VoxelSpatialIndex.Entry entryB : nearby) {
                obbB.set(entryB.center, bodyB.getRotationMatrix(), 0.5D, 0.5D, 0.5D);
                if (!Obb.intersect(obbA, obbB, normal, penetration)) {
                    continue;
                }
                contactPoint.set(obbA.center).add(obbB.center).mul(0.5D);
                contacts.add(new Contact().set(contactPoint, normal, penetration[0]));
                if (contacts.size() >= MAX_CONTACTS) {
                    break;
                }
            }
            if (contacts.size() >= MAX_CONTACTS) {
                break;
            }
        }

        if (contacts.isEmpty()) {
            return;
        }

        shipA.wake();
        shipB.wake();
        ShipPhysics.solveContacts(bodyA, bodyB, contacts, h, params);

        // Split the deepest overlap so ships do not grind into each other.
        Contact deepest = contacts.get(0);
        for (Contact contact : contacts) {
            if (contact.penetration > deepest.penetration) {
                deepest = contact;
            }
        }
        double correction = Math.min(Math.max(deepest.penetration - 0.005D, 0.0D), 0.1D) * 0.3D;
        if (correction > 0.0D) {
            boolean aDynamic = !bodyA.isKinematic();
            boolean bDynamic = !bodyB.isKinematic();
            double share = aDynamic && bDynamic ? 0.5D : 1.0D;
            if (aDynamic) {
                bodyA.position.fma(correction * share, deepest.normal);
            }
            if (bDynamic) {
                bodyB.position.fma(-correction * share, deepest.normal);
            }
        }
    }
}
