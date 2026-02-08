package com.crystalrealm.ecotalebanking.gui;

import au.ellie.hyui.builders.PageBuilder;

import com.crystalrealm.ecotalebanking.EcoTaleBankingPlugin;
import com.crystalrealm.ecotalebanking.lang.LangManager;
import com.crystalrealm.ecotalebanking.model.*;
import com.crystalrealm.ecotalebanking.service.*;
import com.crystalrealm.ecotalebanking.util.MessageUtil;
import com.crystalrealm.ecotalebanking.util.MiniMessageParser;
import com.crystalrealm.ecotalebanking.util.PluginLogger;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Interactive bank GUI for the player.
 *
 * <p>Tabs:</p>
 * <ul>
 *   <li><b>Overview</b> — wallet balance, deposits, debt, credit score</li>
 *   <li><b>Deposits</b> — available plans with deposit buttons + active deposits with withdraw</li>
 *   <li><b>Loans</b> — take loan buttons + active loans with multiple repay options</li>
 *   <li><b>History</b> — last audit log entries (localized)</li>
 * </ul>
 *
 * @author CrystalRealm
 * @version 1.2.0
 */
public final class PlayerBankGui {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private PlayerBankGui() {}

    /**
     * Build and open the bank GUI for a player.
     */
    public static void open(@Nonnull EcoTaleBankingPlugin plugin,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull UUID playerUuid) {
        open(plugin, playerRef, store, playerUuid, null, null, "overview");
    }

    /**
     * Build and open the bank GUI with an error banner (backwards compat).
     */
    public static void open(@Nonnull EcoTaleBankingPlugin plugin,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull UUID playerUuid,
                            @Nullable String errorMessage) {
        open(plugin, playerRef, store, playerUuid, errorMessage, null, "overview");
    }

