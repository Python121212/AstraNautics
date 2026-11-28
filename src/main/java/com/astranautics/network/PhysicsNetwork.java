package com.astranautics.network;

import com.astranautics.AstraNautics;
import com.astranautics.physics.VoxelMining;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * Client to server channel. The only message is "the local player is still holding
 * the attack button on this hull voxel", which lets the server mine the block with
 * survival timings (vanilla only sends one attack packet per click for entities).
 */
public final class PhysicsNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(AstraNautics.MODID, "physics"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private PhysicsNetwork() {
    }

    public static void init() {
        int id = 0;
        CHANNEL.messageBuilder(MineTick.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MineTick::encode)
                .decoder(MineTick::decode)
                .consumerMainThread(MineTick::handle)
                .add();
    }

    /** Client -&gt; server: keep mining this voxel of this ship. */
    public record MineTick(int shipId, BlockPos cell) {
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(this.shipId);
            buffer.writeBlockPos(this.cell);
        }

        public static MineTick decode(FriendlyByteBuf buffer) {
            return new MineTick(buffer.readVarInt(), buffer.readBlockPos());
        }

        public void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            ServerPlayer player = context.getSender();
            if (player != null) {
                VoxelMining.onMineTick(player, this.shipId, this.cell);
            }
            context.setPacketHandled(true);
        }
    }
}
