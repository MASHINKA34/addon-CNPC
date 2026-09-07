package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * One row per thing a boss can be doing, and everything the rotation needs to know about it.
 *
 * <p>What the controller used to hold as five switch statements side by side - which phase
 * setting turns this ability on, which cooldown it runs down, which slot of the shared
 * ability list it warns under, and one {@code nextXxxAt} field each - is one entry here.
 * The point is not the line count. Adding an ability meant touching five separate places
 * that had no way of telling each other something was missing, and the only symptom of a
 * forgotten branch was an attack that silently never fired or a cooldown that never reset.
 * A row that is short one column does not compile.</p>
 *
 * <p>The declaration order is the rotation order, and it is the one the fight had before
 * this table existed: the controller walks {@link #ROTATION} from a moving start so a short
 * cooldown cannot starve everything behind it. {@link #TELEPORT} and {@link #NONE} carry no
 * settings - a teleport is scheduled from its own random delay range, and nothing at all is
 * not on any clock - so they sort to the end and stay out of the rotation.</p>
 */
enum BossAbility {
    GROUND_ATTACK(BossAbilityKind.AREA, BossPhaseData::isAreaAttackEnabled,
            BossPhaseData::getAreaAttackCooldownTicks),
    RANGED_ATTACK(BossAbilityKind.RANGED, BossPhaseData::isRangedAttackEnabled,
            BossPhaseData::getRangedAttackCooldownTicks),
    MELEE_ATTACK(BossAbilityKind.MELEE, BossPhaseData::isMeleeAttackEnabled,
            BossPhaseData::getMeleeAttackCooldownTicks),
    FLUID_SPIT(BossAbilityKind.FLUID, BossPhaseData::canSpitFluid,
            BossPhaseData::getFluidSpitCooldownTicks),
    HOOK(BossAbilityKind.HOOK, BossPhaseData::isHookEnabled,
            BossPhaseData::getHookCooldownTicks),
    CAPTURE(BossAbilityKind.CAPTURE, BossPhaseData::isCaptureEnabled,
            BossPhaseData::getCaptureCooldownTicks),
    LEAP(BossAbilityKind.LEAP, BossPhaseData::isLeapEnabled,
            BossPhaseData::getLeapCooldownTicks),
    LINE_ATTACK(BossAbilityKind.LINE, BossPhaseData::isLineAttackEnabled,
            BossPhaseData::getLineAttackCooldownTicks),
    GEYSER(BossAbilityKind.GEYSER, BossPhaseData::isGeyserEnabled,
            BossPhaseData::getGeyserCooldownTicks),
    BOULDER(BossAbilityKind.BOULDER, BossPhaseData::canLaunchBoulder,
            BossPhaseData::getBoulderCooldownTicks),
    BOULDER_RAIN(BossAbilityKind.BOULDER_RAIN, BossPhaseData::canLaunchBoulderRain,
            BossPhaseData::getBoulderRainCooldownTicks),
    TETHER(BossAbilityKind.TETHER, BossPhaseData::isTetherEnabled,
            BossPhaseData::getTetherCooldownTicks),
    GRAVITY(BossAbilityKind.GRAVITY, BossPhaseData::isGravityEnabled,
            BossPhaseData::getGravityCooldownTicks),
    MARK(BossAbilityKind.MARK, BossPhaseData::isMarkEnabled,
            BossPhaseData::getMarkCooldownTicks),
    COVER(BossAbilityKind.COVER, BossPhaseData::isCoverEnabled,
            BossPhaseData::getCoverCooldownTicks),
    HUNT(BossAbilityKind.HUNT, BossPhaseData::isHuntEnabled,
            BossPhaseData::getHuntCooldownTicks),
    BEAM(BossAbilityKind.BEAM, BossPhaseData::isBeamEnabled,
            BossPhaseData::getBeamCooldownTicks),
    COCOON(BossAbilityKind.COCOON, BossPhaseData::canCocoon,
            BossPhaseData::getCocoonCooldownTicks),
    SUMMON(BossAbilityKind.SUMMON, BossPhaseData::canSummon,
            BossPhaseData::getSummonCooldownTicks),

    /** Scheduled from its own random delay range rather than from a flat cooldown. */
    TELEPORT(-1, null, null),
    /** No action at all: the boss is between casts. */
    NONE(-1, null, null);

    /** Neither a teleport nor an idle boss appears in any per-ability mask. */
    static final int NO_KIND = -1;

    /**
     * The abilities the rotation walks, in the order it walks them. Derived rather than
     * written out a second time: a new row lands in the rotation by existing.
     */
    static final List<BossAbility> ROTATION =
            Arrays.stream(values()).filter(BossAbility::isScheduledFromCooldown).toList();

    private final int kind;
    private final Predicate<BossPhaseData> enabled;
    private final ToIntFunction<BossPhaseData> cooldown;

    BossAbility(int kind, Predicate<BossPhaseData> enabled, ToIntFunction<BossPhaseData> cooldown) {
        this.kind = kind;
        this.enabled = enabled;
        this.cooldown = cooldown;
    }

    /**
     * Which ability of the shared list this action performs, or {@link #NO_KIND} for the two
     * that are on no list. The warning and standing-cast masks both index by it.
     */
    int kind() {
        return kind;
    }

    /** Whether the rotation owns this one's clock, which is what puts it in {@link #ROTATION}. */
    boolean isScheduledFromCooldown() {
        return enabled != null;
    }

    /** Whether this phase has the ability switched on at all. */
    boolean isEnabledIn(BossPhaseData phase) {
        return enabled != null && enabled.test(phase);
    }

    /** The configured cooldown in ticks, before the enrage bonus is taken off it. */
    int cooldownTicks(BossPhaseData phase) {
        return cooldown == null ? 0 : cooldown.applyAsInt(phase);
    }
}
