package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import com.goodbird.cnpcgeckoaddon.utils.EventGuard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import noppes.npcs.api.event.NpcEvent;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.api.wrapper.WrapperNpcAPI;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * Redirects the drops of a dying boss into the loot chest it is about to leave behind.
 *
 * <p>A boss npc drops its loot from two places, and only one of them is vanilla. The items
 * configured on the Inventory tab are spawned by CustomNPCs itself, straight into the world
 * from its own died event - they never pass through {@link LivingDropsEvent}, so that event
 * alone would leave them lying in the grass next to a chest that was supposed to hold them.
 * Both are intercepted here: the npc drops through the CustomNPCs event, and whatever
 * vanilla or another mod adds through the NeoForge one.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public class BossChestDropEvents {

    private static boolean listeningToNpcApi;

    /**
     * CustomNPCs runs its own event bus, so this listener cannot be an annotation. Signing
     * up at server start is early enough - nothing can die before that - and the flag keeps
     * a second world from adding a second copy.
     */
    @SubscribeEvent
    public static void onServerStarting(final ServerStartingEvent event) {
        EventGuard.handle("chest_drop.server_starting", event, BossChestDropEvents::handleServerStarting);
    }

    private static void handleServerStarting(ServerStartingEvent event) {
        if (listeningToNpcApi) {
            return;
        }
        listeningToNpcApi = true;
        WrapperNpcAPI.EVENT_BUS.addListener(NpcEvent.DiedEvent.class, BossChestDropEvents::onNpcDied);
    }

    /**
     * Takes the npc's own drops before CustomNPCs throws them on the ground.
     *
     * <p>This fires before the death event that schedules the chest, so the items are handed
     * to the scheduler for it to pick up rather than the other way round.</p>
     */
    private static void onNpcDied(final NpcEvent.DiedEvent event) {
        // CustomNPCs posts this from inside die(): what escapes here escapes into the death.
        CrashGuard.run("chest_drop.npc_died", event, BossChestDropEvents::captureNpcDrops);
    }

    private static void captureNpcDrops(final NpcEvent.DiedEvent event) {
        if (event.droppedItems == null || event.droppedItems.length == 0
                || !(event.npc.getMCEntity() instanceof EntityNPCInterface npc)
                || !(npc.level() instanceof ServerLevel level)
                || !wantsDrops(npc)) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>();
        for (IItemStack wrapped : event.droppedItems) {
            ItemStack stack = wrapped == null ? null : wrapped.getMCItemStack();
            if (stack != null && !stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        // Staged before the list is emptied, so a staging that fails leaves the loot to drop
        // the way CustomNPCs drops it rather than nowhere at all.
        BossChestScheduler.takeDrops(level, npc.getUUID(), npc.blockPosition(), drops,
                BossTuningUtil.of(npc).chestStagedDropsTimeoutTicks());
        // Emptying the list is what keeps the loot out of the grass: CustomNPCs spawns
        // exactly what is left in it.
        event.droppedItems = new IItemStack[0];
    }

    @SubscribeEvent
    public static void onLivingDrops(final LivingDropsEvent event) {
        EventGuard.handle("chest_drop.living_drops", event, BossChestDropEvents::handleLivingDrops);
    }

    private static void handleLivingDrops(LivingDropsEvent event) {
        if (event.getDrops().isEmpty()
                || !(event.getEntity() instanceof EntityNPCInterface npc)
                || !(npc.level() instanceof ServerLevel level)
                || !wantsDrops(npc)) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>();
        for (ItemEntity item : event.getDrops()) {
            ItemStack stack = item.getItem();
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        // In this order for the reason above: drops that could not be staged stay drops.
        BossChestScheduler.takeDrops(level, npc.getUUID(), npc.blockPosition(), drops,
                BossTuningUtil.of(npc).chestStagedDropsTimeoutTicks());
        event.getDrops().clear();
    }

    private static boolean wantsDrops(EntityNPCInterface npc) {
        TeleportPathData data = ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
        return data.isEnabled() && data.isChestEnabled() && data.isChestUseNpcDrops();
    }
}
