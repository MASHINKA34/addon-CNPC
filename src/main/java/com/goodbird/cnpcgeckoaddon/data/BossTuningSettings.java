package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The numbers and the noises that used to be literals in the boss code: the pause after an
 * action, the leash on a target, how often a warning is repainted, and every sound and puff
 * the boss itself makes rather than one of its abilities.
 *
 * <p>One of these hangs off {@link TeleportPathData#tuning()}, under the {@code GeckoBossTuning}
 * prefix. Every default here is exactly the constant it replaced, and a boss saved before any
 * of it existed carries none of these keys - so it reads back and fights, sounds and flickers
 * precisely as it did before. Nothing in here changes what an ability does; it is the trim
 * around the abilities, which is why it is one object on the boss rather than a field on each
 * of the two dozen ability settings.</p>
 */
public final class BossTuningSettings {

    /** What a chain may never be longer than, whatever the setting says: the recursion guard. */
    public static final int COMBO_LINK_CEILING = 64;

    public static final int MIN_POST_ACTION_LOCK = 0;
    public static final int MAX_POST_ACTION_LOCK = 200;
    public static final int MIN_RETRY = 1;
    public static final int MAX_RETRY = 600;
    public static final int MIN_BLOCK_FEEDBACK = 1;
    public static final int MAX_BLOCK_FEEDBACK = 100;
    public static final int MAX_DODGE_RETRY = 1200;
    public static final int MIN_TURN_DEGREES = 1;
    public static final int MAX_TURN_DEGREES = 180;
    public static final int MIN_TARGET_LEASH_PERCENT = 100;
    public static final int MAX_TARGET_LEASH_PERCENT = 400;

    public static final int MIN_TELEGRAPH_AUDIENCE = 8;
    public static final int MAX_TELEGRAPH_AUDIENCE = 256;
    public static final int MIN_TELEGRAPH_INTERVAL = 1;
    public static final int MAX_TELEGRAPH_INTERVAL = 10;
    public static final int MAX_AURA_PARTICLES = 64;
    public static final int MIN_FADED_PERCENT = 10;
    public static final int MAX_FADED_PERCENT = 100;
    public static final int MIN_MELEE_HALF_ANGLE = 5;
    public static final int MAX_MELEE_HALF_ANGLE = 180;
    public static final int MIN_SPAWN_RING_RADIUS = 1;
    public static final int MAX_SPAWN_RING_RADIUS = 100;
    public static final int MAX_SPAWN_RINGS = 64;

    public static final int MAX_COMBO_STALE = 6000;
    public static final int MAX_COMBO_RETRY_WINDOW = 1200;
    public static final int MIN_COMBO_LINKS = 1;

    public static final int MIN_TOTEM_LINK_DURATION = 20;
    public static final int MAX_TOTEM_LINK = 1200;
    public static final int MIN_TOTEM_LINK_REFRESH = 10;
    public static final int MIN_TOTEM_FIT_HEIGHT = 5;
    public static final int MAX_TOTEM_FIT_HEIGHT = 80;
    public static final int MIN_TOTEM_FIT_HALF_WIDTH = 1;
    public static final int MAX_TOTEM_FIT_HALF_WIDTH = 40;
    public static final int MAX_TOTEM_REPORT_RANGE = 128;

    public static final int MAX_CHEST_SEARCH = 8;
    public static final int MAX_CHEST_DROP_HEIGHT = 64;
    public static final int MIN_CHEST_STAGED_TIMEOUT = 20;
    public static final int MAX_CHEST_STAGED_TIMEOUT = 1200;
    public static final int MAX_CHEST_AFTER_EXPLOSION = 100;
    public static final int MAX_EXPLOSION_PARTICLE_PERCENT = 400;

    public static final int MIN_HEALTH_LINK_ANNOUNCE = 5;
    public static final int MAX_HEALTH_LINK_ANNOUNCE = 200;
    public static final int MAX_RGB = 0xFFFFFF;
    public static final int MIN_LETHAL_GUARD_HEALTH = 1;
    public static final int MAX_LETHAL_GUARD_HEALTH = 200;

    public static final int MIN_WAVE_CORRIDOR_SPEED = 1;
    public static final int MAX_WAVE_CORRIDOR_SPEED = 50;
    public static final int MIN_WAVE_TICKS = 1;
    public static final int MAX_WAVE_TICKS = 600;
    public static final int MIN_WAVE_FLOOR_DEPTH = 1;
    public static final int MAX_WAVE_FLOOR_DEPTH = 32;
    public static final int MIN_WAVE_BLOCK_LIFETIME = 5;
    public static final int MAX_WAVE_BLOCK_LIFETIME = 400;

    /** Every key below carries this, so the boss tag stays one flat block. */
    private static final String PREFIX = "GeckoBossTuning";

    // Rotation and target.
    private int postActionLockTicks = 10;
    private int retryShortTicks = 5;
    private int retryTicks = 10;
    private int retryLongTicks = 20;
    private int blockFeedbackIntervalTicks = 5;
    private int dodgeRetryTicks = 40;
    private int lineFaceTurnDegrees = 15;
    private int trackTurnDegrees = 90;
    /** How far past its search radius the boss keeps a target, as a percentage of that radius. */
    private int targetLeashPercent = 150;

    // Warnings.
    private int telegraphAudienceRange = 64;
    private int telegraphIntervalTicks = 2;
    private int telegraphAuraParticles = 6;
    private int telegraphFadedPercent = 55;
    private int telegraphMeleeHalfAngle = 60;
    /** Tenths of a block. */
    private int telegraphSpawnRingRadius = 10;
    private int telegraphSpawnRings = 8;
    private final BossSoundCue telegraphSound =
            new BossSoundCue("minecraft:block.note_block.bell", 0.8F, 0.6F);

    // Chains.
    private int comboStaleTicks = 200;
    private int comboRetryWindowTicks = 60;
    private int comboMaxLinks = BossAbilityKind.COUNT;

    // Totems and the hits that bounce off a boss.
    private int totemRetryIntervalTicks = 20;
    private int totemLinkDurationTicks = 200;
    private int totemLinkRefreshTicks = 160;
    /** Tenths of a block: the box a totem has to fit in where it is put. */
    private int totemFitHeight = 18;
    private int totemFitHalfWidth = 3;
    private int totemReportRange = 48;
    private final BossSoundCue totemHitSound = new BossSoundCue("minecraft:item.shield.block", 0.8F, 0.9F);
    private final BossParticleCue totemHitParticles = new BossParticleCue("minecraft:enchant", 8);
    private final BossSoundCue totemLinkSound =
            new BossSoundCue("minecraft:block.amethyst_block.resonate", 1.0F, 1.2F);
    private final BossSoundCue blockedHitSound =
            new BossSoundCue("minecraft:entity.zombie.attack_iron_door", 1.2F, 0.6F);
    private final BossParticleCue blockedHitParticles = new BossParticleCue("minecraft:crit", 20);

    // The chest, the blast, the rage, the hop and the minions.
    private int chestSearchRadius = 2;
    private int chestSearchHeight = 2;
    private int chestMaxDropHeight = 8;
    private int chestStagedDropsTimeoutTicks = 100;
    private int chestAfterExplosionTicks = 2;
    private final BossSoundCue explosionSound =
            new BossSoundCue("minecraft:entity.generic.explode", 4.0F, 0.9F);
    /** How many of the blast's own particle count are actually spat out, as a percentage. */
    private int explosionParticlePercent = 100;
    private final BossSoundCue rageSound =
            new BossSoundCue("minecraft:entity.ender_dragon.growl", 2.0F, 0.7F);
    private final BossParticleCue rageParticles = new BossParticleCue("minecraft:angry_villager", 40);
    private final BossParticleCue rageSmoke = new BossParticleCue("minecraft:large_smoke", 30);
    private final BossSoundCue teleportSound =
            new BossSoundCue("minecraft:entity.enderman.teleport", 1.0F, 1.0F);
    private final BossParticleCue minionDespawnParticles = new BossParticleCue("minecraft:poof", 8);

    // The health link and the blow that would have killed.
    private int healthLinkAnnounceIntervalTicks = 20;
    private int healthLinkDownedColor = 0xFF6A5A;
    private final BossSoundCue healthLinkDownedSound =
            new BossSoundCue("minecraft:entity.ravager.stunned", 1.2F, 0.7F);
    private final BossParticleCue healthLinkDownedParticles = new BossParticleCue("minecraft:soul", 24);
    private final BossSoundCue healthLinkReviveSound =
            new BossSoundCue("minecraft:item.totem.use", 1.0F, 1.0F);
    private final BossParticleCue healthLinkReviveParticles =
            new BossParticleCue("minecraft:totem_of_undying", 40);
    /** Tenths of a heart point: what a blow that would have killed leaves standing. */
    private int lethalGuardHealth = 10;

    // Waves.
    private int waveCorridorSpeed = 5;
    private int waveMinTicks = 10;
    private int waveMaxTicks = 60;
    private int waveFloorSearchDepth = 4;
    private int waveBlockLifetimeTicks = 40;
    private final BossSoundCue waveSoundVines =
            new BossSoundCue("minecraft:block.azalea_leaves.break", 2.5F, 0.7F);
    private final BossSoundCue waveSoundStone =
            new BossSoundCue("minecraft:block.stone.break", 4.0F, 0.5F);
    private final BossSoundCue waveSoundHurricane =
            new BossSoundCue("minecraft:entity.breeze.wind_burst", 3.0F, 0.8F);
    private final BossSoundCue waveSoundFire =
            new BossSoundCue("minecraft:item.firecharge.use", 3.0F, 0.7F);
    private final BossSoundCue waveSoundGhost =
            new BossSoundCue("minecraft:particle.soul_escape", 3.0F, 0.6F);
    private final BossSoundCue waveSoundSculk =
            new BossSoundCue("minecraft:block.sculk_shrieker.shriek", 2.5F, 0.9F);

    public int postActionLockTicks() { return postActionLockTicks; }

    public void setPostActionLockTicks(int value) {
        postActionLockTicks = Mth.clamp(value, MIN_POST_ACTION_LOCK, MAX_POST_ACTION_LOCK);
    }

    public int retryShortTicks() { return retryShortTicks; }

    public void setRetryShortTicks(int value) { retryShortTicks = Mth.clamp(value, MIN_RETRY, MAX_RETRY); }

    public int retryTicks() { return retryTicks; }

    public void setRetryTicks(int value) { retryTicks = Mth.clamp(value, MIN_RETRY, MAX_RETRY); }

    public int retryLongTicks() { return retryLongTicks; }

    public void setRetryLongTicks(int value) { retryLongTicks = Mth.clamp(value, MIN_RETRY, MAX_RETRY); }

    public int blockFeedbackIntervalTicks() { return blockFeedbackIntervalTicks; }

    public void setBlockFeedbackIntervalTicks(int value) {
        blockFeedbackIntervalTicks = Mth.clamp(value, MIN_BLOCK_FEEDBACK, MAX_BLOCK_FEEDBACK);
    }

    public int dodgeRetryTicks() { return dodgeRetryTicks; }

    public void setDodgeRetryTicks(int value) { dodgeRetryTicks = Mth.clamp(value, 0, MAX_DODGE_RETRY); }

    public float lineFaceTurnDegrees() { return lineFaceTurnDegrees; }

    public void setLineFaceTurnDegrees(int value) {
        lineFaceTurnDegrees = Mth.clamp(value, MIN_TURN_DEGREES, MAX_TURN_DEGREES);
    }

    /** How fast the boss swings its head round to whatever it is fighting, in degrees a tick. */
    public float trackTurnDegrees() { return trackTurnDegrees; }

    public void setTrackTurnDegrees(int value) {
        trackTurnDegrees = Mth.clamp(value, MIN_TURN_DEGREES, MAX_TURN_DEGREES);
    }

    public int targetLeashPercent() { return targetLeashPercent; }

    public void setTargetLeashPercent(int value) {
        targetLeashPercent = Mth.clamp(value, MIN_TARGET_LEASH_PERCENT, MAX_TARGET_LEASH_PERCENT);
    }

    /**
     * How far a target may get before the boss lets go of it, for a boss that searches
     * {@code searchRadius} blocks. The one place the leash is worked out: the chase, the hunt
     * and the health scaling all have to agree on who is still in the fight.
     */
    public double targetLeash(double searchRadius) {
        return searchRadius * targetLeashPercent / 100.0D;
    }

    public double telegraphAudienceRange() { return telegraphAudienceRange; }

    public void setTelegraphAudienceRange(int value) {
        telegraphAudienceRange = Mth.clamp(value, MIN_TELEGRAPH_AUDIENCE, MAX_TELEGRAPH_AUDIENCE);
    }

    public int telegraphIntervalTicks() { return telegraphIntervalTicks; }

    public void setTelegraphIntervalTicks(int value) {
        telegraphIntervalTicks = Mth.clamp(value, MIN_TELEGRAPH_INTERVAL, MAX_TELEGRAPH_INTERVAL);
    }

    public int telegraphAuraParticles() { return telegraphAuraParticles; }

    public void setTelegraphAuraParticles(int value) {
        telegraphAuraParticles = Mth.clamp(value, 0, MAX_AURA_PARTICLES);
    }

    public int telegraphFadedPercent() { return telegraphFadedPercent; }

    public void setTelegraphFadedPercent(int value) {
        telegraphFadedPercent = Mth.clamp(value, MIN_FADED_PERCENT, MAX_FADED_PERCENT);
    }

    /** How much of an ability's colour its faded half keeps. */
    public float telegraphFadedBrightness() { return telegraphFadedPercent / 100.0F; }

    public double telegraphMeleeHalfAngle() { return telegraphMeleeHalfAngle; }

    public void setTelegraphMeleeHalfAngle(int value) {
        telegraphMeleeHalfAngle = Mth.clamp(value, MIN_MELEE_HALF_ANGLE, MAX_MELEE_HALF_ANGLE);
    }

    /** Tenths of a block, as the screen edits it. */
    public int telegraphSpawnRingRadiusTenths() { return telegraphSpawnRingRadius; }

    public void setTelegraphSpawnRingRadiusTenths(int value) {
        telegraphSpawnRingRadius = Mth.clamp(value, MIN_SPAWN_RING_RADIUS, MAX_SPAWN_RING_RADIUS);
    }

    public double telegraphSpawnRingRadius() { return telegraphSpawnRingRadius / 10.0D; }

    public int telegraphSpawnRings() { return telegraphSpawnRings; }

    public void setTelegraphSpawnRings(int value) {
        telegraphSpawnRings = Mth.clamp(value, 0, MAX_SPAWN_RINGS);
    }

    public BossSoundCue telegraphSound() { return telegraphSound; }

    public int comboStaleTicks() { return comboStaleTicks; }

    public void setComboStaleTicks(int value) { comboStaleTicks = Mth.clamp(value, 0, MAX_COMBO_STALE); }

    public int comboRetryWindowTicks() { return comboRetryWindowTicks; }

    public void setComboRetryWindowTicks(int value) {
        comboRetryWindowTicks = Mth.clamp(value, 0, MAX_COMBO_RETRY_WINDOW);
    }

    public int comboMaxLinks() { return comboMaxLinks; }

    public void setComboMaxLinks(int value) {
        comboMaxLinks = Mth.clamp(value, MIN_COMBO_LINKS, COMBO_LINK_CEILING);
    }

    public int totemRetryIntervalTicks() { return totemRetryIntervalTicks; }

    public void setTotemRetryIntervalTicks(int value) {
        totemRetryIntervalTicks = Mth.clamp(value, MIN_RETRY, MAX_RETRY);
    }

    public int totemLinkDurationTicks() { return totemLinkDurationTicks; }

    public void setTotemLinkDurationTicks(int value) {
        totemLinkDurationTicks = Mth.clamp(value, MIN_TOTEM_LINK_DURATION, MAX_TOTEM_LINK);
    }

    /**
     * How often the link between a boss and its totem is sent again.
     *
     * <p>Always shorter than the link itself lasts: a refresh that arrived after the beam had
     * already gone out would leave the beam blinking once a cycle.</p>
     */
    public int totemLinkRefreshTicks() {
        return Math.min(totemLinkRefreshTicks, totemLinkDurationTicks - 1);
    }

    public void setTotemLinkRefreshTicks(int value) {
        totemLinkRefreshTicks = Mth.clamp(value, MIN_TOTEM_LINK_REFRESH, MAX_TOTEM_LINK);
    }

    public int totemFitHeightTenths() { return totemFitHeight; }

    public void setTotemFitHeightTenths(int value) {
        totemFitHeight = Mth.clamp(value, MIN_TOTEM_FIT_HEIGHT, MAX_TOTEM_FIT_HEIGHT);
    }

    public double totemFitHeight() { return totemFitHeight / 10.0D; }

    public int totemFitHalfWidthTenths() { return totemFitHalfWidth; }

    public void setTotemFitHalfWidthTenths(int value) {
        totemFitHalfWidth = Mth.clamp(value, MIN_TOTEM_FIT_HALF_WIDTH, MAX_TOTEM_FIT_HALF_WIDTH);
    }

    public double totemFitHalfWidth() { return totemFitHalfWidth / 10.0D; }

    public double totemReportRange() { return totemReportRange; }

    public void setTotemReportRange(int value) {
        totemReportRange = Mth.clamp(value, 0, MAX_TOTEM_REPORT_RANGE);
    }

    public BossSoundCue totemHitSound() { return totemHitSound; }

    public BossParticleCue totemHitParticles() { return totemHitParticles; }

    public BossSoundCue totemLinkSound() { return totemLinkSound; }

    public BossSoundCue blockedHitSound() { return blockedHitSound; }

    public BossParticleCue blockedHitParticles() { return blockedHitParticles; }

    public int chestSearchRadius() { return chestSearchRadius; }

    public void setChestSearchRadius(int value) { chestSearchRadius = Mth.clamp(value, 0, MAX_CHEST_SEARCH); }

    public int chestSearchHeight() { return chestSearchHeight; }

    public void setChestSearchHeight(int value) { chestSearchHeight = Mth.clamp(value, 0, MAX_CHEST_SEARCH); }

    public int chestMaxDropHeight() { return chestMaxDropHeight; }

    public void setChestMaxDropHeight(int value) {
        chestMaxDropHeight = Mth.clamp(value, 0, MAX_CHEST_DROP_HEIGHT);
    }

    public int chestStagedDropsTimeoutTicks() { return chestStagedDropsTimeoutTicks; }

    public void setChestStagedDropsTimeoutTicks(int value) {
        chestStagedDropsTimeoutTicks = Mth.clamp(value, MIN_CHEST_STAGED_TIMEOUT, MAX_CHEST_STAGED_TIMEOUT);
    }

    /** How long after the death blast the chest waits before it is placed. */
    public int chestAfterExplosionTicks() { return chestAfterExplosionTicks; }

    public void setChestAfterExplosionTicks(int value) {
        chestAfterExplosionTicks = Mth.clamp(value, 0, MAX_CHEST_AFTER_EXPLOSION);
    }

    public BossSoundCue explosionSound() { return explosionSound; }

    public int explosionParticlePercent() { return explosionParticlePercent; }

    public void setExplosionParticlePercent(int value) {
        explosionParticlePercent = Mth.clamp(value, 0, MAX_EXPLOSION_PARTICLE_PERCENT);
    }

    public BossSoundCue rageSound() { return rageSound; }

    public BossParticleCue rageParticles() { return rageParticles; }

    public BossParticleCue rageSmoke() { return rageSmoke; }

    public BossSoundCue teleportSound() { return teleportSound; }

    public BossParticleCue minionDespawnParticles() { return minionDespawnParticles; }

    public int healthLinkAnnounceIntervalTicks() { return healthLinkAnnounceIntervalTicks; }

    public void setHealthLinkAnnounceIntervalTicks(int value) {
        healthLinkAnnounceIntervalTicks = Mth.clamp(value, MIN_HEALTH_LINK_ANNOUNCE, MAX_HEALTH_LINK_ANNOUNCE);
    }

    public int healthLinkDownedColor() { return healthLinkDownedColor; }

    public void setHealthLinkDownedColor(int value) {
        healthLinkDownedColor = Mth.clamp(value, 0, MAX_RGB);
    }

    public BossSoundCue healthLinkDownedSound() { return healthLinkDownedSound; }

    public BossParticleCue healthLinkDownedParticles() { return healthLinkDownedParticles; }

    public BossSoundCue healthLinkReviveSound() { return healthLinkReviveSound; }

    public BossParticleCue healthLinkReviveParticles() { return healthLinkReviveParticles; }

    /** Tenths of a heart point, as the screen edits it. */
    public int lethalGuardHealthTenths() { return lethalGuardHealth; }

    public void setLethalGuardHealthTenths(int value) {
        lethalGuardHealth = Mth.clamp(value, MIN_LETHAL_GUARD_HEALTH, MAX_LETHAL_GUARD_HEALTH);
    }

    /** What a blow that would have killed leaves the boss standing on. */
    public float lethalGuardHealth() { return lethalGuardHealth / 10.0F; }

    public int waveCorridorSpeedTenths() { return waveCorridorSpeed; }

    public void setWaveCorridorSpeedTenths(int value) {
        waveCorridorSpeed = Mth.clamp(value, MIN_WAVE_CORRIDOR_SPEED, MAX_WAVE_CORRIDOR_SPEED);
    }

    /** Blocks a tick the front of a corridor wave travels at. */
    public double waveCorridorSpeed() { return waveCorridorSpeed / 10.0D; }

    public int waveMinTicks() { return waveMinTicks; }

    public void setWaveMinTicks(int value) { waveMinTicks = Mth.clamp(value, MIN_WAVE_TICKS, MAX_WAVE_TICKS); }

    public int waveMaxTicks() { return waveMaxTicks; }

    public void setWaveMaxTicks(int value) { waveMaxTicks = Mth.clamp(value, MIN_WAVE_TICKS, MAX_WAVE_TICKS); }

    public int waveFloorSearchDepth() { return waveFloorSearchDepth; }

    public void setWaveFloorSearchDepth(int value) {
        waveFloorSearchDepth = Mth.clamp(value, MIN_WAVE_FLOOR_DEPTH, MAX_WAVE_FLOOR_DEPTH);
    }

    public int waveBlockLifetimeTicks() { return waveBlockLifetimeTicks; }

    public void setWaveBlockLifetimeTicks(int value) {
        waveBlockLifetimeTicks = Mth.clamp(value, MIN_WAVE_BLOCK_LIFETIME, MAX_WAVE_BLOCK_LIFETIME);
    }

    /** The shout one wave style makes as it goes out; the styleless wave has none. */
    public BossSoundCue waveSound(String style) {
        return switch (AreaVfxStyles.normalize(style)) {
            case AreaVfxStyles.VINES -> waveSoundVines;
            case AreaVfxStyles.STONE -> waveSoundStone;
            case AreaVfxStyles.HURRICANE -> waveSoundHurricane;
            case AreaVfxStyles.FIRE -> waveSoundFire;
            case AreaVfxStyles.GHOST -> waveSoundGhost;
            case AreaVfxStyles.SCULK_WAVE -> waveSoundSculk;
            default -> null;
        };
    }

    void writeToNBT(CompoundTag tag) {
        tag.putInt(PREFIX + "PostActionLock", postActionLockTicks);
        tag.putInt(PREFIX + "RetryShort", retryShortTicks);
        tag.putInt(PREFIX + "Retry", retryTicks);
        tag.putInt(PREFIX + "RetryLong", retryLongTicks);
        tag.putInt(PREFIX + "BlockFeedbackInterval", blockFeedbackIntervalTicks);
        tag.putInt(PREFIX + "DodgeRetry", dodgeRetryTicks);
        tag.putInt(PREFIX + "LineFaceTurn", lineFaceTurnDegrees);
        tag.putInt(PREFIX + "TrackTurn", trackTurnDegrees);
        tag.putInt(PREFIX + "TargetLeashPercent", targetLeashPercent);

        tag.putInt(PREFIX + "TelegraphAudience", telegraphAudienceRange);
        tag.putInt(PREFIX + "TelegraphInterval", telegraphIntervalTicks);
        tag.putInt(PREFIX + "TelegraphAuraParticles", telegraphAuraParticles);
        tag.putInt(PREFIX + "TelegraphFadedPercent", telegraphFadedPercent);
        tag.putInt(PREFIX + "TelegraphMeleeHalfAngle", telegraphMeleeHalfAngle);
        tag.putInt(PREFIX + "TelegraphSpawnRingRadius", telegraphSpawnRingRadius);
        tag.putInt(PREFIX + "TelegraphSpawnRings", telegraphSpawnRings);
        telegraphSound.writeToNBT(tag, PREFIX + "TelegraphSound");

        tag.putInt(PREFIX + "ComboStale", comboStaleTicks);
        tag.putInt(PREFIX + "ComboRetryWindow", comboRetryWindowTicks);
        tag.putInt(PREFIX + "ComboMaxLinks", comboMaxLinks);

        tag.putInt(PREFIX + "TotemRetryInterval", totemRetryIntervalTicks);
        tag.putInt(PREFIX + "TotemLinkDuration", totemLinkDurationTicks);
        tag.putInt(PREFIX + "TotemLinkRefresh", totemLinkRefreshTicks);
        tag.putInt(PREFIX + "TotemFitHeight", totemFitHeight);
        tag.putInt(PREFIX + "TotemFitHalfWidth", totemFitHalfWidth);
        tag.putInt(PREFIX + "TotemReportRange", totemReportRange);
        totemHitSound.writeToNBT(tag, PREFIX + "TotemHitSound");
        totemHitParticles.writeToNBT(tag, PREFIX + "TotemHitParticles");
        totemLinkSound.writeToNBT(tag, PREFIX + "TotemLinkSound");
        blockedHitSound.writeToNBT(tag, PREFIX + "BlockedHitSound");
        blockedHitParticles.writeToNBT(tag, PREFIX + "BlockedHitParticles");

        tag.putInt(PREFIX + "ChestSearchRadius", chestSearchRadius);
        tag.putInt(PREFIX + "ChestSearchHeight", chestSearchHeight);
        tag.putInt(PREFIX + "ChestMaxDropHeight", chestMaxDropHeight);
        tag.putInt(PREFIX + "ChestStagedDropsTimeout", chestStagedDropsTimeoutTicks);
        tag.putInt(PREFIX + "ChestAfterExplosion", chestAfterExplosionTicks);
        explosionSound.writeToNBT(tag, PREFIX + "ExplosionSound");
        tag.putInt(PREFIX + "ExplosionParticlePercent", explosionParticlePercent);
        rageSound.writeToNBT(tag, PREFIX + "RageSound");
        rageParticles.writeToNBT(tag, PREFIX + "RageParticles");
        rageSmoke.writeToNBT(tag, PREFIX + "RageSmoke");
        teleportSound.writeToNBT(tag, PREFIX + "TeleportSound");
        minionDespawnParticles.writeToNBT(tag, PREFIX + "MinionDespawn");

        tag.putInt(PREFIX + "HealthLinkAnnounceInterval", healthLinkAnnounceIntervalTicks);
        tag.putInt(PREFIX + "HealthLinkDownedColor", healthLinkDownedColor);
        healthLinkDownedSound.writeToNBT(tag, PREFIX + "HealthLinkDownedSound");
        healthLinkDownedParticles.writeToNBT(tag, PREFIX + "HealthLinkDownedParticles");
        healthLinkReviveSound.writeToNBT(tag, PREFIX + "HealthLinkReviveSound");
        healthLinkReviveParticles.writeToNBT(tag, PREFIX + "HealthLinkReviveParticles");
        tag.putInt(PREFIX + "LethalGuardHealth", lethalGuardHealth);

        tag.putInt(PREFIX + "WaveCorridorSpeed", waveCorridorSpeed);
        tag.putInt(PREFIX + "WaveMinTicks", waveMinTicks);
        tag.putInt(PREFIX + "WaveMaxTicks", waveMaxTicks);
        tag.putInt(PREFIX + "WaveFloorSearchDepth", waveFloorSearchDepth);
        tag.putInt(PREFIX + "WaveBlockLifetime", waveBlockLifetimeTicks);
        waveSoundVines.writeToNBT(tag, PREFIX + "WaveSoundVines");
        waveSoundStone.writeToNBT(tag, PREFIX + "WaveSoundStone");
        waveSoundHurricane.writeToNBT(tag, PREFIX + "WaveSoundHurricane");
        waveSoundFire.writeToNBT(tag, PREFIX + "WaveSoundFire");
        waveSoundGhost.writeToNBT(tag, PREFIX + "WaveSoundGhost");
        waveSoundSculk.writeToNBT(tag, PREFIX + "WaveSoundSculkWave");
    }

    /** A tag with none of these keys is a boss from before any of it was a setting. */
    void readFromNBT(CompoundTag tag) {
        postActionLockTicks = value(tag, PREFIX + "PostActionLock", 10,
                MIN_POST_ACTION_LOCK, MAX_POST_ACTION_LOCK);
        retryShortTicks = value(tag, PREFIX + "RetryShort", 5, MIN_RETRY, MAX_RETRY);
        retryTicks = value(tag, PREFIX + "Retry", 10, MIN_RETRY, MAX_RETRY);
        retryLongTicks = value(tag, PREFIX + "RetryLong", 20, MIN_RETRY, MAX_RETRY);
        blockFeedbackIntervalTicks = value(tag, PREFIX + "BlockFeedbackInterval", 5,
                MIN_BLOCK_FEEDBACK, MAX_BLOCK_FEEDBACK);
        dodgeRetryTicks = value(tag, PREFIX + "DodgeRetry", 40, 0, MAX_DODGE_RETRY);
        lineFaceTurnDegrees = value(tag, PREFIX + "LineFaceTurn", 15, MIN_TURN_DEGREES, MAX_TURN_DEGREES);
        trackTurnDegrees = value(tag, PREFIX + "TrackTurn", 90, MIN_TURN_DEGREES, MAX_TURN_DEGREES);
        targetLeashPercent = value(tag, PREFIX + "TargetLeashPercent", 150,
                MIN_TARGET_LEASH_PERCENT, MAX_TARGET_LEASH_PERCENT);

        telegraphAudienceRange = value(tag, PREFIX + "TelegraphAudience", 64,
                MIN_TELEGRAPH_AUDIENCE, MAX_TELEGRAPH_AUDIENCE);
        telegraphIntervalTicks = value(tag, PREFIX + "TelegraphInterval", 2,
                MIN_TELEGRAPH_INTERVAL, MAX_TELEGRAPH_INTERVAL);
        telegraphAuraParticles = value(tag, PREFIX + "TelegraphAuraParticles", 6, 0, MAX_AURA_PARTICLES);
        telegraphFadedPercent = value(tag, PREFIX + "TelegraphFadedPercent", 55,
                MIN_FADED_PERCENT, MAX_FADED_PERCENT);
        telegraphMeleeHalfAngle = value(tag, PREFIX + "TelegraphMeleeHalfAngle", 60,
                MIN_MELEE_HALF_ANGLE, MAX_MELEE_HALF_ANGLE);
        telegraphSpawnRingRadius = value(tag, PREFIX + "TelegraphSpawnRingRadius", 10,
                MIN_SPAWN_RING_RADIUS, MAX_SPAWN_RING_RADIUS);
        telegraphSpawnRings = value(tag, PREFIX + "TelegraphSpawnRings", 8, 0, MAX_SPAWN_RINGS);
        telegraphSound.readFromNBT(tag, PREFIX + "TelegraphSound");

        comboStaleTicks = value(tag, PREFIX + "ComboStale", 200, 0, MAX_COMBO_STALE);
        comboRetryWindowTicks = value(tag, PREFIX + "ComboRetryWindow", 60, 0, MAX_COMBO_RETRY_WINDOW);
        comboMaxLinks = value(tag, PREFIX + "ComboMaxLinks", BossAbilityKind.COUNT,
                MIN_COMBO_LINKS, COMBO_LINK_CEILING);

        totemRetryIntervalTicks = value(tag, PREFIX + "TotemRetryInterval", 20, MIN_RETRY, MAX_RETRY);
        totemLinkDurationTicks = value(tag, PREFIX + "TotemLinkDuration", 200,
                MIN_TOTEM_LINK_DURATION, MAX_TOTEM_LINK);
        totemLinkRefreshTicks = value(tag, PREFIX + "TotemLinkRefresh", 160,
                MIN_TOTEM_LINK_REFRESH, MAX_TOTEM_LINK);
        totemFitHeight = value(tag, PREFIX + "TotemFitHeight", 18,
                MIN_TOTEM_FIT_HEIGHT, MAX_TOTEM_FIT_HEIGHT);
        totemFitHalfWidth = value(tag, PREFIX + "TotemFitHalfWidth", 3,
                MIN_TOTEM_FIT_HALF_WIDTH, MAX_TOTEM_FIT_HALF_WIDTH);
        totemReportRange = value(tag, PREFIX + "TotemReportRange", 48, 0, MAX_TOTEM_REPORT_RANGE);
        totemHitSound.readFromNBT(tag, PREFIX + "TotemHitSound");
        totemHitParticles.readFromNBT(tag, PREFIX + "TotemHitParticles");
        totemLinkSound.readFromNBT(tag, PREFIX + "TotemLinkSound");
        blockedHitSound.readFromNBT(tag, PREFIX + "BlockedHitSound");
        blockedHitParticles.readFromNBT(tag, PREFIX + "BlockedHitParticles");

        chestSearchRadius = value(tag, PREFIX + "ChestSearchRadius", 2, 0, MAX_CHEST_SEARCH);
        chestSearchHeight = value(tag, PREFIX + "ChestSearchHeight", 2, 0, MAX_CHEST_SEARCH);
        chestMaxDropHeight = value(tag, PREFIX + "ChestMaxDropHeight", 8, 0, MAX_CHEST_DROP_HEIGHT);
        chestStagedDropsTimeoutTicks = value(tag, PREFIX + "ChestStagedDropsTimeout", 100,
                MIN_CHEST_STAGED_TIMEOUT, MAX_CHEST_STAGED_TIMEOUT);
        chestAfterExplosionTicks = value(tag, PREFIX + "ChestAfterExplosion", 2, 0, MAX_CHEST_AFTER_EXPLOSION);
        explosionSound.readFromNBT(tag, PREFIX + "ExplosionSound");
        explosionParticlePercent = value(tag, PREFIX + "ExplosionParticlePercent", 100,
                0, MAX_EXPLOSION_PARTICLE_PERCENT);
        rageSound.readFromNBT(tag, PREFIX + "RageSound");
        rageParticles.readFromNBT(tag, PREFIX + "RageParticles");
        rageSmoke.readFromNBT(tag, PREFIX + "RageSmoke");
        teleportSound.readFromNBT(tag, PREFIX + "TeleportSound");
        minionDespawnParticles.readFromNBT(tag, PREFIX + "MinionDespawn");

        healthLinkAnnounceIntervalTicks = value(tag, PREFIX + "HealthLinkAnnounceInterval", 20,
                MIN_HEALTH_LINK_ANNOUNCE, MAX_HEALTH_LINK_ANNOUNCE);
        healthLinkDownedColor = value(tag, PREFIX + "HealthLinkDownedColor", 0xFF6A5A, 0, MAX_RGB);
        healthLinkDownedSound.readFromNBT(tag, PREFIX + "HealthLinkDownedSound");
        healthLinkDownedParticles.readFromNBT(tag, PREFIX + "HealthLinkDownedParticles");
        healthLinkReviveSound.readFromNBT(tag, PREFIX + "HealthLinkReviveSound");
        healthLinkReviveParticles.readFromNBT(tag, PREFIX + "HealthLinkReviveParticles");
        lethalGuardHealth = value(tag, PREFIX + "LethalGuardHealth", 10,
                MIN_LETHAL_GUARD_HEALTH, MAX_LETHAL_GUARD_HEALTH);

        waveCorridorSpeed = value(tag, PREFIX + "WaveCorridorSpeed", 5,
                MIN_WAVE_CORRIDOR_SPEED, MAX_WAVE_CORRIDOR_SPEED);
        waveMinTicks = value(tag, PREFIX + "WaveMinTicks", 10, MIN_WAVE_TICKS, MAX_WAVE_TICKS);
        waveMaxTicks = value(tag, PREFIX + "WaveMaxTicks", 60, MIN_WAVE_TICKS, MAX_WAVE_TICKS);
        waveFloorSearchDepth = value(tag, PREFIX + "WaveFloorSearchDepth", 4,
                MIN_WAVE_FLOOR_DEPTH, MAX_WAVE_FLOOR_DEPTH);
        waveBlockLifetimeTicks = value(tag, PREFIX + "WaveBlockLifetime", 40,
                MIN_WAVE_BLOCK_LIFETIME, MAX_WAVE_BLOCK_LIFETIME);
        waveSoundVines.readFromNBT(tag, PREFIX + "WaveSoundVines");
        waveSoundStone.readFromNBT(tag, PREFIX + "WaveSoundStone");
        waveSoundHurricane.readFromNBT(tag, PREFIX + "WaveSoundHurricane");
        waveSoundFire.readFromNBT(tag, PREFIX + "WaveSoundFire");
        waveSoundGhost.readFromNBT(tag, PREFIX + "WaveSoundGhost");
        waveSoundSculk.readFromNBT(tag, PREFIX + "WaveSoundSculkWave");
    }
}
