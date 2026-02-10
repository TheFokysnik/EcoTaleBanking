package com.crystalrealm.ecotalebanking.commands;

import com.crystalrealm.ecotalebanking.EcoTaleBankingPlugin;
import com.crystalrealm.ecotalebanking.gui.AdminBankGui;
import com.crystalrealm.ecotalebanking.gui.PlayerBankGui;
import com.crystalrealm.ecotalebanking.lang.LangManager;
import com.crystalrealm.ecotalebanking.model.*;
import com.crystalrealm.ecotalebanking.protection.AbuseGuard;
import com.crystalrealm.ecotalebanking.service.BankService;
import com.crystalrealm.ecotalebanking.service.BankService.BankResult;
import com.crystalrealm.ecotalebanking.util.MessageUtil;
import com.crystalrealm.ecotalebanking.util.MiniMessageParser;
import com.crystalrealm.ecotalebanking.util.PluginLogger;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Collection of /bank commands.
 *
 * @author CrystalRealm
 * @version 1.1.0
 */
public class BankCommandCollection extends AbstractCommandCollection {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    /** Keywords to filter out when parsing trailing args from getInputString(). */
    private static final Set<String> COMMAND_KEYWORDS = Set.of(
            "b", "bank", "balance", "deposit", "withdraw", "deposits", "plans",
            "loan", "repay", "loans", "info", "history", "lang", "langen", "langru", "help",
            "gui", "admin", "freeze", "unfreeze", "reload"
    );

    private final EcoTaleBankingPlugin plugin;

    private static Message msg(String miniMessage) {
        return Message.parse(MiniMessageParser.toJson(miniMessage));
    }

    public BankCommandCollection(EcoTaleBankingPlugin plugin) {
        super("b", "EcoTaleBanking — banking system commands");
        addAliases("bank");
        this.plugin = plugin;

        addSubCommand(new BalanceSubCommand());
        addSubCommand(new DepositSubCommand());
        addSubCommand(new WithdrawSubCommand());
        addSubCommand(new DepositsListSubCommand());
        addSubCommand(new PlansSubCommand());
        addSubCommand(new LoanSubCommand());
        addSubCommand(new RepaySubCommand());
        addSubCommand(new LoansListSubCommand());
        addSubCommand(new InfoSubCommand());
        addSubCommand(new HistorySubCommand());
        addSubCommand(new LangSubCommand());
        addSubCommand(new LangEnSubCommand());
        addSubCommand(new LangRuSubCommand());
        addSubCommand(new HelpSubCommand());
        addSubCommand(new GuiSubCommand());
        addSubCommand(new AdminSubCommand());
    }

    // ────────────────────────────────────────────────────────
    //  ARG PARSING (getInputString based)
    // ────────────────────────────────────────────────────────

    /**
     * Parse non-keyword args from context.getInputString().
     * E.g. "/bank deposit short 1000" → ["short", "1000"]
     */
    private List<String> parseArgs(CommandContext context) {
        try {
            String input = context.getInputString();
            if (input == null || input.isBlank()) return List.of();

            String[] parts = input.trim().split("\\s+");
            List<String> args = new ArrayList<>();
            for (String part : parts) {
                String lower = part.toLowerCase();
                if (lower.startsWith("/")) lower = lower.substring(1);
                if (!COMMAND_KEYWORDS.contains(lower)) {
                    args.add(part);
                }
            }
            return args;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse args: {}", e.getMessage());
        }
        return List.of();
    }

    // ════════════════════════════════════════════════════════
    //  /bank (balance overview)
    // ════════════════════════════════════════════════════════

