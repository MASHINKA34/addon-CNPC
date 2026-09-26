package com.goodbird.cnpcgeckoaddon.client.gui.theme;

import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;

/**
 * CustomNPCs' yes/no button in the addon's theme: the button's face, the switch showing yes or no
 * at its right end, and the word beside it.
 *
 * <p>Still a {@link GuiButtonYesNo}, since the screens read the answer through that class; each
 * constructor is CustomNPCs' own, one for one.</p>
 */
public class ThemeYesNo extends GuiButtonYesNo {

    public ThemeYesNo(IGuiInterface gui, int id, int x, int y, boolean value, OnPress onPress) {
        super(gui, id, x, y, value, onPress);
    }

    public ThemeYesNo(IGuiInterface gui, int id, int x, int y, int width, int height, boolean value,
                      OnPress onPress) {
        super(gui, id, x, y, width, height, value, onPress);
    }

    public ThemeYesNo(IGuiInterface gui, int id, int x, int y, boolean value) {
        super(gui, id, x, y, value);
    }

    public ThemeYesNo(IGuiInterface gui, int id, int x, int y, int width, int height, boolean value) {
        super(gui, id, x, y, width, height, value);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!GeckoTheme.enabled() || !shown) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            return;
        }
        try {
            ThemeWidgets.button(graphics, this, alpha, ThemeIcons.NONE,
                    getBoolean() ? ThemeWidgets.YES : ThemeWidgets.NO);
        } catch (Throwable error) {
            CrashGuard.caught("client.gui.theme.yes_no", error);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
    }
}
