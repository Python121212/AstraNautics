package com.astranautics.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
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

    public static List<Contact> collect(ServerLevel level, RigidBody body, ShipStructure structure) {
        List<Contact> contacts = new ArrayList<>();

        Obb voxelObb = new Obb();
        Obb worldObb = new Obb();
        Vector3d normal = new Vector3d();
        double[] penetration = new double[1];
        Vector3d localCenter = new Vector3d();
        Vector3d worldCenter = new Vector3d();
        Vector3d contactPoint = new Vector3d();

        for (BlockPos localPos : structure.surfaceVoxels()) {
            structure.localCenterRelativeToCom(localPos, localCenter);
            body.toWorld(localCenter, worldCenter);
            voxelObb.set(worldCenter, body.getRotationMatrix(), 0.5D, 0.5D, 0.5D);

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
                        BlockPos worldPos = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(worldPos);
                        if (state.isAir()) {
                            continue;
                        }
                        VoxelShape shape = state.getCollisionShape(level, worldPos);
                        if (shape.isEmpty()) {
                            continue;
                        }
                        for (AABB box : shape.toAabbs()) {
                            AABB worldBox = box.move(worldPos.getX(), worldPos.getY(), worldPos.getZ());
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
