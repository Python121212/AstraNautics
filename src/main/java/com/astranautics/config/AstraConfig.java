package com.astranautics.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common config, written to {@code config/astranautics-common.toml}.
 */
public final class AstraConfig {
    public static final ForgeConfigSpec SPEC;

    // Gravity anchor / assembly
    public static final ForgeConfigSpec.IntValue ASSEMBLY_RADIUS;
    public static final ForgeConfigSpec.IntValue MAX_BLOCKS;

    // Rigid body solver
    public static final ForgeConfigSpec.DoubleValue GRAVITY;
    public static final ForgeConfigSpec.IntValue SUBSTEPS;
    public static final ForgeConfigSpec.DoubleValue FRICTION;
    public static final ForgeConfigSpec.DoubleValue RESTITUTION;
    public static final ForgeConfigSpec.DoubleValue LINEAR_DAMPING;
    public static final ForgeConfigSpec.DoubleValue ANGULAR_DAMPING;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Gravity Anchor: which blocks get turned into a physics ship").push("gravity_anchor");
        ASSEMBLY_RADIUS = builder
                .comment("Radius (in blocks) around the anchor that is scanned for the ship structure.")
                .defineInRange("assemblyRadius", 8, 1, 32);
        MAX_BLOCKS = builder
                .comment("Maximum number of blocks a single ship may contain.")
                .defineInRange("maxBlocks", 512, 1, 4096);
        builder.pop();

        builder.comment("Rigid body physics simulation").push("physics");
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
        builder.pop();

        SPEC = builder.build();
    }

    private AstraConfig() {
    }
}
