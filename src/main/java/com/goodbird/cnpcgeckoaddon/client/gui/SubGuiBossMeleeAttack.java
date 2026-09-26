package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeYesNo;
import com.goodbird.cnpcgeckoaddon.data.BossMeleeAttackSettings;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

public final class SubGuiBossMeleeAttack extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int DAMAGE_FIELD = 3;
    private static final int RANGE_FIELD = 4;
    private static final int KNOCKBACK_FIELD = 5;
    private static final int ACTION_DELAY_FIELD = 6;
    private static final int COOLDOWN_FIELD = 7;
    private static final int TARGET_MODE_BUTTON = 8;
    private static final int AIM_TURN_FIELD = 9;
    private static final int REACH_MODELS_BUTTON = 10;

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossMeleeAttack(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        // Two rows taller than the panel it used to be: the aim's own numbers sit under the
        // rows a builder already knows.
        imageHeight = 304;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new ThemeLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.melee_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;
        addLabel(new ThemeLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 8, y + 6));
        addButton(new ThemeYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20,
                phase.meleeAttack().isEnabled()));
        y += 24;
        addLabel(new ThemeLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 8, y + 6));
        addTextField(new ThemeTextField(ANIMATION_FIELD, this, guiLeft + 88, y, 106, 20,
                phase.meleeAttack().getAnimation()));
        addButton(new ThemeButton(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 24;
        addTargetModeRow(TARGET_MODE_BUTTON, y, phase.meleeAttack().getTargetMode());
        y += 24;
        addNumberField(DAMAGE_FIELD, "cnpcgeckoaddon.boss.damage", y,
                phase.meleeAttack().getDamage(), 1, 1000, 6);
        y += 24;
        addNumberField(RANGE_FIELD, "cnpcgeckoaddon.boss.attack_radius", y,
                phase.meleeAttack().getRange(), 1, 32, 3);
        y += 24;
        addNumberField(KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.knockback", y,
                phase.meleeAttack().getKnockback(), 0, 10, 1);
        y += 24;
        addNumberField(ACTION_DELAY_FIELD, "cnpcgeckoaddon.boss.action_delay", y,
                phase.meleeAttack().getActionDelayTicks(), 0, 1200, 8);
        y += 24;
        addNumberField(COOLDOWN_FIELD, "cnpcgeckoaddon.boss.cooldown", y,
                phase.meleeAttack().getCooldownTicks(), 1, 12000, 30);
        y += 24;
        addNumberField(AIM_TURN_FIELD, "cnpcgeckoaddon.boss.melee_aim_turn", y,
                phase.meleeAttack().getAimTurnDegrees(), BossMeleeAttackSettings.MIN_AIM_TURN,
                BossMeleeAttackSettings.MAX_AIM_TURN, 30);
        y += 24;
        addLabel(new ThemeLabel(REACH_MODELS_BUTTON, "cnpcgeckoaddon.boss.melee_reach_models",
                guiLeft + 8, y + 6));
        addButton(new ThemeYesNo(this, REACH_MODELS_BUTTON, guiLeft + 195, y, 47, 20,
                phase.meleeAttack().isReachAddsModels()));

        addButton(new ThemeButton(this, 67, guiLeft + 6, guiTop + 278, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addDoneButton(guiLeft + 182, guiTop + 278, 60, 20);
    }

    private void addTargetModeRow(int id, int y, int mode) {
        addLabel(new ThemeLabel(id, "cnpcgeckoaddon.boss.target_mode", guiLeft + 8, y + 6));
        addButton(new ThemeButton(this, id, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, mode));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == 67) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(phase.meleeAttack().getEffects(), "cnpcgeckoaddon.boss.effects_melee"));
            return;
        }
        if (button.id == TARGET_MODE_BUTTON) {
            phase.meleeAttack().setTargetMode(button.getValue());
        } else if (button.id == REACH_MODELS_BUTTON) {
            phase.meleeAttack().setReachAddsModels(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ENABLED_BUTTON) {
            phase.meleeAttack().setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.melee_attack_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.meleeAttack().setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD, phase.meleeAttack()::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.meleeAttack().setAnimation(value);
            else animation.setValue(phase.meleeAttack().getAnimation());
        }
        applyNumberField(DAMAGE_FIELD, phase.meleeAttack()::setDamage);
        applyNumberField(RANGE_FIELD, phase.meleeAttack()::setRange);
        applyNumberField(KNOCKBACK_FIELD, phase.meleeAttack()::setKnockback);
        applyNumberField(ACTION_DELAY_FIELD, phase.meleeAttack()::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, phase.meleeAttack()::setCooldownTicks);
        applyNumberField(AIM_TURN_FIELD, phase.meleeAttack()::setAimTurnDegrees);
    }
}
