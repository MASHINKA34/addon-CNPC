package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.ZoneSelection;
import com.goodbird.cnpcgeckoaddon.client.ZoneSelectionClient;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.renderer.BossZonePreview;
import com.goodbird.cnpcgeckoaddon.data.BossCastSpot;
import com.goodbird.cnpcgeckoaddon.data.NpcLaunchPadData;
import com.goodbird.cnpcgeckoaddon.mixin.INpcLaunchPadData;
import com.goodbird.cnpcgeckoaddon.utils.ZoneCoordinates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;

/** Whether this npc is a launch pad, which block it throws players onto, and how. */
public final class SubGuiNpcLaunchPad extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int COORDINATE_BUTTON = 2;
    private static final int X_FIELD = 3;
    private static final int Y_FIELD = 4;
    private static final int Z_FIELD = 5;
    private static final int HERE_BUTTON = 6;
    private static final int HEIGHT_FIELD = 7;
    private static final int COOLDOWN_FIELD = 8;
    private static final int NO_FALL_BUTTON = 9;
    private static final int SOUND_BUTTON = 10;
    private static final int LIFETIME_FIELD = 11;
    private static final int TOUCH_MARGIN_FIELD = 12;
    private static final int GRACE_FIELD = 13;
    private static final int LAUNCH_SOUND_BUTTON = 14;
    private static final int LAUNCH_PARTICLES_BUTTON = 15;
    private static final int EXPIRE_PARTICLES_BUTTON = 16;
    private static final int SELECT_BUTTON = 17;
    private static final int TITLE_LABEL = 30;
    private static final int FIRST_HINT_LABEL = 40;
    private static final int SECOND_HINT_LABEL = 50;

    private static final int FIRST_ROW_Y = 26;
    private static final int ROW_HEIGHT = 22;
    private static final int ROWS = 14;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BOTTOM_MARGIN = 6;
    private static final String HINT = "cnpcgeckoaddon.launch.hint";
    private static final String TUNING_HINT = "cnpcgeckoaddon.npc.tuning_hint";

    private final NpcLaunchPadData data;
    private final EntityNPCInterface npc;

    public SubGuiNpcLaunchPad(DataAI ai, EntityNPCInterface npc) {
        data = ((INpcLaunchPadData) ai).cnpcgeckoaddon$getNpcLaunchPadData();
        this.npc = npc;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // Settled before super.init() centres the panel on it: how many lines the hint wraps to
        // is up to the locale.
        imageHeight = doneButtonY() + BUTTON_HEIGHT + BOTTOM_MARGIN;
        super.init();
        addLabel(new ThemeLabel(TITLE_LABEL, "cnpcgeckoaddon.launch.title", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + FIRST_ROW_Y;

        addYesNo(ENABLED_BUTTON, "cnpcgeckoaddon.launch.enabled", y, data.isEnabled());
        y += ROW_HEIGHT;

        // The cast spot's two modes in the same order; the offset here is from the pad itself.
        addLabel(new ThemeLabel(COORDINATE_BUTTON, "cnpcgeckoaddon.boss.cast_spot_coords", guiLeft + 8, y + 6));
        addButton(new ThemeButton(this, COORDINATE_BUTTON, guiLeft + 112, y, 130, BUTTON_HEIGHT,
                BossCastSpot.COORDINATE_LABELS, data.getCoordinateMode()));
        y += ROW_HEIGHT;

        addLabel(new ThemeLabel(X_FIELD, "cnpcgeckoaddon.boss.minion_spawn_xyz", guiLeft + 8, y + 6));
        addTextField(coordinateField(X_FIELD, guiLeft + 76, y, 52, data.getX()));
        addTextField(coordinateField(Y_FIELD, guiLeft + 132, y, 52, data.getY()));
        addTextField(coordinateField(Z_FIELD, guiLeft + 188, y, 52, data.getZ()));
        y += ROW_HEIGHT;

        addButton(new ThemeButton(this, SELECT_BUTTON, guiLeft + 8, y, 100, BUTTON_HEIGHT,
                ZoneSelectionClient.SELECT_POINT));
        addButton(new ThemeButton(this, HERE_BUTTON, guiLeft + 112, y, 130, BUTTON_HEIGHT,
                "cnpcgeckoaddon.boss.aggro_zone_here"));
        y += ROW_HEIGHT;

        addNumberField(HEIGHT_FIELD, "cnpcgeckoaddon.launch.height", y, data.getArcHeight(),
                NpcLaunchPadData.MIN_ARC_HEIGHT, NpcLaunchPadData.MAX_ARC_HEIGHT, NpcLaunchPadData.DEFAULT_ARC_HEIGHT);
        y += ROW_HEIGHT;

        addNumberField(COOLDOWN_FIELD, "cnpcgeckoaddon.launch.cooldown", y, data.getCooldownTicks(),
                0, NpcLaunchPadData.MAX_COOLDOWN_TICKS, NpcLaunchPadData.DEFAULT_COOLDOWN_TICKS);
        y += ROW_HEIGHT;

        addYesNo(NO_FALL_BUTTON, "cnpcgeckoaddon.launch.no_fall", y, data.isNoFallDamage());
        y += ROW_HEIGHT;

        addYesNo(SOUND_BUTTON, "cnpcgeckoaddon.launch.sound", y, data.isSound());
        y += ROW_HEIGHT;

        addNumberField(LIFETIME_FIELD, "cnpcgeckoaddon.launch.lifetime", y, data.getLifetimeTicks(),
                0, NpcLaunchPadData.MAX_LIFETIME_TICKS, 0);
        y += ROW_HEIGHT;

        addNumberField(TOUCH_MARGIN_FIELD, "cnpcgeckoaddon.launch.touch_margin", y,
                data.getTouchMarginTenths(), 0, NpcLaunchPadData.MAX_TOUCH_MARGIN_TENTHS,
                NpcLaunchPadData.DEFAULT_TOUCH_MARGIN_TENTHS);
        y += ROW_HEIGHT;

        addNumberField(GRACE_FIELD, "cnpcgeckoaddon.launch.grace", y,
                data.getLandingGraceTicks(), 0, NpcLaunchPadData.MAX_LANDING_GRACE_TICKS,
                NpcLaunchPadData.DEFAULT_LANDING_GRACE_TICKS);
        y += ROW_HEIGHT;

        addCueButton(LAUNCH_SOUND_BUTTON, "cnpcgeckoaddon.launch.cue_launch", y, data.getLaunchSound());
        y += ROW_HEIGHT;

        addCueButton(LAUNCH_PARTICLES_BUTTON, "cnpcgeckoaddon.launch.cue_launch_particles", y,
                data.getLaunchParticles());
        y += ROW_HEIGHT;

        addCueButton(EXPIRE_PARTICLES_BUTTON, "cnpcgeckoaddon.launch.cue_expire", y,
                data.getExpireParticles());

        int hintY = addWrappedHint(FIRST_HINT_LABEL, HINT, guiTop + hintY());
        addWrappedHint(SECOND_HINT_LABEL, TUNING_HINT, hintY);
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, BUTTON_HEIGHT);
    }

    /** Where the hint starts, from the panel's top: just under the last row. */
    private int hintY() {
        return FIRST_ROW_Y + ROWS * ROW_HEIGHT + 4;
    }

    /** Where the done button goes, from the panel's top: just under the two hints. */
    private int doneButtonY() {
        return hintY() + wrappedHintHeight(HINT) + wrappedHintHeight(TUNING_HINT) + 4;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            data.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == COORDINATE_BUTTON) {
            applyFields();
            data.setCoordinateMode(button.getValue());
        } else if (button.id == HERE_BUTTON) {
            takePlayerPosition();
        } else if (button.id == SELECT_BUTTON) {
            applyFields();
            // The block clicked itself, not the one in front of it: the pad lands players on top of
            // the block it names, the one "use my position" takes from under the feet. The pick's
            // marker stands on the same block, so it reads the numbers the fields get.
            ZoneSelectionClient.selectPoint(BossZonePreview.KIND_PLAIN, ZoneSelection.Pick.CLICKED, (picked, anchor) -> {
                BlockPos at = ZoneCoordinates.toStored(picked.block(),
                        data.getCoordinateMode() == NpcLaunchPadData.COORDINATE_ABSOLUTE, anchor);
                data.setPosition(at.getX(), at.getY(), at.getZ());
            });
        } else if (button.id == NO_FALL_BUTTON) {
            data.setNoFallDamage(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == SOUND_BUTTON) {
            data.setSound(((GuiButtonYesNo) button).getBoolean());
        }
    }

    /**
     * Writes the block the builder stands on into the fields, in whichever coordinate mode is
     * chosen: the block itself, or its offset from the block the pad stands in.
     *
     * <p>The block under the feet rather than the one the feet are in, because the pad lands
     * players on top of the block it names - standing where they should come down and pressing
     * this is the whole setup.</p>
     */
    private void takePlayerPosition() {
        applyFields();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        BlockPos floor = player.blockPosition().below();
        if (data.getCoordinateMode() == NpcLaunchPadData.COORDINATE_ABSOLUTE) {
            data.setPosition(floor.getX(), floor.getY(), floor.getZ());
        } else {
            BlockPos pad = npc.blockPosition();
            data.setPosition(floor.getX() - pad.getX(), floor.getY() - pad.getY(), floor.getZ() - pad.getZ());
        }
        getTextField(X_FIELD).setValue(Integer.toString(data.getX()));
        getTextField(Y_FIELD).setValue(Integer.toString(data.getY()));
        getTextField(Z_FIELD).setValue(Integer.toString(data.getZ()));
    }

    @Override
    protected void applyFields() {
        data.setPosition(signed(X_FIELD), signed(Y_FIELD), signed(Z_FIELD));
        applyNumberField(HEIGHT_FIELD, data::setArcHeight);
        applyNumberField(COOLDOWN_FIELD, data::setCooldownTicks);
        applyNumberField(LIFETIME_FIELD, data::setLifetimeTicks);
        applyNumberField(TOUCH_MARGIN_FIELD, data::setTouchMarginTenths);
        applyNumberField(GRACE_FIELD, data::setLandingGraceTicks);
    }

    /** Right of the longest toggle label, "Touching it launches players", with room to spare. */
    @Override
    protected int toggleButtonX() {
        return 160;
    }

    @Override
    protected int toggleButtonWidth() {
        return 82;
    }
}
