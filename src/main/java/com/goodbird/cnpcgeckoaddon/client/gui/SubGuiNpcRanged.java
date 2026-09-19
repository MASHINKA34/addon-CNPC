package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossSoundCue;
import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import com.goodbird.cnpcgeckoaddon.utils.ProjectileEntityUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import noppes.npcs.api.constants.PotionEffectType;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataRanged;
import noppes.npcs.shared.client.gui.components.GuiButtonBiDirectional;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Everything about how a plain npc shoots, on one screen with six pages: what it fires, how it
 * fires, the distance it keeps, its burst and reload, what it does about a target it cannot see,
 * and what it all sounds like.
 *
 * <p>The CustomNPCs numbers on it - damage, speed, range, the delays, the burst - are read out
 * of and written into the very {@code DataRanged} the CustomNPCs screen behind this one edits,
 * so the two screens show the same figures and either one may change them; the addon's own
 * settings ride along in the same object. All of it reaches the server the way it always did,
 * with the stats screen's save. Which page is open is remembered for the length of the screen
 * and no longer: it is not a setting.</p>
 */
public final class SubGuiNpcRanged extends SubGuiFieldScreen {
    private static final int PAGE_BUTTON = 1;

    private static final int PROJECTILE_FIELD = 2;
    private static final int FALLBACK_FIELD = 3;
    private static final int RESET_BUTTON = 4;
    private static final int SPEED_FIELD = 5;
    private static final int ACCURACY_FIELD = 6;
    private static final int SIZE_FIELD = 7;
    private static final int GRAVITY_BUTTON = 8;
    private static final int STICKS_BUTTON = 9;
    private static final int GLOWS_BUTTON = 10;
    private static final int SPINS_BUTTON = 11;
    private static final int RENDER_3D_BUTTON = 12;
    private static final int ACCELERATE_BUTTON = 13;
    private static final int EXPLODE_BUTTON = 14;
    private static final int EFFECT_BUTTON = 15;
    private static final int EFFECT_TIME_FIELD = 16;
    private static final int EFFECT_STRENGTH_BUTTON = 17;
    private static final int MUZZLE_FIELD = 18;

    private static final int STRENGTH_FIELD = 20;
    private static final int KNOCKBACK_FIELD = 21;
    private static final int RANGE_FIELD = 22;
    private static final int DELAY_MIN_FIELD = 23;
    private static final int DELAY_MAX_FIELD = 24;
    private static final int SHOT_COUNT_FIELD = 25;
    private static final int CNPC_BURST_FIELD = 26;
    private static final int CNPC_BURST_DELAY_FIELD = 27;
    private static final int FIRE_TYPE_BUTTON = 28;
    private static final int AIM_ANIMATION_BUTTON = 29;
    private static final int MELEE_RANGE_FIELD = 30;

    private static final int ADDON_ENABLED_BUTTON = 40;
    private static final int ENGAGE_MIN_FIELD = 41;
    private static final int ENGAGE_MAX_FIELD = 42;
    private static final int TOO_FAR_BUTTON = 43;
    private static final int TOO_CLOSE_BUTTON = 44;
    private static final int KEEP_DISTANCE_FIELD = 45;

    private static final int LEAD_FIELD = 50;
    private static final int BURST_SHOTS_FIELD = 51;
    private static final int BURST_DELAY_FIELD = 52;
    private static final int SPREAD_FIELD = 53;
    private static final int RELOAD_FIELD = 54;
    private static final int RELOAD_ANIMATION_FIELD = 55;

    private static final int LOS_BUTTON = 60;
    private static final int LOB_WARN_FIELD = 61;
    private static final int LOB_WARN_RADIUS_FIELD = 62;
    private static final int LOB_WARN_CUE_BUTTON = 63;

    private static final int FIRE_SOUND_FIELD = 70;
    private static final int HIT_SOUND_FIELD = 71;
    private static final int GROUND_SOUND_FIELD = 72;
    private static final int SHOT_VOLUME_FIELD = 73;
    private static final int SHOT_PITCH_FIELD = 74;
    private static final int RELOAD_CUE_BUTTON = 75;

    private static final int TITLE_LABEL = 90;
    /** Each hint takes one label per line it wraps to, so every hint has a run of ids to itself. */
    private static final int FIRST_HINT_LABEL = 100;
    private static final int HINT_LABEL_RUN = 10;
    /** Where the second lines of wrapped row labels are counted from. */
    private static final int WRAPPED_LABEL = 300;

