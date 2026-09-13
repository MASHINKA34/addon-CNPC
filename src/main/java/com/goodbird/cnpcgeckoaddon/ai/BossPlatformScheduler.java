package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPlatformSettings;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.utils.TickQueue;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Holds the platforms a boss set alight: each one's outline flashes and the countdown runs for
 * the length of its fuse, and the platform goes off under whoever is still standing on it.
 *
 * <p>The whole mechanic is that fuse. A player who sees the outline under their feet has until
 * the countdown ends to jump to another platform, which is why the outline is drawn whatever
 * the warning settings say - an invisible fuse is not a mechanic, it is a trap - and why it
 * flashes the way the arena hazard's edge does before it opens.</p>
 *
 * <p>The wait cannot be run off the ability that lit it: the boss goes back to its rotation the
 * moment the cast lands. Everything a platform needs is therefore snapshotted here - the
 * enrage bonus included - and driven from the level tick, the way a geyser's fuse is.</p>
 *
 * <p>Nothing here is persisted. A fuse lives for a few seconds, and a server that shuts down
 * inside that window should not set a platform alight under whoever logs in first.</p>
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

    /** One platform, mid fuse. */
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
        private final int lingerTicks;
        private final int lingerIntervalTicks;
        private final String vfx;
        private final long litAt;
        private final long blastAt;

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
            this.lingerTicks = platform.getLingerTicks();
            this.lingerIntervalTicks = platform.getLingerIntervalTicks();
            this.vfx = platform.getVfx();
            this.litAt = litAt;
            this.blastAt = litAt + platform.getFuseTicks();
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
     * Whether this boss still has a platform burning; what a cast spot's stay rule, the finish
     * gate and the chains wait on, and what keeps a second set from being lit on top.
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

    /** @return whether this platform is still burning and belongs back in the queue */
    private static boolean tickPlatform(ServerLevel level, Pending pending, long gameTime) {
        TeleportPathController controller = controllerOf(pending.boss);
        if (controller == null || !pending.boss.isAlive() || pending.boss.isRemoved()) {
            return false;
        }
        if (gameTime >= pending.blastAt) {
            return false;
        }
        if (pending.announces && (gameTime - pending.litAt) % COUNTDOWN_INTERVAL_TICKS == 0L) {
            announceCountdown(level, controller, pending, gameTime);
        }
        if (gameTime % TeleportPathController.TELEGRAPH_INTERVAL_TICKS == 0L && (gameTime / BLINK_TICKS) % 2L == 0L) {
            paint(level, pending);
        }
        return true;
    }

    /** The outline of the platform, on the floor inside it, whenever anyone is near enough to see it. */
    private static void paint(ServerLevel level, Pending pending) {
        AABB box = pending.box;
        Vec3 centre = box.getCenter();
        double reach = Math.max(box.getXsize(), box.getZsize()) * 0.5D;
        if (level.getNearestPlayer(centre.x, pending.floorY, centre.z,
                BossTelegraphUtil.AUDIENCE_RANGE + reach, false) == null) {
            return;
        }
        DustParticleOptions dust = BossTelegraphUtil.dust(BossAbilityKind.PLATFORM);
        BossTelegraphUtil.rectangle(level, box.minX, box.minZ, box.maxX, box.maxZ, pending.floorY, dust);
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
        int seconds = (int) Math.max(1L, (pending.blastAt - gameTime + 19L) / 20L);
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
