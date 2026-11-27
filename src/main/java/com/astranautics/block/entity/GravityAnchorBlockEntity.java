package com.astranautics.block.entity;

import com.astranautics.block.GravityAnchorBlock;
import com.astranautics.config.AstraConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Runs the gravity "physics simulation" of the anchor.
 *
 * <p>Every other server tick each non-spectator entity within
 * {@code gravity_anchor.radius} is accelerated towards the block centre, with the
 * force falling off linearly with distance. Entities already closer than
 * {@link #CORE_RADIUS} are ignored so they do not jitter inside the block.</p>
 */
public class GravityAnchorBlockEntity extends BlockEntity {
    private static final double CORE_RADIUS = 0.75D;

    public GravityAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAVITY_ANCHOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  GravityAnchorBlockEntity blockEntity) {
        if (!state.getValue(GravityAnchorBlock.ENABLED)) {
            return;
        }

        // 10 physics steps per second keeps the motion smooth at a low cost.
        if (level.getGameTime() % 2L != 0L) {
            return;
        }

        double radius = AstraConfig.RADIUS.get();
        double strength = AstraConfig.STRENGTH.get();
        double maxSpeed = AstraConfig.MAX_SPEED.get();

        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 min = center.subtract(radius, radius, radius);
        Vec3 max = center.add(radius, radius, radius);

        List<Entity> entities = level.getEntities((Entity) null, new AABB(min, max),
                entity -> !entity.isSpectator() && entity.isAlive());

        for (Entity entity : entities) {
            Vec3 offset = center.subtract(entity.position());
            double distance = offset.length();

            if (distance < CORE_RADIUS || distance > radius) {
                continue;
            }

            // Linear falloff: strongest at the centre, zero at the edge.
            double falloff = 1.0D - (distance / radius);
            Vec3 acceleration = offset.normalize().scale(strength * falloff);

            entity.push(acceleration.x, acceleration.y, acceleration.z);

            Vec3 velocity = entity.getDeltaMovement();
            if (velocity.length() > maxSpeed) {
                entity.setDeltaMovement(velocity.normalize().scale(maxSpeed));
            }
            entity.hurtMarked = true;
        }
    }
}
