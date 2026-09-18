package com.goodbird.cnpcgeckoaddon.utils;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.function.Consumer;

/**
 * {@link CrashGuard} for the addon's event listeners.
 *
 * <p>An exception out of a listener goes up through the bus into whatever posted the event -
 * a hit being dealt, a level ticking, a frame being drawn - and that is a crash. Behind this
 * a listener that fails is reported and the event goes on as if the addon had not been
 * listening: whether it is cancelled is put back to what the listener found, and for the two
 * damage events so is the number, since a listener that dies halfway through its own
 * bookkeeping has not decided anything worth keeping. What else a listener did before it
 * failed - a barrier it charged, a participant it signed up - is not unwound.</p>
 *
 * <p>The listener is handed over as a consumer of the event so that a static method reference
 * - a constant - does for it; several of these fire for every hit dealt anywhere.</p>
 */
public final class EventGuard {

    private EventGuard() {
    }

    /** Any event: a failed listener leaves it cancelled or not as it found it. */
    public static <E extends Event> void handle(String site, E event, Consumer<? super E> listener) {
        boolean cancellable = event instanceof ICancellableEvent;
        boolean wasCanceled = cancellable && ((ICancellableEvent) event).isCanceled();
        try {
            listener.accept(event);
        } catch (Throwable error) {
            CrashGuard.caught(site, error);
            if (cancellable) {
                restoreCanceled(site, (ICancellableEvent) event, wasCanceled);
            }
        }
    }

    /** A hit on its way in: a failed listener leaves both the number and the cancellation alone. */
    public static void incomingDamage(String site, LivingIncomingDamageEvent event,
                                      Consumer<? super LivingIncomingDamageEvent> listener) {
        boolean wasCanceled = event.isCanceled();
        float amount = event.getAmount();
        try {
            listener.accept(event);
        } catch (Throwable error) {
            CrashGuard.caught(site, error);
            try {
                event.setAmount(amount);
                event.setCanceled(wasCanceled);
            } catch (Throwable restore) {
                CrashGuard.caught(site + ".restore", restore);
            }
        }
    }

    /** The mitigated number about to come off the health: a failed listener leaves it as it was. */
    public static void damagePre(String site, LivingDamageEvent.Pre event,
                                 Consumer<? super LivingDamageEvent.Pre> listener) {
        float damage = event.getNewDamage();
        try {
            listener.accept(event);
        } catch (Throwable error) {
            CrashGuard.caught(site, error);
            try {
                event.setNewDamage(damage);
            } catch (Throwable restore) {
                CrashGuard.caught(site + ".restore", restore);
            }
        }
    }

    private static void restoreCanceled(String site, ICancellableEvent event, boolean wasCanceled) {
        try {
            if (event.isCanceled() != wasCanceled) {
                event.setCanceled(wasCanceled);
            }
        } catch (Throwable restore) {
            CrashGuard.caught(site + ".restore", restore);
        }
    }
}
