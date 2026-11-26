package com.astranautics;

import com.astranautics.registry.ModBlockEntities;
import com.astranautics.registry.ModBlocks;
import com.astranautics.registry.ModCreativeTabs;
import com.astranautics.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * AstraNautics のメインクラス。
 *
 * <p>登録処理（ブロック・アイテム・ブロックエンティティ・専用クリエイティブタブ）を行います。</p>
 */
@Mod(AstraNautics.MOD_ID)
@Mod.EventBusSubscriber(modid = AstraNautics.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AstraNautics {

    /** MOD ID（mods.toml / assets のパスと一致させる） */
    public static final String MOD_ID = "astranautics";

    /** ロガー */
    public static final Logger LOGGER = LogUtils.getLogger();

    public AstraNautics() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // レジストリ登録
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        modEventBus.addListener(this::loadComplete);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        LOGGER.info("[{}] コンストラクタ実行: 登録処理を開始します", MOD_ID);
    }

    /** データパック（material_rules.json）の読み込み登録。他 MOD の物性上書きもここで扱う。 */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new com.astranautics.physics.MaterialRules());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(com.astranautics.network.ModNetwork::register);
        LOGGER.info("[{}] commonSetup 完了", MOD_ID);
    }

    private void loadComplete(final FMLLoadCompleteEvent event) {
        event.enqueueWork(() ->
                LOGGER.info("[{}] バージョン {} の読み込みが完了しました。Ready for launch!",
                        MOD_ID, ModListHelper.version()));
    }
}
