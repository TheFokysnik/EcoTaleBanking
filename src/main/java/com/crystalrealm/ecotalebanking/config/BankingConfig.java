package com.crystalrealm.ecotalebanking.config;

import java.util.List;

/**
 * Корневой конфигурационный POJO для EcoTaleBanking.
 * Десериализуется из JSON через Gson.
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public class BankingConfig {

    private GeneralConfig general = new GeneralConfig();
    private DepositsConfig deposits = new DepositsConfig();
    private LoansConfig loans = new LoansConfig();
    private CreditConfig credit = new CreditConfig();
    private InflationConfig inflation = new InflationConfig();
    private TaxConfig taxes = new TaxConfig();
    private ProtectionConfig protection = new ProtectionConfig();

    public GeneralConfig getGeneral() { return general; }
    public DepositsConfig getDeposits() { return deposits; }
    public LoansConfig getLoans() { return loans; }
    public CreditConfig getCredit() { return credit; }
    public InflationConfig getInflation() { return inflation; }
    public TaxConfig getTaxes() { return taxes; }
    public ProtectionConfig getProtection() { return protection; }

    // ═════════════════════════════════════════════════════════
    //  GENERAL
    // ═════════════════════════════════════════════════════════

    public static class GeneralConfig {
        private String language = "ru";
        private boolean debugMode = false;
        private int autoSaveMinutes = 5;
        private String currencySymbol = "$";
        private int secondsPerGameDay = 2880;

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public boolean isDebugMode() { return debugMode; }
        public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }
        public int getAutoSaveMinutes() { return autoSaveMinutes; }
        public void setAutoSaveMinutes(int autoSaveMinutes) { this.autoSaveMinutes = autoSaveMinutes; }
        public String getCurrencySymbol() { return currencySymbol; }
        public void setCurrencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; }
        public int getSecondsPerGameDay() { return secondsPerGameDay; }
        public void setSecondsPerGameDay(int secondsPerGameDay) { this.secondsPerGameDay = secondsPerGameDay; }
    }

    // ═════════════════════════════════════════════════════════
    //  DEPOSITS
    // ═════════════════════════════════════════════════════════

    public static class DepositsConfig {
        private boolean enabled = true;
        private int maxPerPlayer = 3;
        private double earlyWithdrawalPenaltyRate = 0.0;
        private List<DepositPlanConfig> plans = List.of(
                new DepositPlanConfig("short", 7, 0.03, 100, 10000),
                new DepositPlanConfig("medium", 14, 0.06, 500, 50000),
                new DepositPlanConfig("long", 30, 0.12, 1000, 100000)
        );

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxPerPlayer() { return maxPerPlayer; }
        public void setMaxPerPlayer(int maxPerPlayer) { this.maxPerPlayer = maxPerPlayer; }
        public double getEarlyWithdrawalPenaltyRate() { return earlyWithdrawalPenaltyRate; }
        public void setEarlyWithdrawalPenaltyRate(double r) { this.earlyWithdrawalPenaltyRate = r; }
        public List<DepositPlanConfig> getPlans() { return plans; }
    }

    public static class DepositPlanConfig {
        private String name;
        private int termDays;
        private double baseRate;
        private double minAmount;
        private double maxAmount;

        public DepositPlanConfig() {}

        public DepositPlanConfig(String name, int termDays, double baseRate,
                                 double minAmount, double maxAmount) {
            this.name = name;
            this.termDays = termDays;
            this.baseRate = baseRate;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }

        public String getName() { return name; }
        public int getTermDays() { return termDays; }
        public double getBaseRate() { return baseRate; }
        public double getMinAmount() { return minAmount; }
        public double getMaxAmount() { return maxAmount; }
    }

    // ═════════════════════════════════════════════════════════
    //  LOANS
    // ═════════════════════════════════════════════════════════

    public static class LoansConfig {
        private boolean enabled = true;
        private double baseInterestRate = 0.10;
        private double minAmount = 100;
        private double maxAmount = 50000;
        private int maxActiveLoans = 2;
        private int defaultTermDays = 30;
        private double overduePenaltyRate = 0.02;
        private int defaultAfterDays = 14;
        private double collateralRate = 0.20;
        private int minCreditScoreForLoan = 200;
        private int minLoanDaysForCreditBonus = 3;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getBaseInterestRate() { return baseInterestRate; }
        public void setBaseInterestRate(double r) { this.baseInterestRate = r; }
        public double getMinAmount() { return minAmount; }
        public void setMinAmount(double a) { this.minAmount = a; }
        public double getMaxAmount() { return maxAmount; }
        public void setMaxAmount(double a) { this.maxAmount = a; }
        public int getMaxActiveLoans() { return maxActiveLoans; }
        public void setMaxActiveLoans(int n) { this.maxActiveLoans = n; }
        public int getDefaultTermDays() { return defaultTermDays; }
        public void setDefaultTermDays(int d) { this.defaultTermDays = d; }
        public double getOverduePenaltyRate() { return overduePenaltyRate; }
        public void setOverduePenaltyRate(double r) { this.overduePenaltyRate = r; }
        public int getDefaultAfterDays() { return defaultAfterDays; }
        public void setDefaultAfterDays(int d) { this.defaultAfterDays = d; }
        public double getCollateralRate() { return collateralRate; }
        public void setCollateralRate(double r) { this.collateralRate = r; }
        public int getMinCreditScoreForLoan() { return minCreditScoreForLoan; }
        public void setMinCreditScoreForLoan(int s) { this.minCreditScoreForLoan = s; }
        public int getMinLoanDaysForCreditBonus() { return minLoanDaysForCreditBonus; }
        public void setMinLoanDaysForCreditBonus(int d) { this.minLoanDaysForCreditBonus = d; }
    }

    // ═════════════════════════════════════════════════════════
    //  CREDIT RATING
    // ═════════════════════════════════════════════════════════

    public static class CreditConfig {
        private int initialScore = 500;
        private int loanCompletedBonus = 50;
        private int loanDefaultPenalty = -150;
        private int onTimePaymentBonus = 10;
        private int latePaymentPenalty = -20;
        private int depositCompletedBonus = 15;
        private double vipRateDiscount = 0.02;
        private double excellentRateDiscount = 0.03;
        private double poorRatePenalty = 0.05;

        public int getInitialScore() { return initialScore; }
        public void setInitialScore(int s) { this.initialScore = s; }
        public int getLoanCompletedBonus() { return loanCompletedBonus; }
        public void setLoanCompletedBonus(int b) { this.loanCompletedBonus = b; }
        public int getLoanDefaultPenalty() { return loanDefaultPenalty; }
        public void setLoanDefaultPenalty(int p) { this.loanDefaultPenalty = p; }
        public int getOnTimePaymentBonus() { return onTimePaymentBonus; }
        public void setOnTimePaymentBonus(int b) { this.onTimePaymentBonus = b; }
        public int getLatePaymentPenalty() { return latePaymentPenalty; }
        public void setLatePaymentPenalty(int p) { this.latePaymentPenalty = p; }
        public int getDepositCompletedBonus() { return depositCompletedBonus; }
        public void setDepositCompletedBonus(int b) { this.depositCompletedBonus = b; }
        public double getVipRateDiscount() { return vipRateDiscount; }
        public double getExcellentRateDiscount() { return excellentRateDiscount; }
        public double getPoorRatePenalty() { return poorRatePenalty; }
    }

    // ═════════════════════════════════════════════════════════
    //  INFLATION
    // ═════════════════════════════════════════════════════════

    public static class InflationConfig {
        private boolean enabled = false;
        private double baseInflationRate = 0.02;
        private int updateIntervalHours = 24;
        private double maxInflationRate = 0.20;
        private double minInflationRate = -0.05;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getBaseInflationRate() { return baseInflationRate; }
        public void setBaseInflationRate(double r) { this.baseInflationRate = r; }
        public int getUpdateIntervalHours() { return updateIntervalHours; }
        public void setUpdateIntervalHours(int h) { this.updateIntervalHours = h; }
        public double getMaxInflationRate() { return maxInflationRate; }
        public void setMaxInflationRate(double r) { this.maxInflationRate = r; }
        public double getMinInflationRate() { return minInflationRate; }
        public void setMinInflationRate(double r) { this.minInflationRate = r; }
    }

    // ═════════════════════════════════════════════════════════
    //  TAXES
    // ═════════════════════════════════════════════════════════

    public static class TaxConfig {
        private boolean balanceTaxEnabled = false;
        private double balanceTaxRate = 0.01;
        private double taxFreeThreshold = 1000;
        private boolean interestTaxEnabled = true;
        private double interestTaxRate = 0.13;
        private boolean transactionTaxEnabled = false;
        private double transactionTaxRate = 0.005;
        private List<TaxBracket> progressiveBrackets = List.of(
                new TaxBracket(0, 10000, 0.05),
                new TaxBracket(10000, 50000, 0.10),
                new TaxBracket(50000, 100000, 0.15),
                new TaxBracket(100000, Double.MAX_VALUE, 0.20)
        );

        public boolean isBalanceTaxEnabled() { return balanceTaxEnabled; }
        public double getBalanceTaxRate() { return balanceTaxRate; }
        public double getTaxFreeThreshold() { return taxFreeThreshold; }
        public boolean isInterestTaxEnabled() { return interestTaxEnabled; }
        public double getInterestTaxRate() { return interestTaxRate; }
        public boolean isTransactionTaxEnabled() { return transactionTaxEnabled; }
        public double getTransactionTaxRate() { return transactionTaxRate; }
        public List<TaxBracket> getProgressiveBrackets() { return progressiveBrackets; }
    }

    public static class TaxBracket {
        private double from;
        private double to;
        private double rate;

        public TaxBracket() {}
        public TaxBracket(double from, double to, double rate) {
            this.from = from;
            this.to = to;
            this.rate = rate;
        }

        public double getFrom() { return from; }
        public double getTo() { return to; }
        public double getRate() { return rate; }
    }

    // ═════════════════════════════════════════════════════════
    //  PROTECTION (Anti-abuse)
    // ═════════════════════════════════════════════════════════

    public static class ProtectionConfig {
        private int maxOperationsPerHour = 30;
        private int depositCooldownSeconds = 60;
        private int loanCooldownSeconds = 300;
        private int minAccountAgeDaysForLoan = 1;
        private boolean auditLogEnabled = true;
        private int maxAuditLogEntries = 1000;

        public int getMaxOperationsPerHour() { return maxOperationsPerHour; }
        public void setMaxOperationsPerHour(int n) { this.maxOperationsPerHour = n; }
        public int getDepositCooldownSeconds() { return depositCooldownSeconds; }
        public void setDepositCooldownSeconds(int s) { this.depositCooldownSeconds = s; }
        public int getLoanCooldownSeconds() { return loanCooldownSeconds; }
        public void setLoanCooldownSeconds(int s) { this.loanCooldownSeconds = s; }
        public int getMinAccountAgeDaysForLoan() { return minAccountAgeDaysForLoan; }
        public void setMinAccountAgeDaysForLoan(int d) { this.minAccountAgeDaysForLoan = d; }
        public boolean isAuditLogEnabled() { return auditLogEnabled; }
        public void setAuditLogEnabled(boolean e) { this.auditLogEnabled = e; }
        public int getMaxAuditLogEntries() { return maxAuditLogEntries; }
        public void setMaxAuditLogEntries(int n) { this.maxAuditLogEntries = n; }
    }
}
