package com.goodbird.cnpcgeckoaddon.ai;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The fires a boss lit that burn harder than vanilla's, for as long as each of them does.
 *
 * <p>The fire itself stays vanilla's: the victim's own fire ticks count it down, water puts it
 * out, and the burn arrives as plain fire damage that names nobody. So the only way to tell a
 * burn is the boss' - and how hard it should bite - is to remember who was lit, how hard and
 * until when. Nothing is persisted: after a restart the fire goes on as vanilla fire, which is
 * what it would have turned back into seconds later anyway.</p>
 *
 * <p>A plain static, like the ability door's mark: the server tick is one thread.</p>
 */
public final class BossFireTracker {
    private static final Burns BURNS = new Burns();

    private BossFireTracker() {
    }

    /**
     * Sets a victim alight for {@code durationTicks} and remembers how hard this fire bites.
     *
     * <p>{@code igniteForTicks} is the call lava makes: it never shortens a longer fire that is
     * already burning, and it scales by the victim's burning time the way fire protection
     * expects.</p>
     *
     * @param multiplier the slot's level; 1 is vanilla's own fire and needs no remembering
     */
    public static void ignite(LivingEntity victim, int durationTicks, int multiplier) {
        victim.igniteForTicks(durationTicks);
        // A fire-immune victim, or one whose armour burns the fire down to nothing, never caught.
        if (victim.isOnFire() && victim.level() instanceof ServerLevel level) {
            long now = level.getGameTime();
            BURNS.light(victim.getUUID(), level.dimension(), multiplier, now + durationTicks, now);
        }
    }

    /** How many times over a fire hit on this victim lands right now: 1 without a boss fire. */
    public static int multiplier(LivingEntity victim) {
        return BURNS.multiplier(victim.getUUID(), victim.level().getGameTime());
    }

    /** Whether any boss fire is remembered at all, so the damage path skips the lookup. */
    public static boolean hasPending() {
        return !BURNS.isEmpty();
    }

    /** Drops a victim that died or left, so whatever burns them next starts out as vanilla. */
    public static void forget(Entity victim) {
        BURNS.forget(victim.getUUID());
    }

    /**
     * Drops this level's burns that are over, and those whose victim is no longer burning.
     *
     * <p>The second half keeps "while the boss' fire burns" honest: water puts a fire out long
     * before its time is up, and lava stepped into afterwards is not the boss' fire.</p>
     */
    public static void tick(ServerLevel level) {
        BURNS.sweep(level.dimension(), level.getGameTime(),
                victim -> level.getEntity(victim) instanceof Entity entity && !entity.isOnFire());
    }

    public static void clearLevel(ServerLevel level) {
        BURNS.clear(level.dimension());
    }

    /** One remembered burn: where it was lit, how many times over it bites, and until when. */
    record Burn(ResourceKey<Level> dimension, int multiplier, long until) {
        boolean isLit(long now) {
            return now < until;
        }
    }

    /** The bookkeeping on its own, apart from any world, so it can be checked without one. */
    static final class Burns {
        private final Map<UUID, Burn> byVictim = new HashMap<>();

        /**
         * Remembers a burn, unless the victim carries a stronger one - or an equally strong one
         * that lasts longer - which is still lit.
         */
        void light(UUID victim, ResourceKey<Level> dimension, int multiplier, long until, long now) {
            // Vanilla-strength fire has nothing to multiply, and must not push out a hotter one.
            if (multiplier <= 1) {
                return;
            }
            Burn lit = byVictim.get(victim);
            if (lit == null || !lit.isLit(now) || multiplier > lit.multiplier()
                    || multiplier == lit.multiplier() && until > lit.until()) {
                byVictim.put(victim, new Burn(dimension, multiplier, until));
            }
        }

        int multiplier(UUID victim, long now) {
            Burn burn = byVictim.get(victim);
            return burn != null && burn.isLit(now) ? burn.multiplier() : 1;
        }

        void forget(UUID victim) {
            byVictim.remove(victim);
        }

        boolean isEmpty() {
            return byVictim.isEmpty();
        }

        /** Drops the burns lit in {@code dimension} that are over or whose victim went out. */
        void sweep(ResourceKey<Level> dimension, long now, Predicate<UUID> wentOut) {
            byVictim.entrySet().removeIf(entry -> entry.getValue().dimension().equals(dimension)
                    && (!entry.getValue().isLit(now) || wentOut.test(entry.getKey())));
        }

        void clear(ResourceKey<Level> dimension) {
            byVictim.values().removeIf(burn -> burn.dimension().equals(dimension));
        }
    }
}
