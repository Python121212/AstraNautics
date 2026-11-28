package com.astranautics.physics;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redstone on a hull: the same rules as vanilla, evaluated on the ship's own grid because its
 * blocks are no longer in the world.
 */
class HullRedstoneTest {
    private final Map<BlockPos, HullRedstone.Kind> kinds = new HashMap<>();

    private void put(int x, int y, int z, HullRedstone.Kind kind) {
        this.kinds.put(new BlockPos(x, y, z), kind);
    }

    @Test
    void aRedstoneBlockPowersItsNeighbours() {
        BlockPos source = new BlockPos(0, 0, 0);
        BlockPos rotator = new BlockPos(0, 1, 0);
        put(0, 0, 0, HullRedstone.Kind.SOURCE);

        Map<BlockPos, Integer> power = HullRedstone.strengths(this.kinds);

        assertTrue(HullRedstone.powered(power, rotator));
        assertEquals(Integer.valueOf(15), power.get(source));
    }

    @Test
    void nothingTwoBlocksAwayIsNotPowered() {
        put(0, 0, 0, HullRedstone.Kind.SOURCE);
        Map<BlockPos, Integer> power = HullRedstone.strengths(this.kinds);
        assertFalse(HullRedstone.powered(power, new BlockPos(0, 2, 0)));
    }

    @Test
    void aWireCarriesTheSignalAndLosesOnePerBlock() {
        put(0, 0, 0, HullRedstone.Kind.SOURCE);
        for (int i = 1; i <= 5; i++) {
            put(i, 0, 0, HullRedstone.Kind.WIRE);
        }
        Map<BlockPos, Integer> power = HullRedstone.strengths(this.kinds);

        assertEquals(Integer.valueOf(15), power.get(new BlockPos(1, 0, 0)));
        assertEquals(Integer.valueOf(14), power.get(new BlockPos(2, 0, 0)));
        assertEquals(Integer.valueOf(11), power.get(new BlockPos(5, 0, 0)));
        // A rotator sitting on the far end of the wire is powered.
        assertTrue(HullRedstone.powered(power, new BlockPos(5, 1, 0)));
    }

    @Test
    void aWireSixteenBlocksFromAnySourceIsDead() {
        put(0, 0, 0, HullRedstone.Kind.SOURCE);
        for (int i = 1; i <= 16; i++) {
            put(i, 0, 0, HullRedstone.Kind.WIRE);
        }
        Map<BlockPos, Integer> power = HullRedstone.strengths(this.kinds);
        // The source feeds the first dust at 15, so fifteen blocks later there is nothing left.
        assertEquals(Integer.valueOf(1), power.get(new BlockPos(15, 0, 0)));
        assertFalse(HullRedstone.powered(power, new BlockPos(17, 0, 0)));
    }

    @Test
    void anUnpoweredLeverIsNotASource() {
        put(0, 0, 0, HullRedstone.Kind.OTHER);
        Map<BlockPos, Integer> power = HullRedstone.strengths(this.kinds);
        assertTrue(power.isEmpty());
        assertFalse(HullRedstone.powered(power, new BlockPos(0, 1, 0)));
    }

    @Test
    void twoSourcesFeedFromBothEnds() {
        put(0, 0, 0, HullRedstone.Kind.SOURCE);
        put(12, 0, 0, HullRedstone.Kind.SOURCE);
        for (int i = 1; i <= 11; i++) {
            put(i, 0, 0, HullRedstone.Kind.WIRE);
        }
        Map<BlockPos, Integer> power = HullRedstone.strengths(this.kinds);
        // The middle is reached with the stronger of the two feeds.
        assertTrue(power.get(new BlockPos(6, 0, 0)) >= 9);
        assertTrue(HullRedstone.powered(power, new BlockPos(6, 1, 0)));
    }
}
