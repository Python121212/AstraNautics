package com.astranautics.physics;

import com.astranautics.block.PhysicalizedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 物理化ブロックのクラスタ管理（Union-Find 風の遅延探索）。
 *
 * <p>「連結している物理化ブロックを 1 つの構造体として扱う」ための基盤です。
 * 探索は<b>物理化／レバー操作時のみ</b>行い、毎 tick の近傍探索はしません。
 * 大規模化（100 セル以上）でも軽く動くよう、探索には上限を設けています。</p>
 */
public final class ClusterManager {

    /** 1 クラスタとして扱う最大セル数（超えたら分割扱い）。 */
    public static final int MAX_CLUSTER_SIZE = 512;

    private static final Map<Level, Map<BlockPos, PhysicsCluster>> CACHE =
            new WeakHashMap<>();

    private ClusterManager() {
    }

    private static Map<BlockPos, PhysicsCluster> mapFor(Level level) {
        return CACHE.computeIfAbsent(level, key -> new java.util.concurrent.ConcurrentHashMap<>());
    }

    /** その位置が属するクラスタ（なければ null）。 */
    public static PhysicsCluster at(Level level, BlockPos pos) {
        return mapFor(level).get(pos);
    }

    /**
     * 指定位置から連結成分を探索してクラスタを構築する。
     * 物理化したとき・レバーを操作したとき・ブロックが壊れたときに呼ぶ。
     */
    public static PhysicsCluster rebuild(Level level, BlockPos origin) {
        Map<BlockPos, PhysicsCluster> map = mapFor(level);

        Deque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        BlockPos start = origin.immutable();
        queue.add(start);
        visited.add(start);

        PhysicsCluster cluster = new PhysicsCluster();

        while (!queue.isEmpty() && found.size() < MAX_CLUSTER_SIZE) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockEntity(pos) instanceof PhysicalizedBlockEntity be)) {
                continue;
            }
            found.add(pos);
            cluster.add(be);
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir).immutable();
                if (!visited.add(next)) {
                    continue;
                }
                if (level.getBlockEntity(next) instanceof PhysicalizedBlockEntity) {
                    queue.add(next);
                }
                if (visited.size() > MAX_CLUSTER_SIZE) {
                    break;
                }
            }
        }

        // 古い登録を解除してから新しいクラスタを登録
        for (BlockPos pos : found) {
            PhysicsCluster old = map.remove(pos);
            if (old != null && old != cluster) {
                for (PhysicalizedBlockEntity be : new ArrayList<>(old.cells())) {
                    old.remove(be);
                }
            }
        }
        for (BlockPos pos : found) {
            map.put(pos.immutable(), cluster);
        }
        return cluster;
    }

    /** 物理化が解除されたときの登録解除。 */
    public static void unregister(Level level, BlockPos pos) {
        Map<BlockPos, PhysicsCluster> map = mapFor(level);
        PhysicsCluster cluster = map.remove(pos);
        if (cluster != null) {
            if (level.getBlockEntity(pos) instanceof PhysicalizedBlockEntity be) {
                cluster.remove(be);
            }
        }
    }

    /** 近傍の物理化ブロックを取得（接触判定用・範囲はごく小さい）。 */
    public static List<PhysicalizedBlockEntity> neighbors(Level level, BlockPos pos, int radius) {
        List<PhysicalizedBlockEntity> result = new ArrayList<>(6);
        for (BlockPos p : BlockPos.betweenClosed(
                pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius,
                pos.getX() + radius, pos.getY() + radius, pos.getZ() + radius)) {
            if (p.equals(pos)) {
                continue;
            }
            if (level.getBlockEntity(p) instanceof PhysicalizedBlockEntity be
                    && be.isPhysicsEnabled()) {
                result.add(be);
            }
        }
        return result;
    }

    /** デバッグ用：登録されているクラスタ一覧。 */
    public static Collection<PhysicsCluster> clusters(Level level) {
        return new ArrayList<>(new java.util.LinkedHashSet<>(mapFor(level).values()));
    }

    /** サーバー停止時などのクリア。 */
    public static void clear(ServerLevel level) {
        CACHE.remove(level);
    }
}
