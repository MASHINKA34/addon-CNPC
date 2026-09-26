package com.goodbird.cnpcgeckoaddon.client.gui.theme;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.util.Mth;

/**
 * What a themed button looks like, shared by the plain button and the yes/no one - the two
 * cannot share a superclass of their own, since each has to stay the CustomNPCs class the
 * screens check for.
 */
final class ThemeWidgets {

    /** No switch on the button. */
    static final int NO_TOGGLE = 0;
    static final int YES = 1;
    static final int NO = 2;

    /** How far in from a button's edges its text keeps: vanilla's own margin. */
    private static final int TEXT_INSET = 2;

    /** How far in from a button's left edge its icon sits. */
    private static final int ICON_INSET = 3;

    /** Narrower than this and a button keeps its whole width for its text. */
    private static final int MIN_ICON_WIDTH = 40;
    private static final int MIN_TOGGLE_WIDTH = GeckoTheme.TOGGLE_WIDTH * 2;

    /** How much a switch on a button that cannot be pressed is dimmed. */
    private static final float DISABLED_TINT = 0.6F;

    private ThemeWidgets() {
    }

    /**
     * Draws a button's face for its state, an icon at its left and a switch at its right where
     * it has them, and its text centred in what is left - white with a shadow, scrolled like
     * vanilla's where it is too long to fit.
     */
    static void button(GuiGraphics graphics, AbstractButton button, float alpha, int icon, int toggle) {
        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        GeckoTheme.button(graphics, x, y, width, height,
                GeckoTheme.buttonState(button.active, button.isHoveredOrFocused()));
        int textLeft = x + TEXT_INSET;
        int textRight = x + width - TEXT_INSET;
        if (toggle != NO_TOGGLE && width >= MIN_TOGGLE_WIDTH) {
            int toggleX = x + width - GeckoTheme.TOGGLE_WIDTH;
            float tint = button.active ? 1.0F : DISABLED_TINT;
            graphics.setColor(tint, tint, tint, alpha);
            GeckoTheme.toggle(graphics, toggleX, y + (height - GeckoTheme.TOGGLE_HEIGHT) / 2, toggle == YES);
            graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
            textRight = toggleX - TEXT_INSET;
        }
        if (width >= MIN_ICON_WIDTH && GeckoTheme.hasIcon(icon)) {
            GeckoTheme.icon(graphics, x + ICON_INSET, y + (height - ThemeIcons.SIZE) / 2, icon);
            textLeft = x + ICON_INSET + ThemeIcons.SIZE + TEXT_INSET;
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        int color = button.getFGColor() | Mth.ceil(alpha * 255.0F) << 24;
        AbstractWidget.renderScrollingString(graphics, Minecraft.getInstance().font, button.getMessage(),
                textLeft, y, textRight, y + height, color);
    }
}
