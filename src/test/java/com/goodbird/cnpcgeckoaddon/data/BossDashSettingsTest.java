package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dash's settings, its place on the shared ability lists, and what a boss saved before it
 * existed reads back as.
 *
 * <p>The bounds are written out below rather than read off the class, so this file is the
 * statement of what each number may be; the generic sweep only knows that a number is not
 * absurd. A dash length of zero is a run that never starts, a speed of zero one that never
 * ends, and neither throws.</p>
 */
class BossDashSettingsTest {

    private record Bound(int min, int max) {
    }

    private static final Map<String, Bound> BOUNDS = Map.ofEntries(
            Map.entry("DashActionDelayTicks", new Bound(0, 1200)),
            Map.entry("DashCooldownTicks", new Bound(1, 12000)),
            Map.entry("DashDirection", new Bound(BossPhaseData.DASH_DIRECTION_TARGET, BossPhaseData.DASH_DIRECTION_FACING)),
            Map.entry("DashTargetMode", new Bound(BossTargetMode.MAIN, BossTargetMode.RANDOM)),
            Map.entry("DashLength", new Bound(2, 64)),
            Map.entry("DashSpeed", new Bound(2, 30)),
            Map.entry("DashWidth", new Bound(1, 6)),
            Map.entry("DashHeight", new Bound(1, 8)),
            Map.entry("DashDamage", new Bound(0, 1000)),
            Map.entry("DashKnockback", new Bound(0, 10)),
            Map.entry("DashWallMode", new Bound(BossPhaseData.DASH_WALL_STUN, BossPhaseData.DASH_WALL_RAGE)),
            Map.entry("DashStunTicks", new Bound(0, 1200)),
            Map.entry("DashStunDamagePercent", new Bound(100, 1000)),
            Map.entry("DashSlamRadius", new Bound(1, 16)),
            Map.entry("DashSlamDamage", new Bound(0, 1000)),
            Map.entry("DashSlamKnockback", new Bound(0, 10)),
            Map.entry("DashChainStunTicks", new Bound(0, 1200)),
            Map.entry("DashChainDamagePercent", new Bound(100, 1000)));

    @Test
    @DisplayName("a boss saved before the dash reads it back switched off, at its defaults")
    void anOldSaveReadsTheDefaults() {
        BossPhaseData phase = new BossPhaseData();
        phase.dash().setEnabled(true);
        phase.dash().setAnimation("charge");
        phase.dash().setLength(40);
        phase.dash().setStopOnHit(false);
        phase.dash().setChainStun(false);
        phase.dash().setWallMode(BossPhaseData.DASH_WALL_RAGE);
        phase.dash().setSlamVfx(AreaVfxStyles.FIRE);
        phase.dash().castSpot().setMode(BossCastSpot.MODE_TELEPORT);
        CompoundTag tag = phase.writeToNBT();
        for (String key : List.copyOf(tag.getAllKeys())) {
            if (key.startsWith("Dash")) {
                tag.remove(key);
            }
        }

        // Read into the phase that had everything changed, so the load has to put the defaults
        // back rather than merely leave a fresh object at them.
        phase.readFromNBT(tag);
        BossDashSettings dash = phase.dash();
        assertFalse(dash.isEnabled(), "an old boss must not start dashing on load");
        assertEquals("", dash.getAnimation());
        assertEquals(12, dash.getActionDelayTicks());
        assertEquals(240, dash.getCooldownTicks());
        assertEquals(BossPhaseData.DASH_DIRECTION_TARGET, dash.getDirection());
        assertEquals(BossTargetMode.MAIN, dash.getTargetMode());
        assertEquals(12, dash.getLength());
        assertEquals(8, dash.getSpeed());
        assertEquals(2, dash.getWidth());
        assertEquals(3, dash.getHeight());
        assertEquals(12, dash.getDamage());
        assertEquals(3, dash.getKnockback());
        assertTrue(dash.isStopOnHit(), "the run stops on the first one it meets unless told otherwise");
        assertEquals(BossPhaseData.DASH_WALL_STUN, dash.getWallMode());
        assertEquals(60, dash.getStunTicks());
        assertEquals(200, dash.getStunDamagePercent());
        assertEquals("", dash.getStunAnimation());
        assertEquals(4, dash.getSlamRadius());
        assertEquals(10, dash.getSlamDamage());
        assertEquals(2, dash.getSlamKnockback());
        assertEquals(AreaVfxStyles.NONE, dash.getSlamVfx());
        assertTrue(dash.isChainStun(), "a tether chain stuns the run unless told otherwise");
        assertEquals(80, dash.getChainStunTicks());
        assertEquals(200, dash.getChainDamagePercent());
        assertFalse(dash.castSpot().isSet(), "an old boss dashes from wherever it stands");
        assertFalse(dash.getEffects().isAnyEnabled());
    }

