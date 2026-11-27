package com.astranautics.physics;

import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memoises world block queries for the duration of one simulation step.
 *
 * <p>A large ship touches the same world cells with dozens of voxels; without this
 * cache every voxel re-reads the block state and re-builds its collision shape.</p>
 */
public final class CachingCollisionSource implements BlockCollisionSource {
    private final BlockCollisionSource delegate;
    private final Map<Long, List<AABB>> cache = new HashMap<>(256);

    public CachingCollisionSource(BlockCollisionSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<AABB> collisionBoxesAt(net.minecraft.core.BlockPos pos) {
        long key = pos.asLong();
        List<AABB> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<AABB> boxes = delegate.collisionBoxesAt(pos);
        // Intern empty results to a shared immutable list.
        List<AABB> stored = boxes.isEmpty() ? List.of() : new ArrayList<>(boxes);
        cache.put(key, stored);
        return stored;
    }
}
