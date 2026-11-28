package com.astranautics.physics;

import net.minecraft.core.Direction;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * The angle maths behind the rotator blocks, kept separate so it can be tested without a
 * world.
 *
 * <p>Both modes measure an <em>absolute</em> angle of the hull against its unrotated pose
 * (orientation = identity), which is what makes "turn to 90 degrees" mean the same thing
 * every time instead of "turn 90 degrees from wherever you are":</p>
 * <ul>
 *     <li>yaw: heading of the hull's forward (-Z) direction, measured around world +Y</li>
 *     <li>pitch: tilt of the hull's up (+Y) direction, measured around world +X</li>
 * </ul>
 */
public final class Rotators {
    /** Which way a rotator turns the hull. */
    public enum Mode {
        /** Around the world Y axis: heading, 0..359 degrees. */
        YAW,
        /** Around the world X axis: tilt, 0..359 degrees (180 is upside down). */
        PITCH
    }

    private Rotators() {
    }

    /** The hull's heading in degrees, 0..360: 0 is unrotated, increasing towards +X. */
    public static double yawDegrees(Quaterniond orientation) {
        Vector3d forward = orientation.transform(new Vector3d(0.0D, 0.0D, -1.0D));
        return normalise(Math.toDegrees(Math.atan2(-forward.x, -forward.z)));
    }

    /** The hull's tilt in degrees, 0..360: 0 is upright, 180 is upside down. */
    public static double pitchDegrees(Quaterniond orientation) {
        Vector3d up = orientation.transform(new Vector3d(0.0D, 1.0D, 0.0D));
        return normalise(Math.toDegrees(Math.atan2(up.z, up.y)));
    }

    public static double angleDegrees(Mode mode, Quaterniond orientation) {
        return mode == Mode.YAW ? yawDegrees(orientation) : pitchDegrees(orientation);
    }

    /** Signed difference from current to target in (-180, 180]: the short way round. */
    public static double shortestDelta(double current, double target) {
        double delta = normalise(target - current);
        if (delta > 180.0D) {
            delta -= 360.0D;
        }
        return delta;
    }

    /**
     * Turns {@code orientation} towards {@code target} by at most {@code stepDegrees}.
     *
     * @return the remaining difference after the step, in degrees (0 when it arrived)
     */
    public static double stepTowards(Quaterniond orientation, Mode mode,
                                     double target, double stepDegrees) {
        double current = angleDegrees(mode, orientation);
        double delta = shortestDelta(current, target);
        if (Math.abs(delta) <= 1.0E-6D) {
            return 0.0D;
        }
        double applied = Math.copySign(Math.min(Math.abs(delta), Math.abs(stepDegrees)), delta);
        Direction.Axis axis = mode == Mode.YAW ? Direction.Axis.Y : Direction.Axis.X;
        rotateAboutWorldAxis(orientation, axis, applied);
        return delta - applied;
    }

    /** Rotates the hull's orientation about a world axis, in degrees. */
    public static void rotateAboutWorldAxis(Quaterniond orientation, Direction.Axis axis, double degrees) {
        Vector3d unit = switch (axis) {
            case X -> new Vector3d(1.0D, 0.0D, 0.0D);
            case Y -> new Vector3d(0.0D, 1.0D, 0.0D);
            case Z -> new Vector3d(0.0D, 0.0D, 1.0D);
        };
        Quaterniond rotation = new Quaterniond().fromAxisAngleRad(unit, Math.toRadians(degrees));
        // A turntable applies the rotation in world space: new = rotation * old.
        orientation.set(rotation.mul(orientation)).normalize();
    }

    /** Wraps an angle into [0, 360). */
    public static double normalise(double degrees) {
        double wrapped = degrees % 360.0D;
        return wrapped < 0.0D ? wrapped + 360.0D : wrapped;
    }
}
