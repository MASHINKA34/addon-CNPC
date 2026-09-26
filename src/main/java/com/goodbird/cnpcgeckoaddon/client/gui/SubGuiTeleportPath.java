package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.client.gui.theme.GeckoTheme;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeButton;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeIcons;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeLabel;
import com.goodbird.cnpcgeckoaddon.client.gui.theme.ThemeTextField;
import com.goodbird.cnpcgeckoaddon.client.renderer.BossZonePreview;
import com.goodbird.cnpcgeckoaddon.config.AddonClientConfig;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

public final class SubGuiTeleportPath extends SubGuiFieldScreen implements BossZoneScreen {
    private static final int ENABLED_BUTTON = 1;
    private static final int COMBAT_ONLY_BUTTON = 2;
    private static final int STATIONARY_BUTTON = 3;
    private static final int PHASE_COUNT_FIELD = 4;
    private static final int TRANSITION_ANIMATION_FIELD = 5;
    private static final int TRANSITION_LOCK_FIELD = 6;
    private static final int BOSS_BAR_BUTTON = 25;
    private static final int RESET_BUTTON = 26;
    private static final int RAGE_BUTTON = 27;
    private static final int CHEST_BUTTON = 28;
    private static final int TELEGRAPH_BUTTON = 29;
    private static final int HEALTH_LINK_BUTTON = 30;
    private static final int TUNING_BUTTON = 31;
    private static final int ZONES_BUTTON = 32;
    private static final int THEME_BUTTON = 33;

    /** The menu's height without the theme's switch. */
    private static final int BASE_HEIGHT = 322;

    /** The row the theme's switch adds under the zones' one. */
    private static final int THEME_ROW = 22;

    private final TeleportPathData data;
    private final EntityNPCInterface npc;

