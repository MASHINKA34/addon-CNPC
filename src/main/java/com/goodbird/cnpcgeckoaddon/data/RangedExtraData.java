package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

public class RangedExtraData {
    /**
     * The furthest an npc may be told to back away.
     *
     * <p>The editor offers the same bound, but it is not the only way in: a number out of a
     * hand-edited save or an older world reaches {@code KeepDistanceGoal} exactly as a typed
     * one does, and the goal squares it.</p>
     */
    public static final int MAX_KEEP_DISTANCE = 64;

    /** Tenths of a block from the eyes, either way: the muzzle used to sit at {@code eyeY - 0.2}. */
    public static final int MIN_MUZZLE_HEIGHT = -20;
    public static final int MAX_MUZZLE_HEIGHT = 20;
    public static final int DEFAULT_MUZZLE_HEIGHT = -2;
    /** Tenths, the way every cue keeps a volume: the shot used to be played at 2.0F / 1.0F. */
    public static final int MAX_SHOT_VOLUME = 100;
    public static final int DEFAULT_SHOT_VOLUME = 20;
    public static final int MIN_SHOT_PITCH = 5;
    public static final int MAX_SHOT_PITCH = 30;
    public static final int DEFAULT_SHOT_PITCH = 10;

    /**
     * What CustomNPCs itself allows for the two ranged numbers this addon reads back out of
     * {@code DataRanged}, so its own editor is the only thing that decides them.
     *
     * <p>Both were held to figures of the addon's own invention, and neither was the one on
     * the screen: the explosion was forced to at least 1, so a projectile the CustomNPCs
     * editor was showing as "none" still went off, and the shot count was let up to 32, which
     * is three times what that editor offers and what its own load clamps to.</p>
     */
    public static final int MIN_EXPLODE_SIZE = 0;
    public static final int MAX_EXPLODE_SIZE = 3;
    public static final int MIN_SHOT_COUNT = 1;
    public static final int MAX_SHOT_COUNT = 10;

    /**
     * What an npc shoots when it has neither a usable entity of its own nor a projectile item.
     *
     * <p>CustomNPCs fires its own projectile whether or not the slot holds anything, and one
     * thrown as an empty stack takes the server down when it lands. An arrow is the default
     * rather than "nothing" so that an npc saved before this key existed goes on shooting
     * instead of going quiet; an empty id is how an npc is told not to shoot at all.</p>
     */
    public static final String DEFAULT_FALLBACK_PROJECTILE = "minecraft:arrow";
    private static final String FALLBACK_KEY = "GeckoNpcRangedFallback";

    /**
     * The window the addon's own ranged AI fires from, in tenths of a block.
     *
     * <p>Sixty-four blocks either way: what {@code DataRanged.getRange()} itself allows is a
     * hundred, but that is the distance a shot may cross, not one an npc is asked to hold.</p>
     */
    public static final int MAX_ENGAGE_TENTHS = 640;

    /** What an npc does about a target past the window: walk up to it, or stand and wait. */
    public static final int TOO_FAR_APPROACH = 0;
    public static final int TOO_FAR_WAIT = 1;

    /** And about one inside it: back off, swing at it, or shoot it point blank. */
    public static final int TOO_CLOSE_RETREAT = 0;
    public static final int TOO_CLOSE_MELEE = 1;
    public static final int TOO_CLOSE_FIRE = 2;

    /** How much of the target's own travel during the flight the shot is aimed ahead by. */
    public static final int MAX_LEAD_PERCENT = 100;

    /** The addon's own burst; nought leaves the volley to CustomNPCs' burst and burst delay. */
    public static final int MAX_BURST_SHOTS = 20;
    public static final int MIN_BURST_DELAY_TICKS = 1;
    public static final int MAX_BURST_DELAY_TICKS = 40;
    public static final int DEFAULT_BURST_DELAY_TICKS = 4;

    /** How wide the fan of one burst opens, in degrees of yaw; half of it on the pitch. */
    public static final int MAX_SPREAD_DEGREES = 45;

    /** The pause after a burst. Half a minute is as long as a fight stays interesting. */
    public static final int MAX_RELOAD_TICKS = 600;

    /** What an npc does about a target it cannot see. */
    public static final int LOS_CUSTOMNPCS = 0;
    public static final int LOS_WAIT = 1;
    public static final int LOS_LOB = 2;

