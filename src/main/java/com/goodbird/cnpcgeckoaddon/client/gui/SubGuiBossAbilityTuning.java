package com.goodbird.cnpcgeckoaddon.client.gui;

import net.minecraft.network.chat.Component;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/**
 * The scaffolding the per-ability fine-tuning pages share: a title, a stack of rows, the one
 * line that says the defaults are the old behaviour, and a Done under it.
 *
 * <p>The boss-wide tuning has {@link SubGuiBossTuningTopic} for exactly this, and cannot serve
 * here: it is handed a {@code BossTuningSettings} and titles itself out of the boss' own
 * heading, while these pages hang off one ability's settings and off its own screen. What they
 * do share is the arithmetic - where the rows go, how tall the panel ends up, where the hint
 * lands - and that is what lives here, so a page added by a later ability cannot put its Done
 * off the bottom of the panel.</p>
 */
abstract class SubGuiBossAbilityTuning extends SubGuiFieldScreen {

    protected static final int BUTTON_HEIGHT = 20;
    protected static final int ROW_HEIGHT = 22;

    private static final int FIRST_ROW_Y = 26;
    private static final int BOTTOM_MARGIN = 6;
    private static final int TITLE_LABEL = 90;
    private static final int FIRST_HINT_LABEL = 92;
    private static final String HINT = "cnpcgeckoaddon.boss.tuning_hint";

    private final String titleKey;
    private int rowY;

    protected SubGuiBossAbilityTuning(String titleKey) {
        this.titleKey = titleKey;
        imageWidth = 256;
        closeOnEsc = true;
    }

    /** How many rows this page lays out, counted before the panel is sized on it. */
    protected abstract int rows();

    /** Lays the rows out, each one at {@link #nextRow()}. */
    protected abstract void addRows();

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: how many lines the hint wraps
        // to is up to the locale, and the row count is up to the page.
        imageHeight = doneButtonY() + BUTTON_HEIGHT + BOTTOM_MARGIN;
        super.init();
        addLabel(new GuiLabel(TITLE_LABEL, Component.translatable(titleKey), 0xFFFFFF,
                guiLeft + 8, guiTop + 8, imageWidth - 16, LINE_HEIGHT));
        rowY = guiTop + FIRST_ROW_Y;
        addRows();
        addWrappedText(FIRST_HINT_LABEL, hintText(), guiTop + hintY());
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, BUTTON_HEIGHT);
    }

    /**
     * A row holding an id typed in with a picker beside it, for the handful of tuning
     * settings whose value is a registry name rather than a number.
     *
     * <p>The same three widgets the effect editor uses, at this panel's own column: a label, a
     * field and a Select. The page that adds one reads it back in {@code applyFields} and opens
     * the picker from {@code buttonEvent}, because what may be typed there is the page's to
     * judge - a potion id is not a sound id.</p>
     */
    protected void addPickerRow(int id, String labelKey, int y, String value) {
        addLabel(new GuiLabel(id, labelKey, guiLeft + 8, y + 6));
        addTextField(new GuiTextFieldNop(id, this, guiLeft + 108, y, 86, BUTTON_HEIGHT, value));
        addButton(new GuiButtonNop(this, id, guiLeft + 198, y, 44, BUTTON_HEIGHT,
                "mco.template.button.select"));
    }

    /** The top of the next row down, and moves the stack on by one. */
    protected int nextRow() {
        int at = rowY;
        rowY += ROW_HEIGHT;
        return at;
    }

    private static String hintText() {
        // Not I18n.get: it runs the text through String.format, which a hint holding a per
        // cent sign would not survive.
        return Component.translatable(HINT).getString();
    }

    private int hintY() {
        return FIRST_ROW_Y + rows() * ROW_HEIGHT + 4;
    }

    private int doneButtonY() {
        return hintY() + wrapLines(hintText(), imageWidth - 16).size() * LINE_HEIGHT + 4;
    }
}
