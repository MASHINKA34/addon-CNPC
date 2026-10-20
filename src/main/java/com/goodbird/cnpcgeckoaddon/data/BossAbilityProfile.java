package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.Locale;
import java.util.function.IntUnaryOperator;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * How one ability takes its turns in one phase: the spread on its cooldown and when it first
 * comes due, the odds it goes at all, how often per phase and how many times in a row, where
 * it stands in the rotation, what has to be true for it to go, which of the enrage's
 * multipliers reach it, how its hits are dealt and whom it may land on, and how it looks and
 * sounds - its warning's colour, style, width and lead, the announcement and its sound, its
 * name, and the animations of its cast and of its end.
 *
 * <p>Every field defaults to what the boss did before profiles existed - a flat cooldown, a
 * whole one before the first cast, a cast whenever the clock says so, no limit, no repeat, one
 * rotation in table order, no conditions, the enrage on everything, the number from the
 * ability's own settings dealt as a plain mob attack, the kinds of target the boss aims every
 * ability at, and the boss-wide warning in the kind's own colour under the kind's own name -
 * so a phase nobody touched fights exactly the old fight, and a save from before profiles
 * reads back as all defaults.</p>
 *
 * <p>The keys go into a compound of the profile's own, which the phase keeps in a list tagged
 * with the ability's kind, and only for a profile that differs from the defaults. Numbers come
 * back through {@link BossSettingValue} like every other boss setting: an absent key is the
 * default, a present one is clamped.</p>
 */
public final class BossAbilityProfile {

    /** The widest spread a cooldown may get either way: ten minutes, the longest cooldown there is. */
    public static final int MAX_COOLDOWN_JITTER = 12000;

    /** A first cast that waits the whole cooldown from the phase's start, which is the old rule. */
    public static final int FIRST_CAST_AFTER_COOLDOWN = -1;

    public static final int MAX_FIRST_CAST_DELAY = 12000;

    /** A chance of nothing would be a switched-off ability nobody can see is off. */
    public static final int MIN_CAST_CHANCE = 1;

    public static final int MAX_CAST_CHANCE = 100;

    /** No limit: the ability goes as often as its clock lets it. */
    public static final int NO_CAST_LIMIT = 0;

    public static final int MAX_CASTS_PER_PHASE = 1000;

    public static final int MAX_REPEATS = 20;

    /** A second between one of a row of casts ending and the next one starting. */
    public static final int DEFAULT_REPEAT_DELAY = 20;

    public static final int MAX_REPEAT_DELAY = 1200;

    public static final int MAX_PRIORITY = 9;

    /** The farthest a distance condition reaches, in blocks; 0 leaves the distance alone. */
    public static final int MAX_DISTANCE = 128;

    public static final int MAX_PLAYERS = 64;

    /** A plain mob attack: armour, enchantments and effects all have their say, as before profiles. */
    public static final int DAMAGE_NORMAL = 0;
    /** Through armour, and nothing else: enchantments and effects still cut it. */
    public static final int DAMAGE_PIERCING = 1;
    /**
     * Through armour, enchantments and effects alike. Never past creative, a spectator or the
     * invulnerability frames: those are the game's rules, not a defence.
     */
    public static final int DAMAGE_TRUE = 2;

    /** The most of the victim's health one share of a hit may come to: all of it. */
    public static final int MAX_DAMAGE_PERCENT = 100;

    public static final int MAX_PARTY_DAMAGE_PERCENT = 100;

    /** Three times the hit at the most, for a party big enough to reach it. */
    public static final int DEFAULT_PARTY_DAMAGE_CAP = 200;

    public static final int MAX_PARTY_DAMAGE_CAP = 1000;

    /** Whom the ability lands on is the boss' own setting, the one every ability shared before profiles. */
    public static final int TARGET_KIND_AS_BOSS = -1;

