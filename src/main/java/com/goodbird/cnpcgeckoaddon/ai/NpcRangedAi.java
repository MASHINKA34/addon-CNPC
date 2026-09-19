package com.goodbird.cnpcgeckoaddon.ai;

import com.goodbird.cnpcgeckoaddon.data.RangedExtraData;
import com.goodbird.cnpcgeckoaddon.mixin.IRangedData;
import com.goodbird.cnpcgeckoaddon.utils.NpcAimLead;
import com.goodbird.cnpcgeckoaddon.utils.ProjectileEntityUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.EntityProjectile;
import noppes.npcs.entity.data.DataRanged;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The questions about one npc's ranged settings that more than one place has to ask, and the
 * one shot's worth of aim that a CustomNPCs projectile is turned onto after it is fired.
 *
 * <p>The three answers here are asked by the goal, by the two goals of CustomNPCs the addon
 * stands in front of, and by the projectile's own arrival in the level. Each one is a couple
 * of lines, and a second copy of any of them is how an npc ends up with the addon's goal and
 * CustomNPCs' both firing at once.</p>
 */
public final class NpcRangedAi {

    /**
     * What one attack was aimed at, kept for as long as it takes the projectile to arrive.
     *
     * @param tick    the tick the attack was made on, so a record left over by a shot that
     *                never spawned anything cannot steer the next one
     * @param aim      the point in the world the shot is meant for, lead and all
     * @param spread   how wide that shot may stray, in degrees
     * @param indirect whether the shot was asked for as a lob, which picks the high arc
     */
    private record Aim(long tick, Vec3 aim, int spread, boolean indirect) {
    }

    /**
     * The aim of the attack each npc is in the middle of. Weak so an npc that goes away takes
     * its entry with it; one entry per npc, overwritten by its next shot.
     */
    private static final Map<EntityNPCInterface, Aim> AIMS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NpcRangedAi() {
    }

    public static RangedExtraData extra(EntityNPCInterface npc) {
        return ((IRangedData) npc.stats.ranged).getRangedExtraData();
    }

    /**
     * Whether the addon runs this npc's ranged fight.
     *
     * <p>A boss is never one: its attacks are its controller's, on its own schedule, and the
     * two firing at once would double every volley. Nor is an npc with nothing to shoot - no
     * entity, no item, no fallback: taking its melee away as well would leave it harmless, so it
     * fights the way it did before the switch, and the shot it cannot make says so in the log.</p>
     */
    public static boolean runsRangedAi(EntityNPCInterface npc) {
        return npc != null && extra(npc).isRangedAddonEnabled()
                && !BossMechanicUtil.replacesVanillaAttacks(npc)
                && ProjectileEntityUtil.canShoot(npc);
    }

    /**
     * Whether the addon's goal stands aside for CustomNPCs' melee: the target is nearer than
     * the window and the npc is set to fight it with its hands.
     *
     * <p>Asked from both sides - the addon's goal gives the movement up, CustomNPCs' takes it
     * back - so that exactly one of them is holding the npc at any distance. Only meaningful
     * for an npc {@link #runsRangedAi} says yes to, which both sides ask first.</p>
     */
    public static boolean yieldsToMelee(EntityNPCInterface npc) {
        RangedExtraData extra = extra(npc);
        if (extra.getTooCloseMode() != RangedExtraData.TOO_CLOSE_MELEE) {
            return false;
        }
        LivingEntity target = npc.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        double min = minRange(npc, extra);
        return npc.distanceToSqr(target) < min * min;
    }

    /**
     * The window's near edge in blocks. One typed past the far edge is read as no near edge
     * at all, rather than as a window nothing can ever be inside.
     */
    public static double minRange(EntityNPCInterface npc, RangedExtraData extra) {
        double min = extra.getEngageMinTenths() / 10.0D;
        return min > maxRange(npc, extra) ? 0.0D : min;
    }

    /** The window's far edge in blocks; nought on the screen means the CustomNPCs range. */
    public static double maxRange(EntityNPCInterface npc, RangedExtraData extra) {
        return extra.getEngageMaxTenths() > 0
                ? extra.getEngageMaxTenths() / 10.0D
                : Math.max(1, npc.stats.ranged.getRange());
    }

