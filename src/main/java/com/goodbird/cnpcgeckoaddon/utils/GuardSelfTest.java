package com.goodbird.cnpcgeckoaddon.utils;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The trip wires behind {@code /cnpcgecko selftest}: a way to make one guarded place fail on
 * purpose, so whoever tests a build can see for themselves that the game survives it.
 *
 * <p>The guards cannot be shown working any other way - nothing in a healthy fight throws. A
 * wire is laid inside the guarded body of one real site of each kind, and costs that site one
 * volatile read a call while nothing is armed.</p>
 *
 * <p>Free of the game's classes for the reason {@link CrashGuard} is.</p>
 */
public final class GuardSelfTest {

    /** Inside the geyser scheduler's step on the level tick; its recovery drops the level's geysers. */
    public static final String SCHEDULER = "scheduler";
    /** Inside the addon's handler of incoming damage, on the next hit anybody takes. */
    public static final String DAMAGE = "damage";
    /** Inside the handler of a packet sent to whoever ran the command. */
    public static final String PACKET = "packet";
    /** Inside a client tick of whoever ran the command, armed by a packet. */
    public static final String CLIENT = "client";
    /** Inside the tick of the nearest boss' controller, every tick until that controller gives up. */
    public static final String CONTROLLER = "controller";

    /** In the order the command offers them. */
    public static final List<String> SITES = List.of(SCHEDULER, DAMAGE, PACKET, CLIENT, CONTROLLER);

    private static final Set<String> ARMED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CONTROLLERS = ConcurrentHashMap.newKeySet();

    // Read on every call of every wired site, so the sets above are only looked into once
    // something has actually been armed.
    private static volatile boolean anyArmed;
    private static volatile boolean anyController;

    /** What a wire throws; its own type so a log reader can tell a drill from a defect. */
    public static final class SelfTestFailure extends RuntimeException {
        private SelfTestFailure(String site) {
            super("cnpcgecko selftest: a deliberate failure inside the guarded site '" + site
                    + "' - the game is meant to carry on");
        }
    }

    private GuardSelfTest() {
    }

    /**
     * Whether anything at all is armed: one volatile read, for the sites whose wire is named after
     * what they are running on - a level, a victim - and should only build that name in a drill.
     */
    public static boolean anyArmed() {
        return anyArmed;
    }

    /** Lays a one-shot wire: the next time the site runs, it fails once. */
    public static void arm(String site) {
        ARMED.add(site);
        anyArmed = true;
    }

    /** Whether the site has a wire waiting - for a site that would not otherwise run at all. */
    public static boolean isArmed(String site) {
        return anyArmed && ARMED.contains(site);
    }

    /** Called first thing inside the guarded body. Throws once per {@link #arm}. */
    public static void trip(String site) {
        if (anyArmed && ARMED.remove(site)) {
            anyArmed = !ARMED.isEmpty();
            throw new SelfTestFailure(site);
        }
    }

    /** Fails here and now, for a site that is only ever run for the drill. */
    public static void failNow(String site) {
        throw new SelfTestFailure(site);
    }

    /** Makes one boss' controller fail every tick from now on, until {@link #disarmController}. */
    public static void armController(UUID boss) {
        CONTROLLERS.add(boss);
        anyController = true;
    }

    /** Called first thing inside a controller's guarded tick. */
    public static void tripController(UUID boss) {
        if (anyController && CONTROLLERS.contains(boss)) {
            throw new SelfTestFailure(CONTROLLER);
        }
    }

    /** The drill is over for this boss: its controller was shut down, or the boss is gone. */
    public static void disarmController(UUID boss) {
        if (anyController && CONTROLLERS.remove(boss)) {
            anyController = !CONTROLLERS.isEmpty();
        }
    }

    /** Takes every wire up, fired or not. */
    public static void disarmAll() {
        ARMED.clear();
        CONTROLLERS.clear();
        anyArmed = false;
        anyController = false;
    }
}
