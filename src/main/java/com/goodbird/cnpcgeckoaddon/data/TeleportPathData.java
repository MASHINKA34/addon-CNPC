package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Complete per-NPC configuration for the stationary, multi-phase boss mechanic. */
public final class TeleportPathData {
    public static final int ORDER_SEQUENTIAL = 0;
    public static final int ORDER_PING_PONG = 1;
    public static final int ORDER_RANDOM = 2;

    public static final int MIN_PHASES = 1;
    public static final int MAX_PHASES = 8;

    public static final int MIN_RESET_TICKS = 20;
    public static final int MAX_RESET_TICKS = 12000;

    /** Minions are silently discarded, the way a despawning mob disappears. */
    public static final int MINION_REMOVAL_VANISH = 0;
    /** Minions are killed instead, so death animations, drops and kill scripts still run. */
    public static final int MINION_REMOVAL_KILL = 1;

    /** Particles and sound only - no damage and no block damage. */
    public static final int EXPLOSION_MODE_EFFECT = 0;
    /** A real blast that hurts entities but leaves the terrain alone. */
    public static final int EXPLOSION_MODE_DAMAGE = 1;
    /** Hurts entities and breaks blocks, honouring the mobGriefing game rule. */
    public static final int EXPLOSION_MODE_BLOCKS = 2;
    /** Hurts entities and always breaks blocks, like TNT. */
    public static final int EXPLOSION_MODE_BLOCKS_ALWAYS = 3;

    public static final String[] EXPLOSION_MODE_LABELS = {
            "cnpcgeckoaddon.boss.explosion_mode_effect",
            "cnpcgeckoaddon.boss.explosion_mode_damage",
            "cnpcgeckoaddon.boss.explosion_mode_blocks",
            "cnpcgeckoaddon.boss.explosion_mode_blocks_always"
    };

    public static final String[] MINION_REMOVAL_LABELS = {
            "cnpcgeckoaddon.boss.minions_removal_vanish",
            "cnpcgeckoaddon.boss.minions_removal_kill"
    };

    private static final String ENABLED_KEY = "GeckoTeleportPathEnabled";
    private static final String COMBAT_ONLY_KEY = "GeckoTeleportPathCombatOnly";
    private static final String STATIONARY_KEY = "GeckoBossStationary";
    private static final String ORDER_KEY = "GeckoTeleportPathOrder";
    private static final String SOUND_KEY = "GeckoTeleportPathSound";
    private static final String PHASE_THRESHOLD_KEY = "GeckoBossPhaseThreshold";
    private static final String TRANSITION_ANIMATION_KEY = "GeckoBossTransitionAnimation";
    private static final String TRANSITION_LOCK_KEY = "GeckoBossTransitionLockTicks";
    private static final String PHASE_ONE_KEY = "GeckoBossPhaseOne";
    private static final String PHASE_TWO_KEY = "GeckoBossPhaseTwo";
    private static final String PHASES_KEY = "GeckoBossPhases";
    private static final String TARGET_NEAREST_KEY = "GeckoBossTargetNearestPlayer";
    private static final String TARGET_RADIUS_KEY = "GeckoBossTargetRadius";
    private static final String TARGET_INTERVAL_KEY = "GeckoBossTargetInterval";
    private static final String TARGET_LOS_KEY = "GeckoBossTargetLineOfSight";
    private static final String TARGET_KEEP_KEY = "GeckoBossTargetKeepOutOfRange";
    private static final String MINIONS_DEATH_KEY = "GeckoBossMinionsClearOnDeath";
    private static final String MINIONS_RESET_KEY = "GeckoBossMinionsClearOnReset";
    private static final String MINIONS_REMOVAL_KEY = "GeckoBossMinionsRemovalMode";
    private static final String EXPLOSION_ENABLED_KEY = "GeckoBossExplosionEnabled";
    private static final String EXPLOSION_DELAY_KEY = "GeckoBossExplosionDelay";
    private static final String EXPLOSION_POWER_KEY = "GeckoBossExplosionPower";
    private static final String EXPLOSION_MODE_KEY = "GeckoBossExplosionMode";
    private static final String EXPLOSION_FIRE_KEY = "GeckoBossExplosionFire";
    private static final String BOSS_BAR_STYLE_KEY = "GeckoBossBarStyle";
    private static final String RESET_TICKS_KEY = "GeckoBossResetTicks";
    private static final String RESET_HEAL_KEY = "GeckoBossResetHeal";
    private static final String RESET_RETURN_KEY = "GeckoBossResetReturn";

