package com.astranautics.registry;

import com.astranautics.AstraNautics;
import com.astranautics.item.AdhesiveItem;
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

    /** 接着剤（範囲を選んでまとめて物理化）。 */
    public static final RegistryObject<Item> ADHESIVE = ITEMS.register("adhesive",
            () -> new AdhesiveItem(new Item.Properties().stacksTo(1)));

    /** 物理化ブロックの BlockItem。 */
    public static final RegistryObject<Item> PHYSICALIZED = ITEMS.register("physicalized",
            () -> new BlockItem(ModBlocks.PHYSICALIZED.get(), new Item.Properties()));

    /** 物理コアの BlockItem。 */
    public static final RegistryObject<Item> PHYSICS_CORE = ITEMS.register("physics_core",
            () -> new BlockItem(ModBlocks.PHYSICS_CORE.get(), new Item.Properties()));

    private ModItems() {
    }
}
