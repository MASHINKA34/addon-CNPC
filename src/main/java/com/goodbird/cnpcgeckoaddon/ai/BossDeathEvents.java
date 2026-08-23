package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Everything that happens the moment a boss dies: clearing its minions and arming its
 * death explosion.
 *
 * <p>The controller also notices the death on its next tick, but CustomNPCs is free to
 * discard the NPC right away depending on its respawn settings - in that case there is no
 * next tick and the minions would be orphaned. Listening for the death itself closes that
 * hole; running the cleanup twice is harmless because the second pass finds nothing.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public class BossDeathEvents {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBossDeath(final LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EntityNPCInterface npc)
                || !(npc.level() instanceof ServerLevel level)) {
            return;
        }
        TeleportPathData data = ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
        if (!data.isEnabled()) {
            return;
        }
        if (data.isClearMinionsOnDeath()) {
            BossMinionUtil.clear(level, npc, data.getMinionRemovalMode());
        }
        if (data.isExplosionEnabled()) {
            BossExplosionScheduler.schedule(level, npc, data);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event) {
        if (BossExplosionScheduler.hasPending() && event.getLevel() instanceof ServerLevel level) {
            BossExplosionScheduler.tick(level);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BossExplosionScheduler.clear(level);
        }
    }
}
