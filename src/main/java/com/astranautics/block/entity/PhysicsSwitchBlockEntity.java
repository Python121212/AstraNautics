package com.astranautics.block.entity;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicsSwitchBlock;
import com.astranautics.config.AstraConfig;
import com.astranautics.entity.PhysicsShip;
import com.astranautics.physics.ShipWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Owns the physics state of the block structure around a {@link PhysicsSwitchBlock}.
 *
 * <p>The switch is the only control block of the mod: right-clicking it detaches the
 * connected structure into a {@link PhysicsShip}, right-clicking it again puts the
 * blocks back.</p>
 *
 * <ul>
 *     <li>{@code enabled} and no ship &rarr; assemble the connected structure</li>
 *     <li>{@code !enabled} and a ship &rarr; dissolve it back into the world</li>
 * </ul>
 *
 * <p>A failed assembly keeps the switch enabled and reports the reason instead of
 * flipping the state back off (which used to make the block flash on and off).</p>
 */
public class PhysicsSwitchBlockEntity extends BlockEntity {
    private static final int CHECK_INTERVAL = 4;
    private static final int RETRY_TICKS = 60;
    private static final long FAILURE_LOG_INTERVAL = 100L;

    @Nullable
    private UUID shipId;
    /**
     * True when this switch merely adopts a hull that someone else assembled. Such a switch
     * mirrors that hull: when the hull goes away it turns itself off instead of assembling
     * the restored blocks all over again.
     */
    private boolean linked;
    private int retryCooldown;
    private long lastFailureLog = Long.MIN_VALUE;
    private int lastBlockCount;
    private ShipAssembler.Reason lastReason = ShipAssembler.Reason.NONE;
    private int lastReasonArg;
    private String lastDetail = "";

