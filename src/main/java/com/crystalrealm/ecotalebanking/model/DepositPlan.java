package com.crystalrealm.ecotalebanking.model;

import java.math.BigDecimal;

/**
 * Deposit plan description.
 * Loaded from configuration. Immutable.
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public final class DepositPlan {

    private final String name;
    private final int termDays;
    private final BigDecimal baseRate;
    private final BigDecimal minAmount;
    private final BigDecimal maxAmount;

    /**
     * @param name       plan name (e.g. "short", "medium", "long")
     * @param termDays   term in days (7, 14, 30)
     * @param baseRate   annual base rate (0.05 = 5%)
     * @param minAmount  minimum deposit amount
     * @param maxAmount  maximum deposit amount
     */
    public DepositPlan(String name, int termDays, BigDecimal baseRate,
                       BigDecimal minAmount, BigDecimal maxAmount) {
        this.name = name;
        this.termDays = termDays;
        this.baseRate = baseRate;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    public String getName() { return name; }
    public int getTermDays() { return termDays; }
    public BigDecimal getBaseRate() { return baseRate; }
    public BigDecimal getMinAmount() { return minAmount; }
    public BigDecimal getMaxAmount() { return maxAmount; }

    @Override
    public String toString() {
        return "DepositPlan{" + name + ", " + termDays + "d, rate=" + baseRate + "}";
    }
}