    /** How long the ring stands where a lobbed shot is about to land, and how wide it is. */
    public static final int MAX_LOB_WARN_TICKS = 100;
    public static final int DEFAULT_LOB_WARN_TICKS = 20;
    public static final int MIN_LOB_WARN_RADIUS_TENTHS = 5;
    public static final int MAX_LOB_WARN_RADIUS_TENTHS = 60;
    public static final int DEFAULT_LOB_WARN_RADIUS_TENTHS = 15;

    private static final String ADDON_ENABLED_KEY = "GeckoNpcRangedAddon";
    private static final String ENGAGE_MIN_KEY = "GeckoNpcRangedEngageMin";
    private static final String ENGAGE_MAX_KEY = "GeckoNpcRangedEngageMax";
    private static final String TOO_FAR_KEY = "GeckoNpcRangedTooFar";
    private static final String TOO_CLOSE_KEY = "GeckoNpcRangedTooClose";
    private static final String LEAD_KEY = "GeckoNpcRangedLead";
    private static final String BURST_SHOTS_KEY = "GeckoNpcRangedBurstShots";
    private static final String BURST_DELAY_KEY = "GeckoNpcRangedBurstDelay";
    private static final String SPREAD_KEY = "GeckoNpcRangedSpread";
    private static final String RELOAD_KEY = "GeckoNpcRangedReload";
    private static final String RELOAD_ANIMATION_KEY = "GeckoNpcRangedReloadAnim";
    private static final String LOS_KEY = "GeckoNpcRangedLos";
    private static final String LOB_WARN_KEY = "GeckoNpcRangedLobWarn";
    private static final String LOB_WARN_RADIUS_KEY = "GeckoNpcRangedLobWarnRadius";
    /**
     * The cue prefixes. Each is a different string from every plain key above - the reload
     * cue writes {@code ...ReloadSoundOn} and the plain pause is {@code ...Reload} - so no
     * cue key can land on a number's key or the other way round.
     */
    private static final String RELOAD_SOUND_PREFIX = "GeckoNpcRangedReloadSound";
    private static final String LOB_WARN_PARTICLES_PREFIX = "GeckoNpcRangedLobWarnParticles";

    private String projectileEntity = "";
    private String fallbackProjectile = DEFAULT_FALLBACK_PROJECTILE;
    private int keepDistance = 0;
    private int muzzleHeightTenths = DEFAULT_MUZZLE_HEIGHT;
    private int shotSoundVolumeTenths = DEFAULT_SHOT_VOLUME;
    private int shotSoundPitchTenths = DEFAULT_SHOT_PITCH;
    private boolean rangedAddonEnabled;
    private int engageMinTenths;
    private int engageMaxTenths;
    private int tooFarMode = TOO_FAR_APPROACH;
    private int tooCloseMode = TOO_CLOSE_RETREAT;
    private int leadPercent;
    private int burstShots;
    private int burstDelayTicks = DEFAULT_BURST_DELAY_TICKS;
    private int spreadDegrees;
    private int reloadTicks;
    private String reloadAnimation = "";
    private int losMode = LOS_CUSTOMNPCS;
    private int lobWarnTicks = DEFAULT_LOB_WARN_TICKS;
    private int lobWarnRadiusTenths = DEFAULT_LOB_WARN_RADIUS_TENTHS;
    private final BossSoundCue reloadSound =
            new BossSoundCue("minecraft:item.crossbow.loading_middle", 1.0F, 1.0F);
    private final BossParticleCue lobWarnParticles = new BossParticleCue(BossParticleCue.DUST_ID, 12);

