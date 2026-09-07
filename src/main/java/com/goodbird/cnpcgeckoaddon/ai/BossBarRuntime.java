package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossBarStyles;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossBarStyle;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncBossTimer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;

/**
 * The boss bar this boss draws and the countdown printed under it.
 *
 * <p>Owned by {@link TeleportPathController}. Two bars exist and only ever one of them is
 * up: the styled bar this class owns, and the npc's own vanilla one, which is put back the
 * moment the style is switched off. The countdown always belongs to whichever of the two is
 * showing, or a boss left on style {@code none} would count down against a bar id nobody is
 * drawing.</p>
 */
final class BossBarRuntime {

    /** The client counts down on its own, so the server only has to correct it now and then. */
    private static final int TIMER_SYNC_INTERVAL_TICKS = 5;

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;
    private final ServerBossEvent bossEvent;
    /** Everyone who has earned a look at the bar, whether or not they can see it right now. */
    private final Set<UUID> viewers = new HashSet<>();
    /**
     * Scratch space for {@link #update}, reused rather than allocated per tick.
     *
     * <p>Only ever live inside that one call, which is reached from the boss' tick and from
     * nowhere else, so there is no second walk to trip over a half-filled buffer.</p>
     */
    private final Set<ServerPlayer> eligible = new HashSet<>();
    private final List<ServerPlayer> dropped = new ArrayList<>();

    private String activeStyle = BossBarStyles.NONE;
    private int activeScalePercent = TeleportPathData.DEFAULT_BOSS_BAR_SCALE_PERCENT;
    private byte lastTimerState = PacketSyncBossTimer.STATE_NONE;
    private long nextTimerSyncAt;

    BossBarRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
        this.bossEvent = new ServerBossEvent(npc.getDisplayName(), BossEvent.BossBarColor.WHITE,
                BossEvent.BossBarOverlay.PROGRESS);
    }

    void update(ServerLevel level, TeleportPathData data) {
        String style = BossBarStyles.normalize(data.getBossBarStyle());
        int scalePercent = data.getBossBarScalePercent();
        if (!BossBarStyles.isEnabled(style)) {
            hide();
            restoreNative();
            return;
        }

        npc.bossInfo.setVisible(false);
        if (!boss.hasCombatTarget()) {
            hide();
            return;
        }

        bossEvent.setName(npc.getDisplayName());
        float maximum = npc.getMaxHealth();
        bossEvent.setProgress(maximum <= 0.0F ? 0.0F : Mth.clamp(npc.getHealth() / maximum, 0.0F, 1.0F));
        bossEvent.setVisible(true);

        if (!style.equals(activeStyle) || scalePercent != activeScalePercent) {
            activeStyle = style;
            activeScalePercent = scalePercent;
            for (ServerPlayer player : bossEvent.getPlayers()) {
                NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), style, scalePercent));
            }
        }

        double radiusSquared = data.getTargetSearchRadius() * (double) data.getTargetSearchRadius();
        LivingEntity target = npc.getTarget();
        if (target instanceof ServerPlayer player) {
            viewers.add(player.getUUID());
        }
        eligible.clear();
        // Walked through the iterator rather than over a copy: this runs every tick of every
        // fight, and the copy was a fresh set per boss per tick for the sake of one removal.
        for (Iterator<UUID> it = viewers.iterator(); it.hasNext(); ) {
            Player player = level.getPlayerByUUID(it.next());
            if (player instanceof ServerPlayer serverPlayer && isViewer(serverPlayer)
                    && (serverPlayer == target || npc.distanceToSqr(serverPlayer) <= radiusSquared)) {
                eligible.add(serverPlayer);
            } else {
                it.remove();
            }
        }

        // This copy stays: removePlayer writes to the very list being walked.
        dropped.clear();
        dropped.addAll(bossEvent.getPlayers());
        for (ServerPlayer player : dropped) {
            if (!eligible.contains(player)) {
                bossEvent.removePlayer(player);
                NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), BossBarStyles.NONE,
                        TeleportPathData.DEFAULT_BOSS_BAR_SCALE_PERCENT));
            }
        }
        for (ServerPlayer player : eligible) {
            if (!bossEvent.getPlayers().contains(player)) {
                NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), style, scalePercent));
                // Whoever just joined the bar has no countdown yet, and the throttled sync
                // below would leave them staring at an empty timer for up to five ticks.
                NetworkWrapper.send(player, buildTimerPacket(data));
                bossEvent.addPlayer(player);
            }
        }
    }

    private boolean isViewer(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && !player.isRemoved()
                && (player == npc.getTarget() || npc.canAttack(player) && !npc.isAlliedTo(player));
    }

    /** Signs a player up for the styled bar; whether they can see it yet is settled per tick. */
    void addViewer(ServerPlayer player) {
        viewers.add(player.getUUID());
    }

    void removeViewer(ServerPlayer player) {
        viewers.remove(player.getUUID());
        if (!bossEvent.getPlayers().contains(player)) {
            return;
        }
        bossEvent.removePlayer(player);
        NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), BossBarStyles.NONE,
                TeleportPathData.DEFAULT_BOSS_BAR_SCALE_PERCENT));
    }

    /**
     * Keeps the countdown on everyone watching the boss bar in step with the server.
     *
     * <p>A state change goes out at once; a running countdown only needs the occasional
     * correction, because the client subtracts the ticks itself in between. The two states
     * with nothing left to count are sent once and then left alone.</p>
     */
    void syncTimer(long gameTime, TeleportPathData data) {
        ServerBossEvent bar = timerBossEvent();
        if (bar.getPlayers().isEmpty()) {
            return;
        }
        byte state = timerState(data);
        boolean counting = state == PacketSyncBossTimer.STATE_COUNTDOWN
                || state == PacketSyncBossTimer.STATE_INVULNERABLE;
        if (state == lastTimerState && (!counting || gameTime < nextTimerSyncAt)) {
            return;
        }
        lastTimerState = state;
        nextTimerSyncAt = gameTime + TIMER_SYNC_INTERVAL_TICKS;
        PacketSyncBossTimer packet = buildTimerPacket(data);
        for (ServerPlayer player : bar.getPlayers()) {
            NetworkWrapper.send(player, packet);
        }
    }

    /**
     * The bar the countdown belongs on: the styled one while it is up, the NPC's own bar
     * otherwise. Without this a boss left on style {@code none} would count down against a
     * bar id nobody is drawing.
     */
    ServerBossEvent timerBossEvent() {
        return BossBarStyles.isEnabled(activeStyle) ? bossEvent : npc.bossInfo;
    }

    private byte timerState(TeleportPathData data) {
        if (boss.isInvulnerable()) {
            return PacketSyncBossTimer.STATE_INVULNERABLE;
        }
        if (boss.isRageActive()) {
            return PacketSyncBossTimer.STATE_RAGE;
        }
        if (!data.isRageEnabled() || boss.encounterStartedAt() == NOT_SCHEDULED) {
            return PacketSyncBossTimer.STATE_NONE;
        }
        return PacketSyncBossTimer.STATE_COUNTDOWN;
    }

    /** The immune window borrows the same countdown, so the HUD only has one thing to draw. */
    private PacketSyncBossTimer buildTimerPacket(TeleportPathData data) {
        byte state = timerState(data);
        int remaining = 0;
        int total = 0;
        if (state == PacketSyncBossTimer.STATE_INVULNERABLE) {
            remaining = boss.invulnerableTicksLeft();
            total = data.getPhase(boss.invulnerablePhaseIndex()).getInvulnerableDurationTicks();
        } else if (state == PacketSyncBossTimer.STATE_COUNTDOWN) {
            remaining = boss.rageTicksLeft();
            total = data.getRageDelayTicks();
        } else if (state == PacketSyncBossTimer.STATE_RAGE) {
            total = data.getRageDelayTicks();
        }
        return new PacketSyncBossTimer(timerBossEvent().getId(), remaining, total, state);
    }

    /** Whether the styled bar is the one being drawn right now. */
    boolean isStyled() {
        return BossBarStyles.isEnabled(activeStyle);
    }

    void hide() {
        if (BossBarStyles.isEnabled(activeStyle)) {
            // Only when the styled bar really was up. On style `none` this runs every tick,
            // and resetting the throttle there would put a countdown packet on every one.
            lastTimerState = PacketSyncBossTimer.STATE_NONE;
            nextTimerSyncAt = 0L;
        }
        for (ServerPlayer player : List.copyOf(bossEvent.getPlayers())) {
            bossEvent.removePlayer(player);
            NetworkWrapper.send(player, new PacketSyncBossBarStyle(bossEvent.getId(), BossBarStyles.NONE,
                    TeleportPathData.DEFAULT_BOSS_BAR_SCALE_PERCENT));
        }
        activeStyle = BossBarStyles.NONE;
        activeScalePercent = TeleportPathData.DEFAULT_BOSS_BAR_SCALE_PERCENT;
        viewers.clear();
    }

    /** Takes the styled bar down and the npc's own with it, for every ending of a fight. */
    void stop() {
        hide();
        npc.bossInfo.setVisible(false);
    }

    private void restoreNative() {
        int mode = npc.display.getBossbar();
        npc.bossInfo.setVisible(npc.isAlive() && !npc.isRemoved()
                && (mode == 1 || mode == 2 && boss.hasCombatTarget()));
    }
}
