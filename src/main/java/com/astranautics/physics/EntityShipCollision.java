package com.astranautics.physics;

import com.astranautics.entity.PhysicsShip;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes entities interact with a ship: they are pushed out of the hull and, when
 * they land on top of it, they ride along with its surface velocity.
 *
 * <p>This is a kinematic response (entities do not push the ship) which matches how
 * boats and shulker boxes behave in vanilla and keeps the cost negligible.</p>
 */
public final class EntityShipCollision {
    private static final double QUERY_RADIUS = 2.0D;
    /** Max distance an entity is moved out of the hull per iteration (blocks). */
    private static final double MAX_PUSH = 1.0D;
    /** Deep penetrations (a ship sweeping through a player) are resolved over several passes. */
    private static final int MAX_ITERATIONS = 4;

    private EntityShipCollision() {
    }

    public static void resolve(ServerLevel level, PhysicsShip ship, VoxelSpatialIndex index) {
        AABB area = ship.getBoundingBox().inflate(1.5D);
        List<Entity> entities = level.getEntities(ship, area,
                entity -> !(entity instanceof PhysicsShip) && entity.isAlive() && !entity.isSpectator());
        if (entities.isEmpty()) {
            return;
        }

        RigidBody body = ship.getBody();
        Obb voxelObb = new Obb();
        Obb entityObb = new Obb();
        Vector3d normal = new Vector3d();
        double[] penetration = new double[1];
        Vector3d queryCenter = new Vector3d();
        List<VoxelSpatialIndex.Entry> nearby = new ArrayList<>();

        for (Entity entity : entities) {
            for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
                if (!resolveOne(ship, body, index, entity, voxelObb, entityObb, normal, penetration,
                        queryCenter, nearby)) {
                    break;
                }
            }
        }
    }

    /** One push-out step. Returns false when the entity no longer touches the hull. */
    private static boolean resolveOne(PhysicsShip ship, RigidBody body, VoxelSpatialIndex index,
                                      Entity entity, Obb voxelObb, Obb entityObb, Vector3d normal,
                                      double[] penetration, Vector3d queryCenter,
                                      List<VoxelSpatialIndex.Entry> nearby) {
        AABB box = entity.getBoundingBox();
        queryCenter.set((box.minX + box.maxX) * 0.5D, (box.minY + box.maxY) * 0.5D,
                (box.minZ + box.maxZ) * 0.5D);
        nearby.clear();
        index.query(queryCenter, QUERY_RADIUS, nearby);
        if (nearby.isEmpty()) {
            return false;
        }

        entityObb.setAabb(box);
        double deepest = 0.0D;
        Vector3d deepestNormal = null;
        VoxelSpatialIndex.Entry deepestEntry = null;

        for (VoxelSpatialIndex.Entry entry : nearby) {
            voxelObb.set(entry.center, body.getRotationMatrix(), 0.5D, 0.5D, 0.5D);
            if (!Obb.intersect(entityObb, voxelObb, normal, penetration)) {
                continue;
            }
            if (penetration[0] > deepest) {
                deepest = penetration[0];
                deepestNormal = new Vector3d(normal);
                deepestEntry = entry;
            }
        }

        if (deepestNormal == null) {
            return false;
        }

        double push = Math.min(deepest + 0.001D, MAX_PUSH);
        Vector3d surfaceVelocity = body.velocityAt(deepestEntry.center, new Vector3d());
        Vector3d newPosition = new Vector3d(entity.getX() + deepestNormal.x * push,
                entity.getY() + deepestNormal.y * push,
                entity.getZ() + deepestNormal.z * push);

        moveEntity(entity, newPosition);
        ship.wake();

        if (deepestNormal.y > 0.6D) {
            // Standing on the hull: ride along, do not accumulate fall damage.
            entity.fallDistance = 0.0F;
            entity.setOnGround(true);
            entity.setDeltaMovement(
                    surfaceVelocity.x,
                    Math.max(entity.getDeltaMovement().y, surfaceVelocity.y),
                    surfaceVelocity.z);
        } else {
            // Blocked by a wall: keep the surface velocity, kill the motion into the wall.
            net.minecraft.world.phys.Vec3 velocity = entity.getDeltaMovement();
            double into = velocity.x * deepestNormal.x + velocity.y * deepestNormal.y
                    + velocity.z * deepestNormal.z;
            double surfaceInto = surfaceVelocity.x * deepestNormal.x + surfaceVelocity.y * deepestNormal.y
                    + surfaceVelocity.z * deepestNormal.z;
            double remove = Math.min(into, surfaceInto);
            double vx = velocity.x - deepestNormal.x * remove;
            double vy = velocity.y - deepestNormal.y * remove;
            double vz = velocity.z - deepestNormal.z * remove;
            entity.setDeltaMovement(vx, vy, vz);
        }
        entity.hurtMarked = true;
        return true;
    }

    private static void moveEntity(Entity entity, Vector3d target) {
        if (entity instanceof ServerPlayer player) {
            player.connection.teleport(target.x, target.y, target.z, player.getYRot(), player.getXRot());
        } else {
            entity.setPos(target.x, target.y, target.z);
        }
    }
}
