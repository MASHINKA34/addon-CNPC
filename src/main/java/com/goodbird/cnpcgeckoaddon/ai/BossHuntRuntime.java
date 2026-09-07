package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossEffectSet;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import noppes.npcs.entity.EntityNPCInterface;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

/**
 * The chase: the boss picks one victim and goes after nobody else until it catches them or
 * the time runs out.
 *
 * <p>Owned by {@link TeleportPathController}, which keeps the cooldown and the wind-up and
 * hands the chase over once the ability lands. Nothing here moves the boss - from the tick
 * it sets off the vanilla chase is what runs it, and this only holds it to one target and
 * gives it a longer stride.</p>
 */
final class BossHuntRuntime {

    /** The hunt's stride, hung on the boss the way the enrage bonus is and taken off the same way. */
    private static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "boss_hunt");
    /**
     * Ticks between one catch and the next when catching does not end the hunt. A prey the
     * boss is standing on is hit once a second, not twenty times.
     */
    private static final int CATCH_INTERVAL_TICKS = 20;

    /**
     * The hunt being run right now, frozen on the tick the boss set off.
     *
     * <p>Read back from here rather than off the phase again, the way a take cover strike
     * keeps its settings: a chase can run for a minute, and the rule the prey was told must
     * not change under them. Nothing of this is saved - a server that goes down mid chase
     * owes nobody the rest of it, and a hunt is over the moment its boss is reloaded.</p>
     */
    private static final class Hunt {
        private final int preyId;
        /** Game time the boss gives the chase up at. */
        private final long endsAt;
        private final double catchRadius;
        /** What a catch hits for before the enrage bonus, which is read fresh on every catch. */
        private final int damage;
        private final BossEffectSet effects;
        private final boolean catchEnds;
        /** Whether the rest of the rotation waits for this chase to end. */
        private final boolean silence;
        /** Whether the glow on the prey is this hunt's to take off again. */
        private final boolean glowing;
        /** Game time the boss may next count the prey as caught. */
        private long nextCatchAt;

        private Hunt(LivingEntity prey, BossPhaseData phase, long gameTime) {
            preyId = prey.getId();
            endsAt = gameTime + phase.getHuntDurationTicks();
            catchRadius = phase.getHuntCatchRadius();
            damage = phase.getHuntDamage();
            effects = phase.getHuntEffects();
            catchEnds = phase.isHuntCatchEnds();
            silence = phase.isHuntSilence();
            glowing = phase.isHuntGlow();
            nextCatchAt = gameTime;
        }
    }

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /** The chase being run, or null while the boss is on nobody in particular. */
    private Hunt hunt;

    BossHuntRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /**
     * Whether this candidate can be hunted, or - once the chase is on - still can be.
     *
     * <p>The rule the boss keeps its ordinary target by, with the ability's own immunity on
     * top and without the sight line: the prey is picked to be run down, not shot at, and
     * going round a corner is how one is supposed to run. The slack past the search radius
     * is the one the ordinary combat target allows, so a prey the boss would still be
     * fighting is one it is still hunting; inside an arena that keeps its fight in, leaving
     * the arena is leaving the hunt.</p>
     */
    boolean isValidTarget(LivingEntity target, TeleportPathData data) {
        if (target == null || target.level() != npc.level() || !target.isAlive()
                || target.isRemoved() || !boss.isAbilityTarget(target, BossAbilityKind.HUNT)) {
            return false;
        }
        if (data.isAggroZoneEnabled() && data.isAggroZoneKeepInside()
                && npc.level() instanceof ServerLevel level) {
            AABB zone = boss.aggroZoneBounds(level, data);
            if (zone == null || !zone.contains(target.position())) {
                return false;
            }
        }
        double leash = data.getTargetSearchRadius() * 1.5D;
        return npc.distanceToSqr(target) <= leash * leash;
    }

    /**
     * Sets the boss off after the prey it wound up on.
     *
     * <p>Nothing here moves the boss. From this tick the vanilla chase is what runs it, and
     * this only makes sure the chase has one target and a longer stride: a stationary or
     * totem-held boss never gets to walk anyway, so for one of those the hunt is a change of
     * target and a glow, and nothing else. The wind-up's root is let go at once rather than
     * held through the after-pause, because the chase is the ability - a boss that roared
     * and then stood there for half a second would have handed its prey the head start the
     * roar was meant to be.</p>
     */
    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        LivingEntity prey = boss.pendingTarget(level);
        if (!isValidTarget(prey, boss.settings())) {
            return;
        }
        hunt = new Hunt(prey, phase, gameTime);
        if (hunt.glowing) {
            // Not ambient and no particles: the outline is the mark, and a cloud of swirls
            // round the prey would only hide who it is on. As long as the chase, so the glow
            // goes out with the hunt even if nothing gets to take it off.
            prey.addEffect(new MobEffectInstance(MobEffects.GLOWING, phase.getHuntDurationTicks(),
                    0, false, false, true), npc);
        }
        applySpeed(phase.getHuntSpeedPercent() / 100.0D);
        boss.setTargetIfChanged(prey);
        announce(prey);
        boss.endCastRoot();
    }

    /**
     * Keeps the chase on its prey, and calls it off when it is over.
     *
     * <p>Every tick, so nothing that touched the target since the last one - the vanilla
     * aggro picking whoever hit hardest, a script, another player's swing - lasts past the
     * tick it happened in. The prey is judged by the rule that picked them, so the chase
     * ends the moment they are gone: dead, logged out, out of the arena, or off in
     * creative.</p>
     */
    void tick(ServerLevel level, TeleportPathData data, long gameTime) {
        Hunt current = hunt;
        if (current == null) {
            return;
        }
        LivingEntity prey = level.getEntity(current.preyId) instanceof LivingEntity living ? living : null;
        if (gameTime >= current.endsAt || !isValidTarget(prey, data)) {
            end();
            return;
        }
        boss.setTargetIfChanged(prey);
        if (gameTime >= current.nextCatchAt && isWithinCatch(prey, current.catchRadius)) {
            catchPrey(level, current, prey, gameTime);
        }
    }

    /**
     * Whether the boss has reached its prey, judged the way a melee swing is: from edge to
     * edge rather than centre to centre, because a boss three blocks wide could never bring
     * its centre within two of anyone.
     */
    private boolean isWithinCatch(LivingEntity prey, double catchRadius) {
        double reach = catchRadius + (npc.getBbWidth() + prey.getBbWidth()) * 0.5D;
        return npc.distanceToSqr(prey) <= reach * reach;
    }

    /**
     * The boss has caught up with its prey.
     *
     * <p>The catch is the moment of reaching, not the damage landing: a hit swallowed by
     * invulnerability frames still counts, or a prey caught a tick after a stray arrow
     * would be caught again and again. The vanilla contact swing CustomNPCs makes is left
     * exactly as it was, so this is the hunt's own hit and nothing more.</p>
     */
    private void catchPrey(ServerLevel level, Hunt current, LivingEntity prey, long gameTime) {
        BossAbilityDamageUtil.hit(prey, BossAbilityKind.HUNT, npc, boss.rageUp(current.damage),
                current.effects, 0, 0.0D, 0.0D);
        level.sendParticles(BossTelegraphUtil.dust(BossAbilityKind.HUNT), prey.getX(),
                prey.getY() + prey.getBbHeight() * 0.5D, prey.getZ(), 12,
                prey.getBbWidth() * 0.5D, prey.getBbHeight() * 0.3D, prey.getBbWidth() * 0.5D, 0.0D);
        if (current.catchEnds) {
            end();
            return;
        }
        current.nextCatchAt = gameTime + CATCH_INTERVAL_TICKS;
    }

    /**
     * Calls the chase off, whichever way it ended.
     *
     * <p>Idempotent and the one road out, so every ending - the clock, a catch, the prey
     * gone, a phase change, a reset, the boss dying or the level unloading - takes the
     * stride and the glow off with it. A modifier left behind here would turn the boss into
     * a sprinter for the rest of its life. The target is left alone: the boss keeps fighting
     * whoever it was on, and the ordinary retargeting picks up from there.</p>
     */
    void end() {
        // The stride comes off whether or not a hunt is on record: it is the one part of a
        // hunt that could outlive the record, and taking off a modifier that is not there
        // is free.
        clearSpeed();
        Hunt ended = hunt;
        if (ended == null) {
            return;
        }
        hunt = null;
        // Only the glow this hunt put on; a builder who switched it off may be using the
        // effect for something of their own.
        if (ended.glowing && npc.level() instanceof ServerLevel level
                && level.getEntity(ended.preyId) instanceof LivingEntity prey) {
            prey.removeEffect(MobEffects.GLOWING);
        }
    }

    /**
     * Hangs the chase's stride on the entity itself, the way the enrage bonus is hung.
     *
     * <p>Transient on purpose: a permanent modifier is written into the entity NBT, and a
     * boss that went down mid chase would come back from the reload still sprinting. Scaled
     * on top of the finished value, so it stacks with the enrage rather than replacing it.</p>
     */
    private void applySpeed(double multiplier) {
        AttributeInstance speed = npc.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(MODIFIER_ID);
        speed.addTransientModifier(new AttributeModifier(MODIFIER_ID, multiplier - 1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private void clearSpeed() {
        AttributeInstance speed = npc.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            // Removing a modifier that is not there is a no-op, not an error.
            speed.removeModifier(MODIFIER_ID);
        }
    }

    /**
     * Tells the prey it has been picked: the boss' name, in the action bar and in the
     * hunt's colour.
     *
     * <p>Unconditional, the way the take cover countdown is: being told is the mechanic,
     * and the ordinary warning settings only decide whether everyone else hears the name.
     * Sent through the wind-up and once more as the boss sets off, because the action bar
     * fades and the wind-up may run longer than it stays up.</p>
     */
    void announce(LivingEntity prey) {
        if (!(prey instanceof ServerPlayer player)) {
            return;
        }
        Component line = Component.translatable("cnpcgeckoaddon.boss.hunt_marked", npc.getDisplayName())
                .withStyle(style -> style.withColor(BossTelegraphUtil.textColor(BossAbilityKind.HUNT)));
        player.displayClientMessage(line, true);
    }

    /** Whether the boss is on one prey right now. */
    boolean isHunting() {
        return hunt != null;
    }

    /** Whether a running hunt keeps the rest of the rotation, the teleport included, quiet. */
    boolean isSilenced() {
        return hunt != null && hunt.silence;
    }

    /** Read-only status used by the boss diagnostic command. */
    String status(long gameTime, BossPhaseData phase, long nextHuntAt) {
        AttributeInstance speed = npc.getAttribute(Attributes.MOVEMENT_SPEED);
        boolean stride = speed != null && speed.hasModifier(MODIFIER_ID);
        Hunt current = hunt;
        if (current != null) {
            String prey = npc.level().getEntity(current.preyId) instanceof LivingEntity living
                    ? living.getName().getString() : "?";
            return "Hunt: chasing " + prey + ", " + Math.max(0L, current.endsAt - gameTime)
                    + " ticks left, speed modifier " + (stride ? "on" : "MISSING");
        }
        // Named loudly: a stride still on with no hunt to own it is exactly the leak every
        // way out of a hunt is meant to rule out.
        String leak = stride ? " (speed modifier still on!)" : "";
        if (phase == null || !phase.isHuntEnabled()) {
            return "Hunt: disabled" + leak;
        }
        long remaining = nextHuntAt == NOT_SCHEDULED ? 0L : nextHuntAt - gameTime;
        return (remaining > 0L ? "Hunt: cooldown " + remaining : "Hunt: ready") + leak;
    }
}
