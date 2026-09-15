package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.util.Arrays;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * One health phase of a boss: what it does, and what it looks like doing it.
 *
 * <p>A phase is the sum of two dozen abilities, each with a dozen settings of its own, and
 * for a long time that was two dozen dozen fields on this one class. Every ability now owns
 * its own settings object instead - the same split the runtimes under {@code ai} already
 * have, so {@link BossLeapSettings} is what {@code BossLeapRuntime} reads and nothing else
 * has to be scrolled past to find it.</p>
 *
 * <p>What stayed here is what belongs to the phase rather than to any one ability: the
 * health it opens at, the animation it opens with, the mask saying which abilities pin
 * a walking boss down while they cast, the one saying which effects it sees out before
 * starting anything else, and the chain table saying which ability each one hands straight
 * on to. The save format did not move with the settings -
 * every key is written into the phase's own tag exactly where it always was, so a boss
 * saved before the split loads unchanged.</p>
 */
public final class BossPhaseData {

    public static final int MINION_SPAWN_RANDOM_RADIUS = 0;

    public static final int MINION_SPAWN_CONFIGURED_ONLY = 1;

    public static final int MINION_SPAWN_POINTS_THEN_RANDOM = 2;

    public static final int MINION_ORDER_LIST = 0;

    public static final int MINION_ORDER_ROUND_ROBIN = 1;

    public static final int MINION_ORDER_RANDOM = 2;

    public static final String[] MINION_SPAWN_MODE_LABELS = {
            "cnpcgeckoaddon.boss.minion_spawn_random",
            "cnpcgeckoaddon.boss.minion_spawn_points",
            "cnpcgeckoaddon.boss.minion_spawn_fallback"
    };

    public static final String[] MINION_SPAWN_ORDER_LABELS = {
            "cnpcgeckoaddon.boss.minion_spawn_list_order",
            "cnpcgeckoaddon.boss.minion_spawn_round_robin",
            "cnpcgeckoaddon.boss.minion_spawn_random_order"
    };

    /** Each victim is dragged toward the boss. */
    public static final int HOOK_MODE_PULL = 0;

    /** Every victim is reeled in to one common point and held there for the whole pull. */
    public static final int HOOK_MODE_CINCH = 1;

    public static final String[] HOOK_MODE_LABELS = {
            "cnpcgeckoaddon.boss.hook_mode_pull",
            "cnpcgeckoaddon.boss.hook_mode_cinch"
    };

    /** Straight up and back down onto the same spot. */
    public static final int LEAP_MODE_UP = 0;

    /** An arc that ends on whoever this ability picked. */
    public static final int LEAP_MODE_TARGET = 1;

    /** An arc onto absolute world coordinates. */
    public static final int LEAP_MODE_FIXED = 2;

    /** An arc onto the arena spot the boss started the fight at, plus an offset. */
    public static final int LEAP_MODE_ARENA_OFFSET = 3;

    public static final String[] LEAP_MODE_LABELS = {
            "cnpcgeckoaddon.boss.leap_mode_up",
            "cnpcgeckoaddon.boss.leap_mode_target",
            "cnpcgeckoaddon.boss.leap_mode_fixed",
            "cnpcgeckoaddon.boss.leap_mode_arena"
    };

    /** Absolute leap coordinates share the world limit the chest coordinates use. */
    public static final int MAX_LEAP_COORDINATE = 30000000;

    /** Ceiling on the arc, read by the controller when a high target raises the jump. */
    public static final int MAX_LEAP_HEIGHT = 64;

    /** The corridor is laid down toward whoever the line strike picked. */
    public static final int LINE_DIRECTION_TARGET = 0;

    /** The corridor follows the boss' own gaze, whoever happens to be standing in it. */
    public static final int LINE_DIRECTION_FACING = 1;

    public static final String[] LINE_DIRECTION_LABELS = {
            "cnpcgeckoaddon.boss.line_direction_target",
            "cnpcgeckoaddon.boss.line_direction_facing"
    };

    /** The boulder hugs the floor all the way down its corridor. */
    public static final int BOULDER_MODE_ROLL = 0;

    /** The boulder is lobbed in an arc and breaks on the first thing it meets. */
    public static final int BOULDER_MODE_THROW = 1;

    public static final String[] BOULDER_MODE_LABELS = {
            "cnpcgeckoaddon.boss.boulder_mode_roll",
            "cnpcgeckoaddon.boss.boulder_mode_throw"
    };

    /** Tied to the boss: the way out is away from it. */
    public static final int TETHER_ANCHOR_BOSS = 0;

    /** Tied to the ground the victim was standing on when the cast landed. */
    public static final int TETHER_ANCHOR_SPOT = 1;

    /** Tied to another victim, so the two have to run apart. An odd one out goes to the boss. */
    public static final int TETHER_ANCHOR_PAIR = 2;

    public static final String[] TETHER_ANCHOR_LABELS = {
            "cnpcgeckoaddon.boss.tether_anchor.boss",
            "cnpcgeckoaddon.boss.tether_anchor.spot",
            "cnpcgeckoaddon.boss.tether_anchor.pair"
    };

    /** Everyone in the field is dragged toward the boss, and hurt while they are up against it. */
    public static final int GRAVITY_MODE_PULL = 0;

    /** Everyone in the field is shoved away from the boss; nothing hurts. */
    public static final int GRAVITY_MODE_PUSH = 1;

