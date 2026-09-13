package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPlatformSettings;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.utils.BossFloorUtil;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Holds the platforms a boss set alight: each one's outline flashes and the countdown runs for
 * the length of its fuse, the platform goes off under whoever is still standing on it, and it
 * may smoulder on for a while after, burning anyone who steps back on.
 *
 * <p>The whole mechanic is that fuse. A player who sees the outline under their feet has until
 * the countdown ends to jump to another platform, which is why the outline is drawn whatever
 * the warning settings say - an invisible fuse is not a mechanic, it is a trap - and why it
 * flashes the way the arena hazard's edge does before it opens, and burns steady once it has.</p>
 *
 * <p>The wait cannot be run off the ability that lit it: the boss goes back to its rotation the
 * moment the cast lands. Everything a platform needs is therefore snapshotted here - the
 * enrage bonus included - and driven from the level tick, the way a geyser's fuse is. Hits
 * never run while the queue is being walked: {@link TickQueue} takes the entries a tick works
 * on out first and runs them afterwards.</p>
 *
 * <p>Nothing here is persisted. A server that shuts down inside a fuse or a smoulder should not
 * set a platform alight under whoever logs in first on the next start.</p>
 */
public final class BossPlatformScheduler {

    /**
     * How many platforms one level tick works on. A boss burns one set at a time and a set is
     * at most a phase's sixteen platforms, so this only spreads several bosses' sets over a
     * couple of ticks; the rest keeps its place and is picked up next tick.
     */
    private static final int MAX_PER_TICK = 64;
    /** Half a flash: the outline is painted for this many ticks, then not for as many, the hazard's pace. */
    private static final int BLINK_TICKS = 4;
    /** Once a second: the number in the countdown only changes that often. */
    private static final int COUNTDOWN_INTERVAL_TICKS = 20;
    /** The most lava pops a platform throws up as it goes off, however big it is. */
    private static final int MAX_FLARE_PARTICLES = 24;
    /** One pop per this many square blocks of platform, so a small one still reads as going off. */
    private static final double FLARE_AREA_PER_PARTICLE = 4.0D;

    /**
     * One platform's clock, kept apart from the world so the fuse, the blast and the doses of its
     * smoulder can be tested without one.
     *
     * <p>The smoulder and its doses are counted from when the platform really went off, the way a
     * cone series counts its pauses: a tick that held the platform up - more than a tick's worth
     * of platforms coming due at once - moves the rest along with it rather than landing every
     * dose that fell due in the meantime at once.</p>
     */
    static final class Burn {
        private final long blastAt;
        private final int lingerTicks;
        private final int intervalTicks;
        private boolean blasted;
        private long endsAt;
        private long nextDoseAt;

        Burn(long litAt, int fuseTicks, int lingerTicks, int intervalTicks) {
            this.blastAt = litAt + Math.max(0, fuseTicks);
            this.lingerTicks = Math.max(0, lingerTicks);
            this.intervalTicks = Math.max(1, intervalTicks);
        }

        /** The tick the fuse runs out on. */
        long blastAt() {
            return blastAt;
        }

        /** Whether the fuse is still burning on this tick: the outline flashes, and nobody is hit yet. */
        boolean isFusing(long gameTime) {
            return !blasted && gameTime < blastAt;
        }

        /** Takes the blast off the clock: true exactly once, on the first tick at or past the end of the fuse. */
        boolean takeBlast(long gameTime) {
            if (blasted || gameTime < blastAt) {
                return false;
            }
            blasted = true;
            endsAt = gameTime + lingerTicks;
            nextDoseAt = gameTime + intervalTicks;
            return true;
        }

        /** Takes one dose of the smoulder off the clock: true on each tick one falls due, the last on its final tick. */
        boolean takeDose(long gameTime) {
            if (!blasted || gameTime < nextDoseAt || nextDoseAt > endsAt) {
                return false;
            }
            nextDoseAt = gameTime + intervalTicks;
            return true;
        }

        /** Whether the platform has gone off and is done smouldering: from here it is out. */
        boolean isOver(long gameTime) {
            return blasted && gameTime >= endsAt;
        }
    }

    /** One platform, from the moment its fuse is lit until it is out. */
    private static final class Pending {
        private final ResourceKey<Level> dimension;
        private final EntityNPCInterface boss;
        private final AABB box;
        /** The height the outline is drawn at, and the wave runs out from. */
        private final double floorY;
        /** Whether this one sends the countdown: only the first platform of a cast does. */
        private final boolean announces;
        /** What it hits for, how hard it shoves and throws, enrage already counted in. */
        private final int damage;
        private final int knockback;
        private final int launch;
        private final BossEffectSet effects;
        private final String vfx;
        private final long litAt;
        private final Burn burn;

