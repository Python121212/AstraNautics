package com.astranautics.client;

import com.astranautics.AstraNautics;
import com.astranautics.physics.ShipEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 航行中の船のクライアント側処理。
 *
 * <ul>
 *   <li>船の HUD（速度・燃料・スロットル・セル数）</li>
 *   <li>スニーク + 右クリックで降りる（降りた場所でブロックに戻す）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ShipClientHandler {

    private ShipClientHandler() {
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity().getVehicle() instanceof ShipEntity ship)) {
            return;
        }
        if (!event.getEntity().isShiftKeyDown()) {
            return;
        }
        // 降船（サーバーへはコマンド相当のパケットではなく、
        // スニーク右クリック時にサーバー側で処理するためキャンセルだけする）
        event.setCanceled(true);
        com.astranautics.network.ModNetwork.sendDismountShip();
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.player != null && minecraft.player.getVehicle() instanceof ShipEntity ship)) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();

        int panelW = 168;
        int x = width - panelW - 8;
        int y = 8;
        graphics.fill(x - 4, y - 4, x + panelW + 4, y + 62, 0xB0001018);
        graphics.fill(x - 4, y - 4, x + panelW + 4, y - 3, 0xFF2AC8D2);

        graphics.drawString(minecraft.font, Component.translatable("block.astranautics.ship_helm"),
                x, y, 0xFFDFF8FA, false);
        graphics.drawString(minecraft.font, Component.translatable(
                        "hud.astranautics.ship_cells", ship.cellCount(),
                        String.format("%.1f", ship.totalMass())),
                x, y + 12, 0xFF9FD8E0, false);

        // 燃料バー
        int fuelW = (int) (panelW * ship.getFuel());
        graphics.fill(x, y + 24, x + panelW, y + 30, 0xFF1B3340);
        graphics.fill(x, y + 24, x + fuelW, y + 30, 0xFF5FE0EA);
        graphics.drawString(minecraft.font, Component.translatable(
                        "hud.astranautics.ship_fuel",
                        String.format("%.0f", ship.getFuel() * 100.0F)),
                x, y + 32, 0xFF7FD9E0, false);

        // スロットルバー
        int throttleW = (int) (panelW * Math.abs(ship.getThrottle()));
        graphics.fill(x, y + 42, x + panelW, y + 47, 0xFF1B3340);
        graphics.fill(x, y + 42, x + throttleW, y + 47, 0xFFFFC46A);
        graphics.drawString(minecraft.font, Component.translatable(
                        "hud.astranautics.ship_speed",
                        String.format("%.2f", ship.getDeltaMovement().length())),
                x, y + 50, 0xFFCFEFFF, false);

        // 浮力（気密外皮 + レビタイト）
        int by = y + 62;
        double lift = ship.syncedLift();
        graphics.fill(x - 4, by - 4, x + panelW + 4, by + 30, 0xB0001018);
        graphics.fill(x - 4, by - 4, x + panelW + 4, by - 3, 0xFFB08CF0);
        int liftW = (int) (panelW * Math.min(1.0D, lift / 20.0D));
        graphics.fill(x, by + 12, x + panelW, by + 17, 0xFF241B33);
        graphics.fill(x, by + 12, x + liftW, by + 17, 0xFFB08CF0);
        graphics.drawString(minecraft.font, Component.translatable(
                        "hud.astranautics.ship_lift",
                        String.format("%.1f", lift),
                        ship.syncedEnvelope(), ship.syncedLevitite()),
                x, by, 0xFFD8C6FF, false);
        graphics.drawString(minecraft.font, Component.translatable(
                        "hud.astranautics.ship_lift_hint"),
                x, by + 20, lift > 0 ? 0xFF9FD8E0 : 0xFF8A98A0, false);

        // 自作エンジンの情報
        var engine = ship.engine();
        int ey = by + 38;
        if (engine != null && !engine.isEmpty()) {
            graphics.fill(x - 4, ey - 4, x + panelW + 4, ey + 56, 0xB0001018);
            graphics.fill(x - 4, ey - 4, x + panelW + 4, ey - 3, 0xFFFFC46A);
            graphics.drawString(minecraft.font, engine.summary(), x, ey, 0xFFFFE0A0, false);
            graphics.drawString(minecraft.font, engine.advice(), x, ey + 11, 0xFFCFEFFF, false);
            graphics.drawString(minecraft.font, Component.translatable(
                            "hud.astranautics.engine_speed",
                            String.format("%.2f", engine.topSpeed()),
                            String.format("%.0f", engine.designPressure() * 100.0D)),
                    x, ey + 20, engine.overStressed() ? 0xFFFF8A6A : 0xFFCFEFFF, false);

            // 発熱バー
            int heatW = (int) (panelW * Math.min(1.0D, ship.engineHeat()));
            graphics.fill(x, ey + 32, x + panelW, ey + 37, 0xFF1B3340);
            graphics.fill(x, ey + 32, x + heatW, ey + 37,
                    ship.engineHeat() > 0.85D ? 0xFFFF6A5A : 0xFFFFC46A);
            graphics.drawString(minecraft.font, Component.translatable(
                            "hud.astranautics.engine_heat",
                            String.format("%.0f", ship.engineHeat() * 100.0D)),
                    x, ey + 40, 0xFFE8D0A0, false);
        }

        // 操作方法（下部）
        net.minecraft.network.chat.Component help = Component.translatable(
                "hud.astranautics.ship_controls");
        int hw = minecraft.font.width(help);
        graphics.fill(width / 2 - hw / 2 - 5, height - 30, width / 2 + hw / 2 + 5, height - 16, 0x90001018);
        graphics.drawString(minecraft.font, help, width / 2 - hw / 2, height - 26, 0xFF9FD8E0, false);
    }
}
