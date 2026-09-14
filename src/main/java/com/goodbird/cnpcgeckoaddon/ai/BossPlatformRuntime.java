package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.BossPhaseData;
import com.goodbird.cnpcgeckoaddon.data.BossPlatformSettings;
import com.goodbird.cnpcgeckoaddon.data.BossPlatformZone;
import com.goodbird.cnpcgeckoaddon.data.TeleportPathData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;


/**
 * The platforms: a wind-up that picks which of the builder's boxes go, and a fuse lit under
 * each of them as it lands.
 *
 * <p>Owned by {@link TeleportPathController}. Which platforms burn is settled as the boss
 * commits, so the warning drawn over the wind-up is the box that goes; nothing is hit here -
 * the fuses go to {@link BossPlatformScheduler} the moment the cast lands, because the boss is
 * back on its rotation long before the first platform goes off.</p>
 *
 * <p>The one thing that outlives a cast is the turn a phase taking its platforms in turn has
 * reached, kept by platform id the way a summon's round robin keeps its point, and forgotten
 * when the fight ends. Nothing here is saved.</p>
 */
final class BossPlatformRuntime {

    /** One switched-on platform of the phase being fought, resolved into the world. */
    record Zone(int id, int weight, AABB box) {
    }

    private final TeleportPathController boss;
    private final EntityNPCInterface npc;

    /** The boxes the cast being wound up sets alight, in list order; empty outside a wind-up. */
    private final List<AABB> committed = new ArrayList<>();
    /** The platform a cast taken in turn is winding up on, or 0: the turn only moves once it goes. */
    private int committedTurnId;
    /** The phase that turn belongs to. */
    private int committedPhase = -1;
    /** Phase index -> id of the platform the last cast taken in turn set alight. */
    private final Map<Integer, Integer> turnCursor = new HashMap<>();

    BossPlatformRuntime(TeleportPathController boss, EntityNPCInterface npc) {
        this.boss = boss;
        this.npc = npc;
    }

    boolean tryStart(ServerLevel level, TeleportPathData data, BossPhaseData phase, long gameTime) {
        if (!boss.mayStart(BossAbility.PLATFORM, phase) || gameTime < boss.abilityScheduleAt(BossAbility.PLATFORM)) {
            return false;
        }
        // One set of platforms at a time: a second countdown on top of the first is unreadable,
        // and all but one of them going twice over leaves nowhere to stand. A cast spot's walk
        // refuses the same thing before it sets off.
        if (BossPlatformScheduler.hasPending(npc)) {
            boss.setAbilityScheduleAt(BossAbility.PLATFORM, gameTime + boss.retryLongTicks());
            return false;
        }
        clear();
        BossPlatformSettings platform = phase.platform();
        List<Zone> zones = resolve(level, platform);
        // Nobody on any platform: a fuse lit under empty boxes would spend a whole cooldown on
        // nothing, so the boss looks again shortly instead.
        List<Zone> picked = zones.isEmpty() || !anyoneOn(level, zones) ? List.of() : pick(platform, zones);
        if (picked.isEmpty()) {
            boss.setAbilityScheduleAt(BossAbility.PLATFORM, gameTime + boss.retryTicks());
            return false;
        }
        for (Zone zone : picked) {
            committed.add(zone.box());
        }
        if (platform.getPickMode() == BossPhaseData.PLATFORM_PICK_CYCLE) {
            committedTurnId = picked.getFirst().id();
            committedPhase = boss.currentPhaseIndex();
        }
        boss.beginAction(BossAbility.PLATFORM, platform.getAnimation(), platform.getActionDelayTicks(), gameTime,
                null, data, phase);
        // Only the cooldown is scaled: the wind-up is measured against the animation.
        boss.setAbilityScheduleAt(BossAbility.PLATFORM, gameTime + platform.getActionDelayTicks()
                + boss.rageDown(platform.getCooldownTicks()));
        return true;
    }

