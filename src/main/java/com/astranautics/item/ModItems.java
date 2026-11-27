package com.astranautics.item;

import com.astranautics.AstraNautics;
import com.astranautics.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AstraNautics.MODID);

    public static final RegistryObject<Item> PHYSICS_ASSEMBLER = ITEMS.register("physics_assembler",
            () -> new BlockItem(ModBlocks.PHYSICS_ASSEMBLER.get(), new Item.Properties()));

    public static final RegistryObject<Item> PHYSICS_SWITCH = ITEMS.register("physics_switch",
            () -> new BlockItem(ModBlocks.PHYSICS_SWITCH.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
