package com.astranautics.client;

import com.astranautics.block.RotatorBlock;
import com.astranautics.entity.PhysicsShip;
import com.astranautics.physics.ShipStructure;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Client-only half of the rotator interaction: opens the dial. */
public final class ClientRotatorHook {
    private ClientRotatorHook() {
    }

    /**
     * Opens the dial for a rotator voxel of a hull.
     *
     * <p>The angle comes from the synced structure, so no round trip is needed to show the
     * current setting; picking a new one sends a packet.</p>
     */
    public static void open(PhysicsShip ship, BlockPos cell) {
        ShipStructure structure = ship.getStructure();
        if (structure == null) {
            return;
        }
        BlockState state = structure.voxels().get(cell);
        if (state == null || !(state.getBlock() instanceof RotatorBlock rotator)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new RotatorScreen(ship.getId(), cell, rotator.mode(),
                RotatorBlock.degrees(state)));
    }
}
