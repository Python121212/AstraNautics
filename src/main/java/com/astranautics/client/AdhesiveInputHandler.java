package com.astranautics.client;

import com.astranautics.AstraNautics;
import com.astranautics.item.AdhesiveItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 接着剤の「左クリックで確定」を拾うハンドラ。
 *
 * <p>ブロックを殴ったとき（{@code LEFT_CLICK_BLOCK}）はキャンセルして、破壊せずに
 * 選択の確定だけを行います。空を殴ったときはクライアント側イベントが来ないため、
 * 範囲選択中は「右クリック（選択）→ 左クリック（確定）」の流れを作っています。</p>
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AdhesiveInputHandler {

    private AdhesiveInputHandler() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        // クライアント側でだけ処理（確定はパケットでサーバーへ送る）
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof AdhesiveItem)) {
            return;
        }
        if (AdhesiveItem.getSelection(stack).isEmpty()) {
            return;
        }
        AdhesiveItem.confirmSelection(event.getEntity(), stack);
        event.setCanceled(true);
        // 破壊が始まらないよう、ブロック更新も止める
        event.setUseBlock(PlayerInteractEvent.LeftClickBlock.Action.PASS)
                .setUseItem(PlayerInteractEvent.LeftClickBlock.Action.PASS);
    }

    /** 手に持っている接着剤（メイン→オフハンド）。 */
    static ItemStack heldAdhesive(Minecraft minecraft) {
        if (minecraft.player == null) {
            return ItemStack.EMPTY;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            if (stack.getItem() instanceof AdhesiveItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
