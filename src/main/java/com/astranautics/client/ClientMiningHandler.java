package com.astranautics.client;

import com.astranautics.AstraNautics;
import com.astranautics.entity.PhysicsShip;
import com.astranautics.network.PhysicsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Watches the attack button while the crosshair is on a hull and streams
 * {@code MineTick} messages, so holding left click mines a block with survival
 * timings (a quick click stays a punch).
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientMiningHandler {
    /** Ticks the attack button must be held before this counts as mining. */
    private static final int HOLD_TICKS = 3;

    private static int heldTicks;
    private static int shipId = -1;
    private static BlockPos cell;

    private ClientMiningHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) {
            reset();
            return;
        }
        if (minecraft.player.isSpectator() || !minecraft.options.keyAttack.isDown()) {
            reset();
            return;
        }
        if (!(minecraft.hitResult instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof PhysicsShip ship)) {
            reset();
            return;
        }

        BlockPos target = ship.raycastCell(minecraft.player.getEyePosition(), minecraft.player.getLookAngle());
        if (target == null) {
            reset();
            return;
        }
        if (ship.getId() != shipId || !target.equals(cell)) {
            heldTicks = 0;
            shipId = ship.getId();
            cell = target;
        }

        heldTicks++;
        if (heldTicks >= HOLD_TICKS && (minecraft.player.tickCount & 1) == 0) {
            PhysicsNetwork.CHANNEL.sendToServer(new PhysicsNetwork.MineTick(shipId, target));
        }
    }

    private static void reset() {
        heldTicks = 0;
        shipId = -1;
        cell = null;
    }
}
