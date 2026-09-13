package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import java.util.UUID;
import java.util.function.Function;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

/**
 * Bosses tied into one life by a group name: one health for all of them, or a promise to die
 * together.
 *
 * <p>Owned by {@link TeleportPathController}, one per boss, and holding only what that boss is
 * going through right now. Who its partners are is never stored: it is asked of the live
 * controllers each time, so a partner that died, unloaded or walked out of range stops counting
 * on the tick it did, and nothing of the link reaches the save - a boss lying down when the
 * server stops simply is not lying down when it starts.</p>
 */
final class BossHealthLinkRuntime {

    /** What a downed boss' tick comes to, worked out without a world by {@link #verdict}. */
    enum Verdict {
        /** Its partners are still standing and the window is still open. */
        WAIT,
        /** Nobody to wait for - the only one of its group, or the rest unloaded or out of range. */
        DIE_ALONE,
        /** Every partner is down as well: the whole group dies for real. */
        DIE_TOGETHER,
        /** The window ran out with a partner still standing. */
        GET_UP
    }

    private static final int TICKS_PER_SECOND = 20;
    /** The countdown's red: the one line in the action bar that says the clock is on the party. */
    private static final int DOWNED_COLOR = 0xFF6A5A;

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
    /** Must die together: game time this boss went down at, or NOT_SCHEDULED while it stands. */
    private long downedAt = NOT_SCHEDULED;
    /** Must die together: game time it gets up at unless the rest fall first. */
    private long downedUntil = NOT_SCHEDULED;

    BossHealthLinkRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /**
     * Runs what the link owes this boss on this tick.
     *
     * <p>Asked before anything else the controller does, because it can kill the boss outright,
     * and nothing further down has to run on a boss dying on this tick. The deaths are dealt from
     * here rather than from the hit that set them up, since a kill from inside a hit still landing
     * on a boss reaches that boss mid-hit and runs its death twice.</p>
     *
     * @return true when the link killed this boss
     */
    boolean tick(ServerLevel level, TeleportPathData data, long gameTime) {
        if (poolEmpty) {
            poolEmpty = false;
            if (isShared(data) && npc.isAlive()) {
                // Not under the flag: the ordinary death is what takes the rest of the group along.
                npc.hurt(npc.damageSources().genericKill(), Float.MAX_VALUE);
                return true;
            }
        }
        if (!isDowned()) {
            return false;
        }
        if (!isTogether(data)) {
            // Unlinked, or set to share its health, while it lay there: nothing holds it down now.
            releaseDown();
            return false;
        }
        List<TeleportPathController> partners = linkedTo(npc, data);
        switch (verdict(partners.size(), allDowned(partners), gameTime, downedUntil)) {
            case DIE_ALONE -> {
                killAll(List.of(boss));
                return true;
            }
            case DIE_TOGETHER -> {
                List<TeleportPathController> group = new ArrayList<>(partners);
                group.add(boss);
                killAll(group);
                return true;
            }
            case GET_UP -> getUp(level, data);
            case WAIT -> {
                if (announcesOn(downedAt, gameTime)) {
                    announce(level, gameTime, partners);
                }
            }
        }
        return false;
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
        // current health - and the pool ran dry here first. It dies the way the group dies, on
        // its own tick.
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
     * Must die together: a killing blow on this boss lays it down instead, while it has a partner
     * still standing.
     *
     * <p>A boss with no partner, or whose partners are all down already, is not laid down at all:
     * the hit kills it the ordinary way, and its death takes whoever is lying there along. Nor is
     * one whose lethal-guard totems stand - they hold this hit on their own, and a boss that cannot
     * be killed has nothing to go down from.</p>
     *
     * @return true when the boss went down, and the hit has to be cut to leave it its last health
     */
    boolean downOnLethalHit(TeleportPathData data, float damage) {
        if (linking || isDowned() || !isTogether(data) || !isLethal(damage, npc.getHealth())
                || !(npc.level() instanceof ServerLevel level)) {
            return false;
        }
        if (boss.isTotemProtected() && data.getTotemProtectionMode() == TeleportPathData.TOTEM_PROTECTION_LETHAL_GUARD) {
            return false;
        }
        List<TeleportPathController> partners = linkedTo(npc, data);
        if (!downsOnLethalHit(partners.size(), allDowned(partners))) {
            return false;
        }
        long gameTime = level.getGameTime();
        downedAt = gameTime;
        downedUntil = downedUntil(gameTime, data.getHealthLinkWindowTicks());
        // The stagger's interrupt: the wind-up is dropped and comes back round once the boss is up.
        boss.interruptForBarrierStun(downedUntil);
        boss.playAnimation(data.getHealthLinkDownedAnimation());
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.RAVAGER_STUNNED,
                SoundSource.HOSTILE, 1.2F, 0.7F);
        level.sendParticles(ParticleTypes.SOUL, npc.getX(), npc.getY(0.5D), npc.getZ(), 24,
                npc.getBbWidth() * 0.5D, npc.getBbHeight() * 0.4D, npc.getBbWidth() * 0.5D, 0.02D);
        announce(level, gameTime, partners);
        return true;
    }

