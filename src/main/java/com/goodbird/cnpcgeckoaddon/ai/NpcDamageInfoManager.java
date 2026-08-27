package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.NpcDamageResistEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashSet;
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
        DamageSource source = event.getSource();
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

    private static String format(float damage) {
        return String.format(Locale.ROOT, "%.1f", damage);
    }
}