    /** One throw straight up, and the landing hurts on top of the fall itself. */
    public static final int GRAVITY_MODE_LIFT = 2;

    public static final String[] GRAVITY_MODE_LABELS = {
            "cnpcgeckoaddon.boss.gravity_mode.pull",
            "cnpcgeckoaddon.boss.gravity_mode.push",
            "cnpcgeckoaddon.boss.gravity_mode.lift"
    };

    /** Enough of the party has to be standing in the circle, and the hit is split between them. */
    public static final int MARK_MODE_SOAK = 0;

    /** Nobody else may be standing in it, and everyone who is takes the hit in full. */
    public static final int MARK_MODE_SPREAD = 1;

    public static final String[] MARK_MODE_LABELS = {
            "cnpcgeckoaddon.boss.mark_mode.soak",
            "cnpcgeckoaddon.boss.mark_mode.spread"
    };

    /** Whoever the boss can no longer see - a solid block between its eyes and them - is spared. */
    public static final int COVER_MODE_SIGHT = 0;

    /** Whoever is standing inside one of the shelters the wind-up drew on the floor is spared. */
    public static final int COVER_MODE_SHELTER = 1;

    public static final String[] COVER_MODE_LABELS = {
            "cnpcgeckoaddon.boss.cover_mode.los",
            "cnpcgeckoaddon.boss.cover_mode.shelter"
    };

    /** A safe circle that closes in over the phase; everything outside it burns. */
    public static final int HAZARD_MODE_RING = 0;

    /** One box on the arena that turns dangerous after the delay; everything inside it burns. */
    public static final int HAZARD_MODE_BOX = 1;

    public static final String[] HAZARD_MODE_LABELS = {
            "cnpcgeckoaddon.boss.hazard_mode.ring",
            "cnpcgeckoaddon.boss.hazard_mode.box"
    };

    /** The ring closes in on wherever the boss stood when the phase began. */
    public static final int HAZARD_CENTER_BOSS = 0;

    /** The ring closes in on one spot in the world, wherever the boss went. */
    public static final int HAZARD_CENTER_POINT = 1;

    public static final String[] HAZARD_CENTER_LABELS = {
            "cnpcgeckoaddon.boss.hazard_center.boss",
            "cnpcgeckoaddon.boss.hazard_center.point"
    };

    /** Hazard coordinates share the world limit the aggro zone's corners use. */
    public static final int MAX_HAZARD_COORDINATE = 30000000;

    public static final int CAPTURE_MODE_HOLD = 0;

    public static final int CAPTURE_MODE_LIFT = 1;

    public static final int CAPTURE_EFFECT_PLAYER = 0;

    public static final int CAPTURE_EFFECT_BOSS = 1;

    public static final int CAPTURE_EFFECT_BOTH = 2;

    /** The immune phase runs for its full duration and nothing else ends it. */
    public static final int INVULNERABLE_END_TIMER = 0;

    /** The immune phase lasts until every minion it summoned is dead. */
    public static final int INVULNERABLE_END_MINIONS_DEAD = 1;

    /** Whichever of the two comes first ends the immune phase. */
    public static final int INVULNERABLE_END_TIMER_OR_MINIONS = 2;

    /** The immune phase only ends once both are satisfied. */
    public static final int INVULNERABLE_END_TIMER_AND_MINIONS = 3;

    public static final String[] INVULNERABLE_END_LABELS = {
            "cnpcgeckoaddon.boss.invulnerable_end_timer",
            "cnpcgeckoaddon.boss.invulnerable_end_minions",
            "cnpcgeckoaddon.boss.invulnerable_end_either",
            "cnpcgeckoaddon.boss.invulnerable_end_both"
    };

    /** The barrier goes up once, as the boss steps into the phase. */
    public static final int BARRIER_TRIGGER_ENTER = 0;

    /** The barrier goes up as the phase begins and again a set time after each outcome. */
    public static final int BARRIER_TRIGGER_TIMER = 1;

    public static final String[] BARRIER_TRIGGER_LABELS = {
            "cnpcgeckoaddon.boss.barrier_trigger.enter",
            "cnpcgeckoaddon.boss.barrier_trigger.timer"
    };

    /** A barrier nobody broke in time hits everyone in the fight. */
    public static final int BARRIER_FAIL_DAMAGE = 0;

    /** ...heals the boss by a share of its health. */
    public static final int BARRIER_FAIL_HEAL = 1;

    /** ...sets the boss' own enrage off early, or hits everyone when there is no enrage to set off. */
    public static final int BARRIER_FAIL_RAGE = 2;

    public static final String[] BARRIER_FAIL_LABELS = {
            "cnpcgeckoaddon.boss.barrier_fail.damage",
            "cnpcgeckoaddon.boss.barrier_fail.heal",
            "cnpcgeckoaddon.boss.barrier_fail.rage"
    };

    /** The first beam leaves along the boss' own gaze; the rest are spaced evenly after it. */
    public static final int BEAM_START_FACING = 0;

    /** The first beam leaves at a random angle. */
    public static final int BEAM_START_RANDOM = 1;

    public static final String[] BEAM_START_LABELS = {
            "cnpcgeckoaddon.boss.beam_start.facing",
            "cnpcgeckoaddon.boss.beam_start.random"
    };

    /** The cocoon has to be killed: the clone's own health is the lock. */
    public static final int COCOON_RESCUE_KILL = 0;

