package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossBarStyles;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

/**
 * The shield with a timer: break it in time and the boss stands stunned and takes more,
 * miss the clock and the party pays by the phase's rule.
 *
 * <p>Owned by {@link TeleportPathController}, which arms it on every phase it enters and
 * ticks it above its own combat gates. The hits themselves come in through the damage
 * handler, which calls {@link #absorb} from inside the hit it has already cancelled.</p>
 */
final class BossBarrierRuntime {

    /** How often a standing barrier's aura is painted and its count told to the party. */
    private static final int PAINT_INTERVAL_TICKS = 5;
    /**
     * Vanilla's hurt cooldown, kept by the barrier for itself.
     *
     * <p>A hit the barrier pays for is cancelled before vanilla sees it, so vanilla never
     * arms the ten ticks after a hit in which only the excess over the last one lands. The
     * barrier is meant to be the boss' health standing still, not a softer target than it,
     * so it keeps that rule: a barrier that took every spam click in full would fall to a
     * held button faster than the health behind it ever could.</p>
     */
    private static final int HURT_COOLDOWN_TICKS = 10;

    /**
     * The barrier standing right now, frozen on the tick it went up.
     *
     * <p>Read back from here rather than off the phase again, the way a hunt keeps its
     * settings: the absorb is a count that has to live somewhere, and the outcome the party
     * was set to play for must not change under them halfway through. Nothing of this is
     * saved - a server that goes down mid barrier owes nobody the rest of it, and the next
     * one goes up by its own trigger once the boss is pulled again.</p>
     */
    private static final class Barrier {
        private final float total;
        private float left;
        /** Game time the party's chance runs out at, or NOT_SCHEDULED for a barrier with no clock. */
        private final long expiresAt;
        private final int breakWindowTicks;
        private final int breakDamagePercent;
        private final int failMode;
        private final int failDamage;
        private final int failHealPercent;
        private final BossEffectSet failEffects;
        private final String breakAnimation;
        /** Ticks after either outcome until the next barrier, or 0 for a barrier that goes up once. */
        private final int repeatTicks;
        /** Game time vanilla's hurt cooldown, kept by the barrier itself, runs out at. */
        private long cooldownUntil = NOT_SCHEDULED;
        /** The last hit inside that cooldown, which a later one only lands its excess over. */
        private float lastHurt;

        private Barrier(BossPhaseData phase, float absorb, long gameTime) {
            total = absorb;
            left = absorb;
            expiresAt = phase.barrier().getTimeoutTicks() > 0
                    ? gameTime + phase.barrier().getTimeoutTicks() : NOT_SCHEDULED;
            breakWindowTicks = phase.barrier().getBreakWindowTicks();
            breakDamagePercent = phase.barrier().getBreakDamageTakenPercent();
            failMode = phase.barrier().getFailMode();
            failDamage = phase.barrier().getFailDamage();
            failHealPercent = phase.barrier().getFailHealPercent();
            failEffects = phase.barrier().getFailEffects();
            breakAnimation = phase.barrier().getBreakAnimation();
            repeatTicks = phase.barrier().getTrigger() == BossPhaseData.BARRIER_TRIGGER_TIMER
                    ? phase.barrier().getIntervalTicks() : 0;
        }
    }

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /** The barrier standing right now, or null while hits reach the boss' own health. */
    private Barrier barrier;
    /** Game time the window a broken barrier opened closes at, or NOT_SCHEDULED outside one. */
    private long exposedUntil = NOT_SCHEDULED;
    /** What the boss takes inside that window, as a percentage of the hit. */
    private int exposedPercent = 100;
    /** Game time a timer rule's next barrier goes up at, or NOT_SCHEDULED while none is owed. */
    private long nextBarrierAt = NOT_SCHEDULED;

    BossBarrierRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /**
     * Arms the barrier of the phase the boss is fighting in, and drops the last one's:
     * the shield, the window it opened and the next one it owed.
     *
     * <p>Whatever the new phase brings, the old barrier goes, the way the hazard does: a
     * phase change resets the check. Only inside a fight does a new one go up - the boss
     * enters its first phase when it merely loads, and the pull arms that one instead.</p>
     */
    void arm(ServerLevel level, long gameTime, BossPhaseData phase) {
        clear();
        if (boss.isEncounterRunning() && phase.barrier().isEnabled()) {
            raise(level, gameTime, phase);
        }
    }

