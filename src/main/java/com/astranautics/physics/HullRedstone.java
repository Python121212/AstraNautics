package com.astranautics.physics;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Redstone that lives on a hull.
 *
 * <p>Once a structure is physical, its blocks are no longer in the world, so vanilla redstone
 * cannot see them: a redstone block bolted to a ship powers nothing. This computes the same
 * thing on the hull's own grid instead, from the same kinds of part the player would use in the
 * world:</p>
 * <ul>
 *     <li>a <b>source</b> (redstone block, lit torch, powered lever) emits 15</li>
 *     <li><b>wire</b> carries 15 and loses one per block, like vanilla dust</li>
 *     <li>anything that touches an emitting cell is powered, which is what the rotator asks</li>
 * </ul>
 *
 * <p>It is computed once per hull rebuild, so a ship full of redstone costs nothing per tick.</p>
 */
public final class HullRedstone {
    /** What a hull cell contributes to the on-hull redstone. */
    public enum Kind {
        /** Not part of the circuit. */
        OTHER,
        /** Emits 15 (redstone block, torch, powered lever). */
        SOURCE,
        /** Redstone dust: carries a signal with the usual loss. */
        WIRE
    }

    private static final int MAX_POWER = 15;

    private HullRedstone() {
    }

    /**
     * The power each cell emits, for the cells that emit at all.
     *
     * @param kinds what every hull cell is
     */
    public static Map<BlockPos, Integer> strengths(Map<BlockPos, Kind> kinds) {
        Map<BlockPos, Integer> power = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        for (Map.Entry<BlockPos, Kind> entry : kinds.entrySet()) {
            if (entry.getValue() == Kind.SOURCE) {
                power.put(entry.getKey(), MAX_POWER);
                queue.add(entry.getKey());
            }
        }

        // Breadth-first from the sources, so each wire is reached first by its strongest feed.
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            int strength = power.getOrDefault(pos, 0);
            // A source feeds the dust next to it at full strength (as a redstone block does in
            // vanilla); from there on every block costs one.
            int carried = kinds.get(pos) == Kind.SOURCE ? MAX_POWER : strength - 1;
            if (carried <= 0) {
                continue;
            }
            for (BlockPos next : neighbours(pos)) {
                if (kinds.get(next) != Kind.WIRE) {
                    continue;
                }
                if (carried > power.getOrDefault(next, 0)) {
                    power.put(next, carried);
                    queue.add(next);
                }
            }
        }
        return power;
    }

    /**
     * True when the cell itself, or one of its six neighbours, emits power - the vanilla rule
     * for "this block is powered".
     */
    public static boolean powered(Map<BlockPos, Integer> strengths, BlockPos cell) {
        if (strengths.getOrDefault(cell, 0) > 0) {
            return true;
        }
        for (BlockPos next : neighbours(cell)) {
            if (strengths.getOrDefault(next, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    /** The six cells around {@code pos}. */
    public static Iterable<BlockPos> neighbours(BlockPos pos) {
        return java.util.List.of(pos.offset(1, 0, 0), pos.offset(-1, 0, 0),
                pos.offset(0, 1, 0), pos.offset(0, -1, 0),
                pos.offset(0, 0, 1), pos.offset(0, 0, -1));
    }
}
