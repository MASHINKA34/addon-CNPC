package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down what a saved warning mask means when it is read back.
 *
 * <p>The mask is a bit per ability, and the list of abilities grows. A save holding every bit
 * the mod had at the time was saying "warn for everything" and should keep warning for
 * everything, including whatever was appended since - but "everything" and "everything except
 * the newest kind" are the same number, so a builder who silenced the last kind added used to
 * find it switched back on after a reload. The save now carries a stamp of what could warn
 * when it was written, which is what tells the two apart.</p>
 */
class BossTelegraphMaskMigrationTest {

    /** The stamp written beside the mask; spelled out, because a save on disk holds this name. */
    private static final String KNOWN_KEY = "GeckoBossTelegraphAbilitiesKnown";
    private static final String MASK_KEY = "GeckoBossTelegraphAbilities";

    /** The full mask as it stood before {@code ability} joined it, and the name of that day. */
    private record Era(String name, int ability, int mask) {
    }

    /**
     * Every mask that was once "everything the mod offered", by the ability whose arrival ended
     * it. A save from before the stamp is recognised by matching one of these exactly, so the
     * numbers are the save format: they describe tags already on disk and cannot drift with the
     * expressions that produce them.
     */
    private static final List<Era> HISTORY = List.of(
            new Era("before the line strike", BossAbilityKind.LINE, 255),
            new Era("before the geyser", BossAbilityKind.GEYSER, 511),
            new Era("before the boulder", BossAbilityKind.BOULDER, 1535),
            new Era("before the tether", BossAbilityKind.TETHER, 3583),
            new Era("before the gravity field", BossAbilityKind.GRAVITY, 11775),
            new Era("before the marks", BossAbilityKind.MARK, 28159),
            new Era("before the take cover strike", BossAbilityKind.COVER, 60927),
            new Era("before the hunt", BossAbilityKind.HUNT, 126463),
            new Era("before the sweeping beam", BossAbilityKind.BEAM, 388607),
            new Era("before the cocoon", BossAbilityKind.COCOON, 912895),
            new Era("before the dash", BossAbilityKind.DASH, 1961471),
            new Era("before the cone strike", BossAbilityKind.CONE, 4058623),
            new Era("before the platforms", BossAbilityKind.PLATFORM, 8252927),
            new Era("before the rain of stones", BossAbilityKind.BOULDER_RAIN, 16641535));

