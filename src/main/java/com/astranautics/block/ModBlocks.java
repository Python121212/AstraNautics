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

    /** Pulls entities towards itself while its physics simulation is enabled. */
    public static final RegistryObject<Block> PHYSICS_ASSEMBLER =
            BLOCKS.register("physics_assembler", PhysicsAssemblerBlock::new);

    /** Small wall switch that toggles every adjacent physics assembler. */
    public static final RegistryObject<Block> PHYSICS_SWITCH =
            BLOCKS.register("physics_switch", PhysicsSwitchBlock::new);

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
