package com.astranautics.network;

import com.astranautics.AstraNautics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** AstraNautics の軽量ネットワーク（物理オフセットの同期専用）。 */
public final class ModNetwork {

    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AstraNautics.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** 同期パケットを送る最大距離（ブロック）。 */
    private static final double RANGE = 48.0D;

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncPhysicsPacket.class,
                SyncPhysicsPacket::encode, SyncPhysicsPacket::decode, SyncPhysicsPacket::handle);
        CHANNEL.registerMessage(id++, DismountShipPacket.class,
                DismountShipPacket::encode, DismountShipPacket::decode, DismountShipPacket::handle);
        CHANNEL.registerMessage(id++, AdhesiveConfirmPacket.class,
                AdhesiveConfirmPacket::encode, AdhesiveConfirmPacket::decode,
                AdhesiveConfirmPacket::handle);
    }

    /** スニーク右クリックで船から降りる。 */
    public static void sendDismountShip() {
        CHANNEL.sendToServer(new DismountShipPacket());
    }

    /** 接着剤の「左クリック確定」をサーバーへ送る。 */
    public static void sendConfirmSelection() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        net.minecraft.world.item.ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof com.astranautics.item.AdhesiveItem)) {
            stack = player.getOffhandItem();
        }
        if (!(stack.getItem() instanceof com.astranautics.item.AdhesiveItem)) {
            return;
        }
        var selection = com.astranautics.item.AdhesiveItem.getSelection(stack);
        if (selection.isEmpty()) {
            return;
        }
        CHANNEL.sendToServer(AdhesiveConfirmPacket.of(selection));
    }

    /** 近くのプレイヤーだけにオフセットを送る（チャンク再送信を一切起こさない）。 */
    public static void sendToNearby(ServerLevel level, net.minecraft.core.BlockPos pos, SyncPhysicsPacket packet) {
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        double rangeSq = RANGE * RANGE;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(cx, cy, cz) <= rangeSq) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }
}