    /** No colour of the profile's own: the warning is drawn in the kind's colour, as before profiles. */
    public static final int COLOR_OF_KIND = -1;
    /** What {@link #parseColor} hands back for text that is no colour at all. */
    public static final int COLOR_INVALID = -2;
    public static final int MAX_COLOR = 0xFFFFFF;
    /** No style of the profile's own: the boss' style of drawn warnings. */
    public static final String STYLE_AS_BOSS = "";
    /** A line width or a warning lead of the boss' own. */
    public static final int AS_BOSS = -1;
    public static final int MAX_LEAD = TeleportPathData.MAX_TELEGRAPH_LEAD_TICKS;
    /** The action bar announcement and the warning sound: as the boss says, off, or on / its own. */
    public static final int ANNOUNCE_AS_BOSS = -1;
    public static final int ANNOUNCE_OFF = 0;
    public static final int ANNOUNCE_ON = 1;
    public static final int ANNOUNCE_SOUND_AS_BOSS = -1;
    public static final int ANNOUNCE_SOUND_OFF = 0;
    public static final int ANNOUNCE_SOUND_OWN = 1;
    /** Long enough for a name, short enough for the action bar and a status line. */
    public static final int MAX_NAME_LENGTH = 32;

    /**
     * The profile handed out for a kind that has none. Shared, so it is never edited: whoever
     * asks for an ability outside the list gets what an untouched ability would have had.
     */
    private static final BossAbilityProfile DEFAULTS = new BossAbilityProfile();

    private int cooldownJitterTicks;
    private int firstCastDelayTicks = FIRST_CAST_AFTER_COOLDOWN;
    private int castChancePercent = MAX_CAST_CHANCE;
    private int maxCastsPerPhase = NO_CAST_LIMIT;
    private int repeatCasts;
    private int repeatDelayTicks = DEFAULT_REPEAT_DELAY;
    private int priority;
    private int minTargetDistance;
    private int maxTargetDistance;
    private int minPlayers;
    private int healthMinPercent;
    private int healthMaxPercent = 100;
    private boolean rageCooldown = true;
    private boolean rageDamage = true;
    private boolean rageKnockback = true;
    private int damageType = DAMAGE_NORMAL;
    private int damagePercentMax;
    private int damagePercentCurrent;
    private int partyDamagePercent;
    private int partyDamageCapPercent = DEFAULT_PARTY_DAMAGE_CAP;
    private int targetKind = TARGET_KIND_AS_BOSS;
    private int telegraphColor = COLOR_OF_KIND;
    private String telegraphStyle = STYLE_AS_BOSS;
    private int telegraphLineWidth = AS_BOSS;
    private int telegraphLeadTicks = AS_BOSS;
    private int announceMode = ANNOUNCE_AS_BOSS;
    private int announceSoundMode = ANNOUNCE_SOUND_AS_BOSS;
    /** The warning sound of the ability's own, played only while {@link #announceSoundMode} says so. */
    private final BossSoundCue announceSound = new BossSoundCue("minecraft:block.note_block.bell", 0.8F, 0.6F);
    private String displayName = "";
    private String castAnimation = "";
    private String endAnimation = "";

    /** A profile with every field at its default. */
    public BossAbilityProfile() {
    }

    /** The shared all-defaults profile, for a kind that has no profile of its own. Never edit it. */
    public static BossAbilityProfile defaults() {
        return DEFAULTS;
    }

    /**
     * Whether nothing here differs from a fresh profile. Spelled out field by field rather than
     * worked out from the written tag: the rotation asks it for every row on every tick.
     */
    public boolean isDefault() {
        return cooldownJitterTicks == 0
                && firstCastDelayTicks == FIRST_CAST_AFTER_COOLDOWN
                && castChancePercent == MAX_CAST_CHANCE
                && maxCastsPerPhase == NO_CAST_LIMIT
                && repeatCasts == 0
                && repeatDelayTicks == DEFAULT_REPEAT_DELAY
                && priority == 0
                && minTargetDistance == 0
                && maxTargetDistance == 0
                && minPlayers == 0
                && healthMinPercent == 0
                && healthMaxPercent == 100
                && rageCooldown
                && rageDamage
                && rageKnockback
                && damageType == DAMAGE_NORMAL
                && damagePercentMax == 0
                && damagePercentCurrent == 0
                && partyDamagePercent == 0
                && partyDamageCapPercent == DEFAULT_PARTY_DAMAGE_CAP
                && targetKind == TARGET_KIND_AS_BOSS
                && isLookDefault();
    }

