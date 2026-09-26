package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.data.BossTuningSettings;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;

/**
 * The way into the boss' fine-tuning: one button per theme, the way the phase menu is one
 * button per ability.
 *
 * <p>Sixty-odd numbers on one screen is a wall nobody reads, and none of them is something a
 * builder goes looking for twice - so they are split by what they are trim on, and the line
 * at the bottom says the whole set is the old behaviour until it is touched.</p>
 */
public final class SubGuiBossTuning extends SubGuiFieldScreen {
    private static final int FIRST_TOPIC_BUTTON = 10;
    private static final int TITLE_LABEL = 30;
    private static final int FIRST_HINT_LABEL = 40;

    private static final String[] TOPICS = {
            "cnpcgeckoaddon.boss.tuning.rotation",
            "cnpcgeckoaddon.boss.tuning.telegraph",
            "cnpcgeckoaddon.boss.tuning.combos",
            "cnpcgeckoaddon.boss.tuning.totems",
            "cnpcgeckoaddon.boss.tuning.death",
            "cnpcgeckoaddon.boss.tuning.health_link",
            "cnpcgeckoaddon.boss.tuning.waves"
    };

    private static final int FIRST_ROW_Y = 28;
    private static final int ROW_HEIGHT = 27;
    private static final int BUTTON_HEIGHT = 24;
    private static final String HINT = "cnpcgeckoaddon.boss.tuning_hint";

    private final BossTuningSettings tuning;

    public SubGuiBossTuning(BossTuningSettings tuning) {
        this.tuning = tuning;
        imageWidth = 256;
        closeOnEsc = true;
    }

    /** Two columns, the odd one last on a row of its own. */
    private static int gridRows() {
        return (TOPICS.length + 1) / 2;
    }

    @Override
    public void init() {
        imageHeight = doneButtonY() + 20 + 6;
        super.init();
        addLabel(new ThemeLabel(TITLE_LABEL, "cnpcgeckoaddon.boss.tuning_title", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        for (int i = 0; i < TOPICS.length; i++) {
            addButton(new ThemeButton(this, FIRST_TOPIC_BUTTON + i,
                    guiLeft + 8 + (i % 2) * 120, guiTop + FIRST_ROW_Y + (i / 2) * ROW_HEIGHT,
                    114, BUTTON_HEIGHT, TOPICS[i]));
        }
        addWrappedHint(FIRST_HINT_LABEL, HINT, guiTop + hintY());
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, 20);
    }

    private int hintY() {
        return FIRST_ROW_Y + gridRows() * ROW_HEIGHT + 4;
    }

    private int doneButtonY() {
        return hintY() + wrappedHintHeight(HINT) + 4;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        switch (button.id - FIRST_TOPIC_BUTTON) {
            case 0 -> setSubGui(new SubGuiBossTuningRotation(tuning));
            case 1 -> setSubGui(new SubGuiBossTuningTelegraph(tuning));
            case 2 -> setSubGui(new SubGuiBossTuningCombos(tuning));
            case 3 -> setSubGui(new SubGuiBossTuningTotems(tuning));
            case 4 -> setSubGui(new SubGuiBossTuningDeath(tuning));
            case 5 -> setSubGui(new SubGuiBossTuningHealthLink(tuning));
            case 6 -> setSubGui(new SubGuiBossTuningWaves(tuning));
            default -> {
                // Done, which closes itself.
            }
        }
    }
}
