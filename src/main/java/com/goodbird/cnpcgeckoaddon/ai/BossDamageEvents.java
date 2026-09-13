package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.NpcDamageResistEntry;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.entity.EntityFluidSpit;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.INpcImmunityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Every way a boss, its totems and its victims take or turn away a hit.
 *
 * <p>The order the stages run in is the whole mechanic - a shield that drops a hit whole
 * must run before a resistance that would only take a percentage of it - so it is written
 * out rather than left to the bus. The four priorities below are the four stages, and the
 * stages that share one are called in order inside a single listener instead of being four
 * listeners that happen to be declared in that order. Registration order between listeners
 * of equal priority is not something the bus promises, and a shield that silently started
 * running after the resistance would look like a balance change rather than a bug.</p>
 *
 * <p>Keeping them together also means one listener per priority rather than eight on a path
 * every hit dealt anywhere in the world walks down.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public final class BossDamageEvents {

    private BossDamageEvents() {
    }

    /**
     * The multipliers, settled before any other stage reads the number.
     *
     * <p>Registered HIGHEST so every stage below works from what really came in - a resistance
     * is a percentage of what the boss actually swung for, or of how hard its fire actually
     * bit, not of the calm swing or the vanilla burn. Both stages only multiply, so the order
     * between them changes nothing.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScaleIncomingDamage(final LivingIncomingDamageEvent event) {
        scaleEnragedBossAttack(event);
        scaleBossFire(event);
    }

    /**
     * Makes an enraged boss' own swing hit as hard as the enrage says.
     *
     * <p>Here rather than on an attribute because CustomNPCs deals melee damage straight out
     * of {@code stats.melee.getStrength()} and never reads {@code ATTACK_DAMAGE}, so the
     * modifier that would be the obvious home for this scales nothing. What the addon's own
     * abilities hit for goes through the rage multiplier where their settings are read, and
     * this deliberately leaves those alone.</p>
     */
    private static void scaleEnragedBossAttack(LivingIncomingDamageEvent event) {
        float scaled = BossRageRuntime.scaleOwnAttack(event.getSource(), event.getAmount());
        if (scaled != event.getAmount()) {
            event.setAmount(scaled);
        }
    }

    /**
     * Makes a fire a boss lit bite as many times over as its slot's level, while it burns.
     *
     * <p>Every fire hit counts, not only the burn ticks: lava or flames the victim runs into
     * keep feeding the fire the boss started. Once that fire is over or put out the tracker
     * has let go, and the same lava hurts the vanilla way again.</p>
     */
    private static void scaleBossFire(LivingIncomingDamageEvent event) {
        if (!BossFireTracker.hasPending() || !event.getSource().is(DamageTypeTags.IS_FIRE)) {
            return;
        }
        int multiplier = BossFireTracker.multiplier(event.getEntity());
        if (multiplier > 1) {
            event.setAmount(event.getAmount() * multiplier);
        }
    }

    /**
     * The protections that drop a hit whole, in the order they are allowed to claim it.
     *
     * <p>All of them fire before any mitigation is calculated, so what they turn away is
     * dropped rather than reduced to zero - nothing downstream sees a damage number at all.
     * A cancelled event stops the rest of the bus, so each stage below only runs on a hit
     * the one above it let through.</p>
     */
    @SubscribeEvent
    public static void onIncomingDamage(final LivingIncomingDamageEvent event) {
        if (blockTotemsOwnSwing(event) || blockProtectedBoss(event) || blockOutsideAggroZone(event)
                || blockTotemVulnerability(event)) {
            return;
        }
        blockBlastImmunity(event);
    }

    /**
     * A totem's accidental combat AI never hurts anything. The clone still ticks, animates,
     * takes damage and runs death scripts; only its swing is prevented.
     */
    private static boolean blockTotemsOwnSwing(LivingIncomingDamageEvent event) {
        if (!BossTotemUtil.isTotem(event.getSource().getEntity())) {
            return false;
        }
        event.setCanceled(true);
        return true;
    }

    /** Swallows every hit aimed at a boss protected by a phase or full-immunity totems. */
    private static boolean blockProtectedBoss(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityNPCInterface npc)
                || !(npc instanceof IBossController holder)
                // Leaving this tag alone keeps /kill working: a boss nobody can remove while
                // it is immune would be untestable.
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        if (controller == null) {
            return false;
        }
        boolean phaseProtection = controller.isInvulnerable();
        boolean totemProtection = controller.isTotemProtected()
                && controller.getTotemProtectionMode() == TeleportPathData.TOTEM_PROTECTION_FULL_IMMUNITY;
        if (!phaseProtection && !totemProtection) {
            return false;
        }
        // Cancelling means LivingDamageEvent.Post never runs, so whoever swung still has to
        // be signed up for the boss bar here.
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            trackParticipant(npc, player);
        }
        if (totemProtection) {
            controller.playTotemHitFeedback();
        } else {
            controller.playInvulnerableHitFeedback();
        }
        event.setCanceled(true);
        return true;
    }

    /**
     * Turns away a player's hit on a boss whose aggro zone keeps hits from outside out, when
     * that player is standing outside the box.
     *
     * <p>Under the phase and totem protection, so an immune boss still answers the way it
     * always did. Nobody is signed up for the fight here: hitting in from outside is exactly
     * what does not make somebody part of it. The attacker is the causing entity, so an arrow
     * or a thrown npc is judged by where its thrower stands, not by where it lands.</p>
     */
    private static boolean blockOutsideAggroZone(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityNPCInterface npc)
                || !(npc instanceof IBossController holder)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                // The escape hatch the rest of this row leaves: /kill and the void keep working.
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        if (controller == null || !controller.turnsAwayHitFrom(player)) {
            return false;
        }
        float before = event.getAmount();
        event.setCanceled(true);
        controller.playInvulnerableHitFeedback();
        // Nothing downstream is told a cancelled hit existed, so the reason is given here.
        NpcDamageInfoManager.reportOutsideZoneBlock(event, before);
        return true;
    }

    /**
     * Turns away everything a totem's slot does not list as able to break it.
     *
     * <p>The list is read off the totem itself rather than out of its owner's settings,
     * because the owner may be standing in an unloaded chunk while its totem is being
     * swung at.</p>
     */
    private static boolean blockTotemVulnerability(LivingIncomingDamageEvent event) {
        // Leaving the bypass tag alone is the escape hatch the protections above also leave:
        // /kill and the void keep working, or a totem with an empty list could never be got
        // rid of again.
        if (!BossTotemUtil.isTotem(event.getEntity())
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        int ability = incomingAbility(event.getSource());
        if (!BossTotemUtil.rejects(event.getEntity(), ability)) {
            return false;
        }
        float before = event.getAmount();
        event.setCanceled(true);
        // Reported from here for the same reason the resistances report from their own
        // stage: nothing downstream is told this hit existed, let alone why it stopped.
        NpcDamageInfoManager.reportTotemBlock(event, before, ability);
        return true;
    }

    /**
     * Lets an npc marked immune to the death blast stand in the crater.
     *
     * <p>The explosion is a vanilla one going off after the boss is gone, so there is no
     * ability call left to filter it from the inside; what it leaves behind is a damage
     * source tagged as an explosion, and that is what this catches. It cannot tell whose
     * explosion it was, which is why the setting is described as covering every blast rather
     * than only the boss one.</p>
     */
    private static void blockBlastImmunity(LivingIncomingDamageEvent event) {
        if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)
                && BossAbilityDamageUtil.isImmune(event.getEntity(), BossAbilityKind.BLAST)) {
            event.setCanceled(true);
        }
    }

    /**
     * Which ability is behind one hit, or {@link BossAbilityDamageUtil#NO_ABILITY} for damage
     * that belongs to none.
     *
     * <p>Most abilities say so themselves while they land. The ones that cannot are read off
     * the hit instead: a projectile carries its own damage rather than going through the
     * ability door, and an explosion arrives after the boss that armed it is gone - so, the
     * same compromise the blast immunity makes, listing the death blast lets any explosion
     * break the totem.</p>
     */
    private static int incomingAbility(DamageSource source) {
        int landing = BossAbilityDamageUtil.currentAbility();
        if (landing != BossAbilityDamageUtil.NO_ABILITY) {
            return landing;
        }
        if (source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof EntityNPCInterface) {
            return projectile instanceof EntityFluidSpit
                    ? BossAbilityKind.FLUID : BossAbilityKind.RANGED;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return BossAbilityKind.BLAST;
        }
        return BossAbilityDamageUtil.NO_ABILITY;
    }

    /**
     * Applies the npc's own damage resistance list to whatever still comes in.
     *
     * <p>Registered LOW so the protections above have already had their say - a hit they
     * swallowed whole never reaches a mere percentage. The CustomNPCs resistances run later,
     * inside mitigation, so this multiplier stacks on top of them: 50% here and 50% there
     * make 25%.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onNpcDamageResist(final LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityNPCInterface npc)) {
            return;
        }
        float before = event.getAmount();
        NpcDamageResistEntry resist = null;
        if (npc.ais instanceof INpcImmunityData holder
                // The same escape hatch the phase protection leaves: /kill has to keep working.
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            resist = holder.cnpcgeckoaddon$getNpcImmunityData().findResist(event.getSource());
        }
        if (resist != null) {
            if (resist.getPercent() == 0) {
                // Cancelled rather than zeroed: full immunity should leave no knockback, no
                // hurt animation and nothing for on-hit effects to ride in on.
                event.setCanceled(true);
            } else {
                event.setAmount(before * resist.getPercent() / 100.0F);
            }
        }
        // Reported from here rather than its own listener so the breakdown can name the rule
        // that fired and both sides of the multiplication.
        NpcDamageInfoManager.report(event, before, resist);
    }

    /**
     * Pays a hit on a boss out of its barrier, or scales one up while the boss is exposed.
     *
     * <p>Registered LOWEST, under the resistance list: the barrier stands beside the totem
     * shield and the resistances rather than in place of them, so it takes what those let
     * through - the totem shield drops a hit whole before either, and a resisted hit reaches
     * the barrier already reduced. Cancelled rather than zeroed for the immune phase's
     * reason: nothing downstream should see a damage number, a hurt flinch or a knockback
     * for a hit the shield held. The window's multiplier is the other half of the same
     * mechanic, and lives here for the same reason.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBossBarrier(final LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityNPCInterface npc)
                || !(npc instanceof IBossController holder)
                // The same escape hatch every protection leaves: /kill has to keep working.
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        if (controller == null) {
            return;
        }
        float before = event.getAmount();
        if (controller.isBarrierUp()) {
            // A boss staggered under its own shield - a dash into a wall while it stands - pays
            // the stagger's share into the shield, the way it would out of its health: the shield
            // is that health standing still. Under the barrier alone a window never opens while
            // a shield is up, so this changes nothing for it.
            float incoming = controller.isBarrierExposed()
                    ? before * controller.barrierExposedPercent() / 100.0F : before;
            float absorbed = controller.absorbIntoBarrier(incoming,
                    event.getSource().is(DamageTypeTags.BYPASSES_COOLDOWN));
            // Cancelling means LivingDamageEvent.Post never runs, so whoever swung still has
            // to be signed up for the fight here.
            if (event.getSource().getEntity() instanceof ServerPlayer player) {
                trackParticipant(npc, player);
            }
            event.setCanceled(true);
            NpcDamageInfoManager.reportBarrier(event, before, absorbed, controller.barrierLeft());
            return;
        }
        if (controller.isBarrierExposed()) {
            int percent = controller.barrierExposedPercent();
            event.setAmount(before * percent / 100.0F);
            NpcDamageInfoManager.reportExposed(event, before, percent);
        }
    }

    /**
     * The two claims on the fully mitigated number, after armor and effects but before any
     * health changes. Absorption is applied later and can only make the result safer.
     *
     * <p>The lethal guard runs first and the cocoon last, because what breaks a shell open
     * is the damage that would really have landed - including a clamp the guard just put
     * on it.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePre(final LivingDamageEvent.Pre event) {
        clampToLethalGuard(event);
        breakCocoonOnLethalHit(event);
    }

    /** Leaves a boss standing on one health for as long as its lethal-guard totems do. */
    private static void clampToLethalGuard(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof EntityNPCInterface npc)
                || !(npc instanceof IBossController holder)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        if (controller == null || !controller.isTotemProtected()
                || controller.getTotemProtectionMode() != TeleportPathData.TOTEM_PROTECTION_LETHAL_GUARD) {
            return;
        }
        float maximumDamage = Math.max(0.0F, npc.getHealth() - 1.0F);
        if (event.getNewDamage() <= maximumDamage) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            trackParticipant(npc, player);
        }
        event.setNewDamage(maximumDamage);
        controller.playTotemHitFeedback();
    }

    /**
     * A hit that would kill a cocoon breaks it open instead.
     *
     * <p>A cocoon must never die the way a clone dies - CustomNPCs drops its loot and runs
     * its death scripts ahead of vanilla's own death - so the killing blow is turned to
     * nothing here and the manager opens the shell on its next tick, letting the victim out
     * with the effects for it.</p>
     */
    private static void breakCocoonOnLethalHit(LivingDamageEvent.Pre event) {
        if (BossCocoonUtil.isCocoon(event.getEntity()) && BossCocoonManager.breakOnLethalHit(
                event.getEntity(), event.getNewDamage(), event.getSource().getEntity())) {
            event.setNewDamage(0.0F);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(final LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof EntityNPCInterface npc
                && event.getSource().getEntity() instanceof ServerPlayer player) {
            trackParticipant(npc, player);
        } else if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().getEntity() instanceof EntityNPCInterface npc) {
            trackParticipant(npc, player);
        }
    }

    /**
     * What a landing owes, in the one order that lets both halves happen.
     *
     * <p>The gravity throw's hit goes first because the leap's half cancels the event, and a
     * cancelled event is the end of the bus: as two listeners these two could never both run
     * on the same landing, and which one did was down to the order the bus happened to
     * register them in.</p>
     */
    @SubscribeEvent
    public static void onLivingFall(final LivingFallEvent event) {
        landGravityThrow(event);
        cancelOwnLeapFall(event);
    }

    /**
     * Lands the gravity throw's own hit on whoever it threw, the moment they come down.
     *
     * <p>From the fall event rather than from a tick, because this fires before vanilla
     * works out the fall's own damage - and landing the extra hit first is what lets the two
     * stack instead of the second being swallowed by the first's invulnerability frames.
     * Whoever was not thrown is not in the scheduler's list and is left to fall as usual.</p>
     */
    private static void landGravityThrow(LivingFallEvent event) {
        if (!event.getEntity().level().isClientSide) {
            BossGravityScheduler.onFall(event.getEntity(), event.getDistance(), event.getDamageMultiplier());
        }
    }

    /**
     * A boss is not hurt by the arc it threw itself along, nor by the ledge it charged off.
     *
     * <p>The controller wipes the fall distance every tick of a leap, so what is left here
     * is at most one tick of drop - but a jump down off a ledge still crosses vanilla's
     * three block threshold, and dying to your own signature move is not a mechanic.</p>
     *
     * <p>Only a leap in flight and a dash - its run, and the drop off the end of one - are
     * covered: a boss that walks off a ledge on its own falls exactly as it always did.</p>
     */
    private static void cancelOwnLeapFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof EntityNPCInterface npc)
                || !(npc instanceof IBossController holder)) {
            return;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        if (controller != null && (controller.isLeaping() || controller.isDashing())) {
            event.setCanceled(true);
        }
    }

    static void trackParticipant(EntityNPCInterface npc, ServerPlayer player) {
        if (npc instanceof IBossController holder) {
            TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
            if (controller != null) {
                controller.trackParticipant(player);
            }
        }
    }
}
