package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.ai.BossRiftDimension;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.data.BossRiftSettings;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.CustomNpcResourceListener;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The rift's smaller numbers: the spot of a platform built by hand, the blocks the addon builds
 * one of, the lights and the roof, the leash and the fall guard, the tint and the fog a taken
 * player sees, and the nine noises and puffs.
 */
public final class SubGuiBossRiftTuning extends SubGuiBossAbilityTuning {
    private static final int SPOT_X_FIELD = 1;
    private static final int SPOT_Y_FIELD = 2;
    private static final int SPOT_Z_FIELD = 3;
    private static final int PLATFORM_BLOCK_FIELD = 4;
    private static final int WALL_BLOCK_FIELD = 5;
    private static final int LIGHT_BLOCK_FIELD = 6;
    private static final int LIGHT_SPACING_FIELD = 7;
    private static final int ROOF_BUTTON = 8;
    private static final int LEASH_FIELD = 9;
    private static final int FALL_GUARD_FIELD = 10;
    private static final int TINT_COLOR_FIELD = 11;
    private static final int TINT_ALPHA_FIELD = 12;
    private static final int TINT_PULSE_FIELD = 13;
    private static final int FOG_COLOR_FIELD = 14;
    private static final int FOG_DISTANCE_FIELD = 15;
    private static final int LOOP_INTERVAL_FIELD = 16;
    private static final int AMBIENT_INTERVAL_FIELD = 17;
    private static final int CUT_SOUND_BUTTON = 18;
    private static final int ENTER_SOUND_BUTTON = 19;
    private static final int EXIT_SOUND_BUTTON = 20;
    private static final int LOOP_SOUND_BUTTON = 21;
    private static final int SUCCESS_SOUND_BUTTON = 22;
    private static final int FAIL_SOUND_BUTTON = 23;
    private static final int CUT_PARTICLES_BUTTON = 24;
    private static final int ENTER_PARTICLES_BUTTON = 25;
    private static final int AMBIENT_PARTICLES_BUTTON = 26;

    /** Where the second lines of wrapped row labels are counted from, clear of the base page's own. */
    private static final int WRAPPED_LABEL = 60;
    private static final int LABEL_X = 8;
    private static final int LABEL_DROP = 6;
    /** Three small fields to a row, the last flush with the right edge. */
    private static final int TRIPLE_X = 118;
    private static final int TRIPLE_SECOND_X = 160;
    private static final int TRIPLE_THIRD_X = 202;
    private static final int TRIPLE_WIDTH = 40;
    /** Two small fields to a row. */
    private static final int PAIR_X = 140;
    private static final int PAIR_SECOND_X = 194;
    private static final int PAIR_WIDTH = 48;
    /** A block id: the room an id needs. */
    private static final int ID_X = 108;
    private static final int ID_WIDTH = 134;

    private final BossRiftSettings rift;
    private int wrappedLabel;

    public SubGuiBossRiftTuning(BossRiftSettings rift) {
        super("cnpcgeckoaddon.boss.rift_tuning_title");
        this.rift = rift;
    }

    @Override
    protected int rows() {
        return 20;
    }

