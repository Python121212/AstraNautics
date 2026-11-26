package com.astranautics.block;

import com.astranautics.physics.LightPhysicsEngine;
import com.astranautics.physics.MaterialProfile;
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
    private static final String TAG_ORIGINAL_BE = "OriginalBlockEntity";
    /** 移設時に中身（チェストのアイテム等）を引き継ぐためのタグ。 */
    private CompoundTag originalBlockEntityTag;

    /** クライアント側のアニメーション用：出現したゲーム時間。 */
    public long spawnTime = -1L;
    /** クライアント側のアニメーション用：経過 tick。 */
    public float age;
    /** クライアント側：レバーの傾き（0=立ち上がり, 80=横倒し）。描画時に補間される。 */
    public float leverTilt;

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
    /** 素材別物性（キャッシュ）。 */
    private MaterialProfile material;
    /** レッドストーンで ON にされたか（信号が切れたら OFF に戻す）。 */
    private boolean heldByRedstone;
    /** tick ごとの同期が必要か（インパルスを受けたとき）。 */
    public boolean dirty;

    public PhysicalizedBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PHYSICALIZED.get(), pos, state);
    }

    /* ------------------------------------------------------------------ */
    /* 元ブロック情報                                                       */
    /* ------------------------------------------------------------------ */

    public void setOriginalState(BlockState state) {
        this.originalStateTag = NbtUtils.writeBlockState(state);
        this.cachedOriginal = state;
        this.material = null;   // 素材が変わったら物性を再計算
        setChanged();
    }

    /** 元ブロックの BlockEntity データ（チェストの中身など）。無ければ null。 */
    public CompoundTag getOriginalBlockEntityTag() {
        return this.originalBlockEntityTag;
    }

    public void setOriginalBlockEntityTag(CompoundTag tag) {
        this.originalBlockEntityTag = tag == null || tag.isEmpty() ? null : tag.copy();
        this.material = null;
        setChanged();
    }

    /** このセルが属するクラスタ（未登録なら null）。 */
    public com.astranautics.physics.PhysicsCluster getCluster() {
        return this.level == null ? null
                : com.astranautics.physics.ClusterManager.at(this.level, this.worldPosition);
    }

    /** 素材別の物性値（キャッシュ付き）。 */
    public MaterialProfile getMaterial() {
        if (this.material == null) {
            this.material = MaterialProfile.of(getOriginalState());
        }
        return this.material;
    }

    /** レッドストーンで ON にされたか。 */
    public boolean isPhysicsHeldByRedstone() {
        return this.heldByRedstone;
    }

    public void setPhysicsHeldByRedstone(boolean held) {
        this.heldByRedstone = held;
    }

    /** 移設時（長距離移動）に物理状態を引き継ぐ。 */
    public void setPhysicsEnabledFromTransfer(boolean enabled) {
        this.physicsEnabled = enabled;
        this.sleepTicks = 0;
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
        if (tag.contains(TAG_ORIGINAL_BE)) {
            this.originalBlockEntityTag = tag.getCompound(TAG_ORIGINAL_BE).copy();
        }
    }

    /** 持ち運び（アイテム）用に、元ブロック情報をまとめて書き出す。 */
    public void saveToItem(CompoundTag tag) {
        if (!this.originalStateTag.isEmpty()) {
            tag.put(TAG_ORIGINAL, this.originalStateTag);
        }
        if (this.originalBlockEntityTag != null) {
            tag.put(TAG_ORIGINAL_BE, this.originalBlockEntityTag.copy());
        }
    }

    /* ------------------------------------------------------------------ */
    /* 物理演算 ON / OFF                                                    */
    /* ------------------------------------------------------------------ */

    /** 物理演算を ON（レバー上）にする。浮遊 → 落下を開始する。 */
    public void enablePhysics() {
        this.physicsEnabled = true;
        this.sleepTicks = 0;
        if (this.level != null) {
            com.astranautics.physics.ClusterManager.rebuild(this.level, this.worldPosition);
        }
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
        this.heldByRedstone = false;
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
        // 重いものは動きにくい（質量でスケール）
        double inv = 1.0D / Math.max(0.2D, getMaterial().mass());
        this.velX += ix * inv;
        this.velY += iy * inv;
        this.velZ += iz * inv;
        this.sleepTicks = 0;
        this.dirty = true;
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

    /** サーバー側：軽量物理エンジンを回す（外力・プレイヤー・クラスタも含む）。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, PhysicalizedBlockEntity be) {
        if (level.isClientSide || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }

        // レッドストーン／水流などはスリープ中でも判定する（軽い処理）
        if (level.getGameTime() % 5L == 0L) {
            com.astranautics.physics.PhysicsRuntime.applyEnvironment(serverLevel, be);
        }

        if (!be.physicsEnabled) {
            return;
        }

        // 止まっていても、プレイヤーが触れば動き出す
        com.astranautics.physics.PhysicsRuntime.playerInteraction(serverLevel, be);

        if (be.sleepTicks >= LightPhysicsEngine.SLEEP_TICKS && !be.dirty) {
            return;
        }
        be.dirty = false;

        // 排水量ベースの浮力（石の桶でも中が空洞なら浮く）はクラスタ単位で計算
        if (be.getCluster() != null && com.astranautics.physics.Compat.buoyancyEnabled()) {
            com.astranautics.physics.Buoyancy.apply(level, be.getCluster());
        }

        LightPhysicsEngine.tick(level, pos, be);

        // クラスタ単位の押し出し（重なり解消）
        com.astranautics.physics.PhysicsCluster cluster =
                com.astranautics.physics.ClusterManager.at(level, pos);
        if (cluster != null && cluster.size() > 1 && !cluster.isSleeping()) {
            com.astranautics.physics.PhysicsRuntime.resolveClusterOverlap(serverLevel, cluster);
        }

        // 長距離移動したらグリッド位置を補正（将来の宇宙船移動の布石）
        if (level.getGameTime() % 20L == 0L) {
            com.astranautics.physics.PhysicsRuntime.normalizeLongOffsets(serverLevel, be);
        }

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
        tag.put(TAG_OFFSET, doubles(this.offX, this.offY, this.offZ));
        tag.put(TAG_VELOCITY, doubles(this.velX, this.velY, this.velZ));
        tag.putInt(TAG_SLEEP, this.sleepTicks);
        if (this.originalBlockEntityTag != null) {
            tag.put(TAG_ORIGINAL_BE, this.originalBlockEntityTag.copy());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_ORIGINAL)) {
            this.originalStateTag = tag.getCompound(TAG_ORIGINAL);
            this.cachedOriginal = null;
        }
        this.physicsEnabled = tag.getBoolean(TAG_PHYSICS);
        double[] offset = toDoubles(tag.getList(TAG_OFFSET, net.minecraft.nbt.Tag.TAG_DOUBLE));
        if (offset.length == 3) {
            this.offX = offset[0];
            this.offY = offset[1];
            this.offZ = offset[2];
        }
        double[] velocity = toDoubles(tag.getList(TAG_VELOCITY, net.minecraft.nbt.Tag.TAG_DOUBLE));
        if (velocity.length == 3) {
            this.velX = velocity[0];
            this.velY = velocity[1];
            this.velZ = velocity[2];
        }
        this.sleepTicks = tag.getInt(TAG_SLEEP);
        if (tag.contains(TAG_ORIGINAL_BE)) {
            this.originalBlockEntityTag = tag.getCompound(TAG_ORIGINAL_BE).copy();
        }
        this.lastSyncHash = computeSyncHash();
    }

    /** 3 つの double を ListTag に変換（CompoundTag に double 配列 API が無いため）。 */
    private static net.minecraft.nbt.ListTag doubles(double a, double b, double c) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(net.minecraft.nbt.DoubleTag.valueOf(a));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(b));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(c));
        return list;
    }

    private static double[] toDoubles(net.minecraft.nbt.ListTag list) {
        double[] values = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            values[i] = ((net.minecraft.nbt.DoubleTag) list.get(i)).getAsDouble();
        }
        return values;
    }

    /* ------------------------------------------------------------------ */
    /* ケイパビリティ（他 MOD のパイプ・ホッパー等との互換）                 */
    /* ------------------------------------------------------------------ */

    /**
     * 物理化中は空のハンドラを返す。
     *
     * <p>これが無いと他 MOD のホッパー・パイプが {@code null} を受け取って
     * クラッシュする可能性があります（互換性のための安全弁）。</p>
     */
    public static void registerCapabilities(net.minecraftforge.event.AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof PhysicalizedBlockEntity)) {
            return;
        }
        // 何も登録しない＝Capability は null のまま。呼び出し側は null チェックで回避する
        // （Forge の標準 Capability は null セーフに扱われる）
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