    /** Rescuers have to stand beside it for long enough between them: tearing it open by hand. */
    public static final int COCOON_RESCUE_STAND = 1;

    public static final String[] COCOON_RESCUE_LABELS = {
            "cnpcgeckoaddon.boss.cocoon_rescue.kill",
            "cnpcgeckoaddon.boss.cocoon_rescue.stand"
    };

    /** The dash runs at whoever it picked. */
    public static final int DASH_DIRECTION_TARGET = 0;

    /** The dash runs the way the boss is facing, whoever happens to be in the way. */
    public static final int DASH_DIRECTION_FACING = 1;

    public static final String[] DASH_DIRECTION_LABELS = {
            "cnpcgeckoaddon.boss.dash_direction.target",
            "cnpcgeckoaddon.boss.dash_direction.facing"
    };

    /** A dash that meets a wall instead of a victim leaves the boss stunned and taking more. */
    public static final int DASH_WALL_STUN = 0;

    /** ...has the boss slam the ground round itself. */
    public static final int DASH_WALL_SLAM = 1;

    /** ...sets the boss' own enrage off, or stuns it when there is no enrage left to set off. */
    public static final int DASH_WALL_RAGE = 2;

    public static final String[] DASH_WALL_LABELS = {
            "cnpcgeckoaddon.boss.dash_wall.stun",
            "cnpcgeckoaddon.boss.dash_wall.slam",
            "cnpcgeckoaddon.boss.dash_wall.rage"
    };

    /** The cone opens toward whoever it picked. */
    public static final int CONE_AIM_TARGET = 0;

    /** The cone opens the way the boss is facing, whoever happens to be standing in it. */
    public static final int CONE_AIM_FACING = 1;

    /** One cone toward each of the builder's points, one after another. */
    public static final int CONE_AIM_POINTS = 2;

    public static final String[] CONE_AIM_LABELS = {
            "cnpcgeckoaddon.boss.cone_aim.target",
            "cnpcgeckoaddon.boss.cone_aim.facing",
            "cnpcgeckoaddon.boss.cone_aim.points"
    };

    /** Whoever the cone catches is knocked away from the boss. */
    public static final int CONE_IMPULSE_PUSH = 0;

    /** ...thrown straight up, the way a geyser throws. */
    public static final int CONE_IMPULSE_LIFT = 1;

    /** ...pulled in toward the boss. */
    public static final int CONE_IMPULSE_PULL = 2;

    public static final String[] CONE_IMPULSE_LABELS = {
            "cnpcgeckoaddon.boss.cone_impulse.push",
            "cnpcgeckoaddon.boss.cone_impulse.lift",
            "cnpcgeckoaddon.boss.cone_impulse.pull"
    };

    /** A series takes its points in the order the list holds them. */
    public static final int CONE_ORDER_LIST = 0;

    /** A series takes its points in a fresh random order every cast. */
    public static final int CONE_ORDER_RANDOM = 1;

    public static final String[] CONE_ORDER_LABELS = {
            "cnpcgeckoaddon.boss.cone_order.list",
            "cnpcgeckoaddon.boss.cone_order.random"
    };

    /** The warning's end never calls a cone off: the sector it promised is the sector it lands, the line strike's rule. */
    public static final int CONE_DODGE_NEVER = 0;

    /** ...calls it off when nobody it may hit is left in the sector it promised. */
    public static final int CONE_DODGE_SECTOR = 1;

    /** ...calls it off when its target got further away than the cone reaches, sector or not: the rule the cone started with. */
    public static final int CONE_DODGE_RANGE = 2;

    public static final String[] CONE_DODGE_LABELS = {
            "cnpcgeckoaddon.boss.cone_dodge.never",
            "cnpcgeckoaddon.boss.cone_dodge.sector",
            "cnpcgeckoaddon.boss.cone_dodge.range"
    };

    /** The cone's cooldown runs from the wind-up, however long the series after it: the rule the cone started with. */
    public static final int CONE_COOLDOWN_FROM_START = 0;

    /** The cone's cooldown runs from the last cone of the cast. */
    public static final int CONE_COOLDOWN_FROM_END = 1;

    public static final String[] CONE_COOLDOWN_FROM_LABELS = {
            "cnpcgeckoaddon.boss.cone_cooldown_from.start",
            "cnpcgeckoaddon.boss.cone_cooldown_from.end"
    };

    /** One storm from the boss along the committed axis. */
    public static final int HURRICANE_MODE_STRAIGHT = 0;
    /** Four storms out along the world's axes. */
    public static final int HURRICANE_MODE_CROSS = 1;
    /** Four storms out along the diagonals. */
    public static final int HURRICANE_MODE_DIAGONAL = 2;
    /** Storms winding outward round the boss. */
    public static final int HURRICANE_MODE_SPIRAL = 3;
    /** Storms wandering between random points round the boss. */
    public static final int HURRICANE_MODE_TYPHOON = 4;

    public static final String[] HURRICANE_MODE_LABELS = {
            "cnpcgeckoaddon.boss.hurricane_mode.straight",
            "cnpcgeckoaddon.boss.hurricane_mode.cross",
            "cnpcgeckoaddon.boss.hurricane_mode.diagonal",
            "cnpcgeckoaddon.boss.hurricane_mode.spiral",
            "cnpcgeckoaddon.boss.hurricane_mode.typhoon"
    };

