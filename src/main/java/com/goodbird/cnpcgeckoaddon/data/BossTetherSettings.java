package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The leash tied to the boss, to a spot or between two victims.
 *
 * <p>One phase of one boss holds one of these, reached through
 * {@link BossPhaseData#tether()}. It owns its own save format: the keys below are the ones a
 * boss on somebody's server already carries, so they are not the class' to rename.</p>
 */
public final class BossTetherSettings {

    /** The ends the leash's own clock, slack, sag and force per level are held between. */
    public static final int MIN_EFFECT_INTERVAL_TICKS = 1;
    public static final int MAX_EFFECT_INTERVAL_TICKS = 200;
    public static final int MAX_PULL_SLACK = 50;
    /**
     * As far as the beam packet carries a sag: past this it is cut down on the wire, so
     * offering more on the screen would be offering a number that never arrives.
     */
    public static final int MAX_BEAM_SAG_PERCENT = 200;
    public static final int MIN_PULL_PER_LEVEL = 1;
    public static final int MAX_PULL_PER_LEVEL = 200;

    private boolean tetherEnabled;
    private String tetherAnimation = "";
    private int tetherActionDelayTicks = 16;
    private int tetherCooldownTicks = 300;
    private int tetherTargetMode = BossTargetMode.RANDOM;
    private int tetherTargetCount = 2;
    private int tetherAnchor = BossPhaseData.TETHER_ANCHOR_BOSS;
    /** How far from its anchor a victim has to get for the leash to snap. */
    private int tetherBreakDistance = 10;
    /** How long they get to do it before the leash punishes them instead. */
    private int tetherDurationTicks = 120;
    /** Drag toward the anchor, 0 to 10; zero leaves the victim free to walk until the timer runs out. */
    private int tetherPull;
    private int tetherFailDamage = 12;
    private String tetherStyle = HookCordStyles.PARTICLES;
    private int tetherWidthPercent = 100;
    /** Ticks between one dose of the held effects and the next. */
    private int tetherEffectIntervalTicks = 20;
    /** Tenths of a block: inside this the drag lets go, or a victim on the spot would twitch. */
    private int tetherPullSlack = 10;
    /** How far the beam hangs between its ends, the way the capture's says it. */
    private int tetherBeamSagPercent = 100;
    /**
     * Thousandths of a block per tick of drag for each level of pull.
     *
     * <p>The default is pitched against what a player can put in per tick on plain ground -
     * 0.098 walking, 0.127 sprinting. At twenty thousandths level 5 pulls at 0.10, which holds
     * a walker where they are and still lets a sprinter gain about a block every sixteen
     * ticks: the way out is to run, not to stroll. Level 10 outpulls a sprint outright.</p>
     */
    private int tetherPullPerLevel = 20;
    private final BossSoundCue tetherPlaceSound =
            new BossSoundCue("minecraft:block.chain.place", 1.2F, 0.7F);
    private final BossParticleCue tetherPlaceParticles =
            new BossParticleCue(BossParticleCue.DUST_ID, 10);
    private final BossSoundCue tetherBreakSound =
            new BossSoundCue("minecraft:block.chain.break", 1.5F, 1.2F);
    private final BossParticleCue tetherBreakParticles = new BossParticleCue("minecraft:crit", 12);
    private final BossSoundCue tetherFailSound =
            new BossSoundCue("minecraft:block.chain.hit", 2.0F, 0.5F);
    private final BossParticleCue tetherFailParticles = new BossParticleCue("minecraft:smoke", 12);
    /** Dosed every second for as long as the leash holds. */
    private final BossEffectSet tetherEffects = new BossEffectSet();
    /** Landed once, on whoever was still leashed when the time ran out. */
    private final BossEffectSet tetherFailEffects = new BossEffectSet();
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot tetherCastSpot = new BossCastSpot();

    public boolean isEnabled() { return tetherEnabled; }

    public void setEnabled(boolean value) { tetherEnabled = value; }

    public String getAnimation() { return tetherAnimation; }

    public void setAnimation(String value) { tetherAnimation = clean(value); }

    public int getActionDelayTicks() { return tetherActionDelayTicks; }

    public void setActionDelayTicks(int value) { tetherActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return tetherCooldownTicks; }

    public void setCooldownTicks(int value) { tetherCooldownTicks = Mth.clamp(value, 1, 12000); }

    public int getTargetMode() { return tetherTargetMode; }

    public void setTargetMode(int value) { tetherTargetMode = BossTargetMode.clamp(value); }

    /** How many victims one cast leashes; in the pair mode they are leashed two by two. */
    public int getTargetCount() { return tetherTargetCount; }

    public void setTargetCount(int value) { tetherTargetCount = Mth.clamp(value, 1, 8); }

    /** What the leash is tied to: the boss, the ground under the victim, or another victim. */
    public int getAnchor() { return tetherAnchor; }

    public void setAnchor(int value) {
        tetherAnchor = Mth.clamp(value, BossPhaseData.TETHER_ANCHOR_BOSS, BossPhaseData.TETHER_ANCHOR_PAIR);
    }

    public int getBreakDistance() { return tetherBreakDistance; }

    public void setBreakDistance(int value) { tetherBreakDistance = Mth.clamp(value, 3, 48); }

    public int getDurationTicks() { return tetherDurationTicks; }

    public void setDurationTicks(int value) { tetherDurationTicks = Mth.clamp(value, 20, 1200); }

    /** Drag toward the anchor, 0 to 10. Zero only times the victim out; it never moves them. */
    public int getPull() { return tetherPull; }

    public void setPull(int value) { tetherPull = Mth.clamp(value, 0, 10); }

    public int getFailDamage() { return tetherFailDamage; }

    public void setFailDamage(int value) { tetherFailDamage = Mth.clamp(value, 0, 1000); }

    public String getStyle() { return tetherStyle; }

    public void setStyle(String value) { tetherStyle = HookCordStyles.normalize(value); }

    public int getWidthPercent() { return tetherWidthPercent; }

    public void setWidthPercent(int value) { tetherWidthPercent = Mth.clamp(value, 25, 400); }

    /** Ticks between one dose of the held effects and the next. */
    public int getEffectIntervalTicks() { return tetherEffectIntervalTicks; }

    public void setEffectIntervalTicks(int value) {
        tetherEffectIntervalTicks = Mth.clamp(value, MIN_EFFECT_INTERVAL_TICKS, MAX_EFFECT_INTERVAL_TICKS);
    }

    /** Tenths of a block inside which the drag lets go; nought tugs all the way in. */
    public int getPullSlackTenths() { return tetherPullSlack; }

    public void setPullSlackTenths(int value) {
        tetherPullSlack = Mth.clamp(value, 0, MAX_PULL_SLACK);
    }

    /** How far the beam hangs between its ends; nought draws it straight. */
    public int getBeamSagPercent() { return tetherBeamSagPercent; }

    public void setBeamSagPercent(int value) {
        tetherBeamSagPercent = Mth.clamp(value, 0, MAX_BEAM_SAG_PERCENT);
    }

    /** Thousandths of a block a tick of drag per level of pull. */
    public int getPullPerLevelThousandths() { return tetherPullPerLevel; }

    public void setPullPerLevelThousandths(int value) {
        tetherPullPerLevel = Mth.clamp(value, MIN_PULL_PER_LEVEL, MAX_PULL_PER_LEVEL);
    }

    /** The chain closing on a victim. */
    public BossSoundCue getPlaceSound() { return tetherPlaceSound; }

    public BossParticleCue getPlaceParticles() { return tetherPlaceParticles; }

    /** The chain giving, for a victim who ran far enough. */
    public BossSoundCue getBreakSound() { return tetherBreakSound; }

    public BossParticleCue getBreakParticles() { return tetherBreakParticles; }

    /** And holding to the end, on one who did not. */
    public BossSoundCue getFailSound() { return tetherFailSound; }

    public BossParticleCue getFailParticles() { return tetherFailParticles; }

    public BossEffectSet getEffects() { return tetherEffects; }

    public BossEffectSet getFailEffects() { return tetherFailEffects; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return tetherCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("TetherEnabled", tetherEnabled);
        tag.putString("TetherAnimation", tetherAnimation);
        tag.putInt("TetherActionDelayTicks", tetherActionDelayTicks);
        tag.putInt("TetherCooldownTicks", tetherCooldownTicks);
        tag.putInt("TetherTargetMode", tetherTargetMode);
        tag.putInt("TetherTargetCount", tetherTargetCount);
        tag.putInt("TetherAnchor", tetherAnchor);
        tag.putInt("TetherBreakDistance", tetherBreakDistance);
        tag.putInt("TetherDurationTicks", tetherDurationTicks);
        tag.putInt("TetherPull", tetherPull);
        tag.putInt("TetherFailDamage", tetherFailDamage);
        tag.putString("TetherStyle", tetherStyle);
        tag.putInt("TetherWidthPercent", tetherWidthPercent);
        tag.putInt("TetherEffectInterval", tetherEffectIntervalTicks);
        tag.putInt("TetherPullSlack", tetherPullSlack);
        tag.putInt("TetherBeamSag", tetherBeamSagPercent);
        tag.putInt("TetherPullPerLevel", tetherPullPerLevel);
        tetherPlaceSound.writeToNBT(tag, "TetherPlaceSound");
        tetherPlaceParticles.writeToNBT(tag, "TetherPlaceParticles");
        tetherBreakSound.writeToNBT(tag, "TetherBreakSound");
        tetherBreakParticles.writeToNBT(tag, "TetherBreakParticles");
        tetherFailSound.writeToNBT(tag, "TetherFailSound");
        tetherFailParticles.writeToNBT(tag, "TetherFailParticles");
        tag.put("TetherEffects", tetherEffects.writeToNBT());
        tag.put("TetherFailEffects", tetherFailEffects.writeToNBT());
        tetherCastSpot.writeToNBT(tag, "Tether");
    }

    void readFromNBT(CompoundTag tag) {
        tetherEnabled = tag.getBoolean("TetherEnabled");
        tetherAnimation = clean(tag.getString("TetherAnimation"));
        tetherActionDelayTicks = value(tag, "TetherActionDelayTicks", 16, 0, 1200);
        tetherCooldownTicks = value(tag, "TetherCooldownTicks", 300, 1, 12000);
        tetherTargetMode = value(tag, "TetherTargetMode",
                BossTargetMode.RANDOM, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        tetherTargetCount = value(tag, "TetherTargetCount", 2, 1, 8);
        tetherAnchor = value(tag, "TetherAnchor", BossPhaseData.TETHER_ANCHOR_BOSS, BossPhaseData.TETHER_ANCHOR_BOSS, BossPhaseData.TETHER_ANCHOR_PAIR);
        tetherBreakDistance = value(tag, "TetherBreakDistance", 10, 3, 48);
        tetherDurationTicks = value(tag, "TetherDurationTicks", 120, 20, 1200);
        tetherPull = value(tag, "TetherPull", 0, 0, 10);
        tetherFailDamage = value(tag, "TetherFailDamage", 12, 0, 1000);
        // An absent key reads as an empty string, which normalizes back to the plain sparks.
        tetherStyle = HookCordStyles.normalize(tag.getString("TetherStyle"));
        tetherWidthPercent = value(tag, "TetherWidthPercent", 100, 25, 400);
        tetherEffectIntervalTicks = value(tag, "TetherEffectInterval", 20,
                MIN_EFFECT_INTERVAL_TICKS, MAX_EFFECT_INTERVAL_TICKS);
        tetherPullSlack = value(tag, "TetherPullSlack", 10, 0, MAX_PULL_SLACK);
        tetherBeamSagPercent = value(tag, "TetherBeamSag", 100, 0, MAX_BEAM_SAG_PERCENT);
        tetherPullPerLevel = value(tag, "TetherPullPerLevel", 20, MIN_PULL_PER_LEVEL, MAX_PULL_PER_LEVEL);
        tetherPlaceSound.readFromNBT(tag, "TetherPlaceSound");
        tetherPlaceParticles.readFromNBT(tag, "TetherPlaceParticles");
        tetherBreakSound.readFromNBT(tag, "TetherBreakSound");
        tetherBreakParticles.readFromNBT(tag, "TetherBreakParticles");
        tetherFailSound.readFromNBT(tag, "TetherFailSound");
        tetherFailParticles.readFromNBT(tag, "TetherFailParticles");
        tetherEffects.readFromNBT(tag, "TetherEffects");
        tetherFailEffects.readFromNBT(tag, "TetherFailEffects");
        tetherCastSpot.readFromNBT(tag, "Tether");
    }
}
