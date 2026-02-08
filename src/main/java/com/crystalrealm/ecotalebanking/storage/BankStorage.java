package com.crystalrealm.ecotalebanking.storage;

import com.crystalrealm.ecotalebanking.model.AuditLog;
import com.crystalrealm.ecotalebanking.model.BankAccount;
import com.crystalrealm.ecotalebanking.model.CreditScore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Banking system data storage interface.
 * Implementations: {@link JsonBankStorage} (JSON files).
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public interface BankStorage {

    // ── Bank Accounts ──────────────────────────────────────────

    /**
     * Loads an account by UUID or creates a new one.
     */
    @Nonnull
    BankAccount loadOrCreateAccount(@Nonnull UUID playerUuid);

    /**
     * Loads an account (may be null if it does not exist).
     */
    @Nullable
    BankAccount loadAccount(@Nonnull UUID playerUuid);

    /**
     * Saves the account to disk.
     */
    void saveAccount(@Nonnull BankAccount account);

    // ── Credit Scores ──────────────────────────────────────────

    /**
     * Loads the credit score or creates a default one.
     */
    @Nonnull
    CreditScore loadOrCreateCreditScore(@Nonnull UUID playerUuid);

    /**
     * Saves the credit score.
     */
    void saveCreditScore(@Nonnull CreditScore score);

    // ── Audit Log ──────────────────────────────────────────────

    /**
     * Adds an entry to the audit log.
     */
    void addAuditLog(@Nonnull AuditLog entry);

    /**
     * Retrieves the last N audit entries for a player.
     */
    @Nonnull
    List<AuditLog> getAuditLogs(@Nonnull UUID playerUuid, int limit);

    // ── Collections (for admin panel) ─────────────────────────

    /**
     * Returns all loaded accounts.
     */
    @Nonnull
    java.util.Collection<BankAccount> getAllAccounts();

    /**
     * Returns all loaded credit scores.
     */
    @Nonnull
    java.util.Collection<CreditScore> getAllCreditScores();

    // ── Lifecycle ──────────────────────────────────────────────

    /**
     * Saves all cached data to disk.
     */
    void saveAll();

    /**
     * Loads all data. Called on startup.
     */
    void loadAll();
}