    /** Everything the plan reads, taken off this npc once. */
    public static NpcRangedPlan.Settings settings(EntityNPCInterface npc) {
        RangedExtraData extra = extra(npc);
        DataRanged ranged = npc.stats.ranged;
        return new NpcRangedPlan.Settings(minRange(npc, extra), maxRange(npc, extra),
                extra.getTooFarMode(), extra.getTooCloseMode(),
                extra.getBurstShots(), extra.getBurstDelayTicks(), extra.getReloadTicks(),
                extra.getLosMode(), extra.getLobWarnTicks(),
                ranged.getBurst(), ranged.getBurstDelay(), ranged.getFireType());
    }

    /** Where a projectile of this npc leaves from: its eyes, plus whatever the muzzle says. */
    public static Vec3 muzzle(EntityNPCInterface npc, RangedExtraData extra) {
        return new Vec3(npc.getX(), npc.getEyeY() + extra.getMuzzleHeightTenths() / 10.0D, npc.getZ());
    }

    /** The middle of a target, which is what every shot in the addon is aimed at. */
    public static Vec3 centreOf(LivingEntity target) {
        return new Vec3(target.getX(), target.getY(0.5D), target.getZ());
    }

    /** How fast this npc's projectiles fly, in blocks a tick: tenths, the way its editor shows it. */
    public static double projectileSpeed(EntityNPCInterface npc) {
        return Math.max(npc.stats.ranged.getSpeed(), 1) / 10.0D;
    }

    /**
     * Where this attack should be pointed, with the target's own travel taken into account.
     */
    public static Vec3 aimPoint(EntityNPCInterface npc, LivingEntity target, RangedExtraData extra) {
        return NpcAimLead.aimPoint(muzzle(npc, extra), centreOf(target),
                // The last step the target really took: for a player, what its own client
                // reported, which is the only honest answer on the server.
                target.getKnownMovement(), projectileSpeed(npc), extra.getLeadPercent());
    }

    /**
     * Remembers what the attack about to be made by CustomNPCs is aimed at.
     *
     * <p>CustomNPCs builds its own projectile out of the npc's item and aims it at the target's
     * feet, from inside a method the addon only stands at the head of. So the aim is left here
     * and the projectile is turned onto it as it joins the level - which is also where the boss'
     * potions are hung on it, and is the only moment both halves of a shot exist at once.</p>
     */
    public static void expectShot(EntityNPCInterface npc, LivingEntity target, boolean indirect) {
        RangedExtraData extra = extra(npc);
        if (extra.getLeadPercent() <= 0 && extra.getSpreadDegrees() <= 0) {
            // Nothing to turn it onto: the shot goes exactly where it always did.
            AIMS.remove(npc);
            return;
        }
        AIMS.put(npc, new Aim(npc.level().getGameTime(), aimPoint(npc, target, extra),
                extra.getSpreadDegrees(), indirect));
    }

    /**
     * Turns a CustomNPCs projectile that has just been fired onto the aim its npc was given.
     *
     * <p>Only its own npc's, only on the tick it was fired, and only while there is something
     * to change: an npc with no lead and no spread never leaves a record to be found here.</p>
     */
    public static void reaim(Projectile projectile) {
        if (!(projectile instanceof EntityProjectile shot)) {
            // Somebody else's projectile, or one of the addon's own, which was aimed as it was made.
            return;
        }
        Entity owner = shot.getOwner();
        if (!(owner instanceof EntityNPCInterface npc)) {
            return;
        }
        Aim aim = AIMS.get(npc);
        if (aim == null || aim.tick() != npc.level().getGameTime()) {
            return;
        }
        Vec3 from = shot.position();
        Vec3 direction = NpcAimLead.spread(aim.aim().subtract(from), aim.spread(), shot.getRandom());
        if (direction.lengthSqr() < 1.0E-8D) {
            return;
        }
        // Fired again through CustomNPCs' own aiming rather than by setting a velocity: for a
        // projectile with gravity the fourth argument is the launch pitch it works out from the
        // arc, not a speed - the speed is the projectile's own - so an arcing shot keeps its arc,
        // the high one when the shot was asked for as a lob.
        float gravityFactor = shot.hasGravity()
                ? (float) Math.sqrt(direction.x * direction.x + direction.z * direction.z) : 0.0F;
        float angle = shot.getAngleForXYZ(direction.x, direction.y, direction.z,
                gravityFactor, aim.indirect());
        // The accuracy the npc was set to, exactly as CustomNPCs works it out, so re-aiming a
        // shot does not quietly make the npc a better marksman than its own editor says.
        float inaccuracy = 20.0F - Mth.floor(Mth.clamp(npc.stats.ranged.getAccuracy(), 0, 100) / 5.0F);
        shot.shoot(direction.x, direction.y, direction.z, angle, inaccuracy);
    }
}
