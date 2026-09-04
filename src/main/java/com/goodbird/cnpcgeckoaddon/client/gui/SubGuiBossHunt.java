package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Hunt: the boss singles one victim out and goes after nobody else until the time runs out. */
public final class SubGuiBossHunt extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int TARGET_MODE_BUTTON = 3;
    private static final int DURATION_FIELD = 4;
    private static final int SPEED_FIELD = 5;
    private static final int CATCH_RADIUS_FIELD = 6;
    private static final int DAMAGE_FIELD = 7;
    private static final int CATCH_ENDS_BUTTON = 8;
    private static final int SILENCE_BUTTON = 9;
    private static final int GLOW_BUTTON = 10;
    private static final int ACTION_DELAY_FIELD = 11;
    private static final int COOLDOWN_FIELD = 12;
    private static final int EFFECTS_BUTTON = 67;
    /** Row labels take ids from here up, two per row, so a wrapped one keeps both its lines. */
    private static final int FIRST_ROW_LABEL = 100;

    private static final int LABEL_COLOR = 0xFFFFFF;
    private static final int LABEL_LINE_HEIGHT = 9;
    /** The pair fields start here, so a pair row's label may run up to this. */
    private static final int PAIR_FIELD_X = 130;
    /** The yes/no buttons start here, so a toggle row's label may run up to this. */
    private static final int TOGGLE_BUTTON_X = 155;

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private int nextRowLabel = FIRST_ROW_LABEL;

    public SubGuiBossHunt(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 262;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        nextRowLabel = FIRST_ROW_LABEL;
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.hunt_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addToggleRow(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", y, phase.isHuntEnabled());
        y += 21;

        addSelectRow(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, phase.getHuntAnimation());
        y += 21;

        addLabel(new GuiLabel(TARGET_MODE_BUTTON, "cnpcgeckoaddon.boss.target_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.getHuntTargetMode()));
        y += 21;

        addPairRow(DURATION_FIELD, SPEED_FIELD, "cnpcgeckoaddon.boss.hunt_duration", y,
                phase.getHuntDurationTicks(), 20, 1200, 160,
                phase.getHuntSpeedPercent(), 50, 300, 130);
        y += 21;
        addPairRow(CATCH_RADIUS_FIELD, DAMAGE_FIELD, "cnpcgeckoaddon.boss.hunt_catch", y,
                phase.getHuntCatchRadius(), 1, 6, 2,
                phase.getHuntDamage(), 0, 1000, 15);
        y += 21;

        addToggleRow(CATCH_ENDS_BUTTON, "cnpcgeckoaddon.boss.hunt_catch_ends", y, phase.isHuntCatchEnds());
        y += 21;
        addToggleRow(SILENCE_BUTTON, "cnpcgeckoaddon.boss.hunt_silence", y, phase.isHuntSilence());
        y += 21;
        addToggleRow(GLOW_BUTTON, "cnpcgeckoaddon.boss.hunt_glow", y, phase.isHuntGlow());
        y += 21;

        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.getHuntActionDelayTicks(), 0, 1200, 10,
                phase.getHuntCooldownTicks(), 1, 12000, 400);
        y += 21;

        int hintY = addWrappedHint(31, "cnpcgeckoaddon.boss.hunt_hint", y + 3);
        int buttonsY = Math.max(hintY + 4, guiTop + 236);
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, buttonsY, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, buttonsY, 60, 20,
                "gui.done", button -> close()));
    }

    private void addSelectRow(int id, String label, int y, String value) {
        addLabel(new GuiLabel(id, label, guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(id, this, guiLeft + 108, y, 86, 20, value));
        addButton(new GuiButtonNop(this, id, guiLeft + 198, y, 44, 20, "mco.template.button.select"));
    }

    /** A yes/no on one line, with a label that may take two. */
    private void addToggleRow(int id, String label, int y, boolean value) {
        addRowLabel(label, y, TOGGLE_BUTTON_X - 6 - 2);
        addButton(new GuiButtonYesNo(this, id, guiLeft + TOGGLE_BUTTON_X, y, 87, 20, value));
    }

    /** Two small numbers on one line, so the whole ability still fits a single screen. */
    private void addPairRow(int leftId, int rightId, String label, int y,
                            int leftValue, int leftMin, int leftMax, int leftFallback,
                            int rightValue, int rightMin, int rightMax, int rightFallback) {
        addRowLabel(label, y, PAIR_FIELD_X - 6 - 2);
        addPairedField(leftId, guiLeft + PAIR_FIELD_X, y, leftValue, leftMin, leftMax, leftFallback);
        addPairedField(rightId, guiLeft + 190, y, rightValue, rightMin, rightMax, rightFallback);
    }

    /**
     * A row's label, on one line when it fits beside the row's control and on two when it
     * does not.
     *
     * <p>The names this screen was given are sentences rather than words - "no other
     * abilities while hunting" - and in Russian they run well past where the button starts.
     * A GuiLabel never clips, so the long ones are broken at a space and stacked inside
     * the row's own twenty pixels instead of running under the control.</p>
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
            setSubGui(new SubGuiBossEffectList(phase.getHuntEffects(), "cnpcgeckoaddon.boss.effects_hunt"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.setHuntEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == CATCH_ENDS_BUTTON) {
            phase.setHuntCatchEnds(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == SILENCE_BUTTON) {
            phase.setHuntSilence(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == GLOW_BUTTON) {
            phase.setHuntGlow(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TARGET_MODE_BUTTON) {
            phase.setHuntTargetMode(button.getValue());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting hunt animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setHuntAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase::setHuntActionDelayTicks);
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
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.setHuntAnimation(value);
            else animation.setValue(phase.getHuntAnimation());
        }
        GuiTextFieldNop duration = getTextField(DURATION_FIELD);
        if (duration != null) phase.setHuntDurationTicks(duration.getInteger());
        GuiTextFieldNop speed = getTextField(SPEED_FIELD);
        if (speed != null) phase.setHuntSpeedPercent(speed.getInteger());
        GuiTextFieldNop catchRadius = getTextField(CATCH_RADIUS_FIELD);
        if (catchRadius != null) phase.setHuntCatchRadius(catchRadius.getInteger());
        GuiTextFieldNop damage = getTextField(DAMAGE_FIELD);
        if (damage != null) phase.setHuntDamage(damage.getInteger());
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setHuntActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setHuntCooldownTicks(cooldown.getInteger());
    }
}
