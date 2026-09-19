package com.goodbird.cnpcgeckoaddon.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a shadow copy is left holding of the boss it was made from.
 *
 * <p>A copy is built from the boss' whole saved data, so everything it must not have - the
 * chest, the blast, the link, the totems, the other phases, the bar - arrives switched on and
 * has to be switched off before the copy's first tick. None of that throws when it is missed:
 * a copy with a chest simply drops one when it dies, and a copy with the bar simply gives the
 * real boss away.</p>
 */
class BossShadowStripTest {

    private static final long MASK = (1L << BossAbilityKind.MELEE) | (1L << BossAbilityKind.DASH)
            | (1L << BossAbilityKind.SHADOW) | (1L << BossAbilityKind.SUMMON) | (1L << BossAbilityKind.HAZARD)
            | (1L << BossAbilityKind.RIFT);

    @Test
    @DisplayName("everything that makes a boss more than a fighter is switched off on the copy")
    void theCopyKeepsOnlyTheFight() {
        TeleportPathData data = fullBoss();
        data.stripToShadow(data.getPhase(1), MASK);

        assertTrue(data.isEnabled(), "the copy runs a controller of its own");
        assertFalse(data.isChestEnabled());
        assertFalse(data.isExplosionEnabled());
        assertFalse(data.isHealthLinked());
        assertEquals("", data.getHealthLinkGroup());
        assertFalse(data.isTotemsEnabled());
        assertEquals(0, data.getTotems().size());
        assertFalse(data.isAggroZoneEnabled());
        assertFalse(data.isHealthScalingEnabled());
        assertFalse(data.isRageEnabled());
        assertFalse(data.isHomeLeashEnabled());
        assertEquals(BossBarStyles.NONE, data.getBossBarStyle());
        // What the copy is for: it fights and warns exactly as the boss does.
        assertTrue(data.isTelegraphEnabled());
        assertTrue(data.isCombatOnly());
    }

    @Test
    @DisplayName("one phase at full health, with the mask's abilities and nothing else")
    void onePhaseWithTheMask() {
        TeleportPathData data = fullBoss();
        data.stripToShadow(data.getPhase(1), MASK);

        assertEquals(1, data.getPhaseCount());
        BossPhaseData only = data.getPhase(0);
        assertEquals(100, only.getStartHealthPercent());
        assertEquals("roar", only.getAppearanceAnimation(), "the phase is the second one, copied");
        assertTrue(only.meleeAttack().isEnabled());
        assertTrue(only.dash().isEnabled());
        assertFalse(only.geyser().isEnabled(), "on in the source, off the mask");
        assertFalse(only.areaAttack().isEnabled());
        assertFalse(only.shadow().isEnabled(), "copies never make copies");
        assertFalse(only.summon().isEnabled(), "and never call for minions");
        assertFalse(only.rift().isEnabled(), "nor open a rift, whatever the mask says");
        assertFalse(only.hazard().isEnabled(), "the arena is the boss' to burn, whatever the mask says");
        assertFalse(only.invulnerable().isEnabled());
        assertFalse(only.barrier().isEnabled());
    }

    @Test
    @DisplayName("the copy's phase is a copy: the boss' own phase is untouched")
    void theSourcePhaseIsNotShared() {
        TeleportPathData boss = fullBoss();
        BossPhaseData source = boss.getPhase(1);
        TeleportPathData copy = new TeleportPathData();
        copy.readFromNBT(boss.writeToNBT(new net.minecraft.nbt.CompoundTag()));
        copy.stripToShadow(source, MASK);

        assertTrue(source.geyser().isEnabled(), "the source keeps what the mask took off the copy");
        assertTrue(source.summon().isEnabled());
        assertTrue(source.hazard().isEnabled());
        assertEquals(40, source.getStartHealthPercent());
        assertEquals(3, boss.getPhaseCount());
        copy.getPhase(0).meleeAttack().setEnabled(false);
        assertTrue(source.meleeAttack().isEnabled(), "editing the copy's phase reaches nothing of the boss'");
    }

    @Test
    @DisplayName("an empty mask leaves a copy that only stands there, and the two forbidden bits are dropped")
    void anEmptyMaskCastsNothing() {
        TeleportPathData data = fullBoss();
        data.stripToShadow(data.getPhase(1), 0);
        BossPhaseData only = data.getPhase(0);
        for (int kind : BossShadowSettings.COPY_ABILITIES) {
            only.setAbilityEnabled(kind, true);
        }
        assertTrue(only.meleeAttack().isEnabled());
        assertTrue(only.hurricane().isEnabled());
        BossShadowSettings shadow = new BossShadowSettings();
        shadow.setAbilities(-1);
        assertEquals(BossShadowSettings.COPY_ALL, shadow.getAbilities());
        assertFalse(shadow.castsAbility(BossAbilityKind.SUMMON));
        assertFalse(shadow.castsAbility(BossAbilityKind.SHADOW));
        assertFalse(shadow.castsAbility(BossAbilityKind.RIFT), "the rift is the boss' own");
        assertFalse(shadow.castsAbility(BossAbilityKind.HAZARD));
        assertFalse(shadow.castsAbility(BossAbilityKind.BLAST));
        assertTrue(shadow.castsAbility(BossAbilityKind.MELEE));
    }

    @Test
    @DisplayName("a copy's health is a share of the boss' maximum or a number of its own")
    void copyHealthFollowsTheMode() {
        BossShadowSettings shadow = new BossShadowSettings();
        assertEquals(20, shadow.copyMaxHealth(100.0F), "twenty per cent by default");
        shadow.setHealthPercent(1);
        assertEquals(1, shadow.copyMaxHealth(10.0F), "never under one");
        shadow.setHealthMode(BossShadowSettings.HEALTH_VALUE);
        shadow.setHealthValue(350);
        assertEquals(350, shadow.copyMaxHealth(100.0F));
    }

    /** A boss with everything switched on, three phases, and the second one busy. */
    private static TeleportPathData fullBoss() {
        TeleportPathData data = new TeleportPathData();
        data.setEnabled(true);
        data.markConfigured();
        data.setCombatOnly(true);
        data.setTelegraphEnabled(true);
        data.setChestEnabled(true);
        data.setExplosionEnabled(true);
        data.setHealthLinkGroup("pair");
        data.setTotemsEnabled(true);
        data.getTotems().add();
        data.setAggroZoneEnabled(true);
        data.setHealthScalingEnabled(true);
        data.setRageEnabled(true);
        data.setHomeLeashEnabled(true);
        data.setBossBarStyle(BossBarStyles.values().get(BossBarStyles.values().size() - 1).id());
        data.setPhaseCount(3);
        data.setPhaseThreshold(1, 40);
        BossPhaseData second = data.getPhase(1);
        second.setAppearanceAnimation("roar");
        second.meleeAttack().setEnabled(true);
        second.dash().setEnabled(true);
        second.geyser().setEnabled(true);
        second.areaAttack().setEnabled(true);
        second.summon().setEnabled(true);
        second.shadow().setEnabled(true);
        second.hazard().setEnabled(true);
        second.invulnerable().setEnabled(true);
        second.barrier().setEnabled(true);
        return data;
    }
}
