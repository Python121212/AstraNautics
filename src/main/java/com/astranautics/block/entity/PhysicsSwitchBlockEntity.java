package com.astranautics.block.entity;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicsSwitchBlock;
import com.astranautics.config.AstraConfig;
import com.astranautics.entity.PhysicsShip;
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
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.shipId = tag.hasUUID("Ship") ? tag.getUUID("Ship") : null;
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
