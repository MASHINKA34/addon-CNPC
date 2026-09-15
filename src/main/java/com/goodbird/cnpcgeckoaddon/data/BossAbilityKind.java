package com.goodbird.cnpcgeckoaddon.data;

import java.util.Arrays;

/**
 * The one list of boss abilities every per-ability mask indexes into.
 *
 * <p>Warnings, npc immunities and the standing-cast choice all switch abilities on and off
 * a bit at a time, and a second private numbering would let the same ability mean different
 * bits - and carry two different names - in two of the masks.</p>
 *
 * <p>The order is fixed for good: saved bosses already hold these positions in their warning
 * mask, so anything new is appended rather than slotted in.</p>
 */
public final class BossAbilityKind {
    public static final int AREA = 0;
    public static final int RANGED = 1;
    public static final int MELEE = 2;
    public static final int FLUID = 3;
    public static final int HOOK = 4;
    public static final int CAPTURE = 5;
    public static final int SUMMON = 6;
    public static final int LEAP = 7;
    public static final int LINE = 8;
    /** Not something the boss aims: the explosion it leaves behind when it dies. */
    public static final int BLAST = 9;
    public static final int GEYSER = 10;
    public static final int BOULDER = 11;
    public static final int BOULDER_RAIN = 12;
    /** A leash the victim has to break by running: off the boss, off a spot, or off each other. */
    public static final int TETHER = 13;
    /** A field around the boss that drags everyone in, shoves them out or throws them up. */
    public static final int GRAVITY = 14;
    /** A mark on a victim that goes off on its own: share the hit, or carry it clear. */
    public static final int MARK = 15;
    /** A hit on the whole arena that only spares whoever got out of sight or into a shelter. */
    public static final int COVER = 16;
    /**
     * Not something the boss aims: the arena itself turning dangerous for a phase, as a
     * safe circle closing in or a box that starts to burn.
     */
    public static final int HAZARD = 17;
    /**
     * A chase: the boss picks one victim and goes after nobody else until it catches them
     * or the time runs out.
     */
    public static final int HUNT = 18;
    /**
     * A beam swept round the boss: one or more lines that keep turning for a while after
     * the cast and burn whoever they catch up with.
     */
    public static final int BEAM = 19;
    /**
     * A victim locked inside a clone spawned on the spot they stood on: the rest of the
     * party has to come and break them out, or they pay for it when the time runs out.
     */
    public static final int COCOON = 20;
    /**
     * A charge down a line the boss committed to: whoever it meets first takes the hit, and a
     * wall it meets instead stuns it, sets off a slam round it or enrages it.
     */
    public static final int DASH = 21;
    /**
     * A fan of a hit laid out from the boss: toward whoever it picked, along its gaze, or over
     * the builder's points one after another, knocking away, throwing up or pulling in whoever
     * stands in the sector.
     */
    public static final int CONE = 22;
    /**
     * One of the builder's platforms set alight: its outline flashes for a fuse, and whoever is
     * still standing on it when the fuse runs out is hit, shoved off and thrown up.
     */
    public static final int PLATFORM = 23;
    /**
     * A storm on the arena floor that travels, bounces off walls and keeps whoever it runs into:
     * lifted, carried round its eye and spun, hit on a clock, then thrown clear.
     */
    public static final int HURRICANE = 24;
    /**
     * Copies of the boss itself, built from its own saved data: they look and fight like it,
     * cast only the abilities the phase picked for them, and end by swapping places with it,
     * being drawn back into it or going off around themselves.
     */
    public static final int SHADOW = 25;
    /**
     * Rings of the arena floor round the boss that hit one after another: from the circle
     * under its feet outward to the edge, or at random radii, each one warned for and then
     * landed on whoever still stands in it, thrown up and, if the phase says so, slammed down.
     */
    public static final int SEISMIC = 26;
    public static final int COUNT = 27;

    public static final String[] LABELS = {
            "cnpcgeckoaddon.boss.ability.area",
            "cnpcgeckoaddon.boss.ability.ranged",
            "cnpcgeckoaddon.boss.ability.melee",
            "cnpcgeckoaddon.boss.ability.fluid",
            "cnpcgeckoaddon.boss.ability.hook",
            "cnpcgeckoaddon.boss.ability.capture",
            "cnpcgeckoaddon.boss.ability.summon",
            "cnpcgeckoaddon.boss.ability.leap",
            "cnpcgeckoaddon.boss.ability.line",
            "cnpcgeckoaddon.boss.ability.blast",
            "cnpcgeckoaddon.boss.ability.geyser",
            "cnpcgeckoaddon.boss.ability.boulder",
            "cnpcgeckoaddon.boss.ability.boulder_rain",
            "cnpcgeckoaddon.boss.ability.tether",
            "cnpcgeckoaddon.boss.ability.gravity",
            "cnpcgeckoaddon.boss.ability.mark",
            "cnpcgeckoaddon.boss.ability.cover",
            "cnpcgeckoaddon.boss.ability.hazard",
            "cnpcgeckoaddon.boss.ability.hunt",
            "cnpcgeckoaddon.boss.ability.beam",
            "cnpcgeckoaddon.boss.ability.cocoon",
            "cnpcgeckoaddon.boss.ability.dash",
            "cnpcgeckoaddon.boss.ability.cone",
            "cnpcgeckoaddon.boss.ability.platform",
            "cnpcgeckoaddon.boss.ability.hurricane",
            "cnpcgeckoaddon.boss.ability.shadow",
            "cnpcgeckoaddon.boss.ability.seismic"
    };

