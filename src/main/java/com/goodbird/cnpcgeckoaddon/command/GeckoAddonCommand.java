package com.goodbird.cnpcgeckoaddon.command;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.ai.NpcDamageInfoManager;
import com.goodbird.cnpcgeckoaddon.ai.TeleportPathController;
import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.IBossController;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.ProjectileEntityUtil;
import com.goodbird.cnpcgeckoaddon.world.NpcCarryManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CNPCGeckoAddon.MODID)
public class GeckoAddonCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cnpcgecko")
                .requires(source -> source.hasPermission(2));
        root.then(Commands.literal("scan").executes(context -> check(context.getSource(), false)));
        root.then(Commands.literal("fix").executes(context -> check(context.getSource(), true)));
        root.then(Commands.literal("boss").executes(context -> showBossStatus(context.getSource())));
        root.then(Commands.literal("carry").executes(context -> toggleCarry(context.getSource())));
        root.then(Commands.literal("damageinfo").executes(context -> toggleDamageInfo(context.getSource())));
        event.getDispatcher().register(root);
    }

    private static int toggleCarry(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = NpcCarryManager.toggleMode(player);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "cnpcgeckoaddon.carry.on" : "cnpcgeckoaddon.carry.off"), false);
        return 1;
    }

    private static int toggleDamageInfo(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean enabled = NpcDamageInfoManager.toggle(player);
        source.sendSuccess(() -> Component.translatable(enabled
                ? "cnpcgeckoaddon.cmd.damageinfo_on" : "cnpcgeckoaddon.cmd.damageinfo_off"), false);
        return 1;
    }

    private static int showBossStatus(CommandSourceStack source) {
        int found = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof EntityNPCInterface npc)) {
                    continue;
                }
                TeleportPathData data = ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
                if (!data.isEnabled()) {
                    continue;
                }
                found++;
                TeleportPathController controller = npc instanceof IBossController holder
                        ? holder.cnpcgeckoaddon$getTeleportPathController() : null;
                int configured = controller == null
                        ? (int) data.getTotems().entries().stream()
                        .filter(entry -> entry.isEnabled() && !entry.getCloneName().isEmpty()).count()
                        : controller.configuredTotemCount();
                int alive = controller == null ? 0 : controller.aliveTotemCount();
                boolean protectedNow = controller != null && controller.isTotemProtected();
                boolean heldNow = controller != null && controller.isTotemHeld();
                // The mode is only worth naming while the formation still wards at all.
                String protection = !data.isTotemsEnabled() ? "disabled"
                        : !data.isTotemGrantInvulnerability() ? "off"
                        : data.getTotemProtectionMode() == TeleportPathData.TOTEM_PROTECTION_FULL_IMMUNITY
                        ? "full immunity" : "lethal guard";
                String hold = !data.isTotemsEnabled() || !data.isTotemHoldBoss() ? "off"
                        : heldNow ? "on (holding)" : "on (idle)";
                String respawn = switch (data.getTotemRespawnMode()) {
                    case TeleportPathData.TOTEM_RESPAWN_NEVER -> "never";
                    case TeleportPathData.TOTEM_RESPAWN_DELAYED -> "delayed";
                    default -> "next encounter";
                };
                String bossLine = describe(npc, "boss status");
                String totemLine = "Totems: " + alive + "/" + configured + ", protection="
                        + protection + (protectedNow ? " (active)" : " (inactive)")
                        + ", hold=" + hold + ", respawn=" + respawn;
                String captureLine = controller == null ? "Capture: ready"
                        : controller.captureStatus(level.getGameTime());
                String leashLine = controller == null
                        ? (data.isHomeLeashEnabled() ? "Home leash: awaiting controller" : "Home leash: off")
                        : controller.homeLeashStatus(level.getGameTime(), data);
                String partyHealthLine = controller == null
                        ? (data.isHealthScalingEnabled()
                        ? "Party health: awaiting controller" : "Party health: off")
                        : controller.partyHealthStatus(data);
                source.sendSuccess(() -> Component.literal(bossLine), false);
                source.sendSuccess(() -> Component.literal(totemLine), false);
                source.sendSuccess(() -> Component.literal(captureLine), false);
                source.sendSuccess(() -> Component.literal(leashLine), false);
                source.sendSuccess(() -> Component.literal(partyHealthLine), false);
            }
        }
        if (found == 0) {
            source.sendSuccess(() -> Component.literal("No loaded configured bosses found"), false);
        }
        return found;
    }

    private static int check(CommandSourceStack source, boolean fix) {
        List<String> problems = new ArrayList<>();
        int fixed = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof EntityCustomNpc npc)) {
                    continue;
                }
                String model = checkModel(npc);
                if (model != null) {
                    problems.add(describe(npc, model));
                }
                RangedExtraData ranged = ((IRangedData) npc.stats.ranged).getRangedExtraData();
                String projectile = ranged.getProjectileEntity();
                if (projectile != null && !projectile.isEmpty() && !ProjectileEntityUtil.isSelectable(projectile, level)) {
                    problems.add(describe(npc, "projectile " + projectile));
                    if (fix) {
                        ranged.setProjectileEntity("");
                        npc.updateClient();
                        fixed++;
                    }
                }
                TeleportPathData teleport =
                        ((ITeleportPathData) npc.ais).cnpcgeckoaddon$getTeleportPathData();
                if (teleport.isEnabled() && npc.ais.getMovingPathSize() < 2) {
                    problems.add(describe(npc, "teleport path needs at least 2 points"));
                    if (fix) {
                        teleport.setEnabled(false);
                        npc.updateClient();
                        fixed++;
                    }
                }
            }
        }
        if (problems.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No broken npc models, projectiles or teleport paths found"), false);
            return 1;
        }
        for (String problem : problems) {
            source.sendSuccess(() -> Component.literal(problem), false);
        }
        int repaired = fixed;
        source.sendSuccess(() -> Component.literal(fix
                ? "Found " + problems.size() + " problems, repaired " + repaired + " NPC settings"
                : "Found " + problems.size() + " problems, run /cnpcgecko fix to repair invalid NPC settings"), false);
        return problems.size();
    }

    private static String checkModel(EntityCustomNpc npc) {
        ResourceLocation name = npc.modelData.getEntityName();
        if (!npc.modelData.hasEntity() || name == null) {
            return null;
        }
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(name)) {
            return null;
        }
        return "model entity " + name;
    }

    private static String describe(EntityNPCInterface npc, String problem) {
        return String.format("%s at %s %d %d %d: %s",
                npc.getName().getString(),
                npc.level().dimension().location(),
                npc.blockPosition().getX(),
                npc.blockPosition().getY(),
                npc.blockPosition().getZ(),
                problem);
    }
}
