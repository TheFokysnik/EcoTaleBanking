package com.crystalrealm.ecotalebanking.gui;

import com.crystalrealm.ecotalebanking.EcoTaleBankingPlugin;
import com.crystalrealm.ecotalebanking.config.BankingConfig;
import com.crystalrealm.ecotalebanking.lang.LangManager;
import com.crystalrealm.ecotalebanking.model.*;
import com.crystalrealm.ecotalebanking.service.*;
import com.crystalrealm.ecotalebanking.storage.BankStorage;
import com.crystalrealm.ecotalebanking.util.MessageUtil;
import com.crystalrealm.ecotalebanking.util.MiniMessageParser;
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
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Native admin GUI for bank management.
 *
 * <p>Tabs:</p>
 * <ul>
 *   <li><b>Dashboard</b> — aggregate statistics</li>
 *   <li><b>Accounts</b> — all accounts with freeze/unfreeze</li>
 *   <li><b>Activity</b> — recent operations across all players</li>
 *   <li><b>Settings</b> — in-game config editor with sub-tabs</li>
 * </ul>
 *
 * @author CrystalRealm
 * @version 2.0.0
 */
public final class AdminBankGui extends InteractiveCustomUIPage<AdminBankGui.AdminEventData> {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private static final String PAGE_PATH = "Pages/CrystalRealm_EcoTaleBanking_AdminBank.ui";
    private static final String ACC_ROW   = "Pages/CrystalRealm_EcoTaleBanking_AccRow.ui";
    private static final String ACT_ROW   = "Pages/CrystalRealm_EcoTaleBanking_ActRow.ui";
    private static final String LOG_ROW   = "Pages/CrystalRealm_EcoTaleBanking_LogRow.ui";

    // ── Event data codec ────────────────────────────────────
    private static final String KEY_ACTION = "Action";
    private static final String KEY_ID     = "Id";
    private static final String KEY_TAB    = "Tab";

    static final BuilderCodec<AdminEventData> CODEC = ReflectiveCodecBuilder
            .<AdminEventData>create(AdminEventData.class, AdminEventData::new)
            .addStringField(KEY_ACTION, (d, v) -> d.action = v, d -> d.action)
            .addStringField(KEY_ID,     (d, v) -> d.id = v,     d -> d.id)
            .addStringField(KEY_TAB,    (d, v) -> d.tab = v,    d -> d.tab)
            .build();

    // ── Instance fields ─────────────────────────────────────
    private final EcoTaleBankingPlugin plugin;
    private final UUID adminUuid;
    private final String selectedTab;
    private String currentSettingsSubTab = "general";

    private Ref<EntityStore> savedRef;
    private Store<EntityStore> savedStore;

    public AdminBankGui(@Nonnull EcoTaleBankingPlugin plugin,
                        @Nonnull PlayerRef playerRef,
                        @Nonnull UUID adminUuid) {
        this(plugin, playerRef, adminUuid, "dashboard");
    }

    public AdminBankGui(@Nonnull EcoTaleBankingPlugin plugin,
                        @Nonnull PlayerRef playerRef,
                        @Nonnull UUID adminUuid,
                        @Nonnull String selectedTab) {
        super(playerRef, CustomPageLifetime.CanDismiss, CODEC);
        this.plugin = plugin;
        this.adminUuid = adminUuid;
        this.selectedTab = selectedTab;
    }

    // ════════════════════════════════════════════════════════
    //  BUILD
    // ════════════════════════════════════════════════════════

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        this.savedRef = ref;
        this.savedStore = store;

        LangManager lang     = plugin.getLangManager();
        BankService bank     = plugin.getBankService();
        BankStorage storage  = plugin.getStorage();

        MessageUtil.cachePlayerRef(adminUuid, playerRef);

        Collection<BankAccount> allAccounts = storage.getAllAccounts();
        Collection<CreditScore> allCredits  = storage.getAllCreditScores();

        // Parse settings sub-tab
        String settingsSubTab = "general";
        String mainSelectedTab = selectedTab;
        if (selectedTab.startsWith("settings:")) {
            mainSelectedTab = "settings";
            settingsSubTab = selectedTab.substring("settings:".length());
        }

        // Load root template
        cmd.append(PAGE_PATH);

        // Title
        cmd.set("#TitleLabel.Text", L(lang, "gui.admin.title"));

        // Tab labels
        cmd.set("#TabDashboard.Text", L(lang, "gui.admin.tab.dashboard"));
        cmd.set("#TabAccounts.Text", L(lang, "gui.admin.tab.accounts"));
        cmd.set("#TabActivity.Text", L(lang, "gui.admin.tab.activity"));
        cmd.set("#TabSettings.Text", L(lang, "gui.admin.tab.settings"));

        // Tab visibility
        cmd.set("#DashboardContent.Visible", "dashboard".equals(mainSelectedTab));
        cmd.set("#AccountsContent.Visible",  "accounts".equals(mainSelectedTab));
        cmd.set("#ActivityContent.Visible",  "activity".equals(mainSelectedTab));
        cmd.set("#SettingsContent.Visible",  "settings".equals(mainSelectedTab));