    /**
     * What the mask held on the day before {@code ability} joined it.
     *
     * <p>Not a bit range: an ability keeps the bit its kind was given, and the kinds joined the
     * warnings in a different order than they were numbered, so "everything before the platforms"
     * is a number rather than a prefix.</p>
     */
    static int maskBefore(int ability) {
        return HISTORY.stream().filter(era -> era.ability() == ability).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "no saved mask predates ability " + ability)).mask();
    }

    /** A tag as a save written before the stamp existed holds it: the mask alone. */
    static CompoundTag stampless(TeleportPathData data) {
        CompoundTag tag = data.writeToNBT(new CompoundTag());
        tag.remove(KNOWN_KEY);
        return tag;
    }

    private static TeleportPathData configuredBoss() {
        TeleportPathData data = new TeleportPathData();
        data.setEnabled(true);
        data.markConfigured();
        return data;
    }

    private static TeleportPathData reread(CompoundTag tag) {
        TeleportPathData data = new TeleportPathData();
        data.readFromNBT(tag);
        return data;
    }

    /** A save holding {@code saved} under a mod that could warn for {@code known}. */
    private static TeleportPathData rereadStamped(int saved, int known) {
        CompoundTag tag = configuredBoss().writeToNBT(new CompoundTag());
        tag.putInt(MASK_KEY, saved);
        tag.putInt(KNOWN_KEY, known);
        return reread(tag);
    }

    @Test
    @DisplayName("silencing the newest ability survives a reload")
    void theLastAbilitySilencedStaysSilent() {
        TeleportPathData chose = configuredBoss();
        chose.setTelegraphAbility(BossAbilityKind.PLATFORM, false);
        TeleportPathData reloaded = reread(chose.writeToNBT(new CompoundTag()));
        assertFalse(reloaded.isTelegraphAbility(BossAbilityKind.PLATFORM),
                "the mask minus its newest bit is not the same thing as a save that predates it");
        assertTrue(reloaded.isTelegraphAbility(BossAbilityKind.MELEE), "and nothing else was touched");
    }

    @TestFactory
    @DisplayName("a stampless save holding everything the mod had then warns for everything now")
    Stream<DynamicTest> everyOlderFullMaskIsMigrated() {
        return HISTORY.stream().map(era -> DynamicTest.dynamicTest(era.name(), () -> {
            TeleportPathData everything = configuredBoss();
            everything.setTelegraphAbilities(era.mask());
            CompoundTag tag = stampless(everything);
            assertEquals(era.mask(), tag.getInt(MASK_KEY),
                    "the mask " + era.name() + " is part of the save format and cannot change");
            TeleportPathData reloaded = reread(tag);
            assertEquals(TeleportPathData.TELEGRAPH_ALL_ABILITIES, reloaded.getTelegraphAbilities(),
                    "a boss warning for every ability it had was warning for everything");
            assertTrue(reloaded.isTelegraphAbility(BossAbilityKind.BOULDER_RAIN),
                    "including the kinds that only started warning later");
        }));
    }

    @Test
    @DisplayName("a stampless save that silenced something keeps its choice")
    void aStamplessChoiceIsKept() {
        int chosen = maskBefore(BossAbilityKind.PLATFORM) & ~(1 << BossAbilityKind.HOOK);
        TeleportPathData chose = configuredBoss();
        chose.setTelegraphAbilities(chosen);
        TeleportPathData reloaded = reread(stampless(chose));
        assertEquals(chosen, reloaded.getTelegraphAbilities(),
                "a mask that is nothing the mod ever offered whole is a builder's own choice");
        assertFalse(reloaded.isTelegraphAbility(BossAbilityKind.BOULDER_RAIN),
                "and a kind that started warning later stays off rather than overriding it");
    }

    @Test
    @DisplayName("a stamped save from before an ability existed is read by its stamp")
    void aStampedSaveIsReadByItsStamp() {
        int known = TeleportPathData.TELEGRAPH_ALL_ABILITIES & ~(1 << BossAbilityKind.PLATFORM);
        assertEquals(TeleportPathData.TELEGRAPH_ALL_ABILITIES,
                rereadStamped(known, known).getTelegraphAbilities(),
                "everything the save knew about was on, so everything is on");

        int chosen = known & ~(1 << BossAbilityKind.DASH);
        TeleportPathData reloaded = rereadStamped(chosen, known);
        assertEquals(chosen, reloaded.getTelegraphAbilities(), "a choice is kept bit for bit");
        assertFalse(reloaded.isTelegraphAbility(BossAbilityKind.PLATFORM),
                "and an ability the save never knew about stays off");
    }

    @Test
    @DisplayName("the rain of stones warns like everything else the boss winds up")
    void theRainIsOnTheWarningList() {
        assertTrue(Arrays.stream(TeleportPathData.TELEGRAPH_ABILITIES)
                        .anyMatch(ability -> ability == BossAbilityKind.BOULDER_RAIN),
                "the rain has a row on the warning screen");
        assertEquals(BossAbilityKind.BOULDER_RAIN,
                TeleportPathData.TELEGRAPH_ABILITIES[TeleportPathData.TELEGRAPH_ABILITIES.length - 1],
                "appended, so no existing row moved");
        assertTrue(new TeleportPathData().isTelegraphAbility(BossAbilityKind.BOULDER_RAIN),
                "and a new boss warns for it until its builder says otherwise");
    }

    @Test
    @DisplayName("a save from this build is taken at its word")
    void anUpToDateStampChangesNothing() {
        int chosen = TeleportPathData.TELEGRAPH_ALL_ABILITIES
                & ~(1 << BossAbilityKind.MELEE) & ~(1 << BossAbilityKind.LEAP);
        assertEquals(chosen,
                rereadStamped(chosen, TeleportPathData.TELEGRAPH_ALL_ABILITIES).getTelegraphAbilities(),
                "nothing was appended since, so there is nothing to fill in");
    }
}
