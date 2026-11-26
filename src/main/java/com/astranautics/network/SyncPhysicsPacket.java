package com.astranautics.network;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicalizedBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 物理オフセット同期パケット（サーバー → クライアント）。
 *
 * <p>値は 1/512 ブロック精度に量子化し、boolean と合わせて 3 つの int + 1 byte だけ。
 * ブロック更新パケットを使わないため、チャンクの再メッシュが発生しません。</p>
 */
public class SyncPhysicsPacket {

    private final BlockPos pos;
    private final int x;
    private final int y;
    private final int z;
    private final boolean powered;

    public SyncPhysicsPacket(BlockPos pos, double offX, double offY, double offZ, boolean powered) {
        this.pos = pos.immutable();
        this.x = (int) Math.round(offX * 512.0D);
        this.y = (int) Math.round(offY * 512.0D);
        this.z = (int) Math.round(offZ * 512.0D);
        this.powered = powered;
    }

    public static SyncPhysicsPacket of(BlockPos pos, PhysicalizedBlockEntity be) {
        return new SyncPhysicsPacket(pos,
                be.getOffX(), be.getOffY(), be.getOffZ(), be.isPhysicsEnabled());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeVarInt(this.x);
        buf.writeVarInt(this.y);
        buf.writeVarInt(this.z);
        buf.writeBoolean(this.powered);
    }

    public static SyncPhysicsPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int x = buf.readVarInt();
        int y = buf.readVarInt();
        int z = buf.readVarInt();
        boolean powered = buf.readBoolean();
        return new SyncPhysicsPacket(pos, x / 512.0D, y / 512.0D, z / 512.0D, powered);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.apply(this)));
        context.setPacketHandled(true);
    }

    private static final class ClientHandler {
        private ClientHandler() {
        }

        static void apply(SyncPhysicsPacket packet) {
            var level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            if (level.getBlockEntity(packet.pos) instanceof PhysicalizedBlockEntity be) {
                be.capturePreviousOffset();
                be.applyServerOffsets(packet.x / 512.0D, packet.y / 512.0D, packet.z / 512.0D, packet.powered);
            } else {
                AstraNautics.LOGGER.debug("[{}] 同期先のブロックエンティティが見つかりません: {}",
                        AstraNautics.MOD_ID, packet.pos);
            }
        }
    }
}
