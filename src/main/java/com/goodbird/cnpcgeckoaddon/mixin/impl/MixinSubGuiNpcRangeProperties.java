package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.client.gui.SubGuiRangedExtras;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.client.gui.SubGuiNpcRangeProperties;
import noppes.npcs.entity.data.DataRanged;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubGuiNpcRangeProperties.class)
public abstract class MixinSubGuiNpcRangeProperties extends GuiBasic {

    @Shadow(remap = false)
    private DataRanged ranged;

    @Inject(method = "init", at = @At("TAIL"))
    public void cnpcgeckoaddon$addExtrasButton(CallbackInfo ci) {
        if (!(ranged instanceof IRangedData)) {
            return;
        }
        addButton(new GuiButtonNop(this, 940, guiLeft + 5, guiTop + 190, 100, 20, "Projectile Extras", (b) ->
                setSubGui(new SubGuiRangedExtras(ranged))));
    }

    @Inject(method = "subGuiClosed", at = @At("HEAD"), cancellable = true, remap = false)
    public void cnpcgeckoaddon$skipExtrasSubGui(Screen subgui, CallbackInfo ci) {
        if (subgui instanceof SubGuiRangedExtras) {
            ci.cancel();
        }
    }
}