    /**
     * Build and open the bank GUI, optionally showing an error or success banner.
     *
     * @param successMessage green success banner text (null to hide)
     * @param errorMessage   red error banner text (null to hide)
     * @param selectedTab    which tab to open on ("overview", "deposits", "loans", "history")
     */
    public static void open(@Nonnull EcoTaleBankingPlugin plugin,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull UUID playerUuid,
                            @Nullable String errorMessage,
                            @Nullable String successMessage,
                            @Nonnull String selectedTab) {

        LangManager lang = plugin.getLangManager();
        BankService bank = plugin.getBankService();

        // Cache PlayerRef for notifications
        MessageUtil.cachePlayerRef(playerUuid, playerRef);

        BankAccount account = bank.getAccount(playerUuid);
        CreditScore credit  = bank.getCreditService().getScore(playerUuid);
        double wallet       = bank.getWalletBalance(playerUuid);

        // Save player name for admin panel display
        try {
            String username = playerRef.getUsername();
            if (username != null && !username.isEmpty()) {
                account.setLastKnownName(username);
            }
        } catch (Exception ignored) {}

        // Button action maps
        Map<String, String> withdrawMap = new LinkedHashMap<>();
        Map<String, String[]> depositActionMap = new LinkedHashMap<>();
        Map<String, BigDecimal> loanActionMap = new LinkedHashMap<>();
        Map<String, String[]> repayActionMap = new LinkedHashMap<>();

        // ── Build HYUIML ──────────────────────────────────────
        StringBuilder html = new StringBuilder();
        html.append(CSS);

        String tabOverview  = esc(L(lang, playerUuid, "gui.tab.overview"));
        String tabDeposits  = esc(L(lang, playerUuid, "gui.tab.deposits"));
        String tabLoans     = esc(L(lang, playerUuid, "gui.tab.loans"));
        String tabHistory   = esc(L(lang, playerUuid, "gui.tab.history"));

        html.append("""
            <div class="page-overlay">
              <div class="decorated-container" data-hyui-title="%s"
                   style="anchor-width: 780; anchor-height: 560;">
                <div class="container-contents" style="layout-mode: Top; padding: 6;">
                  <nav id="bank-tabs" class="tabs"
                       data-tabs="overview:%s:overview-content,deposits:%s:deposits-content,loans:%s:loans-content,history:%s:history-content"
                       data-selected="%s">
                  </nav>
            """.formatted(
                esc(L(lang, playerUuid, "gui.title")),
                tabOverview, tabDeposits, tabLoans, tabHistory,
                esc(selectedTab)
        ));

        // Helper: render banner HTML
        String errorBannerHtml = "";
        if (errorMessage != null && !errorMessage.isEmpty()) {
            errorBannerHtml = """
                <div style="background-color: #8b0000(0.85); padding: 6 12; layout-mode: Left;">
                  <p style="color: #ff4444; font-size: 13; font-weight: bold;">%s</p>
                </div>
                """.formatted(esc(errorMessage));
        }
        String successBannerHtml = "";
        if (successMessage != null && !successMessage.isEmpty()) {
            successBannerHtml = """
                <div style="background-color: #006400(0.85); padding: 6 12; layout-mode: Left;">
                  <p style="color: #55ff55; font-size: 13; font-weight: bold;">%s</p>
                </div>
                """.formatted(esc(successMessage));
        }

        // TAB: Overview
        html.append(tabOpen("overview"));
        if ("overview".equals(selectedTab)) {
            html.append(errorBannerHtml);
            html.append(successBannerHtml);
        }
        html.append(overviewTab(lang, playerUuid, bank, account, credit, wallet));
        html.append(TAB_CLOSE);

        // TAB: Deposits
        html.append(tabOpen("deposits"));
        if ("deposits".equals(selectedTab)) {
            html.append(errorBannerHtml);
            html.append(successBannerHtml);
        }
        html.append(depositsTab(lang, playerUuid, bank, account, withdrawMap, depositActionMap));
        html.append(TAB_CLOSE);

        // TAB: Loans
        html.append(tabOpen("loans"));
        if ("loans".equals(selectedTab)) {
            html.append(errorBannerHtml);
            html.append(successBannerHtml);
        }
        html.append(loansTab(lang, playerUuid, bank, account, credit, loanActionMap, repayActionMap));
        html.append(TAB_CLOSE);

        // TAB: History
        html.append(tabOpen("history"));
        html.append(historyTab(lang, playerUuid, bank));
        html.append(TAB_CLOSE);

        html.append(FOOTER_HTML);

        // ── Create HyUI page ──────────────────────────────────
        PageBuilder builder = PageBuilder.pageForPlayer(playerRef)
                .fromHtml(html.toString())
                .withLifetime(CustomPageLifetime.CanDismiss);

        // Withdraw deposit buttons
        for (var entry : withdrawMap.entrySet()) {
            String btnId    = entry.getKey();
            String depositId = entry.getValue();
            builder.addEventListener(btnId, CustomUIEventBindingType.Activating, (data, ctx) -> {
                BankService.BankResult result = bank.closeDeposit(playerUuid, depositId);
                if (result.isSuccess()) {
                    plugin.getAbuseGuard().recordGenericOperation(playerUuid);
                    String successText = L(lang, playerUuid, "gui.withdraw_success_banner",
                            "id", depositId, "amount", result.getDetail());
                    open(plugin, playerRef, store, playerUuid, null, successText, "deposits");
                } else {
                    String errText = L(lang, playerUuid, "gui.error." + result.getMessageKey());
                    open(plugin, playerRef, store, playerUuid, errText, null, "deposits");
                }
            });
        }

        // Deposit action buttons (open new deposits)
        for (var entry : depositActionMap.entrySet()) {
            String btnId = entry.getKey();
            String planName = entry.getValue()[0];
            BigDecimal amount = new BigDecimal(entry.getValue()[1]);
            builder.addEventListener(btnId, CustomUIEventBindingType.Activating, (data, ctx) -> {
                BankService.BankResult result = bank.openDeposit(playerUuid, planName, amount);
                if (result.isSuccess()) {
                    plugin.getAbuseGuard().recordGenericOperation(playerUuid);
                    String successText = L(lang, playerUuid, "gui.deposit_success",
                            "plan", L(lang, playerUuid, "plan." + planName),
                            "amount", MessageUtil.formatCoins(amount));
                    open(plugin, playerRef, store, playerUuid, null, successText, "deposits");
                } else {
                    String errText = L(lang, playerUuid, "gui.error." + result.getMessageKey());
                    open(plugin, playerRef, store, playerUuid, errText, null, "deposits");
                }
            });
        }

        // Take loan buttons
        for (var entry : loanActionMap.entrySet()) {
            String btnId = entry.getKey();
            BigDecimal amount = entry.getValue();
            builder.addEventListener(btnId, CustomUIEventBindingType.Activating, (data, ctx) -> {
                BankService.BankResult result = bank.takeLoan(playerUuid, amount);
                if (result.isSuccess()) {
                    plugin.getAbuseGuard().recordGenericOperation(playerUuid);
                    String successText = L(lang, playerUuid, "gui.loan_success",
                            "amount", MessageUtil.formatCoins(amount));
                    open(plugin, playerRef, store, playerUuid, null, successText, "loans");
                } else {
                    // Show error in GUI with collateral info for insufficient_collateral
                    String errKey = result.getMessageKey();
                    String errText;
                    if ("insufficient_collateral".equals(errKey)) {
                        BigDecimal collateral = amount.multiply(
                                BigDecimal.valueOf(bank.getLoanService().getCollateralRate()))
                                .setScale(2, RoundingMode.HALF_UP);
                        errText = L(lang, playerUuid, "gui.error.insufficient_collateral",
                                "amount", MessageUtil.formatCoins(collateral));
                    } else {
                        errText = L(lang, playerUuid, "gui.error." + errKey);
                    }
                    open(plugin, playerRef, store, playerUuid, errText, null, "loans");
                }
            });
        }

        // Repay loan buttons
        for (var entry : repayActionMap.entrySet()) {
            String btnId = entry.getKey();
            String loanId = entry.getValue()[0];
            BigDecimal amount = new BigDecimal(entry.getValue()[1]);
            builder.addEventListener(btnId, CustomUIEventBindingType.Activating, (data, ctx) -> {
                BankService.BankResult result = bank.repayLoan(playerUuid, loanId, amount);
                if (result.isSuccess()) {
                    plugin.getAbuseGuard().recordGenericOperation(playerUuid);
                    String successText = L(lang, playerUuid, "gui.repay_success",
                            "amount", MessageUtil.formatCoins(amount));
                    open(plugin, playerRef, store, playerUuid, null, successText, "loans");
                } else {
                    String errText = L(lang, playerUuid, "gui.error." + result.getMessageKey());
                    open(plugin, playerRef, store, playerUuid, errText, null, "loans");
                }
            });
        }

        builder.open(store);
        LOGGER.info("Player bank GUI opened for {}", playerUuid);
    }

