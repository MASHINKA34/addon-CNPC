package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The shell closed round a victim, and the guard posted beside it.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#cocoon()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossCocoonSettings {

    /**
     * The cocoon: each victim is locked inside a clone spawned on the spot they stood on,
     * and only the rest of the party can let them out. Only the wind-up is a cast; the
     * lock itself runs on the level tick, which is why it has a time limit and a rescue
     * rule rather than a range.
     */
    private boolean cocoonEnabled;
    private String cocoonAnimation = "";
    private int cocoonActionDelayTicks = 16;
    private int cocoonCooldownTicks = 400;
    private int cocoonTargetMode = BossTargetMode.RANDOM;
    private int cocoonTargetCount = 1;
    /** The clone that stands on the victim. An empty name leaves the ability silent. */
    private int cocoonCloneTab = 1;
    private String cocoonCloneName = "";
    private int cocoonRescueMode = BossPhaseData.COCOON_RESCUE_KILL;
    /** Stand rule: how close a rescuer has to be, and how many ticks they put in between them. */
    private int cocoonRescueRadius = 3;
    private int cocoonRescueTicks = 60;
    /** How long the party gets before the cocoon bursts on its victim. */
    private int cocoonDurationTicks = 300;
    private int cocoonFailDamage = 40;
    /** The clone posted beside each cocoon. An empty name posts nobody. */
    private int cocoonGuardTab = 1;
    private String cocoonGuardName = "";
    /** Dosed every second to a cocooned victim for as long as they are inside. */
    private final BossEffectSet cocoonVictimEffects = new BossEffectSet();
    /** Landed once, on a victim nobody came for in time. */
    private final BossEffectSet cocoonFailEffects = new BossEffectSet();
    /** Landed once, on a victim the party let out. */
    private final BossEffectSet cocoonFreeEffects = new BossEffectSet();
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot cocoonCastSpot = new BossCastSpot();

    /** Whether the boss ever locks victims inside cocoons in this phase. */
    public boolean isEnabled() { return cocoonEnabled; }

    public void setEnabled(boolean value) { cocoonEnabled = value; }

    public String getAnimation() { return cocoonAnimation; }

    public void setAnimation(String value) { cocoonAnimation = clean(value); }

    /** The wind-up: the spinning before the cocoons close. */
    public int getActionDelayTicks() { return cocoonActionDelayTicks; }

    public void setActionDelayTicks(int value) { cocoonActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return cocoonCooldownTicks; }

    public void setCooldownTicks(int value) { cocoonCooldownTicks = Mth.clamp(value, 1, 12000); }

    public int getTargetMode() { return cocoonTargetMode; }

    public void setTargetMode(int value) { cocoonTargetMode = BossTargetMode.clamp(value); }

    /** How many victims one cast locks up, each in a cocoon of their own. */
    public int getTargetCount() { return cocoonTargetCount; }

    public void setTargetCount(int value) { cocoonTargetCount = Mth.clamp(value, 1, 4); }

    public int getCloneTab() { return cocoonCloneTab; }

    public void setCloneTab(int value) { cocoonCloneTab = Mth.clamp(value, 1, 9); }

    public String getCloneName() { return cocoonCloneName; }

    public void setCloneName(String value) { cocoonCloneName = clean(value); }

    /** How the party lets a victim out: by killing the cocoon, or by standing beside it. */
    public int getRescueMode() { return cocoonRescueMode; }

    public void setRescueMode(int value) {
        cocoonRescueMode = Mth.clamp(value, BossPhaseData.COCOON_RESCUE_KILL, BossPhaseData.COCOON_RESCUE_STAND);
    }

    /** Stand rule: how close to the cocoon a rescuer has to be for their time to count. */
    public int getRescueRadius() { return cocoonRescueRadius; }

    public void setRescueRadius(int value) { cocoonRescueRadius = Mth.clamp(value, 1, 8); }

    /** Stand rule: the ticks the rescuers have to put in between them, two beside it counting double. */
    public int getRescueTicks() { return cocoonRescueTicks; }

    public void setRescueTicks(int value) { cocoonRescueTicks = Mth.clamp(value, 10, 1200); }

    /** How long the party gets before the cocoon bursts on whoever is still inside. */
    public int getDurationTicks() { return cocoonDurationTicks; }

    public void setDurationTicks(int value) { cocoonDurationTicks = Mth.clamp(value, 20, 2400); }

    /** What the burst hits the victim for; zero leaves only the effects. */
    public int getFailDamage() { return cocoonFailDamage; }

    public void setFailDamage(int value) { cocoonFailDamage = Mth.clamp(value, 0, 1000); }

    public int getGuardTab() { return cocoonGuardTab; }

    public void setGuardTab(int value) { cocoonGuardTab = Mth.clamp(value, 1, 9); }

    public String getGuardName() { return cocoonGuardName; }

    public void setGuardName(String value) { cocoonGuardName = clean(value); }

    /** Whether the cocoon can fire at all: switched on, and with a clone to close round somebody. */
    public boolean canCocoon() { return cocoonEnabled && !cocoonCloneName.isEmpty(); }

    public BossEffectSet getVictimEffects() { return cocoonVictimEffects; }

    public BossEffectSet getFailEffects() { return cocoonFailEffects; }

    public BossEffectSet getFreeEffects() { return cocoonFreeEffects; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return cocoonCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("CocoonEnabled", cocoonEnabled);
        tag.putString("CocoonAnimation", cocoonAnimation);
        tag.putInt("CocoonActionDelayTicks", cocoonActionDelayTicks);
        tag.putInt("CocoonCooldownTicks", cocoonCooldownTicks);
        tag.putInt("CocoonTargetMode", cocoonTargetMode);
        tag.putInt("CocoonTargetCount", cocoonTargetCount);
        tag.putInt("CocoonCloneTab", cocoonCloneTab);
        tag.putString("CocoonCloneName", cocoonCloneName);
        tag.putInt("CocoonRescueMode", cocoonRescueMode);
        tag.putInt("CocoonRescueRadius", cocoonRescueRadius);
        tag.putInt("CocoonRescueTicks", cocoonRescueTicks);
        tag.putInt("CocoonDurationTicks", cocoonDurationTicks);
        tag.putInt("CocoonFailDamage", cocoonFailDamage);
        tag.putInt("CocoonGuardTab", cocoonGuardTab);
        tag.putString("CocoonGuardName", cocoonGuardName);
        tag.put("CocoonVictimEffects", cocoonVictimEffects.writeToNBT());
        tag.put("CocoonFailEffects", cocoonFailEffects.writeToNBT());
        tag.put("CocoonFreeEffects", cocoonFreeEffects.writeToNBT());
        cocoonCastSpot.writeToNBT(tag, "Cocoon");
    }

    void readFromNBT(CompoundTag tag) {
        // A boss saved before the cocoon existed carries no key and reads as off.
        cocoonEnabled = tag.getBoolean("CocoonEnabled");
        cocoonAnimation = clean(tag.getString("CocoonAnimation"));
        cocoonActionDelayTicks = value(tag, "CocoonActionDelayTicks", 16, 0, 1200);
        cocoonCooldownTicks = value(tag, "CocoonCooldownTicks", 400, 1, 12000);
        cocoonTargetMode = value(tag, "CocoonTargetMode",
                BossTargetMode.RANDOM, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        cocoonTargetCount = value(tag, "CocoonTargetCount", 1, 1, 4);
        cocoonCloneTab = value(tag, "CocoonCloneTab", 1, 1, 9);
        cocoonCloneName = clean(tag.getString("CocoonCloneName"));
        cocoonRescueMode = value(tag, "CocoonRescueMode", BossPhaseData.COCOON_RESCUE_KILL,
                BossPhaseData.COCOON_RESCUE_KILL, BossPhaseData.COCOON_RESCUE_STAND);
        cocoonRescueRadius = value(tag, "CocoonRescueRadius", 3, 1, 8);
        cocoonRescueTicks = value(tag, "CocoonRescueTicks", 60, 10, 1200);
        cocoonDurationTicks = value(tag, "CocoonDurationTicks", 300, 20, 2400);
        cocoonFailDamage = value(tag, "CocoonFailDamage", 40, 0, 1000);
        cocoonGuardTab = value(tag, "CocoonGuardTab", 1, 1, 9);
        cocoonGuardName = clean(tag.getString("CocoonGuardName"));
        cocoonVictimEffects.readFromNBT(tag, "CocoonVictimEffects");
        cocoonFailEffects.readFromNBT(tag, "CocoonFailEffects");
        cocoonFreeEffects.readFromNBT(tag, "CocoonFreeEffects");
        cocoonCastSpot.readFromNBT(tag, "Cocoon");
    }
}
