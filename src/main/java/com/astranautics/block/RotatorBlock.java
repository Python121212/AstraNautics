package com.astranautics.block;

import com.astranautics.physics.Rotators;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;

/**
 * Turns the whole hull to an angle chosen in its dial.
 *
 * <p>Two of them, because there are two obvious ways a player wants to turn a ship:</p>
 * <ul>
 *     <li>{@link Rotators.Mode#YAW} - around the world's vertical axis (a turntable)</li>
 *     <li>{@link Rotators.Mode#PITCH} - around the world's east-west axis (a full flip, end over end)</li>
 * </ul>
 *
 * <p>The chosen angle lives in the block state, so it travels with the hull's synced structure
 * and the client can draw the dial without asking the server anything. The rotation itself
 * runs on the server and only while the block receives a redstone signal - the hull is an
 * entity, so its blocks cannot be powered as blocks; the signal is read from the world position
 * the block would be at, which is why a lever or a wire has to touch the outside of the hull
 * where the rotator sits.</p>
 */
public class RotatorBlock extends Block {
    /** Angle steps of 15 degrees: 24 positions, and a dial you can actually click. */
    public static final int STEPS = 24;
    public static final int DEGREES_PER_STEP = 360 / STEPS;
    /** 0..23, the chosen angle divided by 15. */
    public static final IntegerProperty ANGLE = IntegerProperty.create("angle", 0, STEPS - 1);
    /** Lit while the block sees a redstone signal (and is therefore turning). */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    private final Rotators.Mode mode;

    public RotatorBlock(Rotators.Mode mode) {
        super(BlockBehaviour.Properties.of()
                .mapColor(mode == Rotators.Mode.YAW ? MapColor.COLOR_LIGHT_BLUE : MapColor.COLOR_ORANGE)
                .strength(2.5F, 7.0F)
                .sound(SoundType.METAL));
        this.mode = mode;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(ANGLE, 0)
                .setValue(POWERED, false));
    }

    public Rotators.Mode mode() {
        return this.mode;
    }

    public static int degrees(BlockState state) {
        return state.getValue(ANGLE) * DEGREES_PER_STEP;
    }

    public static int stepFor(double degrees) {
        int step = (int) Math.round(((degrees % 360.0D + 360.0D) % 360.0D) / DEGREES_PER_STEP);
        return Math.floorMod(step, STEPS);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ANGLE, POWERED);
    }
}
