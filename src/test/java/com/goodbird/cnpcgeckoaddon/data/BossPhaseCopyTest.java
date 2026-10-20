package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Copying one ability, or a whole phase, from another phase of the same boss.
 *
 * <p>A copied ability has to arrive whole - its own settings, its bits in the standing-cast and
 * see-through masks, its hold, its follow-up and its profile - and nothing else of the phase may
 * move with it. Both phases are filled with different values everywhere first, so a field the copy
 * forgot, or one it carried into a neighbour, shows up as a difference rather than as two defaults
 * that happen to agree.</p>
 */
class BossPhaseCopyTest {

    /** What a phase keeps of its own besides its abilities' settings: everything else in its tag is an ability's. */
    private static final Set<String> PHASE_KEYS = Set.of("StartHealthPercent", "AppearanceAnimation",
            "AppearanceLockTicks", "CastRootMask", "FinishMask", "ComboFollowUp", "ComboDelay", "FinishHold",
            "AbilityProfiles");

    @TestFactory
    @DisplayName("a copied ability equals its source, and every other ability of the phase stays as it was")
    Stream<DynamicTest> copiesOneKindAndNothingElse() {
        return IntStream.range(0, BossAbilityKind.COUNT)
                .filter(BossPhaseData::isCopyableAbility)
                .mapToObj(kind -> DynamicTest.dynamicTest(BossAbilityKind.LABELS[kind], () -> {
                    BossPhaseData source = filled(1);
                    BossPhaseData target = filled(2);
                    List<CompoundTag> before = kindStates(target);
                    CompoundTag phaseBefore = phaseOwnState(target);
                    CompoundTag wanted = kindState(source, kind);
                    assertNotEquals(wanted, before.get(kind), "the two phases should differ in this ability to begin with");

                    assertTrue(target.copyAbilityFrom(source, kind));

                    assertEquals(wanted, kindState(target, kind), "the copied ability equals its source");
                    for (int other = 0; other < BossAbilityKind.COUNT; other++) {
                        if (other != kind) {
                            assertEquals(before.get(other), kindState(target, other),
                                    BossAbilityKind.LABELS[other] + " moved with " + BossAbilityKind.LABELS[kind]);
                        }
                    }
                    assertEquals(phaseBefore, phaseOwnState(target), "the threshold, the appearance, the barrier, "
                            + "the immune phase and the hop are not an ability's");
                    assertEquals(kindState(source, kind), wanted, "the source is left as it was");
                }));
    }

    @Test
    @DisplayName("every key of a phase belongs to an ability the copy carries, or to the phase itself")
    void everyPhaseKeyHasAnOwner() {
        BossPhaseData phase = filled(1);
        Set<String> owned = new TreeSet<>(PHASE_KEYS);
        for (int kind = 0; kind < BossAbilityKind.COUNT; kind++) {
            if (BossPhaseData.isCopyableAbility(kind)) {
                owned.addAll(phase.abilitySettingsTag(kind).getAllKeys());
            }
        }
        owned.addAll(notAbilities(phase).getAllKeys());
        Set<String> orphans = new TreeSet<>(phase.writeToNBT().getAllKeys());
        orphans.removeAll(owned);
        assertTrue(orphans.isEmpty(), "these keys of a phase belong to no kind the ability copy knows - a new kind "
                + "needs its line in BossPhaseData.settingsNbt, a new phase field its place in the copy: " + orphans);
    }

    @Test
    @DisplayName("exactly the kinds a phase keeps settings for can be copied")
    void copyableKindsHaveSettings() {
        BossPhaseData phase = new BossPhaseData();
        for (int kind = -1; kind <= BossAbilityKind.COUNT; kind++) {
            assertEquals(BossPhaseData.isCopyableAbility(kind), phase.abilitySettingsTag(kind) != null,
                    "kind " + kind);
        }
        assertFalse(BossPhaseData.isCopyableAbility(BossAbilityKind.BLAST), "the death blast lives on the boss");
        assertTrue(BossPhaseData.isCopyableAbility(BossAbilityKind.HAZARD), "the hazard is a phase's own");
    }

