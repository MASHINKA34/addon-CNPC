package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossCaptureState;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the cocoons a boss closes round its victims. Nothing here is persisted.
 *
 * <p>A cocoon is the capture with the key handed to everyone else. The capture pins somebody
 * and lets go on its own; a cocoon pins them and stands a clone on the spot, and it is the
 * clone the party has to deal with - kill it, or stand beside it for long enough between
 * them - before the time runs out and the cocoon bursts on whoever is still inside.</p>
 *
 * <p>The hold is the capture's, on the spot and with the head free: a held player's own
 * client keeps sending movement, so the hold only sticks because {@link #handleMovePacket}
 * drops those packets and the capture's state packet locks their client's prediction. The
 * clone is pinned back onto the victim every tick the way a totem is pinned to its slot,
 * because a walking clone would be pushed off them by the victim's own body.</p>
 *
 * <p>Nothing outlives the fight or the server. The clone is an ordinary saved entity, so
 * one that made it through a restart comes back as a shell with nobody inside, and the boss
 * discards it on its first tick the way it discards a totem it no longer knows.</p>
 */
public final class BossCocoonManager {
    /** Ticks between one dose of the held effects and the next. */
    private static final int EFFECT_INTERVAL_TICKS = 20;
    /** How often the rescuers beside a cocoon are told how long it has left. */
    private static final int ANNOUNCE_INTERVAL_TICKS = 10;
    /**
     * How often the victim's client is told about the lock again. Once would do on its
     * own; the repeat is what puts the lock back should anything else on the client's
     * side have let it go in the meantime.
     */
    private static final int LOCK_SYNC_INTERVAL_TICKS = 40;
    /** How far from a cocoon somebody may stand and still be told about it. */
    private static final double ANNOUNCE_RANGE = 12.0D;
    private static final double POSITION_EPSILON_SQUARED = 1.0E-8D;

    private static final List<Cocoon> COCOONS = new ArrayList<>();
    private static final Map<UUID, Cocoon> BY_VICTIM = new HashMap<>();

    private BossCocoonManager() {
    }

    private static final class Cocoon {
        private final UUID victimId;
        private final String victimName;
        private final UUID bossId;
        private final ResourceKey<Level> levelKey;
        private final int originPhaseIndex;
        /** Where the victim stood as the cocoon closed, and where both of them are pinned. */
        private final Vec3 anchor;
        /** The clone standing on the victim, which is what the rescuers are dealing with. */
        private final UUID cocoonId;
        private final float cocoonYaw;
        private final long startedAt;
        private final long endsAt;
        private final int rescueMode;
        private final double rescueRadius;
        private final int rescueTicks;
        /** What a burst hits for, with the enrage bonus already in it. */
        private final int failDamage;
        private final BossEffectSet victimEffects;
        private final BossEffectSet failEffects;
        private final BossEffectSet freeEffects;
        /** Stand rule: the ticks the rescuers have put in so far, every one of them counting. */
        private int rescueProgress;
        /** Set by a hit that would have killed the shell; the next tick opens it instead. */
        private boolean broken;

        private Cocoon(EntityNPCInterface boss, LivingEntity victim, Entity cocoon, BossPhaseData phase,
                       int phaseIndex, int failDamage, long gameTime) {
            victimId = victim.getUUID();
            victimName = victim.getName().getString();
            bossId = boss.getUUID();
            levelKey = victim.level().dimension();
            originPhaseIndex = phaseIndex;
            anchor = victim.position();
            cocoonId = cocoon.getUUID();
            cocoonYaw = cocoon.getYRot();
            startedAt = gameTime;
            endsAt = gameTime + phase.getCocoonDurationTicks();
            rescueMode = phase.getCocoonRescueMode();
            rescueRadius = phase.getCocoonRescueRadius();
            rescueTicks = phase.getCocoonRescueTicks();
            this.failDamage = failDamage;
            victimEffects = phase.getCocoonVictimEffects();
            failEffects = phase.getCocoonFailEffects();
            freeEffects = phase.getCocoonFreeEffects();
        }
    }

    /**
     * Closes one cocoon round one victim.
     *
     * <p>The clone has already been spawned on them and marked by the caller; from here on
     * it is pinned and watched by the tick. A victim somebody is already holding, or one
     * not standing in this level, is left alone and the caller takes the clone back.</p>
     *
     * @param failDamage what a burst hits for, with the enrage bonus already in it
     */
    public static boolean start(ServerLevel level, EntityNPCInterface boss, LivingEntity victim, Entity cocoon,
                                BossPhaseData phase, int phaseIndex, int failDamage, long gameTime) {
        if (BY_VICTIM.containsKey(victim.getUUID()) || victim.level() != level || cocoon.level() != level
                || !level.noBlockCollision(victim, victim.getBoundingBox())) {
            return false;
        }
        Cocoon held = new Cocoon(boss, victim, cocoon, phase, phaseIndex, failDamage, gameTime);
        COCOONS.add(held);
        BY_VICTIM.put(held.victimId, held);
        pinCocoon(cocoon, held);
        if (!hold(victim, held)) {
            COCOONS.remove(held);
            BY_VICTIM.remove(held.victimId);
            return false;
        }
        if (victim instanceof ServerPlayer player) {
            syncLock(player, held, true);
        }
        // The cocoon closing, on the victim rather than the boss: it is theirs now.
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SPIDER_AMBIENT,
                SoundSource.HOSTILE, 1.2F, 0.6F);
        burst(level, victim, ParticleTypes.CLOUD);
        return true;
    }

    public static boolean isCocooned(UUID victimId) {
        return BY_VICTIM.containsKey(victimId);
    }

    /** Whether this clone is a cocoon somebody is being held in right now. */
    public static boolean isHolding(UUID cocoonId) {
        for (Cocoon cocoon : COCOONS) {
            if (cocoon.cocoonId.equals(cocoonId)) {
                return true;
            }
        }
        return false;
    }

    /** How many cocoons one boss has closed right now, for its status line. */
    public static int countForBoss(UUID bossId) {
        int count = 0;
        for (Cocoon cocoon : COCOONS) {
            if (cocoon.bossId.equals(bossId)) {
                count++;
            }
        }
        return count;
    }

    /** The names of everyone one boss is holding, for its status line. */
    public static String victimNamesForBoss(UUID bossId) {
        StringBuilder names = new StringBuilder();
        for (Cocoon cocoon : COCOONS) {
            if (cocoon.bossId.equals(bossId)) {
                names.append(names.isEmpty() ? "" : ", ").append(cocoon.victimName);
            }
        }
        return names.toString();
    }

    /** Drops a held player's movement while still taking their look: the head stays free in a cocoon. */
    public static boolean handleMovePacket(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        Cocoon cocoon = BY_VICTIM.get(player.getUUID());
        if (cocoon == null || !player.level().dimension().equals(cocoon.levelKey)) {
            return false;
        }
        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());
        float yaw = packet.getYRot(player.getYRot());
        float pitch = packet.getXRot(player.getXRot());
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return false;
        }
        if (packet.hasRotation()) {
            player.setYRot(Mth.wrapDegrees(yaw));
            player.setYHeadRot(player.getYRot());
            player.setXRot(Mth.wrapDegrees(pitch));
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setKnownMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
        return true;
    }

    public static void tick(ServerLevel level) {
        if (COCOONS.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        for (Cocoon cocoon : COCOONS.toArray(Cocoon[]::new)) {
            if (!cocoon.levelKey.equals(level.dimension())) {
                continue;
            }
            EntityNPCInterface boss = usableBoss(level, cocoon);
            LivingEntity victim = usableVictim(level, cocoon);
            if (boss == null || victim == null) {
                release(level, cocoon);
                continue;
            }
            Entity shell = level.getEntity(cocoon.cocoonId);
            if (cocoon.broken || shell == null || shell.isRemoved() || !shell.isAlive()) {
                // Beaten open, or taken away by a command: either way the party opened it.
                free(level, boss, cocoon, victim);
                continue;
            }
            // Read before the clock: opening it on the very last tick still counts.
            if (cocoon.rescueMode == BossPhaseData.COCOON_RESCUE_STAND) {
                cocoon.rescueProgress += rescuersBeside(level, cocoon, victim);
                if (cocoon.rescueProgress >= cocoon.rescueTicks) {
                    free(level, boss, cocoon, victim);
                    continue;
                }
            }
            if (gameTime >= cocoon.endsAt) {
                burst(level, boss, cocoon, victim);
                continue;
            }
            pinCocoon(shell, cocoon);
            if (!hold(victim, cocoon)) {
                // The spot itself has gone: something solid was pushed into it. Nobody is
                // owed a burst for that, and nobody rescued them either.
                release(level, cocoon);
                continue;
            }
            long held = gameTime - cocoon.startedAt;
            if (held % EFFECT_INTERVAL_TICKS == 0L && cocoon.victimEffects.isAnyEnabled()) {
                BossAbilityDamageUtil.applyEffects(victim, BossAbilityKind.COCOON, boss, cocoon.victimEffects);
            }
            if (held % LOCK_SYNC_INTERVAL_TICKS == 0L && victim instanceof ServerPlayer player) {
                syncLock(player, cocoon, true);
            }
            if (held % ANNOUNCE_INTERVAL_TICKS == 0L) {
                announce(level, cocoon, victim, gameTime);
            }
        }
    }

    /** The boss behind a cocoon, or null once it, its fight or its phase's setting is gone. */
    private static EntityNPCInterface usableBoss(ServerLevel level, Cocoon cocoon) {
        if (!(level.getEntity(cocoon.bossId) instanceof EntityNPCInterface boss) || boss.isRemoved()
                || !boss.isAlive() || !(boss instanceof IBossController holder)) {
            return null;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        return controller != null && controller.isCocoonEnabledForPhase(cocoon.originPhaseIndex) ? boss : null;
    }

    /** The victim of a cocoon, or null as soon as they are no longer there to hold. */
    private static LivingEntity usableVictim(ServerLevel level, Cocoon cocoon) {
        if (!(level.getEntity(cocoon.victimId) instanceof LivingEntity victim) || victim.isRemoved()
                || !victim.isAlive()
                || victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return null;
        }
        return victim;
    }

    /**
     * How many people are tearing at the cocoon on this tick, for the stand rule.
     *
     * <p>Every player inside the radius who is not the one inside: two beside it count
     * double, which is what makes coming together the faster rescue. The victim is left
     * out because they are always inside the radius, and a cocoon that opened itself would
     * not be a rescue.</p>
     */
    private static int rescuersBeside(ServerLevel level, Cocoon cocoon, LivingEntity victim) {
        double radiusSquared = cocoon.rescueRadius * cocoon.rescueRadius;
        int count = 0;
        for (ServerPlayer player : level.players()) {
            if (player != victim && player.isAlive() && !player.isSpectator()
                    && player.position().distanceToSqr(cocoon.anchor) <= radiusSquared) {
                count++;
            }
        }
        return count;
    }

    /** The clone put back onto the spot it was spawned on, the way a totem is pinned to its slot. */
    private static void pinCocoon(Entity shell, Cocoon cocoon) {
        Vec3 anchor = cocoon.anchor;
        if (shell.position().distanceToSqr(anchor) > POSITION_EPSILON_SQUARED
                || Math.abs(Mth.wrapDegrees(shell.getYRot() - cocoon.cocoonYaw)) > 0.01F) {
            shell.moveTo(anchor.x, anchor.y, anchor.z, cocoon.cocoonYaw, 0.0F);
        }
        shell.setDeltaMovement(Vec3.ZERO);
        shell.fallDistance = 0.0F;
        if (shell instanceof Mob mob) {
            // A cocoon fights nobody and walks nowhere, whatever the clone was set up to do.
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.yBodyRot = cocoon.cocoonYaw;
            mob.yHeadRot = cocoon.cocoonYaw;
        }
    }

    /** The capture's hold on the spot, with the head left free. */
    private static boolean hold(LivingEntity victim, Cocoon cocoon) {
        AABB desiredBox = victim.getBoundingBox().move(cocoon.anchor.subtract(victim.position()));
        // Blocks only: the cocoon itself stands inside this box, and a clone whose hitbox
        // is solid must not read as the wall that lets its victim go.
        if (!victim.level().noBlockCollision(victim, desiredBox)) {
            return false;
        }
        boolean moved = victim.position().distanceToSqr(cocoon.anchor) > POSITION_EPSILON_SQUARED;
        boolean hadMotion = victim.getDeltaMovement().lengthSqr() > POSITION_EPSILON_SQUARED;
        if (moved) {
            victim.setPos(cocoon.anchor);
        }
        // Gravity still pulls on the victim during its own tick; the pin simply runs after
        // it every time, so what the trackers broadcast is always the anchored position.
        victim.setDeltaMovement(Vec3.ZERO);
        victim.fallDistance = 0.0F;
        victim.hurtMarked |= moved || hadMotion;
        if (victim instanceof ServerPlayer player) {
            player.setKnownMovement(Vec3.ZERO);
        }
        // A path left running would re-apply movement on the victim's own next tick.
        if (victim instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        return true;
    }

    /**
     * The party got there: the victim is let out with the effects for it, and the shell
     * goes without drops or death scripts - it may already be a corpse, or still standing
     * under the stand rule.
     */
    private static void free(ServerLevel level, EntityNPCInterface boss, Cocoon cocoon, LivingEntity victim) {
        if (!release(level, cocoon)) {
            return;
        }
        if (cocoon.freeEffects.isAnyEnabled()) {
            BossAbilityDamageUtil.applyEffects(victim, BossAbilityKind.COCOON, boss, cocoon.freeEffects);
        }
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ITEM_BREAK,
                SoundSource.HOSTILE, 1.2F, 0.8F);
        burst(level, victim, ParticleTypes.CRIT);
    }

    /**
     * Nobody came in time: the fail damage and effects land on the victim, and only then
     * are they let out. The shell bursts with them, drops and scripts and all left out.
     */
    private static void burst(ServerLevel level, EntityNPCInterface boss, Cocoon cocoon, LivingEntity victim) {
        if (!release(level, cocoon)) {
            return;
        }
        // No knockback: what a cocoon does to somebody nobody came for is crush them, not throw them.
        BossAbilityDamageUtil.hit(victim, BossAbilityKind.COCOON, boss, cocoon.failDamage,
                cocoon.failEffects, 0, 0.0D, 0.0D);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.HOSTILE, 0.8F, 1.6F);
        burst(level, victim, ParticleTypes.SMOKE);
    }

    private static void burst(ServerLevel level, LivingEntity victim, ParticleOptions particle) {
        double y = victim.getY() + victim.getBbHeight() * 0.5D;
        level.sendParticles(particle, victim.getX(), y, victim.getZ(), 12, 0.3D, 0.4D, 0.3D, 0.1D);
        level.sendParticles(BossTelegraphUtil.dust(BossAbilityKind.COCOON), victim.getX(), y, victim.getZ(),
                10, 0.4D, 0.5D, 0.4D, 0.0D);
    }

    /**
     * The time left, in the action bar of everyone standing near the cocoon.
     *
     * <p>Sent on a clock rather than once, the way a mark's countdown is: the line is what
     * says how long there is to get there. It goes to whoever is close enough to do
     * something about it, and not to the victim, who is inside and can do nothing. Under
     * the stand rule it carries how far the rescue has got, so somebody deciding whether
     * to stay knows what leaving would throw away.</p>
     */
    private static void announce(ServerLevel level, Cocoon cocoon, LivingEntity victim, long gameTime) {
        // Rounded up, so the last second reads as one rather than as none. The number goes
        // in through %s: vanilla's translation formatter takes that one placeholder and
        // nothing else, and a %d would leave the raw template on the screen.
        int seconds = (int) Math.max(1L, (cocoon.endsAt - gameTime + 19L) / 20L);
        MutableComponent line = Component.translatable("cnpcgeckoaddon.boss.cocoon_rescue_bar", seconds);
        if (cocoon.rescueMode == BossPhaseData.COCOON_RESCUE_STAND) {
            int percent = Mth.clamp(cocoon.rescueProgress * 100 / Math.max(1, cocoon.rescueTicks), 0, 99);
            line.append("  " + percent + "%");
        }
        line.withStyle(style -> style.withColor(BossTelegraphUtil.textColor(BossAbilityKind.COCOON)));
        double rangeSquared = ANNOUNCE_RANGE * ANNOUNCE_RANGE;
        for (ServerPlayer player : level.players()) {
            if (player != victim && !player.isSpectator()
                    && player.position().distanceToSqr(cocoon.anchor) <= rangeSquared) {
                player.displayClientMessage(line, true);
            }
        }
    }

    /**
     * The victim is gone: dead, logged out, or in another world. The cocoon goes with
     * them, and nothing lands on anybody.
     */
    public static void releaseVictim(LivingEntity victim) {
        Cocoon cocoon = BY_VICTIM.get(victim.getUUID());
        if (cocoon == null) {
            return;
        }
        ServerLevel level = victim.getServer() == null ? null : victim.getServer().getLevel(cocoon.levelKey);
        release(level, cocoon, victim);
    }

    /**
     * A hit that would have killed a shell breaks it open instead.
     *
     * <p>The clone must never actually die: CustomNPCs drops its inventory and runs its
     * death scripts before vanilla so much as hears about the death, and a cocoon is meant
     * to go the way a totem goes, with neither. So the killing blow is taken away here and
     * the shell is opened on the next tick, which is where the victim is let out with the
     * effects for it.</p>
     *
     * @param attacker whoever dealt the hit, or null for damage with nobody behind it
     * @return whether the hit was the one that broke it, and is to land as nothing
     */
    public static boolean breakOnLethalHit(LivingEntity shell, float damage, Entity attacker) {
        if (COCOONS.isEmpty() || damage < shell.getHealth()) {
            return false;
        }
        for (Cocoon cocoon : COCOONS) {
            if (!cocoon.cocoonId.equals(shell.getUUID())) {
                continue;
            }
            cocoon.broken = true;
            // Whoever broke it is in the fight now, the way whoever hurts the boss is.
            if (attacker instanceof ServerPlayer player && shell.level() instanceof ServerLevel level
                    && level.getEntity(cocoon.bossId) instanceof IBossController holder
                    && holder.cnpcgeckoaddon$getTeleportPathController() != null) {
                holder.cnpcgeckoaddon$getTeleportPathController().trackParticipant(player);
            }
            return true;
        }
        return false;
    }

    /**
     * A cocoon's shell died anyway - a script set its health to nothing, say: whoever is
     * inside is let out at once, with the effects for it, rather than a tick later when
     * the clock next finds the shell gone.
     */
    public static void onShellDeath(Entity shell) {
        if (COCOONS.isEmpty() || !(shell.level() instanceof ServerLevel level)) {
            return;
        }
        for (Cocoon cocoon : COCOONS.toArray(Cocoon[]::new)) {
            if (!cocoon.cocoonId.equals(shell.getUUID())) {
                continue;
            }
            EntityNPCInterface boss = usableBoss(level, cocoon);
            LivingEntity victim = usableVictim(level, cocoon);
            if (boss == null || victim == null) {
                release(level, cocoon);
            } else {
                free(level, boss, cocoon, victim);
            }
        }
    }

    public static void releaseByBoss(EntityNPCInterface boss) {
        if (COCOONS.isEmpty()) {
            return;
        }
        ServerLevel level = boss.level() instanceof ServerLevel found ? found : null;
        for (Cocoon cocoon : COCOONS.toArray(Cocoon[]::new)) {
            if (cocoon.bossId.equals(boss.getUUID())) {
                release(level, cocoon);
            }
        }
    }

    public static void clearLevel(ServerLevel level) {
        for (Cocoon cocoon : COCOONS.toArray(Cocoon[]::new)) {
            if (cocoon.levelKey.equals(level.dimension())) {
                release(level, cocoon);
            }
        }
    }

    private static boolean release(ServerLevel level, Cocoon cocoon) {
        return release(level, cocoon, null);
    }

    /**
     * Takes the hold off the victim, the lock off their client and the shell out of the
     * arena. Safe to reach twice: a burst can kill the victim, whose death releases the
     * cocoon from inside the burst, which then releases it again on its way out.
     *
     * @return whether this call was the one that took it down
     */
    private static boolean release(ServerLevel level, Cocoon cocoon, LivingEntity knownVictim) {
        if (!COCOONS.remove(cocoon)) {
            return false;
        }
        BY_VICTIM.remove(cocoon.victimId, cocoon);
        LivingEntity victim = knownVictim;
        if (victim == null && level != null && level.getEntity(cocoon.victimId) instanceof LivingEntity found) {
            victim = found;
        }
        if (victim != null) {
            victim.setDeltaMovement(0.0D, -0.05D, 0.0D);
            victim.fallDistance = 0.0F;
            victim.hurtMarked = true;
            if (victim instanceof ServerPlayer player) {
                player.setKnownMovement(Vec3.ZERO);
                syncLock(player, cocoon, false);
            }
        }
        // Discarded, never killed: no drops and no death scripts, the way a totem goes.
        if (level != null && level.getEntity(cocoon.cocoonId) instanceof Entity shell && !shell.isRemoved()) {
            shell.discard();
        }
        return true;
    }

    /** The capture's own client lock, set up as a hold on the spot with the head free. */
    private static void syncLock(ServerPlayer player, Cocoon cocoon, boolean active) {
        NetworkWrapper.send(player, new PacketSyncBossCaptureState(active, cocoon.anchor.x,
                cocoon.anchor.y, cocoon.anchor.z, cocoon.startedAt, cocoon.endsAt, cocoon.startedAt,
                cocoon.anchor.y, player.getYRot(), player.getXRot(), true));
    }
}
