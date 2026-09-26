package com.goodbird.cnpcgeckoaddon.client.gui.theme;

import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;

/**
 * A CustomNPCs button that draws itself in the addon's theme while the theme is on, and exactly
 * as CustomNPCs does otherwise.
 *
 * <p>Only the drawing differs. It is still a {@link GuiButtonNop} - the same id, place, size,
 * cycling values and click - so a screen swaps the class it builds and nothing else. Each
 * constructor is CustomNPCs' own, one for one.</p>
 */
public class ThemeButton extends GuiButtonNop {

    private int icon = ThemeIcons.NONE;

    public ThemeButton(IGuiInterface gui, int id, int x, int y, String label) {
        super(gui, id, x, y, label);
    }

    public ThemeButton(IGuiInterface gui, int id, int x, int y, String[] display, int value) {
        super(gui, id, x, y, display, value);
    }

    public ThemeButton(IGuiInterface gui, int id, int x, int y, int width, int height, String label) {
        super(gui, id, x, y, width, height, label);
    }

    public ThemeButton(IGuiInterface gui, int id, int x, int y, int width, int height, String label,
                       OnPress onPress) {
        super(gui, id, x, y, width, height, label, onPress);
    }

    public ThemeButton(IGuiInterface gui, int id, int x, int y, int width, int height, String label,
                       boolean enabled) {
        super(gui, id, x, y, width, height, label, enabled);
    }

    public ThemeButton(IGuiInterface gui, int id, int x, int y, int width, int height, String[] display,
                       int value) {
        super(gui, id, x, y, width, height, display, value);
    }

    public ThemeButton(IGuiInterface gui, int id, int x, int y, int width, int height, int value,
                       String... display) {
        super(gui, id, x, y, width, height, value, display);
    }

    public ThemeButton(IGuiInterface gui, int id, int x, int y, int width, int height, OnPress onPress,
                       int value, String... display) {
        super(gui, id, x, y, width, height, onPress, value, display);
    }

    /**
     * Puts an icon before the text while the theme is on: an ability's kind, or a menu section
     * from {@link ThemeIcons}. An index with nothing drawn for it leaves the button as it was.
     */
    public ThemeButton withIcon(int index) {
        this.icon = index;
        return this;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!GeckoTheme.enabled() || !shown) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            return;
        }
        try {
            ThemeWidgets.button(graphics, this, alpha, icon, ThemeWidgets.NO_TOGGLE);
        } catch (Throwable error) {
            CrashGuard.caught("client.gui.theme.button", error);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
    }
}
