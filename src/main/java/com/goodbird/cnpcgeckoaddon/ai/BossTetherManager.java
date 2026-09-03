package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.entity.EntityBossTetherAnchor;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossLink;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the leashes a boss throws. Nothing here is persisted.
 *
 * <p>A tether is the capture turned inside out. The capture pins somebody and lets go on its
 * own; a tether lets them move and dares them to. Every tick each leash is measured, and one
 * stretched past its break distance snaps with nothing worse than a noise - while one still
 * holding when its time runs out is what the punishment lands through.</p>
 *
 * <p>The three anchors are one object with two ends. Tied to the boss or to a spot, a leash
 * has one victim and its far end is the boss or a stake planted where they stood; tied in a
 * pair it has two victims and no far end at all, each being the other's. That is why a pair
 * is one leash rather than two: the distance between them is one number, so both snap on the
 * same tick and both are punished together.</p>
 *
 * <p>Nothing outlives the fight or the server either. A stake is an entity that is never
 * saved and gives itself up when the manager stops renewing it, so a restart mid-tether comes
 * back with the arena exactly as it was.</p>
 */
public final class BossTetherManager {
    /** Ticks between one dose of the held effects and the next. */
    private static final int EFFECT_INTERVAL_TICKS = 20;
    /**
     * Blocks per tick of drag for each level of pull, applied as a steady force.
     *
     * <p>Pitched against what a player can put in per tick on plain ground - 0.098 walking,
     * 0.127 sprinting. Level 5 pulls at 0.10, which holds a walker where they are and still
     * lets a sprinter gain about a block every sixteen ticks: the way out is to run, not to
     * stroll. Level 10 outpulls a sprint outright.</p>
     */
    private static final double PULL_PER_LEVEL = 0.02D;
    /**
     * What a client keeps of its last tick's movement on plain ground: block friction times
     * the air drag every entity gets. The pull is added on top of exactly this, so that with
     * no pull at all the speed sent back is the one the client was about to work out itself.
     */
    private static final double CLIENT_GROUND_DRAG = 0.6D * 0.91D;
    /** Inside this the pull lets go, or a victim standing on the spot would twitch about it. */
    private static final double PULL_SLACK = 1.0D;
    /** The beam hangs by its style's own sag; the tether has no setting of its own for it. */
    private static final int BEAM_SAG_PERCENT = 100;

    private static final List<Tether> TETHERS = new ArrayList<>();
    private static final Map<UUID, Tether> BY_VICTIM = new HashMap<>();

    private BossTetherManager() {
    }

    /** One end of a leash, remembered both ways: the id for lookups, the number for packets. */
    private record End(UUID id, int entityId) {
        private static End of(Entity entity) {
            return new End(entity.getUUID(), entity.getId());
        }
    }

    private static final class Tether {
        private final UUID bossId;
        private final int bossEntityId;
        private final ResourceKey<Level> levelKey;
        private final int originPhaseIndex;
        private final int anchor;
        /** One victim, or two in the pair mode. */
        private final List<End> victims;
        /** Where a spot leash is tied; null for the other anchors. */
        private final Vec3 spot;
        /** The stake standing on that spot, for the beam to end on; null for the other anchors. */
        private final End stake;
        private final long startedAt;
        private final long endsAt;
        private final double breakDistance;
        private final double pullSpeed;
        private final int failDamage;
        private final BossEffectSet effects;
        private final BossEffectSet failEffects;
        private final String style;
        private final int widthPercent;

        private Tether(EntityNPCInterface boss, BossPhaseData phase, int phaseIndex, int anchor,
                       List<End> victims, Vec3 spot, End stake, int failDamage, long gameTime) {
            this.bossId = boss.getUUID();
            this.bossEntityId = boss.getId();
            this.levelKey = boss.level().dimension();
            this.originPhaseIndex = phaseIndex;
            this.anchor = anchor;
            this.victims = victims;
            this.spot = spot;
            this.stake = stake;
            this.startedAt = gameTime;
            this.endsAt = gameTime + phase.getTetherDurationTicks();
            this.breakDistance = phase.getTetherBreakDistance();
            this.pullSpeed = phase.getTetherPull() * PULL_PER_LEVEL;
            this.failDamage = failDamage;
            this.effects = phase.getTetherEffects();
            this.failEffects = phase.getTetherFailEffects();
            this.style = phase.getTetherStyle();
            this.widthPercent = phase.getTetherWidthPercent();
        }
    }

