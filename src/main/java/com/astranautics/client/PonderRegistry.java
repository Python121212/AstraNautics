package com.astranautics.client;

import com.astranautics.item.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which items have a ponder and what it says.
 *
 * <p>Registration is by item, so any entry in {@link #ENTRIES} gets the "hold W to see how
 * this works" treatment in every container screen - the inventory, the creative menu, a
 * chest - without each screen having to know about it.</p>
 */
public final class PonderRegistry {
    private static final Map<Item, PonderEntry> ENTRIES = new LinkedHashMap<>();

    static {
        register("physics_switch", ModItems.PHYSICS_SWITCH.get(), 2);
        register("balloon", ModItems.BALLOON.get(), 2);
        register("propeller", ModItems.PROPELLER.get(), 2);
        register("steam_engine", ModItems.STEAM_ENGINE.get(), 2);
        register("shaft", ModItems.SHAFT.get(), 2);
        register("cogwheel", ModItems.COGWHEEL.get(), 2);
        register("flywheel", ModItems.FLYWHEEL.get(), 2);
    }

    private PonderRegistry() {
    }

    private static void register(String id, Item item, int pages) {
        List<PonderEntry.Page> list = new java.util.ArrayList<>(pages);
        for (int i = 0; i < pages; i++) {
            list.add(PonderEntry.page(id, i));
        }
        ENTRIES.put(item, new PonderEntry(id, list));
    }

    /** The ponder for a stack, or {@code null} when this item has nothing to explain. */
    public static PonderEntry of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return ENTRIES.get(stack.getItem());
    }

    public static boolean has(ItemStack stack) {
        return of(stack) != null;
    }

    public static Map<Item, PonderEntry> entries() {
        return ENTRIES;
    }
}
