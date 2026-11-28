package com.astranautics.physics;

import com.astranautics.entity.PhysicsShip;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Makes entities interact with a ship: they are pushed out of the hull, can stand and
 * walk on it, and ride along when the deck moves.
 *
 * <p>This is a kinematic response (entities do not push the ship), which matches how
 * boats and shulker boxes behave in vanilla and keeps the cost negligible.</p>
 *
 * <p>The resolution is deliberately split by contact direction, because a single
 * "deepest contact wins" rule makes walking on a deck feel broken:</p>
 * <ul>
 *     <li><b>support</b> (normal points up) &rarr; separate upward by the penetration
 *     depth. The player's own horizontal velocity is <em>not</em> touched, so walking
 *     on the hull goes at full speed, and the deck's own surface velocity is added to
 *     the position so the player is carried along.</li>
 *     <li><b>ceiling</b> (normal points down) &rarr; separate downward and stop upward
 *     motion.</li>
 *     <li><b>wall</b> (horizontal normal) &rarr; push out sideways and cancel only the
 *     velocity going into the wall.</li>
 * </ul>
 *
 * <p>Corrections stay small, so a player standing on a hull is never teleported around
 * by the server (that used to fight the client's own simulation and made both standing
 * and walking jitter).</p>
 */
public final class EntityShipCollision {
    /** Voxels further than this from the entity are ignored. */
    private static final double QUERY_RADIUS = 2.0D;
    /** Max distance an entity is moved out of the hull per iteration (blocks). */
    private static final double MAX_PUSH = 0.5D;
    /** Deep penetrations (a ship sweeping through a player) are resolved over several passes. */
    private static final int MAX_ITERATIONS = 3;
    /** Penetrations below this (blocks) are ignored to avoid jitter. */
    private static final double CONTACT_SKIN = 0.02D;
    /** Penetrations below this are treated as already separated for support contacts. */
    private static final double SUPPORT_SKIN = 0.004D;
    /** A normal with at least this much Y is a support (or ceiling) contact. */
    private static final double VERTICAL_NORMAL = 0.5D;
    /**
     * Wall contacts are tested against the entity box trimmed by this much at the feet.
     *
     * <p>Without it, an entity that is standing on the deck and sinks the few centimetres
     * of one tick of gravity also overlaps the *neighbouring* deck block, and the shallow
     * overlap there is resolved along X/Z: the player gets shoved back and their walk
     * speed is cancelled, which feels like "moves a little, stops, moves again".</p>
     */
    private static final double FEET_CLEARANCE = 0.12D;
    /** One tick in seconds. */
    private static final double TICK_SECONDS = 0.05D;
    /** Corrections larger than this (blocks) are synced with a teleport packet. */
    private static final double TELEPORT_THRESHOLD_SQ = 0.4D * 0.4D;
    /**
     * Velocity changes smaller than this are not sent to the client. The server resolves
     * the exact same contacts, so the client reaches the same velocity on its own; a
     * motion packet every tick would only stomp the player's own movement.
     */
    private static final double VELOCITY_PACKET_THRESHOLD_SQ = 0.1D * 0.1D;

    /** Entities that had a support contact in the last resolution (weak keys, no leak). */
    private static final Map<Entity, Long> SUPPORTED = new WeakHashMap<>();

    private EntityShipCollision() {
    }

    /** True if {@code entity} stood on a hull in the previous tick. */
    public static boolean isSupported(Entity entity, long gameTime) {
        Long tick = SUPPORTED.get(entity);
        return tick != null && gameTime >= tick && gameTime - tick <= 2L;
    }

    public static void clearSupported() {
        SUPPORTED.clear();
    }

    public static void resolve(Level level, PhysicsShip ship, VoxelSpatialIndex index) {
        resolve(level, ship, index, null);
    }

