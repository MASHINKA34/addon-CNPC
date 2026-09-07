package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The arena itself turning dangerous for a phase.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#hazard()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossHazardSettings {

    /**
     * The arena hazard: not a cast but the ground itself, armed when the phase is entered
     * and gone when the phase is. Ten seconds of grace by default, so a party does not walk
     * into a phase change already standing in the fire.
     */
    private boolean hazardEnabled;
    private int hazardMode = BossPhaseData.HAZARD_MODE_RING;
    /** How long after the phase begins the arena turns dangerous. */
    private int hazardDelayTicks = 200;
    /** How long before that the edge flashes and the countdown runs. */
    private int hazardWarnTicks = 60;
    private int hazardCenterMode = BossPhaseData.HAZARD_CENTER_BOSS;
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
    /** Dosed with every hit of the hazard, on everyone standing in the fire. */
    private final BossEffectSet hazardEffects = new BossEffectSet();

    /** Whether the arena turns dangerous in this phase at all. */
    public boolean isEnabled() { return hazardEnabled; }

    public void setEnabled(boolean value) { hazardEnabled = value; }

    /** Which shape the danger takes: a ring closing in, or a box switching on. */
    public int getMode() { return hazardMode; }

    public void setMode(int value) {
        hazardMode = Mth.clamp(value, BossPhaseData.HAZARD_MODE_RING, BossPhaseData.HAZARD_MODE_BOX);
    }

    /** Ticks after the phase begins before the arena starts to hurt. */
    public int getDelayTicks() { return hazardDelayTicks; }

    public void setDelayTicks(int value) { hazardDelayTicks = Mth.clamp(value, 0, 12000); }

    /** Ticks of flashing edge and countdown in front of that. */
    public int getWarnTicks() { return hazardWarnTicks; }

    public void setWarnTicks(int value) { hazardWarnTicks = Mth.clamp(value, 0, 600); }

    /** Ring: whether the circle closes in on the boss' spot at phase entry or on a fixed point. */
    public int getCenterMode() { return hazardCenterMode; }

    public void setCenterMode(int value) {
        hazardCenterMode = Mth.clamp(value, BossPhaseData.HAZARD_CENTER_BOSS, BossPhaseData.HAZARD_CENTER_POINT);
    }

    public int getCenterX() { return hazardCenterX; }

    public int getCenterZ() { return hazardCenterZ; }

    public void setCenter(int x, int z) {
        hazardCenterX = hazardCoordinate(x);
        hazardCenterZ = hazardCoordinate(z);
    }

    public int getStartRadius() { return hazardStartRadius; }

    public int getEndRadius() { return hazardEndRadius; }

    /**
     * Where the safe circle starts and where it stops, set as the pair they are read as.
     *
     * <p>The end is held under the start, the way the boulder rain's inner edge is held under
     * its outer one: a circle that closes to where it began, or grows, is not a ring closing
     * in, and the shrink would have nothing to do.</p>
     */
    public void setRadii(int start, int end) {
        hazardStartRadius = Mth.clamp(start, 2, 128);
        hazardEndRadius = Mth.clamp(end, 1, hazardStartRadius - 1);
    }

    /** Ring: how long the circle takes to close from the start radius to the end one. */
    public int getShrinkTicks() { return hazardShrinkTicks; }

    public void setShrinkTicks(int value) { hazardShrinkTicks = Mth.clamp(value, 20, 24000); }

    public int getX1() { return hazardX1; }

    public int getY1() { return hazardY1; }

    public int getZ1() { return hazardZ1; }

    public int getX2() { return hazardX2; }

    public int getY2() { return hazardY2; }

    public int getZ2() { return hazardZ2; }

    public void setCorner1(int x, int y, int z) {
        hazardX1 = hazardCoordinate(x);
        hazardY1 = hazardCoordinate(y);
        hazardZ1 = hazardCoordinate(z);
    }

    public void setCorner2(int x, int y, int z) {
        hazardX2 = hazardCoordinate(x);
        hazardY2 = hazardCoordinate(y);
        hazardZ2 = hazardCoordinate(z);
    }

    /** What one dose of the hazard hits for; zero leaves only the effects. */
    public int getDamage() { return hazardDamage; }

    public void setDamage(int value) { hazardDamage = Mth.clamp(value, 0, 1000); }

    /** Ticks between one dose and the next. */
    public int getIntervalTicks() { return hazardIntervalTicks; }

    public void setIntervalTicks(int value) { hazardIntervalTicks = Mth.clamp(value, 1, 200); }

    private static int hazardCoordinate(int value) {
        return Mth.clamp(value, -BossPhaseData.MAX_HAZARD_COORDINATE, BossPhaseData.MAX_HAZARD_COORDINATE);
    }

    public BossEffectSet getEffects() { return hazardEffects; }

    void writeToNBT(CompoundTag tag) {
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
        tag.put("HazardEffects", hazardEffects.writeToNBT());
    }

    void readFromNBT(CompoundTag tag) {
        hazardEnabled = tag.getBoolean("HazardEnabled");
        hazardMode = value(tag, "HazardMode", BossPhaseData.HAZARD_MODE_RING, BossPhaseData.HAZARD_MODE_RING, BossPhaseData.HAZARD_MODE_BOX);
        hazardDelayTicks = value(tag, "HazardDelayTicks", 200, 0, 12000);
        hazardWarnTicks = value(tag, "HazardWarnTicks", 60, 0, 600);
        hazardCenterMode = value(tag, "HazardCenterMode", BossPhaseData.HAZARD_CENTER_BOSS,
                BossPhaseData.HAZARD_CENTER_BOSS, BossPhaseData.HAZARD_CENTER_POINT);
        setCenter(
                value(tag, "HazardCenterX", 0, -BossPhaseData.MAX_HAZARD_COORDINATE, BossPhaseData.MAX_HAZARD_COORDINATE),
                value(tag, "HazardCenterZ", 0, -BossPhaseData.MAX_HAZARD_COORDINATE, BossPhaseData.MAX_HAZARD_COORDINATE));
        setRadii(
                value(tag, "HazardStartRadius", 30, 2, 128),
                value(tag, "HazardEndRadius", 6, 1, 127));
        hazardShrinkTicks = value(tag, "HazardShrinkTicks", 1200, 20, 24000);
        setCorner1(
                value(tag, "HazardX1", 0, -BossPhaseData.MAX_HAZARD_COORDINATE, BossPhaseData.MAX_HAZARD_COORDINATE),
                value(tag, "HazardY1", 0, -BossPhaseData.MAX_HAZARD_COORDINATE, BossPhaseData.MAX_HAZARD_COORDINATE),
                value(tag, "HazardZ1", 0, -BossPhaseData.MAX_HAZARD_COORDINATE, BossPhaseData.MAX_HAZARD_COORDINATE));
        setCorner2(
                value(tag, "HazardX2", 0, -BossPhaseData.MAX_HAZARD_COORDINATE, BossPhaseData.MAX_HAZARD_COORDINATE),
                value(tag, "HazardY2", 0, -BossPhaseData.MAX_HAZARD_COORDINATE, BossPhaseData.MAX_HAZARD_COORDINATE),
                value(tag, "HazardZ2", 0, -BossPhaseData.MAX_HAZARD_COORDINATE, BossPhaseData.MAX_HAZARD_COORDINATE));
        hazardDamage = value(tag, "HazardDamage", 4, 0, 1000);
        hazardIntervalTicks = value(tag, "HazardIntervalTicks", 20, 1, 200);
        hazardEffects.readFromNBT(tag, "HazardEffects");
    }
}
