package com.crystalrealm.ecotalebanking.service;

import com.crystalrealm.ecotalebanking.config.BankingConfig;
import com.crystalrealm.ecotalebanking.model.CreditScore;
import com.crystalrealm.ecotalebanking.storage.BankStorage;
import com.crystalrealm.ecotalebanking.util.PluginLogger;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Credit rating service.
 *
 * <p>Score 0–1000, initial 500. Affects:</p>
 * <ul>
 *   <li>Loan interest rate (good score → discount)</li>
 *   <li>Maximum loan amount</li>
 *   <li>Collateral requirements</li>
 * </ul>
 *
 * <h3>Score changes</h3>
 * <ul>
 *   <li>Completed loan: +50</li>
 *   <li>Default: -150</li>
 *   <li>On-time payment: +10</li>
 *   <li>Late payment: -20</li>
 *   <li>Completed deposit: +15</li>
 * </ul>
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public class CreditRatingService {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final BankStorage storage;
    private final BankingConfig.CreditConfig config;

    public CreditRatingService(@Nonnull BankStorage storage,
                               @Nonnull BankingConfig.CreditConfig config) {
        this.storage = storage;
        this.config = config;
    }

    /**
     * Gets the player's current credit score.
     */
    @Nonnull
    public CreditScore getScore(@Nonnull UUID playerUuid) {
        return storage.loadOrCreateCreditScore(playerUuid);
    }

    /**
     * Event: loan fully repaid on time.
     */
    public void onLoanCompleted(@Nonnull UUID playerUuid) {
        CreditScore score = getScore(playerUuid);
        score.adjustScore(config.getLoanCompletedBonus());
        score.incrementLoansCompleted();
        storage.saveCreditScore(score);
        LOGGER.debug("Credit score for {} adjusted +{} (loan completed). Now: {}",
                playerUuid, config.getLoanCompletedBonus(), score.getScore());
    }

    /**
     * Event: loan default.
     */
    public void onLoanDefaulted(@Nonnull UUID playerUuid) {
        CreditScore score = getScore(playerUuid);
        score.adjustScore(config.getLoanDefaultPenalty());
        score.incrementLoansDefaulted();
        storage.saveCreditScore(score);
        LOGGER.debug("Credit score for {} adjusted {} (default). Now: {}",
                playerUuid, config.getLoanDefaultPenalty(), score.getScore());
    }

    /**
     * Event: on-time loan payment.
     */
    public void onTimelyPayment(@Nonnull UUID playerUuid) {
        CreditScore score = getScore(playerUuid);
        score.adjustScore(config.getOnTimePaymentBonus());
        score.incrementOnTimePayments();
        storage.saveCreditScore(score);
    }

    /**
     * Event: late payment.
     */
    public void onLatePayment(@Nonnull UUID playerUuid) {
        CreditScore score = getScore(playerUuid);
        score.adjustScore(config.getLatePaymentPenalty());
        score.incrementLatePayments();
        storage.saveCreditScore(score);
    }

    /**
     * Event: deposit successfully matured.
     */
    public void onDepositCompleted(@Nonnull UUID playerUuid) {
        CreditScore score = getScore(playerUuid);
        score.adjustScore(config.getDepositCompletedBonus());
        score.incrementDepositsCompleted();
        storage.saveCreditScore(score);
    }

    /**
     * Calculates the rate modifier based on credit score.
     *
     * <p>Excellent (800+): discount excellentRateDiscount</p>
     * <p>Good (600+): discount excellentRateDiscount / 2</p>
     * <p>Fair (400+): no change</p>
     * <p>Poor (200+): surcharge poorRatePenalty / 2</p>
     * <p>Bad (<200): surcharge poorRatePenalty</p>
     *
     * @return rate modifier (negative = discount)
     */
    public BigDecimal getRateModifier(@Nonnull UUID playerUuid) {
        CreditScore score = getScore(playerUuid);
        int s = score.getScore();

        if (s >= 800) return BigDecimal.valueOf(-config.getExcellentRateDiscount());
        if (s >= 600) return BigDecimal.valueOf(-config.getExcellentRateDiscount() / 2.0);
        if (s >= 400) return BigDecimal.ZERO;
        if (s >= 200) return BigDecimal.valueOf(config.getPoorRatePenalty() / 2.0);
        return BigDecimal.valueOf(config.getPoorRatePenalty());
    }

    /**
     * Maximum loan amount multiplier based on credit score.
     * Excellent: ×2.0, Good: ×1.5, Fair: ×1.0, Poor: ×0.5, Bad: ×0.25
     */
    public BigDecimal getLoanAmountMultiplier(@Nonnull UUID playerUuid) {
        CreditScore score = getScore(playerUuid);
        int s = score.getScore();

        if (s >= 800) return BigDecimal.valueOf(2.0);
        if (s >= 600) return BigDecimal.valueOf(1.5);
        if (s >= 400) return BigDecimal.ONE;
        if (s >= 200) return BigDecimal.valueOf(0.5);
        return BigDecimal.valueOf(0.25);
    }
}