    public PhysicsSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHYSICS_SWITCH.get(), pos, state);
    }

    @Nullable
    public UUID getShipId() {
        return shipId;
    }

    public int getLastBlockCount() {
        return lastBlockCount;
    }

    public ShipAssembler.Reason getLastReason() {
        return this.lastReason;
    }

    /**
     * Links this switch to a hull that is already physical, so it becomes that hull's
     * control instead of starting a new assembly.
     */
    public void linkShip(UUID id) {
        this.shipId = id;
        this.linked = true;
        this.retryCooldown = 0;
        this.lastReason = ShipAssembler.Reason.OK;
        this.lastDetail = "linked to an existing hull";
        this.setChanged();
    }

    /** Chat-ready form of the last outcome. */
    public Component lastMessage() {
        return new ShipAssembler.Result(null, this.lastBlockCount, this.lastReason,
                this.lastReasonArg, this.lastDetail).message();
    }

    // ------------------------------------------------------------------ control

    /** Toggles the switch, performs the transition right away and reports to the player. */
    public static void toggle(Level level, BlockPos pos, @Nullable Player player) {
        if (!(level.getBlockState(pos).getBlock() instanceof PhysicsSwitchBlock)) {
            return;
        }
        boolean newState = !level.getBlockState(pos).getValue(PhysicsSwitchBlock.ENABLED);
        apply(level, pos, newState, player);
    }

    /**
     * Applies the requested state and immediately performs the transition, so the
     * player gets feedback in the same tick.
     *
     * @return the number of blocks that changed hands (assembled or restored)
     */
    public static int apply(Level level, BlockPos pos, boolean enabled, @Nullable Player player) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PhysicsSwitchBlock)) {
            return 0;
        }
        if (state.getValue(PhysicsSwitchBlock.ENABLED) != enabled) {
            level.setBlock(pos, state.setValue(PhysicsSwitchBlock.ENABLED, enabled), 3);
        }
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        if (!(serverLevel.getBlockEntity(pos) instanceof PhysicsSwitchBlockEntity switchEntity)) {
            return 0;
        }

        if (enabled) {
            // A switch placed next to an already physical hull is the "switch it off"
            // control: turn it and the hull goes back to blocks. Only an explicit
            // right-click does this (never the automatic ticker).
            PhysicsShip nearby = player == null ? null : findNearbyShip(serverLevel, pos);
            if (nearby != null) {
                int blocks = nearby.getStructure() == null ? 0 : nearby.getStructure().voxels().size();
                nearby.dissolve(serverLevel);

                BlockState current = serverLevel.getBlockState(pos);
                if (current.getBlock() instanceof PhysicsSwitchBlock) {
                    serverLevel.setBlock(pos, current.setValue(PhysicsSwitchBlock.ENABLED, false), 3);
                }
                if (player != null) {
                    player.displayClientMessage(Component.translatable(
                            "message.astranautics.ship.switched_off", blocks)
                            .withStyle(ChatFormatting.GRAY), false);
                }
                return blocks;
            }

            ShipAssembler.Result result = switchEntity.assembleNow(serverLevel);
            if (player != null) {
                player.displayClientMessage(result.message(), false);
            }
            return result.blocks();
        }

        int restored = switchEntity.dissolveNow(serverLevel);
        if (player != null) {
            player.displayClientMessage(restored > 0
                    ? Component.translatable("message.astranautics.physics_switch.dissolved",
                            restored).withStyle(ChatFormatting.GRAY)
                    : Component.translatable("message.astranautics.physics_switch.no_ship")
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return restored;
    }

    /** Closest physical hull whose bounding box is within reach of this switch. */
    @Nullable
    public static PhysicsShip findNearbyShip(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        double maxDistance = 8.0D;

        PhysicsShip best = null;
        double bestDistance = maxDistance * maxDistance;
        for (PhysicsShip ship : ShipWorld.ships(level)) {
            if (!ship.isAlive()) {
                continue;
            }
            double distance = distanceSqToBox(ship.getBoundingBox(), x, y, z);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = ship;
            }
        }
        return best;
    }

    /** True when the hull's bounding box is within {@code distance} blocks of the position. */
    public static boolean isShipWithin(PhysicsShip ship, BlockPos pos, double distance) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        return distanceSqToBox(ship.getBoundingBox(), x, y, z) <= distance * distance;
    }

    private static double distanceSqToBox(AABB box, double x, double y, double z) {
        double dx = Math.max(0.0D, Math.max(box.minX - x, x - box.maxX));
        double dy = Math.max(0.0D, Math.max(box.minY - y, y - box.maxY));
        double dz = Math.max(0.0D, Math.max(box.minZ - z, z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }

    /** Assembles right now (or reports why it cannot). Never flips the state off. */
    public ShipAssembler.Result assembleNow(ServerLevel level) {
        PhysicsShip existing = resolveShip(level);
        if (existing != null) {
            return new ShipAssembler.Result(existing, this.lastBlockCount,
                    ShipAssembler.Reason.OK, this.lastBlockCount, "already assembled");
        }

        ShipAssembler.Result result = ShipAssembler.assemble(level, this.worldPosition,
                AstraConfig.ASSEMBLY_RADIUS.get(), AstraConfig.MAX_BLOCKS.get());
        this.lastBlockCount = result.blocks();
        this.lastReason = result.reason();
        this.lastReasonArg = result.arg();
        this.lastDetail = result.detail();

        if (result.ok() && result.ship() != null) {
            this.shipId = result.ship().getUUID();
            this.linked = false;
            this.retryCooldown = 0;
            this.setChanged();
            AstraNautics.LOGGER.info("[astranautics] assembled {} blocks into a physics ship at {}",
                    result.blocks(), this.worldPosition);
        } else {
            // A structural exception is not going to fix itself: back off further.
            this.retryCooldown = result.reason() == ShipAssembler.Reason.EXCEPTION
                    ? RETRY_TICKS * 4 : RETRY_TICKS;
            this.logFailure(level, result.detail());
        }
        return result;
    }

    /** Dissolves the linked ship and returns how many blocks were put back. */
    public int dissolveNow(ServerLevel level) {
        PhysicsShip ship = resolveShip(level);
        if (ship == null) {
            if (this.shipId != null) {
                this.shipId = null;
                this.setChanged();
            }
            return 0;
        }

        int blocks = ship.getStructure() == null ? 0 : ship.getStructure().voxels().size();
        try {
            ship.dissolve(level);
        } catch (Exception exception) {
            AstraNautics.LOGGER.error("[astranautics] restoring the ship at {} failed",
                    this.worldPosition, exception);
            ship.discard();
        }
        this.shipId = null;
        this.retryCooldown = 0;
        this.lastReason = ShipAssembler.Reason.NONE;
        this.lastDetail = "dissolved";
        this.setChanged();
        AstraNautics.LOGGER.info("[astranautics] dissolved the physics ship at {} ({} blocks)",
                this.worldPosition, blocks);
        return blocks;
    }

    /** Called when the switch is broken, so the blocks are not lost. */
    public void dissolveOnRemoval(ServerLevel level) {
        if (resolveShip(level) != null) {
            dissolveNow(level);
        }
    }

    /** Chat report: state, ship, and how many blocks the structure would contain. */
    public void sendStatus(Player player) {
        boolean enabled = this.getBlockState().getValue(PhysicsSwitchBlock.ENABLED);
        String shipText = this.shipId == null ? "-" : this.shipId.toString().substring(0, 8);
        String previewText = "-";
        if (enabled && this.shipId == null && this.level instanceof ServerLevel serverLevel) {
            previewText = String.valueOf(ShipAssembler.preview(serverLevel, this.worldPosition,
                    AstraConfig.ASSEMBLY_RADIUS.get(), AstraConfig.MAX_BLOCKS.get()));
        }

        player.sendSystemMessage(Component.translatable("message.astranautics.physics_switch.status",
                enabled ? "ON" : "OFF", shipText, previewText, this.lastMessage())
                .withStyle(ChatFormatting.GOLD));
    }

    // --------------------------------------------------------------------- tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, PhysicsSwitchBlockEntity switchEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.getGameTime() % CHECK_INTERVAL != 0L) {
            return;
        }

        try {
            tickSwitch(serverLevel, state, switchEntity);
        } catch (Exception exception) {
            AstraNautics.LOGGER.error("[astranautics] switch tick at {} failed", pos, exception);
            switchEntity.retryCooldown = RETRY_TICKS * 4;
        }
    }

    private static void tickSwitch(ServerLevel serverLevel, BlockState state,
                                   PhysicsSwitchBlockEntity switchEntity) {
        boolean enabled = state.getValue(PhysicsSwitchBlock.ENABLED);
        PhysicsShip ship = switchEntity.resolveShip(serverLevel);

        if (enabled && ship == null) {
            if (switchEntity.linked) {
                // The hull this switch was mirroring is gone: go back to OFF instead of
                // assembling the blocks it just put down.
                switchEntity.linked = false;
                switchEntity.shipId = null;
                switchEntity.setChanged();
                serverLevel.setBlock(switchEntity.getBlockPos(),
                        state.setValue(PhysicsSwitchBlock.ENABLED, false), 3);
                return;
            }
            if (switchEntity.retryCooldown > 0) {
                switchEntity.retryCooldown -= CHECK_INTERVAL;
                return;
            }
            switchEntity.assembleNow(serverLevel);
            return;
        }

        if (!enabled && ship != null) {
            switchEntity.dissolveNow(serverLevel);
            return;
        }

        if (ship == null && switchEntity.shipId != null) {
            switchEntity.shipId = null;
            switchEntity.setChanged();
        }
    }

    private void logFailure(ServerLevel level, String reason) {
        long now = level.getGameTime();
        if (now - this.lastFailureLog < FAILURE_LOG_INTERVAL) {
            return;
        }
        this.lastFailureLog = now;
        AstraNautics.LOGGER.warn("[astranautics] assembly at {} failed: {} (radius {}, max {} blocks)",
                this.worldPosition, reason, AstraConfig.ASSEMBLY_RADIUS.get(), AstraConfig.MAX_BLOCKS.get());
    }

    @Nullable
    private PhysicsShip resolveShip(ServerLevel level) {
        if (this.shipId == null) {
            return null;
        }
        Entity entity = level.getEntity(this.shipId);
        if (entity instanceof PhysicsShip ship && ship.isAlive()) {
            return ship;
        }
        this.shipId = null;
        return null;
    }

    // ---------------------------------------------------------------------- nbt

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.shipId != null) {
            tag.putUUID("Ship", this.shipId);
        }
        tag.putInt("LastBlocks", this.lastBlockCount);
        tag.putString("LastReason", this.lastReason.name());
        tag.putInt("LastReasonArg", this.lastReasonArg);
        tag.putString("LastDetail", this.lastDetail);
        tag.putBoolean("Linked", this.linked);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.shipId = tag.hasUUID("Ship") ? tag.getUUID("Ship") : null;
        this.linked = tag.getBoolean("Linked");
        this.lastBlockCount = tag.getInt("LastBlocks");
        if (tag.contains("LastReason")) {
            try {
                this.lastReason = ShipAssembler.Reason.valueOf(tag.getString("LastReason"));
            } catch (IllegalArgumentException unknown) {
                this.lastReason = ShipAssembler.Reason.NONE;
            }
        }
        this.lastReasonArg = tag.getInt("LastReasonArg");
        if (tag.contains("LastDetail")) {
            this.lastDetail = tag.getString("LastDetail");
        }
    }
}
