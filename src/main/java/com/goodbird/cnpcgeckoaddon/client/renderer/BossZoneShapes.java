package com.goodbird.cnpcgeckoaddon.client.renderer;

import com.goodbird.cnpcgeckoaddon.ai.BossAbility;
import com.goodbird.cnpcgeckoaddon.ai.BossRiftDimension;
import com.goodbird.cnpcgeckoaddon.client.gui.BossZoneScreen;
import com.goodbird.cnpcgeckoaddon.client.renderer.BossZonePreview.Focus;
import com.goodbird.cnpcgeckoaddon.client.renderer.BossZonePreview.Shape;
import com.goodbird.cnpcgeckoaddon.data.BossAbilityKind;
import com.goodbird.cnpcgeckoaddon.data.BossCastSpot;
import com.goodbird.cnpcgeckoaddon.data.BossConeAimList;
import com.goodbird.cnpcgeckoaddon.data.BossConeAimPoint;
import com.goodbird.cnpcgeckoaddon.data.BossCoverSettings;
import com.goodbird.cnpcgeckoaddon.data.BossHazardSettings;
import com.goodbird.cnpcgeckoaddon.data.BossLeapSettings;
import com.goodbird.cnpcgeckoaddon.data.BossMinionSpawnList;
import com.goodbird.cnpcgeckoaddon.data.BossMinionSpawnPoint;
import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossPlatformZone;
import com.goodbird.cnpcgeckoaddon.data.BossPlatformZoneList;
import com.goodbird.cnpcgeckoaddon.data.BossRiftCrystalList;
import com.goodbird.cnpcgeckoaddon.data.BossRiftCrystalPoint;
import com.goodbird.cnpcgeckoaddon.data.BossTotemEntry;
import com.goodbird.cnpcgeckoaddon.data.BossTotemList;
import com.goodbird.cnpcgeckoaddon.data.BossVentZone;
import com.goodbird.cnpcgeckoaddon.data.BossVentZoneList;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import com.goodbird.cnpcgeckoaddon.utils.ZoneCoordinates;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Every zone and spot of the boss whose screens are open, as shapes to draw.
 *
 * <p>Read straight out of the settings the screens edit - the very objects, not a copy sent
 * from the server - so a corner typed into a field shows the moment the field lets go of it.
 * Each shape is placed the way the fight places it: fixed blocks where they are, offsets from
 * the npc, which is what the fight counts them from as long as the boss stands where it
 * activated, and every box cut to the world's height the way the fight cuts it.</p>
 *
 * <p>What is switched on is drawn, and what is being edited is drawn whether it is switched on
 * or not. With a phase's menu open only that phase is drawn; above it, every phase is, each
 * name headed by its phase.</p>
 */
final class BossZoneShapes {

    /** Deeper than any stack of screens gets; a guard against a stack that loops back on itself. */
    private static final int MAX_DEPTH = 32;

    private BossZoneShapes() {
    }

    /**
     * The shapes of the boss the given stack of screens edits, or none when it edits no boss.
     *
     * @param root  the screen on top of the npc's page, or the one held while its editor stepped aside
     */
    static List<Shape> collect(Screen root, ClientLevel level) {
        TeleportPathData data = null;
        EntityNPCInterface npc = null;
        int phase = -1;
        Object focus = null;
        Screen screen = root;
        for (int depth = 0; screen != null && depth < MAX_DEPTH; depth++) {
            if (screen instanceof BossZoneScreen zone) {
                if (zone.zoneBoss() != null) {
                    data = zone.zoneBoss();
                    npc = zone.zoneNpc();
                }
                if (zone.zonePhase() >= 0) {
                    phase = zone.zonePhase();
                }
                if (zone.zoneFocus() != null) {
                    focus = zone.zoneFocus();
                }
            }
            screen = screen instanceof GuiBasic basic ? basic.getSubGui() : null;
        }
        if (data == null || npc == null) {
            return List.of();
        }
        Builder builder = new Builder(level, npc, focus);
        builder.aggroZone(data);
        builder.totems(data);
        builder.chest(data);
        int count = data.getPhaseCount();
        if (phase >= 0 && phase < count) {
            builder.phase(data.getPhase(phase), "");
        } else {
            for (int index = 0; index < count; index++) {
                builder.phase(data.getPhase(index),
                        I18n.get("cnpcgeckoaddon.boss.phase") + " " + (index + 1) + " · ");
            }
        }
        return builder.shapes;
    }

