package com.astranautics.command;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicsAssemblerBlock;
import com.astranautics.block.entity.PhysicsAssemblerBlockEntity;
import com.astranautics.block.entity.ShipAssembler;
import com.astranautics.config.AstraConfig;
import com.astranautics.entity.PhysicsShip;
import com.astranautics.physics.ShipWorld;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /astranautics diag} — prints everything needed to explain why a structure
 * did or did not turn into a physics body.
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AstraCommands {
    private static final int SCAN_RADIUS = 32;

    private AstraCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("astranautics")
                .then(Commands.literal("diag").executes(AstraCommands::diagnose)));
    }

    private static int diagnose(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("run this as a player"));
            return 0;
        }
        ServerLevel level = (ServerLevel) player.level();
        int radius = AstraConfig.ASSEMBLY_RADIUS.get();
        int maxBlocks = AstraConfig.MAX_BLOCKS.get();

        List<Component> lines = new ArrayList<>();
        lines.add(header("AstraNautics diagnostics"));
        lines.add(Component.literal("radius=" + radius + " maxBlocks=" + maxBlocks
                + " iterations=" + AstraConfig.SOLVER_ITERATIONS.get()
                + " substeps=" + AstraConfig.SUBSTEPS.get()).withStyle(ChatFormatting.GRAY));

        List<PhysicsShip> ships = ShipWorld.ships(level);
        lines.add(Component.literal("ships in level: " + ships.size()).withStyle(ChatFormatting.AQUA));
        for (PhysicsShip ship : ships) {
            lines.add(Component.literal("  - " + describe(ship)).withStyle(ChatFormatting.DARK_AQUA));
        }

        int assemblers = 0;
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
                center.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PhysicsAssemblerBlock)) {
                continue;
            }
            assemblers++;
            BlockEntity blockEntity = level.getBlockEntity(pos);
            boolean enabled = state.getValue(PhysicsAssemblerBlock.ENABLED);
            String shipInfo = "-";
            int lastBlocks = -1;
            if (blockEntity instanceof PhysicsAssemblerBlockEntity assembler) {
                shipInfo = assembler.getShipId() == null ? "none" : assembler.getShipId().toString().substring(0, 8);
                lastBlocks = assembler.getLastBlockCount();
            }
            int preview = enabled && "none".equals(shipInfo)
                    ? ShipAssembler.preview(level, pos, radius, maxBlocks) : -1;

            String text = "  assembler " + pos.toShortString() + " enabled=" + enabled
                    + " ship=" + shipInfo
                    + (lastBlocks >= 0 ? " lastBlocks=" + lastBlocks : "")
                    + (preview >= 0 ? " wouldAssemble=" + preview : "");
            lines.add(Component.literal(text).withStyle(preview == 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
        }

        if (assemblers == 0) {
            lines.add(Component.literal("  no physics assembler within " + SCAN_RADIUS + " blocks")
                    .withStyle(ChatFormatting.RED));
        }

        lines.add(Component.literal("tip: 何も出ない場合はログの [astranautics] 行を確認してください")
                .withStyle(ChatFormatting.GRAY));

        for (Component line : lines) {
            player.sendSystemMessage(line);
        }
        return 1;
    }

    private static String describe(PhysicsShip ship) {
        var body = ship.getBody();
        int blocks = ship.getStructure() == null ? 0 : ship.getStructure().voxels().size();
        return "ship " + ship.blockPosition().toShortString()
                + " blocks=" + blocks
                + " mass=" + String.format("%.1f", body.getMass())
                + " sleeping=" + ship.isSleeping()
                + " v=" + String.format("%.2f", body.linearVelocity.length());
    }

    private static Component header(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GOLD);
    }
}
