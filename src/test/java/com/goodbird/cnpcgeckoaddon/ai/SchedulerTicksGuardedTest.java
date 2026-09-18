package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.util.CompiledClasses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Holds the level tick of the boss schedulers to one guard per scheduler.
 *
 * <p>{@code BossSchedulerEvents} ticks a score of schedulers inside the level tick, where one
 * exception is the server's crash report. Each goes through {@code CrashGuard.tick} with its own
 * {@code clear} as the recovery, so a scheduler that throws drops what it had in that level and
 * the others carry on. A scheduler added later with a plain {@code X.tick(level)} would compile
 * and work, which is why the shape is checked here, in the class file:</p>
 * <ul>
 *     <li>a method reference to a scheduler's tick or clear is handed straight to CrashGuard;</li>
 *     <li>a direct call to one is only made from a private helper that is itself only ever
 *         handed to CrashGuard, never called;</li>
 *     <li>a guarded tick's recovery clears the same scheduler it ticks;</li>
 *     <li>every scheduler ticked here is also cleared, each behind its own guard, when the
 *         level unloads.</li>
 * </ul>
 */
class SchedulerTicksGuardedTest {

    private static final String EVENTS = CompiledClasses.PACKAGE + "ai/BossSchedulerEvents";
    private static final String CRASH_GUARD = CompiledClasses.PACKAGE + "utils/CrashGuard";
    private static final Set<String> GUARD_ENTRIES = Set.of("run", "get", "tick");
    private static final Set<String> TICKS = Set.of("tick");
    private static final Set<String> CLEARS = Set.of("clear", "clearLevel", "clearPending", "shutdownLevel",
            "invalidate");
    /** The addon's own helpers that are not schedulers. */
    private static final Set<String> NOT_SCHEDULERS = Set.of(CRASH_GUARD, EVENTS,
            CompiledClasses.PACKAGE + "utils/EventGuard", CompiledClasses.PACKAGE + "utils/GuardSelfTest");

    /** What one call was handed: the method references built right before it. */
    private record Consumption(String method, MethodInsnNode call, List<Handle> handles) {
        boolean byCrashGuard() {
            return call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(CRASH_GUARD)
                    && GUARD_ENTRIES.contains(call.name);
        }
    }

    private final ClassNode events = CompiledClasses.read(EVENTS);

    @Test
    @DisplayName("a scheduler's tick or clear is only ever handed to CrashGuard")
    void everySchedulerStepIsHandedToTheGuard() {
        Set<String> offenders = new TreeSet<>();
        for (Consumption consumption : consumptions()) {
            for (Handle handle : consumption.handles()) {
                if ((isSchedulerStep(handle.getOwner(), handle.getName()) || isHelper(handle))
                        && !consumption.byCrashGuard()) {
                    offenders.add(consumption.method() + " hands " + name(handle) + " to "
                            + consumption.call().owner + "." + consumption.call().name);
                }
            }
        }
        assertTrue(offenders.isEmpty(), "every scheduler step has to go through CrashGuard.tick (on the level "
                + "tick) or CrashGuard.run (on unload): " + offenders);
    }

    @Test
    @DisplayName("nothing calls a scheduler directly except a helper that only the guard is handed")
    void noSchedulerIsCalledOutsideAGuard() {
        Set<String> offenders = new TreeSet<>();
        for (MethodNode method : events.methods) {
            for (MethodInsnNode call : CompiledClasses.calls(method)) {
                if (isSchedulerStep(call.owner, call.name) && !isGuardedHelper(method)) {
                    offenders.add(method.name + " calls " + call.owner + "." + call.name + " directly");
                }
            }
        }
        assertTrue(offenders.isEmpty(), "a scheduler called straight from the level tick takes the server down "
                + "when it throws; hand it to CrashGuard instead: " + offenders);
    }

