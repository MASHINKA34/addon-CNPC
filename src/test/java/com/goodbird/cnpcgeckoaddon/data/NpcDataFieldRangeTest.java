package com.goodbird.cnpcgeckoaddon.data;

import com.goodbird.cnpcgeckoaddon.util.NbtNumericSweep;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same question {@link BossFieldRangeTest} asks of the boss configuration, asked of the
 * settings that live on a plain npc.
 *
 * <p>These were the ones without the sweep, and both holes it found were the same shape: a
 * bound that existed only in the editor. {@code GeckoKeepDistance} was read straight out of
 * the tag and then squared by the goal that uses it, which overflows an int past 46340 and
 * comes back as a radius of one; {@code TransitionLengthTicks} went into GeckoLib's blend
 * length, where a number that never runs out is a model frozen halfway between two poses.
 * Neither throws, and neither is visible anywhere except on somebody's server.</p>
 *
 * <p>Each bound is written out below rather than derived, so this file is the statement of
 * what these numbers are allowed to be. A field added to one of these classes and left out
 * of the table fails the last test here rather than quietly joining the unchecked ones.</p>
 */
class NpcDataFieldRangeTest {

    /** What one settings class is: how to build it, save it and load it back. */
    private record Host(String name, Supplier<Object> create,
                        Function<Object, CompoundTag> save, BiConsumer<Object, CompoundTag> load) {
    }

    private record Bound(double min, double max) {
    }

    private static final List<Host> HOSTS = List.of(
            new Host("CustomModelData", CustomModelData::new,
                    host -> ((CustomModelData) host).writeToNBT(new CompoundTag()),
                    (host, tag) -> ((CustomModelData) host).readFromNBT(tag)),
            new Host("RangedExtraData", RangedExtraData::new,
                    host -> ((RangedExtraData) host).writeToNBT(new CompoundTag()),
                    (host, tag) -> ((RangedExtraData) host).readFromNBT(tag)),
            new Host("NpcCarryData", NpcCarryData::new,
                    host -> ((NpcCarryData) host).writeToNBT(new CompoundTag()),
                    (host, tag) -> ((NpcCarryData) host).readFromNBT(tag)),
            new Host("NpcLaunchPadData", NpcLaunchPadData::new,
                    host -> ((NpcLaunchPadData) host).writeToNBT(new CompoundTag()),
                    (host, tag) -> ((NpcLaunchPadData) host).readFromNBT(tag)),
            new Host("SoundReactionData", SoundReactionData::new,
                    host -> ((SoundReactionData) host).writeToNBT(new CompoundTag()),
                    (host, tag) -> ((SoundReactionData) host).readFromNBT(tag)),
            new Host("NpcImmunityData", NpcDataFieldRangeTest::configuredImmunity,
                    host -> ((NpcImmunityData) host).writeToNBT(new CompoundTag()),
                    (host, tag) -> ((NpcImmunityData) host).readFromNBT(tag)));

    /** What each saved number is allowed to come back as, keyed by its own tag key. */
    private static final Map<String, Bound> BOUNDS = bounds();

