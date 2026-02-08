package com.crystalrealm.ecotalebanking.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Player's bank account.
 * Contains deposits, loans, and metadata (frozen, created, activity).
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public final class BankAccount {

    private final UUID playerUuid;
    private boolean frozen;
    private String frozenReason;
    private Instant createdAt;
    private Instant lastActivity;
    private String lastKnownName;
    private final List<Deposit> deposits;
    private final List<Loan> loans;

    public BankAccount(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.frozen = false;
        this.frozenReason = null;
        this.createdAt = Instant.now();
        this.lastActivity = Instant.now();
        this.lastKnownName = null;
        this.deposits = new ArrayList<>();
        this.loans = new ArrayList<>();
    }

    // ── Getters ─────────────────────────────────────────────

    public UUID getPlayerUuid() { return playerUuid; }
    public boolean isFrozen() { return frozen; }
    public String getFrozenReason() { return frozenReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActivity() { return lastActivity; }
    public List<Deposit> getDeposits() { return deposits; }
    public List<Loan> getLoans() { return loans; }
    public String getLastKnownName() { return lastKnownName; }
    public void setLastKnownName(String name) { this.lastKnownName = name; }

    // ── Setters ─────────────────────────────────────────────

    public void setFrozen(boolean frozen, String reason) {
        this.frozen = frozen;
        this.frozenReason = reason;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void touchActivity() {
        this.lastActivity = Instant.now();
    }

    // ── Deposit management ──────────────────────────────────

    public void addDeposit(Deposit deposit) {
        deposits.add(deposit);
        touchActivity();
    }

    public Deposit getDepositById(String depositId) {
        return deposits.stream()
                .filter(d -> d.getId().equals(depositId))
                .findFirst().orElse(null);
    }

    /**
     * @return active deposits
     */
    public List<Deposit> getActiveDeposits() {
        return deposits.stream()
                .filter(d -> d.getStatus() == DepositStatus.ACTIVE)
                .toList();
    }

    /**
     * Total amount of active deposits.
     */
    public BigDecimal getTotalDeposited() {
        return getActiveDeposits().stream()
                .map(Deposit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Loan management ─────────────────────────────────────

    public void addLoan(Loan loan) {
        loans.add(loan);
        touchActivity();
    }

    public Loan getLoanById(String loanId) {
        return loans.stream()
                .filter(l -> l.getId().equals(loanId))
                .findFirst().orElse(null);
    }

    /**
     * @return active loans (ACTIVE and OVERDUE)
     */
    public List<Loan> getActiveLoans() {
        return loans.stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE ||
                             l.getStatus() == LoanStatus.OVERDUE)
                .toList();
    }

    /**
     * Total remaining balance of active loans.
     */
    public BigDecimal getTotalDebt() {
        return getActiveLoans().stream()
                .map(Loan::getRemainingBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public String toString() {
        return "BankAccount{uuid=" + playerUuid +
                ", deposits=" + deposits.size() +
                ", loans=" + loans.size() +
                ", frozen=" + frozen + "}";
    }
}
