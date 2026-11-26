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
        // 流体・特殊ブロックは対象外（それ以外の全ブロックに対応）
        if (!airborne && isUnsupported(target)) {
            return false;
        }
        // 他 MOD の特殊ブロックはコンフィグで除外できる
        if (!airborne && com.astranautics.physics.Compat.isBlocked(target)) {
            return false;
        }

        if (level.isClientSide) {
            return true;
        }

        // 全ブロック対応：BlockEntity を持つブロック（チェスト・かまど等）も中身ごと物理化する
        CompoundTag originalBlockEntityTag = null;
        if (!airborne && level.getBlockEntity(pos) != null
                && !(level.getBlockEntity(pos) instanceof PhysicalizedBlockEntity)) {
            try {
                BlockEntity existing = level.getBlockEntity(pos);
                CompoundTag data = existing.saveWithoutMetadata();
                if (!data.isEmpty()) {
                    originalBlockEntityTag = data;
                }
            } catch (Exception exception) {
                com.astranautics.AstraNautics.LOGGER.debug(
                        "[AstraNautics] BlockEntity の退避に失敗（中身は失われます）", exception);
            }
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
            if (originalBlockEntityTag != null) {
                be.setOriginalBlockEntityTag(originalBlockEntityTag);
            }
            be.enablePhysics();
            be.setChanged();
            // 連結クラスタ（将来の「船 = 1 剛体」）を再構築
            com.astranautics.physics.ClusterManager.rebuild(level, pos);
        }

        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(airborne ? ParticleTypes.CLOUD : ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    14, 0.3, 0.3, 0.3, 0.02);
        }
        return true;
    }

    /** 物理化できない特殊ブロックかどうか。 */
    public static boolean isUnsupported(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return true;
        }
        return state.is(Blocks.BEDROCK) || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_GATEWAY) || state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.END_PORTAL_FRAME) || state.is(Blocks.BARRIER)
                || state.is(Blocks.COMMAND_BLOCK) || state.is(Blocks.CHAIN_COMMAND_BLOCK)
                || state.is(Blocks.REPEATING_COMMAND_BLOCK) || state.is(Blocks.STRUCTURE_BLOCK)
                || state.is(Blocks.STRUCTURE_VOID) || state.is(Blocks.JIGSAW)
                || state.is(Blocks.LIGHT) || state.is(Blocks.MOVING_PISTON)
                || state.is(com.astranautics.registry.ModBlocks.PHYSICS_CORE.get());
    }

    /** 物理化を解除して元のブロックへ戻す。 */
    public static boolean restore(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModBlocks.PHYSICALIZED.get())) {
            return false;
        }
        BlockState original = Blocks.AIR.defaultBlockState();
        CompoundTag blockEntityTag = null;
        if (level.getBlockEntity(pos) instanceof PhysicalizedBlockEntity be) {
            original = be.getOriginalState();
            blockEntityTag = be.getOriginalBlockEntityTag();
        }
        boolean placed = level.setBlock(pos, original, Block.UPDATE_ALL);
        // BlockEntity の中身（チェストのアイテム等）を書き戻す
        if (placed && blockEntityTag != null && level.getBlockEntity(pos) != null
                && !(level.getBlockEntity(pos) instanceof PhysicalizedBlockEntity)) {
            try {
                CompoundTag data = blockEntityTag.copy();
                data.remove("x");
                data.remove("y");
                data.remove("z");
                data.remove("id");
                level.getBlockEntity(pos).load(data);
                level.getBlockEntity(pos).setChanged();
            } catch (Exception exception) {
                com.astranautics.AstraNautics.LOGGER.debug(
                        "[AstraNautics] BlockEntity の復元に失敗", exception);
            }
        }
        return placed;
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
        if (!state.is(newState.getBlock())) {
            // クラスタ登録を解除し、隣接クラスタを再構築する
            com.astranautics.physics.ClusterManager.unregister(level, pos);
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                if (level.getBlockEntity(neighbor) instanceof PhysicalizedBlockEntity) {
                    com.astranautics.physics.ClusterManager.rebuild(level, neighbor);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
