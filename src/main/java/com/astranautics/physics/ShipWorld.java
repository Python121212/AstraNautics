package com.astranautics.physics;

import com.astranautics.entity.PhysicsShip;
import net.minecraft.resources.ResourceKey;
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
    private static final Map<ResourceKey<Level>, List<PhysicsShip>> SHIPS = new HashMap<>();

    private ShipWorld() {
    }

    public static void add(PhysicsShip ship) {
        // Client levels are tracked as well: the local player's hull collision needs the
        // ships of the current dimension without scanning every entity of the level.
        Level level = ship.level();
        if (level == null) {
            return;
        }
        SHIPS.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(ship);
    }

    public static void remove(PhysicsShip ship) {
        Level level = ship.level();
        if (level == null) {
            return;
        }
        List<PhysicsShip> ships = SHIPS.get(level.dimension());
        if (ships != null) {
            ships.remove(ship);
        }
    }

    public static List<PhysicsShip> ships(Level level) {
        return SHIPS.getOrDefault(level.dimension(), List.of());
    }

    public static void clear() {
        SHIPS.clear();
    }
}
