package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.ZoneSelectionClient;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeYesNo;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossConeAimPoint;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.utils.ZoneCoordinates;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;

/**
 * One point a cone strike is aimed at: the block on the middle of its cone, given the way a
 * summon's point is - as an offset from the arena or a fixed block - with the same "use my
 * position".
 */
public final class SubGuiBossConeAimPoint extends SubGuiFieldScreen implements BossZoneScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int COORDINATE_BUTTON = 2;
    private static final int X_FIELD = 3;
    private static final int Y_FIELD = 4;
    private static final int Z_FIELD = 5;
    private static final int HERE_BUTTON = 10;
    private static final int DELETE_BUTTON = 11;
    private static final int ARENA_HINT_LABEL = 12;
    private static final int SELECT_BUTTON = 13;
    private static final int TITLE_LABEL = 30;

    /** Three rows, the hint and the buttons: the summon point's screen without its clone rows. */
    private static final int HINT_Y = 97;
    /** The pick in the world, on a row of its own between the hint and the buttons. */
    private static final int SELECT_Y = 112;
    private static final int BUTTONS_Y = 136;

    private static final String[] COORDINATE_LABELS = {
            "cnpcgeckoaddon.boss.minion_spawn_arena",
            "cnpcgeckoaddon.boss.minion_spawn_fixed"
    };

    private final EntityNPCInterface npc;
    private final BossPhaseData phase;
    private final int phaseIndex;
    private final int index;
    private final BossConeAimPoint point;

    public SubGuiBossConeAimPoint(EntityNPCInterface npc, BossPhaseData phase, int phaseIndex, int index) {
        this.npc = npc;
        this.phase = phase;
        this.phaseIndex = phaseIndex;
        this.index = index;
        this.point = phase.cone().getPoints().get(index);
        imageWidth = 256;
        imageHeight = BUTTONS_Y + 26;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new ThemeLabel(TITLE_LABEL, BossAnimationGuiUtil.phaseTitle(
                "cnpcgeckoaddon.boss.cone_point_title", phaseIndex),
                guiLeft + 8, guiTop + 7, 0xFFFFFF));
        int y = guiTop + 25;
        addLabel(new ThemeLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.ability_enabled", guiLeft + 8, y + 6));
        addButton(new ThemeYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, point.isEnabled()));
        y += 23;

        addLabel(new ThemeLabel(COORDINATE_BUTTON, "cnpcgeckoaddon.boss.minion_spawn_coordinate",
                guiLeft + 8, y + 6));
        addButton(new ThemeButton(this, COORDINATE_BUTTON, guiLeft + 112, y, 130, 20,
                COORDINATE_LABELS, point.getCoordinateMode()));
        y += 23;

        addLabel(new ThemeLabel(X_FIELD, "cnpcgeckoaddon.boss.minion_spawn_xyz", guiLeft + 8, y + 6));
        addTextField(coordinateField(X_FIELD, guiLeft + 76, y, 52, point.getX()));
        addTextField(coordinateField(Y_FIELD, guiLeft + 132, y, 52, point.getY()));
        addTextField(coordinateField(Z_FIELD, guiLeft + 188, y, 54, point.getZ()));

        addLabel(new ThemeLabel(ARENA_HINT_LABEL, "cnpcgeckoaddon.boss.minion_spawn_arena_hint",
                guiLeft + 8, guiTop + HINT_Y, 0xA0A0A0));
        addButton(new ThemeButton(this, SELECT_BUTTON, guiLeft + 8, guiTop + SELECT_Y, 234, 20,
                ZoneSelectionClient.SELECT_POINT));
        // Wider than the summon point's: "use my position" runs past 92 pixels in Russian.
        addButton(new ThemeButton(this, HERE_BUTTON, guiLeft + 8, guiTop + BUTTONS_Y, 110, 20,
                "cnpcgeckoaddon.boss.minion_spawn_here"));
        addButton(new ThemeButton(this, DELETE_BUTTON, guiLeft + 122, guiTop + BUTTONS_Y, 56, 20,
                "cnpcgeckoaddon.boss.minion_spawn_delete"));
        addDoneButton(guiLeft + 182, guiTop + BUTTONS_Y, 60, 20);
        updateCoordinateHint();
    }

    private void updateCoordinateHint() {
        GuiLabel hint = getLabel(ARENA_HINT_LABEL);
        if (hint != null) {
            hint.visible = point.getCoordinateMode() == BossConeAimPoint.COORDINATE_ARENA_OFFSET;
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
        } else if (button.id == SELECT_BUTTON) {
            applyFields();
            ZoneSelectionClient.selectPoint(BossAbilityKind.CONE, (picked, anchor) -> {
                BlockPos at = ZoneCoordinates.toStored(picked.inFront(),
                        point.getCoordinateMode() == BossConeAimPoint.COORDINATE_FIXED, anchor);
                point.setPosition(at.getX(), at.getY(), at.getZ());
            });
        } else if (button.id == DELETE_BUTTON) {
            phase.cone().getPoints().remove(index);
            close();
        }
    }

    /** The block the editor stands on, as a fixed block or as an offset from the boss, like a summon point's. */
    private void takePlayerPosition() {
        applyFields();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        BlockPos playerPos = player.blockPosition();
        if (point.getCoordinateMode() == BossConeAimPoint.COORDINATE_FIXED) {
            point.setPosition(playerPos.getX(), playerPos.getY(), playerPos.getZ());
        } else {
            BlockPos bossPos = npc.blockPosition();
            point.setPosition(playerPos.getX() - bossPos.getX(), playerPos.getY() - bossPos.getY(),
                    playerPos.getZ() - bossPos.getZ());
        }
        getTextField(X_FIELD).setValue(Integer.toString(point.getX()));
        getTextField(Y_FIELD).setValue(Integer.toString(point.getY()));
        getTextField(Z_FIELD).setValue(Integer.toString(point.getZ()));
    }

    @Override
    protected void applyFields() {
        point.setPosition(signed(X_FIELD), signed(Y_FIELD), signed(Z_FIELD));
    }

    /** The spot being edited. */
    @Override
    public Object zoneFocus() {
        return point;
    }
}