    /**
     * Leashes everyone one cast landed on.
     *
     * <p>Whoever is already on a leash, or not standing in this level, is left out; the
     * caller has vetted the rest. In the pair mode the victims are tied two by two, nearest
     * first, so a pair really is two people standing together with something to run apart
     * from - and an odd one out is tied to the boss rather than left as the one person in
     * the room with nothing to do.</p>
     *
     * @param failDamage what an unbroken leash hits for, with the enrage bonus already in it
     * @return how many leashes were tied
     */
    public static int start(ServerLevel level, EntityNPCInterface boss, List<LivingEntity> victims,
                            BossPhaseData phase, int phaseIndex, int failDamage, long gameTime) {
        List<LivingEntity> free = new ArrayList<>();
        for (LivingEntity victim : victims) {
            if (victim.level() == level && !BY_VICTIM.containsKey(victim.getUUID())
                    && !free.contains(victim)) {
                free.add(victim);
            }
        }
        int tied = 0;
        if (phase.getTetherAnchor() == BossPhaseData.TETHER_ANCHOR_PAIR) {
            while (free.size() >= 2) {
                LivingEntity first = free.removeFirst();
                LivingEntity partner = nearest(first, free);
                free.remove(partner);
                tie(level, new Tether(boss, phase, phaseIndex, BossPhaseData.TETHER_ANCHOR_PAIR,
                        List.of(End.of(first), End.of(partner)), null, null, failDamage, gameTime),
                        List.of(first, partner), gameTime);
                tied++;
            }
        }
        for (LivingEntity victim : free) {
            int anchor = BossPhaseData.TETHER_ANCHOR_BOSS;
            Vec3 spot = null;
            End stake = null;
            if (phase.getTetherAnchor() == BossPhaseData.TETHER_ANCHOR_SPOT) {
                EntityBossTetherAnchor planted = EntityBossTetherAnchor.plant(level, victim.position());
                if (planted == null) {
                    continue;
                }
                anchor = BossPhaseData.TETHER_ANCHOR_SPOT;
                spot = victim.position();
                stake = End.of(planted);
            }
            tie(level, new Tether(boss, phase, phaseIndex, anchor, List.of(End.of(victim)), spot, stake,
                    failDamage, gameTime), List.of(victim), gameTime);
            tied++;
        }
        return tied;
    }

