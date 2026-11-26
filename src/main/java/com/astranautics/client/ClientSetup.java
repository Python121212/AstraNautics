package com.astranautics.client;

import com.astranautics.AstraNautics;
import com.astranautics.registry.ModBlockEntities;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** クライアント側のセットアップ（レンダラー・アイテムモデル登録）。 */
@Mod.EventBusSubscriber(modid = AstraNautics.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PHYSICALIZED.get(), PhysicalizedRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                com.astranautics.registry.ModItems.PHYSICALIZED.get(),
                new ResourceLocation(AstraNautics.MOD_ID, "has_block"),
                (stack, level, entity, seed) -> stack.hasTag() && stack.getTag().contains("BlockEntityTag") ? 1.0F : 0.0F));
    }
}
