package com.astranautics.event;

import com.astranautics.AstraNautics;
import com.astranautics.block.PhysicalizedBlockEntity;
import com.astranautics.physics.PhysicsRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 物理演算とプレイヤー／爆発をつなぐイベント。
 *
 * <ul>
 *   <li>素手で物理化ブロックを殴る → 視線方向へ吹き飛ばす（狙いをつけて発射できる）</li>
 *   <li>素手で叩き続けると「押す」動きになる</li>
 *   <li>爆発 → 範囲内の物理化ブロックが吹き飛ぶ</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PhysicsEvents {

    /** 殴ったときの射出速度。 */
    private static final double SMACK_POWER = 0.42D;

    private PhysicsEvents() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }
        if (!(level.getBlockEntity(event.getPos()) instanceof PhysicalizedBlockEntity be)) {
            return;
        }
        Player player = event.getEntity();
        if (be.isPhysicsEnabled()) {
            // 物理演算中：視線方向へ吹き飛ばす
            Vec3 look = player.getLookAngle();
            be.wake();
            be.addImpulse(look.x * SMACK_POWER, look.y * SMACK_POWER + 0.16D, look.z * SMACK_POWER);
            level.playSound(null, event.getPos(),
                    net.minecraft.sounds.SoundEvents.ITEM_FRAME_ROTATE_ITEM,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.4F);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.astranautics.smack", String.format("%.2f", be.getMaterial().mass()))
                        .withStyle(net.minecraft.ChatFormatting.AQUA), true);
            }
        } else {
            // 停止中：軽く揺らして起こす
            be.wake();
            be.addImpulse(0.0D, 0.05D, 0.0D);
        }
        event.setCanceled(false);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Vec3 center = event.getExplosion().getPosition();
        PhysicsRuntime.applyExplosion(serverLevel, center, 6.0D);
    }
}