    // ════════════════════════════════════════════════════════
    //  TAB BUILDERS
    // ════════════════════════════════════════════════════════

    /** Overview tab: wallet, deposits summary, debt, credit score, inflation. */
    private static String overviewTab(LangManager lang, UUID uuid,
                                      BankService bank, BankAccount account,
                                      CreditScore credit, double wallet) {
        StringBuilder sb = new StringBuilder();

        // Account frozen banner
        if (account.isFrozen()) {
            sb.append("""
                <div style="background-color: #8b0000(0.7); padding: 8; layout-mode: Left;">
                  <p style="color: #ff4444; font-size: 14; font-weight: bold;">%s: %s</p>
                </div>
                """.formatted(
                    esc(L(lang, uuid, "gui.frozen")),
                    esc(account.getFrozenReason())
            ));
        }

        // Wallet balance (big)
        sb.append("""
            <div style="padding: 8 12; layout-mode: Top;">
              <p style="color: #888888; font-size: 12;">%s</p>
              <p style="color: #55ff55; font-size: 28; font-weight: bold;">%s $</p>
            </div>
            """.formatted(
                esc(L(lang, uuid, "gui.wallet_label")),
                esc(MessageUtil.formatCoins(wallet))
        ));

        // Stats grid
        String deposited = MessageUtil.formatCoins(account.getTotalDeposited());
        String debt = MessageUtil.formatCoins(account.getTotalDebt());
        int activeDeposits = account.getActiveDeposits().size();
        int activeLoans = account.getActiveLoans().size();

        sb.append("""
            <div style="padding: 4 12; layout-mode: Left;">
              <div style="flex-weight: 1; padding: 6; background-color: #1a1a2e(0.8); layout-mode: Top;">
                <p style="color: #888888; font-size: 11;">%s</p>
                <p style="color: #55ffff; font-size: 16; font-weight: bold;">%s $</p>
                <p style="color: #666666; font-size: 10;">%s %d</p>
              </div>
              <div style="flex-weight: 1; padding: 6; background-color: #1a1a2e(0.8); layout-mode: Top;">
                <p style="color: #888888; font-size: 11;">%s</p>
                <p style="color: #ff5555; font-size: 16; font-weight: bold;">%s $</p>
                <p style="color: #666666; font-size: 10;">%s %d</p>
              </div>
            </div>
            """.formatted(
                esc(L(lang, uuid, "gui.total_deposited")),
                esc(deposited),
                esc(L(lang, uuid, "gui.active_count")),
                activeDeposits,
                esc(L(lang, uuid, "gui.total_debt")),
                esc(debt),
                esc(L(lang, uuid, "gui.active_count")),
                activeLoans
        ));

        // Credit score bar — localized rating
        int score = credit.getScore();
        String ratingKey = "rating." + credit.getRating().toLowerCase();
        String rating = L(lang, uuid, ratingKey);
        String scoreColor = scoreColor(score);

        sb.append("""
            <div style="padding: 8 12; layout-mode: Top;">
              <div style="layout-mode: Left;">
                <p style="color: #888888; font-size: 12; flex-weight: 1;">%s</p>
                <p style="color: %s; font-size: 14; font-weight: bold;">%d / 1000 - %s</p>
              </div>
              <progress value="%d" max="1000" style="anchor-width: 100%%; anchor-height: 10;"></progress>
            </div>
            """.formatted(
                esc(L(lang, uuid, "gui.credit_score")),
                scoreColor, score, esc(rating),
                score
        ));

        // Loan terms summary
        BigDecimal maxLoan = bank.getLoanService().getMaxLoanAmount(uuid);
        BigDecimal loanRate = bank.getLoanService().getEffectiveRate(uuid);

        sb.append("""
            <div style="padding: 4 12; layout-mode: Left;">
              <div style="flex-weight: 1; padding: 4; layout-mode: Top;">
                <p style="color: #888888; font-size: 11;">%s</p>
                <p style="color: #55ff55; font-size: 13; font-weight: bold;">%s $</p>
              </div>
              <div style="flex-weight: 1; padding: 4; layout-mode: Top;">
                <p style="color: #888888; font-size: 11;">%s</p>
                <p style="color: #ffff55; font-size: 13; font-weight: bold;">%s</p>
              </div>
            """.formatted(
                esc(L(lang, uuid, "gui.max_loan")),
                esc(MessageUtil.formatCoins(maxLoan)),
                esc(L(lang, uuid, "gui.your_loan_rate")),
                esc(MessageUtil.formatPercent(loanRate))
        ));

        // Inflation (if enabled)
        if (bank.getInflationService().isEnabled()) {
            sb.append("""
              <div style="flex-weight: 1; padding: 4; layout-mode: Top;">
                <p style="color: #888888; font-size: 11;">%s</p>
                <p style="color: #ffaa00; font-size: 13; font-weight: bold;">%s</p>
              </div>
              """.formatted(
                    esc(L(lang, uuid, "gui.inflation")),
                    esc(MessageUtil.formatPercent(bank.getInflationService().getCurrentRate()))
            ));
        }
        sb.append("</div>\n");

        return sb.toString();
    }

