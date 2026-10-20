package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-phase profile of each ability: what it saves, what an old save reads back as, and
 * which numbers a tag is allowed to put into it.
 *
 * <p>Every field here is read by the rotation on every tick, and a default that does not read
 * back as a default changes the fight of every boss saved before profiles existed - the one
 * thing this whole feature promised not to do.</p>
 */
class BossAbilityProfileTest {

    private static final String LIST_KEY = "AbilityProfiles";

    @Test
    @DisplayName("a fresh profile is all defaults, and a phase of them saves no list")
    void freshProfilesAreDefaultAndUnsaved() {
        assertTrue(new BossAbilityProfile().isDefault());
        BossPhaseData phase = new BossPhaseData();
        for (int kind = 0; kind < BossAbilityKind.COUNT; kind++) {
            assertTrue(phase.profile(kind).isDefault(), "ability " + kind + " should start with the default profile");
        }
        assertFalse(phase.writeToNBT().contains(LIST_KEY), "an untouched phase must save exactly what it did before");
    }

    @Test
    @DisplayName("changing any one field makes the profile non-default")
    void everyFieldCountsTowardsDefault() throws IllegalAccessException {
        // A field a later change adds to the profile but forgets here would be dropped on save:
        // only non-default profiles are written.
        Set<String> unnoticed = new TreeSet<>();
        for (Field field : BossAbilityProfile.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            BossAbilityProfile profile = new BossAbilityProfile();
            if (field.getType() == int.class) {
                field.setInt(profile, field.getInt(profile) == 3 ? 4 : 3);
            } else if (field.getType() == boolean.class) {
                field.setBoolean(profile, !field.getBoolean(profile));
            } else if (field.getType() == String.class) {
                field.set(profile, "probe".equals(field.get(profile)) ? "other" : "probe");
            } else if (field.getType() == BossSoundCue.class) {
                // A cue is a final object edited in place: its own fields are swept by the cue
                // persistence sweep, and one of them changed is enough to count here.
                BossSoundCue cue = (BossSoundCue) field.get(profile);
                cue.setVolume(cue.getVolume() == 3 ? 4 : 3);
            } else {
                throw new AssertionError("no probe for " + field.getType() + " " + field.getName());
            }
            if (profile.isDefault()) {
                unnoticed.add(field.getName());
            }
        }
        assertTrue(unnoticed.isEmpty(), "isDefault() does not look at: " + unnoticed);
    }

    @Test
    @DisplayName("changed profiles round-trip through the phase tag, each under its own kind")
    void profilesRoundTrip() {
        BossPhaseData phase = new BossPhaseData();
        BossAbilityProfile melee = phase.profile(BossAbilityKind.MELEE);
        melee.setCooldownJitterTicks(100);
        melee.setFirstCastDelayTicks(0);
        melee.setCastChancePercent(25);
        melee.setMaxCastsPerPhase(2);
        melee.setRepeatCasts(2);
        melee.setRepeatDelayTicks(40);
        melee.setPriority(9);
        melee.setMinTargetDistance(1);
        melee.setMaxTargetDistance(5);
        melee.setMinPlayers(2);
        melee.setHealthMinPercent(50);
        melee.setHealthMaxPercent(90);
        melee.setRageCooldown(false);
        melee.setRageDamage(false);
        melee.setRageKnockback(false);
        phase.profile(BossAbilityKind.VENT).setPriority(3);
        phase.profile(BossAbilityKind.HAZARD).setRageDamage(false);

        CompoundTag tag = phase.writeToNBT();
        ListTag list = tag.getList(LIST_KEY, Tag.TAG_COMPOUND);
        assertEquals(3, list.size(), "only the three changed profiles are written");

        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(tag);
        for (int kind = 0; kind < BossAbilityKind.COUNT; kind++) {
            assertEquals(written(phase.profile(kind)), written(reread.profile(kind)),
                    "ability " + kind + " read back a different profile");
        }
        assertEquals(tag, reread.writeToNBT(), "a re-read phase saves the same tag again");
    }

