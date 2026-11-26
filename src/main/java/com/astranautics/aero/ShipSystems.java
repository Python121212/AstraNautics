package com.astranautics.aero;

import com.astranautics.block.ShipPowerCoreBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 宇宙船の「艦内システム」を数える。
 *
 * <p>Create Aeronautics は車輪・プロペラ・気球などの大気圏向け装備を持ちますが、
 * こちらは<b>宇宙船・宇宙戦艦</b>が前提なので、
 * <b>電力（動力炉）・装甲・レーダー・砲塔</b>といった艦載システムを扱います。</p>
 */
public final class ShipSystems {

    private ShipSystems() {
    }

    /** スキャン結果。 */
    public record Report(int powerCores, int radar, int turrets, int hullPlates, double armor,
                         int thrusters, int nozzles) {

        public static final Report EMPTY = new Report(0, 0, 0, 0, 0.0D, 0, 0);

        /** 供給電力。 */
        public int supply() {
            return this.powerCores * ShipPowerCoreBlock.SUPPLY;
        }

        /** 消費電力（レーダー 20 / 砲塔 35）。 */
        public int demand() {
            return this.radar * 20 + this.turrets * 35;
        }

        /** 電力が足りているか。 */
        public boolean powered() {
            return this.supply() >= this.demand();
        }

        public Component message() {
            return Component.translatable("message.astranautics.systems",
                    this.powerCores, this.supply(), this.demand(),
                    this.radar, this.turrets, this.hullPlates,
                    String.format("%.1f", this.armor));
        }

        public List<Component> lines() {
            List<Component> list = new ArrayList<>();
            list.add(Component.translatable("message.astranautics.systems.power",
                    this.supply(), this.demand())
                    .withStyle(powered() ? ChatFormatting.AQUA : ChatFormatting.RED));
            list.add(Component.translatable("message.astranautics.systems.armor",
                    this.hullPlates, String.format("%.1f", this.armor))
                    .withStyle(ChatFormatting.GRAY));
            return list;
        }
    }

    /** 近傍のブロックから艦内システムを数える。 */
    public static Report scan(Level level, BlockPos center, int radius) {
        int cores = 0;
        int radar = 0;
        int turrets = 0;
        int plates = 0;
        double armor = 0.0D;
        int thrusters = 0;
        int nozzles = 0;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.getBlock() instanceof ShipPowerCoreBlock) {
                        cores++;
                    } else if (state.getBlock() instanceof com.astranautics.block.ShipCoreBlock) {
                        // 艦橋は数えるだけ
                    } else if (state.getBlock() instanceof com.astranautics.block.ShipRadarBlock) {
                        radar++;
                    } else if (state.getBlock() instanceof com.astranautics.block.ShipTurretBlock) {
                        turrets++;
                    } else if (state.getBlock() instanceof com.astranautics.block.HullPlateBlock hull) {
                        plates++;
                        armor += hull.grade().hardness;
                    } else if (state.getBlock() instanceof com.astranautics.block.ThrusterBlock) {
                        thrusters++;
                    } else if (state.getBlock() instanceof com.astranautics.block.NozzleBlock) {
                        nozzles++;
                    }
                }
            }
        }
        return new Report(cores, radar, turrets, plates, armor, thrusters, nozzles);
    }
}
