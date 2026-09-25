package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.ai.BossRiftDimension;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossRiftCrystalPoint;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/**
 * One crystal zone: where it is, given the way a summon's point is - as an offset from the middle
 * of the rift's platform or as a fixed spot in the rift dimension - with "use my position", its own
 * radius, block and colour where it wants one, and its switch.
 */
public final class SubGuiBossRiftCrystalPoint extends SubGuiFieldScreen implements BossZoneScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int COORDINATE_BUTTON = 2;
    private static final int X_FIELD = 3;
    private static final int Y_FIELD = 4;
    private static final int Z_FIELD = 5;
    private static final int HERE_BUTTON = 6;
    private static final int RADIUS_FIELD = 7;
    private static final int BLOCK_FIELD = 8;
    private static final int BLOCK_SELECT_BUTTON = 9;
    private static final int COLOR_FIELD = 10;
    private static final int DELETE_BUTTON = 11;
    private static final int ARENA_HINT_LABEL = 12;
    private static final int TITLE_LABEL = 30;

    private static final int ENABLED_Y = 22;
    private static final int COORDINATE_Y = 44;
    private static final int POSITION_Y = 66;
    private static final int RADIUS_Y = 88;
    private static final int BLOCK_Y = 110;
    private static final int COLOR_Y = 132;
    private static final int HINT_Y = 158;
    private static final int BUTTONS_Y = 176;

    private static final int CONTROL_HEIGHT = 20;
    private static final int LABEL_X = 8;
    private static final int LABEL_DROP = 6;

    private static final String[] COORDINATE_LABELS = {
            "cnpcgeckoaddon.boss.minion_spawn_arena",
            "cnpcgeckoaddon.boss.minion_spawn_fixed"
    };

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private final int index;
    private final BossRiftCrystalPoint point;

    public SubGuiBossRiftCrystalPoint(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex, int index) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        this.index = index;
        this.point = phase.rift().getCrystalPoints().get(index);
        imageWidth = 256;
        imageHeight = BUTTONS_Y + 26;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.rift_crystal_point_title", phaseIndex), guiLeft + LABEL_X, guiTop + 7, 0xFFFFFF));

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + LABEL_X,
                guiTop + ENABLED_Y + LABEL_DROP));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, guiTop + ENABLED_Y, 87, CONTROL_HEIGHT,
                point.isEnabled()));

        addLabel(new GuiLabel(COORDINATE_BUTTON, "cnpcgeckoaddon.boss.minion_spawn_coordinate",
                guiLeft + LABEL_X, guiTop + COORDINATE_Y + LABEL_DROP));
        addButton(new GuiButtonNop(this, COORDINATE_BUTTON, guiLeft + 112, guiTop + COORDINATE_Y, 130, CONTROL_HEIGHT,
                COORDINATE_LABELS, point.getCoordinateMode()));

        addTextField(coordinateField(X_FIELD, guiLeft + LABEL_X, guiTop + POSITION_Y, 40, point.getX()));
        addTextField(coordinateField(Y_FIELD, guiLeft + 52, guiTop + POSITION_Y, 40, point.getY()));
        addTextField(coordinateField(Z_FIELD, guiLeft + 96, guiTop + POSITION_Y, 40, point.getZ()));
        addButton(new GuiButtonNop(this, HERE_BUTTON, guiLeft + 142, guiTop + POSITION_Y, 100, CONTROL_HEIGHT,
                "cnpcgeckoaddon.boss.aggro_zone_here"));

        addNumberField(RADIUS_FIELD, "cnpcgeckoaddon.boss.rift_crystal_point_radius", guiTop + RADIUS_Y,
                point.getRadiusTenths(), 0, BossRiftCrystalPoint.MAX_RADIUS_TENTHS, 0);

        addLabel(new GuiLabel(BLOCK_FIELD, "cnpcgeckoaddon.boss.rift_crystal_point_block", guiLeft + LABEL_X,
                guiTop + BLOCK_Y + LABEL_DROP));
        addTextField(new GuiTextFieldNop(BLOCK_FIELD, this, guiLeft + 108, guiTop + BLOCK_Y, 86, CONTROL_HEIGHT,
                point.getBlockOverride()));
        addButton(new GuiButtonNop(this, BLOCK_SELECT_BUTTON, guiLeft + 198, guiTop + BLOCK_Y, 44, CONTROL_HEIGHT,
                "mco.template.button.select"));

        addLabel(new GuiLabel(COLOR_FIELD, "cnpcgeckoaddon.boss.rift_crystal_point_colour", guiLeft + LABEL_X,
                guiTop + COLOR_Y + LABEL_DROP));
        addTextField(new GuiTextFieldNop(COLOR_FIELD, this, guiLeft + 194, guiTop + COLOR_Y, 48, CONTROL_HEIGHT,
                point.getColorOverride() < 0 ? "" : BossRiftSettings.hex(point.getColorOverride())));

        addLabel(new GuiLabel(ARENA_HINT_LABEL, "cnpcgeckoaddon.boss.rift_crystal_point_hint",
                guiLeft + LABEL_X, guiTop + HINT_Y, 0xA0A0A0));
        addButton(new GuiButtonNop(this, DELETE_BUTTON, guiLeft + LABEL_X, guiTop + BUTTONS_Y, 72, CONTROL_HEIGHT,
                "cnpcgeckoaddon.boss.minion_spawn_delete"));
        addDoneButton(guiLeft + 182, guiTop + BUTTONS_Y, 60, CONTROL_HEIGHT);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            point.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == COORDINATE_BUTTON) {
            applyFields();
            point.setCoordinateMode(button.getValue());
        } else if (button.id == HERE_BUTTON) {
            takePlayerPosition();
        } else if (button.id == BLOCK_SELECT_BUTTON) {
            applyFields();
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.rift_crystal_block",
                    SubGuiBossRiftCrystals.blockIds(), id -> {
                point.setBlockOverride(id);
                getTextField(BLOCK_FIELD).setValue(point.getBlockOverride());
            }));
        } else if (button.id == DELETE_BUTTON) {
            phase.rift().getCrystalPoints().remove(index);
            close();
        }
    }

    /**
     * The block the editor stands on, as a fixed spot or as an offset from the boss, the way a
     * summon point takes it. An offset from the boss is what the rift measures from the middle of
     * its platform, so a builder marks the shape out on the arena and it comes out the same shape
     * in the rift.
     */
    private void takePlayerPosition() {
        applyFields();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        BlockPos here = player.blockPosition();
        if (point.getCoordinateMode() == BossRiftCrystalPoint.COORDINATE_FIXED) {
            point.setPosition(here.getX(), here.getY(), here.getZ());
        } else {
            BlockPos boss = npc.blockPosition();
            point.setPosition(here.getX() - boss.getX(), here.getY() - boss.getY(), here.getZ() - boss.getZ());
        }
        getTextField(X_FIELD).setValue(Integer.toString(point.getX()));
        getTextField(Y_FIELD).setValue(Integer.toString(point.getY()));
        getTextField(Z_FIELD).setValue(Integer.toString(point.getZ()));
    }

    @Override
    protected void applyFields() {
        point.setPosition(signed(X_FIELD), signed(Y_FIELD), signed(Z_FIELD));
        applyNumberField(RADIUS_FIELD, point::setRadiusTenths);
        GuiTextFieldNop block = getTextField(BLOCK_FIELD);
        if (block != null) {
            String value = block.getValue().trim();
            // Empty is "the rift's own block"; anything else has to name a block in the game.
            if (value.isEmpty() || BossRiftDimension.resolveBlock(value) != null) {
                point.setBlockOverride(value);
            } else {
                block.setValue(point.getBlockOverride());
            }
        }
        GuiTextFieldNop color = getTextField(COLOR_FIELD);
        if (color != null) {
            String value = color.getValue().trim();
            // Empty is "the rift's own colour", which is what it reads back as.
            point.setColorOverride(value.isEmpty() ? BossRiftCrystalPoint.NO_COLOR
                    : BossRiftSettings.parseHex(value, point.getColorOverride()));
            color.setValue(point.getColorOverride() < 0 ? "" : BossRiftSettings.hex(point.getColorOverride()));
        }
    }

    /** The crystal zone being edited. */
    @Override
    public Object zoneFocus() {
        return point;
    }
}