    /** Whether the look half is untouched: the boss-wide warning in the kind's colour, under its name. */
    public boolean isLookDefault() {
        return telegraphColor == COLOR_OF_KIND
                && telegraphStyle.isEmpty()
                && telegraphLineWidth == AS_BOSS
                && telegraphLeadTicks == AS_BOSS
                && announceMode == ANNOUNCE_AS_BOSS
                && announceSoundMode == ANNOUNCE_SOUND_AS_BOSS
                && announceSound.isAtDefaults()
                && displayName.isEmpty()
                && castAnimation.isEmpty()
                && endAnimation.isEmpty();
    }

    /** Writes this profile's own keys into {@code tag}; the phase adds the ability's kind beside them. */
    public void writeToNBT(CompoundTag tag) {
        tag.putInt("CooldownJitter", cooldownJitterTicks);
        tag.putInt("FirstCastDelay", firstCastDelayTicks);
        tag.putInt("CastChance", castChancePercent);
        tag.putInt("MaxCasts", maxCastsPerPhase);
        tag.putInt("Repeats", repeatCasts);
        tag.putInt("RepeatDelay", repeatDelayTicks);
        tag.putInt("Priority", priority);
        tag.putInt("MinDistance", minTargetDistance);
        tag.putInt("MaxDistance", maxTargetDistance);
        tag.putInt("MinPlayers", minPlayers);
        tag.putInt("HealthMin", healthMinPercent);
        tag.putInt("HealthMax", healthMaxPercent);
        tag.putBoolean("RageCooldown", rageCooldown);
        tag.putBoolean("RageDamage", rageDamage);
        tag.putBoolean("RageKnockback", rageKnockback);
        tag.putInt("DamageType", damageType);
        tag.putInt("DamagePercentMax", damagePercentMax);
        tag.putInt("DamagePercentCurrent", damagePercentCurrent);
        tag.putInt("PartyDamagePercent", partyDamagePercent);
        tag.putInt("PartyDamageCap", partyDamageCapPercent);
        tag.putInt("TargetKind", targetKind);
        tag.putInt("TelegraphColor", telegraphColor);
        tag.putString("TelegraphStyle", telegraphStyle);
        tag.putInt("TelegraphWidth", telegraphLineWidth);
        tag.putInt("TelegraphLead", telegraphLeadTicks);
        tag.putInt("Announce", announceMode);
        tag.putInt("AnnounceSoundMode", announceSoundMode);
        announceSound.writeToNBT(tag, "AnnounceSound");
        tag.putString("Name", displayName);
        tag.putString("CastAnimation", castAnimation);
        tag.putString("EndAnimation", endAnimation);
    }

