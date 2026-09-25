package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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
 * The vents' settings, their list, their place on the shared ability lists, and what a boss
 * saved before they existed reads back as.
 *
 * <p>The bounds are written out below rather than read off the class, so this file is the
 * statement of what each number may be. A timer beating every tick is a vent that never stops,
 * and a reach of nought one that fires at nobody; neither throws.</p>
 */
class BossVentSettingsTest {

    private record Bound(int min, int max) {
    }

    private static final Map<String, Bound> BOUNDS = Map.ofEntries(
            Map.entry("VentActionDelayTicks", new Bound(0, 1200)),
            Map.entry("VentCooldownTicks", new Bound(1, 12000)),
            Map.entry("VentMode", new Bound(BossVentSettings.MODE_BURST, BossVentSettings.MODE_WALL)),
            Map.entry("VentPattern", new Bound(BossVentSettings.PATTERN_ALL, BossVentSettings.PATTERN_RANDOM)),
            Map.entry("VentRandomCount", new Bound(1, 16)),
            Map.entry("VentCycleTicks", new Bound(5, 12000)),
            Map.entry("VentWarnTicks", new Bound(0, 200)),
            Map.entry("VentActiveTicks", new Bound(1, 1200)),
            Map.entry("VentRepeats", new Bound(0, 1000)),
            Map.entry("VentRecast", new Bound(BossVentSettings.RECAST_RESTART, BossVentSettings.RECAST_IGNORE)),
            Map.entry("VentDamage", new Bound(0, 1000)),
            Map.entry("VentHitIntervalTicks", new Bound(1, 200)),
            Map.entry("VentKnockback", new Bound(0, 10)),
            Map.entry("VentBurstVfxTicks", new Bound(1, 200)),
            Map.entry("VentWallPushTenths", new Bound(1, 40)),
            Map.entry("VentWallMode", new Bound(BossVentSettings.WALL_PUSH, BossVentSettings.WALL_PIN)),
            Map.entry("VentWallDamage", new Bound(0, 1000)),
            Map.entry("VentWallLift", new Bound(0, 20)),
            Map.entry("VentParticleBudget", new Bound(8, 200)),
            Map.entry("VentFlameSoundIntervalTicks", new Bound(1, 200)));

    /**
     * The cues' own prefixes. Their numbers are a volume, a pitch and a count, whose ranges
     * belong to the cue rather than to the vents, and are pinned by the cue's own test.
     */
    private static final List<String> CUE_PREFIXES = List.of(
            "VentHissSound", "VentBurstSound", "VentFlameSound", "VentWallSound", "VentFlameParticles",
            "VentSmokeParticles", "VentBurstParticles", "VentWallParticles", "VentWarnParticles");

    /**
     * The numbers a cue writes under its prefix, and nothing else: the flame's roar sits under
     * {@code VentFlameSound}, which the roar's own interval begins with too, so a prefix alone
     * would wave the interval through unbounded.
     */
    private static final List<String> CUE_NUMBER_SUFFIXES = List.of("Count", "Volume", "Pitch");

