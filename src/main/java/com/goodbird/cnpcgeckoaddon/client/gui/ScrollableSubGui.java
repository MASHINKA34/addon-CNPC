package com.goodbird.cnpcgeckoaddon.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public abstract class ScrollableSubGui extends GuiBasic {
    private static final int MARGIN = 8;
    private static final int SCROLL_STEP = 24;

    /** The sheet size every {@code GuiGraphics.blit} overload GuiBasic reaches for assumes. */
    private static final int PANEL_SHEET = 256;

    /** How tall the panel drawn into menubg.png is; the rest of the sheet below it is empty. */
    private static final int PANEL_HEIGHT = 217;

    /** The border and rounded corners at each end of the panel, which cannot be stretched. */
    private static final int PANEL_CAP = 4;

    /** The panel's middle, whose rows are all identical, so one slab of it tiles to any height. */
    private static final int PANEL_BODY = PANEL_HEIGHT - PANEL_CAP * 2;

    private int scrollOffset;
    private boolean draggingScrollbar;
    private double dragOffset;
    private boolean relayout;

    @Override
    public void init() {
        super.init();
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
        draggingScrollbar = false;
        if (maxScroll() > 0) {
            guiTop = MARGIN - scrollOffset;
        }
    }

    private int maxScroll() {
        return Math.max(0, imageHeight - viewportHeight());
    }

    private int viewportHeight() {
        return Math.max(1, height - MARGIN * 2);
    }

    private int thumbHeight() {
        return Math.min(viewportHeight(), Math.max(16, viewportHeight() * viewportHeight() / imageHeight));
    }

    private int thumbTop() {
        return MARGIN + (viewportHeight() - thumbHeight()) * scrollOffset / maxScroll();
    }

    private void scrollTo(int offset) {
        int next = Mth.clamp(offset, 0, maxScroll());
        int shift = scrollOffset - next;
        if (shift == 0) {
            return;
        }
        scrollOffset = next;
        guiTop += shift;
        Set<AbstractWidget> widgets = Collections.newSetFromMap(new IdentityHashMap<>());
        widgets.addAll(wrapper.npcbuttons.values());
        widgets.addAll(wrapper.textfields.values());
        widgets.addAll(wrapper.labels.values());
        widgets.addAll(wrapper.sliders.values());
        widgets.forEach(widget -> widget.setY(widget.getY() + shift));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (super.mouseScrolled(mouseX, mouseY, horizontal, vertical)) {
            return true;
        }
        if (maxScroll() == 0 || vertical == 0) {
            return false;
        }
        scrollTo(scrollOffset - (int) Math.round(vertical * SCROLL_STEP));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = guiLeft + imageWidth + 2;
        if (!hasSubGui() && maxScroll() > 0 && button == 0 && mouseX >= left && mouseX < left + 6
                && mouseY >= MARGIN && mouseY < height - MARGIN) {
            draggingScrollbar = true;
            dragOffset = mouseY >= thumbTop() && mouseY < thumbTop() + thumbHeight()
                    ? mouseY - thumbTop() : thumbHeight() / 2.0;
            dragScrollbar(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void dragScrollbar(double mouseY) {
        int travel = viewportHeight() - thumbHeight();
        if (travel > 0) {
            scrollTo((int) Math.round((mouseY - MARGIN - dragOffset) * maxScroll() / travel));
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0 && !hasSubGui()) {
            dragScrollbar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        boolean handled = super.keyPressed(key, scanCode, modifiers);
        if (hasSubGui() || maxScroll() == 0) {
            return handled;
        }
        if (!handled && (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN)) {
            scrollTo(scrollOffset + (key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1) * viewportHeight());
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB && getFocused() instanceof AbstractWidget widget) {
            if (widget.getY() < MARGIN) {
                scrollTo(scrollOffset + widget.getY() - MARGIN);
            } else if (widget.getBottom() > height - MARGIN) {
                scrollTo(scrollOffset + widget.getBottom() - height + MARGIN);
            }
        }
        return handled;
    }

    /**
     * Rebuilds the screen before the next frame is drawn.
     *
     * <p>A screen whose set of rows depends on a setting has to lay itself out again when that
     * setting changes, and {@code buttonEvent} runs from inside the click walk over
     * {@code children()} - clearing that list there, which {@code init} does, is a crash.
     * {@link #render} is the next moment nothing is iterating.</p>
     */
    protected void requestLayout() {
        relayout = true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (relayout && !hasSubGui()) {
            relayout = false;
            init();
        }
        if (background != null && imageWidth <= PANEL_SHEET && imageHeight > PANEL_HEIGHT) {
            renderTall(graphics, mouseX, mouseY, partialTick);
        } else {
            super.render(graphics, mouseX, mouseY, partialTick);
        }
        if (!hasSubGui() && maxScroll() > 0) {
            int left = guiLeft + imageWidth + 2;
            graphics.fill(left, MARGIN, left + 6, height - MARGIN, 0xFF303030);
            graphics.fill(left, thumbTop(), left + 6, thumbTop() + thumbHeight(), 0xFFB0B0B0);
        }
    }

    /**
     * Draws the panel GuiBasic would have drawn, and draws it in one piece.
     *
     * <p>GuiBasic lays the background down as a single quad, and the blit it uses hardcodes a
     * {@value #PANEL_SHEET}px sheet. The panel inside menubg.png is only {@value #PANEL_HEIGHT}px
     * of that, so a taller screen samples past the bottom of the texture - which carries no
     * mcmeta and therefore no clamp, and so repeats. What that draws is a transparent band where
     * the panel art runs out, a second copy of the panel's top border starting at
     * {@value #PANEL_SHEET}px, and every row that lands in the band left sitting on the dimmed
     * world.</p>
     *
     * <p>The dim and the panel have to go down in that order and before the widgets, and
     * GuiBasic draws both itself, so it is told it has neither for the length of the call.</p>
     */
    private void renderTall(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation panel = background;
        boolean dim = drawDefaultBackground;
        if (dim && !hasSubGui()) {
            renderBackground(graphics, mouseX, mouseY, partialTick);
        }
        int body = imageHeight - PANEL_CAP * 2;
        graphics.blit(panel, guiLeft, guiTop, 0, 0, imageWidth, PANEL_CAP);
        for (int drawn = 0; drawn < body; drawn += PANEL_BODY) {
            graphics.blit(panel, guiLeft, guiTop + PANEL_CAP + drawn, 0, PANEL_CAP,
                    imageWidth, Math.min(PANEL_BODY, body - drawn));
        }
        graphics.blit(panel, guiLeft, guiTop + imageHeight - PANEL_CAP, 0, PANEL_HEIGHT - PANEL_CAP,
                imageWidth, PANEL_CAP);
        background = null;
        drawDefaultBackground = false;
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
        } finally {
            background = panel;
            drawDefaultBackground = dim;
        }
    }
}
