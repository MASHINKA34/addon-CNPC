package com.goodbird.cnpcgeckoaddon.utils;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.loading.object.BakedAnimations;

import java.util.List;
import java.util.Vector;

public class AnimationFileUtil {
    public static List<String> getAnimationList(String animFileName) {
        Vector<String> list = new Vector<>();
        // The animation file name comes from an editable text field, so it can be
        // anything the user typed. ResourceLocation.parse would throw on that.
        ResourceLocation location = parse(animFileName);
        if (location == null) {
            return list;
        }
        BakedAnimations file = GeckoLibCache.getBakedAnimations().get(location);
        if (file != null) {
            for (Animation anim : file.animations().values()) {
                list.add(anim.name());
            }
        }
        return list;
    }

    /** @return the baked animation, or null when the file or the animation is unknown */
    public static Animation getAnimation(String animFileName, String animationName) {
        ResourceLocation location = parse(animFileName);
        if (location == null || animationName == null || animationName.isEmpty()) {
            return null;
        }
        BakedAnimations file = GeckoLibCache.getBakedAnimations().get(location);
        return file == null ? null : file.animations().get(animationName.trim());
    }

    public static List<String> getAnimationFileList() {
        Vector<String> list = new Vector<>();
        for (ResourceLocation resLoc : GeckoLibCache.getBakedAnimations().keySet()) {
            list.add(resLoc.toString());
        }
        return list;
    }

    /** Lenient {@link ResourceLocation} parsing that returns null instead of throwing. */
    public static ResourceLocation parse(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(value.trim());
    }

    /** Parses {@code value}, falling back to {@code fallback} when it is not a valid location. */
    public static ResourceLocation parseOr(String value, ResourceLocation fallback) {
        ResourceLocation parsed = parse(value);
        return parsed == null ? fallback : parsed;
    }
}