    private static final class Builder {
        private final List<Shape> shapes = new ArrayList<>();
        private final ClientLevel level;
        private final Object focus;
        /** Where an offset of a spot kept as exact numbers counts from: the npc itself. */
        private final Vec3 home;
        /** Where an offset of a block counts from: the npc's block. */
        private final BlockPos homeBlock;
        /** Whether this client stands in the rift, the only place a rift's fixed spots mean anything. */
        private final boolean inRift;

        private Builder(ClientLevel level, EntityNPCInterface npc, Object focus) {
            this.level = level;
            this.focus = focus;
            this.home = npc.position();
            this.homeBlock = npc.blockPosition();
            this.inRift = BossRiftDimension.isRift(level);
        }

        private void aggroZone(TeleportPathData data) {
            boolean focused = focus == Focus.AGGRO_ZONE;
            if (!data.isAggroZoneEnabled() && !focused) {
                return;
            }
            box(BossZonePreview.KIND_AGGRO, I18n.get("cnpcgeckoaddon.boss.aggro_zone_title"),
                    ZoneCoordinates.blockBox(
                            new BlockPos(data.getAggroZoneX1(), data.getAggroZoneY1(), data.getAggroZoneZ1()),
                            new BlockPos(data.getAggroZoneX2(), data.getAggroZoneY2(), data.getAggroZoneZ2())),
                    focused);
        }

        private void totems(TeleportPathData data) {
            BossTotemList totems = data.getTotems();
            for (int index = 0; index < totems.size(); index++) {
                BossTotemEntry entry = totems.get(index);
                boolean focused = entry == focus;
                if (!entry.isEnabled() && !focused) {
                    continue;
                }
                // A fixed totem stands on the very numbers, not the middle of their block: the fight's own rule.
                Vec3 at = entry.getCoordinateMode() == BossTotemEntry.COORDINATE_FIXED
                        ? new Vec3(entry.getX(), entry.getY(), entry.getZ())
                        : home.add(entry.getX(), entry.getY(), entry.getZ());
                shapes.add(Shape.point(BossZonePreview.KIND_TOTEM,
                        numbered("cnpcgeckoaddon.boss.totem_entry_title", index), at, focused));
            }
        }

        private void chest(TeleportPathData data) {
            boolean focused = focus == Focus.CHEST;
            if (!data.isChestEnabled() && !focused) {
                return;
            }
            BlockPos block = switch (data.getChestPlacement()) {
                case TeleportPathData.CHEST_PLACEMENT_FIXED ->
                        new BlockPos(data.getChestFixedX(), data.getChestFixedY(), data.getChestFixedZ());
                case TeleportPathData.CHEST_PLACEMENT_ARENA ->
                        homeBlock.offset(data.getChestOffsetX(), data.getChestOffsetY(), data.getChestOffsetZ());
                // Where the boss falls: nowhere to draw before the fight.
                default -> null;
            };
            if (block != null) {
                shapes.add(Shape.point(BossZonePreview.KIND_CHEST, I18n.get("cnpcgeckoaddon.boss.chest_title"),
                        Vec3.atBottomCenterOf(block), focused));
            }
        }

        private void phase(BossPhaseData phase, String prefix) {
            hazard(phase.hazard(), prefix);
            cover(phase.cover(), prefix);
            platforms(phase.platform().getZones(), prefix);
            vents(phase.vent().getZones(), prefix);
            spawnPoints(phase.summon().getSpawnPoints(), BossAbilityKind.SUMMON,
                    "cnpcgeckoaddon.boss.minion_spawn_point_title", prefix, false);
            spawnPoints(phase.shadow().getPoints(), BossAbilityKind.SHADOW,
                    "cnpcgeckoaddon.boss.shadow_point_title", prefix, false);
            spawnPoints(phase.rift().getMinionPoints(), BossAbilityKind.RIFT,
                    "cnpcgeckoaddon.boss.rift_minions", prefix, true);
            crystals(phase, prefix);
            conePoints(phase.cone().getPoints(), prefix);
            castSpots(phase, prefix);
            leap(phase.leap(), prefix);
        }

