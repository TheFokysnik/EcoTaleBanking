package com.crystalrealm.ecotalebanking.gui;

import au.ellie.hyui.builders.PageBuilder;

import com.crystalrealm.ecotalebanking.EcoTaleBankingPlugin;
import com.crystalrealm.ecotalebanking.config.BankingConfig;
import com.crystalrealm.ecotalebanking.lang.LangManager;
import com.crystalrealm.ecotalebanking.model.*;
import com.crystalrealm.ecotalebanking.service.*;
import com.crystalrealm.ecotalebanking.storage.BankStorage;
import com.crystalrealm.ecotalebanking.util.MessageUtil;
import com.crystalrealm.ecotalebanking.util.MiniMessageParser;
import com.crystalrealm.ecotalebanking.util.PluginLogger;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * Административная GUI-панель для управления банковской системой.
 *
 * <p>Вкладки:</p>
 * <ul>
 *   <li><b>Dashboard</b> — общая статистика: всего аккаунтов, вкладов, займов, замороженных</li>
 *   <li><b>Accounts</b> — список всех аккаунтов с основными показателями</li>
 *   <li><b>Activity</b> — последние операции всех игроков</li>
 *   <li><b>Settings</b> — настройки плагина прямо в игре (конфиг-редактор)</li>
 * </ul>
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public final class AdminBankGui {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private AdminBankGui() {}

    /**
     * Построить и открыть панель администратора.
     *
     * @param plugin    экземпляр плагина
     * @param playerRef PlayerRef администратора
     * @param store     entity store
     * @param adminUuid UUID администратора
     */
    public static void open(@Nonnull EcoTaleBankingPlugin plugin,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull UUID adminUuid) {
        open(plugin, playerRef, store, adminUuid, "dashboard");
    }

    /**
     * Open admin GUI on a specific tab.
     */
    public static void open(@Nonnull EcoTaleBankingPlugin plugin,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull UUID adminUuid,
                            @Nonnull String selectedTab) {

        LangManager lang     = plugin.getLangManager();
        BankService bank     = plugin.getBankService();
        BankStorage storage  = plugin.getStorage();

        // Cache PlayerRef for notifications
        MessageUtil.cachePlayerRef(adminUuid, playerRef);

        Collection<BankAccount> allAccounts = storage.getAllAccounts();
        Collection<CreditScore> allCredits  = storage.getAllCreditScores();

        // Freeze/unfreeze button mapping
        Map<String, UUID> freezeMap   = new LinkedHashMap<>();
        Map<String, UUID> unfreezeMap = new LinkedHashMap<>();

        // ── Build HYUIML ────────────────────────────────────────
        StringBuilder html = new StringBuilder();
        html.append(CSS);

        String tabDashboard = esc(L(lang, adminUuid, "gui.admin.tab.dashboard"));
        String tabAccounts  = esc(L(lang, adminUuid, "gui.admin.tab.accounts"));
        String tabActivity  = esc(L(lang, adminUuid, "gui.admin.tab.activity"));
        String tabSettings  = esc(L(lang, adminUuid, "gui.admin.tab.settings"));

        // Parse settings sub-tab (e.g. "settings:loans" → main="settings", sub="loans")
        String settingsSubTab = "general";
        String mainSelectedTab = selectedTab;
        if (selectedTab.startsWith("settings:")) {
            mainSelectedTab = "settings";
            settingsSubTab = selectedTab.substring("settings:".length());
        }

        html.append("""
            <div class="page-overlay">
              <div class="decorated-container" data-hyui-title="%s"
                   style="anchor-width: 820; anchor-height: 620;">
                <div class="container-contents" style="layout-mode: Top; padding: 6;">
                  <nav id="admin-tabs" class="tabs"
                       data-tabs="dashboard:%s:dashboard-content,accounts:%s:accounts-content,activity:%s:activity-content,settings:%s:settings-content"
                       data-selected="%s">
                  </nav>
            """.formatted(
                esc(L(lang, adminUuid, "gui.admin.title")),
                tabDashboard, tabAccounts, tabActivity, tabSettings,
                esc(mainSelectedTab)
        ));

        // ═══════════════════════════════════════════════════════
        //  TAB: Dashboard
        // ═══════════════════════════════════════════════════════
        html.append(tabOpen("dashboard"));
        html.append(dashboardTab(lang, adminUuid, bank, allAccounts, allCredits));
        html.append(TAB_CLOSE);

        // ═══════════════════════════════════════════════════════
        //  TAB: Accounts
        // ═══════════════════════════════════════════════════════
        html.append(tabOpen("accounts"));
        html.append(accountsTab(lang, adminUuid, bank, allAccounts, allCredits,
                freezeMap, unfreezeMap));
        html.append(TAB_CLOSE);

        // ═══════════════════════════════════════════════════════
        //  TAB: Activity
        // ═══════════════════════════════════════════════════════
        html.append(tabOpen("activity"));
        html.append(activityTab(lang, adminUuid, bank, allAccounts));
        html.append(TAB_CLOSE);

        // ═══════════════════════════════════════════════════════
        //  TAB: Settings
        // ═══════════════════════════════════════════════════════
        Map<String, Runnable> settingsActions = new LinkedHashMap<>();
        Map<String, String> settingsSubTabNav = new LinkedHashMap<>();
        html.append(tabOpen("settings"));
        html.append(settingsTab(lang, adminUuid,
                plugin.getConfigManager().getConfig(), settingsActions,
                settingsSubTabNav, settingsSubTab));
        html.append(TAB_CLOSE);

        html.append(FOOTER_HTML);

        // ── Create HyUI page ──────────────────────────────────
        PageBuilder builder = PageBuilder.pageForPlayer(playerRef)
                .fromHtml(html.toString())
                .withLifetime(CustomPageLifetime.CanDismiss);

        // Freeze buttons
        for (var entry : freezeMap.entrySet()) {
            String btnId     = entry.getKey();
            UUID   targetUuid = entry.getValue();
            builder.addEventListener(btnId, CustomUIEventBindingType.Activating, (data, ctx) -> {
                BankAccount acc = bank.getAccount(targetUuid);
                acc.setFrozen(true, "Frozen by admin via GUI");
                storage.saveAccount(acc);
                sendMsg(playerRef, L(lang, adminUuid, "gui.admin.frozen_success",
                        "uuid", targetUuid.toString().substring(0, 8)));
                open(plugin, playerRef, store, adminUuid);
            });
        }

        // Unfreeze buttons
        for (var entry : unfreezeMap.entrySet()) {
            String btnId     = entry.getKey();
            UUID   targetUuid = entry.getValue();
            builder.addEventListener(btnId, CustomUIEventBindingType.Activating, (data, ctx) -> {
                BankAccount acc = bank.getAccount(targetUuid);
                acc.setFrozen(false, null);
                storage.saveAccount(acc);
                sendMsg(playerRef, L(lang, adminUuid, "gui.admin.unfrozen_success",
                        "uuid", targetUuid.toString().substring(0, 8)));
                open(plugin, playerRef, store, adminUuid);
            });
        }

        // Settings sub-tab navigation
        final String reopenSettingsTab = selectedTab.startsWith("settings") ? selectedTab : "settings:general";
        for (var entry : settingsSubTabNav.entrySet()) {
            String btnId = entry.getKey();
            String targetTab = entry.getValue();
            builder.addEventListener(btnId, CustomUIEventBindingType.Activating, (data, ctx) -> {
                open(plugin, playerRef, store, adminUuid, targetTab);
            });
        }

        // Settings buttons (+/-, toggles, reset)
        for (var entry : settingsActions.entrySet()) {
            String btnId = entry.getKey();
            Runnable action = entry.getValue();
            builder.addEventListener(btnId, CustomUIEventBindingType.Activating, (data, ctx) -> {
                action.run();
                plugin.getConfigManager().save();
                open(plugin, playerRef, store, adminUuid, reopenSettingsTab);
            });
        }

        // Reset to defaults button
        builder.addEventListener("settings-reset", CustomUIEventBindingType.Activating, (data, ctx) -> {
            plugin.getConfigManager().resetToDefaults();
            plugin.getConfigManager().save();
            sendMsg(playerRef, L(lang, adminUuid, "gui.admin.settings.reset_success"));
            open(plugin, playerRef, store, adminUuid, reopenSettingsTab);
        });

        builder.open(store);
        LOGGER.info("Admin bank GUI opened by {}", adminUuid);
    }

    // ═══════════════════════════════════════════════════════════
    //  TAB BUILDERS
    // ═══════════════════════════════════════════════════════════

    /** Dashboard: aggregate stats. */
    private static String dashboardTab(LangManager lang, UUID uuid,
                                       BankService bank,
                                       Collection<BankAccount> accounts,
                                       Collection<CreditScore> credits) {
        StringBuilder sb = new StringBuilder();

        int totalAccounts = accounts.size();
        int frozenCount   = (int) accounts.stream().filter(BankAccount::isFrozen).count();
        int totalDeposits = accounts.stream().mapToInt(a -> a.getActiveDeposits().size()).sum();
        int totalLoans    = accounts.stream().mapToInt(a -> a.getActiveLoans().size()).sum();
        int overdueLoans  = (int) accounts.stream()
                .flatMap(a -> a.getActiveLoans().stream())
                .filter(Loan::isOverdue)
                .count();

        BigDecimal totalDeposited = accounts.stream()
                .map(BankAccount::getTotalDeposited)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDebt = accounts.stream()
                .map(BankAccount::getTotalDebt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double avgCreditScore = credits.stream()
                .mapToInt(CreditScore::getScore)
                .average()
                .orElse(0);

        // Header
        sb.append("""
            <div style="padding: 8 12; layout-mode: Top;">
              <p style="color: #ffaa00; font-size: 16; font-weight: bold;">%s</p>
            </div>
            """.formatted(esc(L(lang, uuid, "gui.admin.dashboard_title"))));

        // Stat cards — row 1
        sb.append("""
            <div style="padding: 4 12; layout-mode: Left;">
              <div style="flex-weight: 1; padding: 8; background-color: #1a2a3a(0.8); layout-mode: Top;">
                <p style="color: #888888; font-size: 11;">%s</p>
                <p style="color: #55ffff; font-size: 22; font-weight: bold;">%d</p>
              </div>
              <div style="flex-weight: 1; padding: 8; background-color: #1a3a1a(0.8); layout-mode: Top;">
                <p style="color: #888888; font-size: 11;">%s</p>
                <p style="color: #55ff55; font-size: 22; font-weight: bold;">%d</p>
              </div>
              <div style="flex-weight: 1; padding: 8; background-color: #3a2a1a(0.8); layout-mode: Top;">
                <p style="color: #888888; font-size: 11;">%s</p>
                <p style="color: #ffaa55; font-size: 22; font-weight: bold;">%d</p>
              </div>
              <div style="flex-weight: 1; padding: 8; background-color: #3a1a1a(0.8); layout-mode: Top;">
                <p style="color: #888888; font-size: 11;">%s</p>
                <p style="color: #ff5555; font-size: 22; font-weight: bold;">%d</p>
              </div>
            </div>
            """.formatted(
                esc(L(lang, uuid, "gui.admin.total_accounts")),
                totalAccounts,
                esc(L(lang, uuid, "gui.admin.total_deposits")),
                totalDeposits,
                esc(L(lang, uuid, "gui.admin.total_loans")),
                totalLoans,
                esc(L(lang, uuid, "gui.admin.frozen_accounts")),
                frozenCount
        ));

        // Stat cards — row 2
        sb.append("""
            <div style="padding: 4 12; layout-mode: Left;">
              <div style="flex-weight: 1; padding: 8; background-color: #1a1a2e(0.8); layout-mode: Top;">
                <p style="color: #888888; font-size: 11;">%s</p>
                <p style="color: #55ff55; font-size: 18; font-weight: bold;">%s $</p>
              </div>
              <div style="flex-weight: 1; padding: 8; background-color: #1a1a2e(0.8); layout-mode: Top;">
                <p style="color: #888888; font-size: 11;">%s</p>
                <p style="color: #ff5555; font-size: 18; font-weight: bold;">%s $</p>
              </div>
              <div style="flex-weight: 1; padding: 8; background-color: #1a1a2e(0.8); layout-mode: Top;">
                <p style="color: #888888; font-size: 11;">%s</p>
                <p style="color: #ff5555; font-size: 18; font-weight: bold;">%d</p>
              </div>
            </div>
            """.formatted(
                esc(L(lang, uuid, "gui.admin.total_deposited")),
                esc(MessageUtil.formatCoins(totalDeposited)),
                esc(L(lang, uuid, "gui.admin.total_debt")),
                esc(MessageUtil.formatCoins(totalDebt)),
                esc(L(lang, uuid, "gui.admin.overdue_loans")),
                overdueLoans
        ));

        // Average credit + inflation
        sb.append("""
            <div style="padding: 4 12; layout-mode: Left;">
              <div style="flex-weight: 1; padding: 8; background-color: #1a1a2e(0.8); layout-mode: Top;">
                <p style="color: #888888; font-size: 11;">%s</p>
                <p style="color: #ffff55; font-size: 18; font-weight: bold;">%.0f / 1000</p>
              </div>
            """.formatted(
                esc(L(lang, uuid, "gui.admin.avg_credit")),
                avgCreditScore
        ));

        if (bank.getInflationService().isEnabled()) {
            sb.append("""
              <div style="flex-weight: 1; padding: 8; background-color: #1a1a2e(0.8); layout-mode: Top;">
                <p style="color: #888888; font-size: 11;">%s</p>
                <p style="color: #ffaa00; font-size: 18; font-weight: bold;">%s</p>
              </div>
              """.formatted(
                    esc(L(lang, uuid, "gui.admin.inflation")),
                    esc(MessageUtil.formatPercent(bank.getInflationService().getCurrentRate()))
            ));
        }
        sb.append("</div>\n");

        return sb.toString();
    }

    /** Accounts list with freeze/unfreeze buttons. */
    private static String accountsTab(LangManager lang, UUID uuid,
                                      BankService bank,
                                      Collection<BankAccount> accounts,
                                      Collection<CreditScore> credits,
                                      Map<String, UUID> freezeMap,
                                      Map<String, UUID> unfreezeMap) {
        StringBuilder sb = new StringBuilder();

        // Map credit scores by UUID for fast lookup
        Map<UUID, CreditScore> creditMap = new HashMap<>();
        for (CreditScore c : credits) {
            creditMap.put(c.getPlayerUuid(), c);
        }

        // Header row
        sb.append("""
            <div style="padding: 4 12; layout-mode: Left; background-color: #333366(0.5); anchor-height: 24;">
              <p style="color: #aaaaaa; font-size: 10; flex-weight: 3; font-weight: bold;">%s</p>
              <p style="color: #aaaaaa; font-size: 10; flex-weight: 2; font-weight: bold;">%s</p>
              <p style="color: #aaaaaa; font-size: 10; flex-weight: 2; font-weight: bold;">%s</p>
              <p style="color: #aaaaaa; font-size: 10; flex-weight: 1; font-weight: bold;">%s</p>
              <p style="color: #aaaaaa; font-size: 10; flex-weight: 1.5; font-weight: bold;">%s</p>
              <p style="color: #aaaaaa; font-size: 10; flex-weight: 2; font-weight: bold;">%s</p>
            </div>
            """.formatted(
                esc(L(lang, uuid, "gui.admin.col.player")),
                esc(L(lang, uuid, "gui.admin.col.deposits")),
                esc(L(lang, uuid, "gui.admin.col.debt")),
                esc(L(lang, uuid, "gui.admin.col.credit")),
                esc(L(lang, uuid, "gui.admin.col.status")),
                esc(L(lang, uuid, "gui.admin.col.action"))
        ));

        if (accounts.isEmpty()) {
            sb.append(emptyLabel(L(lang, uuid, "gui.admin.no_accounts")));
        } else {
            // Sort: frozen first, then by total debt desc
            List<BankAccount> sorted = accounts.stream()
                    .sorted(Comparator
                            .comparing(BankAccount::isFrozen).reversed()
                            .thenComparing(a -> a.getTotalDebt().negate()))
                    .collect(Collectors.toList());

            for (BankAccount acc : sorted) {
                UUID playerUuid = acc.getPlayerUuid();
                String shortId = playerUuid.toString().substring(0, 8);
                String displayName = acc.getLastKnownName() != null
                        ? acc.getLastKnownName() : shortId;
                CreditScore cs = creditMap.getOrDefault(playerUuid, new CreditScore(playerUuid));

                String statusColor;
                String statusText;
                String btnId;
                String btnText;

                if (acc.isFrozen()) {
                    statusColor = "#ff5555";
                    statusText = L(lang, uuid, "gui.admin.status.frozen");
                    btnId = "unfreeze-" + shortId;
                    unfreezeMap.put(btnId, playerUuid);
                    btnText = L(lang, uuid, "gui.admin.btn.unfreeze");
                } else {
                    statusColor = "#55ff55";
                    statusText = L(lang, uuid, "gui.admin.status.active");
                    btnId = "freeze-" + shortId;
                    freezeMap.put(btnId, playerUuid);
                    btnText = L(lang, uuid, "gui.admin.btn.freeze");
                }

                sb.append("""
                    <div style="padding: 3 12; layout-mode: Left; background-color: #1a1a2e(0.6); anchor-height: 24;">
                      <p style="color: #ffffff; font-size: 11; flex-weight: 3;">%s</p>
                      <p style="color: #55ffff; font-size: 11; flex-weight: 2;">%s $ (%d)</p>
                      <p style="color: #ff5555; font-size: 11; flex-weight: 2;">%s $ (%d)</p>
                      <p style="color: #ffff55; font-size: 11; flex-weight: 1;">%d</p>
                      <p style="color: %s; font-size: 11; flex-weight: 1.5; font-weight: bold;">%s</p>
                      <button id="%s" class="small-tertiary-button" style="flex-weight: 2;">%s</button>
                    </div>
                    """.formatted(
                        esc(displayName),
                        esc(MessageUtil.formatCoins(acc.getTotalDeposited())),
                        acc.getActiveDeposits().size(),
                        esc(MessageUtil.formatCoins(acc.getTotalDebt())),
                        acc.getActiveLoans().size(),
                        cs.getScore(),
                        statusColor,
                        esc(statusText),
                        btnId, esc(btnText)
                ));
            }
        }

        return sb.toString();
    }

    /** Activity feed: recent audit logs across all players. */
    private static String activityTab(LangManager lang, UUID uuid,
                                      BankService bank,
                                      Collection<BankAccount> accounts) {
        StringBuilder sb = new StringBuilder();

        // Build player name lookup from accounts
        Map<UUID, String> nameMap = new HashMap<>();
        for (BankAccount acc : accounts) {
            if (acc.getLastKnownName() != null) {
                nameMap.put(acc.getPlayerUuid(), acc.getLastKnownName());
            }
        }

        // Gather last few logs from each account, merge and sort by timestamp
        List<AuditLog> allLogs = new ArrayList<>();
        for (BankAccount acc : accounts) {
            allLogs.addAll(bank.getAuditLogs(acc.getPlayerUuid(), 5));
        }
        allLogs.sort(Comparator.comparing(AuditLog::getTimestamp).reversed());
        // Limit to 30 most recent
        if (allLogs.size() > 30) {
            allLogs = allLogs.subList(0, 30);
        }

        if (allLogs.isEmpty()) {
            sb.append(emptyLabel(L(lang, uuid, "gui.admin.no_activity")));
        } else {
            for (AuditLog log : allLogs) {
                String playerName = nameMap.getOrDefault(log.getPlayerUuid(),
                        log.getPlayerUuid().toString().substring(0, 8));
                String typeColor = typeColor(log.getType());
                String typeName = L(lang, uuid, "txtype." + log.getType().name());
                String desc = formatAuditDescription(lang, uuid, log);

                sb.append("""
                    <div style="padding: 3 10; layout-mode: Top; background-color: #1a1a2e(0.5);">
                      <div style="layout-mode: Left;">
                        <p style="color: #ffffff; font-size: 11; flex-weight: 1; font-weight: bold;">%s</p>
                        <p style="color: %s; font-size: 11; flex-weight: 1;">%s</p>
                        <p style="color: #55ffff; font-size: 11; flex-weight: 1;">%s $</p>
                      </div>
                      <p style="color: #888888; font-size: 10;">%s</p>
                    </div>
                    """.formatted(
                        esc(playerName),
                        typeColor,
                        esc(typeName),
                        esc(MessageUtil.formatCoins(log.getAmount())),
                        esc(desc)
                ));
            }
        }

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════
    //  TAB: Settings — in-game config editor with sub-tabs
    // ═══════════════════════════════════════════════════════════

    private static String settingsTab(LangManager lang, UUID uuid,
                                      BankingConfig config,
                                      Map<String, Runnable> actions,
                                      Map<String, String> subTabNav,
                                      String activeSubTab) {
        StringBuilder sb = new StringBuilder();

        var gen  = config.getGeneral();
        var dep  = config.getDeposits();
        var loan = config.getLoans();
        var cred = config.getCredit();
        var infl = config.getInflation();
        var prot = config.getProtection();

        // ── Sub-tab navigation bar ────────────────────────────
        String[][] subTabs = {
            {"general",    L(lang, uuid, "gui.admin.settings.general")},
            {"deposits",   L(lang, uuid, "gui.admin.settings.deposits_section")},
            {"loans",      L(lang, uuid, "gui.admin.settings.loans_section")},
            {"credit",     L(lang, uuid, "gui.admin.settings.credit_section")},
            {"inflation",  L(lang, uuid, "gui.admin.settings.inflation_section")},
            {"protection", L(lang, uuid, "gui.admin.settings.protection_section")},
        };

        sb.append("<div style=\"padding: 4 6 2 6; layout-mode: Left; anchor-height: 28;\">\n");
        for (String[] tab : subTabs) {
            String tabId    = tab[0];
            String tabLabel = tab[1];
            boolean isActive = tabId.equals(activeSubTab);
            String btnId    = "stab-" + tabId;
            String btnClass = isActive ? "small-secondary-button" : "small-tertiary-button";
            String textColor = isActive ? "#ffaa00" : "#666666";
            String fontW     = isActive ? "font-weight: bold;" : "";

            sb.append("""
                <button id="%s" class="%s" style="flex-weight: 1; font-size: 9; color: %s; %s">%s</button>
                """.formatted(btnId, btnClass, textColor, fontW, esc(tabLabel)));
            subTabNav.put(btnId, "settings:" + tabId);
        }
        sb.append("</div>\n");

        // ── Sub-tab content ───────────────────────────────────
        switch (activeSubTab) {
            case "general" -> {
                addSubTabTitle(sb, L(lang, uuid, "gui.admin.settings.general"));

                // Language cycle (en <-> ru)
                String langVal = gen.getLanguage();
                sb.append("""
                    <div style="padding: 2 12; layout-mode: Left; background-color: #1a1a2e(0.5); anchor-height: 26;">
                      <p style="color: #cccccc; font-size: 11; flex-weight: 4;">%s</p>
                      <button id="set-lang" class="small-secondary-button" style="flex-weight: 1.5; font-size: 12; color: #55ffff; font-weight: bold;">%s</button>
                    </div>
                    """.formatted(
                        esc(L(lang, uuid, "gui.admin.settings.language")),
                        esc(langVal.toUpperCase())
                ));
                actions.put("set-lang", () -> gen.setLanguage(langVal.equals("ru") ? "en" : "ru"));

                addToggle(sb, actions, L(lang, uuid, "gui.admin.settings.debug_mode"),
                          gen.isDebugMode(), "set-debug", v -> gen.setDebugMode(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.autosave"),
                       gen.getAutoSaveMinutes(), "set-autosave", 1, 1, 60,
                       v -> gen.setAutoSaveMinutes(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.game_day_duration"),
                       gen.getSecondsPerGameDay(), "set-gameday", 60, 60, 86400,
                       v -> gen.setSecondsPerGameDay(v));
            }
            case "deposits" -> {
                addSubTabTitle(sb, L(lang, uuid, "gui.admin.settings.deposits_section"));

                addToggle(sb, actions, L(lang, uuid, "gui.admin.settings.enabled"),
                          dep.isEnabled(), "set-dep-on", v -> dep.setEnabled(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.max_per_player"),
                       dep.getMaxPerPlayer(), "set-dep-max", 1, 1, 10,
                       v -> dep.setMaxPerPlayer(v));
                addRate(sb, actions, L(lang, uuid, "gui.admin.settings.early_penalty"),
                        dep.getEarlyWithdrawalPenaltyRate(), "set-dep-penalty", 0.01, 0.0, 1.0,
                        v -> dep.setEarlyWithdrawalPenaltyRate(v));
            }
            case "loans" -> {
                addSubTabTitle(sb, L(lang, uuid, "gui.admin.settings.loans_section"));

                addToggle(sb, actions, L(lang, uuid, "gui.admin.settings.enabled"),
                          loan.isEnabled(), "set-loan-on", v -> loan.setEnabled(v));
                addRate(sb, actions, L(lang, uuid, "gui.admin.settings.interest_rate"),
                        loan.getBaseInterestRate(), "set-loan-rate", 0.01, 0.01, 1.0,
                        v -> loan.setBaseInterestRate(v));
                addAmount(sb, actions, L(lang, uuid, "gui.admin.settings.min_amount"),
                          loan.getMinAmount(), "set-loan-min", 100, 0, 1000000,
                          v -> loan.setMinAmount(v));
                addAmount(sb, actions, L(lang, uuid, "gui.admin.settings.max_amount"),
                          loan.getMaxAmount(), "set-loan-max", 1000, 100, 10000000,
                          v -> loan.setMaxAmount(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.max_active"),
                       loan.getMaxActiveLoans(), "set-loan-active", 1, 1, 10,
                       v -> loan.setMaxActiveLoans(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.term_days"),
                       loan.getDefaultTermDays(), "set-loan-term", 1, 1, 365,
                       v -> loan.setDefaultTermDays(v));
                addRate(sb, actions, L(lang, uuid, "gui.admin.settings.overdue_penalty"),
                        loan.getOverduePenaltyRate(), "set-loan-overdue", 0.01, 0.0, 1.0,
                        v -> loan.setOverduePenaltyRate(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.default_after"),
                       loan.getDefaultAfterDays(), "set-loan-defdays", 1, 1, 90,
                       v -> loan.setDefaultAfterDays(v));
                addRate(sb, actions, L(lang, uuid, "gui.admin.settings.collateral"),
                        loan.getCollateralRate(), "set-loan-coll", 0.01, 0.0, 1.0,
                        v -> loan.setCollateralRate(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.min_credit"),
                       loan.getMinCreditScoreForLoan(), "set-loan-mincr", 10, 0, 1000,
                       v -> loan.setMinCreditScoreForLoan(v));
            }
            case "credit" -> {
                addSubTabTitle(sb, L(lang, uuid, "gui.admin.settings.credit_section"));

                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.initial_score"),
                       cred.getInitialScore(), "set-cr-init", 10, 0, 1000,
                       v -> cred.setInitialScore(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.loan_bonus"),
                       cred.getLoanCompletedBonus(), "set-cr-lbonus", 5, 0, 500,
                       v -> cred.setLoanCompletedBonus(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.loan_penalty"),
                       cred.getLoanDefaultPenalty(), "set-cr-lpen", 10, -1000, 0,
                       v -> cred.setLoanDefaultPenalty(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.ontime_bonus"),
                       cred.getOnTimePaymentBonus(), "set-cr-obonus", 1, 0, 100,
                       v -> cred.setOnTimePaymentBonus(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.late_penalty"),
                       cred.getLatePaymentPenalty(), "set-cr-latep", 5, -500, 0,
                       v -> cred.setLatePaymentPenalty(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.deposit_bonus"),
                       cred.getDepositCompletedBonus(), "set-cr-dbonus", 1, 0, 100,
                       v -> cred.setDepositCompletedBonus(v));
            }
            case "inflation" -> {
                addSubTabTitle(sb, L(lang, uuid, "gui.admin.settings.inflation_section"));

                addToggle(sb, actions, L(lang, uuid, "gui.admin.settings.enabled"),
                          infl.isEnabled(), "set-infl-on", v -> infl.setEnabled(v));
                addRate(sb, actions, L(lang, uuid, "gui.admin.settings.base_rate"),
                        infl.getBaseInflationRate(), "set-infl-base", 0.01, -1.0, 1.0,
                        v -> infl.setBaseInflationRate(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.update_interval"),
                       infl.getUpdateIntervalHours(), "set-infl-hrs", 1, 1, 168,
                       v -> infl.setUpdateIntervalHours(v));
                addRate(sb, actions, L(lang, uuid, "gui.admin.settings.max_rate"),
                        infl.getMaxInflationRate(), "set-infl-max", 0.01, 0.0, 1.0,
                        v -> infl.setMaxInflationRate(v));
                addRate(sb, actions, L(lang, uuid, "gui.admin.settings.min_rate"),
                        infl.getMinInflationRate(), "set-infl-min", 0.01, -1.0, 0.5,
                        v -> infl.setMinInflationRate(v));
            }
            case "protection" -> {
                addSubTabTitle(sb, L(lang, uuid, "gui.admin.settings.protection_section"));

                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.max_ops"),
                       prot.getMaxOperationsPerHour(), "set-prot-ops", 5, 1, 1000,
                       v -> prot.setMaxOperationsPerHour(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.deposit_cooldown"),
                       prot.getDepositCooldownSeconds(), "set-prot-dcool", 10, 0, 3600,
                       v -> prot.setDepositCooldownSeconds(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.loan_cooldown"),
                       prot.getLoanCooldownSeconds(), "set-prot-lcool", 30, 0, 3600,
                       v -> prot.setLoanCooldownSeconds(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.min_account_age"),
                       prot.getMinAccountAgeDaysForLoan(), "set-prot-age", 1, 0, 30,
                       v -> prot.setMinAccountAgeDaysForLoan(v));
                addToggle(sb, actions, L(lang, uuid, "gui.admin.settings.audit_log"),
                          prot.isAuditLogEnabled(), "set-prot-audit", v -> prot.setAuditLogEnabled(v));
                addInt(sb, actions, L(lang, uuid, "gui.admin.settings.max_audit"),
                       prot.getMaxAuditLogEntries(), "set-prot-maxlog", 100, 100, 100000,
                       v -> prot.setMaxAuditLogEntries(v));
            }
        }

        // ── Reset button ──────────────────────────────────────
        sb.append("""
            <div style="padding: 10 12; layout-mode: Left; anchor-height: 36;">
              <button id="settings-reset" class="small-secondary-button" style="flex-weight: 1; font-size: 12; color: #ff5555; font-weight: bold;">%s</button>
            </div>
            """.formatted(
                esc(L(lang, uuid, "gui.admin.settings.reset"))
        ));

        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════
    //  SETTINGS HELPERS
    // ═══════════════════════════════════════════════════════════

    private static void addSubTabTitle(StringBuilder sb, String title) {
        sb.append("""
            <div style="padding: 4 12 2 12; layout-mode: Left; anchor-height: 22;">
              <p style="color: #ffaa00; font-size: 13; font-weight: bold;">%s</p>
            </div>
            """.formatted(esc(title)));
    }

    private static void addToggle(StringBuilder sb, Map<String, Runnable> actions,
                                   String label, boolean value, String id,
                                   Consumer<Boolean> setter) {
        String bgColor  = value ? "#1a2e1a(0.6)" : "#2e1a1a(0.6)";
        String btnColor = value ? "#55ff55" : "#ff5555";
        String text     = value ? "ON" : "OFF";
        sb.append("""
            <div style="padding: 2 12; layout-mode: Left; background-color: %s; anchor-height: 28;">
              <p style="color: #cccccc; font-size: 11; flex-weight: 4;">%s</p>
              <button id="%s" class="small-secondary-button" style="flex-weight: 1.5; font-size: 13; color: %s; font-weight: bold;">%s</button>
            </div>
            """.formatted(bgColor, esc(label), id, btnColor, text));
        actions.put(id, () -> setter.accept(!value));
    }

    private static void addInt(StringBuilder sb, Map<String, Runnable> actions,
                                String label, int value, String id,
                                int step, int min, int max,
                                IntConsumer setter) {
        String dn = id + "-dn";
        String up = id + "-up";
        sb.append("""
            <div style="padding: 2 12; layout-mode: Left; background-color: #1a1a2e(0.5); anchor-height: 28;">
              <p style="color: #cccccc; font-size: 11; flex-weight: 4;">%s</p>
              <button id="%s" class="small-secondary-button" style="flex-weight: 0.5; font-size: 15; color: #ff6666; font-weight: bold;">-</button>
              <p style="color: #55ffff; font-size: 14; flex-weight: 1; font-weight: bold;">%d</p>
              <button id="%s" class="small-secondary-button" style="flex-weight: 0.5; font-size: 15; color: #66ff66; font-weight: bold;">+</button>
            </div>
            """.formatted(esc(label), dn, value, up));
        actions.put(dn, () -> setter.accept(Math.max(min, value - step)));
        actions.put(up, () -> setter.accept(Math.min(max, value + step)));
    }

    private static void addRate(StringBuilder sb, Map<String, Runnable> actions,
                                 String label, double value, String id,
                                 double step, double min, double max,
                                 DoubleConsumer setter) {
        String dn = id + "-dn";
        String up = id + "-up";
        String display = String.format("%.1f%%", value * 100);
        sb.append("""
            <div style="padding: 2 12; layout-mode: Left; background-color: #1a1a2e(0.5); anchor-height: 28;">
              <p style="color: #cccccc; font-size: 11; flex-weight: 4;">%s</p>
              <button id="%s" class="small-secondary-button" style="flex-weight: 0.5; font-size: 15; color: #ff6666; font-weight: bold;">-</button>
              <p style="color: #ffaa00; font-size: 14; flex-weight: 1; font-weight: bold;">%s</p>
              <button id="%s" class="small-secondary-button" style="flex-weight: 0.5; font-size: 15; color: #66ff66; font-weight: bold;">+</button>
            </div>
            """.formatted(esc(label), dn, esc(display), up));
        actions.put(dn, () -> setter.accept(Math.max(min, value - step)));
        actions.put(up, () -> setter.accept(Math.min(max, value + step)));
    }

    private static void addAmount(StringBuilder sb, Map<String, Runnable> actions,
                                   String label, double value, String id,
                                   double step, double min, double max,
                                   DoubleConsumer setter) {
        String dn = id + "-dn";
        String up = id + "-up";
        String display = String.format("%.0f $", value);
        sb.append("""
            <div style="padding: 2 12; layout-mode: Left; background-color: #1a1a2e(0.5); anchor-height: 28;">
              <p style="color: #cccccc; font-size: 11; flex-weight: 4;">%s</p>
              <button id="%s" class="small-secondary-button" style="flex-weight: 0.5; font-size: 15; color: #ff6666; font-weight: bold;">-</button>
              <p style="color: #55ff55; font-size: 14; flex-weight: 1; font-weight: bold;">%s</p>
              <button id="%s" class="small-secondary-button" style="flex-weight: 0.5; font-size: 15; color: #66ff66; font-weight: bold;">+</button>
            </div>
            """.formatted(esc(label), dn, esc(display), up));
        actions.put(dn, () -> setter.accept(Math.max(min, value - step)));
        actions.put(up, () -> setter.accept(Math.min(max, value + step)));
    }

    // ═══════════════════════════════════════════════════════════
    //  HTML HELPERS
    // ═══════════════════════════════════════════════════════════

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

    private static String emptyLabel(String text) {
        return "<p style=\"color: #888888; font-size: 13; padding: 16;\">"
                + esc(text) + "</p>\n";
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

    // ═══════════════════════════════════════════════════════════
    //  SHARED HELPERS
    // ═══════════════════════════════════════════════════════════

    private static String L(LangManager lang, UUID uuid, String key, String... args) {
        return lang.getForPlayer(uuid, key, args);
    }

    /**
     * Format audit log description into human-readable localized text.
     * Descriptions use "|" separated structured data.
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
            default -> raw; // legacy or non-structured descriptions
        };
    }

    private static String esc(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

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
