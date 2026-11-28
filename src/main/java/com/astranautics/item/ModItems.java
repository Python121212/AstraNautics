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

    public static final RegistryObject<Item> PHYSICS_SWITCH = ITEMS.register("physics_switch",
            () -> new BlockItem(ModBlocks.PHYSICS_SWITCH.get(), new Item.Properties()));

    public static final RegistryObject<Item> BALLOON = ITEMS.register("balloon",
            () -> new BlockItem(ModBlocks.BALLOON.get(), new Item.Properties()));

    public static final RegistryObject<Item> PROPELLER = ITEMS.register("propeller",
            () -> new BlockItem(ModBlocks.PROPELLER.get(), new Item.Properties()));

    public static final RegistryObject<Item> STEAM_ENGINE = ITEMS.register("steam_engine",
            () -> new BlockItem(ModBlocks.STEAM_ENGINE.get(), new Item.Properties()));

    public static final RegistryObject<Item> SHAFT = ITEMS.register("shaft",
            () -> new BlockItem(ModBlocks.SHAFT.get(), new Item.Properties()));

    public static final RegistryObject<Item> COGWHEEL = ITEMS.register("cogwheel",
            () -> new BlockItem(ModBlocks.COGWHEEL.get(), new Item.Properties()));

    public static final RegistryObject<Item> FLYWHEEL = ITEMS.register("flywheel",
            () -> new BlockItem(ModBlocks.FLYWHEEL.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
