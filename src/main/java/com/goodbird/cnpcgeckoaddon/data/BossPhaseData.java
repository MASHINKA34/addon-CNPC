package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/** Ability and animation settings for one health phase of a stationary boss. */
public final class BossPhaseData {
    /** Each victim is dragged toward the boss. */
    public static final int HOOK_MODE_PULL = 0;
    /** Every victim is reeled in to one common point and held there for the whole pull. */
    public static final int HOOK_MODE_CINCH = 1;

    public static final String[] HOOK_MODE_LABELS = {
            "cnpcgeckoaddon.boss.hook_mode_pull",
            "cnpcgeckoaddon.boss.hook_mode_cinch"
    };

    public static final int CAPTURE_MODE_HOLD = 0;
    public static final int CAPTURE_MODE_LIFT = 1;
    public static final int CAPTURE_EFFECT_PLAYER = 0;
    public static final int CAPTURE_EFFECT_BOSS = 1;
    public static final int CAPTURE_EFFECT_BOTH = 2;

    /** The immune phase runs for its full duration and nothing else ends it. */
    public static final int INVULNERABLE_END_TIMER = 0;
    /** The immune phase lasts until every minion it summoned is dead. */
    public static final int INVULNERABLE_END_MINIONS_DEAD = 1;
    /** Whichever of the two comes first ends the immune phase. */
    public static final int INVULNERABLE_END_TIMER_OR_MINIONS = 2;
    /** The immune phase only ends once both are satisfied. */
    public static final int INVULNERABLE_END_TIMER_AND_MINIONS = 3;

    public static final String[] INVULNERABLE_END_LABELS = {
            "cnpcgeckoaddon.boss.invulnerable_end_timer",
            "cnpcgeckoaddon.boss.invulnerable_end_minions",
            "cnpcgeckoaddon.boss.invulnerable_end_either",
            "cnpcgeckoaddon.boss.invulnerable_end_both"
    };

    /** Health percentage at which this phase takes over. Phase 1 is pinned to 100. */
    private int startHealthPercent = 100;

    private String teleportPreparationAnimation = "";
    private int teleportPreparationTicks = 20;
    private String appearanceAnimation = "";
    private int appearanceLockTicks = 20;
    private int teleportMinDelayTicks = 60;
    private int teleportMaxDelayTicks = 100;

    private boolean summonEnabled;
    private String summonAnimation = "";
    private int summonActionDelayTicks = 20;
    private int summonCooldownTicks = 400;
    private String minionCloneName = "";
    private int minionCloneTab = 1;
    private int minionCount = 3;
    private int minionRadius = 4;
    private int maxAliveMinions = 6;

    private boolean areaAttackEnabled;
    private String areaAttackAnimation = "";
    private int areaAttackActionDelayTicks = 12;
    private int areaAttackCooldownTicks = 100;
    private int areaAttackDamage = 8;
    private int areaAttackRadius = 5;
    private int areaAttackKnockback = 1;

    private boolean rangedAttackEnabled;
    private String rangedAttackAnimation = "";
    private int rangedAttackActionDelayTicks = 12;
    private int rangedAttackCooldownTicks = 80;
    private int rangedAttackDamage = 6;
    private int rangedAttackMinRange = 4;
    private int rangedAttackMaxRange = 24;
    private int rangedAttackTargetMode = BossTargetMode.MAIN;

    private boolean meleeAttackEnabled;
    private String meleeAttackAnimation = "";
    private int meleeAttackActionDelayTicks = 8;
    private int meleeAttackCooldownTicks = 30;
    private int meleeAttackDamage = 6;
    private int meleeAttackRange = 3;
    private int meleeAttackKnockback = 1;
    private int meleeAttackTargetMode = BossTargetMode.MAIN;

    private boolean fluidSpitEnabled;
    private String fluidSpitAnimation = "";
    private int fluidSpitActionDelayTicks = 12;
    private int fluidSpitCooldownTicks = 120;
    private String fluidSpitBlock = "minecraft:lava";
    private int fluidSpitLifetimeTicks = 60;
    private int fluidSpitRadius = 1;
    private int fluidSpitDamage = 0;
    private int fluidSpitMinRange = 2;
    private int fluidSpitMaxRange = 24;
    private int fluidSpitTargetMode = BossTargetMode.MAIN;

    private boolean hookEnabled;
    private String hookAnimation = "";
    private int hookActionDelayTicks = 10;
    private int hookCooldownTicks = 160;
    private int hookTargetMode = BossTargetMode.FARTHEST;
    private int hookTargetCount = 1;
    private int hookDamage = 4;
    /** Tenths of a block per tick, so 8 pulls at 0.4 blocks/tick. */
    private int hookPullStrength = 8;
    private int hookPullDurationTicks = 20;
    private int hookStopDistance = 2;
    private int hookMinRange = 4;
    private int hookMaxRange = 24;
    private int hookMode = HOOK_MODE_PULL;
    private String hookCordStyle = HookCordStyles.PARTICLES;

