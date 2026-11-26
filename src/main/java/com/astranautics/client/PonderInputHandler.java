package com.astranautics.client;

import com.astranautics.AstraNautics;
import com.astranautics.client.ponder.PonderScreen;
import com.astranautics.client.ponder.PonderScene;
import com.astranautics.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「アイテムにカーソルを当てて W を長押し」で Ponder を開くハンドラ。
 *
 * <p>インベントリ（クリエイティブ含む）でスロットにカーソルを合わせ、
 * <b>W を約 0.6 秒ホールド</b>すると思考 UI が開きます。Ponder 対応アイテムは
 * 物理化装置と接着剤です。</p>
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PonderInputHandler {

    /** W のキーコード。 */
    private static final int KEY_W = 87;
    /** 開くまでのホールド tick（20 tick = 1 秒）。 */
    private static final int HOLD_TICKS = 12;

    private static int holdTicks;
    private static boolean ponderTriggered;
    private static ItemStack lastHovered = ItemStack.EMPTY;

    private PonderInputHandler() {
    }

    /** AbstractContainerScreen#hoveredSlot は protected なのでリフレクションで参照する。 */
    private static java.lang.reflect.Field hoveredSlotField;

    private static Slot hoveredSlot(AbstractContainerScreen<?> screen) {
        try {
            if (hoveredSlotField == null) {
                hoveredSlotField = net.minecraftforge.fml.util.ObfuscationReflectionHelper
                        .findField(AbstractContainerScreen.class, "hoveredSlot");
                hoveredSlotField.setAccessible(true);
            }
            return (Slot) hoveredSlotField.get(screen);
        } catch (Exception exception) {
            return null;
        }
    }

    /* ------------------------------------------------------------------ */
    /* ホバー中のアイテムを調べる                                           */
    /* ------------------------------------------------------------------ */

    private static ItemStack hoveredStack() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            Slot slot = hoveredSlot(containerScreen);
            if (slot != null && slot.hasItem()) {
                return slot.getItem();
            }
            return ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    /** そのアイテムに Ponder があるか。 */
    public static boolean hasPonder(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ModItems.PHYSICALIZER.get()) || stack.is(ModItems.ADHESIVE.get())) {
            return true;
        }
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && com.astranautics.client.ponder.PonderCatalog.forItem(id) != null;
    }

    /** そのアイテムの Ponder シーン開始インデックス。 */
    public static int sceneIndexFor(ItemStack stack) {
        if (stack.is(ModItems.ADHESIVE.get())) {
            return 1;
        }
        var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && com.astranautics.client.ponder.PonderCatalog.forItem(id) != null) {
            return com.astranautics.client.ponder.PonderCatalog.firstIndex(id);
        }
        return 0;
    }

    /* ------------------------------------------------------------------ */
    /* 入力                                                                 */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PonderScreen) {
            return;
        }
        if (!(minecraft.screen instanceof AbstractContainerScreen<?>)) {
            return;
        }
        if (event.getKeyCode() == KEY_W) {
            ItemStack hovered = hoveredStack();
            if (hasPonder(hovered) && !ponderTriggered) {
                // W 長押しの開始（バニラ側の移動キーには流さない）
                lastHovered = hovered;
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof PonderScreen) {
            return;
        }
        if (!(minecraft.screen instanceof AbstractContainerScreen<?>)) {
            holdTicks = 0;
            ponderTriggered = false;
            return;
        }
        ItemStack hovered = hoveredStack();
        boolean wDown = isWDown(minecraft);

        if (hasPonder(hovered) && wDown) {
            holdTicks++;
            if (holdTicks >= HOLD_TICKS && !ponderTriggered) {
                ponderTriggered = true;
                openPonder(minecraft, hovered);
            }
        } else {
            holdTicks = 0;
            if (!wDown) {
                ponderTriggered = false;
            }
        }
    }

    private static boolean isWDown(Minecraft minecraft) {
        if (minecraft.screen == null) {
            return false;
        }
        // キーボードの生の状態を見る（画面を開いていても押下状態は取得できる）
        long window = minecraft.getWindow().getWindow();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, KEY_W) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private static void openPonder(Minecraft minecraft, ItemStack stack) {
        int index = sceneIndexFor(stack);
        PonderScene scene = PonderScene.byIndex(index);
        if (scene == null) {
            return;
        }
        minecraft.setScreen(new PonderScreen(scene, index));
        holdTicks = 0;
        ponderTriggered = true;
    }

    /* ------------------------------------------------------------------ */
    /* ツールチップのヒント                                                 */
    /* ------------------------------------------------------------------ */

    @SubscribeEvent
    public static void onTooltip(ScreenEvent.Render.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }
        Slot slot = hoveredSlot(containerScreen);
        if (slot == null || !hasPonder(slot.getItem())) {
            return;
        }
        int hold = Math.min(HOLD_TICKS, holdTicks);
        int progress = (int) (100.0F * hold / HOLD_TICKS);
        String text = progress > 0
                ? "W 長押しで思考 UI を開く ... " + progress + "%"
                : "W 長押しで思考 UI を見る";
        var font = minecraft.font;
        int tw = font.width(text);
        int x = containerScreen.getGuiLeft() + containerScreen.getXSize() + 4;
        int y = event.getMouseY();
        if (x + tw + 4 > event.getGuiGraphics().guiWidth()) {
            x = event.getMouseX() - tw - 8;
        }
        event.getGuiGraphics().fill(x - 3, y - 3, x + tw + 3, y + 10, 0xD0101820);
        event.getGuiGraphics().fill(x - 3, y - 3, x + tw + 3, y - 2, 0xFF2AC8D2);
        event.getGuiGraphics().drawString(font, text, x, y, 0xFF8FE7EE, false);
        if (progress > 0) {
            event.getGuiGraphics().fill(x, y + 11, x + tw, y + 13, 0xFF2AC8D2);
            event.getGuiGraphics().fill(x, y + 11, x + (int) (tw * progress / 100.0F), y + 13, 0xFF5FE0EA);
        }
    }

    /** クリエイティブタブの検索欄などで誤爆しないためのフック（将来拡張用）。 */
    public static boolean isCreative(AbstractContainerScreen<?> screen) {
        return screen instanceof CreativeModeInventoryScreen || screen instanceof InventoryScreen;
    }
}
