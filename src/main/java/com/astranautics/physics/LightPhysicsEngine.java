package com.astranautics.physics;

import com.astranautics.block.PhysicalizedBlockEntity;
import com.astranautics.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * AstraNautics 独自の超軽量ブロック物理エンジン（AstraLite）。
 *
 * <p>設計方針（Create: Aeronautics のような重量級物理とは別系統）:</p>
 * <ul>
 *   <li><b>グリッドを動かさない</b>: ブロックはその場に残し、描画オフセットだけを動かす。
 *       チャンクの再メッシュやブロック更新が発生しないため極端に軽い。</li>
 *   <li><b>軸分離 + サブステップ</b>: 1 tick あたり最大 {@value #MAX_SUBSTEPS} 回の移動判定のみ。</li>
 *   <li><b>スリープ</b>: 接地してほぼ静止したら計算を止める（{@value #SLEEP_TICKS} tick でスリープ）。</li>
 *   <li><b>回転なし</b>: 姿勢は常に保持。</li>
 *   <li><b>接触インパルス</b>: 物理化ブロック同士がぶつかると力が伝播する（物理強化）。</li>
 * </ul>
 */
public final class LightPhysicsEngine {

    /** エンジン識別子（ログ・デバッグ用）。 */
    public static final String ENGINE_ID = "astralite-1";

    /** 重力加速度（ブロック/tick^2）。 */
    public static final double GRAVITY = 0.08D;
    /** 空気抵抗。 */
    public static final double AIR_DRAG = 0.99D;
    /** 接地時の水平摩擦。 */
    public static final double GROUND_FRICTION = 0.60D;
    /** 反発係数。 */
    public static final double BOUNCE = 0.34D;
    /** 速度上限（ブロック/tick）。 */
    public static final double MAX_SPEED = 1.15D;
    /** この速度未満かつ接地ならスリープカウントを進める。 */
    public static final double SLEEP_SPEED = 0.008D;
    /** スリープするまでの tick 数。 */
    public static final int SLEEP_TICKS = 20;
    /** 1 サブステップの最大移動量（ブロック）。 */
    private static final double SUBSTEP = 0.22D;
    /** 1 tick あたりの最大サブステップ数。 */
    private static final int MAX_SUBSTEPS = 4;
    /** 接触インパルスの強さ。 */
    private static final double CONTACT_IMPULSE = 0.055D;

    private LightPhysicsEngine() {
    }

    /**
     * 1 tick 分のシミュレーション。
     *
     * @return オフセットが変化したら true（クライアント同期が必要）
     */
    public static boolean tick(Level level, BlockPos pos, PhysicalizedBlockEntity be) {
        if (!be.isPhysicsEnabled()) {
            return false;
        }
        if (be.getSleepTicks() >= SLEEP_TICKS) {
            be.setVelocity(0.0D, 0.0D, 0.0D);
            return false;
        }

        double startX = be.getOffX();
        double startY = be.getOffY();
        double startZ = be.getOffZ();

        // 重力と抵抗
        double vx = Mth.clamp(be.getVelX() * AIR_DRAG, -MAX_SPEED, MAX_SPEED);
        double vy = Mth.clamp(be.getVelY() - GRAVITY, -MAX_SPEED, MAX_SPEED);
        double vz = Mth.clamp(be.getVelZ() * AIR_DRAG, -MAX_SPEED, MAX_SPEED);

        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        int steps = Mth.clamp((int) Math.ceil(speed / SUBSTEP), 1, MAX_SUBSTEPS);

        boolean grounded = false;
        for (int i = 0; i < steps && !grounded; i++) {
            double dx = vx / steps;
            double dy = vy / steps;
            double dz = vz / steps;

            // --- Y 軸 ---
            if (dy != 0.0D) {
                if (level.noCollision(box(pos, be).move(0.0D, dy, 0.0D))) {
                    be.addOffset(0.0D, dy, 0.0D);
                } else if (dy < 0.0D) {
                    propagateContact(level, pos, be, 1, vy);
                    if (Math.abs(vy) > 0.22D) {
                        vy = -vy * BOUNCE;                 // 軽く跳ねる
                        be.addOffset(0.0D, dy * 0.25D, 0.0D);
                    } else {
                        vy = 0.0D;
                    }
                    grounded = true;
                } else {
                    propagateContact(level, pos, be, 1, vy);
                    vy = 0.0D;                             // 天井
                }
            }

            // --- X 軸 ---
            if (dx != 0.0D) {
                if (level.noCollision(box(pos, be).move(dx, 0.0D, 0.0D))) {
                    be.addOffset(dx, 0.0D, 0.0D);
                } else {
                    propagateContact(level, pos, be, 0, vx);
                    vx = -vx * BOUNCE;
                }
            }

            // --- Z 軸 ---
            if (dz != 0.0D) {
                if (level.noCollision(box(pos, be).move(0.0D, 0.0D, dz))) {
                    be.addOffset(0.0D, 0.0D, dz);
                } else {
                    propagateContact(level, pos, be, 2, vz);
                    vz = -vz * BOUNCE;
                }
            }
        }

        if (grounded) {
            vx *= GROUND_FRICTION;
            vz *= GROUND_FRICTION;
        }
        be.setVelocity(vx, vy, vz);

        double finalSpeed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (grounded && finalSpeed < SLEEP_SPEED) {
            be.setSleepTicks(be.getSleepTicks() + 1);
            if (be.getSleepTicks() >= SLEEP_TICKS) {
                be.setVelocity(0.0D, 0.0D, 0.0D);
            }
        } else {
            be.setSleepTicks(0);
        }

        return startX != be.getOffX() || startY != be.getOffY() || startZ != be.getOffZ();
    }

    /**
     * 衝突面に接している物理化ブロックへ力を伝播する（物理強化）。
     * 走査範囲は自身の AABB + 僅かなマージンだけなので非常に軽い。
     */
    private static void propagateContact(Level level, BlockPos pos, PhysicalizedBlockEntity be,
                                         int axis, double velocity) {
        if (level.isClientSide || Math.abs(velocity) < 0.05D) {
            return;
        }
        AABB scan = box(pos, be).inflate(0.06D);
        for (BlockPos p : BlockPos.betweenClosed(
                Mth.floor(scan.minX), Mth.floor(scan.minY), Mth.floor(scan.minZ),
                Mth.floor(scan.maxX), Mth.floor(scan.maxY), Mth.floor(scan.maxZ))) {
            if (p.equals(pos) || !level.getBlockState(p).is(ModBlocks.PHYSICALIZED.get())) {
                continue;
            }
            if (level.getBlockEntity(p) instanceof PhysicalizedBlockEntity other && other.isPhysicsEnabled()) {
                double impulse = Math.min(Math.abs(velocity) * CONTACT_IMPULSE, 0.05D);
                other.addImpulse(
                        axis == 0 ? Math.signum(velocity) * impulse : 0.0D,
                        axis == 1 ? -impulse * 0.6D : 0.0D,
                        axis == 2 ? Math.signum(velocity) * impulse : 0.0D);
            }
        }
    }

    /** 現在のオフセットを反映した AABB（ブロック 1 個分、回転なし）。 */
    public static AABB box(BlockPos pos, PhysicalizedBlockEntity be) {
        double x = pos.getX() + be.getOffX();
        double y = pos.getY() + be.getOffY();
        double z = pos.getZ() + be.getOffZ();
        return new AABB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
    }
}
