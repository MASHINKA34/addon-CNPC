package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.ai.NpcRangedPlan.Move;
import com.goodbird.cnpcgeckoaddon.ai.NpcRangedPlan.Settings;
import com.goodbird.cnpcgeckoaddon.ai.NpcRangedPlan.Step;
import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The addon's ranged fight, ticked with numbers: the window, the two ways out of it, sight,
 * the burst and the pause after it.
 *
 * <p>These are the rules the builder sets on the screen, and every one of them has a state
 * behind it that a single-tick check cannot see - a burst that fires three and then stops, a
 * warning that has to end before the shot leaves. So the plan is run for a stretch of ticks
 * and what it asked for on each is written down and compared.</p>
 */
class NpcRangedPlanTest {

    private static final IntSupplier VOLLEY_DELAY = () -> 20;

    /** The window 6..14 of the acceptance steps, with a single shot every twenty ticks. */
    private static Settings window(int tooFar, int tooClose) {
        return new Settings(6.0D, 14.0D, tooFar, tooClose, 1, 4, 0,
                RangedExtraData.LOS_CUSTOMNPCS, 20, 1, 0, NpcRangedPlan.FIRE_TYPE_FLAT);
    }

    private static Settings burst(int shots, int delay, int reload) {
        return new Settings(0.0D, 14.0D, RangedExtraData.TOO_FAR_APPROACH, RangedExtraData.TOO_CLOSE_FIRE,
                shots, delay, reload, RangedExtraData.LOS_CUSTOMNPCS, 20, 1, 0, NpcRangedPlan.FIRE_TYPE_FLAT);
    }

    private static Settings sight(int losMode, int fireType, int lobWarn) {
        return new Settings(0.0D, 14.0D, RangedExtraData.TOO_FAR_APPROACH, RangedExtraData.TOO_CLOSE_FIRE,
                1, 4, 0, losMode, lobWarn, 1, 0, fireType);
    }

    /** A plan whose first shot is due on its very next tick. */
    private static NpcRangedPlan ready() {
        NpcRangedPlan plan = new NpcRangedPlan();
        plan.start(0);
        return plan;
    }

    /** Runs the plan for a while and keeps every tick's orders. */
    private static List<Step> run(NpcRangedPlan plan, Settings settings, double distance,
                                  boolean sight, int ticks) {
        List<Step> steps = new ArrayList<>();
        for (int i = 0; i < ticks; i++) {
            steps.add(plan.tick(settings, distance, sight, VOLLEY_DELAY));
        }
        return steps;
    }

    private static int shots(List<Step> steps) {
        return (int) steps.stream().filter(Step::fire).count();
    }

