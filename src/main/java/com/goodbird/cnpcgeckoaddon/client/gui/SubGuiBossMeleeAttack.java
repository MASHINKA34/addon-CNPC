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

public final class SubGuiBossMeleeAttack extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int DAMAGE_FIELD = 3;
    private static final int RANGE_FIELD = 4;
    private static final int KNOCKBACK_FIELD = 5;
    private static final int ACTION_DELAY_FIELD = 6;
    private static final int COOLDOWN_FIELD = 7;
    private static final int TARGET_MODE_BUTTON = 8;

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossMeleeAttack(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
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
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.melee_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;
        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20,
                phase.isMeleeAttackEnabled()));
        y += 24;
        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 8, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 88, y, 106, 20,
                phase.getMeleeAttackAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 24;
        addTargetModeRow(TARGET_MODE_BUTTON, y, phase.getMeleeAttackTargetMode());
        y += 24;
        addNumberField(DAMAGE_FIELD, "cnpcgeckoaddon.boss.damage", y,
                phase.getMeleeAttackDamage(), 1, 1000, 6);
        y += 24;
        addNumberField(RANGE_FIELD, "cnpcgeckoaddon.boss.attack_radius", y,
                phase.getMeleeAttackRange(), 1, 32, 3);
        y += 24;
        addNumberField(KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.knockback", y,
                phase.getMeleeAttackKnockback(), 0, 10, 1);
        y += 24;
        addNumberField(ACTION_DELAY_FIELD, "cnpcgeckoaddon.boss.action_delay", y,
                phase.getMeleeAttackActionDelayTicks(), 0, 1200, 8);
        y += 24;
        addNumberField(COOLDOWN_FIELD, "cnpcgeckoaddon.boss.cooldown", y,
                phase.getMeleeAttackCooldownTicks(), 1, 12000, 30);

        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 230, 60, 20,
                "gui.done", button -> close()));
    }


    private void addTargetModeRow(int id, int y, int mode) {
        addLabel(new GuiLabel(id, "cnpcgeckoaddon.boss.target_mode", guiLeft + 8, y + 6));
        addButton(new GuiButtonNop(this, id, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, mode));
    }

    private void addNumberField(int id, String label, int y, int value, int min, int max, int fallback) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + 172, y, 70, 20,
                Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == TARGET_MODE_BUTTON) {
            phase.setMeleeAttackTargetMode(button.getValue());
        } else if (button.id == ENABLED_BUTTON) {
            phase.setMeleeAttackEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting melee attack animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setMeleeAttackAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD, phase::setMeleeAttackActionDelayTicks);
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
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.setMeleeAttackAnimation(value);
            else animation.setValue(phase.getMeleeAttackAnimation());
        }
        GuiTextFieldNop damage = getTextField(DAMAGE_FIELD);
        if (damage != null) phase.setMeleeAttackDamage(damage.getInteger());
        GuiTextFieldNop range = getTextField(RANGE_FIELD);
        if (range != null) phase.setMeleeAttackRange(range.getInteger());
        GuiTextFieldNop knockback = getTextField(KNOCKBACK_FIELD);
        if (knockback != null) phase.setMeleeAttackKnockback(knockback.getInteger());
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setMeleeAttackActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setMeleeAttackCooldownTicks(cooldown.getInteger());
    }
}
