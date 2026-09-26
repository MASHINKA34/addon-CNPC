package com.goodbird.cnpcgeckoaddon.client.gui.theme;

import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/**
 * CustomNPCs' text field in the addon's theme: the theme's frame instead of vanilla's, the text
 * white with its shadow, and everything else - typing, the clamp, the red of a number out of
 * range, the cursor - CustomNPCs' own.
 *
 * <p>Vanilla draws its frame and its text in one method, so the frame is hidden from it for the
 * length of that call by answering "not bordered" - while the text is still laid out where a
 * bordered field puts it: vanilla places the text by its own field, and the width it cuts the
 * text to is answered here.</p>
 */
public class ThemeTextField extends GuiTextFieldNop {

    /** CustomNPCs' colour for a field holding a valid value. */
    private static final int PLAIN_TEXT = 0xE0E0E0;
    private static final int THEME_TEXT = 0xFFFFFF;

    /** A light line round the field that has the keyboard, as vanilla's highlighted frame does. */
    private static final int FOCUS_OUTLINE = 0xA0FFFFFF;

    /** The inset vanilla gives a bordered field's text on each side. */
    private static final int BORDER_INSET = 4;

    /** Set only while vanilla draws, with the theme's frame already down under it. */
    private boolean themeFrame;

    public ThemeTextField(int id, Screen parent, int x, int y, int width, int height, String text) {
        super(id, parent, x, y, width, height, text);
    }

    public ThemeTextField(int id, Screen parent, int x, int y, int width, int height, Component text) {
        super(id, parent, x, y, width, height, text);
    }

    @Override
    public int getTextColor() {
        int color = super.getTextColor();
        return color == PLAIN_TEXT && GeckoTheme.enabled() ? THEME_TEXT : color;
    }

    @Override
    public boolean isBordered() {
        return !themeFrame && super.isBordered();
    }

    @Override
    public int getInnerWidth() {
        return themeFrame ? getWidth() - BORDER_INSET * 2 : super.getInnerWidth();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!GeckoTheme.enabled() || !enabled || !isVisible() || !super.isBordered()) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            return;
        }
        try {
            GeckoTheme.field(graphics, getX(), getY(), getWidth(), getHeight());
            if (isFocused()) {
                graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), FOCUS_OUTLINE);
            }
        } catch (Throwable error) {
            CrashGuard.caught("client.gui.theme.field", error);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            return;
        }
        themeFrame = true;
        try {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        } finally {
            themeFrame = false;
        }
    }
}
