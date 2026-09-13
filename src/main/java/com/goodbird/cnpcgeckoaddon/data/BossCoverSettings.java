package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The hit on the whole arena that spares only whoever got out of sight.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#cover()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossCoverSettings {

    /** The sight line, the wave and the shelters, each in the unit its label names. */
    public static final int MAX_KNEE_HEIGHT = 100;
    public static final int MIN_WAVE_SPEED = 1;
    public static final int MAX_WAVE_SPEED = 50;
    public static final int MIN_VFX_TICKS = 1;
    public static final int MAX_VFX_TICKS = 600;
    public static final int MAX_POST_HEIGHT = 16;
    public static final int MIN_SHELTER_SPACING = 100;
    public static final int MAX_SHELTER_SPACING = 500;

    private boolean coverEnabled;
    private String coverAnimation = "";
    /**
     * The wind-up is the whole mechanic: it is the time everyone gets to hide, and the one
     * warning there is. Held at a second at the least, because a strike nobody could have
     * got out of is not a mechanic, it is a trap.
     */
    private int coverActionDelayTicks = 80;
    private int coverCooldownTicks = 500;
    private int coverMode = BossPhaseData.COVER_MODE_SIGHT;
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
    /** Hundredths of a block the second sight line is drawn to, so 25 is knee height. */
    private int coverKneeHeight = 25;
    /** Tenths of a block a tick the shockwave travels, which sets how long it is drawn. */
    private int coverWaveSpeed = 10;
    private int coverVfxMinTicks = 20;
    private int coverVfxMaxTicks = 60;
    /** Blocks of dust stacked over a shelter's middle so it can be seen across the arena. */
    private int coverPostHeight = 3;
    /** How far apart two shelters are held, as a percentage of one shelter's radius. */
    private int coverShelterSpacing = 200;
    private final BossSoundCue coverBlastSound =
            new BossSoundCue("minecraft:entity.generic.explode", 4.0F, 0.6F);
    private final BossParticleCue coverBlastParticles = new BossParticleCue(BossParticleCue.DUST_ID, 40);
    /** Landed on everyone the strike caught out in the open. */
    private final BossEffectSet coverEffects = new BossEffectSet();
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot coverCastSpot = new BossCastSpot();

    public boolean isEnabled() { return coverEnabled; }

    public void setEnabled(boolean value) { coverEnabled = value; }

    public String getAnimation() { return coverAnimation; }

    public void setAnimation(String value) { coverAnimation = clean(value); }

    /** The time everyone gets to hide, which is also the only warning: never under a second. */
    public int getActionDelayTicks() { return coverActionDelayTicks; }

    public void setActionDelayTicks(int value) { coverActionDelayTicks = Mth.clamp(value, 20, 1200); }

    public int getCooldownTicks() { return coverCooldownTicks; }

    public void setCooldownTicks(int value) { coverCooldownTicks = Mth.clamp(value, 1, 12000); }

    /** Which of the two ways out spares somebody: out of sight, or inside a shelter. */
    public int getMode() { return coverMode; }

    public void setMode(int value) {
        coverMode = Mth.clamp(value, BossPhaseData.COVER_MODE_SIGHT, BossPhaseData.COVER_MODE_SHELTER);
    }

    public int getRange() { return coverRange; }

    public void setRange(int value) { coverRange = Mth.clamp(value, 4, 96); }

    public int getDamage() { return coverDamage; }

    public void setDamage(int value) { coverDamage = Mth.clamp(value, 0, 1000); }

    public int getKnockback() { return coverKnockback; }

    public void setKnockback(int value) { coverKnockback = Mth.clamp(value, 0, 10); }

    public int getShelterCount() { return coverShelterCount; }

    public void setShelterCount(int value) { coverShelterCount = Mth.clamp(value, 1, 6); }

    public int getShelterRadius() { return coverShelterRadius; }

    public void setShelterRadius(int value) { coverShelterRadius = Mth.clamp(value, 1, 16); }

    public int getShelterMinRange() { return coverShelterMinRange; }

    public int getShelterMaxRange() { return coverShelterMaxRange; }

    /**
     * The ring the shelters are scattered in, set as the pair it is read as.
     *
     * <p>The inner edge is held under the outer one, the way the boulder rain's is: a ring
     * with no width would leave the wind-up with nowhere to put a shelter down.</p>
     */
    public void setShelterRing(int min, int max) {
        coverShelterMaxRange = Mth.clamp(max, 2, 64);
        coverShelterMinRange = Mth.clamp(min, 1, Math.min(48, coverShelterMaxRange - 1));
    }

    public String getVfx() { return coverVfx; }

    public void setVfx(String value) { coverVfx = AreaVfxStyles.normalize(value); }

    /**
     * Hundredths of a block above the victim's feet the second sight line is drawn to.
     *
     * <p>Two lines rather than one is what makes ducking behind a slab being seen: raise this
     * and a low wall stops covering anybody, drop it to nothing and a carpet does.</p>
     */
    public int getKneeHeightHundredths() { return coverKneeHeight; }

    public void setKneeHeightHundredths(int value) {
        coverKneeHeight = Mth.clamp(value, 0, MAX_KNEE_HEIGHT);
    }

    public double getKneeHeight() { return coverKneeHeight / 100.0D; }

    /** Tenths of a block a tick the shockwave travels. */
    public int getWaveSpeedTenths() { return coverWaveSpeed; }

    public void setWaveSpeedTenths(int value) {
        coverWaveSpeed = Mth.clamp(value, MIN_WAVE_SPEED, MAX_WAVE_SPEED);
    }

    public double getWaveSpeed() { return coverWaveSpeed / 10.0D; }

    /** The floor and the ceiling on how long the wave is drawn for, whatever the range says. */
    public int getVfxMinTicks() { return coverVfxMinTicks; }

    public void setVfxMinTicks(int value) {
        coverVfxMinTicks = Mth.clamp(value, MIN_VFX_TICKS, MAX_VFX_TICKS);
    }

    public int getVfxMaxTicks() { return coverVfxMaxTicks; }

    public void setVfxMaxTicks(int value) {
        coverVfxMaxTicks = Mth.clamp(value, MIN_VFX_TICKS, MAX_VFX_TICKS);
    }

    /** Blocks of dust over a shelter's middle; zero leaves the ring on the floor alone. */
    public int getPostHeight() { return coverPostHeight; }

    public void setPostHeight(int value) { coverPostHeight = Mth.clamp(value, 0, MAX_POST_HEIGHT); }

    /**
     * How far apart two shelters are held, as a percentage of one shelter's radius.
     *
     * <p>Two hundred is two radii, which is the two circles standing edge to edge; at a
     * hundred they are allowed to overlap by half, and a cramped ring fits more of them.</p>
     */
    public int getShelterSpacingPercent() { return coverShelterSpacing; }

    public void setShelterSpacingPercent(int value) {
        coverShelterSpacing = Mth.clamp(value, MIN_SHELTER_SPACING, MAX_SHELTER_SPACING);
    }

    /** Blocks two shelters are held apart at this radius. */
    public double shelterSpacing() { return getShelterRadius() * coverShelterSpacing / 100.0D; }

    public BossSoundCue getBlastSound() { return coverBlastSound; }

    public BossParticleCue getBlastParticles() { return coverBlastParticles; }

    public BossEffectSet getEffects() { return coverEffects; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return coverCastSpot; }

    void writeToNBT(CompoundTag tag) {
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
        tag.put("CoverEffects", coverEffects.writeToNBT());
        tag.putInt("CoverKneeHeight", coverKneeHeight);
        tag.putInt("CoverWaveSpeed", coverWaveSpeed);
        tag.putInt("CoverVfxMin", coverVfxMinTicks);
        tag.putInt("CoverVfxMax", coverVfxMaxTicks);
        tag.putInt("CoverPostHeight", coverPostHeight);
        tag.putInt("CoverShelterSpacing", coverShelterSpacing);
        coverBlastSound.writeToNBT(tag, "CoverBlastSound");
        coverBlastParticles.writeToNBT(tag, "CoverBlastParticles");
        coverCastSpot.writeToNBT(tag, "Cover");
    }

    void readFromNBT(CompoundTag tag) {
        coverEnabled = tag.getBoolean("CoverEnabled");
        coverAnimation = clean(tag.getString("CoverAnimation"));
        coverActionDelayTicks = value(tag, "CoverActionDelayTicks", 80, 20, 1200);
        coverCooldownTicks = value(tag, "CoverCooldownTicks", 500, 1, 12000);
        coverMode = value(tag, "CoverMode", BossPhaseData.COVER_MODE_SIGHT, BossPhaseData.COVER_MODE_SIGHT, BossPhaseData.COVER_MODE_SHELTER);
        coverRange = value(tag, "CoverRange", 40, 4, 96);
        coverDamage = value(tag, "CoverDamage", 40, 0, 1000);
        coverKnockback = value(tag, "CoverKnockback", 2, 0, 10);
        coverShelterCount = value(tag, "CoverShelterCount", 2, 1, 6);
        coverShelterRadius = value(tag, "CoverShelterRadius", 3, 1, 16);
        setShelterRing(
                value(tag, "CoverShelterMinRange", 4, 1, 48),
                value(tag, "CoverShelterMaxRange", 14, 2, 64));
        coverVfx = AreaVfxStyles.normalize(tag.getString("CoverVfx"));
        coverEffects.readFromNBT(tag, "CoverEffects");
        coverKneeHeight = value(tag, "CoverKneeHeight", 25, 0, MAX_KNEE_HEIGHT);
        coverWaveSpeed = value(tag, "CoverWaveSpeed", 10, MIN_WAVE_SPEED, MAX_WAVE_SPEED);
        coverVfxMinTicks = value(tag, "CoverVfxMin", 20, MIN_VFX_TICKS, MAX_VFX_TICKS);
        coverVfxMaxTicks = value(tag, "CoverVfxMax", 60, MIN_VFX_TICKS, MAX_VFX_TICKS);
        coverPostHeight = value(tag, "CoverPostHeight", 3, 0, MAX_POST_HEIGHT);
        coverShelterSpacing = value(tag, "CoverShelterSpacing", 200,
                MIN_SHELTER_SPACING, MAX_SHELTER_SPACING);
        coverBlastSound.readFromNBT(tag, "CoverBlastSound");
        coverBlastParticles.readFromNBT(tag, "CoverBlastParticles");
        coverCastSpot.readFromNBT(tag, "Cover");
    }
}