    /** The pages, in the order the cycle button walks them. */
    private static final int PAGE_PROJECTILE = 0;
    private static final int PAGE_FIRE = 1;
    private static final int PAGE_DISTANCE = 2;
    private static final int PAGE_BURST = 3;
    private static final int PAGE_SIGHT = 4;
    private static final int PAGE_SOUNDS = 5;
    private static final String[] PAGES = {
            "cnpcgeckoaddon.npc_ranged.page_projectile",
            "cnpcgeckoaddon.npc_ranged.page_fire",
            "cnpcgeckoaddon.npc_ranged.page_distance",
            "cnpcgeckoaddon.npc_ranged.page_burst",
            "cnpcgeckoaddon.npc_ranged.page_sight",
            "cnpcgeckoaddon.npc_ranged.page_sounds",
    };

    private static final String[] TOO_FAR_LABELS = {
            "cnpcgeckoaddon.npc_ranged.too_far.approach",
            "cnpcgeckoaddon.npc_ranged.too_far.wait",
    };
    private static final String[] TOO_CLOSE_LABELS = {
            "cnpcgeckoaddon.npc_ranged.too_close.retreat",
            "cnpcgeckoaddon.npc_ranged.too_close.melee",
            "cnpcgeckoaddon.npc_ranged.too_close.fire",
    };
    private static final String[] LOS_LABELS = {
            "cnpcgeckoaddon.npc_ranged.los.customnpcs",
            "cnpcgeckoaddon.npc_ranged.los.wait",
            "cnpcgeckoaddon.npc_ranged.los.lob",
    };
    /** CustomNPCs' "shoot indirect", by its own values: never, past half the range, while hidden. */
    private static final String[] FIRE_TYPE_LABELS = {
            "cnpcgeckoaddon.npc_ranged.cnpc_fire_type.direct",
            "cnpcgeckoaddon.npc_ranged.cnpc_fire_type.arc",
            "cnpcgeckoaddon.npc_ranged.cnpc_fire_type.mortar",
    };
    private static final String[] EXPLODE_LABELS = {
            "cnpcgeckoaddon.npc_ranged.cnpc_explode.none",
            "cnpcgeckoaddon.npc_ranged.cnpc_explode.small",
            "cnpcgeckoaddon.npc_ranged.cnpc_explode.medium",
            "cnpcgeckoaddon.npc_ranged.cnpc_explode.large",
    };
    private static final String[] EFFECT_STRENGTH_LABELS = {
            "cnpcgeckoaddon.npc_ranged.cnpc_effect_strength.regular",
            "cnpcgeckoaddon.npc_ranged.cnpc_effect_strength.amplified",
    };

    /**
     * What CustomNPCs stores for a projectile that sets its victim on fire, in place of a
     * potion index; it sits past the potions on the list, the way its own screen shows it.
     */
    private static final int EFFECT_FIRE = 666;
    private static final int POTION_COUNT = 32;

    /** What the CustomNPCs editor itself allows for each of its numbers, so this one agrees with it. */
    private static final int MAX_STRENGTH = Integer.MAX_VALUE;
    private static final int MAX_KNOCKBACK = 3;
    private static final int MIN_SIZE = 5;
    private static final int MAX_SIZE = 20;
    private static final int MIN_SPEED = 1;
    private static final int MAX_SPEED = 50;
    private static final int MAX_ACCURACY = 100;
    private static final int MIN_RANGE = 1;
    private static final int MAX_RANGE = 100;
    private static final int MIN_DELAY = 1;
    private static final int MAX_DELAY = 9999;
    private static final int MIN_CNPC_BURST = 1;
    private static final int MAX_CNPC_BURST = 100;
    private static final int MAX_CNPC_BURST_DELAY = 30;
    private static final int MAX_EFFECT_TIME = 99999;
    /** The melee range's own ceiling is the npc's aggro range; this is for an npc with none to ask. */
    private static final int MAX_MELEE_RANGE = 64;

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
    /** An id typed in, and the picker beside it. */
    private static final int SELECT_FIELD_X = 104;
    private static final int SELECT_FIELD_WIDTH = 91;
    private static final int SELECT_BUTTON_X = 199;
    private static final int SELECT_BUTTON_WIDTH = 43;
    /** Two numbers to a row, the second flush with the right edge; a lone number sits where the second would. */
    private static final int PAIR_X = 140;
    private static final int PAIR_SECOND_X = 194;
    private static final int PAIR_WIDTH = 48;
    private static final int HINT_GAP = 3;
    private static final int BOTTOM_MARGIN = 8;

    private final DataRanged ranged;
    private final RangedExtraData extra;
    private final EntityNPCInterface npc;
    private int page = PAGE_PROJECTILE;
    private int wrappedLabel;
    private int hintLabel;

