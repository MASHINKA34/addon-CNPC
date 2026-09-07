package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The clones the boss calls for, and where it puts them.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#summon()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossSummonSettings {

    private boolean summonEnabled;
    private String summonAnimation = "";
    private int summonActionDelayTicks = 20;
    private int summonCooldownTicks = 400;
    private String minionCloneName = "";
    private int minionCloneTab = 1;
    private int minionCount = 3;
    private int minionRadius = 4;
    private int maxAliveMinions = 6;
    private int minionSpawnMode = BossPhaseData.MINION_SPAWN_RANDOM_RADIUS;
    private int minionSpawnOrder = BossPhaseData.MINION_ORDER_LIST;
    private int minionPointSearchRadius;
    private boolean minionReuseOccupiedPoints;
    private final BossMinionSpawnList minionSpawnPoints = new BossMinionSpawnList();

    public boolean isEnabled() { return summonEnabled; }

    public void setEnabled(boolean value) { summonEnabled = value; }

    public String getAnimation() { return summonAnimation; }

    public void setAnimation(String value) { summonAnimation = clean(value); }

    public int getActionDelayTicks() { return summonActionDelayTicks; }

    public void setActionDelayTicks(int value) { summonActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return summonCooldownTicks; }

    public void setCooldownTicks(int value) { summonCooldownTicks = Mth.clamp(value, 20, 12000); }

    public String getCloneName() { return minionCloneName; }

    public void setCloneName(String value) { minionCloneName = clean(value); }

    public int getCloneTab() { return minionCloneTab; }

    public void setCloneTab(int value) { minionCloneTab = Mth.clamp(value, 1, 9); }

    public int getCount() { return minionCount; }

    public void setCount(int value) { minionCount = Mth.clamp(value, 1, 32); }

    public int getRadius() { return minionRadius; }

    public void setRadius(int value) { minionRadius = Mth.clamp(value, 1, 32); }

    public int getMaxAlives() { return maxAliveMinions; }

    public void setMaxAlives(int value) { maxAliveMinions = Mth.clamp(value, 1, 128); }

    public int getSpawnMode() { return minionSpawnMode; }

    public void setSpawnMode(int value) {
        minionSpawnMode = Mth.clamp(value, BossPhaseData.MINION_SPAWN_RANDOM_RADIUS, BossPhaseData.MINION_SPAWN_POINTS_THEN_RANDOM);
    }

    public int getSpawnOrder() { return minionSpawnOrder; }

    public void setSpawnOrder(int value) {
        minionSpawnOrder = Mth.clamp(value, BossPhaseData.MINION_ORDER_LIST, BossPhaseData.MINION_ORDER_RANDOM);
    }

    public int getPointSearchRadius() { return minionPointSearchRadius; }

    public void setPointSearchRadius(int value) { minionPointSearchRadius = Mth.clamp(value, 0, 4); }

    public boolean isReuseOccupiedPoints() { return minionReuseOccupiedPoints; }

    public void setReuseOccupiedPoints(boolean value) { minionReuseOccupiedPoints = value; }

    public BossMinionSpawnList getSpawnPoints() { return minionSpawnPoints; }

    public boolean canSummon() {
        if (!summonEnabled) {
            return false;
        }
        if (minionSpawnMode == BossPhaseData.MINION_SPAWN_RANDOM_RADIUS) {
            return !minionCloneName.isEmpty();
        }
        return minionSpawnPoints.hasUsableClone(minionCloneName)
                || (minionSpawnMode == BossPhaseData.MINION_SPAWN_POINTS_THEN_RANDOM && !minionCloneName.isEmpty());
    }

    void writeToNBT(CompoundTag tag) {
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
    }

    void readFromNBT(CompoundTag tag) {
        summonEnabled = tag.getBoolean("SummonEnabled");
        summonAnimation = clean(tag.getString("SummonAnimation"));
        summonActionDelayTicks = value(tag, "SummonActionDelayTicks", 20, 0, 1200);
        summonCooldownTicks = value(tag, "SummonCooldownTicks", 400, 20, 12000);
        minionCloneName = clean(tag.getString("MinionCloneName"));
        minionCloneTab = value(tag, "MinionCloneTab", 1, 1, 9);
        minionCount = value(tag, "MinionCount", 3, 1, 32);
        minionRadius = value(tag, "MinionRadius", 4, 1, 32);
        maxAliveMinions = value(tag, "MaxAliveMinions", 6, 1, 128);
        minionSpawnMode = value(tag, "MinionSpawnMode", BossPhaseData.MINION_SPAWN_RANDOM_RADIUS,
                BossPhaseData.MINION_SPAWN_RANDOM_RADIUS, BossPhaseData.MINION_SPAWN_POINTS_THEN_RANDOM);
        minionSpawnOrder = value(tag, "MinionSpawnOrder", BossPhaseData.MINION_ORDER_LIST,
                BossPhaseData.MINION_ORDER_LIST, BossPhaseData.MINION_ORDER_RANDOM);
        minionPointSearchRadius = value(tag, "MinionPointSearchRadius", 0, 0, 4);
        minionReuseOccupiedPoints = tag.getBoolean("MinionReuseOccupiedPoints");
        minionSpawnPoints.readFromNBT(tag, "MinionSpawnPoints");
    }
}