    private class BalanceSubCommand extends AbstractAsyncCommand {
        BalanceSubCommand() { super("balance", "Shows bank account overview"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(context, sender, "ecotale.bank.use")) return done();

            UUID uuid = sender.getUuid();
            BankService bank = plugin.getBankService();
            BankAccount account = bank.getAccount(uuid);

            double wallet = bank.getWalletBalance(uuid);
            BigDecimal deposited = account.getTotalDeposited();
            BigDecimal debt = account.getTotalDebt();

            context.sendMessage(msg(L(sender, "cmd.bank.header")));
            context.sendMessage(msg(L(sender, "cmd.bank.wallet",
                    "amount", MessageUtil.formatCoins(wallet))));
            context.sendMessage(msg(L(sender, "cmd.bank.deposited",
                    "amount", MessageUtil.formatCoins(deposited))));
            context.sendMessage(msg(L(sender, "cmd.bank.debt",
                    "amount", MessageUtil.formatCoins(debt))));
            context.sendMessage(msg(L(sender, "cmd.bank.deposits_count",
                    "count", String.valueOf(account.getActiveDeposits().size()))));
            context.sendMessage(msg(L(sender, "cmd.bank.loans_count",
                    "count", String.valueOf(account.getActiveLoans().size()))));

            CreditScore credit = bank.getCreditService().getScore(uuid);
            context.sendMessage(msg(L(sender, "cmd.bank.credit",
                    "score", String.valueOf(credit.getScore()),
                    "rating", credit.getRating())));

            if (account.isFrozen()) {
                context.sendMessage(msg(L(sender, "cmd.bank.frozen",
                        "reason", account.getFrozenReason())));
            }
            context.sendMessage(msg(L(sender, "cmd.bank.footer")));

            return done();
        }
    }

    // ════════════════════════════════════════════════════════
    //  /bank deposit <plan> <amount>
    // ════════════════════════════════════════════════════════

    private class DepositSubCommand extends AbstractAsyncCommand {
        DepositSubCommand() { super("deposit", "Open a bank deposit"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(context, sender, "ecotale.bank.deposit")) return done();

            UUID uuid = sender.getUuid();
            AbuseGuard guard = plugin.getAbuseGuard();

            if (!guard.checkRateLimit(uuid)) {
                context.sendMessage(msg(L(sender, "cmd.error.rate_limit")));
                return done();
            }
            if (!guard.checkDepositCooldown(uuid)) {
                long remaining = guard.getDepositCooldownRemaining(uuid);
                context.sendMessage(msg(L(sender, "cmd.error.cooldown",
                        "seconds", String.valueOf(remaining))));
                return done();
            }

            List<String> args = parseArgs(context);
            if (args.size() < 2) {
                context.sendMessage(msg(L(sender, "cmd.help.deposit")));
                return done();
            }

            String planName = args.get(0);
            BigDecimal amount;
            try {
                amount = new BigDecimal(args.get(1));
                if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                context.sendMessage(msg(L(sender, "cmd.error.invalid_amount")));
                return done();
            }

            BankResult result = plugin.getBankService().openDeposit(uuid, planName, amount);

            if (result.isSuccess()) {
                guard.recordDeposit(uuid);
                context.sendMessage(msg(L(sender, "cmd.deposit.success",
                        "amount", MessageUtil.formatCoins(amount),
                        "plan", planName,
                        "id", result.getDetail())));
            } else {
                context.sendMessage(msg(L(sender, "cmd.error." + result.getMessageKey())));
            }

            return done();
        }
    }

    // ════════════════════════════════════════════════════════
    //  /bank withdraw <id>
    // ════════════════════════════════════════════════════════

    private class WithdrawSubCommand extends AbstractAsyncCommand {
        WithdrawSubCommand() { super("withdraw", "Close/withdraw a deposit"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(context, sender, "ecotale.bank.deposit")) return done();

            UUID uuid = sender.getUuid();
            List<String> args = parseArgs(context);
            if (args.isEmpty()) {
                context.sendMessage(msg(L(sender, "cmd.help.withdraw")));
                return done();
            }

            String depositId = args.get(0);
            BankResult result = plugin.getBankService().closeDeposit(uuid, depositId);

            if (result.isSuccess()) {
                plugin.getAbuseGuard().recordGenericOperation(uuid);
                context.sendMessage(msg(L(sender, "cmd.withdraw.success",
                        "id", depositId,
                        "amount", result.getDetail())));
            } else {
                context.sendMessage(msg(L(sender, "cmd.error." + result.getMessageKey())));
            }

            return done();
        }
    }

    // ════════════════════════════════════════════════════════
    //  /bank deposits
    // ════════════════════════════════════════════════════════

