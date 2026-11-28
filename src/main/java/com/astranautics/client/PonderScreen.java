package com.astranautics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The "ponder" window: a small panel that explains what an item does and how to use it.
 *
 * <p>Opened by {@link PonderHandler} when W is held over an item that has a
 * {@link PonderEntry}. Deliberately not a 3D animated scene like Create's ponder - it is a
 * readable card with pages, which fits in a mod that already talks to the player through the
 * action bar.</p>
 */
public class PonderScreen extends Screen {
    private static final int PANEL_WIDTH = 284;
    private static final int PANEL_HEIGHT = 172;
    private static final int PADDING = 14;
    private static final int TITLE_Y = 12;
    private static final int DIVIDER_Y = 38;
    private static final int PAGE_TITLE_Y = 48;
    private static final int BODY_Y = 68;
    private static final int LINE_HEIGHT = 11;

    private static final int COLOR_PANEL = 0xE8141419;
    private static final int COLOR_BORDER = 0xFF3C3C48;
    private static final int COLOR_ACCENT = 0xFF5CC8F5;
    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_BODY = 0xFFC6C6CE;
    private static final int COLOR_DIM = 0xFF87878F;

    private final PonderEntry entry;
    private final ItemStack stack;
    private int page;

    public PonderScreen(PonderEntry entry, ItemStack stack) {
        super(entry.title());
        this.entry = entry;
        this.stack = stack.copy();
        this.stack.setCount(1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;

        graphics.fill(left, top, right, bottom, COLOR_PANEL);
        // Accent strip along the top edge, then a plain frame.
        graphics.fill(left, top, right, top + 2, COLOR_ACCENT);
        graphics.fill(left, top, left + 1, bottom, COLOR_BORDER);
        graphics.fill(right - 1, top, right, bottom, COLOR_BORDER);
        graphics.fill(left, bottom - 1, right, bottom, COLOR_BORDER);

        // Item icon at twice the size, then its name.
        graphics.pose().pushPose();
        graphics.pose().translate((float) (left + PADDING), (float) (top + TITLE_Y - 2), 0.0F);
        graphics.pose().scale(1.6F, 1.6F, 1.0F);
        graphics.renderItem(this.stack, 0, 0);
        graphics.pose().popPose();
        graphics.drawString(this.font, this.title, left + PADDING + 32, top + TITLE_Y + 2, COLOR_TITLE, false);

        graphics.fill(left + PADDING, top + DIVIDER_Y, right - PADDING, top + DIVIDER_Y + 1, COLOR_BORDER);

        PonderEntry.Page current = this.entry.pages().get(Math.min(this.page, this.entry.pageCount() - 1));
        graphics.drawString(this.font, current.title(), left + PADDING, top + PAGE_TITLE_Y, COLOR_ACCENT, false);

        int textWidth = PANEL_WIDTH - PADDING * 2;
        List<FormattedCharSequence> lines = this.font.split(current.body(), textWidth);
        int y = top + BODY_Y;
        for (FormattedCharSequence line : lines) {
            if (y > bottom - 26) {
                break;
            }
            graphics.drawString(this.font, line, left + PADDING, y, COLOR_BODY, false);
            y += LINE_HEIGHT;
        }

        // Footer: how to move between pages, and where we are.
        String hint = this.entry.pageCount() > 1 ? "← →  /  ホイール" : "Esc";
        graphics.drawString(this.font, Component.translatable("ponder.astranautics.hint", hint),
                left + PADDING, bottom - 16, COLOR_DIM, false);
        String counter = (this.page + 1) + " / " + this.entry.pageCount();
        graphics.drawString(this.font, counter, right - PADDING - this.font.width(counter),
                bottom - 16, COLOR_DIM, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_DOWN) {
            return this.turnPage(1);
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_UP) {
            return this.turnPage(-1);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0.0D) {
            return this.turnPage(delta > 0.0D ? 1 : -1);
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** Wraps around, so a two page entry is one key press each way. */
    private boolean turnPage(int step) {
        int count = this.entry.pageCount();
        if (count <= 1) {
            return false;
        }
        this.page = Math.floorMod(this.page + step, count);
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                    .forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
        return true;
    }
}