    /**
     * Lights a fuse under every platform the wind-up picked.
     *
     * <p>What each of them hits for is taken now, the enrage bonus included, so a builder
     * editing the phase or the enrage running out mid fuse cannot change what the people
     * already jumping off are answering. The fuse itself is left alone by the enrage, the
     * geyser's rule: it is the window to get off, not a number the fight may turn down.</p>
     */
    void perform(ServerLevel level, BossPhaseData phase, long gameTime) {
        List<AABB> boxes = List.copyOf(committed);
        int turn = committedTurnId;
        int turnPhase = committedPhase;
        clear();
        if (boxes.isEmpty()) {
            return;
        }
        if (turn > 0) {
            turnCursor.put(turnPhase, turn);
        }
        BossPlatformSettings platform = phase.platform();
        int damage = boss.rageUp(platform.getDamage());
        int knockback = boss.rageUp(platform.getKnockback());
        int launch = boss.rageUp(platform.getLaunch());
        // One countdown per cast: the first platform speaks for all of them.
        boolean announces = true;
        for (AABB box : boxes) {
            BossPlatformScheduler.schedule(level, npc, box, floorY(box, npc.getY()), announces, platform,
                    damage, knockback, launch, gameTime);
            announces = false;
        }
    }

    /**
     * The outlines of the platforms the cast being wound up sets alight, for its warning.
     *
     * <p>Dotted as closely as the fuse will dot them, and nothing inside: the wind-up shows
     * which boxes were picked, and the fire is the fuse's to show.</p>
     *
     * @param spacing blocks between two points of each outline
     */
    void drawCommitted(ServerLevel level, BossTelegraphPaint paint, double spacing) {
        for (AABB box : committed) {
            BossTelegraphUtil.rectangle(level, box.minX, box.minZ, box.maxX, box.maxZ,
                    floorY(box, npc.getY()), paint, spacing);
        }
    }

    /**
     * Drops the platforms a wind-up picked; a wind-up that was called off sets nothing alight
     * and moves no turn on.
     */
    void clear() {
        committed.clear();
        committedTurnId = 0;
        committedPhase = -1;
    }

    /** Forgets which platform went last, for every ending of a fight. */
    void clearCursor() {
        turnCursor.clear();
    }

    /** Every switched-on platform of this phase that is still inside the world, in list order. */
    private List<Zone> resolve(ServerLevel level, BossPlatformSettings platform) {
        BlockPos home = BlockPos.containing(boss.homeX(), boss.homeY(), boss.homeZ());
        List<Zone> zones = new ArrayList<>();
        for (BossPlatformZone zone : platform.getZones().entries()) {
            if (!zone.isEnabled()) {
                continue;
            }
            AABB box = zoneBox(zone, home, level.getMinBuildHeight(), level.getMaxBuildHeight());
            if (box != null) {
                zones.add(new Zone(zone.getZoneId(), zone.getWeight(), box));
            }
        }
        return zones;
    }