    /** Reads every key back, an absent one as its default; reading an empty tag resets the profile. */
    public void readFromNBT(CompoundTag tag) {
        cooldownJitterTicks = value(tag, "CooldownJitter", 0, 0, MAX_COOLDOWN_JITTER);
        firstCastDelayTicks = value(tag, "FirstCastDelay", FIRST_CAST_AFTER_COOLDOWN,
                FIRST_CAST_AFTER_COOLDOWN, MAX_FIRST_CAST_DELAY);
        castChancePercent = value(tag, "CastChance", MAX_CAST_CHANCE, MIN_CAST_CHANCE, MAX_CAST_CHANCE);
        maxCastsPerPhase = value(tag, "MaxCasts", NO_CAST_LIMIT, 0, MAX_CASTS_PER_PHASE);
        repeatCasts = value(tag, "Repeats", 0, 0, MAX_REPEATS);
        repeatDelayTicks = value(tag, "RepeatDelay", DEFAULT_REPEAT_DELAY, 0, MAX_REPEAT_DELAY);
        priority = value(tag, "Priority", 0, 0, MAX_PRIORITY);
        minTargetDistance = value(tag, "MinDistance", 0, 0, MAX_DISTANCE);
        maxTargetDistance = value(tag, "MaxDistance", 0, 0, MAX_DISTANCE);
        minPlayers = value(tag, "MinPlayers", 0, 0, MAX_PLAYERS);
        healthMinPercent = value(tag, "HealthMin", 0, 0, 100);
        // A window whose top is under its bottom is one no health ever sits in: it is closed to
        // the bottom instead, so the ability still goes at exactly that health.
        healthMaxPercent = Math.max(healthMinPercent, value(tag, "HealthMax", 100, 0, 100));
        // Absent is the old behaviour, which is the enrage reaching everything.
        rageCooldown = !tag.contains("RageCooldown") || tag.getBoolean("RageCooldown");
        rageDamage = !tag.contains("RageDamage") || tag.getBoolean("RageDamage");
        rageKnockback = !tag.contains("RageKnockback") || tag.getBoolean("RageKnockback");
        damageType = value(tag, "DamageType", DAMAGE_NORMAL, DAMAGE_NORMAL, DAMAGE_TRUE);
        damagePercentMax = value(tag, "DamagePercentMax", 0, 0, MAX_DAMAGE_PERCENT);
        damagePercentCurrent = value(tag, "DamagePercentCurrent", 0, 0, MAX_DAMAGE_PERCENT);
        partyDamagePercent = value(tag, "PartyDamagePercent", 0, 0, MAX_PARTY_DAMAGE_PERCENT);
        partyDamageCapPercent = value(tag, "PartyDamageCap", DEFAULT_PARTY_DAMAGE_CAP, 0, MAX_PARTY_DAMAGE_CAP);
        targetKind = value(tag, "TargetKind", TARGET_KIND_AS_BOSS, TARGET_KIND_AS_BOSS,
                TeleportPathData.ABILITY_TARGET_ALL);
        // Absent is the boss' own warning in the kind's colour, which is every warning before profiles.
        telegraphColor = value(tag, "TelegraphColor", COLOR_OF_KIND, COLOR_OF_KIND, MAX_COLOR);
        telegraphStyle = normalizeStyle(tag.getString("TelegraphStyle"));
        telegraphLineWidth = lineWidth(value(tag, "TelegraphWidth", AS_BOSS, AS_BOSS,
                TeleportPathData.MAX_TELEGRAPH_LINE_WIDTH));
        telegraphLeadTicks = value(tag, "TelegraphLead", AS_BOSS, AS_BOSS, MAX_LEAD);
        announceMode = value(tag, "Announce", ANNOUNCE_AS_BOSS, ANNOUNCE_AS_BOSS, ANNOUNCE_ON);
        announceSoundMode = value(tag, "AnnounceSoundMode", ANNOUNCE_SOUND_AS_BOSS, ANNOUNCE_SOUND_AS_BOSS,
                ANNOUNCE_SOUND_OWN);
        announceSound.readFromNBT(tag, "AnnounceSound");
        displayName = cleanName(tag.getString("Name"));
        castAnimation = clean(tag.getString("CastAnimation"));
        endAnimation = clean(tag.getString("EndAnimation"));
    }

    /** Makes this profile the same as {@code other}, through the tag, so a field cannot be missed. */
    public void copyFrom(BossAbilityProfile other) {
        CompoundTag tag = new CompoundTag();
        other.writeToNBT(tag);
        readFromNBT(tag);
    }

    /**
     * The cooldown one cast buys: the configured one moved by up to the spread either way, and
     * never under a tick. Draws nothing from {@code random} without a spread, so an untouched
     * profile leaves every other random pick of the fight exactly where it was.
     */
    public int spreadCooldown(int cooldownTicks, RandomSource random) {
        if (cooldownJitterTicks <= 0) {
            return cooldownTicks;
        }
        return Math.max(1, cooldownTicks + random.nextIntBetweenInclusive(-cooldownJitterTicks, cooldownJitterTicks));
    }

    /** A cooldown with the enrage's say in it, or as it stands when the enrage is kept off it. */
    public int cooldownUnderRage(int cooldownTicks, IntUnaryOperator rage) {
        return rageCooldown ? rage.applyAsInt(cooldownTicks) : cooldownTicks;
    }

    /** The same for a number this ability hits for. */
    public int damageUnderRage(int damage, IntUnaryOperator rage) {
        return rageDamage ? rage.applyAsInt(damage) : damage;
    }

    /** The same for a knockback, a launch or a pull. */
    public int knockbackUnderRage(int strength, IntUnaryOperator rage) {
        return rageKnockback ? rage.applyAsInt(strength) : strength;
    }

    public int getCooldownJitterTicks() { return cooldownJitterTicks; }

    public void setCooldownJitterTicks(int value) { cooldownJitterTicks = Mth.clamp(value, 0, MAX_COOLDOWN_JITTER); }

    public int getFirstCastDelayTicks() { return firstCastDelayTicks; }

