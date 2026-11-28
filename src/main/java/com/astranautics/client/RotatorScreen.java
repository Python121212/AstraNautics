package com.astranautics.client;

import com.astranautics.block.RotatorBlock;
import com.astranautics.physics.Rotators;
import com.astranautics.network.PhysicsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import org.lwjgl.glfw.GLFW;

/**
 * The rotator's dial: a circle in the middle, a needle at the chosen angle, click anywhere on
 * the ring to set it.
 *
 * <p>The angle is a block state property, so this screen reads it straight from the client's
 * copy of the hull and only sends a packet when the player actually picks a new angle. That
 * keeps the dialog instant and makes it impossible to desync by opening it.</p>
 */
public class RotatorScreen extends Screen {
    private static final int RADIUS = 62;
    private static final int RING_INNER = RADIUS - 12;
    private static final int RING_OUTER = RADIUS + 6;
    private static final int COLOR_BACKDROP = 0xE8141419;
    private static final int COLOR_RING = 0xFF2C2C38;
    private static final int COLOR_TICK = 0xFF5A5A6A;
    private static final int COLOR_MAJOR = 0xFF9A9AAA;
    private static final int COLOR_ACCENT = 0xFF5CC8F5;
    private static final int COLOR_WARM = 0xFFFF9E48;
    private static final int COLOR_TEXT = 0xFFC6C6CE;
    private static final int COLOR_DIM = 0xFF87878F;

    private final int shipId;
    private final BlockPos cell;
    private final Rotators.Mode mode;
    private int angle;
    private int centreX;
    private int centreY;

