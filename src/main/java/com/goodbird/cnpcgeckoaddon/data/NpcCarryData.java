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

    private static final String CARRYABLE_KEY = "GeckoNpcCarryable";
    private static final String SNEAK_KEY = "GeckoNpcCarrySneak";
    private static final String ITEM_KEY = "GeckoNpcCarryItem";
    private static final String SLOWNESS_KEY = "GeckoNpcCarrySlow";
    private static final String DROP_ON_DAMAGE_KEY = "GeckoNpcCarryDropOnDamage";
    private static final String INVULNERABLE_KEY = "GeckoNpcCarryInvulnerable";
    private static final String UPDATES_HOME_KEY = "GeckoNpcCarryUpdatesHome";
    private static final String LEASH_KEY = "GeckoNpcCarryLeash";

    private boolean carryable;
    private boolean requireSneak = true;
    private String requiredItem = "";
    private int slownessPercent = DEFAULT_SLOWNESS_PERCENT;
    private boolean dropOnDamage = true;
    private boolean invulnerable;
    private boolean updatesHome;
    private int leashRadius;

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putBoolean(CARRYABLE_KEY, carryable);
        tag.putBoolean(SNEAK_KEY, requireSneak);
        tag.putString(ITEM_KEY, requiredItem);
        tag.putInt(SLOWNESS_KEY, slownessPercent);
        tag.putBoolean(DROP_ON_DAMAGE_KEY, dropOnDamage);
        tag.putBoolean(INVULNERABLE_KEY, invulnerable);
        tag.putBoolean(UPDATES_HOME_KEY, updatesHome);
        tag.putInt(LEASH_KEY, leashRadius);
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
}
