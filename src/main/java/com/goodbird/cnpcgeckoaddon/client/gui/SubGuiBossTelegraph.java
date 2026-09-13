package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.TelegraphLineStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/** What the boss shows and says before an ability lands, and how long it gives for it. */
public final class SubGuiBossTelegraph extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int STYLE_BUTTON = 2;
    private static final int ANNOUNCE_BUTTON = 3;
    private static final int SOUND_BUTTON = 4;
    private static final int DODGE_BUTTON = 5;
    private static final int LEAD_FIELD = 6;
    private static final int ZONE_RADIUS_FIELD = 7;
    private static final int ABILITIES_BUTTON = 8;
    private static final int LINE_STYLE_BUTTON = 9;
    private static final int LINE_WIDTH_FIELD = 10;
    private static final int LINE_MOTION_BUTTON = 11;
    private static final int LINE_FILL_FIELD = 12;
    private static final int LINE_LASTING_BUTTON = 13;

    /** Where the abilities button sits, under the last row of settings. */
    private static final int ABILITIES_Y = 284;
    /** And where the hints start, under it. */
    private static final int HINTS_Y = 310;
    /** One line of hint plus the pixel that keeps two of them apart. */
    private static final int HINT_LINE = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 6;
    private static final String LINE_HINT = "cnpcgeckoaddon.boss.telegraph_line_hint";

    private static final String[] LINE_STYLE_LABELS = TelegraphLineStyles.values().stream()
            .map(TelegraphLineStyles.Style::translationKey)
            .toArray(String[]::new);

    private final TeleportPathData data;

    public SubGuiBossTelegraph(TeleportPathData data) {
        this.data = data;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: how many lines the last hint
        // wraps to is up to the locale.
        imageHeight = doneButtonY() + BUTTON_HEIGHT + BOTTOM_MARGIN;
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.telegraph_title", guiLeft + 6, guiTop + 6, 0xFFFFFF));

        int y = guiTop + 20;
        addYesNo(ENABLED_BUTTON, "cnpcgeckoaddon.boss.telegraph_enabled", y, data.isTelegraphEnabled());
        y += 22;
        addLabel(new GuiLabel(STYLE_BUTTON, "cnpcgeckoaddon.boss.telegraph_style", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, STYLE_BUTTON, guiLeft + 130, y, 112, 20,
                TeleportPathData.TELEGRAPH_STYLE_LABELS, data.getTelegraphStyle()));
        y += 22;
        addYesNo(ANNOUNCE_BUTTON, "cnpcgeckoaddon.boss.telegraph_announce", y, data.isTelegraphAnnounce());
        y += 22;
        addYesNo(SOUND_BUTTON, "cnpcgeckoaddon.boss.telegraph_sound", y, data.isTelegraphSound());
        y += 22;
        addNumberField(LEAD_FIELD, "cnpcgeckoaddon.boss.telegraph_lead", y,
                data.getTelegraphLeadTicks(), TeleportPathData.MIN_TELEGRAPH_LEAD_TICKS,
                TeleportPathData.MAX_TELEGRAPH_LEAD_TICKS, TeleportPathData.DEFAULT_TELEGRAPH_LEAD_TICKS);
        y += 22;
        addNumberField(ZONE_RADIUS_FIELD, "cnpcgeckoaddon.boss.telegraph_zone_radius", y,
                data.getTelegraphZoneRadius(), TeleportPathData.MIN_TELEGRAPH_ZONE_RADIUS,
                TeleportPathData.MAX_TELEGRAPH_ZONE_RADIUS, TeleportPathData.DEFAULT_TELEGRAPH_ZONE_RADIUS);
        y += 22;
        addYesNo(DODGE_BUTTON, "cnpcgeckoaddon.boss.telegraph_dodge", y, data.isTelegraphDodge());

        // How the zone is drawn rather than what is drawn. The width, the depth of the flood
        // and the switch below are shown whatever the style is: they say nothing while the
        // zone is dust, and hiding them would only make them hard to find afterwards.
        y += 22;
        addCycle(LINE_STYLE_BUTTON, "cnpcgeckoaddon.boss.telegraph_line_style", y,
                LINE_STYLE_LABELS, TelegraphLineStyles.indexOf(data.getTelegraphLineStyle()));
        y += 22;
        addNumberField(LINE_WIDTH_FIELD, "cnpcgeckoaddon.boss.telegraph_line_width", y,
                data.getTelegraphLineWidth(), TeleportPathData.MIN_TELEGRAPH_LINE_WIDTH,
                TeleportPathData.MAX_TELEGRAPH_LINE_WIDTH, TeleportPathData.DEFAULT_TELEGRAPH_LINE_WIDTH);
        y += 22;
        addCycle(LINE_MOTION_BUTTON, "cnpcgeckoaddon.boss.telegraph_motion", y,
                TeleportPathData.TELEGRAPH_MOTION_LABELS, data.getTelegraphLineMotion());
        y += 22;
        addNumberField(LINE_FILL_FIELD, "cnpcgeckoaddon.boss.telegraph_line_fill", y,
                data.getTelegraphLineFill(), TeleportPathData.MIN_TELEGRAPH_LINE_FILL,
                TeleportPathData.MAX_TELEGRAPH_LINE_FILL, TeleportPathData.MIN_TELEGRAPH_LINE_FILL);
        y += 22;
        addYesNo(LINE_LASTING_BUTTON, "cnpcgeckoaddon.boss.telegraph_line_lasting", y,
                data.isTelegraphLineLasting());

        addButton(new GuiButtonNop(this, ABILITIES_BUTTON, guiLeft + 6, guiTop + ABILITIES_Y, 236, 20,
                "cnpcgeckoaddon.boss.telegraph_abilities"));

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.telegraph_hint", guiLeft + 6, guiTop + HINTS_Y, 0xA0A0A0));
        addLabel(new GuiLabel(32, "cnpcgeckoaddon.boss.telegraph_lead_hint",
                guiLeft + 6, guiTop + HINTS_Y + HINT_LINE, 0xA0A0A0));
        addLabel(new GuiLabel(33, "cnpcgeckoaddon.boss.telegraph_dodge_hint",
                guiLeft + 6, guiTop + HINTS_Y + 2 * HINT_LINE, 0xA0A0A0));
        // Wrapped rather than one label: this one is wider than the panel, and a label never
        // wraps and never clips.
        addWrappedHint(34, LINE_HINT, guiTop + HINTS_Y + 3 * HINT_LINE);
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, BUTTON_HEIGHT);
    }

    /** Where the done button goes, from the panel's top: just under the last hint. */
    private int doneButtonY() {
        return HINTS_Y + 3 * HINT_LINE + wrappedHintHeight(LINE_HINT) + 4;
    }

    /**
     * A row whose label runs the width of the screen, so the toggle sits hard against the
     * right edge: "warn before abilities" is a sentence in some languages, not a word.
     */

    @Override
    protected int numberLabelX() {
        // This screen family starts its labels a column tighter than the shared default.
        return 6;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            data.setTelegraphEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == STYLE_BUTTON) {
            data.setTelegraphStyle(button.getValue());
        } else if (button.id == ANNOUNCE_BUTTON) {
            data.setTelegraphAnnounce(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == SOUND_BUTTON) {
            data.setTelegraphSound(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == DODGE_BUTTON) {
            data.setTelegraphDodge(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == LINE_STYLE_BUTTON) {
            data.setTelegraphLineStyle(TelegraphLineStyles.byIndex(button.getValue()));
        } else if (button.id == LINE_MOTION_BUTTON) {
            data.setTelegraphLineMotion(button.getValue());
        } else if (button.id == LINE_LASTING_BUTTON) {
            data.setTelegraphLineLasting(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == ABILITIES_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossTelegraphAbilities(data));
        }
    }

    @Override
    protected void applyFields() {
        applyNumberField(LEAD_FIELD, data::setTelegraphLeadTicks);
        applyNumberField(ZONE_RADIUS_FIELD, data::setTelegraphZoneRadius);
        applyNumberField(LINE_WIDTH_FIELD, data::setTelegraphLineWidth);
        applyNumberField(LINE_FILL_FIELD, data::setTelegraphLineFill);
    }

    @Override
    protected int toggleLabelX() {
        return 6;
    }

    @Override
    protected int toggleButtonX() {
        return 196;
    }

    @Override
    protected int toggleButtonWidth() {
        return 46;
    }

    /** The cycling rows carry whole sentences, so they take the room the toggles do not. */
    @Override
    protected int cycleButtonX() {
        return 96;
    }

    @Override
    protected int cycleButtonWidth() {
        return 146;
    }

}
