package com.goodbird.cnpcgeckoaddon.data;

import com.goodbird.cnpcgeckoaddon.utils.ContainerBlockUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.List;

/** Complete per-NPC configuration for the stationary, multi-phase boss mechanic. */
public final class TeleportPathData {
    public static final int ORDER_SEQUENTIAL = 0;
    public static final int ORDER_PING_PONG = 1;
    public static final int ORDER_RANDOM = 2;

    public static final int MIN_PHASES = 1;
    public static final int MAX_PHASES = 8;

    public static final int MIN_BOSS_BAR_SCALE_PERCENT = 25;
    public static final int MAX_BOSS_BAR_SCALE_PERCENT = 150;
    /** The size a style was drawn at before the setting existed, so old bosses keep it. */
    public static final int DEFAULT_BOSS_BAR_SCALE_PERCENT = 100;

    public static final int MIN_RESET_TICKS = 20;
    public static final int MAX_RESET_TICKS = 12000;
    public static final int MIN_HOME_LEASH_RADIUS = 1;
    public static final int MAX_HOME_LEASH_RADIUS = 512;
    public static final int MIN_HOME_LEASH_GRACE_TICKS = 0;
    public static final int MAX_HOME_LEASH_GRACE_TICKS = 1200;

    /**
     * Which kinds of living entity a boss ability may pick as its target.
     *
     * <p>Defaults to players and NPCs: a boss dropped into a dungeon full of hostile NPCs
     * is expected to fight them, while {@link #ABILITY_TARGET_ALL} is opt-in because it
     * lets a wandering cow become the "farthest target".</p>
     */
    public static final int ABILITY_TARGET_PLAYERS = 0;
    public static final int ABILITY_TARGET_PLAYERS_AND_NPCS = 1;
    public static final int ABILITY_TARGET_ALL = 2;
    public static final String[] ABILITY_TARGET_KIND_LABELS = {
            "cnpcgeckoaddon.boss.ability_target_players",
            "cnpcgeckoaddon.boss.ability_target_players_npcs",
            "cnpcgeckoaddon.boss.ability_target_all"
    };

    public static final int AGGRO_ZONE_TARGET_NEAREST = 0;
    public static final int AGGRO_ZONE_TARGET_RANDOM = 1;
    public static final String[] AGGRO_ZONE_TARGET_LABELS = {
            "cnpcgeckoaddon.boss.aggro_zone_nearest",
            "cnpcgeckoaddon.boss.aggro_zone_random"
    };
    public static final int MIN_AGGRO_ZONE_RECHECK_TICKS = 1;
    public static final int MAX_AGGRO_ZONE_RECHECK_TICKS = 200;
    /** Matches the coordinate limit used by the world border and block positions. */
    public static final int MAX_AGGRO_ZONE_COORDINATE = 30000000;

    public static final int HEALTH_SCALING_PERCENT = 0;
    public static final int HEALTH_SCALING_FLAT = 1;
    public static final int HEALTH_SCALING_PERCENT_AND_FLAT = 2;
    public static final int HEALTH_SCALING_LOCK_AT_START = 0;
    public static final int HEALTH_SCALING_DYNAMIC = 1;
    public static final int HEALTH_SCALING_KEEP_PERCENT = 0;
    public static final int HEALTH_SCALING_KEEP_CURRENT = 1;
    public static final int MIN_HEALTH_PER_PLAYER_PERCENT = 0;
    public static final int MAX_HEALTH_PER_PLAYER_PERCENT = 1000;
    public static final int MIN_HEALTH_PER_PLAYER_FLAT = 0;
    public static final int MAX_HEALTH_PER_PLAYER_FLAT = 1000000;
    public static final int MIN_HEALTH_SCALING_PLAYER_CAP = 1;
    public static final int MAX_HEALTH_SCALING_PLAYER_CAP = 128;
    public static final int MIN_HEALTH_SCALING_RECHECK_TICKS = 1;
    public static final int MAX_HEALTH_SCALING_RECHECK_TICKS = 200;
    public static final String[] HEALTH_SCALING_MODE_LABELS = {
            "cnpcgeckoaddon.boss.health_scaling_percent",
            "cnpcgeckoaddon.boss.health_scaling_flat",
            "cnpcgeckoaddon.boss.health_scaling_both"
    };
    public static final String[] HEALTH_SCALING_UPDATE_LABELS = {
            "cnpcgeckoaddon.boss.health_scaling_locked",
            "cnpcgeckoaddon.boss.health_scaling_dynamic"
    };
    public static final String[] HEALTH_SCALING_ADJUSTMENT_LABELS = {
            "cnpcgeckoaddon.boss.health_scaling_keep_pct",
            "cnpcgeckoaddon.boss.health_scaling_keep_hp"
    };

    public static final int MIN_RAGE_DELAY_TICKS = 100;
    public static final int MAX_RAGE_DELAY_TICKS = 72000;
    public static final int MIN_RAGE_MULTIPLIER_PERCENT = 100;
    public static final int MAX_RAGE_MULTIPLIER_PERCENT = 1000;

    public static final int MIN_CHEST_DELAY_TICKS = 0;
    public static final int MAX_CHEST_DELAY_TICKS = 1200;
    /** Five seconds is the least that gives anyone a chance to walk over and open it. */
    public static final int MIN_CHEST_LIFETIME_TICKS = 100;
    /** Six real hours, which is as good as forever for a single fight. */
    public static final int MAX_CHEST_LIFETIME_TICKS = 432000;

    /** Minions are silently discarded, the way a despawning mob disappears. */
    public static final int MINION_REMOVAL_VANISH = 0;
    /** Minions are killed instead, so death animations, drops and kill scripts still run. */
    public static final int MINION_REMOVAL_KILL = 1;

    /** Particles and sound only - no damage and no block damage. */
    public static final int EXPLOSION_MODE_EFFECT = 0;
    /** A real blast that hurts entities but leaves the terrain alone. */
    public static final int EXPLOSION_MODE_DAMAGE = 1;
    /** Hurts entities and breaks blocks, honouring the mobGriefing game rule. */
    public static final int EXPLOSION_MODE_BLOCKS = 2;
    /** Hurts entities and always breaks blocks, like TNT. */
    public static final int EXPLOSION_MODE_BLOCKS_ALWAYS = 3;

    public static final String[] EXPLOSION_MODE_LABELS = {
            "cnpcgeckoaddon.boss.explosion_mode_effect",
            "cnpcgeckoaddon.boss.explosion_mode_damage",
            "cnpcgeckoaddon.boss.explosion_mode_blocks",
            "cnpcgeckoaddon.boss.explosion_mode_blocks_always"
    };

    /** Only the danger zone on the floor. */
    public static final int TELEGRAPH_STYLE_ZONE = 0;
    /** Only the charge-up on the boss itself, for a player who is not looking down. */
    public static final int TELEGRAPH_STYLE_AURA = 1;
    public static final int TELEGRAPH_STYLE_BOTH = 2;

    public static final String[] TELEGRAPH_STYLE_LABELS = {
            "cnpcgeckoaddon.boss.telegraph_style_zone",
            "cnpcgeckoaddon.boss.telegraph_style_aura",
            "cnpcgeckoaddon.boss.telegraph_style_both"
    };

    /**
     * The abilities that warn before they land, in the order they are offered. A quick jab
     * can be left silent while the heavy swing that kills still warns.
     *
     * <p>Everything the boss winds up and aims. The death blast is deliberately absent - it
     * goes off after the fight is already lost, so there is nothing to warn about - and its
     * bit is simply skipped rather than reused, because the bits of a saved boss have to keep
     * meaning what they meant when it was built.</p>
     */
    public static final int[] TELEGRAPH_ABILITIES = {
            BossAbilityKind.AREA, BossAbilityKind.RANGED, BossAbilityKind.MELEE,
            BossAbilityKind.FLUID, BossAbilityKind.HOOK, BossAbilityKind.CAPTURE,
            BossAbilityKind.SUMMON, BossAbilityKind.LEAP, BossAbilityKind.LINE,
            BossAbilityKind.GEYSER, BossAbilityKind.BOULDER, BossAbilityKind.TETHER,
            BossAbilityKind.GRAVITY
    };
    /** Everything warns until a builder switches an ability off. */
    public static final int TELEGRAPH_ALL_ABILITIES = telegraphMask();
    /** What {@link #TELEGRAPH_ALL_ABILITIES} was before the line strike joined the mask. */
    private static final int TELEGRAPH_ABILITIES_BEFORE_LINE = (1 << BossAbilityKind.LINE) - 1;
    /** And before the geyser did, which is every bit up to and including the line strike. */
    private static final int TELEGRAPH_ABILITIES_BEFORE_GEYSER = (1 << (BossAbilityKind.LINE + 1)) - 1;
    /** And before the boulder: everything through the geyser, minus the unwarnable blast. */
    private static final int TELEGRAPH_ABILITIES_BEFORE_BOULDER =
            ((1 << (BossAbilityKind.GEYSER + 1)) - 1) & ~(1 << BossAbilityKind.BLAST);
    /**
     * And before the tether: everything through the boulder, minus the blast. The boulder
     * rain never joined the mask - it is aimed at nobody - so its bit is not in here either.
     */
    private static final int TELEGRAPH_ABILITIES_BEFORE_TETHER =
            ((1 << (BossAbilityKind.BOULDER + 1)) - 1) & ~(1 << BossAbilityKind.BLAST);
    /** And before the gravity field: everything through the tether, minus the same two. */
    private static final int TELEGRAPH_ABILITIES_BEFORE_GRAVITY =
            ((1 << (BossAbilityKind.TETHER + 1)) - 1)
                    & ~(1 << BossAbilityKind.BLAST) & ~(1 << BossAbilityKind.BOULDER_RAIN);

    private static int telegraphMask() {
        int mask = 0;
        for (int ability : TELEGRAPH_ABILITIES) {
            mask |= 1 << ability;
        }
        return mask;
    }

    /**
     * Radius of the ring an aimed ability paints under whoever it picked. Its own reach is
     * no use here - a hook that pulls from forty blocks would ring half the arena - so the
     * mark gets a size of its own. Area abilities keep marking the ground they really cover.
     */
    public static final int MIN_TELEGRAPH_ZONE_RADIUS = 1;
    public static final int MAX_TELEGRAPH_ZONE_RADIUS = 16;
    public static final int DEFAULT_TELEGRAPH_ZONE_RADIUS = 3;

