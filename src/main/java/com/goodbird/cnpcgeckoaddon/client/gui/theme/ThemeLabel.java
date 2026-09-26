package com.goodbird.cnpcgeckoaddon.client.gui.theme;

import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/**
 * CustomNPCs' label, readable on the theme's panel: a colour too dark for it - the grey
 * CustomNPCs gives every label by default, picked for its light stone panel - is drawn in the
 * theme's light grey while the theme is on. Every other colour, and everything else about the
 * label, is left as the screen set it.
 *
 * <p>The label remembers the colour it was given, since CustomNPCs keeps it where nothing else
 * can read it. That colour is also how the screen's title is found for the theme's title strip:
 * titles are the white labels in the panel's top left corner. Each constructor is CustomNPCs'
 * own, one for one.</p>
 */
public class ThemeLabel extends GuiLabel {

    /** The colour every screen's title is given. */
    public static final int TITLE_COLOR = 0xFFFFFF;

    private int color;

    public ThemeLabel(int id, Component label, int color, int x, int y, int width, int height) {
        super(id, label, color, x, y, width, height);
        this.color = color;
    }

    public ThemeLabel(int id, String label, int x, int y) {
        super(id, label, x, y);
        this.color = CustomNpcResourceListener.DefaultTextColor;
    }

    public ThemeLabel(int id, String label, int x, int y, String tooltip) {
        super(id, label, x, y, tooltip);
        this.color = CustomNpcResourceListener.DefaultTextColor;
    }

    public ThemeLabel(int id, String label, int x, int y, int color) {
        super(id, label, x, y, color);
        this.color = color;
    }

    public ThemeLabel(int id, String label, int x, int y, int width, int height) {
        super(id, label, x, y, width, height);
        this.color = CustomNpcResourceListener.DefaultTextColor;
    }

    public ThemeLabel(int id, String label, int x, int y, int color, int width, int height) {
        super(id, label, x, y, color, width, height);
        this.color = color;
    }

    @Override
    public void setColor(int color) {
        this.color = color;
        super.setColor(color);
    }

    /** Whether this label is drawn in the white screens give their titles. */
    public boolean isTitleColored() {
        return (color & 0xFFFFFF) == TITLE_COLOR;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int drawn = GeckoTheme.enabled() ? ThemeGate.panelTextColor(color) : color;
        if (drawn == color) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            return;
        }
        super.setColor(drawn);
        try {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        } catch (Throwable error) {
            CrashGuard.caught("client.gui.theme.label", error);
        } finally {
            super.setColor(color);
        }
    }
}
