package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.ai.BossAbility;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which ability each ability hands straight on to when it ends, and how long after: one row per
 * ability of the rotation, laid out like the cast spots screen.
 *
 * <p>The rows are the rotation table itself, in its own order, so a chain edited here is the one
 * the fight reads for that ability. The follow-up is picked from a list rather than cycled on
 * the button: nineteen choices on one button is nineteen clicks to reach the last of them.</p>
 */
public final class SubGuiBossCombos extends SubGuiFieldScreen {
    private static final int TITLE_LABEL = 30;
    private static final int DELAY_HEADER_LABEL = 31;
    private static final int FIRST_HINT_LABEL = 40;
    private static final int FIRST_NAME_LABEL = 100;
    private static final int FIRST_FOLLOW_UP_BUTTON = 100;
    private static final int FIRST_DELAY_FIELD = 200;

    private static final int HEADER_Y = 24;
    private static final int FIRST_ROW_Y = 36;
    private static final int ROW_HEIGHT = 22;
    private static final int FOLLOW_UP_BUTTON_X = 110;
    private static final int FOLLOW_UP_BUTTON_WIDTH = 92;
    private static final int DELAY_FIELD_X = 206;
    private static final int DELAY_FIELD_WIDTH = 36;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 6;
    private static final String NONE = "cnpcgeckoaddon.boss.combo_none";
    private static final String DELAY_HEADER = "cnpcgeckoaddon.boss.combo_delay";
    private static final String HINT = "cnpcgeckoaddon.boss.combo_hint";

    private final BossPhaseData phase;
    private final int phaseIndex;
    private final List<BossAbility> rows = BossAbility.ROTATION;

    public SubGuiBossCombos(BossPhaseData phase, int phaseIndex) {
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: how many lines the hint wraps to
        // is up to the locale. Nineteen rows never fit one panel, so the screen scrolls.
        imageHeight = doneButtonY() + BUTTON_HEIGHT + BOTTOM_MARGIN;
        super.init();
        addLabel(new ThemeLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.combo_phase", phaseIndex), guiLeft + 8, guiTop + 8, 0xFFFFFF));
        // Ended flush with the delay column: the header is wider than the fields it names, and a
        // label never clips, so it is measured rather than started at the column.
        int delayRight = guiLeft + DELAY_FIELD_X + DELAY_FIELD_WIDTH;
        addLabel(new ThemeLabel(DELAY_HEADER_LABEL, DELAY_HEADER,
                delayRight - font.width(I18n.get(DELAY_HEADER)), guiTop + HEADER_Y));

        for (int i = 0; i < rows.size(); i++) {
            int kind = rows.get(i).kind();
            int y = guiTop + FIRST_ROW_Y + i * ROW_HEIGHT;
            addLabel(new ThemeLabel(FIRST_NAME_LABEL + i, BossAbilityKind.LABELS[kind], guiLeft + 8, y + 6));
            addButton(new ThemeButton(this, FIRST_FOLLOW_UP_BUTTON + i, guiLeft + FOLLOW_UP_BUTTON_X, y,
                    FOLLOW_UP_BUTTON_WIDTH, BUTTON_HEIGHT, followUpLabel(kind)));
            GuiTextFieldNop delay = new ThemeTextField(FIRST_DELAY_FIELD + i, this, guiLeft + DELAY_FIELD_X, y,
                    DELAY_FIELD_WIDTH, BUTTON_HEIGHT, Integer.toString(phase.comboDelay(kind)));
            delay.setNumbersOnly();
            delay.setMinMaxDefault(0, BossPhaseData.MAX_COMBO_DELAY, 0);
            addTextField(delay);
        }
        addWrappedHint(FIRST_HINT_LABEL, HINT, guiTop + hintY());
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, BUTTON_HEIGHT);
    }

    /** Where the hint starts, from the panel's top: just under the last row. */
    private int hintY() {
        return FIRST_ROW_Y + rows.size() * ROW_HEIGHT + 4;
    }

    /** Where the done button goes, from the panel's top: just under the hint. */
    private int doneButtonY() {
        return hintY() + wrappedHintHeight(HINT) + 4;
    }

    /** The follow-up's name as a key, or the dash's while the slot is empty. */
    private String followUpLabel(int kind) {
        int next = phase.comboFollowUp(kind);
        return next == BossPhaseData.NO_COMBO ? NONE : BossAbilityKind.LABELS[next];
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int row = button.id - FIRST_FOLLOW_UP_BUTTON;
        if (row < 0 || row >= rows.size()) {
            return;
        }
        int kind = rows.get(row).kind();
        // The picker hands back the name it showed, so each name is kept with the slot it
        // stands for. Every ability of the rotation but the row's own: nothing follows itself.
        Map<String, Integer> choices = new LinkedHashMap<>();
        choices.put(I18n.get(NONE), BossPhaseData.NO_COMBO);
        for (BossAbility ability : rows) {
            if (ability.kind() != kind) {
                choices.put(I18n.get(BossAbilityKind.LABELS[ability.kind()]), ability.kind());
            }
        }
        setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.boss.combo_pick", List.copyOf(choices.keySet()), name -> {
            Integer next = choices.get(name);
            if (next == null) {
                return;
            }
            phase.setComboFollowUp(kind, next);
            // Relabelled in place: rebuilding the screen from here would crash the click.
            GuiButtonNop picked = getButton(FIRST_FOLLOW_UP_BUTTON + row);
            if (picked != null) {
                picked.setDisplayText(followUpLabel(kind));
            }
        }));
    }

    @Override
    protected void applyFields() {
        for (int i = 0; i < rows.size(); i++) {
            int kind = rows.get(i).kind();
            applyNumberField(FIRST_DELAY_FIELD + i, ticks -> phase.setComboDelay(kind, ticks));
        }
    }
}
