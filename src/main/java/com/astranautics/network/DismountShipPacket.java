package com.astranautics.network;

import com.astranautics.physics.ShipEntity;
import com.astranautics.physics.ShipService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 船から降りて、その場でブロックに戻すパケット。 */
public class DismountShipPacket {

    public static void encode(DismountShipPacket packet, FriendlyByteBuf buffer) {
    }

    public static DismountShipPacket decode(FriendlyByteBuf buffer) {
        return new DismountShipPacket();
    }

    public static void handle(DismountShipPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.getVehicle() instanceof ShipEntity ship)) {
                return;
            }
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            int placed = ShipService.land(level, ship, true);
            if (placed > 0) {
                player.displayClientMessage(Component.translatable(
                        "message.astranautics.landed", placed), true);
            } else {
                player.stopRiding();
                player.displayClientMessage(Component.translatable(
                        "message.astranautics.land_failed"), true);
            }
        });
        context.setPacketHandled(true);
    }
}