    /**
     * How long a player is guaranteed to see a warning before the ability behind it lands.
     *
     * <p>Counted together with the wind-up rather than added to it: the wind-up is measured
     * against the attack animation, so anything stretching it would leave the swing playing
     * after the hit. Only the shortfall is added, and it is added in front, where nothing is
     * animating yet.</p>
     */
    public static final int MIN_TELEGRAPH_LEAD_TICKS = 0;
    public static final int MAX_TELEGRAPH_LEAD_TICKS = 200;
    /** A second and a half: long enough to look down, read the ring and walk out of it. */
    public static final int DEFAULT_TELEGRAPH_LEAD_TICKS = 30;

    /** The chest lands where the boss fell - what it has always done. */
    public static final int CHEST_PLACEMENT_DEATH = 0;
    /** Where the boss fell, shifted by the configured offset. */
    public static final int CHEST_PLACEMENT_DEATH_OFFSET = 1;
    /** Where the boss stood when the fight started, shifted by the configured offset. */
    public static final int CHEST_PLACEMENT_ARENA = 2;
    /** One spot in the world, whatever happened during the fight. */
    public static final int CHEST_PLACEMENT_FIXED = 3;

    public static final String[] CHEST_PLACEMENT_LABELS = {
            "cnpcgeckoaddon.boss.chest_placement_death",
            "cnpcgeckoaddon.boss.chest_placement_offset",
            "cnpcgeckoaddon.boss.chest_placement_arena",
            "cnpcgeckoaddon.boss.chest_placement_fixed"
    };

    public static final int MIN_CHEST_OFFSET = -64;
    public static final int MAX_CHEST_OFFSET = 64;
    /** The world border's own limit: anything past it cannot hold a block anyway. */
    public static final int MAX_CHEST_COORDINATE = 30000000;

    public static final String[] MINION_REMOVAL_LABELS = {
            "cnpcgeckoaddon.boss.minions_removal_vanish",
            "cnpcgeckoaddon.boss.minions_removal_kill"
    };

    public static final int TOTEM_PROTECTION_FULL_IMMUNITY = 0;
    public static final int TOTEM_PROTECTION_LETHAL_GUARD = 1;
    public static final int TOTEM_ACTIVATION_ALWAYS = 0;
    public static final int TOTEM_ACTIVATION_ENCOUNTER_START = 1;
    public static final int TOTEM_ACTIVATION_ENCOUNTER_TIMER = 2;
    public static final int TOTEM_ACTIVATION_PHASE_ENTER = 3;
    public static final int TOTEM_RESPAWN_NEVER = 0;
    public static final int TOTEM_RESPAWN_NEXT_ENCOUNTER = 1;
    public static final int TOTEM_RESPAWN_DELAYED = 2;
    public static final int MIN_TOTEM_ACTIVATION_DELAY_TICKS = 0;
    public static final int MIN_TOTEM_RESPAWN_DELAY_TICKS = 20;
    public static final int MAX_TOTEM_DELAY_TICKS = 72000;

    public static final String[] TOTEM_PROTECTION_LABELS = {
            "cnpcgeckoaddon.boss.totem_protection_full",
            "cnpcgeckoaddon.boss.totem_protection_lethal"
    };
    public static final String[] TOTEM_ACTIVATION_LABELS = {
            "cnpcgeckoaddon.boss.totem_always",
            "cnpcgeckoaddon.boss.totem_encounter",
            "cnpcgeckoaddon.boss.totem_timer",
            "cnpcgeckoaddon.boss.totem_phase"
    };
    public static final String[] TOTEM_RESPAWN_LABELS = {
            "cnpcgeckoaddon.boss.totem_respawn_never",
            "cnpcgeckoaddon.boss.totem_respawn_reset",
            "cnpcgeckoaddon.boss.totem_respawn_delayed"
    };

    private static final String ENABLED_KEY = "GeckoTeleportPathEnabled";
    private static final String COMBAT_ONLY_KEY = "GeckoTeleportPathCombatOnly";
    private static final String STATIONARY_KEY = "GeckoBossStationary";
    private static final String ORDER_KEY = "GeckoTeleportPathOrder";
    private static final String SOUND_KEY = "GeckoTeleportPathSound";
    private static final String PHASE_THRESHOLD_KEY = "GeckoBossPhaseThreshold";
    private static final String TRANSITION_ANIMATION_KEY = "GeckoBossTransitionAnimation";
    private static final String TRANSITION_LOCK_KEY = "GeckoBossTransitionLockTicks";
    private static final String PHASE_ONE_KEY = "GeckoBossPhaseOne";
    private static final String PHASE_TWO_KEY = "GeckoBossPhaseTwo";
    private static final String PHASES_KEY = "GeckoBossPhases";
    private static final String TARGET_NEAREST_KEY = "GeckoBossTargetNearestPlayer";
    private static final String TARGET_RADIUS_KEY = "GeckoBossTargetRadius";
    private static final String TARGET_INTERVAL_KEY = "GeckoBossTargetInterval";
    private static final String TARGET_LOS_KEY = "GeckoBossTargetLineOfSight";
    private static final String TARGET_KEEP_KEY = "GeckoBossTargetKeepOutOfRange";
    private static final String ABILITY_TARGET_KIND_KEY = "GeckoBossAbilityTargetKind";
    private static final String AGGRO_ZONE_ENABLED_KEY = "GeckoBossAggroZoneEnabled";
    private static final String AGGRO_ZONE_X1_KEY = "GeckoBossAggroZoneX1";
    private static final String AGGRO_ZONE_Y1_KEY = "GeckoBossAggroZoneY1";
    private static final String AGGRO_ZONE_Z1_KEY = "GeckoBossAggroZoneZ1";
    private static final String AGGRO_ZONE_X2_KEY = "GeckoBossAggroZoneX2";
    private static final String AGGRO_ZONE_Y2_KEY = "GeckoBossAggroZoneY2";
    private static final String AGGRO_ZONE_Z2_KEY = "GeckoBossAggroZoneZ2";
    private static final String AGGRO_ZONE_INTERVAL_KEY = "GeckoBossAggroZoneInterval";
    private static final String AGGRO_ZONE_TARGET_KEY = "GeckoBossAggroZoneTarget";
    private static final String AGGRO_ZONE_KEEP_KEY = "GeckoBossAggroZoneKeepInside";
    private static final String HEALTH_SCALING_ENABLED_KEY = "GeckoBossHealthScalingEnabled";
    private static final String HEALTH_SCALING_MODE_KEY = "GeckoBossHealthScalingMode";
    private static final String HEALTH_PER_PLAYER_PERCENT_KEY = "GeckoBossHealthPerPlayerPercent";
    private static final String HEALTH_PER_PLAYER_FLAT_KEY = "GeckoBossHealthPerPlayerFlat";
    private static final String HEALTH_SCALING_UPDATE_KEY = "GeckoBossHealthScalingUpdate";
    private static final String HEALTH_SCALING_ADJUSTMENT_KEY = "GeckoBossHealthScalingAdjustment";
    private static final String HEALTH_SCALING_PLAYER_CAP_KEY = "GeckoBossHealthScalingPlayerCap";
    private static final String HEALTH_SCALING_INTERVAL_KEY = "GeckoBossHealthScalingInterval";
    private static final String MINIONS_DEATH_KEY = "GeckoBossMinionsClearOnDeath";
    private static final String MINIONS_RESET_KEY = "GeckoBossMinionsClearOnReset";
    private static final String MINIONS_REMOVAL_KEY = "GeckoBossMinionsRemovalMode";
    private static final String EXPLOSION_ENABLED_KEY = "GeckoBossExplosionEnabled";
    private static final String EXPLOSION_DELAY_KEY = "GeckoBossExplosionDelay";
    private static final String EXPLOSION_POWER_KEY = "GeckoBossExplosionPower";
    private static final String EXPLOSION_MODE_KEY = "GeckoBossExplosionMode";
    private static final String EXPLOSION_FIRE_KEY = "GeckoBossExplosionFire";
    private static final String TELEGRAPH_ENABLED_KEY = "GeckoBossTelegraphEnabled";
    private static final String TELEGRAPH_STYLE_KEY = "GeckoBossTelegraphStyle";
    private static final String TELEGRAPH_ABILITIES_KEY = "GeckoBossTelegraphAbilities";
    private static final String TELEGRAPH_ANNOUNCE_KEY = "GeckoBossTelegraphAnnounce";
    private static final String TELEGRAPH_SOUND_KEY = "GeckoBossTelegraphSound";
    private static final String TELEGRAPH_ZONE_RADIUS_KEY = "GeckoBossTelegraphZoneRadius";
    private static final String TELEGRAPH_LEAD_KEY = "GeckoBossTelegraphLead";
    private static final String TELEGRAPH_DODGE_KEY = "GeckoBossTelegraphDodge";
    private static final String CHEST_ENABLED_KEY = "GeckoBossChestEnabled";
    private static final String CHEST_BLOCK_KEY = "GeckoBossChestBlock";
    private static final String CHEST_DELAY_KEY = "GeckoBossChestDelay";
    private static final String CHEST_LIFETIME_KEY = "GeckoBossChestLifetime";
    private static final String CHEST_NAME_KEY = "GeckoBossChestName";
    private static final String CHEST_NPC_DROPS_KEY = "GeckoBossChestNpcDrops";
    private static final String CHEST_LOOT_TABLE_KEY = "GeckoBossChestLootTable";
    private static final String CHEST_LOOT_KEY = "GeckoBossChestLoot";
    private static final String CHEST_PLACEMENT_KEY = "GeckoBossChestPlacement";
    private static final String CHEST_OFFSET_X_KEY = "GeckoBossChestOffsetX";
    private static final String CHEST_OFFSET_Y_KEY = "GeckoBossChestOffsetY";
    private static final String CHEST_OFFSET_Z_KEY = "GeckoBossChestOffsetZ";
    private static final String CHEST_FIXED_X_KEY = "GeckoBossChestFixedX";
    private static final String CHEST_FIXED_Y_KEY = "GeckoBossChestFixedY";
    private static final String CHEST_FIXED_Z_KEY = "GeckoBossChestFixedZ";
    private static final String CHEST_STYLE_KEY = "GeckoBossChestStyle";
    private static final String BOSS_BAR_STYLE_KEY = "GeckoBossBarStyle";
    private static final String BOSS_BAR_SCALE_KEY = "GeckoBossBarScale";
    private static final String RESET_TICKS_KEY = "GeckoBossResetTicks";
    private static final String RESET_HEAL_KEY = "GeckoBossResetHeal";
    private static final String RESET_RETURN_KEY = "GeckoBossResetReturn";
    private static final String HOME_LEASH_ENABLED_KEY = "GeckoBossHomeLeashEnabled";
    private static final String HOME_LEASH_RADIUS_KEY = "GeckoBossHomeLeashRadius";
    private static final String HOME_LEASH_VERTICAL_KEY = "GeckoBossHomeLeashVertical";
    private static final String HOME_LEASH_GRACE_KEY = "GeckoBossHomeLeashGrace";
    private static final String RAGE_ENABLED_KEY = "GeckoBossRageEnabled";
    private static final String RAGE_DELAY_KEY = "GeckoBossRageDelay";
    private static final String RAGE_MULTIPLIER_KEY = "GeckoBossRageMultiplier";
    private static final String RAGE_ANIMATION_KEY = "GeckoBossRageAnimation";
    private static final String RAGE_LOCK_KEY = "GeckoBossRageLock";
    private static final String TOTEMS_ENABLED_KEY = "GeckoBossTotemsEnabled";
    private static final String TOTEM_PROTECTION_KEY = "GeckoBossTotemProtection";
    private static final String TOTEM_GRANT_INVULN_KEY = "GeckoTotemGrantInvuln";
    private static final String TOTEM_HOLD_BOSS_KEY = "GeckoTotemHoldBoss";
    private static final String TOTEM_SILENCE_KEY = "GeckoTotemSuppressAbilities";
    private static final String TOTEM_UNTARGETABLE_KEY = "GeckoTotemUntargetable";
    private static final String TOTEM_ACTIVATION_KEY = "GeckoBossTotemActivation";
    private static final String TOTEM_PHASE_KEY = "GeckoBossTotemPhase";
    private static final String TOTEM_DELAY_KEY = "GeckoBossTotemDelay";
    private static final String TOTEM_RESPAWN_KEY = "GeckoBossTotemRespawn";
    private static final String TOTEM_RESPAWN_DELAY_KEY = "GeckoBossTotemRespawnDelay";
    private static final String TOTEM_RESET_HEALTH_KEY = "GeckoBossTotemResetHealth";
    private static final String TOTEM_REMOVE_DEATH_KEY = "GeckoBossTotemRemoveOnDeath";
    private static final String TOTEM_BEAM_STYLE_KEY = "GeckoBossTotemBeamStyle";
    private static final String TOTEM_BEAM_WIDTH_KEY = "GeckoBossTotemBeamWidth";
    private static final String TOTEM_BEAM_SAG_KEY = "GeckoBossTotemBeamSag";
    private static final String TOTEMS_KEY = "GeckoBossTotems";

