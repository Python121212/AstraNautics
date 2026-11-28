package com.astranautics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;

/**
 * A lifting hook: bolt one onto a hull, right-click it, and the hull is carried along with you.
 *
 * <p>It works as a winch rather than as a teleport: the hull is dragged towards a point in front
 * of the player by a spring, so it still collides with the world, still swings on its angular
 * damping, and still feels like a heavy thing being towed. A hull heavier than
 * {@code hook.maxMass} cannot be lifted at all.</p>
 */
public class HookBlock extends Block {
    /** Lit while a player is carrying the hull by this hook. */
    public static final BooleanProperty ENGAGED = BooleanProperty.create("engaged");

    public HookBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BROWN)
                .strength(2.0F, 6.0F)
                .sound(SoundType.COPPER)
                .lightLevel(state -> state.getValue(ENGAGED) ? 7 : 0));
        this.registerDefaultState(this.stateDefinition.any().setValue(ENGAGED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ENGAGED);
    }
}
