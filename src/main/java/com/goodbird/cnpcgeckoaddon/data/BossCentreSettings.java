package com.goodbird.cnpcgeckoaddon.data;

/**
 * The settings of an ability that acts round a point - the area slam, the gravity field, the
 * seismic rings, the take cover strike, the boulder rain and the beams - where that point goes.
 *
 * <p>One shape for the six of them, so the controller resolves the centre and a screen edits it
 * the same way for each: each class keeps the two numbers under its own prefix.</p>
 */
public interface BossCentreSettings {

    /** {@link BossCentreMode#BOSS} or {@link BossCentreMode#TARGET}. */
    int getCentreMode();

    void setCentreMode(int value);

    /** Which target a centre on a target goes to, as a {@link BossTargetMode}. */
    int getTargetMode();

    void setTargetMode(int value);
}
