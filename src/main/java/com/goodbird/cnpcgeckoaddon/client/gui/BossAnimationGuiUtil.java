package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import com.goodbird.cnpcgeckoaddon.utils.AnimationFileUtil;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import software.bernie.geckolib.animation.Animation;

import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

final class BossAnimationGuiUtil {
    private BossAnimationGuiUtil() {
    }

    static List<String> getAnimations(EntityNPCInterface npc) {
        try {
            String file = ((IDataDisplay) npc.display).getCustomModelData().getAnimFile();
            return AnimationFileUtil.getAnimationList(file);
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    static boolean isValid(EntityNPCInterface npc, String animation) {
        return animation == null || animation.trim().isEmpty() || getAnimations(npc).contains(animation.trim());
    }

    /** @return the animation length in ticks, or -1 when it cannot be determined */
    static int getLengthTicks(EntityNPCInterface npc, String animation) {
        try {
            String file = ((IDataDisplay) npc.display).getCustomModelData().getAnimFile();
            Animation baked = AnimationFileUtil.getAnimation(file, animation);
            if (baked == null) {
                return -1;
            }
            double length = baked.length();
            return length > 0.0D ? (int) Math.round(length) : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /**
     * Points an ability's action delay at the end of the animation that was just picked.
     *
     * <p>The delay decides how long after the animation starts the damage lands, so leaving
     * it at a default shorter than the animation makes the hit arrive mid-swing. Only the
     * client knows how long an animation actually is, so this is done here, at the moment
     * the animation is chosen, and the value stays editable afterwards.</p>
     */
    static void syncDelayToAnimation(GuiBasic gui, EntityNPCInterface npc, String animation,
                                     int delayFieldId, IntConsumer setter) {
        int ticks = getLengthTicks(npc, animation);
        if (ticks <= 0) {
            return;
        }
        ticks = Mth.clamp(ticks, 0, 1200);
        setter.accept(ticks);
        GuiTextFieldNop field = gui.getTextField(delayFieldId);
        if (field != null) {
            field.setValue(Integer.toString(ticks));
        }
    }

    /** Builds a "&lt;something&gt; N" heading for a phase screen. */
    static String phaseTitle(String prefixKey, int phaseIndex) {
        return I18n.get(prefixKey) + " " + (phaseIndex + 1);
    }
}