    /**
     * @param extraFilter additional filter (e.g. only the local player on a client), may be null
     */
    public static void resolve(Level level, PhysicsShip ship, VoxelSpatialIndex index,
                              @Nullable Predicate<Entity> extraFilter) {
        AABB area = ship.getBoundingBox().inflate(1.5D);
        List<Entity> entities = level.getEntities(ship, area,
                entity -> !(entity instanceof PhysicsShip) && entity.isAlive() && !entity.isSpectator()
                        && (extraFilter == null || extraFilter.test(entity)));
        if (entities.isEmpty()) {
            return;
        }

        RigidBody body = ship.getBody();
        Obb voxelObb = new Obb();
        Obb entityObb = new Obb();
        Obb bodyObb = new Obb();
        Vector3d normal = new Vector3d();
        double[] penetration = new double[1];
        Vector3d queryCenter = new Vector3d();
        List<VoxelSpatialIndex.Entry> nearby = new ArrayList<>();

        for (Entity entity : entities) {
            SUPPORTED.remove(entity);
            for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
                if (!resolveOne(level, body, index, entity, voxelObb, entityObb, bodyObb, normal,
                        penetration, queryCenter, nearby)) {
                    break;
                }
            }
        }
    }

    /** One push-out step. Returns true when a deep contact may need another pass. */
    private static boolean resolveOne(Level level, RigidBody body, VoxelSpatialIndex index,
                                      Entity entity, Obb voxelObb, Obb entityObb, Obb bodyObb,
                                      Vector3d normal, double[] penetration, Vector3d queryCenter,
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
        double bodyMinY = Math.min(box.minY + FEET_CLEARANCE, box.maxY - 0.01D);
        bodyObb.setAabb(new AABB(box.minX, bodyMinY, box.minZ, box.maxX, box.maxY, box.maxZ));
        double supportPen = 0.0D;
        Vector3d supportNormal = null;
        VoxelSpatialIndex.Entry supportEntry = null;
        double ceilingPen = 0.0D;
        double wallPen = 0.0D;
        Vector3d wallNormal = null;

        for (VoxelSpatialIndex.Entry entry : nearby) {
            voxelObb.set(entry.center, body.getRotationMatrix(), 0.5D, 0.5D, 0.5D);
            if (!Obb.intersect(entityObb, voxelObb, normal, penetration)) {
                continue;
            }
            double pen = penetration[0];
            if (pen <= 0.0D) {
                continue;
            }
            if (normal.y > VERTICAL_NORMAL) {
                if (pen > supportPen) {
                    supportPen = pen;
                    supportNormal = new Vector3d(normal);
                    supportEntry = entry;
                }
            } else if (normal.y < -VERTICAL_NORMAL) {
                ceilingPen = Math.max(ceilingPen, pen);
            } else {
                // Confirm the side contact against the trimmed body box so the few
                // centimetres the feet sink into the deck do not read as a wall.
                if (!Obb.intersect(bodyObb, voxelObb, normal, penetration)) {
                    continue;
                }
                if (penetration[0] > wallPen) {
                    wallPen = penetration[0];
                    wallNormal = new Vector3d(normal);
                }
            }
        }

        if (supportNormal == null && wallNormal == null && ceilingPen <= 0.0D) {
            return false;
        }

        double targetX = entity.getX();
        double targetY = entity.getY();
        double targetZ = entity.getZ();
        Vec3 velocity = entity.getDeltaMovement();
        double vx = velocity.x;
        double vy = velocity.y;
        double vz = velocity.z;
        double velocityDeltaSq = 0.0D;
        boolean moved = false;

        // --- support: stand on the deck -------------------------------------------
        if (supportNormal != null) {
            // Remember the support even when the separation is already zero: the tick
            // handler uses it to cancel the player's own gravity (see ShipPlatformSupport).
            SUPPORTED.put(entity, level.getGameTime());
            entity.fallDistance = 0.0F;
            entity.setOnGround(true);
        }
        if (supportNormal != null && supportPen > SUPPORT_SKIN) {
            // Separate straight up by the vertical distance needed to clear the block:
            // correct for a tilted hull (unlike snapping to "voxel centre + 0.5", which
            // jumps between blocks) and it does not slide the entity sideways.
            double lift = Math.min(supportPen / Math.max(0.7D, supportNormal.y) + SUPPORT_SKIN, MAX_PUSH);
            targetY += lift;
            moved = true;

            if (vy < 0.0D) {
                velocityDeltaSq += vy * vy;
                vy = 0.0D;
            }

            // Ride with the deck: carry the entity by the surface velocity of the block
            // it stands on (this is what a moving platform does, without touching the
            // entity's own horizontal velocity).
            Vector3d surface = body.velocityAt(supportEntry.center, new Vector3d());
            if (Math.abs(surface.x) > 1.0E-4D || Math.abs(surface.z) > 1.0E-4D) {
                targetX += surface.x * TICK_SECONDS;
                targetZ += surface.z * TICK_SECONDS;
            }
        }

        // --- ceiling: pushed down by the hull above -------------------------------
        if (ceilingPen > CONTACT_SKIN) {
            targetY -= Math.min(ceilingPen + CONTACT_SKIN, MAX_PUSH);
            moved = true;
            if (vy > 0.0D) {
                velocityDeltaSq += vy * vy;
                vy = 0.0D;
            }
        }

        // --- wall: side of the hull ------------------------------------------------
        if (wallNormal != null && wallPen > CONTACT_SKIN) {
            double horizontal = Math.sqrt(wallNormal.x * wallNormal.x + wallNormal.z * wallNormal.z);
            if (horizontal > 1.0E-4D) {
                double nx = wallNormal.x / horizontal;
                double nz = wallNormal.z / horizontal;
                double push = Math.min(wallPen + CONTACT_SKIN, MAX_PUSH * 0.6D);
                targetX += nx * push;
                targetZ += nz * push;
                moved = true;

                double into = vx * nx + vz * nz;
                if (into < 0.0D) {
                    velocityDeltaSq += into * into;
                    vx -= nx * into;
                    vz -= nz * into;
                }
            }
        }

        if (!moved) {
            return false;
        }

        if (velocityDeltaSq > 0.0D) {
            entity.setDeltaMovement(vx, vy, vz);
            if (velocityDeltaSq > VELOCITY_PACKET_THRESHOLD_SQ) {
                // Only tell the client about a real velocity change: a motion packet every
                // tick stomps the player's own movement and shows up as stutter.
                entity.hurtMarked = true;
            }
        }

        double dx = targetX - entity.getX();
        double dy = targetY - entity.getY();
        double dz = targetZ - entity.getZ();
        if (dx * dx + dy * dy + dz * dz > 1.0E-10D) {
            moveEntity(level, entity, targetX, targetY, targetZ);
        }
        // Another pass is only useful when the entity was still deep inside the hull.
        return supportPen > CONTACT_SKIN || wallPen > 0.25D || ceilingPen > CONTACT_SKIN;
    }

    private static void moveEntity(Level level, Entity entity, double x, double y, double z) {
        double dx = x - entity.getX();
        double dy = y - entity.getY();
        double dz = z - entity.getZ();
        double distanceSq = dx * dx + dy * dy + dz * dz;

        if (level.isClientSide) {
            entity.setPos(x, y, z);
            return;
        }
        if (entity instanceof ServerPlayer player) {
            if (distanceSq > TELEPORT_THRESHOLD_SQ) {
                // Large corrections (falling through, a hull sweeping the player away)
                // must reach the client, otherwise it rubber-bands on its own.
                player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
            } else {
                // Small corrections are applied on both sides identically; keeping them
                // off the wire avoids a position packet every single tick.
                player.setPos(x, y, z);
            }
            return;
        }
        entity.setPos(x, y, z);
    }
}