    public void setFirstCastDelayTicks(int value) {
        firstCastDelayTicks = Mth.clamp(value, FIRST_CAST_AFTER_COOLDOWN, MAX_FIRST_CAST_DELAY);
    }

    public int getCastChancePercent() { return castChancePercent; }

    public void setCastChancePercent(int value) { castChancePercent = Mth.clamp(value, MIN_CAST_CHANCE, MAX_CAST_CHANCE); }

    public int getMaxCastsPerPhase() { return maxCastsPerPhase; }

    public void setMaxCastsPerPhase(int value) { maxCastsPerPhase = Mth.clamp(value, 0, MAX_CASTS_PER_PHASE); }

    public int getRepeatCasts() { return repeatCasts; }

    public void setRepeatCasts(int value) { repeatCasts = Mth.clamp(value, 0, MAX_REPEATS); }

    public int getRepeatDelayTicks() { return repeatDelayTicks; }

    public void setRepeatDelayTicks(int value) { repeatDelayTicks = Mth.clamp(value, 0, MAX_REPEAT_DELAY); }

    public int getPriority() { return priority; }

    public void setPriority(int value) { priority = Mth.clamp(value, 0, MAX_PRIORITY); }

    public int getMinTargetDistance() { return minTargetDistance; }

    public void setMinTargetDistance(int value) { minTargetDistance = Mth.clamp(value, 0, MAX_DISTANCE); }

    public int getMaxTargetDistance() { return maxTargetDistance; }

    public void setMaxTargetDistance(int value) { maxTargetDistance = Mth.clamp(value, 0, MAX_DISTANCE); }

    public int getMinPlayers() { return minPlayers; }

    public void setMinPlayers(int value) { minPlayers = Mth.clamp(value, 0, MAX_PLAYERS); }

    public int getHealthMinPercent() { return healthMinPercent; }

    /** Raises the top of the window with it when the bottom passes it, the way a load does. */
    public void setHealthMinPercent(int value) {
        healthMinPercent = Mth.clamp(value, 0, 100);
        healthMaxPercent = Math.max(healthMaxPercent, healthMinPercent);
    }

    public int getHealthMaxPercent() { return healthMaxPercent; }

    /** Never under the bottom of the window: a top below it is closed to the bottom. */
    public void setHealthMaxPercent(int value) { healthMaxPercent = Math.max(healthMinPercent, Mth.clamp(value, 0, 100)); }

    public boolean isRageCooldown() { return rageCooldown; }

    public void setRageCooldown(boolean value) { rageCooldown = value; }

    public boolean isRageDamage() { return rageDamage; }

    public void setRageDamage(boolean value) { rageDamage = value; }

    public boolean isRageKnockback() { return rageKnockback; }

    public void setRageKnockback(boolean value) { rageKnockback = value; }

    /** One of {@link #DAMAGE_NORMAL}, {@link #DAMAGE_PIERCING} or {@link #DAMAGE_TRUE}. */
    public int getDamageType() { return damageType; }

    public void setDamageType(int value) { damageType = Mth.clamp(value, DAMAGE_NORMAL, DAMAGE_TRUE); }

    /** The share of the victim's maximum health added to every hit, in per cent. */
    public int getDamagePercentMax() { return damagePercentMax; }

    public void setDamagePercentMax(int value) { damagePercentMax = Mth.clamp(value, 0, MAX_DAMAGE_PERCENT); }

    /** The share of the health the victim has left added to every hit, in per cent. */
    public int getDamagePercentCurrent() { return damagePercentCurrent; }

    public void setDamagePercentCurrent(int value) {
        damagePercentCurrent = Mth.clamp(value, 0, MAX_DAMAGE_PERCENT);
    }

    /** How much harder every hit lands for each player in the fight past the first, in per cent. */
    public int getPartyDamagePercent() { return partyDamagePercent; }

    public void setPartyDamagePercent(int value) {
        partyDamagePercent = Mth.clamp(value, 0, MAX_PARTY_DAMAGE_PERCENT);
    }

    /** The most the party may add to a hit, in per cent of it. */
    public int getPartyDamageCapPercent() { return partyDamageCapPercent; }

    public void setPartyDamageCapPercent(int value) {
        partyDamageCapPercent = Mth.clamp(value, 0, MAX_PARTY_DAMAGE_CAP);
    }

