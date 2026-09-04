package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** Ability and animation settings for one health phase of a stationary boss. */
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

    /**
     * The abilities whose wind-up can pin a walking boss to the spot it started on, in
     * {@link BossAbilityKind} order. One mask rather than a boolean per ability: there are
     * already ten of them, and each new one would otherwise drag a field, a getter and a
     * GUI row behind it.
     *
     * <p>The movers are deliberately absent. A leap roots its crouch unconditionally and
     * flies free from the push, a teleport is never held - moving away is the whole
     * ability - and the death blast goes off with nobody left standing to hold.</p>
     */
    public static final int[] CAST_ROOT_ABILITIES = {
            BossAbilityKind.AREA, BossAbilityKind.RANGED, BossAbilityKind.MELEE,
            BossAbilityKind.FLUID, BossAbilityKind.HOOK, BossAbilityKind.CAPTURE,
            BossAbilityKind.SUMMON, BossAbilityKind.LINE, BossAbilityKind.GEYSER,
            BossAbilityKind.BOULDER, BossAbilityKind.BOULDER_RAIN, BossAbilityKind.TETHER,
            BossAbilityKind.GRAVITY, BossAbilityKind.MARK, BossAbilityKind.COVER,
            BossAbilityKind.HUNT
    };
    /**
     * Every ability is cast standing still until a builder frees it, existing bosses
     * included: a warning zone that travels with a running boss lies about where the hit
     * lands, and a line strike fires into a corridor the boss has already left.
     */
    public static final int CAST_ROOT_ALL = castRootAllMask();

    private static int castRootAllMask() {
        int mask = 0;
        for (int ability : CAST_ROOT_ABILITIES) {
            mask |= 1 << ability;
        }
        return mask;
    }

    /** Health percentage at which this phase takes over. Phase 1 is pinned to 100. */
    private int startHealthPercent = 100;

    private String teleportPreparationAnimation = "";
    private int teleportPreparationTicks = 20;
    private String appearanceAnimation = "";
    private int appearanceLockTicks = 20;
    private int teleportMinDelayTicks = 60;
    private int teleportMaxDelayTicks = 100;

    private boolean summonEnabled;
    private String summonAnimation = "";
    private int summonActionDelayTicks = 20;
    private int summonCooldownTicks = 400;
    private String minionCloneName = "";
    private int minionCloneTab = 1;
    private int minionCount = 3;
    private int minionRadius = 4;
    private int maxAliveMinions = 6;
    private int minionSpawnMode = MINION_SPAWN_RANDOM_RADIUS;
    private int minionSpawnOrder = MINION_ORDER_LIST;
    private int minionPointSearchRadius;
    private boolean minionReuseOccupiedPoints;
    private final BossMinionSpawnList minionSpawnPoints = new BossMinionSpawnList();

    private boolean areaAttackEnabled;
    private String areaAttackAnimation = "";
    private int areaAttackActionDelayTicks = 12;
    private int areaAttackCooldownTicks = 100;
    private int areaAttackDamage = 8;
    private int areaAttackRadius = 5;
    private int areaAttackKnockback = 1;
    private String areaAttackVfx = AreaVfxStyles.NONE;
    private int areaAttackVfxDurationTicks = 20;
    private boolean areaAttackBlockWave;

    private boolean lineAttackEnabled;
    private String lineAttackAnimation = "";
    private int lineAttackActionDelayTicks = 12;
    private int lineAttackCooldownTicks = 140;
    private int lineAttackDirection = LINE_DIRECTION_TARGET;
    /** Off for animations that must not turn with the strike, such as a full-circle swing. */
    private boolean lineAttackFaceAxis = true;
    private int lineAttackTargetMode = BossTargetMode.MAIN;
    private int lineAttackLength = 9;
    private int lineAttackWidth = 2;
    private int lineAttackHeight = 3;
    private int lineAttackDamage = 10;
    private int lineAttackKnockback = 2;
    /** How far past the corridor the weaker wave reaches; zero leaves the flanks alone. */
    private int lineAttackSideWidth = 2;
    private int lineAttackSidePercent = 50;
    private String lineAttackVfx = AreaVfxStyles.NONE;
    private boolean lineAttackBlockWave;

    private boolean rangedAttackEnabled;
    private String rangedAttackAnimation = "";
    private int rangedAttackActionDelayTicks = 12;
    private int rangedAttackCooldownTicks = 80;
    private int rangedAttackDamage = 6;
    private int rangedAttackMinRange = 4;
    private int rangedAttackMaxRange = 24;
    private int rangedAttackTargetMode = BossTargetMode.MAIN;

    private boolean meleeAttackEnabled;
    private String meleeAttackAnimation = "";
    private int meleeAttackActionDelayTicks = 8;
    private int meleeAttackCooldownTicks = 30;
    private int meleeAttackDamage = 6;
    private int meleeAttackRange = 3;
    private int meleeAttackKnockback = 1;
    private int meleeAttackTargetMode = BossTargetMode.MAIN;

    private boolean fluidSpitEnabled;
    private String fluidSpitAnimation = "";
    private int fluidSpitActionDelayTicks = 12;
    private int fluidSpitCooldownTicks = 120;
    private String fluidSpitBlock = "minecraft:lava";
    private int fluidSpitLifetimeTicks = 60;
    private int fluidSpitRadius = 1;
    private int fluidSpitDamage = 0;
    private int fluidSpitMinRange = 2;
    private int fluidSpitMaxRange = 24;
    private int fluidSpitTargetMode = BossTargetMode.MAIN;

    private boolean hookEnabled;
    private String hookAnimation = "";
    private int hookActionDelayTicks = 10;
    private int hookCooldownTicks = 160;
    private int hookTargetMode = BossTargetMode.FARTHEST;
    private int hookTargetCount = 1;
    private int hookDamage = 4;
    /** Tenths of a block per tick, so 8 pulls at 0.4 blocks/tick. */
    private int hookPullStrength = 8;
    private int hookPullDurationTicks = 20;
    private int hookStopDistance = 2;
    private int hookMinRange = 4;
    private int hookMaxRange = 24;
    private int hookMode = HOOK_MODE_PULL;
    private String hookCordStyle = HookCordStyles.PARTICLES;

    private boolean captureEnabled;
    private String captureAnimation = "";
    private int captureActionDelayTicks = 10;
    private int captureCooldownTicks = 200;
    private int captureTargetMode = BossTargetMode.RANDOM;
    private int captureMinRange;
    private int captureMaxRange = 16;
    private int captureMode = CAPTURE_MODE_HOLD;
    private int captureDurationTicks = 60;
    private int captureLiftHeight = 5;
    private int captureLiftTicks = 40;
    private int captureEffectTarget = CAPTURE_EFFECT_PLAYER;
    private String captureBeamStyle = HookCordStyles.GHOST;
    private int captureBeamWidthPercent = 100;
    private int captureBeamSagPercent;
    private boolean captureAllowLook = true;

    private boolean leapEnabled;
    private String leapAnimation = "";
    private String leapLandAnimation = "";
    private int leapActionDelayTicks = 12;
    private int leapCooldownTicks = 200;
    private int leapMode = LEAP_MODE_UP;
    private int leapTargetMode = BossTargetMode.MAIN;
    private int leapHeight = 8;
    private int leapMinRange = 4;
    private int leapMaxRange = 24;
    private int leapOffsetX;
    private int leapOffsetY;
    private int leapOffsetZ;
    private int leapFixedX;
    private int leapFixedY;
    private int leapFixedZ;
    private int leapImpactDamage = 10;
    private int leapImpactRadius = 4;
    private int leapImpactKnockback = 2;
    /** Ends a leap that never lands, so a jump into a pit cannot strand the boss in mid air. */
    private int leapMaxAirTicks = 100;
    private boolean leapTelegraph = true;
    private String leapVfx = AreaVfxStyles.NONE;
    private boolean leapBlockWave;

    private boolean geyserEnabled;
    private String geyserAnimation = "";
    private int geyserActionDelayTicks = 12;
    private int geyserCooldownTicks = 160;
    private int geyserTargetMode = BossTargetMode.RANDOM;
    private int geyserTargetCount = 1;
    private int geyserMinRange = 3;
    private int geyserMaxRange = 24;
    /** How long the mark sits on the floor before the column comes up through it. */
    private int geyserFuseTicks = 25;
    private int geyserRadius = 3;
    private int geyserDamage = 8;
    /** Tenths of a block per tick, so 8 throws a victim up at 0.8 blocks a tick. */
    private int geyserLaunch = 8;
    private boolean geyserFollowTarget;
    /** Empty leaves nothing behind; anything else is a block id the eruption pools. */
    private String geyserFluid = "";
    private int geyserFluidLifetimeTicks = 60;
    private String geyserVfx = AreaVfxStyles.NONE;
    private boolean geyserBlockWave;

    private boolean boulderEnabled;
    private String boulderAnimation = "";
    private int boulderActionDelayTicks = 16;
    private int boulderCooldownTicks = 180;
    private int boulderMode = BOULDER_MODE_ROLL;
    private int boulderTargetMode = BossTargetMode.MAIN;
    /** Cosmetic only: what the stone is drawn as, never what it does to the arena. */
    private String boulderBlock = "minecraft:stone";
    /** The drawn skin. Default keeps the plain scaled block the boulder started life as. */
    private String boulderStyle = BoulderStyles.BLOCK;
    /** Diameter in tenths of a block, so 15 rolls a 1.5 block stone. */
    private int boulderScale = 15;
    /** Tenths of a block per tick, so 6 travels at 0.6 blocks a tick. */
    private int boulderSpeed = 6;
    private int boulderRange = 20;
    private int boulderDamage = 12;
    private int boulderKnockback = 3;
    /** Off, the boulder rolls through the whole line; on, it breaks on the first victim. */
    private boolean boulderStopsOnHit;
    private int boulderShatterRadius = 2;
    private int boulderShatterDamage = 4;
    private String boulderVfx = AreaVfxStyles.NONE;

    private boolean boulderRainEnabled;
    private String boulderRainAnimation = "";
    private int boulderRainActionDelayTicks = 16;
    private int boulderRainCooldownTicks = 240;
    /** Outer edge of the ring the volley falls in, measured from where the boss cast it. */
    private int boulderRainRadius = 12;
    /** Inner edge, the dead zone at the boss' own feet. Held under the outer one. */
    private int boulderRainMinRadius;
    private int boulderRainCount = 8;
    /** Ticks between one stone and the next; 0 drops the whole volley on one tick. */
    private int boulderRainIntervalTicks = 4;
    /** How high above the floor a stone starts, which is also how long its mark burns. */
    private int boulderRainFallHeight = 16;
    /** Cosmetic only, exactly as the corridor boulder's block is. */
    private String boulderRainBlock = "minecraft:stone";
    private String boulderRainStyle = BoulderStyles.BLOCK;
    /** Diameter in tenths of a block, so 12 drops a 1.2 block stone. */
    private int boulderRainScale = 12;
    private int boulderRainDamage = 10;
    private int boulderRainKnockback = 2;
    private int boulderRainShatterRadius = 2;
    private int boulderRainShatterDamage = 4;
    private String boulderRainVfx = AreaVfxStyles.NONE;

    private boolean tetherEnabled;
    private String tetherAnimation = "";
    private int tetherActionDelayTicks = 16;
    private int tetherCooldownTicks = 300;
    private int tetherTargetMode = BossTargetMode.RANDOM;
    private int tetherTargetCount = 2;
    private int tetherAnchor = TETHER_ANCHOR_BOSS;
    /** How far from its anchor a victim has to get for the leash to snap. */
    private int tetherBreakDistance = 10;
    /** How long they get to do it before the leash punishes them instead. */
    private int tetherDurationTicks = 120;
    /** Drag toward the anchor, 0 to 10; zero leaves the victim free to walk until the timer runs out. */
    private int tetherPull;
    private int tetherFailDamage = 12;
    private String tetherStyle = HookCordStyles.PARTICLES;
    private int tetherWidthPercent = 100;

    private boolean gravityEnabled;
    private String gravityAnimation = "";
    private int gravityActionDelayTicks = 20;
    private int gravityCooldownTicks = 300;
    private int gravityMode = GRAVITY_MODE_PULL;
    private int gravityRadius = 16;
    /** How long the pull or the push keeps working; a throw is over the tick it happens. */
    private int gravityDurationTicks = 60;
    /**
     * Pull and push: hundredths of a block per tick added every tick, so 10 is a steady
     * 0.10. Throw: tenths of a block per tick straight up, the way the geyser's launch is.
     *
     * <p>The default is pitched against what a player on plain ground puts in per tick -
     * 0.098 walking, 0.127 sprinting - and sits between the two: a walker is held where
     * they stand, a sprinter gains about a block every sixteen ticks. Anyone standing still
     * is reeled in at walking pace.</p>
     */
    private int gravityStrength = 10;
    /** How close to the boss counts as touching it, for the pull's bite. */
    private int gravityTouchRadius = 2;
    private int gravityDamage = 8;
    private String gravityVfx = AreaVfxStyles.NONE;

    private boolean markEnabled;
    private String markAnimation = "";
    private int markActionDelayTicks = 12;
    private int markCooldownTicks = 240;
    private int markMode = MARK_MODE_SOAK;
    private int markTargetMode = BossTargetMode.RANDOM;
    private int markTargetCount = 1;
    /** How long the mark burns on its carrier before it goes off. */
    private int markFuseTicks = 60;
    private int markRadius = 4;
    /** On, the circle rides its carrier; off, it stays on the ground they were called out on. */
    private boolean markFollow;
    /** Gather up: how many of the fight have to be standing inside when it goes off. */
    private int markMinPlayers = 2;
    /** Gather up: shared out between everyone inside. Spread out: dealt to each neighbour. */
    private int markDamage = 30;
    /** Gather up: what everyone inside takes instead when there were not enough of them. */
    private int markFailDamage = 60;
    /** Spread out: what the carrier pays for carrying it, wherever they took it. */
    private int markSelfDamage;
    private String markVfx = AreaVfxStyles.NONE;

    private boolean coverEnabled;
    private String coverAnimation = "";
    /**
     * The wind-up is the whole mechanic: it is the time everyone gets to hide, and the one
     * warning there is. Held at a second at the least, because a strike nobody could have
     * got out of is not a mechanic, it is a trap.
     */
    private int coverActionDelayTicks = 80;
    private int coverCooldownTicks = 500;
    private int coverMode = COVER_MODE_SIGHT;
    /** How far from the boss the strike reaches: the arena, not one shape on its floor. */
    private int coverRange = 40;
    private int coverDamage = 40;
    private int coverKnockback = 2;
    /** Shelter rule: how many circles the wind-up puts down, and how wide each one is. */
    private int coverShelterCount = 2;
    private int coverShelterRadius = 3;
    /** The ring around the boss the shelters are scattered in. Held with min under max. */
    private int coverShelterMinRange = 4;
    private int coverShelterMaxRange = 14;
    private String coverVfx = AreaVfxStyles.NONE;

    /**
     * The arena hazard: not a cast but the ground itself, armed when the phase is entered
     * and gone when the phase is. Ten seconds of grace by default, so a party does not walk
     * into a phase change already standing in the fire.
     */
    private boolean hazardEnabled;
    private int hazardMode = HAZARD_MODE_RING;
    /** How long after the phase begins the arena turns dangerous. */
    private int hazardDelayTicks = 200;
    /** How long before that the edge flashes and the countdown runs. */
    private int hazardWarnTicks = 60;
    private int hazardCenterMode = HAZARD_CENTER_BOSS;
    /** Ring, fixed point rule: the block the safe circle closes in on. */
    private int hazardCenterX;
    private int hazardCenterZ;
    /** Ring: where the safe circle starts and where it stops closing. Held with end under start. */
    private int hazardStartRadius = 30;
    private int hazardEndRadius = 6;
    private int hazardShrinkTicks = 1200;
    /** Box: two corners, the way the aggro zone is measured. */
    private int hazardX1;
    private int hazardY1;
    private int hazardZ1;
    private int hazardX2;
    private int hazardY2;
    private int hazardZ2;
    /** What one dose hits for, and how often a dose goes out. */
    private int hazardDamage = 4;
    private int hazardIntervalTicks = 20;

    /**
     * The hunt: the boss picks one victim and goes after nobody else. Only the wind-up is a
     * cast; the chase itself is the boss walking, which is why it has a speed and a length
     * rather than a range.
     */
    private boolean huntEnabled;
    private String huntAnimation = "";
    private int huntActionDelayTicks = 10;
    private int huntCooldownTicks = 400;
    private int huntTargetMode = BossTargetMode.FARTHEST;
    /** How long the boss stays on its prey before it gives the chase up. */
    private int huntDurationTicks = 160;
    /** The boss' walking speed for the length of the chase, as a percentage of its own. */
    private int huntSpeedPercent = 130;
    /** How close the boss has to get for the prey to count as caught. */
    private int huntCatchRadius = 2;
    private int huntDamage = 15;
    /** Off, a caught prey is hit and chased on until the time runs out. */
    private boolean huntCatchEnds = true;
    /** On, the rest of the rotation waits for the chase to end. */
    private boolean huntSilence;
    /** On, the prey glows for the length of the chase, so the whole party can see who was picked. */
    private boolean huntGlow = true;

    /** Which abilities this phase casts standing still, one bit per {@link BossAbilityKind}. */
    private int castRootMask = CAST_ROOT_ALL;

    private boolean invulnerableEnabled;
    private int invulnerableEndMode = INVULNERABLE_END_TIMER_OR_MINIONS;
    private int invulnerableDurationTicks = 200;
    private boolean invulnerableAllowTeleport;
    private boolean invulnerableSummonImmediately = true;

    private final BossEffectSet areaAttackEffects = new BossEffectSet();
    private final BossEffectSet lineAttackEffects = new BossEffectSet();
    private final BossEffectSet rangedAttackEffects = new BossEffectSet();
    private final BossEffectSet meleeAttackEffects = new BossEffectSet();
    private final BossEffectSet fluidSpitEffects = new BossEffectSet();
    private final BossEffectSet hookEffects = new BossEffectSet();
    private final BossEffectSet captureEffects = new BossEffectSet();
    private final BossEffectSet leapEffects = new BossEffectSet();
    private final BossEffectSet geyserEffects = new BossEffectSet();
    private final BossEffectSet boulderEffects = new BossEffectSet();
    private final BossEffectSet boulderRainEffects = new BossEffectSet();
    /** Dosed every second for as long as the leash holds. */
    private final BossEffectSet tetherEffects = new BossEffectSet();
    /** Landed once, on whoever was still leashed when the time ran out. */
    private final BossEffectSet tetherFailEffects = new BossEffectSet();
    /** Dosed every second to everyone inside the field, whichever way it is pushing them. */
    private final BossEffectSet gravityEffects = new BossEffectSet();
    /** Landed on whoever took the mark's own damage, in either of its two rules. */
    private final BossEffectSet markEffects = new BossEffectSet();
    /** Gather up: landed instead on everyone inside when there were not enough of them. */
    private final BossEffectSet markFailEffects = new BossEffectSet();
    /** Landed on everyone the strike caught out in the open. */
    private final BossEffectSet coverEffects = new BossEffectSet();
    /** Dosed with every hit of the hazard, on everyone standing in the fire. */
    private final BossEffectSet hazardEffects = new BossEffectSet();
    /** Landed on the prey each time the hunt catches it. */
    private final BossEffectSet huntEffects = new BossEffectSet();

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("StartHealthPercent", startHealthPercent);
        tag.putString("TeleportPreparationAnimation", teleportPreparationAnimation);
        tag.putInt("TeleportPreparationTicks", teleportPreparationTicks);
        tag.putString("AppearanceAnimation", appearanceAnimation);
        tag.putInt("AppearanceLockTicks", appearanceLockTicks);
        tag.putInt("TeleportMinDelayTicks", teleportMinDelayTicks);
        tag.putInt("TeleportMaxDelayTicks", teleportMaxDelayTicks);
        tag.putBoolean("SummonEnabled", summonEnabled);
        tag.putString("SummonAnimation", summonAnimation);
        tag.putInt("SummonActionDelayTicks", summonActionDelayTicks);
        tag.putInt("SummonCooldownTicks", summonCooldownTicks);
        tag.putString("MinionCloneName", minionCloneName);
        tag.putInt("MinionCloneTab", minionCloneTab);
        tag.putInt("MinionCount", minionCount);
        tag.putInt("MinionRadius", minionRadius);
        tag.putInt("MaxAliveMinions", maxAliveMinions);
        tag.putInt("MinionSpawnMode", minionSpawnMode);
        tag.putInt("MinionSpawnOrder", minionSpawnOrder);
        tag.putInt("MinionPointSearchRadius", minionPointSearchRadius);
        tag.putBoolean("MinionReuseOccupiedPoints", minionReuseOccupiedPoints);
        tag.put("MinionSpawnPoints", minionSpawnPoints.writeToNBT());
        tag.putBoolean("AreaAttackEnabled", areaAttackEnabled);
        tag.putString("AreaAttackAnimation", areaAttackAnimation);
        tag.putInt("AreaAttackActionDelayTicks", areaAttackActionDelayTicks);
        tag.putInt("AreaAttackCooldownTicks", areaAttackCooldownTicks);
        tag.putInt("AreaAttackDamage", areaAttackDamage);
        tag.putInt("AreaAttackRadius", areaAttackRadius);
        tag.putInt("AreaAttackKnockback", areaAttackKnockback);
        tag.putString("AreaAttackVfx", areaAttackVfx);
        tag.putInt("AreaAttackVfxDuration", areaAttackVfxDurationTicks);
        tag.putBoolean("AreaAttackBlockWave", areaAttackBlockWave);
        tag.putBoolean("LineAttackEnabled", lineAttackEnabled);
        tag.putString("LineAttackAnimation", lineAttackAnimation);
        tag.putInt("LineAttackActionDelayTicks", lineAttackActionDelayTicks);
        tag.putInt("LineAttackCooldownTicks", lineAttackCooldownTicks);
        tag.putInt("LineAttackDirection", lineAttackDirection);
        tag.putBoolean("LineAttackFaceAxis", lineAttackFaceAxis);
        tag.putInt("LineAttackTargetMode", lineAttackTargetMode);
        tag.putInt("LineAttackLength", lineAttackLength);
        tag.putInt("LineAttackWidth", lineAttackWidth);
        tag.putInt("LineAttackHeight", lineAttackHeight);
        tag.putInt("LineAttackDamage", lineAttackDamage);
        tag.putInt("LineAttackKnockback", lineAttackKnockback);
        tag.putInt("LineAttackSideWidth", lineAttackSideWidth);
        tag.putInt("LineAttackSidePercent", lineAttackSidePercent);
        tag.putString("LineAttackVfx", lineAttackVfx);
        tag.putBoolean("LineAttackBlockWave", lineAttackBlockWave);
        tag.putBoolean("RangedAttackEnabled", rangedAttackEnabled);
        tag.putString("RangedAttackAnimation", rangedAttackAnimation);
        tag.putInt("RangedAttackActionDelayTicks", rangedAttackActionDelayTicks);
        tag.putInt("RangedAttackCooldownTicks", rangedAttackCooldownTicks);
        tag.putInt("RangedAttackDamage", rangedAttackDamage);
        tag.putInt("RangedAttackMinRange", rangedAttackMinRange);
        tag.putInt("RangedAttackMaxRange", rangedAttackMaxRange);
        tag.putInt("RangedAttackTargetMode", rangedAttackTargetMode);
        tag.putBoolean("MeleeAttackEnabled", meleeAttackEnabled);
        tag.putString("MeleeAttackAnimation", meleeAttackAnimation);
        tag.putInt("MeleeAttackActionDelayTicks", meleeAttackActionDelayTicks);
        tag.putInt("MeleeAttackCooldownTicks", meleeAttackCooldownTicks);
        tag.putInt("MeleeAttackDamage", meleeAttackDamage);
        tag.putInt("MeleeAttackRange", meleeAttackRange);
        tag.putInt("MeleeAttackKnockback", meleeAttackKnockback);
        tag.putInt("MeleeAttackTargetMode", meleeAttackTargetMode);
        tag.putBoolean("FluidSpitEnabled", fluidSpitEnabled);
        tag.putString("FluidSpitAnimation", fluidSpitAnimation);
        tag.putInt("FluidSpitActionDelayTicks", fluidSpitActionDelayTicks);
        tag.putInt("FluidSpitCooldownTicks", fluidSpitCooldownTicks);
        tag.putString("FluidSpitBlock", fluidSpitBlock);
        tag.putInt("FluidSpitLifetimeTicks", fluidSpitLifetimeTicks);
        tag.putInt("FluidSpitRadius", fluidSpitRadius);
        tag.putInt("FluidSpitDamage", fluidSpitDamage);
        tag.putInt("FluidSpitMinRange", fluidSpitMinRange);
        tag.putInt("FluidSpitMaxRange", fluidSpitMaxRange);
        tag.putInt("FluidSpitTargetMode", fluidSpitTargetMode);
        tag.putBoolean("HookEnabled", hookEnabled);
        tag.putString("HookAnimation", hookAnimation);
        tag.putInt("HookActionDelayTicks", hookActionDelayTicks);
        tag.putInt("HookCooldownTicks", hookCooldownTicks);
        tag.putInt("HookTargetMode", hookTargetMode);
        tag.putInt("HookTargetCount", hookTargetCount);
        tag.putInt("HookDamage", hookDamage);
        tag.putInt("HookPullStrength", hookPullStrength);
        tag.putInt("HookPullDurationTicks", hookPullDurationTicks);
        tag.putInt("HookStopDistance", hookStopDistance);
        tag.putInt("HookMinRange", hookMinRange);
        tag.putInt("HookMaxRange", hookMaxRange);
        tag.putInt("HookMode", hookMode);
        tag.putString("HookCordStyle", hookCordStyle);
        tag.putBoolean("CaptureEnabled", captureEnabled);
        tag.putString("CaptureAnimation", captureAnimation);
        tag.putInt("CaptureActionDelayTicks", captureActionDelayTicks);
        tag.putInt("CaptureCooldownTicks", captureCooldownTicks);
        tag.putInt("CaptureTargetMode", captureTargetMode);
        tag.putInt("CaptureMinRange", captureMinRange);
        tag.putInt("CaptureMaxRange", captureMaxRange);
        tag.putInt("CaptureMode", captureMode);
        tag.putInt("CaptureDurationTicks", captureDurationTicks);
        tag.putInt("CaptureLiftHeight", captureLiftHeight);
        tag.putInt("CaptureLiftTicks", captureLiftTicks);
        tag.putInt("CaptureEffectTarget", captureEffectTarget);
        tag.putString("CaptureBeamStyle", captureBeamStyle);
        tag.putInt("CaptureBeamWidthPercent", captureBeamWidthPercent);
        tag.putInt("CaptureBeamSagPercent", captureBeamSagPercent);
        tag.putBoolean("CaptureAllowLook", captureAllowLook);
        tag.putBoolean("LeapEnabled", leapEnabled);
        tag.putString("LeapAnimation", leapAnimation);
        tag.putString("LeapLandAnimation", leapLandAnimation);
        tag.putInt("LeapActionDelayTicks", leapActionDelayTicks);
        tag.putInt("LeapCooldownTicks", leapCooldownTicks);
        tag.putInt("LeapMode", leapMode);
        tag.putInt("LeapTargetMode", leapTargetMode);
        tag.putInt("LeapHeight", leapHeight);
        tag.putInt("LeapMinRange", leapMinRange);
        tag.putInt("LeapMaxRange", leapMaxRange);
        tag.putInt("LeapOffsetX", leapOffsetX);
        tag.putInt("LeapOffsetY", leapOffsetY);
        tag.putInt("LeapOffsetZ", leapOffsetZ);
        tag.putInt("LeapFixedX", leapFixedX);
        tag.putInt("LeapFixedY", leapFixedY);
        tag.putInt("LeapFixedZ", leapFixedZ);
        tag.putInt("LeapImpactDamage", leapImpactDamage);
        tag.putInt("LeapImpactRadius", leapImpactRadius);
        tag.putInt("LeapImpactKnockback", leapImpactKnockback);
        tag.putInt("LeapMaxAirTicks", leapMaxAirTicks);
        tag.putBoolean("LeapTelegraph", leapTelegraph);
        tag.putString("LeapVfx", leapVfx);
        tag.putBoolean("LeapBlockWave", leapBlockWave);
        tag.putBoolean("GeyserEnabled", geyserEnabled);
        tag.putString("GeyserAnimation", geyserAnimation);
        tag.putInt("GeyserActionDelayTicks", geyserActionDelayTicks);
        tag.putInt("GeyserCooldownTicks", geyserCooldownTicks);
        tag.putInt("GeyserTargetMode", geyserTargetMode);
        tag.putInt("GeyserTargetCount", geyserTargetCount);
        tag.putInt("GeyserMinRange", geyserMinRange);
        tag.putInt("GeyserMaxRange", geyserMaxRange);
        tag.putInt("GeyserFuseTicks", geyserFuseTicks);
        tag.putInt("GeyserRadius", geyserRadius);
        tag.putInt("GeyserDamage", geyserDamage);
        tag.putInt("GeyserLaunch", geyserLaunch);
        tag.putBoolean("GeyserFollowTarget", geyserFollowTarget);
        tag.putString("GeyserFluid", geyserFluid);
        tag.putInt("GeyserFluidLifetime", geyserFluidLifetimeTicks);
        tag.putString("GeyserVfx", geyserVfx);
        tag.putBoolean("GeyserBlockWave", geyserBlockWave);
        tag.putBoolean("BoulderEnabled", boulderEnabled);
        tag.putString("BoulderAnimation", boulderAnimation);
        tag.putInt("BoulderActionDelayTicks", boulderActionDelayTicks);
        tag.putInt("BoulderCooldownTicks", boulderCooldownTicks);
        tag.putInt("BoulderMode", boulderMode);
        tag.putInt("BoulderTargetMode", boulderTargetMode);
        tag.putString("BoulderBlock", boulderBlock);
        tag.putString("BoulderStyle", boulderStyle);
        tag.putInt("BoulderScale", boulderScale);
        tag.putInt("BoulderSpeed", boulderSpeed);
        tag.putInt("BoulderRange", boulderRange);
        tag.putInt("BoulderDamage", boulderDamage);
        tag.putInt("BoulderKnockback", boulderKnockback);
        tag.putBoolean("BoulderStopsOnHit", boulderStopsOnHit);
        tag.putInt("BoulderShatterRadius", boulderShatterRadius);
        tag.putInt("BoulderShatterDamage", boulderShatterDamage);
        tag.putString("BoulderVfx", boulderVfx);
        tag.putBoolean("BoulderRainEnabled", boulderRainEnabled);
        tag.putString("BoulderRainAnimation", boulderRainAnimation);
        tag.putInt("BoulderRainActionDelayTicks", boulderRainActionDelayTicks);
        tag.putInt("BoulderRainCooldownTicks", boulderRainCooldownTicks);
        tag.putInt("BoulderRainRadius", boulderRainRadius);
        tag.putInt("BoulderRainMinRadius", boulderRainMinRadius);
        tag.putInt("BoulderRainCount", boulderRainCount);
        tag.putInt("BoulderRainIntervalTicks", boulderRainIntervalTicks);
        tag.putInt("BoulderRainFallHeight", boulderRainFallHeight);
        tag.putString("BoulderRainBlock", boulderRainBlock);
        tag.putString("BoulderRainStyle", boulderRainStyle);
        tag.putInt("BoulderRainScale", boulderRainScale);
        tag.putInt("BoulderRainDamage", boulderRainDamage);
        tag.putInt("BoulderRainKnockback", boulderRainKnockback);
        tag.putInt("BoulderRainShatterRadius", boulderRainShatterRadius);
        tag.putInt("BoulderRainShatterDamage", boulderRainShatterDamage);
        tag.putString("BoulderRainVfx", boulderRainVfx);
        tag.putBoolean("TetherEnabled", tetherEnabled);
        tag.putString("TetherAnimation", tetherAnimation);
        tag.putInt("TetherActionDelayTicks", tetherActionDelayTicks);
        tag.putInt("TetherCooldownTicks", tetherCooldownTicks);
        tag.putInt("TetherTargetMode", tetherTargetMode);
        tag.putInt("TetherTargetCount", tetherTargetCount);
        tag.putInt("TetherAnchor", tetherAnchor);
        tag.putInt("TetherBreakDistance", tetherBreakDistance);
        tag.putInt("TetherDurationTicks", tetherDurationTicks);
        tag.putInt("TetherPull", tetherPull);
        tag.putInt("TetherFailDamage", tetherFailDamage);
        tag.putString("TetherStyle", tetherStyle);
        tag.putInt("TetherWidthPercent", tetherWidthPercent);
        tag.putBoolean("GravityEnabled", gravityEnabled);
        tag.putString("GravityAnimation", gravityAnimation);
        tag.putInt("GravityActionDelayTicks", gravityActionDelayTicks);
        tag.putInt("GravityCooldownTicks", gravityCooldownTicks);
        tag.putInt("GravityMode", gravityMode);
        tag.putInt("GravityRadius", gravityRadius);
        tag.putInt("GravityDurationTicks", gravityDurationTicks);
        tag.putInt("GravityStrength", gravityStrength);
        tag.putInt("GravityTouchRadius", gravityTouchRadius);
        tag.putInt("GravityDamage", gravityDamage);
        tag.putString("GravityVfx", gravityVfx);
        tag.putBoolean("MarkEnabled", markEnabled);
        tag.putString("MarkAnimation", markAnimation);
        tag.putInt("MarkActionDelayTicks", markActionDelayTicks);
        tag.putInt("MarkCooldownTicks", markCooldownTicks);
        tag.putInt("MarkMode", markMode);
        tag.putInt("MarkTargetMode", markTargetMode);
        tag.putInt("MarkTargetCount", markTargetCount);
        tag.putInt("MarkFuseTicks", markFuseTicks);
        tag.putInt("MarkRadius", markRadius);
        tag.putBoolean("MarkFollow", markFollow);
        tag.putInt("MarkMinPlayers", markMinPlayers);
        tag.putInt("MarkDamage", markDamage);
        tag.putInt("MarkFailDamage", markFailDamage);
        tag.putInt("MarkSelfDamage", markSelfDamage);
        tag.putString("MarkVfx", markVfx);
        tag.putBoolean("CoverEnabled", coverEnabled);
        tag.putString("CoverAnimation", coverAnimation);
        tag.putInt("CoverActionDelayTicks", coverActionDelayTicks);
        tag.putInt("CoverCooldownTicks", coverCooldownTicks);
        tag.putInt("CoverMode", coverMode);
        tag.putInt("CoverRange", coverRange);
        tag.putInt("CoverDamage", coverDamage);
        tag.putInt("CoverKnockback", coverKnockback);
        tag.putInt("CoverShelterCount", coverShelterCount);
        tag.putInt("CoverShelterRadius", coverShelterRadius);
        tag.putInt("CoverShelterMinRange", coverShelterMinRange);
        tag.putInt("CoverShelterMaxRange", coverShelterMaxRange);
        tag.putString("CoverVfx", coverVfx);
        tag.putBoolean("HazardEnabled", hazardEnabled);
        tag.putInt("HazardMode", hazardMode);
        tag.putInt("HazardDelayTicks", hazardDelayTicks);
        tag.putInt("HazardWarnTicks", hazardWarnTicks);
        tag.putInt("HazardCenterMode", hazardCenterMode);
        tag.putInt("HazardCenterX", hazardCenterX);
        tag.putInt("HazardCenterZ", hazardCenterZ);
        tag.putInt("HazardStartRadius", hazardStartRadius);
        tag.putInt("HazardEndRadius", hazardEndRadius);
        tag.putInt("HazardShrinkTicks", hazardShrinkTicks);
        tag.putInt("HazardX1", hazardX1);
        tag.putInt("HazardY1", hazardY1);
        tag.putInt("HazardZ1", hazardZ1);
        tag.putInt("HazardX2", hazardX2);
        tag.putInt("HazardY2", hazardY2);
        tag.putInt("HazardZ2", hazardZ2);
        tag.putInt("HazardDamage", hazardDamage);
        tag.putInt("HazardIntervalTicks", hazardIntervalTicks);
        tag.putBoolean("HuntEnabled", huntEnabled);
        tag.putString("HuntAnimation", huntAnimation);
        tag.putInt("HuntActionDelayTicks", huntActionDelayTicks);
        tag.putInt("HuntCooldownTicks", huntCooldownTicks);
        tag.putInt("HuntTargetMode", huntTargetMode);
        tag.putInt("HuntDurationTicks", huntDurationTicks);
        tag.putInt("HuntSpeedPercent", huntSpeedPercent);
        tag.putInt("HuntCatchRadius", huntCatchRadius);
        tag.putInt("HuntDamage", huntDamage);
        tag.putBoolean("HuntCatchEnds", huntCatchEnds);
        tag.putBoolean("HuntSilence", huntSilence);
        tag.putBoolean("HuntGlow", huntGlow);
        tag.putInt("CastRootMask", castRootMask);
        tag.putBoolean("InvulnerableEnabled", invulnerableEnabled);
        tag.putInt("InvulnerableEndMode", invulnerableEndMode);
        tag.putInt("InvulnerableDurationTicks", invulnerableDurationTicks);
        tag.putBoolean("InvulnerableAllowTeleport", invulnerableAllowTeleport);
        tag.putBoolean("InvulnerableSummonImmediately", invulnerableSummonImmediately);
        tag.put("AreaAttackEffects", areaAttackEffects.writeToNBT());
        tag.put("LineAttackEffects", lineAttackEffects.writeToNBT());
        tag.put("RangedAttackEffects", rangedAttackEffects.writeToNBT());
        tag.put("MeleeAttackEffects", meleeAttackEffects.writeToNBT());
        tag.put("FluidSpitEffects", fluidSpitEffects.writeToNBT());
        tag.put("HookEffects", hookEffects.writeToNBT());
        tag.put("CaptureEffects", captureEffects.writeToNBT());
        tag.put("LeapEffects", leapEffects.writeToNBT());
        tag.put("GeyserEffects", geyserEffects.writeToNBT());
        tag.put("BoulderEffects", boulderEffects.writeToNBT());
        tag.put("BoulderRainEffects", boulderRainEffects.writeToNBT());
        tag.put("TetherEffects", tetherEffects.writeToNBT());
        tag.put("TetherFailEffects", tetherFailEffects.writeToNBT());
        tag.put("GravityEffects", gravityEffects.writeToNBT());
        tag.put("MarkEffects", markEffects.writeToNBT());
        tag.put("MarkFailEffects", markFailEffects.writeToNBT());
        tag.put("CoverEffects", coverEffects.writeToNBT());
        tag.put("HazardEffects", hazardEffects.writeToNBT());
        tag.put("HuntEffects", huntEffects.writeToNBT());
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        startHealthPercent = value(tag, "StartHealthPercent", 100, 1, 100);
        teleportPreparationAnimation = clean(tag.getString("TeleportPreparationAnimation"));
        teleportPreparationTicks = value(tag, "TeleportPreparationTicks", 20, 0, 1200);
        appearanceAnimation = clean(tag.getString("AppearanceAnimation"));
        appearanceLockTicks = value(tag, "AppearanceLockTicks", 20, 0, 1200);
        setTeleportDelayRange(
                value(tag, "TeleportMinDelayTicks", 60, 10, 1200),
                value(tag, "TeleportMaxDelayTicks", 100, 10, 1200));
        summonEnabled = tag.getBoolean("SummonEnabled");
        summonAnimation = clean(tag.getString("SummonAnimation"));
        summonActionDelayTicks = value(tag, "SummonActionDelayTicks", 20, 0, 1200);
        summonCooldownTicks = value(tag, "SummonCooldownTicks", 400, 20, 12000);
        minionCloneName = clean(tag.getString("MinionCloneName"));
        minionCloneTab = value(tag, "MinionCloneTab", 1, 1, 9);
        minionCount = value(tag, "MinionCount", 3, 1, 32);
        minionRadius = value(tag, "MinionRadius", 4, 1, 32);
        maxAliveMinions = value(tag, "MaxAliveMinions", 6, 1, 128);
        minionSpawnMode = value(tag, "MinionSpawnMode", MINION_SPAWN_RANDOM_RADIUS,
                MINION_SPAWN_RANDOM_RADIUS, MINION_SPAWN_POINTS_THEN_RANDOM);
        minionSpawnOrder = value(tag, "MinionSpawnOrder", MINION_ORDER_LIST,
                MINION_ORDER_LIST, MINION_ORDER_RANDOM);
        minionPointSearchRadius = value(tag, "MinionPointSearchRadius", 0, 0, 4);
        minionReuseOccupiedPoints = tag.getBoolean("MinionReuseOccupiedPoints");
        minionSpawnPoints.readFromNBT(tag, "MinionSpawnPoints");
        areaAttackEnabled = tag.getBoolean("AreaAttackEnabled");
        areaAttackAnimation = clean(tag.getString("AreaAttackAnimation"));
        areaAttackActionDelayTicks = value(tag, "AreaAttackActionDelayTicks", 12, 0, 1200);
        areaAttackCooldownTicks = value(tag, "AreaAttackCooldownTicks", 100, 1, 12000);
        areaAttackDamage = value(tag, "AreaAttackDamage", 8, 1, 1000);
        areaAttackRadius = value(tag, "AreaAttackRadius", 5, 1, 32);
        areaAttackKnockback = value(tag, "AreaAttackKnockback", 1, 0, 10);
        areaAttackVfx = AreaVfxStyles.normalize(tag.getString("AreaAttackVfx"));
        areaAttackVfxDurationTicks = value(tag, "AreaAttackVfxDuration", 20, 5, 100);
        areaAttackBlockWave = tag.getBoolean("AreaAttackBlockWave");
        lineAttackEnabled = tag.getBoolean("LineAttackEnabled");
        lineAttackAnimation = clean(tag.getString("LineAttackAnimation"));
        lineAttackActionDelayTicks = value(tag, "LineAttackActionDelayTicks", 12, 0, 1200);
        lineAttackCooldownTicks = value(tag, "LineAttackCooldownTicks", 140, 1, 12000);
        lineAttackDirection = value(tag, "LineAttackDirection", LINE_DIRECTION_TARGET,
                LINE_DIRECTION_TARGET, LINE_DIRECTION_FACING);
        lineAttackFaceAxis = !tag.contains("LineAttackFaceAxis") || tag.getBoolean("LineAttackFaceAxis");
        lineAttackTargetMode = value(tag, "LineAttackTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        lineAttackLength = value(tag, "LineAttackLength", 9, 1, 64);
        lineAttackWidth = value(tag, "LineAttackWidth", 2, 1, 8);
        lineAttackHeight = value(tag, "LineAttackHeight", 3, 1, 8);
        lineAttackDamage = value(tag, "LineAttackDamage", 10, 1, 1000);
        lineAttackKnockback = value(tag, "LineAttackKnockback", 2, 0, 10);
        lineAttackSideWidth = value(tag, "LineAttackSideWidth", 2, 0, 8);
        lineAttackSidePercent = value(tag, "LineAttackSidePercent", 50, 10, 100);
        lineAttackVfx = AreaVfxStyles.normalize(tag.getString("LineAttackVfx"));
        lineAttackBlockWave = tag.getBoolean("LineAttackBlockWave");
        rangedAttackEnabled = tag.getBoolean("RangedAttackEnabled");
        rangedAttackAnimation = clean(tag.getString("RangedAttackAnimation"));
        rangedAttackActionDelayTicks = value(tag, "RangedAttackActionDelayTicks", 12, 0, 1200);
        rangedAttackCooldownTicks = value(tag, "RangedAttackCooldownTicks", 80, 1, 12000);
        rangedAttackDamage = value(tag, "RangedAttackDamage", 6, 1, 1000);
        setRangedAttackRange(
                value(tag, "RangedAttackMinRange", 4, 0, 64),
                value(tag, "RangedAttackMaxRange", 24, 1, 128));
        rangedAttackTargetMode = value(tag, "RangedAttackTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        meleeAttackEnabled = tag.getBoolean("MeleeAttackEnabled");
        meleeAttackAnimation = clean(tag.getString("MeleeAttackAnimation"));
        meleeAttackActionDelayTicks = value(tag, "MeleeAttackActionDelayTicks", 8, 0, 1200);
        meleeAttackCooldownTicks = value(tag, "MeleeAttackCooldownTicks", 30, 1, 12000);
        meleeAttackDamage = value(tag, "MeleeAttackDamage", 6, 1, 1000);
        meleeAttackRange = value(tag, "MeleeAttackRange", 3, 1, 32);
        meleeAttackKnockback = value(tag, "MeleeAttackKnockback", 1, 0, 10);
        meleeAttackTargetMode = value(tag, "MeleeAttackTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        fluidSpitEnabled = tag.getBoolean("FluidSpitEnabled");
        fluidSpitAnimation = clean(tag.getString("FluidSpitAnimation"));
        fluidSpitActionDelayTicks = value(tag, "FluidSpitActionDelayTicks", 12, 0, 1200);
        fluidSpitCooldownTicks = value(tag, "FluidSpitCooldownTicks", 120, 1, 12000);
        fluidSpitBlock = tag.contains("FluidSpitBlock")
                ? clean(tag.getString("FluidSpitBlock")) : "minecraft:lava";
        fluidSpitLifetimeTicks = value(tag, "FluidSpitLifetimeTicks", 60, 5, 1200);
        fluidSpitRadius = value(tag, "FluidSpitRadius", 1, 0, 4);
        fluidSpitDamage = value(tag, "FluidSpitDamage", 0, 0, 1000);
        setFluidSpitRange(
                value(tag, "FluidSpitMinRange", 2, 0, 64),
                value(tag, "FluidSpitMaxRange", 24, 1, 128));
        fluidSpitTargetMode = value(tag, "FluidSpitTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);

        hookEnabled = tag.getBoolean("HookEnabled");
        hookAnimation = clean(tag.getString("HookAnimation"));
        hookActionDelayTicks = value(tag, "HookActionDelayTicks", 10, 0, 1200);
        hookCooldownTicks = value(tag, "HookCooldownTicks", 160, 1, 12000);
        hookTargetMode = value(tag, "HookTargetMode",
                BossTargetMode.FARTHEST, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        hookTargetCount = value(tag, "HookTargetCount", 1, 1, 8);
        hookDamage = value(tag, "HookDamage", 4, 0, 1000);
        hookPullStrength = value(tag, "HookPullStrength", 8, 1, 20);
        hookPullDurationTicks = value(tag, "HookPullDurationTicks", 20, 1, 200);
        hookStopDistance = value(tag, "HookStopDistance", 2, 0, 32);
        setHookRange(
                value(tag, "HookMinRange", 4, 0, 64),
                value(tag, "HookMaxRange", 24, 1, 128));
        hookMode = value(tag, "HookMode", HOOK_MODE_PULL, HOOK_MODE_PULL, HOOK_MODE_CINCH);
        // An absent key reads as an empty string, which normalizes back to the plain sparks.
        hookCordStyle = HookCordStyles.normalize(tag.getString("HookCordStyle"));

        captureEnabled = tag.getBoolean("CaptureEnabled");
        captureAnimation = clean(tag.getString("CaptureAnimation"));
        captureActionDelayTicks = value(tag, "CaptureActionDelayTicks", 10, 0, 1200);
        captureCooldownTicks = value(tag, "CaptureCooldownTicks", 200, 20, 12000);
        captureTargetMode = value(tag, "CaptureTargetMode",
                BossTargetMode.RANDOM, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        setCaptureRange(
                value(tag, "CaptureMinRange", 0, 0, 64),
                value(tag, "CaptureMaxRange", 16, 1, 128));
        captureMode = value(tag, "CaptureMode", CAPTURE_MODE_HOLD, CAPTURE_MODE_HOLD, CAPTURE_MODE_LIFT);
        captureDurationTicks = value(tag, "CaptureDurationTicks", 60, 1, 1200);
        captureLiftHeight = value(tag, "CaptureLiftHeight", 5, 0, 64);
        captureLiftTicks = value(tag, "CaptureLiftTicks", 40, 1, 1200);
        captureEffectTarget = value(tag, "CaptureEffectTarget", CAPTURE_EFFECT_PLAYER,
                CAPTURE_EFFECT_PLAYER, CAPTURE_EFFECT_BOTH);
        captureBeamStyle = tag.contains("CaptureBeamStyle")
                ? HookCordStyles.normalize(tag.getString("CaptureBeamStyle")) : HookCordStyles.GHOST;
        captureBeamWidthPercent = value(tag, "CaptureBeamWidthPercent", 100, 25, 400);
        captureBeamSagPercent = value(tag, "CaptureBeamSagPercent", 0, 0, 200);
        captureAllowLook = !tag.contains("CaptureAllowLook") || tag.getBoolean("CaptureAllowLook");

        leapEnabled = tag.getBoolean("LeapEnabled");
        leapAnimation = clean(tag.getString("LeapAnimation"));
        leapLandAnimation = clean(tag.getString("LeapLandAnimation"));
        leapActionDelayTicks = value(tag, "LeapActionDelayTicks", 12, 0, 1200);
        leapCooldownTicks = value(tag, "LeapCooldownTicks", 200, 1, 12000);
        leapMode = value(tag, "LeapMode", LEAP_MODE_UP, LEAP_MODE_UP, LEAP_MODE_ARENA_OFFSET);
        leapTargetMode = value(tag, "LeapTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        leapHeight = value(tag, "LeapHeight", 8, 1, MAX_LEAP_HEIGHT);
        setLeapRange(
                value(tag, "LeapMinRange", 4, 0, 64),
                value(tag, "LeapMaxRange", 24, 1, 128));
        leapOffsetX = value(tag, "LeapOffsetX", 0, -64, 64);
        leapOffsetY = value(tag, "LeapOffsetY", 0, -64, 64);
        leapOffsetZ = value(tag, "LeapOffsetZ", 0, -64, 64);
        leapFixedX = value(tag, "LeapFixedX", 0, -MAX_LEAP_COORDINATE, MAX_LEAP_COORDINATE);
        leapFixedY = value(tag, "LeapFixedY", 0, -MAX_LEAP_COORDINATE, MAX_LEAP_COORDINATE);
        leapFixedZ = value(tag, "LeapFixedZ", 0, -MAX_LEAP_COORDINATE, MAX_LEAP_COORDINATE);
        leapImpactDamage = value(tag, "LeapImpactDamage", 10, 0, 1000);
        leapImpactRadius = value(tag, "LeapImpactRadius", 4, 1, 32);
        leapImpactKnockback = value(tag, "LeapImpactKnockback", 2, 0, 10);
        leapMaxAirTicks = value(tag, "LeapMaxAirTicks", 100, 20, 400);
        // A boss saved before the marker existed gets it: an unannounced slam that big
        // reads as an unfair death rather than as a mechanic.
        leapTelegraph = !tag.contains("LeapTelegraph") || tag.getBoolean("LeapTelegraph");
        leapVfx = AreaVfxStyles.normalize(tag.getString("LeapVfx"));
        leapBlockWave = tag.getBoolean("LeapBlockWave");

        geyserEnabled = tag.getBoolean("GeyserEnabled");
        geyserAnimation = clean(tag.getString("GeyserAnimation"));
        geyserActionDelayTicks = value(tag, "GeyserActionDelayTicks", 12, 0, 1200);
        geyserCooldownTicks = value(tag, "GeyserCooldownTicks", 160, 1, 12000);
        geyserTargetMode = value(tag, "GeyserTargetMode",
                BossTargetMode.RANDOM, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        geyserTargetCount = value(tag, "GeyserTargetCount", 1, 1, 8);
        setGeyserRange(
                value(tag, "GeyserMinRange", 3, 0, 64),
                value(tag, "GeyserMaxRange", 24, 1, 128));
        geyserFuseTicks = value(tag, "GeyserFuseTicks", 25, 5, 200);
        geyserRadius = value(tag, "GeyserRadius", 3, 1, 16);
        geyserDamage = value(tag, "GeyserDamage", 8, 0, 1000);
        geyserLaunch = value(tag, "GeyserLaunch", 8, 0, 20);
        geyserFollowTarget = tag.getBoolean("GeyserFollowTarget");
        geyserFluid = clean(tag.getString("GeyserFluid"));
        geyserFluidLifetimeTicks = value(tag, "GeyserFluidLifetime", 60, 5, 1200);
        geyserVfx = AreaVfxStyles.normalize(tag.getString("GeyserVfx"));
        geyserBlockWave = tag.getBoolean("GeyserBlockWave");

        boulderEnabled = tag.getBoolean("BoulderEnabled");
        boulderAnimation = clean(tag.getString("BoulderAnimation"));
        boulderActionDelayTicks = value(tag, "BoulderActionDelayTicks", 16, 0, 1200);
        boulderCooldownTicks = value(tag, "BoulderCooldownTicks", 180, 1, 12000);
        boulderMode = value(tag, "BoulderMode", BOULDER_MODE_ROLL, BOULDER_MODE_ROLL, BOULDER_MODE_THROW);
        boulderTargetMode = value(tag, "BoulderTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        boulderBlock = tag.contains("BoulderBlock")
                ? clean(tag.getString("BoulderBlock")) : "minecraft:stone";
        // An absent key is a boss saved before the skins existed, and normalize maps it to
        // the plain block - so nobody's boulder changes its look under them.
        boulderStyle = BoulderStyles.normalize(tag.getString("BoulderStyle"));
        boulderScale = value(tag, "BoulderScale", 15, 5, 40);
        boulderSpeed = value(tag, "BoulderSpeed", 6, 1, 20);
        boulderRange = value(tag, "BoulderRange", 20, 4, 64);
        boulderDamage = value(tag, "BoulderDamage", 12, 0, 1000);
        boulderKnockback = value(tag, "BoulderKnockback", 3, 0, 10);
        boulderStopsOnHit = tag.getBoolean("BoulderStopsOnHit");
        boulderShatterRadius = value(tag, "BoulderShatterRadius", 2, 0, 16);
        boulderShatterDamage = value(tag, "BoulderShatterDamage", 4, 0, 1000);
        boulderVfx = AreaVfxStyles.normalize(tag.getString("BoulderVfx"));

        boulderRainEnabled = tag.getBoolean("BoulderRainEnabled");
        boulderRainAnimation = clean(tag.getString("BoulderRainAnimation"));
        boulderRainActionDelayTicks = value(tag, "BoulderRainActionDelayTicks", 16, 0, 1200);
        boulderRainCooldownTicks = value(tag, "BoulderRainCooldownTicks", 240, 1, 12000);
        setBoulderRainRing(
                value(tag, "BoulderRainRadius", 12, 2, 48),
                value(tag, "BoulderRainMinRadius", 0, 0, 47));
        boulderRainCount = value(tag, "BoulderRainCount", 8, 1, 32);
        boulderRainIntervalTicks = value(tag, "BoulderRainIntervalTicks", 4, 0, 100);
        boulderRainFallHeight = value(tag, "BoulderRainFallHeight", 16, 4, 48);
        boulderRainBlock = tag.contains("BoulderRainBlock")
                ? clean(tag.getString("BoulderRainBlock")) : "minecraft:stone";
        boulderRainStyle = BoulderStyles.normalize(tag.getString("BoulderRainStyle"));
        boulderRainScale = value(tag, "BoulderRainScale", 12, 5, 40);
        boulderRainDamage = value(tag, "BoulderRainDamage", 10, 0, 1000);
        boulderRainKnockback = value(tag, "BoulderRainKnockback", 2, 0, 10);
        boulderRainShatterRadius = value(tag, "BoulderRainShatterRadius", 2, 0, 16);
        boulderRainShatterDamage = value(tag, "BoulderRainShatterDamage", 4, 0, 1000);
        boulderRainVfx = AreaVfxStyles.normalize(tag.getString("BoulderRainVfx"));

        tetherEnabled = tag.getBoolean("TetherEnabled");
        tetherAnimation = clean(tag.getString("TetherAnimation"));
        tetherActionDelayTicks = value(tag, "TetherActionDelayTicks", 16, 0, 1200);
        tetherCooldownTicks = value(tag, "TetherCooldownTicks", 300, 1, 12000);
        tetherTargetMode = value(tag, "TetherTargetMode",
                BossTargetMode.RANDOM, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        tetherTargetCount = value(tag, "TetherTargetCount", 2, 1, 8);
        tetherAnchor = value(tag, "TetherAnchor", TETHER_ANCHOR_BOSS, TETHER_ANCHOR_BOSS, TETHER_ANCHOR_PAIR);
        tetherBreakDistance = value(tag, "TetherBreakDistance", 10, 3, 48);
        tetherDurationTicks = value(tag, "TetherDurationTicks", 120, 20, 1200);
        tetherPull = value(tag, "TetherPull", 0, 0, 10);
        tetherFailDamage = value(tag, "TetherFailDamage", 12, 0, 1000);
        // An absent key reads as an empty string, which normalizes back to the plain sparks.
        tetherStyle = HookCordStyles.normalize(tag.getString("TetherStyle"));
        tetherWidthPercent = value(tag, "TetherWidthPercent", 100, 25, 400);

        gravityEnabled = tag.getBoolean("GravityEnabled");
        gravityAnimation = clean(tag.getString("GravityAnimation"));
        gravityActionDelayTicks = value(tag, "GravityActionDelayTicks", 20, 0, 1200);
        gravityCooldownTicks = value(tag, "GravityCooldownTicks", 300, 1, 12000);
        gravityMode = value(tag, "GravityMode", GRAVITY_MODE_PULL, GRAVITY_MODE_PULL, GRAVITY_MODE_LIFT);
        gravityRadius = value(tag, "GravityRadius", 16, 3, 48);
        gravityDurationTicks = value(tag, "GravityDurationTicks", 60, 5, 400);
        gravityStrength = value(tag, "GravityStrength", 10, 1, 20);
        gravityTouchRadius = value(tag, "GravityTouchRadius", 2, 1, 6);
        gravityDamage = value(tag, "GravityDamage", 8, 0, 1000);
        gravityVfx = AreaVfxStyles.normalize(tag.getString("GravityVfx"));

        markEnabled = tag.getBoolean("MarkEnabled");
        markAnimation = clean(tag.getString("MarkAnimation"));
        markActionDelayTicks = value(tag, "MarkActionDelayTicks", 12, 0, 1200);
        markCooldownTicks = value(tag, "MarkCooldownTicks", 240, 1, 12000);
        markMode = value(tag, "MarkMode", MARK_MODE_SOAK, MARK_MODE_SOAK, MARK_MODE_SPREAD);
        markTargetMode = value(tag, "MarkTargetMode",
                BossTargetMode.RANDOM, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        markTargetCount = value(tag, "MarkTargetCount", 1, 1, 8);
        markFuseTicks = value(tag, "MarkFuseTicks", 60, 10, 400);
        markRadius = value(tag, "MarkRadius", 4, 1, 16);
        markFollow = tag.getBoolean("MarkFollow");
        markMinPlayers = value(tag, "MarkMinPlayers", 2, 1, 10);
        markDamage = value(tag, "MarkDamage", 30, 0, 1000);
        markFailDamage = value(tag, "MarkFailDamage", 60, 0, 1000);
        markSelfDamage = value(tag, "MarkSelfDamage", 0, 0, 1000);
        markVfx = AreaVfxStyles.normalize(tag.getString("MarkVfx"));

        coverEnabled = tag.getBoolean("CoverEnabled");
        coverAnimation = clean(tag.getString("CoverAnimation"));
        coverActionDelayTicks = value(tag, "CoverActionDelayTicks", 80, 20, 1200);
        coverCooldownTicks = value(tag, "CoverCooldownTicks", 500, 1, 12000);
        coverMode = value(tag, "CoverMode", COVER_MODE_SIGHT, COVER_MODE_SIGHT, COVER_MODE_SHELTER);
        coverRange = value(tag, "CoverRange", 40, 4, 96);
        coverDamage = value(tag, "CoverDamage", 40, 0, 1000);
        coverKnockback = value(tag, "CoverKnockback", 2, 0, 10);
        coverShelterCount = value(tag, "CoverShelterCount", 2, 1, 6);
        coverShelterRadius = value(tag, "CoverShelterRadius", 3, 1, 16);
        setCoverShelterRing(
                value(tag, "CoverShelterMinRange", 4, 1, 48),
                value(tag, "CoverShelterMaxRange", 14, 2, 64));
        coverVfx = AreaVfxStyles.normalize(tag.getString("CoverVfx"));

        hazardEnabled = tag.getBoolean("HazardEnabled");
        hazardMode = value(tag, "HazardMode", HAZARD_MODE_RING, HAZARD_MODE_RING, HAZARD_MODE_BOX);
        hazardDelayTicks = value(tag, "HazardDelayTicks", 200, 0, 12000);
        hazardWarnTicks = value(tag, "HazardWarnTicks", 60, 0, 600);
        hazardCenterMode = value(tag, "HazardCenterMode", HAZARD_CENTER_BOSS,
                HAZARD_CENTER_BOSS, HAZARD_CENTER_POINT);
        setHazardCenter(
                value(tag, "HazardCenterX", 0, -MAX_HAZARD_COORDINATE, MAX_HAZARD_COORDINATE),
                value(tag, "HazardCenterZ", 0, -MAX_HAZARD_COORDINATE, MAX_HAZARD_COORDINATE));
        setHazardRadii(
                value(tag, "HazardStartRadius", 30, 2, 128),
                value(tag, "HazardEndRadius", 6, 1, 127));
        hazardShrinkTicks = value(tag, "HazardShrinkTicks", 1200, 20, 24000);
        setHazardCorner1(
                value(tag, "HazardX1", 0, -MAX_HAZARD_COORDINATE, MAX_HAZARD_COORDINATE),
                value(tag, "HazardY1", 0, -MAX_HAZARD_COORDINATE, MAX_HAZARD_COORDINATE),
                value(tag, "HazardZ1", 0, -MAX_HAZARD_COORDINATE, MAX_HAZARD_COORDINATE));
        setHazardCorner2(
                value(tag, "HazardX2", 0, -MAX_HAZARD_COORDINATE, MAX_HAZARD_COORDINATE),
                value(tag, "HazardY2", 0, -MAX_HAZARD_COORDINATE, MAX_HAZARD_COORDINATE),
                value(tag, "HazardZ2", 0, -MAX_HAZARD_COORDINATE, MAX_HAZARD_COORDINATE));
        hazardDamage = value(tag, "HazardDamage", 4, 0, 1000);
        hazardIntervalTicks = value(tag, "HazardIntervalTicks", 20, 1, 200);

        huntEnabled = tag.getBoolean("HuntEnabled");
        huntAnimation = clean(tag.getString("HuntAnimation"));
        huntActionDelayTicks = value(tag, "HuntActionDelayTicks", 10, 0, 1200);
        huntCooldownTicks = value(tag, "HuntCooldownTicks", 400, 1, 12000);
        huntTargetMode = value(tag, "HuntTargetMode",
                BossTargetMode.FARTHEST, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        huntDurationTicks = value(tag, "HuntDurationTicks", 160, 20, 1200);
        huntSpeedPercent = value(tag, "HuntSpeedPercent", 130, 50, 300);
        huntCatchRadius = value(tag, "HuntCatchRadius", 2, 1, 6);
        huntDamage = value(tag, "HuntDamage", 15, 0, 1000);
        // The two that default to on read an absent key as on, the way the immune phase's
        // immediate summon does.
        huntCatchEnds = !tag.contains("HuntCatchEnds") || tag.getBoolean("HuntCatchEnds");
        huntSilence = tag.getBoolean("HuntSilence");
        huntGlow = !tag.contains("HuntGlow") || tag.getBoolean("HuntGlow");

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

        invulnerableEnabled = tag.getBoolean("InvulnerableEnabled");
        invulnerableEndMode = value(tag, "InvulnerableEndMode", INVULNERABLE_END_TIMER_OR_MINIONS,
                INVULNERABLE_END_TIMER, INVULNERABLE_END_TIMER_AND_MINIONS);
        invulnerableDurationTicks = value(tag, "InvulnerableDurationTicks", 200, 20, 12000);
        invulnerableAllowTeleport = tag.getBoolean("InvulnerableAllowTeleport");
        invulnerableSummonImmediately = !tag.contains("InvulnerableSummonImmediately")
                || tag.getBoolean("InvulnerableSummonImmediately");

        areaAttackEffects.readFromNBT(tag, "AreaAttackEffects");
        lineAttackEffects.readFromNBT(tag, "LineAttackEffects");
        rangedAttackEffects.readFromNBT(tag, "RangedAttackEffects");
        meleeAttackEffects.readFromNBT(tag, "MeleeAttackEffects");
        fluidSpitEffects.readFromNBT(tag, "FluidSpitEffects");
        hookEffects.readFromNBT(tag, "HookEffects");
        captureEffects.readFromNBT(tag, "CaptureEffects");
        leapEffects.readFromNBT(tag, "LeapEffects");
        geyserEffects.readFromNBT(tag, "GeyserEffects");
        boulderEffects.readFromNBT(tag, "BoulderEffects");
        boulderRainEffects.readFromNBT(tag, "BoulderRainEffects");
        tetherEffects.readFromNBT(tag, "TetherEffects");
        tetherFailEffects.readFromNBT(tag, "TetherFailEffects");
        gravityEffects.readFromNBT(tag, "GravityEffects");
        markEffects.readFromNBT(tag, "MarkEffects");
        markFailEffects.readFromNBT(tag, "MarkFailEffects");
        coverEffects.readFromNBT(tag, "CoverEffects");
        hazardEffects.readFromNBT(tag, "HazardEffects");
        huntEffects.readFromNBT(tag, "HuntEffects");
    }

    private static int value(CompoundTag tag, String key, int fallback, int min, int max) {
        return tag.contains(key) ? Mth.clamp(tag.getInt(key), min, max) : fallback;
    }

    public int getStartHealthPercent() { return startHealthPercent; }
    public void setStartHealthPercent(int value) { startHealthPercent = Mth.clamp(value, 1, 100); }

    public String getTeleportPreparationAnimation() { return teleportPreparationAnimation; }
    public void setTeleportPreparationAnimation(String value) { teleportPreparationAnimation = clean(value); }
    public int getTeleportPreparationTicks() { return teleportPreparationTicks; }
    public void setTeleportPreparationTicks(int value) { teleportPreparationTicks = Mth.clamp(value, 0, 1200); }
    public String getAppearanceAnimation() { return appearanceAnimation; }
    public void setAppearanceAnimation(String value) { appearanceAnimation = clean(value); }
    public int getAppearanceLockTicks() { return appearanceLockTicks; }
    public void setAppearanceLockTicks(int value) { appearanceLockTicks = Mth.clamp(value, 0, 1200); }
    public int getTeleportMinDelayTicks() { return teleportMinDelayTicks; }
    public int getTeleportMaxDelayTicks() { return teleportMaxDelayTicks; }
    public void setTeleportDelayRange(int min, int max) {
        min = Mth.clamp(min, 10, 1200);
        max = Mth.clamp(max, 10, 1200);
        teleportMinDelayTicks = Math.min(min, max);
        teleportMaxDelayTicks = Math.max(min, max);
    }

    public boolean isSummonEnabled() { return summonEnabled; }
    public void setSummonEnabled(boolean value) { summonEnabled = value; }
    public String getSummonAnimation() { return summonAnimation; }
    public void setSummonAnimation(String value) { summonAnimation = clean(value); }
    public int getSummonActionDelayTicks() { return summonActionDelayTicks; }
    public void setSummonActionDelayTicks(int value) { summonActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getSummonCooldownTicks() { return summonCooldownTicks; }
    public void setSummonCooldownTicks(int value) { summonCooldownTicks = Mth.clamp(value, 20, 12000); }
    public String getMinionCloneName() { return minionCloneName; }
    public void setMinionCloneName(String value) { minionCloneName = clean(value); }
    public int getMinionCloneTab() { return minionCloneTab; }
    public void setMinionCloneTab(int value) { minionCloneTab = Mth.clamp(value, 1, 9); }
    public int getMinionCount() { return minionCount; }
    public void setMinionCount(int value) { minionCount = Mth.clamp(value, 1, 32); }
    public int getMinionRadius() { return minionRadius; }
    public void setMinionRadius(int value) { minionRadius = Mth.clamp(value, 1, 32); }
    public int getMaxAliveMinions() { return maxAliveMinions; }
    public void setMaxAliveMinions(int value) { maxAliveMinions = Mth.clamp(value, 1, 128); }
    public int getMinionSpawnMode() { return minionSpawnMode; }
    public void setMinionSpawnMode(int value) {
        minionSpawnMode = Mth.clamp(value, MINION_SPAWN_RANDOM_RADIUS, MINION_SPAWN_POINTS_THEN_RANDOM);
    }
    public int getMinionSpawnOrder() { return minionSpawnOrder; }
    public void setMinionSpawnOrder(int value) {
        minionSpawnOrder = Mth.clamp(value, MINION_ORDER_LIST, MINION_ORDER_RANDOM);
    }
    public int getMinionPointSearchRadius() { return minionPointSearchRadius; }
    public void setMinionPointSearchRadius(int value) { minionPointSearchRadius = Mth.clamp(value, 0, 4); }
    public boolean isMinionReuseOccupiedPoints() { return minionReuseOccupiedPoints; }
    public void setMinionReuseOccupiedPoints(boolean value) { minionReuseOccupiedPoints = value; }
    public BossMinionSpawnList getMinionSpawnPoints() { return minionSpawnPoints; }
    public boolean canSummon() {
        if (!summonEnabled) {
            return false;
        }
        if (minionSpawnMode == MINION_SPAWN_RANDOM_RADIUS) {
            return !minionCloneName.isEmpty();
        }
        return minionSpawnPoints.hasUsableClone(minionCloneName)
                || (minionSpawnMode == MINION_SPAWN_POINTS_THEN_RANDOM && !minionCloneName.isEmpty());
    }

    public boolean isAreaAttackEnabled() { return areaAttackEnabled; }
    public void setAreaAttackEnabled(boolean value) { areaAttackEnabled = value; }
    public String getAreaAttackAnimation() { return areaAttackAnimation; }
    public void setAreaAttackAnimation(String value) { areaAttackAnimation = clean(value); }
    public int getAreaAttackActionDelayTicks() { return areaAttackActionDelayTicks; }
    public void setAreaAttackActionDelayTicks(int value) { areaAttackActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getAreaAttackCooldownTicks() { return areaAttackCooldownTicks; }
    public void setAreaAttackCooldownTicks(int value) { areaAttackCooldownTicks = Mth.clamp(value, 1, 12000); }
    public int getAreaAttackDamage() { return areaAttackDamage; }
    public void setAreaAttackDamage(int value) { areaAttackDamage = Mth.clamp(value, 1, 1000); }
    public int getAreaAttackRadius() { return areaAttackRadius; }
    public void setAreaAttackRadius(int value) { areaAttackRadius = Mth.clamp(value, 1, 32); }
    public int getAreaAttackKnockback() { return areaAttackKnockback; }
    public void setAreaAttackKnockback(int value) { areaAttackKnockback = Mth.clamp(value, 0, 10); }
    public String getAreaAttackVfx() { return areaAttackVfx; }
    public void setAreaAttackVfx(String value) { areaAttackVfx = AreaVfxStyles.normalize(value); }
    public int getAreaAttackVfxDurationTicks() { return areaAttackVfxDurationTicks; }
    public void setAreaAttackVfxDurationTicks(int value) { areaAttackVfxDurationTicks = Mth.clamp(value, 5, 100); }
    public boolean isAreaAttackBlockWave() { return areaAttackBlockWave; }
    public void setAreaAttackBlockWave(boolean value) { areaAttackBlockWave = value; }

    public boolean isLineAttackEnabled() { return lineAttackEnabled; }
    public void setLineAttackEnabled(boolean value) { lineAttackEnabled = value; }
    public String getLineAttackAnimation() { return lineAttackAnimation; }
    public void setLineAttackAnimation(String value) { lineAttackAnimation = clean(value); }
    public int getLineAttackActionDelayTicks() { return lineAttackActionDelayTicks; }
    public void setLineAttackActionDelayTicks(int value) { lineAttackActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getLineAttackCooldownTicks() { return lineAttackCooldownTicks; }
    public void setLineAttackCooldownTicks(int value) { lineAttackCooldownTicks = Mth.clamp(value, 1, 12000); }
    /** Whether the corridor is laid toward the chosen victim or along the boss' own gaze. */
    public int getLineAttackDirection() { return lineAttackDirection; }
    public void setLineAttackDirection(int value) {
        lineAttackDirection = Mth.clamp(value, LINE_DIRECTION_TARGET, LINE_DIRECTION_FACING);
    }
    /** Whether the wind-up turns the model onto the committed corridor instead of the target. */
    public boolean isLineAttackFaceAxis() { return lineAttackFaceAxis; }
    public void setLineAttackFaceAxis(boolean value) { lineAttackFaceAxis = value; }
    public int getLineAttackTargetMode() { return lineAttackTargetMode; }
    public void setLineAttackTargetMode(int value) { lineAttackTargetMode = BossTargetMode.clamp(value); }
    /** How far down the line the strike reaches, measured flat from the boss. */
    public int getLineAttackLength() { return lineAttackLength; }
    public void setLineAttackLength(int value) { lineAttackLength = Mth.clamp(value, 1, 64); }
    /** The full width of the corridor, so the strike reaches half of this to either side. */
    public int getLineAttackWidth() { return lineAttackWidth; }
    public void setLineAttackWidth(int value) { lineAttackWidth = Mth.clamp(value, 1, 8); }
    /** How far above and below the boss the strike still catches somebody. */
    public int getLineAttackHeight() { return lineAttackHeight; }
    public void setLineAttackHeight(int value) { lineAttackHeight = Mth.clamp(value, 1, 8); }
    public int getLineAttackDamage() { return lineAttackDamage; }
    public void setLineAttackDamage(int value) { lineAttackDamage = Mth.clamp(value, 1, 1000); }
    public int getLineAttackKnockback() { return lineAttackKnockback; }
    public void setLineAttackKnockback(int value) { lineAttackKnockback = Mth.clamp(value, 0, 10); }
    /** Width of the softer band running along each flank of the corridor. */
    public int getLineAttackSideWidth() { return lineAttackSideWidth; }
    public void setLineAttackSideWidth(int value) { lineAttackSideWidth = Mth.clamp(value, 0, 8); }
    /** What the flanks hit for, as a percentage of the corridor's own damage. */
    public int getLineAttackSidePercent() { return lineAttackSidePercent; }
    public void setLineAttackSidePercent(int value) { lineAttackSidePercent = Mth.clamp(value, 10, 100); }
    public String getLineAttackVfx() { return lineAttackVfx; }
    public void setLineAttackVfx(String value) { lineAttackVfx = AreaVfxStyles.normalize(value); }
    public boolean isLineAttackBlockWave() { return lineAttackBlockWave; }
    public void setLineAttackBlockWave(boolean value) { lineAttackBlockWave = value; }

    public boolean isRangedAttackEnabled() { return rangedAttackEnabled; }
    public void setRangedAttackEnabled(boolean value) { rangedAttackEnabled = value; }
    public String getRangedAttackAnimation() { return rangedAttackAnimation; }
    public void setRangedAttackAnimation(String value) { rangedAttackAnimation = clean(value); }
    public int getRangedAttackActionDelayTicks() { return rangedAttackActionDelayTicks; }
    public void setRangedAttackActionDelayTicks(int value) {
        rangedAttackActionDelayTicks = Mth.clamp(value, 0, 1200);
    }
    public int getRangedAttackCooldownTicks() { return rangedAttackCooldownTicks; }
    public void setRangedAttackCooldownTicks(int value) {
        rangedAttackCooldownTicks = Mth.clamp(value, 1, 12000);
    }
    public int getRangedAttackDamage() { return rangedAttackDamage; }
    public void setRangedAttackDamage(int value) { rangedAttackDamage = Mth.clamp(value, 1, 1000); }
    public int getRangedAttackMinRange() { return rangedAttackMinRange; }
    public int getRangedAttackMaxRange() { return rangedAttackMaxRange; }
    public void setRangedAttackRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        rangedAttackMinRange = Math.min(min, max);
        rangedAttackMaxRange = Math.max(min, max);
    }

    public int getRangedAttackTargetMode() { return rangedAttackTargetMode; }
    public void setRangedAttackTargetMode(int value) {
        rangedAttackTargetMode = BossTargetMode.clamp(value);
    }

    public boolean isMeleeAttackEnabled() { return meleeAttackEnabled; }
    public void setMeleeAttackEnabled(boolean value) { meleeAttackEnabled = value; }
    public String getMeleeAttackAnimation() { return meleeAttackAnimation; }
    public void setMeleeAttackAnimation(String value) { meleeAttackAnimation = clean(value); }
    public int getMeleeAttackActionDelayTicks() { return meleeAttackActionDelayTicks; }
    public void setMeleeAttackActionDelayTicks(int value) {
        meleeAttackActionDelayTicks = Mth.clamp(value, 0, 1200);
    }
    public int getMeleeAttackCooldownTicks() { return meleeAttackCooldownTicks; }
    public void setMeleeAttackCooldownTicks(int value) {
        meleeAttackCooldownTicks = Mth.clamp(value, 1, 12000);
    }
    public int getMeleeAttackDamage() { return meleeAttackDamage; }
    public void setMeleeAttackDamage(int value) { meleeAttackDamage = Mth.clamp(value, 1, 1000); }
    public int getMeleeAttackRange() { return meleeAttackRange; }
    public void setMeleeAttackRange(int value) { meleeAttackRange = Mth.clamp(value, 1, 32); }
    public int getMeleeAttackKnockback() { return meleeAttackKnockback; }
    public void setMeleeAttackKnockback(int value) { meleeAttackKnockback = Mth.clamp(value, 0, 10); }
    public int getMeleeAttackTargetMode() { return meleeAttackTargetMode; }
    public void setMeleeAttackTargetMode(int value) {
        meleeAttackTargetMode = BossTargetMode.clamp(value);
    }

    public boolean isFluidSpitEnabled() { return fluidSpitEnabled; }
    public void setFluidSpitEnabled(boolean value) { fluidSpitEnabled = value; }
    public String getFluidSpitAnimation() { return fluidSpitAnimation; }
    public void setFluidSpitAnimation(String value) { fluidSpitAnimation = clean(value); }
    public int getFluidSpitActionDelayTicks() { return fluidSpitActionDelayTicks; }
    public void setFluidSpitActionDelayTicks(int value) {
        fluidSpitActionDelayTicks = Mth.clamp(value, 0, 1200);
    }
    public int getFluidSpitCooldownTicks() { return fluidSpitCooldownTicks; }
    public void setFluidSpitCooldownTicks(int value) {
        fluidSpitCooldownTicks = Mth.clamp(value, 1, 12000);
    }
    /** Block id of the fluid to spit, for example {@code minecraft:lava}. */
    public String getFluidSpitBlock() { return fluidSpitBlock; }
    public void setFluidSpitBlock(String value) { fluidSpitBlock = clean(value); }
    public int getFluidSpitLifetimeTicks() { return fluidSpitLifetimeTicks; }
    public void setFluidSpitLifetimeTicks(int value) {
        fluidSpitLifetimeTicks = Mth.clamp(value, 5, 1200);
    }
    public int getFluidSpitRadius() { return fluidSpitRadius; }
    public void setFluidSpitRadius(int value) { fluidSpitRadius = Mth.clamp(value, 0, 4); }
    public int getFluidSpitDamage() { return fluidSpitDamage; }
    public void setFluidSpitDamage(int value) { fluidSpitDamage = Mth.clamp(value, 0, 1000); }
    public int getFluidSpitMinRange() { return fluidSpitMinRange; }
    public int getFluidSpitMaxRange() { return fluidSpitMaxRange; }
    public void setFluidSpitRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        fluidSpitMinRange = Math.min(min, max);
        fluidSpitMaxRange = Math.max(min, max);
    }
    public int getFluidSpitTargetMode() { return fluidSpitTargetMode; }
    public void setFluidSpitTargetMode(int value) {
        fluidSpitTargetMode = BossTargetMode.clamp(value);
    }
    public boolean canSpitFluid() { return fluidSpitEnabled && !fluidSpitBlock.isEmpty(); }

    public boolean isHookEnabled() { return hookEnabled; }
    public void setHookEnabled(boolean value) { hookEnabled = value; }
    public String getHookAnimation() { return hookAnimation; }
    public void setHookAnimation(String value) { hookAnimation = clean(value); }
    public int getHookActionDelayTicks() { return hookActionDelayTicks; }
    public void setHookActionDelayTicks(int value) { hookActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getHookCooldownTicks() { return hookCooldownTicks; }
    public void setHookCooldownTicks(int value) { hookCooldownTicks = Mth.clamp(value, 1, 12000); }
    public int getHookTargetMode() { return hookTargetMode; }
    public void setHookTargetMode(int value) { hookTargetMode = BossTargetMode.clamp(value); }
    /** How many victims a single cast grabs. */
    public int getHookTargetCount() { return hookTargetCount; }
    public void setHookTargetCount(int value) { hookTargetCount = Mth.clamp(value, 1, 8); }
    public int getHookDamage() { return hookDamage; }
    public void setHookDamage(int value) { hookDamage = Mth.clamp(value, 0, 1000); }
    /** Pull speed in tenths of a block per tick. */
    public int getHookPullStrength() { return hookPullStrength; }
    public void setHookPullStrength(int value) { hookPullStrength = Mth.clamp(value, 1, 20); }
    public int getHookPullDurationTicks() { return hookPullDurationTicks; }
    public void setHookPullDurationTicks(int value) { hookPullDurationTicks = Mth.clamp(value, 1, 200); }
    /** The pull releases once the victim is this close, so they are not ground into the boss. */
    public int getHookStopDistance() { return hookStopDistance; }
    public void setHookStopDistance(int value) { hookStopDistance = Mth.clamp(value, 0, 32); }
    public int getHookMinRange() { return hookMinRange; }
    public int getHookMaxRange() { return hookMaxRange; }
    public void setHookRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        hookMinRange = Math.min(min, max);
        hookMaxRange = Math.max(min, max);
    }

    public int getHookMode() { return hookMode; }
    public void setHookMode(int value) { hookMode = Mth.clamp(value, HOOK_MODE_PULL, HOOK_MODE_CINCH); }
    public String getHookCordStyle() { return hookCordStyle; }
    public void setHookCordStyle(String value) { hookCordStyle = HookCordStyles.normalize(value); }

    public boolean isCaptureEnabled() { return captureEnabled; }
    public void setCaptureEnabled(boolean value) { captureEnabled = value; }
    public String getCaptureAnimation() { return captureAnimation; }
    public void setCaptureAnimation(String value) { captureAnimation = clean(value); }
    public int getCaptureActionDelayTicks() { return captureActionDelayTicks; }
    public void setCaptureActionDelayTicks(int value) { captureActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getCaptureCooldownTicks() { return captureCooldownTicks; }
    public void setCaptureCooldownTicks(int value) { captureCooldownTicks = Mth.clamp(value, 20, 12000); }
    public int getCaptureTargetMode() { return captureTargetMode; }
    public void setCaptureTargetMode(int value) { captureTargetMode = BossTargetMode.clamp(value); }
    public int getCaptureMinRange() { return captureMinRange; }
    public int getCaptureMaxRange() { return captureMaxRange; }
    public void setCaptureRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        captureMinRange = Math.min(min, max);
        captureMaxRange = Math.max(min, max);
    }
    public int getCaptureMode() { return captureMode; }
    public void setCaptureMode(int value) { captureMode = Mth.clamp(value, CAPTURE_MODE_HOLD, CAPTURE_MODE_LIFT); }
    public int getCaptureDurationTicks() { return captureDurationTicks; }
    public void setCaptureDurationTicks(int value) { captureDurationTicks = Mth.clamp(value, 1, 1200); }
    public int getCaptureLiftHeight() { return captureLiftHeight; }
    public void setCaptureLiftHeight(int value) { captureLiftHeight = Mth.clamp(value, 0, 64); }
    public int getCaptureLiftTicks() { return captureLiftTicks; }
    public void setCaptureLiftTicks(int value) { captureLiftTicks = Mth.clamp(value, 1, 1200); }
    public int getCaptureEffectTarget() { return captureEffectTarget; }
    public void setCaptureEffectTarget(int value) {
        captureEffectTarget = Mth.clamp(value, CAPTURE_EFFECT_PLAYER, CAPTURE_EFFECT_BOTH);
    }
    public String getCaptureBeamStyle() { return captureBeamStyle; }
    public void setCaptureBeamStyle(String value) { captureBeamStyle = HookCordStyles.normalize(value); }
    public int getCaptureBeamWidthPercent() { return captureBeamWidthPercent; }
    public void setCaptureBeamWidthPercent(int value) { captureBeamWidthPercent = Mth.clamp(value, 25, 400); }
    public int getCaptureBeamSagPercent() { return captureBeamSagPercent; }
    public void setCaptureBeamSagPercent(int value) { captureBeamSagPercent = Mth.clamp(value, 0, 200); }
    public boolean isCaptureAllowLook() { return captureAllowLook; }
    public void setCaptureAllowLook(boolean value) { captureAllowLook = value; }

    public boolean isLeapEnabled() { return leapEnabled; }
    public void setLeapEnabled(boolean value) { leapEnabled = value; }
    public String getLeapAnimation() { return leapAnimation; }
    public void setLeapAnimation(String value) { leapAnimation = clean(value); }
    /** Played the moment the boss touches down, alongside the slam itself. */
    public String getLeapLandAnimation() { return leapLandAnimation; }
    public void setLeapLandAnimation(String value) { leapLandAnimation = clean(value); }
    public int getLeapActionDelayTicks() { return leapActionDelayTicks; }
    public void setLeapActionDelayTicks(int value) { leapActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getLeapCooldownTicks() { return leapCooldownTicks; }
    public void setLeapCooldownTicks(int value) { leapCooldownTicks = Mth.clamp(value, 1, 12000); }
    public int getLeapMode() { return leapMode; }
    public void setLeapMode(int value) { leapMode = Mth.clamp(value, LEAP_MODE_UP, LEAP_MODE_ARENA_OFFSET); }
    public int getLeapTargetMode() { return leapTargetMode; }
    public void setLeapTargetMode(int value) { leapTargetMode = BossTargetMode.clamp(value); }
    /** How high the arc climbs above the spot the boss pushed off from. */
    public int getLeapHeight() { return leapHeight; }
    public void setLeapHeight(int value) { leapHeight = Mth.clamp(value, 1, MAX_LEAP_HEIGHT); }
    public int getLeapMinRange() { return leapMinRange; }
    public int getLeapMaxRange() { return leapMaxRange; }
    public void setLeapRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        leapMinRange = Math.min(min, max);
        leapMaxRange = Math.max(min, max);
    }
    public int getLeapOffsetX() { return leapOffsetX; }
    public int getLeapOffsetY() { return leapOffsetY; }
    public int getLeapOffsetZ() { return leapOffsetZ; }
    public void setLeapOffset(int x, int y, int z) {
        leapOffsetX = Mth.clamp(x, -64, 64);
        leapOffsetY = Mth.clamp(y, -64, 64);
        leapOffsetZ = Mth.clamp(z, -64, 64);
    }
    public int getLeapFixedX() { return leapFixedX; }
    public int getLeapFixedY() { return leapFixedY; }
    public int getLeapFixedZ() { return leapFixedZ; }
    public void setLeapFixed(int x, int y, int z) {
        leapFixedX = Mth.clamp(x, -MAX_LEAP_COORDINATE, MAX_LEAP_COORDINATE);
        leapFixedY = Mth.clamp(y, -MAX_LEAP_COORDINATE, MAX_LEAP_COORDINATE);
        leapFixedZ = Mth.clamp(z, -MAX_LEAP_COORDINATE, MAX_LEAP_COORDINATE);
    }
    public int getLeapImpactDamage() { return leapImpactDamage; }
    public void setLeapImpactDamage(int value) { leapImpactDamage = Mth.clamp(value, 0, 1000); }
    public int getLeapImpactRadius() { return leapImpactRadius; }
    public void setLeapImpactRadius(int value) { leapImpactRadius = Mth.clamp(value, 1, 32); }
    public int getLeapImpactKnockback() { return leapImpactKnockback; }
    public void setLeapImpactKnockback(int value) { leapImpactKnockback = Mth.clamp(value, 0, 10); }
    public int getLeapMaxAirTicks() { return leapMaxAirTicks; }
    public void setLeapMaxAirTicks(int value) { leapMaxAirTicks = Mth.clamp(value, 20, 400); }
    /** Draws the landing ring through the windup and the flight. */
    public boolean isLeapTelegraph() { return leapTelegraph; }
    public void setLeapTelegraph(boolean value) { leapTelegraph = value; }
    public String getLeapVfx() { return leapVfx; }
    public void setLeapVfx(String value) { leapVfx = AreaVfxStyles.normalize(value); }
    public boolean isLeapBlockWave() { return leapBlockWave; }
    public void setLeapBlockWave(boolean value) { leapBlockWave = value; }

    public boolean isGeyserEnabled() { return geyserEnabled; }
    public void setGeyserEnabled(boolean value) { geyserEnabled = value; }
    public String getGeyserAnimation() { return geyserAnimation; }
    public void setGeyserAnimation(String value) { geyserAnimation = clean(value); }
    public int getGeyserActionDelayTicks() { return geyserActionDelayTicks; }
    public void setGeyserActionDelayTicks(int value) { geyserActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getGeyserCooldownTicks() { return geyserCooldownTicks; }
    public void setGeyserCooldownTicks(int value) { geyserCooldownTicks = Mth.clamp(value, 1, 12000); }
    public int getGeyserTargetMode() { return geyserTargetMode; }
    public void setGeyserTargetMode(int value) { geyserTargetMode = BossTargetMode.clamp(value); }
    /** How many marks a single cast puts on the floor, one under each victim it picked. */
    public int getGeyserTargetCount() { return geyserTargetCount; }
    public void setGeyserTargetCount(int value) { geyserTargetCount = Mth.clamp(value, 1, 8); }
    public int getGeyserMinRange() { return geyserMinRange; }
    public int getGeyserMaxRange() { return geyserMaxRange; }
    public void setGeyserRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        geyserMinRange = Math.min(min, max);
        geyserMaxRange = Math.max(min, max);
    }
    /** The window a victim has to walk out of the circle, which is the whole mechanic. */
    public int getGeyserFuseTicks() { return geyserFuseTicks; }
    public void setGeyserFuseTicks(int value) { geyserFuseTicks = Mth.clamp(value, 5, 200); }
    public int getGeyserRadius() { return geyserRadius; }
    public void setGeyserRadius(int value) { geyserRadius = Mth.clamp(value, 1, 16); }
    public int getGeyserDamage() { return geyserDamage; }
    public void setGeyserDamage(int value) { geyserDamage = Mth.clamp(value, 0, 1000); }
    /** Upward throw in tenths of a block per tick; zero leaves the victim on the floor. */
    public int getGeyserLaunch() { return geyserLaunch; }
    public void setGeyserLaunch(int value) { geyserLaunch = Mth.clamp(value, 0, 20); }
    /** With this on the mark rides the victim, so there is nowhere to step out to. */
    public boolean isGeyserFollowTarget() { return geyserFollowTarget; }
    public void setGeyserFollowTarget(boolean value) { geyserFollowTarget = value; }
    public String getGeyserFluid() { return geyserFluid; }
    public void setGeyserFluid(String value) { geyserFluid = clean(value); }
    public int getGeyserFluidLifetimeTicks() { return geyserFluidLifetimeTicks; }
    public void setGeyserFluidLifetimeTicks(int value) {
        geyserFluidLifetimeTicks = Mth.clamp(value, 5, 1200);
    }
    public String getGeyserVfx() { return geyserVfx; }
    public void setGeyserVfx(String value) { geyserVfx = AreaVfxStyles.normalize(value); }
    public boolean isGeyserBlockWave() { return geyserBlockWave; }
    public void setGeyserBlockWave(boolean value) { geyserBlockWave = value; }
    /** Whether the eruption pools anything, i.e. whether the fluid id is worth resolving. */
    public boolean leavesGeyserFluid() { return !geyserFluid.isEmpty(); }

    public boolean isBoulderEnabled() { return boulderEnabled; }
    public void setBoulderEnabled(boolean value) { boulderEnabled = value; }
    public String getBoulderAnimation() { return boulderAnimation; }
    public void setBoulderAnimation(String value) { boulderAnimation = clean(value); }
    public int getBoulderActionDelayTicks() { return boulderActionDelayTicks; }
    public void setBoulderActionDelayTicks(int value) { boulderActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getBoulderCooldownTicks() { return boulderCooldownTicks; }
    public void setBoulderCooldownTicks(int value) { boulderCooldownTicks = Mth.clamp(value, 1, 12000); }
    /** Whether the stone rolls along the floor or is thrown in an arc. */
    public int getBoulderMode() { return boulderMode; }
    public void setBoulderMode(int value) {
        boulderMode = Mth.clamp(value, BOULDER_MODE_ROLL, BOULDER_MODE_THROW);
    }
    public int getBoulderTargetMode() { return boulderTargetMode; }
    public void setBoulderTargetMode(int value) { boulderTargetMode = BossTargetMode.clamp(value); }
    /** Block id the stone is drawn as, for example {@code minecraft:deepslate}. */
    public String getBoulderBlock() { return boulderBlock; }
    public void setBoulderBlock(String value) { boulderBlock = clean(value); }
    public String getBoulderStyle() { return boulderStyle; }
    public void setBoulderStyle(String value) { boulderStyle = BoulderStyles.normalize(value); }
    /** Diameter in tenths of a block. */
    public int getBoulderScale() { return boulderScale; }
    public void setBoulderScale(int value) { boulderScale = Mth.clamp(value, 5, 40); }
    /** Travel speed in tenths of a block per tick. */
    public int getBoulderSpeed() { return boulderSpeed; }
    public void setBoulderSpeed(int value) { boulderSpeed = Mth.clamp(value, 1, 20); }
    /** How far down the corridor the stone travels before breaking apart on its own. */
    public int getBoulderRange() { return boulderRange; }
    public void setBoulderRange(int value) { boulderRange = Mth.clamp(value, 4, 64); }
    public int getBoulderDamage() { return boulderDamage; }
    public void setBoulderDamage(int value) { boulderDamage = Mth.clamp(value, 0, 1000); }
    public int getBoulderKnockback() { return boulderKnockback; }
    public void setBoulderKnockback(int value) { boulderKnockback = Mth.clamp(value, 0, 10); }
    /** Off rolls through the whole line; on breaks the stone on the first victim it hits. */
    public boolean isBoulderStopsOnHit() { return boulderStopsOnHit; }
    public void setBoulderStopsOnHit(boolean value) { boulderStopsOnHit = value; }
    public int getBoulderShatterRadius() { return boulderShatterRadius; }
    public void setBoulderShatterRadius(int value) { boulderShatterRadius = Mth.clamp(value, 0, 16); }
    public int getBoulderShatterDamage() { return boulderShatterDamage; }
    public void setBoulderShatterDamage(int value) { boulderShatterDamage = Mth.clamp(value, 0, 1000); }
    public String getBoulderVfx() { return boulderVfx; }
    public void setBoulderVfx(String value) { boulderVfx = AreaVfxStyles.normalize(value); }
    /** Whether the ability is worth scheduling: on, and with a block to be made of. */
    public boolean canLaunchBoulder() { return boulderEnabled && !boulderBlock.isEmpty(); }

    public boolean isBoulderRainEnabled() { return boulderRainEnabled; }
    public void setBoulderRainEnabled(boolean value) { boulderRainEnabled = value; }
    public String getBoulderRainAnimation() { return boulderRainAnimation; }
    public void setBoulderRainAnimation(String value) { boulderRainAnimation = clean(value); }
    public int getBoulderRainActionDelayTicks() { return boulderRainActionDelayTicks; }
    public void setBoulderRainActionDelayTicks(int value) {
        boulderRainActionDelayTicks = Mth.clamp(value, 0, 1200);
    }
    public int getBoulderRainCooldownTicks() { return boulderRainCooldownTicks; }
    public void setBoulderRainCooldownTicks(int value) {
        boulderRainCooldownTicks = Mth.clamp(value, 1, 12000);
    }
    public int getBoulderRainRadius() { return boulderRainRadius; }
    public int getBoulderRainMinRadius() { return boulderRainMinRadius; }
    /**
     * The ring the volley falls in, set as the pair it is read as.
     *
     * <p>The inner edge is held under the outer one: a dead zone as wide as the ring would
     * leave the cast with nowhere left to drop a stone.</p>
     */
    public void setBoulderRainRing(int radius, int minRadius) {
        boulderRainRadius = Mth.clamp(radius, 2, 48);
        boulderRainMinRadius = Mth.clamp(minRadius, 0, boulderRainRadius - 1);
    }
    public int getBoulderRainCount() { return boulderRainCount; }
    public void setBoulderRainCount(int value) { boulderRainCount = Mth.clamp(value, 1, 32); }
    public int getBoulderRainIntervalTicks() { return boulderRainIntervalTicks; }
    public void setBoulderRainIntervalTicks(int value) {
        boulderRainIntervalTicks = Mth.clamp(value, 0, 100);
    }
    public int getBoulderRainFallHeight() { return boulderRainFallHeight; }
    public void setBoulderRainFallHeight(int value) { boulderRainFallHeight = Mth.clamp(value, 4, 48); }
    public String getBoulderRainBlock() { return boulderRainBlock; }
    public void setBoulderRainBlock(String value) { boulderRainBlock = clean(value); }
    public String getBoulderRainStyle() { return boulderRainStyle; }
    public void setBoulderRainStyle(String value) { boulderRainStyle = BoulderStyles.normalize(value); }
    public int getBoulderRainScale() { return boulderRainScale; }
    public void setBoulderRainScale(int value) { boulderRainScale = Mth.clamp(value, 5, 40); }
    public int getBoulderRainDamage() { return boulderRainDamage; }
    public void setBoulderRainDamage(int value) { boulderRainDamage = Mth.clamp(value, 0, 1000); }
    public int getBoulderRainKnockback() { return boulderRainKnockback; }
    public void setBoulderRainKnockback(int value) { boulderRainKnockback = Mth.clamp(value, 0, 10); }
    public int getBoulderRainShatterRadius() { return boulderRainShatterRadius; }
    public void setBoulderRainShatterRadius(int value) {
        boulderRainShatterRadius = Mth.clamp(value, 0, 16);
    }
    public int getBoulderRainShatterDamage() { return boulderRainShatterDamage; }
    public void setBoulderRainShatterDamage(int value) {
        boulderRainShatterDamage = Mth.clamp(value, 0, 1000);
    }
    public String getBoulderRainVfx() { return boulderRainVfx; }
    public void setBoulderRainVfx(String value) { boulderRainVfx = AreaVfxStyles.normalize(value); }

    public boolean canLaunchBoulderRain() { return boulderRainEnabled && !boulderRainBlock.isEmpty(); }

    public boolean isTetherEnabled() { return tetherEnabled; }
    public void setTetherEnabled(boolean value) { tetherEnabled = value; }
    public String getTetherAnimation() { return tetherAnimation; }
    public void setTetherAnimation(String value) { tetherAnimation = clean(value); }
    public int getTetherActionDelayTicks() { return tetherActionDelayTicks; }
    public void setTetherActionDelayTicks(int value) { tetherActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getTetherCooldownTicks() { return tetherCooldownTicks; }
    public void setTetherCooldownTicks(int value) { tetherCooldownTicks = Mth.clamp(value, 1, 12000); }
    public int getTetherTargetMode() { return tetherTargetMode; }
    public void setTetherTargetMode(int value) { tetherTargetMode = BossTargetMode.clamp(value); }
    /** How many victims one cast leashes; in the pair mode they are leashed two by two. */
    public int getTetherTargetCount() { return tetherTargetCount; }
    public void setTetherTargetCount(int value) { tetherTargetCount = Mth.clamp(value, 1, 8); }
    /** What the leash is tied to: the boss, the ground under the victim, or another victim. */
    public int getTetherAnchor() { return tetherAnchor; }
    public void setTetherAnchor(int value) {
        tetherAnchor = Mth.clamp(value, TETHER_ANCHOR_BOSS, TETHER_ANCHOR_PAIR);
    }
    public int getTetherBreakDistance() { return tetherBreakDistance; }
    public void setTetherBreakDistance(int value) { tetherBreakDistance = Mth.clamp(value, 3, 48); }
    public int getTetherDurationTicks() { return tetherDurationTicks; }
    public void setTetherDurationTicks(int value) { tetherDurationTicks = Mth.clamp(value, 20, 1200); }
    /** Drag toward the anchor, 0 to 10. Zero only times the victim out; it never moves them. */
    public int getTetherPull() { return tetherPull; }
    public void setTetherPull(int value) { tetherPull = Mth.clamp(value, 0, 10); }
    public int getTetherFailDamage() { return tetherFailDamage; }
    public void setTetherFailDamage(int value) { tetherFailDamage = Mth.clamp(value, 0, 1000); }
    public String getTetherStyle() { return tetherStyle; }
    public void setTetherStyle(String value) { tetherStyle = HookCordStyles.normalize(value); }
    public int getTetherWidthPercent() { return tetherWidthPercent; }
    public void setTetherWidthPercent(int value) { tetherWidthPercent = Mth.clamp(value, 25, 400); }

    public boolean isGravityEnabled() { return gravityEnabled; }
    public void setGravityEnabled(boolean value) { gravityEnabled = value; }
    public String getGravityAnimation() { return gravityAnimation; }
    public void setGravityAnimation(String value) { gravityAnimation = clean(value); }
    public int getGravityActionDelayTicks() { return gravityActionDelayTicks; }
    public void setGravityActionDelayTicks(int value) { gravityActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getGravityCooldownTicks() { return gravityCooldownTicks; }
    public void setGravityCooldownTicks(int value) { gravityCooldownTicks = Mth.clamp(value, 1, 12000); }
    /** Which way the field works: in, out, or up. */
    public int getGravityMode() { return gravityMode; }
    public void setGravityMode(int value) {
        gravityMode = Mth.clamp(value, GRAVITY_MODE_PULL, GRAVITY_MODE_LIFT);
    }
    public int getGravityRadius() { return gravityRadius; }
    public void setGravityRadius(int value) { gravityRadius = Mth.clamp(value, 3, 48); }
    /** How long a pull or a push runs for; a throw ignores it. */
    public int getGravityDurationTicks() { return gravityDurationTicks; }
    public void setGravityDurationTicks(int value) { gravityDurationTicks = Mth.clamp(value, 5, 400); }
    /** Hundredths of a block per tick for the pull and the push, tenths for the throw. */
    public int getGravityStrength() { return gravityStrength; }
    public void setGravityStrength(int value) { gravityStrength = Mth.clamp(value, 1, 20); }
    /** How close to the boss the pull has to get somebody before it starts to hurt them. */
    public int getGravityTouchRadius() { return gravityTouchRadius; }
    public void setGravityTouchRadius(int value) { gravityTouchRadius = Mth.clamp(value, 1, 6); }
    /** What the pull's bite and the throw's landing hit for; the push never hurts. */
    public int getGravityDamage() { return gravityDamage; }
    public void setGravityDamage(int value) { gravityDamage = Mth.clamp(value, 0, 1000); }
    public String getGravityVfx() { return gravityVfx; }
    public void setGravityVfx(String value) { gravityVfx = AreaVfxStyles.normalize(value); }

    public boolean isMarkEnabled() { return markEnabled; }
    public void setMarkEnabled(boolean value) { markEnabled = value; }
    public String getMarkAnimation() { return markAnimation; }
    public void setMarkAnimation(String value) { markAnimation = clean(value); }
    public int getMarkActionDelayTicks() { return markActionDelayTicks; }
    public void setMarkActionDelayTicks(int value) { markActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getMarkCooldownTicks() { return markCooldownTicks; }
    public void setMarkCooldownTicks(int value) { markCooldownTicks = Mth.clamp(value, 1, 12000); }
    /** Which of the two rules the mark goes off by: gather up, or spread out. */
    public int getMarkMode() { return markMode; }
    public void setMarkMode(int value) {
        markMode = Mth.clamp(value, MARK_MODE_SOAK, MARK_MODE_SPREAD);
    }
    public int getMarkTargetMode() { return markTargetMode; }
    public void setMarkTargetMode(int value) { markTargetMode = BossTargetMode.clamp(value); }
    /** How many marks one cast hands out. */
    public int getMarkTargetCount() { return markTargetCount; }
    public void setMarkTargetCount(int value) { markTargetCount = Mth.clamp(value, 1, 8); }
    public int getMarkFuseTicks() { return markFuseTicks; }
    public void setMarkFuseTicks(int value) { markFuseTicks = Mth.clamp(value, 10, 400); }
    public int getMarkRadius() { return markRadius; }
    public void setMarkRadius(int value) { markRadius = Mth.clamp(value, 1, 16); }
    /** Whether the circle rides its carrier or stays where they were standing at the cast. */
    public boolean isMarkFollow() { return markFollow; }
    public void setMarkFollow(boolean value) { markFollow = value; }
    public int getMarkMinPlayers() { return markMinPlayers; }
    public void setMarkMinPlayers(int value) { markMinPlayers = Mth.clamp(value, 1, 10); }
    public int getMarkDamage() { return markDamage; }
    public void setMarkDamage(int value) { markDamage = Mth.clamp(value, 0, 1000); }
    public int getMarkFailDamage() { return markFailDamage; }
    public void setMarkFailDamage(int value) { markFailDamage = Mth.clamp(value, 0, 1000); }
    public int getMarkSelfDamage() { return markSelfDamage; }
    public void setMarkSelfDamage(int value) { markSelfDamage = Mth.clamp(value, 0, 1000); }
    public String getMarkVfx() { return markVfx; }
    public void setMarkVfx(String value) { markVfx = AreaVfxStyles.normalize(value); }

    public boolean isCoverEnabled() { return coverEnabled; }
    public void setCoverEnabled(boolean value) { coverEnabled = value; }
    public String getCoverAnimation() { return coverAnimation; }
    public void setCoverAnimation(String value) { coverAnimation = clean(value); }
    /** The time everyone gets to hide, which is also the only warning: never under a second. */
    public int getCoverActionDelayTicks() { return coverActionDelayTicks; }
    public void setCoverActionDelayTicks(int value) { coverActionDelayTicks = Mth.clamp(value, 20, 1200); }
    public int getCoverCooldownTicks() { return coverCooldownTicks; }
    public void setCoverCooldownTicks(int value) { coverCooldownTicks = Mth.clamp(value, 1, 12000); }
    /** Which of the two ways out spares somebody: out of sight, or inside a shelter. */
    public int getCoverMode() { return coverMode; }
    public void setCoverMode(int value) {
        coverMode = Mth.clamp(value, COVER_MODE_SIGHT, COVER_MODE_SHELTER);
    }
    public int getCoverRange() { return coverRange; }
    public void setCoverRange(int value) { coverRange = Mth.clamp(value, 4, 96); }
    public int getCoverDamage() { return coverDamage; }
    public void setCoverDamage(int value) { coverDamage = Mth.clamp(value, 0, 1000); }
    public int getCoverKnockback() { return coverKnockback; }
    public void setCoverKnockback(int value) { coverKnockback = Mth.clamp(value, 0, 10); }
    public int getCoverShelterCount() { return coverShelterCount; }
    public void setCoverShelterCount(int value) { coverShelterCount = Mth.clamp(value, 1, 6); }
    public int getCoverShelterRadius() { return coverShelterRadius; }
    public void setCoverShelterRadius(int value) { coverShelterRadius = Mth.clamp(value, 1, 16); }
    public int getCoverShelterMinRange() { return coverShelterMinRange; }
    public int getCoverShelterMaxRange() { return coverShelterMaxRange; }
    /**
     * The ring the shelters are scattered in, set as the pair it is read as.
     *
     * <p>The inner edge is held under the outer one, the way the boulder rain's is: a ring
     * with no width would leave the wind-up with nowhere to put a shelter down.</p>
     */
    public void setCoverShelterRing(int min, int max) {
        coverShelterMaxRange = Mth.clamp(max, 2, 64);
        coverShelterMinRange = Mth.clamp(min, 1, Math.min(48, coverShelterMaxRange - 1));
    }
    public String getCoverVfx() { return coverVfx; }
    public void setCoverVfx(String value) { coverVfx = AreaVfxStyles.normalize(value); }

    /** Whether the arena turns dangerous in this phase at all. */
    public boolean isHazardEnabled() { return hazardEnabled; }
    public void setHazardEnabled(boolean value) { hazardEnabled = value; }
    /** Which shape the danger takes: a ring closing in, or a box switching on. */
    public int getHazardMode() { return hazardMode; }
    public void setHazardMode(int value) {
        hazardMode = Mth.clamp(value, HAZARD_MODE_RING, HAZARD_MODE_BOX);
    }
    /** Ticks after the phase begins before the arena starts to hurt. */
    public int getHazardDelayTicks() { return hazardDelayTicks; }
    public void setHazardDelayTicks(int value) { hazardDelayTicks = Mth.clamp(value, 0, 12000); }
    /** Ticks of flashing edge and countdown in front of that. */
    public int getHazardWarnTicks() { return hazardWarnTicks; }
    public void setHazardWarnTicks(int value) { hazardWarnTicks = Mth.clamp(value, 0, 600); }
    /** Ring: whether the circle closes in on the boss' spot at phase entry or on a fixed point. */
    public int getHazardCenterMode() { return hazardCenterMode; }
    public void setHazardCenterMode(int value) {
        hazardCenterMode = Mth.clamp(value, HAZARD_CENTER_BOSS, HAZARD_CENTER_POINT);
    }
    public int getHazardCenterX() { return hazardCenterX; }
    public int getHazardCenterZ() { return hazardCenterZ; }
    public void setHazardCenter(int x, int z) {
        hazardCenterX = hazardCoordinate(x);
        hazardCenterZ = hazardCoordinate(z);
    }
    public int getHazardStartRadius() { return hazardStartRadius; }
    public int getHazardEndRadius() { return hazardEndRadius; }
    /**
     * Where the safe circle starts and where it stops, set as the pair they are read as.
     *
     * <p>The end is held under the start, the way the boulder rain's inner edge is held under
     * its outer one: a circle that closes to where it began, or grows, is not a ring closing
     * in, and the shrink would have nothing to do.</p>
     */
    public void setHazardRadii(int start, int end) {
        hazardStartRadius = Mth.clamp(start, 2, 128);
        hazardEndRadius = Mth.clamp(end, 1, hazardStartRadius - 1);
    }
    /** Ring: how long the circle takes to close from the start radius to the end one. */
    public int getHazardShrinkTicks() { return hazardShrinkTicks; }
    public void setHazardShrinkTicks(int value) { hazardShrinkTicks = Mth.clamp(value, 20, 24000); }
    public int getHazardX1() { return hazardX1; }
    public int getHazardY1() { return hazardY1; }
    public int getHazardZ1() { return hazardZ1; }
    public int getHazardX2() { return hazardX2; }
    public int getHazardY2() { return hazardY2; }
    public int getHazardZ2() { return hazardZ2; }
    public void setHazardCorner1(int x, int y, int z) {
        hazardX1 = hazardCoordinate(x);
        hazardY1 = hazardCoordinate(y);
        hazardZ1 = hazardCoordinate(z);
    }
    public void setHazardCorner2(int x, int y, int z) {
        hazardX2 = hazardCoordinate(x);
        hazardY2 = hazardCoordinate(y);
        hazardZ2 = hazardCoordinate(z);
    }
    /** What one dose of the hazard hits for; zero leaves only the effects. */
    public int getHazardDamage() { return hazardDamage; }
    public void setHazardDamage(int value) { hazardDamage = Mth.clamp(value, 0, 1000); }
    /** Ticks between one dose and the next. */
    public int getHazardIntervalTicks() { return hazardIntervalTicks; }
    public void setHazardIntervalTicks(int value) { hazardIntervalTicks = Mth.clamp(value, 1, 200); }
    private static int hazardCoordinate(int value) {
        return Mth.clamp(value, -MAX_HAZARD_COORDINATE, MAX_HAZARD_COORDINATE);
    }

    /** Whether the boss ever singles one victim out and goes after them in this phase. */
    public boolean isHuntEnabled() { return huntEnabled; }
    public void setHuntEnabled(boolean value) { huntEnabled = value; }
    public String getHuntAnimation() { return huntAnimation; }
    public void setHuntAnimation(String value) { huntAnimation = clean(value); }
    /** The wind-up: the roar before the boss sets off. */
    public int getHuntActionDelayTicks() { return huntActionDelayTicks; }
    public void setHuntActionDelayTicks(int value) { huntActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getHuntCooldownTicks() { return huntCooldownTicks; }
    public void setHuntCooldownTicks(int value) { huntCooldownTicks = Mth.clamp(value, 1, 12000); }
    public int getHuntTargetMode() { return huntTargetMode; }
    public void setHuntTargetMode(int value) { huntTargetMode = BossTargetMode.clamp(value); }
    /** Ticks the boss stays on its prey before it gives the chase up. */
    public int getHuntDurationTicks() { return huntDurationTicks; }
    public void setHuntDurationTicks(int value) { huntDurationTicks = Mth.clamp(value, 20, 1200); }
    /** Walking speed for the length of the chase, as a percentage of the boss' own. */
    public int getHuntSpeedPercent() { return huntSpeedPercent; }
    public void setHuntSpeedPercent(int value) { huntSpeedPercent = Mth.clamp(value, 50, 300); }
    /** How close the boss has to get for the prey to count as caught. */
    public int getHuntCatchRadius() { return huntCatchRadius; }
    public void setHuntCatchRadius(int value) { huntCatchRadius = Mth.clamp(value, 1, 6); }
    /** What catching the prey hits for; zero leaves only the effects. */
    public int getHuntDamage() { return huntDamage; }
    public void setHuntDamage(int value) { huntDamage = Mth.clamp(value, 0, 1000); }
    /** Whether catching the prey ends the chase, or the boss keeps after them until the time is up. */
    public boolean isHuntCatchEnds() { return huntCatchEnds; }
    public void setHuntCatchEnds(boolean value) { huntCatchEnds = value; }
    /** Whether the rest of the rotation waits for the chase to end. */
    public boolean isHuntSilence() { return huntSilence; }
    public void setHuntSilence(boolean value) { huntSilence = value; }
    /** Whether the prey glows for the length of the chase. */
    public boolean isHuntGlow() { return huntGlow; }
    public void setHuntGlow(boolean value) { huntGlow = value; }

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

    /** While this phase runs the boss takes no damage and only its summon ability fires. */
    public boolean isInvulnerableEnabled() { return invulnerableEnabled; }
    public void setInvulnerableEnabled(boolean value) { invulnerableEnabled = value; }
    public int getInvulnerableEndMode() { return invulnerableEndMode; }
    public void setInvulnerableEndMode(int value) {
        invulnerableEndMode = Mth.clamp(value, INVULNERABLE_END_TIMER, INVULNERABLE_END_TIMER_AND_MINIONS);
    }
    public int getInvulnerableDurationTicks() { return invulnerableDurationTicks; }
    public void setInvulnerableDurationTicks(int value) {
        invulnerableDurationTicks = Mth.clamp(value, 20, 12000);
    }
    public boolean isInvulnerableAllowTeleport() { return invulnerableAllowTeleport; }
    public void setInvulnerableAllowTeleport(boolean value) { invulnerableAllowTeleport = value; }
    /** Skips the first summon cooldown so the phase opens with a wave instead of an idle wait. */
    public boolean isInvulnerableSummonImmediately() { return invulnerableSummonImmediately; }
    public void setInvulnerableSummonImmediately(boolean value) { invulnerableSummonImmediately = value; }

    /**
     * Whether dead minions are part of this phase's exit condition.
     *
     * <p>A phase with no clone configured can never satisfy "all minions are dead", so the
     * minion half of the condition is dropped there - otherwise the boss would stay immune
     * for the rest of the fight.</p>
     */
    public boolean invulnerableWaitsForMinions() {
        return canSummon() && invulnerableEndMode != INVULNERABLE_END_TIMER;
    }

    /** Counterpart of {@link #invulnerableWaitsForMinions()}; the two are never both false. */
    public boolean invulnerableWaitsForTimer() {
        return invulnerableEndMode != INVULNERABLE_END_MINIONS_DEAD || !canSummon();
    }

    public BossEffectSet getAreaAttackEffects() { return areaAttackEffects; }
    public BossEffectSet getLineAttackEffects() { return lineAttackEffects; }
    public BossEffectSet getRangedAttackEffects() { return rangedAttackEffects; }
    public BossEffectSet getMeleeAttackEffects() { return meleeAttackEffects; }
    public BossEffectSet getFluidSpitEffects() { return fluidSpitEffects; }
    public BossEffectSet getHookEffects() { return hookEffects; }
    public BossEffectSet getCaptureEffects() { return captureEffects; }
    public BossEffectSet getLeapEffects() { return leapEffects; }
    public BossEffectSet getGeyserEffects() { return geyserEffects; }
    public BossEffectSet getBoulderEffects() { return boulderEffects; }
    public BossEffectSet getBoulderRainEffects() { return boulderRainEffects; }
    public BossEffectSet getTetherEffects() { return tetherEffects; }
    public BossEffectSet getTetherFailEffects() { return tetherFailEffects; }
    public BossEffectSet getGravityEffects() { return gravityEffects; }
    public BossEffectSet getMarkEffects() { return markEffects; }
    public BossEffectSet getMarkFailEffects() { return markFailEffects; }
    public BossEffectSet getCoverEffects() { return coverEffects; }
    public BossEffectSet getHazardEffects() { return hazardEffects; }
    public BossEffectSet getHuntEffects() { return huntEffects; }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
