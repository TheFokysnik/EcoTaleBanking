package com.crystalrealm.ecotalebanking.gui;

import com.crystalrealm.ecotalebanking.EcoTaleBankingPlugin;
import com.crystalrealm.ecotalebanking.lang.LangManager;
import com.crystalrealm.ecotalebanking.model.*;
import com.crystalrealm.ecotalebanking.service.*;
import com.crystalrealm.ecotalebanking.util.MessageUtil;
import com.crystalrealm.ecotalebanking.util.PluginLogger;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Native interactive bank GUI for the player.
 *
 * <p>Tabs:</p>
 * <ul>
 *   <li><b>Overview</b> — wallet balance, deposits, debt, credit score</li>
 *   <li><b>Deposits</b> — available plans + active deposits with withdraw</li>
 *   <li><b>Loans</b> — take loan buttons + active loans with repay options</li>
 *   <li><b>History</b> — last audit log entries</li>
 * </ul>
 *
 * @author CrystalRealm
 * @version 2.0.0
 */
public final class PlayerBankGui extends InteractiveCustomUIPage<PlayerBankGui.BankEventData> {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private static final String PAGE_PATH = "Pages/CrystalRealm_EcoTaleBanking_PlayerBank.ui";
    private static final int MAX_PLANS    = 3;
    private static final int MAX_DEPOSITS = 3;
    private static final int MAX_LOANS    = 2;
    private static final int MAX_HISTORY  = 15;

    // ── Event data codec ────────────────────────────────────
    private static final String KEY_ACTION = "Action";
    private static final String KEY_ID     = "Id";
    private static final String KEY_PLAN   = "Plan";
    private static final String KEY_AMOUNT = "Amount";

    static final BuilderCodec<BankEventData> CODEC = ReflectiveCodecBuilder
            .<BankEventData>create(BankEventData.class, BankEventData::new)
            .addStringField(KEY_ACTION, (d, v) -> d.action = v, d -> d.action)
            .addStringField(KEY_ID,     (d, v) -> d.id = v,     d -> d.id)
            .addStringField(KEY_PLAN,   (d, v) -> d.plan = v,   d -> d.plan)
            .addStringField(KEY_AMOUNT, (d, v) -> d.amount = v, d -> d.amount)
            .build();

    // ── Instance fields ─────────────────────────────────────
    private final EcoTaleBankingPlugin plugin;
    private final UUID playerUuid;
    private final String selectedTab;
    @Nullable private final String errorMessage;
    @Nullable private final String successMessage;

    // Store ref/store for re-open
    private Ref<EntityStore> savedRef;
    private Store<EntityStore> savedStore;

    public PlayerBankGui(@Nonnull EcoTaleBankingPlugin plugin,
                         @Nonnull PlayerRef playerRef,
                         @Nonnull UUID playerUuid) {
        this(plugin, playerRef, playerUuid, null, null, "overview");
    }

    public PlayerBankGui(@Nonnull EcoTaleBankingPlugin plugin,
                         @Nonnull PlayerRef playerRef,
                         @Nonnull UUID playerUuid,
                         @Nullable String errorMessage,
                         @Nullable String successMessage,
                         @Nonnull String selectedTab) {
        super(playerRef, CustomPageLifetime.CanDismiss, CODEC);
        this.plugin = plugin;
        this.playerUuid = playerUuid;
        this.errorMessage = errorMessage;
        this.successMessage = successMessage;
        this.selectedTab = selectedTab;
    }

    // ════════════════════════════════════════════════════════
    //  BUILD — initial page construction
    // ════════════════════════════════════════════════════════

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        this.savedRef = ref;
        this.savedStore = store;

        LangManager lang = plugin.getLangManager();
        BankService bank = plugin.getBankService();

        MessageUtil.cachePlayerRef(playerUuid, playerRef);

        BankAccount account = bank.getAccount(playerUuid);
        CreditScore credit  = bank.getCreditService().getScore(playerUuid);
        double wallet       = bank.getWalletBalance(playerUuid);

        try {
            String username = playerRef.getUsername();
            if (username != null && !username.isEmpty()) {
                account.setLastKnownName(username);
            }
        } catch (Exception ignored) {}

        // Load root template
        cmd.append(PAGE_PATH);

        // Set title
        cmd.set("#TitleLabel.Text", L(lang, "gui.title"));

