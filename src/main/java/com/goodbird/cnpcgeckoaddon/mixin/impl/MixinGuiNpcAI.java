package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.client.gui.SubGuiSoundReaction;
import com.goodbird.cnpcgeckoaddon.client.gui.SubGuiTeleportPath;
import noppes.npcs.client.gui.mainmenu.GuiNpcAI;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiNpcAI.class)
public abstract class MixinGuiNpcAI extends GuiNPCInterface2 {

    @Shadow(remap = false)
    private DataAI ai;

    protected MixinGuiNpcAI(EntityNPCInterface npc) {
        super(npc);
    }

    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void cnpcgeckoaddon$addSoundReactionButton(CallbackInfo ci) {
        addButton(new GuiButtonNop(this, 941, guiLeft + 150, guiTop + 135, 140, 20,
                "cnpcgeckoaddon.sound.open", button -> setSubGui(new SubGuiSoundReaction(ai))));
        addButton(new GuiButtonNop(this, 942, guiLeft + 150, guiTop + 160, 140, 20,
                "cnpcgeckoaddon.teleport.open", button -> setSubGui(new SubGuiTeleportPath(ai, npc))));
    }
}
