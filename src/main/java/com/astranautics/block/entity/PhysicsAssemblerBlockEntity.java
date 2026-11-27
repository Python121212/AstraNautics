package com.astranautics.block.entity;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicsAssemblerBlock;
import com.astranautics.config.AstraConfig;
import com.astranautics.entity.PhysicsShip;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Keeps the link between a physics assembler block and the ship it spawned.
 *
 * <p>Every few ticks the block state is compared with the current ship:</p>
 * <ul>
 *     <li>{@code enabled} and no ship &rarr; assemble the connected structure</li>
 *     <li>{@code !enabled} and a ship &rarr; dissolve it back into the world</li>
 * </ul>
 *
 * <p>A failed assembly leaves the block enabled and logs why, so the state no
 * longer flickers back off.</p>
 */
public class PhysicsAssemblerBlockEntity extends BlockEntity {
    private static final int CHECK_INTERVAL = 4;
    private static final long FAILURE_LOG_INTERVAL = 100L;

    @Nullable
    private UUID shipId;
    private int retryCooldown;
    private long lastFailureLog = Long.MIN_VALUE;
    private int lastBlockCount;

    public PhysicsAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHYSICS_ASSEMBLER.get(), pos, state);
    }

    @Nullable
    public UUID getShipId() {
        return shipId;
    }

    public int getLastBlockCount() {
        return lastBlockCount;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PhysicsAssemblerBlockEntity assembler) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.getGameTime() % CHECK_INTERVAL != 0L) {
            return;
        }

        boolean enabled = state.getValue(PhysicsAssemblerBlock.ENABLED);
        PhysicsShip ship = assembler.resolveShip(serverLevel);

        if (enabled && ship == null) {
            if (assembler.retryCooldown > 0) {
                assembler.retryCooldown -= CHECK_INTERVAL;
                return;
            }

            ShipAssembler.Result result = ShipAssembler.assemble(serverLevel, pos,
                    AstraConfig.ASSEMBLY_RADIUS.get(), AstraConfig.MAX_BLOCKS.get());
            assembler.lastBlockCount = result.blocks();

            if (result.ok() && result.ship() != null) {
                assembler.shipId = result.ship().getUUID();
                assembler.retryCooldown = 0;
                assembler.setChanged();
                AstraNautics.LOGGER.info("[astranautics] assembled {} blocks into a physics ship at {}",
                        result.blocks(), pos);
            } else {
                assembler.retryCooldown = 60;
                assembler.logFailure(serverLevel, pos, result.reason());
            }
            return;
        }

        if (!enabled && ship != null) {
            ship.dissolve(serverLevel);
            assembler.shipId = null;
            assembler.retryCooldown = 0;
            assembler.setChanged();
            AstraNautics.LOGGER.info("[astranautics] dissolved the physics ship at {}", pos);
            return;
        }

        if (ship == null && assembler.shipId != null) {
            assembler.shipId = null;
            assembler.setChanged();
        }
    }

    private void logFailure(ServerLevel level, BlockPos pos, String reason) {
        long now = level.getGameTime();
        if (now - lastFailureLog < FAILURE_LOG_INTERVAL) {
            return;
        }
        lastFailureLog = now;
        AstraNautics.LOGGER.warn("[astranautics] assembly at {} failed: {} (radius {}, max {} blocks)",
                pos, reason, AstraConfig.ASSEMBLY_RADIUS.get(), AstraConfig.MAX_BLOCKS.get());
    }

    @Nullable
    private PhysicsShip resolveShip(ServerLevel level) {
        if (shipId == null) {
            return null;
        }
        Entity entity = level.getEntity(shipId);
        if (entity instanceof PhysicsShip ship && ship.isAlive()) {
            return ship;
        }
        shipId = null;
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (shipId != null) {
            tag.putUUID("Ship", shipId);
        }
        tag.putInt("LastBlocks", lastBlockCount);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        shipId = tag.hasUUID("Ship") ? tag.getUUID("Ship") : null;
        lastBlockCount = tag.getInt("LastBlocks");
    }
}
