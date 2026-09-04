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

/** Sweeping beam: lines that turn round the boss for a while and burn whoever they catch. */
public final class SubGuiBossBeam extends SubGuiFieldScreen implements ITextfieldListener {
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
        setBackground("menubg.png");
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

        addToggleRow(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", y, phase.isBeamEnabled());
        y += 21;

        addSelectRow(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, phase.getBeamAnimation());
        y += 21;

        addRowLabel("cnpcgeckoaddon.boss.beam_shape", y, TRIPLE_FIELD_X - 6 - 2);
        addSmallField(COUNT_FIELD, guiLeft + TRIPLE_FIELD_X, y, TRIPLE_FIELD_WIDTH,
                phase.getBeamCount(), 1, 4, 1);
        addSmallField(LENGTH_FIELD, guiLeft + TRIPLE_FIELD_X + TRIPLE_FIELD_STEP, y, TRIPLE_FIELD_WIDTH,
                phase.getBeamLength(), 3, 64, 20);
        addSmallField(WIDTH_FIELD, guiLeft + TRIPLE_FIELD_X + 2 * TRIPLE_FIELD_STEP, y, TRIPLE_FIELD_WIDTH,
                phase.getBeamWidth(), 1, 6, 1);
        y += 21;

        addRowLabel("cnpcgeckoaddon.boss.beam_spin", y, PAIR_FIELD_X - 6 - 2);
        addSmallField(DURATION_FIELD, guiLeft + PAIR_FIELD_X, y, 52, phase.getBeamDurationTicks(), 10, 1200, 120);
        // Plain rather than numbers-only: setNumbersOnly() lets nothing but digits through,
        // and the speed's sign is its direction - the minus would be impossible to type.
        addTextField(new GuiTextFieldNop(SPEED_FIELD, this, guiLeft + 190, y, 52, 20,
                Integer.toString(phase.getBeamDegreesPerSecond())));
        y += 21;

        addLabel(new GuiLabel(START_MODE_BUTTON, "cnpcgeckoaddon.boss.beam_start", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, START_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.BEAM_START_LABELS, phase.getBeamStartMode()));
        y += 21;

        addToggleRow(FOLLOW_BUTTON, "cnpcgeckoaddon.boss.beam_follow", y, phase.isBeamFollowsBoss());
        y += 21;
        addToggleRow(WALLS_BUTTON, "cnpcgeckoaddon.boss.beam_walls", y, phase.isBeamStopsAtWalls());
        y += 21;

        addPairRow(DAMAGE_FIELD, INTERVAL_FIELD, "cnpcgeckoaddon.boss.beam_hit", y,
                phase.getBeamDamage(), 0, 1000, 6,
                phase.getBeamHitIntervalTicks(), 1, 100, 10);
        y += 21;
        addNumberField(KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.knockback", y, phase.getBeamKnockback(), 0, 10, 1);
        y += 21;
        addPairRow(ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                phase.getBeamActionDelayTicks(), 0, 1200, 20,
                phase.getBeamCooldownTicks(), 1, 12000, 360);
        y += 21;

        int hintY = addWrappedHint(31, "cnpcgeckoaddon.boss.beam_hint", y + 3);
        int buttonsY = Math.max(hintY + 4, guiTop + 258);
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
            setSubGui(new SubGuiBossEffectList(phase.getBeamEffects(), "cnpcgeckoaddon.boss.effects_beam"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.setBeamEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == FOLLOW_BUTTON) {
            phase.setBeamFollowsBoss(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == WALLS_BUTTON) {
            phase.setBeamStopsAtWalls(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == START_MODE_BUTTON) {
            phase.setBeamStartMode(button.getValue());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting beam animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setBeamAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        phase::setBeamActionDelayTicks);
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
            if (BossAnimationGuiUtil.isValid(npc, value)) phase.setBeamAnimation(value);
            else animation.setValue(phase.getBeamAnimation());
        }
        GuiTextFieldNop count = getTextField(COUNT_FIELD);
        if (count != null) phase.setBeamCount(count.getInteger());
        GuiTextFieldNop length = getTextField(LENGTH_FIELD);
        if (length != null) phase.setBeamLength(length.getInteger());
        GuiTextFieldNop width = getTextField(WIDTH_FIELD);
        if (width != null) phase.setBeamWidth(width.getInteger());
        GuiTextFieldNop duration = getTextField(DURATION_FIELD);
        if (duration != null) phase.setBeamDurationTicks(duration.getInteger());
        GuiTextFieldNop speed = getTextField(SPEED_FIELD);
        if (speed != null) {
            phase.setBeamDegreesPerSecond(signed(speed));
            // Clamped on the way in, so what the screen shows is what the boss will do.
            speed.setValue(Integer.toString(phase.getBeamDegreesPerSecond()));
        }
        GuiTextFieldNop damage = getTextField(DAMAGE_FIELD);
        if (damage != null) phase.setBeamDamage(damage.getInteger());
        GuiTextFieldNop interval = getTextField(INTERVAL_FIELD);
        if (interval != null) phase.setBeamHitIntervalTicks(interval.getInteger());
        GuiTextFieldNop knockback = getTextField(KNOCKBACK_FIELD);
        if (knockback != null) phase.setBeamKnockback(knockback.getInteger());
        GuiTextFieldNop delay = getTextField(ACTION_DELAY_FIELD);
        if (delay != null) phase.setBeamActionDelayTicks(delay.getInteger());
        GuiTextFieldNop cooldown = getTextField(COOLDOWN_FIELD);
        if (cooldown != null) phase.setBeamCooldownTicks(cooldown.getInteger());
    }

    /** The plain field's number, read the way the leap screen reads its coordinates. */
    private static int signed(GuiTextFieldNop field) {
        String value = field.getValue().trim();
        try {
            // A lone minus is what a half-typed negative number looks like.
            return value.isEmpty() || value.equals("-") ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
