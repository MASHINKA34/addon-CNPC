package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.RETRY_LONG_TICKS;

/**
 * The take cover strike: a channel that hits the whole arena and spares only whoever hid.
 *
 * <p>Owned by {@link TeleportPathController}, which starts it as an ordinary pending action
 * and calls back here when the wind-up ends. The wind-up <em>is</em> the mechanic - it is
 * the time everyone gets to hide - so nothing runs on afterwards: the boss channels, and the
 * strike lands on the tick the channel ends.</p>
 *
 * <p>What the strike was armed with is frozen the moment the boss commits, enrage bonus and
 * all. From that tick the warning is a promise, and neither a builder editing the phase nor
 * the enrage timer running out underneath may change what the people already running for
 * cover are answering. Nothing here is saved: a server that goes down mid channel owes
 * nobody the strike.</p>
 */
final class BossCoverRuntime {

    /** Tries this many spots per shelter before giving that shelter up as unplaceable. */
    private static final int SHELTER_ATTEMPTS = 12;
    /** Where the second sight line is drawn to: knee height, so a slab is not full cover. */
    private static final double KNEE_HEIGHT = 0.25D;
    /** Blocks per tick the shockwave travels, which is what sets how long it is drawn for. */
    private static final double WAVE_SPEED = 1.0D;
    private static final int MIN_VFX_DURATION_TICKS = 20;
    private static final int MAX_VFX_DURATION_TICKS = 60;
    /** Blocks of dust stacked over a shelter's centre so it can be found from across the arena. */
    private static final int SHELTER_POST_HEIGHT = 3;

    /**
     * Everything a strike was wound up with, frozen on the tick it began.
     *
     * @param mode          shelter rule or line-of-sight rule
     * @param shelterRadius how far from a shelter's centre still counts as inside it
     * @param shelters      where the wind-up drew them, empty under the line-of-sight rule
     */
    private record CoverCast(int mode, double range, int damage, int knockback, BossEffectSet effects,
                             String vfx, double shelterRadius, List<Vec3> shelters) {
    }

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /** The strike being wound up, or null outside one. */
    private CoverCast cast;

    BossCoverRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /**
     * Winds up a strike if this phase has one and anybody is in reach of it.
     *
     * <p>Nothing is aimed, exactly as the boulder rain is not: the range is the shape, and
     * the cast only asks whether anybody is inside it worth spending a cooldown on. Which is
     * also why the standing-cast choice matters here more than anywhere - the sight lines
     * are drawn from wherever the boss is when they are checked.</p>
     */
    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!phase.cover().isEnabled() || gameTime < boss.abilityScheduleAt(BossAbility.COVER)) {
            return false;
        }
        if (boss.coverVictims(level, npc.position(), phase.cover().getRange()).isEmpty()) {
            boss.setAbilityScheduleAt(BossAbility.COVER, gameTime + RETRY_LONG_TICKS);
            return false;
        }
        boolean shelterRule = phase.cover().getMode() == BossPhaseData.COVER_MODE_SHELTER;
        List<Vec3> shelters = shelterRule ? placeShelters(level, phase) : List.of();
        if (shelterRule && shelters.isEmpty()) {
            // Nowhere to put a single shelter down is a strike nobody could have answered.
            boss.setAbilityScheduleAt(BossAbility.COVER, gameTime + RETRY_LONG_TICKS);
            return false;
        }
        cast = new CoverCast(phase.cover().getMode(), phase.cover().getRange(),
                boss.rageUp(phase.cover().getDamage()), boss.rageUp(phase.cover().getKnockback()),
                phase.cover().getEffects(), phase.cover().getVfx(), phase.cover().getShelterRadius(), shelters);
        boss.beginAction(BossAbility.COVER, phase.cover().getAnimation(),
                phase.cover().getActionDelayTicks(), gameTime, null, data, phase);
        // Only the cooldown is scaled: the wind-up is the time to hide, and an enrage that
        // shortened it would turn a mechanic into a strike nobody can answer.
        boss.setAbilityScheduleAt(BossAbility.COVER, gameTime + phase.cover().getActionDelayTicks()
                + boss.rageDown(phase.cover().getCooldownTicks()));
        return true;
    }

    /**
     * The strike itself: everyone in reach, less whoever the arena is hiding.
     *
     * <p>Who got away is judged on this tick and nowhere else - the sight lines from where
     * the boss stands now, the shelters where the wind-up drew them - so a player who
     * stepped out of cover on the last second is caught, and one who stepped in is not.</p>
     */
    void perform(ServerLevel level) {
        CoverCast strike = cast;
        if (strike == null) {
            return;
        }
        Vec3 origin = npc.position();
        // Started before the hits, so what a player sees leaves at the same moment the damage
        // lands rather than a tick behind it. No block wave: a shockwave the size of the
        // arena would lift half its floor.
        BossAreaVfxScheduler.schedule(level, origin, strike.vfx(), strike.range(),
                waveDuration(strike.range()), false);
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.HOSTILE, 4.0F, 0.6F);
        level.sendParticles(BossTelegraphUtil.dust(BossAbilityKind.COVER), npc.getX(),
                npc.getY() + npc.getBbHeight() * 0.5D, npc.getZ(), 40,
                npc.getBbWidth(), npc.getBbHeight() * 0.5D, npc.getBbWidth(), 0.0D);
        for (LivingEntity victim : boss.coverVictims(level, origin, strike.range())) {
            boolean spared = strike.mode() == BossPhaseData.COVER_MODE_SHELTER
                    ? isSheltered(strike.shelters(), strike.shelterRadius(), victim.position())
                    : isOutOfSight(level, victim);
            if (spared) {
                continue;
            }
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.COVER, npc, strike.damage(), strike.effects(),
                    strike.knockback(), npc.getX() - victim.getX(), npc.getZ() - victim.getZ());
        }
    }

    /**
     * The name and the time left, in the action bar of everyone the strike may reach.
     *
     * <p>Sent on every repaint rather than once, the way a mark's countdown is: the line is
     * what says how long there is to run. It goes to whoever this fight belongs to and to
     * everyone standing inside the range as well, because the strike reaches them wherever
     * they stand and whether or not they have a boss bar up.</p>
     */
    void announceCountdown(ServerLevel level, long landsAt) {
        CoverCast strike = cast;
        if (strike == null) {
            return;
        }
        // Rounded up, so the last second reads as one rather than as none. The numbers go
        // in through %s: vanilla's translation formatter takes that one placeholder and
        // nothing else, and a %d would leave the raw template on the screen.
        int seconds = (int) Math.max(1L, (landsAt - level.getGameTime() + 19L) / 20L);
        Component line = Component.translatable("cnpcgeckoaddon.boss.cover_countdown",
                        Component.translatable(BossAbilityKind.LABELS[BossAbilityKind.COVER]), seconds)
                .withStyle(style -> style.withColor(BossTelegraphUtil.textColor(BossAbilityKind.COVER)));
        Set<ServerPlayer> audience = new LinkedHashSet<>(boss.timerBossEvent().getPlayers());
        double rangeSquared = strike.range() * strike.range();
        for (ServerPlayer player : level.players()) {
            if (npc.distanceToSqr(player) <= rangeSquared
                    && boss.isAbilityTarget(player, BossAbilityKind.COVER)) {
                audience.add(player);
            }
        }
        for (ServerPlayer player : audience) {
            player.displayClientMessage(line, true);
        }
    }

    /**
     * Every shelter of the strike being wound up: a ring on the floor, and a post of the
     * same dust over its centre so it can be found from across the arena.
     */
    void drawShelters(ServerLevel level, DustParticleOptions dust) {
        CoverCast strike = cast;
        if (strike == null) {
            return;
        }
        for (Vec3 shelter : strike.shelters()) {
            BossTelegraphUtil.ring(level, shelter, strike.shelterRadius(), dust);
            for (int step = 0; step < SHELTER_POST_HEIGHT; step++) {
                level.sendParticles(dust, shelter.x, shelter.y + 0.5D + step, shelter.z, 1,
                        0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** Drops the frozen settings; a wind-up that was called off owes nobody the strike. */
    void clear() {
        cast = null;
    }

    /**
     * Scatters the shelters for one cast: random points in the ring around the boss, on the
     * floor, and never two of them close enough to overlap.
     *
     * <p>Drawn evenly over the ring's area the way the boulder rain draws its stones, and
     * held two radii apart so each circle stands on its own: two shelters running into each
     * other read as one odd shape rather than as two places to be. A point over a hole or
     * crowding another is tried again a few times and then given up on, so a cramped ring
     * simply gets fewer shelters than it asked for.</p>
     */
    private List<Vec3> placeShelters(ServerLevel level, BossPhaseData phase) {
        List<Vec3> shelters = new ArrayList<>();
        RandomSource random = npc.getRandom();
        Vec3 origin = npc.position();
        double min = phase.cover().getShelterMinRange();
        double max = phase.cover().getShelterMaxRange();
        double apart = phase.cover().getShelterRadius() * 2.0D;
        for (int i = 0; i < phase.cover().getShelterCount(); i++) {
            for (int attempt = 0; attempt < SHELTER_ATTEMPTS; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = Math.sqrt(min * min + random.nextDouble() * (max * max - min * min));
                double x = origin.x + Math.cos(angle) * distance;
                double z = origin.z + Math.sin(angle) * distance;
                if (crowdsShelter(shelters, x, z, apart)) {
                    continue;
                }
                BlockPos floor = BossAreaVfxScheduler.findFloor(level, x, origin.y, z);
                if (floor != null) {
                    shelters.add(new Vec3(x, floor.getY() + 1.0D, z));
                    break;
                }
            }
        }
        return shelters;
    }

    /**
     * Whether the arena hides this victim from the boss: a solid block on both sight lines,
     * from the boss' eyes to their head and to their knees.
     *
     * <p>Two lines rather than one, so that ducking behind a slab - legs covered, head in
     * plain view - is being seen, and so is peering out over a wall. Anything with a
     * collision box counts as cover, leaves and glass included; grass, water and carpets
     * stop nothing.</p>
     */
    private boolean isOutOfSight(ServerLevel level, LivingEntity victim) {
        Vec3 eyes = npc.getEyePosition();
        return blocksSight(level, eyes, victim.getEyePosition())
                && blocksSight(level, eyes, victim.position().add(0.0D, KNEE_HEIGHT, 0.0D));
    }

    private boolean blocksSight(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, npc)).getType() != HitResult.Type.MISS;
    }

    /** Whether a shelter at this spot would stand closer than {@code apart} to one already down. */
    static boolean crowdsShelter(List<Vec3> shelters, double x, double z, double apart) {
        for (Vec3 shelter : shelters) {
            double dx = shelter.x - x;
            double dz = shelter.z - z;
            if (dx * dx + dz * dz < apart * apart) {
                return true;
            }
        }
        return false;
    }

    /** Whether this spot is inside one of the cast's shelters, judged as every circle is. */
    static boolean isSheltered(List<Vec3> shelters, double shelterRadius, Vec3 spot) {
        double radiusSquared = shelterRadius * shelterRadius;
        for (Vec3 shelter : shelters) {
            if (spot.distanceToSqr(shelter) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    /** How long the strike's wave takes to reach the edge of its range at a shockwave's pace. */
    static int waveDuration(double range) {
        return Mth.clamp((int) Math.round(range / WAVE_SPEED),
                MIN_VFX_DURATION_TICKS, MAX_VFX_DURATION_TICKS);
    }
}