        // Tab switching events
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabDashboard",
                new EventData().append(KEY_ACTION, "tab").append(KEY_TAB, "dashboard"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabAccounts",
                new EventData().append(KEY_ACTION, "tab").append(KEY_TAB, "accounts"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabActivity",
                new EventData().append(KEY_ACTION, "tab").append(KEY_TAB, "activity"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabSettings",
                new EventData().append(KEY_ACTION, "tab").append(KEY_TAB, "settings:general"));

        // Build tabs
        buildDashboardTab(cmd, lang, bank, allAccounts, allCredits);
        buildAccountsTab(cmd, events, lang, bank, allAccounts, allCredits);
        buildActivityTab(cmd, lang, bank, allAccounts);
        buildSettingsTab(cmd, events, lang, settingsSubTab);

        LOGGER.info("Admin bank GUI built for {}", adminUuid);
    }

    // ════════════════════════════════════════════════════════
    //  HANDLE EVENTS
    // ════════════════════════════════════════════════════════

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull AdminEventData data) {
        LangManager lang    = plugin.getLangManager();
        BankService bank    = plugin.getBankService();
        BankStorage storage = plugin.getStorage();

        switch (data.action) {
            case "tab" -> {
                try {
                    String mainTab = data.tab;
                    if (data.tab.startsWith("settings:")) mainTab = "settings";
                    UICommandBuilder tabCmd = new UICommandBuilder();
                    tabCmd.set("#DashboardContent.Visible", "dashboard".equals(mainTab));
                    tabCmd.set("#AccountsContent.Visible", "accounts".equals(mainTab));
                    tabCmd.set("#ActivityContent.Visible", "activity".equals(mainTab));
                    tabCmd.set("#SettingsContent.Visible", "settings".equals(mainTab));
                    sendUpdate(tabCmd);
                } catch (Exception e) {
                    LOGGER.warn("[tab] sendUpdate failed, falling back to reopen: {}", e.getMessage());
                    reopen(data.tab.startsWith("settings:") ? "settings" : data.tab);
                }
            }

            case "freeze" -> {
                UUID targetUuid = UUID.fromString(data.id);
                BankAccount acc = bank.getAccount(targetUuid);
                acc.setFrozen(true, "Frozen by admin via GUI");
                storage.saveAccount(acc);
                sendMsg(L(lang, "gui.admin.frozen_success",
                        "uuid", data.id.substring(0, 8)));
                reopen("accounts");
            }

            case "unfreeze" -> {
                UUID targetUuid = UUID.fromString(data.id);
                BankAccount acc = bank.getAccount(targetUuid);
                acc.setFrozen(false, null);
                storage.saveAccount(acc);
                sendMsg(L(lang, "gui.admin.unfrozen_success",
                        "uuid", data.id.substring(0, 8)));
                reopen("accounts");
            }

            case "settings_subtab" -> {
                try {
                    currentSettingsSubTab = data.tab;
                    UICommandBuilder tabCmd = new UICommandBuilder();
                    tabCmd.set("#SetGenContent.Visible", "general".equals(data.tab));
                    tabCmd.set("#SetDepContent.Visible", "deposits".equals(data.tab));
                    tabCmd.set("#SetLoanContent.Visible", "loans".equals(data.tab));
                    tabCmd.set("#SetCredContent.Visible", "credit".equals(data.tab));
                    tabCmd.set("#SetInflContent.Visible", "inflation".equals(data.tab));
                    tabCmd.set("#SetProtContent.Visible", "protection".equals(data.tab));
                    tabCmd.set("#SettingsSubTitle.Text", stripDecorators(L(lang, getSubTabKey(data.tab))));
                    sendUpdate(tabCmd);
                } catch (Exception e) {
                    LOGGER.warn("[settings_subtab] sendUpdate failed: {}", e.getMessage());
                    reopen("settings:" + data.tab);
                }
            }

            case "settings_reset" -> {
                plugin.getConfigManager().resetToDefaults();
                plugin.getConfigManager().save();
                sendMsg(L(lang, "gui.admin.settings.reset_success"));
                reopen(selectedTab.startsWith("settings") ? selectedTab : "settings:general");
            }

            case "settings_save_config" -> {
                boolean saved = plugin.getConfigManager().save();
                if (saved) {
                    sendMsg(L(lang, "gui.admin.settings.save_config_success"));
                } else {
                    sendMsg(L(lang, "gui.admin.settings.save_config_fail"));
                }
            }

            case "settings_reload_plugin" -> {
                boolean success = plugin.getConfigManager().reload();
                if (success) {
                    String newLang = plugin.getConfigManager().getConfig().getGeneral().getLanguage();
                    plugin.getLangManager().reload(newLang);
                    sendMsg(L(lang, "gui.admin.settings.reload_plugin_success"));
                    reopen(selectedTab.startsWith("settings") ? selectedTab : "settings:general");
                } else {
                    sendMsg(L(lang, "gui.admin.settings.reload_plugin_fail"));
                }
            }

            // Settings value changes
            case "set" -> {
                applySettingsChange(data.id);
                plugin.getConfigManager().save();
                try {
                    UICommandBuilder cmd = new UICommandBuilder();
                    refreshSettingsValues(cmd);
                    sendUpdate(cmd);
                } catch (Exception e) {
                    LOGGER.warn("[set] sendUpdate failed, falling back to reopen: {}", e.getMessage());
                    reopen("settings:" + currentSettingsSubTab);
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  TAB: Dashboard
    // ════════════════════════════════════════════════════════

    private void buildDashboardTab(UICommandBuilder cmd, LangManager lang,
                                   BankService bank,
                                   Collection<BankAccount> accounts,
                                   Collection<CreditScore> credits) {
        int totalAccounts = accounts.size();
        int frozenCount   = (int) accounts.stream().filter(BankAccount::isFrozen).count();
        int totalDeposits = accounts.stream().mapToInt(a -> a.getActiveDeposits().size()).sum();
        int totalLoans    = accounts.stream().mapToInt(a -> a.getActiveLoans().size()).sum();
        int overdueLoans  = (int) accounts.stream()
                .flatMap(a -> a.getActiveLoans().stream())
                .filter(Loan::isOverdue).count();

        BigDecimal totalDeposited = accounts.stream()
                .map(BankAccount::getTotalDeposited)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDebt = accounts.stream()
                .map(BankAccount::getTotalDebt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double avgCredit = credits.stream()
                .mapToInt(CreditScore::getScore).average().orElse(0);

        cmd.set("#DashTitle.Text", L(lang, "gui.admin.dashboard_title"));

        // Row 1
        cmd.set("#DashAccountsLabel.Text", L(lang, "gui.admin.total_accounts"));
        cmd.set("#DashAccountsValue.Text", String.valueOf(totalAccounts));
        cmd.set("#DashDepositsLabel.Text", L(lang, "gui.admin.total_deposits"));
        cmd.set("#DashDepositsValue.Text", String.valueOf(totalDeposits));
        cmd.set("#DashLoansLabel.Text", L(lang, "gui.admin.total_loans"));
        cmd.set("#DashLoansValue.Text", String.valueOf(totalLoans));
        cmd.set("#DashFrozenLabel.Text", L(lang, "gui.admin.frozen_accounts"));
        cmd.set("#DashFrozenValue.Text", String.valueOf(frozenCount));

        // Row 2
        cmd.set("#DashTotalDepLabel.Text", L(lang, "gui.admin.total_deposited"));
        cmd.set("#DashTotalDepValue.Text", MessageUtil.formatCoins(totalDeposited) + " $");
        cmd.set("#DashTotalDebtLabel.Text", L(lang, "gui.admin.total_debt"));
        cmd.set("#DashTotalDebtValue.Text", MessageUtil.formatCoins(totalDebt) + " $");
        cmd.set("#DashOverdueLabel.Text", L(lang, "gui.admin.overdue_loans"));
        cmd.set("#DashOverdueValue.Text", String.valueOf(overdueLoans));

        // Row 3
        cmd.set("#DashAvgCreditLabel.Text", L(lang, "gui.admin.avg_credit"));
        cmd.set("#DashAvgCreditValue.Text", String.format("%.0f / 1000", avgCredit));

        if (bank.getInflationService().isEnabled()) {
            cmd.set("#DashInflation.Visible", true);
            cmd.set("#DashInflationLabel.Text", L(lang, "gui.admin.inflation"));
            cmd.set("#DashInflationValue.Text", MessageUtil.formatPercent(
                    bank.getInflationService().getCurrentRate()));
        }
    }

    // ════════════════════════════════════════════════════════
    //  TAB: Accounts
    // ════════════════════════════════════════════════════════

    private void buildAccountsTab(UICommandBuilder cmd, UIEventBuilder events,
                                  LangManager lang, BankService bank,
                                  Collection<BankAccount> accounts,
                                  Collection<CreditScore> credits) {
        // Header
        cmd.set("#AccHdrPlayer.Text", L(lang, "gui.admin.col.player"));
        cmd.set("#AccHdrDeposits.Text", L(lang, "gui.admin.col.deposits"));
        cmd.set("#AccHdrDebt.Text", L(lang, "gui.admin.col.debt"));
        cmd.set("#AccHdrCredit.Text", L(lang, "gui.admin.col.credit"));
        cmd.set("#AccHdrStatus.Text", L(lang, "gui.admin.col.status"));
        cmd.set("#AccHdrAction.Text", L(lang, "gui.admin.col.action"));

        Map<UUID, CreditScore> creditMap = new HashMap<>();
        for (CreditScore c : credits) creditMap.put(c.getPlayerUuid(), c);

        if (accounts.isEmpty()) {
            cmd.append("#AccountsContainer", LOG_ROW);
            cmd.set("#LogDesc.Text", L(lang, "gui.admin.no_accounts"));
        } else {
            List<BankAccount> sorted = accounts.stream()
                    .sorted(Comparator.comparing(BankAccount::isFrozen).reversed()
                            .thenComparing(a -> a.getTotalDebt().negate()))
                    .collect(Collectors.toList());

            for (BankAccount acc : sorted) {
                UUID playerUuid = acc.getPlayerUuid();
                String shortId = playerUuid.toString().substring(0, 8);
                String displayName = acc.getLastKnownName() != null
                        ? acc.getLastKnownName() : shortId;
                CreditScore cs = creditMap.getOrDefault(playerUuid, new CreditScore(playerUuid));

                cmd.append("#AccountsContainer", ACC_ROW);

                cmd.set("#AccPlayer.Text", displayName);
                cmd.set("#AccDeposits.Text", MessageUtil.formatCoins(acc.getTotalDeposited())
                        + " $ (" + acc.getActiveDeposits().size() + ")");
                cmd.set("#AccDebt.Text", MessageUtil.formatCoins(acc.getTotalDebt())
                        + " $ (" + acc.getActiveLoans().size() + ")");
                cmd.set("#AccCredit.Text", String.valueOf(cs.getScore()));

                if (acc.isFrozen()) {
                    cmd.set("#AccStatus.Text", L(lang, "gui.admin.status.frozen"));
                    cmd.set("#AccAction.Text", L(lang, "gui.admin.btn.unfreeze"));
                    events.addEventBinding(CustomUIEventBindingType.Activating, "#AccAction",
                            new EventData().append(KEY_ACTION, "unfreeze")
                                    .append(KEY_ID, playerUuid.toString()));
                } else {
                    cmd.set("#AccStatus.Text", L(lang, "gui.admin.status.active"));
                    cmd.set("#AccAction.Text", L(lang, "gui.admin.btn.freeze"));
                    events.addEventBinding(CustomUIEventBindingType.Activating, "#AccAction",
                            new EventData().append(KEY_ACTION, "freeze")
                                    .append(KEY_ID, playerUuid.toString()));
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  TAB: Activity
    // ════════════════════════════════════════════════════════

    private void buildActivityTab(UICommandBuilder cmd, LangManager lang,
                                  BankService bank, Collection<BankAccount> accounts) {
        Map<UUID, String> nameMap = new HashMap<>();
        for (BankAccount acc : accounts) {
            if (acc.getLastKnownName() != null) {
                nameMap.put(acc.getPlayerUuid(), acc.getLastKnownName());
            }
        }

        List<AuditLog> allLogs = new ArrayList<>();
        for (BankAccount acc : accounts) {
            allLogs.addAll(bank.getAuditLogs(acc.getPlayerUuid(), 5));
        }
        allLogs.sort(Comparator.comparing(AuditLog::getTimestamp).reversed());
        if (allLogs.size() > 30) allLogs = allLogs.subList(0, 30);

        if (allLogs.isEmpty()) {
            cmd.append("#ActivityContainer", LOG_ROW);
            cmd.set("#LogDesc.Text", L(lang, "gui.admin.no_activity"));
        } else {
            for (AuditLog log : allLogs) {
                String playerName = nameMap.getOrDefault(log.getPlayerUuid(),
                        log.getPlayerUuid().toString().substring(0, 8));
                String typeName = L(lang, "txtype." + log.getType().name());
                String desc = formatAuditDescription(lang, log);

                cmd.append("#ActivityContainer", ACT_ROW);
                cmd.set("#ActPlayer.Text", playerName);
                cmd.set("#ActType.Text", typeName);
                cmd.set("#ActAmount.Text", MessageUtil.formatCoins(log.getAmount()) + " $");
                cmd.set("#ActDesc.Text", desc);
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  TAB: Settings (unique IDs per row — no templates)
    // ════════════════════════════════════════════════════════

    private void buildSettingsTab(UICommandBuilder cmd, UIEventBuilder events,
                                  LangManager lang, String activeSubTab) {
        BankingConfig config = plugin.getConfigManager().getConfig();
        var gen  = config.getGeneral();
        var dep  = config.getDeposits();
        var loan = config.getLoans();
        var cred = config.getCredit();
        var infl = config.getInflation();
        var prot = config.getProtection();

        // Sub-tab button labels (short first word only)
        cmd.set("#STabGeneral.Text", shortLabel(L(lang, "gui.admin.settings.general")));
        cmd.set("#STabDeposits.Text", shortLabel(L(lang, "gui.admin.settings.deposits_section")));
        cmd.set("#STabLoans.Text", shortLabel(L(lang, "gui.admin.settings.loans_section")));
        cmd.set("#STabCredit.Text", shortLabel(L(lang, "gui.admin.settings.credit_section")));
        cmd.set("#STabInflation.Text", shortLabel(L(lang, "gui.admin.settings.inflation_section")));
        cmd.set("#STabProtection.Text", shortLabel(L(lang, "gui.admin.settings.protection_section")));

        // Sub-tab button events
        events.addEventBinding(CustomUIEventBindingType.Activating, "#STabGeneral",
                new EventData().append(KEY_ACTION, "settings_subtab").append(KEY_TAB, "general"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#STabDeposits",
                new EventData().append(KEY_ACTION, "settings_subtab").append(KEY_TAB, "deposits"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#STabLoans",
                new EventData().append(KEY_ACTION, "settings_subtab").append(KEY_TAB, "loans"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#STabCredit",
                new EventData().append(KEY_ACTION, "settings_subtab").append(KEY_TAB, "credit"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#STabInflation",
                new EventData().append(KEY_ACTION, "settings_subtab").append(KEY_TAB, "inflation"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#STabProtection",
                new EventData().append(KEY_ACTION, "settings_subtab").append(KEY_TAB, "protection"));

        // Sub-content visibility
        cmd.set("#SetGenContent.Visible",  "general".equals(activeSubTab));
        cmd.set("#SetDepContent.Visible",  "deposits".equals(activeSubTab));
        cmd.set("#SetLoanContent.Visible", "loans".equals(activeSubTab));
        cmd.set("#SetCredContent.Visible", "credit".equals(activeSubTab));
        cmd.set("#SetInflContent.Visible", "inflation".equals(activeSubTab));
        cmd.set("#SetProtContent.Visible", "protection".equals(activeSubTab));

        cmd.set("#SettingsSubTitle.Text", stripDecorators(L(lang, getSubTabKey(activeSubTab))));

        // Reset button
        cmd.set("#SettingsReset.Text", L(lang, "gui.admin.settings.reset"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SettingsReset",
                new EventData().append(KEY_ACTION, "settings_reset"));

        // Save Config to disk button
        cmd.set("#SettingsSaveConfig.Text", L(lang, "gui.admin.settings.save_config"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SettingsSaveConfig",
                new EventData().append(KEY_ACTION, "settings_save_config"));

        // Reload Plugin button
        cmd.set("#SettingsReloadPlugin.Text", L(lang, "gui.admin.settings.reload_plugin"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SettingsReloadPlugin",
                new EventData().append(KEY_ACTION, "settings_reload_plugin"));

        // ── General ──
        cmd.set("#SGDebugLbl.Text", L(lang, "gui.admin.settings.debug_mode"));
        cmd.set("#SGDebugTgl.Text", gen.isDebugMode() ? "ON" : "OFF");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SGDebugTgl",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "debug_mode"));

        cmd.set("#SGAutoLbl.Text", L(lang, "gui.admin.settings.autosave"));
        cmd.set("#SGAutoVal.Text", String.valueOf(gen.getAutoSaveMinutes()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SGAutoDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "autosave_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SGAutoUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "autosave_up"));

        cmd.set("#SGDayLbl.Text", L(lang, "gui.admin.settings.game_day_duration"));
        cmd.set("#SGDayVal.Text", String.valueOf(gen.getSecondsPerGameDay()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SGDayDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "gameday_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SGDayUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "gameday_up"));

        cmd.set("#SGLangLbl.Text", L(lang, "gui.admin.settings.language"));
        cmd.set("#SGLangTgl.Text", gen.getLanguage().toUpperCase());
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SGLangTgl",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "language"));

        // ── Deposits ──
        cmd.set("#SDEnabledLbl.Text", L(lang, "gui.admin.settings.enabled"));
        cmd.set("#SDEnabledTgl.Text", dep.isEnabled() ? "ON" : "OFF");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SDEnabledTgl",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "dep_enabled"));

        cmd.set("#SDMaxLbl.Text", L(lang, "gui.admin.settings.max_per_player"));
        cmd.set("#SDMaxVal.Text", String.valueOf(dep.getMaxPerPlayer()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SDMaxDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "dep_max_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SDMaxUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "dep_max_up"));

        cmd.set("#SDPenLbl.Text", L(lang, "gui.admin.settings.early_penalty"));
        cmd.set("#SDPenVal.Text", String.format("%.1f%%", dep.getEarlyWithdrawalPenaltyRate() * 100));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SDPenDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "dep_penalty_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SDPenUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "dep_penalty_up"));

        // ── Loans ──
        cmd.set("#SLEnabledLbl.Text", L(lang, "gui.admin.settings.enabled"));
        cmd.set("#SLEnabledTgl.Text", loan.isEnabled() ? "ON" : "OFF");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLEnabledTgl",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_enabled"));

        cmd.set("#SLRateLbl.Text", L(lang, "gui.admin.settings.interest_rate"));
        cmd.set("#SLRateVal.Text", String.format("%.1f%%", loan.getBaseInterestRate() * 100));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLRateDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_rate_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLRateUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_rate_up"));

        cmd.set("#SLMinLbl.Text", L(lang, "gui.admin.settings.min_amount"));
        cmd.set("#SLMinVal.Text", String.format("%.0f $", loan.getMinAmount()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLMinDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_min_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLMinUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_min_up"));

        cmd.set("#SLMaxLbl.Text", L(lang, "gui.admin.settings.max_amount"));
        cmd.set("#SLMaxVal.Text", String.format("%.0f $", loan.getMaxAmount()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLMaxDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_max_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLMaxUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_max_up"));

        cmd.set("#SLActLbl.Text", L(lang, "gui.admin.settings.max_active"));
        cmd.set("#SLActVal.Text", String.valueOf(loan.getMaxActiveLoans()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLActDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_active_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLActUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_active_up"));

        cmd.set("#SLTermLbl.Text", L(lang, "gui.admin.settings.term_days"));
        cmd.set("#SLTermVal.Text", String.valueOf(loan.getDefaultTermDays()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLTermDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_term_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLTermUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_term_up"));

        cmd.set("#SLOvdLbl.Text", L(lang, "gui.admin.settings.overdue_penalty"));
        cmd.set("#SLOvdVal.Text", String.format("%.1f%%", loan.getOverduePenaltyRate() * 100));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLOvdDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_overdue_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLOvdUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_overdue_up"));

        cmd.set("#SLDefLbl.Text", L(lang, "gui.admin.settings.default_after"));
        cmd.set("#SLDefVal.Text", String.valueOf(loan.getDefaultAfterDays()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLDefDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_defdays_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLDefUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_defdays_up"));

        cmd.set("#SLColLbl.Text", L(lang, "gui.admin.settings.collateral"));
        cmd.set("#SLColVal.Text", String.format("%.1f%%", loan.getCollateralRate() * 100));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLColDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_coll_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLColUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_coll_up"));

        cmd.set("#SLCrdLbl.Text", L(lang, "gui.admin.settings.min_credit"));
        cmd.set("#SLCrdVal.Text", String.valueOf(loan.getMinCreditScoreForLoan()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLCrdDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_mincr_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SLCrdUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "loan_mincr_up"));

        // ── Credit ──
        cmd.set("#SCInitLbl.Text", L(lang, "gui.admin.settings.initial_score"));
        cmd.set("#SCInitVal.Text", String.valueOf(cred.getInitialScore()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SCInitDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "cr_init_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SCInitUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "cr_init_up"));

        cmd.set("#SCLBonLbl.Text", L(lang, "gui.admin.settings.loan_bonus"));
        cmd.set("#SCLBonVal.Text", String.valueOf(cred.getLoanCompletedBonus()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SCLBonDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "cr_lbonus_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SCLBonUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "cr_lbonus_up"));

        cmd.set("#SCLPenLbl.Text", L(lang, "gui.admin.settings.loan_penalty"));
        cmd.set("#SCLPenVal.Text", String.valueOf(cred.getLoanDefaultPenalty()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SCLPenDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "cr_lpen_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SCLPenUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "cr_lpen_up"));

        cmd.set("#SCOTBLbl.Text", L(lang, "gui.admin.settings.ontime_bonus"));
        cmd.set("#SCOTBVal.Text", String.valueOf(cred.getOnTimePaymentBonus()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SCOTBDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "cr_obonus_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SCOTBUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "cr_obonus_up"));

        cmd.set("#SCLatLbl.Text", L(lang, "gui.admin.settings.late_penalty"));
        cmd.set("#SCLatVal.Text", String.valueOf(cred.getLatePaymentPenalty()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SCLatDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "cr_latep_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SCLatUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "cr_latep_up"));

        cmd.set("#SCDBonLbl.Text", L(lang, "gui.admin.settings.deposit_bonus"));
        cmd.set("#SCDBonVal.Text", String.valueOf(cred.getDepositCompletedBonus()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SCDBonDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "cr_dbonus_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SCDBonUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "cr_dbonus_up"));

        // ── Inflation ──
        cmd.set("#SIEnabledLbl.Text", L(lang, "gui.admin.settings.enabled"));
        cmd.set("#SIEnabledTgl.Text", infl.isEnabled() ? "ON" : "OFF");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SIEnabledTgl",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "infl_enabled"));

        cmd.set("#SIBaseLbl.Text", L(lang, "gui.admin.settings.base_rate"));
        cmd.set("#SIBaseVal.Text", String.format("%.1f%%", infl.getBaseInflationRate() * 100));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SIBaseDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "infl_base_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SIBaseUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "infl_base_up"));

        cmd.set("#SIIntLbl.Text", L(lang, "gui.admin.settings.update_interval"));
        cmd.set("#SIIntVal.Text", String.valueOf(infl.getUpdateIntervalHours()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SIIntDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "infl_hrs_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SIIntUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "infl_hrs_up"));

        cmd.set("#SIMaxLbl.Text", L(lang, "gui.admin.settings.max_rate"));
        cmd.set("#SIMaxVal.Text", String.format("%.1f%%", infl.getMaxInflationRate() * 100));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SIMaxDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "infl_max_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SIMaxUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "infl_max_up"));

        cmd.set("#SIMinLbl.Text", L(lang, "gui.admin.settings.min_rate"));
        cmd.set("#SIMinVal.Text", String.format("%.1f%%", infl.getMinInflationRate() * 100));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SIMinDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "infl_min_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SIMinUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "infl_min_up"));

        // ── Protection ──
        cmd.set("#SPOpsLbl.Text", L(lang, "gui.admin.settings.max_ops"));
        cmd.set("#SPOpsVal.Text", String.valueOf(prot.getMaxOperationsPerHour()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SPOpsDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "prot_ops_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SPOpsUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "prot_ops_up"));

        cmd.set("#SPDCLbl.Text", L(lang, "gui.admin.settings.deposit_cooldown"));
        cmd.set("#SPDCVal.Text", String.valueOf(prot.getDepositCooldownSeconds()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SPDCDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "prot_dcool_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SPDCUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "prot_dcool_up"));

        cmd.set("#SPLCLbl.Text", L(lang, "gui.admin.settings.loan_cooldown"));
        cmd.set("#SPLCVal.Text", String.valueOf(prot.getLoanCooldownSeconds()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SPLCDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "prot_lcool_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SPLCUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "prot_lcool_up"));

        cmd.set("#SPAgeLbl.Text", L(lang, "gui.admin.settings.min_account_age"));
        cmd.set("#SPAgeVal.Text", String.valueOf(prot.getMinAccountAgeDaysForLoan()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SPAgeDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "prot_age_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SPAgeUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "prot_age_up"));

        cmd.set("#SPAudLbl.Text", L(lang, "gui.admin.settings.audit_log"));
        cmd.set("#SPAudTgl.Text", prot.isAuditLogEnabled() ? "ON" : "OFF");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SPAudTgl",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "prot_audit"));

        cmd.set("#SPMLLbl.Text", L(lang, "gui.admin.settings.max_audit"));
        cmd.set("#SPMLVal.Text", String.valueOf(prot.getMaxAuditLogEntries()));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SPMLDn",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "prot_maxlog_down"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#SPMLUp",
                new EventData().append(KEY_ACTION, "set").append(KEY_ID, "prot_maxlog_up"));
    }

    // ── Refresh all settings values via sendUpdate ────────────

    private void refreshSettingsValues(UICommandBuilder cmd) {
        BankingConfig config = plugin.getConfigManager().getConfig();
        var gen  = config.getGeneral();
        var dep  = config.getDeposits();
        var loan = config.getLoans();
        var cred = config.getCredit();
        var infl = config.getInflation();
        var prot = config.getProtection();

        // General
        cmd.set("#SGDebugTgl.Text", gen.isDebugMode() ? "ON" : "OFF");
        cmd.set("#SGAutoVal.Text", String.valueOf(gen.getAutoSaveMinutes()));
        cmd.set("#SGDayVal.Text", String.valueOf(gen.getSecondsPerGameDay()));
        cmd.set("#SGLangTgl.Text", gen.getLanguage().toUpperCase());

        // Deposits
        cmd.set("#SDEnabledTgl.Text", dep.isEnabled() ? "ON" : "OFF");
        cmd.set("#SDMaxVal.Text", String.valueOf(dep.getMaxPerPlayer()));
        cmd.set("#SDPenVal.Text", String.format("%.1f%%", dep.getEarlyWithdrawalPenaltyRate() * 100));

        // Loans
        cmd.set("#SLEnabledTgl.Text", loan.isEnabled() ? "ON" : "OFF");
        cmd.set("#SLRateVal.Text", String.format("%.1f%%", loan.getBaseInterestRate() * 100));
        cmd.set("#SLMinVal.Text", String.format("%.0f $", loan.getMinAmount()));
        cmd.set("#SLMaxVal.Text", String.format("%.0f $", loan.getMaxAmount()));
        cmd.set("#SLActVal.Text", String.valueOf(loan.getMaxActiveLoans()));
        cmd.set("#SLTermVal.Text", String.valueOf(loan.getDefaultTermDays()));
        cmd.set("#SLOvdVal.Text", String.format("%.1f%%", loan.getOverduePenaltyRate() * 100));
        cmd.set("#SLDefVal.Text", String.valueOf(loan.getDefaultAfterDays()));
        cmd.set("#SLColVal.Text", String.format("%.1f%%", loan.getCollateralRate() * 100));
        cmd.set("#SLCrdVal.Text", String.valueOf(loan.getMinCreditScoreForLoan()));

        // Credit
        cmd.set("#SCInitVal.Text", String.valueOf(cred.getInitialScore()));
        cmd.set("#SCLBonVal.Text", String.valueOf(cred.getLoanCompletedBonus()));
        cmd.set("#SCLPenVal.Text", String.valueOf(cred.getLoanDefaultPenalty()));
        cmd.set("#SCOTBVal.Text", String.valueOf(cred.getOnTimePaymentBonus()));
        cmd.set("#SCLatVal.Text", String.valueOf(cred.getLatePaymentPenalty()));
        cmd.set("#SCDBonVal.Text", String.valueOf(cred.getDepositCompletedBonus()));

        // Inflation
        cmd.set("#SIEnabledTgl.Text", infl.isEnabled() ? "ON" : "OFF");
        cmd.set("#SIBaseVal.Text", String.format("%.1f%%", infl.getBaseInflationRate() * 100));
        cmd.set("#SIIntVal.Text", String.valueOf(infl.getUpdateIntervalHours()));
        cmd.set("#SIMaxVal.Text", String.format("%.1f%%", infl.getMaxInflationRate() * 100));
        cmd.set("#SIMinVal.Text", String.format("%.1f%%", infl.getMinInflationRate() * 100));

        // Protection
        cmd.set("#SPOpsVal.Text", String.valueOf(prot.getMaxOperationsPerHour()));
        cmd.set("#SPDCVal.Text", String.valueOf(prot.getDepositCooldownSeconds()));
        cmd.set("#SPLCVal.Text", String.valueOf(prot.getLoanCooldownSeconds()));
        cmd.set("#SPAgeVal.Text", String.valueOf(prot.getMinAccountAgeDaysForLoan()));
        cmd.set("#SPAudTgl.Text", prot.isAuditLogEnabled() ? "ON" : "OFF");
        cmd.set("#SPMLVal.Text", String.valueOf(prot.getMaxAuditLogEntries()));
    }

    // ── Apply settings change ───────────────────────────────

    private void applySettingsChange(String settingId) {
        BankingConfig config = plugin.getConfigManager().getConfig();
        var gen  = config.getGeneral();
        var dep  = config.getDeposits();
        var loan = config.getLoans();
        var cred = config.getCredit();
        var infl = config.getInflation();
        var prot = config.getProtection();

        switch (settingId) {
            // General
            case "debug_mode"       -> gen.setDebugMode(!gen.isDebugMode());
            case "language"         -> {
                java.util.List<String> langs = java.util.List.of("en", "ru", "pt_br", "fr", "de", "es");
                int idx = langs.indexOf(gen.getLanguage());
                gen.setLanguage(langs.get((idx + 1) % langs.size()));
            }
            case "autosave_up"      -> gen.setAutoSaveMinutes(Math.min(60, gen.getAutoSaveMinutes() + 1));
            case "autosave_down"    -> gen.setAutoSaveMinutes(Math.max(1, gen.getAutoSaveMinutes() - 1));
            case "gameday_up"       -> gen.setSecondsPerGameDay(Math.min(86400, gen.getSecondsPerGameDay() + 60));
            case "gameday_down"     -> gen.setSecondsPerGameDay(Math.max(60, gen.getSecondsPerGameDay() - 60));

            // Deposits
            case "dep_enabled"      -> dep.setEnabled(!dep.isEnabled());
            case "dep_max_up"       -> dep.setMaxPerPlayer(Math.min(10, dep.getMaxPerPlayer() + 1));
            case "dep_max_down"     -> dep.setMaxPerPlayer(Math.max(1, dep.getMaxPerPlayer() - 1));
            case "dep_penalty_up"   -> dep.setEarlyWithdrawalPenaltyRate(Math.min(1.0, dep.getEarlyWithdrawalPenaltyRate() + 0.01));
            case "dep_penalty_down" -> dep.setEarlyWithdrawalPenaltyRate(Math.max(0.0, dep.getEarlyWithdrawalPenaltyRate() - 0.01));

            // Loans
            case "loan_enabled"     -> loan.setEnabled(!loan.isEnabled());
            case "loan_rate_up"     -> loan.setBaseInterestRate(Math.min(1.0, loan.getBaseInterestRate() + 0.01));
            case "loan_rate_down"   -> loan.setBaseInterestRate(Math.max(0.01, loan.getBaseInterestRate() - 0.01));
            case "loan_min_up"      -> loan.setMinAmount(Math.min(1000000, loan.getMinAmount() + 100));
            case "loan_min_down"    -> loan.setMinAmount(Math.max(0, loan.getMinAmount() - 100));
            case "loan_max_up"      -> loan.setMaxAmount(Math.min(10000000, loan.getMaxAmount() + 1000));
            case "loan_max_down"    -> loan.setMaxAmount(Math.max(100, loan.getMaxAmount() - 1000));
            case "loan_active_up"   -> loan.setMaxActiveLoans(Math.min(10, loan.getMaxActiveLoans() + 1));
            case "loan_active_down" -> loan.setMaxActiveLoans(Math.max(1, loan.getMaxActiveLoans() - 1));
            case "loan_term_up"     -> loan.setDefaultTermDays(Math.min(365, loan.getDefaultTermDays() + 1));
            case "loan_term_down"   -> loan.setDefaultTermDays(Math.max(1, loan.getDefaultTermDays() - 1));
            case "loan_overdue_up"  -> loan.setOverduePenaltyRate(Math.min(1.0, loan.getOverduePenaltyRate() + 0.01));
            case "loan_overdue_down"-> loan.setOverduePenaltyRate(Math.max(0.0, loan.getOverduePenaltyRate() - 0.01));
            case "loan_defdays_up"  -> loan.setDefaultAfterDays(Math.min(90, loan.getDefaultAfterDays() + 1));
            case "loan_defdays_down"-> loan.setDefaultAfterDays(Math.max(1, loan.getDefaultAfterDays() - 1));
            case "loan_coll_up"     -> loan.setCollateralRate(Math.min(1.0, loan.getCollateralRate() + 0.01));
            case "loan_coll_down"   -> loan.setCollateralRate(Math.max(0.0, loan.getCollateralRate() - 0.01));
            case "loan_mincr_up"    -> loan.setMinCreditScoreForLoan(Math.min(1000, loan.getMinCreditScoreForLoan() + 10));
            case "loan_mincr_down"  -> loan.setMinCreditScoreForLoan(Math.max(0, loan.getMinCreditScoreForLoan() - 10));

            // Credit
            case "cr_init_up"       -> cred.setInitialScore(Math.min(1000, cred.getInitialScore() + 10));
            case "cr_init_down"     -> cred.setInitialScore(Math.max(0, cred.getInitialScore() - 10));
            case "cr_lbonus_up"     -> cred.setLoanCompletedBonus(Math.min(500, cred.getLoanCompletedBonus() + 5));
            case "cr_lbonus_down"   -> cred.setLoanCompletedBonus(Math.max(0, cred.getLoanCompletedBonus() - 5));
            case "cr_lpen_up"       -> cred.setLoanDefaultPenalty(Math.min(0, cred.getLoanDefaultPenalty() + 10));
            case "cr_lpen_down"     -> cred.setLoanDefaultPenalty(Math.max(-1000, cred.getLoanDefaultPenalty() - 10));
            case "cr_obonus_up"     -> cred.setOnTimePaymentBonus(Math.min(100, cred.getOnTimePaymentBonus() + 1));
            case "cr_obonus_down"   -> cred.setOnTimePaymentBonus(Math.max(0, cred.getOnTimePaymentBonus() - 1));
            case "cr_latep_up"      -> cred.setLatePaymentPenalty(Math.min(0, cred.getLatePaymentPenalty() + 5));
            case "cr_latep_down"    -> cred.setLatePaymentPenalty(Math.max(-500, cred.getLatePaymentPenalty() - 5));
            case "cr_dbonus_up"     -> cred.setDepositCompletedBonus(Math.min(100, cred.getDepositCompletedBonus() + 1));
            case "cr_dbonus_down"   -> cred.setDepositCompletedBonus(Math.max(0, cred.getDepositCompletedBonus() - 1));

            // Inflation
            case "infl_enabled"     -> infl.setEnabled(!infl.isEnabled());
            case "infl_base_up"     -> infl.setBaseInflationRate(Math.min(1.0, infl.getBaseInflationRate() + 0.01));
            case "infl_base_down"   -> infl.setBaseInflationRate(Math.max(-1.0, infl.getBaseInflationRate() - 0.01));
            case "infl_hrs_up"      -> infl.setUpdateIntervalHours(Math.min(168, infl.getUpdateIntervalHours() + 1));
            case "infl_hrs_down"    -> infl.setUpdateIntervalHours(Math.max(1, infl.getUpdateIntervalHours() - 1));
            case "infl_max_up"      -> infl.setMaxInflationRate(Math.min(1.0, infl.getMaxInflationRate() + 0.01));
            case "infl_max_down"    -> infl.setMaxInflationRate(Math.max(0.0, infl.getMaxInflationRate() - 0.01));
            case "infl_min_up"      -> infl.setMinInflationRate(Math.min(0.5, infl.getMinInflationRate() + 0.01));
            case "infl_min_down"    -> infl.setMinInflationRate(Math.max(-1.0, infl.getMinInflationRate() - 0.01));

            // Protection
            case "prot_ops_up"      -> prot.setMaxOperationsPerHour(Math.min(1000, prot.getMaxOperationsPerHour() + 5));
            case "prot_ops_down"    -> prot.setMaxOperationsPerHour(Math.max(1, prot.getMaxOperationsPerHour() - 5));
            case "prot_dcool_up"    -> prot.setDepositCooldownSeconds(Math.min(3600, prot.getDepositCooldownSeconds() + 10));
            case "prot_dcool_down"  -> prot.setDepositCooldownSeconds(Math.max(0, prot.getDepositCooldownSeconds() - 10));
            case "prot_lcool_up"    -> prot.setLoanCooldownSeconds(Math.min(3600, prot.getLoanCooldownSeconds() + 30));
            case "prot_lcool_down"  -> prot.setLoanCooldownSeconds(Math.max(0, prot.getLoanCooldownSeconds() - 30));
            case "prot_age_up"      -> prot.setMinAccountAgeDaysForLoan(Math.min(30, prot.getMinAccountAgeDaysForLoan() + 1));
            case "prot_age_down"    -> prot.setMinAccountAgeDaysForLoan(Math.max(0, prot.getMinAccountAgeDaysForLoan() - 1));
            case "prot_audit"       -> prot.setAuditLogEnabled(!prot.isAuditLogEnabled());
            case "prot_maxlog_up"   -> prot.setMaxAuditLogEntries(Math.min(100000, prot.getMaxAuditLogEntries() + 100));
            case "prot_maxlog_down" -> prot.setMaxAuditLogEntries(Math.max(100, prot.getMaxAuditLogEntries() - 100));
        }
    }

    // ════════════════════════════════════════════════════════
    //  RE-OPEN
    // ════════════════════════════════════════════════════════

    private void reopen(@Nonnull String tab) {
        close();
        AdminBankGui newPage = new AdminBankGui(plugin, playerRef, adminUuid, tab);
        PageOpenHelper.openPage(savedRef, savedStore, newPage);
    }

    // ════════════════════════════════════════════════════════
    //  STATIC OPEN (entry point from commands)
    // ════════════════════════════════════════════════════════

    public static void open(@Nonnull EcoTaleBankingPlugin plugin,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull UUID adminUuid) {
        open(plugin, playerRef, ref, store, adminUuid, "dashboard");
    }

    public static void open(@Nonnull EcoTaleBankingPlugin plugin,
                            @Nonnull PlayerRef playerRef,
                            @Nonnull Ref<EntityStore> ref,
                            @Nonnull Store<EntityStore> store,
                            @Nonnull UUID adminUuid,
                            @Nonnull String selectedTab) {
        AdminBankGui page = new AdminBankGui(plugin, playerRef, adminUuid, selectedTab);
        PageOpenHelper.openPage(ref, store, page);
    }

    // ════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════

    private String L(LangManager lang, String key, String... args) {
        return lang.getForPlayer(adminUuid, key, args);
    }

    private static String stripDecorators(String text) {
        if (text == null) return "";
        text = text.trim();
        if (text.startsWith("-- ")) text = text.substring(3);
        if (text.endsWith(" --")) text = text.substring(0, text.length() - 3);
        return text.trim();
    }

    private static String shortLabel(String text) {
        text = stripDecorators(text);
        int space = text.indexOf(' ');
        return space > 0 ? text.substring(0, space) : text;
    }

    private String getSubTabKey(String subTab) {
        return switch (subTab) {
            case "general"    -> "gui.admin.settings.general";
            case "deposits"   -> "gui.admin.settings.deposits_section";
            case "loans"      -> "gui.admin.settings.loans_section";
            case "credit"     -> "gui.admin.settings.credit_section";
            case "inflation"  -> "gui.admin.settings.inflation_section";
            case "protection" -> "gui.admin.settings.protection_section";
            default           -> "gui.admin.settings.general";
        };
    }

    private void sendMsg(String miniMessageText) {
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

    public static class AdminEventData {
        public String action = "";
        public String id = "";
        public String tab = "";
    }
}
