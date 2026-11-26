package com.astranautics.block;

import com.astranautics.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 物理化されたブロックの情報を保持するブロックエンティティ。
 *
 * <p>元ブロックの {@link BlockState} を NBT に保存し、
 * クライアント側レンダラーがそれを読み取って描画します。</p>
 */
public class PhysicalizedBlockEntity extends BlockEntity {

    /** クライアント側のアニメーション用：出現してからの経過ゲーム時間。 */
    public long spawnTime = -1L;
    /** クライアント側のアニメーション用：直近の経過時間（tick）。 */
    public float age;

    private CompoundTag originalStateTag = new CompoundTag();
    private BlockState cachedOriginal;

    public PhysicalizedBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHYSICALIZED.get(), pos, state);
    }

    /* ------------------------------------------------------------------ */

    public void setOriginalState(BlockState state) {
        this.originalStateTag = NbtUtils.writeBlockState(state);
        this.cachedOriginal = state;
        setChanged();
    }

    public BlockState getOriginalState() {
        if (cachedOriginal == null) {
            if (originalStateTag.isEmpty()) {
                return Blocks.STONE.defaultBlockState();
            }
            cachedOriginal = NbtUtils.readBlockState(
                    level != null ? level.holderLookup(net.minecraft.core.registries.Registries.BLOCK)
                            : net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),
                    originalStateTag);
        }
        return cachedOriginal;
    }

    /** アイテム（BlockEntityTag）から復元する。 */
    public void loadFromItem(CompoundTag tag) {
        if (tag.contains("OriginalState")) {
            this.originalStateTag = tag.getCompound("OriginalState");
            this.cachedOriginal = null;
            setChanged();
        }
    }

    /* ------------------------------------------------------------------ */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!originalStateTag.isEmpty()) {
            tag.put("OriginalState", originalStateTag);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("OriginalState")) {
            this.originalStateTag = tag.getCompound("OriginalState");
            this.cachedOriginal = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /** クライアント側で毎フレーム呼ばれる簡易アニメーション更新。 */
    public static void clientTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                                  PhysicalizedBlockEntity be) {
        if (!level.isClientSide) {
            return;
        }
        if (be.spawnTime < 0L) {
            be.spawnTime = level.getGameTime();
        }
        be.age = level.getGameTime() - be.spawnTime + level.getPartialTick();
    }

    /** 水中・空中でも自然に動き続けるようにする。 */
    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                                  PhysicalizedBlockEntity be) {
        // 見た目のみの実装なので、サーバー側ではフェーズ（回転開始角度）だけを保つ
        if (state.hasProperty(PhysicalizedBlock.PHASE) && state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            // 未使用（将来拡張用）
        }
    }
}