    public SubGuiNpcRanged(DataRanged ranged) {
        this.ranged = ranged;
        this.extra = ((IRangedData) ranged).getRangedExtraData();
        this.npc = ((IRangedData) ranged).getRangedNpc();
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // The panel is centred from imageHeight, so the height is settled before super.init()
        // reads it: which page is open decides the rows, and the locale how many lines each
        // hint takes. The projectile page has more on it than a panel, so it scrolls.
        imageHeight = layout(false);
        super.init();
        layout(true);
    }

    /**
     * Puts every row of the open page down the panel, or with {@code place} false only
     * measures how tall they come to.
     *
     * @return the height the panel needs
     */
    private int layout(boolean place) {
        wrappedLabel = WRAPPED_LABEL;
        hintLabel = FIRST_HINT_LABEL;
        if (place) {
            addLabel(new GuiLabel(TITLE_LABEL, "cnpcgeckoaddon.npc_ranged.title", guiLeft + LABEL_X, guiTop + 5,
                    0xFFFFFF));
            addButton(new GuiButtonNop(this, PAGE_BUTTON, guiLeft + CHOICE_X, guiTop + FIRST_ROW, CHOICE_WIDTH,
                    CONTROL_HEIGHT, PAGES, page));
        }
        int y = FIRST_ROW + ROW + HINT_GAP;
        y = switch (page) {
            case PAGE_FIRE -> firePage(place, y);
            case PAGE_DISTANCE -> distancePage(place, y);
            case PAGE_BURST -> burstPage(place, y);
            case PAGE_SIGHT -> sightPage(place, y);
            case PAGE_SOUNDS -> soundsPage(place, y);
            default -> projectilePage(place, y);
        };
        y += HINT_GAP;
        if (place) {
            addDoneButton(guiLeft + 182, guiTop + y, 60, CONTROL_HEIGHT);
        }
        return y + CONTROL_HEIGHT + BOTTOM_MARGIN;
    }

    private int projectilePage(boolean place, int y) {
        y = entity(place, PROJECTILE_FIELD, "cnpcgeckoaddon.ranged_extras.projectile_entity", y,
                extra.getProjectileEntity());
        y = hint(place, "cnpcgeckoaddon.ranged_extras.projectile_hint", y);
        y = entity(place, FALLBACK_FIELD, "cnpcgeckoaddon.ranged_extras.fallback_projectile", y,
                extra.getFallbackProjectile());
        y = hint(place, "cnpcgeckoaddon.ranged_extras.fallback_hint", y);
        y = hint(place, "cnpcgeckoaddon.npc_ranged.item_hint", y);
        if (place) {
            addButton(new GuiButtonNop(this, RESET_BUTTON, guiLeft + CHOICE_X, guiTop + y, CHOICE_WIDTH,
                    CONTROL_HEIGHT, "cnpcgeckoaddon.ranged_extras.reset_projectile"));
        }
        y += ROW;
        y = single(place, SPEED_FIELD, "cnpcgeckoaddon.npc_ranged.cnpc_speed", y,
                ranged.getSpeed(), MIN_SPEED, MAX_SPEED, 10);
        y = single(place, ACCURACY_FIELD, "cnpcgeckoaddon.npc_ranged.cnpc_accuracy", y,
                ranged.getAccuracy(), 0, MAX_ACCURACY, 90);
        y = single(place, SIZE_FIELD, "cnpcgeckoaddon.npc_ranged.cnpc_size", y,
                ranged.getSize(), MIN_SIZE, MAX_SIZE, 10);
        // Below the eyes by default, and a numbers-only field refuses the minus sign that says so.
        if (place) {
            rowLabel(MUZZLE_FIELD, "cnpcgeckoaddon.ranged_extras.muzzle", y, PAIR_SECOND_X);
            addTextField(coordinateField(MUZZLE_FIELD, guiLeft + PAIR_SECOND_X, guiTop + y, PAIR_WIDTH,
                    extra.getMuzzleHeightTenths()));
        }
        y += ROW;
        y = toggle(place, GRAVITY_BUTTON, "cnpcgeckoaddon.npc_ranged.cnpc_gravity", y, ranged.getHasGravity());
        y = toggle(place, ACCELERATE_BUTTON, "cnpcgeckoaddon.npc_ranged.cnpc_accelerate", y, ranged.getAccelerate());
        y = toggle(place, STICKS_BUTTON, "cnpcgeckoaddon.npc_ranged.cnpc_sticks", y, ranged.getSticks());
        y = toggle(place, GLOWS_BUTTON, "cnpcgeckoaddon.npc_ranged.cnpc_glows", y, ranged.getGlows());
        y = toggle(place, SPINS_BUTTON, "cnpcgeckoaddon.npc_ranged.cnpc_spins", y, ranged.getSpins());
        y = toggle(place, RENDER_3D_BUTTON, "cnpcgeckoaddon.npc_ranged.cnpc_render3d", y, ranged.getRender3D());
        y = choice(place, EXPLODE_BUTTON, "cnpcgeckoaddon.npc_ranged.cnpc_explode", y, EXPLODE_LABELS,
                Math.floorMod(ranged.getExplodeSize(), EXPLODE_LABELS.length));
        if (place) {
            rowLabel(EFFECT_BUTTON, "cnpcgeckoaddon.npc_ranged.cnpc_effect", y, CHOICE_X);
            // Two-way, the way CustomNPCs' own screen walks the same list: thirty-four entries
            // are too many to click through one way round.
            addButton(new GuiButtonBiDirectional(this, EFFECT_BUTTON, guiLeft + CHOICE_X, guiTop + y,
                    CHOICE_WIDTH, CONTROL_HEIGHT, effectNames(), effectIndex(ranged.getEffectType())));
        }
        y += ROW;
        y = single(place, EFFECT_TIME_FIELD, "cnpcgeckoaddon.npc_ranged.cnpc_effect_time", y,
                ranged.getEffectTime(), 1, MAX_EFFECT_TIME, 5);
        y = choice(place, EFFECT_STRENGTH_BUTTON, "cnpcgeckoaddon.npc_ranged.cnpc_effect_strength", y,
                EFFECT_STRENGTH_LABELS, Math.floorMod(ranged.getEffectStrength(), EFFECT_STRENGTH_LABELS.length));
        return y;
    }

