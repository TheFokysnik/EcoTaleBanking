package com.crystalrealm.ecotalebanking;

import com.crystalrealm.ecotalebanking.commands.BankCommandCollection;
import com.crystalrealm.ecotalebanking.config.BankingConfig;
import com.crystalrealm.ecotalebanking.config.ConfigManager;
import com.crystalrealm.ecotalebanking.lang.LangManager;
import com.crystalrealm.ecotalebanking.protection.AbuseGuard;
import com.crystalrealm.ecotalebanking.scheduler.BankScheduler;
import com.crystalrealm.ecotalebanking.service.*;
import com.crystalrealm.ecotalebanking.storage.BankStorage;
import com.crystalrealm.ecotalebanking.storage.JsonBankStorage;
import com.crystalrealm.ecotalebanking.util.MessageUtil;
import com.crystalrealm.ecotalebanking.util.PluginLogger;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

/**
 * EcoTaleBanking — полноценная банковская система для Hytale.
 *
 * <p>Возможности:</p>
 * <ul>
 *   <li>Банковские вклады (депозиты) с процентными ставками</li>
 *   <li>Кредиты (займы) с залогом и процентами</li>
 *   <li>Кредитный рейтинг (0—1000)</li>
 *   <li>Система инфляции (динамические ставки)</li>
 *   <li>Налоги (баланс, проценты, транзакции)</li>
 *   <li>Защита от злоупотреблений (rate limit, cooldowns)</li>
 *   <li>Полная локализация (RU/EN)</li>
 *   <li>Аудит-лог всех операций</li>
 * </ul>
 *
 * <h3>Архитектура</h3>
 * <pre>
 *   Commands → BankService (facade)
 *                  ↕
 *       DepositService  LoanService
 *       CreditService   TaxService
 *       InflationService
 *                  ↕
 *           JsonBankStorage (JSON-файлы)
 *                  ↕
 *           EcotaleAPI (кошелёк)
 * </pre>
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public class EcoTaleBankingPlugin extends JavaPlugin {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();
    public static final String VERSION = "1.0.0";

    private static EcoTaleBankingPlugin instance;

    // ── Core ────────────────────────────────────────────────
    private ConfigManager configManager;
    private LangManager langManager;
    private BankStorage storage;

    // ── Services ────────────────────────────────────────────
    private CreditRatingService creditService;
    private InflationService inflationService;
    private TaxService taxService;
    private DepositService depositService;
    private LoanService loanService;
    private BankService bankService;

    // ── Protection ──────────────────────────────────────────
    private AbuseGuard abuseGuard;

    // ── Scheduler ───────────────────────────────────────────
    private BankScheduler scheduler;

    /**
     * Обязательный конструктор для Hytale plugins.
     */
    public EcoTaleBankingPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    // ═════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═════════════════════════════════════════════════════════

    /**
     * SETUP phase — load configuration, initialize services,
     * register commands. Ecotale API may not be available yet.
     */
    @Override
    protected void setup() {
        LOGGER.info("EcoTaleBanking v{} — Setting up...", VERSION);

        // 1. Configuration
        configManager = new ConfigManager(getDataDirectory());
        configManager.loadOrCreate();
        LOGGER.info("Configuration loaded from {}", configManager.getConfigPath());

        BankingConfig config = configManager.getConfig();

        // Initialize game day duration from config
        com.crystalrealm.ecotalebanking.util.GameTime.init(
                config.getGeneral().getSecondsPerGameDay()
        );
        LOGGER.info("Game day duration: {} real seconds.", config.getGeneral().getSecondsPerGameDay());

        // 2. Localization
        langManager = new LangManager(getDataDirectory());
        langManager.load(config.getGeneral().getLanguage());

        // 3. Storage
        storage = new JsonBankStorage(
                getDataDirectory(),
                config.getProtection().getMaxAuditLogEntries()
        );
        storage.loadAll();

        // 4. Services (in dependency order)
        creditService = new CreditRatingService(storage, config.getCredit());
        inflationService = new InflationService(config.getInflation());
        taxService = new TaxService(config.getTaxes());

        depositService = new DepositService(
                storage, config.getDeposits(),
                inflationService, taxService, creditService
        );

        loanService = new LoanService(
                storage, config.getLoans(),
                inflationService, creditService
        );

        bankService = new BankService(
                storage, depositService, loanService,
                creditService, taxService, inflationService
        );

        // 5. Protection
        abuseGuard = new AbuseGuard(config.getProtection());

        // 6. Commands
        getCommandRegistry().registerCommand(new BankCommandCollection(this));

        LOGGER.info("Setup phase complete. Services initialized.");
    }

    /**
     * START phase — all plugins loaded. Ecotale API is available.
     */
    @Override
    protected void start() {
        LOGGER.info("Starting EcoTaleBanking...");

        // Проверка Ecotale
        try {
            boolean ecotaleAvailable = com.ecotale.api.EcotaleAPI.isAvailable();
            if (ecotaleAvailable) {
                LOGGER.info("Ecotale API connected.");
            } else {
                LOGGER.error("Ecotale API not available! Banking operations will fail.");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to check Ecotale API: {}", e.getMessage());
        }

        // Запуск планировщика
        BankingConfig config = configManager.getConfig();
        scheduler = new BankScheduler(
                bankService, storage, inflationService,
                config.getGeneral().getAutoSaveMinutes(),
                config.getInflation().getUpdateIntervalHours()
        );
        scheduler.start();

        LOGGER.info("EcoTaleBanking started! Banking system is active.");
    }

    /**
     * Фаза SHUTDOWN — сохранение, очистка.
     */
    @Override
    protected void shutdown() {
        LOGGER.info("EcoTaleBanking shutting down...");

        if (scheduler != null) scheduler.shutdown();
        if (storage != null) storage.saveAll();
        if (abuseGuard != null) abuseGuard.clearAll();
        if (langManager != null) langManager.clearPlayerData();
        MessageUtil.clearCache();

        instance = null;
        LOGGER.info("EcoTaleBanking shutdown complete.");
    }

    // ═════════════════════════════════════════════════════════
    //  PUBLIC GETTERS
    // ═════════════════════════════════════════════════════════

    @Nonnull public String getVersion() { return VERSION; }
    @Nonnull public static EcoTaleBankingPlugin getInstance() { return instance; }
    @Nonnull public ConfigManager getConfigManager() { return configManager; }
    @Nonnull public LangManager getLangManager() { return langManager; }
    @Nonnull public BankService getBankService() { return bankService; }
    @Nonnull public AbuseGuard getAbuseGuard() { return abuseGuard; }
    @Nonnull public BankStorage getStorage() { return storage; }
}
