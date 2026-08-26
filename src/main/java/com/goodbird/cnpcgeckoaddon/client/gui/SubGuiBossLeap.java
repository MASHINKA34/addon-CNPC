package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossTargetMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Leap slam: the boss jumps - up, at someone, or onto a spot - and hits the ground. */
public final class SubGuiBossLeap extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int ANIMATION_FIELD = 2;
    private static final int LAND_ANIMATION_FIELD = 3;
    private static final int MODE_BUTTON = 4;
    private static final int TARGET_MODE_BUTTON = 5;
    private static final int HEIGHT_FIELD = 6;
    private static final int MIN_RANGE_FIELD = 7;
    private static final int MAX_RANGE_FIELD = 8;
    private static final int COORDS_LABEL = 9;
    private static final int X_FIELD = 10;
    private static final int Y_FIELD = 11;
    private static final int Z_FIELD = 12;
    private static final int HERE_BUTTON = 13;
    private static final int IMPACT_BUTTON = 14;
    private static final int EFFECTS_BUTTON = 67;

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;

    public SubGuiBossLeap(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.leap_phase", phaseIndex),
                guiLeft + 8, guiTop + 5, 0xFFFFFF));
        int y = guiTop + 18;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 6, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, phase.isLeapEnabled()));
        y += 21;

        addLabel(new GuiLabel(ANIMATION_FIELD, "cnpcgeckoaddon.boss.animation", guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(ANIMATION_FIELD, this, guiLeft + 88, y, 106, 20,
                phase.getLeapAnimation()));
        addButton(new GuiButtonNop(this, ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 21;

        addLabel(new GuiLabel(LAND_ANIMATION_FIELD, "cnpcgeckoaddon.boss.leap_land_anim", guiLeft + 6, y + 6));
        addTextField(new GuiTextFieldNop(LAND_ANIMATION_FIELD, this, guiLeft + 88, y, 106, 20,
                phase.getLeapLandAnimation()));
        addButton(new GuiButtonNop(this, LAND_ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 21;

        addLabel(new GuiLabel(MODE_BUTTON, "cnpcgeckoaddon.boss.leap_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossPhaseData.LEAP_MODE_LABELS, phase.getLeapMode()));
        y += 21;

        addLabel(new GuiLabel(TARGET_MODE_BUTTON, "cnpcgeckoaddon.boss.target_mode", guiLeft + 6, y + 6));
        addButton(new GuiButtonNop(this, TARGET_MODE_BUTTON, guiLeft + 112, y, 130, 20,
                BossTargetMode.LABELS, phase.getLeapTargetMode()));
        y += 21;

        addNumberField(HEIGHT_FIELD, "cnpcgeckoaddon.boss.leap_height", y, phase.getLeapHeight(),
                1, BossPhaseData.MAX_LEAP_HEIGHT, 8);
        y += 21;
        addPairRow(MIN_RANGE_FIELD, MAX_RANGE_FIELD, "cnpcgeckoaddon.boss.range", y,
                phase.getLeapMinRange(), 0, 64, 4,
                phase.getLeapMaxRange(), 1, 128, 24);

        // The label sits above the fields rather than beside them, so a coordinate eight
        // digits long still has somewhere to go.
        addLabel(new GuiLabel(COORDS_LABEL, "cnpcgeckoaddon.boss.chest_offset", guiLeft + 6, guiTop + 168));
        addTextField(coordinateField(X_FIELD, guiLeft + 6, guiTop + 178, 74));
        addTextField(coordinateField(Y_FIELD, guiLeft + 88, guiTop + 178, 74));
        addTextField(coordinateField(Z_FIELD, guiLeft + 172, guiTop + 178, 70));

        addButton(new GuiButtonNop(this, EFFECTS_BUTTON, guiLeft + 6, guiTop + 204, 116, 20,
                "cnpcgeckoaddon.boss.effects_settings"));
        addButton(new GuiButtonNop(this, IMPACT_BUTTON, guiLeft + 126, guiTop + 204, 116, 20,
                "cnpcgeckoaddon.boss.leap_impact_settings"));
        addButton(new GuiButtonNop(this, HERE_BUTTON, guiLeft + 6, guiTop + 232, 120, 20,
                "cnpcgeckoaddon.boss.chest_here"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 232, 60, 20,
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
        boolean fixed = phase.getLeapMode() == BossPhaseData.LEAP_MODE_FIXED;
        boolean arena = phase.getLeapMode() == BossPhaseData.LEAP_MODE_ARENA_OFFSET;

        GuiLabel label = getLabel(COORDS_LABEL);
        if (label != null) {
            label.setMessage(Component.translatable(fixed
                    ? "cnpcgeckoaddon.boss.chest_coords" : "cnpcgeckoaddon.boss.chest_offset"));
        }
        showValue(X_FIELD, fixed ? phase.getLeapFixedX() : phase.getLeapOffsetX(), fixed || arena);
        showValue(Y_FIELD, fixed ? phase.getLeapFixedY() : phase.getLeapOffsetY(), fixed || arena);
        showValue(Z_FIELD, fixed ? phase.getLeapFixedZ() : phase.getLeapOffsetZ(), fixed || arena);

        GuiButtonNop here = getButton(HERE_BUTTON);
        if (here != null) {
            // Only the fixed mode has somewhere to put a position. The offset is measured
            // from the arena spot, and standing somewhere says nothing about what it is.
            here.setEnabled(fixed);
        }
    }

    private void showValue(int id, int value, boolean editable) {
        GuiTextFieldNop field = getTextField(id);
        if (field != null) {
            field.setValue(Integer.toString(value));
            field.enabled = editable;
        }
    }

    /** Two small numbers on one line, so the whole ability still fits a single screen. */
    private void addPairRow(int leftId, int rightId, String label, int y,
                            int leftValue, int leftMin, int leftMax, int leftFallback,
                            int rightValue, int rightMin, int rightMax, int rightFallback) {
        addLabel(new GuiLabel(leftId, label, guiLeft + 6, y + 6));
        addPairedField(leftId, guiLeft + 130, y, leftValue, leftMin, leftMax, leftFallback);
        addPairedField(rightId, guiLeft + 190, y, rightValue, rightMin, rightMax, rightFallback);
    }

    private void addPairedField(int id, int x, int y, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x, y, 52, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    private void addNumberField(int id, String label, int y, int value, int min, int max, int fallback) {
        addLabel(new GuiLabel(id, label, guiLeft + 6, y + 6));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + 172, y, 70, 20,
                Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == EFFECTS_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossEffectList(phase.getLeapEffects(), "cnpcgeckoaddon.boss.effects_leap"));
        } else if (button.id == IMPACT_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossLeapImpact(phase, phaseIndex));
        } else if (button.id == ENABLED_BUTTON) {
            phase.setLeapEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == MODE_BUTTON) {
            // Store what is on screen against the old mode first: the same three fields
            // stand for the offset in one mode and for absolute coordinates in another.
            applyFields();
            phase.setLeapMode(button.getValue());
            refresh();
        } else if (button.id == TARGET_MODE_BUTTON) {
            phase.setLeapTargetMode(button.getValue());
        } else if (button.id == HERE_BUTTON) {
            takePlayerPosition();
        } else if (button.id == ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting leap animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setLeapAnimation(name);
                getTextField(ANIMATION_FIELD).setValue(name);
                // The delay field lives on the impact screen, so only the phase is updated.
                BossAnimationGuiUtil.syncDelayToAnimation(this, npc, name,
                        SubGuiBossLeapImpact.ACTION_DELAY_FIELD, phase::setLeapActionDelayTicks);
            }));
        } else if (button.id == LAND_ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting landing animation:",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                phase.setLeapLandAnimation(name);
                getTextField(LAND_ANIMATION_FIELD).setValue(name);
            }));
        }
    }

    /** Fills the fields with the block the editor is standing on. */
    private void takePlayerPosition() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || phase.getLeapMode() != BossPhaseData.LEAP_MODE_FIXED) {
            return;
        }
        BlockPos pos = player.blockPosition();
        phase.setLeapFixed(pos.getX(), pos.getY(), pos.getZ());
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
        applyAnimation(ANIMATION_FIELD, phase.getLeapAnimation(), phase::setLeapAnimation);
        applyAnimation(LAND_ANIMATION_FIELD, phase.getLeapLandAnimation(), phase::setLeapLandAnimation);
        GuiTextFieldNop height = getTextField(HEIGHT_FIELD);
        if (height != null) phase.setLeapHeight(height.getInteger());
        GuiTextFieldNop min = getTextField(MIN_RANGE_FIELD);
        GuiTextFieldNop max = getTextField(MAX_RANGE_FIELD);
        if (min != null && max != null) phase.setLeapRange(min.getInteger(), max.getInteger());

        int x = signed(X_FIELD);
        int y = signed(Y_FIELD);
        int z = signed(Z_FIELD);
        if (phase.getLeapMode() == BossPhaseData.LEAP_MODE_FIXED) {
            phase.setLeapFixed(x, y, z);
        } else {
            // The modes with nothing to aim keep writing the offset. It is ignored while
            // they are selected, so anything typed before switching is still there after.
            phase.setLeapOffset(x, y, z);
        }
    }

    private void applyAnimation(int id, String current, java.util.function.Consumer<String> setter) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return;
        }
        String value = field.getValue().trim();
        if (BossAnimationGuiUtil.isValid(npc, value)) setter.accept(value);
        else field.setValue(current);
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
