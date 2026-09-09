package com.goodbird.cnpcgeckoaddon.data;

import java.util.List;

/**
 * What the sweeping beam is drawn out of.
 *
 * <p>{@link #KIND} is the default and carries no artwork of its own: it draws the beam in the
 * ability's dust the way it has always been drawn, so bosses saved before the looks existed
 * keep the beam they had. Every other look swaps the particles and nothing else - the beams
 * turn, reach and burn exactly the same, and the wind-up's warning keeps the ability's colour
 * whatever the look, so it reads the same under every boss.</p>
 *
 * <p>Only the id and the label live here: this class is read by the GUI as well, and the
 * particle choices belong to the server-side scheduler that emits them.</p>
 */
public final class BeamLooks {
    public static final String KIND = "kind";
    public static final String FIRE = "fire";
    public static final String SOUL = "soul";
    public static final String VOID = "void";
    public static final String LIGHTNING = "lightning";
    public static final String FROST = "frost";
    public static final String SCULK = "sculk";
    public static final String TOXIC = "toxic";

    private static final List<Look> LOOKS = List.of(
            new Look(KIND, "cnpcgeckoaddon.boss.beam_look.kind"),
            new Look(FIRE, "cnpcgeckoaddon.boss.beam_look.fire"),
            new Look(SOUL, "cnpcgeckoaddon.boss.beam_look.soul"),
            new Look(VOID, "cnpcgeckoaddon.boss.beam_look.void"),
            new Look(LIGHTNING, "cnpcgeckoaddon.boss.beam_look.lightning"),
            new Look(FROST, "cnpcgeckoaddon.boss.beam_look.frost"),
            new Look(SCULK, "cnpcgeckoaddon.boss.beam_look.sculk"),
            new Look(TOXIC, "cnpcgeckoaddon.boss.beam_look.toxic")
    );

    private BeamLooks() {
    }

    public static List<Look> values() {
        return LOOKS;
    }

    public static Look get(String id) {
        if (id != null) {
            for (Look look : LOOKS) {
                if (look.id().equals(id)) {
                    return look;
                }
            }
        }
        return LOOKS.getFirst();
    }

    public static String normalize(String id) {
        return get(id).id();
    }

    /** One beam's artwork. */
    public record Look(String id, String translationKey) {
    }
}
