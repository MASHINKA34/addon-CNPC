package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.ai.BossTotemUtil;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketRestoreBossTotems;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Boss-wide protection, activation, respawn, and beam settings. */
public final class SubGuiBossTotems extends SubGuiFieldScreen implements ITextfieldListener {
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

    private static final String[] BEAM_STYLE_LABELS = HookCordStyles.values().stream()
            .map(HookCordStyles.Style::translationKey).toArray(String[]::new);

    private final EntityNPCInterface npc;
    private final TeleportPathData data;

    public SubGuiBossTotems(EntityNPCInterface npc, TeleportPathData data) {
        this.npc = npc;
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 336;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.totem_title", guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;
        addYesNo(ENABLED_BUTTON, "cnpcgeckoaddon.boss.totem_enabled", y, data.isTotemsEnabled());
        y += 21;
        addWideYesNo(GRANT_INVULN_BUTTON, "cnpcgeckoaddon.boss.totem_grant_invuln", y,
                data.isTotemGrantInvulnerability());
        y += 21;
        // Directly under the flag it belongs to: with the ward off, the protection mode
        // below has nothing left to pick between.
        addChoice(PROTECTION_BUTTON, "cnpcgeckoaddon.boss.totem_protection", y,
                TeleportPathData.TOTEM_PROTECTION_LABELS, data.getTotemProtectionMode());
        y += 21;
        addWideYesNo(HOLD_BUTTON, "cnpcgeckoaddon.boss.totem_hold", y, data.isTotemHoldBoss());
        y += 21;
        addChoice(ACTIVATION_BUTTON, "cnpcgeckoaddon.boss.totem_activation", y,
                TeleportPathData.TOTEM_ACTIVATION_LABELS, data.getTotemActivationMode());
        y += 21;

        addNumberField(PHASE_FIELD, "cnpcgeckoaddon.boss.totem_activation_phase", y,
                data.getTotemActivationPhase(), TeleportPathData.MIN_PHASES,
                TeleportPathData.MAX_PHASES, 1);
        addNumberField(ACTIVATION_DELAY_FIELD, "cnpcgeckoaddon.boss.totem_delay", y,
                data.getTotemActivationDelayTicks(), TeleportPathData.MIN_TOTEM_ACTIVATION_DELAY_TICKS,
                TeleportPathData.MAX_TOTEM_DELAY_TICKS, 200);
        y += 21;

        addChoice(RESPAWN_BUTTON, "cnpcgeckoaddon.boss.totem_respawn", y,
                TeleportPathData.TOTEM_RESPAWN_LABELS, data.getTotemRespawnMode());
        y += 21;
        addNumberField(RESPAWN_DELAY_FIELD, "cnpcgeckoaddon.boss.totem_respawn_delay", y,
                data.getTotemRespawnDelayTicks(), TeleportPathData.MIN_TOTEM_RESPAWN_DELAY_TICKS,
                TeleportPathData.MAX_TOTEM_DELAY_TICKS, 200);
        y += 21;

        addYesNo(RESET_HEALTH_BUTTON, "cnpcgeckoaddon.boss.totem_reset_health", y,
                data.isTotemResetHealth());
        y += 21;
        addYesNo(REMOVE_DEATH_BUTTON, "cnpcgeckoaddon.boss.totem_remove_death", y,
                data.isTotemRemoveOnBossDeath());
        y += 21;
        addChoice(BEAM_STYLE_BUTTON, "cnpcgeckoaddon.boss.totem_beam", y,
                BEAM_STYLE_LABELS, beamStyleIndex(data.getTotemBeamStyle()));
        y += 21;

        addLabel(new GuiLabel(BEAM_WIDTH_FIELD, "cnpcgeckoaddon.boss.totem_beam_width_sag",
                guiLeft + 8, y + 6));
        addSmallNumber(BEAM_WIDTH_FIELD, guiLeft + 154, y, data.getTotemBeamWidthPercent(), 25, 400, 100);
        addSmallNumber(BEAM_SAG_FIELD, guiLeft + 200, y, data.getTotemBeamSagPercent(), 0, 200, 0);

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.boss.totem_hint", guiLeft + 8, guiTop + 277, 0xA0A0A0));
        addLabel(new GuiLabel(33, "cnpcgeckoaddon.boss.totem_hold_hint", guiLeft + 8, guiTop + 289, 0xA0A0A0));
        addLabel(new GuiLabel(32, "cnpcgeckoaddon.teleport.ticks_hint", guiLeft + 8, guiTop + 301, 0xA0A0A0));
        addButton(new GuiButtonNop(this, LIST_BUTTON, guiLeft + 8, guiTop + 312, 92, 20,
                "cnpcgeckoaddon.boss.totem_list"));
        addButton(new GuiButtonNop(this, RESTORE_BUTTON, guiLeft + 104, guiTop + 312, 88, 20,
                "cnpcgeckoaddon.boss.totem_restore"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 196, guiTop + 312, 46, 20,
                "gui.done", button -> close()));
        updateConditionalFields();
    }