    /**
     * {@link #TARGET_KIND_AS_BOSS}, or one of the boss' own kinds of target -
     * {@link TeleportPathData#ABILITY_TARGET_PLAYERS}, {@link TeleportPathData#ABILITY_TARGET_PLAYERS_AND_NPCS}
     * or {@link TeleportPathData#ABILITY_TARGET_ALL} - for this ability alone.
     */
    public int getTargetKind() { return targetKind; }

    public void setTargetKind(int value) {
        targetKind = Mth.clamp(value, TARGET_KIND_AS_BOSS, TeleportPathData.ABILITY_TARGET_ALL);
    }

    /** {@link #COLOR_OF_KIND}, or the packed RGB the ability's warning, dust and name are drawn in. */
    public int getTelegraphColor() { return telegraphColor; }

    /** Anything under nought is the kind's colour; anything past white is white. */
    public void setTelegraphColor(int value) {
        telegraphColor = value < 0 ? COLOR_OF_KIND : Math.min(value, MAX_COLOR);
    }

    /** {@link #STYLE_AS_BOSS}, or one of {@link TelegraphLineStyles} for this ability's warning alone. */
    public String getTelegraphStyle() { return telegraphStyle; }

    public void setTelegraphStyle(String value) { telegraphStyle = normalizeStyle(value); }

    /** {@link #AS_BOSS}, or the width of a drawn band in tenths of a block, the boss' own range. */
    public int getTelegraphLineWidth() { return telegraphLineWidth; }

    public void setTelegraphLineWidth(int value) { telegraphLineWidth = lineWidth(value); }

    /** {@link #AS_BOSS}, or how many ticks the warning goes ahead of the wind-up for this ability. */
    public int getTelegraphLeadTicks() { return telegraphLeadTicks; }

    public void setTelegraphLeadTicks(int value) { telegraphLeadTicks = Mth.clamp(value, AS_BOSS, MAX_LEAD); }

    /** {@link #ANNOUNCE_AS_BOSS}, {@link #ANNOUNCE_OFF} or {@link #ANNOUNCE_ON}. */
    public int getAnnounceMode() { return announceMode; }

    public void setAnnounceMode(int value) { announceMode = Mth.clamp(value, ANNOUNCE_AS_BOSS, ANNOUNCE_ON); }

    /** {@link #ANNOUNCE_SOUND_AS_BOSS}, {@link #ANNOUNCE_SOUND_OFF} or {@link #ANNOUNCE_SOUND_OWN}. */
    public int getAnnounceSoundMode() { return announceSoundMode; }

    public void setAnnounceSoundMode(int value) {
        announceSoundMode = Mth.clamp(value, ANNOUNCE_SOUND_AS_BOSS, ANNOUNCE_SOUND_OWN);
    }

    /** The warning sound of the ability's own; edited in place, the way every cue is. */
    public BossSoundCue getAnnounceSound() { return announceSound; }

    /** The name the fight calls the ability by, or empty for the kind's own. */
    public String getDisplayName() { return displayName; }

    public void setDisplayName(String value) { displayName = cleanName(value); }

    /** The animation played as the ability goes off, after its wind-up; empty for none. */
    public String getCastAnimation() { return castAnimation; }

    public void setCastAnimation(String value) { castAnimation = clean(value); }

    /** The animation played when the ability's lasting effect runs out on its own; empty for none. */
    public String getEndAnimation() { return endAnimation; }

    public void setEndAnimation(String value) { endAnimation = clean(value); }

    /** The colour this ability is drawn in: the profile's own, or {@code kindColor}. */
    public int colorOr(int kindColor) {
        return telegraphColor >= 0 ? telegraphColor : kindColor;
    }

    /** The style its warning is drawn in: the profile's own, or the boss' {@code bossStyle}. */
    public String styleOr(String bossStyle) {
        return telegraphStyle.isEmpty() ? bossStyle : telegraphStyle;
    }

    /** The width of its drawn bands: the profile's own, or the boss' {@code bossWidth}. */
    public int lineWidthOr(int bossWidth) {
        return telegraphLineWidth >= 0 ? telegraphLineWidth : bossWidth;
    }

    /** How far ahead of the wind-up its warning goes: the profile's own lead, or the boss' {@code bossLead}. */
    public int leadOr(int bossLead) {
        return telegraphLeadTicks >= 0 ? telegraphLeadTicks : bossLead;
    }

