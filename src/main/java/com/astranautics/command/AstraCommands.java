package com.astranautics.command;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicalizedBlockEntity;
import com.astranautics.physics.ClusterManager;
import com.astranautics.physics.PhysicsCluster;
import com.astranautics.physics.PhysicsRuntime;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 物理演算のデバッグ・操作コマンド。
 *
 * <pre>
 *   /astranautics stats              現在のセル／クラスタ状況
 *   /astranautics cluster            見ているブロックのクラスタ情報
 *   /astranautics push &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;強さ&gt;  任意方向へ押す
 *   /astranautics boom &lt;半径&gt;        視線先で爆発相当の力を発生
 * </pre>
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AstraCommands {

    private AstraCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("astranautics")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("stats").executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    var stats = PhysicsRuntime.stats(level);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "[AstraLite] 覚醒セル: " + stats.get("cellsAwake")
                                    + " / スリープ: " + stats.get("cellsSleeping")
                                    + " / クラスタ: " + stats.get("clusters")), false);
                    return 1;
                }))
                .then(Commands.literal("material").executes(ctx -> {
                    var source = ctx.getSource();
                    BlockPos pos = lookedAtPos(source);
                    if (pos == null) {
                        source.sendFailure(Component.literal("ブロックを見てください"));
                        return 0;
                    }
                    var state = source.getLevel().getBlockState(pos);
                    var profile = com.astranautics.physics.MaterialProfile.of(state);
                    var id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(state.getBlock());
                    source.sendSuccess(() -> Component.literal(String.format(
                            "%s → 質量=%.2f 摩擦=%.2f 反発=%.2f 抵抗=%.3f 浮きやすい=%s",
                            id, profile.mass(), profile.friction(), profile.restitution(),
                            profile.drag(), profile.buoyant())), false);
                    source.sendSuccess(() -> Component.literal(
                            "物性ルール読み込み数: " + com.astranautics.physics.MaterialRules.loadedRuleCount()
                                    + " / 対応ブロック数: "
                                    + com.astranautics.physics.MaterialRules.supportedBlockCount()), false);
                    return 1;
                }))
                .then(Commands.literal("cluster").executes(ctx -> {
                    BlockPos pos = lookedAtPos(ctx.getSource());
                    if (pos == null) {
                        ctx.getSource().sendFailure(Component.literal("物理化ブロックを見てください"));
                        return 0;
                    }
                    PhysicsCluster cluster = ClusterManager.at(ctx.getSource().getLevel(), pos);
                    if (cluster == null) {
                        ctx.getSource().sendFailure(Component.literal("クラスタが見つかりません"));
                        return 0;
                    }
                    var buoy = com.astranautics.physics.Buoyancy.evaluate(ctx.getSource().getLevel(), cluster);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            com.astranautics.physics.Buoyancy.describe(buoy)), false);
                    double[] v = cluster.massWeightedVelocity();
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(
                            "%s / 摩擦=%.2f 反発=%.2f / 代表速度=(%.3f, %.3f, %.3f)",
                            cluster.debugId(), cluster.avgFriction(), cluster.avgRestitution(),
                            v[0], v[1], v[2])), false);
                    return 1;
                }))
                .then(Commands.literal("push")
                        .then(Commands.argument("x", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                .then(Commands.argument("y", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("z", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("power",
                                                                com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0D, 2.0D))
                                                        .executes(ctx -> {
                                                            BlockPos pos = lookedAtPos(ctx.getSource());
                                                            if (pos == null) {
                                                                ctx.getSource().sendFailure(
                                                                        Component.literal("物理化ブロックを見てください"));
                                                                return 0;
                                                            }
                                                            if (!(ctx.getSource().getLevel().getBlockEntity(pos)
                                                                    instanceof PhysicalizedBlockEntity be)) {
                                                                ctx.getSource().sendFailure(
                                                                        Component.literal("物理化ブロックではありません"));
                                                                return 0;
                                                            }
                                                            double power = com.mojang.brigadier.arguments.DoubleArgumentType
                                                                    .getDouble(ctx, "power");
                                                            be.wake();
                                                            be.addImpulse(
                                                                    com.mojang.brigadier.arguments.DoubleArgumentType
                                                                            .getDouble(ctx, "x") * power,
                                                                    com.mojang.brigadier.arguments.DoubleArgumentType
                                                                            .getDouble(ctx, "y") * power,
                                                                    com.mojang.brigadier.arguments.DoubleArgumentType
                                                                            .getDouble(ctx, "z") * power);
                                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                                    "押しました: power=" + power), false);
                                                            return 1;
                                                        }))))))
                .then(Commands.literal("physicalize").executes(ctx -> {
                    BlockPos pos = lookedAtPos(ctx.getSource());
                    if (pos == null) {
                        ctx.getSource().sendFailure(Component.literal("ブロックを見てください"));
                        return 0;
                    }
                    if (!com.astranautics.block.PhysicalizedBlock.physicalize(ctx.getSource().getLevel(), pos)) {
                        ctx.getSource().sendFailure(Component.literal("このブロックは物理化できません"));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("物理化しました"), false);
                    return 1;
                }))
                .then(Commands.literal("boom")
                        .then(Commands.argument("radius",
                                        com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0D, 32.0D))
                                .executes(ctx -> {
                                    var source = ctx.getSource();
                                    var entity = source.getEntity();
                                    if (entity == null) {
                                        source.sendFailure(Component.literal("プレイヤーとして実行してください"));
                                        return 0;
                                    }
                                    double radius = com.mojang.brigadier.arguments.DoubleArgumentType
                                            .getDouble(ctx, "radius");
                                    int affected = PhysicsRuntime.applyExplosion(
                                            source.getLevel(), entity.position(), radius);
                                    source.sendSuccess(() -> Component.literal(
                                            affected + " 個の物理化ブロックを吹き飛ばしました"), false);
                                    return 1;
                                }))));
    }

    private static BlockPos lookedAtPos(CommandSourceStack source) {
        var entity = source.getEntity();
        if (entity == null) {
            return null;
        }
        HitResult hit = entity.pick(64.0D, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit) {
            return blockHit.getBlockPos();
        }
        return null;
    }
}
