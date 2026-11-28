package com.astranautics.command;

import com.astranautics.AstraNautics;
import com.astranautics.entity.PhysicsShip;
import com.astranautics.physics.EntityShipCollision;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /astranautics watch} — samples the player (and the hull under their feet) for a
 * second and reports how much each one actually moved.
 *
 * <p>It answers the only question that matters when the view still shakes: is it the player
 * that moves, or the hull? A deck that is awake and micro-moving is invisible on its own but
 * drags the camera with it.</p>
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShipWatch {
    private static final int SAMPLE_TICKS = 60;
    private static final Map<UUID, Sample> SAMPLES = new HashMap<>();

    private ShipWatch() {
    }

    static void start(ServerPlayer player) {
        SAMPLES.put(player.getUUID(), new Sample(player.getY()));
        player.sendSystemMessage(Component.literal("[watch] " + SAMPLE_TICKS
                + " tick 計測します。その場に立ったままお待ちください").withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SAMPLES.isEmpty()
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        Sample sample = SAMPLES.get(player.getUUID());
        if (sample == null) {
            return;
        }

        double y = player.getY();
        if (sample.ticks > 0) {
            sample.playerStep = Math.max(sample.playerStep, Math.abs(y - sample.lastY));
        }
        sample.lastY = y;
        sample.minY = Math.min(sample.minY, y);
        sample.maxY = Math.max(sample.maxY, y);

        ServerLevel level = (ServerLevel) player.level();
        int shipId = EntityShipCollision.supportShipId(player, level.getGameTime());
        if (shipId >= 0) {
            sample.supportedTicks++;
            sample.shipId = shipId;
            Entity entity = level.getEntity(shipId);
            if (entity instanceof PhysicsShip ship) {
                double shipY = ship.getY();
                if (sample.shipSamples > 0) {
                    sample.shipStep = Math.max(sample.shipStep, Math.abs(shipY - sample.lastShipY));
                }
                sample.lastShipY = shipY;
                sample.minShipY = Math.min(sample.minShipY, shipY);
                sample.maxShipY = Math.max(sample.maxShipY, shipY);
                sample.shipSamples++;
                sample.sleeping = ship.isSleeping();
                sample.shipSpeed = ship.getBody().linearVelocity.length();
                sample.shipSpin = ship.getBody().angularVelocity.length();
            }
        }

        sample.ticks++;
        if (sample.ticks >= SAMPLE_TICKS) {
            SAMPLES.remove(player.getUUID());
            report(player, sample);
        }
    }

    private static void report(ServerPlayer player, Sample sample) {
        double yRange = sample.maxY - sample.minY;
        send(player, "── watch: " + sample.ticks + " tick ──", ChatFormatting.GOLD);
        send(player, String.format(Locale.ROOT,
                "プレイヤーY 変化幅=%.5f  1tick最大=%.5f", yRange, sample.playerStep),
                yRange > 0.0005D ? ChatFormatting.RED : ChatFormatting.GREEN);

        if (sample.shipId < 0) {
            send(player, "支持: 0/" + sample.ticks + " tick（船の上に立っていません）", ChatFormatting.YELLOW);
            return;
        }

        send(player, "支持: " + sample.supportedTicks + "/" + sample.ticks + " tick  ship=#"
                + sample.shipId, ChatFormatting.AQUA);
        if (sample.shipSamples == 0) {
            send(player, "船の座標が取れませんでした", ChatFormatting.RED);
            return;
        }
        double shipRange = sample.maxShipY - sample.minShipY;
        send(player, String.format(Locale.ROOT,
                "船Y 変化幅=%.5f  1tick最大=%.5f", shipRange, sample.shipStep),
                shipRange > 0.0005D ? ChatFormatting.RED : ChatFormatting.GREEN);
        send(player, String.format(Locale.ROOT,
                "船 速度=%.4f  角速度=%.4f  睡眠=%s",
                sample.shipSpeed, sample.shipSpin, sample.sleeping),
                sample.sleeping ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        send(player, "→ 赤い行が「動いている側」です。それを報告してください", ChatFormatting.GRAY);
    }

    private static void send(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    private static final class Sample {
        private int ticks;
        private double minY = Double.MAX_VALUE;
        private double maxY = -Double.MAX_VALUE;
        private double lastY;
        private double playerStep;
        private int supportedTicks;
        private int shipId = -1;
        private double minShipY = Double.MAX_VALUE;
        private double maxShipY = -Double.MAX_VALUE;
        private double lastShipY;
        private double shipStep;
        private int shipSamples;
        private boolean sleeping;
        private double shipSpeed;
        private double shipSpin;

        private Sample(double y) {
            this.lastY = y;
            this.minY = y;
            this.maxY = y;
        }
    }
}
