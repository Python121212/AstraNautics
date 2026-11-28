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
 * torque for free - balloons on one end of a hull tilt it, an off-centre propeller spins it.</p>
 *
 * <p>The numbers follow Create: Aeronautics, which is the reference implementation this mod
 * is measured against:</p>
 * <ul>
 *     <li><b>air pressure</b> {@code p(y) = e^(-0.004 (y - 63))} - lift, thrust and drag all
 *     scale with it. A ship therefore rises until {@code lift * p(y) = weight} and settles at
 *     that altitude; above {@code y ~ 320} there is no lift left at all. That is a natural
 *     ceiling instead of a height limit.</li>
 *     <li><b>balloon lift</b> {@code 1.5} mass units per block at sea level (their
 *     {@code LiftStrength}), times pressure.</li>
 *     <li><b>propeller thrust</b> falls linearly to zero once the hull moves along the thrust
 *     direction as fast as the propeller's airflow - a hard top speed no amount of extra
 *     thrust can beat. Thrust also scales with pressure, so high altitude means weaker
 *     engines.</li>
 *     <li><b>envelope drag</b>: balloon blocks are bulky, so every one of them adds drag.
 *     That is why a huge balloon ship is slow.</li>
 *     <li><b>no torque from propellers</b> by default: Aeronautics exempts propellers from
 *     torque on purpose, otherwise every aircraft rolls or yaws from its own engines.</li>
 * </ul>
 */
public final class Aerodynamics {
    private static final double TICK_SECONDS = 0.05D;

    /** Height at which air pressure is defined as 1.0 (sea level in a vanilla world). */
    public static final double SEA_LEVEL = 63.0D;
    /** Pressure above this is clipped; below sea level the air gets thicker, but sanity. */
    private static final double MAX_PRESSURE = 4.0D;
    /** Impulses below this are noise. */
    private static final double MIN_IMPULSE = 1.0E-5D;
    /** Sample points for the cheap "is there water near this hull at all" test. */
    private static final int WATER_SAMPLES = 4;

    private Aerodynamics() {
    }

    // ------------------------------------------------------------------ formulas

    /**
     * Air pressure relative to sea level, the Create: Aeronautics curve
     * {@code e^(-falloff * (y - 63))}: 1.0 at y=63, about 0.46 at y=256, nearly nothing at y=320.
     */
    public static double airPressure(double y, double falloff) {
        double pressure = Math.exp(-falloff * (y - SEA_LEVEL));
        if (Double.isNaN(pressure)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(MAX_PRESSURE, pressure));
    }

    /** Live pressure for the current config. */
    public static double airPressure(double y) {
        return airPressure(y, AstraConfig.AIR_PRESSURE_FALLOFF.get());
    }

