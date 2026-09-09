package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The lines swept round the boss after the cast.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#beam()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossBeamSettings {

    /**
     * The sweeping beam: lines that keep turning round the boss after the cast. Only the
     * wind-up is a cast; the sweep itself runs on the level tick, which is why it has a
     * length of time and a turning speed rather than a range.
     */
    private boolean beamEnabled;
    private String beamAnimation = "";
    private int beamActionDelayTicks = 20;
    private int beamCooldownTicks = 360;
    /** How many beams leave the boss, spaced evenly round it. */
    private int beamCount = 1;
    private int beamLength = 20;
    /** The full width of a beam, so it reaches half of this to either side of its line. */
    private int beamWidth = 1;
    private int beamDurationTicks = 120;
    /** Degrees a second. The sign is the direction: positive turns anticlockwise seen from above. */
    private int beamDegreesPerSecond = 30;
    private int beamStartMode = BossPhaseData.BEAM_START_FACING;
    /** On, the beams turn round wherever the boss is; off, round the spot it cast them from. */
    private boolean beamFollowsBoss = true;
    /** On, a beam ends at the first solid block on its line; off, it burns through walls. */
    private boolean beamStopsAtWalls = true;
    private int beamDamage = 6;
    /** Ticks between one hit on the same victim and the next, however long they stay in it. */
    private int beamHitIntervalTicks = 10;
    /** Sideways, off the beam's line, rather than away from the boss. */
    private int beamKnockback = 1;
    /** What the beams are drawn out of. Default keeps the ability's dust the beam started life with. */
    private String beamLook = BeamLooks.KIND;
    /** Landed with every hit of a beam, on whoever it caught up with. */
    private final BossEffectSet beamEffects = new BossEffectSet();

    /** Whether the boss ever sweeps beams round itself in this phase. */
    public boolean isEnabled() { return beamEnabled; }

    public void setEnabled(boolean value) { beamEnabled = value; }

    public String getAnimation() { return beamAnimation; }

    public void setAnimation(String value) { beamAnimation = clean(value); }

    /** The wind-up: the charge before the beams come on. */
    public int getActionDelayTicks() { return beamActionDelayTicks; }

    public void setActionDelayTicks(int value) { beamActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return beamCooldownTicks; }

    public void setCooldownTicks(int value) { beamCooldownTicks = Mth.clamp(value, 1, 12000); }

    /** How many beams leave the boss at once, spaced evenly round it. */
    public int getCount() { return beamCount; }

    public void setCount(int value) { beamCount = Mth.clamp(value, 1, 4); }

    /** How far from its centre a beam reaches, before any wall cuts it short. */
    public int getLength() { return beamLength; }

    public void setLength(int value) { beamLength = Mth.clamp(value, 3, 64); }

    /** The full width of a beam, so it reaches half of this to either side of its line. */
    public int getWidth() { return beamWidth; }

    public void setWidth(int value) { beamWidth = Mth.clamp(value, 1, 6); }

    /** How long the beams keep turning after the cast. */
    public int getDurationTicks() { return beamDurationTicks; }

    public void setDurationTicks(int value) { beamDurationTicks = Mth.clamp(value, 10, 1200); }

    /** Degrees a second; positive turns anticlockwise seen from above, negative the other way. */
    public int getDegreesPerSecond() { return beamDegreesPerSecond; }

    public void setDegreesPerSecond(int value) { beamDegreesPerSecond = Mth.clamp(value, -360, 360); }

    /** Whether the first beam leaves along the boss' gaze or at a random angle. */
    public int getStartMode() { return beamStartMode; }

    public void setStartMode(int value) {
        beamStartMode = Mth.clamp(value, BossPhaseData.BEAM_START_FACING, BossPhaseData.BEAM_START_RANDOM);
    }

    /** Whether the beams turn round the boss wherever it walks, or round the spot it cast from. */
    public boolean isFollowsBoss() { return beamFollowsBoss; }

    public void setFollowsBoss(boolean value) { beamFollowsBoss = value; }

    /** Whether a beam ends at the first solid block on its line, so a pillar is cover. */
    public boolean isStopsAtWalls() { return beamStopsAtWalls; }

    public void setStopsAtWalls(boolean value) { beamStopsAtWalls = value; }

    /** What one hit of a beam does; zero leaves only the effects and the shove. */
    public int getDamage() { return beamDamage; }

    public void setDamage(int value) { beamDamage = Mth.clamp(value, 0, 1000); }

    /** The least a victim gets between one hit of a beam and the next. */
    public int getHitIntervalTicks() { return beamHitIntervalTicks; }

    public void setHitIntervalTicks(int value) { beamHitIntervalTicks = Mth.clamp(value, 1, 100); }

    public int getKnockback() { return beamKnockback; }

    public void setKnockback(int value) { beamKnockback = Mth.clamp(value, 0, 10); }

    /** What the beams are drawn out of: only the particles, never the reach or the burn. */
    public String getLook() { return beamLook; }

    public void setLook(String value) { beamLook = BeamLooks.normalize(value); }

    public BossEffectSet getEffects() { return beamEffects; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("BeamEnabled", beamEnabled);
        tag.putString("BeamAnimation", beamAnimation);
        tag.putInt("BeamActionDelayTicks", beamActionDelayTicks);
        tag.putInt("BeamCooldownTicks", beamCooldownTicks);
        tag.putInt("BeamCount", beamCount);
        tag.putInt("BeamLength", beamLength);
        tag.putInt("BeamWidth", beamWidth);
        tag.putInt("BeamDurationTicks", beamDurationTicks);
        tag.putInt("BeamDegreesPerSecond", beamDegreesPerSecond);
        tag.putInt("BeamStartMode", beamStartMode);
        tag.putBoolean("BeamFollowsBoss", beamFollowsBoss);
        tag.putBoolean("BeamStopsAtWalls", beamStopsAtWalls);
        tag.putInt("BeamDamage", beamDamage);
        tag.putInt("BeamHitIntervalTicks", beamHitIntervalTicks);
        tag.putInt("BeamKnockback", beamKnockback);
        tag.putString("BeamLook", beamLook);
        tag.put("BeamEffects", beamEffects.writeToNBT());
    }

    void readFromNBT(CompoundTag tag) {
        // A boss saved before the beam existed carries no key and reads as off.
        beamEnabled = tag.getBoolean("BeamEnabled");
        beamAnimation = clean(tag.getString("BeamAnimation"));
        beamActionDelayTicks = value(tag, "BeamActionDelayTicks", 20, 0, 1200);
        beamCooldownTicks = value(tag, "BeamCooldownTicks", 360, 1, 12000);
        beamCount = value(tag, "BeamCount", 1, 1, 4);
        beamLength = value(tag, "BeamLength", 20, 3, 64);
        beamWidth = value(tag, "BeamWidth", 1, 1, 6);
        beamDurationTicks = value(tag, "BeamDurationTicks", 120, 10, 1200);
        beamDegreesPerSecond = value(tag, "BeamDegreesPerSecond", 30, -360, 360);
        beamStartMode = value(tag, "BeamStartMode", BossPhaseData.BEAM_START_FACING, BossPhaseData.BEAM_START_FACING, BossPhaseData.BEAM_START_RANDOM);
        // The two that default to on read an absent key as on, the way the hunt's glow does.
        beamFollowsBoss = !tag.contains("BeamFollowsBoss") || tag.getBoolean("BeamFollowsBoss");
        beamStopsAtWalls = !tag.contains("BeamStopsAtWalls") || tag.getBoolean("BeamStopsAtWalls");
        beamDamage = value(tag, "BeamDamage", 6, 0, 1000);
        beamHitIntervalTicks = value(tag, "BeamHitIntervalTicks", 10, 1, 100);
        beamKnockback = value(tag, "BeamKnockback", 1, 0, 10);
        // A boss saved before the looks existed carries no key, and reads as the dust it always had.
        beamLook = BeamLooks.normalize(tag.getString("BeamLook"));
        beamEffects.readFromNBT(tag, "BeamEffects");
    }
}