    private static boolean isCueNumber(String key) {
        for (String prefix : CUE_PREFIXES) {
            for (String suffix : CUE_NUMBER_SUFFIXES) {
                if (key.equals(prefix + suffix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A vent's own numbers, the same way: the list writes one compound per vent. */
    private static final Map<String, Bound> ZONE_BOUNDS = Map.ofEntries(
            Map.entry("CoordinateMode", new Bound(BossVentZone.COORDINATE_ARENA_OFFSET, BossVentZone.COORDINATE_FIXED)),
            Map.entry("X1", new Bound(-30000000, 30000000)),
            Map.entry("Y1", new Bound(-30000000, 30000000)),
            Map.entry("Z1", new Bound(-30000000, 30000000)),
            Map.entry("X2", new Bound(-30000000, 30000000)),
            Map.entry("Y2", new Bound(-30000000, 30000000)),
            Map.entry("Z2", new Bound(-30000000, 30000000)),
            Map.entry("Face", new Bound(BossVentZone.FACE_FLOOR, BossVentZone.FACE_EAST)),
            Map.entry("Reach", new Bound(1, 32)),
            Map.entry("ModeOverride", new Bound(BossVentZone.MODE_PHASE, BossVentSettings.MODE_WALL)),
            Map.entry("DelayTicks", new Bound(0, 1200)),
            Map.entry("Weight", new Bound(1, 100)));

    @Test
    @DisplayName("a boss saved before the vents reads them back switched off, at their defaults")
    void anOldSaveReadsTheDefaults() {
        BossPhaseData phase = new BossPhaseData();
        BossVentSettings changed = phase.vent();
        changed.setEnabled(true);
        changed.setAnimation("roar");
        changed.setMode(BossVentSettings.MODE_WALL);
        changed.setPattern(BossVentSettings.PATTERN_RANDOM);
        changed.setCycleTicks(40);
        changed.setRepeats(3);
        changed.setRecast(BossVentSettings.RECAST_STOP);
        changed.setWallMode(BossVentSettings.WALL_PIN);
        changed.setBurstVfx(AreaVfxStyles.STONE);
        changed.getZones().add();
        changed.getEffects().get(0).setEnabled(true);
        changed.castSpot().setMode(BossCastSpot.MODE_TELEPORT);
        CompoundTag tag = phase.writeToNBT();
        for (String key : List.copyOf(tag.getAllKeys())) {
            if (key.startsWith("Vent")) {
                tag.remove(key);
            }
        }
        // A save from before the vents never had their standing-cast bit either.
        tag.putLong("CastRootMask", BossPhaseData.CAST_ROOT_ALL & ~(1L << BossAbilityKind.VENT));

        // Read into the phase that had everything changed, so the load has to put the defaults
        // back rather than merely leave a fresh object at them.
        phase.readFromNBT(tag);
        BossVentSettings vent = phase.vent();
        assertFalse(vent.isEnabled(), "an old boss must not start firing vents on load");
        assertFalse(vent.canCast());
        assertEquals("", vent.getAnimation());
        assertEquals(20, vent.getActionDelayTicks());
        assertEquals(600, vent.getCooldownTicks());
        assertEquals(0, vent.getZones().size(), "an old boss has no vents");
        assertEquals(BossVentSettings.MODE_FLAME, vent.getMode());
        assertEquals(BossVentSettings.PATTERN_ALL, vent.getPattern());
        assertEquals(1, vent.getRandomCount());
        assertEquals(100, vent.getCycleTicks());
        assertEquals(20, vent.getWarnTicks());
        assertEquals(40, vent.getActiveTicks());
        assertEquals(0, vent.getRepeats(), "the timer runs until it is stopped");
        assertEquals(BossVentSettings.RECAST_RESTART, vent.getRecast());
        assertEquals(6, vent.getDamage());
        assertEquals(10, vent.getHitIntervalTicks());
        assertEquals(1, vent.getKnockback());
        assertEquals(AreaVfxStyles.FIRE, vent.getBurstVfx(), "a blast shows its fire unless told otherwise");
        assertEquals(10, vent.getBurstVfxTicks());
        assertEquals(8, vent.getWallPushTenths());
        assertEquals(BossVentSettings.WALL_PUSH, vent.getWallMode());
        assertEquals(0, vent.getWallDamage(), "a wall only holds unless told to hurt");
        assertEquals(2, vent.getWallLift());
        assertEquals(48, vent.getParticleBudget());
        assertEquals(10, vent.getFlameSoundIntervalTicks());
        assertEquals("minecraft:block.fire.extinguish", vent.getHissSound().getSoundId());
        assertEquals("minecraft:flame", vent.getFlameParticles().getParticleId());
        assertEquals(8, vent.getFlameParticles().getCount());
        assertEquals(BossParticleCue.DUST_ID, vent.getWarnParticles().getParticleId());
        assertFalse(vent.castSpot().isSet(), "an old boss casts from wherever it stands");
        assertFalse(vent.getEffects().isAnyEnabled());
        assertTrue(phase.isCastRooted(BossAbilityKind.VENT),
                "a save from before the vents gets their standing-cast bit, the way every new kind did");
    }

    @Test
    @DisplayName("every number the vents save comes back inside its own range")
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
    @DisplayName("no vent number is saved without a bound written out here")
    void everyNumberHasABound() {
        CompoundTag tag = new BossPhaseData().writeToNBT();
        // The spot's numbers belong to the cast spot, which has bounds and tests of its own.
        Set<String> numbers = tag.getAllKeys().stream()
                .filter(key -> key.startsWith("Vent") && !key.startsWith("VentSpot"))
                .filter(key -> !isCueNumber(key))
                .filter(key -> tag.get(key) instanceof IntTag)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(BOUNDS.keySet()), numbers,
                "a vent number was added or dropped without its bound being stated here");
    }

    @Test
    @DisplayName("every number a vent saves comes back inside its own range, and has a bound here")
    void everyZoneNumberIsClamped() {
        BossVentZoneList list = new BossVentZoneList();
        list.add();
        CompoundTag baseline = list.writeToNBT().getCompound(0);
        Set<String> numbers = baseline.getAllKeys().stream()
                .filter(key -> baseline.get(key) instanceof IntTag && !key.equals("ZoneId"))
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(ZONE_BOUNDS.keySet()), numbers,
                "a vent number was added or dropped without its bound being stated here");
        for (Map.Entry<String, Bound> entry : ZONE_BOUNDS.entrySet()) {
            for (int extreme : new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                CompoundTag poisoned = baseline.copy();
                poisoned.putInt(entry.getKey(), extreme);
                ListTag saved = new ListTag();
                saved.add(poisoned);
                BossVentZoneList reread = new BossVentZoneList();
                reread.readFromNBT(saved);
                int back = reread.writeToNBT().getCompound(0).getInt(entry.getKey());
                int expected = extreme > 0 ? entry.getValue().max() : entry.getValue().min();
                assertEquals(expected, back, entry.getKey() + " read " + extreme + " back as " + back);
            }
        }
    }

    @Test
    @DisplayName("a vent saved with only its corners reads the rest back at its defaults")
    void aBareZoneReadsTheDefaults() {
        CompoundTag bare = new CompoundTag();
        bare.putInt("X1", 3);
        bare.putInt("Z2", -2);
        ListTag saved = new ListTag();
        saved.add(bare);
        BossVentZoneList list = new BossVentZoneList();
        list.readFromNBT(saved);
        BossVentZone zone = list.get(0);
        assertTrue(zone.isEnabled());
        assertEquals(BossVentZone.COORDINATE_ARENA_OFFSET, zone.getCoordinateMode());
        assertEquals(3, zone.getX1());
        assertEquals(-2, zone.getZ2());
        assertEquals(BossVentZone.FACE_FLOOR, zone.getFace());
        assertEquals(4, zone.getReach());
        assertEquals(BossVentZone.MODE_PHASE, zone.getModeOverride(), "no mode of its own is the phase's");
        assertEquals(0, zone.getDelayTicks());
        assertEquals(1, zone.getWeight());
        assertTrue(zone.getZoneId() > 0);
    }

    @Test
    @DisplayName("a vent with no mode of its own takes the phase's, and one with a mode keeps it")
    void theModeFallsBackToThePhase() {
        BossVentZone zone = new BossVentZoneList().add();
        assertNotNull(zone);
        assertEquals(BossVentSettings.MODE_WALL, zone.modeIn(BossVentSettings.MODE_WALL));
        assertEquals(BossVentSettings.MODE_BURST, zone.modeIn(BossVentSettings.MODE_BURST));
        zone.setModeOverride(BossVentSettings.MODE_FLAME);
        assertEquals(BossVentSettings.MODE_FLAME, zone.modeIn(BossVentSettings.MODE_WALL));
        zone.setModeOverride(-5);
        assertEquals(BossVentZone.MODE_PHASE, zone.getModeOverride(), "below the list is the phase's");
    }

    @Test
    @DisplayName("the list holds sixteen vents, keeps its ids through a delete and a reload, and casts with one on")
    void theListIsBoundedAndKeepsItsIds() {
        BossVentSettings vent = new BossVentSettings();
        vent.setEnabled(true);
        assertFalse(vent.canCast(), "switched on with no vent is nothing to cast");
        BossVentZoneList zones = vent.getZones();
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < BossVentZoneList.MAX_ENTRIES; i++) {
            BossVentZone zone = zones.add();
            assertNotNull(zone);
            assertTrue(ids.add(zone.getZoneId()), "ids are unique");
        }
        assertNull(zones.add(), "the seventeenth vent is refused");
        assertTrue(vent.canCast());
        int second = zones.get(1).getZoneId();
        zones.remove(0);
        assertEquals(second, zones.get(0).getZoneId(), "deleting a row leaves the others' ids alone");
        for (BossVentZone zone : zones.entries()) {
            zone.setEnabled(false);
        }
        assertFalse(vent.canCast(), "every vent switched off is nothing to cast");
        assertFalse(vent.isConfigured());

        BossVentZoneList reread = new BossVentZoneList();
        reread.readFromNBT(zones.writeToNBT());
        assertEquals(zones.size(), reread.size());
        for (int i = 0; i < zones.size(); i++) {
            assertEquals(zones.get(i).getZoneId(), reread.get(i).getZoneId());
        }
    }

    @Test
    @DisplayName("the editor's setters hold the same ranges the save does")
    void settersClamp() {
        BossVentSettings vent = new BossVentSettings();
        vent.setActionDelayTicks(-5);
        vent.setCooldownTicks(0);
        vent.setMode(9);
        vent.setPattern(-1);
        vent.setRandomCount(99);
        vent.setCycleTicks(1);
        vent.setWarnTicks(999);
        vent.setActiveTicks(0);
        vent.setRepeats(-3);
        vent.setRecast(7);
        vent.setDamage(5000);
        vent.setHitIntervalTicks(0);
        vent.setKnockback(-1);
        vent.setBurstVfx("nonsense");
        vent.setBurstVfxTicks(999);
        vent.setWallPushTenths(0);
        vent.setWallMode(4);
        vent.setWallDamage(-4);
        vent.setWallLift(99);
        vent.setParticleBudget(1);
        vent.setFlameSoundIntervalTicks(0);
        assertEquals(0, vent.getActionDelayTicks());
        assertEquals(1, vent.getCooldownTicks());
        assertEquals(BossVentSettings.MODE_WALL, vent.getMode());
        assertEquals(BossVentSettings.PATTERN_ALL, vent.getPattern());
        assertEquals(16, vent.getRandomCount());
        assertEquals(5, vent.getCycleTicks());
        assertEquals(200, vent.getWarnTicks());
        assertEquals(1, vent.getActiveTicks());
        assertEquals(0, vent.getRepeats());
        assertEquals(BossVentSettings.RECAST_IGNORE, vent.getRecast());
        assertEquals(1000, vent.getDamage());
        assertEquals(1, vent.getHitIntervalTicks());
        assertEquals(0, vent.getKnockback());
        assertEquals(AreaVfxStyles.NONE, vent.getBurstVfx(), "an unknown style reads as none");
        assertEquals(200, vent.getBurstVfxTicks());
        assertEquals(1, vent.getWallPushTenths());
        assertEquals(BossVentSettings.WALL_PIN, vent.getWallMode());
        assertEquals(0, vent.getWallDamage());
        assertEquals(20, vent.getWallLift());
        assertEquals(8, vent.getParticleBudget());
        assertEquals(1, vent.getFlameSoundIntervalTicks());

        BossVentZone zone = new BossVentZoneList().add();
        assertNotNull(zone);
        zone.setFace(12);
        zone.setReach(0);
        zone.setModeOverride(9);
        zone.setDelayTicks(-1);
        zone.setWeight(1000);
        assertEquals(BossVentZone.FACE_EAST, zone.getFace());
        assertEquals(1, zone.getReach());
        assertEquals(BossVentSettings.MODE_WALL, zone.getModeOverride());
        assertEquals(0, zone.getDelayTicks());
        assertEquals(100, zone.getWeight());
    }

    @Test
    @DisplayName("the vents sit on every list a cast ability belongs to, and the copies may cast them")
    void theVentsAreOnTheSharedLists() {
        assertEquals(BossAbilityKind.COUNT - 1, BossAbilityKind.VENT, "the vents are the newest kind");
        assertEquals("cnpcgeckoaddon.boss.ability.vent", BossAbilityKind.LABELS[BossAbilityKind.VENT]);
        assertTrue(contains(BossAbilityKind.IMMUNITY_ABILITIES, BossAbilityKind.VENT), "an npc can be immune to them");
        assertTrue(contains(BossAbilityKind.LASTING_ABILITIES, BossAbilityKind.VENT), "their timer outlives the cast");
        assertTrue(contains(BossAbilityKind.COMBO_ABILITIES, BossAbilityKind.VENT), "they chain like any cast");
        assertTrue(contains(BossPhaseData.CAST_ROOT_ABILITIES, BossAbilityKind.VENT), "their wind-up can be rooted");
        assertTrue(contains(TeleportPathData.TELEGRAPH_ABILITIES, BossAbilityKind.VENT), "their wind-up warns");
        assertTrue(contains(BossShadowSettings.COPY_ABILITIES, BossAbilityKind.VENT), "a copy may be handed them");
        assertTrue(contains(BossRiftSettings.MEANWHILE_ABILITIES, BossAbilityKind.VENT),
                "and a rift may let the boss start them meanwhile");
        assertEquals(BossVentSettings.MODE_LABELS.length, BossVentSettings.MODE_WALL + 1);
        assertEquals(BossVentSettings.PATTERN_LABELS.length, BossVentSettings.PATTERN_RANDOM + 1);
        assertEquals(BossVentSettings.RECAST_LABELS.length, BossVentSettings.RECAST_IGNORE + 1);
        assertEquals(BossVentSettings.WALL_MODE_LABELS.length, BossVentSettings.WALL_PIN + 1);
        assertEquals(BossVentZone.FACE_LABELS.length, BossVentZone.FACE_EAST + 1);
        assertEquals(BossVentZone.MODE_LABELS.length, BossVentSettings.MODE_WALL + 2,
                "the phase's own mode and the three");
    }

    private static boolean contains(int[] list, int kind) {
        return Arrays.stream(list).anyMatch(entry -> entry == kind);
    }
}