    private void raise(ServerLevel level, long gameTime, BossPhaseData phase) {
        nextBarrierAt = NOT_SCHEDULED;
        barrier = new Barrier(phase, phase.barrier().barrierAbsorb(npc.getMaxHealth()), gameTime);
        boss.playAnimation(phase.barrier().getAnimation());
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.HOSTILE, 1.0F, 1.3F);
        level.sendParticles(dust(), npc.getX(), npc.getY(0.5D), npc.getZ(), 40,
                npc.getBbWidth() * 0.8D, npc.getBbHeight() * 0.5D, npc.getBbWidth() * 0.8D, 0.0D);
        announce(level, gameTime);
    }

    /**
     * Runs the barrier of the phase being fought: the clock while it stands, the window
     * after it breaks, and the next one a timer rule owes.
     *
     * <p>The hits themselves come in through the damage handler, not through here.</p>
     */
    void tick(ServerLevel level, TeleportPathData data, long gameTime) {
        if (barrier == null && exposedUntil == NOT_SCHEDULED && nextBarrierAt == NOT_SCHEDULED) {
            return;
        }
        BossPhaseData phase = data.getPhase(boss.currentPhaseIndex());
        // Switched off mid-fight, everything goes at once rather than running on until the
        // phase ends; a window is taken back with it, multiplier and stun included.
        if (!boss.isEncounterRunning() || !phase.barrier().isEnabled()) {
            clear();
            return;
        }
        Barrier standing = barrier;
        if (standing != null) {
            if (standing.expiresAt != NOT_SCHEDULED && gameTime >= standing.expiresAt) {
                fail(level, data, standing, gameTime);
                return;
            }
            if (gameTime % PAINT_INTERVAL_TICKS == 0L) {
                paint(level);
                announce(level, gameTime);
            }
            return;
        }
        if (exposedUntil != NOT_SCHEDULED) {
            if (gameTime < exposedUntil) {
                if (gameTime % PAINT_INTERVAL_TICKS == 0L) {
                    announceExposed(level);
                }
                return;
            }
            endExposure();
        }
        // Only once the window is shut: a shield going up over an exposed boss would take
        // the window's promise back early.
        if (nextBarrierAt != NOT_SCHEDULED && gameTime >= nextBarrierAt) {
            raise(level, gameTime, phase);
        }
    }

    /** Whether a barrier stands right now. Read by the damage handler and the status line. */
    boolean isUp() {
        return boss.isActive() && barrier != null;
    }

    /** What the standing barrier still absorbs, or 0 when none stands. */
    float left() {
        return isUp() ? barrier.left : 0.0F;
    }

    /**
     * Pays one hit out of the barrier, and breaks it when the hit is the last it can take.
     *
     * <p>Called from inside the hit, the way the immune phase's feedback is: the handler
     * has already cancelled it, so nothing here reaches the boss' health. A hit inside the
     * barrier's own hurt cooldown only lands its excess over the last one, exactly as
     * vanilla would have let it; the excess of the breaking hit is dropped rather than
     * passed on - the shield holds the whole of the last blow.</p>
     *
     * @param bypassesCooldown whether the source is one vanilla lets straight through the cooldown
     * @return how much the barrier took; 0 for a hit the cooldown dropped whole
     */
    float absorb(float amount, boolean bypassesCooldown) {
        Barrier standing = barrier;
        if (!isUp() || amount <= 0.0F || !(npc.level() instanceof ServerLevel level)) {
            return 0.0F;
        }
        long gameTime = level.getGameTime();
        float landing = amount;
        if (gameTime < standing.cooldownUntil && !bypassesCooldown) {
            if (amount <= standing.lastHurt) {
                return 0.0F;
            }
            landing = amount - standing.lastHurt;
            standing.lastHurt = amount;
        } else {
            standing.lastHurt = amount;
            standing.cooldownUntil = gameTime + HURT_COOLDOWN_TICKS;
        }
        float absorbed = Math.min(landing, standing.left);
        standing.left -= absorbed;
        if (standing.left <= 0.0F) {
            breakDown(level, standing, gameTime);
        } else {
            playHitFeedback(level, gameTime);
        }
        return absorbed;
    }

    /**
     * The party got through in time: the shield shatters and, when the phase gives one,
     * the window opens - the boss stands stunned and takes more for as long as it lasts.
     */
    private void breakDown(ServerLevel level, Barrier broken, long gameTime) {
        barrier = null;
        boss.playAnimation(broken.breakAnimation);
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.SHIELD_BREAK,
                SoundSource.HOSTILE, 1.5F, 0.6F);
        level.sendParticles(ParticleTypes.END_ROD, npc.getX(), npc.getY(0.6D), npc.getZ(), 40,
                npc.getBbWidth() * 0.6D, npc.getBbHeight() * 0.4D, npc.getBbWidth() * 0.6D, 0.15D);
        scheduleNext(broken, gameTime);
        if (broken.breakWindowTicks <= 0) {
            return;
        }
        exposedUntil = gameTime + broken.breakWindowTicks;
        exposedPercent = broken.breakDamagePercent;
        boss.interruptForBarrierStun(exposedUntil);
        announceExposed(level);
    }

    /**
     * The clock ran out with the shield still up: the party pays, by the phase's rule.
     *
     * <p>The enrage rule sets the boss' own enrage off early and never a second one; a boss
     * with no enrage to set off falls back to hitting everyone, so the rule always costs
     * something. The potions land on everyone whichever rule it was.</p>
     */
    private void fail(ServerLevel level, TeleportPathData data, Barrier failed, long gameTime) {
        barrier = null;
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.HOSTILE, 1.5F, 0.6F);
        int mode = failed.failMode;
        if (mode == BossPhaseData.BARRIER_FAIL_RAGE && !data.isRageEnabled()) {
            mode = BossPhaseData.BARRIER_FAIL_DAMAGE;
        }
        if (mode == BossPhaseData.BARRIER_FAIL_RAGE) {
            // Already enraged, there is nothing left to set off: the timer beat the barrier to it.
            if (!boss.isRageActive()) {
                boss.beginRage(level, gameTime, data);
            }
        } else if (mode == BossPhaseData.BARRIER_FAIL_HEAL) {
            npc.heal(npc.getMaxHealth() * failed.failHealPercent / 100.0F);
            level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.TOTEM_USE,
                    SoundSource.HOSTILE, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.HEART, npc.getX(), npc.getY(0.7D), npc.getZ(), 20,
                    npc.getBbWidth() * 0.6D, npc.getBbHeight() * 0.4D, npc.getBbWidth() * 0.6D, 0.0D);
        } else {
            level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE,
                    SoundSource.HOSTILE, 1.0F, 0.8F);
        }
        int damage = mode == BossPhaseData.BARRIER_FAIL_DAMAGE ? boss.rageUp(failed.failDamage) : 0;
        for (ServerPlayer player : audience(level)) {
            // The bar's other viewers are watching, not fighting, and somebody who has gone
            // creative or died since they signed in is out of the fight: the party pays.
            if (!boss.isEncounterParticipant(player) || !boss.isParticipant(player)) {
                continue;
            }
            // No knockback: the price is the hit, not a shove. Under no ability's name, so
            // the immunity list has nothing to say - and nothing else the fail lands on can
            // be immune, because it lands on players alone.
            BossAbilityDamageUtil.hit(player, BossAbilityDamageUtil.NO_ABILITY, npc, damage,
                    failed.failEffects, 0, 0.0D, 0.0D);
        }
        scheduleNext(failed, gameTime);
    }

    /** A timer rule owes the next barrier this long after either outcome; a one-off owes nothing. */
    private void scheduleNext(Barrier ended, long gameTime) {
        nextBarrierAt = ended.repeatTicks > 0 ? gameTime + ended.repeatTicks : NOT_SCHEDULED;
    }

    private void endExposure() {
        exposedUntil = NOT_SCHEDULED;
        exposedPercent = 100;
    }

    /**
     * Takes the whole barrier down: the shield, the window and the next one owed.
     *
     * <p>Idempotent and the one road out, so every ending - a phase change, a reset, the
     * boss dying, the level unloading, the setting switched off - shuts the window with
     * it. A multiplier or a stun left standing here would outlive the fight it belonged to.</p>
     */
    void clear() {
        barrier = null;
        nextBarrierAt = NOT_SCHEDULED;
        endExposure();
    }

    /** True while a broken barrier's window has the boss taking more than it usually does. */
    boolean isExposed() {
        return boss.isActive() && exposedUntil != NOT_SCHEDULED;
    }

    /** What the boss takes inside the window, as a percentage; 100 outside one. */
    int exposedPercent() {
        return isExposed() ? exposedPercent : 100;
    }

    /** Read-only status used by the boss diagnostic command. */
    String status(long gameTime) {
        Barrier standing = barrier;
        if (standing != null) {
            String clock = standing.expiresAt == NOT_SCHEDULED ? "no timer"
                    : Math.max(0L, standing.expiresAt - gameTime) + " ticks left";
            return "Barrier: up, " + TeleportPathController.formatHealth(standing.left) + "/"
                    + TeleportPathController.formatHealth(standing.total) + " absorb left, " + clock;
        }
        if (isExposed()) {
            return "Barrier: broken, exposed " + Math.max(0L, exposedUntil - gameTime)
                    + " ticks left, damage taken " + exposedPercent + "%";
        }
        BossPhaseData phase = boss.activePhase();
        if (phase == null || !phase.barrier().isEnabled()) {
            return "Barrier: disabled";
        }
        if (nextBarrierAt != NOT_SCHEDULED) {
            return "Barrier: down, next in " + Math.max(0L, nextBarrierAt - gameTime) + " ticks";
        }
        return "Barrier: down";
    }

    /**
     * The shield made visible: a loose ring of dust round the boss' body, in the bar's colour.
     *
     * <p>Decoration, so a barrier nobody is near enough to see costs nothing. Scattered up
     * the body rather than laid on the floor, because the shield is on the boss and not on
     * the arena.</p>
     */
    private void paint(ServerLevel level) {
        if (level.getNearestPlayer(npc.getX(), npc.getY(), npc.getZ(),
                BossTelegraphUtil.AUDIENCE_RANGE, false) == null) {
            return;
        }
        DustParticleOptions dust = dust();
        RandomSource random = npc.getRandom();
        double radius = npc.getBbWidth() * 0.75D + 0.3D;
        double turn = random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < 8; i++) {
            double angle = turn + i * Mth.TWO_PI / 8;
            level.sendParticles(dust, npc.getX() + Math.cos(angle) * radius,
                    npc.getY() + random.nextDouble() * npc.getBbHeight(),
                    npc.getZ() + Math.sin(angle) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** The barrier's colour: the styled bar's accent when the boss has one, a white-blue otherwise. */
    private int color() {
        return BossBarStyles.get(boss.settings().getBossBarStyle()).accent();
    }

    private DustParticleOptions dust() {
        return BossTelegraphUtil.dustOf(color());
    }

    /** A chime and a few sparks for a hit the barrier took, throttled the way the immune clang is. */
    private void playHitFeedback(ServerLevel level, long gameTime) {
        if (!boss.claimBlockFeedback(gameTime)) {
            return;
        }
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.HOSTILE, 1.0F, 1.0F + npc.getRandom().nextFloat() * 0.3F);
        level.sendParticles(dust(), npc.getX(), npc.getY(0.6D), npc.getZ(), 8,
                npc.getBbWidth() * 0.6D, npc.getBbHeight() * 0.4D, npc.getBbWidth() * 0.6D, 0.0D);
    }

    /**
     * What is left and how long there is, in the action bar of everyone this fight belongs to.
     *
     * <p>Sent on every repaint rather than once, the way the hazard countdown is: the line
     * is the count the party is racing. The numbers go in through %s, which is the one
     * placeholder vanilla's translation formatter takes.</p>
     */
    private void announce(ServerLevel level, long gameTime) {
        Barrier standing = barrier;
        if (standing == null) {
            return;
        }
        int left = (int) Math.ceil(standing.left);
        Component line;
        if (standing.expiresAt == NOT_SCHEDULED) {
            line = Component.translatable("cnpcgeckoaddon.boss.barrier_status_open", left);
        } else {
            // Rounded up, so the last second reads as one rather than as none.
            int seconds = (int) Math.max(1L, (standing.expiresAt - gameTime + 19L) / 20L);
            line = Component.translatable("cnpcgeckoaddon.boss.barrier_status", left, seconds);
        }
        int color = color();
        Component styled = line.copy().withStyle(style -> style.withColor(color));
        for (ServerPlayer player : audience(level)) {
            player.displayClientMessage(styled, true);
        }
    }

    /** The window's one word, loud: this is the moment the whole check was for. */
    private void announceExposed(ServerLevel level) {
        Component line = Component.translatable("cnpcgeckoaddon.boss.barrier_exposed")
                .withStyle(style -> style.withColor(0xFFD23A).withBold(true));
        for (ServerPlayer player : audience(level)) {
            player.displayClientMessage(line, true);
        }
    }

    /** Everyone with a bar up plus everyone signed into the fight, the hazard's audience. */
    private Set<ServerPlayer> audience(ServerLevel level) {
        Set<ServerPlayer> audience = new LinkedHashSet<>(boss.timerBossEvent().getPlayers());
        for (UUID playerId : boss.encounterParticipants()) {
            if (level.getPlayerByUUID(playerId) instanceof ServerPlayer player) {
                audience.add(player);
            }
        }
        return audience;
    }
}
