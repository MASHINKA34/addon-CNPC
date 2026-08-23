package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Immune phase: the boss shrugs off every hit and answers only with summons. */
public final class SubGuiBossInvulnerable extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int END_MODE_BUTTON = 2;
    private static final int DURATION_FIELD = 3;
    private static final int TELEPORT_BUTTON = 4;
    private static final int SUMMON_NOW_BUTTON = 5;

    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossInvulnerable(BossPhaseData phase, int phaseIndex) {
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 216;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.invulnerable_phase", phaseIndex), guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 30;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.invulnerable_enabled", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20,
                phase.isInvulnerableEnabled()));
        y += 27;

        addLabel(new GuiLabel(END_MODE_BUTTON, "cnpcgeckoaddon.boss.invulnerable_end_mode", guiLeft + 8, y + 6));
        addButton(new GuiButtonNop(this, END_MODE_BUTTON, guiLeft + 92, y, 150, 20,
                BossPhaseData.INVULNERABLE_END_LABELS, phase.getInvulnerableEndMode()));
        y += 27;

        addLabel(new GuiLabel(DURATION_FIELD, "cnpcgeckoaddon.boss.invulnerable_duration", guiLeft + 8, y + 6));
        GuiTextFieldNop duration = new GuiTextFieldNop(DURATION_FIELD, this, guiLeft + 172, y, 70, 20,
                Integer.toString(phase.getInvulnerableDurationTicks()));
        duration.setNumbersOnly();
        duration.setMinMaxDefault(20, 12000, 200);
        addTextField(duration);
        y += 27;

        addLabel(new GuiLabel(TELEPORT_BUTTON, "cnpcgeckoaddon.boss.invulnerable_teleport", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, TELEPORT_BUTTON, guiLeft + 155, y, 87, 20,
                phase.isInvulnerableAllowTeleport()));
        y += 27;

        addLabel(new GuiLabel(SUMMON_NOW_BUTTON, "cnpcgeckoaddon.boss.invulnerable_summon_now", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, SUMMON_NOW_BUTTON, guiLeft + 155, y, 87, 20,
                phase.isInvulnerableSummonImmediately()));

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.invulnerable_hint", guiLeft + 8, guiTop + 166, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 190, 60, 20,
                "gui.done", button -> close()));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            phase.setInvulnerableEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == END_MODE_BUTTON) {
            phase.setInvulnerableEndMode(button.getValue());
        } else if (button.id == TELEPORT_BUTTON) {
            phase.setInvulnerableAllowTeleport(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == SUMMON_NOW_BUTTON) {
            phase.setInvulnerableSummonImmediately(((GuiButtonYesNo) button).getBoolean());
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop field) { applyFields(); }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        GuiTextFieldNop duration = getTextField(DURATION_FIELD);
        if (duration != null) phase.setInvulnerableDurationTicks(duration.getInteger());
    }
}