    public CompoundTag writeToNBT(CompoundTag nbttagcompound) {
        nbttagcompound.putString("GeckoProjectileEntity", projectileEntity);
        nbttagcompound.putString(FALLBACK_KEY, fallbackProjectile);
        nbttagcompound.putInt("GeckoKeepDistance", keepDistance);
        nbttagcompound.putInt("GeckoNpcRangedMuzzle", muzzleHeightTenths);
        nbttagcompound.putInt("GeckoNpcRangedShotVolume", shotSoundVolumeTenths);
        nbttagcompound.putInt("GeckoNpcRangedShotPitch", shotSoundPitchTenths);
        nbttagcompound.putBoolean(ADDON_ENABLED_KEY, rangedAddonEnabled);
        nbttagcompound.putInt(ENGAGE_MIN_KEY, engageMinTenths);
        nbttagcompound.putInt(ENGAGE_MAX_KEY, engageMaxTenths);
        nbttagcompound.putInt(TOO_FAR_KEY, tooFarMode);
        nbttagcompound.putInt(TOO_CLOSE_KEY, tooCloseMode);
        nbttagcompound.putInt(LEAD_KEY, leadPercent);
        nbttagcompound.putInt(BURST_SHOTS_KEY, burstShots);
        nbttagcompound.putInt(BURST_DELAY_KEY, burstDelayTicks);
        nbttagcompound.putInt(SPREAD_KEY, spreadDegrees);
        nbttagcompound.putInt(RELOAD_KEY, reloadTicks);
        nbttagcompound.putString(RELOAD_ANIMATION_KEY, reloadAnimation);
        nbttagcompound.putInt(LOS_KEY, losMode);
        nbttagcompound.putInt(LOB_WARN_KEY, lobWarnTicks);
        nbttagcompound.putInt(LOB_WARN_RADIUS_KEY, lobWarnRadiusTenths);
        reloadSound.writeToNBT(nbttagcompound, RELOAD_SOUND_PREFIX);
        lobWarnParticles.writeToNBT(nbttagcompound, LOB_WARN_PARTICLES_PREFIX);
        return nbttagcompound;
    }

    public void readFromNBT(CompoundTag nbttagcompound) {
        setProjectileEntity(nbttagcompound.getString("GeckoProjectileEntity"));
        // Asked for as a string and nothing else: a save that never wrote the key and one
        // that holds something other than text under it both read the default, while an
        // empty string is a setting in its own right and stays empty.
        setFallbackProjectile(nbttagcompound.contains(FALLBACK_KEY, Tag.TAG_STRING)
                ? nbttagcompound.getString(FALLBACK_KEY) : DEFAULT_FALLBACK_PROJECTILE);
        setKeepDistance(nbttagcompound.getInt("GeckoKeepDistance"));
        muzzleHeightTenths = readInt(nbttagcompound, "GeckoNpcRangedMuzzle", DEFAULT_MUZZLE_HEIGHT,
                MIN_MUZZLE_HEIGHT, MAX_MUZZLE_HEIGHT);
        shotSoundVolumeTenths = readInt(nbttagcompound, "GeckoNpcRangedShotVolume",
                DEFAULT_SHOT_VOLUME, 0, MAX_SHOT_VOLUME);
        shotSoundPitchTenths = readInt(nbttagcompound, "GeckoNpcRangedShotPitch",
                DEFAULT_SHOT_PITCH, MIN_SHOT_PITCH, MAX_SHOT_PITCH);
        // A save without these keys is an npc from before the addon's ranged AI existed: the
        // switch reads false, and every number below reads the figure that changes nothing.
        rangedAddonEnabled = nbttagcompound.getBoolean(ADDON_ENABLED_KEY);
        engageMinTenths = readInt(nbttagcompound, ENGAGE_MIN_KEY, 0, 0, MAX_ENGAGE_TENTHS);
        engageMaxTenths = readInt(nbttagcompound, ENGAGE_MAX_KEY, 0, 0, MAX_ENGAGE_TENTHS);
        tooFarMode = readInt(nbttagcompound, TOO_FAR_KEY, TOO_FAR_APPROACH, TOO_FAR_APPROACH, TOO_FAR_WAIT);
        tooCloseMode = readInt(nbttagcompound, TOO_CLOSE_KEY, TOO_CLOSE_RETREAT,
                TOO_CLOSE_RETREAT, TOO_CLOSE_FIRE);
        leadPercent = readInt(nbttagcompound, LEAD_KEY, 0, 0, MAX_LEAD_PERCENT);
        burstShots = readInt(nbttagcompound, BURST_SHOTS_KEY, 0, 0, MAX_BURST_SHOTS);
        burstDelayTicks = readInt(nbttagcompound, BURST_DELAY_KEY, DEFAULT_BURST_DELAY_TICKS,
                MIN_BURST_DELAY_TICKS, MAX_BURST_DELAY_TICKS);
        spreadDegrees = readInt(nbttagcompound, SPREAD_KEY, 0, 0, MAX_SPREAD_DEGREES);
        reloadTicks = readInt(nbttagcompound, RELOAD_KEY, 0, 0, MAX_RELOAD_TICKS);
        setReloadAnimation(nbttagcompound.getString(RELOAD_ANIMATION_KEY));
        losMode = readInt(nbttagcompound, LOS_KEY, LOS_CUSTOMNPCS, LOS_CUSTOMNPCS, LOS_LOB);
        lobWarnTicks = readInt(nbttagcompound, LOB_WARN_KEY, DEFAULT_LOB_WARN_TICKS, 0, MAX_LOB_WARN_TICKS);
        lobWarnRadiusTenths = readInt(nbttagcompound, LOB_WARN_RADIUS_KEY, DEFAULT_LOB_WARN_RADIUS_TENTHS,
                MIN_LOB_WARN_RADIUS_TENTHS, MAX_LOB_WARN_RADIUS_TENTHS);
        reloadSound.readFromNBT(nbttagcompound, RELOAD_SOUND_PREFIX);
        lobWarnParticles.readFromNBT(nbttagcompound, LOB_WARN_PARTICLES_PREFIX);
    }