    private void addYesNo(int id, String label, int y, boolean value) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, id, guiLeft + 142, y, 100, 20, value));
    }

    /**
     * A row whose label runs on past the toggle column, with the toggle pushed hard right.
     * "Totems grant invulnerability" is a sentence, not a word, and it does not fit the
     * width the one-word rows above leave it.
     */
    private void addWideYesNo(int id, String label, int y, boolean value) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, id, guiLeft + 196, y, 46, 20, value));
    }

    private void addChoice(int id, String label, int y, String[] values, int selected) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        addButton(new GuiButtonNop(this, id, guiLeft + 112, y, 130, 20, values, selected));
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

    private void updateConditionalFields() {
        boolean phase = data.getTotemActivationMode() == TeleportPathData.TOTEM_ACTIVATION_PHASE_ENTER;
        boolean timer = data.getTotemActivationMode() == TeleportPathData.TOTEM_ACTIVATION_ENCOUNTER_TIMER;
        setVisible(PHASE_FIELD, phase);
        setVisible(ACTIVATION_DELAY_FIELD, timer);
        setVisible(RESPAWN_DELAY_FIELD,
                data.getTotemRespawnMode() == TeleportPathData.TOTEM_RESPAWN_DELAYED);
    }

    private void setVisible(int id, boolean visible) {
        GuiLabel label = getLabel(id);
        if (label != null) label.visible = visible;
        GuiTextFieldNop field = getTextField(id);
        if (field != null) field.visible = visible;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            data.setTotemsEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == GRANT_INVULN_BUTTON) {
            data.setTotemGrantInvulnerability(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == HOLD_BUTTON) {
            data.setTotemHoldBoss(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == PROTECTION_BUTTON) {
            data.setTotemProtectionMode(button.getValue());
        } else if (button.id == ACTIVATION_BUTTON) {
            applyFields();
            data.setTotemActivationMode(button.getValue());
            updateConditionalFields();
        } else if (button.id == RESPAWN_BUTTON) {
            applyFields();
            data.setTotemRespawnMode(button.getValue());
            updateConditionalFields();
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
    public void unFocused(GuiTextFieldNop field) { applyFields(); }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        GuiTextFieldNop phase = getTextField(PHASE_FIELD);
        if (phase != null) data.setTotemActivationPhase(phase.getInteger());
        GuiTextFieldNop activationDelay = getTextField(ACTIVATION_DELAY_FIELD);
        if (activationDelay != null) data.setTotemActivationDelayTicks(activationDelay.getInteger());
        GuiTextFieldNop respawnDelay = getTextField(RESPAWN_DELAY_FIELD);
        if (respawnDelay != null) data.setTotemRespawnDelayTicks(respawnDelay.getInteger());
        GuiTextFieldNop width = getTextField(BEAM_WIDTH_FIELD);
        if (width != null) data.setTotemBeamWidthPercent(width.getInteger());
        GuiTextFieldNop sag = getTextField(BEAM_SAG_FIELD);
        if (sag != null) data.setTotemBeamSagPercent(sag.getInteger());
    }
}