    /** The straight storm goes at whoever the cast picked. */
    public static final int HURRICANE_AIM_TARGET = 0;
    /** The straight storm goes the way the boss is facing, whoever happens to be in the way. */
    public static final int HURRICANE_AIM_FACING = 1;

    public static final String[] HURRICANE_AIM_LABELS = {
            "cnpcgeckoaddon.boss.hurricane_aim.target",
            "cnpcgeckoaddon.boss.hurricane_aim.facing"
    };

    /** One platform goes, drawn by the platforms' weights. */
    public static final int PLATFORM_PICK_RANDOM = 0;

    /** The platform the boss' target stands on goes, or a random one when it stands on none. */
    public static final int PLATFORM_PICK_TARGET = 1;

    /** The platforms go one after another, in list order. */
    public static final int PLATFORM_PICK_CYCLE = 2;

    /** Every platform goes but one, and that one is the only safe place to be. */
    public static final int PLATFORM_PICK_ALL_BUT_ONE = 3;

    public static final String[] PLATFORM_PICK_LABELS = {
            "cnpcgeckoaddon.boss.platform_pick.random",
            "cnpcgeckoaddon.boss.platform_pick.target",
            "cnpcgeckoaddon.boss.platform_pick.cycle",
            "cnpcgeckoaddon.boss.platform_pick.all_but_one"
    };

    /**
     * The abilities whose wind-up can pin a walking boss to the spot it started on, in
     * {@link BossAbilityKind} order. One mask rather than a boolean per ability: there are
     * already ten of them, and each new one would otherwise drag a field, a getter and a
     * GUI row behind it.
     *
     * <p>The movers are deliberately absent. A leap roots its crouch unconditionally and
     * flies free from the push, a teleport is never held - moving away is the whole
     * ability - and the death blast goes off with nobody left standing to hold. The dash is
     * the one mover listed: its bit only holds the wind-up, and the run lets go of the pin
     * on its own the moment it starts, the way the leap's push does. The cone strike's bit
     * likewise only holds the wind-up: a series over its points stands still whatever it says.
     * So does the platforms' bit: their fuses burn on wherever the boss walks off to, and the
     * hurricane's: its storms travel on their own once let go. And the shadow copies': the
     * copies fight on their own feet once they stand.</p>
     */
    public static final int[] CAST_ROOT_ABILITIES = {
            BossAbilityKind.AREA, BossAbilityKind.RANGED, BossAbilityKind.MELEE,
            BossAbilityKind.FLUID, BossAbilityKind.HOOK, BossAbilityKind.CAPTURE,
            BossAbilityKind.SUMMON, BossAbilityKind.LINE, BossAbilityKind.GEYSER,
            BossAbilityKind.BOULDER, BossAbilityKind.BOULDER_RAIN, BossAbilityKind.TETHER,
            BossAbilityKind.GRAVITY, BossAbilityKind.MARK, BossAbilityKind.COVER,
            BossAbilityKind.HUNT, BossAbilityKind.BEAM, BossAbilityKind.COCOON,
            BossAbilityKind.DASH, BossAbilityKind.CONE, BossAbilityKind.PLATFORM,
            BossAbilityKind.HURRICANE, BossAbilityKind.SHADOW
    };

    /**
     * Every ability is cast standing still until a builder frees it, existing bosses
     * included: a warning zone that travels with a running boss lies about where the hit
     * lands, and a line strike fires into a corridor the boss has already left.
     */
    public static final int CAST_ROOT_ALL = castRootAllMask();

    /** A chain slot with nothing in it: the ability ends and the rotation carries on. */
    public static final int NO_COMBO = -1;

    /** The longest wait between an ability ending and its follow-up starting: a minute. */
    public static final int MAX_COMBO_DELAY = 1200;

    /** The longest hold after an ability the phase sees through: a minute, like the chain delay. */
    public static final int MAX_FINISH_HOLD = 1200;

    /** Health percentage at which this phase takes over. Phase 1 is pinned to 100. */
    private int startHealthPercent = 100;
    private String appearanceAnimation = "";
    private int appearanceLockTicks = 20;
    /** Which abilities this phase casts standing still, one bit per {@link BossAbilityKind}. */
    private int castRootMask = CAST_ROOT_ALL;
    /**
     * Which abilities this phase sees through before it starts anything else - their effect,
     * and the hold after it - one bit per {@link BossAbilityKind}. Off by default: the boss
     * only ever waited out its wind-ups.
     */
    private int finishMask;
    /**
     * How many ticks after each marked ability has done its work the boss goes on finishing
     * it, one slot per {@link BossAbilityKind}: counted from the cast for an instant ability,
     * from the end of the effect for a lasting one. Nothing by default: a marked lasting
     * ability waits for its effect alone, as it did before the hold existed.
     */
    private final int[] finishHoldTicks = new int[BossAbilityKind.COUNT];
    /**
     * The ability each ability hands straight on to when it ends, one slot per
     * {@link BossAbilityKind}, or {@link #NO_COMBO}. Empty by default: the rotation alone
     * decided what came next.
     */
    private final int[] comboFollowUp = newComboFollowUps();
    /** How many ticks after its ability ends each slot's follow-up starts. */
    private final int[] comboDelay = new int[BossAbilityKind.COUNT];

