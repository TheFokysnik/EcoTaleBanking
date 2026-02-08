package com.crystalrealm.ecotalebanking.model;

/**
 * Transaction type for auditing and logging.
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public enum TransactionType {

    // ── Deposits ──────────────────────────────────────────
    DEPOSIT_OPEN,
    DEPOSIT_CLOSE,
    DEPOSIT_INTEREST,
    DEPOSIT_EARLY_WITHDRAWAL,

    // ── Loans ─────────────────────────────────────────────
    LOAN_TAKE,
    LOAN_REPAY,
    LOAN_DAILY_PAYMENT,
    LOAN_OVERDUE,
    LOAN_DEFAULT,

    // ── Taxes ────────────────────────────────────────────
    TAX_BALANCE,
    TAX_INTEREST,
    TAX_TRANSACTION,

    // ── Sanctions / penalties ──────────────────────────────
    PENALTY,
    FREEZE,
    UNFREEZE,

    // ── General ─────────────────────────────────────────────
    BANK_WITHDRAWAL,
    BANK_DEPOSIT
}
