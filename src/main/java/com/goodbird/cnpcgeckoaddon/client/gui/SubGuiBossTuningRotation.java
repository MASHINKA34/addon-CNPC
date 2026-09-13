package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossTuningSettings;

/** The pauses between the boss' own actions, and how far it keeps hold of a target. */
public final class SubGuiBossTuningRotation extends SubGuiBossTuningTopic {
    private static final int POST_ACTION_LOCK_FIELD = 1;
    private static final int RETRY_SHORT_FIELD = 2;
    private static final int RETRY_FIELD = 3;
    private static final int RETRY_LONG_FIELD = 4;
    private static final int BLOCK_FEEDBACK_FIELD = 5;
    private static final int DODGE_RETRY_FIELD = 6;
    private static final int LINE_FACE_TURN_FIELD = 7;
    private static final int TRACK_TURN_FIELD = 8;
    private static final int TARGET_LEASH_FIELD = 9;

    public SubGuiBossTuningRotation(BossTuningSettings tuning) {
        super("cnpcgeckoaddon.boss.tuning.rotation", tuning);
    }

    @Override
    protected int rows() {
        return 9;
    }

    @Override
    protected void addRows() {
        addNumberField(POST_ACTION_LOCK_FIELD, "cnpcgeckoaddon.boss.tuning.post_action_lock", nextRow(),
                tuning.postActionLockTicks(), BossTuningSettings.MIN_POST_ACTION_LOCK,
                BossTuningSettings.MAX_POST_ACTION_LOCK, 10);
        addNumberField(RETRY_SHORT_FIELD, "cnpcgeckoaddon.boss.tuning.retry_short", nextRow(),
                tuning.retryShortTicks(), BossTuningSettings.MIN_RETRY, BossTuningSettings.MAX_RETRY, 5);
        addNumberField(RETRY_FIELD, "cnpcgeckoaddon.boss.tuning.retry", nextRow(),
                tuning.retryTicks(), BossTuningSettings.MIN_RETRY, BossTuningSettings.MAX_RETRY, 10);
        addNumberField(RETRY_LONG_FIELD, "cnpcgeckoaddon.boss.tuning.retry_long", nextRow(),
                tuning.retryLongTicks(), BossTuningSettings.MIN_RETRY, BossTuningSettings.MAX_RETRY, 20);
        addNumberField(BLOCK_FEEDBACK_FIELD, "cnpcgeckoaddon.boss.tuning.block_feedback", nextRow(),
                tuning.blockFeedbackIntervalTicks(), BossTuningSettings.MIN_BLOCK_FEEDBACK,
                BossTuningSettings.MAX_BLOCK_FEEDBACK, 5);
        addNumberField(DODGE_RETRY_FIELD, "cnpcgeckoaddon.boss.tuning.dodge_retry", nextRow(),
                tuning.dodgeRetryTicks(), 0, BossTuningSettings.MAX_DODGE_RETRY, 40);
        addNumberField(LINE_FACE_TURN_FIELD, "cnpcgeckoaddon.boss.tuning.line_face_turn", nextRow(),
                (int) tuning.lineFaceTurnDegrees(), BossTuningSettings.MIN_TURN_DEGREES,
                BossTuningSettings.MAX_TURN_DEGREES, 15);
        addNumberField(TRACK_TURN_FIELD, "cnpcgeckoaddon.boss.tuning.track_turn", nextRow(),
                (int) tuning.trackTurnDegrees(), BossTuningSettings.MIN_TURN_DEGREES,
                BossTuningSettings.MAX_TURN_DEGREES, 90);
        addNumberField(TARGET_LEASH_FIELD, "cnpcgeckoaddon.boss.tuning.target_leash", nextRow(),
                tuning.targetLeashPercent(), BossTuningSettings.MIN_TARGET_LEASH_PERCENT,
                BossTuningSettings.MAX_TARGET_LEASH_PERCENT, 150);
    }

    @Override
    protected void applyFields() {
        applyNumberField(POST_ACTION_LOCK_FIELD, tuning::setPostActionLockTicks);
        applyNumberField(RETRY_SHORT_FIELD, tuning::setRetryShortTicks);
        applyNumberField(RETRY_FIELD, tuning::setRetryTicks);
        applyNumberField(RETRY_LONG_FIELD, tuning::setRetryLongTicks);
        applyNumberField(BLOCK_FEEDBACK_FIELD, tuning::setBlockFeedbackIntervalTicks);
        applyNumberField(DODGE_RETRY_FIELD, tuning::setDodgeRetryTicks);
        applyNumberField(LINE_FACE_TURN_FIELD, tuning::setLineFaceTurnDegrees);
        applyNumberField(TRACK_TURN_FIELD, tuning::setTrackTurnDegrees);
        applyNumberField(TARGET_LEASH_FIELD, tuning::setTargetLeashPercent);
    }
}
