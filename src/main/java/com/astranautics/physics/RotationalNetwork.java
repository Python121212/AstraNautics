package com.astranautics.physics;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The drivetrain of a hull: engines, shafts, flywheels, gears and propellers, grouped into
 * networks that actually mesh.
 *
 * <p>This is computed once per hull rebuild inside {@link ShipStructure}, so a rotating
 * drivetrain costs nothing per tick - the physics only ever asks "which engines feed this
 * propeller".</p>
 *
 * <p>Meshing rules, deliberately simple enough to understand without a manual:</p>
 * <ul>
 *     <li>a <b>gear</b> meshes with anything rotatable next to it - it is the corner piece,
 *     the only way to change the axis;</li>
 *     <li>two <b>axial</b> parts (engine, shaft, flywheel, propeller) mesh only when they sit
 *     end to end on their shared axis, so a shaft along X next to a shaft along Y does not
 *     transmit anything;</li>
 *     <li>a <b>propeller</b>'s axis is its facing, i.e. the shaft goes in behind it.</li>
 * </ul>
 *
 * <p>Nodes carry an axis index (0 = X, 1 = Y, 2 = Z) rather than a {@code Direction.Axis} so
 * the whole thing can be unit tested without a Minecraft bootstrap.</p>
 */
public final class RotationalNetwork {
    /** What a hull cell contributes to a drivetrain. */
    public enum Kind {
        /** Not part of any drivetrain. */
        NONE,
        /** Source of rotation. */
        ENGINE,
        /** Plain transmission. */
        SHAFT,
        /** Transmission with inertia. */
        FLYWHEEL,
        /** Corner: meshes regardless of axis. */
        GEAR,
        /** Consumer: turns rotation into thrust. */
        PROPELLER
    }

    /** One cell of the drivetrain. */
    public record Node(Kind kind, int axis) {
        public static final Node NONE = new Node(Kind.NONE, 0);

        public boolean rotatable() {
            return this.kind != Kind.NONE;
        }
    }

    /** A connected group of drivetrain cells. */
    public static final class Network {
        private final List<BlockPos> engines = new ArrayList<>();
        private final List<BlockPos> propellers = new ArrayList<>();
        private int flywheels;
        private int parts;

        /** Engine cells feeding this network. */
        public List<BlockPos> engines() {
            return Collections.unmodifiableList(this.engines);
        }

        /** Propeller cells driven by this network. */
        public List<BlockPos> propellers() {
            return Collections.unmodifiableList(this.propellers);
        }

        /** Flywheels on this network; each one slows the engine's response. */
        public int flywheels() {
            return this.flywheels;
        }

        /** Total rotatable cells, for status messages. */
        public int parts() {
            return this.parts;
        }

        /** True when this network can actually turn something. */
        public boolean powered() {
            return !this.engines.isEmpty() && !this.propellers.isEmpty();
        }
    }

    private static final int[][] STEPS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private RotationalNetwork() {
    }

    /**
     * Groups {@code nodes} into meshing networks.
     *
     * @param nodes drivetrain cells by local position; cells that are not drivetrain may be
     *              left out or mapped to {@link Node#NONE}
     */
    public static List<Network> build(Map<BlockPos, Node> nodes) {
        List<Network> networks = new ArrayList<>();
        if (nodes.isEmpty()) {
            return networks;
        }
        Set<BlockPos> seen = new LinkedHashSet<>();
        for (Map.Entry<BlockPos, Node> entry : nodes.entrySet()) {
            if (!entry.getValue().rotatable() || seen.contains(entry.getKey())) {
                continue;
            }
            networks.add(flood(nodes, entry.getKey(), seen));
        }
        return networks;
    }

    private static Network flood(Map<BlockPos, Node> nodes, BlockPos start, Set<BlockPos> seen) {
        Network network = new Network();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            Node node = nodes.get(pos);
            if (node == null || !node.rotatable()) {
                continue;
            }
            network.parts++;
            if (node.kind() == Kind.ENGINE) {
                network.engines.add(pos);
            } else if (node.kind() == Kind.PROPELLER) {
                network.propellers.add(pos);
            } else if (node.kind() == Kind.FLYWHEEL) {
                network.flywheels++;
            }

            for (int[] step : STEPS) {
                int axis = step[0] != 0 ? 0 : (step[1] != 0 ? 1 : 2);
                BlockPos next = pos.offset(step[0], step[1], step[2]);
                if (seen.contains(next)) {
                    continue;
                }
                Node other = nodes.get(next);
                if (other == null || !other.rotatable() || !meshes(node, other, axis)) {
                    continue;
                }
                seen.add(next);
                queue.add(next);
            }
        }
        return network;
    }

    /** Can rotation pass from {@code a} to an adjacent {@code b} across a face on {@code axis}? */
    static boolean meshes(Node a, Node b, int axis) {
        if (a.kind() == Kind.GEAR || b.kind() == Kind.GEAR) {
            return true;
        }
        return a.axis() == axis && b.axis() == axis;
    }

    /**
     * Maps the results onto the hull: propeller cell to the engines that drive it, and every
     * engine cell in the hull.
     */
    public static Drivetrain resolve(Map<BlockPos, Node> nodes) {
        Map<BlockPos, List<BlockPos>> propellerEngines = new LinkedHashMap<>();
        Set<BlockPos> engines = new LinkedHashSet<>();
        int flywheels = 0;
        for (Network network : build(nodes)) {
            engines.addAll(network.engines());
            flywheels += network.flywheels();
            if (network.engines().isEmpty()) {
                continue;
            }
            for (BlockPos propeller : network.propellers()) {
                propellerEngines.put(propeller, List.copyOf(network.engines()));
            }
        }
        return new Drivetrain(propellerEngines, List.copyOf(engines), flywheels);
    }

    /** Result of {@link #resolve(Map)}. */
    public record Drivetrain(Map<BlockPos, List<BlockPos>> propellerEngines,
                             List<BlockPos> engines,
                             int flywheels) {
    }
}
