package com.goodbird.cnpcgeckoaddon.client;

import com.goodbird.cnpcgeckoaddon.CNPCGeckoAddon;
import com.goodbird.cnpcgeckoaddon.utils.CrashGuard;
import com.goodbird.cnpcgeckoaddon.utils.EventGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;

/**
 * "Select in the world": the npc's editor steps aside, the builder clicks the blocks, and the
 * editor comes back with them filled in.
 *
 * <p>The editor is not closed but hidden. The whole stack of screens - the npc's own page and
 * every settings screen opened on top of it - is held here as it was and put back with
 * {@code setScreen}, which lays every screen of the stack out again from the settings it edits.
 * That is what keeps every other value on the screen as it was before the pick: they were read
 * into the settings when the button was pressed, and they are read back out of them now. Nothing
 * is sent anywhere in between; the settings still go to the server the way they always have,
 * when the editor is closed.</p>
 *
 * <p>While the builder picks, neither mouse button reaches the world: the left one marks a
 * block instead of breaking it, the right one gives the pick up. Escape gives it up too and
 * brings the editor back instead of the pause menu. Anything else that takes the screen - the
 * inventory, the chat, death, a change of dimension - ends the pick, and what the editor held is
 * saved the way closing it would have saved it, since the editor itself is not coming back.
 * Leaving the world simply drops it.</p>
 */
@EventBusSubscriber(modid = CNPCGeckoAddon.MODID, value = Dist.CLIENT)
public final class ZoneSelectionClient {

    public static final String CANCELLED = "cnpcgeckoaddon.zone.cancelled";
    /** What the button of an editor with a box to pick reads, and of one with a spot to pick. */
    public static final String SELECT_BOX = "cnpcgeckoaddon.zone.select_box";
    public static final String SELECT_POINT = "cnpcgeckoaddon.zone.select_point";

    /**
     * How far the crosshair reaches for a block while picking. Much further than a hand does:
     * the far corner of an arena is picked from its middle rather than by walking to it.
     */
    public static final double PICK_RANGE = 64.0D;

    /** What a finished box is written into, given the block offsets are measured from. */
    @FunctionalInterface
    public interface BoxPicked {
        void apply(ZoneSelection.Box box, BlockPos anchor);
    }

    /** What a finished point is written into, given the block offsets are measured from. */
    @FunctionalInterface
    public interface PointPicked {
        void apply(ZoneSelection.Point point, BlockPos anchor);
    }

    private static final class Session {
        private final ZoneSelection selection;
        private final int kind;
        private final Screen screen;
        /** The editor whose button started the pick, on top of {@link #screen}'s stack. */
        private final Screen editor;
        private final BoxPicked onBox;
        private final PointPicked onPoint;
        private boolean hidden;
        private ClientLevel level;
        /** Whether the left button is still down from the click last counted. */
        private boolean attackHeld;

        private Session(ZoneSelection selection, int kind, Screen screen, BoxPicked onBox, PointPicked onPoint) {
            this.selection = selection;
            this.kind = kind;
            this.screen = screen;
            this.editor = topOf(screen);
            this.onBox = onBox;
            this.onPoint = onPoint;
        }
    }

    private static Session session;

    /**
     * Set when the editor comes back in the middle of a tick: clicks the game still has queued
     * for that tick would otherwise land on the world behind the editor. Cleared at its end.
     */
    private static boolean swallowClicks;

    /** Escape pauses the sounds along with the game; the editor put back instead does not. */
    private static boolean resumeSound;

    private ZoneSelectionClient() {
    }

    /**
     * Starts picking two corners for the editor on screen.
     *
     * @param kind    what the outline is coloured as, the preview's kinds
     * @param corners which block a click makes a corner: the one in front of the face clicked for a
     *                zone somebody stands in, the one clicked for a zone made of the blocks it names
     * @param apply   writes the box into the editor's settings; called once, on the second click
     */
    public static void selectBox(int kind, ZoneSelection.Pick corners, BoxPicked apply) {
        begin(ZoneSelection.box(corners), kind, apply, null);
    }

