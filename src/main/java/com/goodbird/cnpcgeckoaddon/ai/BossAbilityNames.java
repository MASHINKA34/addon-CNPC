package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * What the fight calls an ability: the name its profile gives it in a phase, or the kind's own.
 *
 * <p>One place for every line that names an ability to a player - the warning in the action bar,
 * the countdowns, the zones shown in the world, the diagnostic status - so a renamed ability is
 * renamed everywhere at once. The screens that lay the phase out keep the kind's own name: that is
 * the builder's landmark, not the players'.</p>
 */
public final class BossAbilityNames {

    private BossAbilityNames() {
    }

    /**
     * The name, as a fresh component the caller may style: the profile's own as typed - it is the
     * builder's text, in whatever language they wrote it - or the kind's, translated for whoever
     * reads it. A kind outside the list has no name to translate and is called by its number.
     *
     * @param phase the phase being fought, or null for none, which is the kind's own name
     */
    public static MutableComponent name(BossPhaseData phase, int kind) {
        if (kind < 0 || kind >= BossAbilityKind.COUNT) {
            return Component.literal(Integer.toString(kind));
        }
        String custom = phase == null ? "" : phase.profile(kind).getDisplayName();
        return custom.isEmpty() ? Component.translatable(BossAbilityKind.LABELS[kind]) : Component.literal(custom);
    }
}
