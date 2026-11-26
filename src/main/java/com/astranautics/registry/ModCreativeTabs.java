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
                        output.accept(ModItems.NOZZLE_MOLD.get());
                        output.accept(ModItems.ENVELOPE.get());
                        output.accept(ModItems.HOT_AIR_BURNER.get());
                        output.accept(ModItems.STEAM_VENT.get());
                        output.accept(ModItems.LEVITITE.get());
                        output.accept(ModItems.PEARLESCENT_LEVITITE.get());
                        output.accept(ModItems.AIRSHIP_ASSEMBLY.get());
                        for (var item : ModItems.ENGINE_PARTS.values()) {
                            output.accept(item.get());
                        }
                        output.accept(ModItems.PHYSICALIZED.get());
                        output.accept(ModItems.PHYSICS_CORE.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }
}
