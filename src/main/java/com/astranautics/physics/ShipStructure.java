package com.astranautics.physics;

import com.astranautics.block.BalloonBlock;
import com.astranautics.block.PropellerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The voxel content of a physics ship, expressed in the ship's local frame.
 *
 * <p>Local coordinates are integer block positions anchored at the minimum corner
 * of the assembled region; the centre of mass is derived from per-block masses.</p>
 */
public final class ShipStructure implements VoxelBody {
    private final Map<BlockPos, BlockState> voxels;
    private final Set<BlockPos> surfaceVoxels;
    /** Per-voxel local collision box, so slabs / stairs / fences collide like they should. */
    private final Map<BlockPos, AABB> collisionBoxes;

    private final Vector3d centerOfMass = new Vector3d();
    private final double mass;
    private final Matrix3d inertiaLocal = new Matrix3d();
    private final AABB localBounds;
    /** Aerodynamics cache: recomputed only when the hull changes, not every tick. */
    private final int balloonCount;
    private final Vector3d balloonCentroid = new Vector3d();
    private final List<ThrustSource> thrusters;
    private final int enclosedVolume;

    /** A powered propeller: where it sits (local, relative to the centre of mass) and which
     * way it pushes (also local, so the world direction follows the hull's rotation). */
    public static final class ThrustSource {
        public final Vector3d localCenter;
        public final Vector3d localDirection;

        public ThrustSource(Vector3d localCenter, Vector3d localDirection) {
            this.localCenter = localCenter;
            this.localDirection = localDirection;
        }
    }

    private ShipStructure(Map<BlockPos, BlockState> voxels, double mass,
                          Vector3d centerOfMass, Matrix3d inertiaLocal, AABB localBounds,
                          Set<BlockPos> surfaceVoxels, Map<BlockPos, AABB> collisionBoxes,
                          int balloonCount, Vector3d balloonCentroid, List<ThrustSource> thrusters,
                          int enclosedVolume) {
        this.voxels = Collections.unmodifiableMap(voxels);
        this.collisionBoxes = Collections.unmodifiableMap(collisionBoxes);
        this.mass = mass;
        this.centerOfMass.set(centerOfMass);
        this.inertiaLocal.set(inertiaLocal);
        this.localBounds = localBounds;
        this.surfaceVoxels = Collections.unmodifiableSet(surfaceVoxels);
        this.balloonCount = balloonCount;
        this.balloonCentroid.set(balloonCentroid);
        this.thrusters = Collections.unmodifiableList(thrusters);
        this.enclosedVolume = enclosedVolume;
    }