        private Pending(ResourceKey<Level> dimension, EntityNPCInterface boss, AABB box, double floorY,
                        boolean announces, BossPlatformSettings platform, int damage, int knockback,
                        int launch, long litAt) {
            this.dimension = dimension;
            this.boss = boss;
            this.box = box;
            this.floorY = floorY;
            this.announces = announces;
            this.damage = damage;
            this.knockback = knockback;
            this.launch = launch;
            this.effects = platform.getEffects();
            this.vfx = platform.getVfx();
            this.litAt = litAt;
            this.burn = new Burn(litAt, platform.getFuseTicks(), platform.getLingerTicks(),
                    platform.getLingerIntervalTicks());
        }
    }

    private static final TickQueue<Pending> PENDING = new TickQueue<>("boss platforms", MAX_PER_TICK);

    private BossPlatformScheduler() {
    }

    /**
     * Lights the fuse under one platform.
     *
     * @param floorY    the height the outline is drawn at
     * @param announces whether this platform sends the cast's countdown
     * @param damage    what the platform hits for, with the enrage bonus already in it
     * @param knockback how hard it shoves, in the same already-scaled terms
     * @param launch    how hard it throws, the same again
     */
    public static void schedule(ServerLevel level, EntityNPCInterface boss, AABB box, double floorY,
                                boolean announces, BossPlatformSettings platform, int damage, int knockback,
                                int launch, long gameTime) {
        PENDING.add(new Pending(level.dimension(), boss, box, floorY, announces, platform, damage, knockback,
                launch, gameTime));
        // One hiss as the fuse catches, for the player who is not looking down.
        Vec3 centre = box.getCenter();
        level.playSound(null, centre.x, floorY, centre.z, SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.5F, 0.8F);
    }

    public static boolean hasPending() {
        return !PENDING.isEmpty();
    }

    /**
     * Whether this boss still has a platform lit or smouldering; what a cast spot's stay rule,
     * the finish gate and the chains wait on, and what keeps a second set from being lit on top.
     */
    public static boolean hasPending(EntityNPCInterface boss) {
        return !PENDING.isEmpty() && PENDING.find(pending -> pending.boss == boss) != null;
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        PENDING.sweep(pending -> pending.dimension.equals(level.dimension()),
                pending -> tickPlatform(level, pending, gameTime));
    }

    /** Drops anything still burning in a level that is going away. */
    public static void clear(ServerLevel level) {
        PENDING.removeIf(pending -> pending.dimension.equals(level.dimension()));
    }

    /**
     * Puts out every platform one boss set alight, for its death, the end of its fight and a
     * change of phase.
     *
     * <p>A platform is the boss doing something, the way its sweeping beam is, not a mine left in
     * the arena: a phase that is over or a boss that is dead owes the party nothing more.</p>
     */
    public static void clearBoss(EntityNPCInterface boss) {
        if (PENDING.isEmpty()) {
            return;
        }
        PENDING.removeIf(pending -> pending.boss == boss);
    }

    /** @return whether this platform is still lit or smouldering and belongs back in the queue */
    private static boolean tickPlatform(ServerLevel level, Pending pending, long gameTime) {
        TeleportPathController controller = controllerOf(pending.boss);
        if (controller == null || !pending.boss.isAlive() || pending.boss.isRemoved()) {
            return false;
        }
        Burn burn = pending.burn;
        if (burn.isFusing(gameTime)) {
            if (pending.announces && (gameTime - pending.litAt) % COUNTDOWN_INTERVAL_TICKS == 0L) {
                announceCountdown(level, controller, pending, gameTime);
            }
            if (gameTime % TeleportPathController.TELEGRAPH_INTERVAL_TICKS == 0L
                    && (gameTime / BLINK_TICKS) % 2L == 0L && hasAudience(level, pending)) {
                outline(level, pending);
            }
            return true;
        }
        if (burn.takeBlast(gameTime)) {
            blast(level, controller, pending);
        } else if (burn.takeDose(gameTime)) {
            dose(level, controller, pending);
        }
        if (burn.isOver(gameTime)) {
            return false;
        }
        if (gameTime % TeleportPathController.TELEGRAPH_INTERVAL_TICKS == 0L && hasAudience(level, pending)) {
            // Steady from here on, and a flame now and then inside it: the platform is still burning.
            outline(level, pending);
            scatter(level, pending, ParticleTypes.FLAME, 1);
        }
        return true;
    }

    /**
     * The platform going off: the wave out of its middle, the flare and the bang, and then
     * everyone still standing on it hit, shoved away from its middle and thrown up.
     */
    private static void blast(ServerLevel level, TeleportPathController controller, Pending pending) {
        AABB box = pending.box;
        Vec3 centre = box.getCenter();
        Vec3 ground = new Vec3(centre.x, pending.floorY, centre.z);
        double radius = halfDiagonal(box);
        // All three started before the hits, so what a player sees and hears goes out at the same
        // moment the damage lands rather than a tick behind it. No block wave: the platform is the
        // builder's, and lifting its floor out from under the party is not the mechanic.
        BossAreaVfxScheduler.schedule(level, ground, pending.vfx, radius, BossCoverRuntime.waveDuration(radius), false);
        if (hasAudience(level, pending)) {
            outline(level, pending);
            scatter(level, pending, ParticleTypes.LAVA,
                    (int) Math.round(box.getXsize() * box.getZsize() / FLARE_AREA_PER_PARTICLE));
        }
        level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.HOSTILE, 2.0F, 0.9F);