    private static Map<String, Bound> bounds() {
        Map<String, Bound> bounds = new LinkedHashMap<>();
        bounds.put("Width", new Bound(0.0D, CustomModelData.MAX_HITBOX_SIZE));
        bounds.put("Height", new Bound(0.0D, CustomModelData.MAX_HITBOX_SIZE));
        bounds.put("HitboxScale", new Bound(0.05D, 16.0D));
        bounds.put("TransitionLengthTicks", new Bound(0.0D, CustomModelData.MAX_TRANSITION_LENGTH_TICKS));
        bounds.put("GeckoKeepDistance", new Bound(0.0D, RangedExtraData.MAX_KEEP_DISTANCE));
        bounds.put("GeckoNpcRangedMuzzle",
                new Bound(RangedExtraData.MIN_MUZZLE_HEIGHT, RangedExtraData.MAX_MUZZLE_HEIGHT));
        bounds.put("GeckoNpcRangedShotVolume", new Bound(0.0D, RangedExtraData.MAX_SHOT_VOLUME));
        bounds.put("GeckoNpcRangedShotPitch",
                new Bound(RangedExtraData.MIN_SHOT_PITCH, RangedExtraData.MAX_SHOT_PITCH));
        bounds.put("GeckoNpcRangedEngageMin", new Bound(0.0D, RangedExtraData.MAX_ENGAGE_TENTHS));
        bounds.put("GeckoNpcRangedEngageMax", new Bound(0.0D, RangedExtraData.MAX_ENGAGE_TENTHS));
        bounds.put("GeckoNpcRangedTooFar",
                new Bound(RangedExtraData.TOO_FAR_APPROACH, RangedExtraData.TOO_FAR_WAIT));
        bounds.put("GeckoNpcRangedTooClose",
                new Bound(RangedExtraData.TOO_CLOSE_RETREAT, RangedExtraData.TOO_CLOSE_FIRE));
        bounds.put("GeckoNpcRangedLead", new Bound(0.0D, RangedExtraData.MAX_LEAD_PERCENT));
        bounds.put("GeckoNpcRangedBurstShots", new Bound(0.0D, RangedExtraData.MAX_BURST_SHOTS));
        bounds.put("GeckoNpcRangedBurstDelay",
                new Bound(RangedExtraData.MIN_BURST_DELAY_TICKS, RangedExtraData.MAX_BURST_DELAY_TICKS));
        bounds.put("GeckoNpcRangedSpread", new Bound(0.0D, RangedExtraData.MAX_SPREAD_DEGREES));
        bounds.put("GeckoNpcRangedReload", new Bound(0.0D, RangedExtraData.MAX_RELOAD_TICKS));
        bounds.put("GeckoNpcRangedLos", new Bound(RangedExtraData.LOS_CUSTOMNPCS, RangedExtraData.LOS_LOB));
        bounds.put("GeckoNpcRangedLobWarn", new Bound(0.0D, RangedExtraData.MAX_LOB_WARN_TICKS));
        bounds.put("GeckoNpcRangedLobWarnRadius", new Bound(RangedExtraData.MIN_LOB_WARN_RADIUS_TENTHS,
                RangedExtraData.MAX_LOB_WARN_RADIUS_TENTHS));
        bounds.put("GeckoNpcRangedReloadSoundVolume", new Bound(BossSoundCue.MIN_VOLUME, BossSoundCue.MAX_VOLUME));
        bounds.put("GeckoNpcRangedReloadSoundPitch", new Bound(BossSoundCue.MIN_PITCH, BossSoundCue.MAX_PITCH));
        bounds.put("GeckoNpcRangedLobWarnParticlesCount",
                new Bound(BossParticleCue.MIN_COUNT, BossParticleCue.MAX_COUNT));
        bounds.put("GeckoNpcCarrySlow", new Bound(0.0D, NpcCarryData.MAX_SLOWNESS_PERCENT));
        bounds.put("GeckoNpcCarryLeash", new Bound(0.0D, NpcCarryData.MAX_LEASH_RADIUS));
        bounds.put("GeckoNpcCarryThrowSpeed", new Bound(NpcCarryData.MIN_THROW_SPEED, NpcCarryData.MAX_THROW_SPEED));
        bounds.put("GeckoNpcCarryThrowDamage", new Bound(0.0D, NpcCarryData.MAX_THROW_DAMAGE));
        bounds.put("GeckoNpcCarryThrowKnockback", new Bound(0.0D, NpcCarryData.MAX_THROW_KNOCKBACK));
        bounds.put("GeckoNpcCarryThrowSelfDamage", new Bound(0.0D, NpcCarryData.MAX_THROW_DAMAGE));
        bounds.put("GeckoNpcCarryThrowCooldown", new Bound(0.0D, NpcCarryData.MAX_THROW_COOLDOWN_TICKS));
        bounds.put("GeckoNpcCarryDistance",
                new Bound(NpcCarryData.MIN_CARRY_DISTANCE, NpcCarryData.MAX_CARRY_DISTANCE));
        bounds.put("GeckoNpcCarryDrop", new Bound(0.0D, NpcCarryData.MAX_CARRY_DROP));
        bounds.put("GeckoNpcCarryPlaceReach",
                new Bound(NpcCarryData.MIN_PLACE_REACH, NpcCarryData.MAX_PLACE_REACH));
        bounds.put("GeckoNpcCarryPreviewFree", new Bound(0.0D, NpcCarryData.MAX_COLOR));
        bounds.put("GeckoNpcCarryPreviewBlocked", new Bound(0.0D, NpcCarryData.MAX_COLOR));
        bounds.put("GeckoNpcCarryThrowGravity",
                new Bound(NpcCarryData.MIN_THROW_GRAVITY, NpcCarryData.MAX_THROW_GRAVITY));
        bounds.put("GeckoNpcCarryThrowLift", new Bound(0.0D, NpcCarryData.MAX_THROW_LIFT));
        bounds.put("GeckoNpcCarryThrowMaxFlight", new Bound(NpcCarryData.MIN_THROW_MAX_FLIGHT_TICKS,
                NpcCarryData.MAX_THROW_MAX_FLIGHT_TICKS));
        bounds.put("GeckoNpcLaunchCoordMode", new Bound(NpcLaunchPadData.COORDINATE_NPC_OFFSET,
                NpcLaunchPadData.COORDINATE_ABSOLUTE));
        bounds.put("GeckoNpcLaunchX", new Bound(-NpcLaunchPadData.MAX_COORDINATE, NpcLaunchPadData.MAX_COORDINATE));
        bounds.put("GeckoNpcLaunchY", new Bound(-NpcLaunchPadData.MAX_COORDINATE, NpcLaunchPadData.MAX_COORDINATE));
        bounds.put("GeckoNpcLaunchZ", new Bound(-NpcLaunchPadData.MAX_COORDINATE, NpcLaunchPadData.MAX_COORDINATE));
        bounds.put("GeckoNpcLaunchHeight", new Bound(NpcLaunchPadData.MIN_ARC_HEIGHT, NpcLaunchPadData.MAX_ARC_HEIGHT));
        bounds.put("GeckoNpcLaunchCooldown", new Bound(0.0D, NpcLaunchPadData.MAX_COOLDOWN_TICKS));
        bounds.put("GeckoNpcLaunchLifetime", new Bound(0.0D, NpcLaunchPadData.MAX_LIFETIME_TICKS));
        bounds.put("GeckoNpcLaunchTouchMargin", new Bound(0.0D, NpcLaunchPadData.MAX_TOUCH_MARGIN_TENTHS));
        bounds.put("GeckoNpcLaunchGrace", new Bound(0.0D, NpcLaunchPadData.MAX_LANDING_GRACE_TICKS));
        bounds.put("GeckoNpcLaunchSoundVolume", new Bound(BossSoundCue.MIN_VOLUME, BossSoundCue.MAX_VOLUME));
        bounds.put("GeckoNpcLaunchSoundPitch", new Bound(BossSoundCue.MIN_PITCH, BossSoundCue.MAX_PITCH));
        bounds.put("GeckoNpcLaunchParticlesCount",
                new Bound(BossParticleCue.MIN_COUNT, BossParticleCue.MAX_COUNT));
        bounds.put("GeckoNpcLaunchExpireCount",
                new Bound(BossParticleCue.MIN_COUNT, BossParticleCue.MAX_COUNT));
        bounds.put("GeckoSoundReactionRadius", new Bound(1.0D, 16.0D));
        bounds.put("GeckoSoundReactionMemory", new Bound(20.0D, 1200.0D));
        bounds.put("GeckoSoundReactionCooldown", new Bound(0.0D, 200.0D));
        bounds.put("GeckoSoundReactionMode", new Bound(SoundReactionData.MODE_INVESTIGATE,
                SoundReactionData.MODE_ATTACK_ENEMIES));
        bounds.put("Percent", new Bound(0.0D, NpcDamageResistEntry.PERCENT_MAX));
        return Map.copyOf(bounds);
    }