    private class DepositsListSubCommand extends AbstractAsyncCommand {
        DepositsListSubCommand() { super("deposits", "List active deposits"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(context, sender, "ecotale.bank.use")) return done();

            UUID uuid = sender.getUuid();
            BankAccount account = plugin.getBankService().getAccount(uuid);
            List<Deposit> deposits = account.getActiveDeposits();

            context.sendMessage(msg(L(sender, "cmd.deposits.header")));

            if (deposits.isEmpty()) {
                context.sendMessage(msg(L(sender, "cmd.deposits.none")));
            } else {
                for (Deposit d : deposits) {
                    context.sendMessage(msg(L(sender, "cmd.deposits.entry",
                            "id", d.getId(),
                            "plan", d.getPlanName(),
                            "amount", MessageUtil.formatCoins(d.getAmount()),
                            "rate", MessageUtil.formatPercent(d.getInterestRate()),
                            "accrued", MessageUtil.formatCoins(d.getAccruedInterest()),
                            "days_left", String.valueOf(
                                    Math.max(0, d.getTermDays() - d.getElapsedDays())),
                            "status", MessageUtil.coloredStatus(d.getStatus().name())
                    )));
                }
            }

            context.sendMessage(msg(L(sender, "cmd.deposits.footer")));
            return done();
        }
    }

    // ════════════════════════════════════════════════════════
    //  /bank plans
    // ════════════════════════════════════════════════════════

    private class PlansSubCommand extends AbstractAsyncCommand {
        PlansSubCommand() { super("plans", "Show deposit plans"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();

            List<DepositPlan> plans = plugin.getBankService()
                    .getDepositService().getAvailablePlans();

            context.sendMessage(msg(L(sender, "cmd.plans.header")));
            for (DepositPlan p : plans) {
                context.sendMessage(msg(L(sender, "cmd.plans.entry",
                        "name", p.getName(),
                        "days", String.valueOf(p.getTermDays()),
                        "rate", MessageUtil.formatPercent(p.getBaseRate()),
                        "min", MessageUtil.formatCoins(p.getMinAmount()),
                        "max", MessageUtil.formatCoins(p.getMaxAmount())
                )));
            }
            context.sendMessage(msg(L(sender, "cmd.plans.footer")));
            return done();
        }
    }

    // ════════════════════════════════════════════════════════
    //  /bank loan <amount>
    // ════════════════════════════════════════════════════════

    private class LoanSubCommand extends AbstractAsyncCommand {
        LoanSubCommand() { super("loan", "Take a loan"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(context, sender, "ecotale.bank.loan")) return done();

            UUID uuid = sender.getUuid();
            AbuseGuard guard = plugin.getAbuseGuard();

            if (!guard.checkRateLimit(uuid)) {
                context.sendMessage(msg(L(sender, "cmd.error.rate_limit")));
                return done();
            }
            if (!guard.checkLoanCooldown(uuid)) {
                long remaining = guard.getLoanCooldownRemaining(uuid);
                context.sendMessage(msg(L(sender, "cmd.error.cooldown",
                        "seconds", String.valueOf(remaining))));
                return done();
            }

            List<String> args = parseArgs(context);
            if (args.isEmpty()) {
                context.sendMessage(msg(L(sender, "cmd.help.loan")));
                return done();
            }

            BigDecimal amount;
            try {
                amount = new BigDecimal(args.get(0));
                if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                context.sendMessage(msg(L(sender, "cmd.error.invalid_amount")));
                return done();
            }

            BankResult result = plugin.getBankService().takeLoan(uuid, amount);

            if (result.isSuccess()) {
                guard.recordLoan(uuid);
                BigDecimal rate = plugin.getBankService().getLoanService().getEffectiveRate(uuid);
                context.sendMessage(msg(L(sender, "cmd.loan.success",
                        "amount", MessageUtil.formatCoins(amount),
                        "rate", MessageUtil.formatPercent(rate),
                        "id", result.getDetail())));
            } else {
                context.sendMessage(msg(L(sender, "cmd.error." + result.getMessageKey())));
            }

            return done();
        }
    }

    // ════════════════════════════════════════════════════════
    //  /bank repay <id> <amount>
    // ════════════════════════════════════════════════════════

    private class RepaySubCommand extends AbstractAsyncCommand {
        RepaySubCommand() { super("repay", "Repay a loan"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(context, sender, "ecotale.bank.loan")) return done();

            UUID uuid = sender.getUuid();
            List<String> args = parseArgs(context);
            if (args.size() < 2) {
                context.sendMessage(msg(L(sender, "cmd.help.repay")));
                return done();
            }

            String loanId = args.get(0);
            BigDecimal amount;
            try {
                amount = new BigDecimal(args.get(1));
                if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                context.sendMessage(msg(L(sender, "cmd.error.invalid_amount")));
                return done();
            }

            BankResult result = plugin.getBankService().repayLoan(uuid, loanId, amount);

            if (result.isSuccess()) {
                plugin.getAbuseGuard().recordGenericOperation(uuid);
                context.sendMessage(msg(L(sender, "cmd." + result.getMessageKey(),
                        "id", loanId,
                        "amount", result.getDetail())));
            } else {
                context.sendMessage(msg(L(sender, "cmd.error." + result.getMessageKey())));
            }

            return done();
        }
    }

