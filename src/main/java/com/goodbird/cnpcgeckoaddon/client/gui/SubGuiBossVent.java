package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossVentSettings;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.List;

/**
 * Vents: the timer their cast starts - its beat, its warning, how long each vent lasts and how
 * many cycles it runs - what the vents do by default, and the blast, the flame and the wall.
 */
public final class SubGuiBossVent extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int ACTION_DELAY_FIELD = 3;
    private static final int COOLDOWN_FIELD = 4;
    private static final int MODE_BUTTON = 5;
    private static final int PATTERN_BUTTON = 6;
    private static final int RANDOM_COUNT_FIELD = 7;
    private static final int CYCLE_FIELD = 8;
    private static final int WARN_FIELD = 9;
    private static final int ACTIVE_FIELD = 10;
    private static final int REPEATS_FIELD = 11;
    private static final int RECAST_BUTTON = 12;
    private static final int DAMAGE_FIELD = 13;
    private static final int HIT_INTERVAL_FIELD = 14;
    private static final int KNOCKBACK_FIELD = 15;
    private static final int WALL_PUSH_FIELD = 16;
    private static final int WALL_DAMAGE_FIELD = 17;
    private static final int WALL_MODE_BUTTON = 18;
    private static final int WALL_LIFT_FIELD = 19;
    private static final int BURST_VFX_BUTTON = 20;
    private static final int BURST_VFX_TICKS_FIELD = 21;
    private static final int ZONES_BUTTON = 22;
    private static final int TUNING_BUTTON = 23;
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
    /** Two numbers to a row, the second flush with the right edge; a lone number sits where the second would. */
    private static final int PAIR_X = 140;
    private static final int PAIR_SECOND_X = 194;
    private static final int PAIR_WIDTH = 48;
    /** A choice with a number beside it: the choice narrowed to leave the number its place. */
    private static final int CHOICE_BESIDE_WIDTH = PAIR_SECOND_X - 4 - CHOICE_X;
    private static final int HINT_GAP = 4;
    private static final int BUTTONS_GAP = 5;
    private static final int BOTTOM_MARGIN = 8;

    private static final String HINT = "cnpcgeckoaddon.boss.vent_hint";
    private static final String[] VFX_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey)
            .toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private int wrappedLabel;

    public SubGuiBossVent(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // The panel is centred from imageHeight, so the height is settled before super.init() reads
        // it: the locale decides how many lines the wrapped labels and the hint take. The vents
        // have more settings than a panel holds, so the screen scrolls.
        imageHeight = layout(false);
        super.init();
        layout(true);
    }

    /**
     * Puts every row down the panel, or with {@code place} false only measures how tall they
     * come to - the platform screen's way of keeping the panel and its contents from drifting apart.
     *
     * @return the height the panel needs
     */
    private int layout(boolean place) {
        BossVentSettings vent = phase.vent();
        wrappedLabel = WRAPPED_LABEL;
        if (place) {
            addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.vent_phase",
                    phaseIndex), guiLeft + LABEL_X, guiTop + 5, 0xFFFFFF));
        }
        int y = FIRST_ROW;
        y = toggle(place, ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", y, vent.isEnabled());
        y = select(place, ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, vent.getAnimation());
        y = pair(place, ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                vent.getActionDelayTicks(), 0, BossVentSettings.MAX_ACTION_DELAY_TICKS, 20,
                vent.getCooldownTicks(), BossVentSettings.MIN_COOLDOWN_TICKS, BossVentSettings.MAX_COOLDOWN_TICKS, 600);
        y = choice(place, MODE_BUTTON, "cnpcgeckoaddon.boss.vent_mode", y, BossVentSettings.MODE_LABELS,
                vent.getMode());
        y = choice(place, PATTERN_BUTTON, "cnpcgeckoaddon.boss.vent_pattern", y, BossVentSettings.PATTERN_LABELS,
                vent.getPattern());
        y = single(place, RANDOM_COUNT_FIELD, "cnpcgeckoaddon.boss.vent_random_count", y, vent.getRandomCount(),
                BossVentSettings.MIN_RANDOM_COUNT, BossVentSettings.MAX_RANDOM_COUNT, 1);
        y = single(place, CYCLE_FIELD, "cnpcgeckoaddon.boss.vent_cycle", y, vent.getCycleTicks(),
                BossVentSettings.MIN_CYCLE_TICKS, BossVentSettings.MAX_CYCLE_TICKS, 100);
        y = single(place, WARN_FIELD, "cnpcgeckoaddon.boss.vent_warn", y, vent.getWarnTicks(),
                0, BossVentSettings.MAX_WARN_TICKS, 20);
        y = single(place, ACTIVE_FIELD, "cnpcgeckoaddon.boss.vent_active", y, vent.getActiveTicks(),
                BossVentSettings.MIN_ACTIVE_TICKS, BossVentSettings.MAX_ACTIVE_TICKS, 40);
        y = single(place, REPEATS_FIELD, "cnpcgeckoaddon.boss.vent_repeats", y, vent.getRepeats(),
                0, BossVentSettings.MAX_REPEATS, 0);
        y = choice(place, RECAST_BUTTON, "cnpcgeckoaddon.boss.vent_recast", y, BossVentSettings.RECAST_LABELS,
                vent.getRecast());
        y = pair(place, DAMAGE_FIELD, HIT_INTERVAL_FIELD, "cnpcgeckoaddon.boss.vent_hit", y,
                vent.getDamage(), 0, BossVentSettings.MAX_DAMAGE, 6,
                vent.getHitIntervalTicks(), BossVentSettings.MIN_HIT_INTERVAL_TICKS,
                BossVentSettings.MAX_HIT_INTERVAL_TICKS, 10);
        y = single(place, KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.vent_knockback", y, vent.getKnockback(),
                0, BossVentSettings.MAX_KNOCKBACK, 1);
        y = pair(place, WALL_PUSH_FIELD, WALL_DAMAGE_FIELD, "cnpcgeckoaddon.boss.vent_wall", y,
                vent.getWallPushTenths(), BossVentSettings.MIN_WALL_PUSH, BossVentSettings.MAX_WALL_PUSH, 8,
                vent.getWallDamage(), 0, BossVentSettings.MAX_DAMAGE, 0);
        y = choice(place, WALL_MODE_BUTTON, "cnpcgeckoaddon.boss.vent_wall_mode", y,
                BossVentSettings.WALL_MODE_LABELS, vent.getWallMode());
        y = single(place, WALL_LIFT_FIELD, "cnpcgeckoaddon.boss.vent_wall_lift", y, vent.getWallLift(),
                0, BossVentSettings.MAX_WALL_LIFT, 2);
        y = choiceAndNumber(place, BURST_VFX_BUTTON, BURST_VFX_TICKS_FIELD, "cnpcgeckoaddon.boss.vent_burst_vfx", y,
                VFX_LABELS, vfxIndex(vent.getBurstVfx()), vent.getBurstVfxTicks(),
                BossVentSettings.MIN_BURST_VFX_TICKS, BossVentSettings.MAX_BURST_VFX_TICKS, 10);
        if (place) {
            addButton(new GuiButtonNop(this, ZONES_BUTTON, guiLeft + LABEL_X, guiTop + y,
                    RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT, "cnpcgeckoaddon.boss.vent_zones"));
        }
        y += ROW;

        y += HINT_GAP;
        if (place) {
            addWrappedHint(HINT_LABEL, HINT, guiTop + y);
        }
        y += wrappedHintHeight(HINT) + BUTTONS_GAP;
        if (place) {
            addButton(new GuiButtonNop(this, TUNING_BUTTON, guiLeft + LABEL_X, guiTop + y,
                    RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT, "cnpcgeckoaddon.boss.vent_tuning"));
        }
        y += ROW;
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

    /** A choice and a number on one line: the wave's style and how long it runs. */
    private int choiceAndNumber(boolean place, int buttonId, int fieldId, String key, int y, String[] values,
                                int selected, int value, int min, int max, int fallback) {
        if (place) {
            rowLabel(buttonId, key, y, CHOICE_X);
            addButton(new GuiButtonNop(this, buttonId, guiLeft + CHOICE_X, guiTop + y, CHOICE_BESIDE_WIDTH,
                    CONTROL_HEIGHT, values, selected));
            number(fieldId, guiLeft + PAIR_SECOND_X, guiTop + y, value, min, max, fallback);
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
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, PAIR_WIDTH, CONTROL_HEIGHT, Integer.toString(value));
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

    /** Where this wave sits on the cycle button; an id the list no longer has reads as its first style. */
    private static int vfxIndex(String id) {
        for (int i = 0; i < AreaVfxStyles.values().size(); i++) {
            if (AreaVfxStyles.values().get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        BossVentSettings vent = phase.vent();
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(vent.getEffects(), "cnpcgeckoaddon.boss.effects_vent"));
        } else if (button.id == ZONES_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossVentZoneList(npc, phase, phaseIndex));
        } else if (button.id == TUNING_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossVentTuning(vent));
        } else if (button.id == ENABLED_BUTTON) {
            vent.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == MODE_BUTTON) {
            vent.setMode(button.getValue());
        } else if (button.id == PATTERN_BUTTON) {
            vent.setPattern(button.getValue());
        } else if (button.id == RECAST_BUTTON) {
            vent.setRecast(button.getValue());
        } else if (button.id == WALL_MODE_BUTTON) {
            vent.setWallMode(button.getValue());
        } else if (button.id == BURST_VFX_BUTTON) {
            vent.setBurstVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.vent_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                vent.setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        vent::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        BossVentSettings vent = phase.vent();
        GuiTextFieldNop animation = getTextField(ANIMATION_FIELD);
        if (animation != null) {
            // Kept only when the model has it; otherwise the field snaps back.
            String value = animation.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) {
                vent.setAnimation(value);
            } else {
                animation.setValue(vent.getAnimation());
            }
        }
        applyNumberField(ACTION_DELAY_FIELD, vent::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, vent::setCooldownTicks);
        applyNumberField(RANDOM_COUNT_FIELD, vent::setRandomCount);
        applyNumberField(CYCLE_FIELD, vent::setCycleTicks);
        applyNumberField(WARN_FIELD, vent::setWarnTicks);
        applyNumberField(ACTIVE_FIELD, vent::setActiveTicks);
        applyNumberField(REPEATS_FIELD, vent::setRepeats);
        applyNumberField(DAMAGE_FIELD, vent::setDamage);
        applyNumberField(HIT_INTERVAL_FIELD, vent::setHitIntervalTicks);
        applyNumberField(KNOCKBACK_FIELD, vent::setKnockback);
        applyNumberField(WALL_PUSH_FIELD, vent::setWallPushTenths);
        applyNumberField(WALL_DAMAGE_FIELD, vent::setWallDamage);
        applyNumberField(WALL_LIFT_FIELD, vent::setWallLift);
        applyNumberField(BURST_VFX_TICKS_FIELD, vent::setBurstVfxTicks);
    }
}
