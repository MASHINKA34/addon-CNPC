package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The hop along the path, and how long the boss winds up for it.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#teleport()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossTeleportSettings {

    private String teleportPreparationAnimation = "";
    private int teleportPreparationTicks = 20;
    private int teleportMinDelayTicks = 60;
    private int teleportMaxDelayTicks = 100;

    public String getPreparationAnimation() { return teleportPreparationAnimation; }

    public void setPreparationAnimation(String value) { teleportPreparationAnimation = clean(value); }

    public int getPreparationTicks() { return teleportPreparationTicks; }

    public void setPreparationTicks(int value) { teleportPreparationTicks = Mth.clamp(value, 0, 1200); }

    public int getMinDelayTicks() { return teleportMinDelayTicks; }

    public int getMaxDelayTicks() { return teleportMaxDelayTicks; }

    public void setDelayRange(int min, int max) {
        min = Mth.clamp(min, 10, 1200);
        max = Mth.clamp(max, 10, 1200);
        teleportMinDelayTicks = Math.min(min, max);
        teleportMaxDelayTicks = Math.max(min, max);
    }

    void writeToNBT(CompoundTag tag) {
        tag.putString("TeleportPreparationAnimation", teleportPreparationAnimation);
        tag.putInt("TeleportPreparationTicks", teleportPreparationTicks);
        tag.putInt("TeleportMinDelayTicks", teleportMinDelayTicks);
        tag.putInt("TeleportMaxDelayTicks", teleportMaxDelayTicks);
    }

    void readFromNBT(CompoundTag tag) {
        teleportPreparationAnimation = clean(tag.getString("TeleportPreparationAnimation"));
        teleportPreparationTicks = value(tag, "TeleportPreparationTicks", 20, 0, 1200);
        setDelayRange(
                value(tag, "TeleportMinDelayTicks", 60, 10, 1200),
                value(tag, "TeleportMaxDelayTicks", 100, 10, 1200));
    }
}
