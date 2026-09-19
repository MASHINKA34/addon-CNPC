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
    private record Era(String name, int ability, long mask) {
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
    static long maskBefore(int ability) {
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
    private static TeleportPathData rereadStamped(long saved, long known) {
        CompoundTag tag = configuredBoss().writeToNBT(new CompoundTag());
        tag.putLong(MASK_KEY, saved);
        tag.putLong(KNOWN_KEY, known);
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
            assertEquals(era.mask(), tag.getLong(MASK_KEY),
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
        long chosen = maskBefore(BossAbilityKind.PLATFORM) & ~(1L << BossAbilityKind.HOOK);
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
        long known = TeleportPathData.TELEGRAPH_ALL_ABILITIES & ~(1L << BossAbilityKind.PLATFORM);
        assertEquals(TeleportPathData.TELEGRAPH_ALL_ABILITIES,
                rereadStamped(known, known).getTelegraphAbilities(),
                "everything the save knew about was on, so everything is on");

        long chosen = known & ~(1L << BossAbilityKind.DASH);
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
        // The rain was appended after the platforms, the hurricane after it, the shadow copies
        // after that, the seismic waves after them and the rift last, so its row is exactly where
        // the saves that know it left it: four before the end.
        assertEquals(BossAbilityKind.BOULDER_RAIN,
                TeleportPathData.TELEGRAPH_ABILITIES[TeleportPathData.TELEGRAPH_ABILITIES.length - 5],
                "appended after the platforms, so no existing row moved");
        assertTrue(new TeleportPathData().isTelegraphAbility(BossAbilityKind.BOULDER_RAIN),
                "and a new boss warns for it until its builder says otherwise");
    }

    @Test
    @DisplayName("the hurricane warns too, appended after the rain, and a stamped save fills it in")
    void theHurricaneIsOnTheWarningList() {
        assertEquals(BossAbilityKind.HURRICANE,
                TeleportPathData.TELEGRAPH_ABILITIES[TeleportPathData.TELEGRAPH_ABILITIES.length - 4],
                "appended after the rain, so no existing row moved");
        assertTrue(new TeleportPathData().isTelegraphAbility(BossAbilityKind.HURRICANE),
                "and a new boss warns for it until its builder says otherwise");
        // A save from between the stamp and the hurricane knew everything but the hurricane,
        // and warned for all of it: the stamp says so, and the hurricane joins the rest.
        long known = TeleportPathData.TELEGRAPH_ALL_ABILITIES & ~(1L << BossAbilityKind.HURRICANE);
        assertEquals(TeleportPathData.TELEGRAPH_ALL_ABILITIES,
                rereadStamped(known, known).getTelegraphAbilities(),
                "everything the save knew about was on, so everything is on");
        long chosen = known & ~(1L << BossAbilityKind.DASH);
        assertEquals(chosen, rereadStamped(chosen, known).getTelegraphAbilities(),
                "a choice is kept bit for bit, and the hurricane stays off with it");
    }

    @Test
    @DisplayName("the shadow copies warn too, appended after the hurricane, and a stamped save fills them in")
    void theShadowCopiesAreOnTheWarningList() {
        assertEquals(BossAbilityKind.SHADOW,
                TeleportPathData.TELEGRAPH_ABILITIES[TeleportPathData.TELEGRAPH_ABILITIES.length - 3],
                "appended after the hurricane, so no existing row moved");
        assertTrue(new TeleportPathData().isTelegraphAbility(BossAbilityKind.SHADOW),
                "and a new boss warns for them until its builder says otherwise");
        // A save from between the hurricane and the copies knew everything but the copies, and
        // warned for all of it: the stamp says so, and the copies join the rest.
        long known = TeleportPathData.TELEGRAPH_ALL_ABILITIES & ~(1L << BossAbilityKind.SHADOW);
        assertEquals(TeleportPathData.TELEGRAPH_ALL_ABILITIES,
                rereadStamped(known, known).getTelegraphAbilities(),
                "everything the save knew about was on, so everything is on");
        long chosen = known & ~(1L << BossAbilityKind.HURRICANE);
        assertEquals(chosen, rereadStamped(chosen, known).getTelegraphAbilities(),
                "a choice is kept bit for bit, and the copies stay off with it");
    }

    @Test
    @DisplayName("the seismic waves warn too, appended after the shadow copies, and a stamped save fills them in")
    void theSeismicWavesAreOnTheWarningList() {
        assertEquals(BossAbilityKind.SEISMIC,
                TeleportPathData.TELEGRAPH_ABILITIES[TeleportPathData.TELEGRAPH_ABILITIES.length - 2],
                "appended after the copies, so no existing row moved");
        assertTrue(new TeleportPathData().isTelegraphAbility(BossAbilityKind.SEISMIC),
                "and a new boss warns for them until its builder says otherwise");
        // A save from between the copies and the waves knew everything but the waves, and
        // warned for all of it: the stamp says so, and the waves join the rest.
        long known = TeleportPathData.TELEGRAPH_ALL_ABILITIES & ~(1L << BossAbilityKind.SEISMIC);
        assertEquals(TeleportPathData.TELEGRAPH_ALL_ABILITIES,
                rereadStamped(known, known).getTelegraphAbilities(),
                "everything the save knew about was on, so everything is on");
        long chosen = known & ~(1L << BossAbilityKind.SHADOW);
        assertEquals(chosen, rereadStamped(chosen, known).getTelegraphAbilities(),
                "a choice is kept bit for bit, and the waves stay off with it");
    }

    @Test
    @DisplayName("the rift warns too, appended after the seismic waves, and a stamped save fills it in")
    void theRiftIsOnTheWarningList() {
        assertEquals(BossAbilityKind.RIFT,
                TeleportPathData.TELEGRAPH_ABILITIES[TeleportPathData.TELEGRAPH_ABILITIES.length - 1],
                "appended, so no existing row moved");
        assertTrue(new TeleportPathData().isTelegraphAbility(BossAbilityKind.RIFT),
                "and a new boss warns for it until its builder says otherwise");
        // A save from between the waves and the rift knew everything but the rift, and warned
        // for all of it: the stamp says so, and the rift joins the rest.
        long known = TeleportPathData.TELEGRAPH_ALL_ABILITIES & ~(1L << BossAbilityKind.RIFT);
        assertEquals(TeleportPathData.TELEGRAPH_ALL_ABILITIES,
                rereadStamped(known, known).getTelegraphAbilities(),
                "everything the save knew about was on, so everything is on");
        long chosen = known & ~(1L << BossAbilityKind.SEISMIC);
        assertEquals(chosen, rereadStamped(chosen, known).getTelegraphAbilities(),
                "a choice is kept bit for bit, and the rift stays off with it");
    }

    @Test
    @DisplayName("a save from this build is taken at its word")
    void anUpToDateStampChangesNothing() {
        long chosen = TeleportPathData.TELEGRAPH_ALL_ABILITIES
                & ~(1L << BossAbilityKind.MELEE) & ~(1L << BossAbilityKind.LEAP);
        assertEquals(chosen,
                rereadStamped(chosen, TeleportPathData.TELEGRAPH_ALL_ABILITIES).getTelegraphAbilities(),
                "nothing was appended since, so there is nothing to fill in");
    }
}