    @Override
    protected void addRows() {
        wrappedLabel = WRAPPED_LABEL;
        int y = nextRow();
        rowLabel(SPOT_X_FIELD, "cnpcgeckoaddon.boss.rift_spot", y, TRIPLE_X);
        addTextField(coordinateField(SPOT_X_FIELD, guiLeft + TRIPLE_X, y, TRIPLE_WIDTH, rift.getArenaX()));
        addTextField(coordinateField(SPOT_Y_FIELD, guiLeft + TRIPLE_SECOND_X, y, TRIPLE_WIDTH, rift.getArenaY()));
        addTextField(coordinateField(SPOT_Z_FIELD, guiLeft + TRIPLE_THIRD_X, y, TRIPLE_WIDTH, rift.getArenaZ()));
        idRow(PLATFORM_BLOCK_FIELD, "cnpcgeckoaddon.boss.rift_block_platform", rift.getPlatformBlock());
        idRow(WALL_BLOCK_FIELD, "cnpcgeckoaddon.boss.rift_block_wall", rift.getWallBlock());
        idRow(LIGHT_BLOCK_FIELD, "cnpcgeckoaddon.boss.rift_block_light", rift.getLightBlock());
        addNumberField(LIGHT_SPACING_FIELD, "cnpcgeckoaddon.boss.rift_light_spacing", nextRow(),
                rift.getLightSpacing(), 0, BossRiftSettings.MAX_LIGHT_SPACING, 6);
        addYesNo(ROOF_BUTTON, "cnpcgeckoaddon.boss.rift_roof", nextRow(), rift.isPlatformRoof());
        addNumberField(LEASH_FIELD, "cnpcgeckoaddon.boss.rift_leash", nextRow(),
                rift.getLeashRadius(), 0, BossRiftSettings.MAX_LEASH_RADIUS, 0);
        addNumberField(FALL_GUARD_FIELD, "cnpcgeckoaddon.boss.rift_fall_guard", nextRow(),
                rift.getFallGuardDepth(), 0, BossRiftSettings.MAX_FALL_GUARD_DEPTH, 8);
        y = nextRow();
        rowLabel(TINT_COLOR_FIELD, "cnpcgeckoaddon.boss.rift_tint", y, TRIPLE_X);
        addTextField(new ThemeTextField(TINT_COLOR_FIELD, this, guiLeft + TRIPLE_X, y, TRIPLE_WIDTH, BUTTON_HEIGHT,
                BossRiftSettings.hex(rift.getTintColor())));
        small(TINT_ALPHA_FIELD, TRIPLE_SECOND_X, y, TRIPLE_WIDTH, rift.getTintAlpha(),
                0, BossRiftSettings.MAX_TINT_ALPHA, 35);
        small(TINT_PULSE_FIELD, TRIPLE_THIRD_X, y, TRIPLE_WIDTH, rift.getTintPulseTicks(),
                0, BossRiftSettings.MAX_TINT_PULSE_TICKS, 40);
        y = nextRow();
        rowLabel(FOG_COLOR_FIELD, "cnpcgeckoaddon.boss.rift_fog", y, PAIR_X);
        addTextField(new ThemeTextField(FOG_COLOR_FIELD, this, guiLeft + PAIR_X, y, PAIR_WIDTH, BUTTON_HEIGHT,
                BossRiftSettings.hex(rift.getFogColor())));
        small(FOG_DISTANCE_FIELD, PAIR_SECOND_X, y, PAIR_WIDTH, rift.getFogDistance(),
                0, BossRiftSettings.MAX_FOG_DISTANCE, 24);
        y = nextRow();
        rowLabel(LOOP_INTERVAL_FIELD, "cnpcgeckoaddon.boss.rift_intervals", y, PAIR_X);
        small(LOOP_INTERVAL_FIELD, PAIR_X, y, PAIR_WIDTH, rift.getLoopIntervalTicks(),
                BossRiftSettings.MIN_CUE_INTERVAL_TICKS, BossRiftSettings.MAX_CUE_INTERVAL_TICKS, 60);
        small(AMBIENT_INTERVAL_FIELD, PAIR_SECOND_X, y, PAIR_WIDTH, rift.getAmbientIntervalTicks(),
                BossRiftSettings.MIN_CUE_INTERVAL_TICKS, BossRiftSettings.MAX_CUE_INTERVAL_TICKS, 20);

        addCueButton(CUT_SOUND_BUTTON, "cnpcgeckoaddon.boss.rift_cue_cut", nextRow(), rift.getCutSound());
        addCueButton(ENTER_SOUND_BUTTON, "cnpcgeckoaddon.boss.rift_cue_enter", nextRow(), rift.getEnterSound());
        addCueButton(EXIT_SOUND_BUTTON, "cnpcgeckoaddon.boss.rift_cue_exit", nextRow(), rift.getExitSound());
        addCueButton(LOOP_SOUND_BUTTON, "cnpcgeckoaddon.boss.rift_cue_loop", nextRow(), rift.getLoopSound());
        addCueButton(SUCCESS_SOUND_BUTTON, "cnpcgeckoaddon.boss.rift_cue_success", nextRow(), rift.getSuccessSound());
        addCueButton(FAIL_SOUND_BUTTON, "cnpcgeckoaddon.boss.rift_cue_fail", nextRow(), rift.getFailSound());
        addCueButton(CUT_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.rift_cue_cut_particles", nextRow(),
                rift.getCutParticles());
        addCueButton(ENTER_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.rift_cue_enter_particles", nextRow(),
                rift.getEnterParticles());
        addCueButton(AMBIENT_PARTICLES_BUTTON, "cnpcgeckoaddon.boss.rift_cue_ambient_particles", nextRow(),
                rift.getAmbientParticles());
    }