    /** The default for a key an older world never wrote, and the clamp for one it did. */
    private static int readInt(CompoundTag tag, String key, int fallback, int min, int max) {
        return tag.contains(key) ? Mth.clamp(tag.getInt(key), min, max) : fallback;
    }

    public String getProjectileEntity() {
        return projectileEntity;
    }

    public void setProjectileEntity(String projectileEntity) {
        this.projectileEntity = projectileEntity == null ? "" : projectileEntity.trim();
    }

    /** The entity fired when there is nothing else to fire; empty means the npc holds its fire. */
    public String getFallbackProjectile() {
        return fallbackProjectile;
    }

    public void setFallbackProjectile(String fallbackProjectile) {
        this.fallbackProjectile = fallbackProjectile == null ? "" : fallbackProjectile.trim();
    }

    public int getKeepDistance() {
        return keepDistance;
    }

    public void setKeepDistance(int keepDistance) {
        this.keepDistance = Mth.clamp(keepDistance, 0, MAX_KEEP_DISTANCE);
    }

    /** Where the projectile leaves from, in tenths of a block above or below the npc's eyes. */
    public int getMuzzleHeightTenths() {
        return muzzleHeightTenths;
    }

    public void setMuzzleHeightTenths(int muzzleHeightTenths) {
        this.muzzleHeightTenths = Mth.clamp(muzzleHeightTenths, MIN_MUZZLE_HEIGHT, MAX_MUZZLE_HEIGHT);
    }

    /** How loud the shot is, in tenths: 20 is the 2.0F the call used to hold. */
    public int getShotSoundVolumeTenths() {
        return shotSoundVolumeTenths;
    }

    public void setShotSoundVolumeTenths(int shotSoundVolumeTenths) {
        this.shotSoundVolumeTenths = Mth.clamp(shotSoundVolumeTenths, 0, MAX_SHOT_VOLUME);
    }

    /** And how high, in tenths. The sound itself is the one CustomNPCs' own editor names. */
    public int getShotSoundPitchTenths() {
        return shotSoundPitchTenths;
    }

    public void setShotSoundPitchTenths(int shotSoundPitchTenths) {
        this.shotSoundPitchTenths = Mth.clamp(shotSoundPitchTenths, MIN_SHOT_PITCH, MAX_SHOT_PITCH);
    }

    /**
     * Whether the addon runs this npc's ranged fight rather than CustomNPCs.
     *
     * <p>The switch for everything below it. Off - which is what every npc saved before it
     * existed reads - and CustomNPCs' own ranged goal is left exactly as it was: the addon
     * still decides what is fired and still aims it, because those were never the goal's.</p>
     */
    public boolean isRangedAddonEnabled() {
        return rangedAddonEnabled;
    }

    public void setRangedAddonEnabled(boolean rangedAddonEnabled) {
        this.rangedAddonEnabled = rangedAddonEnabled;
    }

    /** The near edge of the window the npc fires from, in tenths of a block. */
    public int getEngageMinTenths() {
        return engageMinTenths;
    }

    public void setEngageMinTenths(int engageMinTenths) {
        this.engageMinTenths = Mth.clamp(engageMinTenths, 0, MAX_ENGAGE_TENTHS);
    }

    /** And the far edge; nought means whatever the CustomNPCs range is set to. */
    public int getEngageMaxTenths() {
        return engageMaxTenths;
    }

