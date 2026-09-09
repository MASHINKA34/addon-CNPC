package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossCastSpot;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
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
 * <p>The cast spot is a column too, and the one reason the table is public: the rotation
 * reads where an ability sends the boss from the same row the settings screen edits, so a
 * spot cannot be wired to one ability on the screen and another in play.</p>
 *
 * <p>The declaration order is the rotation order, and it is the one the fight had before
 * this table existed: the controller walks {@link #ROTATION} from a moving start so a short
 * cooldown cannot starve everything behind it. {@link #TELEPORT} and {@link #NONE} carry no
 * settings - a teleport is scheduled from its own random delay range, and nothing at all is
 * not on any clock - so they sort to the end and stay out of the rotation.</p>
 */
public enum BossAbility {
    GROUND_ATTACK(BossAbilityKind.AREA, phase -> phase.areaAttack().isEnabled(),
            phase -> phase.areaAttack().getCooldownTicks(), phase -> phase.areaAttack().castSpot()),
    RANGED_ATTACK(BossAbilityKind.RANGED, phase -> phase.rangedAttack().isEnabled(),
            phase -> phase.rangedAttack().getCooldownTicks(), phase -> phase.rangedAttack().castSpot()),
    MELEE_ATTACK(BossAbilityKind.MELEE, phase -> phase.meleeAttack().isEnabled(),
            phase -> phase.meleeAttack().getCooldownTicks(), phase -> phase.meleeAttack().castSpot()),
    FLUID_SPIT(BossAbilityKind.FLUID, phase -> phase.fluidSpit().canSpit(),
            phase -> phase.fluidSpit().getCooldownTicks(), phase -> phase.fluidSpit().castSpot()),
    HOOK(BossAbilityKind.HOOK, phase -> phase.hook().isEnabled(),
            phase -> phase.hook().getCooldownTicks(), phase -> phase.hook().castSpot()),
    CAPTURE(BossAbilityKind.CAPTURE, phase -> phase.capture().isEnabled(),
            phase -> phase.capture().getCooldownTicks(), phase -> phase.capture().castSpot()),
    LEAP(BossAbilityKind.LEAP, phase -> phase.leap().isEnabled(),
            phase -> phase.leap().getCooldownTicks(), phase -> phase.leap().castSpot()),
    LINE_ATTACK(BossAbilityKind.LINE, phase -> phase.lineAttack().isEnabled(),
            phase -> phase.lineAttack().getCooldownTicks(), phase -> phase.lineAttack().castSpot()),
    GEYSER(BossAbilityKind.GEYSER, phase -> phase.geyser().isEnabled(),
            phase -> phase.geyser().getCooldownTicks(), phase -> phase.geyser().castSpot()),
    BOULDER(BossAbilityKind.BOULDER, phase -> phase.boulder().canLaunch(),
            phase -> phase.boulder().getCooldownTicks(), phase -> phase.boulder().castSpot()),
    BOULDER_RAIN(BossAbilityKind.BOULDER_RAIN, phase -> phase.boulderRain().canLaunch(),
            phase -> phase.boulderRain().getCooldownTicks(), phase -> phase.boulderRain().castSpot()),
    TETHER(BossAbilityKind.TETHER, phase -> phase.tether().isEnabled(),
            phase -> phase.tether().getCooldownTicks(), phase -> phase.tether().castSpot()),
    GRAVITY(BossAbilityKind.GRAVITY, phase -> phase.gravity().isEnabled(),
            phase -> phase.gravity().getCooldownTicks(), phase -> phase.gravity().castSpot()),
    MARK(BossAbilityKind.MARK, phase -> phase.mark().isEnabled(),
            phase -> phase.mark().getCooldownTicks(), phase -> phase.mark().castSpot()),
    COVER(BossAbilityKind.COVER, phase -> phase.cover().isEnabled(),
            phase -> phase.cover().getCooldownTicks(), phase -> phase.cover().castSpot()),
    HUNT(BossAbilityKind.HUNT, phase -> phase.hunt().isEnabled(),
            phase -> phase.hunt().getCooldownTicks(), phase -> phase.hunt().castSpot()),
    BEAM(BossAbilityKind.BEAM, phase -> phase.beam().isEnabled(),
            phase -> phase.beam().getCooldownTicks(), phase -> phase.beam().castSpot()),
    COCOON(BossAbilityKind.COCOON, phase -> phase.cocoon().canCocoon(),
            phase -> phase.cocoon().getCooldownTicks(), phase -> phase.cocoon().castSpot()),
    SUMMON(BossAbilityKind.SUMMON, phase -> phase.summon().canSummon(),
            phase -> phase.summon().getCooldownTicks(), phase -> phase.summon().castSpot()),

    /** Scheduled from its own random delay range rather than from a flat cooldown. */
    TELEPORT(-1, null, null, null),
    /** No action at all: the boss is between casts. */
    NONE(-1, null, null, null);

    /** Neither a teleport nor an idle boss appears in any per-ability mask. */
    static final int NO_KIND = -1;

    /**
     * The abilities the rotation walks, in the order it walks them. Derived rather than
     * written out a second time: a new row lands in the rotation by existing.
     */
    public static final List<BossAbility> ROTATION =
            Arrays.stream(values()).filter(BossAbility::isScheduledFromCooldown).toList();

    private final int kind;
    private final Predicate<BossPhaseData> enabled;
    private final ToIntFunction<BossPhaseData> cooldown;
    private final Function<BossPhaseData, BossCastSpot> castSpot;

    BossAbility(int kind, Predicate<BossPhaseData> enabled, ToIntFunction<BossPhaseData> cooldown,
                Function<BossPhaseData, BossCastSpot> castSpot) {
        this.kind = kind;
        this.enabled = enabled;
        this.cooldown = cooldown;
        this.castSpot = castSpot;
    }

    /**
     * Which ability of the shared list this action performs, or {@link #NO_KIND} for the two
     * that are on no list. The warning and standing-cast masks both index by it.
     */
    public int kind() {
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

    /**
     * Where this phase sends the boss before it casts this, or null for the two actions that
     * carry no settings. Unset for most abilities of most bosses, which is the boss casting
     * from wherever it stands.
     */
    public BossCastSpot castSpot(BossPhaseData phase) {
        return castSpot == null ? null : castSpot.apply(phase);
    }
}
