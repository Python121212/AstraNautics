package com.astranautics.physics;

import com.astranautics.AstraNautics;
import com.astranautics.config.AstraConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Pins a player's feet to the deck height that the collision pass resolved last tick.
 *
 * <p>Vanilla only collides entities with blocks, so a hull is not part of a player's own
 * movement. Without help the player sinks one tick of gravity (0.078) into the deck, the
 * hull collision pushes them back out at the end of the tick, and the camera wobbles with
 * that correction - the whole world appears to shake because the camera is the world's
 * reference frame.</p>
 *
 * <p>Cancelling gravity was not enough: any rounding left a residual sink that the
 * collision pass had to fix, so the position still moved by a few thousandths every tick.
 * Snapping the feet to the exact height we resolved last tick makes the position a
 * constant of the deck instead: the tick's gravity sink is undone by the same collision
 * pass, and the rendered position (the {@code yo -> y} pair of the tick) is identical, so
 * nothing moves at all. Jumping is untouched (the snap is skipped while rising), and
 * stepping off simply stops the support from being recorded.</p>
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShipPlatformSupport {
    private ShipPlatformSupport() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Player player = event.player;
        if (player.isPassenger() || player.isSpectator() || player.isNoGravity()) {
            return;
        }
        if (!AstraConfig.ENABLE_ENTITY_COLLISIONS.get()) {
            return;
        }

        double deckY = EntityShipCollision.supportHeight(player, player.level().getGameTime());
        if (Double.isNaN(deckY)) {
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y > 0.02D) {
            // Rising (jumping, a lift): let vanilla handle it.
            return;
        }

        player.setPos(player.getX(), deckY, player.getZ());
        player.setDeltaMovement(velocity.x, 0.0D, velocity.z);
        player.setOnGround(true);
        player.fallDistance = 0.0F;
    }
}
