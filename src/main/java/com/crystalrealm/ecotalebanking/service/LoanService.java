package com.crystalrealm.ecotalebanking.service;

import com.crystalrealm.ecotalebanking.config.BankingConfig;
import com.crystalrealm.ecotalebanking.model.*;
import com.crystalrealm.ecotalebanking.storage.BankStorage;
import com.crystalrealm.ecotalebanking.util.GameTime;
import com.crystalrealm.ecotalebanking.util.PluginLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * Loan (credit) management service.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>Loan issuance with credit score validation</li>
 *   <li>Collateral — percentage of the amount</li>
 *   <li>Interest accrual</li>
 *   <li>Repayment (partial / full)</li>
 *   <li>Automatic transition to OVERDUE/DEFAULTED</li>
 *   <li>Overdue penalty</li>
 * </ul>
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public class LoanService {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final BankStorage storage;
    private final BankingConfig.LoansConfig config;
    private final InflationService inflationService;
    private final CreditRatingService creditService;

    public LoanService(@Nonnull BankStorage storage,
                       @Nonnull BankingConfig.LoansConfig config,
                       @Nonnull InflationService inflationService,
                       @Nonnull CreditRatingService creditService) {
        this.storage = storage;
        this.config = config;
        this.inflationService = inflationService;
        this.creditService = creditService;
    }

    /**
     * Checks whether the player can take a loan of the given amount.
     *
     * @return rejection reason, or null if allowed
     */
    @Nullable
    public String validateLoan(@Nonnull UUID playerUuid, @Nonnull BigDecimal amount) {
        BankAccount account = storage.loadOrCreateAccount(playerUuid);
        CreditScore score = creditService.getScore(playerUuid);

        // Min. credit score
        if (score.getScore() < config.getMinCreditScoreForLoan()) {
            return "credit_too_low";
        }

        // Max. active loans
        if (account.getActiveLoans().size() >= config.getMaxActiveLoans()) {
            return "too_many_loans";
        }

        // Min/max amounts adjusted for credit score
        BigDecimal maxAllowed = BigDecimal.valueOf(config.getMaxAmount())
                .multiply(creditService.getLoanAmountMultiplier(playerUuid))
                .setScale(2, RoundingMode.HALF_UP);

        if (amount.compareTo(BigDecimal.valueOf(config.getMinAmount())) < 0) {
            return "amount_too_low";
        }
        if (amount.compareTo(maxAllowed) > 0) {
            return "amount_too_high";
        }

        // Account age
        long accountAgeDays = (Instant.now().getEpochSecond() - account.getCreatedAt().getEpochSecond()) / GameTime.getSecondsPerDay();
        // Not enforced here to avoid first-play issues, but can be checked

        return null; // OK
    }

    /**
     * Issues a loan to the player.
     *
     * @return Loan or null on error
     */
    @Nullable
    public Loan issueLoan(@Nonnull UUID playerUuid, @Nonnull BigDecimal amount) {
        String validation = validateLoan(playerUuid, amount);
        if (validation != null) return null;

        BankAccount account = storage.loadOrCreateAccount(playerUuid);

        // Ставка: base + credit modifier + inflation
        BigDecimal baseRate = BigDecimal.valueOf(config.getBaseInterestRate());
        BigDecimal creditMod = creditService.getRateModifier(playerUuid);
        BigDecimal effectiveRate = inflationService.adjustLoanRate(baseRate.add(creditMod));
        effectiveRate = effectiveRate.max(BigDecimal.valueOf(0.01)); // minimum 1%

        // Collateral
        BigDecimal collateral = amount.multiply(BigDecimal.valueOf(config.getCollateralRate()))
                .setScale(2, RoundingMode.HALF_UP);

        // Create loan
        String loanId = UUID.randomUUID().toString().substring(0, 8);
        Loan loan = new Loan(loanId, playerUuid, amount, effectiveRate,
                config.getDefaultTermDays(), Instant.now(), collateral);
        // dailyPayment is auto-calculated in Loan constructor via recalculateDailyPayment()

        account.addLoan(loan);
        storage.saveAccount(account);

        // Audit
        storage.addAuditLog(new AuditLog(
                UUID.randomUUID().toString().substring(0, 8),
                playerUuid, TransactionType.LOAN_TAKE, amount,
                loanId + "|" + amount + "|" + config.getDefaultTermDays() + "|" +
                        effectiveRate + "|" + collateral + "|" + loan.getDailyPayment()
        ));

        LOGGER.info("Loan issued: {} to {} — {} coins, rate={}, collateral={}",
                loanId, playerUuid, amount, effectiveRate, collateral);

        return loan;
    }

    /**
     * Repays a loan (partially or fully).
     *
     * @return actual amount deducted, or null on error
     */
    @Nullable
    public BigDecimal repayLoan(@Nonnull UUID playerUuid,
                                @Nonnull String loanId,
                                @Nonnull BigDecimal amount) {
        BankAccount account = storage.loadOrCreateAccount(playerUuid);
        Loan loan = account.getLoanById(loanId);
        if (loan == null) return null;
        if (loan.getStatus() != LoanStatus.ACTIVE && loan.getStatus() != LoanStatus.OVERDUE) {
            return null;
        }

        // Cap payment to remaining balance
        BigDecimal remaining = loan.getRemainingBalance();
        BigDecimal actual = amount.min(remaining);

        loan.setRemainingBalance(remaining.subtract(actual));
        loan.setTotalPaid(loan.getTotalPaid().add(actual));
        loan.setLastPaymentDate(Instant.now());

        // Check full repayment
        if (loan.getRemainingBalance().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(LoanStatus.PAID);
            loan.setRemainingBalance(BigDecimal.ZERO);
            loan.setDailyPayment(BigDecimal.ZERO);
            // Anti-abuse: only award credit bonus if loan was held for minimum days
            if (loan.getElapsedDays() >= config.getMinLoanDaysForCreditBonus()) {
                creditService.onLoanCompleted(playerUuid);
            } else {
                LOGGER.info("Loan {} repaid too quickly ({}d < {}d), no credit bonus",
                        loanId, loan.getElapsedDays(), config.getMinLoanDaysForCreditBonus());
            }
            LOGGER.info("Loan {} fully repaid by {}", loanId, playerUuid);
        } else {
            // Recalculate daily payment for remaining term (takes effect next day)
            loan.recalculateDailyPayment();
            // On-time payment bonus — only if loan held for minimum days
            if (loan.getStatus() == LoanStatus.ACTIVE &&
                loan.getElapsedDays() >= config.getMinLoanDaysForCreditBonus()) {
                creditService.onTimelyPayment(playerUuid);
            }
        }

        storage.saveAccount(account);

        // Audit
        storage.addAuditLog(new AuditLog(
                UUID.randomUUID().toString().substring(0, 8),
                playerUuid, TransactionType.LOAN_REPAY, actual,
                loanId + "|" + actual + "|" + loan.getRemainingBalance()
        ));

        return actual;
    }

    /**
     * Applies a daily auto-payment to a loan (modifies loan in-place, no save).
     * Called by BankService during daily processing.
     *
     * @return the actual amount applied, or ZERO if not applicable
     */
    @Nonnull
    public BigDecimal applyDailyPayment(@Nonnull Loan loan) {
        if (loan.getStatus() != LoanStatus.ACTIVE &&
            loan.getStatus() != LoanStatus.OVERDUE) return BigDecimal.ZERO;

        BigDecimal daily = loan.getDailyPayment();
        if (daily == null || daily.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        BigDecimal remaining = loan.getRemainingBalance();
        BigDecimal actual = daily.min(remaining);

        loan.setRemainingBalance(remaining.subtract(actual));
        loan.setTotalPaid(loan.getTotalPaid().add(actual));
        loan.setLastPaymentDate(Instant.now());

        if (loan.getRemainingBalance().compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus(LoanStatus.PAID);
            loan.setRemainingBalance(BigDecimal.ZERO);
            loan.setDailyPayment(BigDecimal.ZERO);
            // Anti-abuse: only award credit bonus if loan was held for minimum days
            if (loan.getElapsedDays() >= config.getMinLoanDaysForCreditBonus()) {
                creditService.onLoanCompleted(loan.getPlayerUuid());
            }
            LOGGER.info("Loan {} fully repaid via daily payments", loan.getId());
        }

        return actual;
    }

    /**
     * Processes overdue loans.
     * Called by the scheduler daily.
     *
     * @param loan   loan to check
     */
    public void processOverdue(@Nonnull Loan loan) {
        if (loan.getStatus() != LoanStatus.ACTIVE &&
            loan.getStatus() != LoanStatus.OVERDUE) return;

        UUID playerUuid = loan.getPlayerUuid();

        if (loan.isOverdue() && loan.getStatus() == LoanStatus.ACTIVE) {
            // Transition to OVERDUE
            loan.setStatus(LoanStatus.OVERDUE);
            loan.setMissedPayments(loan.getMissedPayments() + 1);
            creditService.onLatePayment(playerUuid);

            storage.addAuditLog(new AuditLog(
                    UUID.randomUUID().toString().substring(0, 8),
                    playerUuid, TransactionType.LOAN_OVERDUE,
                    loan.getRemainingBalance(),
                    loan.getId() + "|" + loan.getRemainingBalance()
            ));

            LOGGER.warn("Loan {} for {} is now OVERDUE", loan.getId(), playerUuid);
        }

        // Overdue penalty
        if (loan.getStatus() == LoanStatus.OVERDUE) {
            BigDecimal penalty = loan.getRemainingBalance()
                    .multiply(BigDecimal.valueOf(config.getOverduePenaltyRate()))
                    .setScale(2, RoundingMode.HALF_UP);
            loan.setRemainingBalance(loan.getRemainingBalance().add(penalty));

            // Check for default (if overdue > defaultAfterDays)
            long overdueDays = -loan.getDaysUntilDue();
            if (overdueDays > config.getDefaultAfterDays()) {
                loan.setStatus(LoanStatus.DEFAULTED);
                creditService.onLoanDefaulted(playerUuid);

                storage.addAuditLog(new AuditLog(
                        UUID.randomUUID().toString().substring(0, 8),
                        playerUuid, TransactionType.LOAN_DEFAULT,
                        loan.getRemainingBalance(),
                        loan.getId() + "|" + loan.getRemainingBalance() + "|" + overdueDays
                ));

                LOGGER.warn("Loan {} for {} has DEFAULTED", loan.getId(), playerUuid);
            }
        }
    }

    /**
     * Accrues daily interest on an active loan.
     *
     * @return accrued amount
     */
    @Nonnull
    public BigDecimal accrueDailyInterest(@Nonnull Loan loan) {
        if (loan.getStatus() != LoanStatus.ACTIVE &&
            loan.getStatus() != LoanStatus.OVERDUE) return BigDecimal.ZERO;

        BigDecimal daily = loan.getDailyInterestAmount().setScale(2, RoundingMode.HALF_UP);
        loan.setRemainingBalance(loan.getRemainingBalance().add(daily));

        return daily;
    }

    /**
     * @return maximum available loan amount for the player
     */
    @Nonnull
    public BigDecimal getMaxLoanAmount(@Nonnull UUID playerUuid) {
        return BigDecimal.valueOf(config.getMaxAmount())
                .multiply(creditService.getLoanAmountMultiplier(playerUuid))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * @return calculated rate for the player
     */
    @Nonnull
    public BigDecimal getEffectiveRate(@Nonnull UUID playerUuid) {
        BigDecimal baseRate = BigDecimal.valueOf(config.getBaseInterestRate());
        BigDecimal creditMod = creditService.getRateModifier(playerUuid);
        BigDecimal rate = inflationService.adjustLoanRate(baseRate.add(creditMod));
        return rate.max(BigDecimal.valueOf(0.01));
    }

    public boolean isEnabled() { return config.isEnabled(); }
    public double getCollateralRate() { return config.getCollateralRate(); }
    public int getDefaultTermDays() { return config.getDefaultTermDays(); }
    public int getMaxActiveLoans() { return config.getMaxActiveLoans(); }
}
