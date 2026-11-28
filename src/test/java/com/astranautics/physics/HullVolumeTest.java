package com.astranautics.physics;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Enclosed air is what makes a hull float: a hollow boat displaces the air it seals in,
 * a solid block of the same wood does not.
 */
class HullVolumeTest {
    private static Set<BlockPos> shell(int radius) {
        Set<BlockPos> voxels = new HashSet<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean edge = Math.abs(x) == radius || Math.abs(y) == radius || Math.abs(z) == radius;
                    if (edge) {
                        voxels.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return voxels;
    }

    @Test
    void hollowShellEnclosesItsInterior() {
        // radius 1 => 3x3x3 shell with exactly one air cell inside
        assertEquals(1, HullVolume.enclosedCells(shell(1)));
        // radius 2 => 5x5x5 shell with 3x3x3 = 27 air cells inside
        assertEquals(27, HullVolume.enclosedCells(shell(2)));
    }

    @Test
    void solidBlockEnclosesNothing() {
        Set<BlockPos> voxels = new HashSet<>();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    voxels.add(new BlockPos(x, y, z));
                }
            }
        }
        assertEquals(0, HullVolume.enclosedCells(voxels));
    }

    @Test
    void holeInTheShellLetsTheAirOut() {
        Set<BlockPos> shell = shell(2);
        shell.remove(new BlockPos(2, 0, 0));
        assertEquals(0, HullVolume.enclosedCells(shell));
    }

    @Test
    void emptyHullIsZero() {
        assertTrue(HullVolume.enclosedCells(Set.of()) == 0);
    }
}
