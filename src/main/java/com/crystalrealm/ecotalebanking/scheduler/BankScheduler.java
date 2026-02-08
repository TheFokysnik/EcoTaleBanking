package com.crystalrealm.ecotalebanking.scheduler;

import com.crystalrealm.ecotalebanking.model.BankAccount;
import com.crystalrealm.ecotalebanking.model.Deposit;
import com.crystalrealm.ecotalebanking.model.Loan;
import com.crystalrealm.ecotalebanking.service.BankService;
import com.crystalrealm.ecotalebanking.service.InflationService;
import com.crystalrealm.ecotalebanking.service.TaxService;
import com.crystalrealm.ecotalebanking.storage.BankStorage;
import com.crystalrealm.ecotalebanking.util.PluginLogger;
import com.hypixel.hytale.server.core.HytaleServer;

import javax.annotation.Nonnull;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Periodic task scheduler for the banking system.
 *
 * <p>Tasks:</p>
 * <ul>
 *   <li>Auto-save (every N minutes)</li>
 *   <li>Interest accrual (daily)</li>
 *   <li>Overdue processing (daily)</li>
 *   <li>Inflation updates (every N hours)</li>
 * </ul>
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public class BankScheduler {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private final BankService bankService;
    private final BankStorage storage;
    private final InflationService inflationService;
    private final int autoSaveMinutes;
    private final int inflationUpdateHours;

    private ScheduledFuture<?> autoSaveTask;
    private ScheduledFuture<?> dailyProcessTask;
    private ScheduledFuture<?> inflationTask;

    public BankScheduler(@Nonnull BankService bankService,
                         @Nonnull BankStorage storage,
                         @Nonnull InflationService inflationService,
                         int autoSaveMinutes,
                         int inflationUpdateHours) {
        this.bankService = bankService;
        this.storage = storage;
        this.inflationService = inflationService;
        this.autoSaveMinutes = autoSaveMinutes;
        this.inflationUpdateHours = inflationUpdateHours;
    }

    /**
     * Starts all periodic tasks.
     */
    public void start() {
        // Auto-save
        autoSaveTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                this::autoSave,
                autoSaveMinutes, autoSaveMinutes, TimeUnit.MINUTES
        );
        LOGGER.info("Auto-save scheduled every {} minutes.", autoSaveMinutes);

        // Daily processing (interest + overdue checks)
        // First run after 60 seconds, then every game day
        long gameDaySeconds = com.crystalrealm.ecotalebanking.util.GameTime.getSecondsPerDay();
        dailyProcessTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                this::dailyProcessing,
                60, gameDaySeconds, TimeUnit.SECONDS
        );
        LOGGER.info("Daily processing scheduled: first run in 60s, then every {} seconds (1 game day).", gameDaySeconds);

        // Inflation updates
        if (inflationService.isEnabled()) {
            inflationTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                    this::updateInflation,
                    inflationUpdateHours, inflationUpdateHours, TimeUnit.HOURS
            );
            LOGGER.info("Inflation updates scheduled every {} hours.", inflationUpdateHours);
        }
    }

    /**
     * Stops all tasks.
     */
    public void shutdown() {
        if (autoSaveTask != null) autoSaveTask.cancel(false);
        if (dailyProcessTask != null) dailyProcessTask.cancel(false);
        if (inflationTask != null) inflationTask.cancel(false);

        // Final save
        storage.saveAll();
        LOGGER.info("BankScheduler shutdown. Final save completed.");
    }

    // ─── Tasks ──────────────────────────────────────────────────

    private void autoSave() {
        try {
            storage.saveAll();
            LOGGER.debug("Auto-save completed.");
        } catch (Exception e) {
            LOGGER.error("Auto-save failed: {}", e.getMessage(), e);
        }
    }

    private void dailyProcessing() {
        try {
            LOGGER.info("Running daily bank processing...");
            bankService.dailyProcessing();
        } catch (Exception e) {
            LOGGER.error("Daily processing failed: {}", e.getMessage(), e);
        }
    }

    private void updateInflation() {
        try {
            inflationService.updateRate();
        } catch (Exception e) {
            LOGGER.error("Inflation update failed: {}", e.getMessage(), e);
        }
    }
}
