package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rift's settings on their own: what a boss saved before the rift reads back as, the mask of
 * what the boss may cast meanwhile, the way out that asks for what the phase has, and the colours
 * a screen types in as hex.
 */
class BossRiftSettingsTest {

    @Test
    @DisplayName("a phase saved before the rift reads back with it off, rooted and at every default")
    void anOldSaveReadsAsOff() {
        BossPhaseData old = new BossPhaseData();
        CompoundTag tag = old.writeToNBT();
        for (String key : tag.getAllKeys().toArray(String[]::new)) {
            if (key.startsWith("Rift")) {
                tag.remove(key);
            }
        }
        // A cast-root mask from before the rift: every bit it knew, none of the rift's.
        tag.putLong("CastRootMask", BossPhaseData.CAST_ROOT_ALL & ~(1L << BossAbilityKind.RIFT));
        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(tag);
        BossRiftSettings rift = reread.rift();
        assertFalse(rift.isEnabled());
        assertFalse(rift.canCast());
        assertTrue(reread.isCastRooted(BossAbilityKind.RIFT), "the new bit takes the rooted default");
        assertEquals(30, rift.getActionDelayTicks());
        assertEquals(1200, rift.getCooldownTicks());
        assertEquals(BossTargetMode.MAIN, rift.getTargetMode());
        assertEquals(1, rift.getTargetCount());
        assertEquals(BossRiftSettings.EXIT_SURVIVE, rift.getExitMode());
        assertEquals(1200, rift.getTimeLimitTicks());
        assertTrue(rift.isFailOnDeath());
        assertEquals(1, rift.getSoloMaxPlayers());
        assertEquals(200, rift.getSoloVulnerableTicks());
        assertEquals(150, rift.getSoloVulnerablePercent());
        assertEquals(50, rift.getGroupDamagePercent());
        assertEquals(0L, rift.getAbilities());
        assertEquals(BossRiftSettings.ARENA_BUILT, rift.getArenaMode());
        assertEquals(0, rift.getSlot());
        assertEquals(64, rift.getArenaY());
        assertEquals(64, rift.getPlatformY());
        assertEquals(16, rift.getPlatformRadius());
        assertEquals("minecraft:end_stone", rift.getPlatformBlock());
        assertEquals("minecraft:obsidian", rift.getWallBlock());
        assertEquals("minecraft:sea_lantern", rift.getLightBlock());
        assertEquals(4, rift.getWallHeight());
        assertEquals(6, rift.getLightSpacing());
        assertFalse(rift.isPlatformRoof());
        assertEquals(0, rift.getLeashRadius());
        assertEquals(8, rift.getFallGuardDepth());
        assertEquals(3, rift.getMinionCount());
        assertEquals(6, rift.getMinionRadius());
        assertTrue(rift.isMinionRemoveOnEnd());
        assertEquals(0, rift.getCrystalPoints().size(), "no zones: the crystals stand on a ring");
        assertEquals(4, rift.getCrystalCount());
        assertEquals(8, rift.getCrystalRingRadius());
        assertEquals(20, rift.getCrystalHoverTenths());
        assertEquals(2.0D, rift.crystalHover(), 1.0E-9D, "two blocks over the floor");
        assertEquals("minecraft:amethyst_cluster", rift.getCrystalBlock());
        assertEquals(0xB47AFF, rift.getCrystalColor());
        assertTrue(rift.isCrystalGlow(), "an absent key is a save from before the crystals: they glow");
        assertEquals(3, rift.getCrystalSpinDegrees());
        assertEquals(3, rift.getCrystalBobTenths());
        assertEquals(40, rift.getCrystalBobPeriodTicks());
        assertEquals(10, rift.getCrystalScaleTenths());
        assertEquals(BossRiftSettings.COLLECT_ZONE, rift.getCrystalCollectMode());
        assertEquals(15, rift.getCrystalCollectRadiusTenths());
        assertTrue(rift.isCrystalZoneRing());
        assertEquals(10, rift.getCrystalAmbientIntervalTicks());
        assertEquals("minecraft:block.amethyst_block.chime", rift.getCrystalCollectSound().getSoundId());
        assertEquals("minecraft:end_rod", rift.getCrystalCollectParticles().getParticleId());
        assertEquals(BossParticleCue.DUST_ID, rift.getCrystalAmbientParticles().getParticleId());
        assertFalse(rift.isFailRage());
        assertEquals(0, rift.getFailArenaDamage());
        assertEquals(32, rift.getFailArenaRadius());
        assertEquals(0, rift.getFailHealPercent());
        assertEquals(0x6A00B4, rift.getTintColor());
        assertEquals(35, rift.getTintAlpha());
        assertEquals(40, rift.getTintPulseTicks());
        assertEquals(0x2B003F, rift.getFogColor());
        assertEquals(24, rift.getFogDistance());
        assertEquals(60, rift.getLoopIntervalTicks());
        assertEquals(20, rift.getAmbientIntervalTicks());
        assertEquals("minecraft:entity.enderman.teleport", rift.getCutSound().getSoundId());
        assertEquals("minecraft:reverse_portal", rift.getCutParticles().getParticleId());
    }