    private boolean enabled;
    private boolean combatOnly = true;
    private boolean stationary;
    private int order = ORDER_SEQUENTIAL;
    private boolean playSound = true;
    private String phaseTransitionAnimation = "";
    private int phaseTransitionLockTicks = 40;

    /**
     * Ordered from the healthiest phase to the weakest. Index 0 always starts at full
     * health; every later phase takes over once the boss drops to its own threshold.
     */
    private final List<BossPhaseData> phases = new ArrayList<>();

    private boolean targetNearestPlayer;
    private int targetSearchRadius = 32;
    private int targetRecheckTicks = 20;
    private boolean targetRequiresLineOfSight;
    private boolean keepTargetOutOfRange;
    private int abilityTargetKind = ABILITY_TARGET_PLAYERS_AND_NPCS;

    private boolean aggroZoneEnabled;
    private int aggroZoneX1;
    private int aggroZoneY1;
    private int aggroZoneZ1;
    private int aggroZoneX2;
    private int aggroZoneY2;
    private int aggroZoneZ2;
    private int aggroZoneRecheckTicks = 5;
    private int aggroZoneTargetMode = AGGRO_ZONE_TARGET_NEAREST;
    private boolean aggroZoneKeepInside;

    private boolean healthScalingEnabled;
    private int healthScalingMode = HEALTH_SCALING_PERCENT;
    private int healthPerPlayerPercent = 50;
    private int healthPerPlayerFlat = 20;
    private int healthScalingUpdateMode = HEALTH_SCALING_LOCK_AT_START;
    private int healthScalingAdjustment = HEALTH_SCALING_KEEP_PERCENT;
    private int healthScalingPlayerCap = 8;
    private int healthScalingRecheckTicks = 20;

    private int resetTicks = 100;
    private boolean resetHeal = true;
    private boolean resetReturn;
    private boolean homeLeashEnabled;
    private int homeLeashRadius = 32;
    private boolean homeLeashVertical;
    private int homeLeashGraceTicks;

    private boolean rageEnabled;
    private int rageDelayTicks = 3600;
    private int rageMultiplierPercent = 200;
    private String rageAnimation = "";
    private int rageLockTicks = 40;

    private boolean clearMinionsOnDeath = true;
    private boolean clearMinionsOnReset = true;
    private int minionRemovalMode = MINION_REMOVAL_VANISH;

    private boolean totemsEnabled;
    /** What a standing formation does to the boss; all four may be off, leaving only the beams. */
    private boolean totemGrantInvulnerability = true;
    private boolean totemHoldBoss;
    private boolean totemSuppressAbilities;
    private boolean totemUntargetable;
    private int totemProtectionMode = TOTEM_PROTECTION_FULL_IMMUNITY;
    private int totemActivationMode = TOTEM_ACTIVATION_ALWAYS;
    private int totemActivationPhase = 1;
    private int totemActivationDelayTicks = 200;
    private int totemRespawnMode = TOTEM_RESPAWN_NEXT_ENCOUNTER;
    private int totemRespawnDelayTicks = 200;
    private boolean totemResetHealth = true;
    private boolean totemRemoveOnBossDeath = true;
    private String totemBeamStyle = HookCordStyles.GHOST;
    private int totemBeamWidthPercent = 100;
    private int totemBeamSagPercent;
    private final BossTotemList totems = new BossTotemList();

    private boolean explosionEnabled;
    private int explosionDelayTicks = 20;
    private int explosionPower = 4;
    private int explosionMode = EXPLOSION_MODE_DAMAGE;
    private boolean explosionFire;

    /**
     * On by default, and deliberately so: a wind-up nobody can read turns a boss fight into
     * guesswork, and switching the warning back off is one button.
     */
    private boolean telegraphEnabled = true;
    private int telegraphStyle = TELEGRAPH_STYLE_BOTH;
    private int telegraphAbilities = TELEGRAPH_ALL_ABILITIES;
    private int telegraphZoneRadius = DEFAULT_TELEGRAPH_ZONE_RADIUS;
    private int telegraphLeadTicks = DEFAULT_TELEGRAPH_LEAD_TICKS;
    private boolean telegraphDodge = true;
    private boolean telegraphAnnounce = true;
    private boolean telegraphSound = true;

    private boolean chestEnabled;
    private String chestBlock = ContainerBlockUtil.DEFAULT_ID;
    private int chestDelayTicks;
    private int chestLifetimeTicks = 6000;
    private String chestName = "";
    private boolean chestUseNpcDrops;
    private String chestLootTable = "";
    private final BossLootList chestLoot = new BossLootList();
    private int chestPlacement = CHEST_PLACEMENT_DEATH;
    private int chestOffsetX;
    private int chestOffsetY;
    private int chestOffsetZ;
    private int chestFixedX;
    private int chestFixedY;
    private int chestFixedZ;
    private String chestStyle = BossChestStyles.VANILLA;

    private String bossBarStyle = BossBarStyles.NONE;
    private int bossBarScalePercent = DEFAULT_BOSS_BAR_SCALE_PERCENT;

    public TeleportPathData() {
        setPhaseCount(2);
    }

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putBoolean(ENABLED_KEY, enabled);
        tag.putBoolean(COMBAT_ONLY_KEY, combatOnly);
        tag.putBoolean(STATIONARY_KEY, stationary);
        tag.putInt(ORDER_KEY, order);
        tag.putBoolean(SOUND_KEY, playSound);
        tag.putString(TRANSITION_ANIMATION_KEY, phaseTransitionAnimation);
        tag.putInt(TRANSITION_LOCK_KEY, phaseTransitionLockTicks);

        ListTag list = new ListTag();
        for (BossPhaseData phase : phases) {
            list.add(phase.writeToNBT());
        }
        tag.put(PHASES_KEY, list);

        // Keep the pre-1.7 keys populated so downgrading only loses the extra phases
        // instead of resetting the whole boss.
        tag.put(PHASE_ONE_KEY, phases.get(0).writeToNBT());
        tag.put(PHASE_TWO_KEY, phases.get(Math.min(1, phases.size() - 1)).writeToNBT());
        tag.putInt(PHASE_THRESHOLD_KEY, getPhaseTwoHealthPercent());
        tag.putInt("GeckoTeleportPathMinDelay", phases.get(0).getTeleportMinDelayTicks());
        tag.putInt("GeckoTeleportPathMaxDelay", phases.get(0).getTeleportMaxDelayTicks());

