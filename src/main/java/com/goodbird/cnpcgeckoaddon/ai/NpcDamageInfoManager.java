package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.NpcDamageResistEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The /cnpcgecko damageinfo toggle: while it is on, every hit that player lands on an npc
 * is broken down in their chat - damage type id, its tags, the delivering entity, and what
 * the resistance list did to the number.
 *
 * <p>Exists because a gun mod's damage ids are written nowhere a server owner can read:
 * one test shot against any npc names the exact string to put into the resistance list,
 * instead of an hour spent in someone else's sources.</p>
 */
public final class NpcDamageInfoManager {
    private static final Set<UUID> ENABLED = new HashSet<>();

    /** How far from a bouncing totem a breakdown listener may stand and still be told, in blocks. */
    private static final double TOTEM_REPORT_RANGE = 48.0D;

    private NpcDamageInfoManager() {
    }

    /** @return true when the breakdown is on for this player after the toggle */
    public static boolean toggle(ServerPlayer player) {
        if (ENABLED.add(player.getUUID())) {
            return true;
        }
        ENABLED.remove(player.getUUID());
        return false;
    }

    /** Chats the breakdown of one npc hit back to its attacker, if they asked for it. */
    public static void report(LivingIncomingDamageEvent event, float before, NpcDamageResistEntry resist) {
        if (ENABLED.isEmpty() || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || !ENABLED.contains(player.getUUID())) {
            return;
        }
        StringBuilder text = describe(event.getSource());
        float after = event.isCanceled() ? 0.0F : event.getAmount();
        text.append("\ndamage=").append(format(before));
        if (resist == null) {
            text.append(" (no matching rule)");
        } else {
            text.append(" -> ").append(format(after))
                    .append(" (").append(resist.getMatcher())
                    .append(" ").append(resist.getPercent()).append("%)");
        }
        player.sendSystemMessage(Component.literal(text.toString()).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Chats what the boss' barrier did with a hit: how much of it the shield took, and how
     * much shield is left after it. A hit inside the barrier's hurt cooldown takes nothing,
     * and says so rather than leaving a zero to puzzle over.
     *
     * <p>One line under the breakdown the resistance listener already sent, rather than a
     * whole second breakdown: the hit has been described once by then.</p>
     */
    public static void reportBarrier(LivingIncomingDamageEvent event, float before, float absorbed, float left) {
        if (ENABLED.isEmpty() || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || !ENABLED.contains(player.getUUID())) {
            return;
        }
        StringBuilder text = new StringBuilder("barrier: absorbed ").append(format(absorbed))
                .append(" of ").append(format(before)).append(", ").append(format(left)).append(" left");
        if (absorbed <= 0.0F && before > 0.0F) {
            text.append(" (inside the hurt cooldown)");
        } else if (left <= 0.0F) {
            text.append(" (broken)");
        }
        player.sendSystemMessage(Component.literal(text.toString()).withStyle(ChatFormatting.GRAY));
    }

    /** Chats the window's multiplier, the same way: one line under the breakdown. */
    public static void reportExposed(LivingIncomingDamageEvent event, float before, int percent) {
        if (ENABLED.isEmpty() || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || !ENABLED.contains(player.getUUID())) {
            return;
        }
        player.sendSystemMessage(Component.literal("barrier: exposed, " + format(before) + " -> "
                + format(event.getAmount()) + " (" + percent + "%)").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Chats why a totem refused a hit: the same breakdown, ending in the ability that was
     * behind it and is not on that slot's list.
     *
     * <p>Told to anyone standing near the totem rather than only to whoever swung, because
     * the hits worth explaining most - the lava the builder poured, a fire, the boss' own
     * ability - have no player behind them to answer.</p>
     */
    public static void reportTotemBlock(LivingIncomingDamageEvent event, float before, int ability) {
        if (ENABLED.isEmpty()) {
            return;
        }
        MutableComponent message = Component.literal(describe(event.getSource())
                        .append("\ndamage=").append(format(before)).append(" -> 0.0 (totem: ")
                        .toString())
                .append(ability >= 0 && ability < BossAbilityKind.COUNT
                        ? Component.translatable(BossAbilityKind.LABELS[ability])
                        : Component.literal("plain damage"))
                .append(" not on its list)")
                .withStyle(ChatFormatting.GRAY);
        for (ServerPlayer player : listeners(event)) {
            player.sendSystemMessage(message);
        }
    }

    /** The attacker when there is one, plus every listener within sight of the totem. */
    private static List<ServerPlayer> listeners(LivingIncomingDamageEvent event) {
        List<ServerPlayer> result = new ArrayList<>();
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && ENABLED.contains(attacker.getUUID())) {
            result.add(attacker);
        }
        Entity totem = event.getEntity();
        if (totem.level() instanceof ServerLevel level) {
            for (ServerPlayer player : level.players()) {
                if (!result.contains(player) && ENABLED.contains(player.getUUID())
                        && player.distanceToSqr(totem) <= TOTEM_REPORT_RANGE * TOTEM_REPORT_RANGE) {
                    result.add(player);
                }
            }
        }
        return result;
    }

    /** The half of the breakdown that is only about the hit: its type, its carrier, its tags. */
    private static StringBuilder describe(DamageSource source) {
        StringBuilder text = new StringBuilder("type=");
        text.append(source.typeHolder().unwrapKey()
                .map(key -> key.location().toString()).orElse("(unregistered)"));
        Entity direct = source.getDirectEntity();
        if (direct != null) {
            text.append("  entity=").append(BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()));
        }
        String tags = source.typeHolder().tags()
                .map(tag -> "#" + tag.location())
                .collect(Collectors.joining(" "));
        if (!tags.isEmpty()) {
            text.append("\ntags=").append(tags);
        }
        return text;
    }

    private static String format(float damage) {
        return String.format(Locale.ROOT, "%.1f", damage);
    }
}
