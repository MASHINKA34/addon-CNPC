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

/** Chain hook: yanks victims toward the boss. */
public final class SubGuiBossHook extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int TARGET_MODE_BUTTON = 3;
    private static final int TARGET_COUNT_FIELD = 4;
    private static final int DAMAGE_FIELD = 5;
    private static final int STRENGTH_FIELD = 6;
    private static final int DURATION_FIELD = 7;
    private static final int STOP_FIELD = 8;
    private static final int MIN_RANGE_FIELD = 9;
    private static final int MAX_RANGE_FIELD = 10;
    private static final int ACTION_DELAY_FIELD = 11;
    private static final int COOLDOWN_FIELD = 12;

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossHook(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
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
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.hook_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, phase.isHookEnabled()));
        y += 21;

        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 88, y, 106, 20,
                phase.getHookAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 21;

        addLabel(new GuiLabel(TARGET_MODE_BUTTON, "cnpcgeckoaddon.boss.target_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.getHookTargetMode()));
        y += 21;

        addNumberField(TARGET_COUNT_FIELD, "cnpcgeckoaddon.boss.hook_targets", y,
                phase.getHookTargetCount(), 1, 8, 1);
        y += 21;
        addNumberField(DAMAGE_FIELD, "cnpcgeckoaddon.boss.damage", y, phase.getHookDamage(), 0, 1000, 4);
        y += 21;
        addPullRow(y, phase.getHookPullStrength(), phase.getHookPullDurationTicks());
        y += 21;
        addNumberField(STOP_FIELD, "cnpcgeckoaddon.boss.hook_stop", y, phase.getHookStopDistance(), 0, 32, 2);
        y += 21;
        addRangeRow(y, phase.getHookMinRange(), phase.getHookMaxRange());
        y += 21;
        addNumberField(ACTION_DELAY_FIELD, "cnpcgeckoaddon.boss.action_delay", y,
                phase.getHookActionDelayTicks(), 0, 1200, 10);
        y += 21;
        addNumberField(COOLDOWN_FIELD, "cnpcgeckoaddon.boss.cooldown", y,
                phase.getHookCooldownTicks(), 1, 12000, 160);

        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 232, 60, 20,
                "gui.done", button -> close()));
    }

    /** Strength and duration share a row so the whole ability still fits one screen. */
    private void addPullRow(int y, int strength, int duration) {
        addLabel(new GuiLabel(STRENGTH_FIELD, "cnpcgeckoaddon.boss.hook_pull", guiLeft + 6, y + 6));
        addPairedField(STRENGTH_FIELD, guiLeft + 130, y, strength, 1, 20, 8);
        addPairedField(DURATION_FIELD, guiLeft + 190, y, duration, 1, 200, 20);
    }

    private void addRangeRow(int y, int min, int max) {
        addLabel(new GuiLabel(MIN_RANGE_FIELD, "cnpcgeckoaddon.boss.range", guiLeft + 6, y + 6));
        addPairedField(MIN_RANGE_FIELD, guiLeft + 130, y, min, 0, 64, 4);
        addPairedField(MAX_RANGE_FIELD, guiLeft + 190, y, max, 1, 128, 24);
    }

    private void addPairedField(int id, int x, int y, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, 52, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    private void addNumberField(int id, String label, int y, int value, int min, int max, int fallback) {
        addLabel(new GuiLabel(id, label, guiLeft + 6, y + 6));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + 175, y, 67, 20,
                Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            phase.setHookEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TARGET_MODE_BUTTON) {
            phase.setHookTargetMode(button.getValue());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting hook animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setHookAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase::setHookActionDelayTicks);
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
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.setHookAnimation(value);
            else animation.setValue(phase.getHookAnimation());
        }
        GuiTextFieldNop count = getTextField(TARGET_COUNT_FIELD);
        if (count != null) phase.setHookTargetCount(count.getInteger());
        GuiTextFieldNop damage = getTextField(DAMAGE_FIELD);
        if (damage != null) phase.setHookDamage(damage.getInteger());
        GuiTextFieldNop strength = getTextField(STRENGTH_FIELD);
        if (strength != null) phase.setHookPullStrength(strength.getInteger());
        GuiTextFieldNop duration = getTextField(DURATION_FIELD);
        if (duration != null) phase.setHookPullDurationTicks(duration.getInteger());
        GuiTextFieldNop stop = getTextField(STOP_FIELD);
        if (stop != null) phase.setHookStopDistance(stop.getInteger());
        GuiTextFieldNop min = getTextField(MIN_RANGE_FIELD);
        GuiTextFieldNop max = getTextField(MAX_RANGE_FIELD);
        if (min != null && max != null) phase.setHookRange(min.getInteger(), max.getInteger());
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setHookActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setHookCooldownTicks(cooldown.getInteger());
    }
}