    @Test
    @DisplayName("the blast, a number out of range and a missing source copy nothing")
    void nothingToCopyChangesNothing() {
        BossPhaseData source = filled(1);
        BossPhaseData target = filled(2);
        CompoundTag before = target.writeToNBT();

        assertFalse(target.copyAbilityFrom(source, BossAbilityKind.BLAST));
        assertFalse(target.copyAbilityFrom(source, -1));
        assertFalse(target.copyAbilityFrom(source, BossAbilityKind.COUNT));
        assertFalse(target.copyAbilityFrom(null, BossAbilityKind.AREA));
        assertTrue(target.copyAbilityFrom(target, BossAbilityKind.AREA), "a phase is already its own copy");

        assertEquals(before, target.writeToNBT());
    }

    @Test
    @DisplayName("an untouched profile copied over a changed one leaves the ability on defaults")
    void aDefaultProfileIsCopiedToo() {
        BossPhaseData source = new BossPhaseData();
        BossPhaseData target = filled(2);
        assertFalse(target.profile(BossAbilityKind.MELEE).isDefault());

        target.copyAbilityFrom(source, BossAbilityKind.MELEE);

        assertTrue(target.profile(BossAbilityKind.MELEE).isDefault());
        assertFalse(target.profile(BossAbilityKind.RANGED).isDefault(), "the neighbour keeps its own");
    }

    @Test
    @DisplayName("a chain is copied as it is, onto an ability this phase has switched off")
    void aFollowUpIsCopiedAsItIs() {
        BossPhaseData source = new BossPhaseData();
        source.setComboFollowUp(BossAbilityKind.AREA, BossAbilityKind.MELEE);
        source.setComboDelay(BossAbilityKind.AREA, 40);
        BossPhaseData target = new BossPhaseData();
        target.meleeAttack().setEnabled(false);

        target.copyAbilityFrom(source, BossAbilityKind.AREA);

        assertEquals(BossAbilityKind.MELEE, target.comboFollowUp(BossAbilityKind.AREA));
        assertEquals(40, target.comboDelay(BossAbilityKind.AREA));
        assertFalse(target.meleeAttack().isEnabled(), "the follow-up itself is not switched on by the copy");
    }

    @Test
    @DisplayName("a whole phase copied over another equals it but for the threshold")
    void aWholePhaseKeepsItsThreshold() {
        BossPhaseData source = filled(1);
        source.setStartHealthPercent(80);
        BossPhaseData target = filled(2);
        target.setStartHealthPercent(40);

        target.copyFrom(source);

        CompoundTag wanted = source.writeToNBT();
        wanted.putInt("StartHealthPercent", 40);
        assertEquals(wanted, target.writeToNBT());
        assertEquals(80, source.getStartHealthPercent(), "the source is left as it was");
    }

    @Test
    @DisplayName("a phase copied inside a boss keeps its place on the ladder")
    void aCopiedPhaseKeepsItsWindow() {
        TeleportPathData boss = new TeleportPathData();
        boss.setPhaseCount(3);
        boss.setPhaseThreshold(1, 70);
        boss.setPhaseThreshold(2, 40);
        boss.getPhase(0).copyFrom(filled(1));
        int min = boss.getPhaseThresholdMin(2);
        int max = boss.getPhaseThresholdMax(2);

        boss.getPhase(2).copyFrom(boss.getPhase(0));

        assertEquals(100, boss.getPhase(0).getStartHealthPercent());
        assertEquals(70, boss.getPhase(1).getStartHealthPercent());
        assertEquals(40, boss.getPhase(2).getStartHealthPercent());
        assertEquals(min, boss.getPhaseThresholdMin(2));
        assertEquals(max, boss.getPhaseThresholdMax(2));
        assertEquals(2, boss.resolvePhaseIndex(40));
        assertEquals(boss.getPhase(0).areaAttack().getDamage(), boss.getPhase(2).areaAttack().getDamage());
    }

    @Test
    @DisplayName("copying a phase onto itself changes nothing")
    void aPhaseCopiedOntoItselfStays() {
        BossPhaseData phase = filled(1);
        CompoundTag before = phase.writeToNBT();

        phase.copyFrom(phase);
        phase.copyFrom(null);

        assertEquals(before, phase.writeToNBT());
    }

