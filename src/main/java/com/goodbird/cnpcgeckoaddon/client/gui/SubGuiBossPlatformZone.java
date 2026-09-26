package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.ZoneSelection;
import com.goodbird.cnpcgeckoaddon.client.ZoneSelectionClient;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeYesNo;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossPlatformZone;
import com.goodbird.cnpcgeckoaddon.utils.ZoneCoordinates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/**
 * One platform: its two corners, given the way a summon's point is - as an offset from the arena
 * or as fixed blocks - with "use my position" beside each corner, its random weight and its switch.
 */
public final class SubGuiBossPlatformZone extends SubGuiFieldScreen implements BossZoneScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int COORDINATE_BUTTON = 2;
    private static final int CORNER1_LABEL = 3;
    private static final int X1_FIELD = 4;
    private static final int Y1_FIELD = 5;
    private static final int Z1_FIELD = 6;
    private static final int CORNER1_HERE_BUTTON = 7;
    private static final int CORNER2_LABEL = 8;
    private static final int X2_FIELD = 9;
    private static final int Y2_FIELD = 10;
    private static final int Z2_FIELD = 11;
    private static final int CORNER2_HERE_BUTTON = 12;
    private static final int WEIGHT_FIELD = 13;
    private static final int DELETE_BUTTON = 14;
    private static final int ARENA_HINT_LABEL = 15;
    private static final int SELECT_BUTTON = 16;
    private static final int TITLE_LABEL = 30;

    /** The rows, the hint and the buttons, packed so the whole platform fits one panel. */
    private static final int ENABLED_Y = 20;
    private static final int COORDINATE_Y = 42;
    private static final int CORNER1_Y = 64;
    private static final int CORNER2_Y = 101;
    /** How far under a corner's label its fields start. */
    private static final int CORNER_FIELDS_DROP = 12;
    /** The pick in the world, on a row of its own under the corners it fills. */
    private static final int SELECT_Y = 137;
    private static final int WEIGHT_Y = 159;
    private static final int HINT_Y = 184;
    private static final int BUTTONS_Y = 202;

    private static final String[] COORDINATE_LABELS = {
            "cnpcgeckoaddon.boss.minion_spawn_arena",
            "cnpcgeckoaddon.boss.minion_spawn_fixed"
    };

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private final int index;
    private final BossPlatformZone zone;

    public SubGuiBossPlatformZone(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex, int index) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        this.index = index;
        this.zone = phase.platform().getZones().get(index);
        imageWidth = 256;
        imageHeight = BUTTONS_Y + 26;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new ThemeLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.platform_zone_title", phaseIndex), guiLeft + 8, guiTop + 7, 0xFFFFFF));

        addLabel(new ThemeLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 8,
                guiTop + ENABLED_Y + 6));
        addButton(new ThemeYesNo(this, ENABLED_BUTTON, guiLeft + 155, guiTop + ENABLED_Y, 87, 20,
                zone.isEnabled()));

        addLabel(new ThemeLabel(COORDINATE_BUTTON, "cnpcgeckoaddon.boss.minion_spawn_coordinate",
                guiLeft + 8, guiTop + COORDINATE_Y + 6));
        addButton(new ThemeButton(this, COORDINATE_BUTTON, guiLeft + 112, guiTop + COORDINATE_Y, 130, 20,
                COORDINATE_LABELS, zone.getCoordinateMode()));

        // Measured the way the arena hazard's box is: two corners, either order, both inclusive.
        addLabel(new ThemeLabel(CORNER1_LABEL, "cnpcgeckoaddon.boss.aggro_zone_corner1", guiLeft + 8,
                guiTop + CORNER1_Y + 2));
        addCornerFields(X1_FIELD, Y1_FIELD, Z1_FIELD, CORNER1_HERE_BUTTON, guiTop + CORNER1_Y + CORNER_FIELDS_DROP,
                zone.getX1(), zone.getY1(), zone.getZ1());
        addLabel(new ThemeLabel(CORNER2_LABEL, "cnpcgeckoaddon.boss.aggro_zone_corner2", guiLeft + 8,
                guiTop + CORNER2_Y + 2));
        addCornerFields(X2_FIELD, Y2_FIELD, Z2_FIELD, CORNER2_HERE_BUTTON, guiTop + CORNER2_Y + CORNER_FIELDS_DROP,
                zone.getX2(), zone.getY2(), zone.getZ2());

        addButton(new ThemeButton(this, SELECT_BUTTON, guiLeft + 8, guiTop + SELECT_Y, 234, 20,
                ZoneSelectionClient.SELECT_BOX));

        addNumberField(WEIGHT_FIELD, "cnpcgeckoaddon.boss.platform_zone_weight", guiTop + WEIGHT_Y,
                zone.getWeight(), BossPlatformZone.MIN_WEIGHT, BossPlatformZone.MAX_WEIGHT, BossPlatformZone.MIN_WEIGHT);

        addLabel(new ThemeLabel(ARENA_HINT_LABEL, "cnpcgeckoaddon.boss.minion_spawn_arena_hint",
                guiLeft + 8, guiTop + HINT_Y, 0xA0A0A0));
        addButton(new ThemeButton(this, DELETE_BUTTON, guiLeft + 8, guiTop + BUTTONS_Y, 72, 20,
                "cnpcgeckoaddon.boss.minion_spawn_delete"));
        addDoneButton(guiLeft + 182, guiTop + BUTTONS_Y, 60, 20);
        updateCoordinateHint();
    }

    private void addCornerFields(int xId, int yId, int zId, int buttonId, int y, int x, int cornerY, int z) {
        addTextField(coordinateField(xId, guiLeft + 8, y, 40, x));
        addTextField(coordinateField(yId, guiLeft + 52, y, 40, cornerY));
        addTextField(coordinateField(zId, guiLeft + 96, y, 40, z));
        addButton(new ThemeButton(this, buttonId, guiLeft + 142, y, 100, 20, "cnpcgeckoaddon.boss.aggro_zone_here"));
    }

    private void updateCoordinateHint() {
        GuiLabel hint = getLabel(ARENA_HINT_LABEL);
        if (hint != null) {
            hint.visible = zone.getCoordinateMode() == BossPlatformZone.COORDINATE_ARENA_OFFSET;
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            zone.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == COORDINATE_BUTTON) {
            applyFields();
            zone.setCoordinateMode(button.getValue());
            updateCoordinateHint();
        } else if (button.id == CORNER1_HERE_BUTTON) {
            BlockPos here = cornerHere();
            if (here != null) {
                zone.setCorner1(here.getX(), here.getY(), here.getZ());
                showCorner(X1_FIELD, Y1_FIELD, Z1_FIELD, zone.getX1(), zone.getY1(), zone.getZ1());
            }
        } else if (button.id == CORNER2_HERE_BUTTON) {
            BlockPos here = cornerHere();
            if (here != null) {
                zone.setCorner2(here.getX(), here.getY(), here.getZ());
                showCorner(X2_FIELD, Y2_FIELD, Z2_FIELD, zone.getX2(), zone.getY2(), zone.getZ2());
            }
        } else if (button.id == SELECT_BUTTON) {
            applyFields();
            // Written in the platform's own mode, an offset measured from the same block "use my
            // position" measures from, and cornered on the blocks in front of the faces clicked -
            // the blocks "use my position" takes, the ones the feet are in: a platform burns
            // whoever's feet are inside, and a box of the floor blocks themselves holds nobody.
            ZoneSelectionClient.selectBox(BossAbilityKind.PLATFORM, ZoneSelection.Pick.IN_FRONT, (box, anchor) -> {
                boolean fixed = zone.getCoordinateMode() == BossPlatformZone.COORDINATE_FIXED;
                BlockPos min = ZoneCoordinates.toStored(box.min(), fixed, anchor);
                BlockPos max = ZoneCoordinates.toStored(box.max(), fixed, anchor);
                zone.setCorner1(min.getX(), min.getY(), min.getZ());
                zone.setCorner2(max.getX(), max.getY(), max.getZ());
            });
        } else if (button.id == DELETE_BUTTON) {
            phase.platform().getZones().remove(index);
            close();
        }
    }

    /**
     * The block the editor stands on, as a fixed block or as an offset from the boss, the way a
     * summon point takes it: what is typed in the other corner is read first, so it is not lost.
     */
    private BlockPos cornerHere() {
        applyFields();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        BlockPos playerPos = player.blockPosition();
        if (zone.getCoordinateMode() == BossPlatformZone.COORDINATE_FIXED) {
            return playerPos;
        }
        return playerPos.subtract(npc.blockPosition());
    }

    private void showCorner(int xId, int yId, int zId, int x, int y, int z) {
        getTextField(xId).setValue(Integer.toString(x));
        getTextField(yId).setValue(Integer.toString(y));
        getTextField(zId).setValue(Integer.toString(z));
    }

    @Override
    protected void applyFields() {
        zone.setCorner1(signed(X1_FIELD), signed(Y1_FIELD), signed(Z1_FIELD));
        zone.setCorner2(signed(X2_FIELD), signed(Y2_FIELD), signed(Z2_FIELD));
        applyNumberField(WEIGHT_FIELD, zone::setWeight);
    }

    /** The platform being edited. */
    @Override
    public Object zoneFocus() {
        return zone;
    }
}
