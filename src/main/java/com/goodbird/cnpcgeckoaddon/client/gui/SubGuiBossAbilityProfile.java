package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeYesNo;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * One ability's profile in one phase: its timer, its turn in the rotation, the conditions it
 * casts under and which of the enrage's multipliers reach it, a group under a heading each.
 *
 * <p>Opened from the phase's list of profiles and from the ability's own screen, on the same
 * profile object, so the two can never disagree. Everything at its default is the rotation the
 * boss had before profiles, which the hint at the bottom says once.</p>
 *
 * <p>The groups are laid down one after another by {@link #layout}, each a method of its own that
 * takes the row it starts on and hands back the row after it: a later group is one more method and
 * one more line there, on ids after the last ones used here.</p>
 */
public final class SubGuiBossAbilityProfile extends SubGuiFieldScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    // Timer.
    private static final int JITTER_FIELD = 1;
    private static final int FIRST_DELAY_FIELD = 2;
    private static final int REPEATS_FIELD = 3;
    private static final int REPEAT_DELAY_FIELD = 4;
    // Rotation.
    private static final int PRIORITY_FIELD = 5;
    private static final int CHANCE_FIELD = 6;
    private static final int MAX_CASTS_FIELD = 7;
    // Conditions.
    private static final int MIN_DISTANCE_FIELD = 8;
    private static final int MAX_DISTANCE_FIELD = 9;
    private static final int MIN_PLAYERS_FIELD = 10;
    private static final int HEALTH_MIN_FIELD = 11;
    private static final int HEALTH_MAX_FIELD = 12;
    // Rage.
    private static final int RAGE_COOLDOWN_BUTTON = 13;
    private static final int RAGE_DAMAGE_BUTTON = 14;
    private static final int RAGE_KNOCKBACK_BUTTON = 15;

    private static final int TITLE_LABEL = 30;
    /** Each group's heading takes the next label id from here, in the order the groups are laid down. */
    private static final int FIRST_HEADING_LABEL = 40;
    private static final int HINT_LABEL = 60;
    /** Where the second lines of wrapped row labels are counted from. */
    private static final int WRAPPED_LABEL = 100;

    /** The first row's offset from the top of the panel, clear of the title. */
    private static final int FIRST_ROW = 18;
    private static final int CONTROL_HEIGHT = 20;
    private static final int ROW = CONTROL_HEIGHT + 1;
    /** A heading's line, and the gap after a group before the next heading. */
    private static final int HEADING_HEIGHT = 12;
    private static final int GROUP_GAP = 4;
    private static final int LABEL_X = 8;
    private static final int LABEL_DROP = 6;
    private static final int RIGHT_EDGE = 242;
    /** One number to a row, where every other settings screen puts it. */
    private static final int SINGLE_X = 172;
    private static final int SINGLE_WIDTH = 70;
    /** Two numbers to a row, the second flush with the right edge. */
    private static final int PAIR_X = 140;
    private static final int PAIR_SECOND_X = 194;
    private static final int PAIR_WIDTH = 48;
    private static final int TOGGLE_X = 196;
    private static final int TOGGLE_WIDTH = 46;
    private static final int HINT_GAP = 2;
    private static final int BUTTONS_GAP = 5;
    private static final int BOTTOM_MARGIN = 8;
    private static final String HINT = "cnpcgeckoaddon.boss.profile_hint";

    /** The screen this editor was opened from: the phase's list of profiles, or the ability's own screen. */
    private final Screen parent;
    private final BossPhaseData phase;
    private final int kind;
    private final BossAbilityProfile profile;
    private int wrappedLabel;
    private int headingLabel;

    public SubGuiBossAbilityProfile(Screen parent, BossPhaseData phase, int kind) {
        this.parent = parent;
        this.phase = phase;
        this.kind = kind;
        this.profile = phase.profile(kind);
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // The panel is centred from imageHeight, so the height is settled before super.init()
        // reads it: how many lines the hint and the row labels wrap to is up to the locale. Four
        // groups never fit one panel, so the screen scrolls.
        imageHeight = layout(false);
        super.init();
        layout(true);
    }

    /**
     * Puts every group down the panel, or with {@code place} false only measures how tall they
     * come to - the dash screen's way of keeping the panel and its contents from drifting apart.
     *
     * @return the height the panel needs
     */
    private int layout(boolean place) {
        wrappedLabel = WRAPPED_LABEL;
        headingLabel = FIRST_HEADING_LABEL;
        if (place) {
            addLabel(new ThemeLabel(TITLE_LABEL, title(), guiLeft + 8, guiTop + 5, ThemeLabel.TITLE_COLOR));
        }
        int y = FIRST_ROW;
        y = timerGroup(place, y);
        y = rotationGroup(place, y);
        y = conditionsGroup(place, y);
        y = rageGroup(place, y);

        y += HINT_GAP;
        if (place) {
            addWrappedHint(HINT_LABEL, HINT, guiTop + y);
        }
        y += wrappedHintHeight(HINT) + BUTTONS_GAP;
        if (place) {
            addDoneButton(guiLeft + 182, guiTop + y, 60, CONTROL_HEIGHT);
        }
        return y + CONTROL_HEIGHT + BOTTOM_MARGIN;
    }

    /** The spread on the cooldown, when the first cast comes, and the casts that follow it in a row. */
    private int timerGroup(boolean place, int y) {
        y = heading(place, "cnpcgeckoaddon.boss.profile.timer", y);
        y = single(place, JITTER_FIELD, "cnpcgeckoaddon.boss.profile.jitter", y, profile.getCooldownJitterTicks(),
                0, BossAbilityProfile.MAX_COOLDOWN_JITTER, 0);
        // -1 is a value here, and the number fields take no minus sign: this one is read back by hand.
        if (place) {
            rowLabel(FIRST_DELAY_FIELD, "cnpcgeckoaddon.boss.profile.first_delay", y, SINGLE_X);
            addTextField(new ThemeTextField(FIRST_DELAY_FIELD, this, guiLeft + SINGLE_X, guiTop + y, SINGLE_WIDTH,
                    CONTROL_HEIGHT, Integer.toString(profile.getFirstCastDelayTicks())));
        }
        y += ROW;
        y = pair(place, REPEATS_FIELD, REPEAT_DELAY_FIELD, "cnpcgeckoaddon.boss.profile.repeats", y,
                profile.getRepeatCasts(), 0, BossAbilityProfile.MAX_REPEATS, 0,
                profile.getRepeatDelayTicks(), 0, BossAbilityProfile.MAX_REPEAT_DELAY,
                BossAbilityProfile.DEFAULT_REPEAT_DELAY);
        return y + GROUP_GAP;
    }

    /** Where the ability stands among the others, the odds it goes, and how often per phase. */
    private int rotationGroup(boolean place, int y) {
        y = heading(place, "cnpcgeckoaddon.boss.profile.rotation", y);
        y = single(place, PRIORITY_FIELD, "cnpcgeckoaddon.boss.profile.priority", y, profile.getPriority(),
                0, BossAbilityProfile.MAX_PRIORITY, 0);
        y = single(place, CHANCE_FIELD, "cnpcgeckoaddon.boss.profile.chance", y, profile.getCastChancePercent(),
                BossAbilityProfile.MIN_CAST_CHANCE, BossAbilityProfile.MAX_CAST_CHANCE, BossAbilityProfile.MAX_CAST_CHANCE);
        y = single(place, MAX_CASTS_FIELD, "cnpcgeckoaddon.boss.profile.max_casts", y, profile.getMaxCastsPerPhase(),
                0, BossAbilityProfile.MAX_CASTS_PER_PHASE, BossAbilityProfile.NO_CAST_LIMIT);
        return y + GROUP_GAP;
    }

    /** What has to be true for the ability to go: the target's distance, the party, the boss' health. */
    private int conditionsGroup(boolean place, int y) {
        y = heading(place, "cnpcgeckoaddon.boss.profile.conditions", y);
        y = pair(place, MIN_DISTANCE_FIELD, MAX_DISTANCE_FIELD, "cnpcgeckoaddon.boss.profile.distance", y,
                profile.getMinTargetDistance(), 0, BossAbilityProfile.MAX_DISTANCE, 0,
                profile.getMaxTargetDistance(), 0, BossAbilityProfile.MAX_DISTANCE, 0);
        y = single(place, MIN_PLAYERS_FIELD, "cnpcgeckoaddon.boss.profile.min_players", y, profile.getMinPlayers(),
                0, BossAbilityProfile.MAX_PLAYERS, 0);
        y = pair(place, HEALTH_MIN_FIELD, HEALTH_MAX_FIELD, "cnpcgeckoaddon.boss.profile.health_window", y,
                profile.getHealthMinPercent(), 0, 100, 0, profile.getHealthMaxPercent(), 0, 100, 100);
        return y + GROUP_GAP;
    }

    /** Which of the enrage's multipliers reach this ability: its cooldown, its damage, its knockback. */
    private int rageGroup(boolean place, int y) {
        y = heading(place, "cnpcgeckoaddon.boss.profile.rage", y);
        y = toggle(place, RAGE_COOLDOWN_BUTTON, "cnpcgeckoaddon.boss.profile.rage_cooldown", y,
                profile.isRageCooldown());
        y = toggle(place, RAGE_DAMAGE_BUTTON, "cnpcgeckoaddon.boss.profile.rage_damage", y, profile.isRageDamage());
        y = toggle(place, RAGE_KNOCKBACK_BUTTON, "cnpcgeckoaddon.boss.profile.rage_knockback", y,
                profile.isRageKnockback());
        return y + GROUP_GAP;
    }

    /** "Profile: Leap slam" - the ability's own name, the one the phase menu shows. */
    private String title() {
        Component name = kind >= 0 && kind < BossAbilityKind.COUNT
                ? Component.translatable(BossAbilityKind.LABELS[kind]) : Component.literal(Integer.toString(kind));
        return Component.translatable("cnpcgeckoaddon.boss.profile_title", name).getString();
    }

    private int heading(boolean place, String key, int y) {
        if (place) {
            addLabel(new ThemeLabel(headingLabel++, key, guiLeft + LABEL_X, guiTop + y + 2, ThemeLabel.TITLE_COLOR));
        }
        return y + HEADING_HEIGHT;
    }

    private int single(boolean place, int id, String key, int y, int value, int min, int max, int fallback) {
        if (place) {
            rowLabel(id, key, y, SINGLE_X);
            number(id, guiLeft + SINGLE_X, guiTop + y, SINGLE_WIDTH, value, min, max, fallback);
        }
        return y + ROW;
    }

    /** Two numbers on one line: the pairs the labels name with a slash between them. */
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

    private int toggle(boolean place, int id, String key, int y, boolean value) {
        if (place) {
            rowLabel(id, key, y, TOGGLE_X);
            addButton(new ThemeYesNo(this, id, guiLeft + TOGGLE_X, guiTop + y, TOGGLE_WIDTH, CONTROL_HEIGHT, value));
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
        // Not I18n.get: it runs the text through String.format, and two of these labels carry a
        // percent sign; a translatable component hands the text back as it reads.
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
        if (!(button instanceof GuiButtonYesNo yesNo)) {
            return;
        }
        boolean value = yesNo.getBoolean();
        if (button.id == RAGE_COOLDOWN_BUTTON) {
            profile.setRageCooldown(value);
        } else if (button.id == RAGE_DAMAGE_BUTTON) {
            profile.setRageDamage(value);
        } else if (button.id == RAGE_KNOCKBACK_BUTTON) {
            profile.setRageKnockback(value);
        }
    }

    @Override
    protected void applyFields() {
        applyNumberField(JITTER_FIELD, profile::setCooldownJitterTicks);
        GuiTextFieldNop firstDelay = getTextField(FIRST_DELAY_FIELD);
        if (firstDelay != null) {
            profile.setFirstCastDelayTicks(signed(firstDelay));
            // Shown back as kept: anything under -1 is -1, anything past the ceiling is the ceiling.
            firstDelay.setValue(Integer.toString(profile.getFirstCastDelayTicks()));
        }
        applyNumberField(REPEATS_FIELD, profile::setRepeatCasts);
        applyNumberField(REPEAT_DELAY_FIELD, profile::setRepeatDelayTicks);
        applyNumberField(PRIORITY_FIELD, profile::setPriority);
        applyNumberField(CHANCE_FIELD, profile::setCastChancePercent);
        applyNumberField(MAX_CASTS_FIELD, profile::setMaxCastsPerPhase);
        applyNumberField(MIN_DISTANCE_FIELD, profile::setMinTargetDistance);
        applyNumberField(MAX_DISTANCE_FIELD, profile::setMaxTargetDistance);
        applyNumberField(MIN_PLAYERS_FIELD, profile::setMinPlayers);
        applyNumberField(HEALTH_MIN_FIELD, profile::setHealthMinPercent);
        applyNumberField(HEALTH_MAX_FIELD, profile::setHealthMaxPercent);
        // A top typed under the bottom is raised to it; the field shows the window that was kept.
        GuiTextFieldNop healthMax = getTextField(HEALTH_MAX_FIELD);
        if (healthMax != null) {
            healthMax.setValue(Integer.toString(profile.getHealthMaxPercent()));
        }
    }

    @Override
    public void close() {
        super.close();
        // Led by the screen's name rather than by a capital: the screens' sources hold no
        // sentences, and the guard that keeps them out cannot tell a log line from a label.
        LOGGER.debug("{}: profile editor of ability kind {} closed, profile now {}",
                parent == null ? "no screen" : parent.getClass().getSimpleName(), kind, profile);
    }
}
