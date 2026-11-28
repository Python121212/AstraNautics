package com.astranautics.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;

/**
 * Welds a physical hull to the world.
 *
 * <p>A hull is a rigid body, so it falls, slides and gets shoved around. Bolting one of these
 * on locks it: the pose is remembered and restored every tick, and the hull's grid is snapped
 * to whole blocks first (so it lines up with the normal blocks around it). Breaking the block,
 * or right-clicking it, releases the hull again.</p>
 */
public class AnchorBlock extends Block {
    /** Whether the hull this block sits on is currently welded in place. */
    public static final BooleanProperty LINKED = BooleanProperty.create("linked");

    public AnchorBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(3.0F, 9.0F)
                .sound(SoundType.NETHERITE_BLOCK)
                .lightLevel(state -> state.getValue(LINKED) ? 6 : 0));
        this.registerDefaultState(this.stateDefinition.any().setValue(LINKED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LINKED);
    }
}
