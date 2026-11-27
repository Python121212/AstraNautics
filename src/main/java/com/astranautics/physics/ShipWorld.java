package com.astranautics.physics;

import com.astranautics.entity.GravityShip;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-dimension registry of live ships.
 *
 * <p>Used for the once-per-tick ship-vs-ship / ship-vs-entity pass and for the
 * sleep pass, so those do not run once per ship.</p>
 */
public final class ShipWorld {
    private static final Map<ResourceKey<Level>, List<GravityShip>> SHIPS = new HashMap<>();

    private ShipWorld() {
    }

    public static void add(GravityShip ship) {
        if (!(ship.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        SHIPS.computeIfAbsent(serverLevel.dimension(), key -> new ArrayList<>()).add(ship);
    }

    public static void remove(GravityShip ship) {
        if (!(ship.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        List<GravityShip> ships = SHIPS.get(serverLevel.dimension());
        if (ships != null) {
            ships.remove(ship);
        }
    }

    public static List<GravityShip> ships(ServerLevel level) {
        return SHIPS.getOrDefault(level.dimension(), List.of());
    }

    public static void clear() {
        SHIPS.clear();
    }
}