    private int firePage(boolean place, int y) {
        y = single(place, STRENGTH_FIELD, "cnpcgeckoaddon.npc_ranged.cnpc_strength", y,
                ranged.getStrength(), 0, MAX_STRENGTH, 5);
        y = single(place, KNOCKBACK_FIELD, "cnpcgeckoaddon.npc_ranged.cnpc_knockback", y,
                ranged.getKnockback(), 0, MAX_KNOCKBACK, 0);
        y = single(place, RANGE_FIELD, "cnpcgeckoaddon.npc_ranged.cnpc_range", y,
                ranged.getRange(), MIN_RANGE, MAX_RANGE, 2);
        y = pair(place, DELAY_MIN_FIELD, DELAY_MAX_FIELD, "cnpcgeckoaddon.npc_ranged.cnpc_delay", y,
                ranged.getDelayMin(), MIN_DELAY, MAX_DELAY, 20, ranged.getDelayMax(), MIN_DELAY, MAX_DELAY, 20);
        y = single(place, SHOT_COUNT_FIELD, "cnpcgeckoaddon.npc_ranged.cnpc_shot_count", y,
                ranged.getShotCount(), RangedExtraData.MIN_SHOT_COUNT, RangedExtraData.MAX_SHOT_COUNT, 1);
        y = pair(place, CNPC_BURST_FIELD, CNPC_BURST_DELAY_FIELD, "cnpcgeckoaddon.npc_ranged.cnpc_burst", y,
                ranged.getBurst(), MIN_CNPC_BURST, MAX_CNPC_BURST, 20,
                ranged.getBurstDelay(), 0, MAX_CNPC_BURST_DELAY, 0);
        y = hint(place, "cnpcgeckoaddon.npc_ranged.cnpc_burst_hint", y);
        y = choice(place, FIRE_TYPE_BUTTON, "cnpcgeckoaddon.npc_ranged.cnpc_fire_type", y, FIRE_TYPE_LABELS,
                Math.floorMod(ranged.getFireType(), FIRE_TYPE_LABELS.length));
        y = hint(place, "cnpcgeckoaddon.npc_ranged.cnpc_fire_type_hint", y);
        y = toggle(place, AIM_ANIMATION_BUTTON, "cnpcgeckoaddon.npc_ranged.cnpc_aim_animation", y,
                ranged.getHasAimAnimation());
        y = single(place, MELEE_RANGE_FIELD, "cnpcgeckoaddon.npc_ranged.cnpc_melee_range", y,
                ranged.getMeleeRange(), 0, maxMeleeRange(), 5);
        y = hint(place, "cnpcgeckoaddon.npc_ranged.cnpc_melee_range_hint", y);
        return y;
    }

