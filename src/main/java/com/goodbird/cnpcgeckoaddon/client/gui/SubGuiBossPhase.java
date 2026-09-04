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
        imageHeight = 316;
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

        // Two columns: a single 234-wide stack ran out of rows at the seventh ability.
        addAbilityButton(10, 0, 0, "cnpcgeckoaddon.boss.teleport_settings");
        addAbilityButton(11, 1, 0, "cnpcgeckoaddon.boss.summon_settings");
        addAbilityButton(12, 0, 1, "cnpcgeckoaddon.boss.ground_settings");
        addAbilityButton(13, 1, 1, "cnpcgeckoaddon.boss.ranged_settings");
        addAbilityButton(14, 0, 2, "cnpcgeckoaddon.boss.melee_settings");
        addAbilityButton(15, 1, 2, "cnpcgeckoaddon.boss.fluid_settings");
        addAbilityButton(16, 0, 3, "cnpcgeckoaddon.boss.hook_settings");
        addAbilityButton(17, 1, 3, "cnpcgeckoaddon.boss.invulnerable_settings");
        addAbilityButton(18, 0, 4, "cnpcgeckoaddon.boss.capture_settings");
        addAbilityButton(19, 1, 4, "cnpcgeckoaddon.boss.leap_settings");
        addAbilityButton(20, 0, 5, "cnpcgeckoaddon.boss.line_settings");
        addAbilityButton(21, 1, 5, "cnpcgeckoaddon.boss.geyser_settings");
        addAbilityButton(22, 0, 6, "cnpcgeckoaddon.boss.cast_move_settings");
        addAbilityButton(23, 1, 6, "cnpcgeckoaddon.boss.boulder_settings");
        // Directly under the corridor boulder: the two are read against each other.
        addAbilityButton(24, 0, 7, "cnpcgeckoaddon.boss.boulder_rain_settings");
        addAbilityButton(25, 1, 7, "cnpcgeckoaddon.boss.tether_settings");
        addAbilityButton(26, 0, 8, "cnpcgeckoaddon.boss.gravity_settings");
        // The grid runs to nine rows now, so Done gets a line of its own below it.
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 290, 60, 20,
                "gui.done", button -> close()));
    }

    private void addAbilityButton(int id, int column, int row, String label) {
        addButton(new GuiButtonNop(this, id, guiLeft + 8 + column * 120, guiTop + 46 + row * 27,
                114, 24, label));
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
        } else if (button.id == 17) {
            setSubGui(new SubGuiBossInvulnerable(phase, phaseIndex));
        } else if (button.id == 18) {
            setSubGui(new SubGuiBossCapture(npc, phase, phaseIndex));
        } else if (button.id == 19) {
            setSubGui(new SubGuiBossLeap(npc, phase, phaseIndex));
        } else if (button.id == 20) {
            setSubGui(new SubGuiBossLineAttack(npc, phase, phaseIndex));
        } else if (button.id == 21) {
            setSubGui(new SubGuiBossGeyser(npc, phase, phaseIndex));
        } else if (button.id == 22) {
            setSubGui(new SubGuiBossCastMovement(phase, phaseIndex));
        } else if (button.id == 23) {
            setSubGui(new SubGuiBossBoulder(npc, phase, phaseIndex));
        } else if (button.id == 24) {
            setSubGui(new SubGuiBossBoulderRain(npc, phase, phaseIndex));
        } else if (button.id == 25) {
            setSubGui(new SubGuiBossTether(npc, phase, phaseIndex));
        } else if (button.id == 26) {
            setSubGui(new SubGuiBossGravity(npc, phase, phaseIndex));
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
