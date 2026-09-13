package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cone strike's settings, its aim points, its place on the shared ability lists, and what a
 * boss saved before it existed reads back as.
 *
 * <p>The bounds are written out below rather than read off the class, so this file is the
 * statement of what each number may be; the generic sweep only knows that a number is not
 * absurd. An angle of zero is a sector nobody stands in, a length of zero one that never
 * reaches past the boss, and neither throws.</p>
 */
class BossConeSettingsTest {

    private record Bound(int min, int max) {
    }

    private static final Map<String, Bound> BOUNDS = Map.ofEntries(
            Map.entry("ConeActionDelayTicks", new Bound(0, 1200)),
            Map.entry("ConeCooldownTicks", new Bound(1, 12000)),
            Map.entry("ConeAimMode", new Bound(BossPhaseData.CONE_AIM_TARGET, BossPhaseData.CONE_AIM_POINTS)),
            Map.entry("ConeTargetMode", new Bound(BossTargetMode.MAIN, BossTargetMode.RANDOM)),
            Map.entry("ConeAngle", new Bound(10, 180)),
            Map.entry("ConeLength", new Bound(2, 64)),
            Map.entry("ConeHeight", new Bound(1, 8)),
            Map.entry("ConeDamage", new Bound(0, 1000)),
            Map.entry("ConeImpulseMode", new Bound(BossPhaseData.CONE_IMPULSE_PUSH, BossPhaseData.CONE_IMPULSE_PULL)),
            Map.entry("ConeImpulseStrength", new Bound(0, 40)),
            Map.entry("ConePointOrder", new Bound(BossPhaseData.CONE_ORDER_LIST, BossPhaseData.CONE_ORDER_RANDOM)),
            Map.entry("ConePointCount", new Bound(0, 16)),
            Map.entry("ConePointIntervalTicks", new Bound(0, 200)));

    @Test
    @DisplayName("a boss saved before the cone reads it back switched off, at its defaults")
    void anOldSaveReadsTheDefaults() {
        BossPhaseData phase = new BossPhaseData();
        BossConeSettings changed = phase.cone();
        changed.setEnabled(true);
        changed.setAnimation("sweep");
        changed.setAimMode(BossPhaseData.CONE_AIM_POINTS);
        changed.setAngle(170);
        changed.setImpulseMode(BossPhaseData.CONE_IMPULSE_LIFT);
        changed.setFaceAxis(false);
        changed.setPointCount(3);
        changed.getPoints().add();
        changed.castSpot().setMode(BossCastSpot.MODE_TELEPORT);
        CompoundTag tag = phase.writeToNBT();
        for (String key : List.copyOf(tag.getAllKeys())) {
            if (key.startsWith("Cone")) {
                tag.remove(key);
            }
        }

        // Read into the phase that had everything changed, so the load has to put the defaults
        // back rather than merely leave a fresh object at them.
        phase.readFromNBT(tag);
        BossConeSettings cone = phase.cone();
        assertFalse(cone.isEnabled(), "an old boss must not start swinging cones on load");
        assertEquals("", cone.getAnimation());
        assertEquals(12, cone.getActionDelayTicks());
        assertEquals(160, cone.getCooldownTicks());
        assertEquals(BossPhaseData.CONE_AIM_TARGET, cone.getAimMode());
        assertEquals(BossTargetMode.MAIN, cone.getTargetMode());
        assertEquals(60, cone.getAngle());
        assertEquals(10, cone.getLength());
        assertEquals(3, cone.getHeight());
        assertEquals(10, cone.getDamage());
        assertEquals(BossPhaseData.CONE_IMPULSE_PUSH, cone.getImpulseMode());
        assertEquals(2, cone.getImpulseStrength());
        assertTrue(cone.isFaceAxis(), "the boss turns onto its cone unless told otherwise");
        assertEquals(0, cone.getPoints().size(), "an old boss has no points to sweep");
        assertEquals(BossPhaseData.CONE_ORDER_LIST, cone.getPointOrder());
        assertEquals(0, cone.getPointCount());
        assertEquals(10, cone.getPointIntervalTicks());
        assertFalse(cone.castSpot().isSet(), "an old boss swings from wherever it stands");
        assertFalse(cone.getEffects().isAnyEnabled());
    }