    private int distancePage(boolean place, int y) {
        y = toggle(place, ADDON_ENABLED_BUTTON, "cnpcgeckoaddon.npc_ranged.addon_enabled", y,
                extra.isRangedAddonEnabled());
        y = hint(place, "cnpcgeckoaddon.npc_ranged.addon_enabled_hint", y);
        y = pair(place, ENGAGE_MIN_FIELD, ENGAGE_MAX_FIELD, "cnpcgeckoaddon.npc_ranged.engage", y,
                extra.getEngageMinTenths(), 0, RangedExtraData.MAX_ENGAGE_TENTHS, 0,
                extra.getEngageMaxTenths(), 0, RangedExtraData.MAX_ENGAGE_TENTHS, 0);
        y = hint(place, "cnpcgeckoaddon.npc_ranged.engage_hint", y);
        y = choice(place, TOO_FAR_BUTTON, "cnpcgeckoaddon.npc_ranged.too_far", y, TOO_FAR_LABELS,
                extra.getTooFarMode());
        y = choice(place, TOO_CLOSE_BUTTON, "cnpcgeckoaddon.npc_ranged.too_close", y, TOO_CLOSE_LABELS,
                extra.getTooCloseMode());
        y = hint(place, "cnpcgeckoaddon.npc_ranged.too_close_hint", y);
        y = single(place, KEEP_DISTANCE_FIELD, "cnpcgeckoaddon.ranged_extras.keep_distance", y,
                extra.getKeepDistance(), 0, RangedExtraData.MAX_KEEP_DISTANCE, 0);
        y = hint(place, "cnpcgeckoaddon.npc_ranged.keep_distance_hint", y);
        return y;
    }

    private int burstPage(boolean place, int y) {
        y = single(place, LEAD_FIELD, "cnpcgeckoaddon.npc_ranged.lead", y,
                extra.getLeadPercent(), 0, RangedExtraData.MAX_LEAD_PERCENT, 0);
        y = hint(place, "cnpcgeckoaddon.npc_ranged.lead_hint", y);
        y = pair(place, BURST_SHOTS_FIELD, BURST_DELAY_FIELD, "cnpcgeckoaddon.npc_ranged.burst", y,
                extra.getBurstShots(), 0, RangedExtraData.MAX_BURST_SHOTS, 0,
                extra.getBurstDelayTicks(), RangedExtraData.MIN_BURST_DELAY_TICKS,
                RangedExtraData.MAX_BURST_DELAY_TICKS, RangedExtraData.DEFAULT_BURST_DELAY_TICKS);
        y = hint(place, "cnpcgeckoaddon.npc_ranged.burst_hint", y);
        y = single(place, SPREAD_FIELD, "cnpcgeckoaddon.npc_ranged.spread", y,
                extra.getSpreadDegrees(), 0, RangedExtraData.MAX_SPREAD_DEGREES, 0);
        y = hint(place, "cnpcgeckoaddon.npc_ranged.spread_hint", y);
        y = single(place, RELOAD_FIELD, "cnpcgeckoaddon.npc_ranged.reload", y,
                extra.getReloadTicks(), 0, RangedExtraData.MAX_RELOAD_TICKS, 0);
        // The animation on its own row under the number the label names with it: an id and its
        // picker do not fit beside a number in the panel's width.
        if (place) {
            addTextField(new GuiTextFieldNop(RELOAD_ANIMATION_FIELD, this, guiLeft + SELECT_FIELD_X, guiTop + y,
                    SELECT_FIELD_WIDTH, CONTROL_HEIGHT, extra.getReloadAnimation()));
            addButton(new GuiButtonNop(this, RELOAD_ANIMATION_FIELD, guiLeft + SELECT_BUTTON_X, guiTop + y,
                    SELECT_BUTTON_WIDTH, CONTROL_HEIGHT, "mco.template.button.select"));
        }
        y += ROW;
        y = hint(place, "cnpcgeckoaddon.npc_ranged.reload_hint", y);
        return y;
    }

    private int sightPage(boolean place, int y) {
        y = choice(place, LOS_BUTTON, "cnpcgeckoaddon.npc_ranged.los", y, LOS_LABELS, extra.getLosMode());
        y = hint(place, "cnpcgeckoaddon.npc_ranged.los_hint", y);
        y = pair(place, LOB_WARN_FIELD, LOB_WARN_RADIUS_FIELD, "cnpcgeckoaddon.npc_ranged.lob_warn", y,
                extra.getLobWarnTicks(), 0, RangedExtraData.MAX_LOB_WARN_TICKS, RangedExtraData.DEFAULT_LOB_WARN_TICKS,
                extra.getLobWarnRadiusTenths(), RangedExtraData.MIN_LOB_WARN_RADIUS_TENTHS,
                RangedExtraData.MAX_LOB_WARN_RADIUS_TENTHS, RangedExtraData.DEFAULT_LOB_WARN_RADIUS_TENTHS);
        y = hint(place, "cnpcgeckoaddon.npc_ranged.lob_warn_hint", y);
        if (place) {
            addCueButton(LOB_WARN_CUE_BUTTON, "cnpcgeckoaddon.npc_ranged.cue_lob_warn", guiTop + y,
                    extra.getLobWarnParticles());
        }
        y += ROW;
        return y;
    }

