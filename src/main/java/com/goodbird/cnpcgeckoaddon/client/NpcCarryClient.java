package com.goodbird.cnpcgeckoaddon.client;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketNpcCarryThrow;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** The client's half of the throw: the one click the server cannot see on its own. */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class NpcCarryClient {

    private NpcCarryClient() {
    }

    /**
     * Tells the server about a right click into the air with an empty hand.
     *
     * <p>This event exists for exactly that: the client sends no use packet when there is
     * nothing to use, and NeoForge's own note on the event says the server has to be told.
     * Every such click goes up, carrying or not, because the client does not know whose hands
     * are full - the server answers an empty-handed click with nothing when they are not.</p>
     */
    @SubscribeEvent
    public static void onRightClickEmpty(final PlayerInteractEvent.RightClickEmpty event) {
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            NetworkWrapper.sendToServer(new PacketNpcCarryThrow());
        }
    }
}
