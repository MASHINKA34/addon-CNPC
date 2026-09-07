package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The window a phase cannot be hurt in, and what closes it.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#invulnerable()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossInvulnerableSettings {

    private boolean invulnerableEnabled;
    private int invulnerableEndMode = BossPhaseData.INVULNERABLE_END_TIMER_OR_MINIONS;
    private int invulnerableDurationTicks = 200;
    private boolean invulnerableAllowTeleport;
    private boolean invulnerableSummonImmediately = true;

    /** While this phase runs the boss takes no damage and only its summon ability fires. */
    public boolean isEnabled() { return invulnerableEnabled; }

    public void setEnabled(boolean value) { invulnerableEnabled = value; }

    public int getEndMode() { return invulnerableEndMode; }

    public void setEndMode(int value) {
        invulnerableEndMode = Mth.clamp(value, BossPhaseData.INVULNERABLE_END_TIMER, BossPhaseData.INVULNERABLE_END_TIMER_AND_MINIONS);
    }

    public int getDurationTicks() { return invulnerableDurationTicks; }

    public void setDurationTicks(int value) {
        invulnerableDurationTicks = Mth.clamp(value, 20, 12000);
    }

    public boolean isAllowTeleport() { return invulnerableAllowTeleport; }

    public void setAllowTeleport(boolean value) { invulnerableAllowTeleport = value; }

    /** Skips the first summon cooldown so the phase opens with a wave instead of an idle wait. */
    public boolean isSummonImmediately() { return invulnerableSummonImmediately; }

    public void setSummonImmediately(boolean value) { invulnerableSummonImmediately = value; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("InvulnerableEnabled", invulnerableEnabled);
        tag.putInt("InvulnerableEndMode", invulnerableEndMode);
        tag.putInt("InvulnerableDurationTicks", invulnerableDurationTicks);
        tag.putBoolean("InvulnerableAllowTeleport", invulnerableAllowTeleport);
        tag.putBoolean("InvulnerableSummonImmediately", invulnerableSummonImmediately);
    }

    void readFromNBT(CompoundTag tag) {
        invulnerableEnabled = tag.getBoolean("InvulnerableEnabled");
        invulnerableEndMode = value(tag, "InvulnerableEndMode", BossPhaseData.INVULNERABLE_END_TIMER_OR_MINIONS,
                BossPhaseData.INVULNERABLE_END_TIMER, BossPhaseData.INVULNERABLE_END_TIMER_AND_MINIONS);
        invulnerableDurationTicks = value(tag, "InvulnerableDurationTicks", 200, 20, 12000);
        invulnerableAllowTeleport = tag.getBoolean("InvulnerableAllowTeleport");
        invulnerableSummonImmediately = !tag.contains("InvulnerableSummonImmediately")
                || tag.getBoolean("InvulnerableSummonImmediately");
    }
}
