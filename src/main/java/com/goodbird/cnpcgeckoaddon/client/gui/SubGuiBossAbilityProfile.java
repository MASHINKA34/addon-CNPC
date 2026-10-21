package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeYesNo;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossSettingValue;
import com.goodbird.cnpcgeckoaddon.data.TelegraphLineStyles;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

/**
 * One ability's profile in one phase: its timer, its turn in the rotation, the conditions it
 * casts under, which of the enrage's multipliers reach it, how its hits are dealt and whom they
 * land on, and how it looks and sounds, a group under a heading each.
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
    // Damage and targets.
    private static final int DAMAGE_TYPE_BUTTON = 16;
    private static final int DAMAGE_PERCENT_MAX_FIELD = 17;
    private static final int DAMAGE_PERCENT_CURRENT_FIELD = 18;
    private static final int PARTY_DAMAGE_FIELD = 19;
    private static final int PARTY_DAMAGE_CAP_FIELD = 20;
    private static final int TARGET_KIND_BUTTON = 21;
    // Look. Past the title's label id rather than on it: a row's label takes its control's id.
    private static final int COLOR_FIELD = 22;
    private static final int COLOR_ERROR_LABEL = 23;
    private static final int STYLE_BUTTON = 24;
    private static final int WIDTH_FIELD = 25;
    private static final int LEAD_FIELD = 26;
    private static final int ANNOUNCE_BUTTON = 27;
    private static final int ANNOUNCE_SOUND_BUTTON = 28;
    private static final int ANNOUNCE_CUE_BUTTON = 29;
    private static final int NAME_FIELD = 31;
    private static final int CAST_ANIMATION_FIELD = 32;
    private static final int END_ANIMATION_FIELD = 33;

    /** In the profile's own order, so a button's index is the damage type. */
    private static final String[] DAMAGE_TYPE_LABELS = {
            "cnpcgeckoaddon.boss.profile.damage_type.normal",
            "cnpcgeckoaddon.boss.profile.damage_type.piercing",
            "cnpcgeckoaddon.boss.profile.damage_type.true"
    };
    /** As the boss first, then the boss' own three, so a button's index is the profile's kind plus one. */
    private static final String[] TARGET_KIND_LABELS = {
            "cnpcgeckoaddon.boss.profile.target_kind.boss",
            "cnpcgeckoaddon.boss.profile.target_kind.players",
            "cnpcgeckoaddon.boss.profile.target_kind.npcs",
            "cnpcgeckoaddon.boss.profile.target_kind.all"
    };
    /** As the boss first, then the styles the boss' own screen offers, in their table's order. */
    private static final String[] STYLE_LABELS = Stream.concat(Stream.of("cnpcgeckoaddon.boss.profile.style.boss"),
            TelegraphLineStyles.values().stream().map(TelegraphLineStyles.Style::translationKey)).toArray(String[]::new);
    /** As the boss, off, on: a button's index is the profile's mode plus one, as for the kinds of target. */
    private static final String[] ANNOUNCE_LABELS = {
            "cnpcgeckoaddon.boss.profile.announce.boss",
            "cnpcgeckoaddon.boss.profile.announce.off",
            "cnpcgeckoaddon.boss.profile.announce.on"
    };
    private static final String[] ANNOUNCE_SOUND_LABELS = {
            "cnpcgeckoaddon.boss.profile.announce_sound.boss",
            "cnpcgeckoaddon.boss.profile.announce_sound.off",
            "cnpcgeckoaddon.boss.profile.announce_sound.own"
    };
    private static final String COLOR_BAD = "cnpcgeckoaddon.boss.profile.color_bad";
    private static final int ERROR_COLOR = 0xFF5555;
    /** "#" and six digits. */
    private static final int COLOR_MAX_LENGTH = 7;

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
    /** A choice among a few: as wide as a pair of numbers, where the pairs start. */
    private static final int CYCLE_X = PAIR_X;
    /** A name typed in, with a Select beside it for an animation: the picker rows' own columns. */
    private static final int TEXT_X = 108;
    private static final int SELECT_X = 198;
    private static final int SELECT_WIDTH = 44;
    /** The line under the colour that says a typed colour was refused, kept so the rows do not jump. */
    private static final int ERROR_LINE = LINE_HEIGHT + 1;
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
    /** Set when the last read refused a typed colour, so the line under it says so until it is fixed. */
    private boolean colorRejected;

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
        // reads it: how many lines the hint and the row labels wrap to is up to the locale. Six
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
        y = damageGroup(place, y);
        y = lookGroup(place, y);

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

    /**
     * How the hits are dealt - their damage type, shares of the victim's health and the party's
     * bonus - and whom they land on. The two choices write straight into the profile from their
     * buttons, so the rage group's handler below stays as it was.
     */
    private int damageGroup(boolean place, int y) {
        y = heading(place, "cnpcgeckoaddon.boss.profile.damage", y);
        y = cycle(place, DAMAGE_TYPE_BUTTON, "cnpcgeckoaddon.boss.profile.damage_type", y, DAMAGE_TYPE_LABELS,
                profile.getDamageType(), button -> profile.setDamageType(button.getValue()));
        y = pair(place, DAMAGE_PERCENT_MAX_FIELD, DAMAGE_PERCENT_CURRENT_FIELD,
                "cnpcgeckoaddon.boss.profile.damage_percent", y,
                profile.getDamagePercentMax(), 0, BossAbilityProfile.MAX_DAMAGE_PERCENT, 0,
                profile.getDamagePercentCurrent(), 0, BossAbilityProfile.MAX_DAMAGE_PERCENT, 0);
        y = pair(place, PARTY_DAMAGE_FIELD, PARTY_DAMAGE_CAP_FIELD, "cnpcgeckoaddon.boss.profile.party_damage", y,
                profile.getPartyDamagePercent(), 0, BossAbilityProfile.MAX_PARTY_DAMAGE_PERCENT, 0,
                profile.getPartyDamageCapPercent(), 0, BossAbilityProfile.MAX_PARTY_DAMAGE_CAP,
                BossAbilityProfile.DEFAULT_PARTY_DAMAGE_CAP);
        y = cycle(place, TARGET_KIND_BUTTON, "cnpcgeckoaddon.boss.profile.target_kind", y, TARGET_KIND_LABELS,
                profile.getTargetKind() - BossAbilityProfile.TARGET_KIND_AS_BOSS,
                button -> profile.setTargetKind(button.getValue() + BossAbilityProfile.TARGET_KIND_AS_BOSS));
        return y + GROUP_GAP;
    }

    /**
     * How the ability looks and sounds: the colour, style, width and lead of its warning, whether it
     * is announced and with which sound, the name the fight calls it, and the animations of its cast
     * and of its end. Every choice writes straight into the profile from its button; the typed ones
     * are read back with the rest in {@link #applyFields}.
     */
    private int lookGroup(boolean place, int y) {
        y = heading(place, "cnpcgeckoaddon.boss.profile.look", y);
        // Six hex digits rather than a number: a numbers-only field would leave the builder
        // converting FF2020 into sixteen million by hand. Empty is the kind's own colour.
        if (place) {
            rowLabel(COLOR_FIELD, "cnpcgeckoaddon.boss.profile.color", y, SINGLE_X);
            GuiTextFieldNop color = new ThemeTextField(COLOR_FIELD, this, guiLeft + SINGLE_X, guiTop + y, SINGLE_WIDTH,
                    CONTROL_HEIGHT, BossAbilityProfile.formatColor(profile.getTelegraphColor()));
            color.setMaxLength(COLOR_MAX_LENGTH);
            addTextField(color);
            addLabel(new ThemeLabel(COLOR_ERROR_LABEL, colorErrorText(), ERROR_COLOR, guiLeft + LABEL_X,
                    guiTop + y + ROW, RIGHT_EDGE - LABEL_X, LINE_HEIGHT));
        }
        y += ROW + ERROR_LINE;
        y = choice(place, STYLE_BUTTON, "cnpcgeckoaddon.boss.profile.style", y, STYLE_LABELS, this::styleIndex,
                index -> profile.setTelegraphStyle(index == 0 ? BossAbilityProfile.STYLE_AS_BOSS
                        : TelegraphLineStyles.byIndex(index - 1)));
        // -1 is a value in both, and the number fields take no minus sign: these are read back by hand.
        y = signedField(place, WIDTH_FIELD, "cnpcgeckoaddon.boss.profile.width", y, profile.getTelegraphLineWidth());
        y = signedField(place, LEAD_FIELD, "cnpcgeckoaddon.boss.profile.lead", y, profile.getTelegraphLeadTicks());
        y = choice(place, ANNOUNCE_BUTTON, "cnpcgeckoaddon.boss.profile.announce", y, ANNOUNCE_LABELS,
                () -> profile.getAnnounceMode() - BossAbilityProfile.ANNOUNCE_AS_BOSS,
                index -> profile.setAnnounceMode(index + BossAbilityProfile.ANNOUNCE_AS_BOSS));
        y = choice(place, ANNOUNCE_SOUND_BUTTON, "cnpcgeckoaddon.boss.profile.announce_sound", y,
                ANNOUNCE_SOUND_LABELS, () -> profile.getAnnounceSoundMode() - BossAbilityProfile.ANNOUNCE_SOUND_AS_BOSS,
                index -> profile.setAnnounceSoundMode(index + BossAbilityProfile.ANNOUNCE_SOUND_AS_BOSS));
        if (place) {
            addCueButton(ANNOUNCE_CUE_BUTTON, "cnpcgeckoaddon.boss.profile.announce_cue", guiTop + y,
                    profile.getAnnounceSound());
        }
        y += ROW;
        if (place) {
            rowLabel(NAME_FIELD, "cnpcgeckoaddon.boss.profile.name", y, TEXT_X);
            GuiTextFieldNop name = new ThemeTextField(NAME_FIELD, this, guiLeft + TEXT_X, guiTop + y,
                    RIGHT_EDGE - TEXT_X, CONTROL_HEIGHT, profile.getDisplayName());
            name.setMaxLength(BossAbilityProfile.MAX_NAME_LENGTH);
            addTextField(name);
        }
        y += ROW;
        y = animation(place, CAST_ANIMATION_FIELD, "cnpcgeckoaddon.boss.profile.cast_animation", y,
                profile.getCastAnimation(), "cnpcgeckoaddon.string_picker.cast_animation", profile::setCastAnimation);
        y = animation(place, END_ANIMATION_FIELD, "cnpcgeckoaddon.boss.profile.end_animation", y,
                profile.getEndAnimation(), "cnpcgeckoaddon.string_picker.end_animation", profile::setEndAnimation);
        return y + GROUP_GAP;
    }

    /** Where the profile's style sits among the choices: nought for the boss', its table's place plus one otherwise. */
    private int styleIndex() {
        String style = profile.getTelegraphStyle();
        return style.isEmpty() ? 0 : TelegraphLineStyles.indexOf(style) + 1;
    }

    /**
     * A choice among a few on a button of its own across the row, reading "Label: choice" - the
     * name and the value on one button, since a value such as "as the boss" says nothing beside a
     * label it does not sit under. Each click moves to the next choice and writes it at once.
     */
    private int choice(boolean place, int id, String key, int y, String[] valueKeys, IntSupplier index,
                       IntConsumer choose) {
        if (place) {
            addButton(new ThemeButton(this, id, guiLeft + LABEL_X, guiTop + y, RIGHT_EDGE - LABEL_X, CONTROL_HEIGHT,
                    choiceText(key, valueKeys, index.getAsInt()), button -> {
                        choose.accept((index.getAsInt() + 1) % valueKeys.length);
                        ((GuiButtonNop) button).setDisplayText(choiceText(key, valueKeys, index.getAsInt()));
                        LOGGER.debug("{}: {} of ability kind {} now {}", getClass().getSimpleName(), key, kind,
                                valueKeys[index.getAsInt()]);
                    }));
        }
        return y + ROW;
    }

    private static String choiceText(String key, String[] valueKeys, int index) {
        int shown = Math.floorMod(index, valueKeys.length);
        return Component.translatable(key, Component.translatable(valueKeys[shown])).getString();
    }

    /** A number that may be -1, typed into a plain field and read back by hand in {@link #applyFields}. */
    private int signedField(boolean place, int id, String key, int y, int value) {
        if (place) {
            rowLabel(id, key, y, SINGLE_X);
            addTextField(new ThemeTextField(id, this, guiLeft + SINGLE_X, guiTop + y, SINGLE_WIDTH, CONTROL_HEIGHT,
                    Integer.toString(value)));
        }
        return y + ROW;
    }

    /**
     * An animation typed in, with a Select beside it that lists the npc's own - the leap screen's two
     * rows over again. The npc is the one whose page the screens were opened from.
     */
    private int animation(boolean place, int id, String key, int y, String value, String pickerKey,
                          Consumer<String> setter) {
        if (place) {
            rowLabel(id, key, y, TEXT_X);
            addTextField(new ThemeTextField(id, this, guiLeft + TEXT_X, guiTop + y, SELECT_X - TEXT_X - 4,
                    CONTROL_HEIGHT, value));
            addButton(new ThemeButton(this, id, guiLeft + SELECT_X, guiTop + y, SELECT_WIDTH, CONTROL_HEIGHT,
                    "mco.template.button.select", button -> {
                        applyFields();
                        EntityNPCInterface npc = BossAnimationGuiUtil.editedNpc();
                        List<String> animations = BossAnimationGuiUtil.getAnimations(npc);
                        LOGGER.debug("{}: picking {} of ability kind {} among {} animations of {}",
                                getClass().getSimpleName(), key, kind, animations.size(),
                                npc == null ? "no npc" : npc.getName().getString());
                        setSubGui(new GuiStringSelection(this, pickerKey, animations, name -> {
                            setter.accept(name);
                            GuiTextFieldNop field = getTextField(id);
                            if (field != null) {
                                field.setValue(name);
                            }
                        }));
                    }));
        }
        return y + ROW;
    }

    /** What the line under the colour says: nothing, or that the typed colour was refused. */
    private Component colorErrorText() {
        return colorRejected ? Component.translatable(COLOR_BAD) : Component.literal("");
    }

    private void showColorRejected(boolean value) {
        colorRejected = value;
        GuiLabel label = getLabel(COLOR_ERROR_LABEL);
        if (label != null) {
            label.setMessage(colorErrorText());
        }
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

    /** A choice among a few, cycled by clicking; {@code apply} takes the button with its new value. */
    private int cycle(boolean place, int id, String key, int y, String[] labels, int value,
                      Consumer<GuiButtonNop> apply) {
        if (place) {
            rowLabel(id, key, y, CYCLE_X);
            addButton(new ThemeButton(this, id, guiLeft + CYCLE_X, guiTop + y, RIGHT_EDGE - CYCLE_X, CONTROL_HEIGHT,
                    button -> apply.accept((GuiButtonNop) button), value, labels));
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
            // Nothing usable typed is the field's own "not set" - after a full cooldown - and never
            // nought, which would be a cast the moment the phase starts.
            profile.setFirstCastDelayTicks(signedOr(FIRST_DELAY_FIELD, firstDelay,
                    BossAbilityProfile.FIRST_CAST_AFTER_COOLDOWN));
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
        applyNumberField(DAMAGE_PERCENT_MAX_FIELD, profile::setDamagePercentMax);
        applyNumberField(DAMAGE_PERCENT_CURRENT_FIELD, profile::setDamagePercentCurrent);
        applyNumberField(PARTY_DAMAGE_FIELD, profile::setPartyDamagePercent);
        applyNumberField(PARTY_DAMAGE_CAP_FIELD, profile::setPartyDamageCapPercent);
        applyLook();
    }

    /** The look group's typed fields: the colour, the width and the lead, the name, the two animations. */
    private void applyLook() {
        GuiTextFieldNop color = getTextField(COLOR_FIELD);
        if (color != null) {
            // Anything that is not six hex digits is refused while the field is still in front of
            // whoever typed it, the way a cue's id is: the colour stays what it was, and says so.
            int parsed = BossAbilityProfile.parseColor(color.getValue());
            if (parsed == BossAbilityProfile.COLOR_INVALID) {
                LOGGER.debug("{}: refused colour '{}' for ability kind {}, kept '{}'", getClass().getSimpleName(),
                        color.getValue(), kind, BossAbilityProfile.formatColor(profile.getTelegraphColor()));
                showColorRejected(true);
            } else {
                profile.setTelegraphColor(parsed);
                showColorRejected(false);
            }
            color.setValue(BossAbilityProfile.formatColor(profile.getTelegraphColor()));
        }
        applySigned(WIDTH_FIELD, profile::setTelegraphLineWidth, profile::getTelegraphLineWidth);
        applySigned(LEAD_FIELD, profile::setTelegraphLeadTicks, profile::getTelegraphLeadTicks);
        GuiTextFieldNop name = getTextField(NAME_FIELD);
        if (name != null) {
            profile.setDisplayName(name.getValue());
            // Shown back as kept: trimmed, and cut to the longest name there is.
            name.setValue(profile.getDisplayName());
        }
        applyAnimation(CAST_ANIMATION_FIELD, profile.getCastAnimation(), profile::setCastAnimation);
        applyAnimation(END_ANIMATION_FIELD, profile.getEndAnimation(), profile::setEndAnimation);
    }

    /**
     * A field that may hold -1: an empty one, a lone minus or anything that is not a number is the
     * boss' own as well, and whatever the profile kept of the typed number - a width under the
     * narrowest is the boss', a lead past the ceiling is the ceiling - is shown back.
     */
    private void applySigned(int id, IntConsumer setter, IntSupplier kept) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return;
        }
        setter.accept(signedOr(id, field, BossAbilityProfile.AS_BOSS));
        field.setValue(Integer.toString(kept.getAsInt()));
    }

    /**
     * The number in a field whose "not set" is {@code unset}, or {@code unset} when it holds none.
     * Not {@code signed()}: a coordinate rightly reads a half-typed number as nought, but nought here
     * is a setting of its own rather than none.
     */
    private int signedOr(int id, GuiTextFieldNop field, int unset) {
        String typed = field.getValue();
        int value = BossSettingValue.parseOr(typed, unset);
        if (value == unset && !typed.trim().equals(Integer.toString(unset))) {
            LOGGER.debug("{}: field {} of ability kind {} holds no number ('{}'), read as {}",
                    getClass().getSimpleName(), id, kind, typed, unset);
        }
        return value;
    }

    /**
     * An animation the npc does not have would play nothing with nothing on screen to say why, so it
     * is refused the leap screen's way. With no npc to ask - an editor whose stack does not start at
     * the npc's page - the name is taken as typed.
     */
    private void applyAnimation(int id, String current, Consumer<String> setter) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return;
        }
        String value = field.getValue().trim();
        EntityNPCInterface npc = BossAnimationGuiUtil.editedNpc();
        if (npc == null || BossAnimationGuiUtil.isValid(npc, value)) {
            setter.accept(value);
        } else {
            LOGGER.debug("{}: refused animation '{}' for ability kind {}: {} has no such animation",
                    getClass().getSimpleName(), value, kind, npc.getName().getString());
            field.setValue(current);
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