    /**
     * Deposits tab: available plans with deposit action buttons + active deposits with withdraw.
     */
    private static String depositsTab(LangManager lang, UUID uuid,
                                      BankService bank, BankAccount account,
                                      Map<String, String> withdrawMap,
                                      Map<String, String[]> depositActionMap) {
        StringBuilder sb = new StringBuilder();

        // ── Available plans with deposit buttons ──
        List<DepositPlan> plans = bank.getDepositService().getAvailablePlans();
        sb.append(sectionHeader(L(lang, uuid, "gui.available_plans")));

        // Table header
        sb.append("""
            <div style="padding: 2 8; layout-mode: Left; background-color: #333366(0.5);">
              <p style="color: #aaaaaa; font-size: 10; flex-weight: 2; font-weight: bold;">%s</p>
              <p style="color: #aaaaaa; font-size: 10; flex-weight: 1; font-weight: bold;">%s</p>
              <p style="color: #aaaaaa; font-size: 10; flex-weight: 1; font-weight: bold;">%s</p>
              <p style="color: #aaaaaa; font-size: 10; flex-weight: 2; font-weight: bold;">%s</p>
              <p style="color: #aaaaaa; font-size: 10; flex-weight: 3; font-weight: bold;">%s</p>
            </div>
            """.formatted(
                esc(L(lang, uuid, "gui.col.plan")),
                esc(L(lang, uuid, "gui.col.term")),
                esc(L(lang, uuid, "gui.col.rate")),
                esc(L(lang, uuid, "gui.col.limits")),
                esc(L(lang, uuid, "gui.col.action"))
        ));

        for (DepositPlan p : plans) {
            // Calculate 3 preset amounts: min, geometric mean, max
            BigDecimal minAmt = p.getMinAmount();
            BigDecimal maxAmt = p.getMaxAmount();
            BigDecimal midAmt = BigDecimal.valueOf(
                    Math.round(Math.sqrt(minAmt.doubleValue() * maxAmt.doubleValue()))
            );
            if (midAmt.compareTo(minAmt) <= 0) midAmt = minAmt;
            if (midAmt.compareTo(maxAmt) >= 0) midAmt = maxAmt;

            String planName = p.getName();
            // Localized plan name
            String localizedPlan = L(lang, uuid, "plan." + planName);

            String btnId1 = "dep-" + planName + "-" + minAmt.toPlainString();
            String btnId2 = "dep-" + planName + "-" + midAmt.toPlainString();
            String btnId3 = "dep-" + planName + "-" + maxAmt.toPlainString();

            depositActionMap.put(btnId1, new String[]{planName, minAmt.toPlainString()});
            boolean showMid = midAmt.compareTo(minAmt) > 0 && midAmt.compareTo(maxAmt) < 0;
            if (showMid) {
                depositActionMap.put(btnId2, new String[]{planName, midAmt.toPlainString()});
            }
            depositActionMap.put(btnId3, new String[]{planName, maxAmt.toPlainString()});

            String buttonsHtml;
            if (showMid) {
                buttonsHtml = """
                    <div style="flex-weight: 3; layout-mode: Left;">
                      <button id="%s" class="small-secondary-button">%s</button>
                      <button id="%s" class="small-secondary-button">%s</button>
                      <button id="%s" class="small-secondary-button">%s</button>
                    </div>""".formatted(
                        btnId1, shortAmount(minAmt),
                        btnId2, shortAmount(midAmt),
                        btnId3, shortAmount(maxAmt)
                );
            } else {
                buttonsHtml = """
                    <div style="flex-weight: 3; layout-mode: Left;">
                      <button id="%s" class="small-secondary-button">%s</button>
                      <button id="%s" class="small-secondary-button">%s</button>
                    </div>""".formatted(
                        btnId1, shortAmount(minAmt),
                        btnId3, shortAmount(maxAmt)
                );
            }

            sb.append("""
                <div style="padding: 3 8; layout-mode: Left; background-color: #1a1a2e(0.6);">
                  <p style="color: #55ff55; font-size: 12; flex-weight: 2; font-weight: bold;">%s</p>
                  <p style="color: #ffffff; font-size: 12; flex-weight: 1;">%s %s</p>
                  <p style="color: #ffff55; font-size: 12; flex-weight: 1;">%s</p>
                  <p style="color: #aaaaaa; font-size: 11; flex-weight: 2;">%s - %s $</p>
                  %s
                </div>
                """.formatted(
                    esc(localizedPlan),
                    esc(String.valueOf(p.getTermDays())),
                    esc(L(lang, uuid, "gui.days")),
                    esc(MessageUtil.formatPercent(p.getBaseRate())),
                    esc(MessageUtil.formatCoins(minAmt)),
                    esc(MessageUtil.formatCoins(maxAmt)),
                    buttonsHtml
            ));
        }

        sb.append("""
            <p style="color: #666666; font-size: 10; padding: 2 8;">%s</p>
            """.formatted(esc(L(lang, uuid, "gui.deposit_hint"))));

        // ── Active deposits with withdraw buttons ──
        List<Deposit> deposits = account.getActiveDeposits();
        sb.append(sectionHeader(L(lang, uuid, "gui.your_deposits")));

        if (deposits.isEmpty()) {
            sb.append(emptyLabel(L(lang, uuid, "gui.no_deposits")));
        } else {
            for (Deposit d : deposits) {
                int daysLeft = (int) Math.max(0, d.getTermDays() - d.getElapsedDays());
                boolean matured = d.isMatured();
                String statusColor = matured ? "#55ff55" : "#ffff55";
                String statusText = matured
                        ? L(lang, uuid, "gui.status.matured")
                        : L(lang, uuid, "gui.status.active");

                String btnId = "withdraw-" + d.getId();
                withdrawMap.put(btnId, d.getId());

                String btnText = matured
                        ? L(lang, uuid, "gui.btn.collect")
                        : L(lang, uuid, "gui.btn.withdraw_early");

                // Localized plan name in deposit display
                String depPlanName = L(lang, uuid, "plan." + d.getPlanName());

                sb.append("""
                    <div style="background-color: #1a1a2e(0.85); padding: 4 8; layout-mode: Top;">
                      <div style="layout-mode: Left;">
                        <p style="color: #55ff55; font-size: 13; font-weight: bold; flex-weight: 1;">%s - %s $</p>
                        <p style="color: %s; font-size: 12; font-weight: bold;">%s</p>
                      </div>
                      <div style="layout-mode: Left;">
                        <p style="color: #aaaaaa; font-size: 11; flex-weight: 1;">%s: <span style="color: #55ffff;">%s</span> | %s: <span style="color: #ffff55;">+%s $</span> | %s: <span style="color: #ffffff;">%d%s</span></p>
                        <button id="%s" class="small-secondary-button">%s</button>
                      </div>
                    </div>
                    """.formatted(
                        esc(depPlanName),
                        esc(MessageUtil.formatCoins(d.getAmount())),
                        statusColor, esc(statusText),
                        esc(L(lang, uuid, "gui.rate_label")),
                        esc(MessageUtil.formatPercent(d.getInterestRate())),
                        esc(L(lang, uuid, "gui.accrued")),
                        esc(MessageUtil.formatCoins(d.getAccruedInterest())),
                        esc(L(lang, uuid, "gui.remaining")),
                        daysLeft,
                        esc(L(lang, uuid, "gui.days_short")),
                        btnId, esc(btnText)
                ));
            }
        }

        return sb.toString();
    }

