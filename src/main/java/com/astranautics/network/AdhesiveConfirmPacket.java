package com.astranautics.network;

import com.astranautics.AstraNautics;
import com.astranautics.item.AdhesiveItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 接着剤の「左クリック確定」をサーバーへ伝えるパケット。
 *
 * <p>クライアントは選択状態をアイテム NBT に持っているため、確定時だけ送れば十分です。</p>
 */
public class AdhesiveConfirmPacket {

    /** 選択リスト（クライアント側で確定したもの）。 */
    private final int[] xs;
    private final int[] ys;
    private final int[] zs;

    public AdhesiveConfirmPacket(int[] xs, int[] ys, int[] zs) {
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
    }

    public static AdhesiveConfirmPacket of(java.util.List<net.minecraft.core.BlockPos> selection) {
        int[] xs = new int[selection.size()];
        int[] ys = new int[selection.size()];
        int[] zs = new int[selection.size()];
        for (int i = 0; i < selection.size(); i++) {
            xs[i] = selection.get(i).getX();
            ys[i] = selection.get(i).getY();
            zs[i] = selection.get(i).getZ();
        }
        return new AdhesiveConfirmPacket(xs, ys, zs);
    }

    public static void encode(AdhesiveConfirmPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.xs.length);
        for (int i = 0; i < packet.xs.length; i++) {
            buffer.writeInt(packet.xs[i]);
            buffer.writeInt(packet.ys[i]);
            buffer.writeInt(packet.zs[i]);
        }
    }

    public static AdhesiveConfirmPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        int[] xs = new int[size];
        int[] ys = new int[size];
        int[] zs = new int[size];
        for (int i = 0; i < size; i++) {
            xs[i] = buffer.readInt();
            ys[i] = buffer.readInt();
            zs[i] = buffer.readInt();
        }
        return new AdhesiveConfirmPacket(xs, ys, zs);
    }

    public static void handle(AdhesiveConfirmPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            ItemStack stack = player.getMainHandItem();
            if (!(stack.getItem() instanceof AdhesiveItem)) {
                stack = player.getOffhandItem();
            }
            if (!(stack.getItem() instanceof AdhesiveItem)) {
                return;
            }
            java.util.List<net.minecraft.core.BlockPos> selection = new java.util.ArrayList<>(packet.xs.length);
            for (int i = 0; i < packet.xs.length; i++) {
                selection.add(new net.minecraft.core.BlockPos(packet.xs[i], packet.ys[i], packet.zs[i]));
            }
            // サーバー側でも同じ検証を通す
            java.util.List<net.minecraft.core.BlockPos> valid = new java.util.ArrayList<>(selection.size());
            for (net.minecraft.core.BlockPos pos : selection) {
                var state = player.level().getBlockState(pos);
                if (state.isAir() || com.astranautics.block.PhysicalizedBlock.isUnsupported(state)) {
                    continue;
                }
                if (pos.distSqr(player.blockPosition()) > 64.0D * 64.0D) {
                    continue;
                }
                valid.add(pos);
            }
            if (valid.isEmpty()) {
                AstraNautics.LOGGER.debug("[AstraNautics] 接着確定: 有効なブロックがありません");
                return;
            }
            AdhesiveItem.confirmOnServer(player, stack, valid);
        });
        context.setPacketHandled(true);
    }
}
