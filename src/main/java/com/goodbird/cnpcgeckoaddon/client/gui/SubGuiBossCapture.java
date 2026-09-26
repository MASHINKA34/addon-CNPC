package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeYesNo;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/** Capture timing and target selection; visual and hold details live on a second page. */
public final class SubGuiBossCapture extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int ACTION_DELAY_FIELD = 3;
    private static final int COOLDOWN_FIELD = 4;
    private static final int TARGET_MODE_BUTTON = 5;
    private static final int MIN_RANGE_FIELD = 6;
    private static final int MAX_RANGE_FIELD = 7;
    private static final int MODE_BUTTON = 8;
    private static final int DURATION_FIELD = 9;
    private static final int TUNING_BUTTON = 10;
    private static final int DETAILS_BUTTON = 67;

    private static final String[] MODE_LABELS = {
            "cnpcgeckoaddon.boss.capture_hold",
            "cnpcgeckoaddon.boss.capture_lift"
    };

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossCapture(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        // One row taller than the panel it used to be: the grab's own noise and puff sit
        // under the rows a builder already knows.
        imageHeight = 280;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new ThemeLabel(30, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.capture_phase", phaseIndex), guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new ThemeLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.capture_enabled", guiLeft + 6, y + 6));
        addButton(new ThemeYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20,
                phase.capture().isEnabled()));
        y += 24;

        addLabel(new ThemeLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 6, y + 6));
        addTextField(new ThemeTextField(ANIMATION_FIELD, this, guiLeft + 88, y, 106, 20,
                phase.capture().getAnimation()));
        addButton(new ThemeButton(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 24;

        addNumberField(ACTION_DELAY_FIELD, "cnpcgeckoaddon.boss.action_delay", y,
                phase.capture().getActionDelayTicks(), 0, 1200, 10);
        y += 24;
        addNumberField(COOLDOWN_FIELD, "cnpcgeckoaddon.boss.cooldown", y,
                phase.capture().getCooldownTicks(), 20, 12000, 200);
        y += 24;

        addLabel(new ThemeLabel(TARGET_MODE_BUTTON, "cnpcgeckoaddon.boss.target_mode", guiLeft + 6, y + 6));
        addButton(new ThemeButton(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.capture().getTargetMode()));
        y += 24;

        addNumberField(MIN_RANGE_FIELD, "cnpcgeckoaddon.boss.min_range", y,
                phase.capture().getMinRange(), 0, 64, 0);
        y += 24;
        addNumberField(MAX_RANGE_FIELD, "cnpcgeckoaddon.boss.max_range", y,
                phase.capture().getMaxRange(), 1, 128, 16);
        y += 24;

        addLabel(new ThemeLabel(MODE_BUTTON, "cnpcgeckoaddon.boss.capture_mode", guiLeft + 6, y + 6));
        addButton(new ThemeButton(this, MODE_BUTTON, guiLeft + 112, y, 130, 20,
                MODE_LABELS, phase.capture().getMode()));
        y += 24;
        addNumberField(DURATION_FIELD, "cnpcgeckoaddon.boss.capture_duration", y,
                phase.capture().getDurationTicks(), 1, 1200, 60);

        addButton(new ThemeButton(this, TUNING_BUTTON, guiLeft + 6, guiTop + 232, 236, 20,
                "cnpcgeckoaddon.boss.capture_tuning"));
        addButton(new ThemeButton(this, DETAILS_BUTTON, guiLeft + 6, guiTop + 256, 150, 20,
                "cnpcgeckoaddon.boss.capture_effects_beam"));
        addDoneButton(guiLeft + 182, guiTop + 256, 60, 20);
    }

    @Override
    protected int numberLabelX() {
        // This screen family starts its labels a column tighter than the shared default.
        return 6;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == TUNING_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossCaptureTuning(phase.capture()));
        } else if (button.id == DETAILS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossCaptureEffects(phase));
        } else if (button.id == ENABLED_BUTTON) {
            phase.capture().setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TARGET_MODE_BUTTON) {
            phase.capture().setTargetMode(button.getValue());
        } else if (button.id == MODE_BUTTON) {
            phase.capture().setMode(button.getValue());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.capture_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.capture().setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase.capture()::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.capture().setAnimation(value);
            else animation.setValue(phase.capture().getAnimation());
        }
        applyNumberField(ACTION_DELAY_FIELD, phase.capture()::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, phase.capture()::setCooldownTicks);
        GuiTextFieldNop min = getTextField(MIN_RANGE_FIELD);
        GuiTextFieldNop max = getTextField(MAX_RANGE_FIELD);
        if (min != null && max != null) phase.capture().setRange(min.getInteger(), max.getInteger());
        applyNumberField(DURATION_FIELD, phase.capture()::setDurationTicks);
    }
}
