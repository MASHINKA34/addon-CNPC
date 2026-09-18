package com.goodbird.cnpcgeckoaddon.data;

import com.goodbird.cnpcgeckoaddon.ai.BossFireTracker;
import com.goodbird.cnpcgeckoaddon.ai.BossMinionUtil;
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

/**
 * One effect a boss attack hangs on whoever it hits: a potion by its registry id, or
 * {@link #FIRE_ID}, which sets the victim alight instead.
 */
public final class BossEffectData {
    /**
     * Not a potion: a slot holding this id sets the victim on fire for its duration.
     *
     * <p>An id in the existing slot rather than a setting of its own, so fire rides along on
     * every effect set an ability already carries - projectiles and cocoons included - with no
     * new key in the save.</p>
     */
    public static final String FIRE_ID = "cnpcgeckoaddon:fire";

    /**
     * Vanilla's ceiling on an amplifier: the effect is saved with it as an unsigned byte, and one
     * above it throws on the save - which is the victim's save, and the chunk's.
     */
    public static final int MAX_AMPLIFIER = 255;

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
     * Applies the effect if it is switched on and its id still resolves, or lights the fire
     * when the id is {@link #FIRE_ID} - asked first, since no registry knows that one.
     *
     * <p>Unknown ids are ignored rather than reported: a modpack can lose the mod an effect
     * came from, and a boss that spams the log every swing for it would be worse than one
     * that quietly hits for plain damage.</p>
     */
    public void apply(LivingEntity victim, Entity source) {
        apply(victim, source, 0);
    }

    /**
     * The same with {@code extraLevels} on top of the slot's own level: the stacks a residue
     * has on whoever stands in it. The potion's amplifier and the fire's level both grow by
     * it; the duration is the slot's own either way.
     */
    public void apply(LivingEntity victim, Entity source, int extraLevels) {
        if (!enabled || victim == null) {
            return;
        }
        if (isFire(effectId)) {
            ignite(victim, source, extraLevels);
            return;
        }
        Holder<MobEffect> effect = resolve(effectId);
        if (effect == null) {
            return;
        }
        victim.addEffect(new MobEffectInstance(effect, durationTicks, amplifierWith(extraLevels),
                false, showParticles, showParticles), source);
    }

    /**
     * The amplifier with this many levels on top: held under vanilla's ceiling, and never below
     * the slot's own, since stacks only ever add.
     */
    public int amplifierWith(int extraLevels) {
        // Added as longs: a stack count near the top of an int would otherwise wrap round to a
        // negative sum, and the clamp would take the slot below its own level.
        return (int) Math.min(MAX_AMPLIFIER, (long) amplifier + Math.max(0, extraLevels));
    }

    /** The 1-based level with the extra on top: what the fire burns at. */
    public int levelWith(int extraLevels) {
        return amplifierWith(extraLevels) + 1;
    }

    /**
     * Sets the victim alight the way lava does, the level multiplying the burn.
     *
     * <p>{@code igniteForTicks} only ever lengthens a fire and scales by the victim's burning
     * time, and fire immunity, fire resistance and water keep their vanilla say over the burn.
     * The particles switch is left out: the flames are the effect.</p>
     */
    private void ignite(LivingEntity victim, Entity source, int extraLevels) {
        // The capture's boss half hands its slots to the boss itself, and a stray shot can
        // land on a summon; a boss lighting its own side is never what the slot meant.
        if (victim == source || source != null && BossMinionUtil.isMinionOf(victim, source)) {
            return;
        }
        BossFireTracker.ignite(victim, durationTicks, levelWith(extraLevels));
    }

    /** Whether this id names the fire rather than a potion. */
    public static boolean isFire(String id) {
        return id != null && FIRE_ID.equals(id.trim());
    }

    /** The potion behind an id, or null - always for {@link #FIRE_ID}, which is not a MobEffect. */
    public static Holder<MobEffect> resolve(String id) {
        if (id == null || id.isEmpty() || isFire(id)) {
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
        return isFire(id) || resolve(id) != null;
    }

    /** Every registered effect id for the selection GUI, the fire ahead of them all. */
    public static List<String> getSelectableIds() {
        List<String> ids = new ArrayList<>();
        for (ResourceLocation key : BuiltInRegistries.MOB_EFFECT.keySet()) {
            ids.add(key.toString());
        }
        Collections.sort(ids);
        ids.add(0, FIRE_ID);
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
