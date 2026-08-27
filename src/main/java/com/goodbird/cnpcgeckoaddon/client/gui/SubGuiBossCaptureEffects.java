package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Potion receiver, lift geometry, look lock, and shared animated-link appearance. */
public final class SubGuiBossCaptureEffects extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int EFFECT_TARGET_BUTTON = 1;
    private static final int LIFT_HEIGHT_FIELD = 2;
    private static final int LIFT_TICKS_FIELD = 3;
    private static final int ALLOW_LOOK_BUTTON = 4;
    private static final int BEAM_STYLE_BUTTON = 5;
    private static final int BEAM_WIDTH_FIELD = 6;
    private static final int BEAM_SAG_FIELD = 7;
    private static final int EFFECTS_BUTTON = 67;

    private static final String[] EFFECT_TARGET_LABELS = {
            "cnpcgeckoaddon.boss.capture_effect_player",
            "cnpcgeckoaddon.boss.capture_effect_boss",
            "cnpcgeckoaddon.boss.capture_effect_both"
    };
    private static final String[] BEAM_STYLE_LABELS = HookCordStyles.values().stream()
            .map(HookCordStyles.Style::translationKey).toArray(String[]::new);

    private final BossPhaseData phase;

    public SubGuiBossCaptureEffects(BossPhaseData phase) {
        this.phase = phase;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.capture_title", guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new GuiLabel(EFFECT_TARGET_BUTTON, "cnpcgeckoaddon.boss.capture_effect_target",
                guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, EFFECT_TARGET_BUTTON, guiLeft + 112, y, 130, 20,
                EFFECT_TARGET_LABELS, phase.getCaptureEffectTarget()));
        y += 24;

        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, y, 236, 20,
                "cnpcgeckoaddon.boss.capture_effects"));
        y += 24;

        addNumberField(LIFT_HEIGHT_FIELD, "cnpcgeckoaddon.boss.capture_lift_height", y,
                phase.getCaptureLiftHeight(), 0, 64, 5);
        y += 24;
        addNumberField(LIFT_TICKS_FIELD, "cnpcgeckoaddon.boss.capture_lift_ticks", y,
                phase.getCaptureLiftTicks(), 1, 1200, 40);
        y += 24;

        addLabel(new GuiLabel(ALLOW_LOOK_BUTTON, "cnpcgeckoaddon.boss.capture_allow_look",
                guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ALLOW_LOOK_BUTTON, guiLeft + 155, y, 87, 20,
                phase.isCaptureAllowLook()));
        y += 24;

        addLabel(new GuiLabel(BEAM_STYLE_BUTTON, "cnpcgeckoaddon.boss.capture_beam", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, BEAM_STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                BEAM_STYLE_LABELS, beamStyleIndex()));
        y += 24;
        addNumberField(BEAM_WIDTH_FIELD, "cnpcgeckoaddon.boss.capture_beam_width", y,
                phase.getCaptureBeamWidthPercent(), 25, 400, 100);
        y += 24;
        addNumberField(BEAM_SAG_FIELD, "cnpcgeckoaddon.boss.capture_beam_sag", y,
                phase.getCaptureBeamSagPercent(), 0, 200, 0);

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.capture_hint",
                guiLeft + 6, guiTop + 213, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 232, 60, 20,
                "gui.done", button -> close()));
        updateLiftFields();
    }

    @Override
    protected int numberLabelX() {
        // This screen family starts its labels a column tighter than the shared default.
        return 6;
    }

    private int beamStyleIndex() {
        String id = phase.getCaptureBeamStyle();
        for (int i = 0; i < HookCordStyles.values().size(); i++) {
            if (HookCordStyles.values().get(i).id().equals(id)) return i;
        }
        return 0;
    }

    private void updateLiftFields() {
        boolean enabled = phase.getCaptureMode() == BossPhaseData.CAPTURE_MODE_LIFT;
        GuiTextFieldNop height = getTextField(LIFT_HEIGHT_FIELD);
        GuiTextFieldNop ticks = getTextField(LIFT_TICKS_FIELD);
        if (height != null) height.enabled = enabled;
        if (ticks != null) ticks.enabled = enabled;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == EFFECT_TARGET_BUTTON) {
            phase.setCaptureEffectTarget(button.getValue());
        } else if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(phase.getCaptureEffects(),
                    "cnpcgeckoaddon.boss.capture_effects"));
        } else if (button.id == ALLOW_LOOK_BUTTON) {
            phase.setCaptureAllowLook(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == BEAM_STYLE_BUTTON) {
            phase.setCaptureBeamStyle(HookCordStyles.values().get(button.getValue()).id());
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
        GuiTextFieldNop height = getTextField(LIFT_HEIGHT_FIELD);
        if (height != null) phase.setCaptureLiftHeight(height.getInteger());
        GuiTextFieldNop ticks = getTextField(LIFT_TICKS_FIELD);
        if (ticks != null) phase.setCaptureLiftTicks(ticks.getInteger());
        GuiTextFieldNop width = getTextField(BEAM_WIDTH_FIELD);
        if (width != null) phase.setCaptureBeamWidthPercent(width.getInteger());
        GuiTextFieldNop sag = getTextField(BEAM_SAG_FIELD);
        if (sag != null) phase.setCaptureBeamSagPercent(sag.getInteger());
    }
}