        for (LivingEntity victim : controller.platformVictims(level, box)) {
            // The shove and the throw are this platform's own half rather than something on top of
            // the hit, the geyser's rule: a totem this platform may not break is left standing, not moved.
            if (BossAbilityDamageUtil.passesBy(victim, BossAbilityKind.PLATFORM)) {
                continue;
            }
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.PLATFORM, pending.boss, pending.damage,
                    pending.effects, 0, 0.0D, 0.0D);
            // Given whether the damage landed or not, the cone strike's way: a platform set to no
            // damage still throws everyone off it, and a victim still in their hurt cooldown is not
            // left standing in the smoulder. Vanilla shoves against the vector it is handed, so the
            // way to the middle throws the victim straight out from it.
            BossConeRuntime.shove(victim, pending.knockback, ground.x - victim.getX(), ground.z - victim.getZ());
            BossGeyserScheduler.launch(victim, pending.launch);
        }
    }

    /**
     * One dose of the smoulder on everyone standing on the platform, by the same rule the blast
     * used. No shove and no throw: the smoulder is the floor, and the floor does not shove, the
     * arena hazard's rule.
     */
    private static void dose(ServerLevel level, TeleportPathController controller, Pending pending) {
        for (LivingEntity victim : controller.platformVictims(level, pending.box)) {
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.PLATFORM, pending.boss, pending.damage,
                    pending.effects, 0, 0.0D, 0.0D);
        }
    }

    /** The flat distance from a platform's middle to one of its corners, which is what its wave reaches. */
    static double halfDiagonal(AABB box) {
        return 0.5D * Math.sqrt(box.getXsize() * box.getXsize() + box.getZsize() * box.getZsize());
    }

    /**
     * Decoration only, so a platform with nobody near enough to see it costs nothing. The
     * platform's own reach is added on: its edge can be a long way from its middle.
     */
    private static boolean hasAudience(ServerLevel level, Pending pending) {
        Vec3 centre = pending.box.getCenter();
        double reach = Math.max(pending.box.getXsize(), pending.box.getZsize()) * 0.5D;
        return level.getNearestPlayer(centre.x, pending.floorY, centre.z,
                BossTelegraphUtil.AUDIENCE_RANGE + reach, false) != null;
    }

    /** The outline of the platform, on the floor inside it. */
    private static void outline(ServerLevel level, Pending pending) {
        AABB box = pending.box;
        DustParticleOptions dust = BossTelegraphUtil.dust(BossAbilityKind.PLATFORM);
        BossTelegraphUtil.rectangle(level, box.minX, box.minZ, box.maxX, box.maxZ, pending.floorY, dust);
    }

    /**
     * A few particles at random spots on the platform's floor, found the way its outline finds
     * it, so they come up out of the platform rather than hanging in the air over a gap in it.
     */
    private static void scatter(ServerLevel level, Pending pending, ParticleOptions particle, int count) {
        AABB box = pending.box;
        RandomSource random = level.getRandom();
        int particles = Mth.clamp(count, 1, MAX_FLARE_PARTICLES);
        for (int i = 0; i < particles; i++) {
            double x = box.minX + random.nextDouble() * box.getXsize();
            double z = box.minZ + random.nextDouble() * box.getZsize();
            BlockPos floor = BossFloorUtil.findFloor(level, x, pending.floorY, z);
            if (floor != null) {
                level.sendParticles(particle, x, floor.getY() + 1.05D, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /**
     * The name and the time left, in the action bar of everyone this fight belongs to.
     *
     * <p>The arena hazard's line and its audience: the platforms are a problem set to the whole
     * party, so it goes to every participant and not only to whoever has a bar up. Rounded up,
     * so the last second reads as one rather than as none, and handed in through %s, the one
     * placeholder vanilla's translation formatter takes.</p>
     */
    private static void announceCountdown(ServerLevel level, TeleportPathController controller, Pending pending,
                                          long gameTime) {
        int seconds = (int) Math.max(1L, (pending.burn.blastAt() - gameTime + 19L) / 20L);
        Component line = Component.translatable("cnpcgeckoaddon.boss.platform_countdown",
                        Component.translatable(BossAbilityKind.LABELS[BossAbilityKind.PLATFORM]), seconds)
                .withStyle(style -> style.withColor(BossTelegraphUtil.textColor(BossAbilityKind.PLATFORM)));
        Set<ServerPlayer> audience = new LinkedHashSet<>(controller.timerBossEvent().getPlayers());
        for (UUID playerId : controller.encounterParticipants()) {
            if (level.getPlayerByUUID(playerId) instanceof ServerPlayer player) {
                audience.add(player);
            }
        }
        for (ServerPlayer player : audience) {
            player.displayClientMessage(line, true);
        }
    }

    private static TeleportPathController controllerOf(EntityNPCInterface boss) {
        return boss instanceof IBossController holder ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
    }
}
