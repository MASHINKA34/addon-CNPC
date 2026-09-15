package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.util.Arrays;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The shadow copies: how many of the boss stand up beside it, with what health and where,
 * what they cast, whether the boss keeps trading places with them, and how they end.
 *
 * <p>One phase of one boss holds one of these, reached through {@link BossPhaseData#shadow()}.
 * It owns its own save format under the {@code Shadow} prefix; a boss saved before the copies
 * existed carries none of these keys and reads back with the ability switched off.</p>
 *
 * <p>The three endings are independent switches, as the builder asked: the swapping runs
 * while the copies live whatever the finale is, and the finale - nothing, the boss drawing
 * the copies back in, or the copies going off - comes either when their time runs out or on
 * the next cast of this ability, whichever the phase says.</p>
 */
public final class BossShadowSettings {

    public static final int MAX_COUNT = 8;
    /** The copy's maximum health is this share of the boss' own. */
    public static final int HEALTH_PERCENT = 0;
    /** The copy's maximum health is a number of its own. */
    public static final int HEALTH_VALUE = 1;
    public static final String[] HEALTH_LABELS = {
            "cnpcgeckoaddon.boss.shadow_health.percent",
            "cnpcgeckoaddon.boss.shadow_health.value"
    };
    public static final int MAX_HEALTH_VALUE = 100000;
    public static final int MAX_LIFETIME_TICKS = 6000;
    public static final int MAX_SPAWN_RADIUS = 32;
    public static final int MIN_SWAP_INTERVAL_TICKS = 20;
    public static final int MAX_SWAP_INTERVAL_TICKS = 1200;
    /** The copies simply go when they end. */
    public static final int FINALE_NONE = 0;
    /** The boss draws every copy back into itself: health for each, and a stack of damage. */
    public static final int FINALE_ABSORB = 1;
    /** Every copy goes off where it stands. */
    public static final int FINALE_BLAST = 2;
    public static final String[] FINALE_LABELS = {
            "cnpcgeckoaddon.boss.shadow_finale.none",
            "cnpcgeckoaddon.boss.shadow_finale.absorb",
            "cnpcgeckoaddon.boss.shadow_finale.blast"
    };
    /** The end comes when the copies' time runs out. */
    public static final int FINALE_AT_LIFETIME = 0;
    /** The end comes on the next cast of this ability, which ends the copies instead of adding to them. */
    public static final int FINALE_AT_RECAST = 1;
    public static final String[] FINALE_AT_LABELS = {
            "cnpcgeckoaddon.boss.shadow_finale_at.lifetime",
            "cnpcgeckoaddon.boss.shadow_finale_at.recast"
    };
    public static final int MAX_ABSORB_HEAL_PERCENT = 100;
    public static final int MAX_ABSORB_DAMAGE_PERCENT = 200;
    public static final int MAX_ABSORB_STACKS = 16;
    public static final int MIN_ABSORB_BUFF_TICKS = 20;
    public static final int MAX_ABSORB_BUFF_TICKS = 12000;
    public static final int MAX_BLAST_RADIUS = 16;
    public static final int MAX_BLAST_DAMAGE = 1000;
    public static final int MAX_BLAST_KNOCKBACK = 10;
    public static final int MIN_BLAST_VFX_TICKS = 1;
    public static final int MAX_BLAST_VFX_TICKS = 200;

    /**
     * The abilities a copy may be told to cast, in the order they are offered: everything the
     * rotation casts, less the two that would make copies of copies - the copies themselves,
     * and the minion summon.
     */
    public static final int[] COPY_ABILITIES = Arrays.stream(BossAbilityKind.COMBO_ABILITIES)
            .filter(kind -> kind != BossAbilityKind.SUMMON && kind != BossAbilityKind.SHADOW)
            .toArray();

    /** Every bit {@link #COPY_ABILITIES} owns; any other bit in the mask is never read. */
    public static final long COPY_ALL = maskOf(COPY_ABILITIES);

    private boolean shadowEnabled;
    private String shadowAnimation = "";
    private int shadowActionDelayTicks = 20;
    private int shadowCooldownTicks = 600;
    private int shadowCount = 2;
    private int shadowHealthMode = HEALTH_PERCENT;
    private int shadowHealthPercent = 20;
    private int shadowHealthValue = 100;
    /** Nought is for good: until they are killed, taken back or the phase ends. */
    private int shadowLifetimeTicks = 600;
    /** Which abilities the copies cast, one bit per {@link BossAbilityKind}; nothing by default. */
    private long shadowAbilities;
    /** Where the copies stand up; the clone name a point may carry is ignored, there being no clone. */
    private final BossMinionSpawnList shadowPoints = new BossMinionSpawnList();
    /** The ring round the boss the copies stand on when the phase names no points. */
    private int shadowSpawnRadius = 4;
    private boolean shadowHideBossBar = true;
    private boolean shadowSwapEnabled;
    private int shadowSwapIntervalTicks = 100;
    private boolean shadowSwapOnlyIdle = true;
    private int shadowFinale = FINALE_NONE;
    private int shadowFinaleAt = FINALE_AT_LIFETIME;
    private int shadowAbsorbHealPercent = 10;
    private int shadowAbsorbDamagePercent = 15;
    private int shadowAbsorbMaxStacks = 4;
    private int shadowAbsorbBuffTicks = 600;
    /** The styleless default draws no beam at all. */
    private String shadowAbsorbBeam = HookCordStyles.PARTICLES;
    private int shadowBlastRadius = 4;
    private int shadowBlastDamage = 12;
    private int shadowBlastKnockback = 2;
    private final BossEffectSet shadowBlastEffects = new BossEffectSet();
    private String shadowBlastVfx = AreaVfxStyles.NONE;
    private int shadowBlastVfxTicks = 20;
    private boolean shadowClearOnPhaseChange = true;
    private final BossSoundCue shadowSpawnSound =
            new BossSoundCue("minecraft:entity.illusioner.mirror_move", 1.0F, 0.8F);
    private final BossParticleCue shadowSpawnParticles = new BossParticleCue("minecraft:portal", 24);
    private final BossSoundCue shadowAbsorbSound =
            new BossSoundCue("minecraft:entity.illusioner.cast_spell", 1.0F, 0.7F);
    private final BossParticleCue shadowAbsorbParticles = new BossParticleCue("minecraft:soul", 16);
    private final BossSoundCue shadowBlastSound = new BossSoundCue("minecraft:entity.generic.explode", 2.0F, 1.0F);
    private final BossParticleCue shadowBlastParticles = new BossParticleCue("minecraft:explosion", 1);
    private final BossParticleCue shadowVanishParticles = new BossParticleCue("minecraft:poof", 12);
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot shadowCastSpot = new BossCastSpot();

    public boolean isEnabled() { return shadowEnabled; }

    public void setEnabled(boolean value) { shadowEnabled = value; }

    /** The wind-up: played before the copies stand up, and before the finale on a recast. */
    public String getAnimation() { return shadowAnimation; }

    public void setAnimation(String value) { shadowAnimation = clean(value); }

    public int getActionDelayTicks() { return shadowActionDelayTicks; }

    public void setActionDelayTicks(int value) { shadowActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return shadowCooldownTicks; }

    public void setCooldownTicks(int value) { shadowCooldownTicks = Mth.clamp(value, 1, 12000); }

    /** Copies per cast; there are never more alive, since a cast with copies standing adds none. */
    public int getCount() { return shadowCount; }

    public void setCount(int value) { shadowCount = Mth.clamp(value, 1, MAX_COUNT); }

    public int getHealthMode() { return shadowHealthMode; }

    public void setHealthMode(int value) { shadowHealthMode = Mth.clamp(value, HEALTH_PERCENT, HEALTH_VALUE); }

    public int getHealthPercent() { return shadowHealthPercent; }

    public void setHealthPercent(int value) { shadowHealthPercent = Mth.clamp(value, 1, 100); }

    public int getHealthValue() { return shadowHealthValue; }

    public void setHealthValue(int value) { shadowHealthValue = Mth.clamp(value, 1, MAX_HEALTH_VALUE); }

    /** The maximum health one copy stands up with, from the boss' own maximum and the mode. */
    public int copyMaxHealth(float bossMaxHealth) {
        if (shadowHealthMode == HEALTH_VALUE) {
            return shadowHealthValue;
        }
        return Math.max(1, Math.round(bossMaxHealth * shadowHealthPercent / 100.0F));
    }

    public int getLifetimeTicks() { return shadowLifetimeTicks; }

    public void setLifetimeTicks(int value) { shadowLifetimeTicks = Mth.clamp(value, 0, MAX_LIFETIME_TICKS); }

    public long getAbilities() { return shadowAbilities; }

    public void setAbilities(long value) { shadowAbilities = value & COPY_ALL; }

    /** Whether the copies cast this ability; never for the two that are kept off the list. */
    public boolean castsAbility(int kind) {
        return kind >= 0 && kind < BossAbilityKind.COUNT && (shadowAbilities & (1L << kind)) != 0;
    }

    public void setCastsAbility(int kind, boolean value) {
        if (kind < 0 || kind >= BossAbilityKind.COUNT) {
            return;
        }
        setAbilities(value ? shadowAbilities | (1L << kind) : shadowAbilities & ~(1L << kind));
    }

    public BossMinionSpawnList getPoints() { return shadowPoints; }

    public int getSpawnRadius() { return shadowSpawnRadius; }

    public void setSpawnRadius(int value) { shadowSpawnRadius = Mth.clamp(value, 1, MAX_SPAWN_RADIUS); }

    /** Whether the boss' bar is taken down while copies stand: a bar over one head gives the real one away. */
    public boolean isHideBossBar() { return shadowHideBossBar; }

    public void setHideBossBar(boolean value) { shadowHideBossBar = value; }

    public boolean isSwapEnabled() { return shadowSwapEnabled; }

    public void setSwapEnabled(boolean value) { shadowSwapEnabled = value; }

    public int getSwapIntervalTicks() { return shadowSwapIntervalTicks; }

    public void setSwapIntervalTicks(int value) {
        shadowSwapIntervalTicks = Mth.clamp(value, MIN_SWAP_INTERVAL_TICKS, MAX_SWAP_INTERVAL_TICKS);
    }

    /** Whether a swap waits for both the boss and the copy to be between casts and not moving under one. */
    public boolean isSwapOnlyIdle() { return shadowSwapOnlyIdle; }

    public void setSwapOnlyIdle(boolean value) { shadowSwapOnlyIdle = value; }

    public int getFinale() { return shadowFinale; }

    public void setFinale(int value) { shadowFinale = Mth.clamp(value, FINALE_NONE, FINALE_BLAST); }

    public int getFinaleAt() { return shadowFinaleAt; }

    public void setFinaleAt(int value) { shadowFinaleAt = Mth.clamp(value, FINALE_AT_LIFETIME, FINALE_AT_RECAST); }

    /** Whether a cast while copies stand ends them rather than doing nothing. */
    public boolean isFinaleOnRecast() { return shadowFinaleAt == FINALE_AT_RECAST; }

    /** Whether the copies' time running out is the finale rather than a plain vanishing. */
    public boolean isFinaleOnLifetime() { return shadowFinaleAt == FINALE_AT_LIFETIME; }

    /** Per copy taken back, as a share of the boss' maximum health. */
    public int getAbsorbHealPercent() { return shadowAbsorbHealPercent; }

    public void setAbsorbHealPercent(int value) {
        shadowAbsorbHealPercent = Mth.clamp(value, 0, MAX_ABSORB_HEAL_PERCENT);
    }

    /** Per copy taken back, on top of everything the boss hits for, per stack. */
    public int getAbsorbDamagePercent() { return shadowAbsorbDamagePercent; }

    public void setAbsorbDamagePercent(int value) {
        shadowAbsorbDamagePercent = Mth.clamp(value, 0, MAX_ABSORB_DAMAGE_PERCENT);
    }

    public int getAbsorbMaxStacks() { return shadowAbsorbMaxStacks; }

    public void setAbsorbMaxStacks(int value) { shadowAbsorbMaxStacks = Mth.clamp(value, 1, MAX_ABSORB_STACKS); }

    /** How long the stacks last, counted from the last one added. */
    public int getAbsorbBuffTicks() { return shadowAbsorbBuffTicks; }

    public void setAbsorbBuffTicks(int value) {
        shadowAbsorbBuffTicks = Mth.clamp(value, MIN_ABSORB_BUFF_TICKS, MAX_ABSORB_BUFF_TICKS);
    }

    /** The cord drawn from a copy to the boss as it is taken back; the styleless default draws none. */
    public String getAbsorbBeam() { return shadowAbsorbBeam; }

    public void setAbsorbBeam(String value) { shadowAbsorbBeam = HookCordStyles.normalize(value); }

    public int getBlastRadius() { return shadowBlastRadius; }

    public void setBlastRadius(int value) { shadowBlastRadius = Mth.clamp(value, 1, MAX_BLAST_RADIUS); }

    public int getBlastDamage() { return shadowBlastDamage; }

    public void setBlastDamage(int value) { shadowBlastDamage = Mth.clamp(value, 0, MAX_BLAST_DAMAGE); }

    /** Away from the copy, the way the ground slam shoves. */
    public int getBlastKnockback() { return shadowBlastKnockback; }

    public void setBlastKnockback(int value) { shadowBlastKnockback = Mth.clamp(value, 0, MAX_BLAST_KNOCKBACK); }

    public BossEffectSet getBlastEffects() { return shadowBlastEffects; }

    public String getBlastVfx() { return shadowBlastVfx; }

    public void setBlastVfx(String value) { shadowBlastVfx = AreaVfxStyles.normalize(value); }

    public int getBlastVfxTicks() { return shadowBlastVfxTicks; }

    public void setBlastVfxTicks(int value) {
        shadowBlastVfxTicks = Mth.clamp(value, MIN_BLAST_VFX_TICKS, MAX_BLAST_VFX_TICKS);
    }

    /** Whether the copies go with the phase that cast them. */
    public boolean isClearOnPhaseChange() { return shadowClearOnPhaseChange; }

    public void setClearOnPhaseChange(boolean value) { shadowClearOnPhaseChange = value; }

    public BossSoundCue getSpawnSound() { return shadowSpawnSound; }

    public BossParticleCue getSpawnParticles() { return shadowSpawnParticles; }

    public BossSoundCue getAbsorbSound() { return shadowAbsorbSound; }

    public BossParticleCue getAbsorbParticles() { return shadowAbsorbParticles; }

    public BossSoundCue getBlastSound() { return shadowBlastSound; }

    public BossParticleCue getBlastParticles() { return shadowBlastParticles; }

    public BossParticleCue getVanishParticles() { return shadowVanishParticles; }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public BossCastSpot castSpot() { return shadowCastSpot; }

    /**
     * A copy of these settings as they stand, for the runtime to hold while its copies live:
     * the phase's own object goes on being edited, and copies cast under one set of rules
     * should end under the same set.
     */
    public BossShadowSettings copy() {
        CompoundTag tag = new CompoundTag();
        writeToNBT(tag);
        BossShadowSettings copy = new BossShadowSettings();
        copy.readFromNBT(tag);
        return copy;
    }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("ShadowEnabled", shadowEnabled);
        tag.putString("ShadowAnimation", shadowAnimation);
        tag.putInt("ShadowActionDelayTicks", shadowActionDelayTicks);
        tag.putInt("ShadowCooldownTicks", shadowCooldownTicks);
        tag.putInt("ShadowCount", shadowCount);
        tag.putInt("ShadowHealthMode", shadowHealthMode);
        tag.putInt("ShadowHealthPercent", shadowHealthPercent);
        tag.putInt("ShadowHealthValue", shadowHealthValue);
        tag.putInt("ShadowLifetimeTicks", shadowLifetimeTicks);
        tag.putLong("ShadowAbilities", shadowAbilities);
        tag.put("ShadowPoints", shadowPoints.writeToNBT());
        tag.putInt("ShadowSpawnRadius", shadowSpawnRadius);
        tag.putBoolean("ShadowHideBossBar", shadowHideBossBar);
        tag.putBoolean("ShadowSwapEnabled", shadowSwapEnabled);
        tag.putInt("ShadowSwapIntervalTicks", shadowSwapIntervalTicks);
        tag.putBoolean("ShadowSwapOnlyIdle", shadowSwapOnlyIdle);
        tag.putInt("ShadowFinale", shadowFinale);
        tag.putInt("ShadowFinaleAt", shadowFinaleAt);
        tag.putInt("ShadowAbsorbHealPercent", shadowAbsorbHealPercent);
        tag.putInt("ShadowAbsorbDamagePercent", shadowAbsorbDamagePercent);
        tag.putInt("ShadowAbsorbMaxStacks", shadowAbsorbMaxStacks);
        tag.putInt("ShadowAbsorbBuffTicks", shadowAbsorbBuffTicks);
        tag.putString("ShadowAbsorbBeam", shadowAbsorbBeam);
        tag.putInt("ShadowBlastRadius", shadowBlastRadius);
        tag.putInt("ShadowBlastDamage", shadowBlastDamage);
        tag.putInt("ShadowBlastKnockback", shadowBlastKnockback);
        tag.put("ShadowBlastEffects", shadowBlastEffects.writeToNBT());
        tag.putString("ShadowBlastVfx", shadowBlastVfx);
        tag.putInt("ShadowBlastVfxTicks", shadowBlastVfxTicks);
        tag.putBoolean("ShadowClearOnPhaseChange", shadowClearOnPhaseChange);
        shadowSpawnSound.writeToNBT(tag, "ShadowSpawnSound");
        shadowSpawnParticles.writeToNBT(tag, "ShadowSpawnParticles");
        shadowAbsorbSound.writeToNBT(tag, "ShadowAbsorbSound");
        shadowAbsorbParticles.writeToNBT(tag, "ShadowAbsorbParticles");
        shadowBlastSound.writeToNBT(tag, "ShadowBlastSound");
        shadowBlastParticles.writeToNBT(tag, "ShadowBlastParticles");
        shadowVanishParticles.writeToNBT(tag, "ShadowVanishParticles");
        shadowCastSpot.writeToNBT(tag, "Shadow");
    }

    void readFromNBT(CompoundTag tag) {
        shadowEnabled = tag.getBoolean("ShadowEnabled");
        shadowAnimation = clean(tag.getString("ShadowAnimation"));
        shadowActionDelayTicks = value(tag, "ShadowActionDelayTicks", 20, 0, 1200);
        shadowCooldownTicks = value(tag, "ShadowCooldownTicks", 600, 1, 12000);
        shadowCount = value(tag, "ShadowCount", 2, 1, MAX_COUNT);
        shadowHealthMode = value(tag, "ShadowHealthMode", HEALTH_PERCENT, HEALTH_PERCENT, HEALTH_VALUE);
        shadowHealthPercent = value(tag, "ShadowHealthPercent", 20, 1, 100);
        shadowHealthValue = value(tag, "ShadowHealthValue", 100, 1, MAX_HEALTH_VALUE);
        shadowLifetimeTicks = value(tag, "ShadowLifetimeTicks", 600, 0, MAX_LIFETIME_TICKS);
        shadowAbilities = tag.getLong("ShadowAbilities") & COPY_ALL;
        shadowPoints.readFromNBT(tag, "ShadowPoints");
        shadowSpawnRadius = value(tag, "ShadowSpawnRadius", 4, 1, MAX_SPAWN_RADIUS);
        // The three that default to on: an absent key is a boss saved before they existed.
        shadowHideBossBar = !tag.contains("ShadowHideBossBar") || tag.getBoolean("ShadowHideBossBar");
        shadowSwapEnabled = tag.getBoolean("ShadowSwapEnabled");
        shadowSwapIntervalTicks = value(tag, "ShadowSwapIntervalTicks", 100,
                MIN_SWAP_INTERVAL_TICKS, MAX_SWAP_INTERVAL_TICKS);
        shadowSwapOnlyIdle = !tag.contains("ShadowSwapOnlyIdle") || tag.getBoolean("ShadowSwapOnlyIdle");
        shadowFinale = value(tag, "ShadowFinale", FINALE_NONE, FINALE_NONE, FINALE_BLAST);
        shadowFinaleAt = value(tag, "ShadowFinaleAt", FINALE_AT_LIFETIME, FINALE_AT_LIFETIME, FINALE_AT_RECAST);
        shadowAbsorbHealPercent = value(tag, "ShadowAbsorbHealPercent", 10, 0, MAX_ABSORB_HEAL_PERCENT);
        shadowAbsorbDamagePercent = value(tag, "ShadowAbsorbDamagePercent", 15, 0, MAX_ABSORB_DAMAGE_PERCENT);
        shadowAbsorbMaxStacks = value(tag, "ShadowAbsorbMaxStacks", 4, 1, MAX_ABSORB_STACKS);
        shadowAbsorbBuffTicks = value(tag, "ShadowAbsorbBuffTicks", 600, MIN_ABSORB_BUFF_TICKS, MAX_ABSORB_BUFF_TICKS);
        shadowAbsorbBeam = tag.contains("ShadowAbsorbBeam")
                ? HookCordStyles.normalize(tag.getString("ShadowAbsorbBeam")) : HookCordStyles.PARTICLES;
        shadowBlastRadius = value(tag, "ShadowBlastRadius", 4, 1, MAX_BLAST_RADIUS);
        shadowBlastDamage = value(tag, "ShadowBlastDamage", 12, 0, MAX_BLAST_DAMAGE);
        shadowBlastKnockback = value(tag, "ShadowBlastKnockback", 2, 0, MAX_BLAST_KNOCKBACK);
        shadowBlastEffects.readFromNBT(tag, "ShadowBlastEffects");
        shadowBlastVfx = tag.contains("ShadowBlastVfx")
                ? AreaVfxStyles.normalize(tag.getString("ShadowBlastVfx")) : AreaVfxStyles.NONE;
        shadowBlastVfxTicks = value(tag, "ShadowBlastVfxTicks", 20, MIN_BLAST_VFX_TICKS, MAX_BLAST_VFX_TICKS);
        shadowClearOnPhaseChange = !tag.contains("ShadowClearOnPhaseChange")
                || tag.getBoolean("ShadowClearOnPhaseChange");
        shadowSpawnSound.readFromNBT(tag, "ShadowSpawnSound");
        shadowSpawnParticles.readFromNBT(tag, "ShadowSpawnParticles");
        shadowAbsorbSound.readFromNBT(tag, "ShadowAbsorbSound");
        shadowAbsorbParticles.readFromNBT(tag, "ShadowAbsorbParticles");
        shadowBlastSound.readFromNBT(tag, "ShadowBlastSound");
        shadowBlastParticles.readFromNBT(tag, "ShadowBlastParticles");
        shadowVanishParticles.readFromNBT(tag, "ShadowVanishParticles");
        shadowCastSpot.readFromNBT(tag, "Shadow");
    }

    private static long maskOf(int[] abilities) {
        long mask = 0L;
        for (int ability : abilities) {
            mask |= 1L << ability;
        }
        return mask;
    }
}
