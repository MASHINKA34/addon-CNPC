package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The damage and target half of an ability's profile: the type its hits are dealt as, the
 * shares of the victim's health, the party's bonus and its ceiling, and whom the ability may
 * land on - what each saves as, what an older profile reads back as, and the ranges a tag may
 * put into them.
 */
class BossAbilityProfileDamageTargetsTest {

    private static final String[] KEYS = {"DamageType", "DamagePercentMax", "DamagePercentCurrent",
            "PartyDamagePercent", "PartyDamageCap", "TargetKind"};

    @Test
    @DisplayName("a fresh profile deals a plain mob attack for its number, aimed as the boss aims")
    void defaultsAreTheOldHit() {
        BossAbilityProfile profile = new BossAbilityProfile();
        assertEquals(BossAbilityProfile.DAMAGE_NORMAL, profile.getDamageType());
        assertEquals(0, profile.getDamagePercentMax());
        assertEquals(0, profile.getDamagePercentCurrent());
        assertEquals(0, profile.getPartyDamagePercent());
        assertEquals(BossAbilityProfile.DEFAULT_PARTY_DAMAGE_CAP, profile.getPartyDamageCapPercent());
        assertEquals(BossAbilityProfile.TARGET_KIND_AS_BOSS, profile.getTargetKind());
        assertTrue(profile.isDefault());
    }

    @Test
    @DisplayName("a profile saved before these fields reads them back as the defaults")
    void anOlderProfileReadsTheDefaults() {
        CompoundTag older = new CompoundTag();
        older.putInt("Priority", 4);
        BossAbilityProfile profile = new BossAbilityProfile();
        profile.setDamageType(BossAbilityProfile.DAMAGE_TRUE);
        profile.setDamagePercentMax(30);
        profile.setDamagePercentCurrent(40);
        profile.setPartyDamagePercent(50);
        profile.setPartyDamageCapPercent(60);
        profile.setTargetKind(TeleportPathData.ABILITY_TARGET_ALL);
        profile.readFromNBT(older);
        assertEquals(4, profile.getPriority());
        profile.setPriority(0);
        assertTrue(profile.isDefault(), "an absent key has to read as its default, not keep what was there: "
                + profile);
    }

    @Test
    @DisplayName("every field round-trips through the phase, under its own key")
    void theFieldsRoundTrip() {
        BossPhaseData phase = new BossPhaseData();
        BossAbilityProfile geyser = phase.profile(BossAbilityKind.GEYSER);
        geyser.setDamageType(BossAbilityProfile.DAMAGE_PIERCING);
        geyser.setDamagePercentMax(10);
        geyser.setDamagePercentCurrent(50);
        geyser.setPartyDamagePercent(25);
        geyser.setPartyDamageCapPercent(75);
        geyser.setTargetKind(TeleportPathData.ABILITY_TARGET_PLAYERS);
        // Each one alone is enough to have the profile written.
        phase.profile(BossAbilityKind.AREA).setTargetKind(TeleportPathData.ABILITY_TARGET_PLAYERS_AND_NPCS);
        phase.profile(BossAbilityKind.BEAM).setPartyDamageCapPercent(0);

        CompoundTag tag = phase.writeToNBT();
        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(tag);
        BossAbilityProfile back = reread.profile(BossAbilityKind.GEYSER);
        assertEquals(BossAbilityProfile.DAMAGE_PIERCING, back.getDamageType());
        assertEquals(10, back.getDamagePercentMax());
        assertEquals(50, back.getDamagePercentCurrent());
        assertEquals(25, back.getPartyDamagePercent());
        assertEquals(75, back.getPartyDamageCapPercent());
        assertEquals(TeleportPathData.ABILITY_TARGET_PLAYERS, back.getTargetKind());
        assertEquals(TeleportPathData.ABILITY_TARGET_PLAYERS_AND_NPCS,
                reread.profile(BossAbilityKind.AREA).getTargetKind());
        assertEquals(0, reread.profile(BossAbilityKind.BEAM).getPartyDamageCapPercent());
        assertTrue(reread.profile(BossAbilityKind.MELEE).isDefault(), "an untouched ability reads back untouched");
        assertFalse(reread.profile(BossAbilityKind.BEAM).isDefault(), "a ceiling of nought is a change of its own");
        assertEquals(tag, reread.writeToNBT(), "a re-read phase saves the same tag again");

        CompoundTag written = new CompoundTag();
        geyser.writeToNBT(written);
        for (String key : KEYS) {
            assertTrue(written.contains(key), "the profile does not write " + key);
        }
    }