    public void setEngageMaxTenths(int engageMaxTenths) {
        this.engageMaxTenths = Mth.clamp(engageMaxTenths, 0, MAX_ENGAGE_TENTHS);
    }

    /** {@link #TOO_FAR_APPROACH} or {@link #TOO_FAR_WAIT}. */
    public int getTooFarMode() {
        return tooFarMode;
    }

    public void setTooFarMode(int tooFarMode) {
        this.tooFarMode = Mth.clamp(tooFarMode, TOO_FAR_APPROACH, TOO_FAR_WAIT);
    }

    /** {@link #TOO_CLOSE_RETREAT}, {@link #TOO_CLOSE_MELEE} or {@link #TOO_CLOSE_FIRE}. */
    public int getTooCloseMode() {
        return tooCloseMode;
    }

    public void setTooCloseMode(int tooCloseMode) {
        this.tooCloseMode = Mth.clamp(tooCloseMode, TOO_CLOSE_RETREAT, TOO_CLOSE_FIRE);
    }

    /** How far ahead of a running target the shot goes, as a share of its travel; 0 = at it. */
    public int getLeadPercent() {
        return leadPercent;
    }

    public void setLeadPercent(int leadPercent) {
        this.leadPercent = Mth.clamp(leadPercent, 0, MAX_LEAD_PERCENT);
    }

    /** Shots in one burst of the addon's own; 0 leaves the volley to CustomNPCs. */
    public int getBurstShots() {
        return burstShots;
    }

    public void setBurstShots(int burstShots) {
        this.burstShots = Mth.clamp(burstShots, 0, MAX_BURST_SHOTS);
    }

    public int getBurstDelayTicks() {
        return burstDelayTicks;
    }

    public void setBurstDelayTicks(int burstDelayTicks) {
        this.burstDelayTicks = Mth.clamp(burstDelayTicks, MIN_BURST_DELAY_TICKS, MAX_BURST_DELAY_TICKS);
    }

    /** How wide each shot may stray from the aim, in degrees; 0 is dead on it. */
    public int getSpreadDegrees() {
        return spreadDegrees;
    }

    public void setSpreadDegrees(int spreadDegrees) {
        this.spreadDegrees = Mth.clamp(spreadDegrees, 0, MAX_SPREAD_DEGREES);
    }

    /** The pause after a burst, on top of the delay between shots; 0 is no pause at all. */
    public int getReloadTicks() {
        return reloadTicks;
    }

    public void setReloadTicks(int reloadTicks) {
        this.reloadTicks = Mth.clamp(reloadTicks, 0, MAX_RELOAD_TICKS);
    }

    /** What the npc's model plays while it reloads; empty plays nothing. */
    public String getReloadAnimation() {
        return reloadAnimation;
    }

    public void setReloadAnimation(String reloadAnimation) {
        this.reloadAnimation = reloadAnimation == null ? "" : reloadAnimation.trim();
    }

    /** {@link #LOS_CUSTOMNPCS}, {@link #LOS_WAIT} or {@link #LOS_LOB}. */
    public int getLosMode() {
        return losMode;
    }

    public void setLosMode(int losMode) {
        this.losMode = Mth.clamp(losMode, LOS_CUSTOMNPCS, LOS_LOB);
    }

    /** How long the ring stands where a lobbed shot will land before it is fired; 0 = no ring. */
    public int getLobWarnTicks() {
        return lobWarnTicks;
    }

    public void setLobWarnTicks(int lobWarnTicks) {
        this.lobWarnTicks = Mth.clamp(lobWarnTicks, 0, MAX_LOB_WARN_TICKS);
    }

    public int getLobWarnRadiusTenths() {
        return lobWarnRadiusTenths;
    }

    public void setLobWarnRadiusTenths(int lobWarnRadiusTenths) {
        this.lobWarnRadiusTenths = Mth.clamp(lobWarnRadiusTenths,
                MIN_LOB_WARN_RADIUS_TENTHS, MAX_LOB_WARN_RADIUS_TENTHS);
    }

    /** The noise the pause after a burst makes. */
    public BossSoundCue getReloadSound() {
        return reloadSound;
    }

    /** And the puff that marks where a lobbed shot is coming down. */
    public BossParticleCue getLobWarnParticles() {
        return lobWarnParticles;
    }
}