    // ════════════════════════════════════════════════════════
    //  /bank loans
    // ════════════════════════════════════════════════════════

    private class LoansListSubCommand extends AbstractAsyncCommand {
        LoansListSubCommand() { super("loans", "List active loans"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(context, sender, "ecotale.bank.use")) return done();

            UUID uuid = sender.getUuid();
            BankAccount account = plugin.getBankService().getAccount(uuid);
            List<Loan> loans = account.getActiveLoans();

            context.sendMessage(msg(L(sender, "cmd.loans.header")));

            if (loans.isEmpty()) {
                context.sendMessage(msg(L(sender, "cmd.loans.none")));
            } else {
                for (Loan l : loans) {
                    context.sendMessage(msg(L(sender, "cmd.loans.entry",
                            "id", l.getId(),
                            "principal", MessageUtil.formatCoins(l.getPrincipalAmount()),
                            "remaining", MessageUtil.formatCoins(l.getRemainingBalance()),
                            "rate", MessageUtil.formatPercent(l.getInterestRate()),
                            "days_left", String.valueOf(l.getDaysUntilDue()),
                            "status", MessageUtil.coloredStatus(l.getStatus().name())
                    )));
                }
            }

            context.sendMessage(msg(L(sender, "cmd.loans.footer")));
            return done();
        }
    }

    // ════════════════════════════════════════════════════════
    //  /bank info
    // ════════════════════════════════════════════════════════

    private class InfoSubCommand extends AbstractAsyncCommand {
        InfoSubCommand() { super("info", "Detailed bank info"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();

            UUID uuid = sender.getUuid();
            BankService bank = plugin.getBankService();
            CreditScore credit = bank.getCreditService().getScore(uuid);

            context.sendMessage(msg(L(sender, "cmd.info.header")));
            context.sendMessage(msg(L(sender, "cmd.info.version",
                    "version", plugin.getVersion())));
            context.sendMessage(msg(L(sender, "cmd.info.credit_score",
                    "score", String.valueOf(credit.getScore()),
                    "rating", credit.getRating())));
            context.sendMessage(msg(L(sender, "cmd.info.loans_completed",
                    "count", String.valueOf(credit.getTotalLoansCompleted()))));
            context.sendMessage(msg(L(sender, "cmd.info.loans_defaulted",
                    "count", String.valueOf(credit.getTotalLoansDefaulted()))));
            context.sendMessage(msg(L(sender, "cmd.info.deposits_completed",
                    "count", String.valueOf(credit.getTotalDepositsCompleted()))));

            BigDecimal maxLoan = bank.getLoanService().getMaxLoanAmount(uuid);
            BigDecimal loanRate = bank.getLoanService().getEffectiveRate(uuid);
            context.sendMessage(msg(L(sender, "cmd.info.max_loan",
                    "amount", MessageUtil.formatCoins(maxLoan))));
            context.sendMessage(msg(L(sender, "cmd.info.loan_rate",
                    "rate", MessageUtil.formatPercent(loanRate))));

            if (bank.getInflationService().isEnabled()) {
                context.sendMessage(msg(L(sender, "cmd.info.inflation",
                        "rate", MessageUtil.formatPercent(bank.getInflationService().getCurrentRate()))));
            }

            context.sendMessage(msg(L(sender, "cmd.info.footer")));
            return done();
        }
    }

    // ════════════════════════════════════════════════════════
    //  /bank history
    // ════════════════════════════════════════════════════════

    private class HistorySubCommand extends AbstractAsyncCommand {
        HistorySubCommand() { super("history", "Transaction history"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(context, sender, "ecotale.bank.use")) return done();

            UUID uuid = sender.getUuid();
            List<AuditLog> logs = plugin.getBankService().getAuditLogs(uuid, 10);

            context.sendMessage(msg(L(sender, "cmd.history.header")));

            if (logs.isEmpty()) {
                context.sendMessage(msg(L(sender, "cmd.history.none")));
            } else {
                for (AuditLog log : logs) {
                    context.sendMessage(msg(L(sender, "cmd.history.entry",
                            "type", log.getType().name(),
                            "amount", MessageUtil.formatCoins(log.getAmount()),
                            "desc", log.getDescription()
                    )));
                }
            }

            context.sendMessage(msg(L(sender, "cmd.history.footer")));
            return done();
        }
    }

