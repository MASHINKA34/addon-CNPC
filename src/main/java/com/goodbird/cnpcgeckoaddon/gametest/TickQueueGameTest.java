package com.goodbird.cnpcgeckoaddon.gametest;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(CNPCGeckoAddon.MODID)
@PrefixGameTestTemplate(false)
public class TickQueueGameTest {
    @GameTest(template = "fluid_platform")
    public static void runningEntryCanCancelItself(GameTestHelper helper) {
        TickQueue<String> queue = new TickQueue<>("test actions", 16);
        queue.add("active");
        queue.sweep(entry -> true, entry -> {
            helper.assertFalse(queue.isEmpty(), "the running entry must remain visible to cancellation guards");
            helper.assertTrue(entry.equals(queue.find(entry::equals)), "find must include the running entry");
            queue.removeIf(entry::equals);
            helper.assertTrue(queue.find(entry::equals) == null, "a cancelled entry must stop being discoverable");
            return true;
        });
        helper.assertTrue(queue.isEmpty(), "returning true must not revive a cancelled action");
        int[] repeats = {0};
        queue.drain(entry -> true, entry -> repeats[0]++);
        helper.assertTrue(repeats[0] == 0, "a cancelled action must never run on the next tick");
        helper.succeed();
    }

    @GameTest(template = "fluid_platform")
    public static void cancellationRemovesPulledAndDeferredEntries(GameTestHelper helper) {
        TickQueue<String> queue = new TickQueue<>("test actions", 2);
        queue.add("first");
        queue.add("pulled");
        queue.add("deferred");
        List<String> executed = new ArrayList<>();
        queue.sweep(entry -> true, entry -> {
            executed.add(entry);
            queue.add("arrival");
            queue.removeIf(candidate -> true);
            return true;
        });
        helper.assertTrue(executed.equals(List.of("first")), "cancellation must also stop this tick's remaining work");
        helper.assertTrue(queue.isEmpty(), "cancellation must cover current, pulled, deferred and arriving work");
        helper.succeed();
    }

    @GameTest(template = "fluid_platform")
    public static void workAddedAfterCancellationWaitsForNextTick(GameTestHelper helper) {
        TickQueue<String> queue = new TickQueue<>("test actions", 16);
        queue.add("boss");
        queue.sweep(entry -> true, entry -> {
            queue.removeIf(entry::equals);
            queue.add("boss");
            return true;
        });
        int[] executed = {0};
        queue.drain(entry -> true, entry -> executed[0]++);
        helper.assertTrue(executed[0] == 1, "only the newly scheduled action may run after cancellation");
        helper.assertTrue(queue.isEmpty(), "the replacement must run exactly once");
        helper.succeed();
    }
}
