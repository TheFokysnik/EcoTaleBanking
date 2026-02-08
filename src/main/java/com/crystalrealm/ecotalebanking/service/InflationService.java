package com.crystalrealm.ecotalebanking.service;

import com.crystalrealm.ecotalebanking.config.BankingConfig;
import com.crystalrealm.ecotalebanking.util.PluginLogger;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Inflation service.
 *
 * <p>Models inflation based on a configurable base rate.
 * The current inflation rate affects interest rates
 * of deposits and loans.</p>
 *
 * <p>When inflation is enabled:</p>
 * <ul>
 *   <li>Deposit rates are adjusted upward (inflation compensation)</li>
 *   <li>Loan rates are adjusted upward</li>
 *   <li>Periodic recalculation on schedule</li>
 * </ul>
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public class InflationService {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final BankingConfig.InflationConfig config;
    private BigDecimal currentRate;
    private Instant lastUpdated;

    public InflationService(@Nonnull BankingConfig.InflationConfig config) {
        this.config = config;
        this.currentRate = BigDecimal.valueOf(config.getBaseInflationRate());
        this.lastUpdated = Instant.now();
    }

    /**
     * @return true if the inflation system is enabled
     */
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * @return current inflation rate (0.02 = 2%)
     */
    @Nonnull
    public BigDecimal getCurrentRate() {
        return currentRate;
    }

    /**
     * @return time of the last rate update
     */
    @Nonnull
    public Instant getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Updates the inflation rate.
     * Called by the periodic scheduler.
     *
     * <p>Simple model: random fluctuation ±20% of the base rate,
     * bounded by min/max limits.</p>
     */
    public void updateRate() {
        if (!config.isEnabled()) return;

        BigDecimal base = BigDecimal.valueOf(config.getBaseInflationRate());

        // Small random fluctuation ±20% of the base rate
        double fluctuation = (Math.random() - 0.5) * 0.4; // -20% to +20%
        BigDecimal delta = base.multiply(BigDecimal.valueOf(fluctuation))
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal newRate = currentRate.add(delta).setScale(6, RoundingMode.HALF_UP);

        // Clamp to limits
        BigDecimal min = BigDecimal.valueOf(config.getMinInflationRate());
        BigDecimal max = BigDecimal.valueOf(config.getMaxInflationRate());
        newRate = newRate.max(min).min(max);

        // Smooth mean-reversion to the base rate
        BigDecimal diff = base.subtract(newRate);
        newRate = newRate.add(diff.multiply(BigDecimal.valueOf(0.1)))
                .setScale(6, RoundingMode.HALF_UP);

        BigDecimal oldRate = this.currentRate;
        this.currentRate = newRate;
        this.lastUpdated = Instant.now();

        LOGGER.info("Inflation rate updated: {} → {} (base: {})",
                oldRate.toPlainString(), newRate.toPlainString(), base.toPlainString());
    }

    /**
     * Adjusts the deposit interest rate for inflation.
     * Deposit rate is increased to compensate for inflation.
     *
     * @param baseRate deposit base rate
     * @return adjusted rate
     */
    @Nonnull
    public BigDecimal adjustDepositRate(@Nonnull BigDecimal baseRate) {
        if (!config.isEnabled()) return baseRate;
        // adjustedRate = baseRate + inflationRate * 0.5
        BigDecimal adjustment = currentRate.multiply(BigDecimal.valueOf(0.5))
                .setScale(6, RoundingMode.HALF_UP);
        return baseRate.add(adjustment).max(BigDecimal.ZERO);
    }

    /**
     * Adjusts the loan interest rate for inflation.
     * Loan rate is increased proportionally to inflation.
     *
     * @param baseRate loan base rate
     * @return adjusted rate
     */
    @Nonnull
    public BigDecimal adjustLoanRate(@Nonnull BigDecimal baseRate) {
        if (!config.isEnabled()) return baseRate;
        return baseRate.add(currentRate).max(BigDecimal.ZERO);
    }
}
