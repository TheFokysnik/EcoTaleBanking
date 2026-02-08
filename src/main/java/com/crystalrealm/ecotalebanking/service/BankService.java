package com.crystalrealm.ecotalebanking.service;

import com.crystalrealm.ecotalebanking.model.*;
import com.crystalrealm.ecotalebanking.storage.BankStorage;
import com.crystalrealm.ecotalebanking.util.MessageUtil;
import com.crystalrealm.ecotalebanking.util.PluginLogger;
import com.ecotale.api.EcotaleAPI;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Central facade of the banking system.
 *
 * <p>Coordinates all services and the Ecotale API.
 * All commands operate through this facade.</p>
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public class BankService {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final BankStorage storage;
    private final DepositService depositService;
    private final LoanService loanService;
    private final CreditRatingService creditService;
    private final TaxService taxService;
    private final InflationService inflationService;

    public BankService(@Nonnull BankStorage storage,
                       @Nonnull DepositService depositService,
                       @Nonnull LoanService loanService,
                       @Nonnull CreditRatingService creditService,
                       @Nonnull TaxService taxService,
                       @Nonnull InflationService inflationService) {
        this.storage = storage;
        this.depositService = depositService;
        this.loanService = loanService;
        this.creditService = creditService;
        this.taxService = taxService;
        this.inflationService = inflationService;
    }

    // ═════════════════════════════════════════════════════════
    //  ACCOUNT
    // ═════════════════════════════════════════════════════════

    /**
     * Gets or creates a bank account.
     */
    @Nonnull
    public BankAccount getAccount(@Nonnull UUID playerUuid) {
        return storage.loadOrCreateAccount(playerUuid);
    }

    /**
     * Gets the Ecotale balance (player's wallet).
     */
    public double getWalletBalance(@Nonnull UUID playerUuid) {
        try {
            return EcotaleAPI.getBalance(playerUuid);
        } catch (Exception e) {
            LOGGER.error("Failed to get Ecotale balance for {}: {}", playerUuid, e.getMessage());
            return 0;
        }
    }

    /**
     * Freezes the account (admin / anti-abuse).
     */
    public void freezeAccount(@Nonnull UUID playerUuid, @Nonnull String reason) {
        BankAccount account = getAccount(playerUuid);
        account.setFrozen(true, reason);
        storage.saveAccount(account);

        storage.addAuditLog(new AuditLog(
                UUID.randomUUID().toString().substring(0, 8),
                playerUuid, TransactionType.FREEZE, BigDecimal.ZERO,
                "Account frozen: " + reason
        ));

        LOGGER.warn("Account {} frozen: {}", playerUuid, reason);
    }

    /**
     * Unfreezes the account.
     */
    public void unfreezeAccount(@Nonnull UUID playerUuid) {
        BankAccount account = getAccount(playerUuid);
        account.setFrozen(false, null);
        storage.saveAccount(account);

        storage.addAuditLog(new AuditLog(
                UUID.randomUUID().toString().substring(0, 8),
                playerUuid, TransactionType.UNFREEZE, BigDecimal.ZERO,
                "Account unfrozen"
        ));

        LOGGER.info("Account {} unfrozen", playerUuid);
    }

    // ═════════════════════════════════════════════════════════
    //  DEPOSITS
    // ═════════════════════════════════════════════════════════

    /**
     * Opens a deposit: withdraws from Ecotale → places into deposit.
     *
     * @return operation result
     */
    @Nonnull
    public BankResult openDeposit(@Nonnull UUID playerUuid,
                                  @Nonnull String planName,
                                  @Nonnull BigDecimal amount) {
        BankAccount account = getAccount(playerUuid);
        if (account.isFrozen()) {
            return BankResult.error("account_frozen");
        }

        // Check Ecotale balance
        if (!EcotaleAPI.hasBalance(playerUuid, amount.doubleValue())) {
            return BankResult.error("insufficient_funds");
        }

        // Transaction fee
        BigDecimal txTax = taxService.calculateTransactionTax(amount);
        BigDecimal totalCost = amount.add(txTax);

        if (!EcotaleAPI.hasBalance(playerUuid, totalCost.doubleValue())) {
            return BankResult.error("insufficient_funds_with_tax");
        }

        Deposit deposit = depositService.openDeposit(playerUuid, planName, amount);
        if (deposit == null) {
            return BankResult.error("deposit_failed");
        }

        // Withdraw from Ecotale
        EcotaleAPI.withdraw(playerUuid, totalCost.doubleValue(), "Bank deposit: " + deposit.getId());

        return BankResult.success("deposit_opened", deposit.getId());
    }

    /**
     * Closes a deposit: returns amount + interest − taxes to Ecotale.
     */
    @Nonnull
    public BankResult closeDeposit(@Nonnull UUID playerUuid,
                                   @Nonnull String depositId) {
        BankAccount account = getAccount(playerUuid);
        if (account.isFrozen()) {
            return BankResult.error("account_frozen");
        }

        BigDecimal payout = depositService.closeDeposit(playerUuid, depositId);
        if (payout == null) {
            return BankResult.error("deposit_not_found");
        }

        // Credit to Ecotale
        EcotaleAPI.deposit(playerUuid, payout.doubleValue(), "Deposit closed: " + depositId);

        return BankResult.success("deposit_closed", payout.toPlainString());
    }

    // ═════════════════════════════════════════════════════════
    //  LOANS
    // ═════════════════════════════════════════════════════════

    /**
     * Takes a loan: credits money to Ecotale, deducts collateral.
     */
    @Nonnull
    public BankResult takeLoan(@Nonnull UUID playerUuid,
                               @Nonnull BigDecimal amount) {
        BankAccount account = getAccount(playerUuid);
        if (account.isFrozen()) {
            return BankResult.error("account_frozen");
        }

        // Validation
        String validation = loanService.validateLoan(playerUuid, amount);
        if (validation != null) {
            return BankResult.error(validation);
        }

        // Collateral
        BigDecimal collateral = amount.multiply(BigDecimal.valueOf(loanService.getCollateralRate()))
                .setScale(2, java.math.RoundingMode.HALF_UP);

        if (!EcotaleAPI.hasBalance(playerUuid, collateral.doubleValue())) {
            return BankResult.error("insufficient_collateral");
        }

        Loan loan = loanService.issueLoan(playerUuid, amount);
        if (loan == null) {
            return BankResult.error("loan_failed");
        }

        // Deduct collateral
        EcotaleAPI.withdraw(playerUuid, collateral.doubleValue(), "Loan collateral: " + loan.getId());

        // Credit loan amount
        EcotaleAPI.deposit(playerUuid, amount.doubleValue(), "Loan issued: " + loan.getId());

        return BankResult.success("loan_issued", loan.getId());
    }

    /**
     * Repays a loan: withdraws from Ecotale.
     */
    @Nonnull
    public BankResult repayLoan(@Nonnull UUID playerUuid,
                                @Nonnull String loanId,
                                @Nonnull BigDecimal amount) {
        BankAccount account = getAccount(playerUuid);
        if (account.isFrozen()) {
            return BankResult.error("account_frozen");
        }

        if (!EcotaleAPI.hasBalance(playerUuid, amount.doubleValue())) {
            return BankResult.error("insufficient_funds");
        }

        BigDecimal actual = loanService.repayLoan(playerUuid, loanId, amount);
        if (actual == null) {
            return BankResult.error("loan_not_found");
        }

        // Withdraw from Ecotale
        EcotaleAPI.withdraw(playerUuid, actual.doubleValue(), "Loan repayment: " + loanId);

        // Check whether to return collateral
        Loan loan = account.getLoanById(loanId);
        if (loan != null && loan.getStatus() == LoanStatus.PAID) {
            BigDecimal collateralReturn = loan.getCollateralAmount();
            EcotaleAPI.deposit(playerUuid, collateralReturn.doubleValue(),
                    "Collateral returned: " + loanId);
            return BankResult.success("loan_fully_repaid", actual.toPlainString());
        }

        return BankResult.success("loan_partially_repaid", actual.toPlainString());
    }

    // ═════════════════════════════════════════════════════════
    //  PERIODIC TASKS
    // ═════════════════════════════════════════════════════════

    /**
     * Daily processing: interest, overdue checks, taxes.
     * Called by the scheduler once per game day.
     */
    public void dailyProcessing() {
        LOGGER.info("Running daily bank processing...");

        Collection<BankAccount> accounts = storage.getAllAccounts();
        int depositCount = 0;
        int loanCount = 0;

        for (BankAccount account : accounts) {
            UUID playerUuid = account.getPlayerUuid();

            // --- Deposit interest accrual ---
            for (Deposit deposit : account.getActiveDeposits()) {
                BigDecimal accrued = depositService.accrueDailyInterest(deposit);
                if (accrued.compareTo(BigDecimal.ZERO) > 0) {
                    depositCount++;
                    // Notify player
                    String msg = "<green>[Банк] <gray>Начисление процентов по вкладу <white>"
                            + deposit.getId() + "<gray>: <green>+"
                            + MessageUtil.formatCoins(accrued) + " $";
                    MessageUtil.sendNotification(playerUuid, msg);
                }
            }
            depositService.updateDynamicRates(account);

            // --- Loan processing ---
            loanCount += processAccountLoansInternal(account);

            storage.saveAccount(account);
        }

        LOGGER.info("Daily processing complete. Deposits interest: {}, Loan payments: {}",
                depositCount, loanCount);
    }

    /**
     * Accrues interest on deposits of a specific account.
     */
    public void processAccountDeposits(@Nonnull BankAccount account) {
        for (Deposit deposit : account.getActiveDeposits()) {
            depositService.accrueDailyInterest(deposit);
        }
        depositService.updateDynamicRates(account);
    }

    /**
     * Processes loans of a specific account.
     * Includes interest accrual, auto-payments, and overdue checks.
     */
    public void processAccountLoans(@Nonnull BankAccount account) {
        processAccountLoansInternal(account);
        storage.saveAccount(account);
    }

    /**
     * Internal loan processing with notifications.
     *
     * @return number of payments processed
     */
    private int processAccountLoansInternal(@Nonnull BankAccount account) {
        UUID playerUuid = account.getPlayerUuid();
        int paymentCount = 0;

        for (Loan loan : account.getActiveLoans()) {
            loanService.accrueDailyInterest(loan);

            // Auto-deduct daily payment from wallet
            BigDecimal daily = loan.getDailyPayment();
            if (daily != null && daily.compareTo(BigDecimal.ZERO) > 0) {
                if (EcotaleAPI.hasBalance(playerUuid, daily.doubleValue())) {
                    EcotaleAPI.withdraw(playerUuid, daily.doubleValue(),
                            "Auto daily payment: " + loan.getId());
                    BigDecimal paid = loanService.applyDailyPayment(loan);
                    if (paid.compareTo(BigDecimal.ZERO) > 0) {
                        paymentCount++;
                        storage.addAuditLog(new AuditLog(
                                UUID.randomUUID().toString().substring(0, 8),
                                playerUuid, TransactionType.LOAN_DAILY_PAYMENT, paid,
                                loan.getId() + "|" + paid + "|" + loan.getRemainingBalance()
                        ));
                        // Notify player about auto-payment
                        String msg = "<yellow>[Банк] <gray>Автоплатёж по займу <white>"
                                + loan.getId() + "<gray>: <yellow>"
                                + MessageUtil.formatCoins(paid) + " $"
                                + " <dark_gray>(остаток: "
                                + MessageUtil.formatCoins(loan.getRemainingBalance()) + " $)";
                        MessageUtil.sendNotification(playerUuid, msg);
                    }
                } else {
                    // Missed daily payment
                    loan.setMissedPayments(loan.getMissedPayments() + 1);
                    LOGGER.warn("Player {} missed daily payment {} on loan {}",
                            playerUuid, daily, loan.getId());

                    // Notify player about missed payment
                    String msg = "<red>[Банк] <gray>Не удалось списать платёж по займу <white>"
                            + loan.getId() + "<gray>: <red>"
                            + MessageUtil.formatCoins(daily) + " $"
                            + " <dark_gray>(недостаточно средств)";
                    MessageUtil.sendNotification(playerUuid, msg);
                }
            }

            loanService.processOverdue(loan);
        }

        return paymentCount;
    }

    // ═════════════════════════════════════════════════════════
    //  INFO
    // ═════════════════════════════════════════════════════════

    @Nonnull
    public List<AuditLog> getAuditLogs(@Nonnull UUID playerUuid, int limit) {
        return storage.getAuditLogs(playerUuid, limit);
    }

    @Nonnull public DepositService getDepositService() { return depositService; }
    @Nonnull public LoanService getLoanService() { return loanService; }
    @Nonnull public CreditRatingService getCreditService() { return creditService; }
    @Nonnull public TaxService getTaxService() { return taxService; }
    @Nonnull public InflationService getInflationService() { return inflationService; }

    // ═════════════════════════════════════════════════════════
    //  RESULT
    // ═════════════════════════════════════════════════════════

    /**
     * Bank operation result.
     */
    public static final class BankResult {
        private final boolean success;
        private final String messageKey;
        private final String detail;

        private BankResult(boolean success, String messageKey, String detail) {
            this.success = success;
            this.messageKey = messageKey;
            this.detail = detail;
        }

        public static BankResult success(String key, String detail) {
            return new BankResult(true, key, detail);
        }

        public static BankResult error(String key) {
            return new BankResult(false, key, null);
        }

        public boolean isSuccess() { return success; }
        public String getMessageKey() { return messageKey; }
        public String getDetail() { return detail; }
    }
}
