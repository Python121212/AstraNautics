package com.astranautics.physics;

import com.astranautics.entity.PhysicsShip;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Progressive block removal from a hull, using vanilla mining timings.
 *
 * <p>Vanilla only sends a single attack packet per click when the crosshair is on an
 * entity, so the client streams {@code MineTick} messages while the attack button is
 * held. The server turns those into progress with vanilla's own per-tick figure
 * ({@code BlockState#getDestroyProgress}), which already accounts for hardness, the
 * correct tool, efficiency and the underwater penalty. Creative players break the
 * block on the first tick.</p>
 */
public final class VoxelMining {
    /** Ticks without a packet after which the progress is dropped (vanilla resets on release). */
    private static final int TIMEOUT = 10;
    private static final int SOUND_INTERVAL = 5;

    private static final Map<UUID, State> STATES = new HashMap<>();

    private VoxelMining() {
    }

    private static final class State {
        int shipId = -1;
        BlockPos cell = BlockPos.ZERO;
        double progress;
        long lastTick;
        long lastSound;

        /** Vanilla progress (0..1) this block loses in one tick. */
        double progressPerTick(ServerLevel level, PhysicsShip ship, BlockPos cell, ServerPlayer player) {
            ShipStructure structure = ship.getStructure();
            BlockState hit = structure == null ? null : structure.voxels().get(cell);
            if (hit == null) {
                return 0.0D;
            }
            float progress = hit.getDestroyProgress(player, level, ship.blockPosition());
            return progress <= 0.0F ? 0.0D : progress;
        }
    }

    /** Clears the mining state of every player. */
    public static void clear() {
        STATES.clear();
    }

    /** True if this player is currently mining this exact voxel of this hull. */
    public static boolean isMining(ServerPlayer player, PhysicsShip ship, BlockPos cell) {
        State state = STATES.get(player.getUUID());
        if (state == null || state.shipId != ship.getId() || !state.cell.equals(cell)) {
            return false;
        }
        return player.level().getGameTime() - state.lastTick <= TIMEOUT;
    }

    /** Server side: one tick of holding the attack button on {@code cell}. */
    public static void onMineTick(ServerPlayer player, int shipId, BlockPos cell) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(level.getEntity(shipId) instanceof PhysicsShip ship) || ship.isRemoved()) {
            STATES.remove(player.getUUID());
            return;
        }
        ShipStructure structure = ship.getStructure();
        if (structure == null || !structure.voxels().containsKey(cell)) {
            STATES.remove(player.getUUID());
            return;
        }

        // Creative breaks instantly, exactly like a block.
        if (player.getAbilities().instabuild) {
            ship.breakVoxelAt(level, cell);
            STATES.remove(player.getUUID());
            return;
        }

        long now = level.getGameTime();
        State state = STATES.get(player.getUUID());
        if (state == null || state.shipId != shipId || !state.cell.equals(cell)
                || now - state.lastTick > TIMEOUT) {
            state = new State();
            // The click that started this hold also punched the hull: take that back.
            ship.undoLastPunch(level, player);
            state.shipId = shipId;
            state.cell = cell;
            state.lastSound = now - SOUND_INTERVAL;
            STATES.put(player.getUUID(), state);
        }

        long elapsed = state.lastTick == 0L ? 1L : Math.max(1L, Math.min(4L, now - state.lastTick));
        state.lastTick = now;
        state.progress += elapsed * state.progressPerTick(level, ship, cell, player);

        if (state.progress >= 1.0D) {
            ship.breakVoxelAt(level, cell);
            STATES.remove(player.getUUID());
            return;
        }

        if (now - state.lastSound >= SOUND_INTERVAL) {
            state.lastSound = now;
            BlockState hitState = structure.voxels().get(cell);
            if (hitState != null) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        hitState.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.35F,
                        0.6F + level.random.nextFloat() * 0.2F);
            }
        }
    }
}
