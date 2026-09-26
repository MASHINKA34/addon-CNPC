package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossParticleCue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/** The editor behind every particle cue button: which particle, how many, and a look at them. */
public final class SubGuiBossParticleCue extends SubGuiFieldScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int PARTICLE_FIELD = 2;
    private static final int COUNT_FIELD = 3;
    private static final int TEST_BUTTON = 4;
    private static final int RESET_BUTTON = 5;
    private static final int TITLE_LABEL = 30;
    private static final int ERROR_LABEL = 31;
    private static final int FIRST_HINT_LABEL = 40;

    private static final int FIRST_ROW_Y = 26;
    private static final int ROW_HEIGHT = 22;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TEXT_FIELD_X = 108;
    private static final int ERROR_COLOR = 0xFF5555;
    private static final String HINT = "cnpcgeckoaddon.cue.hint";

    /** How many of a big puff the preview spits: enough to read, cheap enough to spam. */
    private static final int MAX_PREVIEW = 40;

    private final String titleKey;
    private final BossParticleCue cue;
    private boolean rejected;

    public SubGuiBossParticleCue(String titleKey, BossParticleCue cue) {
        this.titleKey = titleKey;
        this.cue = cue;
        imageWidth = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        imageHeight = doneButtonY() + BUTTON_HEIGHT + 6;
        super.init();
        addLabel(new ThemeLabel(TITLE_LABEL, Component.translatable("cnpcgeckoaddon.cue.particle_title")
                .append(": ").append(Component.translatable(titleKey)), 0xFFFFFF,
                guiLeft + 8, guiTop + 8, imageWidth - 16, LINE_HEIGHT));
        int y = guiTop + FIRST_ROW_Y;

        addYesNo(ENABLED_BUTTON, "cnpcgeckoaddon.cue.enabled", y, cue.isEnabled());
        y += ROW_HEIGHT;

        addLabel(new ThemeLabel(PARTICLE_FIELD, "cnpcgeckoaddon.cue.particle_id", guiLeft + 8, y + 6));
        addTextField(new ThemeTextField(PARTICLE_FIELD, this, guiLeft + TEXT_FIELD_X, y, 86,
                BUTTON_HEIGHT, cue.getParticleId()));
        addButton(new ThemeButton(this, PARTICLE_FIELD, guiLeft + 198, y, 44, BUTTON_HEIGHT,
                "mco.template.button.select"));
        y += ROW_HEIGHT;

        addNumberField(COUNT_FIELD, "cnpcgeckoaddon.cue.count", y, cue.getCount(),
                BossParticleCue.MIN_COUNT, BossParticleCue.MAX_COUNT, cue.getDefaultCount());
        y += ROW_HEIGHT;

        addButton(new ThemeButton(this, TEST_BUTTON, guiLeft + 8, y, 114, BUTTON_HEIGHT,
                "cnpcgeckoaddon.cue.test"));
        addButton(new ThemeButton(this, RESET_BUTTON, guiLeft + 128, y, 114, BUTTON_HEIGHT,
                "cnpcgeckoaddon.cue.reset"));

        int hintEnd = addWrappedText(FIRST_HINT_LABEL, hintText(), guiTop + hintY());
        addLabel(new ThemeLabel(ERROR_LABEL, Component.literal(rejected ? errorText() : ""),
                ERROR_COLOR, guiLeft + 8, hintEnd, imageWidth - 16, LINE_HEIGHT));
        addDoneButton(guiLeft + 182, guiTop + doneButtonY(), 60, BUTTON_HEIGHT);
    }

    private static String hintText() {
        return Component.translatable(HINT).getString();
    }

    private static String errorText() {
        return Component.translatable("cnpcgeckoaddon.cue.unknown_id").getString();
    }

    private int hintY() {
        return FIRST_ROW_Y + 4 * ROW_HEIGHT + 4;
    }

    private int doneButtonY() {
        return hintY() + wrapLines(hintText(), imageWidth - 16).size() * LINE_HEIGHT + LINE_HEIGHT + 4;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            cue.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == PARTICLE_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.cue_particle",
                    BossParticleCue.getSelectableIds(), name -> {
                cue.setParticleId(name);
                showRejected(false);
                getTextField(PARTICLE_FIELD).setValue(cue.getParticleId());
            }));
        } else if (button.id == TEST_BUTTON) {
            applyFields();
            spitHere();
        } else if (button.id == RESET_BUTTON) {
            cue.reset();
            showRejected(false);
            requestLayout();
        }
    }

    /**
     * Spits the puff round the editor's own feet, on the client alone.
     *
     * <p>The ability's own dust has no ability behind it in a menu, so the preview shows the
     * first one's colour: what it says is how dense the puff is, not which hue it will be.</p>
     */
    private void spitHere() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        ParticleOptions options = cue.resolve(BossAbilityKind.AREA);
        if (options == null) {
            return;
        }
        for (int i = 0; i < Math.min(cue.getCount(), MAX_PREVIEW); i++) {
            minecraft.level.addParticle(options,
                    player.getX() + (player.getRandom().nextDouble() - 0.5D),
                    player.getY() + 0.2D + player.getRandom().nextDouble() * 0.6D,
                    player.getZ() + (player.getRandom().nextDouble() - 0.5D),
                    0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void applyFields() {
        applyNumberField(COUNT_FIELD, cue::setCount);
        GuiTextFieldNop field = getTextField(PARTICLE_FIELD);
        if (field == null) {
            return;
        }
        String value = field.getValue().trim();
        if (BossParticleCue.isKnownParticle(value)) {
            cue.setParticleId(value);
            showRejected(false);
        } else {
            field.setValue(cue.getParticleId());
            showRejected(true);
        }
    }

    private void showRejected(boolean value) {
        rejected = value;
        GuiLabel label = getLabel(ERROR_LABEL);
        if (label != null) {
            label.setMessage(Component.literal(value ? errorText() : ""));
        }
    }

    @Override
    protected int toggleButtonX() {
        return TEXT_FIELD_X;
    }

    @Override
    protected int toggleButtonWidth() {
        return 134;
    }
}
