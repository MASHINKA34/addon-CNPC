package com.goodbird.cnpcgeckoaddon.data;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.BossTelegraphUtil;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
 * One puff a boss throws out, as a setting rather than as a literal in the code that throws it.
 *
 * <p>The particle half of {@link BossSoundCue}, with the same three-key save format and the
 * same promise: a boss saved before the cue existed reads back with exactly the particle and
 * the count it always had.</p>
 *
 * <p>{@link #DUST_ID} is not a registered particle: it means "the colour of whatever ability
 * is doing this", which is what the arena marks are drawn in. Only a caller that knows which
 * ability that is - through {@link #emitDust} - can serve it; everywhere else it falls back
 * the way an unknown id does.</p>
 */
public final class BossParticleCue {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    /** Not a registry id: the dust an ability paints the floor with, in its own colour. */
    public static final String DUST_ID = "dust";

    public static final int MIN_COUNT = 0;
    public static final int MAX_COUNT = 200;

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** Whether an id names a simple particle; a field so a test needs no registries. */
    private static Predicate<String> registry = BossParticleCue::registryHolds;

    private final String defaultParticleId;
    private final int defaultCount;

    private boolean enabled = true;
    private String particleId;
    private int count;

    public BossParticleCue(String defaultParticleId, int defaultCount) {
        this.defaultParticleId = clean(defaultParticleId);
        this.defaultCount = Mth.clamp(defaultCount, MIN_COUNT, MAX_COUNT);
        this.particleId = this.defaultParticleId;
        this.count = this.defaultCount;
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean value) { enabled = value; }

    public String getParticleId() { return particleId; }

    public void setParticleId(String value) {
        String cleaned = clean(value);
        particleId = cleaned.isEmpty() ? defaultParticleId : cleaned;
    }

    public int getCount() { return count; }

    public void setCount(int value) { count = Mth.clamp(value, MIN_COUNT, MAX_COUNT); }

    public String getDefaultParticleId() { return defaultParticleId; }

    public int getDefaultCount() { return defaultCount; }

    public void reset() {
        enabled = true;
        particleId = defaultParticleId;
        count = defaultCount;
    }

    /** An independent copy, for a scheduler that has to keep what it was told on the cast. */
    public BossParticleCue copy() {
        BossParticleCue copy = new BossParticleCue(defaultParticleId, defaultCount);
        copy.enabled = enabled;
        copy.particleId = particleId;
        copy.count = count;
        return copy;
    }

    /** The id that will actually be spat out, with one line in the log the first time it is not the set one. */
    public String resolvedId() {
        if (isDust(particleId) || registry.test(particleId)) {
            return particleId;
        }
        if (WARNED.add(particleId)) {
            LOGGER.warn("Boss particle cue: no particle is registered as '{}', using '{}' instead",
                    particleId, defaultParticleId);
        }
        return defaultParticleId;
    }

    /** Throws the puff out, or nothing at all when it is switched off or set to no particles. */
    public void emit(ServerLevel level, double x, double y, double z,
                     double dx, double dy, double dz, double speed) {
        emitDust(level, x, y, z, dx, dy, dz, speed, -1);
    }

    /**
     * The same, from a caller that can serve {@link #DUST_ID}.
     *
     * @param ability which ability's colour the dust is, numbered by {@link BossAbilityKind};
     *                below zero for a caller with no ability to speak of
     */
    public void emitDust(ServerLevel level, double x, double y, double z,
                         double dx, double dy, double dz, double speed, int ability) {
        if (!enabled || level == null || count <= 0) {
            return;
        }
        ParticleOptions options = resolve(ability);
        if (options == null) {
            return;
        }
        level.sendParticles(options, x, y, z, count, dx, dy, dz, speed);
    }

    /**
     * The same again, from a caller whose dust is not an ability's colour.
     *
     * <p>The barrier is drawn in the boss bar's accent rather than in any ability's colour, so
     * {@link #DUST_ID} there means "whatever the shield is being painted in" - which only the
     * caller knows. Handing the dust in is how it stays one cue with one switch and one count
     * rather than a second kind of cue for the one ability that has no colour of its own.</p>
     *
     * @param dust what {@link #DUST_ID} stands for here, or null for no dust at all
     */
    public void emitDust(ServerLevel level, double x, double y, double z,
                         double dx, double dy, double dz, double speed, DustParticleOptions dust) {
        if (!enabled || level == null || count <= 0) {
            return;
        }
        String id = resolvedId();
        ParticleOptions options = isDust(id) ? dust : optionsFor(id, -1);
        if (options == null) {
            return;
        }
        level.sendParticles(options, x, y, z, count, dx, dy, dz, speed);
    }

    public void writeToNBT(CompoundTag tag, String prefix) {
        tag.putBoolean(prefix + "On", enabled);
        tag.putString(prefix + "Particle", particleId);
        tag.putInt(prefix + "Count", count);
    }

    public void readFromNBT(CompoundTag tag, String prefix) {
        enabled = !tag.contains(prefix + "On") || tag.getBoolean(prefix + "On");
        setParticleId(tag.contains(prefix + "Particle")
                ? tag.getString(prefix + "Particle") : defaultParticleId);
        count = value(tag, prefix + "Count", defaultCount, MIN_COUNT, MAX_COUNT);
    }

    /** Whether this id is one the editor may keep: a simple particle, or the ability's own dust. */
    public static boolean isKnownParticle(String id) {
        return isDust(id) || registry.test(id);
    }

    public static boolean isDust(String id) {
        return id != null && DUST_ID.equals(id.trim());
    }

    /** Every particle that takes no extra data, plus the dust, for the picker. */
    public static List<String> getSelectableIds() {
        List<String> ids = new ArrayList<>();
        for (ResourceLocation key : BuiltInRegistries.PARTICLE_TYPE.keySet()) {
            if (BuiltInRegistries.PARTICLE_TYPE.get(key) instanceof SimpleParticleType) {
                ids.add(key.toString());
            }
        }
        Collections.sort(ids);
        ids.add(0, DUST_ID);
        return ids;
    }

    /**
     * What to hand {@code sendParticles} for this cue. Only simple particles are served: a
     * block dust or a vibration needs data no id of its own carries.
     *
     * @param ability the ability whose colour {@link #DUST_ID} means, or below zero for none
     */
    public ParticleOptions resolve(int ability) {
        return optionsFor(resolvedId(), ability);
    }

    private static ParticleOptions optionsFor(String id, int ability) {
        if (isDust(id)) {
            // The caller with no ability behind it has no colour to ask for, so it is nothing.
            return ability < 0 ? null : BossTelegraphUtil.dust(ability);
        }
        ResourceLocation location = ResourceLocation.tryParse(clean(id));
        if (location == null) {
            return null;
        }
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(location);
        return type instanceof SimpleParticleType simple ? simple : null;
    }

    private static boolean registryHolds(String id) {
        ResourceLocation location = ResourceLocation.tryParse(clean(id));
        return location != null && BuiltInRegistries.PARTICLE_TYPE.get(location) instanceof SimpleParticleType;
    }

    /** Swaps in an answer to "does this particle exist", for tests that have no registries. */
    static void useRegistry(Predicate<String> known) {
        registry = known == null ? BossParticleCue::registryHolds : known;
        WARNED.clear();
    }
}
