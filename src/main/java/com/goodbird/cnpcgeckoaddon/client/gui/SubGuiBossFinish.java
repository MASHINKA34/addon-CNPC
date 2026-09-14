package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.ai.BossAbility;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.List;

/**
 * Which abilities the boss sees out before it starts anything else, and for how long after
 * each: one row per ability of the rotation, laid out like the chains screen.
 *
 * <p>The rows are the rotation table itself, in its own order, so a mark set here is the one
 * the gate reads for that ability. The button carries the ability's name with its mark, the
 * way the standing-cast screen does, so the legend under the rows says what the two signs
 * mean once rather than twenty-two times.</p>
 */
public final class SubGuiBossFinish extends SubGuiFieldScreen {
    private static final int TITLE_LABEL = 30;
    private static final int HOLD_HEADER_LABEL = 31;
    private static final int LEGEND_LABEL = 40;
    private static final int FIRST_HINT_LABEL = 50;
    private static final int FIRST_INSTANT_HINT_LABEL = 60;
    private static final int FIRST_ABILITY_BUTTON = 100;
    private static final int FIRST_HOLD_FIELD = 200;

    private static final int HEADER_Y = 24;
    private static final int FIRST_ROW_Y = 36;
    private static final int ROW_HEIGHT = 22;
    private static final int ABILITY_BUTTON_WIDTH = 190;
    private static final int HOLD_FIELD_X = 206;
    private static final int HOLD_FIELD_WIDTH = 36;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 6;
    /**
     * The hold an instant ability is given when it is marked with none: a mark on a swing with
     * nothing to wait for would wait for nothing, and a second is about a swing's worth of
     * animation. The builder is free to put 0 back.
     */
    private static final int INSTANT_HOLD_TICKS = 20;
    private static final String HOLD_HEADER = "cnpcgeckoaddon.boss.finish_hold";
    private static final String HINT = "cnpcgeckoaddon.boss.finish_hint";
    private static final String INSTANT_HINT = "cnpcgeckoaddon.boss.finish_hint_instant";

    private final BossPhaseData phase;
    private final int phaseIndex;
    private final List<BossAbility> rows = BossAbility.ROTATION;

    public SubGuiBossFinish(BossPhaseData phase, int phaseIndex) {
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: how many lines the legend and
        // the hints wrap to is up to the locale. Twenty-two rows never fit one panel, so the
        // screen scrolls.
        imageHeight = doneButtonY() + BUTTON_HEIGHT + BOTTOM_MARGIN;
        super.init();
        addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.finish_title", phaseIndex), guiLeft + 8, guiTop + 8, 0xFFFFFF));
        // Ended flush with the hold column: the header is wider than the fields it names, and a
        // label never clips, so it is measured rather than started at the column.
        int holdRight = guiLeft + HOLD_FIELD_X + HOLD_FIELD_WIDTH;
        addLabel(new GuiLabel(HOLD_HEADER_LABEL, HOLD_HEADER,
                holdRight - font.width(I18n.get(HOLD_HEADER)), guiTop + HEADER_Y));

        for (int i = 0; i < rows.size(); i++) {
            int kind = rows.get(i).kind();
            int y = guiTop + FIRST_ROW_Y + i * ROW_HEIGHT;
            addButton(new GuiButtonNop(this, FIRST_ABILITY_BUTTON + i, guiLeft + 8, y,
                    ABILITY_BUTTON_WIDTH, BUTTON_HEIGHT, abilityLabel(kind)));
            GuiTextFieldNop hold = new GuiTextFieldNop(FIRST_HOLD_FIELD + i, this, guiLeft + HOLD_FIELD_X, y,
                    HOLD_FIELD_WIDTH, BUTTON_HEIGHT, Integer.toString(phase.finishHoldTicks(kind)));
            hold.setNumbersOnly();
            hold.setMinMaxDefault(0, BossPhaseData.MAX_FINISH_HOLD, 0);
            addTextField(hold);
        }
        int y = addWrappedText(LEGEND_LABEL, legend(), guiTop + legendY());
        y = addWrappedHint(FIRST_HINT_LABEL, HINT, y + 2);
        addWrappedHint(FIRST_INSTANT_HINT_LABEL, INSTANT_HINT, y + 2);
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, BUTTON_HEIGHT);
    }

    /** Where the legend starts, from the panel's top: just under the last row. */
    private int legendY() {
        return FIRST_ROW_Y + rows.size() * ROW_HEIGHT + 4;
    }

    /** Where the done button goes, from the panel's top: just under the legend and the two hints. */
    private int doneButtonY() {
        int legend = wrapLines(legend(), imageWidth - 16).size() * LINE_HEIGHT;
        return legendY() + legend + 2 + wrappedHintHeight(HINT) + 2 + wrappedHintHeight(INSTANT_HINT) + 4;
    }

    private static String legend() {
        return "+ " + I18n.get("cnpcgeckoaddon.boss.finish_wait")
                + "   - " + I18n.get("cnpcgeckoaddon.boss.finish_free");
    }

    /** "+ Sweeping beam" while the boss sees the sweep out, "- ..." while it does not. */
    private String abilityLabel(int kind) {
        return (phase.waitsForFinish(kind) ? "+ " : "- ") + I18n.get(BossAbilityKind.LABELS[kind]);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int row = button.id - FIRST_ABILITY_BUTTON;
        if (row < 0 || row >= rows.size()) {
            return;
        }
        int kind = rows.get(row).kind();
        boolean marked = !phase.waitsForFinish(kind);
        phase.setWaitsForFinish(kind, marked);
        // Relabelled in place: rebuilding the screen from a click would crash the click.
        button.setDisplayText(abilityLabel(kind));
        // An instant ability marked with no hold would wait for nothing, so the mark brings a
        // hold with it while the field holds none; a lasting one has its effect to wait for.
        GuiTextFieldNop hold = getTextField(FIRST_HOLD_FIELD + row);
        if (marked && !isLasting(kind) && hold != null && signed(hold) <= 0) {
            hold.setValue(Integer.toString(INSTANT_HOLD_TICKS));
            phase.setFinishHoldTicks(kind, INSTANT_HOLD_TICKS);
        }
    }

    /** Whether this ability leaves an effect behind that the boss can wait for without a hold. */
    private static boolean isLasting(int kind) {
        return (BossAbilityKind.LASTING_ALL & 1 << kind) != 0;
    }

    @Override
    protected void applyFields() {
        for (int i = 0; i < rows.size(); i++) {
            int kind = rows.get(i).kind();
            applyNumberField(FIRST_HOLD_FIELD + i, ticks -> phase.setFinishHoldTicks(kind, ticks));
        }
    }
}
