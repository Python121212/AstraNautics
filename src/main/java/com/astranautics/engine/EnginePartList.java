package com.astranautics.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自作エンジンの部品リスト（座標ではなく「部品の並び」で評価するための中間表現）。
 *
 * <p>船のように<b>ブロックがワールドに存在しない</b>場合でも評価できるよう、
 * 座標とブロックステートの組だけを持ちます。</p>
 */
public final class EnginePartList {

    private final List<BlockPos> positions = new ArrayList<>();
    private final List<BlockState> states = new ArrayList<>();

    public void add(BlockPos pos, BlockState state) {
        this.positions.add(pos.immutable());
        this.states.add(state);
    }

    public int size() {
        return this.positions.size();
    }

    public BlockPos pos(int index) {
        return this.positions.get(index);
    }

    public BlockState state(int index) {
        return this.states.get(index);
    }

    /** 指定座標のブロックステート（無ければ null）。 */
    /** エンジンとして評価する対象か（部品＋スラスター）。 */
    public static boolean isEngineBlock(BlockState state) {
        if (state == null) {
            return false;
        }
        return EnginePart.fromState(state) != null
                || state.getBlock() instanceof com.astranautics.block.ThrusterBlock;
    }

    public BlockState at(BlockPos pos) {
        for (int i = 0; i < this.positions.size(); i++) {
            if (this.positions.get(i).equals(pos)) {
                return this.states.get(i);
            }
        }
        return null;
    }

    /** 全てを走査して接続成分ごとのリストに分割する。 */
    public List<EnginePartList> splitIntoEngines() {
        List<EnginePartList> engines = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        for (int i = 0; i < this.positions.size(); i++) {
            BlockPos start = this.positions.get(i);
            if (visited.contains(start)) {
                continue;
            }
            EnginePartList engine = new EnginePartList();
            List<BlockPos> queue = new ArrayList<>();
            queue.add(start);
            visited.add(start);
            for (int q = 0; q < queue.size(); q++) {
                BlockPos pos = queue.get(q);
                BlockState state = at(pos);
                if (state == null) {
                    continue;
                }
                engine.add(pos, state);
                for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                    BlockPos next = pos.relative(dir);
                    if (visited.contains(next)) {
                        continue;
                    }
                    BlockState neighbor = at(next);
                    if (neighbor != null && isEngineBlock(neighbor)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
            if (engine.size() > 0) {
                engines.add(engine);
            }
        }
        return engines;
    }
}