    /**
     * Loans tab: credit info, take-loan buttons, active loans with multiple repay options.
     */
    private static String loansTab(LangManager lang, UUID uuid,
                                   BankService bank, BankAccount account,
                                   CreditScore credit,
                                   Map<String, BigDecimal> loanActionMap,
                                   Map<String, String[]> repayActionMap) {
        StringBuilder sb = new StringBuilder();

        // Credit limits section
        sb.append(sectionHeader(L(lang, uuid, "gui.credit_limits")));

        BigDecimal maxLoan = bank.getLoanService().getMaxLoanAmount(uuid);
        BigDecimal effectiveRate = bank.getLoanService().getEffectiveRate(uuid);
        String ratingKey = "rating." + credit.getRating().toLowerCase();
        String rating = L(lang, uuid, ratingKey);
        int score = credit.getScore();

        sb.append("""
            <div style="padding: 6 12; background-color: #1a1a2e(0.8); layout-mode: Top;">
              <div style="layout-mode: Left;">
                <p style="color: #888888; font-size: 12; flex-weight: 1;">%s</p>
                <p style="color: %s; font-size: 14; font-weight: bold;">%d - %s</p>
              </div>
              <div style="layout-mode: Left; padding: 4 0;">
                <div style="flex-weight: 1; layout-mode: Top;">
                  <p style="color: #888888; font-size: 11;">%s</p>
                  <p style="color: #55ff55; font-size: 15; font-weight: bold;">%s $</p>
                </div>
                <div style="flex-weight: 1; layout-mode: Top;">
                  <p style="color: #888888; font-size: 11;">%s</p>
                  <p style="color: #ffff55; font-size: 15; font-weight: bold;">%s</p>
                </div>
                <div style="flex-weight: 1; layout-mode: Top;">
                  <p style="color: #888888; font-size: 11;">%s</p>
                  <p style="color: #ffffff; font-size: 15; font-weight: bold;">%d / %d</p>
                </div>
              </div>
            </div>
            """.formatted(
                esc(L(lang, uuid, "gui.credit_score")),
                scoreColor(score), score, esc(rating),
                esc(L(lang, uuid, "gui.max_loan")),
                esc(MessageUtil.formatCoins(maxLoan)),
                esc(L(lang, uuid, "gui.your_loan_rate")),
                esc(MessageUtil.formatPercent(effectiveRate)),
                esc(L(lang, uuid, "gui.active_loans")),
                account.getActiveLoans().size(),
                bank.getLoanService().getMaxActiveLoans()
        ));

        // ── Take loan buttons ──
        if (maxLoan.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amt1 = maxLoan.multiply(BigDecimal.valueOf(0.1))
                    .setScale(0, RoundingMode.UP);
            BigDecimal amt2 = maxLoan.multiply(BigDecimal.valueOf(0.5))
                    .setScale(0, RoundingMode.UP);
            BigDecimal amt3 = maxLoan;

            BigDecimal minLoanAmt = BigDecimal.valueOf(100);
            if (amt1.compareTo(minLoanAmt) < 0) amt1 = minLoanAmt;
            if (amt2.compareTo(amt1) <= 0) amt2 = amt1;

            String lBtnId1 = "loan-" + amt1.toPlainString();
            String lBtnId2 = "loan-" + amt2.toPlainString();
            String lBtnId3 = "loan-max";

            loanActionMap.put(lBtnId1, amt1);
            if (amt2.compareTo(amt1) > 0 && amt2.compareTo(amt3) < 0) {
                loanActionMap.put(lBtnId2, amt2);
            }
            loanActionMap.put(lBtnId3, amt3);

            sb.append("""
                <div style="padding: 6 12; layout-mode: Left;">
                  <p style="color: #888888; font-size: 12; flex-weight: 1;">%s:</p>
                """.formatted(esc(L(lang, uuid, "gui.btn.take_loan"))));

            sb.append("""
                  <button id="%s" class="small-secondary-button">%s $</button>
                """.formatted(lBtnId1, shortAmount(amt1)));

            if (amt2.compareTo(amt1) > 0 && amt2.compareTo(amt3) < 0) {
                sb.append("""
                  <button id="%s" class="small-secondary-button">%s $</button>
                """.formatted(lBtnId2, shortAmount(amt2)));
            }

            sb.append("""
                  <button id="%s" class="small-secondary-button">MAX %s $</button>
                </div>
                """.formatted(lBtnId3, shortAmount(amt3)));
        }

        sb.append("""
            <p style="color: #666666; font-size: 10; padding: 2 8;">%s</p>
            """.formatted(esc(L(lang, uuid, "gui.loan_hint"))));

        // Credit history stats
        sb.append("""
            <div style="padding: 4 12; layout-mode: Left;">
              <div style="flex-weight: 1; padding: 3; background-color: #1a3a1a(0.6); layout-mode: Top;">
                <p style="color: #55ff55; font-size: 11;">[+] %s</p>
                <p style="color: #ffffff; font-size: 14; font-weight: bold;">%d</p>
              </div>
              <div style="flex-weight: 1; padding: 3; background-color: #3a1a1a(0.6); layout-mode: Top;">
                <p style="color: #ff5555; font-size: 11;">[x] %s</p>
                <p style="color: #ffffff; font-size: 14; font-weight: bold;">%d</p>
              </div>
              <div style="flex-weight: 1; padding: 3; background-color: #1a1a3a(0.6); layout-mode: Top;">
                <p style="color: #5555ff; font-size: 11;">[*] %s</p>
                <p style="color: #ffffff; font-size: 14; font-weight: bold;">%d</p>
              </div>
            </div>
            """.formatted(
                esc(L(lang, uuid, "gui.loans_completed")),
                credit.getTotalLoansCompleted(),
                esc(L(lang, uuid, "gui.loans_defaulted")),
                credit.getTotalLoansDefaulted(),
                esc(L(lang, uuid, "gui.on_time_payments")),
                credit.getOnTimePayments()
        ));

        // ── Active loans with multiple repay options ──
        List<Loan> loans = account.getActiveLoans();
        sb.append(sectionHeader(L(lang, uuid, "gui.your_loans")));

        if (loans.isEmpty()) {
            sb.append(emptyLabel(L(lang, uuid, "gui.no_loans")));
        } else {
            for (Loan l : loans) {
                int daysLeft = (int) l.getDaysUntilDue();
                boolean overdue = l.isOverdue();
                String statusColor = overdue ? "#ff5555" : "#ffff55";
                String statusText = overdue
                        ? L(lang, uuid, "gui.status.overdue")
                        : L(lang, uuid, "gui.status.active");

                int repayPct = l.getPrincipalAmount().compareTo(BigDecimal.ZERO) > 0
                        ? l.getTotalPaid()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(l.getPrincipalAmount(), 0, RoundingMode.HALF_UP)
                            .intValue()
                        : 0;

                BigDecimal remaining = l.getRemainingBalance();
                BigDecimal daily = l.getDailyPayment() != null ? l.getDailyPayment() : BigDecimal.ZERO;

                // Repay 10%, 25%, 50%, All
                BigDecimal r10 = remaining.multiply(BigDecimal.valueOf(0.10))
                        .setScale(0, RoundingMode.UP).max(BigDecimal.ONE);
                BigDecimal r25 = remaining.multiply(BigDecimal.valueOf(0.25))
                        .setScale(0, RoundingMode.UP);
                BigDecimal r50 = remaining.multiply(BigDecimal.valueOf(0.50))
                        .setScale(0, RoundingMode.UP);

                String btn10Id = "repay10-" + l.getId();
                String btn25Id = "repay25-" + l.getId();
                String btn50Id = "repay50-" + l.getId();
                String btnAllId = "repayAll-" + l.getId();

                repayActionMap.put(btn10Id, new String[]{l.getId(), r10.toPlainString()});
                repayActionMap.put(btn25Id, new String[]{l.getId(), r25.toPlainString()});
                repayActionMap.put(btn50Id, new String[]{l.getId(), r50.toPlainString()});
                repayActionMap.put(btnAllId, new String[]{l.getId(), remaining.toPlainString()});

                sb.append("""
                    <div style="background-color: #1a1a2e(0.85); padding: 5 8; layout-mode: Top;">
                      <div style="layout-mode: Left;">
                        <p style="color: #ff9955; font-size: 14; font-weight: bold; flex-weight: 1;">%s $</p>
                        <p style="color: %s; font-size: 12; font-weight: bold;">%s</p>
                      </div>
                      <div style="layout-mode: Left; padding: 2 0;">
                        <p style="color: #aaaaaa; font-size: 11; flex-weight: 1;">%s: <span style="color: #ff5555;">%s $</span> | %s: <span style="color: #ffff55;">%s</span> | %s: <span style="color: #ffffff;">%d%s</span></p>
                      </div>
                      <div style="layout-mode: Left; padding: 2 0;">
                        <p style="color: #aaaaaa; font-size: 11; flex-weight: 1;">%s: <span style="color: #55ffff;">%s $/%s</span></p>
                        <p style="color: #888888; font-size: 10;">%s: %d%%</p>
                        <progress value="%d" max="100" style="anchor-width: 80; anchor-height: 8;"></progress>
                      </div>
                      <div style="layout-mode: Left; padding: 2 0;">
                        <button id="%s" class="small-secondary-button">10%% (%s)</button>
                        <button id="%s" class="small-secondary-button">25%% (%s)</button>
                        <button id="%s" class="small-secondary-button">50%% (%s)</button>
                        <button id="%s" class="small-secondary-button">%s (%s)</button>
                      </div>
                    </div>
                    """.formatted(
                        esc(MessageUtil.formatCoins(l.getPrincipalAmount())),
                        statusColor, esc(statusText),
                        esc(L(lang, uuid, "gui.remaining_debt")),
                        esc(MessageUtil.formatCoins(remaining)),
                        esc(L(lang, uuid, "gui.rate_label")),
                        esc(MessageUtil.formatPercent(l.getInterestRate())),
                        esc(L(lang, uuid, "gui.due_in")),
                        Math.max(0, daysLeft),
                        esc(L(lang, uuid, "gui.days_short")),
                        esc(L(lang, uuid, "gui.daily_payment")),
                        esc(MessageUtil.formatCoins(daily)),
                        esc(L(lang, uuid, "gui.days_short")),
                        esc(L(lang, uuid, "gui.repaid")),
                        Math.min(100, repayPct),
                        Math.min(100, repayPct),
                        btn10Id, shortAmount(r10),
                        btn25Id, shortAmount(r25),
                        btn50Id, shortAmount(r50),
                        btnAllId,
                        esc(L(lang, uuid, "gui.btn.repay_all")),
                        shortAmount(remaining)
                ));
            }
        }

        sb.append("""
            <p style="color: #666666; font-size: 10; padding: 2 8;">%s</p>
            """.formatted(esc(L(lang, uuid, "gui.repay_hint"))));

        return sb.toString();
    }