    private boolean enabled;
    private boolean combatOnly = true;
    private boolean stationary;
    private int order = ORDER_SEQUENTIAL;
    private boolean playSound = true;
    private String phaseTransitionAnimation = "";
    private int phaseTransitionLockTicks = 40;

    /**
     * Ordered from the healthiest phase to the weakest. Index 0 always starts at full
     * health; every later phase takes over once the boss drops to its own threshold.
     */
    private final List<BossPhaseData> phases = new ArrayList<>();

    private boolean targetNearestPlayer;
    private int targetSearchRadius = 32;
    private int targetRecheckTicks = 20;
    private boolean targetRequiresLineOfSight;
    private boolean keepTargetOutOfRange;

    private int resetTicks = 100;
    private boolean resetHeal = true;
    private boolean resetReturn;

    private boolean clearMinionsOnDeath = true;
    private boolean clearMinionsOnReset = true;
    private int minionRemovalMode = MINION_REMOVAL_VANISH;

    private boolean explosionEnabled;
    private int explosionDelayTicks = 20;
    private int explosionPower = 4;
    private int explosionMode = EXPLOSION_MODE_DAMAGE;
    private boolean explosionFire;
    private String bossBarStyle = BossBarStyles.NONE;

    public TeleportPathData() {
        setPhaseCount(2);
    }

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putBoolean(ENABLED_KEY, enabled);
        tag.putBoolean(COMBAT_ONLY_KEY, combatOnly);
        tag.putBoolean(STATIONARY_KEY, stationary);
        tag.putInt(ORDER_KEY, order);
        tag.putBoolean(SOUND_KEY, playSound);
        tag.putString(TRANSITION_ANIMATION_KEY, phaseTransitionAnimation);
        tag.putInt(TRANSITION_LOCK_KEY, phaseTransitionLockTicks);

        ListTag list = new ListTag();
        for (BossPhaseData phase : phases) {
            list.add(phase.writeToNBT());
        }
        tag.put(PHASES_KEY, list);

        // Keep the pre-1.7 keys populated so downgrading only loses the extra phases
        // instead of resetting the whole boss.
        tag.put(PHASE_ONE_KEY, phases.get(0).writeToNBT());
        tag.put(PHASE_TWO_KEY, phases.get(Math.min(1, phases.size() - 1)).writeToNBT());
        tag.putInt(PHASE_THRESHOLD_KEY, getPhaseTwoHealthPercent());
        tag.putInt("GeckoTeleportPathMinDelay", phases.get(0).getTeleportMinDelayTicks());
        tag.putInt("GeckoTeleportPathMaxDelay", phases.get(0).getTeleportMaxDelayTicks());

        tag.putBoolean(TARGET_NEAREST_KEY, targetNearestPlayer);
        tag.putInt(TARGET_RADIUS_KEY, targetSearchRadius);
        tag.putInt(TARGET_INTERVAL_KEY, targetRecheckTicks);
        tag.putBoolean(TARGET_LOS_KEY, targetRequiresLineOfSight);
        tag.putBoolean(TARGET_KEEP_KEY, keepTargetOutOfRange);
        tag.putInt(RESET_TICKS_KEY, resetTicks);
        tag.putBoolean(RESET_HEAL_KEY, resetHeal);
        tag.putBoolean(RESET_RETURN_KEY, resetReturn);
        tag.putBoolean(MINIONS_DEATH_KEY, clearMinionsOnDeath);
        tag.putBoolean(MINIONS_RESET_KEY, clearMinionsOnReset);
        tag.putInt(MINIONS_REMOVAL_KEY, minionRemovalMode);
        tag.putBoolean(EXPLOSION_ENABLED_KEY, explosionEnabled);
        tag.putInt(EXPLOSION_DELAY_KEY, explosionDelayTicks);
        tag.putInt(EXPLOSION_POWER_KEY, explosionPower);
        tag.putInt(EXPLOSION_MODE_KEY, explosionMode);
        tag.putBoolean(EXPLOSION_FIRE_KEY, explosionFire);
        tag.putString(BOSS_BAR_STYLE_KEY, bossBarStyle);
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        enabled = tag.getBoolean(ENABLED_KEY);
        combatOnly = !tag.contains(COMBAT_ONLY_KEY) || tag.getBoolean(COMBAT_ONLY_KEY);
        stationary = tag.contains(STATIONARY_KEY) && tag.getBoolean(STATIONARY_KEY);
        order = tag.contains(ORDER_KEY)
                ? Mth.clamp(tag.getInt(ORDER_KEY), ORDER_SEQUENTIAL, ORDER_RANDOM)
                : ORDER_SEQUENTIAL;
        playSound = !tag.contains(SOUND_KEY) || tag.getBoolean(SOUND_KEY);
        phaseTransitionAnimation = tag.getString(TRANSITION_ANIMATION_KEY).trim();
        phaseTransitionLockTicks = tag.contains(TRANSITION_LOCK_KEY)
                ? Mth.clamp(tag.getInt(TRANSITION_LOCK_KEY), 0, 1200) : 40;

