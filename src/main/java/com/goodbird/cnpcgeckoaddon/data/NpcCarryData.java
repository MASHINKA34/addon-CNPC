package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * Per-npc settings that let ordinary players carry this npc, no command and no permission.
 *
 * <p>These live on the npc itself rather than with the boss settings: what players run
 * around a dungeon with is a plain npc, and it has to work with no op online.</p>
 */
public class NpcCarryData {
    public static final int MAX_SLOWNESS_PERCENT = 90;
    public static final int DEFAULT_SLOWNESS_PERCENT = 30;
    public static final int MAX_LEASH_RADIUS = 256;
    /** Tenths of a block per tick: the slowest legal throw still clears the carrier's own feet. */
    public static final int MIN_THROW_SPEED = 3;
    public static final int MAX_THROW_SPEED = 40;
    public static final int DEFAULT_THROW_SPEED = 12;
    public static final int MAX_THROW_DAMAGE = 1000;
    public static final int DEFAULT_THROW_DAMAGE = 20;
    public static final int MAX_THROW_KNOCKBACK = 10;
    public static final int DEFAULT_THROW_KNOCKBACK = 2;
    public static final int MAX_THROW_COOLDOWN_TICKS = 1200;
    public static final int DEFAULT_THROW_COOLDOWN_TICKS = 20;
    /** Tenths of a block in front of the carrier's eyes, before the npc's own width. */
    public static final int MIN_CARRY_DISTANCE = 5;
    public static final int MAX_CARRY_DISTANCE = 60;
    public static final int DEFAULT_CARRY_DISTANCE = 20;
    /** Hundredths of a block under eye level, so the npc is not sat on the crosshair. */
    public static final int MAX_CARRY_DROP = 150;
    public static final int DEFAULT_CARRY_DROP = 35;
    public static final int MIN_PLACE_REACH = 1;
    public static final int MAX_PLACE_REACH = 32;
    public static final int DEFAULT_PLACE_REACH = 6;
    /**
     * The preview ring's two colours as 0xRRGGBB.
     *
     * <p>The ring used to be drawn from floats - 0.35/0.95/0.45 and 0.95/0.25/0.25 - and a hex
     * colour is eight bits a channel, so these are those floats to the nearest step of the
     * scale a builder can actually type. The difference is under half a step and invisible.</p>
     */
    public static final int DEFAULT_PREVIEW_FREE_COLOR = 0x59F273;
    public static final int DEFAULT_PREVIEW_BLOCKED_COLOR = 0xF24040;
    public static final int MAX_COLOR = 0xFFFFFF;
    /** Thousandths of a block per tick per tick, the way the boulder's own gravity is kept. */
    public static final int MIN_THROW_GRAVITY = 5;
    public static final int MAX_THROW_GRAVITY = 300;
    public static final int DEFAULT_THROW_GRAVITY = 50;
    /** Hundredths added to the look's y before it is normalised, so a level throw still lobs. */
    public static final int MAX_THROW_LIFT = 100;
    public static final int DEFAULT_THROW_LIFT = 12;
    public static final int MIN_THROW_MAX_FLIGHT_TICKS = 20;
    public static final int MAX_THROW_MAX_FLIGHT_TICKS = 600;
    public static final int DEFAULT_THROW_MAX_FLIGHT_TICKS = 100;

