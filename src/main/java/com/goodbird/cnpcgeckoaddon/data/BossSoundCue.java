package com.goodbird.cnpcgeckoaddon.data;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * One noise a boss makes, as a setting rather than as a literal in the code that makes it.
 *
 * <p>Every {@code playSound} in the addon used to name its own sound, volume and pitch, and a
 * builder who wanted the warning bell to be a horn had nowhere to say so. A cue is that call
 * turned into four settings - on, which sound, how loud, how high - written under whatever
 * prefix its owner gives it, so a boss tag stays one flat block and a boss saved before the
 * cue existed reads back with exactly the sound it always had.</p>
 *
 * <p>Volume and pitch are kept in tenths, the way every other fractional setting in the boss
 * is, so the GUI can edit them as whole numbers.</p>
 */
public final class BossSoundCue {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    public static final int MIN_VOLUME = 0;
    public static final int MAX_VOLUME = 100;
    /** A tenth of the vanilla pitch range, whose ends are 0.5 and 2.0, with room above. */
    public static final int MIN_PITCH = 5;
    public static final int MAX_PITCH = 30;

    /** Ids already complained about, so a modpack missing a sound costs one line, not one per tick. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /**
     * Whether an id names a sound that exists. Held as a field so a test can answer it without
     * booting the registries, which is the only thing here that needs a running game.
     */
    private static Predicate<String> registry = BossSoundCue::registryHolds;

    private final String defaultSoundId;
    private final int defaultVolume;
    private final int defaultPitch;

    private boolean enabled = true;
    private String soundId;
    private int volume;
    private int pitch;

    /**
     * @param defaultSoundId the sound this cue made before it was a setting, as a registry id
     * @param defaultVolume  and how loud it was, as the {@code playSound} call wrote it
     * @param defaultPitch   and how high
     */
    public BossSoundCue(String defaultSoundId, float defaultVolume, float defaultPitch) {
        this.defaultSoundId = clean(defaultSoundId);
        this.defaultVolume = Mth.clamp(Math.round(defaultVolume * 10.0F), MIN_VOLUME, MAX_VOLUME);
        this.defaultPitch = Mth.clamp(Math.round(defaultPitch * 10.0F), MIN_PITCH, MAX_PITCH);
        this.soundId = this.defaultSoundId;
        this.volume = this.defaultVolume;
        this.pitch = this.defaultPitch;
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean value) { enabled = value; }

    public String getSoundId() { return soundId; }

    public void setSoundId(String value) {
        String cleaned = clean(value);
        soundId = cleaned.isEmpty() ? defaultSoundId : cleaned;
    }

    /** Tenths: 8 is the 0.8F the call used to hold. */
    public int getVolume() { return volume; }

    public void setVolume(int value) { volume = Mth.clamp(value, MIN_VOLUME, MAX_VOLUME); }

    public int getPitch() { return pitch; }

    public void setPitch(int value) { pitch = Mth.clamp(value, MIN_PITCH, MAX_PITCH); }

    public String getDefaultSoundId() { return defaultSoundId; }

    public int getDefaultVolume() { return defaultVolume; }

    public int getDefaultPitch() { return defaultPitch; }

    /** Puts the cue back to the call it was made out of. */
    public void reset() {
        enabled = true;
        soundId = defaultSoundId;
        volume = defaultVolume;
        pitch = defaultPitch;
    }

    /** An independent copy, for a scheduler that has to keep what it was told on the cast. */
    public BossSoundCue copy() {
        BossSoundCue copy = new BossSoundCue(defaultSoundId, defaultVolume / 10.0F, defaultPitch / 10.0F);
        copy.enabled = enabled;
        copy.soundId = soundId;
        copy.volume = volume;
        copy.pitch = pitch;
        return copy;
    }

    public float volumeValue() { return volume / 10.0F; }

    public float pitchValue() { return pitch / 10.0F; }

    /**
     * The id that will actually be played: the one set, or the one this cue was born with when
     * the set one names nothing the game knows.
     *
     * <p>A modpack can lose the mod a sound came from, and a boss that goes silent without a
     * word is a boss nobody can debug - so the fallback is loud once per id and quiet after.</p>
     */
    public String resolvedId() {
        if (registry.test(soundId)) {
            return soundId;
        }
        if (WARNED.add(soundId)) {
            LOGGER.warn("Boss sound cue: no sound is registered as '{}', playing '{}' instead",
                    soundId, defaultSoundId);
        }
        return defaultSoundId;
    }

    /** The sound this cue plays, or null when even its own default has gone missing. */
    public SoundEvent resolve() {
        return lookup(resolvedId());
    }

    /** Plays the cue at a spot in the world, or nothing at all when it is switched off. */
    public void play(ServerLevel level, double x, double y, double z, SoundSource source) {
        play(level, x, y, z, source, 0.0F);
    }

    /**
     * The same, with the random spread on the pitch some of these calls have always had.
     *
     * @param pitchSpread tenths of pitch added at random on top, as the old literal did
     */
    public void play(ServerLevel level, double x, double y, double z, SoundSource source,
                     float pitchSpread) {
        if (!enabled || level == null) {
            return;
        }
        SoundEvent sound = resolve();
        if (sound == null) {
            return;
        }
        float played = pitchValue() + (pitchSpread <= 0.0F ? 0.0F : level.getRandom().nextFloat() * pitchSpread);
        level.playSound(null, x, y, z, sound, source, volumeValue(), played);
    }

    public void writeToNBT(CompoundTag tag, String prefix) {
        tag.putBoolean(prefix + "On", enabled);
        tag.putString(prefix + "Sound", soundId);
        tag.putInt(prefix + "Volume", volume);
        tag.putInt(prefix + "Pitch", pitch);
    }

    /** A tag without these keys is a boss saved before the cue existed: it keeps its old noise. */
    public void readFromNBT(CompoundTag tag, String prefix) {
        enabled = !tag.contains(prefix + "On") || tag.getBoolean(prefix + "On");
        setSoundId(tag.contains(prefix + "Sound") ? tag.getString(prefix + "Sound") : defaultSoundId);
        volume = value(tag, prefix + "Volume", defaultVolume, MIN_VOLUME, MAX_VOLUME);
        pitch = value(tag, prefix + "Pitch", defaultPitch, MIN_PITCH, MAX_PITCH);
    }

    /** Whether the game knows this id, for the editor to refuse a typo while it is on screen. */
    public static boolean isKnownSound(String id) {
        return registry.test(id);
    }

    /** Every registered sound id, for the picker. */
    public static List<String> getSelectableIds() {
        List<String> ids = new ArrayList<>();
        for (ResourceLocation key : BuiltInRegistries.SOUND_EVENT.keySet()) {
            ids.add(key.toString());
        }
        Collections.sort(ids);
        return ids;
    }

    private static SoundEvent lookup(String id) {
        ResourceLocation location = ResourceLocation.tryParse(clean(id));
        return location == null ? null : BuiltInRegistries.SOUND_EVENT.get(location);
    }

    private static boolean registryHolds(String id) {
        return lookup(id) != null;
    }

    /**
     * Swaps in an answer to "does this sound exist", for tests that have no registries to ask.
     * Passing null puts the real registry back.
     */
    static void useRegistry(Predicate<String> known) {
        registry = known == null ? BossSoundCue::registryHolds : known;
        WARNED.clear();
    }
}