        tag.putBoolean(TARGET_NEAREST_KEY, targetNearestPlayer);
        tag.putInt(TARGET_RADIUS_KEY, targetSearchRadius);
        tag.putInt(TARGET_INTERVAL_KEY, targetRecheckTicks);
        tag.putBoolean(TARGET_LOS_KEY, targetRequiresLineOfSight);
        tag.putBoolean(TARGET_KEEP_KEY, keepTargetOutOfRange);
        tag.putInt(ABILITY_TARGET_KIND_KEY, abilityTargetKind);
        tag.putBoolean(AGGRO_ZONE_ENABLED_KEY, aggroZoneEnabled);
        tag.putInt(AGGRO_ZONE_X1_KEY, aggroZoneX1);
        tag.putInt(AGGRO_ZONE_Y1_KEY, aggroZoneY1);
        tag.putInt(AGGRO_ZONE_Z1_KEY, aggroZoneZ1);
        tag.putInt(AGGRO_ZONE_X2_KEY, aggroZoneX2);
        tag.putInt(AGGRO_ZONE_Y2_KEY, aggroZoneY2);
        tag.putInt(AGGRO_ZONE_Z2_KEY, aggroZoneZ2);
        tag.putInt(AGGRO_ZONE_INTERVAL_KEY, aggroZoneRecheckTicks);
        tag.putInt(AGGRO_ZONE_TARGET_KEY, aggroZoneTargetMode);
        tag.putBoolean(AGGRO_ZONE_KEEP_KEY, aggroZoneKeepInside);
        tag.putBoolean(HEALTH_SCALING_ENABLED_KEY, healthScalingEnabled);
        tag.putInt(HEALTH_SCALING_MODE_KEY, healthScalingMode);
        tag.putInt(HEALTH_PER_PLAYER_PERCENT_KEY, healthPerPlayerPercent);
        tag.putInt(HEALTH_PER_PLAYER_FLAT_KEY, healthPerPlayerFlat);
        tag.putInt(HEALTH_SCALING_UPDATE_KEY, healthScalingUpdateMode);
        tag.putInt(HEALTH_SCALING_ADJUSTMENT_KEY, healthScalingAdjustment);
        tag.putInt(HEALTH_SCALING_PLAYER_CAP_KEY, healthScalingPlayerCap);
        tag.putInt(HEALTH_SCALING_INTERVAL_KEY, healthScalingRecheckTicks);
        tag.putInt(RESET_TICKS_KEY, resetTicks);
        tag.putBoolean(RESET_HEAL_KEY, resetHeal);
        tag.putBoolean(RESET_RETURN_KEY, resetReturn);
        tag.putBoolean(HOME_LEASH_ENABLED_KEY, homeLeashEnabled);
        tag.putInt(HOME_LEASH_RADIUS_KEY, homeLeashRadius);
        tag.putBoolean(HOME_LEASH_VERTICAL_KEY, homeLeashVertical);
        tag.putInt(HOME_LEASH_GRACE_KEY, homeLeashGraceTicks);
        tag.putBoolean(RAGE_ENABLED_KEY, rageEnabled);
        tag.putInt(RAGE_DELAY_KEY, rageDelayTicks);
        tag.putInt(RAGE_MULTIPLIER_KEY, rageMultiplierPercent);
        tag.putString(RAGE_ANIMATION_KEY, rageAnimation);
        tag.putInt(RAGE_LOCK_KEY, rageLockTicks);
        tag.putBoolean(MINIONS_DEATH_KEY, clearMinionsOnDeath);
        tag.putBoolean(MINIONS_RESET_KEY, clearMinionsOnReset);
        tag.putInt(MINIONS_REMOVAL_KEY, minionRemovalMode);
        tag.putBoolean(TOTEMS_ENABLED_KEY, totemsEnabled);
        tag.putInt(TOTEM_PROTECTION_KEY, totemProtectionMode);
        tag.putBoolean(TOTEM_GRANT_INVULN_KEY, totemGrantInvulnerability);
        tag.putBoolean(TOTEM_HOLD_BOSS_KEY, totemHoldBoss);
        tag.putBoolean(TOTEM_SILENCE_KEY, totemSuppressAbilities);
        tag.putBoolean(TOTEM_UNTARGETABLE_KEY, totemUntargetable);
        tag.putInt(TOTEM_ACTIVATION_KEY, totemActivationMode);
        tag.putInt(TOTEM_PHASE_KEY, totemActivationPhase);
        tag.putInt(TOTEM_DELAY_KEY, totemActivationDelayTicks);
        tag.putInt(TOTEM_RESPAWN_KEY, totemRespawnMode);
        tag.putInt(TOTEM_RESPAWN_DELAY_KEY, totemRespawnDelayTicks);
        tag.putBoolean(TOTEM_RESET_HEALTH_KEY, totemResetHealth);
        tag.putBoolean(TOTEM_REMOVE_DEATH_KEY, totemRemoveOnBossDeath);
        tag.putString(TOTEM_BEAM_STYLE_KEY, totemBeamStyle);
        tag.putInt(TOTEM_BEAM_WIDTH_KEY, totemBeamWidthPercent);
        tag.putInt(TOTEM_BEAM_SAG_KEY, totemBeamSagPercent);
        tag.put(TOTEMS_KEY, totems.writeToNBT());
        tag.putBoolean(EXPLOSION_ENABLED_KEY, explosionEnabled);
        tag.putInt(EXPLOSION_DELAY_KEY, explosionDelayTicks);
        tag.putInt(EXPLOSION_POWER_KEY, explosionPower);
        tag.putInt(EXPLOSION_MODE_KEY, explosionMode);
        tag.putBoolean(EXPLOSION_FIRE_KEY, explosionFire);
        tag.putBoolean(TELEGRAPH_ENABLED_KEY, telegraphEnabled);
        tag.putInt(TELEGRAPH_STYLE_KEY, telegraphStyle);
        tag.putInt(TELEGRAPH_ABILITIES_KEY, telegraphAbilities);
        tag.putInt(TELEGRAPH_ZONE_RADIUS_KEY, telegraphZoneRadius);
        tag.putInt(TELEGRAPH_LEAD_KEY, telegraphLeadTicks);
        tag.putBoolean(TELEGRAPH_DODGE_KEY, telegraphDodge);
        tag.putBoolean(TELEGRAPH_ANNOUNCE_KEY, telegraphAnnounce);
        tag.putBoolean(TELEGRAPH_SOUND_KEY, telegraphSound);
        tag.putBoolean(CHEST_ENABLED_KEY, chestEnabled);
        tag.putString(CHEST_BLOCK_KEY, chestBlock);
        tag.putInt(CHEST_DELAY_KEY, chestDelayTicks);
        tag.putInt(CHEST_LIFETIME_KEY, chestLifetimeTicks);
        tag.putString(CHEST_NAME_KEY, chestName);
        tag.putBoolean(CHEST_NPC_DROPS_KEY, chestUseNpcDrops);
        tag.putString(CHEST_LOOT_TABLE_KEY, chestLootTable);
        tag.put(CHEST_LOOT_KEY, chestLoot.writeToNBT());
        tag.putInt(CHEST_PLACEMENT_KEY, chestPlacement);
        tag.putInt(CHEST_OFFSET_X_KEY, chestOffsetX);
        tag.putInt(CHEST_OFFSET_Y_KEY, chestOffsetY);
        tag.putInt(CHEST_OFFSET_Z_KEY, chestOffsetZ);
        tag.putInt(CHEST_FIXED_X_KEY, chestFixedX);
        tag.putInt(CHEST_FIXED_Y_KEY, chestFixedY);
        tag.putInt(CHEST_FIXED_Z_KEY, chestFixedZ);
        tag.putString(CHEST_STYLE_KEY, chestStyle);
        tag.putString(BOSS_BAR_STYLE_KEY, bossBarStyle);
        tag.putInt(BOSS_BAR_SCALE_KEY, bossBarScalePercent);
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        enabled = tag.getBoolean(ENABLED_KEY);
        combatOnly = !tag.contains(COMBAT_ONLY_KEY) || tag.getBoolean(COMBAT_ONLY_KEY);
        stationary = tag.contains(STATIONARY_KEY) && tag.getBoolean(STATIONARY_KEY);
        order = tag.contains(ORDER_KEY)
                ? Mth.clamp(tag.getInt(ORDER_KEY), ORDER_SEQUENTIAL, ORDER_RANDOM)
                : ORDER_SEQUENTIAL;
        playSound = !tag.contains(SOUND_KEY) || tag.getBoolean(SOUND_KEY);
        phaseTransitionAnimation = tag.getString(TRANSITION_ANIMATION_KEY).trim();
        phaseTransitionLockTicks = tag.contains(TRANSITION_LOCK_KEY)
                ? Mth.clamp(tag.getInt(TRANSITION_LOCK_KEY), 0, 1200) : 40;

        readPhases(tag);