    /**
     * How much of a propeller's thrust survives the hull already moving: 1 at a standstill,
     * 0 at the propeller's airflow speed, nothing beyond it.
     *
     * @param forwardSpeed speed along the thrust direction (blocks/second)
     * @param airflow      speed at which this propeller stops pushing (blocks/second)
     */
    public static double thrustFactor(double forwardSpeed, double airflow) {
        if (airflow <= 1.0E-6D) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, 1.0D - forwardSpeed / airflow));
    }

    // ------------------------------------------------------------------ state

    /** True when the hull carries anything that could move it by itself. */
    public static boolean isSelfDriven(ShipStructure structure) {
        return structure != null
                && (structure.balloonCount() > 0 || !structure.thrusters().isEmpty());
    }

    /**
     * True when the balloons hold up more than the hull weighs <em>at its current altitude</em>,
     * i.e. it wants to rise.
     */
    public static boolean liftsItself(RigidBody body, ShipStructure structure) {
        if (structure == null || structure.balloonCount() <= 0) {
            return false;
        }
        double lift = structure.balloonCount() * AstraConfig.BALLOON_LIFT.get()
                * airPressure(body.position.y);
        return lift > body.getMass() * 1.02D;
    }

    // ------------------------------------------------------------------ per tick

    /**
     * Applies one tick of aerodynamic impulses to {@code body}.
     *
     * @param level     level the hull is in (for the water tests)
     * @param body      the rigid body to push
     * @param structure voxel content, which carries the lift/thrust/volume cache
     * @param params    gravity and other solver parameters
     */
    public static void apply(Level level, RigidBody body, ShipStructure structure, PhysicsParams params) {
        if (!AstraConfig.ENABLE_AERODYNAMICS.get() || structure == null || body.isKinematic()) {
            return;
        }
        double mass = body.getMass();
        if (mass <= 1.0E-4D) {
            return;
        }

        double pressure = airPressure(body.position.y);
        Matrix3d rotation = body.getRotationMatrix();
        Vector3d world = new Vector3d();
        Vector3d impulse = new Vector3d();

        applyBalloonLift(body, structure, params, pressure, rotation, world, impulse);
        applyThrust(body, structure, pressure, rotation, world, impulse);

        WaterContact water = measureWater(level, body, structure, rotation, world);
        applyWater(body, structure, params, water);

        applyAirDrag(body, structure, pressure, mass);
    }

    private static void applyBalloonLift(RigidBody body, ShipStructure structure, PhysicsParams params,
                                         double pressure, Matrix3d rotation, Vector3d world, Vector3d impulse) {
        if (structure.balloonCount() <= 0) {
            return;
        }
        double lift = AstraConfig.BALLOON_LIFT.get() * structure.balloonCount()
                * params.gravity * pressure * TICK_SECONDS;
        if (lift <= MIN_IMPULSE) {
            return;
        }
        rotation.transform(structure.balloonCentroid(), world).add(body.position);
        body.applyImpulseAt(impulse.set(0.0D, lift, 0.0D), world);
    }

    private static void applyThrust(RigidBody body, ShipStructure structure, double pressure,
                                    Matrix3d rotation, Vector3d world, Vector3d impulse) {
        if (structure.thrusters().isEmpty()) {
            return;
        }
        double perBlock = AstraConfig.PROPELLER_THRUST.get() * pressure * TICK_SECONDS;
        if (perBlock <= MIN_IMPULSE) {
            return;
        }
        double airflow = AstraConfig.PROPELLER_AIRFLOW.get();
        boolean torque = AstraConfig.PROPELLER_TORQUE.get();
        Vector3d direction = new Vector3d();

        for (ShipStructure.ThrustSource thruster : structure.thrusters()) {
            rotation.transform(thruster.localDirection, direction);
            double forward = body.linearVelocity.dot(direction);
            double factor = thrustFactor(forward, airflow);
            if (factor <= 0.0D) {
                continue;
            }
            // Aeronautics applies propeller thrust at the centre of mass so an aircraft does
            // not roll or yaw from its own engines; the torque option is there for players who
            // want an outboard motor that also turns the boat.
            if (torque) {
                rotation.transform(thruster.localCenter, world).add(body.position);
            } else {
                world.set(body.position);
            }
            body.applyImpulseAt(impulse.set(direction).mul(perBlock * factor), world);
        }
    }

    private static void applyWater(RigidBody body, ShipStructure structure, PhysicsParams params,
                                   WaterContact water) {
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

    private static void applyAirDrag(RigidBody body, ShipStructure structure, double pressure, double mass) {
        double speed = body.linearVelocity.length();
        if (speed <= 1.0E-4D) {
            return;
        }
        // Two terms: the hull's own cross-section (quadratic in speed) and the balloons, which
        // are bulky - Aeronautics gives every envelope block 0.33 * pressure of drag per m/s.
        AABB bounds = structure.getLocalBounds();
        Vector3d localDirection = new Vector3d(body.linearVelocity).div(speed)
                .rotate(new Quaterniond(body.orientation).conjugate());
        double area = crossSection(bounds, localDirection);
        double drag = AstraConfig.AIR_DRAG.get() * pressure * area * speed * speed
                + AstraConfig.BALLOON_DRAG.get() * pressure * structure.balloonCount() * speed;
        if (drag <= 0.0D) {
            return;
        }
        // Turned into a velocity change (so it does not depend on the substep count) and capped
        // so a fast ship cannot be stopped dead in one tick.
        double factor = Math.min(0.8D, drag * TICK_SECONDS / mass);
        body.linearVelocity.mul(1.0D - factor);
        body.angularVelocity.mul(1.0D - Math.min(0.5D, factor * 0.5D));
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
