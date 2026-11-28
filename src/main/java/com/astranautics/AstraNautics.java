package com.astranautics;

import com.astranautics.block.ModBlocks;
import com.astranautics.block.entity.ModBlockEntities;
import com.astranautics.config.AstraConfig;
import com.astranautics.entity.ModEntities;
import com.astranautics.physics.ShipPhysicsTicker;
import com.astranautics.physics.ShipWorld;
import com.astranautics.physics.VoxelMining;
import com.astranautics.item.ModItems;
import com.astranautics.network.PhysicsNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AstraNautics - Minecraft 1.20.1 / Forge mod entry point.
 *
 * <p>Adds the <strong>Physics Assembler</strong>, which detaches a connected block
 * structure and simulates it as a rigid body, and the <strong>Gravity Toggle
 * Switch</strong> that turns the simulation of every adjacent anchor on and off.</p>
 */
@Mod(AstraNautics.MODID)
public class AstraNautics {
    public static final String MODID = "astranautics";
    public static final Logger LOGGER = LoggerFactory.getLogger("AstraNautics");

    public AstraNautics() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AstraConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(ShipPhysicsTicker.class);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        VoxelMining.clear();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(PhysicsNetwork::init);
        LOGGER.info("AstraNautics loaded (Minecraft 1.20.1 / Forge)");
    }
}