        private void hazard(BossHazardSettings hazard, String prefix) {
            boolean focused = hazard == focus;
            if (!hazard.isEnabled() && !focused) {
                return;
            }
            String label = prefix + abilityName(BossAbilityKind.HAZARD);
            if (hazard.getMode() == BossPhaseData.HAZARD_MODE_BOX) {
                box(BossAbilityKind.HAZARD, label, ZoneCoordinates.blockBox(
                        new BlockPos(hazard.getX1(), hazard.getY1(), hazard.getZ1()),
                        new BlockPos(hazard.getX2(), hazard.getY2(), hazard.getZ2())), focused);
                return;
            }
            // The ring closes from its first radius to its last; both are drawn.
            Vec3 centre = hazard.getCenterMode() == BossPhaseData.HAZARD_CENTER_POINT
                    ? new Vec3(hazard.getCenterX() + 0.5D, home.y, hazard.getCenterZ() + 0.5D)
                    : home;
            shapes.add(Shape.ring(BossAbilityKind.HAZARD, label, centre, hazard.getStartRadius(), focused));
            if (hazard.getEndRadius() != hazard.getStartRadius()) {
                shapes.add(Shape.ring(BossAbilityKind.HAZARD, "", centre, hazard.getEndRadius(), focused));
            }
        }

        /** How far the strike reaches round the boss, and under the shelter rule the band the shelters fall in. */
        private void cover(BossCoverSettings cover, String prefix) {
            boolean focused = cover == focus;
            if (!cover.isEnabled() && !focused) {
                return;
            }
            shapes.add(Shape.ring(BossAbilityKind.COVER, prefix + abilityName(BossAbilityKind.COVER), home,
                    cover.getRange(), focused));
            if (cover.getMode() == BossPhaseData.COVER_MODE_SHELTER) {
                shapes.add(Shape.ring(BossAbilityKind.COVER, "", home, cover.getShelterMinRange(), focused));
                shapes.add(Shape.ring(BossAbilityKind.COVER, "", home, cover.getShelterMaxRange(), focused));
            }
        }

        private void platforms(BossPlatformZoneList zones, String prefix) {
            for (int index = 0; index < zones.size(); index++) {
                BossPlatformZone zone = zones.get(index);
                boolean focused = zone == focus;
                if (!zone.isEnabled() && !focused) {
                    continue;
                }
                box(BossAbilityKind.PLATFORM, prefix + numbered("cnpcgeckoaddon.boss.platform_zone_title", index),
                        ZoneCoordinates.box(zone.getX1(), zone.getY1(), zone.getZ1(), zone.getX2(), zone.getY2(),
                                zone.getZ2(), zone.getCoordinateMode() == BossPlatformZone.COORDINATE_FIXED, homeBlock),
                        focused);
            }
        }

        private void vents(BossVentZoneList zones, String prefix) {
            for (int index = 0; index < zones.size(); index++) {
                BossVentZone zone = zones.get(index);
                boolean focused = zone == focus;
                if (!zone.isEnabled() && !focused) {
                    continue;
                }
                box(BossAbilityKind.VENT, prefix + numbered("cnpcgeckoaddon.boss.vent_zone_title", index),
                        ZoneCoordinates.box(zone.getX1(), zone.getY1(), zone.getZ1(), zone.getX2(), zone.getY2(),
                                zone.getZ2(), zone.getCoordinateMode() == BossVentZone.COORDINATE_FIXED, homeBlock),
                        focused);
            }
        }

        private void spawnPoints(BossMinionSpawnList points, int kind, String titleKey, String prefix, boolean rift) {
            for (int index = 0; index < points.size(); index++) {
                BossMinionSpawnPoint point = points.get(index);
                boolean focused = point == focus;
                if (!point.isEnabled() && !focused) {
                    continue;
                }
                Vec3 at = spot(point.getCoordinateMode() == BossMinionSpawnPoint.COORDINATE_FIXED,
                        point.getX(), point.getY(), point.getZ(), rift);
                if (at != null) {
                    shapes.add(Shape.point(kind, prefix + numbered(titleKey, index), at, focused));
                }
            }
        }

        /** Each crystal's spot and the circle round it that counts as reaching it. */
        private void crystals(BossPhaseData phase, String prefix) {
            BossRiftCrystalList points = phase.rift().getCrystalPoints();
            for (int index = 0; index < points.size(); index++) {
                BossRiftCrystalPoint point = points.get(index);
                boolean focused = point == focus;
                if (!point.isEnabled() && !focused) {
                    continue;
                }
                Vec3 at = spot(point.getCoordinateMode() == BossRiftCrystalPoint.COORDINATE_FIXED,
                        point.getX(), point.getY(), point.getZ(), true);
                if (at == null) {
                    continue;
                }
                double radius = point.getRadiusTenths() > 0
                        ? point.getRadiusTenths() / 10.0D : phase.rift().crystalCollectRadius();
                shapes.add(Shape.point(BossAbilityKind.RIFT, prefix + numbered("cnpcgeckoaddon.boss.rift_crystals", index),
                        at, focused));
                shapes.add(Shape.ring(BossAbilityKind.RIFT, "", at, radius, focused));
            }
        }

