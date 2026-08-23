package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One potion effect a boss attack hangs on whoever it hits. */
public final class BossEffectData {
    private boolean enabled;
    private String effectId = "minecraft:poison";
    private int durationTicks = 100;
    /** Stored the vanilla way: 0 is level I. */
    private int amplifier;
    private boolean showParticles = true;

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Enabled", enabled);
        tag.putString("Effect", effectId);
        tag.putInt("Duration", durationTicks);
        tag.putInt("Amplifier", amplifier);
        tag.putBoolean("Particles", showParticles);
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        enabled = tag.getBoolean("Enabled");
        effectId = tag.contains("Effect") ? tag.getString("Effect").trim() : "minecraft:poison";
        durationTicks = tag.contains("Duration") ? Mth.clamp(tag.getInt("Duration"), 1, 72000) : 100;
        amplifier = tag.contains("Amplifier") ? Mth.clamp(tag.getInt("Amplifier"), 0, 9) : 0;
        showParticles = !tag.contains("Particles") || tag.getBoolean("Particles");
    }

    /**
     * Applies the effect if it is switched on and its id still resolves.
     *
     * <p>Unknown ids are ignored rather than reported: a modpack can lose the mod an effect
     * came from, and a boss that spams the log every swing for it would be worse than one
     * that quietly hits for plain damage.</p>
     */
    public void apply(LivingEntity victim, Entity source) {
        if (!enabled || victim == null) {
            return;
        }
        Holder<MobEffect> effect = resolve(effectId);
        if (effect == null) {
            return;
        }
        victim.addEffect(new MobEffectInstance(effect, durationTicks, amplifier,
                false, showParticles, showParticles), source);
    }

    public static Holder<MobEffect> resolve(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(id.trim());
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT
                .getHolder(ResourceKey.create(Registries.MOB_EFFECT, location))
                .map(holder -> (Holder<MobEffect>) holder)
                .orElse(null);
    }

    public static boolean isKnownEffect(String id) {
        return resolve(id) != null;
    }

    /** Every registered effect id, for the selection GUI. */
    public static List<String> getSelectableIds() {
        List<String> ids = new ArrayList<>();
        for (ResourceLocation key : BuiltInRegistries.MOB_EFFECT.keySet()) {
            ids.add(key.toString());
        }
        Collections.sort(ids);
        return ids;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getEffectId() { return effectId; }
    public void setEffectId(String value) { effectId = value == null ? "" : value.trim(); }
    public int getDurationTicks() { return durationTicks; }
    public void setDurationTicks(int value) { durationTicks = Mth.clamp(value, 1, 72000); }
    /** 0-based, as vanilla stores it. */
    public int getAmplifier() { return amplifier; }
    public void setAmplifier(int value) { amplifier = Mth.clamp(value, 0, 9); }
    /** 1-based, as the GUI and the tooltip show it. */
    public int getLevel() { return amplifier + 1; }
    public void setLevel(int level) { setAmplifier(level - 1); }
    public boolean isShowParticles() { return showParticles; }
    public void setShowParticles(boolean value) { showParticles = value; }
}
