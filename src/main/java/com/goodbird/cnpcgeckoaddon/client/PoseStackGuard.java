package com.goodbird.cnpcgeckoaddon.client;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Puts a pose stack back the way a failed draw found it.
 *
 * <p>A renderer that throws halfway leaves the poses it pushed on the stack, and the level
 * renderer checks the stack is back at its root at the end of the frame - so a draw the guards
 * caught would still take the client down one call later. The depth of a {@link PoseStack} is
 * not readable, but its top is: a draw that balanced its pushes leaves the very same
 * {@link PoseStack.Pose} object on top that it was handed, so popping until that object is back
 * on top undoes exactly what the failed draw left behind, and nothing below it.</p>
 */
public final class PoseStackGuard {

    /** A draw nests a few poses, never this many; the bound only keeps a broken stack from looping. */
    private static final int MAX_UNWIND = 256;

    private PoseStackGuard() {
    }

    /**
     * Pops back down to {@code top}, the pose that was on top before the draw began.
     *
     * <p>Only ever called after a failure; never pops the root.</p>
     */
    public static void unwind(PoseStack stack, PoseStack.Pose top) {
        for (int popped = 0; popped < MAX_UNWIND && stack.last() != top && !stack.clear(); popped++) {
            stack.popPose();
        }
    }
}