    @Test
    @DisplayName("each guarded tick recovers by clearing the scheduler it ticks, and the level unload clears them all")
    void recoveriesAndUnloadClearTheSameSchedulers() {
        Set<String> ticked = new TreeSet<>();
        Set<String> mismatched = new TreeSet<>();
        Set<String> unloaded = new TreeSet<>();
        int guardedTicks = 0;
        for (Consumption consumption : consumptions()) {
            if (!consumption.byCrashGuard()) {
                continue;
            }
            List<Handle> handles = consumption.handles();
            if (consumption.method().equals("tickSchedulers") && consumption.call().name.equals("tick")) {
                guardedTicks++;
                assertEquals(2, handles.size(), "CrashGuard.tick is handed a step and its recovery");
                String owner = tickedOwner(handles.get(0));
                Handle recovery = handles.get(1);
                if (owner != null) {
                    ticked.add(owner);
                }
                if (owner == null || !CLEARS.contains(recovery.getName()) || !recovery.getOwner().equals(owner)) {
                    mismatched.add(name(handles.get(0)) + " recovers with " + name(recovery));
                }
            }
            if (consumption.method().equals("clearSchedulers")) {
                for (Handle handle : handles) {
                    if (CLEARS.contains(handle.getName())) {
                        unloaded.add(handle.getOwner());
                    }
                }
            }
        }
        assertTrue(guardedTicks >= 18, "only " + guardedTicks + " guarded scheduler ticks were found in "
                + "tickSchedulers, fewer than there are schedulers: one lost its guard, or the walk is not "
                + "reading the class");
        assertTrue(mismatched.isEmpty(), "a failed tick has to clear the scheduler that failed: " + mismatched);
        Set<String> notUnloaded = new TreeSet<>(ticked);
        notUnloaded.removeAll(unloaded);
        assertTrue(notUnloaded.isEmpty(), "these schedulers are ticked but never cleared when their level "
                + "unloads: " + notUnloaded);
    }

    /** The scheduler a guarded step ticks: the reference's own owner, or the one a helper calls. */
    private String tickedOwner(Handle step) {
        if (TICKS.contains(step.getName()) && !NOT_SCHEDULERS.contains(step.getOwner())) {
            return step.getOwner();
        }
        if (isHelper(step)) {
            for (MethodInsnNode call : CompiledClasses.calls(CompiledClasses.method(events, step.getName()))) {
                if (TICKS.contains(call.name) && isSchedulerStep(call.owner, call.name)) {
                    return call.owner;
                }
            }
        }
        return null;
    }

    /** Walks every method for the calls that consume method references, in instruction order. */
    private List<Consumption> consumptions() {
        List<Consumption> consumptions = new ArrayList<>();
        for (MethodNode method : events.methods) {
            List<Handle> pending = new ArrayList<>();
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof InvokeDynamicInsnNode indy) {
                    for (Object argument : indy.bsmArgs) {
                        if (argument instanceof Handle handle) {
                            pending.add(handle);
                        }
                    }
                } else if (instruction instanceof MethodInsnNode call && !pending.isEmpty()) {
                    consumptions.add(new Consumption(method.name, call, List.copyOf(pending)));
                    pending.clear();
                }
            }
        }
        return consumptions;
    }

    private static boolean isSchedulerStep(String owner, String name) {
        return owner.startsWith(CompiledClasses.PACKAGE) && !NOT_SCHEDULERS.contains(owner)
                && (TICKS.contains(name) || CLEARS.contains(name));
    }

    /** A method of this class that calls a scheduler directly. */
    private boolean isHelper(Handle handle) {
        if (!handle.getOwner().equals(EVENTS)) {
            return false;
        }
        for (MethodInsnNode call : CompiledClasses.calls(CompiledClasses.method(events, handle.getName()))) {
            if (isSchedulerStep(call.owner, call.name)) {
                return true;
            }
        }
        return false;
    }

    /** A private helper nobody calls: it is only ever run through the reference the first test checks. */
    private boolean isGuardedHelper(MethodNode method) {
        if ((method.access & Opcodes.ACC_PRIVATE) == 0) {
            return false;
        }
        Map<String, Integer> directCalls = new HashMap<>();
        for (MethodNode other : events.methods) {
            for (MethodInsnNode call : CompiledClasses.calls(other)) {
                if (call.owner.equals(EVENTS)) {
                    directCalls.merge(call.name, 1, Integer::sum);
                }
            }
        }
        return !directCalls.containsKey(method.name);
    }

    private static String name(Handle handle) {
        return handle.getOwner().substring(handle.getOwner().lastIndexOf('/') + 1) + "::" + handle.getName();
    }
}