    private final BossAreaAttackSettings areaAttack = new BossAreaAttackSettings();
    private final BossBarrierSettings barrier = new BossBarrierSettings();
    private final BossBeamSettings beam = new BossBeamSettings();
    private final BossBoulderSettings boulder = new BossBoulderSettings();
    private final BossBoulderRainSettings boulderRain = new BossBoulderRainSettings();
    private final BossCaptureSettings capture = new BossCaptureSettings();
    private final BossCocoonSettings cocoon = new BossCocoonSettings();
    private final BossConeSettings cone = new BossConeSettings();
    private final BossCoverSettings cover = new BossCoverSettings();
    private final BossDashSettings dash = new BossDashSettings();
    private final BossFluidSpitSettings fluidSpit = new BossFluidSpitSettings();
    private final BossGeyserSettings geyser = new BossGeyserSettings();
    private final BossGravitySettings gravity = new BossGravitySettings();
    private final BossHazardSettings hazard = new BossHazardSettings();
    private final BossHookSettings hook = new BossHookSettings();
    private final BossHuntSettings hunt = new BossHuntSettings();
    private final BossHurricaneSettings hurricane = new BossHurricaneSettings();
    private final BossInvulnerableSettings invulnerable = new BossInvulnerableSettings();
    private final BossLeapSettings leap = new BossLeapSettings();
    private final BossLineAttackSettings lineAttack = new BossLineAttackSettings();
    private final BossMarkSettings mark = new BossMarkSettings();
    private final BossMeleeAttackSettings meleeAttack = new BossMeleeAttackSettings();
    private final BossPlatformSettings platform = new BossPlatformSettings();
    private final BossRangedAttackSettings rangedAttack = new BossRangedAttackSettings();
    private final BossShadowSettings shadow = new BossShadowSettings();
    private final BossSummonSettings summon = new BossSummonSettings();
    private final BossTeleportSettings teleport = new BossTeleportSettings();
    private final BossTetherSettings tether = new BossTetherSettings();

    /** The hit that goes off all round the boss, and the wave of floor it lifts. */
    public BossAreaAttackSettings areaAttack() {
        return areaAttack;
    }

    /** The shield with a timer, and what the arena owes when it is not broken in time. */
    public BossBarrierSettings barrier() {
        return barrier;
    }

    /** The lines swept round the boss after the cast. */
    public BossBeamSettings beam() {
        return beam;
    }

    /** The stone rolled, thrown or dropped at one victim. */
    public BossBoulderSettings boulder() {
        return boulder;
    }

    /** The ring of stones dropped out of the sky. */
    public BossBoulderRainSettings boulderRain() {
        return boulderRain;
    }

    /** The grab, and the beam it holds its victim in. */
    public BossCaptureSettings capture() {
        return capture;
    }

    /** The shell closed round a victim, and the guard posted beside it. */
    public BossCocoonSettings cocoon() {
        return cocoon;
    }

    /** The fan of a hit toward a victim, along the gaze or over the builder's points. */
    public BossConeSettings cone() {
        return cone;
    }

    /** The hit on the whole arena that spares only whoever got out of sight. */
    public BossCoverSettings cover() {
        return cover;
    }

    /** The charge down a committed line, and what a wall at the end of it does to the boss. */
    public BossDashSettings dash() {
        return dash;
    }

    /** The lobbed ball of fluid and the puddle it leaves. */
    public BossFluidSpitSettings fluidSpit() {
        return fluidSpit;
    }

    /** The fuse under a victim and the column that follows it. */
    public BossGeyserSettings geyser() {
        return geyser;
    }

    /** The field that pulls, pushes or throws everything around the boss. */
    public BossGravitySettings gravity() {
        return gravity;
    }

    /** The arena itself turning dangerous for a phase. */
    public BossHazardSettings hazard() {
        return hazard;
    }

    /** The chain the boss throws, and the drag that keeps hold of whoever it caught. */
    public BossHookSettings hook() {
        return hook;
    }

    /** The chase: one victim, and nobody else until it is over. */
    public BossHuntSettings hunt() {
        return hunt;
    }

    /** The window a phase cannot be hurt in, and what closes it. */
    public BossInvulnerableSettings invulnerable() {
        return invulnerable;
    }

    /** The jump, its flight and the slam it lands with. */
    public BossLeapSettings leap() {
        return leap;
    }

    /** The corridor struck straight out in front of the boss, and its flank waves. */
    public BossLineAttackSettings lineAttack() {
        return lineAttack;
    }

    /** The circle handed to a victim that goes off wherever they take it. */
    public BossMarkSettings mark() {
        return mark;
    }

    /** The swing the boss makes at whoever is in reach. */
    public BossMeleeAttackSettings meleeAttack() {
        return meleeAttack;
    }

    /** The builder's platforms, and the one the boss sets alight under whoever did not jump off. */
    public BossPlatformSettings platform() {
        return platform;
    }

    /** The projectile the boss throws. */
    public BossRangedAttackSettings rangedAttack() {
        return rangedAttack;
    }

    /** The storms let loose on the floor, and what they do to whoever they run into. */
    public BossHurricaneSettings hurricane() {
        return hurricane;
    }

    /** The copies of the boss itself, what they cast, and how they end. */
    public BossShadowSettings shadow() {
        return shadow;
    }

    /** The clones the boss calls for, and where it puts them. */
    public BossSummonSettings summon() {
        return summon;
    }

