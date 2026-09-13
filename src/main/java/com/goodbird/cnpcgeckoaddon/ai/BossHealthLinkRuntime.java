package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Bosses tied into one life by a group name: one health for all of them, or a promise to die
 * together.
 *
 * <p>Owned by {@link TeleportPathController}, one per boss, and holding only what that boss is
 * going through right now. Who its partners are is never stored: it is asked of the live
 * controllers each time, so a partner that died, unloaded or walked out of range stops counting
 * on the tick it did, and nothing of the link reaches the save.</p>
 */
final class BossHealthLinkRuntime {

    /**
     * Set while the link itself is moving health about or killing, so that what it causes is not
     * linked a second time: a partner killed because its boss died must not go on to kill the boss
     * that died, nor share out the killing blow it was dealt.
     */
    private static boolean linking;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /**
     * Shared health: a partner's hit took this boss' share of the pool below nothing, and it dies
     * on its own next tick - which takes the rest of the group with it.
     */
    private boolean poolEmpty;

    BossHealthLinkRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /**
     * Runs what the link owes this boss on this tick.
     *
     * <p>Asked before anything else the controller does, because it can kill the boss outright,
     * and nothing further down has to run on a boss dying on this tick.</p>
     *
     * @return true when the link killed this boss
     */
    boolean tick(ServerLevel level, TeleportPathData data, long gameTime) {
        if (!poolEmpty) {
            return false;
        }
        poolEmpty = false;
        if (!isShared(data) || !npc.isAlive()) {
            return false;
        }
        // Not under the flag: the ordinary death is what takes the rest of the group along.
        npc.hurt(npc.damageSources().genericKill(), Float.MAX_VALUE);
        return true;
    }

    /**
     * Shared health: the share of its maximum this boss has just lost comes off every partner's
     * maximum too.
     *
     * <p>Through the partners' health rather than through a hit on them: each keeps its own
     * shields and resistances, and they had their say on the hit that landed here - what goes round
     * is what got through. A killing blow is not shared out at all: the death after it takes the
     * partners whole.</p>
     */
    void shareLoss(TeleportPathData data, float damage) {
        if (linking || damage <= 0.0F || !isShared(data) || npc.isDeadOrDying()) {
            return;
        }
        float share = share(damage, npc.getMaxHealth());
        for (TeleportPathController partner : linkedTo(npc, data)) {
            partner.healthLink().loseShare(share);
        }
    }

    /**
     * Shared health: the share of its maximum this boss is about to get back goes to every partner
     * too - only what will really land, so a boss healing at full health fills nobody's pool.
     */
    void shareGain(TeleportPathData data, float amount) {
        if (linking || !isShared(data)) {
            return;
        }
        float share = healedShare(amount, npc.getHealth(), npc.getMaxHealth());
        if (share <= 0.0F) {
            return;
        }
        for (TeleportPathController partner : linkedTo(npc, data)) {
            partner.healthLink().gainShare(share);
        }
    }

    private void loseShare(float share) {
        if (poolEmpty || !npc.isAlive()) {
            return;
        }
        float after = afterLoss(npc.getHealth(), npc.getMaxHealth(), share);
        if (after > 0.0F) {
            npc.setHealth(after);
            return;
        }
        // The shares had drifted apart - one boss hurt before the link, a party scaling that kept
        // current health - and the pool ran dry here first. It dies the way the group dies, but on
        // its own tick: killed from inside the hit still landing on its partner, the death it sets
        // off would reach that partner mid-hit and run the partner's death twice.
        npc.setHealth(Float.MIN_NORMAL);
        poolEmpty = true;
    }

    private void gainShare(float share) {
        if (poolEmpty || !npc.isAlive()) {
            return;
        }
        npc.setHealth(afterGain(npc.getHealth(), npc.getMaxHealth(), share));
    }

    /**
     * A reset that heals this boss refills the pool it shares: every partner is healed to full by
     * the same call. A reset that does not heal moves nobody's health, this boss' included.
     */
    void onEncounterReset(TeleportPathData data, boolean heal) {
        if (!heal || !isShared(data)) {
            return;
        }
        for (TeleportPathController partner : linkedTo(npc, data)) {
            partner.healthLink().refill();
        }
    }

    private void refill() {
        poolEmpty = false;
        if (npc.isAlive()) {
            npc.setHealth(npc.getMaxHealth());
        }
    }

    /** Drops whatever the link was holding; every way out of a fight goes through here. */
    void clear() {
        poolEmpty = false;
    }

    /**
     * What one linked boss' death does to the rest of its group.
     *
     * <p>Shared health: the pool is empty, so every partner dies with it, each by the ordinary
     * death - its own drops, chest, blast and minions - and none of those deaths goes round again.
     * Called from the death event, where CustomNPCs has already dropped this boss' loot.</p>
     */
    static void onBossDeath(EntityNPCInterface npc, TeleportPathData data) {
        if (linking || !isShared(data)) {
            return;
        }
        List<TeleportPathController> rest = linkedTo(npc, data);
        if (!rest.isEmpty()) {
            killAll(rest);
        }
    }