        // Tab labels
        cmd.set("#TabOverview.Text", L(lang, "gui.tab.overview"));
        cmd.set("#TabDeposits.Text", L(lang, "gui.tab.deposits"));
        cmd.set("#TabLoans.Text", L(lang, "gui.tab.loans"));
        cmd.set("#TabHistory.Text", L(lang, "gui.tab.history"));

        // Tab visibility
        cmd.set("#OverviewContent.Visible",  "overview".equals(selectedTab));
        cmd.set("#DepositsContent.Visible",  "deposits".equals(selectedTab));
        cmd.set("#LoansContent.Visible",     "loans".equals(selectedTab));
        cmd.set("#HistoryContent.Visible",   "history".equals(selectedTab));

        // ── Bind ALL events (once, slot-based) ──────────────

        // Tab switching
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabOverview",
                new EventData().append(KEY_ACTION, "tab").append(KEY_ID, "overview"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabDeposits",
                new EventData().append(KEY_ACTION, "tab").append(KEY_ID, "deposits"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabLoans",
                new EventData().append(KEY_ACTION, "tab").append(KEY_ID, "loans"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabHistory",
                new EventData().append(KEY_ACTION, "tab").append(KEY_ID, "history"));

        // Deposit plan buttons (plans are static, bind with actual plan data)
        List<DepositPlan> plans = bank.getDepositService().getAvailablePlans();
        for (int i = 0; i < Math.min(plans.size(), MAX_PLANS); i++) {
            DepositPlan p = plans.get(i);
            int n = i + 1;
            String planName = p.getName();
            BigDecimal minAmt = p.getMinAmount();
            BigDecimal maxAmt = p.getMaxAmount();
            BigDecimal midAmt = BigDecimal.valueOf(
                    Math.round(Math.sqrt(minAmt.doubleValue() * maxAmt.doubleValue())));
            if (midAmt.compareTo(minAmt) <= 0) midAmt = minAmt;
            if (midAmt.compareTo(maxAmt) >= 0) midAmt = maxAmt;
            boolean showMid = midAmt.compareTo(minAmt) > 0 && midAmt.compareTo(maxAmt) < 0;

            events.addEventBinding(CustomUIEventBindingType.Activating, "#P" + n + "Btn1",
                    new EventData().append(KEY_ACTION, "deposit")
                            .append(KEY_PLAN, planName)
                            .append(KEY_AMOUNT, minAmt.toPlainString()));
            if (showMid) {
                events.addEventBinding(CustomUIEventBindingType.Activating, "#P" + n + "Btn2",
                        new EventData().append(KEY_ACTION, "deposit")
                                .append(KEY_PLAN, planName)
                                .append(KEY_AMOUNT, midAmt.toPlainString()));
            }
            events.addEventBinding(CustomUIEventBindingType.Activating, "#P" + n + "Btn3",
                    new EventData().append(KEY_ACTION, "deposit")
                            .append(KEY_PLAN, planName)
                            .append(KEY_AMOUNT, maxAmt.toPlainString()));
        }

        // Withdraw buttons (slot-based: resolve deposit at event time)
        for (int n = 1; n <= MAX_DEPOSITS; n++) {
            events.addEventBinding(CustomUIEventBindingType.Activating, "#D" + n + "Btn",
                    new EventData().append(KEY_ACTION, "withdraw")
                            .append(KEY_ID, String.valueOf(n)));
        }

        // Take loan buttons (percentage-based: compute amount at event time)
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TakeBtn1",
                new EventData().append(KEY_ACTION, "loan").append(KEY_AMOUNT, "10"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TakeBtn2",
                new EventData().append(KEY_ACTION, "loan").append(KEY_AMOUNT, "50"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TakeBtn3",
                new EventData().append(KEY_ACTION, "loan").append(KEY_AMOUNT, "100"));

        // Repay buttons (slot + percentage: resolve loan & compute at event time)
        for (int n = 1; n <= MAX_LOANS; n++) {
            String pre = "#L" + n;
            events.addEventBinding(CustomUIEventBindingType.Activating, pre + "Repay10",
                    new EventData().append(KEY_ACTION, "repay")
                            .append(KEY_ID, String.valueOf(n)).append(KEY_AMOUNT, "10"));
            events.addEventBinding(CustomUIEventBindingType.Activating, pre + "Repay25",
                    new EventData().append(KEY_ACTION, "repay")
                            .append(KEY_ID, String.valueOf(n)).append(KEY_AMOUNT, "25"));
            events.addEventBinding(CustomUIEventBindingType.Activating, pre + "Repay50",
                    new EventData().append(KEY_ACTION, "repay")
                            .append(KEY_ID, String.valueOf(n)).append(KEY_AMOUNT, "50"));
            events.addEventBinding(CustomUIEventBindingType.Activating, pre + "RepayAll",
                    new EventData().append(KEY_ACTION, "repay")
                            .append(KEY_ID, String.valueOf(n)).append(KEY_AMOUNT, "100"));
        }

        // ── Banners ─────────────────────────────────────────
        if (errorMessage != null && !errorMessage.isEmpty()) {
            cmd.set("#ErrorBanner.Visible", true);
            cmd.set("#ErrorText.Text", stripForUI(errorMessage));
        }
        if (successMessage != null && !successMessage.isEmpty()) {
            cmd.set("#SuccessBanner.Visible", true);
            cmd.set("#SuccessText.Text", stripForUI(successMessage));
        }

        // ── Build all tab data ──────────────────────────────
        buildOverviewTab(cmd, lang, bank, account, credit, wallet);
        updateDepositsData(cmd, lang, bank, account);
        updateLoansData(cmd, lang, bank, account, credit);
        updateHistoryData(cmd, lang, bank);

        LOGGER.info("Player bank GUI built for {}", playerUuid);
    }

    // ════════════════════════════════════════════════════════
    //  HANDLE EVENTS
    // ════════════════════════════════════════════════════════

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull BankEventData data) {
        LangManager lang = plugin.getLangManager();
        BankService bank = plugin.getBankService();

        switch (data.action) {
            case "tab" -> {
                try {
                    UICommandBuilder tabCmd = new UICommandBuilder();
                    tabCmd.set("#OverviewContent.Visible", "overview".equals(data.id));
                    tabCmd.set("#DepositsContent.Visible", "deposits".equals(data.id));
                    tabCmd.set("#LoansContent.Visible", "loans".equals(data.id));
                    tabCmd.set("#HistoryContent.Visible", "history".equals(data.id));
                    // Clear banners on tab switch
                    tabCmd.set("#ErrorBanner.Visible", false);
                    tabCmd.set("#SuccessBanner.Visible", false);
                    sendUpdate(tabCmd);
                } catch (Exception e) {
                    LOGGER.warn("[tab] sendUpdate failed, falling back to reopen: {}", e.getMessage());
                    reopenOnTab(data.id);
                }
            }

            case "withdraw" -> {
                // Slot-based: data.id = "1", "2", or "3"
                int slot = Integer.parseInt(data.id);
                List<Deposit> deposits = bank.getAccount(playerUuid).getActiveDeposits();
                if (slot < 1 || slot > deposits.size()) return;
                Deposit d = deposits.get(slot - 1);

                BankService.BankResult result = bank.closeDeposit(playerUuid, d.getId());
                if (result.isSuccess()) {
                    plugin.getAbuseGuard().recordGenericOperation(playerUuid);
                    String success = L(lang, "gui.withdraw_success_banner",
                            "id", d.getId(), "amount", result.getDetail());
                    refreshPage(null, success, "deposits");
                } else {
                    refreshPage(L(lang, "gui.error." + result.getMessageKey()), null, "deposits");
                }
            }

            case "deposit" -> {
                // Plan buttons use actual plan data (bound during build)
                BigDecimal amount = new BigDecimal(data.amount);
                BankService.BankResult result = bank.openDeposit(playerUuid, data.plan, amount);
                if (result.isSuccess()) {
                    plugin.getAbuseGuard().recordGenericOperation(playerUuid);
                    String success = L(lang, "gui.deposit_success",
                            "plan", L(lang, "plan." + data.plan),
                            "amount", MessageUtil.formatCoins(amount));
                    refreshPage(null, success, "deposits");
                } else {
                    refreshPage(L(lang, "gui.error." + result.getMessageKey()), null, "deposits");
                }
            }

            case "loan" -> {
                // Percentage-based: data.amount = "10", "50", or "100"
                int pct = Integer.parseInt(data.amount);
                BigDecimal maxLoan = bank.getLoanService().getMaxLoanAmount(playerUuid);
                BigDecimal amount;
                if (pct == 100) {
                    amount = maxLoan;
                } else {
                    amount = maxLoan.multiply(BigDecimal.valueOf(pct / 100.0))
                            .setScale(0, RoundingMode.UP);
                    BigDecimal minLoanAmt = BigDecimal.valueOf(100);
                    if (amount.compareTo(minLoanAmt) < 0) amount = minLoanAmt;
                    if (amount.compareTo(maxLoan) > 0) amount = maxLoan;
                }

                BankService.BankResult result = bank.takeLoan(playerUuid, amount);
                if (result.isSuccess()) {
                    plugin.getAbuseGuard().recordGenericOperation(playerUuid);
                    String success = L(lang, "gui.loan_success",
                            "amount", MessageUtil.formatCoins(amount));
                    refreshPage(null, success, "loans");
                } else {
                    String errKey = result.getMessageKey();
                    String errText;
                    if ("insufficient_collateral".equals(errKey)) {
                        BigDecimal collateral = amount.multiply(
                                BigDecimal.valueOf(bank.getLoanService().getCollateralRate()))
                                .setScale(2, RoundingMode.HALF_UP);
                        errText = L(lang, "gui.error.insufficient_collateral",
                                "amount", MessageUtil.formatCoins(collateral));
                    } else {
                        errText = L(lang, "gui.error." + errKey);
                    }
                    refreshPage(errText, null, "loans");
                }
            }

            case "repay" -> {
                // Slot + percentage: data.id = "1" or "2", data.amount = "10"/"25"/"50"/"100"
                int slot = Integer.parseInt(data.id);
                int pct = Integer.parseInt(data.amount);
                List<Loan> loans = bank.getAccount(playerUuid).getActiveLoans();
                if (slot < 1 || slot > loans.size()) return;
                Loan l = loans.get(slot - 1);

                BigDecimal remaining = l.getRemainingBalance();
                BigDecimal amount;
                if (pct == 100) {
                    amount = remaining;
                } else {
                    amount = remaining.multiply(BigDecimal.valueOf(pct / 100.0))
                            .setScale(0, RoundingMode.UP).max(BigDecimal.ONE);
                }

                BankService.BankResult result = bank.repayLoan(playerUuid, l.getId(), amount);
                if (result.isSuccess()) {
                    plugin.getAbuseGuard().recordGenericOperation(playerUuid);
                    String success = L(lang, "gui.repay_success",
                            "amount", MessageUtil.formatCoins(amount));
                    refreshPage(null, success, "loans");
                } else {
                    refreshPage(L(lang, "gui.error." + result.getMessageKey()), null, "loans");
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  REFRESH PAGE (sendUpdate — no Loading!)
    // ════════════════════════════════════════════════════════

    private void refreshPage(@Nullable String error, @Nullable String success, @Nonnull String tab) {
        try {
            LangManager lang = plugin.getLangManager();
            BankService bank = plugin.getBankService();
            BankAccount account = bank.getAccount(playerUuid);
            CreditScore credit  = bank.getCreditService().getScore(playerUuid);
            double wallet       = bank.getWalletBalance(playerUuid);

            UICommandBuilder cmd = new UICommandBuilder();

            // Banners
            cmd.set("#ErrorBanner.Visible", error != null && !error.isEmpty());
            if (error != null && !error.isEmpty()) cmd.set("#ErrorText.Text", stripForUI(error));
            cmd.set("#SuccessBanner.Visible", success != null && !success.isEmpty());
            if (success != null && !success.isEmpty()) cmd.set("#SuccessText.Text", stripForUI(success));

            // Tab visibility
            cmd.set("#OverviewContent.Visible", "overview".equals(tab));
            cmd.set("#DepositsContent.Visible", "deposits".equals(tab));
            cmd.set("#LoansContent.Visible", "loans".equals(tab));
            cmd.set("#HistoryContent.Visible", "history".equals(tab));

            // Refresh all data
            buildOverviewTab(cmd, lang, bank, account, credit, wallet);
            updateDepositsData(cmd, lang, bank, account);
            updateLoansData(cmd, lang, bank, account, credit);
            updateHistoryData(cmd, lang, bank);

            sendUpdate(cmd);
        } catch (Exception e) {
            LOGGER.warn("[refreshPage] sendUpdate failed, falling back to reopen: {}", e.getMessage());
            reopen(error, success, tab);
        }
    }

    // ════════════════════════════════════════════════════════
    //  TAB DATA (no events — events bound once in build)
    // ════════════════════════════════════════════════════════

    private void buildOverviewTab(UICommandBuilder cmd, LangManager lang,
                                  BankService bank, BankAccount account,
                                  CreditScore credit, double wallet) {
        // Frozen banner
        if (account.isFrozen()) {
            cmd.set("#FrozenBanner.Visible", true);
            cmd.set("#FrozenText.Text", L(lang, "gui.frozen") + ": " + account.getFrozenReason());
        } else {
            cmd.set("#FrozenBanner.Visible", false);
        }

        // Wallet
        cmd.set("#WalletLabel.Text", L(lang, "gui.wallet_label"));
        cmd.set("#WalletAmount.Text", MessageUtil.formatCoins(wallet) + " $");

        // Deposit stats
        cmd.set("#DepositLabel.Text", L(lang, "gui.total_deposited"));
        cmd.set("#DepositAmount.Text", MessageUtil.formatCoins(account.getTotalDeposited()) + " $");
        cmd.set("#DepositCount.Text", L(lang, "gui.active_count") + " " + account.getActiveDeposits().size());

        // Debt stats
        cmd.set("#DebtLabel.Text", L(lang, "gui.total_debt"));
        cmd.set("#DebtAmount.Text", MessageUtil.formatCoins(account.getTotalDebt()) + " $");
        cmd.set("#DebtCount.Text", L(lang, "gui.active_count") + " " + account.getActiveLoans().size());

        // Credit score
        int score = credit.getScore();
        String rating = L(lang, "rating." + credit.getRating().toLowerCase());
        cmd.set("#CreditLabel.Text", L(lang, "gui.credit_score"));
        cmd.set("#CreditValue.Text", score + " / 1000 - " + rating);

        // Loan terms
        BigDecimal maxLoan = bank.getLoanService().getMaxLoanAmount(playerUuid);
        BigDecimal loanRate = bank.getLoanService().getEffectiveRate(playerUuid);
        cmd.set("#MaxLoanLabel.Text", L(lang, "gui.max_loan"));
        cmd.set("#MaxLoanValue.Text", MessageUtil.formatCoins(maxLoan) + " $");
        cmd.set("#LoanRateLabel.Text", L(lang, "gui.your_loan_rate"));
        cmd.set("#LoanRateValue.Text", MessageUtil.formatPercent(loanRate));

        // Inflation
        if (bank.getInflationService().isEnabled()) {
            cmd.set("#InflationBox.Visible", true);
            cmd.set("#InflationLabel.Text", L(lang, "gui.inflation"));
            cmd.set("#InflationValue.Text", MessageUtil.formatPercent(
                    bank.getInflationService().getCurrentRate()));
        } else {
            cmd.set("#InflationBox.Visible", false);
        }
    }

    private void updateDepositsData(UICommandBuilder cmd, LangManager lang,
                                    BankService bank, BankAccount account) {
        cmd.set("#PlansHeader.Text", L(lang, "gui.available_plans"));
        cmd.set("#DepositHint.Text", L(lang, "gui.deposit_hint"));
        cmd.set("#ActiveDepositsHeader.Text", L(lang, "gui.your_deposits"));

        // Available plans
        List<DepositPlan> plans = bank.getDepositService().getAvailablePlans();
        for (int i = 0; i < MAX_PLANS; i++) {
            int n = i + 1;
            if (i < plans.size()) {
                DepositPlan p = plans.get(i);
                String localizedPlan = L(lang, "plan." + p.getName());
                BigDecimal minAmt = p.getMinAmount();
                BigDecimal maxAmt = p.getMaxAmount();
                BigDecimal midAmt = BigDecimal.valueOf(
                        Math.round(Math.sqrt(minAmt.doubleValue() * maxAmt.doubleValue())));
                if (midAmt.compareTo(minAmt) <= 0) midAmt = minAmt;
                if (midAmt.compareTo(maxAmt) >= 0) midAmt = maxAmt;
                boolean showMid = midAmt.compareTo(minAmt) > 0 && midAmt.compareTo(maxAmt) < 0;

                cmd.set("#Plan" + n + ".Visible", true);
                cmd.set("#P" + n + "Name.Text", localizedPlan);
                cmd.set("#P" + n + "Term.Text", p.getTermDays() + " " + L(lang, "gui.days"));
                cmd.set("#P" + n + "Rate.Text", MessageUtil.formatPercent(p.getBaseRate()));
                cmd.set("#P" + n + "Limits.Text", MessageUtil.formatCoins(minAmt) + " - "
                        + MessageUtil.formatCoins(maxAmt) + " $");

                cmd.set("#P" + n + "Btn1.Visible", true);
                cmd.set("#P" + n + "Btn1.Text", shortAmount(minAmt));
                cmd.set("#P" + n + "Btn2.Visible", showMid);
                if (showMid) cmd.set("#P" + n + "Btn2.Text", shortAmount(midAmt));
                cmd.set("#P" + n + "Btn3.Visible", true);
                cmd.set("#P" + n + "Btn3.Text", shortAmount(maxAmt));
            } else {
                cmd.set("#Plan" + n + ".Visible", false);
            }
        }

        // Active deposits
        List<Deposit> deposits = account.getActiveDeposits();
        boolean noDeposits = deposits.isEmpty();
        cmd.set("#NoDepositsMsg.Visible", noDeposits);
        if (noDeposits) cmd.set("#NoDepositsMsg.Text", L(lang, "gui.no_deposits"));

        for (int i = 0; i < MAX_DEPOSITS; i++) {
            int n = i + 1;
            if (i < deposits.size()) {
                Deposit d = deposits.get(i);
                int daysLeft = (int) Math.max(0, d.getTermDays() - d.getElapsedDays());
                boolean matured = d.isMatured();
                String depPlanName = L(lang, "plan." + d.getPlanName());

                cmd.set("#Dep" + n + ".Visible", true);
                cmd.set("#D" + n + "PlanAmt.Text", depPlanName + " - "
                        + MessageUtil.formatCoins(d.getAmount()) + " $");
                cmd.set("#D" + n + "Status.Text", matured
                        ? L(lang, "gui.status.matured")
                        : L(lang, "gui.status.active"));

                String info = L(lang, "gui.rate_label") + ": " + MessageUtil.formatPercent(d.getInterestRate())
                        + " | " + L(lang, "gui.accrued") + ": +" + MessageUtil.formatCoins(d.getAccruedInterest()) + " $"
                        + " | " + L(lang, "gui.remaining") + ": " + daysLeft + L(lang, "gui.days_short");
                cmd.set("#D" + n + "Info.Text", info);

                String btnText = matured ? L(lang, "gui.btn.collect") : L(lang, "gui.btn.withdraw_early");
                cmd.set("#D" + n + "Btn.Text", btnText);
            } else {
                cmd.set("#Dep" + n + ".Visible", false);
            }
        }
    }

    private void updateLoansData(UICommandBuilder cmd, LangManager lang,
                                 BankService bank, BankAccount account, CreditScore credit) {
        cmd.set("#CreditLimitsHeader.Text", L(lang, "gui.credit_limits"));
        cmd.set("#LoanHint.Text", L(lang, "gui.loan_hint"));
        cmd.set("#ActiveLoansHeader.Text", L(lang, "gui.your_loans"));

        // Credit info
        int score = credit.getScore();
        String rating = L(lang, "rating." + credit.getRating().toLowerCase());
        cmd.set("#LoanCreditLabel.Text", L(lang, "gui.credit_score"));
        cmd.set("#LoanCreditValue.Text", score + " - " + rating);

        BigDecimal maxLoan = bank.getLoanService().getMaxLoanAmount(playerUuid);
        BigDecimal effectiveRate = bank.getLoanService().getEffectiveRate(playerUuid);

        cmd.set("#LoanMaxLabel.Text", L(lang, "gui.max_loan"));
        cmd.set("#LoanMaxValue.Text", MessageUtil.formatCoins(maxLoan) + " $");
        cmd.set("#LoanEffRateLabel.Text", L(lang, "gui.your_loan_rate"));
        cmd.set("#LoanEffRateValue.Text", MessageUtil.formatPercent(effectiveRate));
        cmd.set("#LoanActiveLabel.Text", L(lang, "gui.active_loans"));
        cmd.set("#LoanActiveValue.Text", account.getActiveLoans().size() + " / "
                + bank.getLoanService().getMaxActiveLoans());

        // Credit history stats
        cmd.set("#CompletedLabel.Text", "[+] " + L(lang, "gui.loans_completed"));
        cmd.set("#CompletedValue.Text", String.valueOf(credit.getTotalLoansCompleted()));
        cmd.set("#DefaultedLabel.Text", "[x] " + L(lang, "gui.loans_defaulted"));
        cmd.set("#DefaultedValue.Text", String.valueOf(credit.getTotalLoansDefaulted()));
        cmd.set("#OnTimeLabel.Text", "[*] " + L(lang, "gui.on_time_payments"));
        cmd.set("#OnTimeValue.Text", String.valueOf(credit.getOnTimePayments()));

        // Take loan buttons (text only — events bound with percentages in build)
        if (maxLoan.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amt1 = maxLoan.multiply(BigDecimal.valueOf(0.1))
                    .setScale(0, RoundingMode.UP);
            BigDecimal amt2 = maxLoan.multiply(BigDecimal.valueOf(0.5))
                    .setScale(0, RoundingMode.UP);
            BigDecimal amt3 = maxLoan;
            BigDecimal minLoanAmt = BigDecimal.valueOf(100);
            if (amt1.compareTo(minLoanAmt) < 0) amt1 = minLoanAmt;
            if (amt2.compareTo(amt1) <= 0) amt2 = amt1;

            cmd.set("#TakeBtn1.Visible", true);
            cmd.set("#TakeBtn1.Text", shortAmount(amt1) + " $");

            boolean showMid = amt2.compareTo(amt1) > 0 && amt2.compareTo(amt3) < 0;
            cmd.set("#TakeBtn2.Visible", showMid);
            if (showMid) cmd.set("#TakeBtn2.Text", shortAmount(amt2) + " $");

            cmd.set("#TakeBtn3.Visible", true);
            cmd.set("#TakeBtn3.Text", "MAX " + shortAmount(amt3) + " $");
        } else {
            cmd.set("#TakeBtn1.Visible", false);
            cmd.set("#TakeBtn2.Visible", false);
            cmd.set("#TakeBtn3.Visible", false);
        }

        // Active loans
        List<Loan> loans = account.getActiveLoans();
        boolean noLoans = loans.isEmpty();
        cmd.set("#NoLoansMsg.Visible", noLoans);
        if (noLoans) cmd.set("#NoLoansMsg.Text", L(lang, "gui.no_loans"));

        cmd.set("#RepayHint.Visible", !noLoans);
        if (!noLoans) cmd.set("#RepayHint.Text", L(lang, "gui.repay_hint"));

        for (int i = 0; i < MAX_LOANS; i++) {
            int n = i + 1;
            if (i < loans.size()) {
                Loan l = loans.get(i);
                String pre = "#L" + n;

                int daysLeft = (int) l.getDaysUntilDue();
                boolean overdue = l.isOverdue();
                BigDecimal remaining = l.getRemainingBalance();
                BigDecimal daily = l.getDailyPayment() != null ? l.getDailyPayment() : BigDecimal.ZERO;

                int repayPct = l.getPrincipalAmount().compareTo(BigDecimal.ZERO) > 0
                        ? l.getTotalPaid()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(l.getPrincipalAmount(), 0, RoundingMode.HALF_UP)
                            .intValue()
                        : 0;

                cmd.set("#Loan" + n + ".Visible", true);
                cmd.set(pre + "Amount.Text", MessageUtil.formatCoins(l.getPrincipalAmount()) + " $");
                cmd.set(pre + "Status.Text", overdue
                        ? L(lang, "gui.status.overdue")
                        : L(lang, "gui.status.active"));

                String details = L(lang, "gui.remaining_debt") + ": " + MessageUtil.formatCoins(remaining) + " $"
                        + " | " + L(lang, "gui.rate_label") + ": " + MessageUtil.formatPercent(l.getInterestRate())
                        + " | " + L(lang, "gui.due_in") + ": " + Math.max(0, daysLeft) + L(lang, "gui.days_short");
                cmd.set(pre + "Details.Text", details);

                cmd.set(pre + "DailyInfo.Text", L(lang, "gui.daily_payment") + ": "
                        + MessageUtil.formatCoins(daily) + " $/" + L(lang, "gui.days_short"));
                cmd.set(pre + "RepaidPct.Text", L(lang, "gui.repaid") + ": " + Math.min(100, repayPct) + "%");

                // Repay button texts (events bound with slot+percentage in build)
                BigDecimal r10 = remaining.multiply(BigDecimal.valueOf(0.10))
                        .setScale(0, RoundingMode.UP).max(BigDecimal.ONE);
                BigDecimal r25 = remaining.multiply(BigDecimal.valueOf(0.25))
                        .setScale(0, RoundingMode.UP);
                BigDecimal r50 = remaining.multiply(BigDecimal.valueOf(0.50))
                        .setScale(0, RoundingMode.UP);

                cmd.set(pre + "Repay10.Text", "10% (" + shortAmount(r10) + ")");
                cmd.set(pre + "Repay25.Text", "25% (" + shortAmount(r25) + ")");
                cmd.set(pre + "Repay50.Text", "50% (" + shortAmount(r50) + ")");
                cmd.set(pre + "RepayAll.Text", L(lang, "gui.btn.repay_all") + " (" + shortAmount(remaining) + ")");
            } else {
                cmd.set("#Loan" + n + ".Visible", false);
            }
        }
    }

    private void updateHistoryData(UICommandBuilder cmd, LangManager lang, BankService bank) {
        List<AuditLog> logs = bank.getAuditLogs(playerUuid, MAX_HISTORY);

        boolean noHistory = logs.isEmpty();
        cmd.set("#NoHistoryMsg.Visible", noHistory);
        if (noHistory) cmd.set("#NoHistoryMsg.Text", L(lang, "gui.no_history"));

        for (int i = 0; i < MAX_HISTORY; i++) {
            int n = i + 1;
            if (i < logs.size()) {
                AuditLog log = logs.get(i);
                cmd.set("#H" + n + ".Visible", true);
                cmd.set("#H" + n + "T.Text", L(lang, "txtype." + log.getType().name()));
                cmd.set("#H" + n + "A.Text", MessageUtil.formatCoins(log.getAmount()) + " $");
                cmd.set("#H" + n + "D.Text", formatAuditDescription(lang, log));
            } else {
                cmd.set("#H" + n + ".Visible", false);
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  RE-OPEN (with new state)
    // ════════════════════════════════════════════════════════

    private void reopenOnTab(String tab) {
        reopen(null, null, tab);
    }

    private void reopen(@Nullable String error, @Nullable String success, @Nonnull String tab) {
        close();
        PlayerBankGui newPage = new PlayerBankGui(plugin, playerRef, playerUuid, error, success, tab);
        PageOpenHelper.openPage(savedRef, savedStore, newPage);
    }

    // ════════════════════════════════════════════════════════
    //  STATIC OPEN (entry point from commands)
    // ════════════════════════════════════════════════════════

    public static void open(@Nonnull EcoTaleBankingPlugin plugin,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull UUID playerUuid) {
        open(plugin, playerRef, ref, store, playerUuid, null, null, "overview");
    }

    public static void open(@Nonnull EcoTaleBankingPlugin plugin,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull UUID playerUuid,
                            @Nullable String errorMessage,
                            @Nullable String successMessage,
                            @Nonnull String selectedTab) {
        PlayerBankGui page = new PlayerBankGui(plugin, playerRef, playerUuid,
                errorMessage, successMessage, selectedTab);
        PageOpenHelper.openPage(ref, store, page);
    }

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════

    private String L(LangManager lang, String key, String... args) {
        return lang.getForPlayer(playerUuid, key, args);
    }

    /** Strip non-renderable chars (✔) and MiniMessage tags (<red> etc.) for Hytale UI labels. */
    private static String stripForUI(String text) {
        if (text == null) return "";
        return text.replace("\u2714 ", "").replace("\u2714", "")
                   .replaceAll("<[^>]+>", "").trim();
    }

    private static String shortAmount(BigDecimal amount) {
        double val = amount.doubleValue();
        if (val >= 1_000_000) {
            double m = val / 1_000_000.0;
            return m == Math.floor(m) ? String.format("%.0fM", m) : String.format("%.1fM", m);
        }
        if (val >= 1_000) {
            double k = val / 1_000.0;
            return k == Math.floor(k) ? String.format("%.0fK", k) : String.format("%.1fK", k);
        }
        return amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatAuditDescription(LangManager lang, AuditLog log) {
        String raw = log.getDescription();
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.split("\\|");
        String id = parts.length > 0 ? parts[0] : "";
        return switch (log.getType()) {
            case LOAN_TAKE -> parts.length >= 6
                    ? L(lang, "txdesc.loan_take",
                        "id", id, "amount", parts[1], "days", parts[2],
                        "rate", parts[3], "collateral", parts[4], "daily", parts[5])
                    : raw;
            case LOAN_REPAY -> parts.length >= 3
                    ? L(lang, "txdesc.loan_repay",
                        "id", id, "paid", parts[1], "remaining", parts[2])
                    : raw;
            case LOAN_DAILY_PAYMENT -> parts.length >= 3
                    ? L(lang, "txdesc.loan_daily",
                        "id", id, "paid", parts[1], "remaining", parts[2])
                    : raw;
            case LOAN_OVERDUE -> parts.length >= 2
                    ? L(lang, "txdesc.loan_overdue", "id", id, "debt", parts[1])
                    : raw;
            case LOAN_DEFAULT -> parts.length >= 3
                    ? L(lang, "txdesc.loan_default",
                        "id", id, "debt", parts[1], "days", parts[2])
                    : raw;
            default -> raw;
        };
    }

    // ════════════════════════════════════════════════════════
    //  EVENT DATA CLASS
    // ════════════════════════════════════════════════════════

    public static class BankEventData {
        public String action = "";
        public String id = "";
        public String plan = "";
        public String amount = "";
    }
}