    private static LivingEntity nearest(LivingEntity to, List<LivingEntity> among) {
        LivingEntity best = among.getFirst();
        double bestDistance = to.distanceToSqr(best);
        for (int i = 1; i < among.size(); i++) {
            double distance = to.distanceToSqr(among.get(i));
            if (distance < bestDistance) {
                best = among.get(i);
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void tie(ServerLevel level, Tether tether, List<LivingEntity> victims, long gameTime) {
        TETHERS.add(tether);
        for (End end : tether.victims) {
            BY_VICTIM.put(end.id, tether);
        }
        broadcast(level, tether, linkPacket(tether, (int) (tether.endsAt - gameTime)));
        for (LivingEntity victim : victims) {
            // The clink of the chain closing, on the victim rather than the boss: it is theirs now.
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.CHAIN_PLACE,
                    SoundSource.HOSTILE, 1.2F, 0.7F);
            level.sendParticles(BossTelegraphUtil.dust(BossAbilityKind.TETHER), victim.getX(),
                    victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(), 10, 0.3D, 0.4D, 0.3D, 0.0D);
        }
    }

    public static boolean isTethered(UUID victimId) {
        return BY_VICTIM.containsKey(victimId);
    }

    /** How many leashes one boss has out right now, for its status line. */
    public static int countForBoss(UUID bossId) {
        int count = 0;
        for (Tether tether : TETHERS) {
            if (tether.bossId.equals(bossId)) {
                count++;
            }
        }
        return count;
    }

    /** Gives a late observer the same remaining beam lifetime as current viewers. */
    public static void syncLinkForTracking(ServerPlayer viewer, Entity tracked) {
        if (TETHERS.isEmpty() || !(viewer.level() instanceof ServerLevel level)) {
            return;
        }
        long gameTime = level.getGameTime();
        for (Tether tether : TETHERS) {
            if (!tether.levelKey.equals(level.dimension()) || !isEnd(tether, tracked)) {
                continue;
            }
            int remaining = (int) Math.max(0L, tether.endsAt - gameTime);
            if (remaining > 0) {
                NetworkWrapper.send(viewer, linkPacket(tether, remaining));
            }
        }
    }

    private static boolean isEnd(Tether tether, Entity entity) {
        int id = entity.getId();
        if (id == tether.bossEntityId || tether.stake != null && id == tether.stake.entityId) {
            return true;
        }
        for (End victim : tether.victims) {
            if (victim.entityId == id) {
                return true;
            }
        }
        return false;
    }

    public static void tick(ServerLevel level) {
        if (TETHERS.isEmpty()) {
            return;
        }
        long gameTime = level.getGameTime();
        for (Tether tether : TETHERS.toArray(Tether[]::new)) {
            if (!tether.levelKey.equals(level.dimension())) {
                continue;
            }
            EntityNPCInterface boss = usableBoss(level, tether);
            List<LivingEntity> victims = usableVictims(level, tether);
            if (boss == null || victims == null) {
                release(level, tether);
                continue;
            }
            if (tether.stake != null) {
                if (!(level.getEntity(tether.stake.id) instanceof EntityBossTetherAnchor stake)
                        || !stake.isAlive()) {
                    // Somebody /killed the stake: the beam has nothing to end on, so the leash goes.
                    release(level, tether);
                    continue;
                }
                stake.renewLease();
            }
            // Measured before the clock is read, so getting out on the very last tick still counts.
            if (stretch(boss, tether, victims) >= tether.breakDistance) {
                snap(level, tether, victims);
                continue;
            }
            if (gameTime >= tether.endsAt) {
                punish(level, boss, tether, victims);
                continue;
            }
            if (tether.pullSpeed > 0.0D) {
                pull(boss, tether, victims);
            }
            if ((gameTime - tether.startedAt) % EFFECT_INTERVAL_TICKS == 0L && tether.effects.isAnyEnabled()) {
                for (LivingEntity victim : victims) {
                    BossAbilityDamageUtil.applyEffects(victim, BossAbilityKind.TETHER, boss, tether.effects);
                }
            }
        }
    }

    /** The boss behind a leash, or null once it, its fight or its phase's setting is gone. */
    private static EntityNPCInterface usableBoss(ServerLevel level, Tether tether) {
        if (!(level.getEntity(tether.bossId) instanceof EntityNPCInterface boss) || boss.isRemoved()
                || !boss.isAlive() || !(boss instanceof IBossController holder)) {
            return null;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        return controller != null && controller.isTetherEnabledForPhase(tether.originPhaseIndex) ? boss : null;
    }

    /** Every victim of a leash, or null as soon as one of them is no longer there to hold. */
    private static List<LivingEntity> usableVictims(ServerLevel level, Tether tether) {
        List<LivingEntity> result = new ArrayList<>(tether.victims.size());
        for (End end : tether.victims) {
            if (!(level.getEntity(end.id) instanceof LivingEntity victim) || victim.isRemoved()
                    || !victim.isAlive()
                    || victim instanceof Player player && (player.isCreative() || player.isSpectator())) {
                return null;
            }
            result.add(victim);
        }
        return result;
    }

    /** How far the leash is stretched right now: victim to boss, to spot, or to each other. */
    private static double stretch(EntityNPCInterface boss, Tether tether, List<LivingEntity> victims) {
        return victims.getFirst().position().distanceTo(anchorOf(boss, tether, victims, 0));
    }

    /** Where victim {@code index} is being held toward. */
    private static Vec3 anchorOf(EntityNPCInterface boss, Tether tether, List<LivingEntity> victims, int index) {
        return switch (tether.anchor) {
            case BossPhaseData.TETHER_ANCHOR_SPOT -> tether.spot;
            case BossPhaseData.TETHER_ANCHOR_PAIR -> victims.get(1 - index).position();
            default -> boss.position();
        };
    }

    /**
     * Drags every victim one tick toward whatever it is tied to.
     *
     * <p>A player's own client is what moves them, and each tick it starts from the speed
     * the server last sent and puts its own input on top. So what is sent is the movement
     * the client itself reported, worn down by the ground drag it would have applied anyway,
     * plus the pull: with no pull that reproduces plain walking, and with one it reads as a
     * steady force the victim has to out-run - which is the tug of war
     * {@link #PULL_PER_LEVEL} is pitched for. Only while they stand on the ground, and only
     * sideways, so the height they are already moving at is left alone: the server's idea of
     * a player's fall runs a tick or two behind, and sending it back would cut every jump
     * short.</p>
     */
    private static void pull(EntityNPCInterface boss, Tether tether, List<LivingEntity> victims) {
        for (int i = 0; i < victims.size(); i++) {
            LivingEntity victim = victims.get(i);
            // The drag is the tether, so it asks for itself: a leash on somebody whose totem
            // list or immunity changed under it must not keep tugging.
            if (!victim.onGround() || BossAbilityDamageUtil.passesBy(victim, BossAbilityKind.TETHER)) {
                continue;
            }
            Vec3 delta = anchorOf(boss, tether, victims, i).subtract(victim.position());
            Vec3 flat = new Vec3(delta.x, 0.0D, delta.z);
            double distance = flat.length();
            if (distance <= PULL_SLACK) {
                continue;
            }
            // For a player this is the last step their client reported; a mob's is simply its
            // own speed, which the same drag leaves a little heavier than it would be alone.
            Vec3 carried = victim.getKnownMovement();
            Vec3 velocity = flat.scale(tether.pullSpeed / distance)
                    .add(carried.x * CLIENT_GROUND_DRAG, 0.0D, carried.z * CLIENT_GROUND_DRAG);
            victim.setDeltaMovement(velocity.x, victim.getDeltaMovement().y, velocity.z);
            // Players simulate their own movement, so the server has to push the new velocity
            // to them explicitly. hurtMarked is what makes ServerEntity send it.
            victim.hurtMarked = true;
        }
    }

    /** The leash gives: a noise and some sparks on each victim, and nothing else at all. */
    private static void snap(ServerLevel level, Tether tether, List<LivingEntity> victims) {
        for (LivingEntity victim : victims) {
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.CHAIN_BREAK,
                    SoundSource.HOSTILE, 1.5F, 1.2F);
            burst(level, victim, ParticleTypes.CRIT);
        }
        release(level, tether);
    }

    /**
     * Time ran out with the leash still holding: the fail damage and effects land on every
     * victim it has, and only then is it taken off them.
     */
    private static void punish(ServerLevel level, EntityNPCInterface boss, Tether tether,
                               List<LivingEntity> victims) {
        for (LivingEntity victim : victims) {
            // No knockback: what a leash does to somebody who stayed is hold them, not throw them.
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.TETHER, boss, tether.failDamage,
                    tether.failEffects, 0, 0.0D, 0.0D);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.CHAIN_HIT,
                    SoundSource.HOSTILE, 2.0F, 0.5F);
            burst(level, victim, ParticleTypes.SMOKE);
        }
        release(level, tether);
    }

