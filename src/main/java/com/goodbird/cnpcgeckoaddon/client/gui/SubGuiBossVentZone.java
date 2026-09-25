package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossVentZone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/**
 * One vent: its two corners, given the way a platform's are - as an offset from the arena or as
 * fixed blocks - with "use my position" beside each corner, the face it fires out of and how far
 * out it reaches, a mode of its own, its shift in a volley, its random weight and its switch.
 */
public final class SubGuiBossVentZone extends SubGuiFieldScreen implements BossZoneScreen {
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
    private static final int FACE_BUTTON = 13;
    private static final int REACH_FIELD = 14;
    private static final int MODE_BUTTON = 15;
    private static final int DELAY_FIELD = 16;
    private static final int WEIGHT_FIELD = 17;
    private static final int DELETE_BUTTON = 18;
    private static final int ARENA_HINT_LABEL = 19;
    private static final int TITLE_LABEL = 30;
    private static final int FIRST_HINT_LABEL = 40;

    /** The rows, the hints and the buttons: the platform's box, and the vent's own rows under it. */
    private static final int ENABLED_Y = 20;
    private static final int COORDINATE_Y = 42;
    private static final int CORNER1_Y = 64;
    private static final int CORNER2_Y = 101;
    /** How far under a corner's label its fields start. */
    private static final int CORNER_FIELDS_DROP = 12;
    private static final int FACE_Y = 137;
    private static final int REACH_Y = 159;
    private static final int MODE_Y = 181;
    private static final int DELAY_Y = 203;
    private static final int WEIGHT_Y = 225;
    private static final int ARENA_HINT_Y = 250;
    private static final int HINT_Y = 262;
    private static final int BUTTONS_GAP = 4;
    private static final int BOTTOM_MARGIN = 6;
    private static final String HINT = "cnpcgeckoaddon.boss.vent_zone_hint";

    private static final String[] COORDINATE_LABELS = {
            "cnpcgeckoaddon.boss.minion_spawn_arena",
            "cnpcgeckoaddon.boss.minion_spawn_fixed"
    };

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private final int index;
    private final BossVentZone zone;

