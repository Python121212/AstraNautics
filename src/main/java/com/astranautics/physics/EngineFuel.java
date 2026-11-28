package com.astranautics.physics;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What the steam engine accepts as fuel, and for how many ticks it burns. */
public final class EngineFuel {
    private EngineFuel() {
    }

    /** Burn time in ticks for the given stack, or 0 if it is not fuel. */
    public static int burnTicks(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Item item = stack.getItem();
        if (item == Items.COAL) {
            return 1600;
        }
        if (item == Items.CHARCOAL) {
            return 1200;
        }
        if (item == Items.COAL_BLOCK) {
            return 16000;
        }
        if (item == Items.BLAZE_ROD) {
            return 2400;
        }
        if (item == Items.DRIED_KELP_BLOCK) {
            return 4001;
        }
        if (item == Items.STICK) {
            return 200;
        }
        return 0;
    }
}
