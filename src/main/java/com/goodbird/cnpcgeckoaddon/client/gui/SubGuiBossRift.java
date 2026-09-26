package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeYesNo;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.List;

/**
 * Reality rift: who the boss takes to its pocket dimension, what they have to do there to come
 * back, what the fight is meanwhile and after, and the platform they land on.
 */
public final class SubGuiBossRift extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int ACTION_DELAY_FIELD = 3;
    private static final int COOLDOWN_FIELD = 4;
    private static final int TARGET_MODE_BUTTON = 5;
    private static final int TARGET_COUNT_FIELD = 6;
    private static final int EXIT_BUTTON = 7;
    private static final int TIME_LIMIT_FIELD = 8;
    private static final int FAIL_ON_DEATH_BUTTON = 9;
    private static final int SOLO_MAX_FIELD = 10;
    private static final int SOLO_TICKS_FIELD = 11;
    private static final int SOLO_PERCENT_FIELD = 12;
    private static final int GROUP_DAMAGE_FIELD = 13;
    private static final int ARENA_BUTTON = 14;
    private static final int SLOT_FIELD = 15;
    private static final int PLATFORM_Y_FIELD = 16;
    private static final int PLATFORM_RADIUS_FIELD = 17;
    private static final int WALL_FIELD = 18;
    private static final int ABILITIES_BUTTON = 19;
    private static final int MINIONS_BUTTON = 20;
    private static final int FAIL_BUTTON = 21;
    private static final int TUNING_BUTTON = 22;
    private static final int CRYSTALS_BUTTON = 23;
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
    /** A cycle with a small number beside it: who is taken, and how many of them. */
    private static final int CHOICE_NUMBER_WIDTH = 84;
    private static final int CHOICE_NUMBER_FIELD_X = 200;
    private static final int CHOICE_NUMBER_FIELD_WIDTH = 42;
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

    private static final String HINT = "cnpcgeckoaddon.boss.rift_hint";

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private int wrappedLabel;

    public SubGuiBossRift(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // The panel is centred from imageHeight, so the height is settled before super.init()
        // reads it: the locale decides how many lines the hint takes. The rift has more settings
        // than a panel, so it scrolls.
        imageHeight = layout(false);
        super.init();
        layout(true);
    }

    /**
     * Puts every row down the panel, or with {@code place} false only measures how tall they
     * come to, the shadow copies' screen's way.
     *
     * @return the height the panel needs
     */
    private int layout(boolean place) {
        BossRiftSettings rift = phase.rift();
        wrappedLabel = WRAPPED_LABEL;
        if (place) {
            addLabel(new ThemeLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.rift_phase",
                    phaseIndex), guiLeft + LABEL_X, guiTop + 5, 0xFFFFFF));
        }
        int y = FIRST_ROW;
        y = toggle(place, ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", y, rift.isEnabled());
        y = select(place, ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", y, rift.getAnimation());
        y = pair(place, ACTION_DELAY_FIELD, COOLDOWN_FIELD, "cnpcgeckoaddon.boss.timing", y,
                rift.getActionDelayTicks(), 0, 1200, 30, rift.getCooldownTicks(), 1, 12000, 1200);
        y = choiceNumber(place, TARGET_MODE_BUTTON, TARGET_COUNT_FIELD, "cnpcgeckoaddon.boss.rift_targets", y,
                BossTargetMode.LABELS, rift.getTargetMode(),
                rift.getTargetCount(), 1, BossRiftSettings.MAX_TARGET_COUNT, 1);
        y = choice(place, EXIT_BUTTON, "cnpcgeckoaddon.boss.rift_exit", y,
                BossRiftSettings.EXIT_LABELS, rift.getExitMode());
        y = single(place, TIME_LIMIT_FIELD, "cnpcgeckoaddon.boss.rift_time_limit", y, rift.getTimeLimitTicks(),
                BossRiftSettings.MIN_TIME_LIMIT_TICKS, BossRiftSettings.MAX_TIME_LIMIT_TICKS, 1200);
        y = toggle(place, FAIL_ON_DEATH_BUTTON, "cnpcgeckoaddon.boss.rift_fail_on_death", y, rift.isFailOnDeath());
        y = single(place, SOLO_MAX_FIELD, "cnpcgeckoaddon.boss.rift_solo_max", y, rift.getSoloMaxPlayers(),
                1, BossRiftSettings.MAX_SOLO_PLAYERS, 1);
        y = pair(place, SOLO_TICKS_FIELD, SOLO_PERCENT_FIELD, "cnpcgeckoaddon.boss.rift_solo_vulnerable", y,
                rift.getSoloVulnerableTicks(), 0, BossRiftSettings.MAX_SOLO_VULNERABLE_TICKS, 200,
                rift.getSoloVulnerablePercent(), BossRiftSettings.MIN_SOLO_VULNERABLE_PERCENT,
                BossRiftSettings.MAX_SOLO_VULNERABLE_PERCENT, 150);
        y = single(place, GROUP_DAMAGE_FIELD, "cnpcgeckoaddon.boss.rift_group_damage", y,
                rift.getGroupDamagePercent(), 0, BossRiftSettings.MAX_GROUP_DAMAGE_PERCENT, 50);
        y = choice(place, ARENA_BUTTON, "cnpcgeckoaddon.boss.rift_arena", y,
                BossRiftSettings.ARENA_LABELS, rift.getArenaMode());
        y = single(place, SLOT_FIELD, "cnpcgeckoaddon.boss.rift_slot", y, rift.getSlot(),
                0, BossRiftSettings.MAX_SLOT, 0);
        y = pair(place, PLATFORM_Y_FIELD, PLATFORM_RADIUS_FIELD, "cnpcgeckoaddon.boss.rift_platform", y,
                rift.getPlatformY(), BossRiftSettings.MIN_PLATFORM_Y, BossRiftSettings.MAX_PLATFORM_Y, 64,
                rift.getPlatformRadius(), BossRiftSettings.MIN_PLATFORM_RADIUS,
                BossRiftSettings.MAX_PLATFORM_RADIUS, 16);
        y = single(place, WALL_FIELD, "cnpcgeckoaddon.boss.rift_wall", y, rift.getWallHeight(),
                0, BossRiftSettings.MAX_WALL_HEIGHT, 4);
        y = wide(place, ABILITIES_BUTTON, "cnpcgeckoaddon.boss.rift_abilities", y);
        y = wide(place, MINIONS_BUTTON, "cnpcgeckoaddon.boss.rift_minions", y);
        y = wide(place, CRYSTALS_BUTTON, "cnpcgeckoaddon.boss.rift_crystals", y);
        y = wide(place, FAIL_BUTTON, "cnpcgeckoaddon.boss.rift_fail", y);
        y = wide(place, TUNING_BUTTON, "cnpcgeckoaddon.boss.rift_tuning", y);

        y += HINT_GAP;
        if (place) {
            addWrappedHint(HINT_LABEL, HINT, guiTop + y);
        }
        y += wrappedHintHeight(HINT) + BUTTONS_GAP;
        if (place) {
            addButton(new ThemeButton(this, EFFECTS_BUTTON, guiLeft + LABEL_X, guiTop + y, 120, CONTROL_HEIGHT,
                    "cnpcgeckoaddon.boss.effects_rift"));
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

    /** A cycle and a small number beside it, the two halves the label names with a slash between them. */
    private int choiceNumber(boolean place, int buttonId, int fieldId, String key, int y, String[] values,
                             int selected, int value, int min, int max, int fallback) {
        if (place) {
            rowLabel(buttonId, key, y, CHOICE_X);
            addButton(new ThemeButton(this, buttonId, guiLeft + CHOICE_X, guiTop + y, CHOICE_NUMBER_WIDTH,
                    CONTROL_HEIGHT, values, selected));
            number(fieldId, guiLeft + CHOICE_NUMBER_FIELD_X, guiTop + y, CHOICE_NUMBER_FIELD_WIDTH, value, min, max,
                    fallback);
        }
        return y + ROW;
    }

    /** A button the whole row wide, opening a screen of its own. */
    private int wide(boolean place, int id, String key, int y) {
        if (place) {
            addButton(new ThemeButton(this, id, guiLeft + LABEL_X, guiTop + y, RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT,
                    key));
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
        GuiTextFieldNop field = new ThemeTextField(id, this, x, y, width, CONTROL_HEIGHT, Integer.toString(value));
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
        // hands the text back as is, per cent signs and all.
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

    @Override
    public void buttonEvent(GuiButtonNop button) {
        BossRiftSettings rift = phase.rift();
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(rift.getEffects(), "cnpcgeckoaddon.boss.effects_rift"));
        } else if (button.id == ABILITIES_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossRiftAbilities(rift));
        } else if (button.id == MINIONS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossRiftMinions(npc, phase, phaseIndex));
        } else if (button.id == CRYSTALS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossRiftCrystals(npc, phase, phaseIndex));
        } else if (button.id == FAIL_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossRiftFail(phase, phaseIndex));
        } else if (button.id == TUNING_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossRiftTuning(rift));
        } else if (button.id == ENABLED_BUTTON) {
            rift.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == FAIL_ON_DEATH_BUTTON) {
            rift.setFailOnDeath(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TARGET_MODE_BUTTON) {
            rift.setTargetMode(button.getValue());
        } else if (button.id == EXIT_BUTTON) {
            rift.setExitMode(button.getValue());
        } else if (button.id == ARENA_BUTTON) {
            rift.setArenaMode(button.getValue());
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.rift_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                rift.setAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name, ACTION_DELAY_FIELD,
                        rift::setActionDelayTicks);
            }));
        }
    }

    @Override
    protected void applyFields() {
        BossRiftSettings rift = phase.rift();
        applyAnimation(rift);
        applyNumberField(ACTION_DELAY_FIELD, rift::setActionDelayTicks);
        applyNumberField(COOLDOWN_FIELD, rift::setCooldownTicks);
        applyNumberField(TARGET_COUNT_FIELD, rift::setTargetCount);
        applyNumberField(TIME_LIMIT_FIELD, rift::setTimeLimitTicks);
        applyNumberField(SOLO_MAX_FIELD, rift::setSoloMaxPlayers);
        applyNumberField(SOLO_TICKS_FIELD, rift::setSoloVulnerableTicks);
        applyNumberField(SOLO_PERCENT_FIELD, rift::setSoloVulnerablePercent);
        applyNumberField(GROUP_DAMAGE_FIELD, rift::setGroupDamagePercent);
        applyNumberField(SLOT_FIELD, rift::setSlot);
        applyNumberField(PLATFORM_Y_FIELD, rift::setPlatformY);
        applyNumberField(PLATFORM_RADIUS_FIELD, rift::setPlatformRadius);
        applyNumberField(WALL_FIELD, rift::setWallHeight);
    }

    /** Keeps a typed animation only when the model has it; otherwise the field snaps back. */
    private void applyAnimation(BossRiftSettings rift) {
        GuiTextFieldNop field = getTextField(ANIMATION_FIELD);
        if (field == null) {
            return;
        }
        String value = field.getValue().trim();
        if (BossAnimationGuiUtil.isValid(npc, value)) {
            rift.setAnimation(value);
        } else {
            field.setValue(rift.getAnimation());
        }
    }
}
