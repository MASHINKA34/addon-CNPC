package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/** Sweeping beam: lines that turn round the boss for a while and burn whoever they catch. */
public final class SubGuiBossBeam extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int COUNT_FIELD = 3;
    private static final int LENGTH_FIELD = 4;
    private static final int WIDTH_FIELD = 5;
    private static final int DURATION_FIELD = 6;
    private static final int SPEED_FIELD = 7;
    private static final int START_MODE_BUTTON = 8;
    private static final int FOLLOW_BUTTON = 9;
    private static final int WALLS_BUTTON = 10;
    private static final int DAMAGE_FIELD = 11;
    private static final int INTERVAL_FIELD = 12;
    private static final int KNOCKBACK_FIELD = 13;
    private static final int ACTION_DELAY_FIELD = 14;
    private static final int COOLDOWN_FIELD = 15;
    private static final int EFFECTS_BUTTON = 67;
    /** Row labels take ids from here up, two per row, so a wrapped one keeps both its lines. */
    private static final int FIRST_ROW_LABEL = 100;

    private static final int LABEL_COLOR = 0xFFFFFF;
    private static final int LABEL_LINE_HEIGHT = 9;
    /** The pair fields start here, so a pair row's label may run up to this. */
    private static final int PAIR_FIELD_X = 130;
    /** The three fields of a triple row start here and are this wide, with a gap between. */
    private static final int TRIPLE_FIELD_X = 116;
    private static final int TRIPLE_FIELD_WIDTH = 40;
    private static final int TRIPLE_FIELD_STEP = 43;
    /** The yes/no buttons start here, so a toggle row's label may run up to this. */
    private static final int TOGGLE_BUTTON_X = 155;

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private int nextRowLabel = FIRST_ROW_LABEL;

    public SubGuiBossBeam(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        imageHeight = 284;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        nextRowLabel = FIRST_ROW_LABEL;
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.beam_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addToggleRow(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", y, phase.beam().isEnabled());
        y += 21;

        addSelectRow(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, phase.beam().getAnimation());
        y += 21;

        addRowLabel("cnpcgeckoaddon.boss.beam_shape", y, TRIPLE_FIELD_X - 6 - 2);
        addSmallField(COUNT_FIELD, guiLeft + TRIPLE_FIELD_X, y, TRIPLE_FIELD_WIDTH,
                phase.beam().getCount(), 1, 4, 1);
        addSmallField(LENGTH_FIELD, guiLeft + TRIPLE_FIELD_X + TRIPLE_FIELD_STEP, y, TRIPLE_FIELD_WIDTH,
                phase.beam().getLength(), 3, 64, 20);
        addSmallField(WIDTH_FIELD, guiLeft + TRIPLE_FIELD_X + 2 * TRIPLE_FIELD_STEP, y, TRIPLE_FIELD_WIDTH,
                phase.beam().getWidth(), 1, 6, 1);
        y += 21;

        addRowLabel("cnpcgeckoaddon.boss.beam_spin", y, PAIR_FIELD_X - 6 - 2);
        addSmallField(DURATION_FIELD, guiLeft + PAIR_FIELD_X, y, 52, phase.beam().getDurationTicks(), 10, 1200, 120);
        // Plain rather than numbers-only: setNumbersOnly() lets nothing but digits through,
        // and the speed's sign is its direction - the minus would be impossible to type.
        addTextField(new GuiTextFieldNop(SPEED_FIELD, this, guiLeft + 190, y, 52, 20,
                Integer.toString(phase.beam().getDegreesPerSecond())));
        y += 21;

        addLabel(new GuiLabel(START_MODE_BUTTON, "cnpcgeckoaddon.boss.beam_start", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, START_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.BEAM_START_LABELS, phase.beam().getStartMode()));
        y += 21;

        addToggleRow(FOLLOW_BUTTON, "cnpcgeckoaddon.boss.beam_follow", y, phase.beam().isFollowsBoss());
        y += 21;
        addToggleRow(WALLS_BUTTON, "cnpcgeckoaddon.boss.beam_walls", y, phase.beam().isStopsAtWalls());
        y += 21;

        addPairRow(DAMAGE_FIELD, INTERVAL_FIELD, "cnpcgeckoaddon.boss.beam_hit", y,
                phase.beam().getDamage(), 0, 1000, 6,
                phase.beam().getHitIntervalTicks(), 1, 100, 10);
        y += 21;
        addNumberField(KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.knockback", y, phase.beam().getKnockback(), 0, 10, 1);
        y += 21;
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.beam().getActionDelayTicks(), 0, 1200, 20,
                phase.beam().getCooldownTicks(), 1, 12000, 360);
        y += 21;

        int hintY = addWrappedHint(31, "cnpcgeckoaddon.boss.beam_hint", y + 3);
        int buttonsY = Math.max(hintY + 4, guiTop + 258);
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, buttonsY, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addDoneButton(guiLeft + 182, buttonsY, 60, 20);
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
        addSmallField(leftId, guiLeft + PAIR_FIELD_X, y, 52, leftValue, leftMin, leftMax, leftFallback);
        addSmallField(rightId, guiLeft + 190, y, 52, rightValue, rightMin, rightMax, rightFallback);
    }

    /**
     * A row's label, on one line when it fits beside the row's control and on two when it
     * does not, the way the hunt screen lays its long names out.
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
            setSubGui(new SubGuiBossEffectList(phase.beam().getEffects(), "cnpcgeckoaddon.boss.effects_beam"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.beam().setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == FOLLOW_BUTTON) {
            phase.beam().setFollowsBoss(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == WALLS_BUTTON) {
            phase.beam().setStopsAtWalls(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == START_MODE_BUTTON) {
            phase.beam().setStartMode(button.getValue());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.beam_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.beam().setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase.beam()::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.beam().setAnimation(value);
            else animation.setValue(phase.beam().getAnimation());
        }
        applyNumberField(COUNT_FIELD, phase.beam()::setCount);
        applyNumberField(LENGTH_FIELD, phase.beam()::setLength);
        applyNumberField(WIDTH_FIELD, phase.beam()::setWidth);
        applyNumberField(DURATION_FIELD, phase.beam()::setDurationTicks);
        GuiTextFieldNop speed = getTextField(SPEED_FIELD);
        if (speed != null) {
            phase.beam().setDegreesPerSecond(signed(speed));
            // Clamped on the way in, so what the screen shows is what the boss will do.
            speed.setValue(Integer.toString(phase.beam().getDegreesPerSecond()));
        }
        applyNumberField(DAMAGE_FIELD, phase.beam()::setDamage);
        applyNumberField(INTERVAL_FIELD, phase.beam()::setHitIntervalTicks);
        applyNumberField(KNOCKBACK_FIELD, phase.beam()::setKnockback);
        applyNumberField(ACTION_DELAY_FIELD, phase.beam()::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, phase.beam()::setCooldownTicks);
    }

}