    public SubGuiTeleportPath(DataAI ai, EntityNPCInterface npc) {
        this.data = ((ITeleportPathData) ai).cnpcgeckoaddon$getTeleportPathData();
        this.data.markConfigured();
        this.npc = npc;
        imageWidth = 256;
        // Two rows taller than the settings grid: one for the health link, one for the
        // fine-tuning, and Done on a line of its own under them. The tall-screen drawing tiles
        // the panel to any height, and the screen scrolls in a window too short for it.
        imageHeight = BASE_HEIGHT;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        // The theme's switch only while there is a theme to switch to: without its textures the
        // menu is the one it was, row for row.
        imageHeight = GeckoTheme.available() ? BASE_HEIGHT + THEME_ROW : BASE_HEIGHT;
        super.init();
        // A row tighter than the usual 26 all the way down: it buys the fifth row of
        // buttons the ninth settings screen needs, and a field still clears the one above.
        int y = guiTop + 8;
        addYesNo(ENABLED_BUTTON, "cnpcgeckoaddon.teleport.enabled", y, data.isEnabled());
        y += 21;
        addYesNo(COMBAT_ONLY_BUTTON, "cnpcgeckoaddon.teleport.combat_only", y, data.isCombatOnly());
        y += 21;
        addYesNo(STATIONARY_BUTTON, "cnpcgeckoaddon.boss.stationary", y, data.isStationary());
        y += 21;

        addNumberField(PHASE_COUNT_FIELD, "cnpcgeckoaddon.boss.phase_count", y,
                data.getPhaseCount(), TeleportPathData.MIN_PHASES, TeleportPathData.MAX_PHASES, 2);
        y += 21;

        addLabel(new ThemeLabel(TRANSITION_ANIMATION_FIELD, "cnpcgeckoaddon.boss.transition_anim", guiLeft + 8, y + 6));
        addTextField(new ThemeTextField(TRANSITION_ANIMATION_FIELD, this, guiLeft + 98, y, 96, 20,
                data.getPhaseTransitionAnimation()));
        addButton(new ThemeButton(this, TRANSITION_ANIMATION_FIELD, guiLeft + 198, y, 44, 20,
                "mco.template.button.select"));
        y += 21;
        addNumberField(TRANSITION_LOCK_FIELD, "cnpcgeckoaddon.boss.transition_lock", y,
                data.getPhaseTransitionLockTicks(), 0, 1200, 40);

        // In the theme each section's button carries the section's icon; reset and rage have none.
        addButton(new ThemeButton(this, 22, guiLeft + 8, guiTop + 140, 114, 20,
                "cnpcgeckoaddon.boss.targeting_settings").withIcon(ThemeIcons.AGGRO_ZONE));
        addButton(new ThemeButton(this, 23, guiLeft + 128, guiTop + 140, 114, 20,
                "cnpcgeckoaddon.boss.minion_settings").withIcon(BossAbilityKind.SUMMON));
        addButton(new ThemeButton(this, RESET_BUTTON, guiLeft + 8, guiTop + 162, 114, 20,
                "cnpcgeckoaddon.boss.reset_settings"));
        addButton(new ThemeButton(this, RAGE_BUTTON, guiLeft + 128, guiTop + 162, 114, 20,
                "cnpcgeckoaddon.boss.rage_settings"));
        addButton(new ThemeButton(this, 24, guiLeft + 8, guiTop + 184, 114, 20,
                "cnpcgeckoaddon.boss.explosion_settings").withIcon(BossAbilityKind.BLAST));
        addButton(new ThemeButton(this, CHEST_BUTTON, guiLeft + 128, guiTop + 184, 114, 20,
                "cnpcgeckoaddon.boss.chest_settings").withIcon(ThemeIcons.CHEST));
        addButton(new ThemeButton(this, 20, guiLeft + 8, guiTop + 206, 114, 20,
                "cnpcgeckoaddon.boss.phase_settings").withIcon(ThemeIcons.PHASES));
        addButton(new ThemeButton(this, BOSS_BAR_BUTTON, guiLeft + 128, guiTop + 206, 114, 20,
                "cnpcgeckoaddon.boss.bar_settings").withIcon(ThemeIcons.BOSS_BAR));
        // The three longest labels get a row each, and Done the line under them.
        addButton(new ThemeButton(this, TELEGRAPH_BUTTON, guiLeft + 8, guiTop + 228, 234, 20,
                "cnpcgeckoaddon.boss.telegraph_settings").withIcon(ThemeIcons.TELEGRAPH));
        addButton(new ThemeButton(this, HEALTH_LINK_BUTTON, guiLeft + 8, guiTop + 250, 234, 20,
                "cnpcgeckoaddon.boss.health_link_settings").withIcon(ThemeIcons.HEALTH_LINK));
        addButton(new ThemeButton(this, TUNING_BUTTON, guiLeft + 8, guiTop + 272, 234, 20,
                "cnpcgeckoaddon.boss.tuning_settings").withIcon(ThemeIcons.TUNING));
        // Beside Done, where the row was empty: whether this client draws the boss' zones in the
        // world while any of its screens is open.
        addButton(new ThemeButton(this, ZONES_BUTTON, guiLeft + 8, guiTop + 294, 170, 20, zonesLabel())
                .withIcon(ThemeIcons.POINTS));
        addDoneButton(guiLeft + 182, guiTop + 294, 60, 20);
        if (GeckoTheme.available()) {
            // Under the zones' switch, the other thing this client alone decides: how the
            // addon's screens look.
            addButton(new ThemeButton(this, THEME_BUTTON, guiLeft + 8, guiTop + 294 + THEME_ROW, 170, 20,
                    themeLabel()));
        }
    }


    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            data.setEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == COMBAT_ONLY_BUTTON) {
            data.setCombatOnly(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == STATIONARY_BUTTON) {
            data.setStationary(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == TRANSITION_ANIMATION_FIELD) {
            setSubGui(new GuiStringSelection(this, "cnpcgeckoaddon.string_picker.phase_transition_animation",
                    BossAnimationGuiUtil.getAnimations(npc), name -> {
                data.setPhaseTransitionAnimation(name);
                getTextField(TRANSITION_ANIMATION_FIELD).setValue(name);
            }));
        } else if (button.id == 20) {
            applyFields();
            setSubGui(new SubGuiBossPhaseList(npc, data));
        } else if (button.id == 22) {
            applyFields();
            setSubGui(new SubGuiBossTargeting(npc, data));
        } else if (button.id == 23) {
            applyFields();
            setSubGui(new SubGuiBossMinions(npc, data));
        } else if (button.id == 24) {
            applyFields();
            setSubGui(new SubGuiBossExplosion(npc, data));
        } else if (button.id == BOSS_BAR_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossBarStyle(data));
        } else if (button.id == RESET_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossReset(data));
        } else if (button.id == RAGE_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossRage(npc, data));
        } else if (button.id == CHEST_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossChest(data));
        } else if (button.id == TELEGRAPH_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossTelegraph(data));
        } else if (button.id == HEALTH_LINK_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossHealthLink(npc, data));
        } else if (button.id == TUNING_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossTuning(data.tuning()));
        } else if (button.id == ZONES_BUTTON) {
            BossZonePreview.setShown(!BossZonePreview.isShown());
            button.setDisplayText(zonesLabel());
        } else if (button.id == THEME_BUTTON) {
            // Drawn in the new look from the next frame on, and kept in the client's config.
            AddonClientConfig.setGuiTheme(!AddonClientConfig.guiTheme());
            button.setDisplayText(themeLabel());
        }
    }

    /** "Screen theme: addon", or CustomNPCs: what the theme's switch reads. */
    private static String themeLabel() {
        return Component.translatable("cnpcgeckoaddon.boss.gui_theme", Component.translatable(
                AddonClientConfig.guiTheme() ? "cnpcgeckoaddon.boss.gui_theme.addon"
                        : "cnpcgeckoaddon.boss.gui_theme.npc")).getString();
    }

    /** "Zones in the world: shown", or hidden: what the preview's switch reads. */
    private static String zonesLabel() {
        return Component.translatable("cnpcgeckoaddon.boss.zones_preview", Component.translatable(
                BossZonePreview.isShown() ? "cnpcgeckoaddon.boss.zones_preview.on"
                        : "cnpcgeckoaddon.boss.zones_preview.off")).getString();
    }

    @Override
    public TeleportPathData zoneBoss() {
        return data;
    }

    @Override
    public EntityNPCInterface zoneNpc() {
        return npc;
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        applyField(field);
    }

    @Override
    protected void applyFields() {
        applyField(getTextField(PHASE_COUNT_FIELD));
        applyField(getTextField(TRANSITION_ANIMATION_FIELD));
        applyField(getTextField(TRANSITION_LOCK_FIELD));
    }

    private void applyField(GuiTextFieldNop field) {
        if (field == null) return;
        if (field.id == PHASE_COUNT_FIELD) {
            data.setPhaseCount(field.getInteger());
        } else if (field.id == TRANSITION_LOCK_FIELD) {
            data.setPhaseTransitionLockTicks(field.getInteger());
        } else if (field.id == TRANSITION_ANIMATION_FIELD) {
            String value = field.getValue().trim();
            if (BossAnimationGuiUtil.isValid(npc, value)) {
                data.setPhaseTransitionAnimation(value);
            } else {
                field.setValue(data.getPhaseTransitionAnimation());
            }
        }
    }
}
