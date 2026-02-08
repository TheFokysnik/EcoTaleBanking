package com.crystalrealm.ecotalebanking.model;

/**
 * Deposit status.
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public enum DepositStatus {
    /** Deposit is active and accruing interest. */
    ACTIVE,
    /** Term expired, interest paid out. */
    MATURED,
    /** Deposit withdrawn early (with penalty). */
    WITHDRAWN
}
