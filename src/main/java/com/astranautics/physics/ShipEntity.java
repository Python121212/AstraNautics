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
    /** 浮力（HUD 用）。 */
    public static final EntityDataAccessor<Float> DATA_LIFT =
            SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    /** レビタイトの数（HUD 用）。 */
    public static final EntityDataAccessor<Integer> DATA_LEVITITE =
            SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.INT);
    /** 気密空間のセル数（HUD 用）。 */
    public static final EntityDataAccessor<Integer> DATA_ENVELOPE =
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

    /** 気密外皮で囲った気体の浮力（kpg）。 */
    private double envelopeLift;
    /** レビタイトの浮力（kpg）。 */
    private double levititeLift;
    /** 浮力の対象になった気密空間のセル数。 */
    private int envelopeCells;
    /** レビタイトの数。 */
    private int levititeCount;
    /** 上昇入力（+1 上昇 / -1 下降）。 */
    private double liftInput;
    /** 動力炉の数。 */
    private int powerCores;
    /** 装甲の合計硬度。 */
    private double armorValue;

    /** 旋回の速さ（ラジアン/tick）。 */
    private static final double TURN_SPEED = 0.05D;
    /** 上昇／下降の強さ。 */
    private static final double LIFT_GAIN = 0.045D;
    /** 浮力 1 あたりの加速度（重さ 1 を支える力）。 */
    private static final double BUOYANCY_GAIN = 0.02D;
    /** 沈む速さの上限。 */
    private static final double MAX_SINK = 0.12D;
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
        this.entityData.define(DATA_LIFT, 0.0F);
        this.entityData.define(DATA_LEVITITE, 0);
        this.entityData.define(DATA_ENVELOPE, 0);
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
        // 浮力（気密外皮の熱気 + レビタイト）
        evaluateBuoyancy(level);
        // 艦内システム（電力・装甲）
        evaluateSystems();

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

    /** 気密外皮とレビタイトの浮力を計算する（ブロックに戻す前の状態で実行）。 */
    private void evaluateBuoyancy(Level level) {
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        for (Cell cell : this.cells) {
            positions.add(this.blockPosition().offset(cell.localOffset));
            states.add(cell.state);
        }
        com.astranautics.aero.Envelope.Report envelope =
                com.astranautics.aero.Envelope.scan(level, positions, states);
        this.envelopeLift = envelope.lift();
        this.envelopeCells = envelope.totalCells();

        com.astranautics.aero.Levitite.Result levitite =
                com.astranautics.aero.Levitite.scan(positions, states);
        this.levititeLift = levitite.lift();
        this.levititeCount = levitite.total();
        this.entityData.set(DATA_LIFT, (float) this.liftPower());
        this.entityData.set(DATA_LEVITITE, this.levititeCount);
        this.entityData.set(DATA_ENVELOPE, this.envelopeCells);
    }

    /** 艦内システム（動力炉・レーダー・砲塔・装甲）を数えておく。 */
    private void evaluateSystems() {
        int cores = 0;
        double armor = 0.0D;
        for (Cell cell : this.cells) {
            if (cell.state.getBlock() instanceof com.astranautics.block.ShipPowerCoreBlock) {
                cores++;
            } else if (cell.state.getBlock()
                    instanceof com.astranautics.block.HullPlateBlock hull) {
                armor += hull.grade().hardness;
            }
        }
        this.powerCores = cores;
        this.armorValue = armor;
    }

    /** 動力炉の数。 */
    public int powerCores() {
        return this.powerCores;
    }

    /** 装甲の合計硬度。 */
    public double armorValue() {
        return this.armorValue;
    }

    /** 浮力の合計（kpg）。 */
    public double liftPower() {
        return this.envelopeLift + this.levititeLift;
    }

    /** 気密空間のセル数。 */
    public int envelopeCells() {
        return this.envelopeCells;
    }

    /** レビタイトの数。 */
    public int levititeCount() {
        return this.levititeCount;
    }

    /** 同期された浮力（クライアント HUD 用）。 */
    public float syncedLift() {
        return this.entityData.get(DATA_LIFT);
    }

    /** 同期されたレビタイト数。 */
    public int syncedLevitite() {
        return this.entityData.get(DATA_LEVITITE);
    }

    /** 同期された気密空間セル数。 */
    public int syncedEnvelope() {
        return this.entityData.get(DATA_ENVELOPE);
    }

    /** 自重（ブロック + 搭載エンジン）。 */
    public double hullWeight() {
        return this.totalMass;
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

        // 浮力（気密外皮の熱気 + レビタイト）と重力
        // 宇宙空間では気嚢の浮力は無効（レビタイトは重力を打ち消すので有効）
        boolean vacuum = com.astranautics.aero.Space.isVacuum(this.level(), this.getY());
        double lift = vacuum ? this.levititeLift : liftPower();
        if (lift > 0.0D) {
            // 浮力が重さを上回るぶんだけ上昇。足りなければゆっくり沈む
            double net = lift - this.totalMass * 0.5D;
            double vertical;
            if (net > 0.0D) {
                vertical = Math.min(LIFT_GAIN, net * BUOYANCY_GAIN);
            } else {
                vertical = Math.max(-MAX_SINK, net * BUOYANCY_GAIN);
            }
            // 上昇入力（スペース）で押し上げ、スニークで押し下げ
            vertical += this.liftInput * LIFT_GAIN;
            velocity = velocity.add(0.0D, vertical, 0.0D);
        } else if (this.liftInput != 0.0D && this.throttle > 0.01F) {
            // 浮力が無くても、推進中は僅かに上下できる（姿勢制御）
            velocity = velocity.add(0.0D, this.liftInput * LIFT_GAIN * 0.4D, 0.0D);
        }

        // 抵抗（水中はさらに強い／宇宙はほぼ無抵抗）
        double drag = this.isInWater() ? 0.90D
                : com.astranautics.aero.Space.drag(this.level(), this.getY());
        // レビタイトは低速だと抵抗が増える（Create Aeronautics と同じ挙動）
        if (this.levititeCount > 0) {
            drag *= com.astranautics.aero.Levitite.dragFor(velocity.length(),
                    new com.astranautics.aero.Levitite.Result(this.levititeCount, 0,
                            this.levititeLift));
        }
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

        // W/S = スロットル、A/D = ヨー、視点（マウス）＝機首の向き
        float forward = this.pilot.zza;
        float turn = this.pilot.xxa;

        this.throttle = Mth.approach(this.throttle, Mth.clamp(forward, 0.0F, 1.0F), 0.06F);
        this.setYRot(this.getYRot() - turn * (float) TURN_SPEED);

        // 機首の向きはパイロットの視線（マウス）に合わせる。
        // 船体そのものは回転させない（ロール・ピッチなし＝常に水平姿勢）。
        float targetYaw = this.pilot.getYRot();
        this.setYRot(Mth.approach(this.getYRot(), targetYaw, 3.0F));

        // 上下：スプリント（Ctrl）で上昇、スニークで下降
        // （搭乗中はジャンプ入力がサーバーに届かないため、同期されるキーを使う）
        this.liftInput = 0.0D;
        if (this.pilot.isShiftKeyDown()) {
            this.liftInput = -1.0D;
        } else if (this.pilot.isSprinting()) {
            this.liftInput = 1.0D;
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
        this.envelopeLift = tag.getDouble("EnvelopeLift");
        this.levititeLift = tag.getDouble("LevititeLift");
        this.envelopeCells = tag.getInt("EnvelopeCells");
        this.levititeCount = tag.getInt("LevititeCount");
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
        tag.putDouble("EnvelopeLift", this.envelopeLift);
        tag.putDouble("LevititeLift", this.levititeLift);
        tag.putInt("EnvelopeCells", this.envelopeCells);
        tag.putInt("LevititeCount", this.levititeCount);
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
