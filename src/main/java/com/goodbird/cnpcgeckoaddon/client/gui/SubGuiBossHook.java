package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Chain hook: yanks victims toward the boss, or cinches a whole group onto one spot. */
public final class SubGuiBossHook extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int TARGET_MODE_BUTTON = 3;
    private static final int HOOK_MODE_BUTTON = 4;
    private static final int TARGET_COUNT_FIELD = 5;
    private static final int STOP_FIELD = 6;
    private static final int DAMAGE_FIELD = 7;
    private static final int STRENGTH_FIELD = 8;
    private static final int DURATION_FIELD = 9;
    private static final int MIN_RANGE_FIELD = 10;
    private static final int MAX_RANGE_FIELD = 11;
    private static final int ACTION_DELAY_FIELD = 12;
    private static final int COOLDOWN_FIELD = 13;
    private static final int CORD_STYLE_BUTTON = 14;
    private static final int EFFECTS_BUTTON = 67;

    private static final String[] CORD_STYLE_LABELS = HookCordStyles.values().stream()
            .map(HookCordStyles.Style::translationKey)
            .toArray(String[]::new);

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

        addLabel(new GuiLabel(HOOK_MODE_BUTTON, "cnpcgeckoaddon.boss.hook_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, HOOK_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.HOOK_MODE_LABELS, phase.getHookMode()));
        y += 21;

        addLabel(new GuiLabel(CORD_STYLE_BUTTON, "cnpcgeckoaddon.boss.hook_cord", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, CORD_STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                CORD_STYLE_LABELS, cordStyleIndex()));
        y += 21;

        addPairRow(TARGET_COUNT_FIELD, STOP_FIELD, "cnpcgeckoaddon.boss.hook_targets_stop", y,
                phase.getHookTargetCount(), 1, 8, 1,
                phase.getHookStopDistance(), 0, 32, 2);
        y += 21;
        addNumberField(DAMAGE_FIELD, "cnpcgeckoaddon.boss.damage", y, phase.getHookDamage(), 0, 1000, 4);
        y += 21;
        addPairRow(STRENGTH_FIELD, DURATION_FIELD, "cnpcgeckoaddon.boss.hook_pull", y,
                phase.getHookPullStrength(), 1, 20, 8,
                phase.getHookPullDurationTicks(), 1, 200, 20);
        y += 21;
        addPairRow(MIN_RANGE_FIELD, MAX_RANGE_FIELD, "cnpcgeckoaddon.boss.range", y,
                phase.getHookMinRange(), 0, 64, 4,
                phase.getHookMaxRange(), 1, 128, 24);
        y += 21;
        // The two tick counts share a line so the cord style gets one of its own and the
        // screen still ends above the buttons.
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.hook_timing", y,
                phase.getHookActionDelayTicks(), 0, 1200, 10,
                phase.getHookCooldownTicks(), 1, 12000, 160);

        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, guiTop + 232, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 232, 60, 20,
                "gui.done", button -> close()));
    }

    private int cordStyleIndex() {
        String id = phase.getHookCordStyle();
        for (int i = 0; i < HookCordStyles.values().size(); i++) {
            if (HookCordStyles.values().get(i).id().equals(id)) {
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
            setSubGui(new SubGuiBossEffectList(phase.getHookEffects(), "cnpcgeckoaddon.boss.effects_hook"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.setHookEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TARGET_MODE_BUTTON) {
            phase.setHookTargetMode(button.getValue());
        } else if (button.id == HOOK_MODE_BUTTON) {
            phase.setHookMode(button.getValue());
        } else if (button.id == CORD_STYLE_BUTTON) {
            phase.setHookCordStyle(HookCordStyles.values().get(button.getValue()).id());
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
        GuiTextFieldNop stop = getTextField(STOP_FIELD);
        if (stop != null) phase.setHookStopDistance(stop.getInteger());
        GuiTextFieldNop damage = getTextField(DAMAGE_FIELD);
        if (damage != null) phase.setHookDamage(damage.getInteger());
        GuiTextFieldNop strength = getTextField(STRENGTH_FIELD);
        if (strength != null) phase.setHookPullStrength(strength.getInteger());
        GuiTextFieldNop duration = getTextField(DURATION_FIELD);
        if (duration != null) phase.setHookPullDurationTicks(duration.getInteger());
        GuiTextFieldNop min = getTextField(MIN_RANGE_FIELD);
        GuiTextFieldNop max = getTextField(MAX_RANGE_FIELD);
        if (min != null && max != null) phase.setHookRange(min.getInteger(), max.getInteger());
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setHookActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setHookCooldownTicks(cooldown.getInteger());
    }
}
