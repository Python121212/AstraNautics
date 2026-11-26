package com.astranautics.block;

import com.astranautics.physics.LightPhysicsEngine;
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
 * <p>「レバーを上げる＝物理演算 ON / 下げる＝OFF」に対応し、
 * 物理演算は独自の超軽量エンジン {@link LightPhysicsEngine} が担当します。
 * ブロックのグリッド位置は動かさず、描画オフセットだけを動かすのが軽量の鍵です。</p>
 */
public class PhysicalizedBlockEntity extends BlockEntity {

    private static final String TAG_ORIGINAL = "OriginalState";
    private static final String TAG_PHYSICS = "PhysicsOn";
    private static final String TAG_OFFSET = "Offset";
    private static final String TAG_VELOCITY = "Velocity";
    private static final String TAG_SLEEP = "SleepTicks";

    /** クライアント側のアニメーション用：出現したゲーム時間。 */
    public long spawnTime = -1L;
    /** クライアント側のアニメーション用：経過 tick。 */
    public float age;

    /** クライアント側の補間用：前 tick のオフセット。 */
    public double prevOffX;
    public double prevOffY;
    public double prevOffZ;

    private CompoundTag originalStateTag = new CompoundTag();
    private BlockState cachedOriginal;

    /** 物理演算が有効か（レバーが上か）。 */
    private boolean physicsEnabled;
    /** グリッドからの描画オフセット。 */
    private double offX;
    private double offY;
    private double offZ;
    /** 速度（ブロック/tick）。 */
    private double velX;
    private double velY;
    private double velZ;
    /** 静止判定用カウンタ。 */
    private int sleepTicks;

    /** 同期用ハッシュ（変化検出）。 */
    private int lastSyncHash;

