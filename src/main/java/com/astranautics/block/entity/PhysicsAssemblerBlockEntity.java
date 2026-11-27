package com.astranautics.block.entity;

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
 * Keeps the link between an anchor block and the ship it spawned.
 *
 * <p>Every few ticks the block state is compared with the current ship:</p>
 * <ul>
 *     <li>{@code enabled} and no ship &rarr; assemble the connected structure</li>
 *     <li>{@code !enabled} and a ship &rarr; dissolve it back into the world</li>
 * </ul>
 */
public class PhysicsAssemblerBlockEntity extends BlockEntity {
    private static final int CHECK_INTERVAL = 4;

    @Nullable
    private UUID shipId;
    private int failedAssemblies;

    public PhysicsAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHYSICS_ASSEMBLER.get(), pos, state);
    }

    @Nullable
    public UUID getShipId() {
        return shipId;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PhysicsAssemblerBlockEntity anchor) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.getGameTime() % CHECK_INTERVAL != 0L) {
            return;
        }

        boolean enabled = state.getValue(PhysicsAssemblerBlock.ENABLED);
        PhysicsShip ship = anchor.resolveShip(serverLevel);

        if (enabled && ship == null) {
            if (anchor.failedAssemblies > 0) {
                // Avoid retrying every tick on a hopeless structure.
                return;
            }
            PhysicsShip assembled = ShipAssembler.assemble(serverLevel, pos,
                    AstraConfig.ASSEMBLY_RADIUS.get(), AstraConfig.MAX_BLOCKS.get());
            if (assembled != null) {
                anchor.shipId = assembled.getUUID();
                anchor.failedAssemblies = 0;
                anchor.setChanged();
            } else {
                anchor.failedAssemblies = 20;
                serverLevel.setBlock(pos, state.setValue(PhysicsAssemblerBlock.ENABLED, false), 3);
            }
        } else if (!enabled && ship != null) {
            ship.dissolve(serverLevel);
            anchor.shipId = null;
            anchor.failedAssemblies = 0;
            anchor.setChanged();
        } else if (ship == null && anchor.shipId != null) {
            anchor.shipId = null;
            anchor.setChanged();
        }

        if (anchor.failedAssemblies > 0 && !enabled) {
            anchor.failedAssemblies = 0;
        }
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
            tag.putInt("FailedAssemblies", failedAssemblies);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        shipId = tag.hasUUID("Ship") ? tag.getUUID("Ship") : null;
        failedAssemblies = tag.getInt("FailedAssemblies");
    }
}
