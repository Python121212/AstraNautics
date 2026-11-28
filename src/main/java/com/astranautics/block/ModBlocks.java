package com.astranautics.block;

import com.astranautics.AstraNautics;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, AstraNautics.MODID);

    /** The single control block: turning it on detaches the connected structure. */
    public static final RegistryObject<Block> PHYSICS_SWITCH =
            BLOCKS.register("physics_switch", PhysicsSwitchBlock::new);

    /** Lift gas in a block: each one holds up {@code aerodynamics.balloonLift} mass units. */
    public static final RegistryObject<Block> BALLOON =
            BLOCKS.register("balloon", BalloonBlock::new);

    /** Thrust in a block: a powered one pushes the hull along its facing. */
    public static final RegistryObject<Block> PROPELLER =
            BLOCKS.register("propeller", PropellerBlock::new);

    /** Power source: burns fuel to spin the drivetrain it is bolted into. */
    public static final RegistryObject<Block> STEAM_ENGINE =
            BLOCKS.register("steam_engine", SteamEngineBlock::new);

    /** Drivetrain: carries rotation along its axis. */
    public static final RegistryObject<Block> SHAFT =
            BLOCKS.register("shaft", ShaftBlock::new);

    /** Drivetrain corner: meshes with anything, so it can turn the axis. */
    public static final RegistryObject<Block> COGWHEEL =
            BLOCKS.register("cogwheel", CogwheelBlock::new);

    /** Drivetrain inertia: transmits like a shaft and slows the engine's response. */
    public static final RegistryObject<Block> FLYWHEEL =
            BLOCKS.register("flywheel", FlywheelBlock::new);

    /** Turns the whole hull around the world's vertical axis, to the angle on its dial. */
    public static final RegistryObject<Block> YAW_ROTATOR =
            BLOCKS.register("yaw_rotator", () -> new RotatorBlock(com.astranautics.physics.Rotators.Mode.YAW));

    /** Turns the whole hull end over end around the world's east-west axis. */
    public static final RegistryObject<Block> PITCH_ROTATOR =
            BLOCKS.register("pitch_rotator", () -> new RotatorBlock(com.astranautics.physics.Rotators.Mode.PITCH));

    /** Welds a hull to the world: with one of these bolted on, it cannot move any more. */
    public static final RegistryObject<Block> ANCHOR =
            BLOCKS.register("anchor", AnchorBlock::new);

    /** A lifting hook: right-click it and the hull is carried along with the player. */
    public static final RegistryObject<Block> HOOK =
            BLOCKS.register("hook", HookBlock::new);

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
