package com.goodbird.cnpcgeckoaddon.utils;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncAnimation;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Plays one animation on an npc's model, for whoever has it loaded.
 *
 * <p>The boss controller has done this since the first ability; a plain npc reloading its bow
 * needs exactly the same three lines, and a second copy of them is how one of them ends up
 * sending to everybody or forgetting the loop type.</p>
 */
public final class NpcAnimationUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    private NpcAnimationUtil() {
    }

    /** Plays it once. A blank name plays nothing, which is how "no animation" is written. */
    public static void play(Entity entity, String animation) {
        if (entity == null || animation == null || animation.isBlank()) {
            return;
        }
        try {
            RawAnimation raw = RawAnimation.begin().then(animation.trim(), Animation.LoopType.PLAY_ONCE);
            // Only whoever has the npc loaded: a client without it drops the packet anyway.
            NetworkWrapper.sendToTracking(entity, new PacketSyncAnimation(entity.getId(), raw));
        } catch (Throwable error) {
            LOGGER.warn("Could not play animation {} for entity {}: {}", animation,
                    entity.getName().getString(), error.getMessage());
        }
    }
}
