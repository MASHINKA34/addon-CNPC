package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

public final class SubGuiBossRangedAttack extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int DAMAGE_FIELD = 3;
    private static final int MIN_RANGE_FIELD = 4;
    private static final int MAX_RANGE_FIELD = 5;
    private static final int ACTION_DELAY_FIELD = 6;
    private static final int COOLDOWN_FIELD = 7;
    private static final int TARGET_MODE_BUTTON = 8;

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossRangedAttack(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.ranged_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;
        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20,
                phase.rangedAttack().isEnabled()));
        y += 24;
        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 8, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 88, y, 106, 20,
                phase.rangedAttack().getAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 24;
        addTargetModeRow(TARGET_MODE_BUTTON, y, phase.rangedAttack().getTargetMode());
        y += 24;
        addNumberField(DAMAGE_FIELD, "cnpcgeckoaddon.boss.damage", y,
                phase.rangedAttack().getDamage(), 1, 1000, 6);
        y += 24;
        addNumberField(MIN_RANGE_FIELD, "cnpcgeckoaddon.boss.min_range", y,
                phase.rangedAttack().getMinRange(), 0, 64, 4);
        y += 24;
        addNumberField(MAX_RANGE_FIELD, "cnpcgeckoaddon.boss.max_range", y,
                phase.rangedAttack().getMaxRange(), 1, 128, 24);
        y += 24;
        addNumberField(ACTION_DELAY_FIELD, "cnpcgeckoaddon.boss.action_delay", y,
                phase.rangedAttack().getActionDelayTicks(), 0, 1200, 12);
        y += 24;
        addNumberField(COOLDOWN_FIELD, "cnpcgeckoaddon.boss.cooldown", y,
                phase.rangedAttack().getCooldownTicks(), 1, 12000, 80);

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.projectile_hint",
                guiLeft + 8, guiTop + 212, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 67, guiLeft + 6, guiTop + 230, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addDoneButton(guiLeft + 182, guiTop + 230, 60, 20);
    }

    private void addTargetModeRow(int id, int y, int mode) {
        addLabel(new GuiLabel(id, "cnpcgeckoaddon.boss.target_mode", guiLeft + 8, y + 6));
        addButton(new GuiButtonNop(this, id, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, mode));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == 67) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(phase.rangedAttack().getEffects(), "cnpcgeckoaddon.boss.effects_ranged"));
            return;
        }
        if (button.id == TARGET_MODE_BUTTON) {
            phase.rangedAttack().setTargetMode(button.getValue());
        } else if (button.id == ENABLED_BUTTON) {
            phase.rangedAttack().setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting ranged attack animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.rangedAttack().setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD, phase.rangedAttack()::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.rangedAttack().setAnimation(value);
            else animation.setValue(phase.rangedAttack().getAnimation());
        }
        applyNumberField(DAMAGE_FIELD, phase.rangedAttack()::setDamage);
        GuiTextFieldNop min = getTextField(MIN_RANGE_FIELD);
        GuiTextFieldNop max = getTextField(MAX_RANGE_FIELD);
        if (min != null && max != null) phase.rangedAttack().setRange(min.getInteger(), max.getInteger());
        applyNumberField(ACTION_DELAY_FIELD, phase.rangedAttack()::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, phase.rangedAttack()::setCooldownTicks);
    }
}
