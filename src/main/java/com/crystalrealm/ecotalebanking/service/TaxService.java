package com.crystalrealm.ecotalebanking.service;

import com.crystalrealm.ecotalebanking.config.BankingConfig;
import com.crystalrealm.ecotalebanking.util.PluginLogger;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Tax service.
 *
 * <p>Implements three types of taxes:</p>
 * <ul>
 *   <li><b>Balance tax</b> — periodic % on the amount above a threshold</li>
 *   <li><b>Interest tax</b> — % of accrued deposit interest</li>
 *   <li><b>Transaction tax</b> — fee for banking operations</li>
 * </ul>
 *
 * <p>Supports progressive scales (tax brackets).</p>
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public class TaxService {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final BankingConfig.TaxConfig config;

    public TaxService(@Nonnull BankingConfig.TaxConfig config) {
        this.config = config;
    }

    // ═════════════════════════════════════════════════════════
    //  BALANCE TAX
    // ═════════════════════════════════════════════════════════

    /**
     * Calculates the balance tax (wealth tax).
     * Progressive scale is applied to the amount above taxFreeThreshold.
     *
     * @param totalBalance player's total balance (deposits + Ecotale)
     * @return tax amount
     */
    @Nonnull
    public BigDecimal calculateBalanceTax(@Nonnull BigDecimal totalBalance) {
        if (!config.isBalanceTaxEnabled()) return BigDecimal.ZERO;

        BigDecimal threshold = BigDecimal.valueOf(config.getTaxFreeThreshold());
        if (totalBalance.compareTo(threshold) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal taxableAmount = totalBalance.subtract(threshold);
        List<BankingConfig.TaxBracket> brackets = config.getProgressiveBrackets();

        if (brackets == null || brackets.isEmpty()) {
            // Flat rate
            return taxableAmount.multiply(BigDecimal.valueOf(config.getBalanceTaxRate()))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return calculateProgressiveTax(taxableAmount, brackets);
    }

    /**
     * Calculates progressive tax using a bracket scale.
     */
    @Nonnull
    private BigDecimal calculateProgressiveTax(@Nonnull BigDecimal amount,
                                                @Nonnull List<BankingConfig.TaxBracket> brackets) {
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal remaining = amount;

        for (BankingConfig.TaxBracket bracket : brackets) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal from = BigDecimal.valueOf(bracket.getFrom());
            BigDecimal to = BigDecimal.valueOf(bracket.getTo());
            BigDecimal rate = BigDecimal.valueOf(bracket.getRate());

            BigDecimal bracketSize = to.subtract(from);
            BigDecimal taxable = remaining.min(bracketSize);

            BigDecimal tax = taxable.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            totalTax = totalTax.add(tax);
            remaining = remaining.subtract(taxable);
        }

        return totalTax;
    }

    // ═════════════════════════════════════════════════════════
    //  INTEREST TAX
    // ═════════════════════════════════════════════════════════

    /**
     * Calculates the interest tax on deposits.
     *
     * @param interestEarned accrued interest
     * @return tax amount
     */
    @Nonnull
    public BigDecimal calculateInterestTax(@Nonnull BigDecimal interestEarned) {
        if (!config.isInterestTaxEnabled()) return BigDecimal.ZERO;

        return interestEarned.multiply(BigDecimal.valueOf(config.getInterestTaxRate()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ═════════════════════════════════════════════════════════
    //  TRANSACTION TAX
    // ═════════════════════════════════════════════════════════

    /**
     * Calculates the transaction fee.
     *
     * @param transactionAmount transaction amount
     * @return fee
     */
    @Nonnull
    public BigDecimal calculateTransactionTax(@Nonnull BigDecimal transactionAmount) {
        if (!config.isTransactionTaxEnabled()) return BigDecimal.ZERO;

        return transactionAmount.multiply(BigDecimal.valueOf(config.getTransactionTaxRate()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ═════════════════════════════════════════════════════════
    //  QUERIES
    // ═════════════════════════════════════════════════════════

    public boolean isBalanceTaxEnabled() { return config.isBalanceTaxEnabled(); }
    public boolean isInterestTaxEnabled() { return config.isInterestTaxEnabled(); }
    public boolean isTransactionTaxEnabled() { return config.isTransactionTaxEnabled(); }
    public double getInterestTaxRate() { return config.getInterestTaxRate(); }
    public double getTransactionTaxRate() { return config.getTransactionTaxRate(); }
}
