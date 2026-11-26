package com.astranautics.block;

import com.astranautics.engine.EnginePart;
import com.astranautics.engine.NozzleShape;
import com.astranautics.engine.NozzleSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * ノズルブロック。スラスターの周りに置いて噴射口を作ります。
 *
 * <p><b>形も大きさも自分で選べます。</b></p>
 * <ul>
 *   <li>素手で右クリック … 形（ベル／コーン／エアロスパイク／斜め など）を切替</li>
 *   <li>スニーク＋右クリック … 大きさ（縦ハーフ／標準／2×2／超でかい）を切替</li>
 *   <li>それ以外（素手以外）で右クリック … エンジン性能を表示</li>
 * </ul>
 *
 * <p>形と大きさで推力・最高速度・発熱・質量が変わり、スラスターに接していると
 * 「装着ノズル」としてボーナスが付きます。</p>
 */
public class NozzleBlock extends EnginePartBlock {

    /** ノズルの形。 */
    public static final EnumProperty<NozzleShape> SHAPE =
            EnumProperty.create("shape", NozzleShape.class);
    /** ノズルの大きさ。 */
    public static final EnumProperty<NozzleSize> SIZE =
            EnumProperty.create("size", NozzleSize.class);
    /** 噴射方向（上下＋水平）。 */
    public static final DirectionProperty FACING = DirectionProperty.create("facing",
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
            Direction.UP, Direction.DOWN);

    private static final VoxelShape SHAPE_HALF = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    /** 横ハーフ：幅・奥行きが半分（当たり判定は外形を維持。2 基並べても干渉しない）。 */
    private static final VoxelShape SHAPE_HALF_WIDE =
            Block.box(0.0D, 0.0D, 0.0D, 8.0D, 16.0D, 8.0D);
    private static final VoxelShape SHAPE_FULL = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public NozzleBlock(Properties properties) {
        super(EnginePart.NOZZLE, properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(SHAPE, NozzleShape.BELL)
                .setValue(SIZE, NozzleSize.NORMAL)
                .setValue(FACING, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SHAPE, SIZE, FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection();
        NozzleShape shape = NozzleShape.BELL;
        NozzleSize size = NozzleSize.NORMAL;
        ItemStack stack = context.getItemInHand();
        var tag = stack.getTag();
        if (tag != null && tag.contains("NozzleShape")) {
            shape = NozzleShape.byId(tag.getString("NozzleShape"));
        }
        if (tag != null && tag.contains("NozzleSize")) {
            size = NozzleSize.byId(tag.getString("NozzleSize"));
        }
        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(SHAPE, shape)
                .setValue(SIZE, size);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                               CollisionContext context) {
        // 縦ハーフは下半分、横ハーフは幅・奥行き半分。大きなノズルは通常の立方体。
        NozzleSize size = state.getValue(SIZE);
        if (size == NozzleSize.HALF) {
            return SHAPE_HALF;
        }
        if (size == NozzleSize.HALF_WIDE) {
            return SHAPE_HALF_WIDE;
        }
        return SHAPE_FULL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            if (player.isShiftKeyDown()) {
                // 大きさを切り替え
                NozzleSize next = state.getValue(SIZE).next();
                level.setBlock(pos, state.setValue(SIZE, next), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS,
                        0.5F, 0.8F + next.ordinal() * 0.25F);
                player.displayClientMessage(Component.translatable(
                                "message.astranautics.nozzle_size",
                                Component.translatable(
                                        "block.astranautics.nozzle.size." + next.id))
                        .withStyle(net.minecraft.ChatFormatting.GOLD), true);
                player.displayClientMessage(Component.translatable(next.descriptionKey)
                        .withStyle(net.minecraft.ChatFormatting.GRAY), false);
                return InteractionResult.CONSUME;
            }
            // 形を切り替え
            NozzleShape next = state.getValue(SHAPE).next();
            level.setBlock(pos, state.setValue(SHAPE, next), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                    0.6F, next == NozzleShape.CONE ? 1.6F : 1.1F);
            player.displayClientMessage(Component.translatable(
                            "message.astranautics.nozzle_shape",
                            Component.translatable("block.astranautics.nozzle." + next.id))
                    .withStyle(net.minecraft.ChatFormatting.AQUA), true);
            player.displayClientMessage(Component.translatable(next.descriptionKey)
                    .withStyle(net.minecraft.ChatFormatting.GRAY), false);
            return InteractionResult.CONSUME;
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level,
                                java.util.List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.astranautics.nozzle.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.astranautics.nozzle.size_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
