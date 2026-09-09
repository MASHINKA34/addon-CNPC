package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.ai.BossAbility;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossCastSpot;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/**
 * One ability's cast spot: where it is, how long the walk there may take, which way the
 * boss faces on it, and how long it stays.
 *
 * <p>How the boss gets there - a blink, a walk, or not at all - is chosen on the row that
 * opened this screen, so the row reads at a glance which abilities send the boss anywhere.</p>
 */
public final class SubGuiBossCastSpot extends SubGuiFieldScreen {
    private static final int COORDINATE_BUTTON = 1;
    private static final int X_FIELD = 2;
    private static final int Y_FIELD = 3;
    private static final int Z_FIELD = 4;
    private static final int HERE_BUTTON = 5;
    private static final int TRAVEL_FIELD = 6;
    private static final int YAW_MODE_BUTTON = 7;
    private static final int YAW_FIELD = 8;
    private static final int STAY_MODE_BUTTON = 9;
    private static final int STAY_TICKS_FIELD = 10;
    private static final int TITLE_LABEL = 30;
    private static final int FIRST_HINT_LABEL = 40;

    private static final int ROW_HEIGHT = 23;

    private final EntityNPCInterface npc;
    private final int phaseIndex;
    private final BossAbility ability;
    private final BossCastSpot spot;

    public SubGuiBossCastSpot(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex, BossAbility ability) {
        this.npc = npc;
        this.phaseIndex = phaseIndex;
        this.ability = ability;
        this.spot = ability.castSpot(phase);
        imageWidth = 256;
        imageHeight = 276;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(TITLE_LABEL, I18n.get(BossAbilityKind.LABELS[ability.kind()]) + " · "
                + BossAnimationGuiUtil.phaseTitle("cnpcgeckoaddon.boss.phase", phaseIndex),
                guiLeft + 8, guiTop + 7, 0xFFFFFF));
        int y = guiTop + 25;

        addLabel(new GuiLabel(COORDINATE_BUTTON, "cnpcgeckoaddon.boss.cast_spot_coords", guiLeft + 8, y + 6));
        addButton(new GuiButtonNop(this, COORDINATE_BUTTON, guiLeft + 112, y, 130, 20,
                BossCastSpot.COORDINATE_LABELS, spot.getCoordinateMode()));
        y += ROW_HEIGHT;

        addLabel(new GuiLabel(X_FIELD, "cnpcgeckoaddon.boss.minion_spawn_xyz", guiLeft + 8, y + 6));
        addTextField(coordinateField(X_FIELD, guiLeft + 76, y, 52, spot.getX()));
        addTextField(coordinateField(Y_FIELD, guiLeft + 132, y, 52, spot.getY()));
        addTextField(coordinateField(Z_FIELD, guiLeft + 188, y, 52, spot.getZ()));
        y += ROW_HEIGHT;

        addButton(new GuiButtonNop(this, HERE_BUTTON, guiLeft + 112, y, 130, 20,
                "cnpcgeckoaddon.boss.minion_spawn_here"));
        y += ROW_HEIGHT;

        addNumberField(TRAVEL_FIELD, "cnpcgeckoaddon.boss.cast_spot_travel", y, spot.getTravelTimeoutTicks(),
                BossCastSpot.MIN_TRAVEL_TIMEOUT_TICKS, BossCastSpot.MAX_TRAVEL_TIMEOUT_TICKS, 100);
        y += ROW_HEIGHT;

        addCycle(YAW_MODE_BUTTON, "cnpcgeckoaddon.boss.cast_spot_yaw", y, BossCastSpot.YAW_LABELS, spot.getYawMode());
        y += ROW_HEIGHT;

        // The angle itself, under the choice that reads it: the row says "Fixed" and holds the number.
        addLabel(new GuiLabel(YAW_FIELD, BossCastSpot.YAW_LABELS[BossCastSpot.YAW_FIXED], guiLeft + 8, y + 6));
        GuiTextFieldNop yaw = new GuiTextFieldNop(YAW_FIELD, this, guiLeft + numberFieldX(), y,
                numberFieldWidth(), numberFieldHeight(), Float.toString(spot.getYaw()));
        yaw.setFloatsOnly();
        yaw.setMinMaxDefault(-180.0F, 180.0F, 0.0F);
        addTextField(yaw);
        y += ROW_HEIGHT;

        addCycle(STAY_MODE_BUTTON, "cnpcgeckoaddon.boss.cast_spot_stay", y, BossCastSpot.STAY_LABELS, spot.getStayMode());
        y += ROW_HEIGHT;

        addNumberField(STAY_TICKS_FIELD, "cnpcgeckoaddon.boss.cast_spot_stay.ticks", y, spot.getStayTicks(),
                0, BossCastSpot.MAX_STAY_TICKS, 100);
        y += ROW_HEIGHT;

        addWrappedHint(FIRST_HINT_LABEL, "cnpcgeckoaddon.boss.cast_spot_hint", y + 2);
        addDoneButton(guiLeft + 182, guiTop + 250, 60, 20);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == COORDINATE_BUTTON) {
            applyFields();
            spot.setCoordinateMode(button.getValue());
        } else if (button.id == HERE_BUTTON) {
            takePlayerPosition();
        } else if (button.id == YAW_MODE_BUTTON) {
            spot.setYawMode(button.getValue());
        } else if (button.id == STAY_MODE_BUTTON) {
            spot.setStayMode(button.getValue());
        }
    }

    /**
     * Writes where the builder stands into the fields, in whichever coordinate mode is
     * chosen: the block itself, or its offset from the boss - which on the client is the
     * npc's own position, the closest thing to its home the screen can see.
     */
    private void takePlayerPosition() {
        applyFields();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        BlockPos playerPos = player.blockPosition();
        if (spot.getCoordinateMode() == BossCastSpot.COORDINATE_ABSOLUTE) {
            spot.setPosition(playerPos.getX(), playerPos.getY(), playerPos.getZ());
        } else {
            BlockPos bossPos = npc.blockPosition();
            spot.setPosition(playerPos.getX() - bossPos.getX(), playerPos.getY() - bossPos.getY(),
                    playerPos.getZ() - bossPos.getZ());
        }
        getTextField(X_FIELD).setValue(Integer.toString(spot.getX()));
        getTextField(Y_FIELD).setValue(Integer.toString(spot.getY()));
        getTextField(Z_FIELD).setValue(Integer.toString(spot.getZ()));
    }

    @Override
    protected void applyFields() {
        spot.setPosition(signed(X_FIELD), signed(Y_FIELD), signed(Z_FIELD));
        applyNumberField(TRAVEL_FIELD, spot::setTravelTimeoutTicks);
        GuiTextFieldNop yaw = getTextField(YAW_FIELD);
        if (yaw != null) spot.setYaw(yaw.getFloat());
        applyNumberField(STAY_TICKS_FIELD, spot::setStayTicks);
    }
}
