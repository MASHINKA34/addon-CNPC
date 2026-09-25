package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/** Compact phase menu. Every ability opens its own fully configurable screen. */
public final class SubGuiBossPhase extends SubGuiFieldScreen implements BossZoneScreen {
    private static final int THRESHOLD_FIELD = 1;
    private static final int THRESHOLD_HINT_LABEL = 40;

    /** Where the threshold hint starts, from the panel's top: under the threshold row. */
    private static final int HINT_Y = 42;
    private static final String THRESHOLD_HINT = "cnpcgeckoaddon.boss.phase_threshold_hint";

    private final EntityNPCInterface npc;
    private final TeleportPathData data;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossPhase(EntityNPCInterface npc, TeleportPathData data, int phaseIndex) {
        this.npc = npc;
        this.data = data;
        this.phaseIndex = phaseIndex;
        this.phase = data.getPhase(phaseIndex);
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: how many lines the window hint
        // wraps to is up to the locale.
        imageHeight = doneButtonY() + 20 + 6;
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
            // The field takes 1..100, the ladder takes what fits between the phases either
            // side of this one: the line says which window, and the value the field shows
            // after Done is the one that was really kept.
            addWrappedText(THRESHOLD_HINT_LABEL, hintText(), guiTop + HINT_Y);
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
        addAbilityButton(27, 1, 8, "cnpcgeckoaddon.boss.mark_settings");
        addAbilityButton(28, 0, 9, "cnpcgeckoaddon.boss.cover_settings");
        addAbilityButton(29, 1, 9, "cnpcgeckoaddon.boss.hazard_settings");
        addAbilityButton(30, 0, 10, "cnpcgeckoaddon.boss.hunt_settings");
        addAbilityButton(31, 1, 10, "cnpcgeckoaddon.boss.barrier_settings");
        addAbilityButton(32, 0, 11, "cnpcgeckoaddon.boss.beam_settings");
        addAbilityButton(33, 1, 11, "cnpcgeckoaddon.boss.cocoon_settings");
        addAbilityButton(34, 0, 12, "cnpcgeckoaddon.boss.cast_spots_settings");
        addAbilityButton(35, 1, 12, "cnpcgeckoaddon.boss.finish_settings");
        addAbilityButton(36, 0, 13, "cnpcgeckoaddon.boss.combo_settings");
        addAbilityButton(37, 1, 13, "cnpcgeckoaddon.boss.dash_settings");
        addAbilityButton(38, 0, 14, "cnpcgeckoaddon.boss.cone_settings");
        addAbilityButton(39, 1, 14, "cnpcgeckoaddon.boss.platform_settings");
        addAbilityButton(40, 0, 15, "cnpcgeckoaddon.boss.hurricane_settings");
        addAbilityButton(41, 1, 15, "cnpcgeckoaddon.boss.shadow_settings");
        addAbilityButton(42, 0, 16, "cnpcgeckoaddon.boss.seismic_settings");
        addAbilityButton(43, 1, 16, "cnpcgeckoaddon.boss.rift_settings");
        addAbilityButton(44, 0, 17, "cnpcgeckoaddon.boss.vent_settings");
        // The grid runs to eighteen rows now, so Done keeps a line of its own below it.
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, 20);
    }

    private void addAbilityButton(int id, int column, int row, String label) {
        addButton(new GuiButtonNop(this, id, guiLeft + 8 + column * 120, guiTop + gridY() + row * 27,
                114, 24, label));
    }

    /** The window this phase's threshold really lands in, as the line under the field reads. */
    private String hintText() {
        return Component.translatable(THRESHOLD_HINT, data.getPhaseThresholdMin(phaseIndex),
                data.getPhaseThresholdMax(phaseIndex)).getString();
    }

    /** Where the ability grid starts, from the panel's top: under the hint, however it wraps. */
    private int gridY() {
        return HINT_Y + wrapLines(hintText(), imageWidth - 16).size() * LINE_HEIGHT + 4;
    }

    private int doneButtonY() {
        // Eighteen rows of buttons, and Done on a line of its own below them.
        return gridY() + 18 * 27 + 6;
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
        } else if (button.id == 27) {
            setSubGui(new SubGuiBossMark(npc, phase, phaseIndex));
        } else if (button.id == 28) {
            setSubGui(new SubGuiBossCover(npc, phase, phaseIndex));
        } else if (button.id == 29) {
            setSubGui(new SubGuiBossHazard(phase, phaseIndex));
        } else if (button.id == 30) {
            setSubGui(new SubGuiBossHunt(npc, phase, phaseIndex));
        } else if (button.id == 31) {
            setSubGui(new SubGuiBossBarrier(npc, phase, phaseIndex));
        } else if (button.id == 32) {
            setSubGui(new SubGuiBossBeam(npc, phase, phaseIndex));
        } else if (button.id == 33) {
            setSubGui(new SubGuiBossCocoon(npc, phase, phaseIndex));
        } else if (button.id == 34) {
            setSubGui(new SubGuiBossCastSpots(npc, phase, phaseIndex));
        } else if (button.id == 35) {
            setSubGui(new SubGuiBossFinish(phase, phaseIndex));
        } else if (button.id == 36) {
            setSubGui(new SubGuiBossCombos(phase, phaseIndex));
        } else if (button.id == 37) {
            setSubGui(new SubGuiBossDash(npc, phase, phaseIndex));
        } else if (button.id == 38) {
            setSubGui(new SubGuiBossCone(npc, phase, phaseIndex));
        } else if (button.id == 39) {
            setSubGui(new SubGuiBossPlatform(npc, phase, phaseIndex));
        } else if (button.id == 40) {
            setSubGui(new SubGuiBossHurricane(npc, phase, phaseIndex));
        } else if (button.id == 41) {
            setSubGui(new SubGuiBossShadow(npc, phase, phaseIndex));
        } else if (button.id == 42) {
            setSubGui(new SubGuiBossSeismic(npc, phase, phaseIndex));
        } else if (button.id == 43) {
            setSubGui(new SubGuiBossRift(npc, phase, phaseIndex));
        } else if (button.id == 44) {
            setSubGui(new SubGuiBossVent(npc, phase, phaseIndex));
        }
    }

    @Override
    protected void applyFields() {
        applyNumberField(THRESHOLD_FIELD, percent -> data.setPhaseThreshold(phaseIndex, percent));
        GuiTextFieldNop threshold = getTextField(THRESHOLD_FIELD);
        if (threshold != null) {
            threshold.setValue(Integer.toString(phase.getStartHealthPercent()));
        }
    }

    /** Below a phase's menu the world shows that phase's zones, not every phase's at once. */
    @Override
    public int zonePhase() {
        return phaseIndex;
    }
}
