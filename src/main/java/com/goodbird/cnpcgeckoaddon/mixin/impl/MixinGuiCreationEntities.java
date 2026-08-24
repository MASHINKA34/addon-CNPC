package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.client.gui.GuiModelAnimation;
import com.goodbird.cnpcgeckoaddon.client.gui.GuiModelSelection;
import com.goodbird.cnpcgeckoaddon.client.gui.SubGuiModelExtras;
import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.client.gui.model.GuiCreationEntities;
import noppes.npcs.client.gui.model.GuiCreationScreenInterface;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiCreationEntities.class)
public class MixinGuiCreationEntities extends GuiCreationScreenInterface {

    public MixinGuiCreationEntities() {
        super(null);
    }

    @Inject(method = "init",at = @At("TAIL"))
    public void init(CallbackInfo ci){
        if(npc instanceof EntityCustomNpc && ((EntityCustomNpc)npc).modelData.getEntity(npc) instanceof EntityCustomModel) {
            EntityCustomModel customModel = (EntityCustomModel) ((EntityCustomNpc)npc).modelData.getEntity(npc);
            addLabel(new GuiLabel(212,"Model:", this.guiLeft + 124, this.guiTop + 26,0xffffff));
            this.addButton(new GuiButtonNop(this, 202, this.guiLeft + 160, this.guiTop + 20,
                    150, 20, customModel.modelResLoc.toString(), (b) -> setSubGui(
                    new GuiModelSelection((EntityCustomNpc) npc,
                            name -> getButton(202).setDisplayText(name)))));
            addLabel(new GuiLabel(213,"Model Animation:", this.guiLeft + 124, this.guiTop + 46,0xffffff));
            this.addButton(new GuiButtonNop(this,212,this.guiLeft + 210, this.guiTop + 40, 100, 20, "selectServer.edit",(b)->{
                setSubGui(new GuiModelAnimation());
            }));
            this.addButton(new GuiButtonNop(this, 213, this.guiLeft + 124, this.guiTop + 60, 187, 20, "Extras", (b)->{
                setSubGui(new SubGuiModelExtras(this.npc));
            }));
        }
    }

    @Override
    public void drawNpc(GuiGraphics graphics, LivingEntity entity, int x, int y, float zoomed, int rotation) {
        if(wrapper.subgui==null) {
            super.drawNpc(graphics, entity, x, y, zoomed, rotation);
        }
    }
}
