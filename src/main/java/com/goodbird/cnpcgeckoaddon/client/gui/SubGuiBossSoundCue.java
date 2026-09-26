package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.data.BossSoundCue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/**
 * The editor behind every sound cue button: which sound, how loud, how high, and a way to
 * hear it without leaving the menu.
 *
 * <p>One screen for all of them - the cue itself is handed in, and the title is the label of
 * the button that opened it, so "Warning" and "Rage" read as themselves rather than as
 * "Sound" twice over.</p>
 */
public final class SubGuiBossSoundCue extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int SOUND_FIELD = 2;
    private static final int VOLUME_FIELD = 3;
    private static final int PITCH_FIELD = 4;
    private static final int TEST_BUTTON = 5;
    private static final int RESET_BUTTON = 6;
    private static final int TITLE_LABEL = 30;
    private static final int ERROR_LABEL = 31;
    private static final int FIRST_HINT_LABEL = 40;

    private static final int FIRST_ROW_Y = 26;
    private static final int ROW_HEIGHT = 22;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TEXT_FIELD_X = 108;
    private static final int ERROR_COLOR = 0xFF5555;
    private static final String HINT = "cnpcgeckoaddon.cue.hint";

    private final String titleKey;
    private final BossSoundCue cue;
    /** Set when the last read refused a typed id, so the hint says so until it is fixed. */
    private boolean rejected;

    public SubGuiBossSoundCue(String titleKey, BossSoundCue cue) {
        this.titleKey = titleKey;
        this.cue = cue;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        imageHeight = doneButtonY() + BUTTON_HEIGHT + 6;
        super.init();
        addLabel(new ThemeLabel(TITLE_LABEL, Component.translatable("cnpcgeckoaddon.cue.sound_title")
                .append(": ").append(Component.translatable(titleKey)), 0xFFFFFF,
                guiLeft + 8, guiTop + 8, imageWidth - 16, LINE_HEIGHT));
        int y = guiTop + FIRST_ROW_Y;

        addYesNo(ENABLED_BUTTON, "cnpcgeckoaddon.cue.enabled", y, cue.isEnabled());
        y += ROW_HEIGHT;

        addLabel(new ThemeLabel(SOUND_FIELD, "cnpcgeckoaddon.cue.sound_id", guiLeft + 8, y + 6));
        addTextField(new ThemeTextField(SOUND_FIELD, this, guiLeft + TEXT_FIELD_X, y, 86,
                BUTTON_HEIGHT, cue.getSoundId()));
        addButton(new ThemeButton(this, SOUND_FIELD, guiLeft + 198, y, 44, BUTTON_HEIGHT,
                "mco.template.button.select"));
        y += ROW_HEIGHT;

        addNumberField(VOLUME_FIELD, "cnpcgeckoaddon.cue.volume", y, cue.getVolume(),
                BossSoundCue.MIN_VOLUME, BossSoundCue.MAX_VOLUME, cue.getDefaultVolume());
        y += ROW_HEIGHT;

        addNumberField(PITCH_FIELD, "cnpcgeckoaddon.cue.pitch", y, cue.getPitch(),
                BossSoundCue.MIN_PITCH, BossSoundCue.MAX_PITCH, cue.getDefaultPitch());
        y += ROW_HEIGHT;

        addButton(new ThemeButton(this, TEST_BUTTON, guiLeft + 8, y, 114, BUTTON_HEIGHT,
                "cnpcgeckoaddon.cue.test"));
        addButton(new ThemeButton(this, RESET_BUTTON, guiLeft + 128, y, 114, BUTTON_HEIGHT,
                "cnpcgeckoaddon.cue.reset"));

        int hintEnd = addWrappedText(FIRST_HINT_LABEL, hintText(), guiTop + hintY());
        addLabel(new ThemeLabel(ERROR_LABEL, Component.literal(rejected ? errorText() : ""),
                ERROR_COLOR, guiLeft + 8, hintEnd, imageWidth - 16, LINE_HEIGHT));
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, BUTTON_HEIGHT);
    }

    private static String hintText() {
        return Component.translatable(HINT).getString();
    }

    private static String errorText() {
        return Component.translatable("cnpcgeckoaddon.cue.unknown_id").getString();
    }

    /** Where the hint starts, from the panel's top: under the last row of buttons. */
    private int hintY() {
        return FIRST_ROW_Y + 5 * ROW_HEIGHT + 4;
    }

    private int doneButtonY() {
        return hintY() + wrapLines(hintText(), imageWidth - 16).size() * LINE_HEIGHT + LINE_HEIGHT + 4;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            cue.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == SOUND_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.cue_sound",
                    BossSoundCue.getSelectableIds(), name -> {
                cue.setSoundId(name);
                showRejected(false);
                getTextField(SOUND_FIELD).setValue(cue.getSoundId());
            }));
        } else if (button.id == TEST_BUTTON) {
            applyFields();
            playHere();
        } else if (button.id == RESET_BUTTON) {
            cue.reset();
            showRejected(false);
            requestLayout();
        }
    }

    /** Plays the cue to whoever is editing it, straight through the client's own sound manager. */
    private void playHere() {
        SoundEvent sound = cue.resolve();
        if (sound == null) {
            return;
        }
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(sound, cue.pitchValue(), cue.volumeValue()));
    }

    @Override
    protected void applyFields() {
        applyNumberField(VOLUME_FIELD, cue::setVolume);
        applyNumberField(PITCH_FIELD, cue::setPitch);
        GuiTextFieldNop field = getTextField(SOUND_FIELD);
        if (field == null) {
            return;
        }
        // A typo would leave the boss quiet with nothing on screen to say why, so it is
        // refused while the field is still in front of whoever typed it.
        String value = field.getValue().trim();
        if (BossSoundCue.isKnownSound(value)) {
            cue.setSoundId(value);
            showRejected(false);
        } else {
            field.setValue(cue.getSoundId());
            showRejected(true);
        }
    }

    private void showRejected(boolean value) {
        rejected = value;
        GuiLabel label = getLabel(ERROR_LABEL);
        if (label != null) {
            label.setMessage(Component.literal(value ? errorText() : ""));
        }
    }

    /** Wide enough for the longest choice beside a label that runs to "Sound id". */
    @Override
    protected int toggleButtonX() {
        return TEXT_FIELD_X;
    }

    @Override
    protected int toggleButtonWidth() {
        return 134;
    }
}