        readPhases(tag);

        targetNearestPlayer = tag.getBoolean(TARGET_NEAREST_KEY);
        targetSearchRadius = tag.contains(TARGET_RADIUS_KEY)
                ? Mth.clamp(tag.getInt(TARGET_RADIUS_KEY), 4, 128) : 32;
        targetRecheckTicks = tag.contains(TARGET_INTERVAL_KEY)
                ? Mth.clamp(tag.getInt(TARGET_INTERVAL_KEY), 1, 200) : 20;
        targetRequiresLineOfSight = tag.getBoolean(TARGET_LOS_KEY);
        keepTargetOutOfRange = tag.getBoolean(TARGET_KEEP_KEY);
        resetTicks = tag.contains(RESET_TICKS_KEY)
                ? Mth.clamp(tag.getInt(RESET_TICKS_KEY), MIN_RESET_TICKS, MAX_RESET_TICKS) : 100;
        // Coming back to a fight against a boss still standing at 10% health is nobody's
        // idea of a second attempt, so bosses saved before this option existed get it on.
        resetHeal = !tag.contains(RESET_HEAL_KEY) || tag.getBoolean(RESET_HEAL_KEY);
        resetReturn = tag.getBoolean(RESET_RETURN_KEY);
        // Bosses configured before this option existed leave their minions behind when
        // they die, which is never what anyone wanted - so these default to on.
        clearMinionsOnDeath = !tag.contains(MINIONS_DEATH_KEY) || tag.getBoolean(MINIONS_DEATH_KEY);
        clearMinionsOnReset = !tag.contains(MINIONS_RESET_KEY) || tag.getBoolean(MINIONS_RESET_KEY);
        minionRemovalMode = tag.contains(MINIONS_REMOVAL_KEY)
                ? Mth.clamp(tag.getInt(MINIONS_REMOVAL_KEY), MINION_REMOVAL_VANISH, MINION_REMOVAL_KILL)
                : MINION_REMOVAL_VANISH;

