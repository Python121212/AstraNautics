package com.astranautics.physics;

import com.astranautics.block.PhysicalizedBlockEntity;
import com.astranautics.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物理演算の各種相互作用（AstraLite の「強化」部分）。
 *
 * <ul>
 *   <li>プレイヤー押し・当たり判定</li>
 *   <li>水流・爆発などの外力</li>
 *   <li>レッドストーン信号によるレバー自動 ON/OFF</li>
 *   <li>ブロック同士の押し合い（接触時の分離と力の伝播）</li>
 *   <li>水中の浮力</li>
 * </ul>
 */
public final class PhysicsRuntime {

    /** プレイヤーがブロックを押したときに与える速度。 */
    private static final double PLAYER_PUSH = 0.075D;
    /** 接触で押し出す最大量（1 tick）。 */
    private static final double SEPARATION_MAX = 0.06D;

    private PhysicsRuntime() {
    }

    /* ------------------------------------------------------------------ */
    /* プレイヤーとの相互作用                                               */
    /* ------------------------------------------------------------------ */

    /** プレイヤーと物理化ブロックの当たり判定（サーバー側）。 */
    public static void playerInteraction(ServerLevel level, PhysicalizedBlockEntity be) {
        if (!be.isPhysicsEnabled()) {
            return;
        }
        AABB box = LightPhysicsEngine.box(be.getBlockPos(), be);
        for (Player player : level.players()) {
            AABB playerBox = player.getBoundingBox();
            if (!playerBox.intersects(box)) {
                continue;
            }
            // プレイヤーから見た押し出し方向
            Vec3 toBlock = new Vec3(box.getCenter().x - player.getX(),
                    0.0D,
                    box.getCenter().z - player.getZ());
            if (toBlock.lengthSqr() < 1.0E-4D) {
                toBlock = player.getLookAngle();
            }
            Vec3 dir = toBlock.normalize();
            be.addImpulse(dir.x * PLAYER_PUSH, 0.012D, dir.z * PLAYER_PUSH);
            // プレイヤー側も少し押される（触っている感）
            player.push(dir.x * 0.02D, 0.0D, dir.z * 0.02D);
        }
    }

    /* ------------------------------------------------------------------ */
    /* 外力：水流・爆発・レッドストーン                                     */
    /* ------------------------------------------------------------------ */