    /**
     * Starts picking one block for the editor on screen; {@code apply} is called on the click. For
     * an editor that writes the block in front of the face clicked, as every spot's does.
     */
    public static void selectPoint(int kind, PointPicked apply) {
        selectPoint(kind, ZoneSelection.Pick.IN_FRONT, apply);
    }

    /**
     * Starts picking one block for the editor on screen; {@code apply} is called on the click.
     *
     * @param spot which block of the click {@code apply} writes: the pick's marker stands on it
     */
    public static void selectPoint(int kind, ZoneSelection.Pick spot, PointPicked apply) {
        begin(ZoneSelection.point(spot), kind, null, apply);
    }

    private static void begin(ZoneSelection selection, int kind, BoxPicked onBox, PointPicked onPoint) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        // Hidden on the next tick rather than here: this runs inside the click on the editor's
        // own button, with the editor still walking its widgets.
        session = new Session(selection, kind, minecraft.screen, onBox, onPoint);
    }

    /** Whether the builder is picking blocks right now, with the editor out of the way. */
    public static boolean isPicking() {
        Session current = session;
        return current != null && current.hidden;
    }

    /** The editor held while the builder picks, or null. */
    public static Screen hiddenScreen() {
        Session current = session;
        return current != null && current.hidden ? current.screen : null;
    }

    /** The pick under way, or null. */
    public static ZoneSelection selection() {
        Session current = session;
        return current != null && current.hidden ? current.selection : null;
    }

    /** What the pick under way is coloured as. */
    public static int kind() {
        Session current = session;
        return current == null ? 0 : current.kind;
    }

    /** The block under the crosshair as far as a pick reaches, or null when it is on none. */
    public static BlockHitResult aimedBlock(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity eye = minecraft.getCameraEntity() != null ? minecraft.getCameraEntity() : minecraft.player;
        if (eye == null) {
            return null;
        }
        HitResult hit = eye.pick(PICK_RANGE, partialTick, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block : null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        EventGuard.handle("client.zone_select.click", event, ZoneSelectionClient::handleClick);
    }

    private static void handleClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack() && !event.isUseItem()) {
            return;
        }
        Session current = session;
        if (current == null || !current.hidden) {
            if (swallowClicks) {
                event.setCanceled(true);
                event.setSwingHand(false);
            }
            return;
        }
        // Nothing is broken, placed, used or hit while the builder is picking.
        event.setCanceled(true);
        event.setSwingHand(false);
        Minecraft minecraft = Minecraft.getInstance();
        if (event.isUseItem()) {
            cancel(minecraft, current);
            return;
        }
        // Held down, the button keeps firing every tick at the block under it; only the press counts.
        if (current.attackHeld) {
            return;
        }
        current.attackHeld = true;
        BlockHitResult hit = aimedBlock(1.0F);
        if (hit == null || minecraft.player == null) {
            return;
        }
        if (current.selection.click(hit.getBlockPos(), hit.getDirection(), minecraft.player.getYRot())) {
            finish(minecraft, current);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        EventGuard.handle("client.zone_select.tick", event, ZoneSelectionClient::handleTick);
    }

    private static void handleTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        swallowClicks = false;
        if (resumeSound) {
            resumeSound = false;
            if (minecraft.screen != null && !minecraft.screen.isPauseScreen()) {
                minecraft.getSoundManager().resume();
            }
        }
        Session current = session;
        if (current == null) {
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            session = null;
            return;
        }
        if (!current.hidden) {
            if (minecraft.screen != current.screen || topOf(minecraft.screen) != current.editor) {
                // The editor was closed - Done, Escape, Delete - before it could step aside: there
                // is nothing to pick for, and nothing to come back to.
                session = null;
                return;
            }
            hide(minecraft, current);
            return;
        }
        // The level object changes on a dimension transfer. A screen that got past the opening
        // event is caught here too.
        if (minecraft.level != current.level || minecraft.screen != null) {
            abandon(minecraft, current);
            return;
        }
        if (!minecraft.options.keyAttack.isDown()) {
            current.attackHeld = false;
        }
        String hint = current.selection.hintKey();
        if (hint != null) {
            // Sent every tick, so it never starts to fade while the pick is on.
            minecraft.gui.setOverlayMessage(Component.translatable(hint), false);
        }
    }

    private static void hide(Minecraft minecraft, Session current) {
        // A focused field would otherwise stay the one keys go to after the editor is back.
        GuiTextFieldNop.unfocus();
        current.level = minecraft.level;
        current.hidden = true;
        minecraft.setScreen(null);
    }

    @SubscribeEvent
    public static void screenOpening(ScreenEvent.Opening event) {
        EventGuard.handle("client.zone_select.screen", event, ZoneSelectionClient::handleScreenOpening);
    }

    private static void handleScreenOpening(ScreenEvent.Opening event) {
        Session current = session;
        if (current == null || !current.hidden) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getNewScreen() instanceof PauseScreen) {
            // Escape: the editor comes back as it was, in place of the pause menu.
            session = null;
            current.selection.cancel();
            resumeSound = true;
            tell(minecraft, CANCELLED);
            event.setNewScreen(current.screen);
            return;
        }
        abandon(minecraft, current);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        EventGuard.handle("client.zone_select.logout", event, ZoneSelectionClient::handleLogout);
    }

    private static void handleLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        session = null;
        swallowClicks = false;
        resumeSound = false;
    }

    /** The second corner, or the point, is in: the editor takes it and comes back. */
    private static void finish(Minecraft minecraft, Session current) {
        session = null;
        BlockPos anchor = anchorOf(current.screen);
        // Behind its own guard: a failure here must not hand the click back to the world, which
        // the event guard would do by putting the click's cancellation back.
        CrashGuard.run("client.zone_select.apply", () -> {
            if (current.selection.isPoint()) {
                current.onPoint.apply(current.selection.pointResult(), anchor);
            } else {
                current.onBox.apply(current.selection.result(), anchor);
            }
        });
        minecraft.gui.setOverlayMessage(Component.empty(), false);
        reopen(minecraft, current);
    }

    /** The right button: the pick is given up and the editor comes back as it was. */
    private static void cancel(Minecraft minecraft, Session current) {
        session = null;
        current.selection.cancel();
        tell(minecraft, CANCELLED);
        reopen(minecraft, current);
    }

    private static void reopen(Minecraft minecraft, Session current) {
        swallowClicks = true;
        minecraft.setScreen(current.screen);
    }

    /**
     * Something else took the screen: the pick is over and the editor is not coming back, so
     * what it holds is saved the way closing it saves it rather than dropped with it.
     */
    private static void abandon(Minecraft minecraft, Session current) {
        session = null;
        current.selection.cancel();
        if (current.screen instanceof GuiBasic editor && minecraft.getConnection() != null) {
            editor.save();
        }
        tell(minecraft, CANCELLED);
    }

    private static void tell(Minecraft minecraft, String key) {
        minecraft.gui.setOverlayMessage(Component.translatable(key), false);
    }

    /** The screen on top of a stack of sub-screens: the one the builder is looking at. */
    private static Screen topOf(Screen screen) {
        Screen top = screen;
        // Bounded, against a stack that loops back on itself.
        for (int depth = 0; top instanceof GuiBasic basic && basic.getSubGui() != null && depth < 32; depth++) {
            top = basic.getSubGui();
        }
        return top;
    }

    /**
     * The block an offset is measured from: the npc's own, the one every "use my position"
     * button of these editors measures from. Every editor that picks lives on an npc's page.
     */
    private static BlockPos anchorOf(Screen editor) {
        if (editor instanceof GuiNPCInterface page && page.npc != null) {
            return page.npc.blockPosition();
        }
        return BlockPos.ZERO;
    }
}