    static {
        // Every mask indexes both by the same number, so an ability added to one and not the
        // other would only show up as an out-of-bounds read somewhere in a gui.
        if (LABELS.length != COUNT) {
            throw new IllegalStateException(
                    "BossAbilityKind.LABELS holds " + LABELS.length + " names for " + COUNT + " abilities");
        }
    }

    /**
     * The abilities an npc can be made immune to, in the order they are offered.
     *
     * <p>Everything that lands on somebody. The minion summon is deliberately absent: it
     * spawns helpers rather than touching a victim, so there is nothing for it to pass by.</p>
     */
    public static final int[] IMMUNITY_ABILITIES = {
            AREA, RANGED, MELEE, FLUID, HOOK, CAPTURE, LEAP, LINE, BLAST, GEYSER, BOULDER,
            BOULDER_RAIN, TETHER, GRAVITY, MARK, COVER, HAZARD, HUNT, BEAM, COCOON, DASH, CONE,
            PLATFORM, HURRICANE, SEISMIC
    };

    /**
     * The abilities whose effect outlives the cast, in the order they are offered: the ones a
     * boss told to see them through has something to wait for, whether or not a hold follows.
     * The platforms are one of them: their fuse, and the smoulder after it, burn on long after
     * the wind-up lands. So is the hurricane: its storms travel the arena for their whole
     * lifetime once the cast has let them go. And so are the shadow copies: they stand and
     * fight until their time runs out, they are taken back or they are killed. And the seismic
     * waves: a series of rings runs on the level tick for seconds after the cast that set it off.
     *
     * <p>The leap is absent because its flight already keeps the boss busy until it lands,
     * the dash because its run does the same until it stops, the cone strike because a series
     * over its points does the same until its last cone, and the hunt because its own silence
     * switch does the same job for the chase. Kept as the record of who leaves something
     * behind: the finish screen fills a hold in for everyone else, since marking them without
     * one would wait for nothing.</p>
     */
    public static final int[] LASTING_ABILITIES = {
            HOOK, CAPTURE, GEYSER, BOULDER_RAIN, TETHER, GRAVITY, MARK, BEAM, COCOON, PLATFORM,
            HURRICANE, SHADOW, SEISMIC
    };

    /** Every bit {@link #LASTING_ABILITIES} owns: the marked abilities whose wait is an effect rather than a hold. */
    public static final int LASTING_ALL = maskOf(LASTING_ABILITIES);

    /**
     * The abilities a phase can chain one after another: every one the rotation casts.
     *
     * <p>The death blast and the arena hazard are absent. Neither is cast, so neither ends in
     * a way that could hand on to anything, and neither can be started as a follow-up.</p>
     */
    public static final int[] COMBO_ABILITIES = {
            AREA, RANGED, MELEE, FLUID, HOOK, CAPTURE, SUMMON, LEAP, LINE, GEYSER, BOULDER,
            BOULDER_RAIN, TETHER, GRAVITY, MARK, COVER, HUNT, BEAM, COCOON, DASH, CONE, PLATFORM,
            HURRICANE, SHADOW, SEISMIC
    };

    /** Every bit {@link #COMBO_ABILITIES} owns: the kinds a chain slot may belong to and point at. */
    public static final int COMBO_ALL = maskOf(COMBO_ABILITIES);

    /**
     * The abilities a phase can be told to see through before it starts anything else: every
     * one the rotation casts, which is the chain list again.
     *
     * <p>Wider than {@link #LASTING_ABILITIES} on purpose. An instant ability leaves no effect
     * to wait for, but the server never knows how long its swing's animation runs - that lives
     * in the client's assets - so the builder says how many ticks the boss is still busy with
     * it, and that hold is the whole wait. The death blast and the arena hazard are absent for
     * the chains' reason: neither is cast, so neither has an end to see through.</p>
     */
    public static final int[] FINISH_ABILITIES = Arrays.copyOf(COMBO_ABILITIES, COMBO_ABILITIES.length);

    /** Every bit {@link #FINISH_ABILITIES} owns; any other bit in a finish mask is never read. */
    public static final int FINISH_ALL = maskOf(FINISH_ABILITIES);

    private static int maskOf(int[] abilities) {
        int mask = 0;
        for (int ability : abilities) {
            mask |= 1 << ability;
        }
        return mask;
    }

    private BossAbilityKind() {
    }
}
