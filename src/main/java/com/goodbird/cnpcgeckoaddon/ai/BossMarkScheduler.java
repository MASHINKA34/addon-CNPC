package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.List;
import java.util.UUID;

/**
 * Holds the marks a boss hands out: one victim carries a circle for a few seconds, and then
 * it goes off by one of two rules the party has to answer differently.
 *
 * <p>Gather up is the raid stacking: the hit is a single number split between everyone
 * standing inside, so it is survivable exactly when enough of them came. Spread out is the
 * same shape turned round: the circle hurts everyone in it except the person it is on, so the
 * answer is to carry it away from the group and pay a smaller price alone. One ability rather
 * than two, because they are the same mechanic read from either end - the fuse, the circle
 * and the head count are shared, and only what happens at zero differs.</p>
 *
 * <p>The circle is drawn for the whole fuse whatever the warning settings say, the way a
 * geyser's mark is: everything a player has to decide - who else is inside, whether to run in
 * or out - is decided by looking at it, and an invisible fuse is not a mechanic, it is a trap.
 * The carrier is told in their action bar as well, because they are the one person who cannot
 * see their own feet in a crowd.</p>
 *
 * <p>The wait cannot be run off the ability that lit it: the boss goes back to its rotation
 * the moment the cast lands, and the mark is still seconds from going off. Everything the
 * blast needs is therefore snapshotted here - the enrage bonus included - and driven from the
 * level tick, exactly as {@link BossGeyserScheduler} drives an eruption.</p>
 *
 * <p>A carrier who dies or logs out mid-fuse takes a following mark with them - it is theirs,
 * and there is nothing left for it to ride - while one nailed to the ground goes off anyway,
 * because that circle is a promise made to everybody standing in it, not only to them.</p>
 *
 * <p>Nothing here is persisted. A mark lives for seconds, and a server that shuts down inside
 * that window should not go off under whoever logs in first on the next start.</p>
 */
public final class BossMarkScheduler {

    /**
     * How many marks one level tick works on, blasts included. Far above anything a fight
     * asks for - eight carriers is the most a single cast can mark - so it is here to stop a
     * runaway, not to shape the mechanic. The rest keeps its place and is picked up next tick.
     */
    private static final int MAX_PER_TICK = 32;
    /** Beyond this nobody can see the circle, so the fuse burns down without costing anything. */
    private static final double AUDIENCE_RANGE = 64.0D;
    /** How often the circle is repainted and the carrier's countdown refreshed. */
    private static final int MARK_INTERVAL_TICKS = 2;
    /** How long the blast's wave runs for; a mark has no length setting of its own. */
    private static final int VFX_DURATION_TICKS = 20;
    /** How hard the fuse spits at the centre, from the moment it is lit to the last tick. */
    private static final double MIN_FUSE_SPEED = 0.02D;
    private static final double MAX_FUSE_SPEED = 0.12D;
    /** Sparks over the carrier's own head, so the group can see who is carrying it. */
    private static final int CARRIER_PARTICLES = 2;

    /** One mark, mid-fuse. */
    private static final class Pending {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        /** Who it was put on. Kept for a nailed mark too: the blast is still theirs to pay for. */
        private final UUID carrierId;
        /** Whether the circle rides its carrier or stays on the ground it was lit on. */
        private final boolean follow;
        private final int mode;
        private final double radius;
        /** Gather up: how many bodies inside make the difference between a split and a failure. */
        private final int minPlayers;
        /** Split between everyone inside, or dealt whole to each neighbour; enrage counted in. */
        private final int damage;
        private final int failDamage;
        private final int selfDamage;
        private final BossEffectSet effects;
        private final BossEffectSet failEffects;
        private final String vfx;
        private final long litAt;
        private final long explodesAt;
        /** Where the blast lands; moves under a followed carrier, otherwise fixed. */
        private Vec3 pos;

