package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** Per-NPC settings for Warden-style vibration sensing. */
public class SoundReactionData {
    public static final int MODE_INVESTIGATE = 0;
    public static final int MODE_ATTACK_SOURCE = 1;
    public static final int MODE_ATTACK_ENEMIES = 2;

    private static final String ENABLED_KEY = "GeckoSoundReactionEnabled";
    private static final String RADIUS_KEY = "GeckoSoundReactionRadius";
    private static final String MEMORY_KEY = "GeckoSoundReactionMemory";
    private static final String COOLDOWN_KEY = "GeckoSoundReactionCooldown";
    private static final String MODE_KEY = "GeckoSoundReactionMode";

    private boolean enabled;
    private int radius = 16;
    private int memoryTicks = 100;
    private int cooldownTicks = 20;
    private int mode = MODE_INVESTIGATE;

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putBoolean(ENABLED_KEY, enabled);
        tag.putInt(RADIUS_KEY, radius);
        tag.putInt(MEMORY_KEY, memoryTicks);
        tag.putInt(COOLDOWN_KEY, cooldownTicks);
        tag.putInt(MODE_KEY, mode);
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        enabled = tag.getBoolean(ENABLED_KEY);
        radius = tag.contains(RADIUS_KEY) ? Mth.clamp(tag.getInt(RADIUS_KEY), 1, 16) : 16;
        memoryTicks = tag.contains(MEMORY_KEY) ? Mth.clamp(tag.getInt(MEMORY_KEY), 20, 1200) : 100;
        cooldownTicks = tag.contains(COOLDOWN_KEY) ? Mth.clamp(tag.getInt(COOLDOWN_KEY), 0, 200) : 20;
        mode = tag.contains(MODE_KEY) ? Mth.clamp(tag.getInt(MODE_KEY), MODE_INVESTIGATE, MODE_ATTACK_ENEMIES) : MODE_INVESTIGATE;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = Mth.clamp(radius, 1, 16);
    }

    public int getMemoryTicks() {
        return memoryTicks;
    }

    public void setMemoryTicks(int memoryTicks) {
        this.memoryTicks = Mth.clamp(memoryTicks, 20, 1200);
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public void setCooldownTicks(int cooldownTicks) {
        this.cooldownTicks = Mth.clamp(cooldownTicks, 0, 200);
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = Mth.clamp(mode, MODE_INVESTIGATE, MODE_ATTACK_ENEMIES);
    }
}
