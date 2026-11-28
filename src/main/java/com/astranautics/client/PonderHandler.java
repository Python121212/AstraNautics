package com.astranautics.client;

import com.astranautics.AstraNautics;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * "Hold W over an item to see what it is for" - the ponder trigger.
 *
 * <p>Works in every container screen (inventory, creative menu, a chest) because it looks at
 * the hovered slot of whatever {@link AbstractContainerScreen} is open rather than hooking a
 * specific GUI. Holding W does nothing else while a container is open, so nothing conflicts.</p>
 *
 * <p>The hint and its progress bar are drawn under the screen; the window itself is opened
 * from the client tick, never from a render callback, so no screen is swapped mid-frame.</p>
 */
@Mod.EventBusSubscriber(modid = AstraNautics.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class PonderHandler {
    /** How long W must be held: 0.6 s is long enough not to fire by accident. */
    private static final int HOLD_TICKS = 12;
    /** -1 means "already opened, wait for the key to be released". */
    private static int holdTicks;
    private static ItemStack hovered = ItemStack.EMPTY;

    private PonderHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            reset();
            return;
        }
        Screen screen = minecraft.screen;
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            reset();
            return;
        }

        ItemStack current = hoveredStack(minecraft, container);
        if (current.isEmpty() || !PonderRegistry.has(current)) {
            hovered = ItemStack.EMPTY;
            holdTicks = 0;
            return;
        }
        if (!ItemStack.isSameItemSameTags(current, hovered)) {
            hovered = current.copyWithCount(1);
            holdTicks = 0;
        }
        if (holdTicks < 0) {
            // Already shown once for this hold; only a release re-arms it.
            return;
        }
        if (!isHoldKeyDown(minecraft)) {
            holdTicks = 0;
            return;
        }

        holdTicks++;
        if (holdTicks >= HOLD_TICKS) {
            holdTicks = -1;
            PonderEntry entry = PonderRegistry.of(current);
            if (entry != null) {
                minecraft.setScreen(new PonderScreen(entry, current));
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F));
            }
        }
    }

    /** Draws the "hold W" prompt and its fill bar along the bottom of the screen. */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) {
            return;
        }
        if (hovered.isEmpty() || holdTicks < 0) {
            return;
        }
        Screen screen = event.getScreen();
        GuiGraphics graphics = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;

        String label = Component.translatable("ponder.astranautics.hold").getString();
        int cap = 12;
        int gap = 4;
        int barWidth = 64;
        int textWidth = font.width(label);
        int totalWidth = cap + gap + textWidth + gap + barWidth;
        int x = (screen.width - totalWidth) / 2;
        int y = screen.height - 54;

        graphics.fill(x - 5, y - 4, x + totalWidth + 5, y + cap + 4, 0xB814141A);
        graphics.fill(x - 5, y - 4, x + totalWidth + 5, y - 3, 0xFF3C3C48);
        graphics.fill(x - 5, y + cap + 3, x + totalWidth + 5, y + cap + 4, 0xFF3C3C48);

        // Key cap with the letter on it.
        graphics.fill(x, y, x + cap, y + cap, 0xFF26262E);
        graphics.fill(x, y, x + cap, y + 1, 0xFF5C5C6E);
        graphics.fill(x, y + cap - 1, x + cap, y + cap, 0xFF14141A);
        graphics.drawString(font, "W", x + (cap - font.width("W")) / 2, y + 2, 0xFFFFFFFF, false);

        graphics.drawString(font, label, x + cap + gap, y + 3, 0xFFB8B8C4, false);

        // Progress fill.
        int barX = x + cap + gap + textWidth + gap;
        int barY = y + 4;
        graphics.fill(barX, barY, barX + barWidth, barY + 4, 0xFF2A2A34);
        float progress = Math.min(1.0F, holdTicks / (float) HOLD_TICKS);
        int filled = Math.round(barWidth * progress);
        if (filled > 0) {
            graphics.fill(barX, barY, barX + filled, barY + 4, 0xFF5CC8F5);
        }
    }

    private static boolean isHoldKeyDown(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        return GLFW.glfwGetKey(window.getWindow(), GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
    }

    private static void reset() {
        hovered = ItemStack.EMPTY;
        holdTicks = 0;
    }

    /** The stack under the mouse, found from the slots themselves so no screen internals are needed. */
    private static ItemStack hoveredStack(Minecraft minecraft, AbstractContainerScreen<?> screen) {
        Window window = minecraft.getWindow();
        double scaleX = window.getGuiScaledWidth() / (double) Math.max(1, window.getScreenWidth());
        double scaleY = window.getGuiScaledHeight() / (double) Math.max(1, window.getScreenHeight());
        double mouseX = minecraft.mouseHandler.xpos() * scaleX;
        double mouseY = minecraft.mouseHandler.ypos() * scaleY;

        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) {
                continue;
            }
            int slotX = screen.getGuiLeft() + slot.x;
            int slotY = screen.getGuiTop() + slot.y;
            if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
                return slot.getItem();
            }
        }
        return ItemStack.EMPTY;
    }
}