    @Test
    @DisplayName("a tag without the list, or with an empty one, reads back as every ability on defaults")
    void missingListReadsAsDefaults() {
        BossPhaseData phase = new BossPhaseData();
        for (int kind = 0; kind < BossAbilityKind.COUNT; kind++) {
            phase.profile(kind).setPriority(5);
        }
        CompoundTag withoutList = phase.writeToNBT();
        withoutList.remove(LIST_KEY);
        // Read into the phase that had every profile changed, so the load has to reset them
        // rather than merely leave fresh ones alone.
        phase.readFromNBT(withoutList);
        for (int kind = 0; kind < BossAbilityKind.COUNT; kind++) {
            assertTrue(phase.profile(kind).isDefault(), "a save from before profiles left ability " + kind + " changed");
        }

        phase.profile(BossAbilityKind.AREA).setPriority(5);
        CompoundTag emptyList = phase.writeToNBT();
        emptyList.put(LIST_KEY, new ListTag());
        phase.readFromNBT(emptyList);
        assertTrue(phase.profile(BossAbilityKind.AREA).isDefault());
    }

    @Test
    @DisplayName("an entry for an unknown kind is skipped and the known ones still load")
    void unknownKindsAreSkipped() {
        CompoundTag tag = new BossPhaseData().writeToNBT();
        ListTag list = new ListTag();
        list.add(entry(BossAbilityKind.COUNT, 7));
        list.add(entry(-1, 7));
        list.add(entry(Integer.MAX_VALUE, 7));
        CompoundTag noKind = new CompoundTag();
        noKind.putInt("Priority", 7);
        list.add(noKind);
        list.add(entry(BossAbilityKind.LEAP, 4));
        tag.put(LIST_KEY, list);

        BossPhaseData phase = new BossPhaseData();
        phase.readFromNBT(tag);
        assertEquals(4, phase.profile(BossAbilityKind.LEAP).getPriority());
        for (int kind = 0; kind < BossAbilityKind.COUNT; kind++) {
            if (kind != BossAbilityKind.LEAP) {
                assertTrue(phase.profile(kind).isDefault(), "a stray entry landed on ability " + kind);
            }
        }
        assertEquals(1, phase.writeToNBT().getList(LIST_KEY, Tag.TAG_COMPOUND).size(),
                "the skipped entries are not written back");
    }

    @Test
    @DisplayName("every number a tag holds is clamped into its range")
    void numbersAreClamped() {
        CompoundTag high = new CompoundTag();
        for (String key : new String[]{"CooldownJitter", "FirstCastDelay", "CastChance", "MaxCasts", "Repeats",
                "RepeatDelay", "Priority", "MinDistance", "MaxDistance", "MinPlayers", "HealthMin", "HealthMax"}) {
            high.putInt(key, Integer.MAX_VALUE);
        }
        BossAbilityProfile profile = new BossAbilityProfile();
        profile.readFromNBT(high);
        assertEquals(BossAbilityProfile.MAX_COOLDOWN_JITTER, profile.getCooldownJitterTicks());
        assertEquals(BossAbilityProfile.MAX_FIRST_CAST_DELAY, profile.getFirstCastDelayTicks());
        assertEquals(BossAbilityProfile.MAX_CAST_CHANCE, profile.getCastChancePercent());
        assertEquals(BossAbilityProfile.MAX_CASTS_PER_PHASE, profile.getMaxCastsPerPhase());
        assertEquals(BossAbilityProfile.MAX_REPEATS, profile.getRepeatCasts());
        assertEquals(BossAbilityProfile.MAX_REPEAT_DELAY, profile.getRepeatDelayTicks());
        assertEquals(BossAbilityProfile.MAX_PRIORITY, profile.getPriority());
        assertEquals(BossAbilityProfile.MAX_DISTANCE, profile.getMinTargetDistance());
        assertEquals(BossAbilityProfile.MAX_DISTANCE, profile.getMaxTargetDistance());
        assertEquals(BossAbilityProfile.MAX_PLAYERS, profile.getMinPlayers());
        assertEquals(100, profile.getHealthMinPercent());
        assertEquals(100, profile.getHealthMaxPercent());

        CompoundTag low = new CompoundTag();
        for (String key : high.getAllKeys()) {
            low.putInt(key, Integer.MIN_VALUE);
        }
        profile.readFromNBT(low);
        assertEquals(0, profile.getCooldownJitterTicks());
        assertEquals(BossAbilityProfile.FIRST_CAST_AFTER_COOLDOWN, profile.getFirstCastDelayTicks(),
                "the first delay bottoms out at 'after a whole cooldown', not at a negative wait");
        assertEquals(BossAbilityProfile.MIN_CAST_CHANCE, profile.getCastChancePercent(),
                "a chance of nothing would be a switched-off ability nobody can see is off");
        assertEquals(0, profile.getMaxCastsPerPhase());
        assertEquals(0, profile.getRepeatCasts());
        assertEquals(0, profile.getRepeatDelayTicks());
        assertEquals(0, profile.getPriority());
        assertEquals(0, profile.getMinTargetDistance());
        assertEquals(0, profile.getMaxTargetDistance());
        assertEquals(0, profile.getMinPlayers());
        assertEquals(0, profile.getHealthMinPercent());
        assertEquals(0, profile.getHealthMaxPercent());

        // The setters the screen calls clamp the same way.
        profile.setCastChancePercent(0);
        assertEquals(BossAbilityProfile.MIN_CAST_CHANCE, profile.getCastChancePercent());
        profile.setFirstCastDelayTicks(-50);
        assertEquals(BossAbilityProfile.FIRST_CAST_AFTER_COOLDOWN, profile.getFirstCastDelayTicks());
        profile.setPriority(99);
        assertEquals(BossAbilityProfile.MAX_PRIORITY, profile.getPriority());
    }

