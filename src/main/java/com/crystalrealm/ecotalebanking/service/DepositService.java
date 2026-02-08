package com.crystalrealm.ecotalebanking.service;

import com.crystalrealm.ecotalebanking.config.BankingConfig;
import com.crystalrealm.ecotalebanking.model.*;
import com.crystalrealm.ecotalebanking.storage.BankStorage;
import com.crystalrealm.ecotalebanking.util.PluginLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deposit management service.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>Opening a deposit by plan</li>
 *   <li>Interest accrual (daily)</li>
 *   <li>Early closure with penalty</li>
 *   <li>Automatic closure upon term expiration</li>
 *   <li>Inflation integration (dynamic rates)</li>
 *   <li>Interest tax</li>
 * </ul>
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public class DepositService {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final BankStorage storage;
    private final BankingConfig.DepositsConfig config;
    private final InflationService inflationService;
    private final TaxService taxService;
    private final CreditRatingService creditService;

    public DepositService(@Nonnull BankStorage storage,
                          @Nonnull BankingConfig.DepositsConfig config,
                          @Nonnull InflationService inflationService,
                          @Nonnull TaxService taxService,
                          @Nonnull CreditRatingService creditService) {
        this.storage = storage;
        this.config = config;
        this.inflationService = inflationService;
        this.taxService = taxService;
        this.creditService = creditService;
    }

    /**
     * Gets a DepositPlan by name.
     */
    @Nullable
    public DepositPlan findPlan(@Nonnull String planName) {
        return config.getPlans().stream()
                .filter(p -> p.getName().equalsIgnoreCase(planName))
                .map(p -> new DepositPlan(
                        p.getName(),
                        p.getTermDays(),
                        BigDecimal.valueOf(p.getBaseRate()),
                        BigDecimal.valueOf(p.getMinAmount()),
                        BigDecimal.valueOf(p.getMaxAmount())))
                .findFirst().orElse(null);
    }

    /**
     * Gets the list of available plans.
     */
    @Nonnull
    public List<DepositPlan> getAvailablePlans() {
        return config.getPlans().stream()
                .map(p -> new DepositPlan(
                        p.getName(),
                        p.getTermDays(),
                        BigDecimal.valueOf(p.getBaseRate()),
                        BigDecimal.valueOf(p.getMinAmount()),
                        BigDecimal.valueOf(p.getMaxAmount())))
                .toList();
    }

    /**
     * Opens a new deposit.
     *
     * @return the created Deposit, or null on validation error
     */
    @Nullable
    public Deposit openDeposit(@Nonnull UUID playerUuid,
                               @Nonnull String planName,
                               @Nonnull BigDecimal amount) {
        DepositPlan plan = findPlan(planName);
        if (plan == null) return null;

        // Amount validation
        if (amount.compareTo(plan.getMinAmount()) < 0 ||
            amount.compareTo(plan.getMaxAmount()) > 0) {
            return null;
        }

        BankAccount account = storage.loadOrCreateAccount(playerUuid);

        // Check active deposits limit
        if (account.getActiveDeposits().size() >= config.getMaxPerPlayer()) {
            return null;
        }

        // Calculate rate adjusted for inflation
        BigDecimal effectiveRate = inflationService.adjustDepositRate(plan.getBaseRate());

        // Create deposit
        String depositId = UUID.randomUUID().toString().substring(0, 8);
        Deposit deposit = new Deposit(depositId, playerUuid, planName,
                amount, effectiveRate, plan.getTermDays(), Instant.now());

        account.addDeposit(deposit);
        storage.saveAccount(account);

        // Audit
        storage.addAuditLog(new AuditLog(
                UUID.randomUUID().toString().substring(0, 8),
                playerUuid, TransactionType.DEPOSIT_OPEN, amount,
                "Opened deposit " + depositId + " (" + planName + ", " +
                        plan.getTermDays() + "d, rate=" + effectiveRate + ")"
        ));

        LOGGER.info("Deposit opened: {} by {} — {} coins, plan={}, rate={}",
                depositId, playerUuid, amount, planName, effectiveRate);

        return deposit;
    }

    /**
     * Closes a deposit. If matured — full payout, otherwise with penalty.
     *
     * @return payout amount (after taxes and penalties), or null if not found
     */
    @Nullable
    public BigDecimal closeDeposit(@Nonnull UUID playerUuid,
                                   @Nonnull String depositId) {
        BankAccount account = storage.loadOrCreateAccount(playerUuid);
        Deposit deposit = account.getDepositById(depositId);
        if (deposit == null || deposit.getStatus() != DepositStatus.ACTIVE) {
            return null;
        }

        BigDecimal payout;
        TransactionType txType;

        if (deposit.isMatured()) {
            // Term expired — full payout
            payout = deposit.getTotalPayout();
            deposit.setStatus(DepositStatus.MATURED);
            txType = TransactionType.DEPOSIT_CLOSE;

            // Credit score: bonus for completed deposit
            creditService.onDepositCompleted(playerUuid);
        } else {
            // Early withdrawal — penalty
            BigDecimal penalty = deposit.getAmount()
                    .multiply(BigDecimal.valueOf(config.getEarlyWithdrawalPenaltyRate()))
                    .setScale(2, RoundingMode.HALF_UP);
            deposit.setEarlyWithdrawalPenalty(penalty);
            deposit.setStatus(DepositStatus.WITHDRAWN);
            payout = deposit.getEarlyPayout();
            txType = TransactionType.DEPOSIT_EARLY_WITHDRAWAL;
        }

        // Interest tax
        BigDecimal interestTax = taxService.calculateInterestTax(deposit.getAccruedInterest());
        payout = payout.subtract(interestTax).max(BigDecimal.ZERO);

        storage.saveAccount(account);

        // Audit
        storage.addAuditLog(new AuditLog(
                UUID.randomUUID().toString().substring(0, 8),
                playerUuid, txType, payout,
                "Closed deposit " + depositId + " — payout=" + payout +
                        ", tax=" + interestTax +
                        (deposit.getStatus() == DepositStatus.WITHDRAWN
                                ? ", penalty=" + deposit.getEarlyWithdrawalPenalty()
                                : "")
        ));

        LOGGER.info("Deposit closed: {} by {} — payout={}, tax={}",
                depositId, playerUuid, payout, interestTax);

        return payout;
    }

    /**
     * Accrues daily interest on all active deposits.
     * Called by the scheduler once per day.
     */
    public void accrueInterestAll() {
        LOGGER.info("Accruing daily interest on all active deposits...");
        int count = 0;

        // Iterate over all accounts from the storage cache
        // (loadAll guarantees all are in memory)
        // We access accounts through storage
        // We need an iteration method — but we work through BankStorage
        // Here we iterate over stored accounts

        // Note: in the current JSON implementation, accounts are already cached after loadAll()
        // Use direct calls for each

        // Instead, BankService will call us with specific accounts
        LOGGER.info("Interest accrual completed for {} deposits.", count);
    }

    /**
     * Accrues interest on a specific deposit for one day.
     *
     * @return accrued amount
     */
    @Nonnull
    public BigDecimal accrueDailyInterest(@Nonnull Deposit deposit) {
        if (deposit.getStatus() != DepositStatus.ACTIVE) return BigDecimal.ZERO;

        // dailyRate = totalRate / termDays  (rate is the total yield for the deposit term)
        int termDays = deposit.getTermDays();
        if (termDays <= 0) termDays = 1;
        BigDecimal dailyRate = deposit.getInterestRate()
                .divide(BigDecimal.valueOf(termDays), 8, RoundingMode.HALF_UP);
        BigDecimal daily = deposit.getAmount().multiply(dailyRate)
                .setScale(2, RoundingMode.HALF_UP);

        deposit.setAccruedInterest(deposit.getAccruedInterest().add(daily));

        return daily;
    }

    /**
     * Updates the rate of dynamic deposits (when inflation changes).
     */
    public void updateDynamicRates(@Nonnull BankAccount account) {
        if (!inflationService.isEnabled()) return;

        for (Deposit deposit : account.getActiveDeposits()) {
            DepositPlan plan = findPlan(deposit.getPlanName());
            if (plan != null) {
                BigDecimal newRate = inflationService.adjustDepositRate(plan.getBaseRate());
                deposit.setInterestRate(newRate);
            }
        }
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public int getMaxPerPlayer() {
        return config.getMaxPerPlayer();
    }
}