    @Test
    @DisplayName("every number the cone saves comes back inside its own range")
    void everyNumberIsClamped() {
        CompoundTag baseline = new BossPhaseData().writeToNBT();
        for (Map.Entry<String, Bound> entry : BOUNDS.entrySet()) {
            String key = entry.getKey();
            assertTrue(baseline.contains(key), key + " is not written at all");
            for (int extreme : new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                CompoundTag poisoned = baseline.copy();
                poisoned.putInt(key, extreme);
                BossPhaseData reread = new BossPhaseData();
                reread.readFromNBT(poisoned);
                int back = reread.writeToNBT().getInt(key);
                int expected = extreme > 0 ? entry.getValue().max() : entry.getValue().min();
                assertEquals(expected, back, key + " read " + extreme + " back as " + back);
            }
        }
    }

    @Test
    @DisplayName("no cone number is saved without a bound written out here")
    void everyNumberHasABound() {
        CompoundTag tag = new BossPhaseData().writeToNBT();
        // The spot's numbers belong to the cast spot, which has bounds and tests of its own.
        Set<String> numbers = tag.getAllKeys().stream()
                .filter(key -> key.startsWith("Cone") && !key.startsWith("ConeSpot"))
                .filter(key -> tag.get(key) instanceof IntTag)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(BOUNDS.keySet()), numbers,
                "a cone number was added or dropped without its bound being stated here");
    }

    @Test
    @DisplayName("the editor's setters hold the same ranges the save does")
    void settersClamp() {
        BossConeSettings cone = new BossConeSettings();
        cone.setAngle(5);
        cone.setLength(500);
        cone.setHeight(0);
        cone.setDamage(-4);
        cone.setImpulseStrength(99);
        cone.setImpulseMode(7);
        cone.setAimMode(-2);
        cone.setPointOrder(3);
        cone.setPointCount(40);
        cone.setPointIntervalTicks(-1);
        assertEquals(10, cone.getAngle(), "a sector narrower than a line strike is not a cone");
        assertEquals(64, cone.getLength());
        assertEquals(1, cone.getHeight());
        assertEquals(0, cone.getDamage());
        assertEquals(40, cone.getImpulseStrength());
        assertEquals(BossPhaseData.CONE_IMPULSE_PULL, cone.getImpulseMode());
        assertEquals(BossPhaseData.CONE_AIM_TARGET, cone.getAimMode());
        assertEquals(BossPhaseData.CONE_ORDER_RANDOM, cone.getPointOrder());
        assertEquals(BossConeAimList.MAX_ENTRIES, cone.getPointCount(), "a cast never strikes more points than a list holds");
        assertEquals(0, cone.getPointIntervalTicks());
    }

    @Test
    @DisplayName("a configured cone survives write, read and write again, points included")
    void aConfiguredConeRoundTrips() {
        BossPhaseData phase = new BossPhaseData();
        BossConeSettings cone = phase.cone();
        cone.setEnabled(true);
        cone.setAnimation("sweep");
        cone.setActionDelayTicks(30);
        cone.setCooldownTicks(400);
        cone.setAimMode(BossPhaseData.CONE_AIM_POINTS);
        cone.setTargetMode(BossTargetMode.FARTHEST);
        cone.setAngle(90);
        cone.setLength(24);
        cone.setHeight(5);
        cone.setDamage(0);
        cone.setImpulseMode(BossPhaseData.CONE_IMPULSE_LIFT);
        cone.setImpulseStrength(12);
        cone.setFaceAxis(false);
        cone.setPointOrder(BossPhaseData.CONE_ORDER_RANDOM);
        cone.setPointCount(2);
        cone.setPointIntervalTicks(25);
        cone.getEffects().get(1).setEnabled(true);
        cone.getPoints().add().setPosition(-3, 1, 8);
        BossConeAimPoint fixed = cone.getPoints().add();
        fixed.setCoordinateMode(BossConeAimPoint.COORDINATE_FIXED);
        fixed.setPosition(1000, 70, -2000);
        fixed.setEnabled(false);
        cone.castSpot().setMode(BossCastSpot.MODE_WALK);
        CompoundTag once = phase.writeToNBT();

        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(once);
        assertEquals(once, reread.writeToNBT(), "write -> read -> write should reproduce the cone exactly");
        BossConeAimList points = reread.cone().getPoints();
        assertEquals(2, points.size());
        assertEquals(-3, points.get(0).getX());
        assertEquals(8, points.get(0).getZ());
        assertTrue(points.get(0).isEnabled());
        assertEquals(BossConeAimPoint.COORDINATE_FIXED, points.get(1).getCoordinateMode());
        assertEquals(-2000, points.get(1).getZ());
        assertFalse(points.get(1).isEnabled());
        assertTrue(points.hasEnabled());
    }

    @Test
    @DisplayName("the point list is bounded, and a point read from a save is put inside the world")
    void thePointListIsBounded() {
        BossConeAimList points = new BossConeAimList();
        for (int i = 0; i < BossConeAimList.MAX_ENTRIES; i++) {
            assertTrue(points.add() != null, "point " + i + " should fit");
        }
        assertNull(points.add(), "a full list takes no more points");
        assertFalse(new BossConeAimList().hasEnabled(), "an empty list has nothing to aim at");

        CompoundTag wild = new CompoundTag();
        wild.putInt("CoordinateMode", 9);
        wild.putInt("X", Integer.MAX_VALUE);
        wild.putInt("Y", Integer.MIN_VALUE);
        wild.putInt("Z", 12);
        ListTag list = new ListTag();
        for (int i = 0; i < BossConeAimList.MAX_ENTRIES + 5; i++) {
            list.add(wild.copy());
        }
        BossConeAimList read = new BossConeAimList();
        read.readFromNBT(list);
        assertEquals(BossConeAimList.MAX_ENTRIES, read.size(), "a save with too many points is cut to the list's size");
        BossConeAimPoint point = read.get(0);
        assertEquals(BossConeAimPoint.COORDINATE_FIXED, point.getCoordinateMode());
        assertEquals(BossConeAimPoint.MAX_COORDINATE, point.getX());
        assertEquals(-BossConeAimPoint.MAX_COORDINATE, point.getY());
        assertTrue(point.isEnabled(), "a point saved without the switch is switched on");
    }

    @Test
    @DisplayName("point ids stay unique through a delete, an add and a save with duplicates")
    void pointIdsStayUnique() {
        BossConeAimList points = new BossConeAimList();
        points.add();
        points.add();
        points.add();
        points.remove(1);
        points.add();
        assertEquals(3, distinctIds(points));

        ListTag duplicated = new ListTag();
        for (int i = 0; i < 3; i++) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("PointId", 5);
            duplicated.add(tag);
        }
        BossConeAimList read = new BossConeAimList();
        read.readFromNBT(duplicated);
        assertEquals(3, distinctIds(read), "points saved with one id between them get their own");
        read.add();
        assertEquals(4, distinctIds(read));
    }

    @Test
    @DisplayName("a boss saved before the cone holds its wind-up still; a later choice is kept")
    void theCastRootBitIsMigrated() {
        CompoundTag tag = new BossPhaseData().writeToNBT();
        for (String key : List.copyOf(tag.getAllKeys())) {
            if (key.startsWith("Cone")) {
                tag.remove(key);
            }
        }
        tag.putInt("CastRootMask", BossPhaseData.CAST_ROOT_ALL & ~(1 << BossAbilityKind.CONE));
        BossPhaseData old = new BossPhaseData();
        old.readFromNBT(tag);
        assertTrue(old.isCastRooted(BossAbilityKind.CONE),
                "a save that never saw the cone never chose to let its wind-up walk");

        BossPhaseData chosen = new BossPhaseData();
        chosen.setCastRooted(BossAbilityKind.CONE, false);
        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(chosen.writeToNBT());
        assertFalse(reread.isCastRooted(BossAbilityKind.CONE), "a save that knows the cone keeps its choice");
        assertTrue(reread.isCastRooted(BossAbilityKind.DASH), "freeing the cone frees nothing else");
    }

    @Test
    @DisplayName("a boss that warned for everything warns for the cone; one that chose keeps its choice")
    void theWarningBitIsMigrated() {
        int beforeCone = BossTelegraphMaskMigrationTest.maskBefore(BossAbilityKind.CONE);
        TeleportPathData everything = configuredBoss();
        everything.setTelegraphAbilities(beforeCone);
        TeleportPathData reread = new TeleportPathData();
        reread.readFromNBT(BossTelegraphMaskMigrationTest.stampless(everything));
        assertTrue(reread.isTelegraphAbility(BossAbilityKind.CONE),
                "a boss warning for every ability it had was warning for everything");

        TeleportPathData chose = configuredBoss();
        chose.setTelegraphAbilities(beforeCone & ~(1 << BossAbilityKind.MELEE));
        TeleportPathData rereadChoice = new TeleportPathData();
        rereadChoice.readFromNBT(BossTelegraphMaskMigrationTest.stampless(chose));
        assertFalse(rereadChoice.isTelegraphAbility(BossAbilityKind.CONE),
                "a boss that silenced something made a choice, and the new bit stays off");
        assertFalse(rereadChoice.isTelegraphAbility(BossAbilityKind.MELEE));
    }

    @Test
    @DisplayName("an old boss is not immune to the cone, and no totem is broken by it until listed")
    void theHitMasksStartClear() {
        NpcImmunityData immunity = new NpcImmunityData();
        assertFalse(immunity.isImmuneTo(BossAbilityKind.CONE));
        immunity.setImmuneTo(BossAbilityKind.CONE, true);
        assertTrue(immunity.isImmuneTo(BossAbilityKind.CONE), "an npc can be made immune to the cone");
        BossTotemEntry totem = new BossTotemEntry(1);
        assertFalse(totem.isVulnerableTo(BossAbilityKind.CONE), "a totem does not list the cone by itself");
        totem.setVulnerableTo(BossAbilityKind.CONE, true);
        assertTrue(totem.isVulnerableTo(BossAbilityKind.CONE), "a totem can list the cone");
    }

    @Test
    @DisplayName("the cone sits on every list it belongs to, and only those")
    void theConeIsOnItsLists() {
        assertEquals("cnpcgeckoaddon.boss.ability.cone", BossAbilityKind.LABELS[BossAbilityKind.CONE]);
        assertTrue(BossAbilityKind.COUNT < Integer.SIZE, "the masks are ints, so the list cannot outgrow 31");
        assertTrue(contains(BossAbilityKind.IMMUNITY_ABILITIES, BossAbilityKind.CONE), "an npc can be immune to the cone");
        assertTrue(contains(BossAbilityKind.COMBO_ABILITIES, BossAbilityKind.CONE), "the cone can be chained");
        assertTrue(contains(BossPhaseData.CAST_ROOT_ABILITIES, BossAbilityKind.CONE), "the wind-up can be rooted");
        assertTrue(contains(TeleportPathData.TELEGRAPH_ABILITIES, BossAbilityKind.CONE), "the wind-up warns");
        assertFalse(contains(BossAbilityKind.LASTING_ABILITIES, BossAbilityKind.CONE),
                "a series holds the boss busy itself, so there is nothing to wait out");
        assertEquals(BossAbilityKind.DASH + 1, BossAbilityKind.CONE, "a new kind is appended, never slotted in");
    }

    private static int distinctIds(BossConeAimList points) {
        Set<Integer> ids = new HashSet<>();
        for (BossConeAimPoint point : points.entries()) {
            assertTrue(point.getPointId() > 0, "a point id is never zero or negative");
            ids.add(point.getPointId());
        }
        return ids.size();
    }

    private static boolean contains(int[] list, int ability) {
        for (int entry : list) {
            if (entry == ability) {
                return true;
            }
        }
        return false;
    }

    private static TeleportPathData configuredBoss() {
        TeleportPathData data = new TeleportPathData();
        data.setEnabled(true);
        data.markConfigured();
        return data;
    }
}
