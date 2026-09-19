package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.Locale;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The reality rift: who the boss cuts out of the arena, where in its pocket dimension they land,
 * what they have to do there to come back, and what the fight at home turns into meanwhile and
 * after.
 *
 * <p>One phase of one boss holds one of these, reached through {@link BossPhaseData#rift()}. It
 * owns its own save format under the {@code Rift} prefix; a boss saved before the rift existed
 * carries none of these keys and reads back with the ability switched off. The centre of a
 * platform built by hand is saved as {@code RiftArena*}, since {@code RiftSpot*} is the cast
 * spot's, the way every ability's cast spot sits under its own prefix.</p>
 */
public final class BossRiftSettings {

    /** The taken players come back once the time is up. */
    public static final int EXIT_SURVIVE = 0;
    /** The taken players come back once the rift's own minions are dead; the time is a limit. */
    public static final int EXIT_MINIONS = 1;
    /** The taken players come back once they have gathered the rift's crystals. */
    public static final int EXIT_CRYSTALS = 2;
    /** Both of the two above. */
    public static final int EXIT_BOTH = 3;
    public static final String[] EXIT_LABELS = {
            "cnpcgeckoaddon.boss.rift_exit.survive",
            "cnpcgeckoaddon.boss.rift_exit.minions",
            "cnpcgeckoaddon.boss.rift_exit.crystals",
            "cnpcgeckoaddon.boss.rift_exit.both"
    };
    /**
     * Whether the crystals exist yet. Until they do, the two ways out that ask for them run as
     * the survival one, and the rift says so in the log.
     */
    public static final boolean CRYSTALS_AVAILABLE = false;

    /** The addon lays the platform in the boss' slot. */
    public static final int ARENA_BUILT = 0;
    /** A builder laid the platform by hand; the rift only takes players to its centre. */
    public static final int ARENA_PREBUILT = 1;
    public static final String[] ARENA_LABELS = {
            "cnpcgeckoaddon.boss.rift_arena.built",
            "cnpcgeckoaddon.boss.rift_arena.prebuilt"
    };

    public static final int MAX_TARGET_COUNT = 16;
    public static final int MIN_TIME_LIMIT_TICKS = 100;
    public static final int MAX_TIME_LIMIT_TICKS = 24000;
    public static final int MAX_SOLO_PLAYERS = 16;
    public static final int MAX_SOLO_VULNERABLE_TICKS = 6000;
    public static final int MIN_SOLO_VULNERABLE_PERCENT = 100;
    public static final int MAX_SOLO_VULNERABLE_PERCENT = 1000;
    public static final int MAX_GROUP_DAMAGE_PERCENT = 100;
    /** The last slot of the rift dimension's grid; nought is the boss' own. */
    public static final int MAX_SLOT = 4095;
    public static final int MAX_COORDINATE = 30000000;
    public static final int MIN_ARENA_Y = 1;
    public static final int MAX_ARENA_Y = 255;
    public static final int MIN_PLATFORM_Y = 8;
    public static final int MAX_PLATFORM_Y = 200;
    public static final int MIN_PLATFORM_RADIUS = 4;
    public static final int MAX_PLATFORM_RADIUS = 64;
    public static final int MAX_WALL_HEIGHT = 16;
    public static final int MAX_LIGHT_SPACING = 32;
    public static final int MAX_LEASH_RADIUS = 128;
    public static final int MAX_FALL_GUARD_DEPTH = 64;
    /** The clone tabs every other clone setting of the boss offers. */
    public static final int MAX_CLONE_TAB = 9;
    public static final int MAX_MINION_COUNT = 16;
    public static final int MAX_MINION_RADIUS = 32;
    public static final int MAX_FAIL_ARENA_DAMAGE = 1000;
    public static final int MAX_FAIL_ARENA_RADIUS = 128;
    public static final int MAX_FAIL_HEAL_PERCENT = 100;
    public static final int MAX_COLOR = 0xFFFFFF;
    public static final int MAX_TINT_ALPHA = 100;
    public static final int MAX_TINT_PULSE_TICKS = 200;
    public static final int MAX_FOG_DISTANCE = 256;
    public static final int MIN_CUE_INTERVAL_TICKS = 5;
    public static final int MAX_CUE_INTERVAL_TICKS = 400;
    public static final String DEFAULT_PLATFORM_BLOCK = "minecraft:end_stone";
    public static final String DEFAULT_WALL_BLOCK = "minecraft:obsidian";
    public static final String DEFAULT_LIGHT_BLOCK = "minecraft:sea_lantern";
    public static final int DEFAULT_TINT_COLOR = 0x6A00B4;
    public static final int DEFAULT_FOG_COLOR = 0x2B003F;

    /**
     * The abilities the boss may be let cast while a rift runs, in the order they are offered:
     * everything the rotation casts, less a second rift on top of the first and the two that
     * would stand more of the boss' own up beside it - the minion summon and the shadow copies.
     */
    public static final int[] MEANWHILE_ABILITIES = Arrays.stream(BossAbilityKind.COMBO_ABILITIES)
            .filter(kind -> kind != BossAbilityKind.RIFT && kind != BossAbilityKind.SUMMON
                    && kind != BossAbilityKind.SHADOW)
            .toArray();

    /** Every bit {@link #MEANWHILE_ABILITIES} owns; any other bit in the mask is never read. */
    public static final long MEANWHILE_ALL = maskOf(MEANWHILE_ABILITIES);

    private boolean riftEnabled;
    private String riftAnimation = "";
    private int riftActionDelayTicks = 30;
    private int riftCooldownTicks = 1200;
    private int riftTargetMode = BossTargetMode.MAIN;
    private int riftTargetCount = 1;
    private int riftExitMode = EXIT_SURVIVE;
    /** The survival rift's length, and every other way out's limit. */
    private int riftTimeLimitTicks = 1200;
    private boolean riftFailOnDeath = true;
    /** At most this many fighting on the arena, and the rift is a solo one. */
    private int riftSoloMaxPlayers = 1;
    /** Nought is no window at all after a solo rift got through. */
    private int riftSoloVulnerableTicks = 200;
    private int riftSoloVulnerablePercent = 150;
    /** What the boss takes of every hit while a group's rift runs. */
    private int riftGroupDamagePercent = 50;
    /** What the boss may cast while a rift runs, one bit per {@link BossAbilityKind}; nothing by default. */
    private long riftAbilities;
    private int riftArenaMode = ARENA_BUILT;
    /** Nought takes the slot the boss' UUID hashes to. */
    private int riftSlot;
    /** The centre block of a platform laid by hand: its floor block, in the rift dimension. */
    private int riftArenaX;
    private int riftArenaY = 64;
    private int riftArenaZ;
    private int riftPlatformY = 64;
    private int riftPlatformRadius = 16;
    private String riftPlatformBlock = DEFAULT_PLATFORM_BLOCK;
    private String riftWallBlock = DEFAULT_WALL_BLOCK;
    private String riftLightBlock = DEFAULT_LIGHT_BLOCK;
    private int riftWallHeight = 4;
    /** Nought is no lights. */
    private int riftLightSpacing = 6;
    private boolean riftPlatformRoof;
    /** Nought is no leash: whoever walks off the edge is the fall guard's problem. */
    private int riftLeashRadius;
    /** Nought is no guard: a fall into the void is a death like any other. */
    private int riftFallGuardDepth = 8;
    private String riftMinionCloneName = "";
    private int riftMinionCloneTab = 1;
    private int riftMinionCount = 3;
    /** Offsets are from the platform's centre block, fixed points are in the rift dimension. */
    private final BossMinionSpawnList riftMinionPoints = new BossMinionSpawnList();
    /** The ring round the centre the minions stand on when no point is listed. */
    private int riftMinionRadius = 6;
    private boolean riftMinionRemoveOnEnd = true;
    private boolean riftFailRage;
    /** Nought hits nobody, though the failure's potions still land. */
    private int riftFailArenaDamage;
    private int riftFailArenaRadius = 32;
    private final BossEffectSet riftFailEffects = new BossEffectSet();
    private int riftFailHealPercent;
    private int riftTintColor = DEFAULT_TINT_COLOR;
    private int riftTintAlpha = 35;
    /** Nought is a steady tint. */
    private int riftTintPulseTicks = 40;
    private int riftFogColor = DEFAULT_FOG_COLOR;
    /** Nought leaves the fog as the game draws it. */
    private int riftFogDistance = 24;
    private final BossEffectSet riftEffects = new BossEffectSet();
    private int riftLoopIntervalTicks = 60;
    private int riftAmbientIntervalTicks = 20;
    private final BossSoundCue riftCutSound = new BossSoundCue("minecraft:entity.enderman.teleport", 1.5F, 0.5F);
    private final BossSoundCue riftEnterSound = new BossSoundCue("minecraft:item.chorus_fruit.teleport", 1.0F, 0.6F);
    private final BossSoundCue riftExitSound = new BossSoundCue("minecraft:item.chorus_fruit.teleport", 1.0F, 1.2F);
    private final BossSoundCue riftLoopSound = new BossSoundCue("minecraft:ambient.cave", 0.8F, 0.6F);
    private final BossSoundCue riftSuccessSound = new BossSoundCue("minecraft:entity.player.levelup", 1.0F, 1.4F);
    private final BossSoundCue riftFailSound = new BossSoundCue("minecraft:entity.wither.spawn", 1.0F, 0.8F);
    private final BossParticleCue riftCutParticles = new BossParticleCue("minecraft:reverse_portal", 40);
    private final BossParticleCue riftEnterParticles = new BossParticleCue("minecraft:poof", 20);
    private final BossParticleCue riftAmbientParticles = new BossParticleCue("minecraft:portal", 6);
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot riftCastSpot = new BossCastSpot();

    public boolean isEnabled() { return riftEnabled; }

    public void setEnabled(boolean value) { riftEnabled = value; }

    /** Switched on and able to be cast: a minion rift needs a clone to stand up. */
    public boolean canCast() { return riftEnabled && isConfigured(); }

    /**
     * Whether the way out has what it needs, whatever the switch says: a rift that ends when its
     * minions are dead needs a clone to spawn them from. The other ways out need nothing.
     */
    public boolean isConfigured() {
        return !needsMinions() || !riftMinionCloneName.isEmpty()
                || riftMinionPoints.hasUsableClone(riftMinionCloneName);
    }

    /** Whether this rift's way out, as it runs today, asks for minions. */
    public boolean needsMinions() {
        int mode = effectiveExitMode(riftExitMode, CRYSTALS_AVAILABLE);
        return mode == EXIT_MINIONS || mode == EXIT_BOTH;
    }

    /**
     * The way out a rift really runs: the one the phase picked, or the survival one for the two
     * that ask for crystals while there are none to gather.
     */
    public static int effectiveExitMode(int mode, boolean crystalsAvailable) {
        int clamped = Mth.clamp(mode, EXIT_SURVIVE, EXIT_BOTH);
        if (!crystalsAvailable && (clamped == EXIT_CRYSTALS || clamped == EXIT_BOTH)) {
            return EXIT_SURVIVE;
        }
        return clamped;
    }

    /** The wind-up before the cut. */
    public String getAnimation() { return riftAnimation; }

    public void setAnimation(String value) { riftAnimation = clean(value); }

    public int getActionDelayTicks() { return riftActionDelayTicks; }

    public void setActionDelayTicks(int value) { riftActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return riftCooldownTicks; }

    public void setCooldownTicks(int value) { riftCooldownTicks = Mth.clamp(value, 1, 12000); }

    public int getTargetMode() { return riftTargetMode; }

    public void setTargetMode(int value) { riftTargetMode = BossTargetMode.clamp(value); }

    public int getTargetCount() { return riftTargetCount; }

    public void setTargetCount(int value) { riftTargetCount = Mth.clamp(value, 1, MAX_TARGET_COUNT); }

    public int getExitMode() { return riftExitMode; }

    public void setExitMode(int value) { riftExitMode = Mth.clamp(value, EXIT_SURVIVE, EXIT_BOTH); }

    public int getTimeLimitTicks() { return riftTimeLimitTicks; }

    public void setTimeLimitTicks(int value) {
        riftTimeLimitTicks = Mth.clamp(value, MIN_TIME_LIMIT_TICKS, MAX_TIME_LIMIT_TICKS);
    }

    /** Whether everyone taken dying fails the rift. */
    public boolean isFailOnDeath() { return riftFailOnDeath; }

    public void setFailOnDeath(boolean value) { riftFailOnDeath = value; }

    public int getSoloMaxPlayers() { return riftSoloMaxPlayers; }

    public void setSoloMaxPlayers(int value) { riftSoloMaxPlayers = Mth.clamp(value, 1, MAX_SOLO_PLAYERS); }

    public int getSoloVulnerableTicks() { return riftSoloVulnerableTicks; }

    public void setSoloVulnerableTicks(int value) {
        riftSoloVulnerableTicks = Mth.clamp(value, 0, MAX_SOLO_VULNERABLE_TICKS);
    }

    public int getSoloVulnerablePercent() { return riftSoloVulnerablePercent; }

    public void setSoloVulnerablePercent(int value) {
        riftSoloVulnerablePercent = Mth.clamp(value, MIN_SOLO_VULNERABLE_PERCENT, MAX_SOLO_VULNERABLE_PERCENT);
    }

    public int getGroupDamagePercent() { return riftGroupDamagePercent; }

    public void setGroupDamagePercent(int value) {
        riftGroupDamagePercent = Mth.clamp(value, 0, MAX_GROUP_DAMAGE_PERCENT);
    }

    public long getAbilities() { return riftAbilities; }

    public void setAbilities(long value) { riftAbilities = value & MEANWHILE_ALL; }

    /** Whether the boss may cast this ability while a rift runs; never a second rift, a summon or copies. */
    public boolean castsMeanwhile(int kind) {
        return kind >= 0 && kind < BossAbilityKind.COUNT && (riftAbilities & (1L << kind)) != 0;
    }

    public void setCastsMeanwhile(int kind, boolean value) {
        if (kind < 0 || kind >= BossAbilityKind.COUNT) {
            return;
        }
        setAbilities(value ? riftAbilities | (1L << kind) : riftAbilities & ~(1L << kind));
    }

    public int getArenaMode() { return riftArenaMode; }

    public void setArenaMode(int value) { riftArenaMode = Mth.clamp(value, ARENA_BUILT, ARENA_PREBUILT); }

    /** Whether the platform was laid by hand, so the rift builds nothing and goes to its spot. */
    public boolean isPrebuilt() { return riftArenaMode == ARENA_PREBUILT; }

    public int getSlot() { return riftSlot; }

    public void setSlot(int value) { riftSlot = Mth.clamp(value, 0, MAX_SLOT); }

    public int getArenaX() { return riftArenaX; }

    public void setArenaX(int value) { riftArenaX = Mth.clamp(value, -MAX_COORDINATE, MAX_COORDINATE); }

    public int getArenaY() { return riftArenaY; }

    public void setArenaY(int value) { riftArenaY = Mth.clamp(value, MIN_ARENA_Y, MAX_ARENA_Y); }

    public int getArenaZ() { return riftArenaZ; }

    public void setArenaZ(int value) { riftArenaZ = Mth.clamp(value, -MAX_COORDINATE, MAX_COORDINATE); }

    public int getPlatformY() { return riftPlatformY; }

    public void setPlatformY(int value) { riftPlatformY = Mth.clamp(value, MIN_PLATFORM_Y, MAX_PLATFORM_Y); }

    public int getPlatformRadius() { return riftPlatformRadius; }

    public void setPlatformRadius(int value) {
        riftPlatformRadius = Mth.clamp(value, MIN_PLATFORM_RADIUS, MAX_PLATFORM_RADIUS);
    }

    public String getPlatformBlock() { return riftPlatformBlock; }

    public void setPlatformBlock(String value) { riftPlatformBlock = blockOrDefault(value, DEFAULT_PLATFORM_BLOCK); }

    public String getWallBlock() { return riftWallBlock; }

    public void setWallBlock(String value) { riftWallBlock = blockOrDefault(value, DEFAULT_WALL_BLOCK); }

    public String getLightBlock() { return riftLightBlock; }

    public void setLightBlock(String value) { riftLightBlock = blockOrDefault(value, DEFAULT_LIGHT_BLOCK); }

    public int getWallHeight() { return riftWallHeight; }

    public void setWallHeight(int value) { riftWallHeight = Mth.clamp(value, 0, MAX_WALL_HEIGHT); }

    public int getLightSpacing() { return riftLightSpacing; }

    public void setLightSpacing(int value) { riftLightSpacing = Mth.clamp(value, 0, MAX_LIGHT_SPACING); }

    public boolean isPlatformRoof() { return riftPlatformRoof; }

    public void setPlatformRoof(boolean value) { riftPlatformRoof = value; }

    public int getLeashRadius() { return riftLeashRadius; }

    public void setLeashRadius(int value) { riftLeashRadius = Mth.clamp(value, 0, MAX_LEASH_RADIUS); }

    public int getFallGuardDepth() { return riftFallGuardDepth; }

    public void setFallGuardDepth(int value) { riftFallGuardDepth = Mth.clamp(value, 0, MAX_FALL_GUARD_DEPTH); }

    public String getMinionCloneName() { return riftMinionCloneName; }

    public void setMinionCloneName(String value) { riftMinionCloneName = clean(value); }

    public int getMinionCloneTab() { return riftMinionCloneTab; }

    public void setMinionCloneTab(int value) { riftMinionCloneTab = Mth.clamp(value, 1, MAX_CLONE_TAB); }

    public int getMinionCount() { return riftMinionCount; }

    public void setMinionCount(int value) { riftMinionCount = Mth.clamp(value, 1, MAX_MINION_COUNT); }

    public BossMinionSpawnList getMinionPoints() { return riftMinionPoints; }

    public int getMinionRadius() { return riftMinionRadius; }

    public void setMinionRadius(int value) { riftMinionRadius = Mth.clamp(value, 1, MAX_MINION_RADIUS); }

    public boolean isMinionRemoveOnEnd() { return riftMinionRemoveOnEnd; }

    public void setMinionRemoveOnEnd(boolean value) { riftMinionRemoveOnEnd = value; }

    public boolean isFailRage() { return riftFailRage; }

    public void setFailRage(boolean value) { riftFailRage = value; }

    public int getFailArenaDamage() { return riftFailArenaDamage; }

    public void setFailArenaDamage(int value) { riftFailArenaDamage = Mth.clamp(value, 0, MAX_FAIL_ARENA_DAMAGE); }

    public int getFailArenaRadius() { return riftFailArenaRadius; }

    public void setFailArenaRadius(int value) { riftFailArenaRadius = Mth.clamp(value, 1, MAX_FAIL_ARENA_RADIUS); }

    public BossEffectSet getFailEffects() { return riftFailEffects; }

    public int getFailHealPercent() { return riftFailHealPercent; }

    public void setFailHealPercent(int value) { riftFailHealPercent = Mth.clamp(value, 0, MAX_FAIL_HEAL_PERCENT); }

    /** The tint on a taken player's screen, as 0xRRGGBB. */
    public int getTintColor() { return riftTintColor; }

    public void setTintColor(int value) { riftTintColor = Mth.clamp(value, 0, MAX_COLOR); }

    public int getTintAlpha() { return riftTintAlpha; }

    public void setTintAlpha(int value) { riftTintAlpha = Mth.clamp(value, 0, MAX_TINT_ALPHA); }

    public int getTintPulseTicks() { return riftTintPulseTicks; }

    public void setTintPulseTicks(int value) { riftTintPulseTicks = Mth.clamp(value, 0, MAX_TINT_PULSE_TICKS); }

    public int getFogColor() { return riftFogColor; }

    public void setFogColor(int value) { riftFogColor = Mth.clamp(value, 0, MAX_COLOR); }

    public int getFogDistance() { return riftFogDistance; }

    public void setFogDistance(int value) { riftFogDistance = Mth.clamp(value, 0, MAX_FOG_DISTANCE); }

    /** What a taken player is given on the way in. */
    public BossEffectSet getEffects() { return riftEffects; }

    public int getLoopIntervalTicks() { return riftLoopIntervalTicks; }

    public void setLoopIntervalTicks(int value) {
        riftLoopIntervalTicks = Mth.clamp(value, MIN_CUE_INTERVAL_TICKS, MAX_CUE_INTERVAL_TICKS);
    }

    public int getAmbientIntervalTicks() { return riftAmbientIntervalTicks; }

    public void setAmbientIntervalTicks(int value) {
        riftAmbientIntervalTicks = Mth.clamp(value, MIN_CUE_INTERVAL_TICKS, MAX_CUE_INTERVAL_TICKS);
    }

    public BossSoundCue getCutSound() { return riftCutSound; }

    public BossSoundCue getEnterSound() { return riftEnterSound; }

    public BossSoundCue getExitSound() { return riftExitSound; }

    public BossSoundCue getLoopSound() { return riftLoopSound; }

    public BossSoundCue getSuccessSound() { return riftSuccessSound; }

    public BossSoundCue getFailSound() { return riftFailSound; }

    public BossParticleCue getCutParticles() { return riftCutParticles; }

    public BossParticleCue getEnterParticles() { return riftEnterParticles; }

    public BossParticleCue getAmbientParticles() { return riftAmbientParticles; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return riftCastSpot; }

    /**
     * A colour as the screens write it: six hex digits, no prefix.
     */
    public static String hex(int color) {
        return String.format(Locale.ROOT, "%06X", Mth.clamp(color, 0, MAX_COLOR));
    }

    /**
     * A colour typed as hex, with or without a {@code #} or {@code 0x} in front, or
     * {@code fallback} for text that is not one. Never throws: a half-typed field is read on every
     * change of focus.
     */
    public static int parseHex(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        String digits = text.trim();
        if (digits.startsWith("#")) {
            digits = digits.substring(1);
        } else if (digits.startsWith("0x") || digits.startsWith("0X")) {
            digits = digits.substring(2);
        }
        if (digits.isEmpty() || digits.length() > 6) {
            return fallback;
        }
        // Digit by digit rather than trusting parseInt, which takes a sign in front as well.
        for (int i = 0; i < digits.length(); i++) {
            if (Character.digit(digits.charAt(i), 16) < 0) {
                return fallback;
            }
        }
        try {
            return Mth.clamp(Integer.parseInt(digits, 16), 0, MAX_COLOR);
        } catch (NumberFormatException notHex) {
            return fallback;
        }
    }

    /**
     * A copy of these settings as they stand, for a rift to hold while it runs: the phase's own
     * object goes on being edited, and a rift opened under one set of rules closes under it.
     */
    public BossRiftSettings copy() {
        CompoundTag tag = new CompoundTag();
        writeToNBT(tag);
        BossRiftSettings copy = new BossRiftSettings();
        copy.readFromNBT(tag);
        return copy;
    }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("RiftEnabled", riftEnabled);
        tag.putString("RiftAnimation", riftAnimation);
        tag.putInt("RiftActionDelayTicks", riftActionDelayTicks);
        tag.putInt("RiftCooldownTicks", riftCooldownTicks);
        tag.putInt("RiftTargetMode", riftTargetMode);
        tag.putInt("RiftTargetCount", riftTargetCount);
        tag.putInt("RiftExitMode", riftExitMode);
        tag.putInt("RiftTimeLimitTicks", riftTimeLimitTicks);
        tag.putBoolean("RiftFailOnDeath", riftFailOnDeath);
        tag.putInt("RiftSoloMaxPlayers", riftSoloMaxPlayers);
        tag.putInt("RiftSoloVulnerableTicks", riftSoloVulnerableTicks);
        tag.putInt("RiftSoloVulnerablePercent", riftSoloVulnerablePercent);
        tag.putInt("RiftGroupDamagePercent", riftGroupDamagePercent);
        tag.putLong("RiftAbilities", riftAbilities);
        tag.putInt("RiftArenaMode", riftArenaMode);
        tag.putInt("RiftSlot", riftSlot);
        tag.putInt("RiftArenaX", riftArenaX);
        tag.putInt("RiftArenaY", riftArenaY);
        tag.putInt("RiftArenaZ", riftArenaZ);
        tag.putInt("RiftPlatformY", riftPlatformY);
        tag.putInt("RiftPlatformRadius", riftPlatformRadius);
        tag.putString("RiftPlatformBlock", riftPlatformBlock);
        tag.putString("RiftWallBlock", riftWallBlock);
        tag.putString("RiftLightBlock", riftLightBlock);
        tag.putInt("RiftWallHeight", riftWallHeight);
        tag.putInt("RiftLightSpacing", riftLightSpacing);
        tag.putBoolean("RiftPlatformRoof", riftPlatformRoof);
        tag.putInt("RiftLeashRadius", riftLeashRadius);
        tag.putInt("RiftFallGuardDepth", riftFallGuardDepth);
        tag.putString("RiftMinionCloneName", riftMinionCloneName);
        tag.putInt("RiftMinionCloneTab", riftMinionCloneTab);
        tag.putInt("RiftMinionCount", riftMinionCount);
        tag.put("RiftMinionPoints", riftMinionPoints.writeToNBT());
        tag.putInt("RiftMinionRadius", riftMinionRadius);
        tag.putBoolean("RiftMinionRemoveOnEnd", riftMinionRemoveOnEnd);
        tag.putBoolean("RiftFailRage", riftFailRage);
        tag.putInt("RiftFailArenaDamage", riftFailArenaDamage);
        tag.putInt("RiftFailArenaRadius", riftFailArenaRadius);
        tag.put("RiftFailEffects", riftFailEffects.writeToNBT());
        tag.putInt("RiftFailHealPercent", riftFailHealPercent);
        tag.putInt("RiftTintColor", riftTintColor);
        tag.putInt("RiftTintAlpha", riftTintAlpha);
        tag.putInt("RiftTintPulseTicks", riftTintPulseTicks);
        tag.putInt("RiftFogColor", riftFogColor);
        tag.putInt("RiftFogDistance", riftFogDistance);
        tag.put("RiftEffects", riftEffects.writeToNBT());
        tag.putInt("RiftLoopIntervalTicks", riftLoopIntervalTicks);
        tag.putInt("RiftAmbientIntervalTicks", riftAmbientIntervalTicks);
        riftCutSound.writeToNBT(tag, "RiftCutSound");
        riftEnterSound.writeToNBT(tag, "RiftEnterSound");
        riftExitSound.writeToNBT(tag, "RiftExitSound");
        riftLoopSound.writeToNBT(tag, "RiftLoopSound");
        riftSuccessSound.writeToNBT(tag, "RiftSuccessSound");
        riftFailSound.writeToNBT(tag, "RiftFailSound");
        riftCutParticles.writeToNBT(tag, "RiftCutParticles");
        riftEnterParticles.writeToNBT(tag, "RiftEnterParticles");
        riftAmbientParticles.writeToNBT(tag, "RiftAmbientParticles");
        riftCastSpot.writeToNBT(tag, "Rift");
    }

    void readFromNBT(CompoundTag tag) {
        riftEnabled = tag.getBoolean("RiftEnabled");
        riftAnimation = clean(tag.getString("RiftAnimation"));
        riftActionDelayTicks = value(tag, "RiftActionDelayTicks", 30, 0, 1200);
        riftCooldownTicks = value(tag, "RiftCooldownTicks", 1200, 1, 12000);
        riftTargetMode = value(tag, "RiftTargetMode", BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        riftTargetCount = value(tag, "RiftTargetCount", 1, 1, MAX_TARGET_COUNT);
        riftExitMode = value(tag, "RiftExitMode", EXIT_SURVIVE, EXIT_SURVIVE, EXIT_BOTH);
        riftTimeLimitTicks = value(tag, "RiftTimeLimitTicks", 1200, MIN_TIME_LIMIT_TICKS, MAX_TIME_LIMIT_TICKS);
        // The two that default to on: an absent key is a boss saved before they existed.
        riftFailOnDeath = !tag.contains("RiftFailOnDeath") || tag.getBoolean("RiftFailOnDeath");
        riftSoloMaxPlayers = value(tag, "RiftSoloMaxPlayers", 1, 1, MAX_SOLO_PLAYERS);
        riftSoloVulnerableTicks = value(tag, "RiftSoloVulnerableTicks", 200, 0, MAX_SOLO_VULNERABLE_TICKS);
        riftSoloVulnerablePercent = value(tag, "RiftSoloVulnerablePercent", 150,
                MIN_SOLO_VULNERABLE_PERCENT, MAX_SOLO_VULNERABLE_PERCENT);
        riftGroupDamagePercent = value(tag, "RiftGroupDamagePercent", 50, 0, MAX_GROUP_DAMAGE_PERCENT);
        riftAbilities = tag.getLong("RiftAbilities") & MEANWHILE_ALL;
        riftArenaMode = value(tag, "RiftArenaMode", ARENA_BUILT, ARENA_BUILT, ARENA_PREBUILT);
        riftSlot = value(tag, "RiftSlot", 0, 0, MAX_SLOT);
        riftArenaX = value(tag, "RiftArenaX", 0, -MAX_COORDINATE, MAX_COORDINATE);
        riftArenaY = value(tag, "RiftArenaY", 64, MIN_ARENA_Y, MAX_ARENA_Y);
        riftArenaZ = value(tag, "RiftArenaZ", 0, -MAX_COORDINATE, MAX_COORDINATE);
        riftPlatformY = value(tag, "RiftPlatformY", 64, MIN_PLATFORM_Y, MAX_PLATFORM_Y);
        riftPlatformRadius = value(tag, "RiftPlatformRadius", 16, MIN_PLATFORM_RADIUS, MAX_PLATFORM_RADIUS);
        riftPlatformBlock = tag.contains("RiftPlatformBlock")
                ? blockOrDefault(tag.getString("RiftPlatformBlock"), DEFAULT_PLATFORM_BLOCK) : DEFAULT_PLATFORM_BLOCK;
        riftWallBlock = tag.contains("RiftWallBlock")
                ? blockOrDefault(tag.getString("RiftWallBlock"), DEFAULT_WALL_BLOCK) : DEFAULT_WALL_BLOCK;
        riftLightBlock = tag.contains("RiftLightBlock")
                ? blockOrDefault(tag.getString("RiftLightBlock"), DEFAULT_LIGHT_BLOCK) : DEFAULT_LIGHT_BLOCK;
        riftWallHeight = value(tag, "RiftWallHeight", 4, 0, MAX_WALL_HEIGHT);
        riftLightSpacing = value(tag, "RiftLightSpacing", 6, 0, MAX_LIGHT_SPACING);
        riftPlatformRoof = tag.getBoolean("RiftPlatformRoof");
        riftLeashRadius = value(tag, "RiftLeashRadius", 0, 0, MAX_LEASH_RADIUS);
        riftFallGuardDepth = value(tag, "RiftFallGuardDepth", 8, 0, MAX_FALL_GUARD_DEPTH);
        riftMinionCloneName = clean(tag.getString("RiftMinionCloneName"));
        riftMinionCloneTab = value(tag, "RiftMinionCloneTab", 1, 1, MAX_CLONE_TAB);
        riftMinionCount = value(tag, "RiftMinionCount", 3, 1, MAX_MINION_COUNT);
        riftMinionPoints.readFromNBT(tag, "RiftMinionPoints");
        riftMinionRadius = value(tag, "RiftMinionRadius", 6, 1, MAX_MINION_RADIUS);
        riftMinionRemoveOnEnd = !tag.contains("RiftMinionRemoveOnEnd") || tag.getBoolean("RiftMinionRemoveOnEnd");
        riftFailRage = tag.getBoolean("RiftFailRage");
        riftFailArenaDamage = value(tag, "RiftFailArenaDamage", 0, 0, MAX_FAIL_ARENA_DAMAGE);
        riftFailArenaRadius = value(tag, "RiftFailArenaRadius", 32, 1, MAX_FAIL_ARENA_RADIUS);
        riftFailEffects.readFromNBT(tag, "RiftFailEffects");
        riftFailHealPercent = value(tag, "RiftFailHealPercent", 0, 0, MAX_FAIL_HEAL_PERCENT);
        riftTintColor = value(tag, "RiftTintColor", DEFAULT_TINT_COLOR, 0, MAX_COLOR);
        riftTintAlpha = value(tag, "RiftTintAlpha", 35, 0, MAX_TINT_ALPHA);
        riftTintPulseTicks = value(tag, "RiftTintPulseTicks", 40, 0, MAX_TINT_PULSE_TICKS);
        riftFogColor = value(tag, "RiftFogColor", DEFAULT_FOG_COLOR, 0, MAX_COLOR);
        riftFogDistance = value(tag, "RiftFogDistance", 24, 0, MAX_FOG_DISTANCE);
        riftEffects.readFromNBT(tag, "RiftEffects");
        riftLoopIntervalTicks = value(tag, "RiftLoopIntervalTicks", 60, MIN_CUE_INTERVAL_TICKS, MAX_CUE_INTERVAL_TICKS);
        riftAmbientIntervalTicks = value(tag, "RiftAmbientIntervalTicks", 20,
                MIN_CUE_INTERVAL_TICKS, MAX_CUE_INTERVAL_TICKS);
        riftCutSound.readFromNBT(tag, "RiftCutSound");
        riftEnterSound.readFromNBT(tag, "RiftEnterSound");
        riftExitSound.readFromNBT(tag, "RiftExitSound");
        riftLoopSound.readFromNBT(tag, "RiftLoopSound");
        riftSuccessSound.readFromNBT(tag, "RiftSuccessSound");
        riftFailSound.readFromNBT(tag, "RiftFailSound");
        riftCutParticles.readFromNBT(tag, "RiftCutParticles");
        riftEnterParticles.readFromNBT(tag, "RiftEnterParticles");
        riftAmbientParticles.readFromNBT(tag, "RiftAmbientParticles");
        riftCastSpot.readFromNBT(tag, "Rift");
    }

    /** A block id kept as typed, or the default for an empty one; whether it names a block is the builder's check. */
    private static String blockOrDefault(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private static long maskOf(int[] abilities) {
        long mask = 0L;
        for (int ability : abilities) {
            mask |= 1L << ability;
        }
        return mask;
    }
}
