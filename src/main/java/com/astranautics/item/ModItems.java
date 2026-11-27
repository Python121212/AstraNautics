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

    public static final RegistryObject<Item> GRAVITY_ANCHOR = ITEMS.register("gravity_anchor",
            () -> new BlockItem(ModBlocks.GRAVITY_ANCHOR.get(), new Item.Properties()));

    public static final RegistryObject<Item> GRAVITY_SWITCH = ITEMS.register("gravity_switch",
            () -> new BlockItem(ModBlocks.GRAVITY_SWITCH.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
