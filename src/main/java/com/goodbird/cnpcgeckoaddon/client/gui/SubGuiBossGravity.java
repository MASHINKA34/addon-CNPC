package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/** Gravity: a field around the boss that drags everyone in, shoves them out or throws them up. */
public final class SubGuiBossGravity extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int MODE_BUTTON = 3;
    private static final int RADIUS_FIELD = 4;
    private static final int DURATION_FIELD = 5;
    private static final int STRENGTH_FIELD = 6;
    private static final int TOUCH_RADIUS_FIELD = 7;
    private static final int DAMAGE_FIELD = 8;
    private static final int ACTION_DELAY_FIELD = 9;
    private static final int COOLDOWN_FIELD = 10;
    private static final int VFX_STYLE_BUTTON = 11;
    private static final int EFFECTS_BUTTON = 67;

    private static final String[] VFX_STYLE_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey)
            .toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossGravity(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        imageHeight = 244;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.gravity_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, phase.gravity().isEnabled()));
        y += 21;

        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 108, y, 86, 20,
                phase.gravity().getAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 21;

        addLabel(new GuiLabel(MODE_BUTTON, "cnpcgeckoaddon.boss.gravity_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.GRAVITY_MODE_LABELS, phase.gravity().getMode()));
        y += 21;

        // The shape and its clock share a line, and the force and its bite the next: each
        // pair is read against the other, and a throw ignores the right-hand half of both.
        addPairRow(RADIUS_FIELD, DURATION_FIELD, "cnpcgeckoaddon.boss.gravity_field", y,
                phase.gravity().getRadius(), 3, 48, 16,
                phase.gravity().getDurationTicks(), 5, 400, 60);
        y += 21;
        addPairRow(STRENGTH_FIELD, TOUCH_RADIUS_FIELD, "cnpcgeckoaddon.boss.gravity_strength", y,
                phase.gravity().getStrength(), 1, 20, 10,
                phase.gravity().getTouchRadius(), 1, 6, 2);
        y += 21;
        addNumberField(DAMAGE_FIELD, "cnpcgeckoaddon.boss.damage", y, phase.gravity().getDamage(), 0, 1000, 8);
        y += 21;
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.gravity().getActionDelayTicks(), 0, 1200, 20,
                phase.gravity().getCooldownTicks(), 1, 12000, 300);
        y += 21;

        addLabel(new GuiLabel(VFX_STYLE_BUTTON, "cnpcgeckoaddon.boss.area_vfx", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, VFX_STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                VFX_STYLE_LABELS, vfxStyleIndex()));
        y += 21;

        int hintY = addWrappedHint(31, "cnpcgeckoaddon.boss.gravity_hint", y + 3);
        int buttonsY = Math.max(hintY + 4, guiTop + 214);
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, buttonsY, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addDoneButton(guiLeft + 182, buttonsY, 60, 20);
    }

    private int vfxStyleIndex() {
        String id = phase.gravity().getVfx();
        for (int i = 0; i < AreaVfxStyles.values().size(); i++) {
            if (AreaVfxStyles.values().get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    /** Two small numbers on one line, so the whole ability still fits a single screen. */
    private void addPairRow(int leftId, int rightId, String label, int y,
                            int leftValue, int leftMin, int leftMax, int leftFallback,
                            int rightValue, int rightMin, int rightMax, int rightFallback) {
        addLabel(new GuiLabel(leftId, label, guiLeft + 6, y + 6));
        addPairedField(leftId, guiLeft + 130, y, leftValue, leftMin, leftMax, leftFallback);
        addPairedField(rightId, guiLeft + 190, y, rightValue, rightMin, rightMax, rightFallback);
    }

    private void addPairedField(int id, int x, int y, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, 52, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    @Override
    protected int numberLabelX() {
        return 6;
    }

    @Override
    protected int numberFieldX() {
        return 175;
    }

    @Override
    protected int numberFieldWidth() {
        return 67;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(phase.gravity().getEffects(), "cnpcgeckoaddon.boss.effects_gravity"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.gravity().setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == MODE_BUTTON) {
            phase.gravity().setMode(button.getValue());
        } else if (button.id == VFX_STYLE_BUTTON) {
            phase.gravity().setVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting gravity animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.gravity().setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase.gravity()::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.gravity().setAnimation(value);
            else animation.setValue(phase.gravity().getAnimation());
        }
        applyNumberField(RADIUS_FIELD, phase.gravity()::setRadius);
        applyNumberField(DURATION_FIELD, phase.gravity()::setDurationTicks);
        applyNumberField(STRENGTH_FIELD, phase.gravity()::setStrength);
        applyNumberField(TOUCH_RADIUS_FIELD, phase.gravity()::setTouchRadius);
        applyNumberField(DAMAGE_FIELD, phase.gravity()::setDamage);
        applyNumberField(ACTION_DELAY_FIELD, phase.gravity()::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, phase.gravity()::setCooldownTicks);
    }
}
