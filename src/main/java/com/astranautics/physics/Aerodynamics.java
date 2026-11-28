package com.astranautics.physics;

import com.astranautics.config.AstraConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Forces that come from the medium a hull sits in: balloon lift, propeller thrust, water
 * buoyancy and drag.
 *
 * <p>Everything is applied as an <em>impulse for one tick</em> at the point where it acts,
 * not as a force added to the solver's accumulator. Two reasons: the solver substeps (so a
 * per-tick force would only land in the first substep), and applying at a point gives the
 * torque for free - balloons on one end of a hull tilt it, an off-centre propeller spins it,
 * which is exactly what makes an airship feel like a vehicle instead of an elevator.</p>
 */
public final class Aerodynamics {
    private static final double TICK_SECONDS = 0.05D;
    /** Impulses below this are noise. */
    private static final double MIN_IMPULSE = 1.0E-5D;
    /** Sample points for the cheap "is there water near this hull at all" test. */
    private static final int WATER_SAMPLES = 4;

    private Aerodynamics() {
    }

    /** True when the hull carries anything that could move it by itself. */
    public static boolean isSelfDriven(ShipStructure structure) {
        return structure != null
                && (structure.balloonCount() > 0 || !structure.thrusters().isEmpty());
    }

    /** True when the balloons hold up more than the hull weighs, i.e. it wants to rise. */
    public static boolean liftsItself(RigidBody body, ShipStructure structure) {
        return structure != null && structure.balloonCount() > 0
                && structure.balloonCount() * AstraConfig.BALLOON_LIFT.get() > body.getMass() * 1.02D;
    }

    /**
     * Applies one tick of aerodynamic impulses to {@code body}.
     *
     * @param level         level the hull is in (for the water tests)
     * @param body          the rigid body to push
     * @param structure     voxel content, which carries the lift/thrust/volume cache
     * @param params        gravity and other solver parameters
     */
    public static void apply(Level level, RigidBody body, ShipStructure structure, PhysicsParams params) {
        if (!AstraConfig.ENABLE_AERODYNAMICS.get() || structure == null || body.isKinematic()) {
            return;
        }
        double mass = body.getMass();
        if (mass <= 1.0E-4D) {
            return;
        }

        Matrix3d rotation = body.getRotationMatrix();
        Vector3d world = new Vector3d();
        Vector3d impulse = new Vector3d();

        applyBalloonLift(body, structure, params, rotation, world, impulse);
        applyThrust(body, structure, rotation, world, impulse);

        WaterContact water = measureWater(level, body, structure, rotation, world);
        applyWater(body, structure, params, water, mass);
        applyAirDrag(body, structure, mass);
    }

    private static void applyBalloonLift(RigidBody body, ShipStructure structure, PhysicsParams params,
                                         Matrix3d rotation, Vector3d world, Vector3d impulse) {
        if (structure.balloonCount() <= 0) {
            return;
        }
        double lift = AstraConfig.BALLOON_LIFT.get() * structure.balloonCount()
                * params.gravity * TICK_SECONDS;
        if (lift <= MIN_IMPULSE) {
            return;
        }
        rotation.transform(structure.balloonCentroid(), world).add(body.position);
        body.applyImpulseAt(impulse.set(0.0D, lift, 0.0D), world);
    }

    private static void applyThrust(RigidBody body, ShipStructure structure,
                                    Matrix3d rotation, Vector3d world, Vector3d impulse) {
        if (structure.thrusters().isEmpty()) {
            return;
        }
        double perBlock = AstraConfig.PROPELLER_THRUST.get() * TICK_SECONDS;
        if (perBlock <= MIN_IMPULSE) {
            return;
        }
        for (ShipStructure.ThrustSource thruster : structure.thrusters()) {
            rotation.transform(thruster.localCenter, world).add(body.position);
            rotation.transform(thruster.localDirection, impulse).mul(perBlock);
            body.applyImpulseAt(impulse, world);
        }
    }

    private static void applyWater(RigidBody body, ShipStructure structure, PhysicsParams params,
                                   WaterContact water, double mass) {
        if (water.volume <= 0.0D) {
            return;
        }
        // Displacement: the hull voxels in the water, plus the sealed air they enclose scaled
        // by how much of the hull is under. That is what lets a hollow wooden boat float while
        // the same wood as a solid block sinks.
        double displaced = water.volume + structure.enclosedVolume() * water.fraction;
        double lift = displaced * AstraConfig.WATER_DENSITY.get() * params.gravity * TICK_SECONDS;
        if (lift > MIN_IMPULSE) {
            body.applyImpulseAt(new Vector3d(0.0D, lift, 0.0D), water.centroid);
        }

        double damping = Math.min(0.5D, AstraConfig.WATER_DRAG.get() * water.fraction * TICK_SECONDS);
        if (damping > 0.0D) {
            body.linearVelocity.mul(1.0D - damping);
            body.angularVelocity.mul(1.0D - Math.min(0.5D, damping * 1.5D));
        }
    }