    private static final String CARRYABLE_KEY = "GeckoNpcCarryable";
    private static final String SNEAK_KEY = "GeckoNpcCarrySneak";
    private static final String ITEM_KEY = "GeckoNpcCarryItem";
    private static final String SLOWNESS_KEY = "GeckoNpcCarrySlow";
    private static final String DROP_ON_DAMAGE_KEY = "GeckoNpcCarryDropOnDamage";
    private static final String INVULNERABLE_KEY = "GeckoNpcCarryInvulnerable";
    private static final String UPDATES_HOME_KEY = "GeckoNpcCarryUpdatesHome";
    private static final String LEASH_KEY = "GeckoNpcCarryLeash";
    private static final String THROWABLE_KEY = "GeckoNpcCarryThrowable";
    private static final String THROW_SPEED_KEY = "GeckoNpcCarryThrowSpeed";
    private static final String THROW_DAMAGE_KEY = "GeckoNpcCarryThrowDamage";
    private static final String THROW_KNOCKBACK_KEY = "GeckoNpcCarryThrowKnockback";
    private static final String THROW_SELF_DAMAGE_KEY = "GeckoNpcCarryThrowSelfDamage";
    private static final String THROW_BOMB_KEY = "GeckoNpcCarryThrowBomb";
    private static final String THROW_COOLDOWN_KEY = "GeckoNpcCarryThrowCooldown";
    private static final String CARRY_DISTANCE_KEY = "GeckoNpcCarryDistance";
    private static final String CARRY_DROP_KEY = "GeckoNpcCarryDrop";
    private static final String PLACE_REACH_KEY = "GeckoNpcCarryPlaceReach";
    private static final String PREVIEW_FREE_KEY = "GeckoNpcCarryPreviewFree";
    private static final String PREVIEW_BLOCKED_KEY = "GeckoNpcCarryPreviewBlocked";
    private static final String THROW_GRAVITY_KEY = "GeckoNpcCarryThrowGravity";
    private static final String THROW_LIFT_KEY = "GeckoNpcCarryThrowLift";
    private static final String THROW_MAX_FLIGHT_KEY = "GeckoNpcCarryThrowMaxFlight";

