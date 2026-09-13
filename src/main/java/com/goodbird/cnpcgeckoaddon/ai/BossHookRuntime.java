package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossParticleCue;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossSoundCue;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncHookCord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * The chain the boss throws, and the drag that keeps hold of whoever it caught.
 *
 * <p>Owned by {@link TeleportPathController}. The throw is an ordinary pending action; the
 * drag that follows is not - it runs for as long as the cord holds, one tick at a time, and
 * is ticked above the controller's combat gates. A pull already in flight has to finish even
 * if the boss loses its target halfway, or the victim is left hanging in mid-air.</p>
 *
 * <p>Nothing here is saved. A server that goes down mid pull drops the cords, which is what
 * the client does with them anyway once the entity it was drawing them from is gone.</p>
 */
final class BossHookRuntime {

    /**
     * One victim being dragged. A gather point of null means "keep pulling toward the boss
     * wherever it is"; a cinch freezes one spot instead so everyone lands in the same pile.
     */
    private record HookPull(int targetId, long endsAt, double strength, double stopDistance,
                            Vec3 gatherPoint, String cordStyle, double liftMax, double liftPerBlock,
                            BossParticleCue cordParticles) {
    }

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;
    private final List<HookPull> activePulls = new ArrayList<>();

    BossHookRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /** Whether a cord is still dragging somebody: the throw is over, the pull is not. */
    boolean isPulling() {
        return !activePulls.isEmpty();
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!boss.mayStart(BossAbility.HOOK, phase) || gameTime < boss.abilityScheduleAt(BossAbility.HOOK)) {
            return false;
        }
        List<LivingEntity> targets = boss.selectAbilityTargets(level, phase.hook().getTargetMode(),
                phase.hook().getMaxRange(), candidate -> isValidTarget(candidate, phase),
                phase.hook().getTargetCount());
        if (targets.isEmpty()) {
            boss.setAbilityScheduleAt(BossAbility.HOOK, gameTime + boss.retryTicks());
            return false;
        }
        boss.rememberExtraTargets(targets);
        boss.beginAction(BossAbility.HOOK, phase.hook().getAnimation(),
                phase.hook().getActionDelayTicks(), gameTime, targets.get(0), data, phase);
        boss.setAbilityScheduleAt(BossAbility.HOOK, gameTime + phase.hook().getActionDelayTicks()
                + boss.rageDown(phase.hook().getCooldownTicks()));
        return true;
    }

    boolean isValidTarget(LivingEntity target, BossPhaseData phase) {
        if (target == null || !target.isAlive() || !boss.isAbilityTarget(target, BossAbilityKind.HOOK)) {
            return false;
        }
        double distanceSquared = npc.distanceToSqr(target);
        double min = phase.hook().getMinRange();
        double max = phase.hook().getMaxRange();
        if (distanceSquared < min * min || distanceSquared > max * max) {
            return false;
        }
        // A chain that reaches through a wall looks broken, so honour the NPC line-of-sight flag.
        return !npc.ais.directLOS || npc.canNpcSee(target);
    }

    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        List<LivingEntity> victims = new ArrayList<>();
        LivingEntity primary = boss.pendingTarget(level);
        if (primary != null && isValidTarget(primary, phase)) {
            victims.add(primary);
        }
        for (int id : boss.pendingExtraTargets()) {
            if (level.getEntity(id) instanceof LivingEntity extra
                    && isValidTarget(extra, phase) && !victims.contains(extra)) {
                victims.add(extra);
            }
        }
        if (victims.isEmpty()) {
            return;
        }

        double strength = boss.rageUp(phase.hook().getPullStrength()) / 20.0D;
        long endsAt = gameTime + phase.hook().getPullDurationTicks();
        // A cinch reels everyone onto one spot and keeps them there for the full duration,
        // so the release distance is deliberately ignored - the point is to end up with a
        // tight pile that the next area attack can catch.
        boolean cinch = phase.hook().getMode() == BossPhaseData.HOOK_MODE_CINCH;
        Vec3 gatherPoint = cinch ? npc.position() : null;
        double stopDistance = cinch ? 0.0D : phase.hook().getStopDistance();
        String cordStyle = phase.hook().getCordStyle();
        double liftMax = phase.hook().getLiftMax();
        double liftPerBlock = phase.hook().getLiftPerBlock();
        // Copied on the cast, the way the volley's mark is: a pull runs for seconds, and the
        // phase under it may change while the cord is still drawn.
        BossParticleCue cordParticles = phase.hook().getCordParticles().copy();
        boolean textured = HookCordStyles.isTextured(cordStyle);
        for (LivingEntity victim : victims) {
            if (textured) {
                sendCord(victim.getId(), cordStyle, phase.hook().getPullDurationTicks());
            } else {
                drawChain(level, victim, cordParticles);
            }
            // No knockback here: what the hook shoves with is the pull below, which runs for
            // as long as the cord holds rather than for one tick.
            BossAbilityDamageUtil.hit(victim, BossAbilityKind.HOOK, npc,
                    boss.rageUp(phase.hook().getDamage()), phase.hook().getEffects(), 0, 0.0D, 0.0D);
            // Re-hooking someone already being dragged just refreshes their pull.
            activePulls.removeIf(pull -> pull.targetId() == victim.getId());
            activePulls.add(new HookPull(victim.getId(), endsAt, strength, stopDistance, gatherPoint,
                    cordStyle, liftMax, liftPerBlock, cordParticles));
            applyPull(victim, strength, gatherPoint, liftMax, liftPerBlock);
        }
        playSound(level, cordStyle, phase.hook().getCordSound());
    }

    /**
     * Drags everyone currently hooked one tick closer.
     *
     * <p>Runs before the combat-only early return: a pull that is already in flight has to
     * finish even if the boss loses its target halfway through, otherwise the victim is left
     * hanging in mid-air.</p>
     */
    void tick(ServerLevel level, long gameTime) {
        if (activePulls.isEmpty()) {
            return;
        }
        Iterator<HookPull> iterator = activePulls.iterator();
        while (iterator.hasNext()) {
            HookPull pull = iterator.next();
            boolean expired = gameTime >= pull.endsAt();
            if (expired || !(level.getEntity(pull.targetId()) instanceof LivingEntity victim)
                    || !victim.isAlive() || victim.isRemoved()) {
                // The client counts the same duration down on its own, so only a cord cut
                // short - a death, a despawn - is worth a packet.
                if (!expired) {
                    dropCord(pull);
                }
                iterator.remove();
                continue;
            }
            Vec3 destination = pull.gatherPoint() != null ? pull.gatherPoint() : npc.position();
            double stop = pull.stopDistance();
            if (stop > 0.0D && victim.position().distanceToSqr(destination) <= stop * stop) {
                dropCord(pull);
                iterator.remove();
                continue;
            }
            applyPull(victim, pull.strength(), pull.gatherPoint(), pull.liftMax(), pull.liftPerBlock());
            // A textured cord is drawn by the client and needs no top-up.
            if ((gameTime & 1L) == 0L && !HookCordStyles.isTextured(pull.cordStyle())) {
                drawChain(level, victim, pull.cordParticles());
            }
        }
    }

    /** Wipes the pulls and every cord they are still drawing, for a reset or a fight end. */
    void clear() {
        for (HookPull pull : activePulls) {
            dropCord(pull);
        }
        activePulls.clear();
    }

    private void applyPull(LivingEntity victim, double strength, Vec3 gatherPoint,
                           double liftMax, double liftPerBlock) {
        // The drag is the hook rather than a side effect of it, so it asks for itself: a pull
        // already in flight when the mask - or a totem's ability list - changes must not keep
        // tugging.
        if (BossAbilityDamageUtil.passesBy(victim, BossAbilityKind.HOOK)) {
            return;
        }
        Vec3 destination = gatherPoint != null ? gatherPoint : npc.position();
        Vec3 delta = destination.subtract(victim.position());
        double distance = delta.length();
        if (distance < 1.0E-4D) {
            return;
        }
        Vec3 velocity = delta.scale(strength / distance);
        // A flat yank grinds the victim into whatever is between them and the boss; a little
        // lift lets them clear a step or a fence instead of sticking to it.
        double lift = Math.min(liftMax, distance * liftPerBlock);
        victim.setDeltaMovement(velocity.x, velocity.y + lift, velocity.z);
        victim.fallDistance = 0.0F;
        // Players simulate their own movement, so the server has to push the new velocity
        // to them explicitly. hurtMarked is what makes ServerEntity send it.
        victim.hurtMarked = true;
    }

    /**
     * Each cord gets the voice its artwork implies; the plain sparks keep the old clang.
     *
     * <p>The cue is what plays, and while it names no sound of its own the style's own event
     * and pitch are what it plays - so picking a style still picks a voice, and picking a
     * sound overrules it for every style at once.</p>
     */
    private void playSound(ServerLevel level, String cordStyle, BossSoundCue cue) {
        cue.play(level, npc.getX(), npc.getY(), npc.getZ(), SoundSource.HOSTILE,
                styleSound(cordStyle), stylePitch(cordStyle));
    }

    private static SoundEvent styleSound(String cordStyle) {
        return switch (cordStyle) {
            case HookCordStyles.VINE -> SoundEvents.WEEPING_VINES_BREAK;
            case HookCordStyles.TENTACLE -> SoundEvents.SLIME_ATTACK;
            // A holder rather than the event itself, unlike the three around it.
            case HookCordStyles.GHOST -> SoundEvents.SOUL_ESCAPE.value();
            default -> SoundEvents.CHAIN_PLACE;
        };
    }

    private static float stylePitch(String cordStyle) {
        return switch (cordStyle) {
            case HookCordStyles.VINE -> 0.7F;
            case HookCordStyles.CHAIN_INFERNAL -> 0.4F;
            case HookCordStyles.TENTACLE -> 0.6F;
            case HookCordStyles.GHOST -> 0.8F;
            default -> 0.6F;
        };
    }

    /**
     * Tells everyone watching the boss to draw - or, with a zero duration, to drop - one cord.
     *
     * <p>The guard is for the odd client-side call into a reset: the distributor would throw
     * rather than quietly do nothing there.</p>
     */
    private void sendCord(int victimId, String cordStyle, int durationTicks) {
        if (npc.level().isClientSide()) {
            return;
        }
        NetworkWrapper.sendToTracking(npc, new PacketSyncHookCord(npc.getId(), victimId, cordStyle,
                durationTicks));
    }

    /** Textured cords outlive their pull unless the client is told the pull is over. */
    private void dropCord(HookPull pull) {
        if (HookCordStyles.isTextured(pull.cordStyle())) {
            sendCord(pull.targetId(), pull.cordStyle(), 0);
        }
    }

    private void drawChain(ServerLevel level, LivingEntity victim, BossParticleCue cordParticles) {
        Vec3 from = new Vec3(npc.getX(), npc.getEyeY() - 0.2D, npc.getZ());
        Vec3 to = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
        Vec3 step = to.subtract(from);
        int points = Mth.clamp((int) (step.length() * 2.0D), 1, 64);
        for (int i = 0; i <= points; i++) {
            Vec3 point = from.add(step.scale((double) i / points));
            cordParticles.emitDust(level, point.x, point.y, point.z, 0.0D, 0.0D, 0.0D, 0.0D,
                    BossAbilityKind.HOOK);
        }
    }
}