    private static void burst(ServerLevel level, LivingEntity victim, ParticleOptions particle) {
        double y = victim.getY() + victim.getBbHeight() * 0.5D;
        level.sendParticles(particle, victim.getX(), y, victim.getZ(), 12, 0.3D, 0.4D, 0.3D, 0.1D);
        level.sendParticles(BossTelegraphUtil.dust(BossAbilityKind.TETHER), victim.getX(), y, victim.getZ(),
                10, 0.4D, 0.5D, 0.4D, 0.0D);
    }

    public static void releaseVictim(LivingEntity victim) {
        Tether tether = BY_VICTIM.get(victim.getUUID());
        if (tether == null) {
            return;
        }
        ServerLevel level = victim.getServer() == null ? null : victim.getServer().getLevel(tether.levelKey);
        release(level, tether);
    }

    public static void releaseByBoss(EntityNPCInterface boss) {
        if (TETHERS.isEmpty()) {
            return;
        }
        ServerLevel level = boss.level() instanceof ServerLevel found ? found : null;
        for (Tether tether : TETHERS.toArray(Tether[]::new)) {
            if (tether.bossId.equals(boss.getUUID())) {
                release(level, tether);
            }
        }
    }

    public static void clearLevel(ServerLevel level) {
        for (Tether tether : TETHERS.toArray(Tether[]::new)) {
            if (tether.levelKey.equals(level.dimension())) {
                release(level, tether);
            }
        }
    }