    private boolean captureEnabled;
    private String captureAnimation = "";
    private int captureActionDelayTicks = 10;
    private int captureCooldownTicks = 200;
    private int captureTargetMode = BossTargetMode.RANDOM;
    private int captureMinRange;
    private int captureMaxRange = 16;
    private int captureMode = CAPTURE_MODE_HOLD;
    private int captureDurationTicks = 60;
    private int captureLiftHeight = 5;
    private int captureLiftTicks = 40;
    private int captureEffectTarget = CAPTURE_EFFECT_PLAYER;
    private String captureBeamStyle = HookCordStyles.GHOST;
    private int captureBeamWidthPercent = 100;
    private int captureBeamSagPercent;
    private boolean captureAllowLook = true;

    private boolean invulnerableEnabled;
    private int invulnerableEndMode = INVULNERABLE_END_TIMER_OR_MINIONS;
    private int invulnerableDurationTicks = 200;
    private boolean invulnerableAllowTeleport;
    private boolean invulnerableSummonImmediately = true;

    private final BossEffectSet areaAttackEffects = new BossEffectSet();
    private final BossEffectSet rangedAttackEffects = new BossEffectSet();
    private final BossEffectSet meleeAttackEffects = new BossEffectSet();
    private final BossEffectSet fluidSpitEffects = new BossEffectSet();
    private final BossEffectSet hookEffects = new BossEffectSet();
    private final BossEffectSet captureEffects = new BossEffectSet();

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("StartHealthPercent", startHealthPercent);
        tag.putString("TeleportPreparationAnimation", teleportPreparationAnimation);
        tag.putInt("TeleportPreparationTicks", teleportPreparationTicks);
        tag.putString("AppearanceAnimation", appearanceAnimation);
        tag.putInt("AppearanceLockTicks", appearanceLockTicks);
        tag.putInt("TeleportMinDelayTicks", teleportMinDelayTicks);
        tag.putInt("TeleportMaxDelayTicks", teleportMaxDelayTicks);
        tag.putBoolean("SummonEnabled", summonEnabled);
        tag.putString("SummonAnimation", summonAnimation);
        tag.putInt("SummonActionDelayTicks", summonActionDelayTicks);
        tag.putInt("SummonCooldownTicks", summonCooldownTicks);
        tag.putString("MinionCloneName", minionCloneName);
        tag.putInt("MinionCloneTab", minionCloneTab);
        tag.putInt("MinionCount", minionCount);
        tag.putInt("MinionRadius", minionRadius);
        tag.putInt("MaxAliveMinions", maxAliveMinions);
        tag.putBoolean("AreaAttackEnabled", areaAttackEnabled);
        tag.putString("AreaAttackAnimation", areaAttackAnimation);
        tag.putInt("AreaAttackActionDelayTicks", areaAttackActionDelayTicks);
        tag.putInt("AreaAttackCooldownTicks", areaAttackCooldownTicks);
        tag.putInt("AreaAttackDamage", areaAttackDamage);
        tag.putInt("AreaAttackRadius", areaAttackRadius);
        tag.putInt("AreaAttackKnockback", areaAttackKnockback);
        tag.putBoolean("RangedAttackEnabled", rangedAttackEnabled);
        tag.putString("RangedAttackAnimation", rangedAttackAnimation);
        tag.putInt("RangedAttackActionDelayTicks", rangedAttackActionDelayTicks);
        tag.putInt("RangedAttackCooldownTicks", rangedAttackCooldownTicks);
        tag.putInt("RangedAttackDamage", rangedAttackDamage);
        tag.putInt("RangedAttackMinRange", rangedAttackMinRange);
        tag.putInt("RangedAttackMaxRange", rangedAttackMaxRange);
        tag.putInt("RangedAttackTargetMode", rangedAttackTargetMode);
        tag.putBoolean("MeleeAttackEnabled", meleeAttackEnabled);
        tag.putString("MeleeAttackAnimation", meleeAttackAnimation);
        tag.putInt("MeleeAttackActionDelayTicks", meleeAttackActionDelayTicks);
        tag.putInt("MeleeAttackCooldownTicks", meleeAttackCooldownTicks);
        tag.putInt("MeleeAttackDamage", meleeAttackDamage);
        tag.putInt("MeleeAttackRange", meleeAttackRange);
        tag.putInt("MeleeAttackKnockback", meleeAttackKnockback);
        tag.putInt("MeleeAttackTargetMode", meleeAttackTargetMode);
        tag.putBoolean("FluidSpitEnabled", fluidSpitEnabled);
        tag.putString("FluidSpitAnimation", fluidSpitAnimation);
        tag.putInt("FluidSpitActionDelayTicks", fluidSpitActionDelayTicks);
        tag.putInt("FluidSpitCooldownTicks", fluidSpitCooldownTicks);
        tag.putString("FluidSpitBlock", fluidSpitBlock);
        tag.putInt("FluidSpitLifetimeTicks", fluidSpitLifetimeTicks);
        tag.putInt("FluidSpitRadius", fluidSpitRadius);
        tag.putInt("FluidSpitDamage", fluidSpitDamage);
        tag.putInt("FluidSpitMinRange", fluidSpitMinRange);
        tag.putInt("FluidSpitMaxRange", fluidSpitMaxRange);
        tag.putInt("FluidSpitTargetMode", fluidSpitTargetMode);
        tag.putBoolean("HookEnabled", hookEnabled);
        tag.putString("HookAnimation", hookAnimation);
        tag.putInt("HookActionDelayTicks", hookActionDelayTicks);
        tag.putInt("HookCooldownTicks", hookCooldownTicks);
        tag.putInt("HookTargetMode", hookTargetMode);
        tag.putInt("HookTargetCount", hookTargetCount);
        tag.putInt("HookDamage", hookDamage);
        tag.putInt("HookPullStrength", hookPullStrength);
        tag.putInt("HookPullDurationTicks", hookPullDurationTicks);
        tag.putInt("HookStopDistance", hookStopDistance);
        tag.putInt("HookMinRange", hookMinRange);
        tag.putInt("HookMaxRange", hookMaxRange);
        tag.putInt("HookMode", hookMode);
        tag.putString("HookCordStyle", hookCordStyle);
        tag.putBoolean("CaptureEnabled", captureEnabled);
        tag.putString("CaptureAnimation", captureAnimation);
        tag.putInt("CaptureActionDelayTicks", captureActionDelayTicks);
        tag.putInt("CaptureCooldownTicks", captureCooldownTicks);
        tag.putInt("CaptureTargetMode", captureTargetMode);
        tag.putInt("CaptureMinRange", captureMinRange);
        tag.putInt("CaptureMaxRange", captureMaxRange);
        tag.putInt("CaptureMode", captureMode);
        tag.putInt("CaptureDurationTicks", captureDurationTicks);
        tag.putInt("CaptureLiftHeight", captureLiftHeight);
        tag.putInt("CaptureLiftTicks", captureLiftTicks);
        tag.putInt("CaptureEffectTarget", captureEffectTarget);
        tag.putString("CaptureBeamStyle", captureBeamStyle);
        tag.putInt("CaptureBeamWidthPercent", captureBeamWidthPercent);
        tag.putInt("CaptureBeamSagPercent", captureBeamSagPercent);
        tag.putBoolean("CaptureAllowLook", captureAllowLook);
        tag.putBoolean("InvulnerableEnabled", invulnerableEnabled);
        tag.putInt("InvulnerableEndMode", invulnerableEndMode);
        tag.putInt("InvulnerableDurationTicks", invulnerableDurationTicks);
        tag.putBoolean("InvulnerableAllowTeleport", invulnerableAllowTeleport);
        tag.putBoolean("InvulnerableSummonImmediately", invulnerableSummonImmediately);
        tag.put("AreaAttackEffects", areaAttackEffects.writeToNBT());
        tag.put("RangedAttackEffects", rangedAttackEffects.writeToNBT());
        tag.put("MeleeAttackEffects", meleeAttackEffects.writeToNBT());
        tag.put("FluidSpitEffects", fluidSpitEffects.writeToNBT());
        tag.put("HookEffects", hookEffects.writeToNBT());
        tag.put("CaptureEffects", captureEffects.writeToNBT());
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        startHealthPercent = value(tag, "StartHealthPercent", 100, 1, 100);
        teleportPreparationAnimation = clean(tag.getString("TeleportPreparationAnimation"));
        teleportPreparationTicks = value(tag, "TeleportPreparationTicks", 20, 0, 1200);
        appearanceAnimation = clean(tag.getString("AppearanceAnimation"));
        appearanceLockTicks = value(tag, "AppearanceLockTicks", 20, 0, 1200);
        setTeleportDelayRange(
                value(tag, "TeleportMinDelayTicks", 60, 10, 1200),
                value(tag, "TeleportMaxDelayTicks", 100, 10, 1200));
        summonEnabled = tag.getBoolean("SummonEnabled");
        summonAnimation = clean(tag.getString("SummonAnimation"));
        summonActionDelayTicks = value(tag, "SummonActionDelayTicks", 20, 0, 1200);
        summonCooldownTicks = value(tag, "SummonCooldownTicks", 400, 20, 12000);
        minionCloneName = clean(tag.getString("MinionCloneName"));
        minionCloneTab = value(tag, "MinionCloneTab", 1, 1, 9);
        minionCount = value(tag, "MinionCount", 3, 1, 32);
        minionRadius = value(tag, "MinionRadius", 4, 1, 32);
        maxAliveMinions = value(tag, "MaxAliveMinions", 6, 1, 128);
        areaAttackEnabled = tag.getBoolean("AreaAttackEnabled");
        areaAttackAnimation = clean(tag.getString("AreaAttackAnimation"));
        areaAttackActionDelayTicks = value(tag, "AreaAttackActionDelayTicks", 12, 0, 1200);
        areaAttackCooldownTicks = value(tag, "AreaAttackCooldownTicks", 100, 1, 12000);
        areaAttackDamage = value(tag, "AreaAttackDamage", 8, 1, 1000);
        areaAttackRadius = value(tag, "AreaAttackRadius", 5, 1, 32);
        areaAttackKnockback = value(tag, "AreaAttackKnockback", 1, 0, 10);
        rangedAttackEnabled = tag.getBoolean("RangedAttackEnabled");
        rangedAttackAnimation = clean(tag.getString("RangedAttackAnimation"));
        rangedAttackActionDelayTicks = value(tag, "RangedAttackActionDelayTicks", 12, 0, 1200);
        rangedAttackCooldownTicks = value(tag, "RangedAttackCooldownTicks", 80, 1, 12000);
        rangedAttackDamage = value(tag, "RangedAttackDamage", 6, 1, 1000);
        setRangedAttackRange(
                value(tag, "RangedAttackMinRange", 4, 0, 64),
                value(tag, "RangedAttackMaxRange", 24, 1, 128));
        rangedAttackTargetMode = value(tag, "RangedAttackTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        meleeAttackEnabled = tag.getBoolean("MeleeAttackEnabled");
        meleeAttackAnimation = clean(tag.getString("MeleeAttackAnimation"));
        meleeAttackActionDelayTicks = value(tag, "MeleeAttackActionDelayTicks", 8, 0, 1200);
        meleeAttackCooldownTicks = value(tag, "MeleeAttackCooldownTicks", 30, 1, 12000);
        meleeAttackDamage = value(tag, "MeleeAttackDamage", 6, 1, 1000);
        meleeAttackRange = value(tag, "MeleeAttackRange", 3, 1, 32);
        meleeAttackKnockback = value(tag, "MeleeAttackKnockback", 1, 0, 10);
        meleeAttackTargetMode = value(tag, "MeleeAttackTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        fluidSpitEnabled = tag.getBoolean("FluidSpitEnabled");
        fluidSpitAnimation = clean(tag.getString("FluidSpitAnimation"));
        fluidSpitActionDelayTicks = value(tag, "FluidSpitActionDelayTicks", 12, 0, 1200);
        fluidSpitCooldownTicks = value(tag, "FluidSpitCooldownTicks", 120, 1, 12000);
        fluidSpitBlock = tag.contains("FluidSpitBlock")
                ? clean(tag.getString("FluidSpitBlock")) : "minecraft:lava";
        fluidSpitLifetimeTicks = value(tag, "FluidSpitLifetimeTicks", 60, 5, 1200);
        fluidSpitRadius = value(tag, "FluidSpitRadius", 1, 0, 4);
        fluidSpitDamage = value(tag, "FluidSpitDamage", 0, 0, 1000);
        setFluidSpitRange(
                value(tag, "FluidSpitMinRange", 2, 0, 64),
                value(tag, "FluidSpitMaxRange", 24, 1, 128));
        fluidSpitTargetMode = value(tag, "FluidSpitTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);

        hookEnabled = tag.getBoolean("HookEnabled");
        hookAnimation = clean(tag.getString("HookAnimation"));
        hookActionDelayTicks = value(tag, "HookActionDelayTicks", 10, 0, 1200);
        hookCooldownTicks = value(tag, "HookCooldownTicks", 160, 1, 12000);
        hookTargetMode = value(tag, "HookTargetMode",
                BossTargetMode.FARTHEST, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        hookTargetCount = value(tag, "HookTargetCount", 1, 1, 8);
        hookDamage = value(tag, "HookDamage", 4, 0, 1000);
        hookPullStrength = value(tag, "HookPullStrength", 8, 1, 20);
        hookPullDurationTicks = value(tag, "HookPullDurationTicks", 20, 1, 200);
        hookStopDistance = value(tag, "HookStopDistance", 2, 0, 32);
        setHookRange(
                value(tag, "HookMinRange", 4, 0, 64),
                value(tag, "HookMaxRange", 24, 1, 128));
        hookMode = value(tag, "HookMode", HOOK_MODE_PULL, HOOK_MODE_PULL, HOOK_MODE_CINCH);
        // An absent key reads as an empty string, which normalizes back to the plain sparks.
        hookCordStyle = HookCordStyles.normalize(tag.getString("HookCordStyle"));

        captureEnabled = tag.getBoolean("CaptureEnabled");
        captureAnimation = clean(tag.getString("CaptureAnimation"));
        captureActionDelayTicks = value(tag, "CaptureActionDelayTicks", 10, 0, 1200);
        captureCooldownTicks = value(tag, "CaptureCooldownTicks", 200, 20, 12000);
        captureTargetMode = value(tag, "CaptureTargetMode",
                BossTargetMode.RANDOM, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        setCaptureRange(
                value(tag, "CaptureMinRange", 0, 0, 64),
                value(tag, "CaptureMaxRange", 16, 1, 128));
        captureMode = value(tag, "CaptureMode", CAPTURE_MODE_HOLD, CAPTURE_MODE_HOLD, CAPTURE_MODE_LIFT);
        captureDurationTicks = value(tag, "CaptureDurationTicks", 60, 1, 1200);
        captureLiftHeight = value(tag, "CaptureLiftHeight", 5, 0, 64);
        captureLiftTicks = value(tag, "CaptureLiftTicks", 40, 1, 1200);
        captureEffectTarget = value(tag, "CaptureEffectTarget", CAPTURE_EFFECT_PLAYER,
                CAPTURE_EFFECT_PLAYER, CAPTURE_EFFECT_BOTH);
        captureBeamStyle = tag.contains("CaptureBeamStyle")
                ? HookCordStyles.normalize(tag.getString("CaptureBeamStyle")) : HookCordStyles.GHOST;
        captureBeamWidthPercent = value(tag, "CaptureBeamWidthPercent", 100, 25, 400);
        captureBeamSagPercent = value(tag, "CaptureBeamSagPercent", 0, 0, 200);
        captureAllowLook = !tag.contains("CaptureAllowLook") || tag.getBoolean("CaptureAllowLook");

        invulnerableEnabled = tag.getBoolean("InvulnerableEnabled");
        invulnerableEndMode = value(tag, "InvulnerableEndMode", INVULNERABLE_END_TIMER_OR_MINIONS,
                INVULNERABLE_END_TIMER, INVULNERABLE_END_TIMER_AND_MINIONS);
        invulnerableDurationTicks = value(tag, "InvulnerableDurationTicks", 200, 20, 12000);
        invulnerableAllowTeleport = tag.getBoolean("InvulnerableAllowTeleport");
        invulnerableSummonImmediately = !tag.contains("InvulnerableSummonImmediately")
                || tag.getBoolean("InvulnerableSummonImmediately");

        areaAttackEffects.readFromNBT(tag, "AreaAttackEffects");
        rangedAttackEffects.readFromNBT(tag, "RangedAttackEffects");
        meleeAttackEffects.readFromNBT(tag, "MeleeAttackEffects");
        fluidSpitEffects.readFromNBT(tag, "FluidSpitEffects");
        hookEffects.readFromNBT(tag, "HookEffects");
        captureEffects.readFromNBT(tag, "CaptureEffects");
    }

    private static int value(CompoundTag tag, String key, int fallback, int min, int max) {
        return tag.contains(key) ? Mth.clamp(tag.getInt(key), min, max) : fallback;
    }

    public int getStartHealthPercent() { return startHealthPercent; }
    public void setStartHealthPercent(int value) { startHealthPercent = Mth.clamp(value, 1, 100); }

    public String getTeleportPreparationAnimation() { return teleportPreparationAnimation; }
    public void setTeleportPreparationAnimation(String value) { teleportPreparationAnimation = clean(value); }
    public int getTeleportPreparationTicks() { return teleportPreparationTicks; }
    public void setTeleportPreparationTicks(int value) { teleportPreparationTicks = Mth.clamp(value, 0, 1200); }
    public String getAppearanceAnimation() { return appearanceAnimation; }
    public void setAppearanceAnimation(String value) { appearanceAnimation = clean(value); }
    public int getAppearanceLockTicks() { return appearanceLockTicks; }
    public void setAppearanceLockTicks(int value) { appearanceLockTicks = Mth.clamp(value, 0, 1200); }
    public int getTeleportMinDelayTicks() { return teleportMinDelayTicks; }
    public int getTeleportMaxDelayTicks() { return teleportMaxDelayTicks; }
    public void setTeleportDelayRange(int min, int max) {
        min = Mth.clamp(min, 10, 1200);
        max = Mth.clamp(max, 10, 1200);
        teleportMinDelayTicks = Math.min(min, max);
        teleportMaxDelayTicks = Math.max(min, max);
    }

    public boolean isSummonEnabled() { return summonEnabled; }
    public void setSummonEnabled(boolean value) { summonEnabled = value; }
    public String getSummonAnimation() { return summonAnimation; }
    public void setSummonAnimation(String value) { summonAnimation = clean(value); }
    public int getSummonActionDelayTicks() { return summonActionDelayTicks; }
    public void setSummonActionDelayTicks(int value) { summonActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getSummonCooldownTicks() { return summonCooldownTicks; }
    public void setSummonCooldownTicks(int value) { summonCooldownTicks = Mth.clamp(value, 20, 12000); }
    public String getMinionCloneName() { return minionCloneName; }
    public void setMinionCloneName(String value) { minionCloneName = clean(value); }
    public int getMinionCloneTab() { return minionCloneTab; }
    public void setMinionCloneTab(int value) { minionCloneTab = Mth.clamp(value, 1, 9); }
    public int getMinionCount() { return minionCount; }
    public void setMinionCount(int value) { minionCount = Mth.clamp(value, 1, 32); }
    public int getMinionRadius() { return minionRadius; }
    public void setMinionRadius(int value) { minionRadius = Mth.clamp(value, 1, 32); }
    public int getMaxAliveMinions() { return maxAliveMinions; }
    public void setMaxAliveMinions(int value) { maxAliveMinions = Mth.clamp(value, 1, 128); }
    public boolean canSummon() { return summonEnabled && !minionCloneName.isEmpty(); }

    public boolean isAreaAttackEnabled() { return areaAttackEnabled; }
    public void setAreaAttackEnabled(boolean value) { areaAttackEnabled = value; }
    public String getAreaAttackAnimation() { return areaAttackAnimation; }
    public void setAreaAttackAnimation(String value) { areaAttackAnimation = clean(value); }
    public int getAreaAttackActionDelayTicks() { return areaAttackActionDelayTicks; }
    public void setAreaAttackActionDelayTicks(int value) { areaAttackActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getAreaAttackCooldownTicks() { return areaAttackCooldownTicks; }
    public void setAreaAttackCooldownTicks(int value) { areaAttackCooldownTicks = Mth.clamp(value, 1, 12000); }
    public int getAreaAttackDamage() { return areaAttackDamage; }
    public void setAreaAttackDamage(int value) { areaAttackDamage = Mth.clamp(value, 1, 1000); }
    public int getAreaAttackRadius() { return areaAttackRadius; }
    public void setAreaAttackRadius(int value) { areaAttackRadius = Mth.clamp(value, 1, 32); }
    public int getAreaAttackKnockback() { return areaAttackKnockback; }
    public void setAreaAttackKnockback(int value) { areaAttackKnockback = Mth.clamp(value, 0, 10); }

    public boolean isRangedAttackEnabled() { return rangedAttackEnabled; }
    public void setRangedAttackEnabled(boolean value) { rangedAttackEnabled = value; }
    public String getRangedAttackAnimation() { return rangedAttackAnimation; }
    public void setRangedAttackAnimation(String value) { rangedAttackAnimation = clean(value); }
    public int getRangedAttackActionDelayTicks() { return rangedAttackActionDelayTicks; }
    public void setRangedAttackActionDelayTicks(int value) {
        rangedAttackActionDelayTicks = Mth.clamp(value, 0, 1200);
    }
    public int getRangedAttackCooldownTicks() { return rangedAttackCooldownTicks; }
    public void setRangedAttackCooldownTicks(int value) {
        rangedAttackCooldownTicks = Mth.clamp(value, 1, 12000);
    }
    public int getRangedAttackDamage() { return rangedAttackDamage; }
    public void setRangedAttackDamage(int value) { rangedAttackDamage = Mth.clamp(value, 1, 1000); }
    public int getRangedAttackMinRange() { return rangedAttackMinRange; }
    public int getRangedAttackMaxRange() { return rangedAttackMaxRange; }
    public void setRangedAttackRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        rangedAttackMinRange = Math.min(min, max);
        rangedAttackMaxRange = Math.max(min, max);
    }

    public int getRangedAttackTargetMode() { return rangedAttackTargetMode; }
    public void setRangedAttackTargetMode(int value) {
        rangedAttackTargetMode = BossTargetMode.clamp(value);
    }

    public boolean isMeleeAttackEnabled() { return meleeAttackEnabled; }
    public void setMeleeAttackEnabled(boolean value) { meleeAttackEnabled = value; }
    public String getMeleeAttackAnimation() { return meleeAttackAnimation; }
    public void setMeleeAttackAnimation(String value) { meleeAttackAnimation = clean(value); }
    public int getMeleeAttackActionDelayTicks() { return meleeAttackActionDelayTicks; }
    public void setMeleeAttackActionDelayTicks(int value) {
        meleeAttackActionDelayTicks = Mth.clamp(value, 0, 1200);
    }
    public int getMeleeAttackCooldownTicks() { return meleeAttackCooldownTicks; }
    public void setMeleeAttackCooldownTicks(int value) {
        meleeAttackCooldownTicks = Mth.clamp(value, 1, 12000);
    }
    public int getMeleeAttackDamage() { return meleeAttackDamage; }
    public void setMeleeAttackDamage(int value) { meleeAttackDamage = Mth.clamp(value, 1, 1000); }
    public int getMeleeAttackRange() { return meleeAttackRange; }
    public void setMeleeAttackRange(int value) { meleeAttackRange = Mth.clamp(value, 1, 32); }
    public int getMeleeAttackKnockback() { return meleeAttackKnockback; }
    public void setMeleeAttackKnockback(int value) { meleeAttackKnockback = Mth.clamp(value, 0, 10); }
    public int getMeleeAttackTargetMode() { return meleeAttackTargetMode; }
    public void setMeleeAttackTargetMode(int value) {
        meleeAttackTargetMode = BossTargetMode.clamp(value);
    }

    public boolean isFluidSpitEnabled() { return fluidSpitEnabled; }
    public void setFluidSpitEnabled(boolean value) { fluidSpitEnabled = value; }
    public String getFluidSpitAnimation() { return fluidSpitAnimation; }
    public void setFluidSpitAnimation(String value) { fluidSpitAnimation = clean(value); }
    public int getFluidSpitActionDelayTicks() { return fluidSpitActionDelayTicks; }
    public void setFluidSpitActionDelayTicks(int value) {
        fluidSpitActionDelayTicks = Mth.clamp(value, 0, 1200);
    }
    public int getFluidSpitCooldownTicks() { return fluidSpitCooldownTicks; }
    public void setFluidSpitCooldownTicks(int value) {
        fluidSpitCooldownTicks = Mth.clamp(value, 1, 12000);
    }
    /** Block id of the fluid to spit, for example {@code minecraft:lava}. */
    public String getFluidSpitBlock() { return fluidSpitBlock; }
    public void setFluidSpitBlock(String value) { fluidSpitBlock = clean(value); }
    public int getFluidSpitLifetimeTicks() { return fluidSpitLifetimeTicks; }
    public void setFluidSpitLifetimeTicks(int value) {
        fluidSpitLifetimeTicks = Mth.clamp(value, 5, 1200);
    }
    public int getFluidSpitRadius() { return fluidSpitRadius; }
    public void setFluidSpitRadius(int value) { fluidSpitRadius = Mth.clamp(value, 0, 4); }
    public int getFluidSpitDamage() { return fluidSpitDamage; }
    public void setFluidSpitDamage(int value) { fluidSpitDamage = Mth.clamp(value, 0, 1000); }
    public int getFluidSpitMinRange() { return fluidSpitMinRange; }
    public int getFluidSpitMaxRange() { return fluidSpitMaxRange; }
    public void setFluidSpitRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        fluidSpitMinRange = Math.min(min, max);
        fluidSpitMaxRange = Math.max(min, max);
    }
    public int getFluidSpitTargetMode() { return fluidSpitTargetMode; }
    public void setFluidSpitTargetMode(int value) {
        fluidSpitTargetMode = BossTargetMode.clamp(value);
    }
    public boolean canSpitFluid() { return fluidSpitEnabled && !fluidSpitBlock.isEmpty(); }

    public boolean isHookEnabled() { return hookEnabled; }
    public void setHookEnabled(boolean value) { hookEnabled = value; }
    public String getHookAnimation() { return hookAnimation; }
    public void setHookAnimation(String value) { hookAnimation = clean(value); }
    public int getHookActionDelayTicks() { return hookActionDelayTicks; }
    public void setHookActionDelayTicks(int value) { hookActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getHookCooldownTicks() { return hookCooldownTicks; }
    public void setHookCooldownTicks(int value) { hookCooldownTicks = Mth.clamp(value, 1, 12000); }
    public int getHookTargetMode() { return hookTargetMode; }
    public void setHookTargetMode(int value) { hookTargetMode = BossTargetMode.clamp(value); }
    /** How many victims a single cast grabs. */
    public int getHookTargetCount() { return hookTargetCount; }
    public void setHookTargetCount(int value) { hookTargetCount = Mth.clamp(value, 1, 8); }
    public int getHookDamage() { return hookDamage; }
    public void setHookDamage(int value) { hookDamage = Mth.clamp(value, 0, 1000); }
    /** Pull speed in tenths of a block per tick. */
    public int getHookPullStrength() { return hookPullStrength; }
    public void setHookPullStrength(int value) { hookPullStrength = Mth.clamp(value, 1, 20); }
    public int getHookPullDurationTicks() { return hookPullDurationTicks; }
    public void setHookPullDurationTicks(int value) { hookPullDurationTicks = Mth.clamp(value, 1, 200); }
    /** The pull releases once the victim is this close, so they are not ground into the boss. */
    public int getHookStopDistance() { return hookStopDistance; }
    public void setHookStopDistance(int value) { hookStopDistance = Mth.clamp(value, 0, 32); }
    public int getHookMinRange() { return hookMinRange; }
    public int getHookMaxRange() { return hookMaxRange; }
    public void setHookRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        hookMinRange = Math.min(min, max);
        hookMaxRange = Math.max(min, max);
    }

    public int getHookMode() { return hookMode; }
    public void setHookMode(int value) { hookMode = Mth.clamp(value, HOOK_MODE_PULL, HOOK_MODE_CINCH); }
    public String getHookCordStyle() { return hookCordStyle; }
    public void setHookCordStyle(String value) { hookCordStyle = HookCordStyles.normalize(value); }

    public boolean isCaptureEnabled() { return captureEnabled; }
    public void setCaptureEnabled(boolean value) { captureEnabled = value; }
    public String getCaptureAnimation() { return captureAnimation; }
    public void setCaptureAnimation(String value) { captureAnimation = clean(value); }
    public int getCaptureActionDelayTicks() { return captureActionDelayTicks; }
    public void setCaptureActionDelayTicks(int value) { captureActionDelayTicks = Mth.clamp(value, 0, 1200); }
    public int getCaptureCooldownTicks() { return captureCooldownTicks; }
    public void setCaptureCooldownTicks(int value) { captureCooldownTicks = Mth.clamp(value, 20, 12000); }
    public int getCaptureTargetMode() { return captureTargetMode; }
    public void setCaptureTargetMode(int value) { captureTargetMode = BossTargetMode.clamp(value); }
    public int getCaptureMinRange() { return captureMinRange; }
    public int getCaptureMaxRange() { return captureMaxRange; }
    public void setCaptureRange(int min, int max) {
        min = Mth.clamp(min, 0, 64);
        max = Mth.clamp(max, 1, 128);
        captureMinRange = Math.min(min, max);
        captureMaxRange = Math.max(min, max);
    }
    public int getCaptureMode() { return captureMode; }
    public void setCaptureMode(int value) { captureMode = Mth.clamp(value, CAPTURE_MODE_HOLD, CAPTURE_MODE_LIFT); }
    public int getCaptureDurationTicks() { return captureDurationTicks; }
    public void setCaptureDurationTicks(int value) { captureDurationTicks = Mth.clamp(value, 1, 1200); }
    public int getCaptureLiftHeight() { return captureLiftHeight; }
    public void setCaptureLiftHeight(int value) { captureLiftHeight = Mth.clamp(value, 0, 64); }
    public int getCaptureLiftTicks() { return captureLiftTicks; }
    public void setCaptureLiftTicks(int value) { captureLiftTicks = Mth.clamp(value, 1, 1200); }
    public int getCaptureEffectTarget() { return captureEffectTarget; }
    public void setCaptureEffectTarget(int value) {
        captureEffectTarget = Mth.clamp(value, CAPTURE_EFFECT_PLAYER, CAPTURE_EFFECT_BOTH);
    }
    public String getCaptureBeamStyle() { return captureBeamStyle; }
    public void setCaptureBeamStyle(String value) { captureBeamStyle = HookCordStyles.normalize(value); }
    public int getCaptureBeamWidthPercent() { return captureBeamWidthPercent; }
    public void setCaptureBeamWidthPercent(int value) { captureBeamWidthPercent = Mth.clamp(value, 25, 400); }
    public int getCaptureBeamSagPercent() { return captureBeamSagPercent; }
    public void setCaptureBeamSagPercent(int value) { captureBeamSagPercent = Mth.clamp(value, 0, 200); }
    public boolean isCaptureAllowLook() { return captureAllowLook; }
    public void setCaptureAllowLook(boolean value) { captureAllowLook = value; }

    /** While this phase runs the boss takes no damage and only its summon ability fires. */
    public boolean isInvulnerableEnabled() { return invulnerableEnabled; }
    public void setInvulnerableEnabled(boolean value) { invulnerableEnabled = value; }
    public int getInvulnerableEndMode() { return invulnerableEndMode; }
    public void setInvulnerableEndMode(int value) {
        invulnerableEndMode = Mth.clamp(value, INVULNERABLE_END_TIMER, INVULNERABLE_END_TIMER_AND_MINIONS);
    }
    public int getInvulnerableDurationTicks() { return invulnerableDurationTicks; }
    public void setInvulnerableDurationTicks(int value) {
        invulnerableDurationTicks = Mth.clamp(value, 20, 12000);
    }
    public boolean isInvulnerableAllowTeleport() { return invulnerableAllowTeleport; }
    public void setInvulnerableAllowTeleport(boolean value) { invulnerableAllowTeleport = value; }
    /** Skips the first summon cooldown so the phase opens with a wave instead of an idle wait. */
    public boolean isInvulnerableSummonImmediately() { return invulnerableSummonImmediately; }
    public void setInvulnerableSummonImmediately(boolean value) { invulnerableSummonImmediately = value; }

    /**
     * Whether dead minions are part of this phase's exit condition.
     *
     * <p>A phase with no clone configured can never satisfy "all minions are dead", so the
     * minion half of the condition is dropped there - otherwise the boss would stay immune
     * for the rest of the fight.</p>
     */
    public boolean invulnerableWaitsForMinions() {
        return canSummon() && invulnerableEndMode != INVULNERABLE_END_TIMER;
    }

    /** Counterpart of {@link #invulnerableWaitsForMinions()}; the two are never both false. */
    public boolean invulnerableWaitsForTimer() {
        return invulnerableEndMode != INVULNERABLE_END_MINIONS_DEAD || !canSummon();
    }

    public BossEffectSet getAreaAttackEffects() { return areaAttackEffects; }
    public BossEffectSet getRangedAttackEffects() { return rangedAttackEffects; }
    public BossEffectSet getMeleeAttackEffects() { return meleeAttackEffects; }
    public BossEffectSet getFluidSpitEffects() { return fluidSpitEffects; }
    public BossEffectSet getHookEffects() { return hookEffects; }
    public BossEffectSet getCaptureEffects() { return captureEffects; }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