    /** Which ticks a shot left on, counted from one. */
    private static List<Integer> shotTicks(List<Step> steps) {
        List<Integer> ticks = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).fire()) {
                ticks.add(i + 1);
            }
        }
        return ticks;
    }

    @Test
    @DisplayName("past the window the npc closes in or waits, and does not fire")
    void tooFar() {
        List<Step> approach = run(ready(), window(RangedExtraData.TOO_FAR_APPROACH, RangedExtraData.TOO_CLOSE_RETREAT),
                20.0D, true, 40);
        assertEquals(0, shots(approach));
        assertTrue(approach.stream().allMatch(step -> step.move() == Move.APPROACH));

        List<Step> wait = run(ready(), window(RangedExtraData.TOO_FAR_WAIT, RangedExtraData.TOO_CLOSE_RETREAT),
                20.0D, true, 40);
        assertEquals(0, shots(wait));
        assertTrue(wait.stream().allMatch(step -> step.move() == Move.HOLD));
    }

    @Test
    @DisplayName("inside the window the npc stands and fires")
    void inWindow() {
        List<Step> steps = run(ready(), window(RangedExtraData.TOO_FAR_APPROACH, RangedExtraData.TOO_CLOSE_RETREAT),
                14.0D, true, 45);
        assertTrue(steps.stream().allMatch(step -> step.move() == Move.HOLD));
        // Due at once, then every twenty ticks: the volley delay and nothing else in between.
        assertEquals(List.of(1, 21, 41), shotTicks(steps));
    }

    @Test
    @DisplayName("nearer than the window the npc backs off, swings, or shoots point blank")
    void tooClose() {
        List<Step> retreat = run(ready(), window(RangedExtraData.TOO_FAR_APPROACH, RangedExtraData.TOO_CLOSE_RETREAT),
                3.0D, true, 40);
        assertEquals(0, shots(retreat), "backing off is not shooting");
        assertTrue(retreat.stream().allMatch(step -> step.move() == Move.RETREAT));

        List<Step> melee = run(ready(), window(RangedExtraData.TOO_FAR_APPROACH, RangedExtraData.TOO_CLOSE_MELEE),
                3.0D, true, 40);
        assertEquals(0, shots(melee), "melee is CustomNPCs' to do");
        assertTrue(melee.stream().allMatch(step -> step.move() == Move.MELEE));

        List<Step> fire = run(ready(), window(RangedExtraData.TOO_FAR_APPROACH, RangedExtraData.TOO_CLOSE_FIRE),
                3.0D, true, 40);
        assertEquals(2, shots(fire), "fire anyway fires on the same clock as in the window");
        assertTrue(fire.stream().allMatch(step -> step.move() == Move.HOLD));
    }

    @Test
    @DisplayName("a target that walks into the window is shot as soon as it is there")
    void aShotDueOutsideTheWindowLeavesOnTheWayIn() {
        NpcRangedPlan plan = new NpcRangedPlan();
        plan.start(20);
        Settings settings = window(RangedExtraData.TOO_FAR_APPROACH, RangedExtraData.TOO_CLOSE_RETREAT);
        // Ten ticks of delay run out while the target is still far away.
        assertEquals(0, shots(run(plan, settings, 30.0D, true, 15)));
        Step arrived = plan.tick(settings, 12.0D, true, VOLLEY_DELAY);
        assertTrue(arrived.fire(), "the delay ran down out of reach, so the shot is due on arrival");
    }

    @Test
    @DisplayName("a window typed inside out has no near edge")
    void minPastMaxIsNoMin() {
        Settings insideOut = new Settings(20.0D, 14.0D, RangedExtraData.TOO_FAR_APPROACH,
                RangedExtraData.TOO_CLOSE_RETREAT, 1, 4, 0, RangedExtraData.LOS_CUSTOMNPCS, 20, 1, 0,
                NpcRangedPlan.FIRE_TYPE_FLAT);
        Step step = ready().tick(insideOut, 10.0D, true, VOLLEY_DELAY);
        assertEquals(Move.HOLD, step.move());
        assertTrue(step.fire());
    }

    @Test
    @DisplayName("the addon's burst fires its shots a burst delay apart, then reloads, then waits the volley delay")
    void addonBurst() {
        List<Step> steps = run(ready(), burst(3, 4, 40), 10.0D, true, 120);
        // Three shots four ticks apart, and the whole cycle again once the reload and the delay are over:
        // the reload runs alongside the delay, so the pause is the longer of the two, forty ticks
        // of silence after the last shot.
        assertEquals(List.of(1, 5, 9, 50, 54, 58, 99, 103, 107), shotTicks(steps));
        List<Integer> reloads = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).reloadStarted()) {
                reloads.add(i + 1);
            }
        }
        assertEquals(List.of(9, 58, 107), reloads, "the reload starts on the last shot of each burst");
    }

    @Test
    @DisplayName("without a reload the burst repeats after the volley delay alone")
    void addonBurstWithoutReload() {
        List<Step> steps = run(ready(), burst(2, 3, 0), 10.0D, true, 60);
        assertEquals(List.of(1, 4, 24, 27, 47, 50), shotTicks(steps));
        assertTrue(steps.stream().noneMatch(Step::reloadStarted), "nothing to reload for");
    }

    @Test
    @DisplayName("a burst of nought keeps CustomNPCs' own rhythm: a burst delay on either side of N shots")
    void customNpcsBurst() {
        Settings cnpc = new Settings(0.0D, 14.0D, RangedExtraData.TOO_FAR_APPROACH, RangedExtraData.TOO_CLOSE_FIRE,
                0, 4, 0, RangedExtraData.LOS_CUSTOMNPCS, 20, 3, 2, NpcRangedPlan.FIRE_TYPE_FLAT);
        List<Step> steps = run(ready(), cnpc, 10.0D, true, 70);
        // Tick 1 only starts the burst clock; 3, 5, 7 fire; 9 resets it and rolls the volley
        // delay; and the next cycle begins twenty ticks after that.
        assertEquals(List.of(3, 5, 7, 31, 33, 35, 59, 61, 63), shotTicks(steps));
    }

    @Test
    @DisplayName("a burst of one under CustomNPCs' rules is one shot a cycle, not none")
    void customNpcsSingleShot() {
        Settings cnpc = new Settings(0.0D, 14.0D, RangedExtraData.TOO_FAR_APPROACH, RangedExtraData.TOO_CLOSE_FIRE,
                0, 4, 0, RangedExtraData.LOS_CUSTOMNPCS, 20, 1, 0, NpcRangedPlan.FIRE_TYPE_FLAT);
        assertEquals(List.of(2, 24, 46), shotTicks(run(ready(), cnpc, 10.0D, true, 50)));
    }

    @Test
    @DisplayName("as CustomNPCs: a hidden target is not shot, unless the npc lobs while hidden")
    void sightAsCustomNpcs() {
        List<Step> flat = run(ready(), sight(RangedExtraData.LOS_CUSTOMNPCS, NpcRangedPlan.FIRE_TYPE_FLAT, 20),
                10.0D, false, 40);
        assertEquals(0, shots(flat));
        assertTrue(flat.stream().allMatch(step -> step.move() == Move.HOLD), "and it does not go looking either");

        List<Step> hidden = run(ready(), sight(RangedExtraData.LOS_CUSTOMNPCS, NpcRangedPlan.FIRE_TYPE_WHEN_HIDDEN, 20),
                10.0D, false, 40);
        assertEquals(2, shots(hidden));
        assertTrue(hidden.stream().filter(Step::fire).allMatch(Step::indirect), "every shot arcs");
        assertTrue(hidden.stream().noneMatch(Step::warnLob), "CustomNPCs never warned, so neither does this");
    }

    @Test
    @DisplayName("as CustomNPCs: a far target is lobbed when the npc is set to lob when distant")
    void lobWhenDistant() {
        Settings distant = sight(RangedExtraData.LOS_CUSTOMNPCS, NpcRangedPlan.FIRE_TYPE_WHEN_DISTANT, 20);
        assertTrue(ready().tick(distant, 13.0D, true, VOLLEY_DELAY).indirect(),
                "13 of 14 is past half the squared range");
        assertFalse(ready().tick(distant, 5.0D, true, VOLLEY_DELAY).indirect());
    }

    @Test
    @DisplayName("waiting for a clear shot: no shot, and the npc goes to find one")
    void sightWait() {
        NpcRangedPlan plan = ready();
        Settings wait = sight(RangedExtraData.LOS_WAIT, NpcRangedPlan.FIRE_TYPE_FLAT, 20);
        List<Step> blind = run(plan, wait, 10.0D, false, 30);
        assertEquals(0, shots(blind));
        assertTrue(blind.stream().allMatch(step -> step.move() == Move.APPROACH));
        Step clear = plan.tick(wait, 10.0D, true, VOLLEY_DELAY);
        assertTrue(clear.fire(), "the shot is due the moment the target is in sight");
        assertFalse(clear.indirect());
    }

    @Test
    @DisplayName("lobbing over cover: the ring stands for the warning, then the shot arcs")
    void sightLob() {
        NpcRangedPlan plan = ready();
        Settings lob = sight(RangedExtraData.LOS_LOB, NpcRangedPlan.FIRE_TYPE_FLAT, 20);
        List<Step> steps = run(plan, lob, 10.0D, false, 21);
        for (int i = 0; i < 20; i++) {
            assertTrue(steps.get(i).warnLob(), "tick " + (i + 1) + " warns");
            assertFalse(steps.get(i).fire());
        }
        Step shot = steps.get(20);
        assertFalse(shot.warnLob());
        assertTrue(shot.fire());
        assertTrue(shot.indirect());
        // Back in sight, the next shot is flat and unannounced.
        List<Step> after = run(plan, lob, 10.0D, true, 20);
        assertTrue(after.stream().noneMatch(Step::warnLob));
        assertEquals(1, shots(after));
        assertFalse(after.get(19).indirect());
    }

    @Test
    @DisplayName("a lob warning of nought is no ring: the shot arcs at once")
    void lobWithoutWarning() {
        Step shot = ready().tick(sight(RangedExtraData.LOS_LOB, NpcRangedPlan.FIRE_TYPE_FLAT, 0),
                10.0D, false, VOLLEY_DELAY);
        assertTrue(shot.fire());
        assertTrue(shot.indirect());
        assertFalse(shot.warnLob());
    }

    @Test
    @DisplayName("a warning for a target that steps out of the window comes down")
    void warningEndsWhenTheShotIsOff() {
        NpcRangedPlan plan = ready();
        Settings lob = sight(RangedExtraData.LOS_LOB, NpcRangedPlan.FIRE_TYPE_FLAT, 20);
        run(plan, lob, 10.0D, false, 10);
        assertFalse(plan.tick(lob, 30.0D, false, VOLLEY_DELAY).warnLob(), "out of the window: no ring");
        // And starts over from the beginning once they are back: the ring was down in between.
        List<Step> again = run(plan, lob, 10.0D, false, 21);
        assertEquals(20, again.stream().filter(Step::warnLob).count());
        assertTrue(again.get(20).fire());
    }

    @Test
    @DisplayName("the plan starts at half the shortest delay, the way CustomNPCs' goal does")
    void startsAtHalfTheDelay() {
        NpcRangedPlan plan = new NpcRangedPlan();
        plan.start(20);
        assertEquals(List.of(10, 30), shotTicks(run(plan, burst(1, 4, 0), 10.0D, true, 40)));
    }
}
