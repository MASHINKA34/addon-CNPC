package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Arena hazard: the ground itself turning dangerous for a phase, as a closing ring or a box. */
public final class SubGuiBossHazard extends SubGuiFieldScreen implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int MODE_BUTTON = 2;
    private static final int DELAY_FIELD = 3;
    private static final int WARN_FIELD = 4;
    private static final int DAMAGE_FIELD = 5;
    private static final int INTERVAL_FIELD = 6;
    private static final int CENTER_BUTTON = 7;
    private static final int CENTER_X_FIELD = 8;
    private static final int CENTER_Z_FIELD = 9;
    private static final int CENTER_HERE_BUTTON = 10;
    private static final int START_RADIUS_FIELD = 11;
    private static final int END_RADIUS_FIELD = 12;
    private static final int SHRINK_FIELD = 13;
    private static final int CORNER1_LABEL = 14;
    private static final int X1_FIELD = 15;
    private static final int Y1_FIELD = 16;
    private static final int Z1_FIELD = 17;
    private static final int CORNER1_HERE_BUTTON = 18;
    private static final int CORNER2_LABEL = 19;
    private static final int X2_FIELD = 20;
    private static final int Y2_FIELD = 21;
    private static final int Z2_FIELD = 22;
    private static final int CORNER2_HERE_BUTTON = 23;
    private static final int EFFECTS_BUTTON = 67;

    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossHazard(BossPhaseData phase, int phaseIndex) {
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 248;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.hazard_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, phase.isHazardEnabled()));
        y += 21;

        // The first thing to pick, because the four rows further down belong to one shape
        // only and come and go with it.
        addLabel(new GuiLabel(MODE_BUTTON, "cnpcgeckoaddon.boss.hazard_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.HAZARD_MODE_LABELS, phase.getHazardMode()));
        y += 21;

        // Both shapes: when the arena turns, how long it flashes first, and what it costs.
        addPairRow(DELAY_FIELD, WARN_FIELD, "cnpcgeckoaddon.boss.hazard_delay", y,
                phase.getHazardDelayTicks(), 0, 12000, 200,
                phase.getHazardWarnTicks(), 0, 600, 60);
        y += 21;
        addPairRow(DAMAGE_FIELD, INTERVAL_FIELD, "cnpcgeckoaddon.boss.hazard_damage", y,
                phase.getHazardDamage(), 0, 1000, 4,
                phase.getHazardIntervalTicks(), 1, 200, 20);
        y += 21;

        // The two shapes' rows share the same four lines: only one set is ever on the
        // screen, so laying them over each other keeps the whole hazard on one panel.
        int shapeY = y;
        addLabel(new GuiLabel(CENTER_BUTTON, "cnpcgeckoaddon.boss.hazard_center", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, CENTER_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.HAZARD_CENTER_LABELS, phase.getHazardCenterMode()));
        y += 21;
        addTextField(coordinateField(CENTER_X_FIELD, guiLeft + 8, y, phase.getHazardCenterX()));
        addTextField(coordinateField(CENTER_Z_FIELD, guiLeft + 52, y, phase.getHazardCenterZ()));
        addButton(new GuiButtonNop(this, CENTER_HERE_BUTTON, guiLeft + 142, y, 100, 20,
                "cnpcgeckoaddon.boss.aggro_zone_here"));
        y += 21;
        addPairRow(START_RADIUS_FIELD, END_RADIUS_FIELD, "cnpcgeckoaddon.boss.hazard_radius", y,
                phase.getHazardStartRadius(), 2, 128, 30,
                phase.getHazardEndRadius(), 1, 127, 6);
        y += 21;
        addNumberField(SHRINK_FIELD, "cnpcgeckoaddon.boss.hazard_shrink", y,
                phase.getHazardShrinkTicks(), 20, 24000, 1200);
        y += 21;

        // The box is measured the way the aggro zone is: two corners, either order.
        int boxY = shapeY;
        addLabel(new GuiLabel(CORNER1_LABEL, "cnpcgeckoaddon.boss.aggro_zone_corner1", guiLeft + 6, boxY + 6));
        boxY += 21;
        addCornerFields(X1_FIELD, Y1_FIELD, Z1_FIELD, CORNER1_HERE_BUTTON, boxY,
                phase.getHazardX1(), phase.getHazardY1(), phase.getHazardZ1());
        boxY += 21;
        addLabel(new GuiLabel(CORNER2_LABEL, "cnpcgeckoaddon.boss.aggro_zone_corner2", guiLeft + 6, boxY + 6));
        boxY += 21;
        addCornerFields(X2_FIELD, Y2_FIELD, Z2_FIELD, CORNER2_HERE_BUTTON, boxY,
                phase.getHazardX2(), phase.getHazardY2(), phase.getHazardZ2());

        int hintY = addWrappedHint(31, "cnpcgeckoaddon.boss.hazard_hint", y + 3);
        int buttonsY = Math.max(hintY + 4, guiTop + 212);
        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, buttonsY, 120, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, buttonsY, 60, 20,
                "gui.done", button -> close()));
        applyModeRows();
    }

    /**
     * Takes the other shape's rows off the screen, and the fixed point's coordinates off it
     * while the ring follows the boss.
     *
     * <p>Hidden in place rather than laid out again: this GUI framework has no
     * widget-clearing rebuild, so a screen that re-flowed itself on every click of the
     * shape button would stack a second copy of every row on the first. The rows keep
     * their line and simply stop being drawn or clicked.</p>
     */
    private void applyModeRows() {
        boolean ring = phase.getHazardMode() == BossPhaseData.HAZARD_MODE_RING;
        boolean point = ring && phase.getHazardCenterMode() == BossPhaseData.HAZARD_CENTER_POINT;
        showLabel(CENTER_BUTTON, ring);
        showButton(CENTER_BUTTON, ring);
        showField(CENTER_X_FIELD, point);
        showField(CENTER_Z_FIELD, point);
        showButton(CENTER_HERE_BUTTON, point);
        showLabel(START_RADIUS_FIELD, ring);
        showField(START_RADIUS_FIELD, ring);
        showField(END_RADIUS_FIELD, ring);
        showLabel(SHRINK_FIELD, ring);
        showField(SHRINK_FIELD, ring);

        showLabel(CORNER1_LABEL, !ring);
        showField(X1_FIELD, !ring);
        showField(Y1_FIELD, !ring);
        showField(Z1_FIELD, !ring);
        showButton(CORNER1_HERE_BUTTON, !ring);
        showLabel(CORNER2_LABEL, !ring);
        showField(X2_FIELD, !ring);
        showField(Y2_FIELD, !ring);
        showField(Z2_FIELD, !ring);
        showButton(CORNER2_HERE_BUTTON, !ring);
    }

    private void showLabel(int id, boolean shown) {
        GuiLabel label = getLabel(id);
        if (label != null) {
            label.enabled = shown;
        }
    }

    private void showField(int id, boolean shown) {
        GuiTextFieldNop field = getTextField(id);
        if (field != null) {
            field.enabled = shown;
        }
    }

    private void showButton(int id, boolean shown) {
        GuiButtonNop button = getButton(id);
        if (button != null) {
            button.shown = shown;
            // Hidden is not enough on its own: an unshown button still takes the click.
            button.setEnabled(shown);
        }
    }

    /**
     * Two small numbers on one line, so the whole hazard still fits a single screen.
     *
     * <p>The fields sit further right and narrower than on the other screens, to leave the
     * label the room its longer translations need.</p>
     */
    private void addPairRow(int leftId, int rightId, String label, int y,
                            int leftValue, int leftMin, int leftMax, int leftFallback,
                            int rightValue, int rightMin, int rightMax, int rightFallback) {
        addLabel(new GuiLabel(leftId, label, guiLeft + 6, y + 6));
        addPairedField(leftId, guiLeft + 158, y, leftValue, leftMin, leftMax, leftFallback);
        addPairedField(rightId, guiLeft + 202, y, rightValue, rightMin, rightMax, rightFallback);
    }

    private void addPairedField(int id, int x, int y, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, 40, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    private void addCornerFields(int xId, int yId, int zId, int buttonId, int y,
                                 int x, int cornerY, int z) {
        addTextField(coordinateField(xId, guiLeft + 8, y, x));
        addTextField(coordinateField(yId, guiLeft + 52, y, cornerY));
        addTextField(coordinateField(zId, guiLeft + 96, y, z));
        addButton(new GuiButtonNop(this, buttonId, guiLeft + 142, y, 100, 20,
                "cnpcgeckoaddon.boss.aggro_zone_here"));
    }

    /** Coordinate fields stay plain so a minus sign can be entered before the digits. */
    private GuiTextFieldNop coordinateField(int id, int x, int y, int value) {
        return new GuiTextFieldNop(id, this, x, y, 40, 20, Integer.toString(value));
    }

    @Override
    protected int numberLabelX() {
        return 6;
    }

    @Override
    protected int numberFieldX() {
        return 175;
    }

    @Override
    protected int numberFieldWidth() {
        return 67;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(phase.getHazardEffects(), "cnpcgeckoaddon.boss.effects_hazard"));
        } else if (button.id == ENABLED_BUTTON) {
            phase.setHazardEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == MODE_BUTTON) {
            phase.setHazardMode(button.getValue());
            applyModeRows();
        } else if (button.id == CENTER_BUTTON) {
            phase.setHazardCenterMode(button.getValue());
            applyModeRows();
        } else if (button.id == CENTER_HERE_BUTTON) {
            BlockPos pos = playerPosition();
            if (pos != null) {
                phase.setHazardCenter(pos.getX(), pos.getZ());
                getTextField(CENTER_X_FIELD).setValue(Integer.toString(pos.getX()));
                getTextField(CENTER_Z_FIELD).setValue(Integer.toString(pos.getZ()));
            }
        } else if (button.id == CORNER1_HERE_BUTTON) {
            BlockPos pos = playerPosition();
            if (pos != null) {
                phase.setHazardCorner1(pos.getX(), pos.getY(), pos.getZ());
                showCorner(X1_FIELD, Y1_FIELD, Z1_FIELD, pos);
            }
        } else if (button.id == CORNER2_HERE_BUTTON) {
            BlockPos pos = playerPosition();
            if (pos != null) {
                phase.setHazardCorner2(pos.getX(), pos.getY(), pos.getZ());
                showCorner(X2_FIELD, Y2_FIELD, Z2_FIELD, pos);
            }
        }
    }

    private static BlockPos playerPosition() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : player.blockPosition();
    }

    private void showCorner(int xId, int yId, int zId, BlockPos pos) {
        getTextField(xId).setValue(Integer.toString(pos.getX()));
        getTextField(yId).setValue(Integer.toString(pos.getY()));
        getTextField(zId).setValue(Integer.toString(pos.getZ()));
    }

    @Override
    public void unFocused(GuiTextFieldNop field) { applyFields(); }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        GuiTextFieldNop delay = getTextField(DELAY_FIELD);
        if (delay != null) phase.setHazardDelayTicks(delay.getInteger());
        GuiTextFieldNop warn = getTextField(WARN_FIELD);
        if (warn != null) phase.setHazardWarnTicks(warn.getInteger());
        GuiTextFieldNop damage = getTextField(DAMAGE_FIELD);
        if (damage != null) phase.setHazardDamage(damage.getInteger());
        GuiTextFieldNop interval = getTextField(INTERVAL_FIELD);
        if (interval != null) phase.setHazardIntervalTicks(interval.getInteger());
        // Read whether or not the shape in force shows them: a hidden row keeps the numbers
        // a builder typed into it under the other shape, rather than losing them on a click.
        phase.setHazardCenter(signed(CENTER_X_FIELD), signed(CENTER_Z_FIELD));
        GuiTextFieldNop start = getTextField(START_RADIUS_FIELD);
        GuiTextFieldNop end = getTextField(END_RADIUS_FIELD);
        // Set as a pair: the end is only legal against the start.
        if (start != null && end != null) {
            phase.setHazardRadii(start.getInteger(), end.getInteger());
        }
        GuiTextFieldNop shrink = getTextField(SHRINK_FIELD);
        if (shrink != null) phase.setHazardShrinkTicks(shrink.getInteger());
        phase.setHazardCorner1(signed(X1_FIELD), signed(Y1_FIELD), signed(Z1_FIELD));
        phase.setHazardCorner2(signed(X2_FIELD), signed(Y2_FIELD), signed(Z2_FIELD));
    }

    private int signed(int id) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return 0;
        }
        String value = field.getValue().trim();
        try {
            return value.isEmpty() || value.equals("-") ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
