package com.goodbird.cnpcgeckoaddon.client.gui;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/**
 * Shared scaffolding for the settings screens: the labelled, clamped number field every
 * one of them is built out of, and the grey hint that wraps itself.
 *
 * <p>This used to be a private copy in each screen, and twenty-odd copies of the same
 * seven lines is how one of them drifts. The geometry hooks exist because the screen
 * families deliberately sit their columns a pixel or three apart - each family overrides
 * its own numbers, so every screen keeps exactly the layout it had as a copy.</p>
 */
public abstract class SubGuiFieldScreen extends ScrollableSubGui implements ITextfieldListener {

    /** The id CustomNPCs screens give their close button. */
    protected static final int DONE_BUTTON = 66;

    private static final int HINT_COLOR = 0xA0A0A0;
    private static final int HINT_LINE_HEIGHT = 9;

    /** The panel every settings screen is drawn on; a screen that wants another sets its own. */
    protected SubGuiFieldScreen() {
        setBackground("menubg.png");
    }

    /** The close button each screen ends with, at the spot that screen puts it. */
    protected void addDoneButton(int x, int y, int width, int height) {
        addButton(new GuiButtonNop(this, DONE_BUTTON, x, y, width, height, "gui.done", button -> close()));
    }

    /**
     * Reads this screen's text fields back into the settings they belong to.
     *
     * <p>Called on every field that loses focus and once more on the way out, which is the
     * only thing that makes a value typed into the last field and then closed with Escape
     * reach the boss. Every settings screen had its own identical copy of those two
     * overrides, and thirty-nine copies of the same four lines is how one of them ends up
     * saving on close but not on tab.</p>
     */
    protected void applyFields() {
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        applyFields();
    }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

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

    protected int numberFieldHeight() {
        return 20;
    }

    /** How far below the field's top the label sits, so the two read as one row. */
    protected int numberLabelYOffset() {
        return 6;
    }

    /** A labelled integer field that clamps itself to {@code min..max} and falls back. */
    protected void addNumberField(int id, String label, int y, int value, int min, int max, int fallback) {
        addLabel(new GuiLabel(id, label, guiLeft + numberLabelX(), y + numberLabelYOffset()));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + numberFieldX(), y,
                numberFieldWidth(), numberFieldHeight(), Integer.toString(value));
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
        return addWrappedText(id, I18n.get(key), y);
    }

    /** The same, for a hint that is already a finished line rather than a translation key. */
    protected int addWrappedText(int id, String text, int y) {
        int width = imageWidth - 16;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
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
