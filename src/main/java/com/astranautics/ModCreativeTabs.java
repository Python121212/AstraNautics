package com.astranautics;

import com.astranautics.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Dedicated creative tab so the mod does not depend on Forge's
 * per-version "add to vanilla tab" event API.
 */
public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AstraNautics.MODID);

    public static final RegistryObject<CreativeModeTab> ASTRANAUTICS_TAB =
            CREATIVE_MODE_TABS.register("astranautics", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.astranautics"))
                    .icon(() -> new ItemStack(ModItems.PHYSICS_SWITCH.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.PHYSICS_SWITCH.get());
                        output.accept(ModItems.BALLOON.get());
                        output.accept(ModItems.PROPELLER.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
