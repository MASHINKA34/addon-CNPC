package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/**
 * Which abilities the boss sees out before it starts anything else, one row per ability whose
 * effect outlives its cast. Laid out like the standing-cast screen, which edits the same kind
 * of mask.
 */
public final class SubGuiBossFinish extends SubGuiFieldScreen {
    private static final int TITLE_LABEL = 30;
    private static final int LEGEND_LABEL = 40;
    private static final int FIRST_HINT_LABEL = 50;
    private static final int FIRST_ABILITY_BUTTON = 100;

    /**
     * Two short columns keep the screen inside one panel's height; ten rows in one would not.
     * The platforms fill the second column's fifth slot, so the next one needs a sixth row.
     */
    private static final int ROWS_PER_COLUMN = 5;
    private static final int COLUMN_WIDTH = 117;
    private static final int FIRST_ROW_Y = 24;
    private static final int ROW_HEIGHT = 22;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 6;
    private static final String HINT = "cnpcgeckoaddon.boss.finish_hint";

    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossFinish(BossPhaseData phase, int phaseIndex) {
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: how many lines the legend and
        // the hint wrap to is up to the locale.
        imageHeight = doneButtonY() + BUTTON_HEIGHT + BOTTOM_MARGIN;
        super.init();
        addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.finish_title", phaseIndex), guiLeft + 8, guiTop + 8, 0xFFFFFF));

        for (int i = 0; i < BossAbilityKind.LASTING_ABILITIES.length; i++) {
            addButton(new GuiButtonNop(this, FIRST_ABILITY_BUTTON + i, gridX(i), gridY(i),
                    COLUMN_WIDTH, BUTTON_HEIGHT, abilityLabel(i)));
        }

        int y = addWrappedText(LEGEND_LABEL, legend(), guiTop + legendY());
        addWrappedHint(FIRST_HINT_LABEL, HINT, y + 2);
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, BUTTON_HEIGHT);
    }

    private int gridX(int index) {
        return guiLeft + 8 + index / ROWS_PER_COLUMN * (COLUMN_WIDTH + 6);
    }

    private int gridY(int index) {
        return guiTop + FIRST_ROW_Y + index % ROWS_PER_COLUMN * ROW_HEIGHT;
    }

    /** Where the legend starts, from the panel's top: just under the fullest column. */
    private static int legendY() {
        int rows = Math.min(BossAbilityKind.LASTING_ABILITIES.length, ROWS_PER_COLUMN);
        return FIRST_ROW_Y + rows * ROW_HEIGHT + 4;
    }

    /** Where the done button goes, from the panel's top: just under the legend and the hint. */
    private int doneButtonY() {
        int legend = wrapLines(legend(), imageWidth - 16).size() * LINE_HEIGHT;
        return legendY() + legend + 2 + wrappedHintHeight(HINT) + 4;
    }

    private static String legend() {
        return "+ " + I18n.get("cnpcgeckoaddon.boss.finish_wait")
                + "   - " + I18n.get("cnpcgeckoaddon.boss.finish_free");
    }

    /** "+ Sweeping beam" while the boss waits for the sweep to end, "- ..." while it does not. */
    private String abilityLabel(int index) {
        int ability = BossAbilityKind.LASTING_ABILITIES[index];
        return (phase.waitsForFinish(ability) ? "+ " : "- ") + I18n.get(BossAbilityKind.LABELS[ability]);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int index = button.id - FIRST_ABILITY_BUTTON;
        if (index >= 0 && index < BossAbilityKind.LASTING_ABILITIES.length) {
            int ability = BossAbilityKind.LASTING_ABILITIES[index];
            phase.setWaitsForFinish(ability, !phase.waitsForFinish(ability));
            // Relabelled in place: rebuilding the screen from a click would crash the click.
            button.setDisplayText(abilityLabel(index));
        }
    }
}
