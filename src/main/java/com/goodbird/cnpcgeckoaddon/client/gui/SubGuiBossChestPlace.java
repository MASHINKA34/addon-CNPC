package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Where a boss puts its loot chest down. */
public final class SubGuiBossChestPlace extends GuiBasic implements ITextfieldListener {
    private static final int MODE_BUTTON = 1;
    private static final int COORDS_LABEL = 2;
    private static final int X_FIELD = 3;
    private static final int Y_FIELD = 4;
    private static final int Z_FIELD = 5;
    private static final int HERE_BUTTON = 6;

    private final TeleportPathData data;

    public SubGuiBossChestPlace(TeleportPathData data) {
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 160;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.chest_title", guiLeft + 8, guiTop + 8, 0xFFFFFF));

        addLabel(new GuiLabel(MODE_BUTTON, "cnpcgeckoaddon.boss.chest_placement", guiLeft + 8, guiTop + 34));
        addButton(new GuiButtonNop(this, MODE_BUTTON, guiLeft + 100, guiTop + 28, 142, 20,
                TeleportPathData.CHEST_PLACEMENT_LABELS, data.getChestPlacement()));

        // The label sits above the fields rather than beside them, so a coordinate eight
        // digits long still has somewhere to go.
        addLabel(new GuiLabel(COORDS_LABEL, "cnpcgeckoaddon.boss.chest_offset", guiLeft + 8, guiTop + 56));
        addTextField(coordinateField(X_FIELD, guiLeft + 8, guiTop + 68, 74));
        addTextField(coordinateField(Y_FIELD, guiLeft + 90, guiTop + 68, 74));
        addTextField(coordinateField(Z_FIELD, guiLeft + 172, guiTop + 68, 70));

        addButton(new GuiButtonNop(this, HERE_BUTTON, guiLeft + 8, guiTop + 94, 234, 20,
                "cnpcgeckoaddon.boss.chest_here"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 130, 60, 20,
                "gui.done", button -> close()));

        refresh();
    }

    /**
     * A plain text field, not a numbers-only one.
     *
     * <p>{@code setNumbersOnly()} lets nothing but digits through, and every coordinate on
     * this screen can be negative - the minus sign would be impossible to type.</p>
     */
    private GuiTextFieldNop coordinateField(int id, int x, int y, int width) {
        return new GuiTextFieldNop(id, this, x, y, width, 20, "0");
    }

    /** Puts the screen in step with the selected mode: labels, values and what is editable. */
    private void refresh() {
        boolean fixed = data.getChestPlacement() == TeleportPathData.CHEST_PLACEMENT_FIXED;
        boolean editable = data.getChestPlacement() != TeleportPathData.CHEST_PLACEMENT_DEATH;

        GuiLabel label = getLabel(COORDS_LABEL);
        if (label != null) {
            label.setMessage(Component.translatable(fixed
                    ? "cnpcgeckoaddon.boss.chest_coords" : "cnpcgeckoaddon.boss.chest_offset"));
        }
        showValue(X_FIELD, fixed ? data.getChestFixedX() : data.getChestOffsetX(), editable);
        showValue(Y_FIELD, fixed ? data.getChestFixedY() : data.getChestOffsetY(), editable);
        showValue(Z_FIELD, fixed ? data.getChestFixedZ() : data.getChestOffsetZ(), editable);

        GuiButtonNop here = getButton(HERE_BUTTON);
        if (here != null) {
            here.setEnabled(editable);
        }
    }

    private void showValue(int id, int value, boolean editable) {
        GuiTextFieldNop field = getTextField(id);
        if (field != null) {
            field.setValue(Integer.toString(value));
            field.enabled = editable;
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == MODE_BUTTON) {
            // Store what is on screen against the old mode first: the same three fields
            // stand for the offset in one mode and for absolute coordinates in another.
            applyFields();
            data.setChestPlacement(button.getValue());
            refresh();
        } else if (button.id == HERE_BUTTON) {
            takePlayerPosition();
        }
    }

    /** Fills the fields with the block the editor is standing on. */
    private void takePlayerPosition() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || data.getChestPlacement() == TeleportPathData.CHEST_PLACEMENT_DEATH) {
            return;
        }
        BlockPos pos = player.blockPosition();
        if (data.getChestPlacement() == TeleportPathData.CHEST_PLACEMENT_FIXED) {
            data.setChestFixed(pos.getX(), pos.getY(), pos.getZ());
        } else {
            data.setChestOffset(pos.getX(), pos.getY(), pos.getZ());
        }
        refresh();
    }

    @Override
    public void unFocused(GuiTextFieldNop field) { applyFields(); }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        int x = signed(X_FIELD);
        int y = signed(Y_FIELD);
        int z = signed(Z_FIELD);
        if (data.getChestPlacement() == TeleportPathData.CHEST_PLACEMENT_FIXED) {
            data.setChestFixed(x, y, z);
        } else {
            // Death mode keeps writing the offsets. They are ignored while it is selected,
            // so anything typed before switching modes is still there afterwards.
            data.setChestOffset(x, y, z);
        }
    }

    private int signed(int id) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return 0;
        }
        String value = field.getValue().trim();
        try {
            // A lone minus is what a half-typed negative number looks like.
            return value.isEmpty() || value.equals("-") ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