    /** Whether its name goes into the action bar, the boss' own switch saying so when the profile does not. */
    public boolean announces(boolean bossAnnounces) {
        return announceMode == ANNOUNCE_AS_BOSS ? bossAnnounces : announceMode == ANNOUNCE_ON;
    }

    /**
     * A colour typed as six hex digits, with or without a {@code #} in front: {@link #COLOR_OF_KIND}
     * for nothing typed at all, and {@link #COLOR_INVALID} for anything else that is not one -
     * three digits included, which would read as a colour nobody meant.
     */
    public static int parseColor(String text) {
        String digits = clean(text);
        if (digits.startsWith("#")) {
            digits = digits.substring(1).trim();
        }
        if (digits.isEmpty()) {
            return COLOR_OF_KIND;
        }
        if (digits.length() != 6) {
            return COLOR_INVALID;
        }
        int color = 0;
        for (int i = 0; i < digits.length(); i++) {
            // Digit by digit rather than parseInt, which would take a sign in front as well.
            int digit = Character.digit(digits.charAt(i), 16);
            if (digit < 0) {
                return COLOR_INVALID;
            }
            color = color << 4 | digit;
        }
        return color;
    }

    /** What the colour field shows: six hex digits, or nothing for the kind's colour. */
    public static String formatColor(int color) {
        return color < 0 ? "" : String.format(Locale.ROOT, "%06X", Math.min(color, MAX_COLOR));
    }

    /** {@link #STYLE_AS_BOSS} for nothing or for an id this build does not know; the id itself otherwise. */
    static String normalizeStyle(String value) {
        String id = clean(value);
        if (id.isEmpty()) {
            return STYLE_AS_BOSS;
        }
        // Not TelegraphLineStyles.normalize: that turns an unknown id into particles, which would
        // switch a boss' lines off for this ability rather than leave its warning as the boss draws it.
        return TelegraphLineStyles.get(id).id().equals(id) ? id : STYLE_AS_BOSS;
    }

    /** A width under the boss' narrowest is no width at all, so it is the boss' own. */
    private static int lineWidth(int value) {
        return value < TeleportPathData.MIN_TELEGRAPH_LINE_WIDTH
                ? AS_BOSS : Math.min(value, TeleportPathData.MAX_TELEGRAPH_LINE_WIDTH);
    }

    /** Trimmed, and cut to {@link #MAX_NAME_LENGTH} characters without splitting one in half. */
    static String cleanName(String value) {
        String name = clean(value);
        if (name.codePointCount(0, name.length()) <= MAX_NAME_LENGTH) {
            return name;
        }
        return name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH)).trim();
    }

    /** One line for a log: every field, so a profile that misbehaves can be read back off it. */
    @Override
    public String toString() {
        return "BossAbilityProfile{jitter=" + cooldownJitterTicks + ", firstDelay=" + firstCastDelayTicks
                + ", chance=" + castChancePercent + ", maxCasts=" + maxCastsPerPhase + ", repeats=" + repeatCasts
                + ", repeatDelay=" + repeatDelayTicks + ", priority=" + priority + ", distance="
                + minTargetDistance + ".." + maxTargetDistance + ", minPlayers=" + minPlayers + ", health="
                + healthMinPercent + ".." + healthMaxPercent + ", rage(cooldown=" + rageCooldown + ", damage="
                + rageDamage + ", knockback=" + rageKnockback + "), damageType=" + damageType + ", percent(max="
                + damagePercentMax + ", current=" + damagePercentCurrent + "), party(per=" + partyDamagePercent
                + ", cap=" + partyDamageCapPercent + "), targetKind=" + targetKind + ", look(color="
                + formatColor(telegraphColor) + ", style='" + telegraphStyle + "', width=" + telegraphLineWidth
                + ", lead=" + telegraphLeadTicks + ", announce=" + announceMode + ", sound=" + announceSoundMode
                + (announceSound.isAtDefaults() ? "" : " " + announceSound.getSoundId() + (announceSound.isEnabled()
                ? "" : " off") + " " + announceSound.getVolume() + "/" + announceSound.getPitch())
                + ", name='" + displayName + "', cast='" + castAnimation + "', end='" + endAnimation + "')}";
    }
}