    @Test
    @DisplayName("a health window whose top is under its bottom is closed to the bottom")
    void healthWindowTopIsClampedToItsBottom() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("HealthMin", 70);
        tag.putInt("HealthMax", 30);
        BossAbilityProfile profile = new BossAbilityProfile();
        profile.readFromNBT(tag);
        assertEquals(70, profile.getHealthMinPercent());
        assertEquals(70, profile.getHealthMaxPercent(), "min > max on load clamps the top to the bottom");

        BossAbilityProfile edited = new BossAbilityProfile();
        edited.setHealthMinPercent(60);
        edited.setHealthMaxPercent(40);
        assertEquals(60, edited.getHealthMaxPercent(), "a top typed under the bottom is raised to it");
        edited.setHealthMaxPercent(80);
        edited.setHealthMinPercent(90);
        assertEquals(90, edited.getHealthMaxPercent(), "a bottom raised past the top takes the top with it");
    }

    @Test
    @DisplayName("an absent rage flag reads as the enrage reaching the ability, as it always did")
    void absentRageFlagsReadAsOn() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Priority", 2);
        BossAbilityProfile profile = new BossAbilityProfile();
        profile.setRageCooldown(false);
        profile.setRageDamage(false);
        profile.setRageKnockback(false);
        profile.readFromNBT(tag);
        assertTrue(profile.isRageCooldown());
        assertTrue(profile.isRageDamage());
        assertTrue(profile.isRageKnockback());
    }

    @Test
    @DisplayName("copyFrom makes a profile the same as another, every field of it")
    void copyFromCopiesEverything() {
        BossAbilityProfile source = new BossAbilityProfile();
        source.setCooldownJitterTicks(11);
        source.setFirstCastDelayTicks(12);
        source.setCastChancePercent(13);
        source.setMaxCastsPerPhase(14);
        source.setRepeatCasts(15);
        source.setRepeatDelayTicks(16);
        source.setPriority(7);
        source.setMinTargetDistance(18);
        source.setMaxTargetDistance(19);
        source.setMinPlayers(20);
        source.setHealthMinPercent(21);
        source.setHealthMaxPercent(22);
        source.setRageCooldown(false);
        source.setRageKnockback(false);
        BossAbilityProfile copy = new BossAbilityProfile();
        copy.copyFrom(source);
        assertEquals(written(source), written(copy));

        copy.copyFrom(new BossAbilityProfile());
        assertTrue(copy.isDefault(), "copying the defaults over a changed profile resets it");
    }

    @Test
    @DisplayName("a kind outside the list gets the shared default profile, never null")
    void outOfRangeKindsGetTheSharedDefault() {
        BossPhaseData phase = new BossPhaseData();
        BossAbilityProfile below = phase.profile(-1);
        BossAbilityProfile above = phase.profile(BossAbilityKind.COUNT);
        assertNotNull(below);
        assertSame(BossAbilityProfile.defaults(), below);
        assertSame(below, above);
        assertTrue(below.isDefault());
    }

    private static CompoundTag entry(int kind, int priority) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("Kind", kind);
        entry.putInt("Priority", priority);
        return entry;
    }

    private static CompoundTag written(BossAbilityProfile profile) {
        CompoundTag tag = new CompoundTag();
        profile.writeToNBT(tag);
        return tag;
    }
}