    // ════════════════════════════════════════════════════════
    //  /bank lang | /b langen | /b langru
    // ════════════════════════════════════════════════════════

    private class LangSubCommand extends AbstractAsyncCommand {
        LangSubCommand() { super("lang", "Show language usage"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            context.sendMessage(msg(L(sender, "cmd.lang.usage")));
            return done();
        }
    }

    private class LangEnSubCommand extends AbstractAsyncCommand {
        LangEnSubCommand() { super("langen", "Switch to English"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (plugin.getLangManager().setPlayerLang(sender.getUuid(), "en")) {
                context.sendMessage(msg(L(sender, "cmd.lang.changed")));
            } else {
                context.sendMessage(msg(L(sender, "cmd.lang.invalid")));
            }
            return done();
        }
    }

    private class LangRuSubCommand extends AbstractAsyncCommand {
        LangRuSubCommand() { super("langru", "Switch to Russian"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (plugin.getLangManager().setPlayerLang(sender.getUuid(), "ru")) {
                context.sendMessage(msg(L(sender, "cmd.lang.changed")));
            } else {
                context.sendMessage(msg(L(sender, "cmd.lang.invalid")));
            }
            return done();
        }
    }

    // ════════════════════════════════════════════════════════
    //  /bank help
    // ════════════════════════════════════════════════════════

    private class HelpSubCommand extends AbstractAsyncCommand {
        HelpSubCommand() { super("help", "Shows help"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();

            context.sendMessage(msg(L(sender, "cmd.help.header")));
            context.sendMessage(msg(L(sender, "cmd.help.bank")));
            context.sendMessage(msg(L(sender, "cmd.help.deposit")));
            context.sendMessage(msg(L(sender, "cmd.help.withdraw")));
            context.sendMessage(msg(L(sender, "cmd.help.deposits")));
            context.sendMessage(msg(L(sender, "cmd.help.plans")));
            context.sendMessage(msg(L(sender, "cmd.help.loan")));
            context.sendMessage(msg(L(sender, "cmd.help.repay")));
            context.sendMessage(msg(L(sender, "cmd.help.loans")));
            context.sendMessage(msg(L(sender, "cmd.help.info")));
            context.sendMessage(msg(L(sender, "cmd.help.history")));
            context.sendMessage(msg(L(sender, "cmd.help.lang")));
            context.sendMessage(msg(L(sender, "cmd.help.gui")));
            context.sendMessage(msg(L(sender, "cmd.help.help")));
            context.sendMessage(msg(L(sender, "cmd.help.footer")));

            return done();
        }
    }

    // ════════════════════════════════════════════════════════
    //  /bank gui
    // ════════════════════════════════════════════════════════

    private class GuiSubCommand extends AbstractAsyncCommand {
        GuiSubCommand() { super("gui", "Open bank GUI panel"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(context, sender, "ecotale.bank.use")) return done();

            if (sender instanceof Player player) {
                return openGui(player, sender.getUuid(), false);
            }
            return done();
        }
    }

    // ════════════════════════════════════════════════════════
    //  /bank admin
    // ════════════════════════════════════════════════════════

    private class AdminSubCommand extends AbstractCommandCollection {
        AdminSubCommand() {
            super("admin", "Admin banking commands");
            addSubCommand(new FreezeSubCommand());
            addSubCommand(new UnfreezeSubCommand());
            addSubCommand(new ReloadSubCommand());
            addSubCommand(new AdminGuiSubCommand());
        }
    }

    private class AdminGuiSubCommand extends AbstractAsyncCommand {
        AdminGuiSubCommand() { super("gui", "Open admin bank GUI panel"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(context, sender, "ecotale.bank.admin")) return done();

            if (sender instanceof Player player) {
                return openGui(player, sender.getUuid(), true);
            }
            return done();
        }
    }