    @Test
    @DisplayName("every number the dash saves comes back inside its own range")
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
    @DisplayName("no dash number is saved without a bound written out here")
    void everyNumberHasABound() {
        CompoundTag tag = new BossPhaseData().writeToNBT();
        // The spot's numbers belong to the cast spot, which has bounds and tests of its own.
        Set<String> numbers = tag.getAllKeys().stream()
                .filter(key -> key.startsWith("Dash") && !key.startsWith("DashSpot"))
                .filter(key -> tag.get(key) instanceof IntTag)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(BOUNDS.keySet()), numbers,
                "a dash number was added or dropped without its bound being stated here");
    }

    @Test
    @DisplayName("the editor's setters hold the same ranges the save does")
    void settersClamp() {
        BossDashSettings dash = new BossDashSettings();
        dash.setLength(500);
        dash.setSpeed(-3);
        dash.setWidth(40);
        dash.setStunDamagePercent(10);
        dash.setChainDamagePercent(5000);
        dash.setWallMode(9);
        dash.setDirection(-1);
        assertEquals(64, dash.getLength());
        assertEquals(2, dash.getSpeed(), "a speed of nothing is a run that never ends");
        assertEquals(6, dash.getWidth());
        assertEquals(100, dash.getStunDamagePercent(), "a stun never makes the boss take less than a hit");
        assertEquals(1000, dash.getChainDamagePercent());
        assertEquals(BossPhaseData.DASH_WALL_RAGE, dash.getWallMode());
        assertEquals(BossPhaseData.DASH_DIRECTION_TARGET, dash.getDirection());
    }

    @Test
    @DisplayName("a configured dash survives write, read and write again")
    void aConfiguredDashRoundTrips() {
        BossPhaseData phase = new BossPhaseData();
        BossDashSettings dash = phase.dash();
        dash.setEnabled(true);
        dash.setAnimation("charge");
        dash.setActionDelayTicks(30);
        dash.setCooldownTicks(400);
        dash.setDirection(BossPhaseData.DASH_DIRECTION_FACING);
        dash.setTargetMode(BossTargetMode.FARTHEST);
        dash.setLength(30);
        dash.setSpeed(20);
        dash.setWidth(4);
        dash.setHeight(5);
        dash.setDamage(0);
        dash.setKnockback(0);
        dash.setStopOnHit(false);
        dash.setWallMode(BossPhaseData.DASH_WALL_SLAM);
        dash.setStunTicks(0);
        dash.setStunDamagePercent(350);
        dash.setStunAnimation("dizzy");
        dash.setSlamRadius(9);
        dash.setSlamDamage(25);
        dash.setSlamKnockback(7);
        dash.setSlamVfx(AreaVfxStyles.SCULK_WAVE);
        dash.setChainStun(false);
        dash.setChainStunTicks(150);
        dash.setChainDamagePercent(120);
        dash.getEffects().get(1).setEnabled(true);
        dash.castSpot().setMode(BossCastSpot.MODE_WALK);
        CompoundTag once = phase.writeToNBT();

        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(once);
        assertEquals(once, reread.writeToNBT(), "write -> read -> write should reproduce the dash exactly");
        assertEquals(AreaVfxStyles.SCULK_WAVE, reread.dash().getSlamVfx());
        assertFalse(reread.dash().isStopOnHit());
        assertFalse(reread.dash().isChainStun());
    }

    @Test
    @DisplayName("a boss saved before the dash holds its wind-up still; a later choice is kept")
    void theCastRootBitIsMigrated() {
        CompoundTag tag = new BossPhaseData().writeToNBT();
        for (String key : List.copyOf(tag.getAllKeys())) {
            if (key.startsWith("Dash")) {
                tag.remove(key);
            }
        }
        tag.putInt("CastRootMask", BossPhaseData.CAST_ROOT_ALL & ~(1 << BossAbilityKind.DASH));
        BossPhaseData old = new BossPhaseData();
        old.readFromNBT(tag);
        assertTrue(old.isCastRooted(BossAbilityKind.DASH),
                "a save that never saw the dash never chose to let its wind-up walk");

        BossPhaseData chosen = new BossPhaseData();
        chosen.setCastRooted(BossAbilityKind.DASH, false);
        BossPhaseData reread = new BossPhaseData();
        reread.readFromNBT(chosen.writeToNBT());
        assertFalse(reread.isCastRooted(BossAbilityKind.DASH), "a save that knows the dash keeps its choice");
        assertTrue(reread.isCastRooted(BossAbilityKind.COCOON), "freeing the dash frees nothing else");
    }

    @Test
    @DisplayName("a boss that warned for everything warns for the dash; one that chose keeps its choice")
    void theWarningBitIsMigrated() {
        // Everything the mask held just before the dash joined it, so the kinds appended after
        // the dash are not in it either.
        int beforeDash = TeleportPathData.TELEGRAPH_ALL_ABILITIES & ((1 << BossAbilityKind.DASH) - 1);
        TeleportPathData everything = configuredBoss();
        everything.setTelegraphAbilities(beforeDash);
        TeleportPathData reread = new TeleportPathData();
        reread.readFromNBT(everything.writeToNBT(new CompoundTag()));
        assertTrue(reread.isTelegraphAbility(BossAbilityKind.DASH),
                "a boss warning for every ability it had was warning for everything");

        TeleportPathData chose = configuredBoss();
        chose.setTelegraphAbilities(beforeDash & ~(1 << BossAbilityKind.MELEE));
        TeleportPathData rereadChoice = new TeleportPathData();
        rereadChoice.readFromNBT(chose.writeToNBT(new CompoundTag()));
        assertFalse(rereadChoice.isTelegraphAbility(BossAbilityKind.DASH),
                "a boss that silenced something made a choice, and the new bit stays off");
        assertFalse(rereadChoice.isTelegraphAbility(BossAbilityKind.MELEE));
    }

    @Test
    @DisplayName("the dash sits on every list it belongs to, and only those")
    void theDashIsOnItsLists() {
        assertEquals("cnpcgeckoaddon.boss.ability.dash", BossAbilityKind.LABELS[BossAbilityKind.DASH]);
        assertTrue(BossAbilityKind.COUNT < Integer.SIZE, "the masks are ints, so the list cannot outgrow 31");
        assertTrue(contains(BossAbilityKind.IMMUNITY_ABILITIES, BossAbilityKind.DASH), "an npc can be immune to the dash");
        assertTrue(contains(BossAbilityKind.COMBO_ABILITIES, BossAbilityKind.DASH), "the dash can be chained");
        assertTrue(contains(BossPhaseData.CAST_ROOT_ABILITIES, BossAbilityKind.DASH), "the wind-up can be rooted");
        assertTrue(contains(TeleportPathData.TELEGRAPH_ABILITIES, BossAbilityKind.DASH), "the wind-up warns");
        assertFalse(contains(BossAbilityKind.LASTING_ABILITIES, BossAbilityKind.DASH),
                "the run holds the boss busy itself, so there is nothing to wait out");
        assertEquals(BossAbilityKind.COCOON + 1, BossAbilityKind.DASH, "a new kind is appended, never slotted in");
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
