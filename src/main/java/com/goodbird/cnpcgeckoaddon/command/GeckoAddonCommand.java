package com.goodbird.cnpcgeckoaddon.command;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.mixin.IDataDisplay;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import com.goodbird.cnpcgeckoaddon.mixin.ITeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.ProjectileEntityUtil;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
        event.getDispatcher().register(root);
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