    /**
     * Takes a leash off its victims, its beam off every screen and its stake out of the arena.
     *
     * <p>Safe to reach twice: the punishment can kill a victim, whose death releases the
     * leash from inside the punishment, which then releases it again on its way out.</p>
     */
    private static void release(ServerLevel level, Tether tether) {
        if (!TETHERS.remove(tether)) {
            return;
        }
        for (End victim : tether.victims) {
            BY_VICTIM.remove(victim.id, tether);
        }
        if (level == null) {
            return;
        }
        broadcast(level, tether, linkPacket(tether, 0));
        if (tether.stake != null && level.getEntity(tether.stake.id) instanceof EntityBossTetherAnchor stake) {
            stake.discard();
        }
    }

    /** To everyone who can see either end, and to the victims themselves, who track nothing they are. */
    private static void broadcast(ServerLevel level, Tether tether, PacketSyncBossLink packet) {
        for (Entity end : loadedEnds(level, tether)) {
            NetworkWrapper.sendToTracking(end, packet);
            if (end instanceof ServerPlayer player) {
                NetworkWrapper.send(player, packet);
            }
        }
    }

    /** Whatever of the boss, the stake and the victims is currently in the level. */
    private static List<Entity> loadedEnds(ServerLevel level, Tether tether) {
        List<Entity> result = new ArrayList<>();
        addIfLoaded(result, level, tether.bossId);
        if (tether.stake != null) {
            addIfLoaded(result, level, tether.stake.id);
        }
        for (End victim : tether.victims) {
            addIfLoaded(result, level, victim.id);
        }
        return result;
    }

    private static void addIfLoaded(List<Entity> into, ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        if (entity != null) {
            into.add(entity);
        }
    }

    /**
     * The beam, drawn between the two things it is really between: the boss and its victim,
     * the stake and its victim, or the two halves of a pair. The victim is always the far
     * end, so a beam lands on somebody's chest rather than leaving from their eyes; and the
     * channel is the boss, so two bosses leashing the same people keep beams of their own.
     */
    private static PacketSyncBossLink linkPacket(Tether tether, int durationTicks) {
        int source = switch (tether.anchor) {
            case BossPhaseData.TETHER_ANCHOR_SPOT -> tether.stake.entityId;
            case BossPhaseData.TETHER_ANCHOR_PAIR -> tether.victims.get(1).entityId;
            default -> tether.bossEntityId;
        };
        return new PacketSyncBossLink(PacketSyncBossLink.KIND_TETHER, source, tether.victims.getFirst().entityId,
                tether.bossEntityId, tether.style, durationTicks, tether.widthPercent, BEAM_SAG_PERCENT, false);
    }
}