    private int soundsPage(boolean place, int y) {
        if (place) {
            addLabel(new GuiLabel(FIRE_SOUND_FIELD + 200, "cnpcgeckoaddon.npc_ranged.cnpc_sounds",
                    guiLeft + LABEL_X, guiTop + y + LABEL_DROP));
        }
        y += ROW;
        y = sound(place, FIRE_SOUND_FIELD, "cnpcgeckoaddon.npc_ranged.cnpc_sound_fire", y, 0);
        y = sound(place, HIT_SOUND_FIELD, "cnpcgeckoaddon.npc_ranged.cnpc_sound_hit", y, 1);
        y = sound(place, GROUND_SOUND_FIELD, "cnpcgeckoaddon.npc_ranged.cnpc_sound_ground", y, 2);
        y = single(place, SHOT_VOLUME_FIELD, "cnpcgeckoaddon.ranged_extras.shot_volume", y,
                extra.getShotSoundVolumeTenths(), 0, RangedExtraData.MAX_SHOT_VOLUME,
                RangedExtraData.DEFAULT_SHOT_VOLUME);
        y = single(place, SHOT_PITCH_FIELD, "cnpcgeckoaddon.ranged_extras.shot_pitch", y,
                extra.getShotSoundPitchTenths(), RangedExtraData.MIN_SHOT_PITCH, RangedExtraData.MAX_SHOT_PITCH,
                RangedExtraData.DEFAULT_SHOT_PITCH);
        y = hint(place, "cnpcgeckoaddon.npc_ranged.shot_sound_hint", y);
        if (place) {
            addCueButton(RELOAD_CUE_BUTTON, "cnpcgeckoaddon.npc_ranged.cue_reload", guiTop + y,
                    extra.getReloadSound());
        }
        y += ROW;
        return y;
    }

    /** One of CustomNPCs' three projectile sounds: typed in, or picked from every sound there is. */
    private int sound(boolean place, int id, String key, int y, int slot) {
        if (place) {
            String value = ranged.getSound(slot);
            rowLabel(id, key, y, SELECT_FIELD_X);
            addTextField(new GuiTextFieldNop(id, this, guiLeft + SELECT_FIELD_X, guiTop + y, SELECT_FIELD_WIDTH,
                    CONTROL_HEIGHT, value == null ? "" : value));
            addButton(new GuiButtonNop(this, id, guiLeft + SELECT_BUTTON_X, guiTop + y, SELECT_BUTTON_WIDTH,
                    CONTROL_HEIGHT, "mco.template.button.select"));
        }
        return y + ROW;
    }

