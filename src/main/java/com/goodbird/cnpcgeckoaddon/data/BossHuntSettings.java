package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The chase: one victim, and nobody else until it is over.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#hunt()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossHuntSettings {

    /**
     * The hunt: the boss picks one victim and goes after nobody else. Only the wind-up is a
     * cast; the chase itself is the boss walking, which is why it has a speed and a length
     * rather than a range.
     */
    private boolean huntEnabled;
    private String huntAnimation = "";
    private int huntActionDelayTicks = 10;
    private int huntCooldownTicks = 400;
    private int huntTargetMode = BossTargetMode.FARTHEST;
    /** How long the boss stays on its prey before it gives the chase up. */
    private int huntDurationTicks = 160;
    /** The boss' walking speed for the length of the chase, as a percentage of its own. */
    private int huntSpeedPercent = 130;
    /** How close the boss has to get for the prey to count as caught. */
    private int huntCatchRadius = 2;
    private int huntDamage = 15;
    /** Off, a caught prey is hit and chased on until the time runs out. */
    private boolean huntCatchEnds = true;
    /** On, the rest of the rotation waits for the chase to end. */
    private boolean huntSilence;
    /** On, the prey glows for the length of the chase, so the whole party can see who was picked. */
    private boolean huntGlow = true;
    /** Landed on the prey each time the hunt catches it. */
    private final BossEffectSet huntEffects = new BossEffectSet();

    /** Whether the boss ever singles one victim out and goes after them in this phase. */
    public boolean isEnabled() { return huntEnabled; }

    public void setEnabled(boolean value) { huntEnabled = value; }

    public String getAnimation() { return huntAnimation; }

    public void setAnimation(String value) { huntAnimation = clean(value); }

    /** The wind-up: the roar before the boss sets off. */
    public int getActionDelayTicks() { return huntActionDelayTicks; }

    public void setActionDelayTicks(int value) { huntActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return huntCooldownTicks; }

    public void setCooldownTicks(int value) { huntCooldownTicks = Mth.clamp(value, 1, 12000); }

    public int getTargetMode() { return huntTargetMode; }

    public void setTargetMode(int value) { huntTargetMode = BossTargetMode.clamp(value); }

    /** Ticks the boss stays on its prey before it gives the chase up. */
    public int getDurationTicks() { return huntDurationTicks; }

    public void setDurationTicks(int value) { huntDurationTicks = Mth.clamp(value, 20, 1200); }

    /** Walking speed for the length of the chase, as a percentage of the boss' own. */
    public int getSpeedPercent() { return huntSpeedPercent; }

    public void setSpeedPercent(int value) { huntSpeedPercent = Mth.clamp(value, 50, 300); }

    /** How close the boss has to get for the prey to count as caught. */
    public int getCatchRadius() { return huntCatchRadius; }

    public void setCatchRadius(int value) { huntCatchRadius = Mth.clamp(value, 1, 6); }

    /** What catching the prey hits for; zero leaves only the effects. */
    public int getDamage() { return huntDamage; }

    public void setDamage(int value) { huntDamage = Mth.clamp(value, 0, 1000); }

    /** Whether catching the prey ends the chase, or the boss keeps after them until the time is up. */
    public boolean isCatchEnds() { return huntCatchEnds; }

    public void setCatchEnds(boolean value) { huntCatchEnds = value; }

    /** Whether the rest of the rotation waits for the chase to end. */
    public boolean isSilence() { return huntSilence; }

    public void setSilence(boolean value) { huntSilence = value; }

    /** Whether the prey glows for the length of the chase. */
    public boolean isGlow() { return huntGlow; }

    public void setGlow(boolean value) { huntGlow = value; }

    public BossEffectSet getEffects() { return huntEffects; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("HuntEnabled", huntEnabled);
        tag.putString("HuntAnimation", huntAnimation);
        tag.putInt("HuntActionDelayTicks", huntActionDelayTicks);
        tag.putInt("HuntCooldownTicks", huntCooldownTicks);
        tag.putInt("HuntTargetMode", huntTargetMode);
        tag.putInt("HuntDurationTicks", huntDurationTicks);
        tag.putInt("HuntSpeedPercent", huntSpeedPercent);
        tag.putInt("HuntCatchRadius", huntCatchRadius);
        tag.putInt("HuntDamage", huntDamage);
        tag.putBoolean("HuntCatchEnds", huntCatchEnds);
        tag.putBoolean("HuntSilence", huntSilence);
        tag.putBoolean("HuntGlow", huntGlow);
        tag.put("HuntEffects", huntEffects.writeToNBT());
    }

    void readFromNBT(CompoundTag tag) {
        huntEnabled = tag.getBoolean("HuntEnabled");
        huntAnimation = clean(tag.getString("HuntAnimation"));
        huntActionDelayTicks = value(tag, "HuntActionDelayTicks", 10, 0, 1200);
        huntCooldownTicks = value(tag, "HuntCooldownTicks", 400, 1, 12000);
        huntTargetMode = value(tag, "HuntTargetMode",
                BossTargetMode.FARTHEST, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        huntDurationTicks = value(tag, "HuntDurationTicks", 160, 20, 1200);
        huntSpeedPercent = value(tag, "HuntSpeedPercent", 130, 50, 300);
        huntCatchRadius = value(tag, "HuntCatchRadius", 2, 1, 6);
        huntDamage = value(tag, "HuntDamage", 15, 0, 1000);
        // The two that default to on read an absent key as on, the way the immune phase's
        // immediate summon does.
        huntCatchEnds = !tag.contains("HuntCatchEnds") || tag.getBoolean("HuntCatchEnds");
        huntSilence = tag.getBoolean("HuntSilence");
        huntGlow = !tag.contains("HuntGlow") || tag.getBoolean("HuntGlow");
        huntEffects.readFromNBT(tag, "HuntEffects");
    }
}
