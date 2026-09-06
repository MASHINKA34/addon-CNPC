package com.goodbird.cnpcgeckoaddon.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.util.Mth;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public abstract class ScrollableSubGui extends GuiBasic {
    private static final int MARGIN = 8;
    private static final int SCROLL_STEP = 24;
    private int scrollOffset;
    private boolean draggingScrollbar;
    private double dragOffset;

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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hasSubGui() && maxScroll() > 0) {
            int left = guiLeft + imageWidth + 2;
            graphics.fill(left, MARGIN, left + 6, height - MARGIN, 0xFF303030);
            graphics.fill(left, thumbTop(), left + 6, thumbTop() + thumbHeight(), 0xFFB0B0B0);
        }
    }
}
