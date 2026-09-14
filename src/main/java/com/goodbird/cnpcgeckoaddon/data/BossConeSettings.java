package com.goodbird.cnpcgeckoaddon.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.clean;
import static com.goodbird.cnpcgeckoaddon.data.BossSettingValue.value;

/**
 * The cone strike: a sector struck out from the boss, where it is aimed, and what it does to
 * whoever stands in it.
 *
 * <p>One phase of one boss holds one of these, reached through {@link BossPhaseData#cone()}.
 * It owns its own save format under the {@code Cone} prefix; a boss saved before the cone
 * existed carries none of these keys and reads back with the ability switched off.</p>
 */
public final class BossConeSettings {

    /** The full opening of the sector, in degrees: narrower than this is a line strike. */
    public static final int MIN_ANGLE = 10;
    /** A half circle: past it the "sector" would reach behind the boss. */
    public static final int MAX_ANGLE = 180;
    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 64;
    public static final int MAX_HEIGHT = 8;
    public static final int MAX_DAMAGE = 1000;
    /** Knockback for a push or a pull; tenths of a block per tick for a throw. */
    public static final int MAX_IMPULSE_STRENGTH = 40;
    public static final int MAX_POINT_INTERVAL_TICKS = 200;
    public static final int MAX_FLASH_ARCS = 8;
    /** Degrees the strike may finish the eased wind-up's turn by: a full circle snaps outright. */
    public static final int MIN_SNAP_DEGREES = 10;
    public static final int MAX_SNAP_DEGREES = 360;

    private boolean coneEnabled;
    private String coneAnimation = "";
    private int coneActionDelayTicks = 12;
    private int coneCooldownTicks = 160;
    private int coneAimMode = BossPhaseData.CONE_AIM_TARGET;
    private int coneTargetMode = BossTargetMode.MAIN;
    private int coneAngle = 60;
    private int coneLength = 10;
    private int coneHeight = 3;
    private int coneDamage = 10;
    private int coneImpulseMode = BossPhaseData.CONE_IMPULSE_PUSH;
    private int coneImpulseStrength = 2;
    private final BossEffectSet coneEffects = new BossEffectSet();
    /** Off for animations that must not turn with the strike, such as a full-circle swing. */
    private boolean coneFaceAxis = true;
    private final BossConeAimList conePoints = new BossConeAimList();
    private int conePointOrder = BossPhaseData.CONE_ORDER_LIST;
    /** How many of the points one cast strikes; zero strikes every one that is switched on. */
    private int conePointCount;
    private int conePointIntervalTicks = 10;
    /** How many arcs the strike's flash lays over a fan, spread evenly along its length. */
    private int coneFlashArcs = 3;
    /** The turn left over from the eased wind-up, finished on the tick the cone lands. */
    private int coneSnapDegrees = 360;
    private final BossSoundCue coneSwingSound =
            new BossSoundCue("minecraft:entity.player.attack.sweep", 1.5F, 0.6F);
    /**
     * Whether the warning's end may call the cone off, and by what rule. Never by default: the
     * sector was promised on the floor, and a target that merely ran past the cone's length
     * used to cancel a swing the boss then stood rooted through, which read as a boss that
     * rarely swings at all.
     */
    private int coneDodgeMode = BossPhaseData.CONE_DODGE_NEVER;
    /**
     * Whether a cone along the gaze or at points waits for somebody to stand in it before it is
     * swung; a cone at a target never waits, since whoever it is aimed at is in it.
     */
    private boolean coneNeedsVictim;
    /** Whether the cooldown counts from the wind-up or from the last cone of the cast. */
    private int coneCooldownFrom = BossPhaseData.CONE_COOLDOWN_FROM_END;
    /** Where the boss goes before it casts this, if anywhere. */
    private final BossCastSpot coneCastSpot = new BossCastSpot();

    public boolean isEnabled() { return coneEnabled; }

    public void setEnabled(boolean value) { coneEnabled = value; }

    /** The wind-up: played while the sector is marked, before the strike. */
    public String getAnimation() { return coneAnimation; }

    public void setAnimation(String value) { coneAnimation = clean(value); }

    public int getActionDelayTicks() { return coneActionDelayTicks; }

    public void setActionDelayTicks(int value) { coneActionDelayTicks = Mth.clamp(value, 0, 1200); }

    public int getCooldownTicks() { return coneCooldownTicks; }

    public void setCooldownTicks(int value) { coneCooldownTicks = Mth.clamp(value, 1, 12000); }

    /** Whether the cone opens toward a target, along the gaze, or over the builder's points. */
    public int getAimMode() { return coneAimMode; }

    public void setAimMode(int value) {
        coneAimMode = Mth.clamp(value, BossPhaseData.CONE_AIM_TARGET, BossPhaseData.CONE_AIM_POINTS);
    }

    /** Who the cone is aimed at; only read when it opens toward a target. */
    public int getTargetMode() { return coneTargetMode; }

    public void setTargetMode(int value) { coneTargetMode = BossTargetMode.clamp(value); }

    /** The full opening of the sector in degrees, half of it to either side of its middle. */
    public int getAngle() { return coneAngle; }

