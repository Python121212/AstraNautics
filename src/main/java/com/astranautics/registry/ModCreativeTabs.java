package com.astranautics.registry;

import com.astranautics.AstraNautics;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** 専用クリエイティブタブの登録。 */
public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AstraNautics.MOD_ID);

    public static final RegistryObject<CreativeModeTab> ASTRANAUTICS_TAB = TABS.register("astranautics",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.astranautics"))
                    .icon(() -> new ItemStack(ModItems.PHYSICALIZER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.PHYSICALIZER.get());
                        output.accept(ModItems.ADHESIVE.get());
                        output.accept(ModItems.THRUSTER.get());
                        output.accept(ModItems.SHIP_HELM.get());
                        output.accept(ModItems.PHYSICALIZED.get());
                        output.accept(ModItems.PHYSICS_CORE.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