    public PhysicalizedBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHYSICALIZED.get(), pos, state);
    }

    /* ------------------------------------------------------------------ */
    /* 元ブロック情報                                                       */
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

    public void loadFromItem(CompoundTag tag) {
        if (tag.contains(TAG_ORIGINAL)) {
            this.originalStateTag = tag.getCompound(TAG_ORIGINAL);
            this.cachedOriginal = null;
            setChanged();
        }
    }

    /* ------------------------------------------------------------------ */
    /* 物理演算 ON / OFF                                                    */
    /* ------------------------------------------------------------------ */

    /** 物理演算を ON（レバー上）にする。浮遊 → 落下を開始する。 */
    public void enablePhysics() {
        this.physicsEnabled = true;
        this.sleepTicks = 0;
        // わずかに浮いた状態から始めて「物理化した」感を出す
        this.offY = Math.max(this.offY, 0.02D);
        // 置いた場所が既に埋まっていても押し出されないよう、少しだけ持ち上げる
        this.velY = 0.0D;
        setChanged();
        syncNow();
    }

    /** 物理演算を OFF（レバー下）にする。元のグリッド位置へ戻す。 */
    public void disablePhysics() {
        this.physicsEnabled = false;
        this.offX = 0.0D;
        this.offY = 0.0D;
        this.offZ = 0.0D;
        this.velX = this.velY = this.velZ = 0.0D;
        this.sleepTicks = 0;
        setChanged();
        syncNow();
    }

    public boolean isPhysicsEnabled() {
        return this.physicsEnabled;
    }

    /** レバー操作（ON / OFF トグル）。 */
    public void togglePhysics() {
        if (this.physicsEnabled) {
            disablePhysics();
        } else {
            enablePhysics();
        }
    }

    /** 物理演算の再開（叩いたときなど）。 */
    public void wake() {
        this.sleepTicks = 0;
        if (this.physicsEnabled) {
            setChanged();
        }
    }

    /* ------------------------------------------------------------------ */
    /* 物理パラメータ                                                       */
    /* ------------------------------------------------------------------ */

    public void addOffset(double dx, double dy, double dz) {
        this.offX += dx;
        this.offY += dy;
        this.offZ += dz;
    }

    public void addImpulse(double ix, double iy, double iz) {
        this.velX += ix;
        this.velY += iy;
        this.velZ += iz;
        this.sleepTicks = 0;
        setChanged();
    }

    public double getOffX() {
        return this.offX;
    }

    public double getOffY() {
        return this.offY;
    }

    public double getOffZ() {
        return this.offZ;
    }

    public double getVelX() {
        return this.velX;
    }

    public double getVelY() {
        return this.velY;
    }

    public double getVelZ() {
        return this.velZ;
    }

    public void setVelocity(double vx, double vy, double vz) {
        this.velX = vx;
        this.velY = vy;
        this.velZ = vz;
    }

    public int getSleepTicks() {
        return this.sleepTicks;
    }

    public void setSleepTicks(int ticks) {
        this.sleepTicks = ticks;
    }

    /** サーバーから受け取ったオフセットを適用する（クライアント）。 */
    public void applyServerOffsets(double x, double y, double z, boolean powered) {
        this.offX = x;
        this.offY = y;
        this.offZ = z;
        this.physicsEnabled = powered;
    }

    /** 補間用に前 tick のオフセットを記録する（クライアント）。 */
    public void capturePreviousOffset() {
        this.prevOffX = this.offX;
        this.prevOffY = this.offY;
        this.prevOffZ = this.offZ;
    }

    /* ------------------------------------------------------------------ */
    /* 同期                                                                 */
    /* ------------------------------------------------------------------ */

    private int computeSyncHash() {
        int hash = 1;
        hash = 31 * hash + Double.hashCode(Math.round(this.offX * 512.0D) / 512.0D);
        hash = 31 * hash + Double.hashCode(Math.round(this.offY * 512.0D) / 512.0D);
        hash = 31 * hash + Double.hashCode(Math.round(this.offZ * 512.0D) / 512.0D);
        hash = 31 * hash + (this.physicsEnabled ? 1 : 0);
        return hash;
    }

    /** オフセットが目に見えて変化していれば、近くのプレイヤーだけに軽量パケットで送る。 */
    public void syncNow() {
        int hash = computeSyncHash();
        if (hash == this.lastSyncHash) {
            return;
        }
        this.lastSyncHash = hash;
        if (this.level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.astranautics.network.ModNetwork.sendToNearby(serverLevel, this.worldPosition,
                    com.astranautics.network.SyncPhysicsPacket.of(this.worldPosition, this));
        }
    }

    /* ------------------------------------------------------------------ */
    /* tick                                                                 */
    /* ------------------------------------------------------------------ */

    /** サーバー側：軽量物理エンジンを回す。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, PhysicalizedBlockEntity be) {
        if (level.isClientSide || !be.physicsEnabled) {
            return;
        }
        if (be.sleepTicks >= LightPhysicsEngine.SLEEP_TICKS) {
            return;
        }
        LightPhysicsEngine.tick(level, pos, be);
        be.syncNow();
    }

    /** クライアント側：補間用の記録と簡易アニメーション。 */
    public static void clientTick(Level level, BlockPos pos, BlockState state, PhysicalizedBlockEntity be) {
        if (!level.isClientSide) {
            return;
        }
        be.capturePreviousOffset();
        if (be.spawnTime < 0L) {
            be.spawnTime = level.getGameTime();
        }
        be.age = (float) (level.getGameTime() - be.spawnTime);
    }

    /* ------------------------------------------------------------------ */
    /* NBT                                                                  */
    /* ------------------------------------------------------------------ */

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!this.originalStateTag.isEmpty()) {
            tag.put(TAG_ORIGINAL, this.originalStateTag);
        }
        tag.putBoolean(TAG_PHYSICS, this.physicsEnabled);
        tag.putDoubleArray(TAG_OFFSET, new double[]{this.offX, this.offY, this.offZ});
        tag.putDoubleArray(TAG_VELOCITY, new double[]{this.velX, this.velY, this.velZ});
        tag.putInt(TAG_SLEEP, this.sleepTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_ORIGINAL)) {
            this.originalStateTag = tag.getCompound(TAG_ORIGINAL);
            this.cachedOriginal = null;
        }
        this.physicsEnabled = tag.getBoolean(TAG_PHYSICS);
        double[] offset = tag.getDoubleArray(TAG_OFFSET);
        if (offset.length == 3) {
            this.offX = offset[0];
            this.offY = offset[1];
            this.offZ = offset[2];
        }
        double[] velocity = tag.getDoubleArray(TAG_VELOCITY);
        if (velocity.length == 3) {
            this.velX = velocity[0];
            this.velY = velocity[1];
            this.velZ = velocity[2];
        }
        this.sleepTicks = tag.getInt(TAG_SLEEP);
        this.lastSyncHash = computeSyncHash();
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
