package com.goodbird.cnpcgeckoaddon.network;

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
 * Holds every payload handler of the addon to running its body behind {@code CrashGuard}.
 *
 * <p>NeoForge does not crash on a handler that throws - the main thread task it queues completes
 * exceptionally and is logged - but it logs every one and counts none, and what it does with an
 * exception is its own to change. Behind the guard a payload that cannot be applied, from a
 * server or from a client, is one line in ten seconds and a count in {@code /cnpcgecko guards}.
 * Checked in the class file for the same reason the listeners are: a packet added without it
 * works until the day it throws.</p>
 */
class PacketHandlersGuardedTest {

    private static final String CRASH_GUARD = CompiledClasses.PACKAGE + "utils/CrashGuard";
    private static final String NETWORK = CompiledClasses.PACKAGE + "network/";

    @Test
    @DisplayName("every Packet*.handle runs its body through CrashGuard and nothing else")
    void everyHandlerIsGuarded() {
        Set<String> unguarded = new TreeSet<>();
        int handlers = 0;
        for (ClassNode owner : CompiledClasses.all()) {
            String simpleName = owner.name.substring(owner.name.lastIndexOf('/') + 1);
            if (!owner.name.startsWith(NETWORK) || !simpleName.startsWith("Packet") || simpleName.contains("$")) {
                continue;
            }
            for (MethodNode method : owner.methods) {
                if (!method.name.equals("handle") || (method.access & Opcodes.ACC_STATIC) == 0) {
                    continue;
                }
                handlers++;
                List<MethodInsnNode> calls = CompiledClasses.calls(method);
                boolean guarded = !calls.isEmpty() && calls.stream().allMatch(PacketHandlersGuardedTest::isGuard);
                if (!guarded) {
                    unguarded.add(simpleName + ".handle");
                }
            }
        }
        assertTrue(handlers >= 13, "only " + handlers + " packet handlers were found under " + NETWORK);
        assertTrue(unguarded.isEmpty(), "these handlers are not behind CrashGuard - move the body into a private "
                + "method and hand it to CrashGuard.run: " + unguarded);
    }

    private static boolean isGuard(MethodInsnNode call) {
        return call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(CRASH_GUARD)
                && (call.name.equals("run") || call.name.equals("get"));
    }
}
