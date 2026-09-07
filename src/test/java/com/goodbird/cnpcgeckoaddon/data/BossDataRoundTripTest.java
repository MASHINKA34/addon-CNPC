package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the save round trip of the boss configuration: write, read, write again has to
 * reproduce the identical tag, and a boss with no saved keys at all has to come out with
 * stable defaults. The bosses on the live server exist only as these tags, so an asymmetry
 * here is how their settings would silently rot on every edit-and-save.
 */
class BossDataRoundTripTest {

    @Test
    @DisplayName("a configured boss survives write -> read -> write unchanged")
    void bossSettingsSurviveTheSaveRoundTrip() {
        TeleportPathData first = configuredBoss();

        CompoundTag once = first.writeToNBT(new CompoundTag());
        TeleportPathData reread = new TeleportPathData();
        reread.readFromNBT(once);

        assertEquals(once, reread.writeToNBT(new CompoundTag()),
                "write -> read -> write should reproduce the identical boss tag");
    }

    @Test
    @DisplayName("a boss saved before these keys existed loads to stable defaults")
    void missingKeysProduceStableDefaults() {
        TeleportPathData fromEmpty = new TeleportPathData();
        fromEmpty.readFromNBT(new CompoundTag());

        CompoundTag once = fromEmpty.writeToNBT(new CompoundTag());
        TeleportPathData reread = new TeleportPathData();
        reread.readFromNBT(once);

        assertEquals(once, reread.writeToNBT(new CompoundTag()),
                "the defaults an empty tag produces should survive their own round trip");
        assertFalse(fromEmpty.isEnabled(), "an empty tag should leave the boss disabled");
    }

    @Test
    @DisplayName("an npc that has never been a boss stores no boss keys")
    void untouchedNpcStoresNoBossBlock() {
        CompoundTag untouched = new TeleportPathData().writeToNBT(new CompoundTag());
        assertTrue(untouched.isEmpty(),
                "an npc that has never been a boss should store no boss keys at all");

        TeleportPathData opened = new TeleportPathData();
        opened.markConfigured();
        assertFalse(opened.writeToNBT(new CompoundTag()).isEmpty(),
                "opening the boss screen should start storing the settings");

        TeleportPathData disabled = new TeleportPathData();
        disabled.setEnabled(true);
        disabled.getPhase(1).areaAttack().setDamage(42);
        CompoundTag saved = disabled.writeToNBT(new CompoundTag());
        TeleportPathData reread = new TeleportPathData();
        reread.readFromNBT(saved);
        reread.setEnabled(false);
        TeleportPathData afterToggle = new TeleportPathData();
        afterToggle.readFromNBT(reread.writeToNBT(new CompoundTag()));
        assertEquals(42, afterToggle.getPhase(1).areaAttack().getDamage(),
                "switching a configured boss off must not drop its settings");

        TeleportPathData legacy = new TeleportPathData();
        legacy.markConfigured();
        CompoundTag allDefaults = legacy.writeToNBT(new CompoundTag());
        TeleportPathData migrated = new TeleportPathData();
        migrated.readFromNBT(allDefaults);
        assertTrue(migrated.writeToNBT(new CompoundTag()).isEmpty(),
                "a block of nothing but defaults should be dropped on the next save");
    }

    @Test
    @DisplayName("the npc-side settings survive their own round trip")
    void npcSideSettingsSurviveTheSaveRoundTrip() {
        NpcCarryData carry = new NpcCarryData();
        carry.setCarryable(true);
        carry.setRequireSneak(false);
        carry.setRequiredItem("minecraft:torch");
        carry.setSlownessPercent(45);
        carry.setLeashRadius(24);
        CompoundTag carryOnce = carry.writeToNBT(new CompoundTag());
        NpcCarryData carryReread = new NpcCarryData();
        carryReread.readFromNBT(carryOnce);
        assertEquals(carryOnce, carryReread.writeToNBT(new CompoundTag()),
                "the carry settings should survive their round trip");

        NpcImmunityData immunity = new NpcImmunityData();
        immunity.setImmuneTo(BossAbilityKind.HOOK, true);
        immunity.setImmuneTo(BossAbilityKind.GEYSER, true);
        immunity.getResist(0).setMatcher("scorchedguns:*");
        immunity.getResist(0).setPercent(20);
        // Left in a middle slot on purpose: saving keeps only the set rules, so it has to
        // come back as the second rule rather than the fourth.
        immunity.getResist(3).setMatcher("*");
        immunity.getResist(3).setPercent(50);
        CompoundTag immunityOnce = immunity.writeToNBT(new CompoundTag());
        NpcImmunityData immunityReread = new NpcImmunityData();
        immunityReread.readFromNBT(immunityOnce);
        assertEquals(immunityOnce, immunityReread.writeToNBT(new CompoundTag()),
                "the immunity mask should survive its round trip");
        assertTrue(immunityReread.isImmuneTo(BossAbilityKind.HOOK));
        assertFalse(immunityReread.isImmuneTo(BossAbilityKind.MELEE));
        assertEquals("scorchedguns:*", immunityReread.getResist(0).getMatcher());
        assertEquals(20, immunityReread.getResist(0).getPercent());
        assertEquals("*", immunityReread.getResist(1).getMatcher());
        assertEquals(50, immunityReread.getResist(1).getPercent());
        assertFalse(immunityReread.getResist(2).isSet(),
                "set rules should come back packed in order, the rest empty");
    }