    /**
     * Switches one ability of this phase on or off by its number, for the copy of a phase a
     * shadow is handed: the mask the builder picked for the copies is applied bit by bit
     * through here, so a kind the mask does not know is simply left as it was.
     *
     * <p>The death blast has no switch on a phase - it is the boss' own explosion, kept on
     * the boss - so its bit changes nothing here.</p>
     */
    public void setAbilityEnabled(int kind, boolean value) {
        switch (kind) {
            case BossAbilityKind.AREA -> areaAttack.setEnabled(value);
            case BossAbilityKind.RANGED -> rangedAttack.setEnabled(value);
            case BossAbilityKind.MELEE -> meleeAttack.setEnabled(value);
            case BossAbilityKind.FLUID -> fluidSpit.setEnabled(value);
            case BossAbilityKind.HOOK -> hook.setEnabled(value);
            case BossAbilityKind.CAPTURE -> capture.setEnabled(value);
            case BossAbilityKind.SUMMON -> summon.setEnabled(value);
            case BossAbilityKind.LEAP -> leap.setEnabled(value);
            case BossAbilityKind.LINE -> lineAttack.setEnabled(value);
            case BossAbilityKind.GEYSER -> geyser.setEnabled(value);
            case BossAbilityKind.BOULDER -> boulder.setEnabled(value);
            case BossAbilityKind.BOULDER_RAIN -> boulderRain.setEnabled(value);
            case BossAbilityKind.TETHER -> tether.setEnabled(value);
            case BossAbilityKind.GRAVITY -> gravity.setEnabled(value);
            case BossAbilityKind.MARK -> mark.setEnabled(value);
            case BossAbilityKind.COVER -> cover.setEnabled(value);
            case BossAbilityKind.HAZARD -> hazard.setEnabled(value);
            case BossAbilityKind.HUNT -> hunt.setEnabled(value);
            case BossAbilityKind.BEAM -> beam.setEnabled(value);
            case BossAbilityKind.COCOON -> cocoon.setEnabled(value);
            case BossAbilityKind.DASH -> dash.setEnabled(value);
            case BossAbilityKind.CONE -> cone.setEnabled(value);
            case BossAbilityKind.PLATFORM -> platform.setEnabled(value);
            case BossAbilityKind.HURRICANE -> hurricane.setEnabled(value);
            case BossAbilityKind.SHADOW -> shadow.setEnabled(value);
            default -> {
            }
        }
    }

    /** The hop along the path, and how long the boss winds up for it. */
    public BossTeleportSettings teleport() {
        return teleport;
    }

    /** The leash tied to the boss, to a spot or between two victims. */
    public BossTetherSettings tether() {
        return tether;
    }

    private static int castRootAllMask() {
        int mask = 0;
        for (int ability : CAST_ROOT_ABILITIES) {
            mask |= 1 << ability;
        }
        return mask;
    }

    public int getStartHealthPercent() { return startHealthPercent; }

    public void setStartHealthPercent(int value) { startHealthPercent = Mth.clamp(value, 1, 100); }

    public String getAppearanceAnimation() { return appearanceAnimation; }

    public void setAppearanceAnimation(String value) { appearanceAnimation = clean(value); }

    public int getAppearanceLockTicks() { return appearanceLockTicks; }

    public void setAppearanceLockTicks(int value) { appearanceLockTicks = Mth.clamp(value, 0, 1200); }

    /** Whether this ability's wind-up holds a walking boss on the spot it began on. */
    public boolean isCastRooted(int ability) {
        return isCastRootable(ability) && (castRootMask & 1 << ability) != 0;
    }

    public void setCastRooted(int ability, boolean value) {
        if (!isCastRootable(ability)) {
            return;
        }
        castRootMask = value ? castRootMask | 1 << ability : castRootMask & ~(1 << ability);
    }

    /** Whether this ability has a bit in the mask at all; the movers and the blast have none. */
    private static boolean isCastRootable(int ability) {
        return ability >= 0 && ability < Integer.SIZE && (CAST_ROOT_ALL & 1 << ability) != 0;
    }

    /**
     * Whether the boss lets this ability run out - its effect, and the hold after it - before
     * it starts anything else: another ability, a hop along its path, a walk to a cast spot.
     */
    public boolean waitsForFinish(int ability) {
        return isFinishAbility(ability) && (finishMask & 1 << ability) != 0;
    }

    public void setWaitsForFinish(int ability, boolean value) {
        if (!isFinishAbility(ability)) {
            return;
        }
        finishMask = value ? finishMask | 1 << ability : finishMask & ~(1 << ability);
    }

    /** Whether this ability has a bit in the finish mask: only one the rotation casts. */
    private static boolean isFinishAbility(int ability) {
        return ability >= 0 && ability < Integer.SIZE && (BossAbilityKind.FINISH_ALL & 1 << ability) != 0;
    }

    /**
     * Ticks the boss goes on finishing this ability after it has done its work, starting
     * nothing else. Read only while the ability is marked: an unmarked ability's number is
     * kept for when it is marked again, and means nothing until then.
     */
    public int finishHoldTicks(int ability) {
        return isFinishAbility(ability) ? finishHoldTicks[ability] : 0;
    }

    public void setFinishHoldTicks(int ability, int ticks) {
        if (isFinishAbility(ability)) {
            finishHoldTicks[ability] = Mth.clamp(ticks, 0, MAX_FINISH_HOLD);
        }
    }

