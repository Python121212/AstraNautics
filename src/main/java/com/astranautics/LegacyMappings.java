package com.astranautics;

import com.astranautics.block.ModBlocks;
import com.astranautics.block.entity.ModBlockEntities;
import com.astranautics.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Supplier;

/**
 * World migration for v3.2.0.
 *
 * <p>The physics assembler block was removed: the {@code physics_switch} is now the
 * only control block. Worlds created with v3.0 - v3.1 still contain assembler blocks,
 * so their registry entries are remapped to the switch instead of silently turning
 * into air. The block entity NBT (ship id, last result) is compatible, so a ship that
 * was assembled before the update keeps working.</p>
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LegacyMappings {
    private static final ResourceLocation OLD_ASSEMBLER =
            new ResourceLocation(AstraNautics.MODID, "physics_assembler");

    private LegacyMappings() {
    }

    @SubscribeEvent
    public static void onMissingBlocks(RegistryEvent.MissingMappings<Block> event) {
        remap(event, ModBlocks.PHYSICS_SWITCH::get);
    }

    @SubscribeEvent
    public static void onMissingItems(RegistryEvent.MissingMappings<Item> event) {
        remap(event, ModItems.PHYSICS_SWITCH::get);
    }

    @SubscribeEvent
    public static void onMissingBlockEntities(RegistryEvent.MissingMappings<BlockEntityType<?>> event) {
        remap(event, ModBlockEntities.PHYSICS_SWITCH::get);
    }

    private static <T> void remap(RegistryEvent.MissingMappings<T> event, Supplier<T> replacement) {
        for (RegistryEvent.MissingMappings.Mapping<T> mapping : event.getMappings()) {
            if (!OLD_ASSEMBLER.equals(mapping.key)) {
                continue;
            }
            T value = replacement.get();
            if (value == null) {
                mapping.ignore();
            } else {
                AstraNautics.LOGGER.info("[astranautics] remapping the removed physics assembler to the physics switch");
                mapping.remap(value);
            }
        }
    }
}
