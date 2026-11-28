package com.astranautics.physics;

import com.astranautics.AstraNautics;
import com.astranautics.config.AstraConfig;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cancels a player's gravity for the tick that starts while they stand on a hull.
 *
 * <p>Vanilla only collides entities with blocks, so a hull is not part of a player's own
 * movement: every tick they sink the 0.078 blocks of one tick of gravity into the deck and
 * the collision pass has to lift them back out. That constant sink is what makes a deck
 * feel unstable: the position is corrected 20 times a second, the player's feet overlap the
 * neighbouring deck block, and their walk speed gets cancelled ("moves a little, stops,
 * moves again").</p>
 *
 * <p>Setting the vertical velocity to exactly one tick of gravity before the player ticks
 * makes the net vertical motion zero, so nothing has to be corrected at all while they walk
 * around a flat deck. Jumping still works (the jump input is applied later in the tick and
 * replaces this value), and leaving the deck simply stops being supported.</p>
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShipPlatformSupport {
    /** Slow falling replaces the gravity per tick with this. */
    private static final double SLOW_FALLING_GRAVITY = 0.01D;

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
        if (!EntityShipCollision.isSupported(player, player.level().getGameTime())) {
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        if (velocity.y > 0.0D) {
            // Rising (jumping, a lift): let vanilla handle it.
            return;
        }

        // Exactly the value vanilla subtracts this tick, so the net vertical motion is zero.
        double gravity = player.hasEffect(MobEffects.SLOW_FALLING)
                ? SLOW_FALLING_GRAVITY
                : player.getAttributeValue(Attributes.GRAVITY);
        player.setDeltaMovement(velocity.x, gravity, velocity.z);
        player.setOnGround(true);
        player.fallDistance = 0.0F;
    }
}
