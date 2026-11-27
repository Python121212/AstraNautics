package com.astranautics;

import com.astranautics.block.ModBlocks;
import com.astranautics.block.entity.ModBlockEntities;
import com.astranautics.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;

import java.util.List;

/**
 * World migration for v3.2.0.
 *
 * <p>The physics assembler block was removed: the {@code physics_switch} is now the
 * only control block. Worlds created with v3.0 - v3.1 still contain assembler blocks,
 * so their registry entries are remapped to the switch instead of turning into air.
 * The block entity NBT (ship id, last result) is compatible, so a ship that was
 * assembled before the update keeps working.</p>
 *
 * <p>{@link MissingMappingsEvent} is fired on the Forge event bus while the world's
 * saved registry is loaded.</p>
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyMappings {
    private static final ResourceLocation OLD_ASSEMBLER =
            new ResourceLocation(AstraNautics.MODID, "physics_assembler");

    private LegacyMappings() {
    }

    @SubscribeEvent
    public static void onMissingMappings(MissingMappingsEvent event) {
        if (event.getKey().equals(ForgeRegistries.Keys.BLOCKS)) {
            remap(event.getMappings(ForgeRegistries.Keys.BLOCKS, AstraNautics.MODID),
                    ModBlocks.PHYSICS_SWITCH.get(), "block");
        } else if (event.getKey().equals(ForgeRegistries.Keys.ITEMS)) {
            remap(event.getMappings(ForgeRegistries.Keys.ITEMS, AstraNautics.MODID),
                    ModItems.PHYSICS_SWITCH.get(), "item");
        } else if (event.getKey().equals(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES)) {
            remap(event.getMappings(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, AstraNautics.MODID),
                    ModBlockEntities.PHYSICS_SWITCH.get(), "block entity");
        }
    }

    private static <T> void remap(List<MissingMappingsEvent.Mapping<T>> mappings, T target, String kind) {
        for (MissingMappingsEvent.Mapping<T> mapping : mappings) {
            if (!OLD_ASSEMBLER.equals(mapping.getKey())) {
                continue;
            }
            AstraNautics.LOGGER.info("[astranautics] remapping the removed physics assembler {} to physics_switch", kind);
            mapping.remap(target);
        }
    }
}
