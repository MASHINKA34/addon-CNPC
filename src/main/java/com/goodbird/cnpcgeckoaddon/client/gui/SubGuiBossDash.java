package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.AreaVfxStyles;
import com.goodbird.cnpcgeckoaddon.data.BossDashSettings;
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
import java.util.function.Consumer;

/** Dash: a run down a marked lane - the first one in it takes the hit, a wall costs the boss. */
public final class SubGuiBossDash extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int ACTION_DELAY_FIELD = 3;
    private static final int COOLDOWN_FIELD = 4;
    private static final int DIRECTION_BUTTON = 5;
    private static final int TARGET_MODE_BUTTON = 6;
    private static final int LENGTH_FIELD = 7;
    private static final int SPEED_FIELD = 8;
    private static final int WIDTH_FIELD = 9;
    private static final int HEIGHT_FIELD = 10;
    private static final int DAMAGE_FIELD = 11;
    private static final int KNOCKBACK_FIELD = 12;
    private static final int STOP_ON_HIT_BUTTON = 13;
    private static final int WALL_MODE_BUTTON = 14;
    private static final int STUN_TICKS_FIELD = 15;
    private static final int STUN_PERCENT_FIELD = 16;
    private static final int STUN_ANIMATION_FIELD = 17;
    private static final int SLAM_RADIUS_FIELD = 18;
    private static final int SLAM_DAMAGE_FIELD = 19;
    private static final int SLAM_KNOCKBACK_FIELD = 20;
    private static final int SLAM_VFX_BUTTON = 21;
    private static final int CHAIN_BUTTON = 22;
    private static final int CHAIN_TICKS_FIELD = 23;
    private static final int CHAIN_PERCENT_FIELD = 24;
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
    /** Two numbers to a row, the second flush with the right edge. */
    private static final int PAIR_X = 140;
    private static final int PAIR_SECOND_X = 194;
    private static final int PAIR_WIDTH = 48;
    /** Three numbers to a row, for the slam, which reads as one thing. */
    private static final int TRIPLE_X = 136;
    private static final int TRIPLE_STEP = 37;
    private static final int TRIPLE_WIDTH = 32;
    private static final int HINT_GAP = 4;
    private static final int BUTTONS_GAP = 5;
    private static final int BOTTOM_MARGIN = 8;

    private static final String[] VFX_STYLE_LABELS = AreaVfxStyles.values().stream()
            .map(AreaVfxStyles.Style::translationKey)
            .toArray(String[]::new);
    private static final String HINT = "cnpcgeckoaddon.boss.dash_hint";

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private int wrappedLabel;

    public SubGuiBossDash(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // The panel is centred from imageHeight, so the height is settled before super.init()
        // reads it: the wall's rule decides which rows are on the screen, and the locale how
        // many lines the hint takes. A dash has more settings than a panel, so it scrolls.
        imageHeight = layout(false);
        super.init();
        layout(true);
    }

    /**
     * Puts every row down the panel, or with {@code place} false only measures how tall they
     * come to - the totem screen's way of keeping the panel and its contents from drifting apart.
     *
     * @return the height the panel needs
     */
    private int layout(boolean place) {
        BossDashSettings dash = phase.dash();
        wrappedLabel = WRAPPED_LABEL;
        if (place) {
            addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.dash_phase",
                    phaseIndex), guiLeft + LABEL_X, guiTop + 5, 0xFFFFFF));
        }
        int y = FIRST_ROW;
        y = toggle(place, ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", y, dash.isEnabled());
        y = select(place, ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, dash.getAnimation());
        y = pair(place, ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                dash.getActionDelayTicks(), 0, 1200, 12, dash.getCooldownTicks(), 1, 12000, 240);
        y = choice(place, DIRECTION_BUTTON, "cnpcgeckoaddon.boss.dash_direction", y,
                BossPhaseData.DASH_DIRECTION_LABELS, dash.getDirection());
        y = choice(place, TARGET_MODE_BUTTON, "cnpcgeckoaddon.boss.target_mode", y,
                BossTargetMode.LABELS, dash.getTargetMode());
        y = pair(place, LENGTH_FIELD, SPEED_FIELD, "cnpcgeckoaddon.boss.dash_run", y,
                dash.getLength(), BossDashSettings.MIN_LENGTH, BossDashSettings.MAX_LENGTH, 12,
                dash.getSpeed(), BossDashSettings.MIN_SPEED, BossDashSettings.MAX_SPEED, 8);
        y = pair(place, WIDTH_FIELD, HEIGHT_FIELD, "cnpcgeckoaddon.boss.dash_lane", y,
                dash.getWidth(), 1, BossDashSettings.MAX_WIDTH, 2,
                dash.getHeight(), 1, BossDashSettings.MAX_HEIGHT, 3);
        y = pair(place, DAMAGE_FIELD, KNOCKBACK_FIELD, "cnpcgeckoaddon.boss.dash_hit", y,
                dash.getDamage(), 0, BossDashSettings.MAX_DAMAGE, 12,
                dash.getKnockback(), 0, BossDashSettings.MAX_KNOCKBACK, 3);
        y = toggle(place, STOP_ON_HIT_BUTTON, "cnpcgeckoaddon.boss.dash_stop_on_hit", y, dash.isStopOnHit());

        // Only the wall rule in force shows its numbers: the enrage rule falls back on the stun
        // when there is no enrage left to set off, so it shows the stun's.
        y = choice(place, WALL_MODE_BUTTON, "cnpcgeckoaddon.boss.dash_wall", y,
                BossPhaseData.DASH_WALL_LABELS, dash.getWallMode());
        boolean slam = dash.getWallMode() == BossPhaseData.DASH_WALL_SLAM;
        if (slam) {
            y = slamNumbers(place, y);
            y = choice(place, SLAM_VFX_BUTTON, "cnpcgeckoaddon.boss.area_vfx", y,
                    VFX_STYLE_LABELS, vfxStyleIndex(dash.getSlamVfx()));
        } else {
            y = pair(place, STUN_TICKS_FIELD, STUN_PERCENT_FIELD, "cnpcgeckoaddon.boss.dash_stun", y,
                    dash.getStunTicks(), 0, BossDashSettings.MAX_STUN_TICKS, 60,
                    dash.getStunDamagePercent(), BossDashSettings.MIN_TAKEN_PERCENT,
                    BossDashSettings.MAX_TAKEN_PERCENT, 200);
            y = select(place, STUN_ANIMATION_FIELD, "cnpcgeckoaddon.boss.dash_stun_anim", y,
                    dash.getStunAnimation());
        }

        y = toggle(place, CHAIN_BUTTON, "cnpcgeckoaddon.boss.dash_chain", y, dash.isChainStun());
        y = pair(place, CHAIN_TICKS_FIELD, CHAIN_PERCENT_FIELD, "cnpcgeckoaddon.boss.dash_chain_stun", y,
                dash.getChainStunTicks(), 0, BossDashSettings.MAX_STUN_TICKS, 80,
                dash.getChainDamagePercent(), BossDashSettings.MIN_TAKEN_PERCENT,
                BossDashSettings.MAX_TAKEN_PERCENT, 200);
        if (slam) {
            // The chain's stun plays the same animation the wall's does, so it stays reachable
            // under the slam rule, down here with the only stun that rule leaves.
            y = select(place, STUN_ANIMATION_FIELD, "cnpcgeckoaddon.boss.dash_stun_anim", y,
                    dash.getStunAnimation());
        }

        if (place) {
            addButton(new GuiButtonNop(this, TUNING_BUTTON, guiLeft + LABEL_X, guiTop + y,
                    RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT, "cnpcgeckoaddon.boss.dash_tuning"));
        }
        y += ROW;

        y += HINT_GAP;
        if (place) {
            addWrappedHint(HINT_LABEL, HINT, guiTop + y);
            updateTargetMode();
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

    /** The slam's radius, damage and knockback, which the label names together. */
    private int slamNumbers(boolean place, int y) {
        if (place) {
            BossDashSettings dash = phase.dash();
            rowLabel(SLAM_RADIUS_FIELD, "cnpcgeckoaddon.boss.dash_slam", y, TRIPLE_X);
            number(SLAM_RADIUS_FIELD, guiLeft + TRIPLE_X, guiTop + y, TRIPLE_WIDTH,
                    dash.getSlamRadius(), 1, BossDashSettings.MAX_SLAM_RADIUS, 4);
            number(SLAM_DAMAGE_FIELD, guiLeft + TRIPLE_X + TRIPLE_STEP, guiTop + y, TRIPLE_WIDTH,
                    dash.getSlamDamage(), 0, BossDashSettings.MAX_DAMAGE, 10);
            number(SLAM_KNOCKBACK_FIELD, guiLeft + TRIPLE_X + TRIPLE_STEP * 2, guiTop + y, TRIPLE_WIDTH,
                    dash.getSlamKnockback(), 0, BossDashSettings.MAX_KNOCKBACK, 2);
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
        // Not I18n.get: it runs the text through String.format, which turns the bare % of the
        // stun labels into "Format error: ..."; a translatable component hands it back as is.
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

    /** Who the run is aimed at only matters while it runs at a target. */
    private void updateTargetMode() {
        GuiButtonNop targetMode = getButton(TARGET_MODE_BUTTON);
        if (targetMode != null) {
            targetMode.setEnabled(phase.dash().getDirection() == BossPhaseData.DASH_DIRECTION_TARGET);
        }
    }

    private static int vfxStyleIndex(String id) {
        for (int i = 0; i < AreaVfxStyles.values().size(); i++) {
            if (AreaVfxStyles.values().get(i).id().equals(id)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        BossDashSettings dash = phase.dash();
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(dash.getEffects(), "cnpcgeckoaddon.boss.effects_dash"));
        } else if (button.id == TUNING_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossDashTuning(dash));
        } else if (button.id == ENABLED_BUTTON) {
            dash.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == DIRECTION_BUTTON) {
            dash.setDirection(button.getValue());
            updateTargetMode();
        } else if (button.id == TARGET_MODE_BUTTON) {
            dash.setTargetMode(button.getValue());
        } else if (button.id == STOP_ON_HIT_BUTTON) {
            dash.setStopOnHit(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == WALL_MODE_BUTTON) {
            // Read before the rows change, so a number typed under the old rule is not lost.
            applyFields();
            dash.setWallMode(button.getValue());
            requestLayout();
        } else if (button.id == SLAM_VFX_BUTTON) {
            dash.setSlamVfx(AreaVfxStyles.values().get(button.getValue()).id());
        } else if (button.id == CHAIN_BUTTON) {
            dash.setChainStun(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.dash_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                dash.setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        dash::setActionDelayTicks);
            }));
        } else if (button.id == STUN_ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.dash_stun_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                dash.setStunAnimation(name);
                getTextField(STUN_ANIMATION_FIELD).setValue(name);
            }));
        }
    }

    @Override
    protected void applyFields() {
        BossDashSettings dash = phase.dash();
        applyAnimation(ANIMATION_FIELD, dash.getAnimation(), dash::setAnimation);
        applyAnimation(STUN_ANIMATION_FIELD, dash.getStunAnimation(), dash::setStunAnimation);
        applyNumberField(ACTION_DELAY_FIELD, dash::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, dash::setCooldownTicks);
        applyNumberField(LENGTH_FIELD, dash::setLength);
        applyNumberField(SPEED_FIELD, dash::setSpeed);
        applyNumberField(WIDTH_FIELD, dash::setWidth);
        applyNumberField(HEIGHT_FIELD, dash::setHeight);
        applyNumberField(DAMAGE_FIELD, dash::setDamage);
        applyNumberField(KNOCKBACK_FIELD, dash::setKnockback);
        applyNumberField(STUN_TICKS_FIELD, dash::setStunTicks);
        applyNumberField(STUN_PERCENT_FIELD, dash::setStunDamagePercent);
        applyNumberField(SLAM_RADIUS_FIELD, dash::setSlamRadius);
        applyNumberField(SLAM_DAMAGE_FIELD, dash::setSlamDamage);
        applyNumberField(SLAM_KNOCKBACK_FIELD, dash::setSlamKnockback);
        applyNumberField(CHAIN_TICKS_FIELD, dash::setChainStunTicks);
        applyNumberField(CHAIN_PERCENT_FIELD, dash::setChainDamagePercent);
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
