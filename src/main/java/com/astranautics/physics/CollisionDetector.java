package com.astranautics.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates block-vs-world contacts for a ship.
 *
 * <p>Only voxels on the surface of the structure are tested, and only against
 * blocks whose voxel grid cell actually overlaps the ship voxel, which keeps the
 * detector cheap when a ship is flying.</p>
 */
public final class CollisionDetector {
    private static final double MARGIN = 0.08D;
    private static final int MAX_CONTACTS = 48;

    private CollisionDetector() {
    }

    public static List<Contact> collect(BlockCollisionSource source, RigidBody body, VoxelBody voxelBody) {
        List<Contact> contacts = new ArrayList<>();

        Obb voxelObb = new Obb();
        Obb worldObb = new Obb();
        Vector3d normal = new Vector3d();
        double[] penetration = new double[1];
        Vector3d localCenter = new Vector3d();
        Vector3d worldCenter = new Vector3d();
        Vector3d contactPoint = new Vector3d();

        for (BlockPos localPos : voxelBody.surfaceVoxels()) {
            AABB box = voxelBody.collisionBox(localPos);
            if (VoxelShapes.isEmpty(box)) {
                // No collision shape (a torch, a flower, ...): it rides along but never hits.
                continue;
            }
            voxelBody.localCenterRelativeToCom(localPos, localCenter);
            // Offset from the voxel centre to the centre of its collision box, so a hull of
            // slabs or stairs rests on the shape it actually has.
            localCenter.x += (box.minX + box.maxX) * 0.5D - 0.5D;
            localCenter.y += (box.minY + box.maxY) * 0.5D - 0.5D;
            localCenter.z += (box.minZ + box.maxZ) * 0.5D - 0.5D;
            body.toWorld(localCenter, worldCenter);
            voxelObb.set(worldCenter, body.getRotationMatrix(),
                    box.getXsize() * 0.5D, box.getYsize() * 0.5D, box.getZsize() * 0.5D);

            AABB loose = voxelObb.toAabb().inflate(MARGIN);
            int minX = Mth.floor(loose.minX);
            int minY = Mth.floor(loose.minY);
            int minZ = Mth.floor(loose.minZ);
            int maxX = Mth.floor(loose.maxX);
            int maxY = Mth.floor(loose.maxY);
            int maxZ = Mth.floor(loose.maxZ);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (AABB worldBox : source.collisionBoxesAt(new BlockPos(x, y, z))) {
                            worldObb.setAabb(worldBox);
                            if (!Obb.intersect(voxelObb, worldObb, normal, penetration)) {
                                continue;
                            }
                            contactPoint.set(voxelObb.center).add(worldObb.center).mul(0.5D);
                            contacts.add(new Contact().set(contactPoint, normal, penetration[0]));
                        }
                    }
                }
            }
        }

        if (contacts.size() > MAX_CONTACTS) {
            contacts.sort(Comparator.comparingDouble((Contact c) -> c.penetration).reversed());
            return new ArrayList<>(contacts.subList(0, MAX_CONTACTS));
        }
        return contacts;
    }
}
