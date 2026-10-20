package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A question with Yes and No under it, for the changes a misclick would be expensive to take back:
 * a phase copied over another, a preset laid over a boss. Yes runs the action, and either answer
 * closes the question and hands the screen under it back.
 */
public final class SubGuiConfirm extends SubGuiFieldScreen {
    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    private static final int YES_BUTTON = 1;
    private static final int NO_BUTTON = 2;
    private static final int MESSAGE_LABEL = 10;

    /** Where the question starts, from the panel's top. */
    private static final int MESSAGE_Y = 10;
    /** The gap between the question and the buttons, and under the buttons. */
    private static final int GAP = 10;
    private static final int BUTTON_HEIGHT = 20;

    private final String message;
    private final Runnable onYes;

    /**
     * @param message the question, already in the player's language
     * @param onYes   what a Yes does, run before the question closes
     */
    public SubGuiConfirm(String message, Runnable onYes) {
        this.message = message;
        this.onYes = onYes;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: how many lines the question wraps
        // to is up to the locale and to what it names.
        imageHeight = MESSAGE_Y + wrapLines(message, imageWidth - 16).size() * LINE_HEIGHT + GAP
                + BUTTON_HEIGHT + GAP;
        super.init();
        int y = addWrappedText(MESSAGE_LABEL, message, guiTop + MESSAGE_Y) + GAP;
        addButton(new ThemeButton(this, YES_BUTTON, guiLeft + 8, y, 114, BUTTON_HEIGHT, "cnpcgeckoaddon.confirm.yes",
                button -> answer(true)));
        addButton(new ThemeButton(this, NO_BUTTON, guiLeft + 128, y, 114, BUTTON_HEIGHT, "cnpcgeckoaddon.confirm.no",
                button -> answer(false)));
    }

    private void answer(boolean yes) {
        LOGGER.info("confirmation answered {}: {}", yes ? "yes" : "no", message);
        if (yes) {
            // A Yes that throws is one logged failure; the question still closes, and the client lives.
            CrashGuard.run("client.gui.confirm", onYes);
        }
        close();
    }
}
