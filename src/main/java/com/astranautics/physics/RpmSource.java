package com.astranautics.physics;

import net.minecraft.core.BlockPos;

/**
 * Supplies the current rotation speed of an engine voxel. Thrust scaling happens in
 * {@link Aerodynamics}, but the engines themselves live in the ship entity, so they are
 * reached through this small interface instead of a hard reference.
 */
@FunctionalInterface
public interface RpmSource {
    /** No engine anywhere: propellers only give their base thrust. */
    RpmSource NONE = cell -> 0.0D;

    /** Current RPM of the engine at a local hull cell, or 0 if there is no engine there. */
    double rpmAt(BlockPos localCell);
}
