package com.astranautics.block.entity;

import com.astranautics.block.PhysicsAssemblerBlock;
import com.astranautics.block.PhysicsSwitchBlock;
import com.astranautics.entity.PhysicsShip;
import com.astranautics.entity.ModEntities;
import com.astranautics.physics.BlockPhysics;
import com.astranautics.physics.ShipStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Detaches a connected blob of blocks around a physics assembler and turns it into a
 * {@link PhysicsShip}.
 */
public final class ShipAssembler {
    private ShipAssembler() {
    }

    /**
     * Flood-fills the blocks connected to {@code anchorPos} and replaces them with a
     * ship entity.
     *
     * @return the new ship, or {@code null} when nothing suitable was found
     */
    @Nullable
    public static PhysicsShip assemble(ServerLevel level, BlockPos anchorPos, int radius, int maxBlocks) {
        Set<BlockPos> found = floodFill(level, anchorPos, radius, maxBlocks);
        if (found.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (BlockPos pos : found) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }
        BlockPos corner = new BlockPos(minX, minY, minZ);

        Map<BlockPos, BlockState> localVoxels = new LinkedHashMap<>();
        for (BlockPos pos : found) {
            localVoxels.put(pos.subtract(corner), level.getBlockState(pos));
        }

        ShipStructure structure = ShipStructure.of(localVoxels);

        // World position of the centre of mass.
        Vector3d centerOfMass = structure.getCenterOfMass();
        Vector3d worldCenter = new Vector3d(
                corner.getX() + centerOfMass.x,
                corner.getY() + centerOfMass.y,
                corner.getZ() + centerOfMass.z);

        PhysicsShip ship = ModEntities.GRAVITY_SHIP.get().create(level);
        if (ship == null) {
            return null;
        }
        ship.initialise(structure, worldCenter);
        level.addFreshEntity(ship);

        // Remove the source blocks only after the entity exists.
        for (BlockPos pos : found) {
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }

        return ship;
    }

    private static Set<BlockPos> floodFill(ServerLevel level, BlockPos anchorPos, int radius, int maxBlocks) {
        Set<BlockPos> visited = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        double radiusSq = (double) radius * radius;

        for (Direction direction : Direction.values()) {
            BlockPos start = anchorPos.relative(direction);
            if (isAssemblable(level, start) && start.distSqr(anchorPos) <= radiusSq) {
                queue.add(start);
            }
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && visited.size() < maxBlocks) {
            BlockPos pos = queue.poll();
            if (!visited.add(pos)) {
                continue;
            }
            for (Direction direction : Direction.values()) {
                cursor.setWithOffset(pos, direction);
                if (visited.contains(cursor)) {
                    continue;
                }
                if (cursor.distSqr(anchorPos) > radiusSq) {
                    continue;
                }
                if (isAssemblable(level, cursor)) {
                    queue.add(cursor.immutable());
                }
            }
        }

        return visited;
    }

    private static boolean isAssemblable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!BlockPhysics.isSolid(level, pos, state)) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        if (state.getBlock() instanceof PhysicsAssemblerBlock || state.getBlock() instanceof PhysicsSwitchBlock) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null;
    }

    /** Convenience for callers that only have the anchor's position. */
    public static Vec3 cornerFor(Set<BlockPos> positions) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }
        return new Vec3(minX, minY, minZ);
    }
}