    /** Kills each of these the way /kill does, with the link held off what their deaths set off. */
    private static void killAll(Collection<TeleportPathController> members) {
        boolean outer = linking;
        linking = true;
        try {
            for (TeleportPathController member : members) {
                EntityNPCInterface body = member.npc();
                if (body.isAlive()) {
                    body.hurt(body.damageSources().genericKill(), Float.MAX_VALUE);
                }
            }
        } finally {
            linking = outer;
        }
    }

    private static boolean isShared(TeleportPathData data) {
        return data.isEnabled() && data.isHealthLinked()
                && data.getHealthLinkMode() == TeleportPathData.HEALTH_LINK_SHARED;
    }

    /**
     * Every boss this one is linked to right now, itself left out: its partners, their partners and
     * so on, so the group is the same whichever of its bosses asks.
     *
     * <p>Works for a boss that has just died too - the death event asks it - since only the other
     * members have to be alive.</p>
     */
    static List<TeleportPathController> linkedTo(EntityNPCInterface npc, TeleportPathData data) {
        if (!data.isEnabled() || !data.isHealthLinked()) {
            return List.of();
        }
        Map<EntityNPCInterface, TeleportPathController> live = new IdentityHashMap<>();
        for (TeleportPathController controller : TeleportPathController.liveControllers()) {
            live.put(controller.npc(), controller);
        }
        Set<EntityNPCInterface> group = connected(npc, member -> {
            TeleportPathController own = live.get(member);
            TeleportPathData memberData = own == null ? data : own.settings();
            List<EntityNPCInterface> partners = new ArrayList<>();
            for (Map.Entry<EntityNPCInterface, TeleportPathController> other : live.entrySet()) {
                if (arePartners(member, memberData, other.getKey(), other.getValue().settings())) {
                    partners.add(other.getKey());
                }
            }
            return partners;
        });
        List<TeleportPathController> linked = new ArrayList<>();
        for (EntityNPCInterface member : group) {
            TeleportPathController controller = live.get(member);
            if (member != npc && controller != null) {
                linked.add(controller);
            }
        }
        return linked;
    }

    /**
     * Whether {@code other} is a partner of {@code boss}: alive, switched on, in the same level and
     * in a chunk that is ticking, of the same group and mode, and within both bosses' range.
     *
     * <p>A partner in an unloaded chunk is not one: it is not there to be fought. Both ranges are
     * asked so a pair is partners from either side or from neither, and the mode has to match so a
     * boss sharing its health never pours it into one lying down.</p>
     */
    private static boolean arePartners(EntityNPCInterface boss, TeleportPathData bossData,
                                       EntityNPCInterface other, TeleportPathData otherData) {
        if (other == boss || !other.isAlive() || other.isKilled() || other.level() != boss.level()
                || !otherData.isEnabled() || !sameLink(bossData, otherData)) {
            return false;
        }
        if (!(other.level() instanceof ServerLevel level) || !level.isPositionEntityTicking(other.blockPosition())) {
            return false;
        }
        double distanceSquared = boss.distanceToSqr(other);
        return withinRange(distanceSquared, bossData.getHealthLinkRange())
                && withinRange(distanceSquared, otherData.getHealthLinkRange());
    }

    /** Whether two bosses' settings tie them together: the same group, set, and the same mode. */
    static boolean sameLink(TeleportPathData a, TeleportPathData b) {
        return a.isHealthLinked() && a.getHealthLinkGroup().equals(b.getHealthLinkGroup())
                && a.getHealthLinkMode() == b.getHealthLinkMode();
    }

    /** Whether a partner this far away counts; a range of nothing is the whole level. */
    static boolean withinRange(double distanceSquared, int range) {
        return range <= 0 || distanceSquared <= (double) range * range;
    }

    /** Everything reachable from {@code start} through {@code neighbours}, the start included. */
    static <T> Set<T> connected(T start, Function<T, ? extends Collection<T>> neighbours) {
        Set<T> seen = new LinkedHashSet<>();
        Deque<T> open = new ArrayDeque<>();
        seen.add(start);
        open.add(start);
        while (!open.isEmpty()) {
            for (T next : neighbours.apply(open.poll())) {
                if (seen.add(next)) {
                    open.add(next);
                }
            }
        }
        return seen;
    }

    /** What a loss of {@code amount} is as a share of a boss' maximum health. */
    static float share(float amount, float maxHealth) {
        return amount <= 0.0F || maxHealth <= 0.0F ? 0.0F : amount / maxHealth;
    }

    /**
     * What a heal of {@code amount} will really give back, as a share of the maximum: nothing to a
     * boss with no health left to heal from, and never past full.
     */
    static float healedShare(float amount, float health, float maxHealth) {
        if (amount <= 0.0F || health <= 0.0F || maxHealth <= 0.0F) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(amount, maxHealth - health)) / maxHealth;
    }

    /** A partner's health after losing {@code share} of its maximum; at or under nothing, its pool ran dry. */
    static float afterLoss(float health, float maxHealth, float share) {
        return health - share * maxHealth;
    }

    /** A partner's health after getting {@code share} of its maximum back, never past full. */
    static float afterGain(float health, float maxHealth, float share) {
        return Math.min(maxHealth, health + share * maxHealth);
    }
}