    /**
     * A bit mask, not a magnitude: every bit past the ability count is simply never read,
     * so there is no range to hold it to.
     */
    private static final Set<String> UNBOUNDED = Set.of("GeckoNpcImmuneAbilities");

    @Test
    @DisplayName("every number on an npc's own settings is clamped on the way back in")
    void everyNumberIsClamped() {
        Set<String> broken = new TreeSet<>();
        for (Host host : HOSTS) {
            CompoundTag baseline = host.save().apply(host.create().get());
            for (String path : NbtNumericSweep.numericPaths(baseline)) {
                String key = NbtNumericSweep.keyOf(path);
                Bound bound = BOUNDS.get(key);
                if (bound == null) {
                    continue;
                }
                for (double extreme : extremes(baseline, path)) {
                    Double back = reload(host, baseline, path, extreme);
                    if (back != null && (back < bound.min() || back > bound.max())) {
                        broken.add(host.name() + "." + path + " kept " + back
                                + " for " + extreme + ", outside [" + bound.min() + ", " + bound.max() + "]");
                    }
                }
            }
        }
        assertTrue(broken.isEmpty(), "these settings take whatever the save file says: " + broken);
    }

    @Test
    @DisplayName("a save full of extremes still loads instead of throwing")
    void anEntirelyPoisonedSaveStillLoads() {
        for (Host host : HOSTS) {
            Object fresh = host.create().get();
            CompoundTag poisoned = host.save().apply(fresh);
            for (String path : NbtNumericSweep.numericPaths(poisoned)) {
                NbtNumericSweep.writeAt(poisoned, path, Float.NaN);
            }
            Object reloaded = host.create().get();
            host.load().accept(reloaded, poisoned);
            CompoundTag round = host.save().apply(reloaded);
            assertTrue(round.size() > 0, host.name() + " wrote nothing back after a poisoned load");
        }
    }

