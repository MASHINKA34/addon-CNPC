package com.goodbird.cnpcgeckoaddon.mixin.compat;

import com.hollingsworth.arsnouveau.api.sound.ConfiguredSpellSound;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.hollingsworth.arsnouveau.common.entity.EntityLingeringSpell", remap = false)
public class MixinEntityLingeringSpell {

    @Redirect(method = "castSpells", at = @At(value = "INVOKE", target = "Lcom/hollingsworth/arsnouveau/api/sound/ConfiguredSpellSound;playSound(Lnet/minecraft/world/level/Level;DDD)V"), remap = false)
    private void cnpcgeckoaddon$playSoundIfPresent(ConfiguredSpellSound sound, Level level, double x, double y, double z) {
        if (sound != null) {
            sound.playSound(level, x, y, z);
        }
    }
}