    /** History tab: last N audit log entries with localized types and descriptions. */
    private static String historyTab(LangManager lang, UUID uuid, BankService bank) {
        StringBuilder sb = new StringBuilder();

        List<AuditLog> logs = bank.getAuditLogs(uuid, 20);

        if (logs.isEmpty()) {
            sb.append(emptyLabel(L(lang, uuid, "gui.no_history")));
        } else {
            sb.append("""
                <div style="padding: 2 8; layout-mode: Left; background-color: #333366(0.5);">
                  <p style="color: #aaaaaa; font-size: 10; flex-weight: 2; font-weight: bold;">%s</p>
                  <p style="color: #aaaaaa; font-size: 10; flex-weight: 1; font-weight: bold;">%s</p>
                  <p style="color: #aaaaaa; font-size: 10; flex-weight: 3; font-weight: bold;">%s</p>
                </div>
                """.formatted(
                    esc(L(lang, uuid, "gui.col.type")),
                    esc(L(lang, uuid, "gui.col.amount")),
                    esc(L(lang, uuid, "gui.col.description"))
            ));

            for (AuditLog log : logs) {
                String typeColor = typeColor(log.getType());
                String typeName = L(lang, uuid, "txtype." + log.getType().name());
                String desc = formatAuditDescription(lang, uuid, log);

                sb.append("""
                    <div style="padding: 2 8; layout-mode: Left; background-color: #1a1a2e(0.5);">
                      <p style="color: %s; font-size: 11; flex-weight: 2;">%s</p>
                      <p style="color: #ffffff; font-size: 11; flex-weight: 1;">%s $</p>
                      <p style="color: #aaaaaa; font-size: 10; flex-weight: 3;">%s</p>
                    </div>
                    """.formatted(
                        typeColor,
                        esc(typeName),
                        esc(MessageUtil.formatCoins(log.getAmount())),
                        esc(desc)
                ));
            }
        }

        return sb.toString();
    }