    public RotatorScreen(int shipId, BlockPos cell, Rotators.Mode mode, int angle) {
        super(Component.translatable(mode == Rotators.Mode.YAW
                ? "block.astranautics.yaw_rotator" : "block.astranautics.pitch_rotator"));
        this.shipId = shipId;
        this.cell = cell;
        this.mode = mode;
        this.angle = angle;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        this.centreX = this.width / 2;
        this.centreY = this.height / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int accent = this.mode == Rotators.Mode.YAW ? COLOR_ACCENT : COLOR_WARM;

        // Backdrop behind the dial.
        int boxW = RADIUS * 2 + 60;
        int boxH = RADIUS * 2 + 78;
        int left = this.centreX - boxW / 2;
        int top = this.centreY - boxH / 2 - 4;
        graphics.fill(left, top, left + boxW, top + boxH, COLOR_BACKDROP);
        graphics.fill(left, top, left + boxW, top + 2, accent);
        graphics.fill(left, top, left + 1, top + boxH, 0xFF3C3C48);
        graphics.fill(left + boxW - 1, top, left + boxW, top + boxH, 0xFF3C3C48);
        graphics.fill(left, top + boxH - 1, left + boxW, top + boxH, 0xFF3C3C48);

        graphics.drawCenteredString(this.font, this.title, this.centreX, top + 10, 0xFFFFFFFF);

        // The ring and its ticks.
        this.drawRing(graphics, accent);

        // Needle for the chosen angle: 0 is up, angles run clockwise, matching the dial.
        double radians = Math.toRadians(this.angle);
        int tipX = this.centreX + (int) Math.round(Math.sin(radians) * (RADIUS - 4));
        int tipY = this.centreY - (int) Math.round(Math.cos(radians) * (RADIUS - 4));
        this.drawHand(graphics, this.centreX, this.centreY, tipX, tipY, accent);

        // Hub and readout.
        graphics.fill(this.centreX - 3, this.centreY - 3, this.centreX + 3, this.centreY + 3, 0xFF1A1A20);
        graphics.fill(this.centreX - 2, this.centreY - 2, this.centreX + 2, this.centreY + 2, accent);
        String degrees = this.angle + "°";
        graphics.drawCenteredString(this.font, degrees, this.centreX, this.centreY + 10, 0xFFFFFFFF);

        // Hovering close to the ring previews the angle the click would pick.
        Integer hover = this.angleAt(mouseX, mouseY);
        if (hover != null && hover != this.angle) {
            double hoverRad = Math.toRadians(hover);
            int hx = this.centreX + (int) Math.round(Math.sin(hoverRad) * (RADIUS + 2));
            int hy = this.centreY - (int) Math.round(Math.cos(hoverRad) * (RADIUS + 2));
            graphics.fill(hx - 1, hy - 1, hx + 2, hy + 2, 0xFFFFFFFF);
        }

        graphics.drawCenteredString(this.font,
                Component.translatable("rotator.astranautics.hint", RotatorBlock.DEGREES_PER_STEP),
                this.centreX, top + boxH - 26, COLOR_TEXT);
        graphics.drawCenteredString(this.font,
                Component.translatable("rotator.astranautics.signal"),
                this.centreX, top + boxH - 16, COLOR_DIM);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRing(GuiGraphics graphics, int accent) {
        // A filled ring: draw the outer disc, then punch the middle out with the backdrop colour.
        this.fillDisc(graphics, this.centreX, this.centreY, RING_OUTER, COLOR_RING);
        this.fillDisc(graphics, this.centreX, this.centreY, RING_INNER, COLOR_BACKDROP);

        for (int step = 0; step < RotatorBlock.STEPS; step++) {
            double radians = Math.toRadians(step * RotatorBlock.DEGREES_PER_STEP);
            boolean major = step % 6 == 0;
            boolean current = step * RotatorBlock.DEGREES_PER_STEP == this.angle;
            int inner = major ? RADIUS - 12 : RADIUS - 7;
            int outer = RADIUS;
            int color = current ? accent : (major ? COLOR_MAJOR : COLOR_TICK);
            int x1 = this.centreX + (int) Math.round(Math.sin(radians) * inner);
            int y1 = this.centreY - (int) Math.round(Math.cos(radians) * inner);
            int x2 = this.centreX + (int) Math.round(Math.sin(radians) * outer);
            int y2 = this.centreY - (int) Math.round(Math.cos(radians) * outer);
            this.drawHand(graphics, x1, y1, x2, y2, color);
            if (major) {
                String label = String.valueOf(step * RotatorBlock.DEGREES_PER_STEP);
                int lx = this.centreX + (int) Math.round(Math.sin(radians) * (RADIUS + 18));
                int ly = this.centreY - (int) Math.round(Math.cos(radians) * (RADIUS + 18));
                graphics.drawCenteredString(this.font, label, lx, ly - 4, COLOR_DIM);
            }
        }
    }

    private void fillDisc(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int half = (int) Math.floor(Math.sqrt((double) radius * radius - (double) dy * dy));
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** A 1px line, in the GUI's own coordinates. */
    private void drawHand(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int x = x1;
        int y = y1;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x2 && y == y2) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    /**
     * Angle of the dial under the mouse, snapped to a step, or {@code null} when the mouse is
     * not near the ring.
     */
    private Integer angleAt(double mouseX, double mouseY) {
        double dx = mouseX - this.centreX;
        double dy = mouseY - this.centreY;
        double distance = Math.hypot(dx, dy);
        if (distance < RING_INNER - 4 || distance > RING_OUTER + 12) {
            return null;
        }
        double degrees = Math.toDegrees(Math.atan2(dx, -dy));
        int step = RotatorBlock.stepFor(degrees);
        return step * RotatorBlock.DEGREES_PER_STEP;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Integer picked = this.angleAt(mouseX, mouseY);
            if (picked != null) {
                this.angle = picked;
                PhysicsNetwork.CHANNEL.sendToServer(new PhysicsNetwork.SetRotator(shipId, cell, this.angle));
                if (this.minecraft != null) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4F));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Fine adjustment without the mouse, one step at a time.
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_UP) {
            return this.turn(1);
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_DOWN) {
            return this.turn(-1);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean turn(int steps) {
        this.angle = Math.floorMod(this.angle + steps * RotatorBlock.DEGREES_PER_STEP, 360);
        PhysicsNetwork.CHANNEL.sendToServer(new PhysicsNetwork.SetRotator(shipId, cell, this.angle));
        return true;
    }
}