    /** Whether anyone this boss may hit stands on any of the platforms. */
    private boolean anyoneOn(ServerLevel level, List<Zone> zones) {
        for (Zone zone : zones) {
            if (!boss.platformVictims(level, zone.box()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** The platforms one cast sets alight, by the phase's pick rule. */
    private List<Zone> pick(BossPlatformSettings platform, List<Zone> zones) {
        RandomSource random = npc.getRandom();
        switch (platform.getPickMode()) {
            case BossPhaseData.PLATFORM_PICK_TARGET -> {
                LivingEntity target = npc.getTarget();
                Zone held = target == null ? null : holding(zones, Zone::box, target.position());
                return List.of(held != null ? held : weighted(zones, Zone::weight, random));
            }
            case BossPhaseData.PLATFORM_PICK_CYCLE -> {
                List<Integer> listed = new ArrayList<>();
                for (BossPlatformZone zone : platform.getZones().entries()) {
                    listed.add(zone.getZoneId());
                }
                Set<Integer> usable = new HashSet<>();
                for (Zone zone : zones) {
                    usable.add(zone.id());
                }
                int next = nextInTurn(listed, usable, turnCursor.getOrDefault(boss.currentPhaseIndex(), 0));
                for (Zone zone : zones) {
                    if (zone.id() == next) {
                        return List.of(zone);
                    }
                }
                return List.of();
            }
            case BossPhaseData.PLATFORM_PICK_ALL_BUT_ONE -> {
                return allButOne(zones, random);
            }
            default -> {
                return List.of(weighted(zones, Zone::weight, random));
            }
        }
    }

    /**
     * The box one platform covers in the world: its corners in either order and both inclusive,
     * the way the arena hazard's box is read, counted from the block the boss activated on for
     * a platform given as an offset, and cut to this dimension's build height.
     *
     * @return the box, or null when the build height leaves nothing of it
     */
    static AABB zoneBox(BossPlatformZone zone, BlockPos home, int minBuildHeight, int maxBuildHeight) {
        boolean fixed = zone.getCoordinateMode() == BossPlatformZone.COORDINATE_FIXED;
        long baseX = fixed ? 0L : home.getX();
        long baseY = fixed ? 0L : home.getY();
        long baseZ = fixed ? 0L : home.getZ();
        long minY = Math.max(Math.min(zone.getY1(), zone.getY2()) + baseY, minBuildHeight);
        long maxY = Math.min(Math.max(zone.getY1(), zone.getY2()) + baseY, maxBuildHeight - 1L);
        if (minY > maxY) {
            return null;
        }
        long minX = Math.min(zone.getX1(), zone.getX2()) + baseX;
        long minZ = Math.min(zone.getZ1(), zone.getZ2()) + baseZ;
        long maxX = Math.max(zone.getX1(), zone.getX2()) + baseX;
        long maxZ = Math.max(zone.getZ1(), zone.getZ2()) + baseZ;
        // The upper AABB bounds are exclusive, so adding one takes in every block of the far corner.
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    /**
     * The height a platform's outline is drawn at: inside the box, and as near the boss as it
     * gets, the arena hazard's rule, so the floor search under it finds the platform's own floor.
     */
    static double floorY(AABB box, double bossY) {
        return Mth.clamp(bossY, box.minY, box.maxY - 1.0D);
    }

    /** One entry drawn by weight; every weight counts as at least one. */
    static <T> T weighted(List<T> entries, ToIntFunction<T> weight, RandomSource random) {
        int total = 0;
        for (T entry : entries) {
            total += Math.max(1, weight.applyAsInt(entry));
        }
        int roll = random.nextInt(total);
        for (T entry : entries) {
            roll -= Math.max(1, weight.applyAsInt(entry));
            if (roll < 0) {
                return entry;
            }
        }
        return entries.getLast();
    }

    /** The first entry, in list order, whose box holds this spot; null when none does. */
    static <T> T holding(List<T> entries, Function<T, AABB> box, Vec3 spot) {
        for (T entry : entries) {
            if (box.apply(entry).contains(spot)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * In turn: the id of the first usable platform after the one that went last, in list order
     * and on round past the end; from the top of the list when none went yet, or the one that did
     * is no longer on it.
     *
     * <p>Walked over the whole list rather than the usable part of it, so a platform switched off
     * or left under the world keeps its place in the turn and the next one along still follows it.</p>
     *
     * @param lastId the platform that went last, or 0 when none has
     * @return the id, or 0 when no platform on the list is usable
     */
    static int nextInTurn(List<Integer> listed, Set<Integer> usable, int lastId) {
        int size = listed.size();
        int last = listed.indexOf(lastId);
        for (int step = 1; step <= size; step++) {
            int id = listed.get(Math.floorMod(last + step, size));
            if (usable.contains(id)) {
                return id;
            }
        }
        return 0;
    }

    /**
     * Every entry but one drawn at random, which is the one left safe; nothing when there are not
     * two to choose between, since a lone platform with the only safe spot on it burns nothing.
     */
    static <T> List<T> allButOne(List<T> entries, RandomSource random) {
        if (entries.size() < 2) {
            return List.of();
        }
        List<T> burning = new ArrayList<>(entries);
        burning.remove(random.nextInt(burning.size()));
        return List.copyOf(burning);
    }
}
