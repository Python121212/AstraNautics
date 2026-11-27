package com.astranautics.block.entity;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicsAssemblerBlock;
import com.astranautics.block.PhysicsSwitchBlock;
import com.astranautics.entity.ModEntities;
import com.astranautics.entity.PhysicsShip;
import com.astranautics.physics.ShipStructure;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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

    /** Why an assembly attempt returned the ship (or not). Translated for the player. */
    public enum Reason {
        NONE,
        OK,
        EMPTY,
        NO_ENTITY,
        SPAWN_REFUSED,
        EXCEPTION
    }

    /**
     * Outcome of an assembly attempt, used for the game logic, the chat feedback and
     * the diagnostics command.
     *
     * @param arg extra number for the message (the search radius for {@link Reason#EMPTY})
     * @param detail English debug text for the log / status line
     */
    public record Result(@Nullable PhysicsShip ship, int blocks, Reason reason, int arg, String detail) {
        public boolean ok() {
            return this.ship != null;
        }

        public Component message() {
            Component text = switch (this.reason) {
                case NONE -> Component.translatable("message.astranautics.reason.none");
                case OK -> Component.translatable(
                        "message.astranautics.physics_assembler.assembled", this.blocks);
                case EMPTY -> Component.translatable("message.astranautics.reason.empty", this.arg);
                case NO_ENTITY -> Component.translatable("message.astranautics.reason.no_entity");
                case SPAWN_REFUSED -> Component.translatable("message.astranautics.reason.spawn_refused");
                case EXCEPTION -> Component.translatable("message.astranautics.reason.exception", this.detail);
            };
            ChatFormatting color = switch (this.reason) {
                case OK -> ChatFormatting.AQUA;
                case NONE -> ChatFormatting.GRAY;
                default -> ChatFormatting.RED;
            };
            return text.withStyle(color);
        }
    }

    public static Result assemble(ServerLevel level, BlockPos assemblerPos, int radius, int maxBlocks) {
        Set<BlockPos> found;
        try {
            found = floodFill(level, assemblerPos, radius, maxBlocks);
        } catch (Exception exception) {
            AstraNautics.LOGGER.error("[astranautics] scanning the structure around {} failed", assemblerPos, exception);
            return exception(exception);
        }

        if (found.isEmpty()) {
            return new Result(null, 0, Reason.EMPTY, radius,
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

        ShipStructure structure;
        PhysicsShip ship;
        Vector3d worldCenter;
        try {
            structure = ShipStructure.of(localVoxels);
            Vector3d centerOfMass = structure.getCenterOfMass();
            worldCenter = new Vector3d(
                    corner.getX() + centerOfMass.x,
                    corner.getY() + centerOfMass.y,
                    corner.getZ() + centerOfMass.z);

            ship = ModEntities.PHYSICS_SHIP.get().create(level);
            if (ship == null) {
                return new Result(null, found.size(), Reason.NO_ENTITY, 0,
                        "the physics_ship entity type is missing");
            }
            ship.initialise(structure, worldCenter);
        } catch (Exception exception) {
            // Never let a broken structure take the server thread down with it.
            AstraNautics.LOGGER.error("[astranautics] building the ship entity around {} failed", assemblerPos, exception);
            return exception(exception);
        }

        try {
            if (!level.addFreshEntity(ship)) {
                return new Result(null, found.size(), Reason.SPAWN_REFUSED, 0,
                        "the server refused to spawn the ship entity");
            }
        } catch (Exception exception) {
            AstraNautics.LOGGER.error("[astranautics] spawning the ship around {} failed", assemblerPos, exception);
            return exception(exception);
        }

        // Remove the source blocks only after the entity exists.
        for (BlockPos pos : found) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }

        return new Result(ship, found.size(), Reason.OK, found.size(),
                found.size() + " blocks assembled");
    }

    private static Result exception(Exception exception) {
        String detail = exception.getClass().getSimpleName()
                + (exception.getMessage() == null ? "" : ": " + exception.getMessage());
        return new Result(null, 0, Reason.EXCEPTION, 0, detail);
    }

    /** How many blocks a structure would contain, without touching the world. */
    public static int preview(ServerLevel level, BlockPos assemblerPos, int radius, int maxBlocks) {
        try {
            return floodFill(level, assemblerPos, radius, maxBlocks).size();
        } catch (Exception exception) {
            AstraNautics.LOGGER.error("[astranautics] preview around {} failed", assemblerPos, exception);
            return 0;
        }
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