    private boolean carryable;
    private boolean requireSneak = true;
    private String requiredItem = "";
    private int slownessPercent = DEFAULT_SLOWNESS_PERCENT;
    private boolean dropOnDamage = true;
    private boolean invulnerable;
    private boolean updatesHome;
    private int leashRadius;
    private boolean throwable;
    private int throwSpeed = DEFAULT_THROW_SPEED;
    private int throwDamage = DEFAULT_THROW_DAMAGE;
    private int throwKnockback = DEFAULT_THROW_KNOCKBACK;
    private int throwSelfDamage;
    private boolean throwDiesOnImpact;
    private int throwCooldownTicks = DEFAULT_THROW_COOLDOWN_TICKS;
    private int carryDistanceTenths = DEFAULT_CARRY_DISTANCE;
    private int carryDropHundredths = DEFAULT_CARRY_DROP;
    private int placeReach = DEFAULT_PLACE_REACH;
    private int previewFreeColor = DEFAULT_PREVIEW_FREE_COLOR;
    private int previewBlockedColor = DEFAULT_PREVIEW_BLOCKED_COLOR;
    private int throwGravityThousandths = DEFAULT_THROW_GRAVITY;
    private int throwLiftHundredths = DEFAULT_THROW_LIFT;
    private int throwMaxFlightTicks = DEFAULT_THROW_MAX_FLIGHT_TICKS;

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putBoolean(CARRYABLE_KEY, carryable);
        tag.putBoolean(SNEAK_KEY, requireSneak);
        tag.putString(ITEM_KEY, requiredItem);
        tag.putInt(SLOWNESS_KEY, slownessPercent);
        tag.putBoolean(DROP_ON_DAMAGE_KEY, dropOnDamage);
        tag.putBoolean(INVULNERABLE_KEY, invulnerable);
        tag.putBoolean(UPDATES_HOME_KEY, updatesHome);
        tag.putInt(LEASH_KEY, leashRadius);
        tag.putBoolean(THROWABLE_KEY, throwable);
        tag.putInt(THROW_SPEED_KEY, throwSpeed);
        tag.putInt(THROW_DAMAGE_KEY, throwDamage);
        tag.putInt(THROW_KNOCKBACK_KEY, throwKnockback);
        tag.putInt(THROW_SELF_DAMAGE_KEY, throwSelfDamage);
        tag.putBoolean(THROW_BOMB_KEY, throwDiesOnImpact);
        tag.putInt(THROW_COOLDOWN_KEY, throwCooldownTicks);
        tag.putInt(CARRY_DISTANCE_KEY, carryDistanceTenths);
        tag.putInt(CARRY_DROP_KEY, carryDropHundredths);
        tag.putInt(PLACE_REACH_KEY, placeReach);
        tag.putInt(PREVIEW_FREE_KEY, previewFreeColor);
        tag.putInt(PREVIEW_BLOCKED_KEY, previewBlockedColor);
        tag.putInt(THROW_GRAVITY_KEY, throwGravityThousandths);
        tag.putInt(THROW_LIFT_KEY, throwLiftHundredths);
        tag.putInt(THROW_MAX_FLIGHT_KEY, throwMaxFlightTicks);
        return tag;
    }

    /**
     * Reads the settings back, keeping the defaults for keys an older world never wrote.
     *
     * <p>The two that default to on have to be asked for by name: a missing boolean reads
     * as false, which would quietly turn sneaking and dropping off on every existing npc.</p>
     */
    public void readFromNBT(CompoundTag tag) {
        carryable = tag.getBoolean(CARRYABLE_KEY);
        requireSneak = !tag.contains(SNEAK_KEY) || tag.getBoolean(SNEAK_KEY);
        requiredItem = tag.getString(ITEM_KEY);
        slownessPercent = tag.contains(SLOWNESS_KEY)
                ? Mth.clamp(tag.getInt(SLOWNESS_KEY), 0, MAX_SLOWNESS_PERCENT)
                : DEFAULT_SLOWNESS_PERCENT;
        dropOnDamage = !tag.contains(DROP_ON_DAMAGE_KEY) || tag.getBoolean(DROP_ON_DAMAGE_KEY);
        invulnerable = tag.getBoolean(INVULNERABLE_KEY);
        updatesHome = tag.getBoolean(UPDATES_HOME_KEY);
        leashRadius = Mth.clamp(tag.getInt(LEASH_KEY), 0, MAX_LEASH_RADIUS);
        // A missing boolean reads as false, which for the throw is exactly right: an npc saved
        // before throwing existed cannot be thrown, and carries the way it always did.
        throwable = tag.getBoolean(THROWABLE_KEY);
        throwSpeed = readInt(tag, THROW_SPEED_KEY, DEFAULT_THROW_SPEED, MIN_THROW_SPEED, MAX_THROW_SPEED);
        throwDamage = readInt(tag, THROW_DAMAGE_KEY, DEFAULT_THROW_DAMAGE, 0, MAX_THROW_DAMAGE);
        throwKnockback = readInt(tag, THROW_KNOCKBACK_KEY, DEFAULT_THROW_KNOCKBACK, 0, MAX_THROW_KNOCKBACK);
        throwSelfDamage = readInt(tag, THROW_SELF_DAMAGE_KEY, 0, 0, MAX_THROW_DAMAGE);
        throwDiesOnImpact = tag.getBoolean(THROW_BOMB_KEY);
        throwCooldownTicks = readInt(tag, THROW_COOLDOWN_KEY, DEFAULT_THROW_COOLDOWN_TICKS,
                0, MAX_THROW_COOLDOWN_TICKS);
        carryDistanceTenths = readInt(tag, CARRY_DISTANCE_KEY, DEFAULT_CARRY_DISTANCE,
                MIN_CARRY_DISTANCE, MAX_CARRY_DISTANCE);
        carryDropHundredths = readInt(tag, CARRY_DROP_KEY, DEFAULT_CARRY_DROP, 0, MAX_CARRY_DROP);
        placeReach = readInt(tag, PLACE_REACH_KEY, DEFAULT_PLACE_REACH, MIN_PLACE_REACH, MAX_PLACE_REACH);
        previewFreeColor = readInt(tag, PREVIEW_FREE_KEY, DEFAULT_PREVIEW_FREE_COLOR, 0, MAX_COLOR);
        previewBlockedColor = readInt(tag, PREVIEW_BLOCKED_KEY, DEFAULT_PREVIEW_BLOCKED_COLOR, 0, MAX_COLOR);
        throwGravityThousandths = readInt(tag, THROW_GRAVITY_KEY, DEFAULT_THROW_GRAVITY,
                MIN_THROW_GRAVITY, MAX_THROW_GRAVITY);
        throwLiftHundredths = readInt(tag, THROW_LIFT_KEY, DEFAULT_THROW_LIFT, 0, MAX_THROW_LIFT);
        throwMaxFlightTicks = readInt(tag, THROW_MAX_FLIGHT_KEY, DEFAULT_THROW_MAX_FLIGHT_TICKS,
                MIN_THROW_MAX_FLIGHT_TICKS, MAX_THROW_MAX_FLIGHT_TICKS);
    }

    /** The default for a key an older world never wrote, and the clamp for one it did. */
    private static int readInt(CompoundTag tag, String key, int fallback, int min, int max) {
        return tag.contains(key) ? Mth.clamp(tag.getInt(key), min, max) : fallback;
    }

    public boolean isCarryable() {
        return carryable;
    }

    public void setCarryable(boolean carryable) {
        this.carryable = carryable;
    }

    public boolean isRequireSneak() {
        return requireSneak;
    }

    public void setRequireSneak(boolean requireSneak) {
        this.requireSneak = requireSneak;
    }

    /** Item id the carrier has to be holding, or empty when anything will do. */
    public String getRequiredItem() {
        return requiredItem;
    }

    public void setRequiredItem(String requiredItem) {
        this.requiredItem = requiredItem == null ? "" : requiredItem.trim();
    }

    public int getSlownessPercent() {
        return slownessPercent;
    }

    public void setSlownessPercent(int slownessPercent) {
        this.slownessPercent = Mth.clamp(slownessPercent, 0, MAX_SLOWNESS_PERCENT);
    }

    public boolean isDropOnDamage() {
        return dropOnDamage;
    }

    public void setDropOnDamage(boolean dropOnDamage) {
        this.dropOnDamage = dropOnDamage;
    }

    public boolean isInvulnerable() {
        return invulnerable;
    }

    public void setInvulnerable(boolean invulnerable) {
        this.invulnerable = invulnerable;
    }

    public boolean isUpdatesHome() {
        return updatesHome;
    }

    public void setUpdatesHome(boolean updatesHome) {
        this.updatesHome = updatesHome;
    }

    /** How far from the pickup spot the carrier may get, or 0 for no limit at all. */
    public int getLeashRadius() {
        return leashRadius;
    }

    public void setLeashRadius(int leashRadius) {
        this.leashRadius = Mth.clamp(leashRadius, 0, MAX_LEASH_RADIUS);
    }

    /** Whether a carrier may throw this npc instead of putting it down. */
    public boolean isThrowable() {
        return throwable;
    }

    public void setThrowable(boolean throwable) {
        this.throwable = throwable;
    }

    /** Launch speed in tenths of a block per tick. */
    public int getThrowSpeed() {
        return throwSpeed;
    }

    public void setThrowSpeed(int throwSpeed) {
        this.throwSpeed = Mth.clamp(throwSpeed, MIN_THROW_SPEED, MAX_THROW_SPEED);
    }

    /** What whoever the thrown npc runs into takes. */
    public int getThrowDamage() {
        return throwDamage;
    }

    public void setThrowDamage(int throwDamage) {
        this.throwDamage = Mth.clamp(throwDamage, 0, MAX_THROW_DAMAGE);
    }

    /** How hard the victim is shoved on along the flight. */
    public int getThrowKnockback() {
        return throwKnockback;
    }

    public void setThrowKnockback(int throwKnockback) {
        this.throwKnockback = Mth.clamp(throwKnockback, 0, MAX_THROW_KNOCKBACK);
    }

    /** What the npc itself takes for hitting somebody. */
    public int getThrowSelfDamage() {
        return throwSelfDamage;
    }

    public void setThrowSelfDamage(int throwSelfDamage) {
        this.throwSelfDamage = Mth.clamp(throwSelfDamage, 0, MAX_THROW_DAMAGE);
    }

    /** A bomb: the npc is gone the moment it hits somebody, with no drops and no death scripts. */
    public boolean isThrowDiesOnImpact() {
        return throwDiesOnImpact;
    }

    public void setThrowDiesOnImpact(boolean throwDiesOnImpact) {
        this.throwDiesOnImpact = throwDiesOnImpact;
    }

    /** How long after the pickup the carrier has to hold on before a throw is allowed. */
    public int getThrowCooldownTicks() {
        return throwCooldownTicks;
    }

    public void setThrowCooldownTicks(int throwCooldownTicks) {
        this.throwCooldownTicks = Mth.clamp(throwCooldownTicks, 0, MAX_THROW_COOLDOWN_TICKS);
    }

    /** How far in front of the carrier's eyes the npc floats, in tenths of a block. */
    public int getCarryDistanceTenths() {
        return carryDistanceTenths;
    }

    public void setCarryDistanceTenths(int carryDistanceTenths) {
        this.carryDistanceTenths = Mth.clamp(carryDistanceTenths, MIN_CARRY_DISTANCE, MAX_CARRY_DISTANCE);
    }

    /** How far under eye level it is held, in hundredths of a block. */
    public int getCarryDropHundredths() {
        return carryDropHundredths;
    }

    public void setCarryDropHundredths(int carryDropHundredths) {
        this.carryDropHundredths = Mth.clamp(carryDropHundredths, 0, MAX_CARRY_DROP);
    }

    /** How far the carrier's aim reaches when looking for a spot to put the npc down. */
    public int getPlaceReach() {
        return placeReach;
    }

    public void setPlaceReach(int placeReach) {
        this.placeReach = Mth.clamp(placeReach, MIN_PLACE_REACH, MAX_PLACE_REACH);
    }

    /** The placement ring's colour where the npc fits, as 0xRRGGBB. */
    public int getPreviewFreeColor() {
        return previewFreeColor;
    }

    public void setPreviewFreeColor(int previewFreeColor) {
        this.previewFreeColor = Mth.clamp(previewFreeColor, 0, MAX_COLOR);
    }

    /** And where it does not. */
    public int getPreviewBlockedColor() {
        return previewBlockedColor;
    }

    public void setPreviewBlockedColor(int previewBlockedColor) {
        this.previewBlockedColor = Mth.clamp(previewBlockedColor, 0, MAX_COLOR);
    }

    /** What a thrown npc loses of its upward speed each tick, in thousandths of a block. */
    public int getThrowGravityThousandths() {
        return throwGravityThousandths;
    }

    public void setThrowGravityThousandths(int throwGravityThousandths) {
        this.throwGravityThousandths = Mth.clamp(throwGravityThousandths, MIN_THROW_GRAVITY, MAX_THROW_GRAVITY);
    }

    /** How much a throw aimed level still lobs, in hundredths added to the look. */
    public int getThrowLiftHundredths() {
        return throwLiftHundredths;
    }

    public void setThrowLiftHundredths(int throwLiftHundredths) {
        this.throwLiftHundredths = Mth.clamp(throwLiftHundredths, 0, MAX_THROW_LIFT);
    }

    /** A flight that has met nothing by then is put down where it is. */
    public int getThrowMaxFlightTicks() {
        return throwMaxFlightTicks;
    }

    public void setThrowMaxFlightTicks(int throwMaxFlightTicks) {
        this.throwMaxFlightTicks = Mth.clamp(throwMaxFlightTicks,
                MIN_THROW_MAX_FLIGHT_TICKS, MAX_THROW_MAX_FLIGHT_TICKS);
    }
}
