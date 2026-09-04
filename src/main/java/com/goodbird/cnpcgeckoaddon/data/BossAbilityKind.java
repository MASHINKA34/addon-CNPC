package com.goodbird.cnpcgeckoaddon.data;

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
    public static final int COUNT = 20;

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
            "cnpcgeckoaddon.boss.ability.beam"
    };

    /**
     * The abilities an npc can be made immune to, in the order they are offered.
     *
     * <p>Everything that lands on somebody. The minion summon is deliberately absent: it
     * spawns helpers rather than touching a victim, so there is nothing for it to pass by.</p>
     */
    public static final int[] IMMUNITY_ABILITIES = {
            AREA, RANGED, MELEE, FLUID, HOOK, CAPTURE, LEAP, LINE, BLAST, GEYSER, BOULDER,
            BOULDER_RAIN, TETHER, GRAVITY, MARK, COVER, HAZARD, HUNT, BEAM
    };

    private BossAbilityKind() {
    }
}