    /** The window ran out with a partner standing: back up on the share of health the builder gave it. */
    private void getUp(ServerLevel level, TeleportPathData data) {
        releaseDown();
        float health = reviveHealth(npc.getMaxHealth(), data.getHealthLinkRevivePercent());
        npc.setHealth(Math.max(npc.getHealth(), health));
        boss.playAnimation(data.getHealthLinkReviveAnimation());
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.TOTEM_USE,
                SoundSource.HOSTILE, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, npc.getX(), npc.getY(0.6D), npc.getZ(), 40,
                npc.getBbWidth() * 0.5D, npc.getBbHeight() * 0.4D, npc.getBbWidth() * 0.5D, 0.3D);
    }

    /**
     * The name and the seconds left, in the action bar of everyone fighting this boss or any of
     * its partners - the ones who still have to fall are where the party has to be.
     *
     * <p>The numbers go in through %s, the one placeholder vanilla's translation formatter takes.</p>
     */
    private void announce(ServerLevel level, long gameTime, List<TeleportPathController> partners) {
        Component line = Component.translatable("cnpcgeckoaddon.boss.health_link_downed", npc.getName(),
                        secondsLeft(downedUntil, gameTime))
                .withStyle(style -> style.withColor(DOWNED_COLOR));
        Set<ServerPlayer> audience = new LinkedHashSet<>();
        addAudience(level, boss, audience);
        for (TeleportPathController partner : partners) {
            addAudience(level, partner, audience);
        }
        for (ServerPlayer player : audience) {
            player.displayClientMessage(line, true);
        }
    }

    /** Everyone with this boss' bar up plus everyone signed into its fight, the hazard's audience. */
    private static void addAudience(ServerLevel level, TeleportPathController controller, Set<ServerPlayer> audience) {
        audience.addAll(controller.timerBossEvent().getPlayers());
        for (UUID playerId : controller.encounterParticipants()) {
            if (level.getPlayerByUUID(playerId) instanceof ServerPlayer player) {
                audience.add(player);
            }
        }
    }

    /**
     * Read-only status used by the boss diagnostic command: the group, the mode, how many partners
     * the link finds right now and, for a boss lying down, how long it still lies there.
     */
    String status(TeleportPathData data, long gameTime) {
        if (!data.isHealthLinked()) {
            return "Health link: off";
        }
        String mode = data.getHealthLinkMode() == TeleportPathData.HEALTH_LINK_TOGETHER ? "die together" : "shared";
        String line = "Health link: group " + data.getHealthLinkGroup() + ", " + mode
                + ", partners " + linkedTo(npc, data).size();
        return isDowned() ? line + ", downed " + downedTicksLeft(gameTime) + " ticks" : line;
    }

    /** Whether this boss is lying down right now, waiting on its partners. */
    boolean isDowned() {
        return downedUntil != NOT_SCHEDULED;
    }

    /** Ticks until a downed boss gets up, or 0 while it stands. */
    long downedTicksLeft(long gameTime) {
        return isDowned() ? Math.max(0L, downedUntil - gameTime) : 0L;
    }

    /**
     * The fight ending under this boss.
     *
     * <p>A boss lying down gets up with it, on whatever health the reset leaves it - healed when
     * the reset heals, on its last point when it does not. And a reset that heals refills the pool
     * a shared boss is part of: every partner is healed to full by the same call. The encounters
     * themselves stay apart, so a partner's own fight goes on.</p>
     */
    void onEncounterReset(TeleportPathData data, boolean heal) {
        if (isDowned()) {
            releaseDown();
            boss.playAnimation(data.getHealthLinkReviveAnimation());
        }
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

    private void releaseDown() {
        downedAt = NOT_SCHEDULED;
        downedUntil = NOT_SCHEDULED;
    }

    /** Drops whatever the link was holding; every way out of a fight goes through here. */
    void clear() {
        poolEmpty = false;
        releaseDown();
    }

    /**
     * What one linked boss' death does to the rest of its group.
     *
     * <p>Shared health: the pool is empty, so every partner dies with it. Must die together: once
     * nobody of the group is left standing, whoever is lying down dies too - a boss killed through
     * a /kill while its partners stand takes nobody. Each dies its own ordinary death, drops, chest,
     * blast and minions, and none of those deaths goes round again. Called from the death event,
     * where CustomNPCs has already dropped this boss' loot.</p>
     */
    static void onBossDeath(EntityNPCInterface npc, TeleportPathData data) {
        if (linking || !data.isEnabled() || !data.isHealthLinked()) {
            return;
        }
        List<TeleportPathController> rest = linkedTo(npc, data);
        if (data.getHealthLinkMode() == TeleportPathData.HEALTH_LINK_SHARED
                ? !rest.isEmpty() : deathTakesTheRest(rest.size(), allDowned(rest))) {
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

    private static boolean allDowned(List<TeleportPathController> members) {
        for (TeleportPathController member : members) {
            if (!member.healthLink().isDowned()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isShared(TeleportPathData data) {
        return data.isEnabled() && data.isHealthLinked()
                && data.getHealthLinkMode() == TeleportPathData.HEALTH_LINK_SHARED;
    }

    private static boolean isTogether(TeleportPathData data) {
        return data.isEnabled() && data.isHealthLinked()
                && data.getHealthLinkMode() == TeleportPathData.HEALTH_LINK_TOGETHER;
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

    /** Whether a hit for {@code damage} kills a boss on {@code health}. */
    static boolean isLethal(float damage, float health) {
        return damage >= health;
    }

    /** What a killing blow is cut to when it lays a boss down: all of its health but the last point. */
    static float downedDamage(float health) {
        return Math.max(0.0F, health - 1.0F);
    }

    /** Whether a killing blow lays the boss down: only while it has partners, and one of them stands. */
    static boolean downsOnLethalHit(int partners, boolean allPartnersDowned) {
        return partners > 0 && !allPartnersDowned;
    }

    /** Whether a must-die-together death takes the rest along: only once all that is left of the group lies down. */
    static boolean deathTakesTheRest(int rest, boolean allRestDowned) {
        return rest > 0 && allRestDowned;
    }

    static long downedUntil(long gameTime, int windowTicks) {
        return gameTime + windowTicks;
    }

    /**
     * What a downed boss' tick comes to. The deaths are read before the clock: a partner that fell
     * on the window's last tick still takes the group down with it.
     */
    static Verdict verdict(int partners, boolean allPartnersDowned, long gameTime, long downedUntil) {
        if (partners <= 0) {
            return Verdict.DIE_ALONE;
        }
        if (allPartnersDowned) {
            return Verdict.DIE_TOGETHER;
        }
        return gameTime >= downedUntil ? Verdict.GET_UP : Verdict.WAIT;
    }

    /** The health a boss gets up with: its share of the maximum, never nothing. */
    static float reviveHealth(float maxHealth, int percent) {
        return maxHealth * Math.max(TeleportPathData.MIN_HEALTH_LINK_REVIVE_PERCENT,
                Math.min(TeleportPathData.MAX_HEALTH_LINK_REVIVE_PERCENT, percent)) / 100.0F;
    }

    /** Seconds left on the window, rounded up so the last one reads as one rather than as none. */
    static int secondsLeft(long downedUntil, long gameTime) {
        return (int) Math.max(1L, (downedUntil - gameTime + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND);
    }

    /** Whether the countdown is told on this tick: the tick the boss went down, and once a second after. */
    static boolean announcesOn(long downedAt, long gameTime) {
        return (gameTime - downedAt) % TICKS_PER_SECOND == 0L;
    }
}
