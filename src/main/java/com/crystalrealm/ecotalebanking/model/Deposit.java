package com.crystalrealm.ecotalebanking.model;

import com.crystalrealm.ecotalebanking.util.GameTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Player's bank deposit.
 * Stores all information about the deposited amount, accrued interest,
 * and early withdrawal penalty.
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public final class Deposit {

    private final String id;
    private final UUID playerUuid;
    private final String planName;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private int termDays;
    private Instant startDate;
    private Instant maturityDate;
    private BigDecimal accruedInterest;
    private BigDecimal earlyWithdrawalPenalty;
    private DepositStatus status;

    /**
     * Creates a new deposit.
     */
    public Deposit(String id, UUID playerUuid, String planName,
                   BigDecimal amount, BigDecimal interestRate,
                   int termDays, Instant startDate) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.planName = planName;
        this.amount = amount;
        this.interestRate = interestRate;
        this.termDays = termDays;
        this.startDate = startDate;
        this.maturityDate = startDate.plusSeconds((long) termDays * GameTime.getSecondsPerDay());
        this.accruedInterest = BigDecimal.ZERO;
        this.earlyWithdrawalPenalty = BigDecimal.ZERO;
        this.status = DepositStatus.ACTIVE;
    }

    // ── Getters ─────────────────────────────────────────────

    public String getId() { return id; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlanName() { return planName; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getInterestRate() { return interestRate; }
    public int getTermDays() { return termDays; }
    public Instant getStartDate() { return startDate; }
    public Instant getMaturityDate() { return maturityDate; }
    public BigDecimal getAccruedInterest() { return accruedInterest; }
    public BigDecimal getEarlyWithdrawalPenalty() { return earlyWithdrawalPenalty; }
    public DepositStatus getStatus() { return status; }

    // ── Setters ─────────────────────────────────────────────

    public void setAccruedInterest(BigDecimal accruedInterest) {
        this.accruedInterest = accruedInterest;
    }

    public void setEarlyWithdrawalPenalty(BigDecimal penalty) {
        this.earlyWithdrawalPenalty = penalty;
    }

    public void setStatus(DepositStatus status) {
        this.status = status;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    // ── Computed ─────────────────────────────────────────────

    /**
     * Checks whether the maturity date has been reached.
     */
    public boolean isMatured() {
        return Instant.now().isAfter(maturityDate) || Instant.now().equals(maturityDate);
    }

    /**
     * Total payout amount: deposit + accrued interest.
     */
    public BigDecimal getTotalPayout() {
        return amount.add(accruedInterest);
    }

    /**
     * Early closure amount: deposit + interest - penalty.
     */
    public BigDecimal getEarlyPayout() {
        BigDecimal payout = amount.add(accruedInterest).subtract(earlyWithdrawalPenalty);
        return payout.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : payout;
    }

    /**
     * Number of elapsed days since the opening date.
     */
    public long getElapsedDays() {
        long seconds = Instant.now().getEpochSecond() - startDate.getEpochSecond();
        return Math.max(0, seconds / GameTime.getSecondsPerDay());
    }

    @Override
    public String toString() {
        return "Deposit{id=" + id + ", plan=" + planName +
                ", amount=" + amount + ", status=" + status + "}";
    }
}
