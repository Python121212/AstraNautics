package com.astranautics.physics;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drivetrain meshing: what actually transmits rotation to a propeller.
 *
 * <p>Axis index: 0 = X, 1 = Y, 2 = Z. A shaft along X only meshes end to end along X; a
 * gear meshes with anything, which is how a drivetrain turns a corner.</p>
 */
class RotationalNetworkTest {
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    private final Map<BlockPos, RotationalNetwork.Node> nodes = new LinkedHashMap<>();

    private void put(int x, int y, int z, RotationalNetwork.Kind kind, int axis) {
        this.nodes.put(new BlockPos(x, y, z), new RotationalNetwork.Node(kind, axis));
    }

    @Test
    void engineShaftPropellerIsOneNetwork() {
        // A drivetrain runs along one axis: shafts sit end to end, not side by side.
        put(0, 0, 0, RotationalNetwork.Kind.ENGINE, Z);
        put(0, 0, 1, RotationalNetwork.Kind.SHAFT, Z);
        put(0, 0, 2, RotationalNetwork.Kind.SHAFT, Z);
        put(0, 0, 3, RotationalNetwork.Kind.PROPELLER, Z);

        RotationalNetwork.Drivetrain drivetrain = RotationalNetwork.resolve(this.nodes);

        assertEquals(1, drivetrain.engines().size());
        List<BlockPos> feeding = drivetrain.propellerEngines().get(new BlockPos(0, 0, 3));
        assertTrue(feeding != null && feeding.contains(new BlockPos(0, 0, 0)),
                "the propeller must be fed by the engine at the far end of the shafts");
    }

    @Test
    void aGapStopsTheDrivetrain() {
        put(0, 0, 0, RotationalNetwork.Kind.ENGINE, Z);
        put(0, 0, 1, RotationalNetwork.Kind.SHAFT, Z);
        // (0, 0, 2) left empty
        put(0, 0, 3, RotationalNetwork.Kind.PROPELLER, Z);

        RotationalNetwork.Drivetrain drivetrain = RotationalNetwork.resolve(this.nodes);

        assertTrue(drivetrain.propellerEngines().isEmpty(),
                "a propeller behind a gap gets no rotation");
    }

    @Test
    void aShaftThatTurnsTheCornerDoesNotTransmit() {
        // An X engine butted against a Z shaft: the faces do not line up with either axis.
        put(0, 0, 0, RotationalNetwork.Kind.ENGINE, X);
        put(1, 0, 0, RotationalNetwork.Kind.SHAFT, Z);
        put(1, 0, 1, RotationalNetwork.Kind.PROPELLER, Z);

        RotationalNetwork.Drivetrain drivetrain = RotationalNetwork.resolve(this.nodes);

        assertTrue(drivetrain.propellerEngines().isEmpty(),
                "a Z shaft butted against an X engine is not a mesh");
    }

    @Test
    void aCogwheelTurnsTheCorner() {
        put(0, 0, 0, RotationalNetwork.Kind.ENGINE, X);
        put(1, 0, 0, RotationalNetwork.Kind.SHAFT, X);
        put(2, 0, 0, RotationalNetwork.Kind.GEAR, X);
        put(2, 1, 0, RotationalNetwork.Kind.SHAFT, Y);
        put(2, 2, 0, RotationalNetwork.Kind.PROPELLER, Y);

        RotationalNetwork.Drivetrain drivetrain = RotationalNetwork.resolve(this.nodes);

        assertTrue(drivetrain.propellerEngines().containsKey(new BlockPos(2, 2, 0)),
                "a gear is the joint that lets a drivetrain climb a corner");
        assertEquals(1, drivetrain.engines().size());
    }

    @Test
    void flywheelsAreCountedPerHull() {
        put(0, 0, 0, RotationalNetwork.Kind.ENGINE, Z);
        put(0, 0, 1, RotationalNetwork.Kind.FLYWHEEL, Z);
        put(0, 0, 2, RotationalNetwork.Kind.FLYWHEEL, Z);
        put(0, 0, 3, RotationalNetwork.Kind.PROPELLER, Z);

        assertEquals(2, RotationalNetwork.resolve(this.nodes).flywheels());
    }

    @Test
    void twoEnginesFeedTheSamePropeller() {
        // Two engines on one line of shafts: the propeller takes the fastest of them.
        put(0, 0, 0, RotationalNetwork.Kind.PROPELLER, Y);
        put(0, -1, 0, RotationalNetwork.Kind.SHAFT, Y);
        put(0, -2, 0, RotationalNetwork.Kind.SHAFT, Y);
        put(0, -3, 0, RotationalNetwork.Kind.ENGINE, Y);
        put(0, -4, 0, RotationalNetwork.Kind.ENGINE, Y);

        RotationalNetwork.Drivetrain drivetrain = RotationalNetwork.resolve(this.nodes);

        List<BlockPos> feeding = drivetrain.propellerEngines().get(new BlockPos(0, 0, 0));
        assertEquals(2, feeding == null ? -1 : feeding.size());
    }

    @Test
    void separateDrivetrainsStaySeparate() {
        put(0, 0, 0, RotationalNetwork.Kind.ENGINE, Z);
        put(0, 0, 1, RotationalNetwork.Kind.PROPELLER, Z);
        put(10, 0, 0, RotationalNetwork.Kind.ENGINE, Z);
        put(10, 0, 1, RotationalNetwork.Kind.PROPELLER, Z);

        RotationalNetwork.Drivetrain drivetrain = RotationalNetwork.resolve(this.nodes);

        assertEquals(List.of(new BlockPos(0, 0, 0)), drivetrain.propellerEngines().get(new BlockPos(0, 0, 1)));
        assertEquals(List.of(new BlockPos(10, 0, 0)), drivetrain.propellerEngines().get(new BlockPos(10, 0, 1)));
    }
}
