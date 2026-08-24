package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.ContainerBlockUtil;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Leaves a chest of loot behind when the boss dies. */
public final class SubGuiBossChest extends GuiBasic implements ITextfieldListener {
    private static final int ENABLED_BUTTON = 1;
    private static final int BLOCK_FIELD = 2;
    private static final int DELAY_FIELD = 3;
    private static final int LIFETIME_FIELD = 4;
    private static final int NAME_FIELD = 5;
    private static final int NPC_DROPS_BUTTON = 6;
    private static final int LOOT_TABLE_FIELD = 7;
    private static final int LOOT_LIST_BUTTON = 8;

    private final TeleportPathData data;

    public SubGuiBossChest(TeleportPathData data) {
        this.data = data;
        setBackground("menubg.png");
        imageWidth = 256;
        imageHeight = 256;
        closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        addLabel(new GuiLabel(30, "cnpcgeckoaddon.boss.chest_title", guiLeft + 8, guiTop + 8, 0xFFFFFF));
        int y = guiTop + 26;

        addLabel(new GuiLabel(ENABLED_BUTTON, "cnpcgeckoaddon.boss.chest_enabled", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, ENABLED_BUTTON, guiLeft + 155, y, 87, 20, data.isChestEnabled()));
        y += 24;

        addLabel(new GuiLabel(BLOCK_FIELD, "cnpcgeckoaddon.boss.chest_block", guiLeft + 8, y + 6));
        addTextField(new GuiTextFieldNop(BLOCK_FIELD, this, guiLeft + 98, y, 96, 20, data.getChestBlock()));
        addButton(new GuiButtonNop(this, BLOCK_FIELD, guiLeft + 198, y, 44, 20, "mco.template.button.select"));
        y += 24;

        addNumberField(DELAY_FIELD, "cnpcgeckoaddon.boss.chest_delay", y, data.getChestDelayTicks(),
                TeleportPathData.MIN_CHEST_DELAY_TICKS, TeleportPathData.MAX_CHEST_DELAY_TICKS, 0);
        y += 24;
        addNumberField(LIFETIME_FIELD, "cnpcgeckoaddon.boss.chest_lifetime", y, data.getChestLifetimeTicks(),
                TeleportPathData.MIN_CHEST_LIFETIME_TICKS, TeleportPathData.MAX_CHEST_LIFETIME_TICKS, 6000);
        y += 24;

        addLabel(new GuiLabel(NAME_FIELD, "cnpcgeckoaddon.boss.chest_name", guiLeft + 8, y + 6));
        addTextField(new GuiTextFieldNop(NAME_FIELD, this, guiLeft + 108, y, 134, 20, data.getChestName()));
        y += 24;

        addLabel(new GuiLabel(NPC_DROPS_BUTTON, "cnpcgeckoaddon.boss.chest_npc_drops", guiLeft + 8, y + 6));
        addButton(new GuiButtonYesNo(this, NPC_DROPS_BUTTON, guiLeft + 155, y, 87, 20, data.isChestUseNpcDrops()));
        y += 24;

        addLabel(new GuiLabel(LOOT_TABLE_FIELD, "cnpcgeckoaddon.boss.chest_loot_table", guiLeft + 8, y + 6));
        addTextField(new GuiTextFieldNop(LOOT_TABLE_FIELD, this, guiLeft + 108, y, 134, 20, data.getChestLootTable()));

        addLabel(new GuiLabel(31, "cnpcgeckoaddon.teleport.ticks_hint", guiLeft + 8, guiTop + 198, 0xA0A0A0));
        addLabel(new GuiLabel(32, "cnpcgeckoaddon.boss.chest_hint", guiLeft + 8, guiTop + 210, 0xA0A0A0));

        addButton(new GuiButtonNop(this, LOOT_LIST_BUTTON, guiLeft + 8, guiTop + 230, 168, 20,
                "cnpcgeckoaddon.boss.chest_loot_list"));
        addButton(new GuiButtonNop(this, 66, guiLeft + 182, guiTop + 230, 60, 20,
                "gui.done", button -> close()));
    }

    private void addNumberField(int id, String label, int y, int value, int min, int max, int fallback) {
        addLabel(new GuiLabel(id, label, guiLeft + 8, y + 6));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, guiLeft + 172, y, 70, 20, Integer.toString(value));
        field.setNumbersOnly();
        field.setMinMaxDefault(min, max, fallback);
        addTextField(field);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == ENABLED_BUTTON) {
            data.setChestEnabled(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == NPC_DROPS_BUTTON) {
            data.setChestUseNpcDrops(((GuiButtonYesNo) button).getBoolean());
        } else if (button.id == BLOCK_FIELD) {
            setSubGui(new GuiStringSelection(this, "Selecting chest block:",
                    ContainerBlockUtil.getSelectableIds(), name -> {
                data.setChestBlock(name);
                getTextField(BLOCK_FIELD).setValue(name);
            }));
        } else if (button.id == LOOT_LIST_BUTTON) {
            applyFields();
            setSubGui(new SubGuiBossChestLoot(data.getChestLoot()));
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop field) { applyFields(); }

    @Override
    public void close() {
        applyFields();
        super.close();
    }

    private void applyFields() {
        GuiTextFieldNop block = getTextField(BLOCK_FIELD);
        // Whatever is typed here is kept as typed, even when it names nothing: the boss
        // falls back to a plain chest and says so in the log, and a block from a mod that
        // is not loaded right now should survive being looked at in this menu.
        if (block != null) data.setChestBlock(block.getValue());
        GuiTextFieldNop delay = getTextField(DELAY_FIELD);
        if (delay != null) data.setChestDelayTicks(delay.getInteger());
        GuiTextFieldNop lifetime = getTextField(LIFETIME_FIELD);
        if (lifetime != null) data.setChestLifetimeTicks(lifetime.getInteger());
        GuiTextFieldNop name = getTextField(NAME_FIELD);
        if (name != null) data.setChestName(name.getValue());
        GuiTextFieldNop lootTable = getTextField(LOOT_TABLE_FIELD);
        if (lootTable != null) data.setChestLootTable(lootTable.getValue());
    }
}
