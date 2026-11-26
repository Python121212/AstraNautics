package com.astranautics.physics;

import com.astranautics.block.PhysicalizedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 排水量ベースの浮力（アルキメデスの原理）。
 *
 * <p>「石で桶を作ったら浮く」を実現するための計算です。素材の重さではなく、
 * <b>構造体が水を押しのけた量</b>で浮力が決まります。</p>
 *
 * <pre>
 *   浮力 = 水中にあるセル数 × WATER_DENSITY
 *   重さ = セルの質量の合計
 *   浮く  ⇔ 浮力 &gt; 重さ
 * </pre>
 *
 * <p>つまり、石（質量 2.0）を 1 個だけ水に入れると沈みますが、
 * 石の桶（外側の壁）のように<b>中が空洞で水を排水できる形</b>なら、
 * 排水量が増えて浮きます。木材は質量 0.6 なので素材単体でも浮きます。</p>
 */
public final class Buoyancy {

    /** 水の密度（1 セルあたりの浮力）。質量 1.0 = ちょうど中性浮力。 */
    public static final double WATER_DENSITY = 1.0D;
    /** 浮力の効き（1.0 で完全なアルキメデス、大きいほど浮きやすい）。 */
    public static final double BUOYANCY_GAIN = 0.55D;
    /** 沈下速度の上限（ゆっくり沈む）。 */
    private static final double MAX_SINK_SPEED = 0.06D;
    /** 浮上速度の上限（ゆっくり浮く）。 */
    private static final double MAX_RISE_SPEED = 0.08D;
    /** 水中の粘性（速度の減衰）。 */
    private static final double WATER_DRAG = 0.86D;

    /** クラスタごとの排水形状キャッシュ（セル数が変わったら再計算）。 */
    private static final Map<PhysicsCluster, Cached> CACHE = new HashMap<>();

    private Buoyancy() {
    }

    /** 浮力の計算結果。 */
    public record Result(
            int submergedCells,   // 水中にあるセル数
            int displacingCells,  // 排水しているセル数（空洞＋水中セル）
            double lift,          // 浮力
            double weight,        // 重さ
            double ratio,         // lift / weight（1 以上で浮く）
            boolean floats
    ) {
    }

    private record Cached(int cells, int displacing, double weight) {
    }

    /* ------------------------------------------------------------------ */

    /** そのセルが水中にあるか。 */
    public static boolean isSubmerged(Level level, PhysicalizedBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        // オフセット込みの中心位置で判定
        BlockPos center = BlockPos.containing(
                pos.getX() + 0.5D + be.getOffX(),
                pos.getY() + 0.5D + be.getOffY(),
                pos.getZ() + 0.5D + be.getOffZ());
        FluidState fluid = level.getFluidState(center);
        return fluid.is(FluidTags.WATER) && fluid.getAmount() >= 8;
    }

    /** そのセルが水面のすぐ上（浮いている）か。 */
    public static boolean isAtSurface(Level level, PhysicalizedBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        BlockPos center = BlockPos.containing(
                pos.getX() + 0.5D + be.getOffX(),
                pos.getY() + 0.5D + be.getOffY(),
                pos.getZ() + 0.5D + be.getOffZ());
        if (!level.getFluidState(center).isEmpty()) {
            return false;
        }
        return level.getFluidState(center.below()).is(FluidTags.WATER);
    }

    /**
     * クラスタの排水量を計算する。
     *
     * <p>「排水しているセル」＝水中のセル＋水に接している空洞セル。
     * 空洞（中が空気の空間）を数えることで、桶や船体の形が浮力に反映されます。</p>
     */
    public static Result evaluate(Level level, PhysicsCluster cluster) {
        int submerged = 0;
        double weight = 0.0D;
        List<PhysicalizedBlockEntity> cells = cluster.cells();

        for (PhysicalizedBlockEntity be : cells) {
            weight += be.getMaterial().mass();
            if (isSubmerged(level, be)) {
                submerged++;
            }
        }

        Cached cached = CACHE.get(cluster);
        int displacing;
        if (cached != null && cached.cells() == cluster.size()) {
            displacing = cached.displacing();
        } else {
            displacing = countDisplacingCells(level, cluster);
            CACHE.put(cluster, new Cached(cluster.size(), displacing, weight));
        }

        // 空洞が無くても、水中にあるセルは水を押しのけている
        displacing = Math.max(displacing, submerged);

        double lift = displacing * WATER_DENSITY;
        double ratio = weight <= 0.0D ? Double.MAX_VALUE : lift / weight;
        return new Result(submerged, displacing, lift, weight, ratio, lift > weight);
    }