        private Pending(ResourceKey<Level> dimension, EntityNPCInterface boss, LivingEntity carrier,
                        BossPhaseData phase, int damage, int failDamage, int selfDamage,
                        long gameTime, Vec3 pos) {
            this.dimension = dimension;
            this.boss = boss;
            this.carrierId = carrier.getUUID();
            this.follow = phase.isMarkFollow();
            this.mode = phase.getMarkMode();
            this.radius = phase.getMarkRadius();
            this.minPlayers = phase.getMarkMinPlayers();
            this.damage = damage;
            this.failDamage = failDamage;
            this.selfDamage = selfDamage;
            this.effects = phase.getMarkEffects();
            this.failEffects = phase.getMarkFailEffects();
            this.vfx = phase.getMarkVfx();
            this.litAt = gameTime;
            this.explodesAt = gameTime + phase.getMarkFuseTicks();
            this.pos = pos;
        }
    }

    private static final TickQueue<Pending> PENDING = new TickQueue<>("boss marks", MAX_PER_TICK);

    private BossMarkScheduler() {
    }

    /**
     * Puts one mark on {@code carrier}.
     *
     * @param damage     the shared or per-neighbour hit, with the enrage bonus already in it
     * @param failDamage what a gather that nobody came to hits for, in the same terms
     * @param selfDamage what a spread costs its carrier, in the same terms
     * @return whether a mark was really lit, i.e. whether there was floor to draw it on
     */
    public static boolean schedule(ServerLevel level, EntityNPCInterface boss, LivingEntity carrier,
                                   BossPhaseData phase, int damage, int failDamage, int selfDamage,
                                   long gameTime) {
        Vec3 point = groundUnder(level, carrier);
        if (point == null) {
            return false;
        }
        PENDING.add(new Pending(level.dimension(), boss, carrier, phase, damage, failDamage,
                selfDamage, gameTime, point));
        // On the carrier rather than on the boss: from this moment the mark is theirs.
        level.playSound(null, carrier.getX(), carrier.getY(), carrier.getZ(),
                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.HOSTILE, 1.2F, 1.8F);
        return true;
    }

    /** Whether this victim is already carrying a mark, from this boss or from any other. */
    public static boolean isMarked(UUID carrierId) {
        return PENDING.find(pending -> pending.carrierId.equals(carrierId)) != null;
    }