    // ════════════════════════════════════════════════════════
    //  AUDIT DESCRIPTION FORMATTER
    // ════════════════════════════════════════════════════════

    /**
     * Format audit log description into human-readable localized text.
     * New-format descriptions use "|" as delimiter for structured data.
     */
    private static String formatAuditDescription(LangManager lang, UUID uuid, AuditLog log) {
        String raw = log.getDescription();
        if (raw == null || raw.isEmpty()) return "";
        String[] parts = raw.split("\\|");
        String id = parts.length > 0 ? parts[0] : "";
        return switch (log.getType()) {
            case LOAN_TAKE -> parts.length >= 6
                    ? L(lang, uuid, "txdesc.loan_take",
                        "id", id, "amount", parts[1], "days", parts[2],
                        "rate", parts[3], "collateral", parts[4], "daily", parts[5])
                    : raw;
            case LOAN_REPAY -> parts.length >= 3
                    ? L(lang, uuid, "txdesc.loan_repay",
                        "id", id, "paid", parts[1], "remaining", parts[2])
                    : raw;
            case LOAN_DAILY_PAYMENT -> parts.length >= 3
                    ? L(lang, uuid, "txdesc.loan_daily",
                        "id", id, "paid", parts[1], "remaining", parts[2])
                    : raw;
            case LOAN_OVERDUE -> parts.length >= 2
                    ? L(lang, uuid, "txdesc.loan_overdue", "id", id, "debt", parts[1])
                    : raw;
            case LOAN_DEFAULT -> parts.length >= 3
                    ? L(lang, uuid, "txdesc.loan_default",
                        "id", id, "debt", parts[1], "days", parts[2])
                    : raw;
            default -> raw;
        };
    }

