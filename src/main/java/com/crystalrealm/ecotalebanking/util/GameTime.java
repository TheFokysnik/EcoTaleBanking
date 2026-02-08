package com.crystalrealm.ecotalebanking.util;

/**
 * Provides the configured in-game day duration used for deposit/loan calculations.
 *
 * <p>All deposit terms, loan terms, interest accrual, and overdue checks
 * operate in <b>game days</b>. One game day equals {@link #SECONDS_PER_DAY}
 * real-time seconds (configurable via {@code general.secondsPerGameDay}).</p>
 *
 * <p>Default: 2880 seconds (48 real minutes = 1 game day).</p>
 *
 * @author CrystalRealm
 * @version 1.2.0
 */
public final class GameTime {

    /** Default game day duration: 2880 seconds (48 real minutes). */
    public static final int DEFAULT_SECONDS_PER_DAY = 2880;

    /**
     * Current game day duration in real-time seconds.
     * Set once during plugin initialization via {@link #init(int)}.
     */
    private static volatile int SECONDS_PER_DAY = DEFAULT_SECONDS_PER_DAY;

    private GameTime() {}

    /**
     * Initializes the game day duration from configuration.
     * Must be called once during plugin setup, before any deposit/loan operations.
     *
     * @param secondsPerGameDay real-time seconds per one game day (minimum 60)
     */
    public static void init(int secondsPerGameDay) {
        SECONDS_PER_DAY = Math.max(60, secondsPerGameDay);
    }

    /**
     * @return real-time seconds that constitute one in-game day
     */
    public static int getSecondsPerDay() {
        return SECONDS_PER_DAY;
    }
}
