package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossTotemEntry;
import com.goodbird.cnpcgeckoaddon.data.HookCordStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
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

/** Exact clone, anchor, facing, and per-link overrides for one stable slot. */
public final class SubGuiBossTotemEntry extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int CLONE_TAB_FIELD = 2;
    private static final int CLONE_NAME_FIELD = 3;
    private static final int COORDINATE_BUTTON = 4;
    private static final int X_FIELD = 5;
    private static final int Y_FIELD = 6;
    private static final int Z_FIELD = 7;
    private static final int YAW_FIELD = 8;
    private static final int BEAM_STYLE_BUTTON = 9;
    private static final int BEAM_WIDTH_FIELD = 10;
    private static final int HERE_BUTTON = 11;
    private static final int DELETE_BUTTON = 12;

    private static final String[] COORDINATE_LABELS = {
            "cnpcgeckoaddon.boss.totem_arena_offset",
            "cnpcgeckoaddon.boss.totem_fixed"
    };
    private static final String[] BEAM_OVERRIDE_LABELS;

    static {
        BEAM_OVERRIDE_LABELS = new String[HookCordStyles.values().size() + 1];
        BEAM_OVERRIDE_LABELS[0] = "cnpcgeckoaddon.boss.totem_beam_default";
        for (int i = 0; i < HookCordStyles.values().size(); i++) {
            BEAM_OVERRIDE_LABELS[i + 1] = HookCordStyles.values().get(i).translationKey();
        }
    }

    private final EntityNPCInterface npc;
    private final TeleportPathData data;
    private final int index;
    private final BossTotemEntry entry;

    public SubGuiBossTotemEntry(EntityNPCInterface npc, TeleportPathData data, int index) {
        this.npc = npc;
        this.data = data;
        this.index = index;
        this.entry = data.getTotems().get(index);
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 216;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.totem_entry_title",
                guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 25;
        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.totem_entry_enabled",
                guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, entry.isEnabled()));
        y += 23;

        addLabel(new GuiLabel(CLONE_TAB_FIELD, "cnpcgeckoaddon.boss.totem_clone", guiLeft + 8, y + 6));
        addTextField(numberField(CLONE_TAB_FIELD, guiLeft + 76, y, 30, entry.getCloneTab(), 1, 9, 1));
        addTextField(new GuiTextFieldNop(CLONE_NAME_FIELD, this, guiLeft + 110, y, 132, 20,
                entry.getCloneName()));
        y += 23;

        addLabel(new GuiLabel(COORDINATE_BUTTON, "cnpcgeckoaddon.boss.totem_coordinate_mode",
                guiLeft + 8, y + 6));
        addButton(new GuiButtonNop(this, COORDINATE_BUTTON, guiLeft + 112, y, 130, 20,
                COORDINATE_LABELS, entry.getCoordinateMode()));
        y += 23;

        addLabel(new GuiLabel(X_FIELD, "cnpcgeckoaddon.boss.totem_xyz", guiLeft + 8, y + 6));
        addTextField(signedField(X_FIELD, guiLeft + 76, y, entry.getX()));
        addTextField(signedField(Y_FIELD, guiLeft + 132, y, entry.getY()));
        addTextField(signedField(Z_FIELD, guiLeft + 188, y, entry.getZ()));
        y += 23;

        addLabel(new GuiLabel(YAW_FIELD, "cnpcgeckoaddon.boss.totem_yaw", guiLeft + 8, y + 6));
        GuiTextFieldNop yaw = new GuiTextFieldNop(YAW_FIELD, this, guiLeft + 172, y, 70, 20,
                Float.toString(entry.getYaw()));
        yaw.setFloatsOnly();
        yaw.setMinMaxDefault(-180.0F, 180.0F, 0.0F);
        addTextField(yaw);
        y += 23;

        addLabel(new GuiLabel(BEAM_STYLE_BUTTON, "cnpcgeckoaddon.boss.totem_beam_override",
                guiLeft + 8, y + 6));
        addButton(new GuiButtonNop(this, BEAM_STYLE_BUTTON, guiLeft + 112, y, 130, 20,
                BEAM_OVERRIDE_LABELS, beamOverrideIndex()));
        y += 23;

        addLabel(new GuiLabel(BEAM_WIDTH_FIELD, "cnpcgeckoaddon.boss.totem_beam_width_override",
                guiLeft + 8, y + 6));
        addTextField(numberField(BEAM_WIDTH_FIELD, guiLeft + 172, y, 70,
                entry.getBeamWidthPercentOverride(), 0, 400, 0));

        addButton(new GuiButtonNop(this, HERE_BUTTON, guiLeft + 8, guiTop + 190, 92, 20,
                "cnpcgeckoaddon.boss.totem_here"));
        addButton(new GuiButtonNop(this, DELETE_BUTTON, guiLeft + 104, guiTop + 190, 72, 20,
                "cnpcgeckoaddon.boss.totem_delete"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 190, 60, 20,
                "gui.done", button -> close()));
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

    private int beamOverrideIndex() {
        if (entry.getBeamStyleOverride().isEmpty()) return 0;
        for (int i = 0; i < HookCordStyles.values().size(); i++) {
            if (HookCordStyles.values().get(i).id().equals(entry.getBeamStyleOverride())) return i + 1;
        }
        return 0;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            entry.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == COORDINATE_BUTTON) {
            applyFields();
            entry.setCoordinateMode(button.getValue());
        } else if (button.id == BEAM_STYLE_BUTTON) {
            int selected = button.getValue();
            entry.setBeamStyleOverride(selected == 0 ? "" : HookCordStyles.values().get(selected - 1).id());
        } else if (button.id == HERE_BUTTON) {
            takePlayerPosition();
        } else if (button.id == DELETE_BUTTON) {
            data.getTotems().remove(index);
            super.close();
        }
    }

    private void takePlayerPosition() {
        applyFields();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        BlockPos playerPos = player.blockPosition();
        if (entry.getCoordinateMode() == BossTotemEntry.COORDINATE_FIXED) {
            entry.setPosition(playerPos.getX(), playerPos.getY(), playerPos.getZ());
        } else {
            BlockPos bossPos = npc.blockPosition();
            entry.setPosition(playerPos.getX() - bossPos.getX(), playerPos.getY() - bossPos.getY(),
                    playerPos.getZ() - bossPos.getZ());
        }
        showPosition();
    }

    private void showPosition() {
        getTextField(X_FIELD).setValue(Integer.toString(entry.getX()));
        getTextField(Y_FIELD).setValue(Integer.toString(entry.getY()));
        getTextField(Z_FIELD).setValue(Integer.toString(entry.getZ()));
    }

    @Override
    public void unFocused(GuiTextFieldNop field) { applyFields(); }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        GuiTextFieldNop tab = getTextField(CLONE_TAB_FIELD);
        if (tab != null) entry.setCloneTab(tab.getInteger());
        GuiTextFieldNop name = getTextField(CLONE_NAME_FIELD);
        if (name != null) entry.setCloneName(name.getValue());
        entry.setPosition(signed(X_FIELD), signed(Y_FIELD), signed(Z_FIELD));
        GuiTextFieldNop yaw = getTextField(YAW_FIELD);
        if (yaw != null) entry.setYaw(yaw.getFloat());
        GuiTextFieldNop width = getTextField(BEAM_WIDTH_FIELD);
        if (width != null) entry.setBeamWidthPercentOverride(width.getInteger());
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