    /** An entity id typed by hand or picked off the list of what looks like a projectile. */
    private int entity(boolean place, int id, String key, int y, String value) {
        if (place) {
            rowLabel(id, key, y, SELECT_FIELD_X);
            addTextField(new GuiTextFieldNop(id, this, guiLeft + SELECT_FIELD_X, guiTop + y, SELECT_FIELD_WIDTH,
                    CONTROL_HEIGHT, value));
            addButton(new GuiButtonNop(this, id, guiLeft + SELECT_BUTTON_X, guiTop + y, SELECT_BUTTON_WIDTH,
                    CONTROL_HEIGHT, "mco.template.button.select"));
        }
        return y + ROW;
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

    /** A grey line under a row, wrapped to the panel; each takes its own run of label ids. */
    private int hint(boolean place, String key, int y) {
        if (place) {
            addWrappedHint(hintLabel, key, guiTop + y);
        }
        hintLabel += HINT_LABEL_RUN;
        return y + wrappedHintHeight(key) + HINT_GAP;
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

    /** The npc's own aggro range, which is as far as CustomNPCs lets its melee range go. */
    private int maxMeleeRange() {
        return npc == null || npc.stats == null ? MAX_MELEE_RANGE : Math.max(1, npc.stats.aggroRange);
    }

    /**
     * The list CustomNPCs' own screen walks: none, the thirty-two potions it knows by number,
     * and fire. Built when asked rather than once: the effects are registry entries, and a
     * screen class may be loaded before the registry is ready.
     */
    private static String[] effectNames() {
        List<String> names = new ArrayList<>();
        names.add("cnpcgeckoaddon.npc_ranged.cnpc_effect.none");
        for (int i = 1; i <= POTION_COUNT; i++) {
            MobEffect effect = PotionEffectType.getMCType(i);
            names.add(effect == null ? "cnpcgeckoaddon.npc_ranged.cnpc_effect.none" : effect.getDescriptionId());
        }
        names.add("cnpcgeckoaddon.npc_ranged.cnpc_effect.fire");
        return names.toArray(new String[0]);
    }

    /** Where a stored effect sits on that list; fire is stored as 666 and shown last. */
    private static int effectIndex(int effectType) {
        if (effectType == EFFECT_FIRE) {
            return POTION_COUNT + 1;
        }
        return effectType < 0 || effectType > POTION_COUNT ? 0 : effectType;
    }

    /** And what an entry on the list is stored as. */
    private static int effectType(int index) {
        return index == POTION_COUNT + 1 ? EFFECT_FIRE : Math.max(0, Math.min(index, POTION_COUNT));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        switch (button.id) {
            case PAGE_BUTTON -> {
                // Read before the rows change, so a number typed on the old page is not lost.
                applyFields();
                page = button.getValue();
                requestLayout();
            }
            case RESET_BUTTON -> {
                extra.setProjectileEntity("");
                GuiTextFieldNop field = getTextField(PROJECTILE_FIELD);
                if (field != null) {
                    field.setValue("");
                }
            }
            case PROJECTILE_FIELD -> pickEntity(PROJECTILE_FIELD, extra::setProjectileEntity);
            case FALLBACK_FIELD -> pickEntity(FALLBACK_FIELD, extra::setFallbackProjectile);
            case GRAVITY_BUTTON -> ranged.setHasGravity(yes(button));
            case ACCELERATE_BUTTON -> ranged.setAccelerate(yes(button));
            case STICKS_BUTTON -> ranged.setSticks(yes(button));
            case GLOWS_BUTTON -> ranged.setGlows(yes(button));
            case SPINS_BUTTON -> ranged.setSpins(yes(button));
            case RENDER_3D_BUTTON -> ranged.setRender3D(yes(button));
            case EXPLODE_BUTTON -> ranged.setExplodeSize(button.getValue());
            case EFFECT_BUTTON -> ranged.setEffect(effectType(button.getValue()), ranged.getEffectStrength(),
                    ranged.getEffectTime());
            case EFFECT_STRENGTH_BUTTON -> ranged.setEffect(ranged.getEffectType(), button.getValue(),
                    ranged.getEffectTime());
            case FIRE_TYPE_BUTTON -> ranged.setFireType(button.getValue());
            case AIM_ANIMATION_BUTTON -> ranged.setHasAimAnimation(yes(button));
            case ADDON_ENABLED_BUTTON -> extra.setRangedAddonEnabled(yes(button));
            case TOO_FAR_BUTTON -> extra.setTooFarMode(button.getValue());
            case TOO_CLOSE_BUTTON -> extra.setTooCloseMode(button.getValue());
            case LOS_BUTTON -> extra.setLosMode(button.getValue());
            case RELOAD_ANIMATION_FIELD -> setSubGui(new GuiStringSelection(this,
                    "cnpcgeckoaddon.string_picker.reload_animation", BossAnimationGuiUtil.getAnimations(npc), name -> {
                extra.setReloadAnimation(name);
                GuiTextFieldNop field = getTextField(RELOAD_ANIMATION_FIELD);
                if (field != null) {
                    field.setValue(name);
                }
            }));
            case FIRE_SOUND_FIELD -> pickSound(FIRE_SOUND_FIELD, 0);
            case HIT_SOUND_FIELD -> pickSound(HIT_SOUND_FIELD, 1);
            case GROUND_SOUND_FIELD -> pickSound(GROUND_SOUND_FIELD, 2);
            default -> {
            }
        }
    }

    private static boolean yes(GuiButtonNop button) {
        return button instanceof GuiButtonYesNo yesNo && yesNo.getBoolean();
    }

    private void pickEntity(int id, Consumer<String> setter) {
        setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.projectile_entity",
                ProjectileEntityUtil.getSelectableIds(Minecraft.getInstance().level), name -> {
            setter.accept(name);
            GuiTextFieldNop field = getTextField(id);
            if (field != null) {
                field.setValue(name);
            }
        }));
    }

