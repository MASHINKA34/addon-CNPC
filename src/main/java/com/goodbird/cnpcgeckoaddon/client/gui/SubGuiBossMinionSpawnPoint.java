package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossMinionSpawnPoint;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Coordinates, clone override, facing, and weight for one stable point. */
public final class SubGuiBossMinionSpawnPoint extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int COORDINATE_BUTTON = 2;
    private static final int X_FIELD = 3;
    private static final int Y_FIELD = 4;
    private static final int Z_FIELD = 5;
    private static final int CLONE_TAB_FIELD = 6;
    private static final int CLONE_NAME_FIELD = 7;
    private static final int YAW_FIELD = 8;
    private static final int WEIGHT_FIELD = 9;
    private static final int HERE_BUTTON = 10;
    private static final int DELETE_BUTTON = 11;
    private static final int ARENA_HINT_LABEL = 12;

    private static final String[] COORDINATE_LABELS = {
            "cnpcgeckoaddon.boss.minion_spawn_arena",
            "cnpcgeckoaddon.boss.minion_spawn_fixed"
    };

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private final int index;
    private final BossMinionSpawnPoint point;

    public SubGuiBossMinionSpawnPoint(EntityNPCInterface npc, BossPhaseData phase,
                                      int phaseIndex, int index) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        this.index = index;
        this.point = phase.getMinionSpawnPoints().get(index);
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.minion_spawn_point_title", phaseIndex),
                guiLeft + 8, guiTop + 7, 0xFFFFFF));
        int y = guiTop + 25;
        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled",
                guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20,
                point.isEnabled()));
        y += 23;

        addLabel(new GuiLabel(COORDINATE_BUTTON, "cnpcgeckoaddon.boss.minion_spawn_coordinate",
                guiLeft + 8, y + 6));
        addButton(new GuiButtonNop(this, COORDINATE_BUTTON, guiLeft + 112, y, 130, 20,
                COORDINATE_LABELS, point.getCoordinateMode()));
        y += 23;

        addLabel(new GuiLabel(X_FIELD, "cnpcgeckoaddon.boss.minion_spawn_xyz", guiLeft + 8, y + 6));
        addTextField(signedField(X_FIELD, guiLeft + 76, y, point.getX()));
        addTextField(signedField(Y_FIELD, guiLeft + 132, y, point.getY()));
        addTextField(signedField(Z_FIELD, guiLeft + 188, y, point.getZ()));
        y += 23;

        addLabel(new GuiLabel(CLONE_NAME_FIELD, "cnpcgeckoaddon.boss.minion_spawn_clone_override",
                guiLeft + 8, y + 6));
        addTextField(numberField(CLONE_TAB_FIELD, guiLeft + 76, y, 30,
                point.getCloneTabOverride(), 0, 9, 0));
        addTextField(new GuiTextFieldNop(CLONE_NAME_FIELD, this, guiLeft + 110, y, 132, 20,
                point.getCloneNameOverride()));
        y += 23;

        addLabel(new GuiLabel(YAW_FIELD, "cnpcgeckoaddon.boss.minion_spawn_yaw", guiLeft + 8, y + 6));
        GuiTextFieldNop yaw = new GuiTextFieldNop(YAW_FIELD, this, guiLeft + 172, y, 70, 20,
                Float.toString(point.getYaw()));
        yaw.setFloatsOnly();
        yaw.setMinMaxDefault(-180.0F, 180.0F, 0.0F);
        addTextField(yaw);
        y += 23;

        addLabel(new GuiLabel(WEIGHT_FIELD, "cnpcgeckoaddon.boss.minion_spawn_weight",
                guiLeft + 8, y + 6));
        addTextField(numberField(WEIGHT_FIELD, guiLeft + 172, y, 70, point.getWeight(), 1, 100, 1));

        addLabel(new GuiLabel(ARENA_HINT_LABEL, "cnpcgeckoaddon.boss.minion_spawn_arena_hint",
                guiLeft + 8, guiTop + 181, 0xA0A0A0));
        addButton(new GuiButtonNop(this, HERE_BUTTON, guiLeft + 8, guiTop + 230, 92, 20,
                "cnpcgeckoaddon.boss.minion_spawn_here"));
        addButton(new GuiButtonNop(this, DELETE_BUTTON, guiLeft + 104, guiTop + 230, 72, 20,
                "cnpcgeckoaddon.boss.minion_spawn_delete"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 230, 60, 20,
                "gui.done", button -> close()));
        updateCoordinateHint();
    }

    private GuiTextFieldNop numberField(int id, int x, int y, int width, int value,
                                        int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, width, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        return field;
    }

    private GuiTextFieldNop signedField(int id, int x, int y, int value) {
        return new GuiTextFieldNop(id, this, x, y, 52, 20, Integer.toString(value));
    }

    private void updateCoordinateHint() {
        GuiLabel hint = getLabel(ARENA_HINT_LABEL);
        if (hint != null) {
            hint.visible = point.getCoordinateMode() == BossMinionSpawnPoint.COORDINATE_ARENA_OFFSET;
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            point.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == COORDINATE_BUTTON) {
            applyFields();
            point.setCoordinateMode(button.getValue());
            updateCoordinateHint();
        } else if (button.id == HERE_BUTTON) {
            takePlayerPosition();
        } else if (button.id == DELETE_BUTTON) {
            phase.getMinionSpawnPoints().remove(index);
            super.close();
        }
    }

    private void takePlayerPosition() {
        applyFields();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        BlockPos playerPos = player.blockPosition();
        if (point.getCoordinateMode() == BossMinionSpawnPoint.COORDINATE_FIXED) {
            point.setPosition(playerPos.getX(), playerPos.getY(), playerPos.getZ());
        } else {
            BlockPos bossPos = npc.blockPosition();
            point.setPosition(playerPos.getX() - bossPos.getX(), playerPos.getY() - bossPos.getY(),
                    playerPos.getZ() - bossPos.getZ());
        }
        showPosition();
    }

    private void showPosition() {
        getTextField(X_FIELD).setValue(Integer.toString(point.getX()));
        getTextField(Y_FIELD).setValue(Integer.toString(point.getY()));
        getTextField(Z_FIELD).setValue(Integer.toString(point.getZ()));
    }

    @Override
    public void unFocused(GuiTextFieldNop field) { applyFields(); }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        point.setPosition(signed(X_FIELD), signed(Y_FIELD), signed(Z_FIELD));
        GuiTextFieldNop tab = getTextField(CLONE_TAB_FIELD);
        if (tab != null) point.setCloneTabOverride(tab.getInteger());
        GuiTextFieldNop name = getTextField(CLONE_NAME_FIELD);
        if (name != null) point.setCloneNameOverride(name.getValue());
        GuiTextFieldNop yaw = getTextField(YAW_FIELD);
        if (yaw != null) point.setYaw(yaw.getFloat());
        GuiTextFieldNop weight = getTextField(WEIGHT_FIELD);
        if (weight != null) point.setWeight(weight.getInteger());
    }

    private int signed(int id) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) return 0;
        String value = field.getValue().trim();
        try {
            return value.isEmpty() || value.equals("-") ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