        explosionEnabled = tag.getBoolean(EXPLOSION_ENABLED_KEY);
        explosionDelayTicks = tag.contains(EXPLOSION_DELAY_KEY)
                ? Mth.clamp(tag.getInt(EXPLOSION_DELAY_KEY), 0, 1200) : 20;
        explosionPower = tag.contains(EXPLOSION_POWER_KEY)
                ? Mth.clamp(tag.getInt(EXPLOSION_POWER_KEY), 1, 20) : 4;
        explosionMode = tag.contains(EXPLOSION_MODE_KEY)
                ? Mth.clamp(tag.getInt(EXPLOSION_MODE_KEY), EXPLOSION_MODE_EFFECT, EXPLOSION_MODE_BLOCKS_ALWAYS)
                : EXPLOSION_MODE_DAMAGE;
        explosionFire = tag.getBoolean(EXPLOSION_FIRE_KEY);
        bossBarStyle = BossBarStyles.normalize(tag.getString(BOSS_BAR_STYLE_KEY));
    }

    private void readPhases(CompoundTag tag) {
        phases.clear();
        if (tag.contains(PHASES_KEY, Tag.TAG_LIST)) {
            ListTag list = tag.getList(PHASES_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && phases.size() < MAX_PHASES; i++) {
                BossPhaseData phase = new BossPhaseData();
                phase.readFromNBT(list.getCompound(i));
                phases.add(phase);
            }
        } else if (tag.contains(PHASE_ONE_KEY, Tag.TAG_COMPOUND)) {
            // Boss saved by 1.3.0 - 1.6.x: exactly two phases with a single threshold.
            BossPhaseData first = new BossPhaseData();
            first.readFromNBT(tag.getCompound(PHASE_ONE_KEY));
            phases.add(first);
            if (tag.contains(PHASE_TWO_KEY, Tag.TAG_COMPOUND)) {
                BossPhaseData second = new BossPhaseData();
                second.readFromNBT(tag.getCompound(PHASE_TWO_KEY));
                second.setStartHealthPercent(tag.contains(PHASE_THRESHOLD_KEY)
                        ? tag.getInt(PHASE_THRESHOLD_KEY) : 50);
                phases.add(second);
            }
        } else {
            // Boss saved by 1.3.0 or older: only the shared teleport delay existed.
            int min = tag.contains("GeckoTeleportPathMinDelay") ? tag.getInt("GeckoTeleportPathMinDelay") : 60;
            int max = tag.contains("GeckoTeleportPathMaxDelay") ? tag.getInt("GeckoTeleportPathMaxDelay") : 100;
            for (int i = 0; i < 2; i++) {
                BossPhaseData phase = new BossPhaseData();
                phase.setTeleportDelayRange(min, max);
                phases.add(phase);
            }
            phases.get(1).setStartHealthPercent(50);
        }

        if (phases.isEmpty()) {
            phases.add(new BossPhaseData());
        }
        phases.get(0).setStartHealthPercent(100);
    }

    public int getPhaseCount() {
        return phases.size();
    }

    /**
     * Resizes the phase list. New phases start out spread evenly across the health bar so
     * a freshly added phase already does something instead of never triggering.
     */
    public void setPhaseCount(int count) {
        count = Mth.clamp(count, MIN_PHASES, MAX_PHASES);
        while (phases.size() > count) {
            phases.remove(phases.size() - 1);
        }
        while (phases.size() < count) {
            BossPhaseData phase = new BossPhaseData();
            // Spread the newcomers evenly: 2 phases give 100/50, 3 give 100/67/33.
            // Phases that already exist keep whatever threshold was configured for them.
            int index = phases.size();
            phase.setStartHealthPercent(Math.round(100.0F * (count - index) / count));
            phases.add(phase);
        }
        phases.get(0).setStartHealthPercent(100);
    }

    public BossPhaseData getPhase(int index) {
        return phases.get(Mth.clamp(index, 0, phases.size() - 1));
    }

    /**
     * @return the index of the phase that owns this health percentage - the last one whose
     *         threshold the boss has already dropped to
     */
    public int resolvePhaseIndex(int healthPercent) {
        int result = 0;
        for (int i = 1; i < phases.size(); i++) {
            if (healthPercent <= phases.get(i).getStartHealthPercent()) {
                result = i;
            }
        }
        return result;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isCombatOnly() { return combatOnly; }
    public void setCombatOnly(boolean combatOnly) { this.combatOnly = combatOnly; }
    public boolean isStationary() { return stationary; }
    public void setStationary(boolean stationary) { this.stationary = stationary; }
    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = Mth.clamp(order, ORDER_SEQUENTIAL, ORDER_RANDOM); }
    public boolean shouldPlaySound() { return playSound; }
    public void setPlaySound(boolean playSound) { this.playSound = playSound; }
    public String getPhaseTransitionAnimation() { return phaseTransitionAnimation; }
    public void setPhaseTransitionAnimation(String value) {
        phaseTransitionAnimation = value == null ? "" : value.trim();
    }
    public int getPhaseTransitionLockTicks() { return phaseTransitionLockTicks; }
    public void setPhaseTransitionLockTicks(int value) { phaseTransitionLockTicks = Mth.clamp(value, 0, 1200); }

    /** When set, the boss always switches to the closest reachable player instead of keeping its first target. */
    public boolean isTargetNearestPlayer() { return targetNearestPlayer; }
    public void setTargetNearestPlayer(boolean value) { targetNearestPlayer = value; }
    public int getTargetSearchRadius() { return targetSearchRadius; }
    public void setTargetSearchRadius(int value) { targetSearchRadius = Mth.clamp(value, 4, 128); }
    public int getTargetRecheckTicks() { return targetRecheckTicks; }
    public void setTargetRecheckTicks(int value) { targetRecheckTicks = Mth.clamp(value, 1, 200); }
    public boolean isTargetRequiresLineOfSight() { return targetRequiresLineOfSight; }
    public void setTargetRequiresLineOfSight(boolean value) { targetRequiresLineOfSight = value; }
    /** Keeps the current player target even after it left the search radius. */
    public boolean isKeepTargetOutOfRange() { return keepTargetOutOfRange; }
    public void setKeepTargetOutOfRange(boolean value) { keepTargetOutOfRange = value; }

    /** How long the boss has to be left alone before the encounter counts as over. */
    public int getResetTicks() { return resetTicks; }
    public void setResetTicks(int value) {
        resetTicks = Mth.clamp(value, MIN_RESET_TICKS, MAX_RESET_TICKS);
    }
    /** Whether the reset also puts the boss back to full health. */
    public boolean isResetHeal() { return resetHeal; }
    public void setResetHeal(boolean value) { resetHeal = value; }
    /** Whether the reset also sends the boss back to where it stood when it activated. */
    public boolean isResetReturn() { return resetReturn; }
    public void setResetReturn(boolean value) { resetReturn = value; }

    /** Whether the minions this boss summoned are cleaned up once the boss dies. */
    public boolean isClearMinionsOnDeath() { return clearMinionsOnDeath; }
    public void setClearMinionsOnDeath(boolean value) { clearMinionsOnDeath = value; }
    /** Whether they are cleaned up when the boss disengages and the encounter resets. */
    public boolean isClearMinionsOnReset() { return clearMinionsOnReset; }
    public void setClearMinionsOnReset(boolean value) { clearMinionsOnReset = value; }
    public int getMinionRemovalMode() { return minionRemovalMode; }
    public void setMinionRemovalMode(int value) {
        minionRemovalMode = Mth.clamp(value, MINION_REMOVAL_VANISH, MINION_REMOVAL_KILL);
    }

    /** Whether the boss detonates once it dies. */
    public boolean isExplosionEnabled() { return explosionEnabled; }
    public void setExplosionEnabled(boolean value) { explosionEnabled = value; }
    /** Ticks between the death and the blast, so the death animation can play out first. */
    public int getExplosionDelayTicks() { return explosionDelayTicks; }
    public void setExplosionDelayTicks(int value) { explosionDelayTicks = Mth.clamp(value, 0, 1200); }
    /** Blast radius, on the same scale as vanilla: TNT is 4. */
    public int getExplosionPower() { return explosionPower; }
    public void setExplosionPower(int value) { explosionPower = Mth.clamp(value, 1, 20); }
    public int getExplosionMode() { return explosionMode; }
    public void setExplosionMode(int value) {
        explosionMode = Mth.clamp(value, EXPLOSION_MODE_EFFECT, EXPLOSION_MODE_BLOCKS_ALWAYS);
    }
    public boolean isExplosionFire() { return explosionFire; }
    public void setExplosionFire(boolean value) { explosionFire = value; }
    public String getBossBarStyle() { return bossBarStyle; }
    public void setBossBarStyle(String value) { bossBarStyle = BossBarStyles.normalize(value); }

    // Compatibility helpers retained for migrated NPCs and scripts.
    public BossPhaseData getPhaseOne() { return phases.get(0); }
    public BossPhaseData getPhaseTwo() { return getPhase(1); }
    public int getPhaseTwoHealthPercent() {
        return phases.size() > 1 ? phases.get(1).getStartHealthPercent() : 50;
    }
    public void setPhaseTwoHealthPercent(int value) {
        if (phases.size() > 1) {
            phases.get(1).setStartHealthPercent(value);
        }
    }
    public int getMinDelayTicks() { return phases.get(0).getTeleportMinDelayTicks(); }
    public int getMaxDelayTicks() { return phases.get(0).getTeleportMaxDelayTicks(); }
    public void setDelayRange(int min, int max) { phases.get(0).setTeleportDelayRange(min, max); }
}
