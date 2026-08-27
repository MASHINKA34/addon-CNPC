package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.NpcCarryData;
import com.goodbird.cnpcgeckoaddon.data.NpcImmunityData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * Pins the save round trip of the boss configuration: write, read, write again has to
 * reproduce the identical tag, and a boss with no saved keys at all has to come out with
 * stable defaults. The bosses on the live server exist only as these tags, so an asymmetry
 * here is how their settings would silently rot on every edit-and-save.
 */
@GameTestHolder(CNPCGeckoAddon.MODID)
public class BossDataRoundTripGameTest {

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void bossSettingsSurviveTheSaveRoundTrip(GameTestHelper helper) {
        TeleportPathData first = configuredBoss();

        CompoundTag once = first.writeToNBT(new CompoundTag());
        TeleportPathData reread = new TeleportPathData();
        reread.readFromNBT(once);
        CompoundTag twice = reread.writeToNBT(new CompoundTag());

        helper.assertTrue(once.equals(twice),
                "write -> read -> write should reproduce the identical boss tag");
        helper.succeed();
    }

    /** A boss written by an older version - no keys at all - loads stably too. */
    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void missingKeysProduceStableDefaults(GameTestHelper helper) {
        TeleportPathData fromEmpty = new TeleportPathData();
        fromEmpty.readFromNBT(new CompoundTag());

        CompoundTag once = fromEmpty.writeToNBT(new CompoundTag());
        TeleportPathData reread = new TeleportPathData();
        reread.readFromNBT(once);

        helper.assertTrue(once.equals(reread.writeToNBT(new CompoundTag())),
                "the defaults an empty tag produces should survive their own round trip");
        helper.assertFalse(fromEmpty.isEnabled(), "an empty tag should leave the boss disabled");
        helper.succeed();
    }

    @GameTest(template = "fluid_platform", timeoutTicks = 100)
    public static void npcSideSettingsSurviveTheSaveRoundTrip(GameTestHelper helper) {
        NpcCarryData carry = new NpcCarryData();
        carry.setCarryable(true);
        carry.setRequireSneak(false);
        carry.setRequiredItem("minecraft:torch");
        carry.setSlownessPercent(45);
        carry.setLeashRadius(24);
        CompoundTag carryOnce = carry.writeToNBT(new CompoundTag());
        NpcCarryData carryReread = new NpcCarryData();
        carryReread.readFromNBT(carryOnce);
        helper.assertTrue(carryOnce.equals(carryReread.writeToNBT(new CompoundTag())),
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
        helper.assertTrue(immunityOnce.equals(immunityReread.writeToNBT(new CompoundTag())),
                "the immunity mask should survive its round trip");
        helper.assertTrue(immunityReread.isImmuneTo(BossAbilityKind.HOOK)
                        && !immunityReread.isImmuneTo(BossAbilityKind.MELEE),
                "exactly the bits that were set should come back set");
        helper.assertTrue(immunityReread.getResist(0).getMatcher().equals("scorchedguns:*")
                        && immunityReread.getResist(0).getPercent() == 20,
                "the first damage resistance rule should come back as written");
        helper.assertTrue(immunityReread.getResist(1).getMatcher().equals("*")
                        && immunityReread.getResist(1).getPercent() == 50
                        && !immunityReread.getResist(2).isSet(),
                "set rules should come back packed in order, the rest empty");
        helper.succeed();
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
        phase.setBoulderMode(BossPhaseData.BOULDER_MODE_THROW);
        phase.setBoulderStopsOnHit(true);
        phase.setHookEnabled(true);
        phase.setHookTargetCount(3);
        phase.setCastRooted(BossAbilityKind.HOOK, false);
        phase.setMinionSpawnMode(BossPhaseData.MINION_SPAWN_POINTS_THEN_RANDOM);
        phase.getMinionSpawnPoints().add();
        phase.getMinionSpawnPoints().add();

        data.getTotems().add();
        data.setChestEnabled(true);
        data.setChestLifetimeTicks(1200);
        return data;
    }
}