    public static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        PENDING.sweep(pending -> pending.dimension.equals(level.dimension()),
                pending -> tickFuse(level, pending, gameTime));
    }

    /** Drops anything still burning in a level that is going away. */
    public static void clear(ServerLevel level) {
        PENDING.removeIf(pending -> pending.dimension.equals(level.dimension()));
    }

    /**
     * Puts out the marks one boss handed out, for its death and for the end of its fight.
     *
     * <p>A mark is the boss doing something, not a mine left in the floor: killing it while
     * somebody is still carrying one is a win, and the arena owes the party nothing more.</p>
     */
    public static void clearBoss(EntityNPCInterface boss) {
        if (PENDING.isEmpty()) {
            return;
        }
        PENDING.removeIf(pending -> pending.boss == boss);
    }

    /** @return whether this fuse is still burning and belongs back in the queue */
    private static boolean tickFuse(ServerLevel level, Pending pending, long gameTime) {
        if (!pending.boss.isAlive() || pending.boss.isRemoved()) {
            return false;
        }
        LivingEntity carrier = carrier(level, pending);
        if (carrier == null && pending.follow) {
            // Nothing left to ride. The circle goes out where it stood, so the group sees it
            // end rather than wondering when it is due.
            fizzle(level, pending);
            return false;
        }
        if (carrier != null && pending.follow) {
            follow(level, pending, carrier);
        }
        if (gameTime < pending.explodesAt) {
            burn(level, pending, carrier, gameTime);
            return true;
        }
        explode(level, pending, carrier);
        return false;
    }

    /** Whoever is still carrying this mark, or null once they are gone from the fight. */
    private static LivingEntity carrier(ServerLevel level, Pending pending) {
        if (!(level.getEntity(pending.carrierId) instanceof LivingEntity carrier)
                || carrier.isRemoved() || !carrier.isAlive()) {
            return null;
        }
        // Somebody who switched to creative or to spectating mid-fuse has left the fight as
        // surely as one who logged out.
        return carrier instanceof Player player && (player.isCreative() || player.isSpectator())
                ? null : carrier;
    }

    /** Walks the circle back under whoever is carrying it. */
    private static void follow(ServerLevel level, Pending pending, LivingEntity carrier) {
        Vec3 moved = groundUnder(level, carrier);
        // A carrier out over a hole leaves the circle on the last floor it had, rather than
        // dropping it into one.
        if (moved != null) {
            pending.pos = moved;
        }
    }

    /**
     * The floor somebody is standing on, or null when there is none within reach.
     *
     * <p>Shares the wave's floor search, so the circle lies on the arena the same way every
     * other shape the boss paints does, and gives up on the same holes.</p>
     */
    private static Vec3 groundUnder(ServerLevel level, LivingEntity carrier) {
        BlockPos floor = BossAreaVfxScheduler.findFloor(level, carrier.getX(), carrier.getY(),
                carrier.getZ());
        return floor == null ? null : new Vec3(carrier.getX(), floor.getY() + 1.0D, carrier.getZ());
    }

    /**
     * Paints the fuse and tells its carrier how long is left.
     *
     * <p>The circle says where the blast lands and the countdown says when, which between
     * them is the whole decision. A gather also carries its head count, because the one
     * thing a carrier cannot do is count the people standing on top of them.</p>
     */
    private static void burn(ServerLevel level, Pending pending, LivingEntity carrier, long gameTime) {
        if (gameTime % MARK_INTERVAL_TICKS != 0L) {
            return;
        }
        if (carrier instanceof ServerPlayer player) {
            // The head count costs an entity sweep, so it is only taken for the rule that
            // shows it, and only for a carrier with an action bar to show it in.
            int inside = pending.mode == BossPhaseData.MARK_MODE_SOAK
                    ? victims(level, pending).size() : 0;
            announce(pending, player, inside, gameTime);
        }
        if (level.getNearestPlayer(pending.pos.x, pending.pos.y, pending.pos.z,
                AUDIENCE_RANGE, false) == null) {
            return;
        }
        BossTelegraphUtil.ring(level, pending.pos, pending.radius,
                BossTelegraphUtil.dust(BossAbilityKind.MARK));
        double speed = Mth.lerp(fuseProgress(pending, gameTime), MIN_FUSE_SPEED, MAX_FUSE_SPEED);
        level.sendParticles(ParticleTypes.CRIT, pending.pos.x, pending.pos.y + 0.2D,
                pending.pos.z, 2, 0.2D, 0.05D, 0.2D, speed);
        if (carrier != null) {
            // Above the head, where a mark on somebody in a crowd can still be picked out.
            level.sendParticles(BossTelegraphUtil.dust(BossAbilityKind.MARK), carrier.getX(),
                    carrier.getY() + carrier.getBbHeight() + 0.4D, carrier.getZ(),
                    CARRIER_PARTICLES, 0.2D, 0.1D, 0.2D, 0.0D);
        }
    }

    /**
     * The countdown in the carrier's own action bar, in seconds rather than ticks: it is
     * read at a glance in the middle of a fight, and nobody reacts in twentieths.
     */
    private static void announce(Pending pending, ServerPlayer carrier, int inside, long gameTime) {
        // Rounded up, so the last second of a fuse is shown as one rather than as none.
        int seconds = (int) Math.max(1L, (pending.explodesAt - gameTime + 19L) / 20L);
        MutableComponent line = Component.translatable("cnpcgeckoaddon.boss.mark_countdown",
                Component.translatable(BossAbilityKind.LABELS[BossAbilityKind.MARK]), seconds);
        if (pending.mode == BossPhaseData.MARK_MODE_SOAK) {
            line.append("  ").append(Component.translatable("cnpcgeckoaddon.boss.mark_inside",
                    inside, pending.minPlayers));
        }
        // In the ability's own colour, so the line and the circle on the floor read as one
        // warning rather than as two.
        carrier.displayClientMessage(line.withStyle(style ->
                style.withColor(BossTelegraphUtil.textColor(BossAbilityKind.MARK))), true);
    }

    /** How far the fuse has burned, from 0 the tick it was lit to 1 the tick it goes. */
    private static double fuseProgress(Pending pending, long gameTime) {
        long fuse = pending.explodesAt - pending.litAt;
        return fuse <= 0L ? 1.0D : Mth.clamp((double) (gameTime - pending.litAt) / fuse, 0.0D, 1.0D);
    }

    /** A followed mark whose carrier is gone: a puff where it was, and nothing else at all. */
    private static void fizzle(ServerLevel level, Pending pending) {
        level.playSound(null, pending.pos.x, pending.pos.y, pending.pos.z, SoundEvents.FIRE_EXTINGUISH,
                SoundSource.HOSTILE, 1.0F, 1.4F);
        level.sendParticles(ParticleTypes.SMOKE, pending.pos.x, pending.pos.y + 0.3D, pending.pos.z,
                12, 0.4D, 0.2D, 0.4D, 0.02D);
    }

    private static void explode(ServerLevel level, Pending pending, LivingEntity carrier) {
        Vec3 pos = pending.pos;
        // Started before the hits, so what a player sees leaves at the same moment the damage
        // lands rather than a tick behind it.
        BossAreaVfxScheduler.schedule(level, pos, pending.vfx, pending.radius, VFX_DURATION_TICKS, false);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.HOSTILE, 2.0F, 1.4F);
        level.sendParticles(BossTelegraphUtil.dust(BossAbilityKind.MARK), pos.x, pos.y + 0.5D, pos.z,
                24, pending.radius * 0.4D, 0.3D, pending.radius * 0.4D, 0.0D);
        if (pending.mode == BossPhaseData.MARK_MODE_SOAK) {
            gather(level, pending);
        } else {
            spread(level, pending, carrier);
        }
    }

    /**
     * Gather up: enough bodies inside and the hit is shared out between them, too few and
     * every one of them takes the failure in full.
     *
     * <p>The head count and the split are read off the same list on purpose. Counting one
     * set and hurting another is how {@code damage / count} stops being what anybody
     * actually took.</p>
     */
    private static void gather(ServerLevel level, Pending pending) {
        List<LivingEntity> inside = victims(level, pending);
        if (inside.isEmpty()) {
            return;
        }
        boolean enough = inside.size() >= pending.minPlayers;
        // A share never rounds down to nothing, but an ability set to deal no damage at all
        // still deals none: the floor is against the division, not against the setting.
        int damage = enough
                ? pending.damage <= 0 ? 0 : Math.max(1, pending.damage / inside.size())
                : pending.failDamage;
        BossEffectSet effects = enough ? pending.effects : pending.failEffects;
        for (LivingEntity victim : inside) {
            // No knockback: a mark that scattered the very group it asked for would be
            // answering its own mechanic.
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.MARK, pending.boss, damage,
                    effects, 0, 0.0D, 0.0D);
        }
    }

    /**
     * Spread out: everyone caught standing with the carrier takes the hit whole, and the
     * carrier pays their own smaller price for having carried it.
     */
    private static void spread(ServerLevel level, Pending pending, LivingEntity carrier) {
        for (LivingEntity victim : victims(level, pending)) {
            if (victim == carrier) {
                continue;
            }
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.MARK, pending.boss, pending.damage,
                    pending.effects, 0, 0.0D, 0.0D);
        }
        if (carrier != null) {
            // Their own damage and nothing else: the effects belong to whoever failed to get
            // clear, and the carrier taking it away is the mechanic working.
            BossAbilityDamageUtil.hit(carrier, BossAbilityKind.MARK, pending.boss,
                    pending.selfDamage, null, 0, 0.0D, 0.0D);
        }
    }

    /**
     * Everyone this mark counts and hurts.
     *
     * <p>Asked of the boss that lit it rather than worked out here, so a mark and an area
     * slam can never end up with different ideas of who counts as an enemy - and so the
     * head count is taken by the same rule the fight uses everywhere else.</p>
     */
    private static List<LivingEntity> victims(ServerLevel level, Pending pending) {
        TeleportPathController controller = pending.boss instanceof IBossController holder
                ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
        return controller == null ? List.of()
                : controller.markVictims(level, pending.pos, pending.radius);
    }
}
