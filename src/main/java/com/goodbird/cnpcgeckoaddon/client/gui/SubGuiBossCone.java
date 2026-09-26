package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeYesNo;
import com.goodbird.cnpcgeckoaddon.data.BossConeAimList;
import com.goodbird.cnpcgeckoaddon.data.BossConeSettings;
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

/** Cone strike: a fan of a hit toward a target, along the gaze, or over the builder's points. */
public final class SubGuiBossCone extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int ACTION_DELAY_FIELD = 3;
    private static final int COOLDOWN_FIELD = 4;
    private static final int AIM_BUTTON = 5;
    private static final int TARGET_MODE_BUTTON = 6;
    private static final int ANGLE_FIELD = 7;
    private static final int LENGTH_FIELD = 8;
    private static final int HEIGHT_FIELD = 9;
    private static final int DAMAGE_FIELD = 10;
    private static final int IMPULSE_BUTTON = 11;
    private static final int IMPULSE_STRENGTH_FIELD = 12;
    private static final int FACE_AXIS_BUTTON = 13;
    private static final int ORDER_BUTTON = 14;
    private static final int COUNT_FIELD = 15;
    private static final int INTERVAL_FIELD = 16;
    private static final int POINTS_BUTTON = 17;
    private static final int FLASH_ARCS_FIELD = 18;
    private static final int SNAP_FIELD = 19;
    private static final int SWING_CUE_BUTTON = 20;
    private static final int TUNING_BUTTON = 21;
    private static final int EFFECTS_BUTTON = 67;

    private static final int TITLE_LABEL = 30;
    private static final int HINT_LABEL = 40;
    private static final int SERIES_HINT_LABEL = 50;
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
    /** Two numbers to a row, the second flush with the right edge; a lone number sits where the second would. */
    private static final int PAIR_X = 140;
    private static final int PAIR_SECOND_X = 194;
    private static final int PAIR_WIDTH = 48;
    private static final int HINT_GAP = 4;
    private static final int BUTTONS_GAP = 5;
    private static final int BOTTOM_MARGIN = 8;

    private static final String HINT = "cnpcgeckoaddon.boss.cone_hint";
    private static final String SERIES_HINT = "cnpcgeckoaddon.boss.cone_series_hint";

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private int wrappedLabel;

    public SubGuiBossCone(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // The panel is centred from imageHeight, so the height is settled before super.init()
        // reads it: the aim decides whether the series rows are on the screen, and the locale how
        // many lines the hint takes. With them the cone has more settings than a panel, so it scrolls.
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
        BossConeSettings cone = phase.cone();
        wrappedLabel = WRAPPED_LABEL;
        if (place) {
            addLabel(new ThemeLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.cone_phase",
                    phaseIndex), guiLeft + LABEL_X, guiTop + 5, 0xFFFFFF));
        }
        int y = FIRST_ROW;
        y = toggle(place, ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", y, cone.isEnabled());
        y = select(place, ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, cone.getAnimation());
        y = pair(place, ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                cone.getActionDelayTicks(), 0, 1200, 12, cone.getCooldownTicks(), 1, 12000, 160);
        y = choice(place, AIM_BUTTON, "cnpcgeckoaddon.boss.cone_aim", y,
                BossPhaseData.CONE_AIM_LABELS, cone.getAimMode());
        y = choice(place, TARGET_MODE_BUTTON, "cnpcgeckoaddon.boss.target_mode", y,
                BossTargetMode.LABELS, cone.getTargetMode());
        y = pair(place, ANGLE_FIELD, LENGTH_FIELD, "cnpcgeckoaddon.boss.cone_shape", y,
                cone.getAngle(), BossConeSettings.MIN_ANGLE, BossConeSettings.MAX_ANGLE, 60,
                cone.getLength(), BossConeSettings.MIN_LENGTH, BossConeSettings.MAX_LENGTH, 10);
        y = single(place, HEIGHT_FIELD, "cnpcgeckoaddon.boss.cone_height", y,
                cone.getHeight(), 1, BossConeSettings.MAX_HEIGHT, 3);
        y = single(place, DAMAGE_FIELD, "cnpcgeckoaddon.boss.cone_damage", y,
                cone.getDamage(), 0, BossConeSettings.MAX_DAMAGE, 10);
        y = choice(place, IMPULSE_BUTTON, "cnpcgeckoaddon.boss.cone_impulse", y,
                BossPhaseData.CONE_IMPULSE_LABELS, cone.getImpulseMode());
        y = single(place, IMPULSE_STRENGTH_FIELD, "cnpcgeckoaddon.boss.cone_impulse_strength", y,
                cone.getImpulseStrength(), 0, BossConeSettings.MAX_IMPULSE_STRENGTH, 2);
        // The potions right under the damage and the push, with the rest of what the hit does:
        // at the foot of the tallest screen in the addon the button sat below the window at the
        // larger GUI scales, and the only sign of it was a scrollbar nobody found.
        if (place) {
            addButton(new ThemeButton(this, EFFECTS_BUTTON, guiLeft + LABEL_X, guiTop + y,
                    RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT, "cnpcgeckoaddon.boss.effects_settings"));
        }
        y += ROW;
        y = toggle(place, FACE_AXIS_BUTTON, "cnpcgeckoaddon.boss.line_face_axis", y, cone.isFaceAxis());

        // Only a cone aimed at points has a series to order, count and pace, and points to edit.
        if (cone.getAimMode() == BossPhaseData.CONE_AIM_POINTS) {
            y = choice(place, ORDER_BUTTON, "cnpcgeckoaddon.boss.cone_order", y,
                    BossPhaseData.CONE_ORDER_LABELS, cone.getPointOrder());
            y = pair(place, COUNT_FIELD, INTERVAL_FIELD, "cnpcgeckoaddon.boss.cone_series", y,
                    cone.getPointCount(), 0, BossConeAimList.MAX_ENTRIES, 0,
                    cone.getPointIntervalTicks(), 0, BossConeSettings.MAX_POINT_INTERVAL_TICKS, 10);
            if (place) {
                addButton(new ThemeButton(this, POINTS_BUTTON, guiLeft + LABEL_X, guiTop + y,
                        RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT, "cnpcgeckoaddon.boss.cone_points"));
            }
            y += ROW;
        }

        y = single(place, FLASH_ARCS_FIELD, "cnpcgeckoaddon.boss.cone_flash_arcs", y,
                cone.getFlashArcs(), 0, BossConeSettings.MAX_FLASH_ARCS, 3);
        y = single(place, SNAP_FIELD, "cnpcgeckoaddon.boss.cone_snap", y,
                cone.getSnapDegrees(), BossConeSettings.MIN_SNAP_DEGREES,
                BossConeSettings.MAX_SNAP_DEGREES, 360);
        if (place) {
            addCueButton(SWING_CUE_BUTTON, "cnpcgeckoaddon.boss.cone_cue_swing", guiTop + y,
                    cone.getSwingSound());
        }
        y += ROW;
        if (place) {
            addButton(new ThemeButton(this, TUNING_BUTTON, guiLeft + LABEL_X, guiTop + y,
                    RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT, "cnpcgeckoaddon.boss.cone_tuning"));
        }
        y += ROW;

        y += HINT_GAP;
        if (place) {
            addWrappedHint(HINT_LABEL, HINT, guiTop + y);
            updateTargetMode();
        }
        y += wrappedHintHeight(HINT);
        // What a series costs the boss, under the hint, only while there is a series to cost it.
        if (cone.getAimMode() == BossPhaseData.CONE_AIM_POINTS) {
            if (place) {
                addWrappedHint(SERIES_HINT_LABEL, SERIES_HINT, guiTop + y);
            }
            y += wrappedHintHeight(SERIES_HINT);
        }
        y += BUTTONS_GAP;
        if (place) {
            addDoneButton(guiLeft + 182, guiTop + y, 60, CONTROL_HEIGHT);
        }
        return y + CONTROL_HEIGHT + BOTTOM_MARGIN;
    }

    private int toggle(boolean place, int id, String key, int y, boolean value) {
        if (place) {
            rowLabel(id, key, y, TOGGLE_X);
            addButton(new ThemeYesNo(this, id, guiLeft + TOGGLE_X, guiTop + y, TOGGLE_WIDTH, CONTROL_HEIGHT, value));
        }
        return y + ROW;
    }

    private int choice(boolean place, int id, String key, int y, String[] values, int selected) {
        if (place) {
            rowLabel(id, key, y, CHOICE_X);
            addButton(new ThemeButton(this, id, guiLeft + CHOICE_X, guiTop + y, CHOICE_WIDTH, CONTROL_HEIGHT,
                    values, selected));
        }
        return y + ROW;
    }

    /** An animation: typed in, or picked from the model's own list. */
    private int select(boolean place, int id, String key, int y, String value) {
        if (place) {
            rowLabel(id, key, y, SELECT_FIELD_X);
            addTextField(new ThemeTextField(id, this, guiLeft + SELECT_FIELD_X, guiTop + y, SELECT_FIELD_WIDTH,
                    CONTROL_HEIGHT, value));
            addButton(new ThemeButton(this, id, guiLeft + SELECT_BUTTON_X, guiTop + y, SELECT_BUTTON_WIDTH,
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
            number(leftId, guiLeft + PAIR_X, guiTop + y, leftValue, leftMin, leftMax, leftFallback);
            number(rightId, guiLeft + PAIR_SECOND_X, guiTop + y, rightValue, rightMin, rightMax, rightFallback);
        }
        return y + ROW;
    }

    /** One small number, flush with the right edge where a pair's second number would be. */
    private int single(boolean place, int id, String key, int y, int value, int min, int max, int fallback) {
        if (place) {
            rowLabel(id, key, y, PAIR_SECOND_X);
            number(id, guiLeft + PAIR_SECOND_X, guiTop + y, value, min, max, fallback);
        }
        return y + ROW;
    }

    private void number(int id, int x, int y, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new ThemeTextField(id, this, x, y, PAIR_WIDTH, CONTROL_HEIGHT, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    /**
     * A row's label, wrapped onto two lines rather than run under the control beside it: a
     * GuiLabel neither wraps nor clips, and the Russian names are wider than the English ones.
     */
    private void rowLabel(int id, String key, int y, int controlX) {
        int width = controlX - LABEL_X - 4;
        // Not I18n.get: it runs the text through String.format, which would turn a bare % in a
        // label into "Format error: ..."; a translatable component hands it back as is.
        List<String> lines = wrapLines(Component.translatable(key).getString(), width);
        if (lines.size() == 1) {
            addLabel(new ThemeLabel(id, key, guiLeft + LABEL_X, guiTop + y + LABEL_DROP));
            return;
        }
        int top = guiTop + y + (CONTROL_HEIGHT - lines.size() * LINE_HEIGHT) / 2;
        for (int i = 0; i < lines.size(); i++) {
            addLabel(new ThemeLabel(i == 0 ? id : wrappedLabel++, Component.literal(lines.get(i)),
                    CustomNpcResourceListener.DefaultTextColor, guiLeft + LABEL_X,
                    top + i * LINE_HEIGHT, width, LINE_HEIGHT));
        }
    }

    /** Who the cone is aimed at only matters while it is aimed at a target. */
    private void updateTargetMode() {
        GuiButtonNop targetMode = getButton(TARGET_MODE_BUTTON);
        if (targetMode != null) {
            targetMode.setEnabled(phase.cone().getAimMode() == BossPhaseData.CONE_AIM_TARGET);
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        BossConeSettings cone = phase.cone();
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(cone.getEffects(), "cnpcgeckoaddon.boss.effects_cone"));
        } else if (button.id == TUNING_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossConeTuning(cone));
        } else if (button.id == POINTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossConeAimList(npc, phase, phaseIndex));
        } else if (button.id == ENABLED_BUTTON) {
            cone.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == AIM_BUTTON) {
            // Read before the rows change, so a number typed under the old aim is not lost.
            applyFields();
            cone.setAimMode(button.getValue());
            requestLayout();
        } else if (button.id == TARGET_MODE_BUTTON) {
            cone.setTargetMode(button.getValue());
        } else if (button.id == IMPULSE_BUTTON) {
            cone.setImpulseMode(button.getValue());
        } else if (button.id == FACE_AXIS_BUTTON) {
            cone.setFaceAxis(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ORDER_BUTTON) {
            cone.setPointOrder(button.getValue());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.cone_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                cone.setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        cone::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        BossConeSettings cone = phase.cone();
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            // Kept only when the model has it; otherwise the field snaps back.
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) {
                cone.setAnimation(value);
            } else {
                animation.setValue(cone.getAnimation());
            }
        }
        applyNumberField(ACTION_DELAY_FIELD, cone::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, cone::setCooldownTicks);
        applyNumberField(ANGLE_FIELD, cone::setAngle);
        applyNumberField(LENGTH_FIELD, cone::setLength);
        applyNumberField(HEIGHT_FIELD, cone::setHeight);
        applyNumberField(DAMAGE_FIELD, cone::setDamage);
        applyNumberField(IMPULSE_STRENGTH_FIELD, cone::setImpulseStrength);
        applyNumberField(COUNT_FIELD, cone::setPointCount);
        applyNumberField(FLASH_ARCS_FIELD, cone::setFlashArcs);
        applyNumberField(SNAP_FIELD, cone::setSnapDegrees);
        applyNumberField(INTERVAL_FIELD, cone::setPointIntervalTicks);
    }
}
