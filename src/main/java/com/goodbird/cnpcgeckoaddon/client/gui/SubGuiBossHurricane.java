package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossHurricaneSettings;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.List;

/** Hurricane: storms let go on the floor that keep whoever they catch, lift them, spin them and throw them clear. */
public final class SubGuiBossHurricane extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int ACTION_DELAY_FIELD = 3;
    private static final int COOLDOWN_FIELD = 4;
    private static final int MODE_BUTTON = 5;
    private static final int AIM_BUTTON = 6;
    private static final int TARGET_MODE_BUTTON = 7;
    private static final int COUNT_FIELD = 8;
    private static final int SPEED_FIELD = 9;
    private static final int RANGE_FIELD = 10;
    private static final int LIFETIME_FIELD = 11;
    private static final int RADIUS_FIELD = 12;
    private static final int BOUNCES_FIELD = 13;
    private static final int SPIRAL_DEGREES_FIELD = 14;
    private static final int SPIRAL_GROWTH_FIELD = 15;
    private static final int LIFT_HEIGHT_FIELD = 16;
    private static final int LIFT_TICKS_FIELD = 17;
    private static final int HOLD_FIELD = 18;
    private static final int SPIN_FIELD = 19;
    private static final int SPIN_VIEW_BUTTON = 20;
    private static final int DAMAGE_FIELD = 21;
    private static final int INTERVAL_FIELD = 22;
    private static final int THROW_FIELD = 23;
    private static final int THROW_UP_FIELD = 24;
    private static final int TUNING_BUTTON = 25;
    private static final int EFFECTS_BUTTON = 67;

    private static final int TITLE_LABEL = 30;
    private static final int HINT_LABEL = 40;
    /** Where the second lines of wrapped row labels are counted from. */
    private static final int WRAPPED_LABEL = 80;

    /** The first row's offset from the top of the panel, clear of the title. */
    private static final int FIRST_ROW = 18;
    private static final int CONTROL_HEIGHT = 20;
    private static final int ROW = CONTROL_HEIGHT + 1;
    private static final int LABEL_X = 8;
    private static final int LABEL_DROP = 6;
    private static final int RIGHT_EDGE = 242;
    private static final int TOGGLE_X = 196;
    private static final int TOGGLE_WIDTH = 46;
    private static final int CHOICE_X = 112;
    private static final int CHOICE_WIDTH = 130;
    /** An animation row: the name typed in, and the picker beside it. */
    private static final int SELECT_FIELD_X = 108;
    private static final int SELECT_FIELD_WIDTH = 86;
    private static final int SELECT_BUTTON_X = 198;
    private static final int SELECT_BUTTON_WIDTH = 44;
    /** Two numbers to a row, the second flush with the right edge; a lone number takes the second place. */
    private static final int PAIR_X = 140;
    private static final int PAIR_SECOND_X = 194;
    private static final int PAIR_WIDTH = 48;
    private static final int HINT_GAP = 4;
    private static final int BUTTONS_GAP = 5;
    private static final int BOTTOM_MARGIN = 8;

    private static final String HINT = "cnpcgeckoaddon.boss.hurricane_hint";

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private int wrappedLabel;

    public SubGuiBossHurricane(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // The panel is centred from imageHeight, so the height is settled before super.init()
        // reads it: the locale decides how many lines the hint takes. A hurricane has more
        // settings than a panel, so it scrolls.
        imageHeight = layout(false);
        super.init();
        layout(true);
    }

    /**
     * Puts every row down the panel, or with {@code place} false only measures how tall they
     * come to - the dash screen's way of keeping the panel and its contents from drifting apart.
     *
     * @return the height the panel needs
     */
    private int layout(boolean place) {
        BossHurricaneSettings hurricane = phase.hurricane();
        wrappedLabel = WRAPPED_LABEL;
        if (place) {
            addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.hurricane_phase",
                    phaseIndex), guiLeft + LABEL_X, guiTop + 5, 0xFFFFFF));
        }
        int y = FIRST_ROW;
        y = toggle(place, ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", y, hurricane.isEnabled());
        y = select(place, ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, hurricane.getAnimation());
        y = pair(place, ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                hurricane.getActionDelayTicks(), 0, 1200, 20, hurricane.getCooldownTicks(), 1, 12000, 400);
        y = choice(place, MODE_BUTTON, "cnpcgeckoaddon.boss.hurricane_mode", y,
                BossPhaseData.HURRICANE_MODE_LABELS, hurricane.getLaunchMode());
        y = choice(place, AIM_BUTTON, "cnpcgeckoaddon.boss.hurricane_aim", y,
                BossPhaseData.HURRICANE_AIM_LABELS, hurricane.getAim());
        y = choice(place, TARGET_MODE_BUTTON, "cnpcgeckoaddon.boss.target_mode", y,
                BossTargetMode.LABELS, hurricane.getTargetMode());
        y = single(place, COUNT_FIELD, "cnpcgeckoaddon.boss.hurricane_count", y,
                hurricane.getCount(), 1, BossHurricaneSettings.MAX_COUNT, 3);
        y = pair(place, SPEED_FIELD, RANGE_FIELD, "cnpcgeckoaddon.boss.hurricane_run", y,
                hurricane.getSpeedTenths(), BossHurricaneSettings.MIN_SPEED, BossHurricaneSettings.MAX_SPEED, 4,
                hurricane.getRange(), BossHurricaneSettings.MIN_RANGE, BossHurricaneSettings.MAX_RANGE, 16);
        y = single(place, LIFETIME_FIELD, "cnpcgeckoaddon.boss.hurricane_lifetime", y,
                hurricane.getLifetimeTicks(), BossHurricaneSettings.MIN_LIFETIME_TICKS,
                BossHurricaneSettings.MAX_LIFETIME_TICKS, 200);
        y = single(place, RADIUS_FIELD, "cnpcgeckoaddon.boss.hurricane_radius", y,
                hurricane.getRadiusTenths(), BossHurricaneSettings.MIN_RADIUS, BossHurricaneSettings.MAX_RADIUS, 15);
        y = single(place, BOUNCES_FIELD, "cnpcgeckoaddon.boss.hurricane_bounces", y,
                hurricane.getBounces(), 0, BossHurricaneSettings.MAX_BOUNCES, 0);
        y = pair(place, SPIRAL_DEGREES_FIELD, SPIRAL_GROWTH_FIELD, "cnpcgeckoaddon.boss.hurricane_spiral", y,
                hurricane.getSpiralDegrees(), BossHurricaneSettings.MIN_SPIRAL_DEGREES,
                BossHurricaneSettings.MAX_SPIRAL_DEGREES, 12,
                hurricane.getSpiralGrowthTenths(), 0, BossHurricaneSettings.MAX_SPIRAL_GROWTH, 2);
        y = pair(place, LIFT_HEIGHT_FIELD, LIFT_TICKS_FIELD, "cnpcgeckoaddon.boss.hurricane_lift", y,
                hurricane.getLiftHeight(), BossHurricaneSettings.MIN_LIFT_HEIGHT, BossHurricaneSettings.MAX_LIFT_HEIGHT, 4,
                hurricane.getLiftTicks(), BossHurricaneSettings.MIN_LIFT_TICKS, BossHurricaneSettings.MAX_LIFT_TICKS, 10);
        y = single(place, HOLD_FIELD, "cnpcgeckoaddon.boss.hurricane_hold", y,
                hurricane.getHoldTicks(), BossHurricaneSettings.MIN_HOLD_TICKS, BossHurricaneSettings.MAX_HOLD_TICKS, 60);
        y = single(place, SPIN_FIELD, "cnpcgeckoaddon.boss.hurricane_spin", y,
                hurricane.getSpinDegrees(), 0, BossHurricaneSettings.MAX_SPIN_DEGREES, 15);
        y = toggle(place, SPIN_VIEW_BUTTON, "cnpcgeckoaddon.boss.hurricane_spin_view", y, hurricane.isSpinView());
        y = pair(place, DAMAGE_FIELD, INTERVAL_FIELD, "cnpcgeckoaddon.boss.hurricane_hit", y,
                hurricane.getDamage(), 0, BossHurricaneSettings.MAX_DAMAGE, 4,
                hurricane.getDamageIntervalTicks(), BossHurricaneSettings.MIN_DAMAGE_INTERVAL,
                BossHurricaneSettings.MAX_DAMAGE_INTERVAL, 20);
        y = pair(place, THROW_FIELD, THROW_UP_FIELD, "cnpcgeckoaddon.boss.hurricane_throw", y,
                hurricane.getThrowTenths(), 0, BossHurricaneSettings.MAX_THROW, 6,
                hurricane.getThrowUpTenths(), 0, BossHurricaneSettings.MAX_THROW, 6);

        if (place) {
            addButton(new GuiButtonNop(this, TUNING_BUTTON, guiLeft + LABEL_X, guiTop + y,
                    RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT, "cnpcgeckoaddon.boss.hurricane_tuning"));
        }
        y += ROW;

        y += HINT_GAP;
        if (place) {
            addWrappedHint(HINT_LABEL, HINT, guiTop + y);
            updateAimButtons();
        }
        y += wrappedHintHeight(HINT) + BUTTONS_GAP;
        if (place) {
            addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + LABEL_X, guiTop + y, 120, CONTROL_HEIGHT,
                    "cnpcgeckoaddon.boss.effects_settings"));
            addDoneButton(guiLeft + 182, guiTop + y, 60, CONTROL_HEIGHT);
        }
        return y + CONTROL_HEIGHT + BOTTOM_MARGIN;
    }

    private int toggle(boolean place, int id, String key, int y, boolean value) {
        if (place) {
            rowLabel(id, key, y, TOGGLE_X);
            addButton(new GuiButtonYesNo(this, id, guiLeft + TOGGLE_X, guiTop + y, TOGGLE_WIDTH, CONTROL_HEIGHT, value));
        }
        return y + ROW;
    }

    private int choice(boolean place, int id, String key, int y, String[] values, int selected) {
        if (place) {
            rowLabel(id, key, y, CHOICE_X);
            addButton(new GuiButtonNop(this, id, guiLeft + CHOICE_X, guiTop + y, CHOICE_WIDTH, CONTROL_HEIGHT,
                    values, selected));
        }
        return y + ROW;
    }

    /** An animation: typed in, or picked from the model's own list. */
    private int select(boolean place, int id, String key, int y, String value) {
        if (place) {
            rowLabel(id, key, y, SELECT_FIELD_X);
            addTextField(new GuiTextFieldNop(id, this, guiLeft + SELECT_FIELD_X, guiTop + y, SELECT_FIELD_WIDTH,
                    CONTROL_HEIGHT, value));
            addButton(new GuiButtonNop(this, id, guiLeft + SELECT_BUTTON_X, guiTop + y, SELECT_BUTTON_WIDTH,
                    CONTROL_HEIGHT, "mco.template.button.select"));
        }
        return y + ROW;
    }

    /** Two small numbers on one line: the pairs the labels name with a slash between them. */
    private int pair(boolean place, int leftId, int rightId, String key, int y,
                     int leftValue, int leftMin, int leftMax, int leftFallback,
                     int rightValue, int rightMin, int rightMax, int rightFallback) {
        if (place) {
            rowLabel(leftId, key, y, PAIR_X);
            number(leftId, guiLeft + PAIR_X, guiTop + y, PAIR_WIDTH, leftValue, leftMin, leftMax, leftFallback);
            number(rightId, guiLeft + PAIR_SECOND_X, guiTop + y, PAIR_WIDTH, rightValue, rightMin, rightMax,
                    rightFallback);
        }
        return y + ROW;
    }

    /** One number on a line, flush with the right edge where a pair's second one sits. */
    private int single(boolean place, int id, String key, int y, int value, int min, int max, int fallback) {
        if (place) {
            rowLabel(id, key, y, PAIR_SECOND_X);
            number(id, guiLeft + PAIR_SECOND_X, guiTop + y, PAIR_WIDTH, value, min, max, fallback);
        }
        return y + ROW;
    }

    private void number(int id, int x, int y, int width, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, width, CONTROL_HEIGHT, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    /**
     * A row's label, wrapped onto two lines rather than run under the control beside it: a
     * GuiLabel neither wraps nor clips, and the Russian names here are sentences.
     */
    private void rowLabel(int id, String key, int y, int controlX) {
        int width = controlX - LABEL_X - 4;
        // Not I18n.get: it runs the text through String.format; a translatable component
        // hands the text back as is.
        List<String> lines = wrapLines(Component.translatable(key).getString(), width);
        if (lines.size() == 1) {
            addLabel(new GuiLabel(id, key, guiLeft + LABEL_X, guiTop + y + LABEL_DROP));
            return;
        }
        int top = guiTop + y + (CONTROL_HEIGHT - lines.size() * LINE_HEIGHT) / 2;
        for (int i = 0; i < lines.size(); i++) {
            addLabel(new GuiLabel(i == 0 ? id : wrappedLabel++, Component.literal(lines.get(i)),
                    CustomNpcResourceListener.DefaultTextColor, guiLeft + LABEL_X,
                    top + i * LINE_HEIGHT, width, LINE_HEIGHT));
        }
    }

    /** Only the one straight storm is aimed; only one aimed at somebody picks who. */
    private void updateAimButtons() {
        BossHurricaneSettings hurricane = phase.hurricane();
        boolean straight = hurricane.getLaunchMode() == BossPhaseData.HURRICANE_MODE_STRAIGHT;
        GuiButtonNop aim = getButton(AIM_BUTTON);
        if (aim != null) {
            aim.setEnabled(straight);
        }
        GuiButtonNop targetMode = getButton(TARGET_MODE_BUTTON);
        if (targetMode != null) {
            targetMode.setEnabled(hurricane.isAimedAtTarget());
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        BossHurricaneSettings hurricane = phase.hurricane();
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(hurricane.getEffects(), "cnpcgeckoaddon.boss.effects_hurricane"));
        } else if (button.id == TUNING_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossHurricaneTuning(hurricane));
        } else if (button.id == ENABLED_BUTTON) {
            hurricane.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == MODE_BUTTON) {
            hurricane.setLaunchMode(button.getValue());
            updateAimButtons();
        } else if (button.id == AIM_BUTTON) {
            hurricane.setAim(button.getValue());
            updateAimButtons();
        } else if (button.id == TARGET_MODE_BUTTON) {
            hurricane.setTargetMode(button.getValue());
        } else if (button.id == SPIN_VIEW_BUTTON) {
            hurricane.setSpinView(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.hurricane_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                hurricane.setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        hurricane::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        BossHurricaneSettings hurricane = phase.hurricane();
        applyAnimation(hurricane);
        applyNumberField(ACTION_DELAY_FIELD, hurricane::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, hurricane::setCooldownTicks);
        applyNumberField(COUNT_FIELD, hurricane::setCount);
        applyNumberField(SPEED_FIELD, hurricane::setSpeedTenths);
        applyNumberField(RANGE_FIELD, hurricane::setRange);
        applyNumberField(LIFETIME_FIELD, hurricane::setLifetimeTicks);
        applyNumberField(RADIUS_FIELD, hurricane::setRadiusTenths);
        applyNumberField(BOUNCES_FIELD, hurricane::setBounces);
        applyNumberField(SPIRAL_DEGREES_FIELD, hurricane::setSpiralDegrees);
        applyNumberField(SPIRAL_GROWTH_FIELD, hurricane::setSpiralGrowthTenths);
        applyNumberField(LIFT_HEIGHT_FIELD, hurricane::setLiftHeight);
        applyNumberField(LIFT_TICKS_FIELD, hurricane::setLiftTicks);
        applyNumberField(HOLD_FIELD, hurricane::setHoldTicks);
        applyNumberField(SPIN_FIELD, hurricane::setSpinDegrees);
        applyNumberField(DAMAGE_FIELD, hurricane::setDamage);
        applyNumberField(INTERVAL_FIELD, hurricane::setDamageIntervalTicks);
        applyNumberField(THROW_FIELD, hurricane::setThrowTenths);
        applyNumberField(THROW_UP_FIELD, hurricane::setThrowUpTenths);
    }

    /** Keeps a typed animation only when the model has it; otherwise the field snaps back. */
    private void applyAnimation(BossHurricaneSettings hurricane) {
        GuiTextFieldNop field = getTextField(ANIMATION_FIELD);
        if (field == null) {
            return;
        }
        String value = field.getValue().trim();
        if (BossAnimationGuiUtil.isValid(npc, value)) {
            hurricane.setAnimation(value);
        } else {
            field.setValue(hurricane.getAnimation());
        }
    }
}
