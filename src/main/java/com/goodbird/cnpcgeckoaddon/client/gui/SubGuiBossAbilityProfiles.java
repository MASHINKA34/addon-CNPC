package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.ai.BossAbility;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityProfile;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every ability's profile in one phase at a glance: one row per ability of the rotation, laid out
 * like the finish screen - the ability's name on a button that opens its profile, and a short
 * summary beside it of what the profile changes, or a dash for one that changes nothing.
 *
 * <p>The rows are the rotation table itself, in its own order. The arena hazard has a profile too,
 * but no turn in the rotation, so it has no row here.</p>
 */
public final class SubGuiBossAbilityProfiles extends SubGuiFieldScreen {
    private static final int TITLE_LABEL = 30;
    private static final int LEGEND_LABEL = 40;
    private static final int FIRST_ABILITY_BUTTON = 100;
    private static final int FIRST_SUMMARY_LABEL = 200;

    private static final int FIRST_ROW_Y = 22;
    private static final int ROW_HEIGHT = 22;
    private static final int ABILITY_BUTTON_WIDTH = 120;
    private static final int SUMMARY_X = 134;
    private static final int SUMMARY_WIDTH = 108;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 6;
    /** What a row reads while its profile is all defaults. */
    private static final String DEFAULT_SUMMARY = "—";
    private static final String SEPARATOR = " · ";
    private static final String MORE = "…";

    /** The screen this list was opened from: the phase's menu. */
    private final Screen parent;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private final List<BossAbility> rows = BossAbility.ROTATION;

    public SubGuiBossAbilityProfiles(Screen parent, BossPhaseData phase, int phaseIndex) {
        this.parent = parent;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: how many lines the legend wraps to
        // is up to the locale. Twenty-seven rows never fit one panel, so the screen scrolls.
        imageHeight = doneButtonY() + BUTTON_HEIGHT + BOTTOM_MARGIN;
        super.init();
        addLabel(new ThemeLabel(TITLE_LABEL, Component.translatable("cnpcgeckoaddon.boss.profiles_title",
                Integer.toString(phaseIndex + 1)).getString(), guiLeft + 8, guiTop + 8, ThemeLabel.TITLE_COLOR));
        for (int i = 0; i < rows.size(); i++) {
            int kind = rows.get(i).kind();
            int y = guiTop + FIRST_ROW_Y + i * ROW_HEIGHT;
            addButton(new ThemeButton(this, FIRST_ABILITY_BUTTON + i, guiLeft + 8, y,
                    ABILITY_BUTTON_WIDTH, BUTTON_HEIGHT, BossAbilityKind.LABELS[kind]));
            addLabel(new ThemeLabel(FIRST_SUMMARY_LABEL + i, Component.literal(summary(phase.profile(kind))),
                    CustomNpcResourceListener.DefaultTextColor, guiLeft + SUMMARY_X, y + 6, SUMMARY_WIDTH, LINE_HEIGHT));
        }
        addWrappedText(LEGEND_LABEL, legend(), guiTop + legendY());
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, BUTTON_HEIGHT);
    }

    /** Where the legend starts, from the panel's top: just under the last row. */
    private int legendY() {
        return FIRST_ROW_Y + rows.size() * ROW_HEIGHT + 4;
    }

    /** Where the done button goes, from the panel's top: just under the legend. */
    private int doneButtonY() {
        return legendY() + wrapLines(legend(), imageWidth - 16).size() * LINE_HEIGHT + 4;
    }

    /** What the dash in an untouched row stands for. */
    private static String legend() {
        return DEFAULT_SUMMARY + " " + I18n.get("cnpcgeckoaddon.boss.profile_default");
    }

    /**
     * What the profile changes, shortest first: "\"Inferno\" · P1 · 80 % · 3/phase · T1" for the
     * name it gives the ability, the priority, the chance, the limit and a damage type other than
     * the plain one, an ellipsis for anything else it changes, a dash for nothing. Built from
     * numbers and the builder's own name, with the phase word the phase menu already has; cut short
     * to the column, ellipsis and all, rather than run under the panel's edge.
     */
    private String summary(BossAbilityProfile profile) {
        if (profile.isDefault()) {
            return DEFAULT_SUMMARY;
        }
        List<String> parts = new ArrayList<>();
        if (!profile.getDisplayName().isEmpty()) {
            // First, so it is the last thing the column cuts; a name too long for the column on its
            // own is cut itself, under its ellipsis.
            parts.add(fitted("\"" + profile.getDisplayName() + "\""));
        }
        if (profile.getPriority() > 0) {
            parts.add("P" + profile.getPriority());
        }
        if (profile.getCastChancePercent() < BossAbilityProfile.MAX_CAST_CHANCE) {
            parts.add(profile.getCastChancePercent() + " %");
        }
        if (profile.getMaxCastsPerPhase() > BossAbilityProfile.NO_CAST_LIMIT) {
            parts.add(profile.getMaxCastsPerPhase() + "/" + I18n.get("cnpcgeckoaddon.boss.phase").toLowerCase(Locale.ROOT));
        }
        // T1 through armour, T2 true damage: the number the editor's choice has in the profile.
        if (profile.getDamageType() != BossAbilityProfile.DAMAGE_NORMAL) {
            parts.add("T" + profile.getDamageType());
        }
        // Whatever else the profile changes - the timer, the conditions, the rage - is one mark: the
        // row has no room to spell it out, and the editor is a click away.
        BossAbilityProfile rest = new BossAbilityProfile();
        rest.copyFrom(profile);
        rest.setDisplayName("");
        rest.setPriority(0);
        rest.setCastChancePercent(BossAbilityProfile.MAX_CAST_CHANCE);
        rest.setMaxCastsPerPhase(BossAbilityProfile.NO_CAST_LIMIT);
        rest.setDamageType(BossAbilityProfile.DAMAGE_NORMAL);
        if (!rest.isDefault()) {
            parts.add(MORE);
        }
        String text = String.join(SEPARATOR, parts);
        // The ellipsis only ever ends the list, so a cut list never holds it and gets one added.
        for (int kept = parts.size() - 1; kept > 0 && font.width(text) > SUMMARY_WIDTH; kept--) {
            text = String.join(SEPARATOR, parts.subList(0, kept)) + SEPARATOR + MORE;
        }
        // A name filling the column leaves no room even for the ellipsis after it.
        return fitted(text);
    }

    /** One part cut to the column on its own, ending in an ellipsis when it had to be cut. */
    private String fitted(String part) {
        if (font.width(part) <= SUMMARY_WIDTH) {
            return part;
        }
        return font.plainSubstrByWidth(part, SUMMARY_WIDTH - font.width(MORE)) + MORE;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        int row = button.id - FIRST_ABILITY_BUTTON;
        if (row < 0 || row >= rows.size()) {
            return;
        }
        setSubGui(new SubGuiBossAbilityProfile(this, phase, rows.get(row).kind()));
    }

    /** Back from an editor: the summaries are rebuilt with the screen, on its next frame. */
    @Override
    public void subGuiClosed(Screen subgui) {
        super.subGuiClosed(subgui);
        requestLayout();
    }
}
