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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The platforms' settings, their zone list, their place on the shared ability lists, and what a
 * boss saved before they existed reads back as.
 *
 * <p>The bounds are written out below rather than read off the class, so this file is the
 * statement of what each number may be. A fuse of zero is a platform nobody can jump off in
 * time, and a dose interval of zero one that burns every tick; neither throws.</p>
 */
class BossPlatformSettingsTest {

    private record Bound(int min, int max) {
    }

    private static final Map<String, Bound> BOUNDS = Map.ofEntries(
            Map.entry("PlatformActionDelayTicks", new Bound(0, 1200)),
            Map.entry("PlatformCooldownTicks", new Bound(1, 12000)),
            Map.entry("PlatformPickMode", new Bound(BossPhaseData.PLATFORM_PICK_RANDOM, BossPhaseData.PLATFORM_PICK_ALL_BUT_ONE)),
            Map.entry("PlatformFuseTicks", new Bound(10, 1200)),
            Map.entry("PlatformDamage", new Bound(0, 1000)),
            Map.entry("PlatformKnockback", new Bound(0, 10)),
            Map.entry("PlatformLaunch", new Bound(0, 40)),
            Map.entry("PlatformLingerTicks", new Bound(0, 12000)),
            Map.entry("PlatformLingerIntervalTicks", new Bound(1, 200)));

    /** A zone's own numbers, the same way: the list writes one compound per zone. */
    private static final Map<String, Bound> ZONE_BOUNDS = Map.ofEntries(
            Map.entry("CoordinateMode", new Bound(BossPlatformZone.COORDINATE_ARENA_OFFSET, BossPlatformZone.COORDINATE_FIXED)),
            Map.entry("X1", new Bound(-30000000, 30000000)),
            Map.entry("Y1", new Bound(-30000000, 30000000)),
            Map.entry("Z1", new Bound(-30000000, 30000000)),
            Map.entry("X2", new Bound(-30000000, 30000000)),
            Map.entry("Y2", new Bound(-30000000, 30000000)),
            Map.entry("Z2", new Bound(-30000000, 30000000)),
            Map.entry("Weight", new Bound(1, 100)));

    @Test
    @DisplayName("a boss saved before the platforms reads them back switched off, at their defaults")
    void anOldSaveReadsTheDefaults() {
        BossPhaseData phase = new BossPhaseData();
        BossPlatformSettings changed = phase.platform();
        changed.setEnabled(true);
        changed.setAnimation("stomp");
        changed.setPickMode(BossPhaseData.PLATFORM_PICK_CYCLE);
        changed.setFuseTicks(200);
        changed.setLaunch(30);
        changed.setLingerTicks(400);
        changed.setVfx(AreaVfxStyles.FIRE);
        changed.getZones().add();
        changed.getEffects().get(0).setEnabled(true);
        changed.castSpot().setMode(BossCastSpot.MODE_TELEPORT);
        CompoundTag tag = phase.writeToNBT();
        for (String key : List.copyOf(tag.getAllKeys())) {
            if (key.startsWith("Platform")) {
                tag.remove(key);
            }
        }

        // Read into the phase that had everything changed, so the load has to put the defaults
        // back rather than merely leave a fresh object at them.
        phase.readFromNBT(tag);
        BossPlatformSettings platform = phase.platform();
        assertFalse(platform.isEnabled(), "an old boss must not start setting platforms alight on load");
        assertFalse(platform.canCast());
        assertEquals("", platform.getAnimation());
        assertEquals(10, platform.getActionDelayTicks());
        assertEquals(300, platform.getCooldownTicks());
        assertEquals(0, platform.getZones().size(), "an old boss has no platforms");
        assertEquals(BossPhaseData.PLATFORM_PICK_RANDOM, platform.getPickMode());
        assertEquals(60, platform.getFuseTicks());
        assertEquals(12, platform.getDamage());
        assertEquals(2, platform.getKnockback());
        assertEquals(0, platform.getLaunch());
        assertEquals(0, platform.getLingerTicks(), "an old boss' platform goes off once and is done");
        assertEquals(20, platform.getLingerIntervalTicks());
        assertEquals(AreaVfxStyles.NONE, platform.getVfx());
        assertFalse(platform.castSpot().isSet(), "an old boss casts from wherever it stands");
        assertFalse(platform.getEffects().isAnyEnabled());
    }

