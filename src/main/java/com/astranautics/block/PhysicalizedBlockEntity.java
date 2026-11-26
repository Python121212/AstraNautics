package com.astranautics.block;

import com.astranautics.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 物理化されたブロックの情報を保持するブロックエンティティ。
 *
 * <p>元ブロックの {@link BlockState} を NBT に保存し、
 * クライアント側レンダラーがそれを読み取って描画します。</p>
 */
public class PhysicalizedBlockEntity extends BlockEntity {

    private static final String TAG_ORIGINAL = "OriginalState";

    /** クライアント側のアニメーション用：出現したゲーム時間。 */
    public long spawnTime = -1L;
    /** クライアント側のアニメーション用：経過 tick（partialTick はレンダラー側で加算）。 */
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
        if (this.cachedOriginal != null) {
            return this.cachedOriginal;
        }
        if (this.originalStateTag.isEmpty()) {
            return Blocks.STONE.defaultBlockState();
        }
        try {
            if (this.level != null) {
                this.cachedOriginal = NbtUtils.readBlockState(
                        this.level.registryAccess().registryOrThrow(Registries.BLOCK).asLookup(),
                        this.originalStateTag);
            } else {
                this.cachedOriginal = NbtUtils.readBlockState(
                        BuiltInRegistries.BLOCK.asLookup(), this.originalStateTag);
            }
        } catch (Exception exception) {
            this.cachedOriginal = Blocks.STONE.defaultBlockState();
        }
        return this.cachedOriginal;
    }

    /** アイテム（BlockEntityTag）から復元する。 */
    public void loadFromItem(CompoundTag tag) {
        if (tag.contains(TAG_ORIGINAL)) {
            this.originalStateTag = tag.getCompound(TAG_ORIGINAL);
            this.cachedOriginal = null;
            setChanged();
        }
    }

    /* ------------------------------------------------------------------ */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!this.originalStateTag.isEmpty()) {
            tag.put(TAG_ORIGINAL, this.originalStateTag);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_ORIGINAL)) {
            this.originalStateTag = tag.getCompound(TAG_ORIGINAL);
            this.cachedOriginal = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /** クライアント側で毎 tick 呼ばれるアニメーション用の更新。 */
    public static void clientTick(Level level, BlockPos pos, BlockState state, PhysicalizedBlockEntity be) {
        if (!level.isClientSide) {
            return;
        }
        if (be.spawnTime < 0L) {
            be.spawnTime = level.getGameTime();
        }
        be.age = (float) (level.getGameTime() - be.spawnTime);
    }
}
