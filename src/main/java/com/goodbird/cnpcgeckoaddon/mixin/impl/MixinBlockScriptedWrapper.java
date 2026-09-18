package com.goodbird.cnpcgeckoaddon.mixin.impl;

import com.goodbird.cnpcgeckoaddon.network.NetworkWrapper;
import com.goodbird.cnpcgeckoaddon.network.PacketSyncTileAnimation;
import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.tile.TileEntityCustomModel;
import com.goodbird.cnpcgeckoaddon.utils.ResourceIds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.wrapper.BlockScriptedWrapper;
import noppes.npcs.api.wrapper.BlockWrapper;
import noppes.npcs.blocks.tiles.TileScripted;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import software.bernie.geckolib.animation.RawAnimation;

@Mixin(BlockScriptedWrapper.class)
public abstract class MixinBlockScriptedWrapper extends BlockWrapper {

    /*
     * What a scripted block shows for an id a script got wrong: the placeholders the npc models
     * fall back to, so a typo reads as "model not found" on the block rather than as an
     * exception in the script's console.
     */
    @Unique
    private static final ResourceLocation cnpcgeckoaddon$MISSING_MODEL =
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "geo/modelnotfound.geo.json");
    @Unique
    private static final ResourceLocation cnpcgeckoaddon$MISSING_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "textures/model/alphabet.png");
    @Unique
    private static final ResourceLocation cnpcgeckoaddon$MISSING_ANIMATION =
            ResourceLocation.fromNamespaceAndPath(CNPCGeckoAddon.MODID, "animations/none.animation.json");

    protected MixinBlockScriptedWrapper(Level level, Block block, BlockPos pos) {
        super(level, block, pos);
    }

    @Unique
    public TileEntityCustomModel getOrCreateTECM(){
        TileScripted tile = (TileScripted) getMCTileEntity();
        if(!(tile.renderTile instanceof TileEntityCustomModel)){
            tile.renderTile = new TileEntityCustomModel(tile);
        }
        return (TileEntityCustomModel) tile.renderTile;
    }

    @Unique
    public void setGeckoModel(String model) {
        TileEntityCustomModel geckoTile = getOrCreateTECM();
        geckoTile.modelResLoc = ResourceIds.parseFromScript(model, cnpcgeckoaddon$MISSING_MODEL,
                "gecko model", cnpcgeckoaddon$where());
        ((TileScripted) getMCTileEntity()).needsClientUpdate = true;
    }

    @Unique
    public void setGeckoTexture(String texture) {
        TileEntityCustomModel geckoTile = getOrCreateTECM();
        geckoTile.textureResLoc = ResourceIds.parseFromScript(texture, cnpcgeckoaddon$MISSING_TEXTURE,
                "gecko texture", cnpcgeckoaddon$where());
        ((TileScripted) getMCTileEntity()).needsClientUpdate = true;
    }

    @Unique
    public void setGeckoAnimationFile(String animation) {
        TileEntityCustomModel geckoTile = getOrCreateTECM();
        geckoTile.animResLoc = ResourceIds.parseFromScript(animation, cnpcgeckoaddon$MISSING_ANIMATION,
                "gecko animation file", cnpcgeckoaddon$where());
        ((TileScripted) getMCTileEntity()).needsClientUpdate = true;
    }

    /** The block a script is talking to, for the log line about an id it got wrong. */
    @Unique
    private String cnpcgeckoaddon$where() {
        BlockPos pos = getMCTileEntity().getBlockPos();
        Level level = getMCTileEntity().getLevel();
        return "the scripted block at " + (level == null ? "" : level.dimension().location() + " ")
                + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    @Unique
    public void setGeckoIdleAnimation(String animation) {
        TileEntityCustomModel geckoTile = getOrCreateTECM();
        geckoTile.idleAnimName = animation;
        ((TileScripted) getMCTileEntity()).needsClientUpdate = true;
    }

    @Unique
    public void syncAnimForPlayer(RawAnimation builder, IPlayer<ServerPlayer> player) {
        if (getMCTileEntity().getLevel() instanceof ServerLevel level
                && player.getMCEntity().serverLevel() == level) {
            NetworkWrapper.send(player.getMCEntity(), new PacketSyncTileAnimation(
                    level.dimension().location(), getMCTileEntity().getBlockPos(), builder));
        }
    }

    @Unique
    public void syncAnimForAll(RawAnimation builder) {
        if (getMCTileEntity().getLevel() instanceof ServerLevel level) {
            BlockPos pos = getMCTileEntity().getBlockPos();
            PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos),
                    new PacketSyncTileAnimation(level.dimension().location(), pos, builder));
        }
    }
}
