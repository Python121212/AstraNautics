package com.astranautics.physics;

import com.astranautics.AstraNautics;
import com.astranautics.config.AstraConfig;
import com.astranautics.entity.PhysicsShip;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Once-per-tick pass that only makes sense with every ship of a level in hand:
 * ship-vs-ship contacts, ship-vs-entity contacts and waking sleeping neighbours.
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShipPhysicsTicker {
    private ShipPhysicsTicker() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }

        List<PhysicsShip> ships = ShipWorld.ships(level);
        if (ships.isEmpty()) {
            return;
        }

        // Copy: resolving contacts may discard ships (e.g. a ship falling out of the world).
        List<PhysicsShip> snapshot = new ArrayList<>(ships);
        PhysicsParams params = PhysicsParams.fromConfig();
        double h = 1.0D / 20.0D;

        for (PhysicsShip ship : snapshot) {
            ship.refreshSpatialIndex();
        }

        if (AstraConfig.ENABLE_SHIP_COLLISIONS.get()) {
            for (int i = 0; i < snapshot.size(); i++) {
                PhysicsShip a = snapshot.get(i);
                VoxelSpatialIndex indexA = a.getSpatialIndex();
                if (indexA == null) {
                    continue;
                }
                for (int j = i + 1; j < snapshot.size(); j++) {
                    PhysicsShip b = snapshot.get(j);
                    VoxelSpatialIndex indexB = b.getSpatialIndex();
                    if (indexB == null) {
                        continue;
                    }
                    if (!a.getBoundingBox().inflate(1.0D).intersects(b.getBoundingBox())) {
                        // Waking: a moving ship brushing a sleeping one should wake it.
                        if (a.getBoundingBox().inflate(1.5D).intersects(b.getBoundingBox())) {
                            if (!a.isSleeping()) {
                                b.wake();
                            } else if (!b.isSleeping()) {
                                a.wake();
                            }
                        }
                        continue;
                    }
                    ShipPairPhysics.resolve(params, h, a, indexA, b, indexB);
                }
            }
        }

        if (AstraConfig.ENABLE_ENTITY_COLLISIONS.get()) {
            for (PhysicsShip ship : snapshot) {
                VoxelSpatialIndex index = ship.getSpatialIndex();
                if (index != null) {
                    EntityShipCollision.resolve(level, ship, index);
                }
            }
        }
    }
}
