package com.astranautics.physics;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicalizedBlockEntity;
import com.astranautics.registry.ModBlocks;
import com.astranautics.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 船（クラスタ）とエンティティの橋渡し。
 *
 * <p>「接着 → 物理化 → 搭乗」で船になり、「降船」でブロックに戻ります。
 * これが<b>本物の航行</b>（描画オフセットではなく実座標の移動）の入口です。</p>
 */
public final class ShipService {

    /** 1 ブロックあたりの推力（ブロック/tick^2）。 */
    private static final double THRUST_BASE = 0.02D;
    /** スラスター 1 つあたりの追加推力。 */
    private static final double THRUST_THRUSTER = 0.10D;

    private ShipService() {
    }

    /**
     * ブロックから推力を推定する。
     * スラスター／炎／かまど系は強く、それ以外は微弱（＝ほぼ慣性航行）。
     */
    public static double thrustOf(BlockState state) {
        if (state == null) {
            return 0.0D;
        }
        if (state.is(ModBlocks.THRUSTER.get())) {
            return THRUST_THRUSTER;
        }
        var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key != null) {
            String path = key.getPath();
            if (path.contains("thruster") || path.contains("engine") || path.contains("rocket")) {
                return THRUST_THRUSTER;
            }
        }
        return THRUST_BASE;
    }

    /**
     * プレイヤーを船に搭乗させる（クラスタを吸い上げて航行モードへ）。
     *
     * @return 成功したら true
     */
    public static boolean board(ServerLevel level, Player player, BlockPos anchor) {
        PhysicsCluster cluster = ClusterManager.at(level, anchor);
        if (cluster == null || cluster.isEmpty()) {
            cluster = ClusterManager.rebuild(level, anchor);
        }
        if (cluster == null || cluster.isEmpty()) {
            return false;
        }
        // 既に航行中の船があれば、そこへ乗るだけ
        ShipEntity existing = findShip(level, anchor);
        if (existing != null) {
            return existing.board(player);
        }

        ShipEntity ship = ModEntities.SHIP.get().create(level);
        if (ship == null) {
            return false;
        }
        ship.absorbCluster(level, cluster, anchor);
        ClusterManager.unregister(level, anchor);
        level.addFreshEntity(ship);
        ship.board(player);
        player.displayClientMessage(Component.translatable("message.astranautics.ship_launched",
                ship.cellCount(), String.format("%.1f", ship.totalMass())), true);
        AstraNautics.LOGGER.debug("[AstraNautics] 航行開始: セル={} 質量={}", ship.cellCount(), ship.totalMass());
        return true;
    }

    /** 近くの航行中の船を探す。 */
    public static ShipEntity findShip(ServerLevel level, BlockPos pos) {
        List<ShipEntity> ships = level.getEntitiesOfClass(ShipEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(6.0D));
        return ships.isEmpty() ? null : ships.get(0);
    }

    /**
     * 船をブロックに戻す（着地）。
     *
     * @param snap グリッドへスナップするか
     * @return 戻したブロック数（0 なら失敗）
     */
    public static int land(ServerLevel level, ShipEntity ship, boolean snap) {
        BlockPos base = BlockPos.containing(ship.getX(), ship.getY(), ship.getZ());
        int placed = 0;
        int skipped = 0;

        for (ShipEntity.Cell cell : ship.cells()) {
            BlockPos target = base.offset(cell.localOffset);
            BlockState current = level.getBlockState(target);
            // 置けない場所は上にずらす
            int tries = 0;
            while (!current.isAir() && tries < 4) {
                target = target.above();
                current = level.getBlockState(target);
                tries++;
            }
            if (!current.isAir() && !current.canBeReplaced()) {
                skipped++;
                continue;
            }
            level.setBlock(target, cell.state, 3);
            if (cell.blockEntityTag != null && level.getBlockEntity(target) != null) {
                try {
                    CompoundTag tag = cell.blockEntityTag.copy();
                    tag.remove("x");
                    tag.remove("y");
                    tag.remove("z");
                    tag.putInt("x", target.getX());
                    tag.putInt("y", target.getY());
                    tag.putInt("z", target.getZ());
                    level.getBlockEntity(target).load(tag);
                } catch (Exception exception) {
                    AstraNautics.LOGGER.debug("[AstraNautics] 船のブロックエンティティ復元に失敗", exception);
                }
            }
            placed++;
        }

        if (placed == 0) {
            return 0;
        }
        ship.unboard();
        ship.discard();

        // 戻したブロックを物理化しておく（レバー OFF の状態）
        for (ShipEntity.Cell cell : ship.cells()) {
            BlockPos target = base.offset(cell.localOffset);
            if (level.getBlockState(target).is(Blocks.AIR)) {
                continue;
            }
            if (com.astranautics.block.PhysicalizedBlock.physicalize(level, target)) {
                if (level.getBlockEntity(target) instanceof PhysicalizedBlockEntity be) {
                    be.disablePhysics();
                    level.setBlock(target, level.getBlockState(target)
                            .setValue(com.astranautics.block.PhysicalizedBlock.POWERED, false), 3);
                }
            }
        }
        return placed;
    }

    /** 航行中の船を「その場で」ブロックに戻す（緊急脱出）。 */
    public static int emergencyLand(Level level, ShipEntity ship) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }
        return land(serverLevel, ship, true);
    }
}
