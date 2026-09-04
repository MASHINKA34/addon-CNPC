package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

import java.util.function.Consumer;

/** Barrier: a damage check - burn the shield in time for a stun window, or pay for missing it. */
public final class SubGuiBossBarrier extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int BREAK_ANIMATION_FIELD = 3;
    private static final int TRIGGER_BUTTON = 4;
    private static final int INTERVAL_FIELD = 5;
    private static final int AMOUNT_FIELD = 6;
    private static final int PERCENT_FIELD = 7;
    private static final int TIMEOUT_FIELD = 8;
    private static final int WINDOW_FIELD = 9;
    private static final int WINDOW_DAMAGE_FIELD = 10;
    private static final int FAIL_MODE_BUTTON = 11;
    private static final int FAIL_DAMAGE_FIELD = 12;
    private static final int FAIL_HEAL_FIELD = 13;
    private static final int EFFECTS_BUTTON = 67;
    /** Row labels take ids from here up, two per row, so a wrapped one keeps both its lines. */
    private static final int FIRST_ROW_LABEL = 100;

    private static final int LABEL_COLOR = 0xFFFFFF;
    private static final int LABEL_LINE_HEIGHT = 9;
    /** The pair fields start here, so a pair row's label may run up to this. */
    private static final int PAIR_FIELD_X = 140;
    /** The yes/no buttons start here, so a toggle row's label may run up to this. */
    private static final int TOGGLE_BUTTON_X = 155;
    /** The animation fields start here, so a select row's label may run up to this. */
    private static final int SELECT_FIELD_X = 108;
    /** The rule buttons start here, with the rule's own number in the last slot of the row. */
    private static final int RULE_BUTTON_X = 96;
    private static final int RULE_FIELD_X = 204;

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private int nextRowLabel = FIRST_ROW_LABEL;

    public SubGuiBossBarrier(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 248;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        nextRowLabel = FIRST_ROW_LABEL;
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.barrier_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addToggleRow(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", y, phase.isBarrierEnabled());
        y += 21;

        addSelectRow(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, phase.getBarrierAnimation());
        y += 21;
        addSelectRow(BREAK_ANIMATION_FIELD, "cnpcgeckoaddon.boss.barrier_break_animation", y,
                phase.getBarrierBreakAnimation());
        y += 21;

        // The rule and its number share a row: the timer rule's button reads "every (ticks)"
        // and the field beside it is the ticks. Under the other rule the field goes.
        addRowLabel("cnpcgeckoaddon.boss.barrier_trigger", y, RULE_BUTTON_X - 6 - 2);
        addButton(new GuiButtonNop(this, TRIGGER_BUTTON, guiLeft + RULE_BUTTON_X, y, 104, 20,
                BossPhaseData.BARRIER_TRIGGER_LABELS, phase.getBarrierTrigger()));
        addRuleField(INTERVAL_FIELD, y, phase.getBarrierIntervalTicks(), 20, 24000, 600);
        y += 21;

        addPairRow(AMOUNT_FIELD, PERCENT_FIELD, "cnpcgeckoaddon.boss.barrier_amount", y,
                phase.getBarrierAmount(), 1, 1000000, 200,
                phase.getBarrierPercent(), 0, 100, 0);
        y += 21;
        addNumberField(TIMEOUT_FIELD, "cnpcgeckoaddon.boss.barrier_timeout", y,
                phase.getBarrierTimeoutTicks(), 0, 24000, 300);
        y += 21;
        addPairRow(WINDOW_FIELD, WINDOW_DAMAGE_FIELD, "cnpcgeckoaddon.boss.barrier_window", y,
                phase.getBarrierBreakWindowTicks(), 0, 1200, 60,
                phase.getBarrierBreakDamageTakenPercent(), 100, 500, 150);
        y += 21;

        // The two rules with a number of their own share the last slot; only one is ever on
        // the screen, and the enrage rule shows neither.
        addRowLabel("cnpcgeckoaddon.boss.barrier_fail", y, RULE_BUTTON_X - 6 - 2);
        addButton(new GuiButtonNop(this, FAIL_MODE_BUTTON, guiLeft + RULE_BUTTON_X, y, 104, 20,
                BossPhaseData.BARRIER_FAIL_LABELS, phase.getBarrierFailMode()));
        addRuleField(FAIL_DAMAGE_FIELD, y, phase.getBarrierFailDamage(), 0, 1000, 20);
        addRuleField(FAIL_HEAL_FIELD, y, phase.getBarrierFailHealPercent(), 1, 100, 25);
        y += 21;

        int hintY = addWrappedHint(31, "cnpcgeckoaddon.boss.barrier_hint", y + 3);
        int buttonsY = Math.max(hintY + 4, guiTop + 212);
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, buttonsY, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, buttonsY, 60, 20,
                "gui.done", button -> close()));
        applyRuleRows();
    }

    /**
     * Takes the other rule's number off the screen.
     *
     * <p>Hidden in place rather than laid out again, for the hazard screen's reason: this
     * GUI framework has no widget-clearing rebuild, so a screen that re-flowed itself on
     * every click would stack a second copy of every row on the first. A hidden field keeps
     * whatever was typed into it, so switching rules and back loses nothing.</p>
     */
    private void applyRuleRows() {
        showField(INTERVAL_FIELD, phase.getBarrierTrigger() == BossPhaseData.BARRIER_TRIGGER_TIMER);
        int mode = phase.getBarrierFailMode();
        showField(FAIL_DAMAGE_FIELD, mode == BossPhaseData.BARRIER_FAIL_DAMAGE);
        showField(FAIL_HEAL_FIELD, mode == BossPhaseData.BARRIER_FAIL_HEAL);
    }

    private void showField(int id, boolean shown) {
        GuiTextFieldNop field = getTextField(id);
        if (field != null) {
            field.enabled = shown;
        }
    }

    private void addSelectRow(int id, String label, int y, String value) {
        addRowLabel(label, y, SELECT_FIELD_X - 6 - 2);
        addTextField(new GuiTextFieldNop(id, this, guiLeft + SELECT_FIELD_X, y, 86, 20, value));
        addButton(new GuiButtonNop(this, id, guiLeft + 198, y, 44, 20, "mco.template.button.select"));
    }

    /** A yes/no on one line, with a label that may take two. */
    private void addToggleRow(int id, String label, int y, boolean value) {
        addRowLabel(label, y, TOGGLE_BUTTON_X - 6 - 2);
        addButton(new GuiButtonYesNo(this, id, guiLeft + TOGGLE_BUTTON_X, y, 87, 20, value));
    }

    /** Two small numbers on one line, so the whole barrier still fits a single screen. */
    private void addPairRow(int leftId, int rightId, String label, int y,
                            int leftValue, int leftMin, int leftMax, int leftFallback,
                            int rightValue, int rightMin, int rightMax, int rightFallback) {
        addRowLabel(label, y, PAIR_FIELD_X - 6 - 2);
        addSmallField(leftId, guiLeft + PAIR_FIELD_X, y, 48, leftValue, leftMin, leftMax, leftFallback);
        addSmallField(rightId, guiLeft + 194, y, 48, rightValue, rightMin, rightMax, rightFallback);
    }

    /** The number a rule button carries beside it, in the last slot of its row. */
    private void addRuleField(int id, int y, int value, int min, int max, int fallback) {
        addSmallField(id, guiLeft + RULE_FIELD_X, y, 38, value, min, max, fallback);
    }

    /**
     * A row's label, on one line when it fits beside the row's control and on two when it
     * does not - the hunt screen's answer to names that are sentences rather than words.
     */
    private void addRowLabel(String key, int y, int width) {
        String text = I18n.get(key);
        int x = guiLeft + 6;
        if (font.width(text) <= width) {
            addLabel(new GuiLabel(nextRowLabel, Component.literal(text), LABEL_COLOR, x, y + 6,
                    width, LABEL_LINE_HEIGHT));
            nextRowLabel += 2;
            return;
        }
        // Greedy: the first line takes every word that fits, the second takes the rest.
        StringBuilder first = new StringBuilder();
        StringBuilder second = new StringBuilder();
        for (String word : text.split(" ")) {
            if (second.isEmpty() && font.width(first + (first.isEmpty() ? "" : " ") + word) <= width) {
                if (!first.isEmpty()) {
                    first.append(' ');
                }
                first.append(word);
            } else {
                if (!second.isEmpty()) {
                    second.append(' ');
                }
                second.append(word);
            }
        }
        addLabel(new GuiLabel(nextRowLabel, Component.literal(first.toString()), LABEL_COLOR, x, y + 1,
                width, LABEL_LINE_HEIGHT));
        addLabel(new GuiLabel(nextRowLabel + 1, Component.literal(second.toString()), LABEL_COLOR, x,
                y + 1 + LABEL_LINE_HEIGHT, width, LABEL_LINE_HEIGHT));
        nextRowLabel += 2;
    }

    private void addSmallField(int id, int x, int y, int width, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, width, 20, Integer.toString(value));
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
            setSubGui(new SubGuiBossEffectList(phase.getBarrierFailEffects(), "cnpcgeckoaddon.boss.effects_barrier"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.setBarrierEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TRIGGER_BUTTON) {
            phase.setBarrierTrigger(button.getValue());
            applyRuleRows();
        } else if (button.id == FAIL_MODE_BUTTON) {
            phase.setBarrierFailMode(button.getValue());
            applyRuleRows();
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting barrier animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setBarrierAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
            }));
        } else if (button.id == BREAK_ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting barrier break animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setBarrierBreakAnimation(name);
                getTextField(BREAK_ANIMATION_FIELD).setValue(name);
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
        applyAnimation(ANIMATION_FIELD, phase.getBarrierAnimation(), phase::setBarrierAnimation);
        applyAnimation(BREAK_ANIMATION_FIELD, phase.getBarrierBreakAnimation(), phase::setBarrierBreakAnimation);
        // Read whether or not the rule in force shows them: a hidden field keeps the number
        // a builder typed under the other rule, rather than losing it on a click.
        GuiTextFieldNop interval = getTextField(INTERVAL_FIELD);
        if (interval != null) phase.setBarrierIntervalTicks(interval.getInteger());
        GuiTextFieldNop amount = getTextField(AMOUNT_FIELD);
        if (amount != null) phase.setBarrierAmount(amount.getInteger());
        GuiTextFieldNop percent = getTextField(PERCENT_FIELD);
        if (percent != null) phase.setBarrierPercent(percent.getInteger());
        GuiTextFieldNop timeout = getTextField(TIMEOUT_FIELD);
        if (timeout != null) phase.setBarrierTimeoutTicks(timeout.getInteger());
        GuiTextFieldNop window = getTextField(WINDOW_FIELD);
        if (window != null) phase.setBarrierBreakWindowTicks(window.getInteger());
        GuiTextFieldNop windowDamage = getTextField(WINDOW_DAMAGE_FIELD);
        if (windowDamage != null) phase.setBarrierBreakDamageTakenPercent(windowDamage.getInteger());
        GuiTextFieldNop failDamage = getTextField(FAIL_DAMAGE_FIELD);
        if (failDamage != null) phase.setBarrierFailDamage(failDamage.getInteger());
        GuiTextFieldNop failHeal = getTextField(FAIL_HEAL_FIELD);
        if (failHeal != null) phase.setBarrierFailHealPercent(failHeal.getInteger());
    }

    /** Keeps a typed animation only when the model has it; otherwise the field snaps back. */
    private void applyAnimation(int id, String current, Consumer<String> setter) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return;
        }
        String value = field.getValue().trim();
        if (BossAnimationGuiUtil.isValid(npc, value)) {
            setter.accept(value);
        } else {
            field.setValue(current);
        }
    }
}
