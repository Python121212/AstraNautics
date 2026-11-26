package com.astranautics.command;

import com.astranautics.AstraNautics;
import com.astranautics.physics.ShipEntity;
import com.astranautics.physics.ShipService;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 船の操作コマンド（テスト・脱出用）。 */
@Mod.EventBusSubscriber(modid = AstraNautics.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShipCommand {

    private ShipCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("astranautics")
                .then(Commands.literal("land").executes(ctx -> {
                    var entity = ctx.getSource().getEntity();
                    if (!(entity instanceof net.minecraft.server.level.ServerPlayer player)) {
                        ctx.getSource().sendFailure(Component.translatable("message.astranautics.need_player"));
                        return 0;
                    }
                    if (!(player.getVehicle() instanceof ShipEntity ship)) {
                        ctx.getSource().sendFailure(Component.translatable("message.astranautics.no_ship"));
                        return 0;
                    }
                    ServerLevel level = (ServerLevel) player.level();
                    int placed = ShipService.land(level, ship, true);
                    if (placed <= 0) {
                        ctx.getSource().sendFailure(Component.translatable("message.astranautics.land_failed"));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                            "message.astranautics.landed", placed), false);
                    return 1;
                }))
                .then(Commands.literal("ship_info").executes(ctx -> {
                    var entity = ctx.getSource().getEntity();
                    if (!(entity instanceof net.minecraft.server.level.ServerPlayer player)) {
                        return 0;
                    }
                    if (player.getVehicle() instanceof ShipEntity ship) {
                        ctx.getSource().sendSuccess(() -> Component.translatable(
                                "message.astranautics.ship_info",
                                ship.cellCount(),
                                String.format("%.1f", ship.totalMass()),
                                String.format("%.2f", ship.thrustPower()),
                                String.format("%.0f%%", ship.getFuel() * 100.0F),
                                String.format("%.2f", ship.getThrottle())), false);
                    } else {
                        ctx.getSource().sendFailure(Component.translatable("message.astranautics.no_ship"));
                    }
                    return 1;
                })));
    }
}