    /** A block id typed in across the row. */
    private void idRow(int id, String key, String value) {
        int y = nextRow();
        rowLabel(id, key, y, ID_X);
        addTextField(new ThemeTextField(id, this, guiLeft + ID_X, y, ID_WIDTH, BUTTON_HEIGHT, value));
    }

    private void small(int id, int x, int y, int width, int value, int min, int max, int fallback) {
        GuiTextFieldNop field = new ThemeTextField(id, this, guiLeft + x, y, width, BUTTON_HEIGHT,
                Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    /** A row's label, wrapped onto two lines rather than run under the fields beside it. */
    private void rowLabel(int id, String key, int y, int controlX) {
        int width = controlX - LABEL_X - 4;
        List<String> lines = wrapLines(Component.translatable(key).getString(), width);
        if (lines.size() == 1) {
            addLabel(new ThemeLabel(id, key, guiLeft + LABEL_X, y + LABEL_DROP));
            return;
        }
        int top = y + (BUTTON_HEIGHT - lines.size() * LINE_HEIGHT) / 2;
        for (int i = 0; i < lines.size(); i++) {
            addLabel(new ThemeLabel(i == 0 ? id : wrappedLabel++, Component.literal(lines.get(i)),
                    CustomNpcResourceListener.DefaultTextColor, guiLeft + LABEL_X,
                    top + i * LINE_HEIGHT, width, LINE_HEIGHT));
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ROOF_BUTTON) {
            rift.setPlatformRoof(((GuiButtonYesNo) button).getBoolean());
        }
    }

    @Override
    protected void applyFields() {
        if (getTextField(SPOT_X_FIELD) != null) {
            rift.setArenaX(signed(SPOT_X_FIELD));
            rift.setArenaY(signed(SPOT_Y_FIELD));
            rift.setArenaZ(signed(SPOT_Z_FIELD));
        }
        applyBlock(PLATFORM_BLOCK_FIELD, rift::getPlatformBlock, rift::setPlatformBlock);
        applyBlock(WALL_BLOCK_FIELD, rift::getWallBlock, rift::setWallBlock);
        applyBlock(LIGHT_BLOCK_FIELD, rift::getLightBlock, rift::setLightBlock);
        applyNumberField(LIGHT_SPACING_FIELD, rift::setLightSpacing);
        applyNumberField(LEASH_FIELD, rift::setLeashRadius);
        applyNumberField(FALL_GUARD_FIELD, rift::setFallGuardDepth);
        applyColor(TINT_COLOR_FIELD, rift::getTintColor, rift::setTintColor);
        applyNumberField(TINT_ALPHA_FIELD, rift::setTintAlpha);
        applyNumberField(TINT_PULSE_FIELD, rift::setTintPulseTicks);
        applyColor(FOG_COLOR_FIELD, rift::getFogColor, rift::setFogColor);
        applyNumberField(FOG_DISTANCE_FIELD, rift::setFogDistance);
        applyNumberField(LOOP_INTERVAL_FIELD, rift::setLoopIntervalTicks);
        applyNumberField(AMBIENT_INTERVAL_FIELD, rift::setAmbientIntervalTicks);
    }

    /** Keeps a typed block only when the game has it; otherwise the field snaps back. */
    private void applyBlock(int id, Supplier<String> current, Consumer<String> setter) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return;
        }
        String value = field.getValue().trim();
        if (BossRiftDimension.resolveBlock(value) != null) {
            setter.accept(value);
        } else {
            field.setValue(current.get());
        }
    }

    /** Reads a colour typed as hex, keeping the old one for anything that is not, and shows it back as six digits. */
    private void applyColor(int id, IntSupplier current, IntConsumer setter) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return;
        }
        setter.accept(BossRiftSettings.parseHex(field.getValue(), current.getAsInt()));
        field.setValue(BossRiftSettings.hex(current.getAsInt()));
    }
}