    @Test
    @DisplayName("every number a tag holds is clamped into its range, and so is every setter")
    void theNumbersAreClamped() {
        CompoundTag high = new CompoundTag();
        CompoundTag low = new CompoundTag();
        for (String key : KEYS) {
            high.putInt(key, Integer.MAX_VALUE);
            low.putInt(key, Integer.MIN_VALUE);
        }
        BossAbilityProfile profile = new BossAbilityProfile();
        profile.readFromNBT(high);
        assertEquals(BossAbilityProfile.DAMAGE_TRUE, profile.getDamageType());
        assertEquals(BossAbilityProfile.MAX_DAMAGE_PERCENT, profile.getDamagePercentMax());
        assertEquals(BossAbilityProfile.MAX_DAMAGE_PERCENT, profile.getDamagePercentCurrent());
        assertEquals(BossAbilityProfile.MAX_PARTY_DAMAGE_PERCENT, profile.getPartyDamagePercent());
        assertEquals(BossAbilityProfile.MAX_PARTY_DAMAGE_CAP, profile.getPartyDamageCapPercent());
        assertEquals(TeleportPathData.ABILITY_TARGET_ALL, profile.getTargetKind());

        profile.readFromNBT(low);
        assertEquals(BossAbilityProfile.DAMAGE_NORMAL, profile.getDamageType());
        assertEquals(0, profile.getDamagePercentMax());
        assertEquals(0, profile.getDamagePercentCurrent());
        assertEquals(0, profile.getPartyDamagePercent());
        assertEquals(0, profile.getPartyDamageCapPercent());
        assertEquals(BossAbilityProfile.TARGET_KIND_AS_BOSS, profile.getTargetKind(),
                "the bottom of the target kinds is the boss' own setting, not a kind of its own");

        profile.setDamageType(7);
        assertEquals(BossAbilityProfile.DAMAGE_TRUE, profile.getDamageType());
        profile.setDamageType(-3);
        assertEquals(BossAbilityProfile.DAMAGE_NORMAL, profile.getDamageType());
        profile.setDamagePercentMax(250);
        assertEquals(BossAbilityProfile.MAX_DAMAGE_PERCENT, profile.getDamagePercentMax());
        profile.setDamagePercentCurrent(-1);
        assertEquals(0, profile.getDamagePercentCurrent());
        profile.setPartyDamagePercent(101);
        assertEquals(BossAbilityProfile.MAX_PARTY_DAMAGE_PERCENT, profile.getPartyDamagePercent());
        profile.setPartyDamageCapPercent(5000);
        assertEquals(BossAbilityProfile.MAX_PARTY_DAMAGE_CAP, profile.getPartyDamageCapPercent());
        profile.setTargetKind(3);
        assertEquals(TeleportPathData.ABILITY_TARGET_ALL, profile.getTargetKind());
        profile.setTargetKind(-2);
        assertEquals(BossAbilityProfile.TARGET_KIND_AS_BOSS, profile.getTargetKind());
    }

    @Test
    @DisplayName("copyFrom carries the damage and target fields with the rest")
    void copyFromCarriesThem() {
        BossAbilityProfile source = new BossAbilityProfile();
        source.setDamageType(BossAbilityProfile.DAMAGE_TRUE);
        source.setDamagePercentMax(1);
        source.setDamagePercentCurrent(2);
        source.setPartyDamagePercent(3);
        source.setPartyDamageCapPercent(4);
        source.setTargetKind(TeleportPathData.ABILITY_TARGET_PLAYERS);
        BossAbilityProfile copy = new BossAbilityProfile();
        copy.copyFrom(source);
        CompoundTag expected = new CompoundTag();
        source.writeToNBT(expected);
        CompoundTag actual = new CompoundTag();
        copy.writeToNBT(actual);
        assertEquals(expected, actual);
    }
}
