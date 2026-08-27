package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Line strike: a corridor of full damage straight ahead, with a softer wave down each flank. */
public final class SubGuiBossLineAttack extends SubGuiFieldScreen implements ITextfieldListener {
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
        setBackground("menubg.png");
        imageWidth = 256;
        // Taller than an ordinary ability screen: the corridor, its flanks and its wave are
        // three sets of numbers, and squeezing them onto one page beats a second screen.
        imageHeight = 284;
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
                phase.isLineAttackEnabled()));
        y += 21;

        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 88, y, 106, 20,
                phase.getLineAttackAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 21;

        addLabel(new GuiLabel(TARGET_MODE_BUTTON, "cnpcgeckoaddon.boss.target_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.getLineAttackTargetMode()));
        y += 21;

        addLabel(new GuiLabel(DIRECTION_BUTTON, "cnpcgeckoaddon.boss.line_direction", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, DIRECTION_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.LINE_DIRECTION_LABELS, phase.getLineAttackDirection()));
        y += 21;

        addPairRow(LENGTH_FIELD, WIDTH_FIELD, "cnpcgeckoaddon.boss.line_size", y,
                phase.getLineAttackLength(), 1, 64, 9,
                phase.getLineAttackWidth(), 1, 8, 2);
        y += 21;
        addNumberField(HEIGHT_FIELD, "cnpcgeckoaddon.boss.line_height", y,
                phase.getLineAttackHeight(), 1, 8, 3);
        y += 21;
        addPairRow(DAMAGE_FIELD, KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.line_damage", y,
                phase.getLineAttackDamage(), 1, 1000, 10,
                phase.getLineAttackKnockback(), 0, 10, 2);
        y += 21;
        // A width of zero here is what turns the flanks off outright, damage and wave alike.
        addPairRow(SIDE_WIDTH_FIELD, SIDE_PERCENT_FIELD, "cnpcgeckoaddon.boss.line_side", y,
                phase.getLineAttackSideWidth(), 0, 8, 2,
                phase.getLineAttackSidePercent(), 10, 100, 50);
        y += 21;
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.getLineAttackActionDelayTicks(), 0, 1200, 12,
                phase.getLineAttackCooldownTicks(), 1, 12000, 140);
        y += 21;

        addLabel(new GuiLabel(VFX_STYLE_BUTTON, "cnpcgeckoaddon.boss.area_vfx", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, VFX_STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                VFX_STYLE_LABELS, vfxStyleIndex()));
        y += 21;
        addLabel(new GuiLabel(BLOCK_WAVE_BUTTON, "cnpcgeckoaddon.boss.area_block_wave", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, BLOCK_WAVE_BUTTON, guiLeft + 155, y, 87, 20,
                phase.isLineAttackBlockWave()));

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.line_hint", guiLeft + 6, guiTop + 250, 0xA0A0A0));
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, guiTop + 260, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 260, 60, 20,
                "gui.done", button -> close()));
    }

    private int vfxStyleIndex() {
        String id = phase.getLineAttackVfx();
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
            setSubGui(new SubGuiBossEffectList(phase.getLineAttackEffects(),
                    "cnpcgeckoaddon.boss.effects_line"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.setLineAttackEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TARGET_MODE_BUTTON) {
            phase.setLineAttackTargetMode(button.getValue());
        } else if (button.id == DIRECTION_BUTTON) {
            phase.setLineAttackDirection(button.getValue());
        } else if (button.id == VFX_STYLE_BUTTON) {
            phase.setLineAttackVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == BLOCK_WAVE_BUTTON) {
            phase.setLineAttackBlockWave(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting line strike animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setLineAttackAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase::setLineAttackActionDelayTicks);
            }));
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
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.setLineAttackAnimation(value);
            else animation.setValue(phase.getLineAttackAnimation());
        }
        GuiTextFieldNop length = getTextField(LENGTH_FIELD);
        if (length != null) phase.setLineAttackLength(length.getInteger());
        GuiTextFieldNop width = getTextField(WIDTH_FIELD);
        if (width != null) phase.setLineAttackWidth(width.getInteger());
        GuiTextFieldNop height = getTextField(HEIGHT_FIELD);
        if (height != null) phase.setLineAttackHeight(height.getInteger());
        GuiTextFieldNop damage = getTextField(DAMAGE_FIELD);
        if (damage != null) phase.setLineAttackDamage(damage.getInteger());
        GuiTextFieldNop knockback = getTextField(KNOCKBACK_FIELD);
        if (knockback != null) phase.setLineAttackKnockback(knockback.getInteger());
        GuiTextFieldNop sideWidth = getTextField(SIDE_WIDTH_FIELD);
        if (sideWidth != null) phase.setLineAttackSideWidth(sideWidth.getInteger());
        GuiTextFieldNop sidePercent = getTextField(SIDE_PERCENT_FIELD);
        if (sidePercent != null) phase.setLineAttackSidePercent(sidePercent.getInteger());
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setLineAttackActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setLineAttackCooldownTicks(cooldown.getInteger());
    }
}
