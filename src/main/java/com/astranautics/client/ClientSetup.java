package com.astranautics.client;

import com.astranautics.AstraNautics;
import com.astranautics.registry.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** クライアント側のセットアップ（レンダラー登録）。 */
@Mod.EventBusSubscriber(modid = AstraNautics.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PHYSICALIZED.get(), PhysicalizedRenderer::new);
        event.registerEntityRenderer(com.astranautics.registry.ModEntities.SHIP.get(), ShipRenderer::new);
    }
}