    public void setAngle(int value) { coneAngle = Mth.clamp(value, MIN_ANGLE, MAX_ANGLE); }

    /** How far out from the boss the sector reaches, measured flat. */
    public int getLength() { return coneLength; }

    public void setLength(int value) { coneLength = Mth.clamp(value, MIN_LENGTH, MAX_LENGTH); }

    /** How far above and below the boss' feet the sector still catches somebody. */
    public int getHeight() { return coneHeight; }

    public void setHeight(int value) { coneHeight = Mth.clamp(value, 1, MAX_HEIGHT); }

    public int getDamage() { return coneDamage; }

    public void setDamage(int value) { coneDamage = Mth.clamp(value, 0, MAX_DAMAGE); }

    /** Whether whoever the cone catches is knocked away, thrown up or pulled in. */
    public int getImpulseMode() { return coneImpulseMode; }

    public void setImpulseMode(int value) {
        coneImpulseMode = Mth.clamp(value, BossPhaseData.CONE_IMPULSE_PUSH, BossPhaseData.CONE_IMPULSE_PULL);
    }

    /**
     * How hard: the knockback of a push or a pull, or the speed of a throw in tenths of a
     * block per tick, the way a geyser's launch is given.
     */
    public int getImpulseStrength() { return coneImpulseStrength; }

    public void setImpulseStrength(int value) { coneImpulseStrength = Mth.clamp(value, 0, MAX_IMPULSE_STRENGTH); }

    public BossEffectSet getEffects() { return coneEffects; }

    /** Whether the wind-up and a series turn the model onto the cone instead of after the target. */
    public boolean isFaceAxis() { return coneFaceAxis; }

    public void setFaceAxis(boolean value) { coneFaceAxis = value; }

    /** The points a cast aimed at points strikes, one cone each. */
    public BossConeAimList getPoints() { return conePoints; }

    /** Whether a series takes its points in list order or in a random one. */
    public int getPointOrder() { return conePointOrder; }

    public void setPointOrder(int value) {
        conePointOrder = Mth.clamp(value, BossPhaseData.CONE_ORDER_LIST, BossPhaseData.CONE_ORDER_RANDOM);
    }

    /** How many points one cast strikes; zero for every point that is switched on. */
    public int getPointCount() { return conePointCount; }

    public void setPointCount(int value) { conePointCount = Mth.clamp(value, 0, BossConeAimList.MAX_ENTRIES); }

    /** The pause between two cones of a series; zero strikes them all at once. */
    public int getPointIntervalTicks() { return conePointIntervalTicks; }

    public void setPointIntervalTicks(int value) {
        conePointIntervalTicks = Mth.clamp(value, 0, MAX_POINT_INTERVAL_TICKS);
    }

    /** Where the boss goes before it casts this; a spot that is not set casts from where it stands. */
    public int getFlashArcs() { return coneFlashArcs; }

    public void setFlashArcs(int value) { coneFlashArcs = Mth.clamp(value, 0, MAX_FLASH_ARCS); }

    public int getSnapDegrees() { return coneSnapDegrees; }

    public void setSnapDegrees(int value) {
        coneSnapDegrees = Mth.clamp(value, MIN_SNAP_DEGREES, MAX_SNAP_DEGREES);
    }

    public BossSoundCue getSwingSound() { return coneSwingSound; }

    /** Whether the warning's end may call the cone off: never, with the fan empty, or with the target out of reach. */
    public int getDodgeMode() { return coneDodgeMode; }

    public void setDodgeMode(int value) {
        coneDodgeMode = Mth.clamp(value, BossPhaseData.CONE_DODGE_NEVER, BossPhaseData.CONE_DODGE_RANGE);
    }

    /** Whether a cone along the gaze or at points is only swung with somebody in it. */
    public boolean isNeedsVictim() { return coneNeedsVictim; }

    public void setNeedsVictim(boolean value) { coneNeedsVictim = value; }

    /** Whether the cooldown counts from the wind-up or from the last cone of the cast. */
    public int getCooldownFrom() { return coneCooldownFrom; }

    public void setCooldownFrom(int value) {
        coneCooldownFrom = Mth.clamp(value, BossPhaseData.CONE_COOLDOWN_FROM_START,
                BossPhaseData.CONE_COOLDOWN_FROM_END);
    }

    /**
     * Whether the cast has what it cannot be swung without: a cone at points needs a point
     * switched on, and the other two aims need nothing beyond their numbers. It is what a
     * follow-up still needs once it has skipped the switch, the platforms' way.
     */
    public boolean isConfigured() {
        return coneAimMode != BossPhaseData.CONE_AIM_POINTS || conePoints.hasEnabled();
    }

    /** Switched on with something to swing at, which is what puts the cone on the rotation. */
    public boolean canCast() { return coneEnabled && isConfigured(); }

    public BossCastSpot castSpot() { return coneCastSpot; }