    /** 周囲の液体・爆発の影響を速度に足す。 */
    public static void applyEnvironment(ServerLevel level, PhysicalizedBlockEntity be) {
        BlockPos pos = be.getBlockPos();

        // --- 水流（流体の流れ方向） ---
        net.minecraft.world.level.material.FluidState fluid =
                level.getFluidState(pos.relative(Direction.UP));
        if (!fluid.isEmpty()) {
            Vec3 flow = fluid.getFlow(level, pos.relative(Direction.UP));
            if (flow.lengthSqr() > 1.0E-6D) {
                be.addImpulse(flow.x * 0.05D, flow.y * 0.05D, flow.z * 0.05D);
            }
            if (fluid.is(net.minecraft.tags.FluidTags.WATER)) {
                // 浮力：軽い素材ほど強く浮く
                double lift = be.getMaterial().buoyant() ? 0.09D : 0.045D;
                be.addImpulse(0.0D, lift / Math.max(0.3D, be.getMaterial().mass()), 0.0D);
            }
        }

        // --- レッドストーン信号：レバー自動 ON/OFF ---
        if (level.getGameTime() % 10L == pos.hashCode() % 10L) {
            boolean powered = level.hasNeighborSignal(pos);
            if (powered && !be.isPhysicsEnabled()) {
                be.enablePhysics();
                level.setBlock(pos, level.getBlockState(pos)
                        .setValue(com.astranautics.block.PhysicalizedBlock.POWERED, true), 3);
            } else if (!powered && be.isPhysicsEnabled() && be.isPhysicsHeldByRedstone()) {
                be.disablePhysics();
                level.setBlock(pos, level.getBlockState(pos)
                        .setValue(com.astranautics.block.PhysicalizedBlock.POWERED, false), 3);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* ブロック同士の押し出し                                               */
    /* ------------------------------------------------------------------ */

    /**
     * 連結クラスタを 1 つの構造体として扱い、セル同士の重なりを解消する。
     * 押し出しは「軽いセルほど大きく動く」質量比で配分する。
     */
    public static void resolveClusterOverlap(ServerLevel level, PhysicsCluster cluster) {
        if (cluster.size() < 2) {
            return;
        }
        List<PhysicalizedBlockEntity> cells = cluster.cells();
        for (int i = 0; i < cells.size(); i++) {
            for (int j = i + 1; j < cells.size(); j++) {
                PhysicalizedBlockEntity a = cells.get(i);
                PhysicalizedBlockEntity b = cells.get(j);
                AABB boxA = LightPhysicsEngine.box(a.getBlockPos(), a);
                AABB boxB = LightPhysicsEngine.box(b.getBlockPos(), b);
                if (!boxA.intersects(boxB)) {
                    continue;
                }
                double massA = a.getMaterial().mass();
                double massB = b.getMaterial().mass();
                double total = massA + massB;
                double pushA = massB / total;
                double pushB = massA / total;

                double dx = boxB.getCenter().x - boxA.getCenter().x;
                double dz = boxB.getCenter().z - boxA.getCenter().z;
                double len = Math.max(1.0E-4D, Math.sqrt(dx * dx + dz * dz));
                double amount = Math.min(SEPARATION_MAX, 0.5D * (1.0D - len));
                dx = dx / len * amount;
                dz = dz / len * amount;

                a.addOffset(-dx * pushA, 0.0D, -dz * pushA);
                b.addOffset(dx * pushB, 0.0D, dz * pushB);
                a.wake();
                b.wake();
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* 爆発                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * 爆発で物理化ブロックを吹き飛ばす。
     *
     * @return 吹き飛んだセル数
     */
    public static int applyExplosion(Level level, Vec3 center, double radius) {
        if (level.isClientSide) {
            return 0;
        }
        int affected = 0;
        int r = Mth.ceil(radius);
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (!(level.getBlockEntity(pos) instanceof PhysicalizedBlockEntity be)) {
                continue;
            }
            double dist = Math.sqrt(center.distanceToSqr(
                    pos.getX() + 0.5D + be.getOffX(),
                    pos.getY() + 0.5D + be.getOffY(),
                    pos.getZ() + 0.5D + be.getOffZ()));
            if (dist > radius) {
                continue;
            }
            double strength = (1.0D - dist / radius) * 0.55D;
            double nx = (pos.getX() + 0.5D - center.x) / Math.max(0.1D, dist);
            double ny = (pos.getY() + 0.5D - center.y) / Math.max(0.1D, dist) + 0.35D;
            double nz = (pos.getZ() + 0.5D - center.z) / Math.max(0.1D, dist);
            be.wake();
            be.addImpulse(nx * strength, ny * strength, nz * strength);
            affected++;
        }
        return affected;
    }

    /* ------------------------------------------------------------------ */
    /* クリーンアップ                                                       */
    /* ------------------------------------------------------------------ */

    /**
     * 長距離移動したセルの「グリッド移動」判定。
     * 将来の宇宙船（実座標移動）への布石として、現在は大きなオフセットを
     * 物理化ブロックの再配置ではなく「位置補正」として扱う。
     */
    public static void normalizeLongOffsets(ServerLevel level, PhysicalizedBlockEntity be) {
        double maxOffset = 1.5D;
        if (Math.abs(be.getOffX()) < maxOffset && Math.abs(be.getOffY()) < maxOffset
                && Math.abs(be.getOffZ()) < maxOffset) {
            return;
        }
        // 速度を維持したまま、オフセットをグリッドへ戻す（チェーンソート防止）
        BlockPos from = be.getBlockPos();
        BlockPos to = from.offset((int) Math.floor(be.getOffX()),
                (int) Math.floor(be.getOffY()),
                (int) Math.floor(be.getOffZ()));
        if (from.equals(to) || !level.getBlockState(to).canBeReplaced()) {
            return;
        }
        BlockState state = level.getBlockState(from);
        BlockState original = be.getOriginalState();
        double[] velocity = {be.getVelX(), be.getVelY(), be.getVelZ()};
        level.removeBlock(from, false);
        level.setBlock(to, state, 3);
        if (level.getBlockEntity(to) instanceof PhysicalizedBlockEntity moved) {
            moved.setOriginalState(original);
            moved.setVelocity(velocity[0], velocity[1], velocity[2]);
            moved.setPhysicsEnabledFromTransfer(true);
        }
        ClusterManager.rebuild(level, to);
    }

    /** デバッグ用の統計。 */
    public static Map<String, Object> stats(ServerLevel level) {
        Map<String, Object> map = new HashMap<>();
        int sleeping = 0;
        int awake = 0;
        for (PhysicsCluster cluster : ClusterManager.clusters(level)) {
            if (cluster.isSleeping()) {
                sleeping += cluster.size();
            } else {
                awake += cluster.size();
            }
        }
        map.put("cellsAwake", awake);
        map.put("cellsSleeping", sleeping);
        map.put("clusters", ClusterManager.clusters(level).size());
        return map;
    }

    // 参照だけして未使用警告を出さないためのダミー（将来の宇宙船実装用）
    @SuppressWarnings("unused")
    private static final BlockState PLACEHOLDER = Blocks.AIR.defaultBlockState();

    static {
        // 物理化ブロックの登録が済んでいることを静的に保証（開発時の取りこぼし防止）
        assert ModBlocks.PHYSICALIZED != null;
    }

    /** リストの安全なコピー。 */
    public static List<PhysicalizedBlockEntity> snapshot(List<PhysicalizedBlockEntity> list) {
        return new ArrayList<>(list);
    }
}
