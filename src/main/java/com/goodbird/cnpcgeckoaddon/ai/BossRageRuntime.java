package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.List;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

/**
 * The enrage: a clock that runs from the first combat tick and, when it expires, makes the
 * boss hit harder and act oftener for the rest of the fight.
 *
 * <p>Owned by {@link TeleportPathController}. The bonus reaches the boss three ways, because
 * no one of them covers the others: a transient attribute modifier for the speed, taken off
 * again by {@link #clear} - a permanent modifier would follow the boss into the save file;
 * {@link #up} and {@link #down} where the addon reads an ability's own numbers; and
 * {@link #scaleOwnAttack} on the npc's plain swing, which CustomNPCs deals from a field
 * rather than from an attribute.</p>
 */
final class BossRageRuntime {

    private static final ResourceLocation MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "boss_rage");
    /**
     * The one attribute the enrage can reach.
     *
     * <p>Health is deliberately absent: enrage makes the boss hit harder, not last longer.
     * {@code ATTACK_DAMAGE} is absent for a less obvious reason - CustomNPCs never reads it.
     * {@code EntityNPCInterface#doHurtTarget} takes the number straight out of
     * {@code stats.melee.getStrength()} and {@code registerBaseAttributes} writes that same
     * field back over the attribute's base, so a modifier hung here would be scaling a value
     * nothing ever asks for. The npc's own swing is scaled by {@link #scaleOwnAttack} on the
     * hit itself instead, and every ability the addon owns multiplies through {@link #up}.</p>
     */
    private static final List<Holder<Attribute>> ATTRIBUTES = List.of(Attributes.MOVEMENT_SPEED);

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /** Game time the fight started at, or NOT_SCHEDULED while no encounter is running. */
    private long encounterStartedAt = NOT_SCHEDULED;
    /** Once set, stays set until the encounter ends - a phase change does not calm the boss. */
    private boolean rageActive;

    BossRageRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /** Game time the clock started at, which the boss bar's countdown reads. */
    long encounterStartedAt() {
        return encounterStartedAt;
    }

    /**
     * Runs the enrage countdown and sets the boss off once it expires.
     *
     * <p>The clock freezes instead of resetting whenever the boss is left without a combat
     * target: a lap around the nearest corner is not supposed to buy a fresh timer. Pushing
     * the start forward is what freezes it, and keeps the deadline exactly
     * {@code encounterStartedAt + delay}. Only {@link TeleportPathController#endEncounter} clears the whole thing,
     * at the same moment the phase rolls back.</p>
     */
    void tick(ServerLevel level, long gameTime, TeleportPathData data) {
        if (!data.isRageEnabled()) {
            // Switching the timer off mid-fight has to take the bonus away with it,
            // otherwise the boss stays enraged until somebody kills it.
            clear();
            return;
        }
        if (encounterStartedAt == NOT_SCHEDULED) {
            if (!boss.hasCombatTarget()) {
                return;
            }
            encounterStartedAt = gameTime;
        }
        if (rageActive) {
            return;
        }
        if (!boss.hasCombatTarget()) {
            encounterStartedAt++;
            return;
        }
        if (gameTime < encounterStartedAt + data.getRageDelayTicks()) {
            return;
        }
        begin(level, gameTime, data);
    }

    void begin(ServerLevel level, long gameTime, TeleportPathData data) {
        rageActive = true;
        applyAttributes(multiplier());
        boss.playAnimation(data.getRageAnimation());
        boss.lockActionsUntil(gameTime + data.getRageLockTicks());
        level.playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 2.0F, 0.7F);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, npc.getX(), npc.getY(0.9D), npc.getZ(), 40,
                npc.getBbWidth() * 0.8D, npc.getBbHeight() * 0.5D, npc.getBbWidth() * 0.8D, 0.1D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, npc.getX(), npc.getY(0.4D), npc.getZ(), 30,
                npc.getBbWidth() * 0.7D, npc.getBbHeight() * 0.4D, npc.getBbWidth() * 0.7D, 0.02D);
    }

    /** Whether the boss is enraged right now. Read by the HUD and by the stat scaling below. */
    boolean isActive() {
        return boss.isActive() && rageActive;
    }

    /** @return ticks left before the boss enrages, or 0 when it already has or never will */
    int ticksLeft() {
        TeleportPathData data = boss.settings();
        if (rageActive || !data.isRageEnabled() || encounterStartedAt == NOT_SCHEDULED
                || !(npc.level() instanceof ServerLevel level)) {
            return 0;
        }
        return (int) Math.max(0L, encounterStartedAt + data.getRageDelayTicks() - level.getGameTime());
    }

    /** @return the full length of the countdown, for the HUD to draw a fill fraction against */
    int totalTicks() {
        TeleportPathData data = boss.settings();
        return data.isRageEnabled() ? data.getRageDelayTicks() : 0;
    }

    double multiplier() {
        return boss.settings().getRageMultiplierPercent() / 100.0D;
    }

    /**
     * Scales the npc's own swing while it is enraged, which no attribute can do.
     *
     * <p>Only a hit the boss dealt with its own body counts: {@code getDirectEntity} being the
     * boss itself rules out everything it shot or threw, and those carry their damage from a
     * setting the ability already multiplied. {@link BossAbilityDamageUtil#currentAbility()}
     * rules out the abilities that do hit from the boss' own hitbox, for the same reason - a
     * hit scaled by {@link #up} must not be scaled a second time here.</p>
     *
     * @return the damage this hit should land for, unchanged when the rage has no say in it
     */
    static float scaleOwnAttack(DamageSource source, float amount) {
        if (amount <= 0.0F || BossAbilityDamageUtil.currentAbility() != BossAbilityDamageUtil.NO_ABILITY
                || !(source.getEntity() instanceof EntityNPCInterface npc)
                || source.getDirectEntity() != npc
                || !(npc instanceof IBossController holder)) {
            return amount;
        }
        TeleportPathController controller = holder.cnpcgeckoaddon$getTeleportPathController();
        if (controller == null || !controller.isRageActive()) {
            return amount;
        }
        return (float) (amount * controller.rageMultiplier());
    }

    /**
     * Scales a stat that grows with the rage: damage, knockback, pull strength.
     *
     * <p>Applied everywhere the setting is read rather than written back into the phase,
     * because a phase is persisted NBT - multiplying it in place would save the doubled
     * value and let every fight stack another factor on top of the last one.</p>
     *
     * <p>A zero passes through untouched: zero knockback and zero hook damage mean "none at
     * all", and the rage is not supposed to invent an effect the boss never had.</p>
     */
    int up(int value) {
        if (!rageActive || value <= 0) {
            return value;
        }
        return Math.max(1, (int) Math.round(value * multiplier()));
    }

    /** The other half of {@link #up}, for cooldowns - the boss acts more often, not less. */
    int down(int value) {
        if (!rageActive || value <= 0) {
            return value;
        }
        return Math.max(1, (int) Math.round(value / multiplier()));
    }

    /**
     * Hangs the rage bonus on the entity itself.
     *
     * <p>Transient on purpose: a permanent modifier is written into the entity NBT, and the
     * boss would come back from a world reload still doubled, forever.</p>
     */
    void applyAttributes(double multiplier) {
        // ADD_MULTIPLIED_TOTAL is the 1.21 name of the old MULTIPLY_TOTAL - it scales the
        // finished value by 1 + amount, so a 200% setting has to be handed 1.0.
        AttributeModifier modifier = new AttributeModifier(MODIFIER_ID, multiplier - 1.0D,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        for (Holder<Attribute> attribute : ATTRIBUTES) {
            AttributeInstance instance = npc.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            instance.removeModifier(MODIFIER_ID);
            instance.addTransientModifier(modifier);
        }
    }

    /**
     * Calms the boss down and takes the attribute bonus off again.
     *
     * <p>Idempotent, so every path that ends a fight - a reset, a death, the level being
     * unloaded - can call it without checking first.</p>
     */
    void clear() {
        if (!rageActive && encounterStartedAt == NOT_SCHEDULED) {
            return;
        }
        rageActive = false;
        encounterStartedAt = NOT_SCHEDULED;
        for (Holder<Attribute> attribute : ATTRIBUTES) {
            AttributeInstance instance = npc.getAttribute(attribute);
            if (instance != null) {
                // Removing a modifier that is not there is a no-op, not an error.
                instance.removeModifier(MODIFIER_ID);
            }
        }
    }
}
