package com.astranautics.client;

import com.astranautics.AstraNautics;
import com.astranautics.config.AstraConfig;
import com.astranautics.entity.PhysicsShip;
import com.astranautics.physics.EntityShipCollision;
import com.astranautics.physics.ShipWorld;
import com.astranautics.physics.VoxelSpatialIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Client side hull collision for the local player.
 *
 * <p>This must run at the END of the client tick, i.e. <em>after</em> the local player
 * has ticked. Resolving from the ship's own entity tick looked fine on paper, but entity
 * tick order is by id: when the ship ticks before the player, the correction lands first
 * and the player's own gravity then sinks them ~0.08 blocks again, so every tick they
 * visibly dropped and snapped back.</p>
 *
 * <p>Only the local player is resolved here: every other entity is authoritative on the
 * server and pushing it on the client would fight the server's positions. Only the ships
 * of the current dimension are visited (no scan over every entity of the level).</p>
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientShipCollision {
    private ClientShipCollision() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (!AstraConfig.ENABLE_ENTITY_COLLISIONS.get()) {
            return;
        }

        List<PhysicsShip> ships = ShipWorld.ships(minecraft.level);
        if (ships.isEmpty()) {
            return;
        }

        Predicate<Entity> localPlayerOnly = entity -> entity == minecraft.player;
        List<PhysicsShip> stale = null;
        for (PhysicsShip ship : new ArrayList<>(ships)) {
            if (ship.isRemoved() || ship.level() != minecraft.level) {
                // Ships of a level that is gone (world switch): drop them from the registry.
                if (stale == null) {
                    stale = new ArrayList<>();
                }
                stale.add(ship);
                continue;
            }
            VoxelSpatialIndex index = ship.getSpatialIndex();
            if (index != null) {
                EntityShipCollision.resolve(minecraft.level, ship, index, localPlayerOnly);
            }
        }
        if (stale != null) {
            for (PhysicsShip ship : stale) {
                ShipWorld.remove(ship);
            }
        }
    }
}