    @Test
    @DisplayName("every number these classes save has a stated range")
    void everyNumberIsAccountedFor() {
        Set<String> unlisted = new TreeSet<>();
        int seen = 0;
        for (Host host : HOSTS) {
            CompoundTag baseline = host.save().apply(host.create().get());
            for (String path : NbtNumericSweep.numericPaths(baseline)) {
                String key = NbtNumericSweep.keyOf(path);
                seen++;
                if (!BOUNDS.containsKey(key) && !UNBOUNDED.contains(key)) {
                    unlisted.add(host.name() + "." + path);
                }
            }
        }
        assertTrue(seen >= BOUNDS.size(), "the sweep found only " + seen + " numbers to check");
        assertTrue(unlisted.isEmpty(),
                "these are saved but have no range stated in this test: " + unlisted);
    }

    /**
     * The fallback projectile is the one setting here that is text with a default, so the
     * sweep above - which only walks numbers - says nothing about it. What a hand-edited save
     * can do to it is a different shape of the same hole: a key of the wrong type reads as an
     * empty string, and for this field empty means "hold your fire", so a mistyped key would
     * quietly disarm the npc instead of falling back to the arrow.
     */
    @Test
    @DisplayName("the fallback projectile survives whatever a hand-edited save holds under its key")
    void theFallbackProjectileSurvivesAHandEditedSave() {
        CompoundTag baseline = new RangedExtraData().writeToNBT(new CompoundTag());
        assertTrue(baseline.contains("GeckoNpcRangedFallback", Tag.TAG_STRING),
                "the fallback projectile is not saved under the key this test poisons");

        CompoundTag missing = baseline.copy();
        missing.remove("GeckoNpcRangedFallback");
        assertEquals(RangedExtraData.DEFAULT_FALLBACK_PROJECTILE, fallbackAfter(missing),
                "a save without the key should read the default");

        CompoundTag mistyped = baseline.copy();
        mistyped.putInt("GeckoNpcRangedFallback", Integer.MAX_VALUE);
        assertEquals(RangedExtraData.DEFAULT_FALLBACK_PROJECTILE, fallbackAfter(mistyped),
                "a number under the key should read the default, not an empty id");

        CompoundTag padded = baseline.copy();
        padded.putString("GeckoNpcRangedFallback", "  minecraft:snowball ");
        assertEquals("minecraft:snowball", fallbackAfter(padded), "a padded id should come back trimmed");

        CompoundTag emptied = baseline.copy();
        emptied.putString("GeckoNpcRangedFallback", "");
        assertEquals("", fallbackAfter(emptied), "an empty id is a setting of its own and should stay empty");
    }

    private static String fallbackAfter(CompoundTag tag) {
        RangedExtraData reloaded = new RangedExtraData();
        reloaded.readFromNBT(tag);
        return reloaded.getFallbackProjectile();
    }

    /**
     * The values worth poisoning a field with: the two ends of an int for every number, and
     * the three a float can hold that no clamp catches on its own.
     */
    private static double[] extremes(CompoundTag baseline, String path) {
        if (!NbtNumericSweep.isFloatingPoint(baseline, path)) {
            return new double[]{Integer.MAX_VALUE, Integer.MIN_VALUE};
        }
        return new double[]{Integer.MAX_VALUE, Integer.MIN_VALUE,
                Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
    }

    /** @return what the field holds after one poisoned value went through a save and a load */
    private static Double reload(Host host, CompoundTag baseline, String path, double value) {
        CompoundTag poisoned = baseline.copy();
        if (!NbtNumericSweep.writeAt(poisoned, path, value)) {
            return null;
        }
        Object reloaded = host.create().get();
        host.load().accept(reloaded, poisoned);
        return NbtNumericSweep.readAt(host.save().apply(reloaded), path);
    }

    /**
     * The immunity data only writes resistance rules that were actually set, so one is set
     * here - otherwise the sweep never reaches the percentage inside them.
     */
    private static NpcImmunityData configuredImmunity() {
        NpcImmunityData data = new NpcImmunityData();
        data.getResist(0).setMatcher("minecraft:arrow");
        return data;
    }
}