    // ════════════════════════════════════════════════════════
    //  HTML HELPERS
    // ════════════════════════════════════════════════════════

    private static String tabOpen(String tabId) {
        return """
            <div id="%s-content" class="tab-content" data-hyui-tab-id="%s"
                 style="layout: topscrolling; padding: 4;">
            """.formatted(tabId, tabId);
    }

    private static final String TAB_CLOSE = "</div>\n";

    private static final String FOOTER_HTML = """
                </div>
              </div>
            </div>
            """;

    private static String sectionHeader(String text) {
        return """
            <div style="padding: 6 8 2 8;">
              <p style="color: #ffaa00; font-size: 13; font-weight: bold;">%s</p>
            </div>
            """.formatted(esc(text));
    }

    private static String emptyLabel(String text) {
        return "<p style=\"color: #888888; font-size: 13; padding: 16;\">"
                + esc(text) + "</p>\n";
    }

    private static String scoreColor(int score) {
        if (score >= 800) return "#55ff55";
        if (score >= 650) return "#aaff55";
        if (score >= 450) return "#ffff55";
        if (score >= 250) return "#ffaa55";
        return "#ff5555";
    }

    private static String typeColor(TransactionType type) {
        return switch (type) {
            case DEPOSIT_OPEN, DEPOSIT_INTEREST -> "#55ff55";
            case DEPOSIT_CLOSE, DEPOSIT_EARLY_WITHDRAWAL -> "#55ffff";
            case LOAN_TAKE -> "#ffaa55";
            case LOAN_REPAY, LOAN_DAILY_PAYMENT -> "#55ff55";
            case LOAN_OVERDUE, LOAN_DEFAULT -> "#ff5555";
            case TAX_BALANCE, TAX_INTEREST, TAX_TRANSACTION -> "#ffff55";
            case PENALTY -> "#ff5555";
            case FREEZE, UNFREEZE -> "#ff55ff";
            case BANK_WITHDRAWAL, BANK_DEPOSIT -> "#ffffff";
        };
    }

    /**
     * Short human-readable amount for button labels.
     */
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

    // ════════════════════════════════════════════════════════
    //  SHARED HELPERS
    // ════════════════════════════════════════════════════════

    private static String L(LangManager lang, UUID uuid, String key, String... args) {
        return lang.getForPlayer(uuid, key, args);
    }

    /** Minimal HTML entity escaping. */
    private static String esc(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    /** Send a chat message to the player via reflection. */
    private static void sendMsg(PlayerRef playerRef, String miniMessageText) {
        try {
            String json = MiniMessageParser.toJson(miniMessageText);
            Class<?> msgClass = Class.forName("com.hypixel.hytale.server.core.Message");
            java.lang.reflect.Method parseMethod = msgClass.getMethod("parse", String.class);
            Object message = parseMethod.invoke(null, json);
            java.lang.reflect.Method sendMethod = playerRef.getClass()
                    .getMethod("sendMessage", msgClass);
            sendMethod.invoke(playerRef, message);
        } catch (Exception e) {
            LOGGER.warn("[sendMsg] reflection failed: {}", e.getMessage());
        }
    }

    private static final String CSS = """
        <style>
            .empty-msg { color: #888888; font-size: 14; padding: 20; }
        </style>
        """;
}
