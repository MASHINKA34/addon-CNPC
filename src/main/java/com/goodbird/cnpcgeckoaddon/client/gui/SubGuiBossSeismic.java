package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossSeismicSettings;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.List;

/** Seismic waves: rings of the floor round the boss that hit one after another, throw up and slam down. */
public final class SubGuiBossSeismic extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int ACTION_DELAY_FIELD = 3;
    private static final int COOLDOWN_FIELD = 4;
    private static final int MODE_BUTTON = 5;
    private static final int CORE_FIELD = 6;
    private static final int WIDTH_FIELD = 7;
    private static final int GAP_FIELD = 8;
    private static final int MAX_RADIUS_FIELD = 9;
    private static final int INTERVAL_FIELD = 10;
    private static final int WARN_FIELD = 11;
    private static final int RANDOM_MIN_FIELD = 12;
    private static final int RANDOM_MAX_FIELD = 13;
    private static final int REPEATS_FIELD = 14;
    private static final int REPEAT_DELAY_FIELD = 15;
    private static final int REPEAT_ANIMATION_BUTTON = 16;
    private static final int DAMAGE_FIELD = 17;
    private static final int KNOCKBACK_FIELD = 18;
    private static final int HIT_MODE_BUTTON = 19;
    private static final int LAUNCH_FIELD = 20;
    private static final int SLAM_DELAY_FIELD = 21;
    private static final int SLAM_STRENGTH_FIELD = 22;
    private static final int SLAM_DAMAGE_FIELD = 23;
    private static final int HEIGHT_FIELD = 24;
    private static final int VFX_BUTTON = 25;
    private static final int BLOCK_WAVE_BUTTON = 26;
    private static final int TUNING_BUTTON = 27;
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
    /** Three numbers to a row, the shadow copies' way: narrower, and the label wraps beside them. */
    private static final int TRIPLE_X = 118;
    private static final int TRIPLE_SECOND_X = 160;
    private static final int TRIPLE_THIRD_X = 202;
    private static final int TRIPLE_WIDTH = 40;
    private static final int HINT_GAP = 4;
    private static final int BUTTONS_GAP = 5;
    private static final int BOTTOM_MARGIN = 8;

    private static final String HINT = "cnpcgeckoaddon.boss.seismic_hint";
    private static final String[] VFX_STYLE_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey).toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private int wrappedLabel;

    public SubGuiBossSeismic(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // The panel is centred from imageHeight, so the height is settled before super.init()
        // reads it: the locale decides how many lines the hint takes. The waves have more
        // settings than a panel, so the screen scrolls.
        imageHeight = layout(false);
        super.init();
        layout(true);
    }

    /**
     * Puts every row down the panel, or with {@code place} false only measures how tall they
     * come to - the hurricane screen's way of keeping the panel and its contents from
     * drifting apart.
     *
     * @return the height the panel needs
     */
    private int layout(boolean place) {
        BossSeismicSettings seismic = phase.seismic();
        wrappedLabel = WRAPPED_LABEL;
        if (place) {
            addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.seismic_phase",
                    phaseIndex), guiLeft + LABEL_X, guiTop + 5, 0xFFFFFF));
        }
        int y = FIRST_ROW;
        y = toggle(place, ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", y, seismic.isEnabled());
        y = select(place, ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, seismic.getAnimation());
        y = pair(place, ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                seismic.getActionDelayTicks(), 0, 1200, 20, seismic.getCooldownTicks(), 1, 12000, 300);
        y = choice(place, MODE_BUTTON, "cnpcgeckoaddon.boss.seismic_mode", y,
                BossSeismicSettings.MODE_LABELS, seismic.getMode());
        y = single(place, CORE_FIELD, "cnpcgeckoaddon.boss.seismic_core", y,
                seismic.getCoreRadius(), BossSeismicSettings.MIN_CORE_RADIUS, BossSeismicSettings.MAX_CORE_RADIUS, 3);
        y = pair(place, WIDTH_FIELD, GAP_FIELD, "cnpcgeckoaddon.boss.seismic_ring", y,
                seismic.getRingWidth(), BossSeismicSettings.MIN_RING_WIDTH, BossSeismicSettings.MAX_RING_WIDTH, 2,
                seismic.getRingGap(), 0, BossSeismicSettings.MAX_RING_GAP, 0);
        y = single(place, MAX_RADIUS_FIELD, "cnpcgeckoaddon.boss.seismic_max_radius", y,
                seismic.getMaxRadius(), BossSeismicSettings.MIN_MAX_RADIUS, BossSeismicSettings.MAX_MAX_RADIUS, 16);
        y = pair(place, INTERVAL_FIELD, WARN_FIELD, "cnpcgeckoaddon.boss.seismic_interval", y,
                seismic.getIntervalTicks(), BossSeismicSettings.MIN_INTERVAL_TICKS,
                BossSeismicSettings.MAX_INTERVAL_TICKS, 10,
                seismic.getWarnTicks(), 0, BossSeismicSettings.MAX_WARN_TICKS, 10);
        y = pair(place, RANDOM_MIN_FIELD, RANDOM_MAX_FIELD, "cnpcgeckoaddon.boss.seismic_random", y,
                seismic.getRandomMin(), BossSeismicSettings.MIN_RANDOM_RINGS, BossSeismicSettings.MAX_RANDOM_RINGS, 1,
                seismic.getRandomMax(), BossSeismicSettings.MIN_RANDOM_RINGS, BossSeismicSettings.MAX_RANDOM_RINGS, 3);
        y = pair(place, REPEATS_FIELD, REPEAT_DELAY_FIELD, "cnpcgeckoaddon.boss.seismic_repeats", y,
                seismic.getRepeats(), BossSeismicSettings.MIN_REPEATS, BossSeismicSettings.MAX_REPEATS, 1,
                seismic.getRepeatDelayTicks(), 0, BossSeismicSettings.MAX_REPEAT_DELAY_TICKS, 20);
        y = toggle(place, REPEAT_ANIMATION_BUTTON, "cnpcgeckoaddon.boss.seismic_repeat_animation", y,
                seismic.isRepeatAnimation());
        y = pair(place, DAMAGE_FIELD, KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.seismic_hit", y,
                seismic.getDamage(), 0, BossSeismicSettings.MAX_DAMAGE, 8,
                seismic.getKnockback(), 0, BossSeismicSettings.MAX_KNOCKBACK, 0);
        y = choice(place, HIT_MODE_BUTTON, "cnpcgeckoaddon.boss.seismic_hit_mode", y,
                BossSeismicSettings.HIT_MODE_LABELS, seismic.getHitMode());
        y = single(place, LAUNCH_FIELD, "cnpcgeckoaddon.boss.seismic_launch", y,
                seismic.getLaunch(), BossSeismicSettings.MIN_LAUNCH, BossSeismicSettings.MAX_LAUNCH, 8);
        y = triple(place, SLAM_DELAY_FIELD, SLAM_STRENGTH_FIELD, SLAM_DAMAGE_FIELD, "cnpcgeckoaddon.boss.seismic_slam", y,
                seismic.getSlamDelayTicks(), BossSeismicSettings.MIN_SLAM_DELAY_TICKS,
                BossSeismicSettings.MAX_SLAM_DELAY_TICKS, 8,
                seismic.getSlamStrengthTenths(), BossSeismicSettings.MIN_SLAM_STRENGTH,
                BossSeismicSettings.MAX_SLAM_STRENGTH, 15,
                seismic.getSlamDamage(), 0, BossSeismicSettings.MAX_DAMAGE, 4);
        y = single(place, HEIGHT_FIELD, "cnpcgeckoaddon.boss.seismic_height", y,
                seismic.getHeightTenths(), 0, BossSeismicSettings.MAX_HEIGHT, 10);
        y = choice(place, VFX_BUTTON, "cnpcgeckoaddon.boss.seismic_vfx", y,
                VFX_STYLE_LABELS, vfxStyleIndex(seismic.getVfx()));
        y = toggle(place, BLOCK_WAVE_BUTTON, "cnpcgeckoaddon.boss.seismic_block_wave", y, seismic.isBlockWave());

        if (place) {
            addButton(new GuiButtonNop(this, TUNING_BUTTON, guiLeft + LABEL_X, guiTop + y,
                    RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT, "cnpcgeckoaddon.boss.seismic_tuning"));
        }
        y += ROW;

        y += HINT_GAP;
        if (place) {
            addWrappedHint(HINT_LABEL, HINT, guiTop + y);
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

    /** Three small numbers on one line, for the slam's three: when, how hard, and what the landing hits for. */
    private int triple(boolean place, int firstId, int secondId, int thirdId, String key, int y,
                       int firstValue, int firstMin, int firstMax, int firstFallback,
                       int secondValue, int secondMin, int secondMax, int secondFallback,
                       int thirdValue, int thirdMin, int thirdMax, int thirdFallback) {
        if (place) {
            rowLabel(firstId, key, y, TRIPLE_X);
            number(firstId, guiLeft + TRIPLE_X, guiTop + y, TRIPLE_WIDTH, firstValue, firstMin, firstMax, firstFallback);
            number(secondId, guiLeft + TRIPLE_SECOND_X, guiTop + y, TRIPLE_WIDTH, secondValue, secondMin, secondMax,
                    secondFallback);
            number(thirdId, guiLeft + TRIPLE_THIRD_X, guiTop + y, TRIPLE_WIDTH, thirdValue, thirdMin, thirdMax,
                    thirdFallback);
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

    private static int vfxStyleIndex(String id) {
        List<AreaVfxStyles.Style> styles = AreaVfxStyles.values();
        for (int i = 0; i < styles.size(); i++) {
            if (styles.get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        BossSeismicSettings seismic = phase.seismic();
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(seismic.getEffects(), "cnpcgeckoaddon.boss.effects_seismic"));
        } else if (button.id == TUNING_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossSeismicTuning(seismic));
        } else if (button.id == ENABLED_BUTTON) {
            seismic.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == MODE_BUTTON) {
            seismic.setMode(button.getValue());
        } else if (button.id == HIT_MODE_BUTTON) {
            seismic.setHitMode(button.getValue());
        } else if (button.id == VFX_BUTTON) {
            seismic.setVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == REPEAT_ANIMATION_BUTTON) {
            seismic.setRepeatAnimation(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == BLOCK_WAVE_BUTTON) {
            seismic.setBlockWave(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.seismic_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                seismic.setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        seismic::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        BossSeismicSettings seismic = phase.seismic();
        applyAnimation(seismic);
        applyNumberField(ACTION_DELAY_FIELD, seismic::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, seismic::setCooldownTicks);
        applyNumberField(CORE_FIELD, seismic::setCoreRadius);
        applyNumberField(WIDTH_FIELD, seismic::setRingWidth);
        applyNumberField(GAP_FIELD, seismic::setRingGap);
        applyNumberField(MAX_RADIUS_FIELD, seismic::setMaxRadius);
        applyNumberField(INTERVAL_FIELD, seismic::setIntervalTicks);
        applyNumberField(WARN_FIELD, seismic::setWarnTicks);
        applyNumberField(RANDOM_MIN_FIELD, seismic::setRandomMin);
        applyNumberField(RANDOM_MAX_FIELD, seismic::setRandomMax);
        applyNumberField(REPEATS_FIELD, seismic::setRepeats);
        applyNumberField(REPEAT_DELAY_FIELD, seismic::setRepeatDelayTicks);
        applyNumberField(DAMAGE_FIELD, seismic::setDamage);
        applyNumberField(KNOCKBACK_FIELD, seismic::setKnockback);
        applyNumberField(LAUNCH_FIELD, seismic::setLaunch);
        applyNumberField(SLAM_DELAY_FIELD, seismic::setSlamDelayTicks);
        applyNumberField(SLAM_STRENGTH_FIELD, seismic::setSlamStrengthTenths);
        applyNumberField(SLAM_DAMAGE_FIELD, seismic::setSlamDamage);
        applyNumberField(HEIGHT_FIELD, seismic::setHeightTenths);
    }

    /** Keeps a typed animation only when the model has it; otherwise the field snaps back. */
    private void applyAnimation(BossSeismicSettings seismic) {
        GuiTextFieldNop field = getTextField(ANIMATION_FIELD);
        if (field == null) {
            return;
        }
        String value = field.getValue().trim();
        if (BossAnimationGuiUtil.isValid(npc, value)) {
            seismic.setAnimation(value);
        } else {
            field.setValue(seismic.getAnimation());
        }
    }
}
