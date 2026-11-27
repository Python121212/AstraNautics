package com.astranautics.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common config, written to {@code config/astranautics-common.toml}.
 */
public final class AstraConfig {
    public static final ForgeConfigSpec SPEC;

    // Assembly (the physics switch detaches the connected structure)
    public static final ForgeConfigSpec.IntValue ASSEMBLY_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_BLOCKS;

    // Interaction
    public static final ForgeConfigSpec.DoubleValue PUNCH_SPEED;

    // Rigid body solver
    public static final ForgeConfigSpec.DoubleValue GRAVITY;
    public static final ForgeConfigSpec.IntValue SUBSTEPS;
    public static final ForgeConfigSpec.DoubleValue FRICTION;
    public static final ForgeConfigSpec.DoubleValue RESTITUTION;
    public static final ForgeConfigSpec.DoubleValue LINEAR_DAMPING;
    public static final ForgeConfigSpec.DoubleValue ANGULAR_DAMPING;
    public static final ForgeConfigSpec.IntValue SOLVER_ITERATIONS;

    // Sleeping (idle ships are skipped entirely)
    public static final ForgeConfigSpec.DoubleValue SLEEP_LINEAR_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue SLEEP_ANGULAR_THRESHOLD;
    public static final ForgeConfigSpec.IntValue SLEEP_TICKS;

    // Collision layers
    public static final ForgeConfigSpec.BooleanValue ENABLE_SHIP_COLLISIONS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ENTITY_COLLISIONS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Rigid body physics simulation").push("physics");
        ASSEMBLY_RADIUS = builder
                .comment("Radius (in blocks) around the physics switch that is scanned for the ship structure.")
                .defineInRange("assemblyRadius", 10, 1, 32);
        MAX_BLOCKS = builder
                .comment("Maximum number of blocks a single ship may contain.")
                .defineInRange("maxBlocks", 512, 1, 4096);
        PUNCH_SPEED = builder
                .comment("How fast (blocks/second) a punch can push a ship. Heavier ships move less.")
                .defineInRange("punchSpeed", 2.5D, 0.0D, 20.0D);
        GRAVITY = builder
                .comment("Gravity in blocks/second^2 (vanilla feels like ~32).")
                .defineInRange("gravity", 32.0D, 0.0D, 200.0D);
        SUBSTEPS = builder
                .comment("Physics substeps per game tick. Higher = more accurate and more expensive.")
                .defineInRange("substeps", 2, 1, 8);
        FRICTION = builder
                .comment("Coulomb friction coefficient between ships and the world.")
                .defineInRange("friction", 0.6D, 0.0D, 2.0D);
        RESTITUTION = builder
                .comment("Bounciness (0 = no bounce, 1 = perfectly elastic).")
                .defineInRange("restitution", 0.05D, 0.0D, 1.0D);
        LINEAR_DAMPING = builder
                .comment("Per-tick linear damping factor applied to velocity.")
                .defineInRange("linearDamping", 0.999D, 0.5D, 1.0D);
        ANGULAR_DAMPING = builder
                .comment("Per-tick angular damping factor applied to angular velocity.")
                .defineInRange("angularDamping", 0.995D, 0.5D, 1.0D);
        SOLVER_ITERATIONS = builder
                .comment("Solver iterations per substep. Lower is cheaper; impulses are warm started between steps.")
                .defineInRange("solverIterations", 4, 1, 16);
        builder.pop();

        builder.comment("Sleeping: settled ships skip physics entirely until something wakes them").push("sleeping");
        SLEEP_LINEAR_THRESHOLD = builder
                .comment("Linear speed (blocks/second) below which a ship counts as idle.")
                .defineInRange("linearThreshold", 0.05D, 0.0D, 1.0D);
        SLEEP_ANGULAR_THRESHOLD = builder
                .comment("Angular speed (radians/second) below which a ship counts as idle.")
                .defineInRange("angularThreshold", 0.05D, 0.0D, 1.0D);
        SLEEP_TICKS = builder
                .comment("Consecutive idle ticks before a ship falls asleep.")
                .defineInRange("idleTicks", 40, 1, 400);
        builder.pop();

        builder.comment("Collision layers").push("collisions");
        ENABLE_SHIP_COLLISIONS = builder
                .comment("Ships collide with each other.")
                .define("shipVsShip", true);
        ENABLE_ENTITY_COLLISIONS = builder
                .comment("Entities are pushed out of ships and can stand on them.")
                .define("shipVsEntity", true);
        builder.pop();

        SPEC = builder.build();
    }

    private AstraConfig() {
    }
}
