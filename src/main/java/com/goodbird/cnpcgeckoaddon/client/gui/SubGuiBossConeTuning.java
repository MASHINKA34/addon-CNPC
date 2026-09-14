package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossConeSettings;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/**
 * What calls a cone off at the warning's end, whether a cone along the gaze or at points waits
 * for somebody in its fan, and what its cooldown counts from.
 */
public final class SubGuiBossConeTuning extends SubGuiBossAbilityTuning {
    private static final int DODGE_BUTTON = 1;
    private static final int NEEDS_VICTIM_BUTTON = 2;
    private static final int COOLDOWN_FROM_BUTTON = 3;

    private final BossConeSettings cone;

    public SubGuiBossConeTuning(BossConeSettings cone) {
        super("cnpcgeckoaddon.boss.cone_tuning_title");
        this.cone = cone;
    }

    @Override
    protected int rows() {
        // The two choices take a row for their name and one for the choice itself: what a choice
        // reads - "cancels when the target leaves the fan" - does not fit beside a name.
        return 5;
    }

    @Override
    protected void addRows() {
        addWideCycle(DODGE_BUTTON, "cnpcgeckoaddon.boss.cone_dodge", BossPhaseData.CONE_DODGE_LABELS,
                cone.getDodgeMode());
        addYesNo(NEEDS_VICTIM_BUTTON, "cnpcgeckoaddon.boss.cone_needs_victim", nextRow(), cone.isNeedsVictim());
        addWideCycle(COOLDOWN_FROM_BUTTON, "cnpcgeckoaddon.boss.cone_cooldown_from",
                BossPhaseData.CONE_COOLDOWN_FROM_LABELS, cone.getCooldownFrom());
    }

    /** A choice too wordy to sit beside its name: the name on one row, the choice across the whole of the next. */
    private void addWideCycle(int id, String labelKey, String[] values, int value) {
        addLabel(new GuiLabel(id, labelKey, guiLeft + toggleLabelX(), nextRow() + toggleLabelYOffset()));
        addButton(new GuiButtonNop(this, id, guiLeft + toggleLabelX(), nextRow(),
                imageWidth - toggleLabelX() * 2, toggleButtonHeight(), values, value));
    }

    /** Not "the defaults are the old behaviour": on this page, on purpose, they are not. */
    @Override
    protected String hintKey() {
        return "cnpcgeckoaddon.boss.cone_tuning_hint";
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == DODGE_BUTTON) {
            cone.setDodgeMode(button.getValue());
        } else if (button.id == NEEDS_VICTIM_BUTTON) {
            cone.setNeedsVictim(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == COOLDOWN_FROM_BUTTON) {
            cone.setCooldownFrom(button.getValue());
        }
    }
}
