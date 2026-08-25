package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Capture timing and target selection; visual and hold details live on a second page. */
public final class SubGuiBossCapture extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int ACTION_DELAY_FIELD = 3;
    private static final int COOLDOWN_FIELD = 4;
    private static final int TARGET_MODE_BUTTON = 5;
    private static final int MIN_RANGE_FIELD = 6;
    private static final int MAX_RANGE_FIELD = 7;
    private static final int MODE_BUTTON = 8;
    private static final int DURATION_FIELD = 9;
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
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.capture_phase", phaseIndex), guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.capture_enabled", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20,
                phase.isCaptureEnabled()));
        y += 24;

        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 88, y, 106, 20,
                phase.getCaptureAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 24;

        addNumberField(ACTION_DELAY_FIELD, "cnpcgeckoaddon.boss.action_delay", y,
                phase.getCaptureActionDelayTicks(), 0, 1200, 10);
        y += 24;
        addNumberField(COOLDOWN_FIELD, "cnpcgeckoaddon.boss.cooldown", y,
                phase.getCaptureCooldownTicks(), 20, 12000, 200);
        y += 24;

        addLabel(new GuiLabel(TARGET_MODE_BUTTON, "cnpcgeckoaddon.boss.target_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.getCaptureTargetMode()));
        y += 24;

        addNumberField(MIN_RANGE_FIELD, "cnpcgeckoaddon.boss.min_range", y,
                phase.getCaptureMinRange(), 0, 64, 0);
        y += 24;
        addNumberField(MAX_RANGE_FIELD, "cnpcgeckoaddon.boss.max_range", y,
                phase.getCaptureMaxRange(), 1, 128, 16);
        y += 24;

        addLabel(new GuiLabel(MODE_BUTTON, "cnpcgeckoaddon.boss.capture_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, MODE_BUTTON, guiLeft + 112, y, 130, 20,
                MODE_LABELS, phase.getCaptureMode()));
        y += 24;
        addNumberField(DURATION_FIELD, "cnpcgeckoaddon.boss.capture_duration", y,
                phase.getCaptureDurationTicks(), 1, 1200, 60);

        addButton(new GuiButtonNop(this, DETAILS_BUTTON, guiLeft + 6, guiTop + 232, 150, 20,
                "cnpcgeckoaddon.boss.capture_effects_beam"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 232, 60, 20,
                "gui.done", button -> close()));
    }

    private void addNumberField(int id, String label, int y, int value,
                                int min, int max, int fallback) {
        addLabel(new GuiLabel(id, label, guiLeft + 6, y + 6));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + 172, y, 70, 20,
                Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == DETAILS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossCaptureEffects(phase));
        } else if (button.id == ENABLED_BUTTON) {
            phase.setCaptureEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TARGET_MODE_BUTTON) {
            phase.setCaptureTargetMode(button.getValue());
        } else if (button.id == MODE_BUTTON) {
            phase.setCaptureMode(button.getValue());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting capture animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setCaptureAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase::setCaptureActionDelayTicks);
            }));
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        applyFields();
    }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.setCaptureAnimation(value);
            else animation.setValue(phase.getCaptureAnimation());
        }
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setCaptureActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setCaptureCooldownTicks(cooldown.getInteger());
        GuiTextFieldNop min = getTextField(MIN_RANGE_FIELD);
        GuiTextFieldNop max = getTextField(MAX_RANGE_FIELD);
        if (min != null && max != null) phase.setCaptureRange(min.getInteger(), max.getInteger());
        GuiTextFieldNop duration = getTextField(DURATION_FIELD);
        if (duration != null) phase.setCaptureDurationTicks(duration.getInteger());
    }
}
