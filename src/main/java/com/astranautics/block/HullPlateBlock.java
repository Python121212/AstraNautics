package com.astranautics.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 装甲板。宇宙戦艦の外殻です。
 *
 * <p>硬いぶん重いので、船全体の質量を押し上げます（浮力とのトレードオフ）。
 * 3 段階あり、上位ほど硬く重くなります。</p>
 */
public class HullPlateBlock extends Block {

    /** 装甲の段階。 */
    public enum Grade {
        /** 軽合金：軽い。 */
        LIGHT("light", 1.6D, "tooltip.astranautics.hull_plate.light"),
        /** 標準装甲。 */
        STANDARD("standard", 2.4D, "tooltip.astranautics.hull_plate.standard"),
        /** 重装甲（戦艦級）。 */
        HEAVY("heavy", 3.6D, "tooltip.astranautics.hull_plate.heavy");

        public final String id;
        /** 硬さ（＝質量倍率にも使う）。 */
        public final double hardness;
        public final String tooltipKey;

        Grade(String id, double hardness, String tooltipKey) {
            this.id = id;
            this.hardness = hardness;
            this.tooltipKey = tooltipKey;
        }
    }

    private final Grade grade;

    public HullPlateBlock(Grade grade, Properties properties) {
        super(properties);
        this.grade = grade;
    }

    public Grade grade() {
        return this.grade;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(this.grade.tooltipKey)
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }
}
