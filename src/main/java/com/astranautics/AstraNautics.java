package com.astranautics;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * AstraNautics のメインクラス。
 *
 * <p>現時点では「読み込み確認用」の最小構成で、専用のクリエイティブタブ・
 * アイテム・ブロックは未実装です。ここから徐々に機能を追加していきます。</p>
 */
@Mod(AstraNautics.MOD_ID)
public class AstraNautics {

    /** MOD ID（mods.toml / assets のパスと一致させる） */
    public static final String MOD_ID = "astranautics";

    /** ロガー */
    public static final Logger LOGGER = LogUtils.getLogger();

    public AstraNautics() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 共通セットアップ
        modEventBus.addListener(this::commonSetup);
        // ロード完了時に一度だけログを出す（読み込み確認用）
        modEventBus.addListener(this::loadComplete);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // クリエイティブタブへのアイテム追加フック（将来のアイテム追加用）
        modEventBus.addListener(this::addCreative);

        LOGGER.info("[{}] コンストラクタ実行: 読み込み準備中...", MOD_ID);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("[{}] commonSetup 完了", MOD_ID);
    }

    private void loadComplete(final FMLLoadCompleteEvent event) {
        event.enqueueWork(() ->
                LOGGER.info("[{}] バージョン {} の読み込みが完了しました。Ready for launch!",
                        MOD_ID, ModListHelper.version()));
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // 例: event.getTabKey() == CreativeModeTabs.INGREDIENTS のときにアイテムを追加する
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            // 今後ここに AstraNautics のアイテムを追加
        }
    }

    /** 現在の環境がクライアントかどうか（クラスロードに依存しない判定） */
    public static boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }
}