    private class FreezeSubCommand extends AbstractAsyncCommand {
        FreezeSubCommand() { super("freeze", "Freeze a player account"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(context, sender, "ecotale.bank.admin")) return done();

            List<String> args = parseArgs(context);
            if (args.size() < 2) {
                context.sendMessage(msg(L(sender, "cmd.admin.freeze_usage")));
                return done();
            }

            try {
                UUID targetUuid = UUID.fromString(args.get(0));
                String reason = args.get(1);
                plugin.getBankService().freezeAccount(targetUuid, reason);
                context.sendMessage(msg(L(sender, "gui.admin.frozen_success",
                        "uuid", targetUuid.toString().substring(0, 8))));
            } catch (IllegalArgumentException e) {
                context.sendMessage(msg(L(sender, "cmd.admin.freeze_usage")));
            }
            return done();
        }
    }

    private class UnfreezeSubCommand extends AbstractAsyncCommand {
        UnfreezeSubCommand() { super("unfreeze", "Unfreeze a player account"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(context, sender, "ecotale.bank.admin")) return done();

            List<String> args = parseArgs(context);
            if (args.isEmpty()) {
                context.sendMessage(msg(L(sender, "cmd.admin.unfreeze_usage")));
                return done();
            }

            try {
                UUID targetUuid = UUID.fromString(args.get(0));
                plugin.getBankService().unfreezeAccount(targetUuid);
                context.sendMessage(msg(L(sender, "gui.admin.unfrozen_success",
                        "uuid", targetUuid.toString().substring(0, 8))));
            } catch (IllegalArgumentException e) {
                context.sendMessage(msg(L(sender, "cmd.admin.unfreeze_usage")));
            }
            return done();
        }
    }

    private class ReloadSubCommand extends AbstractAsyncCommand {
        ReloadSubCommand() { super("reload", "Reload banking config"); }

        @Override
        public CompletableFuture<Void> executeAsync(CommandContext context) {
            if (!context.isPlayer()) return done();
            CommandSender sender = context.sender();
            if (!checkPerm(context, sender, "ecotale.bank.admin")) return done();

            boolean success = plugin.getConfigManager().reload();
            if (success) {
                String lang = plugin.getConfigManager().getConfig().getGeneral().getLanguage();
                plugin.getLangManager().reload(lang);
                context.sendMessage(msg(L(sender, "cmd.reload.success")));
                LOGGER.info("Banking config reloaded by {}", sender.getDisplayName());
            } else {
                context.sendMessage(msg(L(sender, "cmd.reload.fail")));
            }

            return done();
        }
    }

    // ════════════════════════════════════════════════════════
    //  GUI HELPER
    // ════════════════════════════════════════════════════════

    private CompletableFuture<Void> openGui(Player player, UUID uuid, boolean admin) {
        try {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) return done();

            Store<EntityStore> store = ref.getStore();

            java.lang.reflect.Method getExt = store.getClass().getMethod("getExternalData");
            Object extData = getExt.invoke(store);
            java.lang.reflect.Method getWorld = extData.getClass().getMethod("getWorld");
            Object worldObj = getWorld.invoke(extData);

            if (worldObj instanceof Executor worldExec) {
                return CompletableFuture.runAsync(() -> {
                    try {
                        java.lang.reflect.Method getComp = store.getClass()
                                .getMethod("getComponent", Ref.class, ComponentType.class);
                        Object result = getComp.invoke(store, ref, PlayerRef.getComponentType());
                        if (result instanceof PlayerRef playerRef) {
                            MessageUtil.cachePlayerRef(uuid, playerRef);
                            if (admin) {
                                AdminBankGui.open(plugin, playerRef, ref, store, uuid);
                            } else {
                                PlayerBankGui.open(plugin, playerRef, ref, store, uuid);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Failed to open GUI: {}", e.getMessage());
                    }
                }, worldExec);
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Failed to dispatch GUI to WorldThread: {}", e.getMessage());
        }
        return done();
    }

    // ════════════════════════════════════════════════════════
    //  SHARED HELPERS
    // ════════════════════════════════════════════════════════

    private LangManager lang() {
        return plugin.getLangManager();
    }

    private String L(CommandSender sender, String key, String... args) {
        return lang().getForPlayer(sender.getUuid(), key, args);
    }

    private boolean checkPerm(CommandContext ctx, CommandSender sender, String perm) {
        if (!sender.hasPermission(perm)) {
            ctx.sendMessage(msg(L(sender, "cmd.no_permission")));
            return false;
        }
        return true;
    }

    private static CompletableFuture<Void> done() {
        return CompletableFuture.completedFuture(null);
    }
}
