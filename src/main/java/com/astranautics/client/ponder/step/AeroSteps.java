package com.astranautics.client.ponder.step;

import com.astranautics.client.ponder.PonderTheme;
import com.astranautics.client.ponder.PonderScene;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 航空ブロック（気密外皮・熱気バーナー・蒸気ベント・レビタイト・組み立て台）の Ponder。
 *
 * <p>Create Aeronautics の Ponder と同じく「置き方のコツ」を短い文章で伝えます。</p>
 */
public final class AeroSteps {

    private AeroSteps() {
    }

    private static ResourceLocation tex(String path) {
        return new ResourceLocation("astranautics", "block/" + path);
    }

    /** 気密外皮：袋を作ってバーナーで暖める。 */
    public static PonderScene envelope() {
        return GenericSteps.connectedParts(
                Component.translatable("ponder.astranautics.envelope.title"),
                java.util.List.of(tex("envelope"), tex("envelope"), tex("envelope")),
                PonderTheme.AERO,
                "ponder.astranautics.envelope.1",
                "ponder.astranautics.envelope.2",
                "ponder.astranautics.envelope.3").build();
    }

    /** 熱気バーナー：船の下に付けて暖める。 */
    public static PonderScene hotAirBurner() {
        return GenericSteps.mountedOnShip(
                Component.translatable("ponder.astranautics.hot_air_burner.title"),
                tex("hot_air_burner_top"), tex("envelope"),
                PonderTheme.AERO_HOT,
                "ponder.astranautics.hot_air_burner.1",
                "ponder.astranautics.hot_air_burner.2").build();
    }

    /** 蒸気ベント。 */
    public static PonderScene steamVent() {
        return GenericSteps.singleBlock(
                Component.translatable("ponder.astranautics.steam_vent.title"),
                tex("steam_vent_top"), PonderTheme.AERO_STEAM,
                "ponder.astranautics.steam_vent.1",
                "ponder.astranautics.steam_vent.2").build();
    }

    /** レビタイト：常に浮く。 */
    public static PonderScene levitite() {
        return GenericSteps.singleBlock(
                Component.translatable("ponder.astranautics.levitite.title"),
                tex("levitite"), PonderTheme.AERO_LEVITITE,
                "ponder.astranautics.levitite.1",
                "ponder.astranautics.levitite.2").build();
    }

    /** 組み立て台：バランス表示。 */
    public static PonderScene airshipAssembly() {
        return GenericSteps.singleBlock(
                Component.translatable("ponder.astranautics.airship_assembly.title"),
                tex("airship_assembly_top"), PonderTheme.AERO,
                "ponder.astranautics.airship_assembly.1",
                "ponder.astranautics.airship_assembly.2").build();
    }
}
