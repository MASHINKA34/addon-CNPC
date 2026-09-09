package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.ai.BossTotemUtil;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketRestoreBossTotems;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.ArrayList;
import java.util.List;

/** Boss-wide protection, activation, respawn, and beam settings. */
public final class SubGuiBossTotems extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int PROTECTION_BUTTON = 2;
    private static final int ACTIVATION_BUTTON = 3;
    private static final int PHASE_FIELD = 4;
    private static final int ACTIVATION_DELAY_FIELD = 5;
    private static final int RESPAWN_BUTTON = 6;
    private static final int RESPAWN_DELAY_FIELD = 7;
    private static final int RESET_HEALTH_BUTTON = 8;
    private static final int REMOVE_DEATH_BUTTON = 9;
    private static final int BEAM_STYLE_BUTTON = 10;
    private static final int BEAM_WIDTH_FIELD = 11;
    private static final int BEAM_SAG_FIELD = 12;
    private static final int LIST_BUTTON = 13;
    private static final int RESTORE_BUTTON = 14;
    private static final int GRANT_INVULN_BUTTON = 15;
    private static final int HOLD_BUTTON = 16;
    private static final int SILENCE_BUTTON = 17;
    private static final int UNTARGETABLE_BUTTON = 18;

    private static final int TITLE_LABEL = 30;
    /** Ten label ids per hint, which is more lines than any locale will need. */
    private static final int HINT_LABEL = 40;
    /** Where the continuation lines of a wrapped row label are counted from. */
    private static final int WRAPPED_LABEL = 80;

    /** The first row's offset from the top of the panel, clear of the title. */
    private static final int FIRST_ROW = 18;
    private static final int CONTROL_HEIGHT = 20;
    private static final int ROW = CONTROL_HEIGHT + 1;
    private static final int LABEL_X = 8;
    /** Where the toggle column starts; also where a row label has to stop. */
    private static final int TOGGLE_X = 196;
    private static final int TOGGLE_WIDTH = 46;
    private static final int CHOICE_X = 112;
    private static final int CHOICE_WIDTH = 130;
    /** The right edge every row lines up on. */
    private static final int ROW_RIGHT = TOGGLE_X + TOGGLE_WIDTH;
    private static final int LABEL_DROP = 6;
    private static final int HINTS_GAP = 4;
    private static final int HINT_GAP = 3;
    private static final int BUTTONS_GAP = 5;
    private static final int BOTTOM_MARGIN = 8;

    private static final String[] BEAM_STYLE_LABELS = HookCordStyles.values().stream()
            .map(HookCordStyles.Style::translationKey).toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final TeleportPathData data;
    private int wrappedLabel;

    public SubGuiBossTotems(EntityNPCInterface npc, TeleportPathData data) {
        this.npc = npc;
        this.data = data;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // The panel is centred from imageHeight, so the height has to be settled before
        // super.init() reads it - and it depends both on which rows this activation and
        // respawn mode show and on how many lines the locale wraps the hints to.
        imageHeight = layout(false);
        super.init();
        layout(true);
    }

    /**
     * Puts every row down the panel, or with {@code place} false only measures how tall they
     * come to.
     *
     * <p>Measuring with the code that does the placing is what keeps the panel's height and its
     * contents from drifting apart; the alternative is a second copy of the same arithmetic
     * that goes stale the first time a row moves. Nothing here reads {@code guiTop} except the
     * placing branches, because on the measuring pass it is still the previous layout's.</p>
     *
     * @return the height the panel needs
     */
    private int layout(boolean place) {
        wrappedLabel = WRAPPED_LABEL;
        if (place) {
            addLabel(new GuiLabel(TITLE_LABEL, "cnpcgeckoaddon.boss.totem_title",
                    guiLeft + LABEL_X, guiTop + 5, 0xFFFFFF));
        }
        int y = FIRST_ROW;
        y = toggle(place, ENABLED_BUTTON, "cnpcgeckoaddon.boss.totem_enabled", y,
                data.isTotemsEnabled());
        y = toggle(place, GRANT_INVULN_BUTTON, "cnpcgeckoaddon.boss.totem_grant_invuln", y,
                data.isTotemGrantInvulnerability());
        // Directly under the flag it belongs to: with the ward off, the protection mode
        // below has nothing left to pick between.
        y = choice(place, PROTECTION_BUTTON, "cnpcgeckoaddon.boss.totem_protection", y,
                TeleportPathData.TOTEM_PROTECTION_LABELS, data.getTotemProtectionMode());
        y = toggle(place, HOLD_BUTTON, "cnpcgeckoaddon.boss.totem_hold", y, data.isTotemHoldBoss());
        y = toggle(place, SILENCE_BUTTON, "cnpcgeckoaddon.boss.totem_silence", y,
                data.isTotemSuppressAbilities());
        y = toggle(place, UNTARGETABLE_BUTTON, "cnpcgeckoaddon.boss.totem_untargetable", y,
                data.isTotemUntargetable());

        y = choice(place, ACTIVATION_BUTTON, "cnpcgeckoaddon.boss.totem_activation", y,
                TeleportPathData.TOTEM_ACTIVATION_LABELS, data.getTotemActivationMode());
        // A mode that needs no number leaves no row behind: an empty row reads as a layout
        // fault, and the panel is sized to whatever is actually on it.
        if (activationPhaseShown()) {
            y = number(place, PHASE_FIELD, "cnpcgeckoaddon.boss.totem_activation_phase", y,
                    data.getTotemActivationPhase(), TeleportPathData.MIN_PHASES,
                    TeleportPathData.MAX_PHASES, 1);
        } else if (activationDelayShown()) {
            y = number(place, ACTIVATION_DELAY_FIELD, "cnpcgeckoaddon.boss.totem_delay", y,
                    data.getTotemActivationDelayTicks(),
                    TeleportPathData.MIN_TOTEM_ACTIVATION_DELAY_TICKS,
                    TeleportPathData.MAX_TOTEM_DELAY_TICKS, 200);
        }

        y = choice(place, RESPAWN_BUTTON, "cnpcgeckoaddon.boss.totem_respawn", y,
                TeleportPathData.TOTEM_RESPAWN_LABELS, data.getTotemRespawnMode());
        if (respawnDelayShown()) {
            y = number(place, RESPAWN_DELAY_FIELD, "cnpcgeckoaddon.boss.totem_respawn_delay", y,
                    data.getTotemRespawnDelayTicks(),
                    TeleportPathData.MIN_TOTEM_RESPAWN_DELAY_TICKS,
                    TeleportPathData.MAX_TOTEM_DELAY_TICKS, 200);
        }

        y = toggle(place, RESET_HEALTH_BUTTON, "cnpcgeckoaddon.boss.totem_reset_health", y,
                data.isTotemResetHealth());
        y = toggle(place, REMOVE_DEATH_BUTTON, "cnpcgeckoaddon.boss.totem_remove_death", y,
                data.isTotemRemoveOnBossDeath());
        y = choice(place, BEAM_STYLE_BUTTON, "cnpcgeckoaddon.boss.totem_beam", y,
                BEAM_STYLE_LABELS, beamStyleIndex(data.getTotemBeamStyle()));
        y = beamShape(place, y);

        y = hints(place, y) + BUTTONS_GAP;
        return buttons(place, y);
    }

    /** A toggle row: the label down the left, a yes/no hard against the right edge. */
    private int toggle(boolean place, int id, String key, int y, boolean value) {
        if (place) {
            rowLabel(id, key, y, TOGGLE_X);
            addButton(new GuiButtonYesNo(this, id, guiLeft + TOGGLE_X, guiTop + y,
                    TOGGLE_WIDTH, CONTROL_HEIGHT, value));
        }
        return y + ROW;
    }

    /** A row whose setting is one of a fixed list of choices. */
    private int choice(boolean place, int id, String key, int y, String[] values, int selected) {
        if (place) {
            rowLabel(id, key, y, CHOICE_X);
            addButton(new GuiButtonNop(this, id, guiLeft + CHOICE_X, guiTop + y,
                    CHOICE_WIDTH, CONTROL_HEIGHT, values, selected));
        }
        return y + ROW;
    }

    /** A row holding one clamped number. */
    private int number(boolean place, int id, String key, int y, int value,
                       int min, int max, int fallback) {
        if (place) {
            addNumberField(id, key, guiTop + y, value, min, max, fallback);
        }
        return y + ROW;
    }

    /** Beam width and sag, which are narrow enough to share one row. */
    private int beamShape(boolean place, int y) {
        if (place) {
            rowLabel(BEAM_WIDTH_FIELD, "cnpcgeckoaddon.boss.totem_beam_width_sag", y, 154);
            addSmallNumber(BEAM_WIDTH_FIELD, guiLeft + 154, guiTop + y,
                    data.getTotemBeamWidthPercent(), 25, 400, 100);
            addSmallNumber(BEAM_SAG_FIELD, guiLeft + 200, guiTop + y,
                    data.getTotemBeamSagPercent(), 0, 200, 0);
        }
        return y + ROW;
    }

    /**
     * The grey block explaining what the wards actually do.
     *
     * <p>All of it goes through the wrapper rather than being placed line by line: "The boss is
     * protected while any linked totem lives" is 252px against the 240px the panel has, and a
     * GuiLabel would simply have drawn it off the edge. The tick hint only earns its two lines
     * when a field measured in ticks is on the screen to explain.</p>
     */
    private int hints(boolean place, int y) {
        List<String> keys = hintKeys();
        y += HINTS_GAP;
        for (int i = 0; i < keys.size(); i++) {
            if (place) {
                addWrappedHint(HINT_LABEL + i * 10, keys.get(i), guiTop + y);
            }
            y += wrappedHintHeight(keys.get(i)) + HINT_GAP;
        }
        return y - HINT_GAP;
    }

    private List<String> hintKeys() {
        List<String> keys = new ArrayList<>(List.of(
                "cnpcgeckoaddon.boss.totem_hint",
                "cnpcgeckoaddon.boss.totem_hold_hint",
                "cnpcgeckoaddon.boss.totem_silence_hint"));
        if (activationDelayShown() || respawnDelayShown()) {
            keys.add("cnpcgeckoaddon.teleport.ticks_hint");
        }
        return keys;
    }

    /**
     * The last two rows of the screen: the two actions, then the way out.
     *
     * <p>They take two rows rather than one because "Restore all now" is 134px in Russian and
     * a Button clips its label to its own width, so the three of them side by side would have
     * left it reading as half a word.</p>
     */
    private int buttons(boolean place, int y) {
        if (place) {
            addButton(new GuiButtonNop(this, LIST_BUTTON, guiLeft + LABEL_X, guiTop + y,
                    ROW_RIGHT - LABEL_X, CONTROL_HEIGHT, "cnpcgeckoaddon.boss.totem_list"));
        }
        y += ROW;
        if (place) {
            addButton(new GuiButtonNop(this, RESTORE_BUTTON, guiLeft + LABEL_X, guiTop + y,
                    TOGGLE_X - LABEL_X - 6, CONTROL_HEIGHT, "cnpcgeckoaddon.boss.totem_restore"));
            addDoneButton(guiLeft + TOGGLE_X, guiTop + y, TOGGLE_WIDTH, CONTROL_HEIGHT);
        }
        return y + CONTROL_HEIGHT + BOTTOM_MARGIN;
    }

    /**
     * The label down the left of a row, wrapped rather than run under the control beside it.
     *
     * <p>Not hypothetical: Russian writes "Totems hide from abilities" at 203px against the
     * 184px this column leaves, and a GuiLabel neither wraps nor clips. Two lines of text still
     * fit inside the height of the control they sit beside, so a wrapped row costs no height.</p>
     */
    private void rowLabel(int id, String key, int y, int controlX) {
        int width = controlX - LABEL_X - 4;
        List<String> lines = wrapLines(I18n.get(key), width);
        if (lines.size() == 1) {
            addLabel(new GuiLabel(id, key, guiLeft + LABEL_X, guiTop + y + LABEL_DROP));
            return;
        }
        int top = guiTop + y + (CONTROL_HEIGHT - lines.size() * LINE_HEIGHT) / 2;
        for (int i = 0; i < lines.size(); i++) {
            addLabel(new GuiLabel(i == 0 ? id : wrappedLabel++, Component.literal(lines.get(i)),
                    CustomNpcResourceListener.DefaultTextColor, guiLeft + LABEL_X,
                    top + i * LINE_HEIGHT, width, LINE_HEIGHT));
        }
    }

    private boolean activationPhaseShown() {
        return data.getTotemActivationMode() == TeleportPathData.TOTEM_ACTIVATION_PHASE_ENTER;
    }

    private boolean activationDelayShown() {
        return data.getTotemActivationMode() == TeleportPathData.TOTEM_ACTIVATION_ENCOUNTER_TIMER;
    }

    private boolean respawnDelayShown() {
        return data.getTotemRespawnMode() == TeleportPathData.TOTEM_RESPAWN_DELAYED;
    }

    private void addSmallNumber(int id, int x, int y, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, 42, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    private int beamStyleIndex(String id) {
        for (int i = 0; i < HookCordStyles.values().size(); i++) {
            if (HookCordStyles.values().get(i).id().equals(id)) return i;
        }
        return 0;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            data.setTotemsEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == GRANT_INVULN_BUTTON) {
            data.setTotemGrantInvulnerability(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == HOLD_BUTTON) {
            data.setTotemHoldBoss(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == SILENCE_BUTTON) {
            data.setTotemSuppressAbilities(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == UNTARGETABLE_BUTTON) {
            data.setTotemUntargetable(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == PROTECTION_BUTTON) {
            data.setTotemProtectionMode(button.getValue());
        } else if (button.id == ACTIVATION_BUTTON) {
            applyFields();
            data.setTotemActivationMode(button.getValue());
            requestLayout();
        } else if (button.id == RESPAWN_BUTTON) {
            applyFields();
            data.setTotemRespawnMode(button.getValue());
            requestLayout();
        } else if (button.id == RESET_HEALTH_BUTTON) {
            data.setTotemResetHealth(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == REMOVE_DEATH_BUTTON) {
            data.setTotemRemoveOnBossDeath(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == BEAM_STYLE_BUTTON) {
            data.setTotemBeamStyle(HookCordStyles.values().get(button.getValue()).id());
        } else if (button.id == LIST_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossTotemList(npc, data));
        } else if (button.id == RESTORE_BUTTON) {
            npc.getPersistentData().remove(BossTotemUtil.DEAD_SLOTS_KEY);
            NetworkWrapper.sendToServer(new PacketRestoreBossTotems(npc.getId()));
        }
    }

    @Override
    protected void applyFields() {
        applyNumberField(PHASE_FIELD, data::setTotemActivationPhase);
        applyNumberField(ACTIVATION_DELAY_FIELD, data::setTotemActivationDelayTicks);
        applyNumberField(RESPAWN_DELAY_FIELD, data::setTotemRespawnDelayTicks);
        applyNumberField(BEAM_WIDTH_FIELD, data::setTotemBeamWidthPercent);
        applyNumberField(BEAM_SAG_FIELD, data::setTotemBeamSagPercent);
    }
}