    @Test
    @DisplayName("what the boss may cast meanwhile never holds a second rift, a summon or the copies")
    void theMeanwhileMaskLeavesThreeOut() {
        BossRiftSettings rift = new BossRiftSettings();
        rift.setAbilities(-1L);
        assertEquals(BossRiftSettings.MEANWHILE_ALL, rift.getAbilities());
        assertFalse(rift.castsMeanwhile(BossAbilityKind.RIFT));
        assertFalse(rift.castsMeanwhile(BossAbilityKind.SUMMON));
        assertFalse(rift.castsMeanwhile(BossAbilityKind.SHADOW));
        assertFalse(rift.castsMeanwhile(BossAbilityKind.BLAST), "not cast, so never listed");
        assertFalse(rift.castsMeanwhile(BossAbilityKind.HAZARD));
        assertTrue(rift.castsMeanwhile(BossAbilityKind.RANGED));
        assertEquals(BossAbilityKind.COMBO_ABILITIES.length - 3, BossRiftSettings.MEANWHILE_ABILITIES.length);

        rift.setAbilities(0L);
        rift.setCastsMeanwhile(BossAbilityKind.RANGED, true);
        rift.setCastsMeanwhile(BossAbilityKind.SUMMON, true);
        assertEquals(1L << BossAbilityKind.RANGED, rift.getAbilities(), "the summon's bit is dropped");
        rift.setCastsMeanwhile(-1, true);
        rift.setCastsMeanwhile(BossAbilityKind.COUNT, true);
        assertEquals(1L << BossAbilityKind.RANGED, rift.getAbilities(), "out of range is ignored");
    }

    @Test
    @DisplayName("a minion rift needs a clone; the others, and the crystal ones for now, need nothing")
    void theWayOutAsksForWhatItNeeds() {
        BossRiftSettings rift = new BossRiftSettings();
        assertTrue(rift.isConfigured(), "surviving the time needs nothing");
        rift.setExitMode(BossRiftSettings.EXIT_MINIONS);
        assertTrue(rift.needsMinions());
        assertFalse(rift.isConfigured(), "no clone to spawn minions from");
        rift.setEnabled(true);
        assertFalse(rift.canCast(), "switched on is not enough");
        rift.getMinionPoints().add().setCloneNameOverride("guard");
        assertTrue(rift.isConfigured(), "a point with a clone of its own is enough");
        rift.getMinionPoints().clear();
        rift.setMinionCloneName("guard");
        assertTrue(rift.canCast());

        assertEquals(BossRiftSettings.EXIT_SURVIVE, BossRiftSettings.effectiveExitMode(BossRiftSettings.EXIT_CRYSTALS, false),
                "a build with no crystals runs the crystal way out as survival");
        assertEquals(BossRiftSettings.EXIT_SURVIVE, BossRiftSettings.effectiveExitMode(BossRiftSettings.EXIT_BOTH, false));
        assertEquals(BossRiftSettings.EXIT_MINIONS, BossRiftSettings.effectiveExitMode(BossRiftSettings.EXIT_MINIONS, false));
        assertEquals(BossRiftSettings.EXIT_CRYSTALS, BossRiftSettings.effectiveExitMode(BossRiftSettings.EXIT_CRYSTALS, true));
        assertEquals(BossRiftSettings.EXIT_BOTH, BossRiftSettings.effectiveExitMode(99, true), "clamped");

        rift.setExitMode(BossRiftSettings.EXIT_CRYSTALS);
        assertTrue(rift.needsCrystals());
        assertFalse(rift.needsMinions(), "gathering crystals stands no minions up");
        rift.setMinionCloneName("");
        assertTrue(rift.isConfigured(), "and needs no clone: the crystals stand on a ring by themselves");
        rift.setExitMode(BossRiftSettings.EXIT_BOTH);
        assertTrue(rift.needsCrystals());
        assertTrue(rift.needsMinions());
        assertFalse(rift.isConfigured(), "both asks for the minions' clone as well");
        rift.setMinionCloneName("guard");
        assertTrue(rift.isConfigured());
    }

