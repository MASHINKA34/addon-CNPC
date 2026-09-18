package com.goodbird.cnpcgeckoaddon.utils;

import com.goodbird.cnpcgeckoaddon.util.CompiledClasses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds every event listener of the addon to one shape: the whole event handed to a guard.
 *
 * <p>A listener is called from inside whatever posted the event - a hit being dealt, a level or
 * a client ticking, a frame being drawn - and an exception out of it is that loop's crash. The
 * rule is checked in the bytecode because it is the kind nobody sees in review: a listener added
 * next month without the guard compiles, passes every other test and works, until the day it
 * throws. The listener's own body goes into a private method the guard is handed; anything the
 * listener calls besides the guard would run unguarded, so nothing else is allowed.</p>
 */
class EventHandlersGuardedTest {

    private static final String SUBSCRIBE_EVENT = "Lnet/neoforged/bus/api/SubscribeEvent;";
    private static final String CRASH_GUARD = CompiledClasses.PACKAGE + "utils/CrashGuard";
    private static final String EVENT_GUARD = CompiledClasses.PACKAGE + "utils/EventGuard";
    private static final Set<String> CRASH_GUARD_ENTRIES = Set.of("run", "get", "tick");
    private static final Set<String> EVENT_GUARD_ENTRIES = Set.of("handle", "incomingDamage", "damagePre");

    /** Fewer than this and the walk is not finding the listeners, rather than every one being guarded. */
    private static final int EXPECTED_AT_LEAST = 60;

    @Test
    @DisplayName("every @SubscribeEvent listener hands its event to a guard and does nothing else")
    void everyListenerRunsBehindAGuard() {
        Set<String> unguarded = new TreeSet<>();
        int listeners = 0;
        for (ClassNode owner : CompiledClasses.all()) {
            for (MethodNode method : owner.methods) {
                if (!CompiledClasses.annotated(method, SUBSCRIBE_EVENT)) {
                    continue;
                }
                listeners++;
                List<MethodInsnNode> calls = CompiledClasses.calls(method);
                boolean guarded = calls.stream().anyMatch(EventHandlersGuardedTest::isGuard);
                boolean onlyTheGuard = calls.stream().allMatch(EventHandlersGuardedTest::isGuard);
                if (!guarded || !onlyTheGuard) {
                    unguarded.add(owner.name.replace('/', '.') + "." + method.name
                            + (guarded ? " (calls something besides the guard)" : " (no guard)"));
                }
            }
        }
        assertTrue(listeners >= EXPECTED_AT_LEAST, "only " + listeners + " listeners were found; the walk "
                + "should see every @SubscribeEvent method of the addon");
        assertTrue(unguarded.isEmpty(), "these listeners are not behind CrashGuard - move the body into a "
                + "private method and hand it to EventGuard.handle (or CrashGuard.run): " + unguarded);
    }

    /** EventGuard stands in for CrashGuard in the listeners, so it has to report through it. */
    @Test
    @DisplayName("the event guard reports every failure through CrashGuard")
    void theEventGuardReportsThroughCrashGuard() {
        ClassNode eventGuard = CompiledClasses.read(EVENT_GUARD);
        for (String entry : EVENT_GUARD_ENTRIES) {
            MethodNode method = CompiledClasses.method(eventGuard, entry);
            boolean reports = CompiledClasses.calls(method).stream()
                    .anyMatch(call -> call.owner.equals(CRASH_GUARD) && call.name.equals("caught"));
            assertTrue(reports, "EventGuard." + entry + " does not hand its failure to CrashGuard.caught");
        }
    }

    static boolean isGuard(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKESTATIC) {
            return false;
        }
        return call.owner.equals(CRASH_GUARD) && CRASH_GUARD_ENTRIES.contains(call.name)
                || call.owner.equals(EVENT_GUARD) && EVENT_GUARD_ENTRIES.contains(call.name);
    }
}