    @Test
    @DisplayName("every number the platforms save comes back inside its own range")
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
    @DisplayName("no platform number is saved without a bound written out here")
    void everyNumberHasABound() {
        CompoundTag tag = new BossPhaseData().writeToNBT();
        // The spot's numbers belong to the cast spot, which has bounds and tests of its own.
        Set<String> numbers = tag.getAllKeys().stream()
                .filter(key -> key.startsWith("Platform") && !key.startsWith("PlatformSpot"))
                .filter(key -> tag.get(key) instanceof IntTag)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(BOUNDS.keySet()), numbers,
                "a platform number was added or dropped without its bound being stated here");
    }

    @Test
    @DisplayName("every number a zone saves comes back inside its own range, and has a bound here")
    void everyZoneNumberIsClamped() {
        BossPlatformZoneList list = new BossPlatformZoneList();
        list.add();
        CompoundTag baseline = list.writeToNBT().getCompound(0);
        Set<String> numbers = baseline.getAllKeys().stream()
                .filter(key -> baseline.get(key) instanceof IntTag && !key.equals("ZoneId"))
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(ZONE_BOUNDS.keySet()), numbers,
                "a zone number was added or dropped without its bound being stated here");
        for (Map.Entry<String, Bound> entry : ZONE_BOUNDS.entrySet()) {
            for (int extreme : new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                CompoundTag poisoned = baseline.copy();
                poisoned.putInt(entry.getKey(), extreme);
                ListTag saved = new ListTag();
                saved.add(poisoned);
                BossPlatformZoneList reread = new BossPlatformZoneList();
                reread.readFromNBT(saved);
                int back = reread.writeToNBT().getCompound(0).getInt(entry.getKey());
                int expected = extreme > 0 ? entry.getValue().max() : entry.getValue().min();
                assertEquals(expected, back, entry.getKey() + " read " + extreme + " back as " + back);
            }
        }
    }

    @Test
    @DisplayName("the editor's setters hold the same ranges the save does")
    void settersClamp() {
        BossPlatformSettings platform = new BossPlatformSettings();
        platform.setActionDelayTicks(-5);
        platform.setCooldownTicks(0);
        platform.setPickMode(9);
        platform.setFuseTicks(1);
        platform.setDamage(5000);
        platform.setKnockback(-1);
        platform.setLaunch(99);
        platform.setLingerTicks(-20);
        platform.setLingerIntervalTicks(0);
        platform.setVfx("no such wave");
        assertEquals(0, platform.getActionDelayTicks());
        assertEquals(1, platform.getCooldownTicks());
        assertEquals(BossPhaseData.PLATFORM_PICK_ALL_BUT_ONE, platform.getPickMode());
        assertEquals(10, platform.getFuseTicks(), "a fuse nobody can jump off in time is not a fuse");
        assertEquals(1000, platform.getDamage());
        assertEquals(0, platform.getKnockback());
        assertEquals(40, platform.getLaunch());
        assertEquals(0, platform.getLingerTicks());
        assertEquals(1, platform.getLingerIntervalTicks(), "a dose every zero ticks would never end");
        assertEquals(AreaVfxStyles.NONE, platform.getVfx());

        BossPlatformZone zone = new BossPlatformZoneList().add();
        zone.setWeight(0);
        assertEquals(1, zone.getWeight(), "a platform that can never be drawn is one to switch off instead");
        zone.setWeight(1000);
        assertEquals(100, zone.getWeight());
        zone.setCoordinateMode(-3);
        assertEquals(BossPlatformZone.COORDINATE_ARENA_OFFSET, zone.getCoordinateMode());
        zone.setCorner1(Integer.MIN_VALUE, 64, Integer.MAX_VALUE);
        assertEquals(-BossPlatformZone.MAX_COORDINATE, zone.getX1());
        assertEquals(64, zone.getY1());
        assertEquals(BossPlatformZone.MAX_COORDINATE, zone.getZ1());
    }

    @Test
    @DisplayName("configured platforms survive write, read and write again, zones included")
    void configuredPlatformsRoundTrip() {
        BossPhaseData phase = new BossPhaseData();
        BossPlatformSettings platform = phase.platform();
        platform.setEnabled(true);
        platform.setAnimation("stomp");
        platform.setActionDelayTicks(25);
        platform.setCooldownTicks(500);
        platform.setPickMode(BossPhaseData.PLATFORM_PICK_TARGET);
        platform.setFuseTicks(90);
        platform.setDamage(0);
        platform.setKnockback(4);
        platform.setLaunch(15);
        platform.setLingerTicks(100);
        platform.setLingerIntervalTicks(25);
        platform.setVfx(AreaVfxStyles.GHOST);
        platform.getEffects().get(2).setEnabled(true);
        BossPlatformZone offset = platform.getZones().add();
        offset.setCorner1(-3, 0, 8);
        offset.setCorner2(2, -1, 4);
        BossPlatformZone fixed = platform.getZones().add();
        fixed.setCoordinateMode(BossPlatformZone.COORDINATE_FIXED);
        fixed.setCorner1(1000, 70, -2000);
        fixed.setCorner2(990, 72, -1990);
        fixed.setWeight(7);
        fixed.setEnabled(false);
        platform.castSpot().setMode(BossCastSpot.MODE_WALK);
        CompoundTag once = phase.writeToNBT();

        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(once);
        assertEquals(once, reread.writeToNBT(), "write -> read -> write should reproduce the platforms exactly");
        BossPlatformZoneList zones = reread.platform().getZones();
        assertEquals(2, zones.size());
        BossPlatformZone first = zones.get(0);
        assertEquals(BossPlatformZone.COORDINATE_ARENA_OFFSET, first.getCoordinateMode());
        assertEquals(-3, first.getX1(), "the corners come back as typed, in the order they were typed");
        assertEquals(8, first.getZ1());
        assertEquals(2, first.getX2());
        assertEquals(-1, first.getY2());
        assertTrue(first.isEnabled());
        BossPlatformZone second = zones.get(1);
        assertEquals(BossPlatformZone.COORDINATE_FIXED, second.getCoordinateMode());
        assertEquals(-1990, second.getZ2());
        assertEquals(7, second.getWeight());
        assertFalse(second.isEnabled());
        assertTrue(reread.platform().canCast(), "one platform switched on is enough to cast");
    }

    @Test
    @DisplayName("a phase with no platform switched on has nothing to cast, whatever its switch says")
    void castingNeedsAPlatform() {
        BossPlatformSettings platform = new BossPlatformSettings();
        platform.setEnabled(true);
        assertFalse(platform.isConfigured(), "no platforms at all");
        assertFalse(platform.canCast());
        BossPlatformZone zone = platform.getZones().add();
        zone.setEnabled(false);
        assertFalse(platform.isConfigured(), "only a platform switched off");
        zone.setEnabled(true);
        assertTrue(platform.isConfigured());
        assertTrue(platform.canCast());
        platform.setEnabled(false);
        assertTrue(platform.isConfigured(), "the setup does not hang on the switch: a chain still casts it");
        assertFalse(platform.canCast());
    }

    @Test
    @DisplayName("the zone list is bounded, and a zone read from a save is put inside the world")
    void theZoneListIsBounded() {
        BossPlatformZoneList zones = new BossPlatformZoneList();
        for (int i = 0; i < BossPlatformZoneList.MAX_ENTRIES; i++) {
            assertNotNull(zones.add(), "zone " + i + " should fit");
        }
        assertNull(zones.add(), "a full list takes no more zones");
        assertFalse(new BossPlatformZoneList().hasEnabled(), "an empty list has nothing to set alight");

        CompoundTag wild = new CompoundTag();
        wild.putInt("CoordinateMode", 9);
        wild.putInt("X1", Integer.MAX_VALUE);
        wild.putInt("Y2", Integer.MIN_VALUE);
        wild.putInt("Z2", 12);
        wild.putInt("Weight", -40);
        ListTag list = new ListTag();
        for (int i = 0; i < BossPlatformZoneList.MAX_ENTRIES + 5; i++) {
            list.add(wild.copy());
        }
        BossPlatformZoneList read = new BossPlatformZoneList();
        read.readFromNBT(list);
        assertEquals(BossPlatformZoneList.MAX_ENTRIES, read.size(), "a save with too many zones is cut to the list's size");
        BossPlatformZone zone = read.get(0);
        assertEquals(BossPlatformZone.COORDINATE_FIXED, zone.getCoordinateMode());
        assertEquals(BossPlatformZone.MAX_COORDINATE, zone.getX1());
        assertEquals(-BossPlatformZone.MAX_COORDINATE, zone.getY2());
        assertEquals(12, zone.getZ2());
        assertEquals(1, zone.getWeight());
        assertTrue(zone.isEnabled(), "a zone saved without the switch is switched on");

        BossPlatformZoneList bare = new BossPlatformZoneList();
        ListTag empty = new ListTag();
        empty.add(new CompoundTag());
        bare.readFromNBT(empty);
        assertEquals(1, bare.get(0).getWeight(), "a zone saved without a weight is drawn as often as any other");
        assertEquals(0, bare.get(0).getX2(), "a zone saved without a corner has it at nought");
    }

    @Test
    @DisplayName("zone ids stay unique through a delete, an add and a save with duplicates")
    void zoneIdsStayUnique() {
        BossPlatformZoneList zones = new BossPlatformZoneList();
        zones.add();
        zones.add();
        zones.add();
        int removed = zones.remove(1).getZoneId();
        zones.add();
        assertEquals(3, distinctIds(zones));
        assertFalse(zones.entries().stream().anyMatch(zone -> zone.getZoneId() == removed),
                "a deleted zone's id is not handed straight back out, or a turn remembered by it would move");

        ListTag duplicated = new ListTag();
        for (int i = 0; i < 3; i++) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("ZoneId", 5);
            duplicated.add(tag);
        }
        BossPlatformZoneList read = new BossPlatformZoneList();
        read.readFromNBT(duplicated);
        assertEquals(3, distinctIds(read), "zones saved with one id between them get their own");
        assertEquals(5, read.get(0).getZoneId(), "the first keeps the id it was saved with");
        read.add();
        assertEquals(4, distinctIds(read));

        ListTag broken = new ListTag();
        CompoundTag negative = new CompoundTag();
        negative.putInt("ZoneId", -8);
        broken.add(negative);
        BossPlatformZoneList fixed = new BossPlatformZoneList();
        fixed.readFromNBT(broken);
        assertTrue(fixed.get(0).getZoneId() > 0, "a zone id is never zero or negative");
    }

    @Test
    @DisplayName("a boss saved before the platforms holds their wind-up still; a later choice is kept")
    void theCastRootBitIsMigrated() {
        CompoundTag tag = new BossPhaseData().writeToNBT();
        for (String key : List.copyOf(tag.getAllKeys())) {
            if (key.startsWith("Platform")) {
                tag.remove(key);
            }
        }
        tag.putInt("CastRootMask", BossPhaseData.CAST_ROOT_ALL & ~(1 << BossAbilityKind.PLATFORM));
        BossPhaseData old = new BossPhaseData();
        old.readFromNBT(tag);
        assertTrue(old.isCastRooted(BossAbilityKind.PLATFORM),
                "a save that never saw the platforms never chose to let their wind-up walk");

        BossPhaseData chosen = new BossPhaseData();
        chosen.setCastRooted(BossAbilityKind.PLATFORM, false);
        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(chosen.writeToNBT());
        assertFalse(reread.isCastRooted(BossAbilityKind.PLATFORM), "a save that knows the platforms keeps its choice");
        assertTrue(reread.isCastRooted(BossAbilityKind.CONE), "freeing the platforms frees nothing else");
    }

    @Test
    @DisplayName("an old boss sees no platform out before it starts the next thing")
    void theFinishBitStartsClear() {
        CompoundTag tag = new BossPhaseData().writeToNBT();
        tag.putInt("FinishMask", BossAbilityKind.LASTING_ALL & ~(1 << BossAbilityKind.PLATFORM));
        BossPhaseData old = new BossPhaseData();
        old.readFromNBT(tag);
        assertFalse(old.waitsForFinish(BossAbilityKind.PLATFORM), "a mask saved before the platforms never marked them");
        assertTrue(old.waitsForFinish(BossAbilityKind.GEYSER), "and the bits it did mark are kept");
        old.setWaitsForFinish(BossAbilityKind.PLATFORM, true);
        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(old.writeToNBT());
        assertTrue(reread.waitsForFinish(BossAbilityKind.PLATFORM), "the platforms' own bit is saved once it is marked");
    }

    @Test
    @DisplayName("a boss that warned for everything warns for the platforms; one that chose keeps its choice")
    void theWarningBitIsMigrated() {
        int beforePlatform = TeleportPathData.TELEGRAPH_ALL_ABILITIES & ((1 << BossAbilityKind.PLATFORM) - 1);
        TeleportPathData everything = configuredBoss();
        everything.setTelegraphAbilities(beforePlatform);
        TeleportPathData reread = new TeleportPathData();
        reread.readFromNBT(everything.writeToNBT(new CompoundTag()));
        assertTrue(reread.isTelegraphAbility(BossAbilityKind.PLATFORM),
                "a boss warning for every ability it had was warning for everything");

        // Not the cone: everything but the cone is exactly what a boss saved before the cone warned
        // for, and that one is migrated as warning for everything.
        TeleportPathData chose = configuredBoss();
        chose.setTelegraphAbilities(beforePlatform & ~(1 << BossAbilityKind.MELEE));
        TeleportPathData rereadChoice = new TeleportPathData();
        rereadChoice.readFromNBT(chose.writeToNBT(new CompoundTag()));
        assertFalse(rereadChoice.isTelegraphAbility(BossAbilityKind.PLATFORM),
                "a boss that silenced something made a choice, and the new bit stays off");
        assertFalse(rereadChoice.isTelegraphAbility(BossAbilityKind.MELEE));
    }

    @Test
    @DisplayName("an old boss is not immune to the platforms, and no totem is broken by them until listed")
    void theHitMasksStartClear() {
        NpcImmunityData immunity = new NpcImmunityData();
        assertFalse(immunity.isImmuneTo(BossAbilityKind.PLATFORM));
        immunity.setImmuneTo(BossAbilityKind.PLATFORM, true);
        assertTrue(immunity.isImmuneTo(BossAbilityKind.PLATFORM), "an npc can be made immune to the platforms");
        NpcImmunityData saved = new NpcImmunityData();
        saved.readFromNBT(immunity.writeToNBT(new CompoundTag()));
        assertTrue(saved.isImmuneTo(BossAbilityKind.PLATFORM), "the new bit survives the npc's save");

        BossTotemEntry totem = new BossTotemEntry(1);
        assertFalse(totem.isVulnerableTo(BossAbilityKind.PLATFORM), "a totem does not list the platforms by itself");
        totem.setVulnerableTo(BossAbilityKind.PLATFORM, true);
        assertTrue(totem.isVulnerableTo(BossAbilityKind.PLATFORM), "a totem can list the platforms");
    }

    @Test
    @DisplayName("the platforms sit on the lists every kind belongs to")
    void thePlatformsAreOnTheirLists() {
        assertEquals("cnpcgeckoaddon.boss.ability.platform", BossAbilityKind.LABELS[BossAbilityKind.PLATFORM]);
        assertTrue(BossAbilityKind.COUNT < Integer.SIZE, "the masks are ints, so the list cannot outgrow 31");
        assertTrue(contains(BossAbilityKind.IMMUNITY_ABILITIES, BossAbilityKind.PLATFORM), "an npc can be immune to them");
        assertTrue(contains(BossPhaseData.CAST_ROOT_ABILITIES, BossAbilityKind.PLATFORM), "the wind-up can be rooted");
        assertTrue(contains(TeleportPathData.TELEGRAPH_ABILITIES, BossAbilityKind.PLATFORM), "the wind-up warns");
        assertTrue(contains(BossAbilityKind.COMBO_ABILITIES, BossAbilityKind.PLATFORM), "the platforms can be chained");
        assertTrue(contains(BossAbilityKind.LASTING_ABILITIES, BossAbilityKind.PLATFORM),
                "the fuse outlives the cast, so a phase can be told to see it out");
        assertEquals(BossAbilityKind.CONE + 1, BossAbilityKind.PLATFORM, "a new kind is appended, never slotted in");
        assertEquals(BossPhaseData.PLATFORM_PICK_LABELS.length, BossPhaseData.PLATFORM_PICK_ALL_BUT_ONE + 1,
                "every pick mode has a name on the screen");
    }

    private static int distinctIds(BossPlatformZoneList zones) {
        Set<Integer> ids = new HashSet<>();
        for (BossPlatformZone zone : zones.entries()) {
            assertTrue(zone.getZoneId() > 0, "a zone id is never zero or negative");
            ids.add(zone.getZoneId());
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
