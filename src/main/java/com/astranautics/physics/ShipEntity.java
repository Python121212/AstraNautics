package com.astranautics.physics;

import com.astranautics.block.PhysicalizedBlockEntity;
import com.astranautics.command.ShipCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 航行中の船（クラスタ剛体）。
 *
 * <p>接着して物理化したブロック群を、このエンティティが「1 隻の船」として
 * 実際の座標で動かします（描画オフセットではなく<b>本物の移動</b>）。</p>
 *
 * <ul>
 *   <li>船体はエンティティの周りにセルとして保持（グリッドは使いません）</li>
 *   <li>推力・質量・燃料から加速度を計算し、<b>1 つの剛体</b>として動く</li>
 *   <li>姿勢（ヨー）も持てるので、旋回しながら進めます</li>
 *   <li>着地／降船時にブロックへ戻す（{@link ShipCommand} / {@link ShipService}）</li>
 * </ul>
 */
public class ShipEntity extends Entity {

    /* ------------------------------------------------------------------ */
    /* 同期データ                                                          */
    /* ------------------------------------------------------------------ */

    /** 推進力（スロットルを含む）。 */
    public static final EntityDataAccessor<Float> DATA_THRUST =
            SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    /** 燃料（0..1）。 */
    public static final EntityDataAccessor<Float> DATA_FUEL =
            SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    /** 現在のスロットル（0..1）。 */
    public static final EntityDataAccessor<Float> DATA_THROTTLE =
            SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    /** セル数（HUD 用）。 */
    public static final EntityDataAccessor<Integer> DATA_CELLS =
            SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.INT);

    /* ------------------------------------------------------------------ */
    /* 船体データ                                                          */
    /* ------------------------------------------------------------------ */

    /** セル（船体のブロック）。 */
    public static final class Cell {
        public final BlockPos localOffset;
        public BlockState state;
        public CompoundTag blockEntityTag;

        public Cell(BlockPos localOffset, BlockState state, CompoundTag blockEntityTag) {
            this.localOffset = localOffset;
            this.state = state;
            this.blockEntityTag = blockEntityTag;
        }
    }

    private final List<Cell> cells = new ArrayList<>();
    /** 合計質量。 */
    private double totalMass = 1.0D;
    /** 推進力（ブロック/tick^2）。 */
    private double thrustPower;
    /** 搭乗者（パイロット）。 */
    private Player pilot;

    /** スロットル（0..1）。 */
    private float throttle;
    /** 燃料（0..1）。 */
    private float fuel = 1.0F;
    /** 自作エンジンの評価結果（搭載していれば）。 */
    private com.astranautics.engine.EngineStats engine = com.astranautics.engine.EngineStats.EMPTY;
    /** エンジンの発熱（0..1、1 で過熱停止）。 */
    private double engineHeat;
    /** 燃料容量（＝燃料タンクの数で伸びる）。 */
    private double fuelCapacity = 1.0D;

    /** 旋回の速さ。 */
    private static final double TURN_SPEED = 1.2D;
    /** 空気抵抗（宇宙では小さくなる）。 */
    private static final double DRAG = 0.985D;

    public ShipEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_THRUST, 0.0F);
        this.entityData.define(DATA_FUEL, 1.0F);
        this.entityData.define(DATA_THROTTLE, 0.0F);
        this.entityData.define(DATA_CELLS, 0);
    }

    /* ------------------------------------------------------------------ */
    /* 船体の構築／復元                                                     */
    /* ------------------------------------------------------------------ */

    /**
     * クラスタを船として「吸い上げる」（ワールドからブロックを除去）。
     *
     * @param anchor 船の原点になる位置
     */
    public void absorbCluster(Level level, PhysicsCluster cluster, BlockPos anchor) {
        this.cells.clear();
        double mass = 0.0D;
        double thrust = 0.0D;

        for (PhysicalizedBlockEntity be : cluster.cells()) {
            BlockState original = be.getOriginalState();
            if (original == null || original.isAir()) {
                continue;
            }
            BlockPos delta = be.getBlockPos().subtract(anchor);
            this.cells.add(new Cell(delta, original, be.getOriginalBlockEntityTag()));
            mass += be.getMaterial().mass();
            thrust += ShipService.thrustOf(original);
            level.removeBlock(be.getBlockPos(), false);
        }
        this.totalMass = Math.max(1.0D, mass);
        this.thrustPower = thrust;

        // 自作エンジン（連結した部品）を評価して推力を上書き
        evaluateEngine(level, anchor);

        // 搭乗者を集計する場合は ShipService が設定する
        this.setPos(anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D);
        this.setBoundingBox(computeBoundingBox());
        this.entityData.set(DATA_CELLS, this.cells.size());
        this.entityData.set(DATA_THRUST, (float) this.thrustPower);
        this.entityData.set(DATA_FUEL, this.fuel);
        this.setDeltaMovement(Vec3.ZERO);
    }

    /** 船体に含まれるエンジン部品を評価して推力・燃費・容量に反映する。 */
    private void evaluateEngine(Level level, BlockPos anchor) {
        com.astranautics.engine.EnginePartList list = new com.astranautics.engine.EnginePartList();
        for (Cell cell : this.cells) {
            if (com.astranautics.engine.EnginePartList.isEngineBlock(cell.state)) {
                list.add(cell.localOffset, cell.state);
            }
        }
        if (list.size() == 0) {
            return;
        }
        // スラスターを複数積む大きな船を想定して全エンジンを合算する
        var stats = com.astranautics.engine.EngineEvaluator.evaluateAll(
                list.splitIntoEngines(),
                com.astranautics.engine.EngineEvaluator.airDensityOf(level));
        if (stats.isEmpty()) {
            return;
        }
        this.engine = stats;
        this.thrustPower = stats.thrust();
        this.fuelCapacity = stats.fuelCapacity();
        this.fuel = 1.0F;
    }

    /** 自作エンジンの評価結果。 */
    public com.astranautics.engine.EngineStats engine() {
        return this.engine;
    }

    /** エンジンの発熱（0..1）。 */
    public double engineHeat() {
        return this.engineHeat;
    }

    /** 従来の書きかけ API との互換（1 セルぶん）。 */
    public void addCell(BlockPos offset, BlockState state) {
        this.cells.add(new Cell(offset, state, null));
        this.totalMass += 1.0D;
        this.entityData.set(DATA_CELLS, this.cells.size());
        this.setBoundingBox(computeBoundingBox());
    }

    /** 船体の大きさから当たり判定を作る。 */
    private AABB computeBoundingBox() {
        if (this.cells.isEmpty()) {
            return new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);
        }
        double minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        boolean first = true;
        for (Cell cell : this.cells) {
            double x = cell.localOffset.getX();
            double y = cell.localOffset.getY();
            double z = cell.localOffset.getZ();
            if (first) {
                minX = x; maxX = x + 1;
                minY = y; maxY = y + 1;
                minZ = z; maxZ = z + 1;
                first = false;
            } else {
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x + 1);
                maxY = Math.max(maxY, y + 1);
                maxZ = Math.max(maxZ, z + 1);
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return computeBoundingBox().inflate(2.0D);
    }

    @Override
    protected AABB makeBoundingBox() {
        return computeBoundingBox();
    }

    /* ------------------------------------------------------------------ */
    /* 航行                                                                 */
    /* ------------------------------------------------------------------ */

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientTick();
            return;
        }

        // 操縦（パイロットがいれば入力、いなければ慣性）
        applyControls();

        Vec3 velocity = this.getDeltaMovement();
        // 推力（機首方向）× スロットル ÷ 質量
        boolean overheated = this.engineHeat >= 1.0D;
        if (this.throttle > 0.001F && this.fuel > 0.0F && !overheated) {
            Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot()).normalize();
            double thrustNow = this.engine.isEmpty()
                    ? this.thrustPower
                    : usableThrust();
            double accel = thrustNow * this.throttle / this.totalMass;
            velocity = velocity.add(forward.scale(accel));

            if (this.engine.isEmpty()) {
                // 部品エンジンが無い場合はゆるやかに燃料を消費
                this.fuel = Math.max(0.0F, this.fuel - this.throttle * 0.0006F
                        * (float) Math.max(1.0D, this.thrustPower));
            } else {
                // 自作エンジン：燃費 × スロットル ÷ 燃料容量
                double use = this.engine.fuelUse() * this.throttle / this.fuelCapacity;
                this.fuel = (float) Math.max(0.0D, this.fuel - use);
            }
            this.entityData.set(DATA_FUEL, this.fuel);
        }

        // エンジンの発熱（スロットルに比例して上がり、切ると冷える）
        if (!this.engine.isEmpty()) {
            double target = this.throttle * this.engine.heat();
            this.engineHeat = Mth.approach((float) this.engineHeat,
                    (float) Mth.clamp(target, 0.0D, 1.3D), 0.01F);
        }

        // 抵抗（水中はさらに強い）
        double drag = this.isInWater() ? 0.90D : DRAG;
        velocity = velocity.scale(drag);
        this.setDeltaMovement(velocity);
        this.move(net.minecraft.world.entity.MoverType.SELF, velocity);

        // 設計上の最高速度でキャップ（ノズル・羽根の構成で変わる）
        double limit = maxSpeed();
        if (velocity.length() > limit) {
            velocity = velocity.normalize().scale(limit);
            this.setDeltaMovement(velocity);
        }

        // 羽根で囲みすぎたエンジンは損傷する（設計圧力 > 1.0）
        if (!this.engine.isEmpty() && this.engine.overStressed() && this.throttle > 0.5F
                && this.random.nextInt(600) == 0) {
            this.throttle = 0.0F;
            this.entityData.set(DATA_THROTTLE, 0.0F);
            this.engineHeat = 1.1D;
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, this.blockPosition(),
                        net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.6F);
            }
        }

        // 速度が落ちたら止める
        if (velocity.lengthSqr() < 1.0E-6D) {
            this.setDeltaMovement(Vec3.ZERO);
        }

        // 燃料切れ・停止時はスロットルを落とす
        if (this.fuel <= 0.0F && this.throttle > 0.0F) {
            this.throttle = 0.0F;
            this.entityData.set(DATA_THROTTLE, 0.0F);
        }

        // 地面に激突したら速度を殺す（めり込み防止）
        if (this.horizontalCollision || this.verticalCollision) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.35D));
        }

        // パイロットを一緒に動かす
        if (this.pilot != null && this.pilot.isPassenger()) {
            this.pilot.setPos(this.getX(), this.getY() + this.getBoundingBox().getYsize() * 0.5D - 0.6D,
                    this.getZ());
        }
    }

    private void clientTick() {
        // クライアントでは補間のみ（物理はサーバー専用）
        if (this.throttle > 0.0F) {
            // 煙のパーティクル程度
            if (this.random.nextInt(3) == 0) {
                Vec3 back = Vec3.directionFromRotation(0.0F, this.getYRot()).scale(-0.9D);
                this.level().addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,
                        this.getX() + back.x, this.getY() + 0.2D, this.getZ() + back.z,
                        0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** パイロットの入力からスロットルとヨーを決める。 */
    private void applyControls() {
        if (this.pilot == null || !this.pilot.isAlive()) {
            this.pilot = null;
            this.throttle = Mth.approach(this.throttle, 0.0F, 0.01F);
            this.entityData.set(DATA_THROTTLE, this.throttle);
            return;
        }

        float forward = 0.0F;
        float turn = 0.0F;
        // バニラの移動入力をそのまま使う（W/S = 前後、A/D = 旋回）
        forward = this.pilot.zza;
        turn = this.pilot.xxa;

        this.throttle = Mth.approach(this.throttle, Mth.clamp(forward, 0.0F, 1.0F), 0.06F);
        this.setYRot(this.getYRot() - turn * (float) TURN_SPEED);

        // 上下（スペース／スニーク）。搭乗中はジャンプ入力がサーバーへ届かないため、
        // プレイヤーの視線ではなく「スニークで下降、前進で上昇」のシンプル操作にする
        double lift = 0.0D;
        if (this.pilot.isShiftKeyDown()) {
            lift = -0.045D;
        } else if (forward > 0.0F && this.pilot.level().getBlockState(
                this.pilot.blockPosition().below()).isAir()) {
            lift = 0.02D;
        }
        if (lift != 0.0D) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0D, lift, 0.0D));
        }

        this.entityData.set(DATA_THROTTLE, this.throttle);
        this.entityData.set(DATA_FUEL, this.fuel);
    }

    /** 搭乗。 */
    public boolean board(Player player) {
        if (this.pilot != null && this.pilot != player) {
            return false;
        }
        this.pilot = player;
        player.startRiding(this, true);
        return true;
    }

    /** 降船。 */
    public void unboard() {
        if (this.pilot != null) {
            Player player = this.pilot;
            this.pilot = null;
            this.throttle = 0.0F;
            this.entityData.set(DATA_THROTTLE, 0.0F);
            player.stopRiding();
        }
    }

    public Player getPilot() {
        return this.pilot;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (passenger instanceof Player player) {
            this.pilot = player;
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (passenger == this.pilot) {
            this.pilot = null;
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.pilot == null && passenger instanceof Player;
    }

    /* ------------------------------------------------------------------ */
    /* 船体情報                                                            */
    /* ------------------------------------------------------------------ */

    public List<Cell> cells() {
        return this.cells;
    }

    public int cellCount() {
        return this.cells.size();
    }

    public double totalMass() {
        return this.totalMass;
    }

    public double thrustPower() {
        return this.thrustPower;
    }

    public float getThrottle() {
        return this.throttle;
    }

    public float getFuel() {
        return this.fuel;
    }

    public void setFuel(float fuel) {
        this.fuel = Mth.clamp(fuel, 0.0F, 1.0F);
        this.entityData.set(DATA_FUEL, this.fuel);
    }

    /** 現在の高さの大気密度を考えた実効推力。 */
    public double usableThrust() {
        if (this.engine.isEmpty()) {
            return this.thrustPower;
        }
        // 高度 200 以上は大気がほぼ無い（宇宙に近い）
        boolean thinAir = this.getY() > 200.0D;
        return thinAir ? this.engine.vacuumThrust() : this.engine.thrust();
    }

    /** 最大速度（ブロック/tick）。ノズル・羽根の構成で変わる。 */
    public double maxSpeed() {
        if (!this.engine.isEmpty()) {
            return this.engine.topSpeed();
        }
        return 0.6D + this.thrustPower * 0.05D;
    }

    /* ------------------------------------------------------------------ */
    /* 保存／読込                                                          */
    /* ------------------------------------------------------------------ */

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.totalMass = tag.getDouble("TotalMass");
        this.thrustPower = tag.getDouble("Thrust");
        this.throttle = tag.getFloat("Throttle");
        this.fuel = tag.getFloat("Fuel");
        this.cells.clear();
        ListTag list = tag.getList("Cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag cellTag = list.getCompound(i);
            BlockPos offset = new BlockPos(cellTag.getInt("X"), cellTag.getInt("Y"), cellTag.getInt("Z"));
            BlockState state = net.minecraft.nbt.NbtUtils.readBlockState(
                    this.level().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BLOCK)
                            .asLookup(), cellTag.getCompound("State"));
            this.cells.add(new Cell(offset, state, cellTag.contains("BE") ? cellTag.getCompound("BE") : null));
        }
        this.entityData.set(DATA_CELLS, this.cells.size());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("TotalMass", this.totalMass);
        tag.putDouble("Thrust", this.thrustPower);
        tag.putFloat("Throttle", this.throttle);
        tag.putFloat("Fuel", this.fuel);
        ListTag list = new ListTag();
        for (Cell cell : this.cells) {
            CompoundTag cellTag = new CompoundTag();
            cellTag.putInt("X", cell.localOffset.getX());
            cellTag.putInt("Y", cell.localOffset.getY());
            cellTag.putInt("Z", cell.localOffset.getZ());
            cellTag.put("State", net.minecraft.nbt.NbtUtils.writeBlockState(cell.state));
            if (cell.blockEntityTag != null) {
                cellTag.put("BE", cell.blockEntityTag);
            }
            list.add(cellTag);
        }
        tag.put("Cells", list);
    }
}
