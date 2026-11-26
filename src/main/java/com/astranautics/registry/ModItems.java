package com.astranautics.registry;

import com.astranautics.AstraNautics;
import com.astranautics.item.PhysicalizerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** アイテムの登録。 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AstraNautics.MOD_ID);

    /** 物理化装置（元のブロックに物理演算を追加するアイテム）。 */
    public static final RegistryObject<Item> PHYSICALIZER = ITEMS.register("physicalizer",
            () -> new PhysicalizerItem(new Item.Properties().stacksTo(16)));

    /** 物理化ブロックの BlockItem。 */
    public static final RegistryObject<Item> PHYSICALIZED = ITEMS.register("physicalized",
            () -> new BlockItem(ModBlocks.PHYSICALIZED.get(), new Item.Properties()));

    private ModItems() {
    }
}