    /** The ability this one hands straight on to when it ends, or {@link #NO_COMBO}. */
    public int comboFollowUp(int ability) {
        return isComboAbility(ability) ? comboFollowUp[ability] : NO_COMBO;
    }

    /**
     * Chains {@code next} onto this ability, or empties the slot with {@link #NO_COMBO}.
     *
     * <p>Anything the rotation does not cast empties it too, and so does the ability itself:
     * a slot pointing at nothing that can start is a chain no screen can show.</p>
     */
    public void setComboFollowUp(int ability, int next) {
        if (isComboAbility(ability)) {
            comboFollowUp[ability] = validFollowUp(ability, next);
        }
    }

    /** Ticks between this ability ending and its follow-up starting. */
    public int comboDelay(int ability) {
        return isComboAbility(ability) ? comboDelay[ability] : 0;
    }

    public void setComboDelay(int ability, int ticks) {
        if (isComboAbility(ability)) {
            comboDelay[ability] = Mth.clamp(ticks, 0, MAX_COMBO_DELAY);
        }
    }

    /**
     * Whether some ability of this phase hands on to this one. A follow-up starts with its
     * switch off, so whatever lets its effect go once the switch is off has to ask this too.
     */
    public boolean isComboFollowUp(int ability) {
        if (!isComboAbility(ability)) {
            return false;
        }
        for (int next : comboFollowUp) {
            if (next == ability) {
                return true;
            }
        }
        return false;
    }

    /** Whether this ability owns a chain slot: only one the rotation casts. */
    private static boolean isComboAbility(int ability) {
        return ability >= 0 && ability < BossAbilityKind.COUNT && (BossAbilityKind.COMBO_ALL & 1 << ability) != 0;
    }

    private static int validFollowUp(int ability, int next) {
        return next != ability && isComboAbility(next) ? next : NO_COMBO;
    }

    private static int[] newComboFollowUps() {
        int[] slots = new int[BossAbilityKind.COUNT];
        Arrays.fill(slots, NO_COMBO);
        return slots;
    }

    /**
     * Whether dead minions are part of this phase's exit condition.
     *
     * <p>A phase with no clone configured can never satisfy "all minions are dead", so the
     * minion half of the condition is dropped there - otherwise the boss would stay immune
     * for the rest of the fight.</p>
     */
    public boolean invulnerableWaitsForMinions() {
        return summon.canSummon() && invulnerable.getEndMode() != INVULNERABLE_END_TIMER;
    }

