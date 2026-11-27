package com.astranautics.physics;

import com.astranautics.block.PhysicsSwitchBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/** {@link BlockCollisionSource} backed by a real {@link ServerLevel}. */
public final class LevelCollisionSource implements BlockCollisionSource {
    private final ServerLevel level;

    public LevelCollisionSource(ServerLevel level) {
        this.level = level;
    }

    @Override
    public List<AABB> collisionBoxesAt(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return List.of();
        }
        // The control block is deliberately transparent to ships: a switch embedded in
        // the hull would otherwise pin the structure in place.
        if (state.getBlock() instanceof PhysicsSwitchBlock) {
            return List.of();
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return List.of();
        }
        List<AABB> boxes = new ArrayList<>(1);
        for (AABB box : shape.toAabbs()) {
            boxes.add(box.move(pos.getX(), pos.getY(), pos.getZ()));
        }
        return boxes;
    }
}