    /**
     * 「水を押しのけているセル」を数える。
     *
     * <p>セルの AABB の外側を 6 方向に走査し、水に面している空洞セル（空気・水源）を
     * 排水体積として扱います。船体（中空）は外側の空洞が多く数えられるため浮きます。</p>
     */
    private static int countDisplacingCells(Level level, PhysicsCluster cluster) {
        if (cluster.isEmpty()) {
            return 0;
        }
        Set<BlockPos> occupied = new HashSet<>();
        Set<BlockPos> water = new HashSet<>();
        List<PhysicalizedBlockEntity> cells = cluster.cells();

        for (PhysicalizedBlockEntity be : cells) {
            BlockPos p = be.getBlockPos();
            occupied.add(p.immutable());
            if (isSubmerged(level, be)) {
                water.add(p.immutable());
            }
        }

        Set<BlockPos> displacing = new HashSet<>(water);

        // 水に接している空洞を探索（BFS の上限つき）
        int limit = Math.min(2048, Math.max(16, cluster.size() * 8));
        for (BlockPos w : water) {
            List<BlockPos> queue = new ArrayList<>();
            queue.add(w);
            for (int i = 0; i < queue.size() && displacing.size() < limit; i++) {
                BlockPos cur = queue.get(i);
                for (Direction dir : Direction.values()) {
                    BlockPos next = cur.relative(dir);
                    if (occupied.contains(next) || displacing.contains(next)) {
                        continue;
                    }
                    FluidState fluid = level.getFluidState(next);
                    boolean isWater = fluid.is(FluidTags.WATER);
                    boolean isAir = level.getBlockState(next).isAir();
                    if (isWater || isAir) {
                        displacing.add(next);
                        if (isAir) {
                            queue.add(next);   // 空洞の奥まで数える（桶の内側）
                        }
                    }
                }
            }
        }
        return displacing.size();
    }

    /* ------------------------------------------------------------------ */
    /* 適用                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * 浮力をクラスタ全体に適用する（毎 tick 呼ぶ）。
     *
     * <p>浮いている間は水面で上下に揺れ、沈むものはゆっくり沈みます。</p>
     */
    public static void apply(Level level, PhysicsCluster cluster) {
        if (cluster.isEmpty()) {
            return;
        }
        Result result = evaluate(level, cluster);
        if (result.submergedCells() == 0 && !result.floats()) {
            // 完全に水の外：浮力なし（落下は通常の物理で処理）
            return;
        }

        // 浮き ⇔ 沈み のバランス（ratio - 1）
        double net = (result.lift() - result.weight()) / Math.max(0.5D, result.weight());
        double push = net * BUOYANCY_GAIN;

        // 沈むものはゆっくり沈むだけに抑える
        if (push < 0.0D) {
            push = Math.max(push, -MAX_SINK_SPEED);
        } else {
            push = Math.min(push, MAX_RISE_SPEED);
        }

        // 揺れ（水面での波打ち）：わずかな上下振動を加える
        double wobble = Math.sin(level.getGameTime() * 0.15D) * 0.0015D *
                (result.floats() ? 1.0D : 0.3D);

        for (PhysicalizedBlockEntity be : cluster.cells()) {
            if (!isSubmerged(level, be) && result.submergedCells() == 0) {
                continue;
            }
            double mass = Math.max(0.2D, be.getMaterial().mass());
            double accel = (push + wobble) / Math.sqrt(mass);
            be.addImpulse(0.0D, accel, 0.0D);
            // 水中の粘性（速度減衰）
            be.setVelocity(
                    be.getVelX() * WATER_DRAG,
                    be.getVelY(),
                    be.getVelZ() * WATER_DRAG);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 表示用                                                              */
    /* ------------------------------------------------------------------ */

    /** プレイヤー向けの説明文字列。 */
    public static String describe(Result result) {
        return String.format("排水=%d 質量=%.1f 浮力比=%.2f → %s",
                result.displacingCells(), result.weight(), result.ratio(),
                result.floats() ? "浮く" : "沈む");
    }

    /** キャッシュ破棄（クラスタ構成が変わったとき）。 */
    public static void invalidate(PhysicsCluster cluster) {
        CACHE.remove(cluster);
    }
}
