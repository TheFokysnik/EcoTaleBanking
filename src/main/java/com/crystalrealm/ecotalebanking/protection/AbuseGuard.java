package com.crystalrealm.ecotalebanking.protection;

import com.crystalrealm.ecotalebanking.config.BankingConfig;
import com.crystalrealm.ecotalebanking.util.PluginLogger;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Protection against banking system abuse.
 *
 * <p>Implements:</p>
 * <ul>
 *   <li>Rate limiting — max operations per hour</li>
 *   <li>Cooldown — minimum interval between operations</li>
 *   <li>Deposit/loan spam protection</li>
 *   <li>Suspicious activity logging</li>
 * </ul>
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public class AbuseGuard {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final BankingConfig.ProtectionConfig config;

    /** Hourly operations counter: uuid → count */
    private final Map<UUID, AtomicInteger> hourlyOps = new ConcurrentHashMap<>();

    /** Last deposit operation: uuid → timestamp */
    private final Map<UUID, Instant> lastDeposit = new ConcurrentHashMap<>();

    /** Last loan operation: uuid → timestamp */
    private final Map<UUID, Instant> lastLoan = new ConcurrentHashMap<>();

    /** Time of the last hourlyOps reset */
    private Instant lastHourlyReset = Instant.now();

    public AbuseGuard(@Nonnull BankingConfig.ProtectionConfig config) {
        this.config = config;
    }

    // ═════════════════════════════════════════════════════════
    //  RATE LIMITING
    // ═════════════════════════════════════════════════════════

    /**
     * Checks whether the hourly operations limit has been exceeded.
     *
     * @return true if the operation is allowed
     */
    public boolean checkRateLimit(@Nonnull UUID playerUuid) {
        resetHourlyIfNeeded();
        AtomicInteger ops = hourlyOps.computeIfAbsent(playerUuid, k -> new AtomicInteger(0));
        return ops.get() < config.getMaxOperationsPerHour();
    }

    /**
     * Records an operation for rate limiting.
     */
    public void recordOperation(@Nonnull UUID playerUuid) {
        hourlyOps.computeIfAbsent(playerUuid, k -> new AtomicInteger(0)).incrementAndGet();
    }

    // ═════════════════════════════════════════════════════════
    //  COOLDOWNS
    // ═════════════════════════════════════════════════════════

    /**
     * Checks the cooldown for a deposit operation.
     *
     * @return true if the cooldown has expired (operation allowed)
     */
    public boolean checkDepositCooldown(@Nonnull UUID playerUuid) {
        Instant last = lastDeposit.get(playerUuid);
        if (last == null) return true;

        long elapsed = Instant.now().getEpochSecond() - last.getEpochSecond();
        return elapsed >= config.getDepositCooldownSeconds();
    }

    /**
     * @return remaining cooldown time in seconds, or 0
     */
    public long getDepositCooldownRemaining(@Nonnull UUID playerUuid) {
        Instant last = lastDeposit.get(playerUuid);
        if (last == null) return 0;

        long elapsed = Instant.now().getEpochSecond() - last.getEpochSecond();
        long remaining = config.getDepositCooldownSeconds() - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Checks the cooldown for a loan operation.
     */
    public boolean checkLoanCooldown(@Nonnull UUID playerUuid) {
        Instant last = lastLoan.get(playerUuid);
        if (last == null) return true;

        long elapsed = Instant.now().getEpochSecond() - last.getEpochSecond();
        return elapsed >= config.getLoanCooldownSeconds();
    }

    public long getLoanCooldownRemaining(@Nonnull UUID playerUuid) {
        Instant last = lastLoan.get(playerUuid);
        if (last == null) return 0;

        long elapsed = Instant.now().getEpochSecond() - last.getEpochSecond();
        long remaining = config.getLoanCooldownSeconds() - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Records a deposit operation.
     */
    public void recordDeposit(@Nonnull UUID playerUuid) {
        lastDeposit.put(playerUuid, Instant.now());
        recordOperation(playerUuid);
    }

    /**
     * Records a loan operation.
     */
    public void recordLoan(@Nonnull UUID playerUuid) {
        lastLoan.put(playerUuid, Instant.now());
        recordOperation(playerUuid);
    }

    /**
     * Records a generic operation.
     */
    public void recordGenericOperation(@Nonnull UUID playerUuid) {
        recordOperation(playerUuid);
    }

    // ═════════════════════════════════════════════════════════
    //  SUSPICIOUS ACTIVITY
    // ═════════════════════════════════════════════════════════

    /**
     * Logs suspicious activity.
     */
    public void logSuspiciousActivity(@Nonnull UUID playerUuid, @Nonnull String activity) {
        LOGGER.warn("[ABUSE] Suspicious activity by {}: {}", playerUuid, activity);
    }

    // ═════════════════════════════════════════════════════════
    //  CLEANUP
    // ═════════════════════════════════════════════════════════

    /**
     * Resets the hourly counter if an hour has passed.
     */
    private void resetHourlyIfNeeded() {
        long elapsed = Instant.now().getEpochSecond() - lastHourlyReset.getEpochSecond();
        if (elapsed >= 3600) {
            hourlyOps.clear();
            lastHourlyReset = Instant.now();
        }
    }

    /**
     * Full cleanup (on shutdown).
     */
    public void clearAll() {
        hourlyOps.clear();
        lastDeposit.clear();
        lastLoan.clear();
    }
}
