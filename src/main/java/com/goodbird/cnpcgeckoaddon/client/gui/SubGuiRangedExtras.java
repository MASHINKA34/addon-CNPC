package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import com.goodbird.cnpcgeckoaddon.utils.ProjectileEntityUtil;
import net.minecraft.client.Minecraft;
import noppes.npcs.entity.data.DataRanged;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

import java.util.ArrayList;
import java.util.List;

public class SubGuiRangedExtras extends GuiBasic implements ITextfieldListener {
    private final RangedExtraData data;

    public SubGuiRangedExtras(DataRanged ranged) {
        this.data = ((IRangedData) ranged).getRangedExtraData();
        setBackground("menubg.png");
        this.imageWidth = 256;
        this.imageHeight = 216;
        this.closeOnEsc = true;
    }

    @Override
    public void init() {
        super.init();
        int y = guiTop + 20;

        addLabel(new GuiLabel(1, "Projectile Entity", guiLeft + 5, y + 6, 0xffffff));
        addTextField(new GuiTextFieldNop(1, this, guiLeft + 100, y, 95, 20, data.getProjectileEntity()));
        addButton(new GuiButtonNop(this, 1, guiLeft + 199, y, 50, 20, "mco.template.button.select", (b) ->
                setSubGui(new GuiStringSelection(this, "Selecting projectile entity:", getEntityList(), (name) -> {
                    data.setProjectileEntity(name);
                    getTextField(1).setValue(name);
                }))));
        y += 24;

        addLabel(new GuiLabel(2, "Keep Distance", guiLeft + 5, y + 6, 0xffffff));
        GuiTextFieldNop keepDistance = new GuiTextFieldNop(2, this, guiLeft + 100, y, 50, 20, "" + data.getKeepDistance());
        keepDistance.setNumbersOnly();
        keepDistance.setMinMaxDefault(0, 64, 0);
        addTextField(keepDistance);
        y += 24;

        addButton(new GuiButtonNop(this, 2, guiLeft + 100, y, 100, 20, "Reset projectile", (b) -> {
            data.setProjectileEntity("");
            getTextField(1).setValue("");
        }));

        addButton(new GuiButtonNop(this, 66, guiLeft + 190, guiTop + 190, 60, 20, "gui.done", (b) -> close()));
    }

    public List<String> getEntityList() {
        return ProjectileEntityUtil.getSelectableIds(Minecraft.getInstance().level);
    }

    public boolean isValidEntity(String name) {
        return ProjectileEntityUtil.isSelectable(name, Minecraft.getInstance().level);
    }

    @Override
    public void unFocused(GuiTextFieldNop textfield) {
        if (textfield.id == 1) {
            String value = textfield.getValue().trim();
            if (value.isEmpty() || isValidEntity(value)) {
                data.setProjectileEntity(value);
                textfield.setValue(value);
            } else {
                textfield.setValue(data.getProjectileEntity());
            }
        }
        if (textfield.id == 2) {
            data.setKeepDistance(textfield.getInteger());
        }
    }
}