    @Test
    @DisplayName("a colour typed as hex is read with or without a prefix, and anything else keeps the old one")
    void hexColours() {
        assertEquals(0x6A00B4, BossRiftSettings.parseHex("6A00B4", 0));
        assertEquals(0x2B003F, BossRiftSettings.parseHex("#2b003f", 0));
        assertEquals(0xFF, BossRiftSettings.parseHex("0xFF", 0));
        assertEquals(0xFF, BossRiftSettings.parseHex(" ff ", 0));
        assertEquals(7, BossRiftSettings.parseHex("zz", 7));
        assertEquals(7, BossRiftSettings.parseHex("", 7));
        assertEquals(7, BossRiftSettings.parseHex("#", 7));
        assertEquals(7, BossRiftSettings.parseHex(null, 7));
        assertEquals(7, BossRiftSettings.parseHex("1234567", 7), "more than six digits is not a colour");
        assertEquals(7, BossRiftSettings.parseHex("-12", 7), "nor is a negative number");
        assertEquals("6A00B4", BossRiftSettings.hex(0x6A00B4));
        assertEquals("00000F", BossRiftSettings.hex(15));
        assertEquals("FFFFFF", BossRiftSettings.hex(Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("every setting survives a save, and a copy is its own object")
    void roundTripAndCopy() {
        BossPhaseData phase = new BossPhaseData();
        BossRiftSettings rift = phase.rift();
        rift.setEnabled(true);
        rift.setExitMode(BossRiftSettings.EXIT_MINIONS);
        rift.setMinionCloneName("guard");
        rift.setTimeLimitTicks(600);
        rift.setArenaMode(BossRiftSettings.ARENA_PREBUILT);
        rift.setArenaX(-1234);
        rift.setArenaY(80);
        rift.setArenaZ(5678);
        rift.setSlot(5);
        rift.setPlatformBlock("minecraft:stone");
        rift.setWallBlock("");
        rift.setTintColor(0x123456);
        rift.setCastsMeanwhile(BossAbilityKind.RANGED, true);
        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(phase.writeToNBT());
        BossRiftSettings back = reread.rift();
        assertTrue(back.isEnabled());
        assertEquals(BossRiftSettings.EXIT_MINIONS, back.getExitMode());
        assertEquals("guard", back.getMinionCloneName());
        assertEquals(600, back.getTimeLimitTicks());
        assertTrue(back.isPrebuilt());
        assertEquals(-1234, back.getArenaX());
        assertEquals(80, back.getArenaY());
        assertEquals(5678, back.getArenaZ());
        assertEquals(5, back.getSlot());
        assertEquals("minecraft:stone", back.getPlatformBlock());
        assertEquals("minecraft:obsidian", back.getWallBlock(), "an emptied block id is the default again");
        assertEquals(0x123456, back.getTintColor());
        assertTrue(back.castsMeanwhile(BossAbilityKind.RANGED));

        BossRiftSettings copy = rift.copy();
        rift.setTimeLimitTicks(900);
        assertEquals(600, copy.getTimeLimitTicks(), "the copy keeps what it was given");
    }
}