    void writeToNBT(CompoundTag tag) {
        tag.putBoolean("ConeEnabled", coneEnabled);
        tag.putString("ConeAnimation", coneAnimation);
        tag.putInt("ConeActionDelayTicks", coneActionDelayTicks);
        tag.putInt("ConeCooldownTicks", coneCooldownTicks);
        tag.putInt("ConeAimMode", coneAimMode);
        tag.putInt("ConeTargetMode", coneTargetMode);
        tag.putInt("ConeAngle", coneAngle);
        tag.putInt("ConeLength", coneLength);
        tag.putInt("ConeHeight", coneHeight);
        tag.putInt("ConeDamage", coneDamage);
        tag.putInt("ConeImpulseMode", coneImpulseMode);
        tag.putInt("ConeImpulseStrength", coneImpulseStrength);
        tag.put("ConeEffects", coneEffects.writeToNBT());
        tag.putBoolean("ConeFaceAxis", coneFaceAxis);
        tag.put("ConePoints", conePoints.writeToNBT());
        tag.putInt("ConePointOrder", conePointOrder);
        tag.putInt("ConePointCount", conePointCount);
        tag.putInt("ConePointIntervalTicks", conePointIntervalTicks);
        tag.putInt("ConeFlashArcs", coneFlashArcs);
        tag.putInt("ConeSnap", coneSnapDegrees);
        coneSwingSound.writeToNBT(tag, "ConeSwingSound");
        tag.putInt("ConeDodge", coneDodgeMode);
        tag.putBoolean("ConeNeedsVictim", coneNeedsVictim);
        tag.putInt("ConeCooldownFrom", coneCooldownFrom);
        coneCastSpot.writeToNBT(tag, "Cone");
    }

    void readFromNBT(CompoundTag tag) {
        coneEnabled = tag.getBoolean("ConeEnabled");
        coneAnimation = clean(tag.getString("ConeAnimation"));
        coneActionDelayTicks = value(tag, "ConeActionDelayTicks", 12, 0, 1200);
        coneCooldownTicks = value(tag, "ConeCooldownTicks", 160, 1, 12000);
        coneAimMode = value(tag, "ConeAimMode", BossPhaseData.CONE_AIM_TARGET,
                BossPhaseData.CONE_AIM_TARGET, BossPhaseData.CONE_AIM_POINTS);
        coneTargetMode = value(tag, "ConeTargetMode",
                BossTargetMode.MAIN, BossTargetMode.MAIN, BossTargetMode.RANDOM);
        coneAngle = value(tag, "ConeAngle", 60, MIN_ANGLE, MAX_ANGLE);
        coneLength = value(tag, "ConeLength", 10, MIN_LENGTH, MAX_LENGTH);
        coneHeight = value(tag, "ConeHeight", 3, 1, MAX_HEIGHT);
        coneDamage = value(tag, "ConeDamage", 10, 0, MAX_DAMAGE);
        coneImpulseMode = value(tag, "ConeImpulseMode", BossPhaseData.CONE_IMPULSE_PUSH,
                BossPhaseData.CONE_IMPULSE_PUSH, BossPhaseData.CONE_IMPULSE_PULL);
        coneImpulseStrength = value(tag, "ConeImpulseStrength", 2, 0, MAX_IMPULSE_STRENGTH);
        coneEffects.readFromNBT(tag, "ConeEffects");
        coneFaceAxis = !tag.contains("ConeFaceAxis") || tag.getBoolean("ConeFaceAxis");
        conePoints.readFromNBT(tag, "ConePoints");
        conePointOrder = value(tag, "ConePointOrder", BossPhaseData.CONE_ORDER_LIST,
                BossPhaseData.CONE_ORDER_LIST, BossPhaseData.CONE_ORDER_RANDOM);
        conePointCount = value(tag, "ConePointCount", 0, 0, BossConeAimList.MAX_ENTRIES);
        conePointIntervalTicks = value(tag, "ConePointIntervalTicks", 10, 0, MAX_POINT_INTERVAL_TICKS);
        // A boss saved before these were settings flashes three arcs and snaps outright,
        // which is what the strike always did.
        coneFlashArcs = value(tag, "ConeFlashArcs", 3, 0, MAX_FLASH_ARCS);
        coneSnapDegrees = value(tag, "ConeSnap", 360, MIN_SNAP_DEGREES, MAX_SNAP_DEGREES);
        coneSwingSound.readFromNBT(tag, "ConeSwingSound");
        // A boss saved before these were settings gets the new rules on purpose: a dodge by
        // reach and a cooldown from the wind-up are what made its cone swing so rarely, and
        // both are still here to choose.
        coneDodgeMode = value(tag, "ConeDodge", BossPhaseData.CONE_DODGE_NEVER,
                BossPhaseData.CONE_DODGE_NEVER, BossPhaseData.CONE_DODGE_RANGE);
        coneNeedsVictim = tag.getBoolean("ConeNeedsVictim");
        coneCooldownFrom = value(tag, "ConeCooldownFrom", BossPhaseData.CONE_COOLDOWN_FROM_END,
                BossPhaseData.CONE_COOLDOWN_FROM_START, BossPhaseData.CONE_COOLDOWN_FROM_END);
        coneCastSpot.readFromNBT(tag, "Cone");
    }
}