    /**
     * A boss with a little of everything switched on, so the trip drags the nested
     * structures - phases, spawn points, totems, loot - along with it.
     */
    private static TeleportPathData configuredBoss() {
        TeleportPathData data = new TeleportPathData();
        data.setEnabled(true);
        data.setPhaseCount(4);
        data.setTelegraphAbilities(TeleportPathData.TELEGRAPH_ALL_ABILITIES
                & ~(1 << BossAbilityKind.MELEE));

        BossPhaseData phase = data.getPhase(1);
        phase.areaAttack().setEnabled(true);
        phase.areaAttack().setDamage(7);
        phase.lineAttack().setEnabled(true);
        phase.lineAttack().setLength(24);
        phase.lineAttack().setFaceAxis(false);
        phase.geyser().setEnabled(true);
        phase.boulder().setEnabled(true);
        phase.boulder().setBlock("minecraft:deepslate");
        phase.boulder().setStyle(BoulderStyles.MAGMA);
        phase.boulder().setMode(BossPhaseData.BOULDER_MODE_THROW);
        phase.boulder().setStopsOnHit(true);
        phase.hook().setEnabled(true);
        phase.hook().setTargetCount(3);
        phase.setCastRooted(BossAbilityKind.HOOK, false);
        phase.tether().setEnabled(true);
        phase.tether().setAnchor(BossPhaseData.TETHER_ANCHOR_PAIR);
        phase.tether().setTargetCount(3);
        phase.tether().setPull(5);
        phase.gravity().setEnabled(true);
        phase.gravity().setMode(BossPhaseData.GRAVITY_MODE_LIFT);
        phase.gravity().setStrength(15);
        phase.setCastRooted(BossAbilityKind.GRAVITY, false);
        phase.mark().setEnabled(true);
        phase.mark().setMode(BossPhaseData.MARK_MODE_SPREAD);
        phase.mark().setFollow(true);
        phase.mark().setMinPlayers(4);
        phase.mark().setSelfDamage(7);
        phase.setCastRooted(BossAbilityKind.MARK, false);
        phase.cover().setEnabled(true);
        phase.cover().setMode(BossPhaseData.COVER_MODE_SHELTER);
        phase.cover().setRange(60);
        phase.cover().setShelterCount(3);
        phase.cover().setShelterRing(6, 20);
        phase.setCastRooted(BossAbilityKind.COVER, false);
        phase.hazard().setEnabled(true);
        phase.hazard().setMode(BossPhaseData.HAZARD_MODE_BOX);
        phase.hazard().setCenterMode(BossPhaseData.HAZARD_CENTER_POINT);
        phase.hazard().setCenter(-120, 45);
        phase.hazard().setRadii(40, 5);
        phase.hazard().setCorner1(-10, 60, -10);
        phase.hazard().setCorner2(10, 70, 10);
        phase.hazard().setIntervalTicks(10);
        phase.hunt().setEnabled(true);
        phase.hunt().setTargetMode(BossTargetMode.RANDOM);
        phase.hunt().setDurationTicks(300);
        phase.hunt().setSpeedPercent(180);
        phase.hunt().setCatchEnds(false);
        phase.hunt().setSilence(true);
        phase.hunt().setGlow(false);
        phase.setCastRooted(BossAbilityKind.HUNT, false);
        phase.beam().setEnabled(true);
        phase.beam().setCount(3);
        phase.beam().setLength(30);
        phase.beam().setWidth(2);
        phase.beam().setDegreesPerSecond(-45);
        phase.beam().setStartMode(BossPhaseData.BEAM_START_RANDOM);
        phase.beam().setFollowsBoss(false);
        phase.beam().setStopsAtWalls(false);
        phase.setCastRooted(BossAbilityKind.BEAM, false);
        phase.cocoon().setEnabled(true);
        phase.cocoon().setTargetMode(BossTargetMode.FARTHEST);
        phase.cocoon().setTargetCount(3);
        phase.cocoon().setCloneTab(4);
        phase.cocoon().setCloneName("silk");
        phase.cocoon().setRescueMode(BossPhaseData.COCOON_RESCUE_STAND);
        phase.cocoon().setRescueRadius(5);
        phase.cocoon().setRescueTicks(100);
        phase.cocoon().setDurationTicks(600);
        phase.cocoon().setFailDamage(55);
        phase.cocoon().setGuardTab(2);
        phase.cocoon().setGuardName("spider");
        phase.setCastRooted(BossAbilityKind.COCOON, false);
        phase.summon().setSpawnMode(BossPhaseData.MINION_SPAWN_POINTS_THEN_RANDOM);
        phase.summon().getSpawnPoints().add();
        phase.summon().getSpawnPoints().add();

        data.getTotems().add();
        data.setChestEnabled(true);
        data.setChestLifetimeTicks(1200);
        return data;
    }
}

