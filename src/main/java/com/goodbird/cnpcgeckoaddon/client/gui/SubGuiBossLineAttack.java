package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/** Line strike: a corridor of full damage straight ahead, with a softer wave down each flank. */
public final class SubGuiBossLineAttack extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int TARGET_MODE_BUTTON = 3;
    private static final int DIRECTION_BUTTON = 4;
    private static final int LENGTH_FIELD = 5;
    private static final int WIDTH_FIELD = 6;
    private static final int HEIGHT_FIELD = 7;
    private static final int DAMAGE_FIELD = 8;
    private static final int KNOCKBACK_FIELD = 9;
    private static final int SIDE_WIDTH_FIELD = 10;
    private static final int SIDE_PERCENT_FIELD = 11;
    private static final int ACTION_DELAY_FIELD = 12;
    private static final int COOLDOWN_FIELD = 13;
    private static final int VFX_STYLE_BUTTON = 14;
    private static final int BLOCK_WAVE_BUTTON = 15;
    private static final int FACE_AXIS_BUTTON = 16;
    private static final int EFFECTS_BUTTON = 67;

    private static final String[] VFX_STYLE_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey)
            .toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossLineAttack(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        // Taller than an ordinary ability screen: the corridor, its flanks and its wave are
        // three sets of numbers, and squeezing them onto one page beats a second screen.
        imageHeight = 305;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.line_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20,
                phase.lineAttack().isEnabled()));
        y += 21;

        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 88, y, 106, 20,
                phase.lineAttack().getAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 21;

        addLabel(new GuiLabel(TARGET_MODE_BUTTON, "cnpcgeckoaddon.boss.target_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.lineAttack().getTargetMode()));
        y += 21;

        addLabel(new GuiLabel(DIRECTION_BUTTON, "cnpcgeckoaddon.boss.line_direction", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, DIRECTION_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.LINE_DIRECTION_LABELS, phase.lineAttack().getDirection()));
        y += 21;

        addLabel(new GuiLabel(FACE_AXIS_BUTTON, "cnpcgeckoaddon.boss.line_face_axis", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, FACE_AXIS_BUTTON, guiLeft + 155, y, 87, 20,
                phase.lineAttack().isFaceAxis()));
        y += 21;

        addPairRow(LENGTH_FIELD, WIDTH_FIELD, "cnpcgeckoaddon.boss.line_size", y,
                phase.lineAttack().getLength(), 1, 64, 9,
                phase.lineAttack().getWidth(), 1, 8, 2);
        y += 21;
        addNumberField(HEIGHT_FIELD, "cnpcgeckoaddon.boss.line_height", y,
                phase.lineAttack().getHeight(), 1, 8, 3);
        y += 21;
        addPairRow(DAMAGE_FIELD, KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.line_damage", y,
                phase.lineAttack().getDamage(), 1, 1000, 10,
                phase.lineAttack().getKnockback(), 0, 10, 2);
        y += 21;
        // A width of zero here is what turns the flanks off outright, damage and wave alike.
        addPairRow(SIDE_WIDTH_FIELD, SIDE_PERCENT_FIELD, "cnpcgeckoaddon.boss.line_side", y,
                phase.lineAttack().getSideWidth(), 0, 8, 2,
                phase.lineAttack().getSidePercent(), 10, 100, 50);
        y += 21;
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.lineAttack().getActionDelayTicks(), 0, 1200, 12,
                phase.lineAttack().getCooldownTicks(), 1, 12000, 140);
        y += 21;

        addLabel(new GuiLabel(VFX_STYLE_BUTTON, "cnpcgeckoaddon.boss.area_vfx", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, VFX_STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                VFX_STYLE_LABELS, vfxStyleIndex()));
        y += 21;
        addLabel(new GuiLabel(BLOCK_WAVE_BUTTON, "cnpcgeckoaddon.boss.area_block_wave", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, BLOCK_WAVE_BUTTON, guiLeft + 155, y, 87, 20,
                phase.lineAttack().isBlockWave()));

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.line_hint", guiLeft + 6, guiTop + 271, 0xA0A0A0));
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, guiTop + 281, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addDoneButton(guiLeft + 182, guiTop + 281, 60, 20);
    }

    private int vfxStyleIndex() {
        String id = phase.lineAttack().getVfx();
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
            setSubGui(new SubGuiBossEffectList(phase.lineAttack().getEffects(),
                    "cnpcgeckoaddon.boss.effects_line"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.lineAttack().setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TARGET_MODE_BUTTON) {
            phase.lineAttack().setTargetMode(button.getValue());
        } else if (button.id == DIRECTION_BUTTON) {
            phase.lineAttack().setDirection(button.getValue());
        } else if (button.id == FACE_AXIS_BUTTON) {
            phase.lineAttack().setFaceAxis(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == VFX_STYLE_BUTTON) {
            phase.lineAttack().setVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == BLOCK_WAVE_BUTTON) {
            phase.lineAttack().setBlockWave(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting line strike animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.lineAttack().setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase.lineAttack()::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.lineAttack().setAnimation(value);
            else animation.setValue(phase.lineAttack().getAnimation());
        }
        applyNumberField(LENGTH_FIELD, phase.lineAttack()::setLength);
        applyNumberField(WIDTH_FIELD, phase.lineAttack()::setWidth);
        applyNumberField(HEIGHT_FIELD, phase.lineAttack()::setHeight);
        applyNumberField(DAMAGE_FIELD, phase.lineAttack()::setDamage);
        applyNumberField(KNOCKBACK_FIELD, phase.lineAttack()::setKnockback);
        applyNumberField(SIDE_WIDTH_FIELD, phase.lineAttack()::setSideWidth);
        applyNumberField(SIDE_PERCENT_FIELD, phase.lineAttack()::setSidePercent);
        applyNumberField(ACTION_DELAY_FIELD, phase.lineAttack()::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, phase.lineAttack()::setCooldownTicks);
    }
}
