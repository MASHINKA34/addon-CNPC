package com.goodbird.cnpcgeckoaddon.client;

import com.goodbird.cnpcgeckoaddon.entity.EntityCustomModel;
import com.goodbird.cnpcgeckoaddon.network.ManualAnimationClientBridge;
import com.goodbird.cnpcgeckoaddon.tile.TileEntityCustomModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import noppes.npcs.blocks.tiles.TileScripted;
import noppes.npcs.entity.EntityCustomNpc;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * The client half of the two manual animation packets: looks the target up in the client
 * level and hands it the animation. Moved here out of the packet classes so those never
 * mention {@code Minecraft} - a packet class is loaded on the dedicated server too.
 */
public final class ManualAnimationClient {

    private ManualAnimationClient() {
    }

    /** Called once from the client renderer registration, before any server can send. */
    public static void register() {
        ManualAnimationClientBridge.setHandlers(
                ManualAnimationClient::applyToEntity, ManualAnimationClient::applyToTile);
    }

    private static void applyToEntity(int entityId, RawAnimation animation) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(entityId);
        if (!(entity instanceof EntityCustomNpc npc)) {
            return;
        }
        if (npc.modelData == null || !(npc.modelData.getEntity(npc) instanceof EntityCustomModel model)) {
            return;
        }
        model.manualAnim = animation;
    }

    private static void applyToTile(BlockPos pos, RawAnimation animation) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof TileScripted tile)) {
            return;
        }
        // renderTile can hold any BlockEntity the scripted block was told to display,
        // so only reuse it when it really is ours.
        if (!(tile.renderTile instanceof TileEntityCustomModel)) {
            tile.renderTile = new TileEntityCustomModel(tile);
        }
        ((TileEntityCustomModel) tile.renderTile).manualAnim = animation;
    }
}
