package com.astranautics.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common config, written to {@code config/astranautics-common.toml}.
 */
public final class AstraConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue RADIUS;
    public static final ForgeConfigSpec.DoubleValue STRENGTH;
    public static final ForgeConfigSpec.DoubleValue MAX_SPEED;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Gravity Anchor physics simulation settings").push("gravity_anchor");

        RADIUS = builder
                .comment("Radius (in blocks) of the gravity field around an enabled anchor.")
                .defineInRange("radius", 12.0D, 1.0D, 64.0D);

        STRENGTH = builder
                .comment("Acceleration added per physics step (blocks/tick). 0.08 is a gentle pull.")
                .defineInRange("strength", 0.08D, 0.0D, 1.0D);

        MAX_SPEED = builder
                .comment("Maximum velocity (blocks/tick) an affected entity can be accelerated to.")
                .defineInRange("maxSpeed", 1.2D, 0.1D, 10.0D);

        builder.pop();

        SPEC = builder.build();
    }

    private AstraConfig() {
    }
}
