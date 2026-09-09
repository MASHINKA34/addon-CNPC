package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The field that pulls, pushes or throws everything around the boss.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#gravity()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossGravitySettings {

    private boolean gravityEnabled;
    private String gravityAnimation = "";
    private int gravityActionDelayTicks = 20;
    private int gravityCooldownTicks = 300;
    private int gravityMode = BossPhaseData.GRAVITY_MODE_PULL;
    private int gravityRadius = 16;
    /** How long the pull or the push keeps working; a throw is over the tick it happens. */
    private int gravityDurationTicks = 60;
    /**
     * Pull and push: hundredths of a block per tick added every tick, so 10 is a steady
     * 0.10. Throw: tenths of a block per tick straight up, the way the geyser's launch is.
     *
     * <p>The default is pitched against what a player on plain ground puts in per tick -
     * 0.098 walking, 0.127 sprinting - and sits between the two: a walker is held where
     * they stand, a sprinter gains about a block every sixteen ticks. Anyone standing still
     * is reeled in at walking pace.</p>
     */
    private int gravityStrength = 10;
    /** How close to the boss counts as touching it, for the pull's bite. */
    private int gravityTouchRadius = 2;
    private int gravityDamage = 8;
    private String gravityVfx = AreaVfxStyles.NONE;
    /** Dosed every second to everyone inside the field, whichever way it is pushing them. */
    private final BossEffectSet gravityEffects = new BossEffectSet();
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot gravityCastSpot = new BossCastSpot();

    public boolean isEnabled() { return gravityEnabled; }

    public void setEnabled(boolean value) { gravityEnabled = value; }

    public String getAnimation() { return gravityAnimation; }

    public void setAnimation(String value) { gravityAnimation = clean(value); }

    public int getActionDelayTicks() { return gravityActionDelayTicks; }

    public void setActionDelayTicks(int value) { gravityActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return gravityCooldownTicks; }

    public void setCooldownTicks(int value) { gravityCooldownTicks = Mth.clamp(value, 1, 12000); }

    /** Which way the field works: in, out, or up. */
    public int getMode() { return gravityMode; }

    public void setMode(int value) {
        gravityMode = Mth.clamp(value, BossPhaseData.GRAVITY_MODE_PULL, BossPhaseData.GRAVITY_MODE_LIFT);
    }

    public int getRadius() { return gravityRadius; }

    public void setRadius(int value) { gravityRadius = Mth.clamp(value, 3, 48); }

    /** How long a pull or a push runs for; a throw ignores it. */
    public int getDurationTicks() { return gravityDurationTicks; }

    public void setDurationTicks(int value) { gravityDurationTicks = Mth.clamp(value, 5, 400); }

    /** Hundredths of a block per tick for the pull and the push, tenths for the throw. */
    public int getStrength() { return gravityStrength; }

    public void setStrength(int value) { gravityStrength = Mth.clamp(value, 1, 20); }

    /** How close to the boss the pull has to get somebody before it starts to hurt them. */
    public int getTouchRadius() { return gravityTouchRadius; }

    public void setTouchRadius(int value) { gravityTouchRadius = Mth.clamp(value, 1, 6); }

    /** What the pull's bite and the throw's landing hit for; the push never hurts. */
    public int getDamage() { return gravityDamage; }

    public void setDamage(int value) { gravityDamage = Mth.clamp(value, 0, 1000); }

    public String getVfx() { return gravityVfx; }

    public void setVfx(String value) { gravityVfx = AreaVfxStyles.normalize(value); }

    public BossEffectSet getEffects() { return gravityEffects; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return gravityCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("GravityEnabled", gravityEnabled);
        tag.putString("GravityAnimation", gravityAnimation);
        tag.putInt("GravityActionDelayTicks", gravityActionDelayTicks);
        tag.putInt("GravityCooldownTicks", gravityCooldownTicks);
        tag.putInt("GravityMode", gravityMode);
        tag.putInt("GravityRadius", gravityRadius);
        tag.putInt("GravityDurationTicks", gravityDurationTicks);
        tag.putInt("GravityStrength", gravityStrength);
        tag.putInt("GravityTouchRadius", gravityTouchRadius);
        tag.putInt("GravityDamage", gravityDamage);
        tag.putString("GravityVfx", gravityVfx);
        tag.put("GravityEffects", gravityEffects.writeToNBT());
        gravityCastSpot.writeToNBT(tag, "Gravity");
    }

    void readFromNBT(CompoundTag tag) {
        gravityEnabled = tag.getBoolean("GravityEnabled");
        gravityAnimation = clean(tag.getString("GravityAnimation"));
        gravityActionDelayTicks = value(tag, "GravityActionDelayTicks", 20, 0, 1200);
        gravityCooldownTicks = value(tag, "GravityCooldownTicks", 300, 1, 12000);
        gravityMode = value(tag, "GravityMode", BossPhaseData.GRAVITY_MODE_PULL, BossPhaseData.GRAVITY_MODE_PULL, BossPhaseData.GRAVITY_MODE_LIFT);
        gravityRadius = value(tag, "GravityRadius", 16, 3, 48);
        gravityDurationTicks = value(tag, "GravityDurationTicks", 60, 5, 400);
        gravityStrength = value(tag, "GravityStrength", 10, 1, 20);
        gravityTouchRadius = value(tag, "GravityTouchRadius", 2, 1, 6);
        gravityDamage = value(tag, "GravityDamage", 8, 0, 1000);
        gravityVfx = AreaVfxStyles.normalize(tag.getString("GravityVfx"));
        gravityEffects.readFromNBT(tag, "GravityEffects");
        gravityCastSpot.readFromNBT(tag, "Gravity");
    }
}
