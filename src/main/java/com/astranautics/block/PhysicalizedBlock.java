package com.astranautics.block;

import com.astranautics.registry.ModBlocks;
import com.astranautics.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 「物理化」されたブロック。レバーのように ON / OFF する。
 *
 * <ul>
 *   <li><b>レバー ON（上）</b>: 物理演算が有効になり、浮遊 → 落下。姿勢は回転せず保持。</li>
 *   <li><b>レバー OFF（下）</b>: 物理演算が無効になり、元の位置に固定表示。</li>
 *   <li>空の位置に物理化装置を使うと、その場に空気ブロックが物理化されて出現する。</li>
 * </ul>
 */
public class PhysicalizedBlock extends BaseEntityBlock {

    /** 物理演算 ON / OFF（レバーの上下）。 */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    /** 元ブロックが空気（空中設置）かどうか。 */
    public static final BooleanProperty AIRBORNE = BooleanProperty.create("airborne");
    /** 見た目の向き。 */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public PhysicalizedBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(POWERED, Boolean.TRUE)
                .setValue(AIRBORNE, Boolean.FALSE)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, AIRBORNE, FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 描画は BlockEntityRenderer（PhysicalizedRenderer）が行う
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
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return createTickerHelper(type, com.astranautics.registry.ModBlockEntities.PHYSICALIZED.get(),
                    PhysicalizedBlockEntity::clientTick);
        }
        return createTickerHelper(type, com.astranautics.registry.ModBlockEntities.PHYSICALIZED.get(),
                PhysicalizedBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, Boolean.TRUE);
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
    /* レバー操作                                                           */
    /* ------------------------------------------------------------------ */

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof PhysicalizedBlockEntity be) {
            boolean enabled;
            if (!level.isClientSide) {
                be.togglePhysics();
                enabled = be.isPhysicsEnabled();
                level.setBlock(pos, state.setValue(POWERED, enabled), Block.UPDATE_CLIENTS);
                // レバーのカチッとした音
                level.playSound(null, pos,
                        enabled ? SoundEvents.LEVER_CLICK : SoundEvents.LEVER_CLICK,
                        SoundSource.BLOCKS, 0.6F, enabled ? 1.2F : 0.8F);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(enabled
                                        ? "message.astranautics.lever_on"
                                        : "message.astranautics.lever_off")
                                .withStyle(enabled ? net.minecraft.ChatFormatting.AQUA
                                        : net.minecraft.ChatFormatting.GRAY), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /* ------------------------------------------------------------------ */
    /* 物理化 / 復元                                                        */
    /* ------------------------------------------------------------------ */

    /**
     * 対象位置を物理化する。
     * 対象が空気なら「空中にブロック化」して物理化する。
     *
     * @return 成功したら true
     */
    public static boolean physicalize(Level level, BlockPos pos) {
        BlockState target = level.getBlockState(pos);
        boolean airborne = target.isAir();
        if (target.is(ModBlocks.PHYSICALIZED.get()) || target.is(Blocks.BEDROCK)) {
            return false;
        }
        if (!airborne && target.getBlock() instanceof BaseEntityBlock) {
            // 既に BlockEntity を持つブロックは中身を失うため対象外
            return false;
        }

        if (level.isClientSide) {
            return true;
        }

        BlockState physicalized = ModBlocks.PHYSICALIZED.get().defaultBlockState()
                .setValue(POWERED, Boolean.TRUE)
                .setValue(AIRBORNE, airborne);

        level.setBlock(pos, physicalized, Block.UPDATE_ALL);

        if (level.getBlockEntity(pos) instanceof PhysicalizedBlockEntity be) {
            // 空中で使った場合は「物理コア」ブロックとして空中にブロック化する
            BlockState original = airborne ? com.astranautics.registry.ModBlocks.PHYSICS_CORE.get()
                    .defaultBlockState() : target;
            be.setOriginalState(original);
            be.enablePhysics();
            be.setChanged();
        }

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(airborne ? ParticleTypes.CLOUD : ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    14, 0.3, 0.3, 0.3, 0.02);
        }
        return true;
    }

    /** 物理化を解除して元のブロックへ戻す。 */
    public static boolean restore(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModBlocks.PHYSICALIZED.get())) {
            return false;
        }
        BlockState original = Blocks.AIR.defaultBlockState();
        if (level.getBlockEntity(pos) instanceof PhysicalizedBlockEntity be) {
            original = be.getOriginalState();
        }
        return level.setBlock(pos, original, Block.UPDATE_ALL);
    }

    /* ------------------------------------------------------------------ */
    /* 設置                                                                 */
    /* ------------------------------------------------------------------ */

    /** 設置時に BlockEntityTag から元ブロック情報を復元する（持ち運んだ場合）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        if (tag != null && level.getBlockEntity(pos) instanceof PhysicalizedBlockEntity be) {
            be.loadFromItem(tag);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof PhysicalizedBlockEntity be) {
            // 物理演算中でもドロップはアイテム側のルートテーブルで処理される
            be.setChanged();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
