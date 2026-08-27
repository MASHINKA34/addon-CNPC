package com.goodbird.cnpcgeckoaddon.client.gui;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/**
 * Shared scaffolding for the settings screens: the labelled, clamped number field every
 * one of them is built out of, and the grey hint that wraps itself.
 *
 * <p>This used to be a private copy in each screen, and twenty-odd copies of the same
 * seven lines is how one of them drifts. The geometry hooks exist because the screen
 * families deliberately sit their columns a pixel or three apart - each family overrides
 * its own numbers, so every screen keeps exactly the layout it had as a copy.</p>
 */
public abstract class SubGuiFieldScreen extends GuiBasic {

    private static final int HINT_COLOR = 0xA0A0A0;
    private static final int HINT_LINE_HEIGHT = 9;

    /** X offset of the label column from the screen's left edge. */
    protected int numberLabelX() {
        return 8;
    }

    /** X offset of the field column from the screen's left edge. */
    protected int numberFieldX() {
        return 172;
    }

    protected int numberFieldWidth() {
        return 70;
    }

    /** A labelled integer field that clamps itself to {@code min..max} and falls back. */
    protected void addNumberField(int id, String label, int y, int value, int min, int max, int fallback) {
        addLabel(new GuiLabel(id, label, guiLeft + numberLabelX(), y + 6));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + numberFieldX(), y,
                numberFieldWidth(), 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    /**
     * A GuiLabel draws one line and never clips it, so a hint too wide for the panel is
     * split into its own labels here rather than running off the edge of the background.
     *
     * @return the y the next thing down may start at
     */
    protected int addWrappedHint(int id, String key, int y) {
        int width = imageWidth - 16;
        StringBuilder line = new StringBuilder();
        for (String word : I18n.get(key).split(" ")) {
            if (!line.isEmpty() && font.width(line + " " + word) > width) {
                addLabel(new GuiLabel(id++, Component.literal(line.toString()), HINT_COLOR,
                        guiLeft + 8, y, width, HINT_LINE_HEIGHT));
                y += HINT_LINE_HEIGHT;
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            addLabel(new GuiLabel(id, Component.literal(line.toString()), HINT_COLOR,
                    guiLeft + 8, y, width, HINT_LINE_HEIGHT));
            y += HINT_LINE_HEIGHT;
        }
        return y;
    }
}