    public SubGuiBossVentZone(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex, int index) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        this.index = index;
        this.zone = phase.vent().getZones().get(index);
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: the hint's lines are the locale's.
        imageHeight = buttonsY() + 20 + BOTTOM_MARGIN;
        super.init();
        addLabel(new GuiLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.vent_zone_title", phaseIndex), guiLeft + 8, guiTop + 7, 0xFFFFFF));

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 8,
                guiTop + ENABLED_Y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, guiTop + ENABLED_Y, 87, 20,
                zone.isEnabled()));

        addLabel(new GuiLabel(COORDINATE_BUTTON, "cnpcgeckoaddon.boss.minion_spawn_coordinate",
                guiLeft + 8, guiTop + COORDINATE_Y + 6));
        addButton(new GuiButtonNop(this, COORDINATE_BUTTON, guiLeft + 112, guiTop + COORDINATE_Y, 130, 20,
                COORDINATE_LABELS, zone.getCoordinateMode()));

        // Measured the way a platform's box is: two corners, either order, both inclusive.
        addLabel(new GuiLabel(CORNER1_LABEL, "cnpcgeckoaddon.boss.aggro_zone_corner1", guiLeft + 8,
                guiTop + CORNER1_Y + 2));
        addCornerFields(X1_FIELD, Y1_FIELD, Z1_FIELD, CORNER1_HERE_BUTTON, guiTop + CORNER1_Y + CORNER_FIELDS_DROP,
                zone.getX1(), zone.getY1(), zone.getZ1());
        addLabel(new GuiLabel(CORNER2_LABEL, "cnpcgeckoaddon.boss.aggro_zone_corner2", guiLeft + 8,
                guiTop + CORNER2_Y + 2));
        addCornerFields(X2_FIELD, Y2_FIELD, Z2_FIELD, CORNER2_HERE_BUTTON, guiTop + CORNER2_Y + CORNER_FIELDS_DROP,
                zone.getX2(), zone.getY2(), zone.getZ2());

        addLabel(new GuiLabel(FACE_BUTTON, "cnpcgeckoaddon.boss.vent_zone_face", guiLeft + 8,
                guiTop + FACE_Y + 6));
        addButton(new GuiButtonNop(this, FACE_BUTTON, guiLeft + 112, guiTop + FACE_Y, 130, 20,
                BossVentZone.FACE_LABELS, zone.getFace()));
        addNumberField(REACH_FIELD, "cnpcgeckoaddon.boss.vent_zone_reach", guiTop + REACH_Y,
                zone.getReach(), BossVentZone.MIN_REACH, BossVentZone.MAX_REACH, BossVentZone.DEFAULT_REACH);
        // The cycle reads "as the phase" first, so its place is the override moved up by one.
        addLabel(new GuiLabel(MODE_BUTTON, "cnpcgeckoaddon.boss.vent_zone_mode", guiLeft + 8,
                guiTop + MODE_Y + 6));
        addButton(new GuiButtonNop(this, MODE_BUTTON, guiLeft + 112, guiTop + MODE_Y, 130, 20,
                BossVentZone.MODE_LABELS, zone.getModeOverride() - BossVentZone.MODE_PHASE));
        addNumberField(DELAY_FIELD, "cnpcgeckoaddon.boss.vent_zone_delay", guiTop + DELAY_Y,
                zone.getDelayTicks(), 0, BossVentZone.MAX_DELAY_TICKS, 0);
        addNumberField(WEIGHT_FIELD, "cnpcgeckoaddon.boss.vent_zone_weight", guiTop + WEIGHT_Y,
                zone.getWeight(), BossVentZone.MIN_WEIGHT, BossVentZone.MAX_WEIGHT, BossVentZone.MIN_WEIGHT);

        addLabel(new GuiLabel(ARENA_HINT_LABEL, "cnpcgeckoaddon.boss.minion_spawn_arena_hint",
                guiLeft + 8, guiTop + ARENA_HINT_Y, 0xA0A0A0));
        addWrappedHint(FIRST_HINT_LABEL, HINT, guiTop + HINT_Y);
        addButton(new GuiButtonNop(this, DELETE_BUTTON, guiLeft + 8, guiTop + buttonsY(), 72, 20,
                "cnpcgeckoaddon.boss.minion_spawn_delete"));
        addDoneButton(guiLeft + 182, guiTop + buttonsY(), 60, 20);
        updateCoordinateHint();
    }

    private int buttonsY() {
        return HINT_Y + wrappedHintHeight(HINT) + BUTTONS_GAP;
    }

    private void addCornerFields(int xId, int yId, int zId, int buttonId, int y, int x, int cornerY, int z) {
        addTextField(coordinateField(xId, guiLeft + 8, y, 40, x));
        addTextField(coordinateField(yId, guiLeft + 52, y, 40, cornerY));
        addTextField(coordinateField(zId, guiLeft + 96, y, 40, z));
        addButton(new GuiButtonNop(this, buttonId, guiLeft + 142, y, 100, 20, "cnpcgeckoaddon.boss.aggro_zone_here"));
    }

    private void updateCoordinateHint() {
        GuiLabel hint = getLabel(ARENA_HINT_LABEL);
        if (hint != null) {
            hint.visible = zone.getCoordinateMode() == BossVentZone.COORDINATE_ARENA_OFFSET;
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
        } else if (button.id == FACE_BUTTON) {
            zone.setFace(button.getValue());
        } else if (button.id == MODE_BUTTON) {
            zone.setModeOverride(button.getValue() + BossVentZone.MODE_PHASE);
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
        } else if (button.id == DELETE_BUTTON) {
            phase.vent().getZones().remove(index);
            close();
        }
    }

    /**
     * The block the editor stands on, as a fixed block or as an offset from the boss, the way a
     * platform's corner is taken: what is typed in the other corner is read first, so it is not lost.
     */
    private BlockPos cornerHere() {
        applyFields();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        BlockPos playerPos = player.blockPosition();
        if (zone.getCoordinateMode() == BossVentZone.COORDINATE_FIXED) {
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
        applyNumberField(REACH_FIELD, zone::setReach);
        applyNumberField(DELAY_FIELD, zone::setDelayTicks);
        applyNumberField(WEIGHT_FIELD, zone::setWeight);
    }

    /** The vent being edited. */
    @Override
    public Object zoneFocus() {
        return zone;
    }
}
