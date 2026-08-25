package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.ai.BossHealthScalingUtil;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

import java.util.Locale;

/** Configures the numeric party bonus independently of boss-bar visibility. */
public final class SubGuiBossHealthScaling extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int MODE_BUTTON = 2;
    private static final int PERCENT_FIELD = 3;
    private static final int FLAT_FIELD = 4;
    private static final int UPDATE_BUTTON = 5;
    private static final int ADJUSTMENT_BUTTON = 6;
    private static final int CAP_FIELD = 7;
    private static final int INTERVAL_FIELD = 8;
    private static final int BASE_PREVIEW_LABEL = 30;
    private static final int ONE_PREVIEW_LABEL = 31;
    private static final int TWO_PREVIEW_LABEL = 32;
    private static final int FOUR_PREVIEW_LABEL = 33;
    private static final int CAP_PREVIEW_LABEL = 34;

    private final EntityNPCInterface npc;
    private final TeleportPathData data;

    public SubGuiBossHealthScaling(EntityNPCInterface npc, TeleportPathData data) {
        this.npc = npc;
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(40, "cnpcgeckoaddon.boss.health_scaling_title",
                guiLeft + 6, guiTop + 4, 0xFFFFFF));

        addYesNo(ENABLED_BUTTON, "cnpcgeckoaddon.boss.health_scaling_enabled",
                guiTop + 16, data.isHealthScalingEnabled());
        addCycle(MODE_BUTTON, "cnpcgeckoaddon.boss.health_scaling_mode", guiTop + 36,
                TeleportPathData.HEALTH_SCALING_MODE_LABELS, data.getHealthScalingMode());
        addNumberField(PERCENT_FIELD, "cnpcgeckoaddon.boss.health_scaling_percent", guiTop + 56,
                data.getHealthPerPlayerPercent(), TeleportPathData.MIN_HEALTH_PER_PLAYER_PERCENT,
                TeleportPathData.MAX_HEALTH_PER_PLAYER_PERCENT, 50);
        addNumberField(FLAT_FIELD, "cnpcgeckoaddon.boss.health_scaling_flat", guiTop + 76,
                data.getHealthPerPlayerFlat(), TeleportPathData.MIN_HEALTH_PER_PLAYER_FLAT,
                TeleportPathData.MAX_HEALTH_PER_PLAYER_FLAT, 20);
        addCycle(UPDATE_BUTTON, "cnpcgeckoaddon.boss.health_scaling_update", guiTop + 96,
                TeleportPathData.HEALTH_SCALING_UPDATE_LABELS, data.getHealthScalingUpdateMode());
        addCycle(ADJUSTMENT_BUTTON, "cnpcgeckoaddon.boss.health_scaling_adjust", guiTop + 116,
                TeleportPathData.HEALTH_SCALING_ADJUSTMENT_LABELS, data.getHealthScalingAdjustment());
        addNumberField(CAP_FIELD, "cnpcgeckoaddon.boss.health_scaling_cap", guiTop + 136,
                data.getHealthScalingPlayerCap(), TeleportPathData.MIN_HEALTH_SCALING_PLAYER_CAP,
                TeleportPathData.MAX_HEALTH_SCALING_PLAYER_CAP, 8);
        addNumberField(INTERVAL_FIELD, "cnpcgeckoaddon.boss.health_scaling_interval", guiTop + 156,
                data.getHealthScalingRecheckTicks(), TeleportPathData.MIN_HEALTH_SCALING_RECHECK_TICKS,
                TeleportPathData.MAX_HEALTH_SCALING_RECHECK_TICKS, 20);

        addLabel(componentLabel(BASE_PREVIEW_LABEL, guiLeft + 6, guiTop + 178));
        addLabel(componentLabel(ONE_PREVIEW_LABEL, guiLeft + 6, guiTop + 189));
        addLabel(componentLabel(TWO_PREVIEW_LABEL, guiLeft + 126, guiTop + 189));
        addLabel(componentLabel(FOUR_PREVIEW_LABEL, guiLeft + 6, guiTop + 200));
        addLabel(componentLabel(CAP_PREVIEW_LABEL, guiLeft + 126, guiTop + 200));
        addLabel(new GuiLabel(41, "cnpcgeckoaddon.boss.health_scaling_hint_first",
                guiLeft + 6, guiTop + 212, 0xA0A0A0));
        addLabel(new GuiLabel(42, "cnpcgeckoaddon.boss.health_scaling_hint_second",
                guiLeft + 6, guiTop + 222, 0xA0A0A0));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 234, 60, 18,
                "gui.done", button -> close()));
        refreshControlsAndPreview();
    }

    private void addYesNo(int id, String label, int y, boolean value) {
        addLabel(new GuiLabel(id, label, guiLeft + 6, y + 5));
        addButton(new GuiButtonYesNo(this, id, guiLeft + 151, y, 91, 18, value));
    }

    private void addCycle(int id, String label, int y, String[] values, int value) {
        addLabel(new GuiLabel(id, label, guiLeft + 6, y + 5));
        addButton(new GuiButtonNop(this, id, guiLeft + 126, y, 116, 18, values, value));
    }

    private void addNumberField(int id, String label, int y, int value,
                                int min, int max, int fallback) {
        addLabel(new GuiLabel(id, label, guiLeft + 6, y + 5));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + 176, y, 66, 18,
                Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    private GuiLabel componentLabel(int id, int x, int y) {
        return new GuiLabel(id, Component.empty(), 0xA0A0A0, x, y, 110, 10);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        applyFields();
        if (button.id == ENABLED_BUTTON) {
            data.setHealthScalingEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == MODE_BUTTON) {
            data.setHealthScalingMode(button.getValue());
        } else if (button.id == UPDATE_BUTTON) {
            data.setHealthScalingUpdateMode(button.getValue());
        } else if (button.id == ADJUSTMENT_BUTTON) {
            data.setHealthScalingAdjustment(button.getValue());
        }
        refreshControlsAndPreview();
    }

    private void refreshControlsAndPreview() {
        GuiTextFieldNop percent = getTextField(PERCENT_FIELD);
        GuiTextFieldNop flat = getTextField(FLAT_FIELD);
        GuiTextFieldNop interval = getTextField(INTERVAL_FIELD);
        if (percent != null) {
            percent.enabled = data.getHealthScalingMode() != TeleportPathData.HEALTH_SCALING_FLAT;
        }
        if (flat != null) {
            flat.enabled = data.getHealthScalingMode() != TeleportPathData.HEALTH_SCALING_PERCENT;
        }
        if (interval != null) {
            interval.enabled = data.getHealthScalingUpdateMode() == TeleportPathData.HEALTH_SCALING_DYNAMIC;
        }

        double base = BossHealthScalingUtil.getMaxHealthWithoutPartyScaling(npc);
        setPreview(BASE_PREVIEW_LABEL, Component.translatable(
                "cnpcgeckoaddon.boss.health_scaling_base", formatHealth(base)));
        setPlayerPreview(ONE_PREVIEW_LABEL, 1, base);
        setPlayerPreview(TWO_PREVIEW_LABEL, 2, base);
        setPlayerPreview(FOUR_PREVIEW_LABEL, 4, base);
        setPlayerPreview(CAP_PREVIEW_LABEL, data.getHealthScalingPlayerCap(), base);
    }

    private void setPlayerPreview(int labelId, int players, double base) {
        setPreview(labelId, Component.translatable("cnpcgeckoaddon.boss.health_scaling_preview",
                players, formatHealth(data.calculateScaledMaxHealth(base, players))));
    }

    private void setPreview(int id, Component text) {
        GuiLabel label = getLabel(id);
        if (label != null) {
            label.setMessage(text);
        }
    }

    private static String formatHealth(double value) {
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value)
                .replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        applyFields();
        refreshControlsAndPreview();
    }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        GuiTextFieldNop percent = getTextField(PERCENT_FIELD);
        if (percent != null) data.setHealthPerPlayerPercent(percent.getInteger());
        GuiTextFieldNop flat = getTextField(FLAT_FIELD);
        if (flat != null) data.setHealthPerPlayerFlat(flat.getInteger());
        GuiTextFieldNop cap = getTextField(CAP_FIELD);
        if (cap != null) data.setHealthScalingPlayerCap(cap.getInteger());
        GuiTextFieldNop interval = getTextField(INTERVAL_FIELD);
        if (interval != null) data.setHealthScalingRecheckTicks(interval.getInteger());
    }
}
