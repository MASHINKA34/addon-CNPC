package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.client.gui.SubGuiNpcRanged;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
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

    /**
     * The addon's own ranged screen, opened off the CustomNPCs one rather than in place of it:
     * both edit the same {@code DataRanged}, and this one carries the addon's settings besides.
     */
    @Inject(method = "init", at = @At("TAIL"))
    public void cnpcgeckoaddon$addExtrasButton(CallbackInfo ci) {
        if (!(ranged instanceof IRangedData)) {
            return;
        }
        try {
            addButton(new GuiButtonNop(this, 940, guiLeft + 5, guiTop + 190, 100, 20,
                    "cnpcgeckoaddon.npc_ranged.open", (b) -> setSubGui(new SubGuiNpcRanged(ranged))));
        } catch (Throwable error) {
            CrashGuard.caught("mixin.gui.range_properties", error);
        }
    }

    /** The CustomNPCs screen reads every closing sub-screen as its sound picker; ours is not one. */
    @Inject(method = "subGuiClosed", at = @At("HEAD"), cancellable = true, remap = false)
    public void cnpcgeckoaddon$skipExtrasSubGui(Screen subgui, CallbackInfo ci) {
        if (subgui instanceof SubGuiNpcRanged) {
            ci.cancel();
        }
    }
}
