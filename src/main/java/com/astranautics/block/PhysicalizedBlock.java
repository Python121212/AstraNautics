package com.astranautics.block;

import com.astranautics.registry.ModBlocks;
import com.astranautics.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * 「物理化」されたブロック。
 *
 * <p>見た目は元のブロックのままですが、ブロックエンティティとして保持され、
 * クライアント側レンダラー（{@code PhysicalizedRenderer}）で浮遊・回転アニメーションが付きます。
 * 元ブロックの種類と向きは NBT（{@link PhysicalizedBlockEntity}）に保存されます。</p>
 */
public class PhysicalizedBlock extends BaseEntityBlock {

    /** アニメーションのフェーズを同期するためのプロパティ（0〜7）。 */
    public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 7);
    /** 見た目の向き。 */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public PhysicalizedBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PHASE, 0)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE, FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 描画は BER（BlockEntityRenderer）側で行う
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhysicalizedBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide) {
            return createTickerHelper(type, com.astranautics.registry.ModBlockEntities.PHYSICALIZED.get(),
                    PhysicalizedBlockEntity::clientTick);
        }
        return null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @SuppressWarnings("deprecation")
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.PHYSICALIZER.get());
    }

    /* ------------------------------------------------------------------ */
    /* 物理化 / 復元                                                        */
    /* ------------------------------------------------------------------ */

    /**
     * 対象ブロックを物理化されたブロックへ置き換える。
     *
     * @return 置き換えに成功したら true
     */
    public static boolean physicalize(Level level, BlockPos pos) {
        BlockState target = level.getBlockState(pos);
        if (target.isAir() || target.is(ModBlocks.PHYSICALIZED.get())
                || target.is(Blocks.BEDROCK) || target.getBlock() instanceof BaseEntityBlock) {
            return false;
        }

        if (level.isClientSide) {
            return true;
        }

        BlockState physicalized = ModBlocks.PHYSICALIZED.get().defaultBlockState()
                .setValue(PHASE, level.random.nextInt(8));

        // 元ブロックの情報を先に取り出す
        level.setBlock(pos, physicalized, Block.UPDATE_ALL);

        if (level.getBlockEntity(pos) instanceof PhysicalizedBlockEntity be) {
            be.setOriginalState(target);
            be.setChanged();
        }
        return true;
    }

    /** 物理化を解除して元のブロックへ戻す。 */
    public static boolean restore(Level level, BlockPos pos) {
        if (!(level.getBlockState(pos).is(ModBlocks.PHYSICALIZED.get()))) {
            return false;
        }
        BlockState original = Blocks.STONE.defaultBlockState();
        if (level.getBlockEntity(pos) instanceof PhysicalizedBlockEntity be) {
            original = be.getOriginalState();
        }
        if (original.isAir()) {
            original = Blocks.STONE.defaultBlockState();
        }
        return level.setBlock(pos, original, Block.UPDATE_ALL);
    }

    /* ------------------------------------------------------------------ */
    /* 保存・読み込み                                                       */
    /* ------------------------------------------------------------------ */

    /** 設置時に BlockEntityTag から元ブロック情報を復元する（物理化ブロックをアイテムとして持ち運んだ場合）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        if (tag != null && level.getBlockEntity(pos) instanceof PhysicalizedBlockEntity be) {
            be.loadFromItem(tag);
        }
    }
}
