package com.crystalrealm.ecotalebanking.model;

/**
 * Loan status.
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public enum LoanStatus {
    /** Loan is active — player is making payments. */
    ACTIVE,
    /** Loan is fully repaid. */
    PAID,
    /** Overdue — penalty is being charged. */
    OVERDUE,
    /** Defaulted — loan written off, sanctions applied. */
    DEFAULTED
}
