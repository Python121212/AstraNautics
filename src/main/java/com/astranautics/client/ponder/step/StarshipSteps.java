package com.astranautics.client.ponder.step;

import com.astranautics.client.ponder.PonderScene;
import com.astranautics.client.ponder.PonderTheme;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 宇宙船・宇宙戦艦の艦載システムの Ponder。
 *
 * <p>大気圏向けの Create Aeronautics 系ブロックとは別に、
 * <b>艦橋・動力炉・レーダー・砲塔・推力レバー・装甲</b>の見せ方を定義します。</p>
 */
public final class StarshipSteps {

    private StarshipSteps() {
    }

    private static ResourceLocation tex(String path) {
        return new ResourceLocation("astranautics", "block/" + path);
    }

    public static PonderScene shipCore() {
        return GenericSteps.mountedOnShip(
                Component.translatable("ponder.astranautics.ship_core.title"),
                tex("ship_core"), tex("hull_plate_standard"), PonderTheme.STARSHIP,
                "ponder.astranautics.ship_core.1",
                "ponder.astranautics.ship_core.2").build();
    }

    public static PonderScene powerCore() {
        return GenericSteps.singleBlock(
                Component.translatable("ponder.astranautics.ship_power_core.title"),
                tex("ship_power_core"), PonderTheme.STARSHIP_POWER,
                "ponder.astranautics.ship_power_core.1",
                "ponder.astranautics.ship_power_core.2").build();
    }

    public static PonderScene radar() {
        return GenericSteps.singleBlock(
                Component.translatable("ponder.astranautics.ship_radar.title"),
                tex("ship_radar_top"), PonderTheme.STARSHIP_SCAN,
                "ponder.astranautics.ship_radar.1",
                "ponder.astranautics.ship_radar.2").build();
    }

    public static PonderScene turret() {
        return GenericSteps.mountedOnShip(
                Component.translatable("ponder.astranautics.ship_turret.title"),
                tex("ship_turret_front"), tex("hull_plate_heavy"), PonderTheme.STARSHIP_WEAPON,
                "ponder.astranautics.ship_turret.1",
                "ponder.astranautics.ship_turret.2").build();
    }

    public static PonderScene throttle() {
        return GenericSteps.singleBlock(
                Component.translatable("ponder.astranautics.ship_throttle.title"),
                tex("ship_throttle_top"), PonderTheme.STARSHIP,
                "ponder.astranautics.ship_throttle.1",
                "ponder.astranautics.ship_throttle.2").build();
    }

    public static PonderScene hullPlate() {
        return GenericSteps.connectedParts(
                Component.translatable("ponder.astranautics.hull_plate_standard.title"),
                java.util.List.of(tex("hull_plate_light"), tex("hull_plate_standard"),
                        tex("hull_plate_heavy")),
                PonderTheme.STARSHIP_ARMOR,
                "ponder.astranautics.hull_plate_standard.1",
                "ponder.astranautics.hull_plate_standard.2").build();
    }
}
