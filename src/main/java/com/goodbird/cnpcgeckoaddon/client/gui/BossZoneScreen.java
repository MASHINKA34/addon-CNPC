package com.goodbird.cnpcgeckoaddon.client.gui;

import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * A screen of a boss' settings as the world preview of its zones reads it.
 *
 * <p>The preview walks the stack of screens open on the npc's page and asks each of these what
 * it knows: the boss menu says which boss the stack belongs to, a phase's menu narrows the
 * preview to that phase, and an editor names the zone or spot it edits, which is then drawn
 * brighter than the rest. Every answer is optional, so a screen says only what it knows.</p>
 */
public interface BossZoneScreen {

    /** The boss whose settings this screen and everything opened from it edit, or null. */
    default TeleportPathData zoneBoss() {
        return null;
    }

    /** The npc that boss is, whose block the offsets are measured from; null alongside {@link #zoneBoss}. */
    default EntityNPCInterface zoneNpc() {
        return null;
    }

    /** The phase whose zones alone are shown while this screen is open, or -1 for no preference. */
    default int zonePhase() {
        return -1;
    }

    /**
     * The zone or spot this screen edits, drawn brighter and thicker than the rest: the settings
     * object itself - a platform, a summon point, the hazard - or a preview token for the ones the
     * boss keeps in no object of their own. Null for a screen that edits none.
     */
    default Object zoneFocus() {
        return null;
    }
}
