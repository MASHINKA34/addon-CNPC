package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import noppes.npcs.entity.EntityNPCInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.NOT_SCHEDULED;
import static com.goodbird.cnpcgeckoaddon.ai.TeleportPathController.formatHealth;

/**
 * The boss' health scaled to how many players turned up.
 *
 * <p>Owned by {@link TeleportPathController}. One numeric ADD_VALUE modifier is hung on the
 * entity and never anything else: the attribute base value stays whatever the builder set,
 * so a boss that loses this modifier - a reload, an unload, the setting switched off - is
 * back at its configured health rather than at whatever the last party left it on.</p>
 */
final class BossHealthScalingRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(CNPCGeckoAddon.MODID);

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    private int scaledPlayerCount = 1;
    private int lockedPlayerCount;
    private long nextCheckAt = NOT_SCHEDULED;
    private int lastUpdateMode = -1;
    private int lastPlayerCap = -1;
    private int lastRecheckTicks = -1;
    private double baseMaxHealth;
    private boolean applied;
    private boolean unavailable;
    private long lastConfiguration = Long.MIN_VALUE;

    BossHealthScalingRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    /** Takes the party snapshot the fight opens with, on the tick somebody pulls the boss. */
    void beginEncounter(ServerLevel level, long gameTime, TeleportPathData data) {
        lockedPlayerCount = countEligiblePlayers(level, data, false);
        scaledPlayerCount = cappedPlayerCount(lockedPlayerCount, data);
        lastUpdateMode = data.getHealthScalingUpdateMode();
        lastPlayerCap = data.getHealthScalingPlayerCap();
        lastRecheckTicks = data.getHealthScalingRecheckTicks();
        nextCheckAt = data.getHealthScalingUpdateMode() == TeleportPathData.HEALTH_SCALING_DYNAMIC
                ? gameTime + data.getHealthScalingRecheckTicks() : NOT_SCHEDULED;
    }

    /** Forgets the party snapshot as the encounter ends; the modifier itself goes separately. */
    void endEncounter() {
        scaledPlayerCount = 1;
        lockedPlayerCount = 0;
        nextCheckAt = NOT_SCHEDULED;
        lastUpdateMode = -1;
        lastPlayerCap = -1;
        lastRecheckTicks = -1;
    }

    /** A participant left: count them again from scratch on the next tick. */
    void recountSoon() {
        nextCheckAt = 0L;
    }

    void tickPlayerCount(ServerLevel level, long gameTime, TeleportPathData data) {
        if (!boss.isEncounterRunning()) {
            return;
        }
        if (!data.isHealthScalingEnabled()) {
            endEncounter();
            return;
        }

        int updateMode = data.getHealthScalingUpdateMode();
        boolean updateModeChanged = updateMode != lastUpdateMode;
        boolean countSettingsChanged = updateModeChanged
                || data.getHealthScalingPlayerCap() != lastPlayerCap
                || data.getHealthScalingRecheckTicks() != lastRecheckTicks;
        if (countSettingsChanged) {
            if (updateMode == TeleportPathData.HEALTH_SCALING_LOCK_AT_START) {
                if (updateModeChanged || lockedPlayerCount < 1) {
                    boss.registerInitialPartyCandidates(level, data);
                    lockedPlayerCount = countEligiblePlayers(level, data, true);
                }
                scaledPlayerCount = cappedPlayerCount(lockedPlayerCount, data);
                nextCheckAt = NOT_SCHEDULED;
            } else {
                scaledPlayerCount = cappedPlayerCount(countEligiblePlayers(level, data, true), data);
                nextCheckAt = gameTime + data.getHealthScalingRecheckTicks();
            }
            lastUpdateMode = updateMode;
            lastPlayerCap = data.getHealthScalingPlayerCap();
            lastRecheckTicks = data.getHealthScalingRecheckTicks();
            return;
        }

        if (updateMode == TeleportPathData.HEALTH_SCALING_LOCK_AT_START) {
            scaledPlayerCount = cappedPlayerCount(lockedPlayerCount, data);
            return;
        }
        if (nextCheckAt != NOT_SCHEDULED && gameTime < nextCheckAt) {
            return;
        }
        scaledPlayerCount = cappedPlayerCount(countEligiblePlayers(level, data, true), data);
        nextCheckAt = gameTime + data.getHealthScalingRecheckTicks();
    }

    private int countEligiblePlayers(ServerLevel level, TeleportPathData data, boolean dynamic) {
        Set<ServerPlayer> candidates = new HashSet<>();
        if (npc.getTarget() instanceof ServerPlayer target) {
            candidates.add(target);
        }
        for (UUID playerId : boss.encounterParticipants()) {
            Player player = level.getPlayerByUUID(playerId);
            if (player instanceof ServerPlayer serverPlayer) {
                candidates.add(serverPlayer);
            }
        }

        ServerPlayer currentTarget = npc.getTarget() instanceof ServerPlayer target ? target : null;
        double dynamicRadius = data.getTargetSearchRadius() * 1.5D;
        double dynamicRadiusSquared = dynamicRadius * dynamicRadius;
        AABB zone = dynamic && data.isAggroZoneEnabled() ? boss.aggroZoneBounds(level, data) : null;
        int count = 0;
        for (ServerPlayer player : candidates) {
            if (player.level() != level || !boss.isParticipant(player)) {
                continue;
            }
            if (dynamic && player != currentTarget
                    && npc.distanceToSqr(player) > dynamicRadiusSquared
                    && (zone == null || !zone.contains(player.position()))) {
                continue;
            }
            count++;
        }
        return Math.max(1, count);
    }

    private static int cappedPlayerCount(int count, TeleportPathData data) {
        return Mth.clamp(count, 1, data.getHealthScalingPlayerCap());
    }

    int scaledPlayerCount() {
        return scaledPlayerCount;
    }

    /** Read-only party-health snapshot for /cnpcgecko boss. */
    String status(TeleportPathData data) {
        double baseline = applied ? baseMaxHealth : npc.getMaxHealth();
        if (!data.isHealthScalingEnabled()) {
            return "Party health: off, base " + formatHealth(baseline)
                    + ", scaled " + formatHealth(npc.getMaxHealth());
        }
        int players = Math.max(1, scaledPlayerCount);
        String update = data.getHealthScalingUpdateMode()
                == TeleportPathData.HEALTH_SCALING_LOCK_AT_START ? "locked" : "dynamic";
        String adjustment = data.getHealthScalingAdjustment()
                == TeleportPathData.HEALTH_SCALING_KEEP_CURRENT ? "keep current" : "keep percent";
        String mode = switch (data.getHealthScalingMode()) {
            case TeleportPathData.HEALTH_SCALING_FLAT -> "+"
                    + data.getHealthPerPlayerFlat() + " HP/player";
            case TeleportPathData.HEALTH_SCALING_PERCENT_AND_FLAT -> "+"
                    + data.getHealthPerPlayerPercent() + "% +"
                    + data.getHealthPerPlayerFlat() + " HP/player";
            default -> "+" + data.getHealthPerPlayerPercent() + "%/player";
        };
        return "Party health: " + players + " players " + update + " ("
                + Math.max(0, players - 1) + " extra), cap " + data.getHealthScalingPlayerCap()
                + ", base " + formatHealth(baseline) + ", scaled "
                + formatHealth(npc.getMaxHealth()) + ", mode " + mode + ", adjust " + adjustment;
    }

    void tick(TeleportPathData data) {
        if (!boss.isEncounterRunning() || !data.isHealthScalingEnabled()) {
            clear(data, false);
            return;
        }
        if (unavailable) {
            return;
        }
        long configuration = configuration(data);
        if (!applied || configuration != lastConfiguration) {
            apply(data, configuration);
        }
    }

    private long configuration(TeleportPathData data) {
        return scaledPlayerCount
                | (long) data.getHealthScalingMode() << 8
                | (long) data.getHealthPerPlayerPercent() << 10
                | (long) data.getHealthPerPlayerFlat() << 21;
    }

    /** Applies one numeric ADD_VALUE bonus without ever changing the attribute base value. */
    private void apply(TeleportPathData data, long configuration) {
        AttributeInstance instance = npc.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) {
            unavailable = true;
            LOGGER.warn("Boss {} has no MAX_HEALTH attribute; party health scaling is disabled",
                    npc.getName().getString());
            return;
        }

        float oldMax = npc.getMaxHealth();
        float oldHealth = npc.getHealth();
        if (!applied) {
            // A controller can be rebuilt around a still-loaded entity, so discard only our id.
            instance.removeModifier(BossHealthScalingUtil.PARTY_HEALTH_MODIFIER_ID);
            baseMaxHealth = finiteHealth(npc.getMaxHealth(), 1.0D);
            applied = true;
        } else {
            instance.removeModifier(BossHealthScalingUtil.PARTY_HEALTH_MODIFIER_ID);
        }

        double bonus = bonus(instance, data);
        if (bonus > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(
                    BossHealthScalingUtil.PARTY_HEALTH_MODIFIER_ID, bonus,
                    AttributeModifier.Operation.ADD_VALUE));
        }
        lastConfiguration = configuration;
        adjustCurrentHealth(oldHealth, oldMax, npc.getMaxHealth(), data.getHealthScalingAdjustment());
    }

    private double bonus(AttributeInstance instance, TeleportPathData data) {
        double desiredMax = data.calculateScaledMaxHealth(baseMaxHealth, scaledPlayerCount);
        double sanitizedMax = instance.getAttribute().value().sanitizeValue(desiredMax);
        return BossHealthScalingUtil.calculateAdditiveBonus(instance, sanitizedMax);
    }

    private static double finiteHealth(double value, double nonFiniteFallback) {
        if (!Double.isFinite(value)) {
            return nonFiniteFallback;
        }
        return Math.max(1.0D, value);
    }

    private void adjustCurrentHealth(float oldHealth, float oldMax, float newMax, int adjustment) {
        if (!npc.isAlive()) {
            return;
        }
        double wanted = adjustment == TeleportPathData.HEALTH_SCALING_KEEP_CURRENT
                ? oldHealth
                : oldMax <= 0.0F ? newMax : (double) newMax * oldHealth / oldMax;
        npc.setHealth((float) Mth.clamp(wanted, 1.0D, Math.max(1.0F, newMax)));
    }

    /** Removes the party modifier and restores HP according to reset and adjustment policy. */
    void clear(TeleportPathData data, boolean resetHeal) {
        AttributeInstance instance = npc.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) {
            applied = false;
            baseMaxHealth = 0.0D;
            lastConfiguration = Long.MIN_VALUE;
            return;
        }
        boolean hadModifier = instance.hasModifier(BossHealthScalingUtil.PARTY_HEALTH_MODIFIER_ID);
        if (!applied && !hadModifier) {
            if (resetHeal && npc.isAlive()) {
                npc.setHealth(npc.getMaxHealth());
            }
            return;
        }

        float oldMax = npc.getMaxHealth();
        float oldHealth = npc.getHealth();
        instance.removeModifier(BossHealthScalingUtil.PARTY_HEALTH_MODIFIER_ID);
        float newMax = npc.getMaxHealth();
        if (npc.isAlive()) {
            if (resetHeal) {
                npc.setHealth(newMax);
            } else {
                adjustCurrentHealth(oldHealth, oldMax, newMax, data.getHealthScalingAdjustment());
            }
        }
        applied = false;
        baseMaxHealth = 0.0D;
        lastConfiguration = Long.MIN_VALUE;
    }
}