        targetNearestPlayer = tag.getBoolean(TARGET_NEAREST_KEY);
        targetSearchRadius = tag.contains(TARGET_RADIUS_KEY)
                ? Mth.clamp(tag.getInt(TARGET_RADIUS_KEY), 4, 128) : 32;
        targetRecheckTicks = tag.contains(TARGET_INTERVAL_KEY)
                ? Mth.clamp(tag.getInt(TARGET_INTERVAL_KEY), 1, 200) : 20;
        targetRequiresLineOfSight = tag.getBoolean(TARGET_LOS_KEY);
        keepTargetOutOfRange = tag.getBoolean(TARGET_KEEP_KEY);
        abilityTargetKind = tag.contains(ABILITY_TARGET_KIND_KEY)
                ? Mth.clamp(tag.getInt(ABILITY_TARGET_KIND_KEY), ABILITY_TARGET_PLAYERS,
                ABILITY_TARGET_ALL) : ABILITY_TARGET_PLAYERS_AND_NPCS;
        aggroZoneEnabled = tag.getBoolean(AGGRO_ZONE_ENABLED_KEY);
        aggroZoneX1 = coordinate(tag, AGGRO_ZONE_X1_KEY);
        aggroZoneY1 = coordinate(tag, AGGRO_ZONE_Y1_KEY);
        aggroZoneZ1 = coordinate(tag, AGGRO_ZONE_Z1_KEY);
        aggroZoneX2 = coordinate(tag, AGGRO_ZONE_X2_KEY);
        aggroZoneY2 = coordinate(tag, AGGRO_ZONE_Y2_KEY);
        aggroZoneZ2 = coordinate(tag, AGGRO_ZONE_Z2_KEY);
        aggroZoneRecheckTicks = tag.contains(AGGRO_ZONE_INTERVAL_KEY)
                ? Mth.clamp(tag.getInt(AGGRO_ZONE_INTERVAL_KEY), MIN_AGGRO_ZONE_RECHECK_TICKS,
                MAX_AGGRO_ZONE_RECHECK_TICKS) : 5;
        aggroZoneTargetMode = tag.contains(AGGRO_ZONE_TARGET_KEY)
                ? Mth.clamp(tag.getInt(AGGRO_ZONE_TARGET_KEY), AGGRO_ZONE_TARGET_NEAREST,
                AGGRO_ZONE_TARGET_RANDOM) : AGGRO_ZONE_TARGET_NEAREST;
        aggroZoneKeepInside = tag.getBoolean(AGGRO_ZONE_KEEP_KEY);
        healthScalingEnabled = tag.getBoolean(HEALTH_SCALING_ENABLED_KEY);
        healthScalingMode = tag.contains(HEALTH_SCALING_MODE_KEY)
                ? Mth.clamp(tag.getInt(HEALTH_SCALING_MODE_KEY), HEALTH_SCALING_PERCENT,
                HEALTH_SCALING_PERCENT_AND_FLAT) : HEALTH_SCALING_PERCENT;
        healthPerPlayerPercent = tag.contains(HEALTH_PER_PLAYER_PERCENT_KEY)
                ? Mth.clamp(tag.getInt(HEALTH_PER_PLAYER_PERCENT_KEY), MIN_HEALTH_PER_PLAYER_PERCENT,
                MAX_HEALTH_PER_PLAYER_PERCENT) : 50;
        healthPerPlayerFlat = tag.contains(HEALTH_PER_PLAYER_FLAT_KEY)
                ? Mth.clamp(tag.getInt(HEALTH_PER_PLAYER_FLAT_KEY), MIN_HEALTH_PER_PLAYER_FLAT,
                MAX_HEALTH_PER_PLAYER_FLAT) : 20;
        healthScalingUpdateMode = tag.contains(HEALTH_SCALING_UPDATE_KEY)
                ? Mth.clamp(tag.getInt(HEALTH_SCALING_UPDATE_KEY), HEALTH_SCALING_LOCK_AT_START,
                HEALTH_SCALING_DYNAMIC) : HEALTH_SCALING_LOCK_AT_START;
        healthScalingAdjustment = tag.contains(HEALTH_SCALING_ADJUSTMENT_KEY)
                ? Mth.clamp(tag.getInt(HEALTH_SCALING_ADJUSTMENT_KEY), HEALTH_SCALING_KEEP_PERCENT,
                HEALTH_SCALING_KEEP_CURRENT) : HEALTH_SCALING_KEEP_PERCENT;
        healthScalingPlayerCap = tag.contains(HEALTH_SCALING_PLAYER_CAP_KEY)
                ? Mth.clamp(tag.getInt(HEALTH_SCALING_PLAYER_CAP_KEY), MIN_HEALTH_SCALING_PLAYER_CAP,
                MAX_HEALTH_SCALING_PLAYER_CAP) : 8;
        healthScalingRecheckTicks = tag.contains(HEALTH_SCALING_INTERVAL_KEY)
                ? Mth.clamp(tag.getInt(HEALTH_SCALING_INTERVAL_KEY), MIN_HEALTH_SCALING_RECHECK_TICKS,
                MAX_HEALTH_SCALING_RECHECK_TICKS) : 20;
        resetTicks = tag.contains(RESET_TICKS_KEY)
                ? Mth.clamp(tag.getInt(RESET_TICKS_KEY), MIN_RESET_TICKS, MAX_RESET_TICKS) : 100;
        // Coming back to a fight against a boss still standing at 10% health is nobody's
        // idea of a second attempt, so bosses saved before this option existed get it on.
        resetHeal = !tag.contains(RESET_HEAL_KEY) || tag.getBoolean(RESET_HEAL_KEY);
        resetReturn = tag.getBoolean(RESET_RETURN_KEY);
        homeLeashEnabled = tag.getBoolean(HOME_LEASH_ENABLED_KEY);
        homeLeashRadius = tag.contains(HOME_LEASH_RADIUS_KEY)
                ? Mth.clamp(tag.getInt(HOME_LEASH_RADIUS_KEY), MIN_HOME_LEASH_RADIUS,
                MAX_HOME_LEASH_RADIUS) : 32;
        homeLeashVertical = tag.getBoolean(HOME_LEASH_VERTICAL_KEY);
        homeLeashGraceTicks = tag.contains(HOME_LEASH_GRACE_KEY)
                ? Mth.clamp(tag.getInt(HOME_LEASH_GRACE_KEY), MIN_HOME_LEASH_GRACE_TICKS,
                MAX_HOME_LEASH_GRACE_TICKS) : 0;
        rageEnabled = tag.getBoolean(RAGE_ENABLED_KEY);
        rageDelayTicks = tag.contains(RAGE_DELAY_KEY)
                ? Mth.clamp(tag.getInt(RAGE_DELAY_KEY), MIN_RAGE_DELAY_TICKS, MAX_RAGE_DELAY_TICKS) : 3600;
        rageMultiplierPercent = tag.contains(RAGE_MULTIPLIER_KEY)
                ? Mth.clamp(tag.getInt(RAGE_MULTIPLIER_KEY), MIN_RAGE_MULTIPLIER_PERCENT,
                MAX_RAGE_MULTIPLIER_PERCENT) : 200;
        rageAnimation = tag.getString(RAGE_ANIMATION_KEY).trim();
        rageLockTicks = tag.contains(RAGE_LOCK_KEY) ? Mth.clamp(tag.getInt(RAGE_LOCK_KEY), 0, 1200) : 40;
        // Bosses configured before this option existed leave their minions behind when
        // they die, which is never what anyone wanted - so these default to on.
        clearMinionsOnDeath = !tag.contains(MINIONS_DEATH_KEY) || tag.getBoolean(MINIONS_DEATH_KEY);
        clearMinionsOnReset = !tag.contains(MINIONS_RESET_KEY) || tag.getBoolean(MINIONS_RESET_KEY);
        minionRemovalMode = tag.contains(MINIONS_REMOVAL_KEY)
                ? Mth.clamp(tag.getInt(MINIONS_REMOVAL_KEY), MINION_REMOVAL_VANISH, MINION_REMOVAL_KILL)
                : MINION_REMOVAL_VANISH;

        totemsEnabled = tag.getBoolean(TOTEMS_ENABLED_KEY);
        setTotemProtectionMode(tag.contains(TOTEM_PROTECTION_KEY) ? tag.getInt(TOTEM_PROTECTION_KEY)
                : TOTEM_PROTECTION_FULL_IMMUNITY);
        // A boss saved before the flags existed only ever warded, so that is what it keeps:
        // the ward defaults on where a missing key exists, the other three default off.
        totemGrantInvulnerability = !tag.contains(TOTEM_GRANT_INVULN_KEY)
                || tag.getBoolean(TOTEM_GRANT_INVULN_KEY);
        totemHoldBoss = tag.getBoolean(TOTEM_HOLD_BOSS_KEY);
        totemSuppressAbilities = tag.getBoolean(TOTEM_SILENCE_KEY);
        totemUntargetable = tag.getBoolean(TOTEM_UNTARGETABLE_KEY);
        setTotemActivationMode(tag.contains(TOTEM_ACTIVATION_KEY) ? tag.getInt(TOTEM_ACTIVATION_KEY)
                : TOTEM_ACTIVATION_ALWAYS);
        setTotemActivationPhase(tag.contains(TOTEM_PHASE_KEY) ? tag.getInt(TOTEM_PHASE_KEY) : 1);
        setTotemActivationDelayTicks(tag.contains(TOTEM_DELAY_KEY) ? tag.getInt(TOTEM_DELAY_KEY) : 200);
        setTotemRespawnMode(tag.contains(TOTEM_RESPAWN_KEY) ? tag.getInt(TOTEM_RESPAWN_KEY)
                : TOTEM_RESPAWN_NEXT_ENCOUNTER);
        setTotemRespawnDelayTicks(tag.contains(TOTEM_RESPAWN_DELAY_KEY)
                ? tag.getInt(TOTEM_RESPAWN_DELAY_KEY) : 200);
        totemResetHealth = !tag.contains(TOTEM_RESET_HEALTH_KEY) || tag.getBoolean(TOTEM_RESET_HEALTH_KEY);
        totemRemoveOnBossDeath = !tag.contains(TOTEM_REMOVE_DEATH_KEY) || tag.getBoolean(TOTEM_REMOVE_DEATH_KEY);
        setTotemBeamStyle(tag.contains(TOTEM_BEAM_STYLE_KEY)
                ? tag.getString(TOTEM_BEAM_STYLE_KEY) : HookCordStyles.GHOST);
        setTotemBeamWidthPercent(tag.contains(TOTEM_BEAM_WIDTH_KEY) ? tag.getInt(TOTEM_BEAM_WIDTH_KEY) : 100);
        setTotemBeamSagPercent(tag.getInt(TOTEM_BEAM_SAG_KEY));
        totems.readFromNBT(tag.contains(TOTEMS_KEY, Tag.TAG_LIST)
                ? tag.getList(TOTEMS_KEY, Tag.TAG_COMPOUND) : new ListTag());

        explosionEnabled = tag.getBoolean(EXPLOSION_ENABLED_KEY);
        explosionDelayTicks = tag.contains(EXPLOSION_DELAY_KEY)
                ? Mth.clamp(tag.getInt(EXPLOSION_DELAY_KEY), 0, 1200) : 20;
        explosionPower = tag.contains(EXPLOSION_POWER_KEY)
                ? Mth.clamp(tag.getInt(EXPLOSION_POWER_KEY), 1, 20) : 4;
        explosionMode = tag.contains(EXPLOSION_MODE_KEY)
                ? Mth.clamp(tag.getInt(EXPLOSION_MODE_KEY), EXPLOSION_MODE_EFFECT, EXPLOSION_MODE_BLOCKS_ALWAYS)
                : EXPLOSION_MODE_DAMAGE;
        explosionFire = tag.getBoolean(EXPLOSION_FIRE_KEY);

        telegraphEnabled = !tag.contains(TELEGRAPH_ENABLED_KEY) || tag.getBoolean(TELEGRAPH_ENABLED_KEY);
        setTelegraphStyle(tag.contains(TELEGRAPH_STYLE_KEY)
                ? tag.getInt(TELEGRAPH_STYLE_KEY) : TELEGRAPH_STYLE_BOTH);
        setTelegraphAbilities(tag.contains(TELEGRAPH_ABILITIES_KEY)
                ? restoreTelegraphAbilities(tag.getInt(TELEGRAPH_ABILITIES_KEY))
                : TELEGRAPH_ALL_ABILITIES);
        setTelegraphZoneRadius(tag.contains(TELEGRAPH_ZONE_RADIUS_KEY)
                ? tag.getInt(TELEGRAPH_ZONE_RADIUS_KEY) : DEFAULT_TELEGRAPH_ZONE_RADIUS);
        setTelegraphLeadTicks(tag.contains(TELEGRAPH_LEAD_KEY)
                ? tag.getInt(TELEGRAPH_LEAD_KEY) : DEFAULT_TELEGRAPH_LEAD_TICKS);
        telegraphDodge = !tag.contains(TELEGRAPH_DODGE_KEY) || tag.getBoolean(TELEGRAPH_DODGE_KEY);
        telegraphAnnounce = !tag.contains(TELEGRAPH_ANNOUNCE_KEY) || tag.getBoolean(TELEGRAPH_ANNOUNCE_KEY);
        telegraphSound = !tag.contains(TELEGRAPH_SOUND_KEY) || tag.getBoolean(TELEGRAPH_SOUND_KEY);