        private void conePoints(BossConeAimList points, String prefix) {
            for (int index = 0; index < points.size(); index++) {
                BossConeAimPoint point = points.get(index);
                boolean focused = point == focus;
                if (!point.isEnabled() && !focused) {
                    continue;
                }
                Vec3 at = spot(point.getCoordinateMode() == BossConeAimPoint.COORDINATE_FIXED,
                        point.getX(), point.getY(), point.getZ(), false);
                shapes.add(Shape.point(BossAbilityKind.CONE, prefix + numbered("cnpcgeckoaddon.boss.cone_point_title", index),
                        at, focused));
            }
        }

        /** The spot of every ability that sends the boss somewhere before it casts. */
        private void castSpots(BossPhaseData phase, String prefix) {
            Set<BossCastSpot> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (BossAbility ability : BossAbility.ROTATION) {
                BossCastSpot spot = ability.castSpot(phase);
                if (spot == null || !seen.add(spot)) {
                    continue;
                }
                boolean focused = spot == focus;
                if (spot.getMode() == BossCastSpot.MODE_NONE && !focused) {
                    continue;
                }
                Vec3 at = spot.getCoordinateMode() == BossCastSpot.COORDINATE_ABSOLUTE
                        ? new Vec3(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D)
                        : home.add(spot.getX(), spot.getY(), spot.getZ());
                shapes.add(Shape.point(ability.kind(), prefix + name("cnpcgeckoaddon.boss.cast_spots_settings")
                        + " · " + abilityName(ability.kind()), at, focused));
            }
        }

        private void leap(BossLeapSettings leap, String prefix) {
            boolean focused = leap == focus;
            if (!leap.isEnabled() && !focused) {
                return;
            }
            Vec3 at = switch (leap.getMode()) {
                case BossPhaseData.LEAP_MODE_FIXED ->
                        new Vec3(leap.getFixedX() + 0.5D, leap.getFixedY(), leap.getFixedZ() + 0.5D);
                case BossPhaseData.LEAP_MODE_ARENA_OFFSET ->
                        home.add(leap.getOffsetX(), leap.getOffsetY(), leap.getOffsetZ());
                // Straight up or onto the target: nowhere to draw before the fight.
                default -> null;
            };
            if (at != null) {
                shapes.add(Shape.point(BossAbilityKind.LEAP, prefix + abilityName(BossAbilityKind.LEAP), at, focused));
            }
        }

        /**
         * Where a spot kept the summon point's way stands: a fixed one in the middle of its block,
         * an offset one that far from the npc. A rift's fixed spots are in the rift and are only
         * drawn there; its offsets are drawn round the boss, the shape the rift copies onto its
         * platform.
         */
        private Vec3 spot(boolean fixed, int x, int y, int z, boolean rift) {
            if (fixed) {
                return rift && !inRift ? null : new Vec3(x + 0.5D, y, z + 0.5D);
            }
            return home.add(x, y, z);
        }

        /** A box cut to the world's height, or in red uncut when nothing of it is left inside. */
        private void box(int kind, String label, AABB whole, boolean focused) {
            AABB cut = ZoneCoordinates.clampToBuildHeight(whole, level.getMinBuildHeight(), level.getMaxBuildHeight());
            shapes.add(Shape.box(cut == null ? BossZonePreview.KIND_INVALID : kind, label, cut == null ? whole : cut,
                    focused));
        }
    }

    private static String abilityName(int kind) {
        return I18n.get(BossAbilityKind.LABELS[kind]);
    }

    private static String numbered(String key, int index) {
        return name(key) + " #" + (index + 1);
    }

    /** A screen's or a button's name without the dots that say a button opens something. */
    private static String name(String key) {
        String text = I18n.get(key).trim();
        if (text.endsWith("…")) {
            return text.substring(0, text.length() - 1).trim();
        }
        if (text.endsWith("...")) {
            return text.substring(0, text.length() - 3).trim();
        }
        return text;
    }
}
