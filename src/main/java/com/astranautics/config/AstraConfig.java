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

    // Aerodynamics (lift, thrust, buoyancy, drag)
    public static final ForgeConfigSpec.BooleanValue ENABLE_AERODYNAMICS;
    public static final ForgeConfigSpec.DoubleValue BALLOON_LIFT;
    public static final ForgeConfigSpec.DoubleValue PROPELLER_THRUST;
    public static final ForgeConfigSpec.DoubleValue AIR_DRAG;
    public static final ForgeConfigSpec.DoubleValue AIR_PRESSURE_FALLOFF;
    public static final ForgeConfigSpec.DoubleValue PROPELLER_AIRFLOW;
    public static final ForgeConfigSpec.DoubleValue BALLOON_DRAG;
    public static final ForgeConfigSpec.BooleanValue PROPELLER_TORQUE;

    public static final ForgeConfigSpec.BooleanValue ENABLE_ENGINES;
    public static final ForgeConfigSpec.DoubleValue ENGINE_RPM_PER_THROTTLE;
    public static final ForgeConfigSpec.DoubleValue ENGINE_RESPONSE;
    public static final ForgeConfigSpec.DoubleValue ENGINE_FLYWHEEL_RESPONSE;
    public static final ForgeConfigSpec.DoubleValue ENGINE_THRUST_BOOST;

    public static final ForgeConfigSpec.BooleanValue ENABLE_ROTATORS;
    public static final ForgeConfigSpec.DoubleValue ROTATOR_SPEED;
    public static final ForgeConfigSpec.DoubleValue WATER_DENSITY;
    public static final ForgeConfigSpec.DoubleValue WATER_DRAG;

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

        builder.comment("Aerodynamics: balloon lift, propeller thrust, water buoyancy and drag")
                .push("aerodynamics");
        ENABLE_AERODYNAMICS = builder
                .comment("Master switch for lift, thrust, buoyancy and drag.")
                .define("enabled", true);
        BALLOON_LIFT = builder
                .comment("Mass units one balloon block holds up. A balloon itself weighs ~0.5,",
                        "so 1.5 lifts about one extra block of wood per balloon.")
                .defineInRange("balloonLift", 1.5D, 0.0D, 100.0D);
        PROPELLER_THRUST = builder
                .comment("Push of one powered propeller, in mass units * blocks/second^2.",
                        "Two propellers at 80 accelerate a 30 unit hull at roughly 5 blocks/s^2.")
                .defineInRange("propellerThrust", 80.0D, 0.0D, 10000.0D);
        AIR_DRAG = builder
                .comment("Air drag coefficient (F = -k * area * speed^2). Bigger = lower top speed.")
                .defineInRange("airDrag", 0.01D, 0.0D, 1.0D);
        AIR_PRESSURE_FALLOFF = builder
                .comment("Air pressure falls as e^(-falloff * (y - 63)), like Create: Aeronautics.",
                        "Lift, thrust and drag all scale with pressure, so a ship settles at the",
                        "altitude where its lift equals its weight and cannot climb past the point",
                        "where there is no lift left. 0 disables the altitude effect.")
                .defineInRange("pressureFalloff", 0.004D, 0.0D, 0.05D);
        PROPELLER_AIRFLOW = builder
                .comment("Speed (blocks/second) at which a propeller stops pushing: thrust falls",
                        "linearly to zero there, which sets the ship's top speed.")
                .defineInRange("propellerAirflow", 24.0D, 1.0D, 200.0D);
        BALLOON_DRAG = builder
                .comment("Drag per balloon block per block/second (Aeronautics uses 0.33).",
                        "Bulky envelopes are why a big balloon ship is slow.")
                .defineInRange("balloonDrag", 0.33D, 0.0D, 10.0D);
        PROPELLER_TORQUE = builder
                .comment("Apply propeller thrust off-centre so engines also turn the hull.",
                        "Off by default: Create: Aeronautics exempts propellers from torque so",
                        "aircraft do not roll from their own engines.")
                .define("propellerTorque", false);
        WATER_DENSITY = builder
                .comment("Mass of one block of displaced water. Buoyancy = displaced * density.",
                        "Sealed air inside the hull counts as displaced volume too.")
                .defineInRange("waterDensity", 1.0D, 0.0D, 100.0D);
        WATER_DRAG = builder
                .comment("Extra linear damping while the hull is under water.")
                .defineInRange("waterDrag", 3.0D, 0.0D, 50.0D);
        builder.pop();

        builder.push("engines");
        ENABLE_ENGINES = builder
                .comment("Steam engines, shafts, gears and flywheels: a drivetrain boosts the",
                        "propellers it is connected to. Turn this off to have propellers work",
                        "on their own base thrust only.")
                .define("enabled", true);
        ENGINE_RPM_PER_THROTTLE = builder
                .comment("RPM per throttle notch. Throttle 4 (the maximum) is 4x this value.")
                .defineInRange("rpmPerThrottle", 32.0D, 1.0D, 1000.0D);
        ENGINE_RESPONSE = builder
                .comment("Seconds an engine takes to reach a new throttle setting.")
                .defineInRange("response", 0.35D, 0.0D, 10.0D);
        ENGINE_FLYWHEEL_RESPONSE = builder
                .comment("Extra seconds of response per flywheel on the drivetrain.")
                .defineInRange("flywheelResponse", 0.45D, 0.0D, 10.0D);
        ENGINE_THRUST_BOOST = builder
                .comment("Extra thrust a propeller gets at full RPM, as a multiple of its base",
                        "thrust (2.0 = up to three times as much).")
                .defineInRange("thrustBoost", 2.0D, 0.0D, 20.0D);
        builder.pop();

        builder.push("rotators");
        ENABLE_ROTATORS = builder
                .comment("Yaw / pitch rotator blocks: while one sees a redstone signal, the hull",
                        "turns towards the angle its dial was set to.")
                .define("enabled", true);
        ROTATOR_SPEED = builder
                .comment("Turning speed in degrees per second.")
                .defineInRange("speed", 90.0D, 1.0D, 1440.0D);
        builder.pop();

        SPEC = builder.build();
    }

    private AstraConfig() {
    }

}