        chestEnabled = tag.getBoolean(CHEST_ENABLED_KEY);
        chestBlock = tag.contains(CHEST_BLOCK_KEY)
                ? tag.getString(CHEST_BLOCK_KEY).trim() : ContainerBlockUtil.DEFAULT_ID;
        chestDelayTicks = tag.contains(CHEST_DELAY_KEY)
                ? Mth.clamp(tag.getInt(CHEST_DELAY_KEY), MIN_CHEST_DELAY_TICKS, MAX_CHEST_DELAY_TICKS) : 0;
        chestLifetimeTicks = tag.contains(CHEST_LIFETIME_KEY)
                ? Mth.clamp(tag.getInt(CHEST_LIFETIME_KEY), MIN_CHEST_LIFETIME_TICKS, MAX_CHEST_LIFETIME_TICKS)
                : 6000;
        chestName = tag.getString(CHEST_NAME_KEY).trim();
        chestUseNpcDrops = tag.getBoolean(CHEST_NPC_DROPS_KEY);
        chestLootTable = tag.getString(CHEST_LOOT_TABLE_KEY).trim();
        chestLoot.readFromNBT(tag, CHEST_LOOT_KEY);
        chestPlacement = tag.contains(CHEST_PLACEMENT_KEY)
                ? Mth.clamp(tag.getInt(CHEST_PLACEMENT_KEY), CHEST_PLACEMENT_DEATH, CHEST_PLACEMENT_FIXED)
                : CHEST_PLACEMENT_DEATH;
        chestOffsetX = offset(tag, CHEST_OFFSET_X_KEY);
        chestOffsetY = offset(tag, CHEST_OFFSET_Y_KEY);
        chestOffsetZ = offset(tag, CHEST_OFFSET_Z_KEY);
        chestFixedX = coordinate(tag, CHEST_FIXED_X_KEY);
        chestFixedY = coordinate(tag, CHEST_FIXED_Y_KEY);
        chestFixedZ = coordinate(tag, CHEST_FIXED_Z_KEY);
        chestStyle = BossChestStyles.normalize(tag.getString(CHEST_STYLE_KEY));

