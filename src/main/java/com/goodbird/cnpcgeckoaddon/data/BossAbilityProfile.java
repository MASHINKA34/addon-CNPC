package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.function.IntUnaryOperator;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * How one ability takes its turns in one phase: the spread on its cooldown and when it first
 * comes due, the odds it goes at all, how often per phase and how many times in a row, where
 * it stands in the rotation, what has to be true for it to go, and which of the enrage's
 * multipliers reach it.
 *
 * <p>Every field defaults to what the boss did before profiles existed - a flat cooldown, a
 * whole one before the first cast, a cast whenever the clock says so, no limit, no repeat, one
 * rotation in table order, no conditions and the enrage on everything - so a phase nobody
 * touched fights exactly the old fight, and a save from before profiles reads back as all
 * defaults.</p>
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
                && rageKnockback;
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

    /** One line for a log: every field, so a profile that misbehaves can be read back off it. */
    @Override
    public String toString() {
        return "BossAbilityProfile{jitter=" + cooldownJitterTicks + ", firstDelay=" + firstCastDelayTicks
                + ", chance=" + castChancePercent + ", maxCasts=" + maxCastsPerPhase + ", repeats=" + repeatCasts
                + ", repeatDelay=" + repeatDelayTicks + ", priority=" + priority + ", distance="
                + minTargetDistance + ".." + maxTargetDistance + ", minPlayers=" + minPlayers + ", health="
                + healthMinPercent + ".." + healthMaxPercent + ", rage(cooldown=" + rageCooldown + ", damage="
                + rageDamage + ", knockback=" + rageKnockback + ")}";
    }
}