    /** Counterpart of {@link #invulnerableWaitsForMinions()}; the two are never both false. */
    public boolean invulnerableWaitsForTimer() {
        return invulnerable.getEndMode() != INVULNERABLE_END_MINIONS_DEAD || !summon.canSummon();
    }

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("StartHealthPercent", startHealthPercent);
        tag.putString("AppearanceAnimation", appearanceAnimation);
        tag.putInt("AppearanceLockTicks", appearanceLockTicks);
        tag.putInt("CastRootMask", castRootMask);
        tag.putInt("FinishMask", finishMask);
        // Copied: an int array tag keeps the very array it is handed, and these go on being edited.
        tag.putIntArray("ComboFollowUp", Arrays.copyOf(comboFollowUp, comboFollowUp.length));
        tag.putIntArray("ComboDelay", Arrays.copyOf(comboDelay, comboDelay.length));
        tag.putIntArray("FinishHold", Arrays.copyOf(finishHoldTicks, finishHoldTicks.length));
        areaAttack.writeToNBT(tag);
        barrier.writeToNBT(tag);
        beam.writeToNBT(tag);
        boulder.writeToNBT(tag);
        boulderRain.writeToNBT(tag);
        capture.writeToNBT(tag);
        cocoon.writeToNBT(tag);
        cone.writeToNBT(tag);
        cover.writeToNBT(tag);
        dash.writeToNBT(tag);
        fluidSpit.writeToNBT(tag);
        geyser.writeToNBT(tag);
        gravity.writeToNBT(tag);
        hazard.writeToNBT(tag);
        hook.writeToNBT(tag);
        hunt.writeToNBT(tag);
        hurricane.writeToNBT(tag);
        invulnerable.writeToNBT(tag);
        leap.writeToNBT(tag);
        lineAttack.writeToNBT(tag);
        mark.writeToNBT(tag);
        meleeAttack.writeToNBT(tag);
        platform.writeToNBT(tag);
        rangedAttack.writeToNBT(tag);
        shadow.writeToNBT(tag);
        summon.writeToNBT(tag);
        teleport.writeToNBT(tag);
        tether.writeToNBT(tag);
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        startHealthPercent = value(tag, "StartHealthPercent", 100, 1, 100);
        appearanceAnimation = clean(tag.getString("AppearanceAnimation"));
        appearanceLockTicks = value(tag, "AppearanceLockTicks", 20, 0, 1200);
        // An absent key is a boss saved before the choice existed. It gets the rooted
        // default on purpose: its warnings were lying whenever it cast on the run.
        castRootMask = tag.contains("CastRootMask")
                ? tag.getInt("CastRootMask") & CAST_ROOT_ALL : CAST_ROOT_ALL;
        // A save from before the boulder existed never chose to let it walk, so the new bit
        // gets the same rooted default the whole mask got when the choice first appeared.
        // Saves that know the boulder always carry its enabled key.
        if (!tag.contains("BoulderEnabled")) {
            castRootMask |= 1 << BossAbilityKind.BOULDER;
        }
        // The same again for the boulder rain, whose bit is newer still.
        if (!tag.contains("BoulderRainEnabled")) {
            castRootMask |= 1 << BossAbilityKind.BOULDER_RAIN;
        }
        // And for the tether, and then the gravity field, each newer than the last.
        if (!tag.contains("TetherEnabled")) {
            castRootMask |= 1 << BossAbilityKind.TETHER;
        }
        if (!tag.contains("GravityEnabled")) {
            castRootMask |= 1 << BossAbilityKind.GRAVITY;
        }
        // And for the marks, and then the take cover strike, newest of the lot.
        if (!tag.contains("MarkEnabled")) {
            castRootMask |= 1 << BossAbilityKind.MARK;
        }
        if (!tag.contains("CoverEnabled")) {
            castRootMask |= 1 << BossAbilityKind.COVER;
        }
        // And for the hunt, whose bit only pins the wind-up: the chase after it walks anyway.
        if (!tag.contains("HuntEnabled")) {
            castRootMask |= 1 << BossAbilityKind.HUNT;
        }
        // And for the beam, whose bit likewise only pins the wind-up: the sweep after it
        // turns round the boss wherever it walks.
        if (!tag.contains("BeamEnabled")) {
            castRootMask |= 1 << BossAbilityKind.BEAM;
        }
        // And for the cocoon, whose bit likewise only pins the wind-up: the lock after it
        // stands wherever its victims stood, whatever the boss does next.
        if (!tag.contains("CocoonEnabled")) {
            castRootMask |= 1 << BossAbilityKind.COCOON;
        }
        // And for the dash, whose bit only pins the wind-up: the run lets go of the pin itself.
        if (!tag.contains("DashEnabled")) {
            castRootMask |= 1 << BossAbilityKind.DASH;
        }
        // And for the cone strike, whose bit likewise only pins the wind-up.
        if (!tag.contains("ConeEnabled")) {
            castRootMask |= 1 << BossAbilityKind.CONE;
        }
        // And for the platforms, whose bit only pins the wind-up too: the fuse burns on its own.
        if (!tag.contains("PlatformEnabled")) {
            castRootMask |= 1 << BossAbilityKind.PLATFORM;
        }
        // And for the hurricane, whose bit only pins the wind-up: the storms travel on their own.
        if (!tag.contains("HurricaneEnabled")) {
            castRootMask |= 1 << BossAbilityKind.HURRICANE;
        }
        // And for the shadow copies, whose bit only pins the wind-up: the copies fight on their own.
        if (!tag.contains("ShadowEnabled")) {
            castRootMask |= 1 << BossAbilityKind.SHADOW;
        }
        // Unlike the root, an absent key reads as nothing marked: a boss saved before the choice
        // existed never waited for an effect to end, and must not start freezing mid fight. A
        // save from when only the lasting abilities could be marked holds zeros in every other
        // bit by construction, so it reads back exactly as it was.
        finishMask = tag.getInt("FinishMask") & BossAbilityKind.FINISH_ALL;
        // A tag from before the chains has neither array and reads as no chains at all. A saved
        // array is laid over the slots rather than trusted: one from a build that knew fewer
        // abilities is short, one from a newer build long, and either may point anywhere.
        int[] followUps = tag.getIntArray("ComboFollowUp");
        int[] delays = tag.getIntArray("ComboDelay");
        for (int ability = 0; ability < BossAbilityKind.COUNT; ability++) {
            boolean owned = isComboAbility(ability);
            comboFollowUp[ability] = owned && ability < followUps.length
                    ? validFollowUp(ability, followUps[ability]) : NO_COMBO;
            comboDelay[ability] = owned && ability < delays.length
                    ? Mth.clamp(delays[ability], 0, MAX_COMBO_DELAY) : 0;
        }
        // The holds the same way: a tag from before they existed has no array and holds
        // nothing, which is the wait every marked ability kept until then.
        int[] holds = tag.getIntArray("FinishHold");
        for (int ability = 0; ability < BossAbilityKind.COUNT; ability++) {
            finishHoldTicks[ability] = isFinishAbility(ability) && ability < holds.length
                    ? Mth.clamp(holds[ability], 0, MAX_FINISH_HOLD) : 0;
        }
        areaAttack.readFromNBT(tag);
        barrier.readFromNBT(tag);
        beam.readFromNBT(tag);
        boulder.readFromNBT(tag);
        boulderRain.readFromNBT(tag);
        capture.readFromNBT(tag);
        cocoon.readFromNBT(tag);
        cone.readFromNBT(tag);
        cover.readFromNBT(tag);
        dash.readFromNBT(tag);
        fluidSpit.readFromNBT(tag);
        geyser.readFromNBT(tag);
        gravity.readFromNBT(tag);
        hazard.readFromNBT(tag);
        hook.readFromNBT(tag);
        hunt.readFromNBT(tag);
        hurricane.readFromNBT(tag);
        invulnerable.readFromNBT(tag);
        leap.readFromNBT(tag);
        lineAttack.readFromNBT(tag);
        mark.readFromNBT(tag);
        meleeAttack.readFromNBT(tag);
        platform.readFromNBT(tag);
        rangedAttack.readFromNBT(tag);
        shadow.readFromNBT(tag);
        summon.readFromNBT(tag);
        teleport.readFromNBT(tag);
        tether.readFromNBT(tag);
    }
}