        bossBarStyle = BossBarStyles.normalize(tag.getString(BOSS_BAR_STYLE_KEY));
        bossBarScalePercent = tag.contains(BOSS_BAR_SCALE_KEY)
                ? Mth.clamp(tag.getInt(BOSS_BAR_SCALE_KEY), MIN_BOSS_BAR_SCALE_PERCENT,
                MAX_BOSS_BAR_SCALE_PERCENT) : DEFAULT_BOSS_BAR_SCALE_PERCENT;
    }

    private void readPhases(CompoundTag tag) {
        phases.clear();
        if (tag.contains(PHASES_KEY, Tag.TAG_LIST)) {
            ListTag list = tag.getList(PHASES_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && phases.size() < MAX_PHASES; i++) {
                BossPhaseData phase = new BossPhaseData();
                phase.readFromNBT(list.getCompound(i));
                phases.add(phase);
            }
        } else if (tag.contains(PHASE_ONE_KEY, Tag.TAG_COMPOUND)) {
            // Boss saved by 1.3.0 - 1.6.x: exactly two phases with a single threshold.
            BossPhaseData first = new BossPhaseData();
            first.readFromNBT(tag.getCompound(PHASE_ONE_KEY));
            phases.add(first);
            if (tag.contains(PHASE_TWO_KEY, Tag.TAG_COMPOUND)) {
                BossPhaseData second = new BossPhaseData();
                second.readFromNBT(tag.getCompound(PHASE_TWO_KEY));
                second.setStartHealthPercent(tag.contains(PHASE_THRESHOLD_KEY)
                        ? tag.getInt(PHASE_THRESHOLD_KEY) : 50);
                phases.add(second);
            }
        } else {
            // Boss saved by 1.3.0 or older: only the shared teleport delay existed.
            int min = tag.contains("GeckoTeleportPathMinDelay") ? tag.getInt("GeckoTeleportPathMinDelay") : 60;
            int max = tag.contains("GeckoTeleportPathMaxDelay") ? tag.getInt("GeckoTeleportPathMaxDelay") : 100;
            for (int i = 0; i < 2; i++) {
                BossPhaseData phase = new BossPhaseData();
                phase.setTeleportDelayRange(min, max);
                phases.add(phase);
            }
            phases.get(1).setStartHealthPercent(50);
        }

        if (phases.isEmpty()) {
            phases.add(new BossPhaseData());
        }
        phases.get(0).setStartHealthPercent(100);
    }

    public int getPhaseCount() {
        return phases.size();
    }

    /**
     * Resizes the phase list. New phases start out spread evenly across the health bar so
     * a freshly added phase already does something instead of never triggering.
     */
    public void setPhaseCount(int count) {
        count = Mth.clamp(count, MIN_PHASES, MAX_PHASES);
        while (phases.size() > count) {
            phases.remove(phases.size() - 1);
        }
        while (phases.size() < count) {
            BossPhaseData phase = new BossPhaseData();
            // Spread the newcomers evenly: 2 phases give 100/50, 3 give 100/67/33.
            // Phases that already exist keep whatever threshold was configured for them.
            int index = phases.size();
            phase.setStartHealthPercent(Math.round(100.0F * (count - index) / count));
            phases.add(phase);
        }
        phases.get(0).setStartHealthPercent(100);
    }

    public BossPhaseData getPhase(int index) {
        return phases.get(Mth.clamp(index, 0, phases.size() - 1));
    }

    /**
     * @return the index of the phase that owns this health percentage - the last one whose
     *         threshold the boss has already dropped to
     */
    public int resolvePhaseIndex(int healthPercent) {
        int result = 0;
        for (int i = 1; i < phases.size(); i++) {
            if (healthPercent <= phases.get(i).getStartHealthPercent()) {
                result = i;
            }
        }
        return result;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isCombatOnly() { return combatOnly; }
    public void setCombatOnly(boolean combatOnly) { this.combatOnly = combatOnly; }
    public boolean isStationary() { return stationary; }
    public void setStationary(boolean stationary) { this.stationary = stationary; }
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = Mth.clamp(order, ORDER_SEQUENTIAL, ORDER_RANDOM); }
    public boolean shouldPlaySound() { return playSound; }
    public void setPlaySound(boolean playSound) { this.playSound = playSound; }
    public String getPhaseTransitionAnimation() { return phaseTransitionAnimation; }
    public void setPhaseTransitionAnimation(String value) {
        phaseTransitionAnimation = value == null ? "" : value.trim();
    }
    public int getPhaseTransitionLockTicks() { return phaseTransitionLockTicks; }
    public void setPhaseTransitionLockTicks(int value) { phaseTransitionLockTicks = Mth.clamp(value, 0, 1200); }

    /** When set, the boss always switches to the closest reachable player instead of keeping its first target. */
    public boolean isTargetNearestPlayer() { return targetNearestPlayer; }
    public void setTargetNearestPlayer(boolean value) { targetNearestPlayer = value; }
    public int getTargetSearchRadius() { return targetSearchRadius; }
    public void setTargetSearchRadius(int value) { targetSearchRadius = Mth.clamp(value, 4, 128); }
    public int getTargetRecheckTicks() { return targetRecheckTicks; }
    public void setTargetRecheckTicks(int value) { targetRecheckTicks = Mth.clamp(value, 1, 200); }
    public boolean isTargetRequiresLineOfSight() { return targetRequiresLineOfSight; }
    public void setTargetRequiresLineOfSight(boolean value) { targetRequiresLineOfSight = value; }
    /** Keeps the current player target even after it left the search radius. */
    public boolean isKeepTargetOutOfRange() { return keepTargetOutOfRange; }
    public void setKeepTargetOutOfRange(boolean value) { keepTargetOutOfRange = value; }
    /** Widens or narrows the pool every ability and the auto-retarget search draw from. */
    public int getAbilityTargetKind() { return abilityTargetKind; }
    public void setAbilityTargetKind(int value) {
        abilityTargetKind = Mth.clamp(value, ABILITY_TARGET_PLAYERS, ABILITY_TARGET_ALL);
    }

    public boolean isAggroZoneEnabled() { return aggroZoneEnabled; }
    public void setAggroZoneEnabled(boolean value) { aggroZoneEnabled = value; }
    public int getAggroZoneX1() { return aggroZoneX1; }
    public int getAggroZoneY1() { return aggroZoneY1; }
    public int getAggroZoneZ1() { return aggroZoneZ1; }
    public int getAggroZoneX2() { return aggroZoneX2; }
    public int getAggroZoneY2() { return aggroZoneY2; }
    public int getAggroZoneZ2() { return aggroZoneZ2; }
    public void setAggroZoneCorner1(int x, int y, int z) {
        aggroZoneX1 = aggroZoneCoordinate(x);
        aggroZoneY1 = aggroZoneCoordinate(y);
        aggroZoneZ1 = aggroZoneCoordinate(z);
    }
    public void setAggroZoneCorner2(int x, int y, int z) {
        aggroZoneX2 = aggroZoneCoordinate(x);
        aggroZoneY2 = aggroZoneCoordinate(y);
        aggroZoneZ2 = aggroZoneCoordinate(z);
    }
    public int getAggroZoneRecheckTicks() { return aggroZoneRecheckTicks; }
    public void setAggroZoneRecheckTicks(int value) {
        aggroZoneRecheckTicks = Mth.clamp(value, MIN_AGGRO_ZONE_RECHECK_TICKS,
                MAX_AGGRO_ZONE_RECHECK_TICKS);
    }
    public int getAggroZoneTargetMode() { return aggroZoneTargetMode; }
    public void setAggroZoneTargetMode(int value) {
        aggroZoneTargetMode = Mth.clamp(value, AGGRO_ZONE_TARGET_NEAREST, AGGRO_ZONE_TARGET_RANDOM);
    }
    public boolean isAggroZoneKeepInside() { return aggroZoneKeepInside; }
    public void setAggroZoneKeepInside(boolean value) { aggroZoneKeepInside = value; }

    public boolean isHealthScalingEnabled() { return healthScalingEnabled; }
    public void setHealthScalingEnabled(boolean value) { healthScalingEnabled = value; }
    public int getHealthScalingMode() { return healthScalingMode; }
    public void setHealthScalingMode(int value) {
        healthScalingMode = Mth.clamp(value, HEALTH_SCALING_PERCENT, HEALTH_SCALING_PERCENT_AND_FLAT);
    }
    public int getHealthPerPlayerPercent() { return healthPerPlayerPercent; }
    public void setHealthPerPlayerPercent(int value) {
        healthPerPlayerPercent = Mth.clamp(value, MIN_HEALTH_PER_PLAYER_PERCENT,
                MAX_HEALTH_PER_PLAYER_PERCENT);
    }
    public int getHealthPerPlayerFlat() { return healthPerPlayerFlat; }
    public void setHealthPerPlayerFlat(int value) {
        healthPerPlayerFlat = Mth.clamp(value, MIN_HEALTH_PER_PLAYER_FLAT, MAX_HEALTH_PER_PLAYER_FLAT);
    }
    public int getHealthScalingUpdateMode() { return healthScalingUpdateMode; }
    public void setHealthScalingUpdateMode(int value) {
        healthScalingUpdateMode = Mth.clamp(value, HEALTH_SCALING_LOCK_AT_START,
                HEALTH_SCALING_DYNAMIC);
    }
    public int getHealthScalingAdjustment() { return healthScalingAdjustment; }
    public void setHealthScalingAdjustment(int value) {
        healthScalingAdjustment = Mth.clamp(value, HEALTH_SCALING_KEEP_PERCENT,
                HEALTH_SCALING_KEEP_CURRENT);
    }
    public int getHealthScalingPlayerCap() { return healthScalingPlayerCap; }
    public void setHealthScalingPlayerCap(int value) {
        healthScalingPlayerCap = Mth.clamp(value, MIN_HEALTH_SCALING_PLAYER_CAP,
                MAX_HEALTH_SCALING_PLAYER_CAP);
    }
    public int getHealthScalingRecheckTicks() { return healthScalingRecheckTicks; }
    public void setHealthScalingRecheckTicks(int value) {
        healthScalingRecheckTicks = Mth.clamp(value, MIN_HEALTH_SCALING_RECHECK_TICKS,
                MAX_HEALTH_SCALING_RECHECK_TICKS);
    }

    /** Shared by runtime and GUI so the preview cannot drift from the encounter formula. */
    public double calculateScaledMaxHealth(double baseMaxHealth, int countedPlayers) {
        double base = Double.isFinite(baseMaxHealth) ? Math.max(1.0D, baseMaxHealth) : 1.0D;
        int players = Mth.clamp(countedPlayers, 1, healthScalingPlayerCap);
        int extraPlayers = players - 1;
        double percentBonus = base * extraPlayers * healthPerPlayerPercent / 100.0D;
        double flatBonus = (double) extraPlayers * healthPerPlayerFlat;
        double scaled = switch (healthScalingMode) {
            case HEALTH_SCALING_FLAT -> base + flatBonus;
            case HEALTH_SCALING_PERCENT_AND_FLAT -> base + percentBonus + flatBonus;
            default -> base + percentBonus;
        };
        if (!Double.isFinite(scaled)) {
            scaled = Double.MAX_VALUE;
        }
        return Attributes.MAX_HEALTH.value().sanitizeValue(Math.max(1.0D, scaled));
    }

    /** How long the boss has to be left alone before the encounter counts as over. */
    public int getResetTicks() { return resetTicks; }
    public void setResetTicks(int value) {
        resetTicks = Mth.clamp(value, MIN_RESET_TICKS, MAX_RESET_TICKS);
    }
    /** Whether the reset also puts the boss back to full health. */
    public boolean isResetHeal() { return resetHeal; }
    public void setResetHeal(boolean value) { resetHeal = value; }
    /** Whether the reset also sends the boss back to where it stood when it activated. */
    public boolean isResetReturn() { return resetReturn; }
    public void setResetReturn(boolean value) { resetReturn = value; }
    public boolean isHomeLeashEnabled() { return homeLeashEnabled; }
    public void setHomeLeashEnabled(boolean value) { homeLeashEnabled = value; }
    public int getHomeLeashRadius() { return homeLeashRadius; }
    public void setHomeLeashRadius(int value) {
        homeLeashRadius = Mth.clamp(value, MIN_HOME_LEASH_RADIUS, MAX_HOME_LEASH_RADIUS);
    }
    public boolean isHomeLeashVertical() { return homeLeashVertical; }
    public void setHomeLeashVertical(boolean value) { homeLeashVertical = value; }
    public int getHomeLeashGraceTicks() { return homeLeashGraceTicks; }
    public void setHomeLeashGraceTicks(int value) {
        homeLeashGraceTicks = Mth.clamp(value, MIN_HOME_LEASH_GRACE_TICKS,
                MAX_HOME_LEASH_GRACE_TICKS);
    }

    /** Whether the boss doubles its stats once the encounter has dragged on long enough. */
    public boolean isRageEnabled() { return rageEnabled; }
    public void setRageEnabled(boolean value) { rageEnabled = value; }
    /** Ticks from the first tick of the fight until the boss enrages. */
    public int getRageDelayTicks() { return rageDelayTicks; }
    public void setRageDelayTicks(int value) {
        rageDelayTicks = Mth.clamp(value, MIN_RAGE_DELAY_TICKS, MAX_RAGE_DELAY_TICKS);
    }
    /** What every scaled stat is multiplied by, in percent: 200 doubles them. */
    public int getRageMultiplierPercent() { return rageMultiplierPercent; }
    public void setRageMultiplierPercent(int value) {
        rageMultiplierPercent = Mth.clamp(value, MIN_RAGE_MULTIPLIER_PERCENT, MAX_RAGE_MULTIPLIER_PERCENT);
    }
    public String getRageAnimation() { return rageAnimation; }
    public void setRageAnimation(String value) { rageAnimation = value == null ? "" : value.trim(); }
    /** How long the boss stands still after enraging, so the animation can play out. */
    public int getRageLockTicks() { return rageLockTicks; }
    public void setRageLockTicks(int value) { rageLockTicks = Mth.clamp(value, 0, 1200); }

    /** Whether the minions this boss summoned are cleaned up once the boss dies. */
    public boolean isClearMinionsOnDeath() { return clearMinionsOnDeath; }
    public void setClearMinionsOnDeath(boolean value) { clearMinionsOnDeath = value; }
    /** Whether they are cleaned up when the boss disengages and the encounter resets. */
    public boolean isClearMinionsOnReset() { return clearMinionsOnReset; }
    public void setClearMinionsOnReset(boolean value) { clearMinionsOnReset = value; }
    public int getMinionRemovalMode() { return minionRemovalMode; }
    public void setMinionRemovalMode(int value) {
        minionRemovalMode = Mth.clamp(value, MINION_REMOVAL_VANISH, MINION_REMOVAL_KILL);
    }

    public boolean isTotemsEnabled() { return totemsEnabled; }
    public void setTotemsEnabled(boolean value) { totemsEnabled = value; }
    public boolean isTotemGrantInvulnerability() { return totemGrantInvulnerability; }
    public void setTotemGrantInvulnerability(boolean value) { totemGrantInvulnerability = value; }
    public boolean isTotemHoldBoss() { return totemHoldBoss; }
    public void setTotemHoldBoss(boolean value) { totemHoldBoss = value; }
    /**
     * Whether a standing formation stops the boss starting anything of its own.
     *
     * <p>The addon's rotation and nothing else. The melee and ranged swings CustomNPCs' own
     * ai makes are outside this flag on purpose: they are not the boss' cast list, and a
     * silenced statue that still punches whoever walks into it is the wanted shape.</p>
     */
    public boolean isTotemSuppressAbilities() { return totemSuppressAbilities; }
    public void setTotemSuppressAbilities(boolean value) { totemSuppressAbilities = value; }
    /**
     * Whether a standing formation keeps this boss off everyone else's aiming list.
     *
     * <p>The choosing only. An area slam, a corridor or a random boulder that happens to
     * cover the spot still lands - going unhurt is what {@link #isTotemGrantInvulnerability()}
     * is for, and the two are meant to be switched on together.</p>
     */
    public boolean isTotemUntargetable() { return totemUntargetable; }
    public void setTotemUntargetable(boolean value) { totemUntargetable = value; }
    public int getTotemProtectionMode() { return totemProtectionMode; }
    public void setTotemProtectionMode(int value) {
        totemProtectionMode = Mth.clamp(value, TOTEM_PROTECTION_FULL_IMMUNITY,
                TOTEM_PROTECTION_LETHAL_GUARD);
    }
    public int getTotemActivationMode() { return totemActivationMode; }
    public void setTotemActivationMode(int value) {
        totemActivationMode = Mth.clamp(value, TOTEM_ACTIVATION_ALWAYS, TOTEM_ACTIVATION_PHASE_ENTER);
    }
    public int getTotemActivationPhase() { return totemActivationPhase; }
    public void setTotemActivationPhase(int value) {
        totemActivationPhase = Mth.clamp(value, MIN_PHASES, MAX_PHASES);
    }
    public int getTotemActivationDelayTicks() { return totemActivationDelayTicks; }
    public void setTotemActivationDelayTicks(int value) {
        totemActivationDelayTicks = Mth.clamp(value, MIN_TOTEM_ACTIVATION_DELAY_TICKS,
                MAX_TOTEM_DELAY_TICKS);
    }
    public int getTotemRespawnMode() { return totemRespawnMode; }
    public void setTotemRespawnMode(int value) {
        totemRespawnMode = Mth.clamp(value, TOTEM_RESPAWN_NEVER, TOTEM_RESPAWN_DELAYED);
    }
    public int getTotemRespawnDelayTicks() { return totemRespawnDelayTicks; }
    public void setTotemRespawnDelayTicks(int value) {
        totemRespawnDelayTicks = Mth.clamp(value, MIN_TOTEM_RESPAWN_DELAY_TICKS,
                MAX_TOTEM_DELAY_TICKS);
    }
    public boolean isTotemResetHealth() { return totemResetHealth; }
    public void setTotemResetHealth(boolean value) { totemResetHealth = value; }
    public boolean isTotemRemoveOnBossDeath() { return totemRemoveOnBossDeath; }
    public void setTotemRemoveOnBossDeath(boolean value) { totemRemoveOnBossDeath = value; }
    public String getTotemBeamStyle() { return totemBeamStyle; }
    public void setTotemBeamStyle(String value) { totemBeamStyle = HookCordStyles.normalize(value); }
    public int getTotemBeamWidthPercent() { return totemBeamWidthPercent; }
    public void setTotemBeamWidthPercent(int value) { totemBeamWidthPercent = Mth.clamp(value, 25, 400); }
    public int getTotemBeamSagPercent() { return totemBeamSagPercent; }
    public void setTotemBeamSagPercent(int value) { totemBeamSagPercent = Mth.clamp(value, 0, 200); }
    public BossTotemList getTotems() { return totems; }

    /** Whether the boss detonates once it dies. */
    public boolean isExplosionEnabled() { return explosionEnabled; }
    public void setExplosionEnabled(boolean value) { explosionEnabled = value; }
    /** Ticks between the death and the blast, so the death animation can play out first. */
    public int getExplosionDelayTicks() { return explosionDelayTicks; }
    public void setExplosionDelayTicks(int value) { explosionDelayTicks = Mth.clamp(value, 0, 1200); }
    /** Blast radius, on the same scale as vanilla: TNT is 4. */
    public int getExplosionPower() { return explosionPower; }
    public void setExplosionPower(int value) { explosionPower = Mth.clamp(value, 1, 20); }
    public int getExplosionMode() { return explosionMode; }
    public void setExplosionMode(int value) {
        explosionMode = Mth.clamp(value, EXPLOSION_MODE_EFFECT, EXPLOSION_MODE_BLOCKS_ALWAYS);
    }
    public boolean isExplosionFire() { return explosionFire; }
    public void setExplosionFire(boolean value) { explosionFire = value; }

    /** Whether players are shown what is coming for as long as the boss winds up. */
    public boolean isTelegraphEnabled() { return telegraphEnabled; }
    public void setTelegraphEnabled(boolean value) { telegraphEnabled = value; }
    public int getTelegraphStyle() { return telegraphStyle; }
    public void setTelegraphStyle(int value) {
        telegraphStyle = Mth.clamp(value, TELEGRAPH_STYLE_ZONE, TELEGRAPH_STYLE_BOTH);
    }
    /** Whether the shape the ability covers is painted on the ground. */
    public boolean isTelegraphZone() { return telegraphStyle != TELEGRAPH_STYLE_AURA; }
    /** Whether the boss itself is lit up in the ability's colour. */
    public boolean isTelegraphAura() { return telegraphStyle != TELEGRAPH_STYLE_ZONE; }
    public int getTelegraphAbilities() { return telegraphAbilities; }
    public void setTelegraphAbilities(int value) { telegraphAbilities = value & TELEGRAPH_ALL_ABILITIES; }
    /** How wide a ring an aimed ability paints under its victim. */
    public int getTelegraphZoneRadius() { return telegraphZoneRadius; }
    public void setTelegraphZoneRadius(int value) {
        telegraphZoneRadius = Mth.clamp(value, MIN_TELEGRAPH_ZONE_RADIUS, MAX_TELEGRAPH_ZONE_RADIUS);
    }
    /**
     * A saved mask, with the bits of later abilities filled in where the save predates them.
     *
     * <p>A boss that had every ability warning on was saying "warn for everything", not
     * "warn for these eight", so it keeps warning for everything. One that had abilities
     * switched off was making a choice, and the new bit stays off rather than overriding
     * it - a newly added ability is off by default anyway, so nothing changes until a
     * builder turns it on and goes looking for its warning.</p>
     */
    private static int restoreTelegraphAbilities(int saved) {
        return saved == TELEGRAPH_ABILITIES_BEFORE_LINE || saved == TELEGRAPH_ABILITIES_BEFORE_GEYSER
                || saved == TELEGRAPH_ABILITIES_BEFORE_BOULDER
                || saved == TELEGRAPH_ABILITIES_BEFORE_TETHER
                || saved == TELEGRAPH_ABILITIES_BEFORE_GRAVITY
                ? TELEGRAPH_ALL_ABILITIES : saved;
    }

    public boolean isTelegraphAbility(int ability) {
        return isTelegraphable(ability) && (telegraphAbilities & 1 << ability) != 0;
    }
    public void setTelegraphAbility(int ability, boolean value) {
        if (!isTelegraphable(ability)) {
            return;
        }
        telegraphAbilities = value
                ? telegraphAbilities | 1 << ability
                : telegraphAbilities & ~(1 << ability);
    }
    /** Whether this ability has a bit in the mask at all; the blast has none. */
    private static boolean isTelegraphable(int ability) {
        return ability >= 0 && ability < Integer.SIZE && (TELEGRAPH_ALL_ABILITIES & 1 << ability) != 0;
    }
    /** Whether the ability's name is put in the action bar as the wind-up starts. */
    public boolean isTelegraphAnnounce() { return telegraphAnnounce; }
    public void setTelegraphAnnounce(boolean value) { telegraphAnnounce = value; }
    public boolean isTelegraphSound() { return telegraphSound; }
    public void setTelegraphSound(boolean value) { telegraphSound = value; }
    /** Ticks of warning a player gets before an ability lands, wind-up included. */
    public int getTelegraphLeadTicks() { return telegraphLeadTicks; }
    public void setTelegraphLeadTicks(int value) {
        telegraphLeadTicks = Mth.clamp(value, MIN_TELEGRAPH_LEAD_TICKS, MAX_TELEGRAPH_LEAD_TICKS);
    }
    /**
     * Whether getting out of the marked zone in time calls the ability off. Without it the
     * warning is only an announcement of a death that was already decided.
     */
    public boolean isTelegraphDodge() { return telegraphDodge; }
    public void setTelegraphDodge(boolean value) { telegraphDodge = value; }

    /** Whether a loot chest is left behind where the boss died. */
    public boolean isChestEnabled() { return chestEnabled; }
    public void setChestEnabled(boolean value) { chestEnabled = value; }
    /** Id of the block the chest is made of; anything that holds items will do. */
    public String getChestBlock() { return chestBlock; }
    public void setChestBlock(String value) {
        chestBlock = value == null || value.trim().isEmpty() ? ContainerBlockUtil.DEFAULT_ID : value.trim();
    }
    /** Ticks between the death and the chest, so the death animation can play out first. */
    public int getChestDelayTicks() { return chestDelayTicks; }
    public void setChestDelayTicks(int value) {
        chestDelayTicks = Mth.clamp(value, MIN_CHEST_DELAY_TICKS, MAX_CHEST_DELAY_TICKS);
    }
    /** How long the chest stands there before it and everything in it is deleted. */
    public int getChestLifetimeTicks() { return chestLifetimeTicks; }
    public void setChestLifetimeTicks(int value) {
        chestLifetimeTicks = Mth.clamp(value, MIN_CHEST_LIFETIME_TICKS, MAX_CHEST_LIFETIME_TICKS);
    }
    /** Title the chest shows when opened; empty means the boss' own name. */
    public String getChestName() { return chestName; }
    public void setChestName(String value) { chestName = value == null ? "" : value.trim(); }
    /** Whether the drops from the npc's own Inventory tab go into the chest instead of the ground. */
    public boolean isChestUseNpcDrops() { return chestUseNpcDrops; }
    public void setChestUseNpcDrops(boolean value) { chestUseNpcDrops = value; }
    /** Vanilla loot table rolled into the chest on top of everything else; empty means none. */
    public String getChestLootTable() { return chestLootTable; }
    public void setChestLootTable(String value) { chestLootTable = value == null ? "" : value.trim(); }
    public BossLootList getChestLoot() { return chestLoot; }

    private static int offset(CompoundTag tag, String key) {
        return tag.contains(key) ? Mth.clamp(tag.getInt(key), MIN_CHEST_OFFSET, MAX_CHEST_OFFSET) : 0;
    }

    private static int coordinate(CompoundTag tag, String key) {
        return tag.contains(key) ? Mth.clamp(tag.getInt(key), -MAX_CHEST_COORDINATE, MAX_CHEST_COORDINATE) : 0;
    }

    private static int aggroZoneCoordinate(int value) {
        return Mth.clamp(value, -MAX_AGGRO_ZONE_COORDINATE, MAX_AGGRO_ZONE_COORDINATE);
    }

    /** Which of the four spots the chest is put down at. */
    public int getChestPlacement() { return chestPlacement; }
    public void setChestPlacement(int value) {
        chestPlacement = Mth.clamp(value, CHEST_PLACEMENT_DEATH, CHEST_PLACEMENT_FIXED);
    }
    /** Shift applied to the death or arena spot, in blocks. */
    public int getChestOffsetX() { return chestOffsetX; }
    public int getChestOffsetY() { return chestOffsetY; }
    public int getChestOffsetZ() { return chestOffsetZ; }
    public void setChestOffset(int x, int y, int z) {
        chestOffsetX = Mth.clamp(x, MIN_CHEST_OFFSET, MAX_CHEST_OFFSET);
        chestOffsetY = Mth.clamp(y, MIN_CHEST_OFFSET, MAX_CHEST_OFFSET);
        chestOffsetZ = Mth.clamp(z, MIN_CHEST_OFFSET, MAX_CHEST_OFFSET);
    }
    /** The one spot the chest appears at in fixed mode, in the dimension the boss died in. */
    public int getChestFixedX() { return chestFixedX; }
    public int getChestFixedY() { return chestFixedY; }
    public int getChestFixedZ() { return chestFixedZ; }
    public void setChestFixed(int x, int y, int z) {
        chestFixedX = Mth.clamp(x, -MAX_CHEST_COORDINATE, MAX_CHEST_COORDINATE);
        chestFixedY = Mth.clamp(y, -MAX_CHEST_COORDINATE, MAX_CHEST_COORDINATE);
        chestFixedZ = Mth.clamp(z, -MAX_CHEST_COORDINATE, MAX_CHEST_COORDINATE);
    }
    /** Which skin the chest wears; vanilla means the plain block from {@link #getChestBlock()}. */
    public String getChestStyle() { return chestStyle; }
    public void setChestStyle(String value) { chestStyle = BossChestStyles.normalize(value); }
    public String getBossBarStyle() { return bossBarStyle; }
    public void setBossBarStyle(String value) { bossBarStyle = BossBarStyles.normalize(value); }
    /** How big the bar is drawn, as a percentage of the style's own size. */
    public int getBossBarScalePercent() { return bossBarScalePercent; }
    public void setBossBarScalePercent(int value) {
        bossBarScalePercent = Mth.clamp(value, MIN_BOSS_BAR_SCALE_PERCENT, MAX_BOSS_BAR_SCALE_PERCENT);
    }

    // Compatibility helpers retained for migrated NPCs and scripts.
    public BossPhaseData getPhaseOne() { return phases.get(0); }
    public BossPhaseData getPhaseTwo() { return getPhase(1); }
    public int getPhaseTwoHealthPercent() {
        return phases.size() > 1 ? phases.get(1).getStartHealthPercent() : 50;
    }
    public void setPhaseTwoHealthPercent(int value) {
        if (phases.size() > 1) {
            phases.get(1).setStartHealthPercent(value);
        }
    }
    public int getMinDelayTicks() { return phases.get(0).getTeleportMinDelayTicks(); }
    public int getMaxDelayTicks() { return phases.get(0).getTeleportMaxDelayTicks(); }
    public void setDelayRange(int min, int max) { phases.get(0).setTeleportDelayRange(min, max); }
}
