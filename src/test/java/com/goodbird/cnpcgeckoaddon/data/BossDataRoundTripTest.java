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
        disabled.getPhase(1).setAreaAttackDamage(42);
        CompoundTag saved = disabled.writeToNBT(new CompoundTag());
        TeleportPathData reread = new TeleportPathData();
        reread.readFromNBT(saved);
        reread.setEnabled(false);
        TeleportPathData afterToggle = new TeleportPathData();
        afterToggle.readFromNBT(reread.writeToNBT(new CompoundTag()));
        assertEquals(42, afterToggle.getPhase(1).getAreaAttackDamage(),
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
        phase.setAreaAttackEnabled(true);
        phase.setAreaAttackDamage(7);
        phase.setLineAttackEnabled(true);
        phase.setLineAttackLength(24);
        phase.setLineAttackFaceAxis(false);
        phase.setGeyserEnabled(true);
        phase.setBoulderEnabled(true);
        phase.setBoulderBlock("minecraft:deepslate");
        phase.setBoulderStyle(BoulderStyles.MAGMA);
        phase.setBoulderMode(BossPhaseData.BOULDER_MODE_THROW);
        phase.setBoulderStopsOnHit(true);
        phase.setHookEnabled(true);
        phase.setHookTargetCount(3);
        phase.setCastRooted(BossAbilityKind.HOOK, false);
        phase.setTetherEnabled(true);
        phase.setTetherAnchor(BossPhaseData.TETHER_ANCHOR_PAIR);
        phase.setTetherTargetCount(3);
        phase.setTetherPull(5);
        phase.setGravityEnabled(true);
        phase.setGravityMode(BossPhaseData.GRAVITY_MODE_LIFT);
        phase.setGravityStrength(15);
        phase.setCastRooted(BossAbilityKind.GRAVITY, false);
        phase.setMarkEnabled(true);
        phase.setMarkMode(BossPhaseData.MARK_MODE_SPREAD);
        phase.setMarkFollow(true);
        phase.setMarkMinPlayers(4);
        phase.setMarkSelfDamage(7);
        phase.setCastRooted(BossAbilityKind.MARK, false);
        phase.setCoverEnabled(true);
        phase.setCoverMode(BossPhaseData.COVER_MODE_SHELTER);
        phase.setCoverRange(60);
        phase.setCoverShelterCount(3);
        phase.setCoverShelterRing(6, 20);
        phase.setCastRooted(BossAbilityKind.COVER, false);
        phase.setHazardEnabled(true);
        phase.setHazardMode(BossPhaseData.HAZARD_MODE_BOX);
        phase.setHazardCenterMode(BossPhaseData.HAZARD_CENTER_POINT);
        phase.setHazardCenter(-120, 45);
        phase.setHazardRadii(40, 5);
        phase.setHazardCorner1(-10, 60, -10);
        phase.setHazardCorner2(10, 70, 10);
        phase.setHazardIntervalTicks(10);
        phase.setHuntEnabled(true);
        phase.setHuntTargetMode(BossTargetMode.RANDOM);
        phase.setHuntDurationTicks(300);
        phase.setHuntSpeedPercent(180);
        phase.setHuntCatchEnds(false);
        phase.setHuntSilence(true);
        phase.setHuntGlow(false);
        phase.setCastRooted(BossAbilityKind.HUNT, false);
        phase.setBeamEnabled(true);
        phase.setBeamCount(3);
        phase.setBeamLength(30);
        phase.setBeamWidth(2);
        phase.setBeamDegreesPerSecond(-45);
        phase.setBeamStartMode(BossPhaseData.BEAM_START_RANDOM);
        phase.setBeamFollowsBoss(false);
        phase.setBeamStopsAtWalls(false);
        phase.setCastRooted(BossAbilityKind.BEAM, false);
        phase.setCocoonEnabled(true);
        phase.setCocoonTargetMode(BossTargetMode.FARTHEST);
        phase.setCocoonTargetCount(3);
        phase.setCocoonCloneTab(4);
        phase.setCocoonCloneName("silk");
        phase.setCocoonRescueMode(BossPhaseData.COCOON_RESCUE_STAND);
        phase.setCocoonRescueRadius(5);
        phase.setCocoonRescueTicks(100);
        phase.setCocoonDurationTicks(600);
        phase.setCocoonFailDamage(55);
        phase.setCocoonGuardTab(2);
        phase.setCocoonGuardName("spider");
        phase.setCastRooted(BossAbilityKind.COCOON, false);
        phase.setMinionSpawnMode(BossPhaseData.MINION_SPAWN_POINTS_THEN_RANDOM);
        phase.getMinionSpawnPoints().add();
        phase.getMinionSpawnPoints().add();

        data.getTotems().add();
        data.setChestEnabled(true);
        data.setChestLifetimeTicks(1200);
        return data;
    }
}