    public static ShipStructure of(Map<BlockPos, BlockState> voxels) {
        if (voxels.isEmpty()) {
            throw new IllegalArgumentException("cannot build an empty ship structure");
        }

        double totalMass = 0.0D;
        Vector3d weighted = new Vector3d();
        for (Map.Entry<BlockPos, BlockState> entry : voxels.entrySet()) {
            double blockMass = BlockPhysics.massOf(entry.getValue());
            totalMass += blockMass;
            BlockPos pos = entry.getKey();
            weighted.add((pos.getX() + 0.5D) * blockMass,
                    (pos.getY() + 0.5D) * blockMass,
                    (pos.getZ() + 0.5D) * blockMass);
        }
        Vector3d com = new Vector3d(weighted).div(totalMass);

        Matrix3d inertia = new Matrix3d();
        for (Map.Entry<BlockPos, BlockState> entry : voxels.entrySet()) {
            double blockMass = BlockPhysics.massOf(entry.getValue());
            BlockPos pos = entry.getKey();
            double rx = (pos.getX() + 0.5D) - com.x;
            double ry = (pos.getY() + 0.5D) - com.y;
            double rz = (pos.getZ() + 0.5D) - com.z;

            // Cuboid inertia of a unit cube: m/6 on the diagonal.
            double self = blockMass / 6.0D;
            // Parallel axis theorem: m * (r^2 E - r r^T)
            double r2 = rx * rx + ry * ry + rz * rz;

            inertia.m00 += self + blockMass * (r2 - rx * rx);
            inertia.m11 += self + blockMass * (r2 - ry * ry);
            inertia.m22 += self + blockMass * (r2 - rz * rz);
            inertia.m01 -= blockMass * rx * ry;
            inertia.m10 -= blockMass * rx * ry;
            inertia.m02 -= blockMass * rx * rz;
            inertia.m20 -= blockMass * rx * rz;
            inertia.m12 -= blockMass * ry * rz;
            inertia.m21 -= blockMass * ry * rz;
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : voxels.keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        AABB bounds = new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);

        Set<BlockPos> surface = new LinkedHashSet<>();
        for (BlockPos pos : voxels.keySet()) {
            if (isSurface(voxels, pos)) {
                surface.add(pos);
            }
        }

        Map<BlockPos, AABB> collisionBoxes = new LinkedHashMap<>();
        int balloonCount = 0;
        Vector3d balloonSum = new Vector3d();
        List<ThrustSource> thrusters = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : voxels.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            collisionBoxes.put(pos, VoxelShapes.collisionBox(state));

            double lx = pos.getX() + 0.5D - com.x;
            double ly = pos.getY() + 0.5D - com.y;
            double lz = pos.getZ() + 0.5D - com.z;
            if (state.getBlock() instanceof BalloonBlock) {
                balloonCount++;
                balloonSum.add(lx, ly, lz);
            } else if (state.getBlock() instanceof PropellerBlock
                    && state.getValue(PropellerBlock.POWERED)) {
                Direction facing = state.getValue(PropellerBlock.FACING);
                thrusters.add(new ThrustSource(new Vector3d(lx, ly, lz),
                        new Vector3d(facing.getStepX(), 0.0D, facing.getStepZ())));
            }
        }
        Vector3d balloonCentroid = balloonCount == 0 ? new Vector3d()
                : new Vector3d(balloonSum).div(balloonCount);

        return new ShipStructure(new LinkedHashMap<>(voxels), totalMass, com, inertia, bounds, surface,
                collisionBoxes, balloonCount, balloonCentroid, thrusters,
                HullVolume.enclosedCells(voxels.keySet()));
    }

    private static boolean isSurface(Map<BlockPos, BlockState> voxels, BlockPos pos) {
        return !voxels.containsKey(pos.above())
                || !voxels.containsKey(pos.below())
                || !voxels.containsKey(pos.north())
                || !voxels.containsKey(pos.south())
                || !voxels.containsKey(pos.east())
                || !voxels.containsKey(pos.west());
    }

    public Map<BlockPos, BlockState> voxels() {
        return voxels;
    }

    @Override
    public Set<BlockPos> surfaceVoxels() {
        return surfaceVoxels;
    }

    /**
     * Collision box of a voxel in the voxel's own coordinates, or {@link VoxelShapes#FULL}
     * for a voxel this structure does not know about.
     */
    @Override
    public AABB collisionBox(BlockPos local) {
        AABB box = this.collisionBoxes.get(local);
        return box == null ? VoxelShapes.FULL : box;
    }

    /** Number of balloon blocks in the hull. */
    public int balloonCount() {
        return this.balloonCount;
    }

    /** Local (centre of mass relative) centroid of the balloons, where their lift acts. */
    public Vector3d balloonCentroid() {
        return this.balloonCentroid;
    }

    /** Powered propellers only: an idle propeller costs nothing. */
    public List<ThrustSource> thrusters() {
        return this.thrusters;
    }

    /** Air sealed inside the hull, in blocks - the volume a floating hull displaces. */
    public int enclosedVolume() {
        return this.enclosedVolume;
    }

    public int voxelCount() {
        return this.voxels.size();
    }

    public double getMass() {
        return mass;
    }

    public Vector3d getCenterOfMass() {
        return centerOfMass;
    }

    public Matrix3d getInertiaLocal() {
        return inertiaLocal;
    }

    public AABB getLocalBounds() {
        return localBounds;
    }

    /** Local-space centre of a voxel, relative to the centre of mass. */
    @Override
    public Vector3d localCenterRelativeToCom(BlockPos local, Vector3d dest) {
        return dest.set(local.getX() + 0.5D - centerOfMass.x,
                local.getY() + 0.5D - centerOfMass.y,
                local.getZ() + 0.5D - centerOfMass.z);
    }
}
