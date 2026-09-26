package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.data.BossTuningSettings;
import net.minecraft.network.chat.Component;

/**
 * The scaffolding the seven fine-tuning screens share: a title, a stack of rows, the one line
 * that says the defaults are the old behaviour, and a Done under it.
 *
 * <p>Each topic says how many rows it has and fills them in; everything about where they go,
 * how tall the panel ends up and where the hint lands is settled here. The screens are plain
 * lists of numbers and cue buttons, and seven copies of the same twenty lines of arithmetic
 * is how one of them ends up with its Done off the bottom of the panel.</p>
 */
abstract class SubGuiBossTuningTopic extends SubGuiFieldScreen {

    protected static final int BUTTON_HEIGHT = 20;
    protected static final int ROW_HEIGHT = 22;

    private static final int FIRST_ROW_Y = 26;
    private static final int BOTTOM_MARGIN = 6;
    private static final int TITLE_LABEL = 90;
    private static final int FIRST_HINT_LABEL = 92;
    private static final String HINT = "cnpcgeckoaddon.boss.tuning_hint";

    protected final BossTuningSettings tuning;
    private final String titleKey;
    private int rowY;

    protected SubGuiBossTuningTopic(String titleKey, BossTuningSettings tuning) {
        this.titleKey = titleKey;
        this.tuning = tuning;
        imageWidth = 256;
        closeOnEsc = true;
    }

    /** How many rows this topic lays out, counted before the panel is sized on it. */
    protected abstract int rows();

    /** Lays the rows out, each one at {@link #nextRow()}. */
    protected abstract void addRows();

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: how many lines the hint wraps
        // to is up to the locale, and the row count is up to the topic.
        imageHeight = doneButtonY() + BUTTON_HEIGHT + BOTTOM_MARGIN;
        super.init();
        addLabel(new ThemeLabel(TITLE_LABEL, Component.translatable("cnpcgeckoaddon.boss.tuning_title")
                .append(": ").append(Component.translatable(titleKey)), 0xFFFFFF,
                guiLeft + 8, guiTop + 8, imageWidth - 16, LINE_HEIGHT));
        rowY = guiTop + FIRST_ROW_Y;
        addRows();
        addWrappedText(FIRST_HINT_LABEL, hintText(), guiTop + hintY());
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, BUTTON_HEIGHT);
    }

    /** The top of the next row down, and moves the stack on by one. */
    protected int nextRow() {
        int at = rowY;
        rowY += ROW_HEIGHT;
        return at;
    }

    private static String hintText() {
        return Component.translatable(HINT).getString();
    }

    private int hintY() {
        return FIRST_ROW_Y + rows() * ROW_HEIGHT + 4;
    }

    private int doneButtonY() {
        return hintY() + wrapLines(hintText(), imageWidth - 16).size() * LINE_HEIGHT + 4;
    }
}
