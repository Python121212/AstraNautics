package com.astranautics.block.entity;

import com.astranautics.AstraNautics;
import com.astranautics.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AstraNautics.MODID);

    public static final RegistryObject<BlockEntityType<PhysicsSwitchBlockEntity>> PHYSICS_SWITCH =
            BLOCK_ENTITIES.register("physics_switch",
                    () -> BlockEntityType.Builder
                            .of(PhysicsSwitchBlockEntity::new, ModBlocks.PHYSICS_SWITCH.get())
                            .build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
