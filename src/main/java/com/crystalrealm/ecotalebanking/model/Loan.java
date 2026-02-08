package com.crystalrealm.ecotalebanking.model;

import com.crystalrealm.ecotalebanking.util.GameTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Player's loan (credit).
 * Tracks the principal amount, accrued interest, payments, and status.
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public final class Loan {

    private final String id;
    private final UUID playerUuid;
    private BigDecimal principalAmount;
    private BigDecimal interestRate;
    private BigDecimal remainingBalance;
    private int termDays;
    private Instant startDate;
    private Instant dueDate;
    private BigDecimal totalPaid;
    private BigDecimal collateralAmount;
    private BigDecimal dailyPayment;
    private LoanStatus status;
    private int missedPayments;
    private Instant lastPaymentDate;

    /**
     * Creates a new loan.
     */
    public Loan(String id, UUID playerUuid, BigDecimal principalAmount,
                BigDecimal interestRate, int termDays, Instant startDate,
                BigDecimal collateralAmount) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.principalAmount = principalAmount;
        this.interestRate = interestRate;
        this.termDays = termDays;
        this.startDate = startDate;
        this.dueDate = startDate.plusSeconds((long) termDays * GameTime.getSecondsPerDay());
        this.remainingBalance = principalAmount;
        this.totalPaid = BigDecimal.ZERO;
        this.collateralAmount = collateralAmount;
        this.dailyPayment = BigDecimal.ZERO;
        this.status = LoanStatus.ACTIVE;
        this.missedPayments = 0;
        this.lastPaymentDate = null;
        recalculateDailyPayment();
    }

    // ── Getters ─────────────────────────────────────────────

    public String getId() { return id; }
    public UUID getPlayerUuid() { return playerUuid; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public BigDecimal getInterestRate() { return interestRate; }
    public BigDecimal getRemainingBalance() { return remainingBalance; }
    public int getTermDays() { return termDays; }
    public Instant getStartDate() { return startDate; }
    public Instant getDueDate() { return dueDate; }
    public BigDecimal getTotalPaid() { return totalPaid; }
    public BigDecimal getCollateralAmount() { return collateralAmount; }
    public LoanStatus getStatus() { return status; }
    public int getMissedPayments() { return missedPayments; }
    public BigDecimal getDailyPayment() { return dailyPayment; }
    public Instant getLastPaymentDate() { return lastPaymentDate; }

    // ── Setters ─────────────────────────────────────────────

    public void setRemainingBalance(BigDecimal remainingBalance) {
        this.remainingBalance = remainingBalance;
    }

    public void setTotalPaid(BigDecimal totalPaid) {
        this.totalPaid = totalPaid;
    }

    public void setStatus(LoanStatus status) {
        this.status = status;
    }

    public void setMissedPayments(int missedPayments) {
        this.missedPayments = missedPayments;
    }

    public void setLastPaymentDate(Instant lastPaymentDate) {
        this.lastPaymentDate = lastPaymentDate;
    }

    public void setDailyPayment(BigDecimal dailyPayment) {
        this.dailyPayment = dailyPayment;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    // ── Computed ─────────────────────────────────────────────

    /**
     * Whether the loan is overdue.
     */
    public boolean isOverdue() {
        return status == LoanStatus.ACTIVE && Instant.now().isAfter(dueDate);
    }

    /**
     * Calculates the daily interest.
     * dailyRate = annualRate / 365
     */
    public BigDecimal getDailyInterestAmount() {
        return remainingBalance.multiply(interestRate)
                .divide(BigDecimal.valueOf(365), 6, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Total accrued debt: principal * (1 + rate * daysElapsed / 365).
     */
    public BigDecimal getTotalOwed() {
        long days = getElapsedDays();
        BigDecimal interest = principalAmount.multiply(interestRate)
                .multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf(365), 6, java.math.RoundingMode.HALF_UP);
        return principalAmount.add(interest).subtract(totalPaid);
    }

    /**
     * Number of elapsed days since the issue date.
     */
    public long getElapsedDays() {
        long seconds = Instant.now().getEpochSecond() - startDate.getEpochSecond();
        return Math.max(0, seconds / GameTime.getSecondsPerDay());
    }

    /**
     * Number of days until the deadline. Negative = overdue.
     */
    public long getDaysUntilDue() {
        long seconds = dueDate.getEpochSecond() - Instant.now().getEpochSecond();
        return seconds / GameTime.getSecondsPerDay();
    }

    /**
     * Recalculates the daily payment based on remaining balance and remaining days.
     * Called when issuing a loan and after early repayment.
     */
    public void recalculateDailyPayment() {
        long daysLeft = Math.max(1, getDaysUntilDue());
        this.dailyPayment = remainingBalance
                .divide(BigDecimal.valueOf(daysLeft), 2, java.math.RoundingMode.CEILING);
    }

    @Override
    public String toString() {
        return "Loan{id=" + id + ", principal=" + principalAmount +
                ", remaining=" + remainingBalance + ", status=" + status + "}";
    }
}