    /**
     * A phase with a value of its own in every field it saves, different for each seed: every number,
     * switch and list of the defaults shaken through the save format, a point or zone in each list the
     * defaults leave empty, and every kind's masks, chain, hold and profile set by hand.
     */
    private static BossPhaseData filled(int seed) {
        BossPhaseData phase = new BossPhaseData();
        phase.readFromNBT(shake(phase.writeToNBT(), seed));
        for (int i = 0; i < seed; i++) {
            phase.summon().getSpawnPoints().add();
            phase.platform().getZones().add();
            phase.cone().getPoints().add();
            phase.vent().getZones().add();
            phase.rift().getCrystalPoints().add();
        }
        for (int kind = 0; kind < BossAbilityKind.COUNT; kind++) {
            phase.setCastRooted(kind, (kind + seed) % 2 == 0);
            phase.setWaitsForFinish(kind, (kind + seed) % 2 == 1);
            phase.setFinishHoldTicks(kind, 5 * kind + seed);
            phase.setComboFollowUp(kind, (kind + seed) % BossAbilityKind.COUNT);
            phase.setComboDelay(kind, 3 * kind + seed);
            BossAbilityProfile profile = phase.profile(kind);
            profile.setPriority((kind + seed) % (BossAbilityProfile.MAX_PRIORITY + 1));
            profile.setCastChancePercent(40 + 10 * seed + kind % 10);
            profile.setRepeatCasts(seed);
            profile.setDisplayName("probe " + seed + " " + kind);
            profile.setTelegraphColor(0x102030 * seed + kind);
        }
        phase.setStartHealthPercent(seed == 1 ? 80 : 40);
        return phase;
    }

    /** The same tag with every number and switch moved by the seed, the way two builders would have set it. */
    private static CompoundTag shake(CompoundTag tag, int seed) {
        CompoundTag shaken = new CompoundTag();
        for (String key : tag.getAllKeys()) {
            shaken.put(key, shake(key, tag.get(key), seed));
        }
        return shaken;
    }

    private static Tag shake(String key, Tag value, int seed) {
        int hash = key.hashCode();
        if (value instanceof ByteTag) {
            return ByteTag.valueOf(((hash + seed) & 1) == 1);
        }
        if (value instanceof IntTag) {
            return IntTag.valueOf(1 + Math.floorMod(hash + 3 * seed, 9));
        }
        if (value instanceof LongTag number) {
            return LongTag.valueOf(number.getAsLong() ^ (1L << Math.floorMod(hash + seed, 20)));
        }
        if (value instanceof FloatTag number) {
            return FloatTag.valueOf(number.getAsFloat() + 0.25F * seed);
        }
        if (value instanceof DoubleTag number) {
            return DoubleTag.valueOf(number.getAsDouble() + 0.25D * seed);
        }
        if (value instanceof CompoundTag compound) {
            return shake(compound, seed);
        }
        if (value instanceof ListTag list) {
            ListTag shaken = new ListTag();
            for (Tag element : list) {
                shaken.add(shake(key, element, seed));
            }
            return shaken;
        }
        return value.copy();
    }

    /** Everything a phase holds about one kind, in one tag, so two of them compare in one line. */
    private static CompoundTag kindState(BossPhaseData phase, int kind) {
        CompoundTag state = new CompoundTag();
        CompoundTag settings = phase.abilitySettingsTag(kind);
        if (settings != null) {
            state.put("Settings", settings);
        }
        state.putBoolean("Rooted", phase.isCastRooted(kind));
        state.putBoolean("SeenThrough", phase.waitsForFinish(kind));
        state.putInt("Hold", phase.finishHoldTicks(kind));
        state.putInt("FollowUp", phase.comboFollowUp(kind));
        state.putInt("Delay", phase.comboDelay(kind));
        CompoundTag profile = new CompoundTag();
        phase.profile(kind).writeToNBT(profile);
        state.put("Profile", profile);
        return state;
    }

    private static List<CompoundTag> kindStates(BossPhaseData phase) {
        List<CompoundTag> states = new ArrayList<>();
        for (int kind = 0; kind < BossAbilityKind.COUNT; kind++) {
            states.add(kindState(phase, kind));
        }
        return states;
    }

    /** What a phase holds that is no ability's: its threshold and appearance, the barrier, the immune phase, the hop. */
    private static CompoundTag phaseOwnState(BossPhaseData phase) {
        CompoundTag state = notAbilities(phase);
        state.putInt("StartHealthPercent", phase.getStartHealthPercent());
        state.putString("AppearanceAnimation", phase.getAppearanceAnimation());
        state.putInt("AppearanceLockTicks", phase.getAppearanceLockTicks());
        return state;
    }

    /** The settings a phase keeps that are not an ability of the rotation's list. */
    private static CompoundTag notAbilities(BossPhaseData phase) {
        CompoundTag tag = new CompoundTag();
        phase.barrier().writeToNBT(tag);
        phase.invulnerable().writeToNBT(tag);
        phase.teleport().writeToNBT(tag);
        return tag;
    }
}