    private void pickSound(int id, int slot) {
        setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.npc_sound",
                BossSoundCue.getSelectableIds(), name -> {
            ranged.setSound(slot, name);
            GuiTextFieldNop field = getTextField(id);
            if (field != null) {
                field.setValue(name);
            }
        }));
    }

    @Override
    protected void applyFields() {
        // Only the open page's fields exist, and a field that is not there is skipped.
        applyEntityField(PROJECTILE_FIELD, extra.getProjectileEntity(), extra::setProjectileEntity);
        // Empty is a setting here rather than the absence of one: it holds the npc's fire.
        applyEntityField(FALLBACK_FIELD, extra.getFallbackProjectile(), extra::setFallbackProjectile);
        applyNumberField(SPEED_FIELD, ranged::setSpeed);
        applyNumberField(ACCURACY_FIELD, ranged::setAccuracy);
        applyNumberField(SIZE_FIELD, ranged::setSize);
        if (getTextField(MUZZLE_FIELD) != null) {
            extra.setMuzzleHeightTenths(signed(MUZZLE_FIELD));
        }
        applyNumberField(EFFECT_TIME_FIELD, time -> ranged.setEffect(ranged.getEffectType(),
                ranged.getEffectStrength(), time));

        applyNumberField(STRENGTH_FIELD, ranged::setStrength);
        applyNumberField(KNOCKBACK_FIELD, ranged::setKnockback);
        applyNumberField(RANGE_FIELD, ranged::setRange);
        GuiTextFieldNop delayMin = getTextField(DELAY_MIN_FIELD);
        GuiTextFieldNop delayMax = getTextField(DELAY_MAX_FIELD);
        if (delayMin != null && delayMax != null) {
            ranged.setDelay(delayMin.getInteger(), delayMax.getInteger());
        }
        applyNumberField(SHOT_COUNT_FIELD, ranged::setShotCount);
        applyNumberField(CNPC_BURST_FIELD, ranged::setBurst);
        applyNumberField(CNPC_BURST_DELAY_FIELD, ranged::setBurstDelay);
        applyMeleeRange();

        applyNumberField(ENGAGE_MIN_FIELD, extra::setEngageMinTenths);
        applyNumberField(ENGAGE_MAX_FIELD, extra::setEngageMaxTenths);
        applyNumberField(KEEP_DISTANCE_FIELD, extra::setKeepDistance);

        applyNumberField(LEAD_FIELD, extra::setLeadPercent);
        applyNumberField(BURST_SHOTS_FIELD, extra::setBurstShots);
        applyNumberField(BURST_DELAY_FIELD, extra::setBurstDelayTicks);
        applyNumberField(SPREAD_FIELD, extra::setSpreadDegrees);
        applyNumberField(RELOAD_FIELD, extra::setReloadTicks);
        applyAnimationField();

        applyNumberField(LOB_WARN_FIELD, extra::setLobWarnTicks);
        applyNumberField(LOB_WARN_RADIUS_FIELD, extra::setLobWarnRadiusTenths);

        applySoundField(FIRE_SOUND_FIELD, 0);
        applySoundField(HIT_SOUND_FIELD, 1);
        applySoundField(GROUND_SOUND_FIELD, 2);
        applyNumberField(SHOT_VOLUME_FIELD, extra::setShotSoundVolumeTenths);
        applyNumberField(SHOT_PITCH_FIELD, extra::setShotSoundPitchTenths);
    }

    /**
     * The melee range only when it changed: CustomNPCs' setter marks the npc's whole goal list
     * for a rebuild, which is not something to do on every field that loses focus.
     */
    private void applyMeleeRange() {
        GuiTextFieldNop field = getTextField(MELEE_RANGE_FIELD);
        if (field != null && field.getInteger() != ranged.getMeleeRange()) {
            ranged.setMeleeRange(field.getInteger());
        }
    }

    /** Takes an id that is empty or looks like a projectile, and puts the old one back otherwise. */
    private void applyEntityField(int id, String current, Consumer<String> setter) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return;
        }
        String value = field.getValue().trim();
        if (value.isEmpty() || ProjectileEntityUtil.isSelectable(value, Minecraft.getInstance().level)) {
            setter.accept(value);
            field.setValue(value);
        } else {
            field.setValue(current);
        }
    }

    /** Kept only when the model has it, or empty; otherwise the field snaps back. */
    private void applyAnimationField() {
        GuiTextFieldNop field = getTextField(RELOAD_ANIMATION_FIELD);
        if (field == null) {
            return;
        }
        String value = field.getValue().trim();
        if (value.isEmpty() || BossAnimationGuiUtil.isValid(npc, value)) {
            extra.setReloadAnimation(value);
        } else {
            field.setValue(extra.getReloadAnimation());
        }
    }

    /** A sound the game knows, or none at all; a typo snaps back to what was there. */
    private void applySoundField(int id, int slot) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return;
        }
        String value = field.getValue().trim();
        if (BossSoundCue.isKnownSound(value)) {
            ranged.setSound(slot, value);
        } else {
            String current = ranged.getSound(slot);
            field.setValue(current == null ? "" : current);
        }
    }
}
