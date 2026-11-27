package com.astranautics.block.entity;

import com.astranautics.block.PhysicsAssemblerBlock;
import com.astranautics.block.PhysicsSwitchBlock;
import com.astranautics.entity.ModEntities;
import com.astranautics.entity.PhysicsShip;
import com.astranautics.physics.ShipStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Detaches a connected blob of blocks around a physics assembler and turns it into
 * a {@link PhysicsShip}.
 *
 * <p>Every block is allowed into a structure, including creative-only blocks such
 * as bedrock and blocks with a block entity (chest, command block, ...). Only air,
 * blocks without a collision shape (flowers, torches, fluids) and the mod's own
 * controller blocks are skipped. The contents of block entities are not preserved
 * when the structure is detached.</p>
 */
public final class ShipAssembler {
    private ShipAssembler() {
    }

    /** Outcome of an assembly attempt, used for both the game logic and diagnostics. */
    public record Result(@Nullable PhysicsShip ship, int blocks, String reason) {
        public boolean ok() {
            return ship != null;
        }
    }

    public static Result assemble(ServerLevel level, BlockPos assemblerPos, int radius, int maxBlocks) {
        Set<BlockPos> found = floodFill(level, assemblerPos, radius, maxBlocks);
        if (found.isEmpty()) {
            return new Result(null, 0,
                    "no connected blocks with a collision shape within " + radius + " blocks");
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

        Vector3d centerOfMass = structure.getCenterOfMass();
        Vector3d worldCenter = new Vector3d(
                corner.getX() + centerOfMass.x,
                corner.getY() + centerOfMass.y,
                corner.getZ() + centerOfMass.z);

        PhysicsShip ship = ModEntities.PHYSICS_SHIP.get().create(level);
        if (ship == null) {
            return new Result(null, found.size(), "could not create the ship entity (entity type missing?)");
        }

        ship.initialise(structure, worldCenter);
        if (!level.addFreshEntity(ship)) {
            return new Result(null, found.size(), "the server refused to spawn the ship entity");
        }

        // Remove the source blocks only after the entity exists.
        for (BlockPos pos : found) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }

        return new Result(ship, found.size(), "ok");
    }

    /** How many blocks a structure would contain, without touching the world. */
    public static int preview(ServerLevel level, BlockPos assemblerPos, int radius, int maxBlocks) {
        return floodFill(level, assemblerPos, radius, maxBlocks).size();
    }

    private static Set<BlockPos> floodFill(ServerLevel level, BlockPos assemblerPos, int radius, int maxBlocks) {
        Set<BlockPos> visited = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        double radiusSq = (double) radius * radius;

        for (Direction direction : Direction.values()) {
            BlockPos start = assemblerPos.relative(direction);
            if (isAssemblable(level, start) && start.distSqr(assemblerPos) <= radiusSq) {
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
                if (visited.contains(cursor) || cursor.distSqr(assemblerPos) > radiusSq) {
                    continue;
                }
                if (isAssemblable(level, cursor)) {
                    // Copy: MutableBlockPos is reused for the next neighbour.
                    queue.add(cursor.immutable());
                }
            }
        }

        return visited;
    }

    /**
     * Everything is physical: no block-entity check, no hardness check. Only blocks
     * the simulation cannot represent are skipped.
     */
    public static boolean isAssemblable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof PhysicsAssemblerBlock
                || state.getBlock() instanceof PhysicsSwitchBlock) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        return !shape.isEmpty();
    }
}