    private static void applyAirDrag(RigidBody body, ShipStructure structure, double mass) {
        double speed = body.linearVelocity.length();
        if (speed <= 1.0E-4D) {
            return;
        }
        AABB bounds = structure.getLocalBounds();
        Vector3d localDirection = new Vector3d(body.linearVelocity).div(speed)
                .rotate(new Quaterniond(body.orientation).conjugate());
        double area = crossSection(bounds, localDirection);
        double coefficient = AstraConfig.AIR_DRAG.get() * area * speed * speed * TICK_SECONDS;
        if (coefficient <= 0.0D) {
            return;
        }
        // Quadratic drag: F = -k * A * |v| * v, applied as a velocity change so it works out
        // the same whatever the substep count is. Capped so a fast ship cannot be stopped
        // dead in one tick.
        double factor = Math.min(0.8D, coefficient / mass);
        body.linearVelocity.mul(1.0D - factor);
        body.angularVelocity.mul(1.0D - factor * 0.5D);
    }

    /** Projected area of the hull's box onto the plane perpendicular to its motion. */
    static double crossSection(AABB bounds, Vector3d localDirection) {
        double hx = (bounds.maxX - bounds.minX) * 0.5D;
        double hy = (bounds.maxY - bounds.minY) * 0.5D;
        double hz = (bounds.maxZ - bounds.minZ) * 0.5D;
        return Math.abs(localDirection.x) * (2.0D * hy * 2.0D * hz)
                + Math.abs(localDirection.y) * (2.0D * hx * 2.0D * hz)
                + Math.abs(localDirection.z) * (2.0D * hx * 2.0D * hy);
    }

    /** Which part of the hull is under water right now. */
    private static WaterContact measureWater(Level level, RigidBody body, ShipStructure structure,
                                             Matrix3d rotation, Vector3d world) {
        WaterContact contact = new WaterContact();
        if (level == null || structure.surfaceVoxels().isEmpty()) {
            return contact;
        }
        if (!waterNearby(level, body, structure)) {
            return contact;
        }

        int total = 0;
        int submerged = 0;
        double sumX = 0.0D;
        double sumY = 0.0D;
        double sumZ = 0.0D;
        for (BlockPos local : structure.surfaceVoxels()) {
            structure.localCenterRelativeToCom(local, world);
            rotation.transform(world).add(body.position);
            total++;
            FluidState fluid = level.getFluidState(BlockPos.containing(world.x, world.y, world.z));
            if (fluid.isEmpty() || !fluid.is(FluidTags.WATER)) {
                continue;
            }
            submerged++;
            contact.volume += fluid.isSource() ? 1.0D : 0.6D;
            sumX += world.x;
            sumY += world.y;
            sumZ += world.z;
        }
        if (submerged == 0) {
            return contact;
        }
        contact.fraction = total == 0 ? 0.0D : (double) submerged / (double) total;
        contact.centroid.set(sumX / submerged, sumY / submerged, sumZ / submerged);
        return contact;
    }

    /**
     * Cheap gate before walking every surface voxel: if neither the hull's centre nor its
     * lowest corners are in water, treat the hull as dry.
     */
    private static boolean waterNearby(Level level, RigidBody body, ShipStructure structure) {
        AABB bounds = structure.getLocalBounds();
        double[] xs = {bounds.minX, bounds.maxX, (bounds.minX + bounds.maxX) * 0.5D};
        double[] zs = {bounds.minZ, bounds.maxZ, (bounds.minZ + bounds.maxZ) * 0.5D};
        Vector3d probe = new Vector3d();
        Matrix3d rotation = body.getRotationMatrix();

        for (int i = 0; i < WATER_SAMPLES; i++) {
            double x = i == 0 ? (bounds.minX + bounds.maxX) * 0.5D : xs[i % xs.length];
            double z = i == 0 ? (bounds.minZ + bounds.maxZ) * 0.5D : zs[i % zs.length];
            double y = i == 0 ? (bounds.minY + bounds.maxY) * 0.5D : bounds.minY;
            probe.set(x, y, z);
            rotation.transform(probe).add(body.position);
            FluidState fluid = level.getFluidState(BlockPos.containing(probe.x, probe.y, probe.z));
            if (!fluid.isEmpty() && fluid.is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    /** How much of the hull is in water, and where that part is. */
    private static final class WaterContact {
        private double volume;
        private double fraction;
        private final Vector3d centroid = new Vector3d();
    }
}
