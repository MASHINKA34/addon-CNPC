package com.goodbird.cnpcgeckoaddon;

import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import com.goodbird.cnpcgeckoaddon.mixin.INpcCarryData;
import com.goodbird.cnpcgeckoaddon.mixin.INpcCarryState;
import com.goodbird.cnpcgeckoaddon.mixin.INpcImmunityData;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import com.goodbird.cnpcgeckoaddon.mixin.ISoundReactionData;
import com.goodbird.cnpcgeckoaddon.mixin.ISoundReactiveNpc;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.entity.data.DataDisplay;
import noppes.npcs.entity.data.DataRanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Says out loud, once, whether this addon actually got hold of CustomNPCs.
 *
 * <p>Every feature here reaches CustomNPCs through an interface a mixin adds to one of its
 * classes, and the version range this addon declares for it is deliberately open: the
 * unofficial 1.21.1 port has no version scheme to bound. So the failure this is written for
 * is not a missing mod, which the loader already refuses to start over - it is a port whose
 * class or method the mixins no longer match. Mixin logs that in its own words, in the
 * middle of a startup log nobody reads, and the addon then loads and does nothing: an npc
 * set to a geckolib model renders as a plain npc, a boss never ticks, and the report that
 * comes back is "the mod is broken".</p>
 *
 * <p>One cast per integration point turns that into one line naming exactly which one went.
 * The casts are the same ones the runtime does on every tick, so this cannot pass while the
 * game fails, and it costs one pass over nine classes at startup.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public final class IntegrationCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    /** One integration point: the interface a mixin adds, and the class it is added to. */
    private record Hook(Class<?> added, Class<?> target, String feature) {
    }

    private static final List<Hook> HOOKS = List.of(
            new Hook(ITeleportPathData.class, DataAI.class, "the boss framework"),
            new Hook(ISoundReactionData.class, DataAI.class, "sound reaction settings"),
            new Hook(INpcCarryData.class, DataAI.class, "carryable npc settings"),
            new Hook(INpcImmunityData.class, DataAI.class, "npc ability immunity"),
            new Hook(IDataDisplay.class, DataDisplay.class, "geckolib models on npcs"),
            new Hook(IRangedData.class, DataRanged.class, "the extra ranged options"),
            new Hook(IBossController.class, EntityNPCInterface.class, "the boss controller"),
            new Hook(INpcCarryState.class, EntityNPCInterface.class, "carrying npcs"),
            new Hook(ISoundReactiveNpc.class, EntityNPCInterface.class, "npcs hearing vibrations"));

    private IntegrationCheck() {
    }

    @SubscribeEvent
    public static void verify(final FMLCommonSetupEvent event) {
        // enqueueWork rather than the event thread: this only reads classes, but every other
        // mod's setup runs in parallel with it, and a log line about a broken install is
        // worth putting where the rest of the loading messages are.
        event.enqueueWork(IntegrationCheck::report);
    }

    private static void report() {
        List<String> missing = new ArrayList<>();
        for (Hook hook : HOOKS) {
            try {
                if (!hook.added().isAssignableFrom(hook.target())) {
                    missing.add(hook.feature() + " (" + hook.target().getName() + ")");
                }
            } catch (Throwable error) {
                // A class that is not there at all is the same answer as one the mixin missed.
                missing.add(hook.feature() + " (" + hook.target().getName() + ": " + error + ")");
            }
        }
        if (missing.isEmpty()) {
            LOGGER.info("CustomNPCs integration is complete: all {} hooks applied", HOOKS.size());
            return;
        }
        LOGGER.error("CustomNPCs integration is incomplete - this build of CustomNPCs is not one "
                + "the addon's mixins fit, and these features will do nothing: {}. "
                + "The mixin log above says which injection failed.", String.join(", ", missing));
    }
}
