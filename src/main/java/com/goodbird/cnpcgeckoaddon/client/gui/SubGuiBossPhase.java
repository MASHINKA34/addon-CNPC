package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Compact phase menu. Every ability opens its own fully configurable screen. */
public final class SubGuiBossPhase extends GuiBasic implements ITextfieldListener {
    private static final int THRESHOLD_FIELD = 1;

    private final EntityNPCInterface npc;
    private final TeleportPathData data;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossPhase(EntityNPCInterface npc, TeleportPathData data, int phaseIndex) {
        this.npc = npc;
        this.data = data;
        this.phaseIndex = phaseIndex;
        this.phase = data.getPhase(phaseIndex);
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.phase", phaseIndex),
                guiLeft + 8, guiTop + 8, 0xFFFFFF));

        if (phaseIndex == 0) {
            // The first phase is what the boss starts the fight in, so its threshold is
            // always full health and there is nothing to configure.
            addLabel(new GuiLabel(THRESHOLD_FIELD, "cnpcgeckoaddon.boss.phase_start_full",
                    guiLeft + 8, guiTop + 27, 0xA0A0A0));
        } else {
            addLabel(new GuiLabel(THRESHOLD_FIELD, "cnpcgeckoaddon.boss.phase_threshold",
                    guiLeft + 8, guiTop + 27));
            GuiTextFieldNop field = new GuiTextFieldNop(THRESHOLD_FIELD, this, guiLeft + 172, guiTop + 21,
                    70, 20, Integer.toString(phase.getStartHealthPercent()));
            field.setNumbersOnly();
            field.setMinMaxDefault(1, 100, 50);
            addTextField(field);
        }

        addButton(new GuiButtonNop(this, 10, guiLeft + 8, guiTop + 46, 234, 24,
                "cnpcgeckoaddon.boss.teleport_settings"));
        addButton(new GuiButtonNop(this, 11, guiLeft + 8, guiTop + 73, 234, 24,
                "cnpcgeckoaddon.boss.summon_settings"));
        addButton(new GuiButtonNop(this, 12, guiLeft + 8, guiTop + 100, 234, 24,
                "cnpcgeckoaddon.boss.ground_settings"));
        addButton(new GuiButtonNop(this, 13, guiLeft + 8, guiTop + 127, 234, 24,
                "cnpcgeckoaddon.boss.ranged_settings"));
        addButton(new GuiButtonNop(this, 14, guiLeft + 8, guiTop + 154, 234, 24,
                "cnpcgeckoaddon.boss.melee_settings"));
        addButton(new GuiButtonNop(this, 15, guiLeft + 8, guiTop + 181, 234, 24,
                "cnpcgeckoaddon.boss.fluid_settings"));
        addButton(new GuiButtonNop(this, 16, guiLeft + 8, guiTop + 208, 234, 24,
                "cnpcgeckoaddon.boss.hook_settings"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 234, 60, 20,
                "gui.done", button -> close()));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == 10) {
            setSubGui(new SubGuiBossTeleport(npc, data, phase, phaseIndex));
        } else if (button.id == 11) {
            setSubGui(new SubGuiBossSummon(npc, phase, phaseIndex));
        } else if (button.id == 12) {
            setSubGui(new SubGuiBossAreaAttack(npc, phase, phaseIndex));
        } else if (button.id == 13) {
            setSubGui(new SubGuiBossRangedAttack(npc, phase, phaseIndex));
        } else if (button.id == 14) {
            setSubGui(new SubGuiBossMeleeAttack(npc, phase, phaseIndex));
        } else if (button.id == 15) {
            setSubGui(new SubGuiBossFluidSpit(npc, phase, phaseIndex));
        } else if (button.id == 16) {
            setSubGui(new SubGuiBossHook(npc, phase, phaseIndex));
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
        GuiTextFieldNop threshold = getTextField(THRESHOLD_FIELD);
        if (threshold != null) {
            phase.setStartHealthPercent(threshold.getInteger());
        }
    }
}
