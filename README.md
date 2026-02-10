# 🏦 EcoTaleBanking

**Complete banking system for Hytale servers — deposits, loans, credit rating, inflation & taxes**

Part of the **EcoTale Ecosystem** — a suite of interconnected plugins that together form a rich, player-driven economy on your Hytale server.

![Hytale Server Mod](https://img.shields.io/badge/Hytale-Server%20Mod-0ea5e9?style=for-the-badge)
![Version](https://img.shields.io/badge/version-1.2.0-10b981?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-17+-f97316?style=for-the-badge&logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-a855f7?style=for-the-badge)
![Ecotale](https://img.shields.io/badge/Ecotale-1.0.7-6366f1?style=for-the-badge)

---

## 🌐 EcoTale Ecosystem

EcoTaleBanking is designed to work alongside other EcoTale plugins. Together they create a seamless and deep gameplay economy:

| Plugin | Description | Synergy with Banking |
|:-------|:------------|:---------------------|
| [**Ecotale**](https://curseforge.com/hytale/mods/ecotale) | Core economy — wallets, currency, transfers | 🔗 **Required.** Banking reads/writes player wallets via Ecotale API |
| [**EcoTaleIncome**](https://curseforge.com/hytale/mods/ecotaleincome) | Earn money from mob kills, mining, woodcutting, farming | 💰 Players earn → deposit savings → grow wealth via interest |
| [**EcoTaleQuests**](https://curseforge.com/hytale/mods/ecotalequests) | Daily & weekly quests with currency/XP rewards | 🎯 Quest rewards → loan collateral, deposit capital |

> **Tip:** Install all three alongside Ecotale and players will naturally flow from *earning* (Income) → *saving & investing* (Banking) → *completing goals* (Quests), creating a self-sustaining game loop!

---

## ✨ Features

### 💳 Deposits
- **3 built-in deposit plans** — Short-Term (7d), Medium-Term (14d), Long-Term (30d)
- **Daily interest accrual** based on game days with inflation-adjusted dynamic rates
- **Early withdrawal** with configurable penalty
- **Interactive GUI** — pick a plan and amount with one click

### 💸 Loans
- **Flexible borrowing** with interest, collateral, and term
- **Daily auto-payments** — automatically deducted from the player's wallet each game day
- **Multiple repayment options** — repay 10%, 25%, 50%, or the full remaining balance
- **Early repayment recalculation** — daily payment is recalculated after any partial repayment
- **Overdue penalties** — penalty interest accrues daily past due date
- **Automatic default** — loan defaults after configurable number of overdue days

### 🏅 Credit Rating
- **0–1000 score** with 5 tiers: Excellent, Good, Fair, Poor, Bad
- Affects loan interest rate, maximum loan amount, and collateral requirements
- Score changes: loan completion (+50), on-time payment (+10), deposit matured (+15), late payment (-20), default (-150)
- **Anti-abuse protection** — instant repay does NOT boost credit score (minimum 3-day hold required)

### 📊 Inflation Engine
- Server-wide dynamic inflation rate with random fluctuation & mean-reversion
- Affects both deposit and loan interest rates in real-time
- Configurable update interval, volatility, min/max bounds

### 🏛️ Taxes
- **Progressive balance tax** — configurable brackets
- **Interest tax** — flat rate on deposit interest payouts (default 13%)
- **Transaction tax** — applied to deposits and loan operations

### 🛡️ Protection & Security
- **Anti-abuse system** — rate limits, operation cooldowns, suspicious activity logging
- **Account freeze** — admins can freeze/unfreeze any player's account
- **Audit log** — every transaction recorded with type, amount, timestamp, and details
- **GUI error display** — errors shown directly in the bank panel, not hidden in chat

### 🖥️ Interactive GUI (Native Hytale CustomUI)
- **Player Panel (4 tabs):**
  - *Overview* — wallet balance, total deposits/debt, credit score bar, inflation, max loan
  - *Deposits* — available plans with quick-deposit buttons, active deposits with withdraw
  - *Loans* — credit limits, take-loan buttons, active loans with 4 repayment buttons + progress bar
  - *History* — localized audit log with color-coded transaction types
- **Admin Panel (4 tabs):**
  - *Dashboard* — server-wide statistics (accounts, deposits, loans, debt, avg credit score)
  - *Accounts* — all players with freeze/unfreeze actions, shows player names
  - *Activity* — recent operations across all players
  - *Settings* — edit 40+ config values in-game with reset to defaults button
- **No Loading screens** — all actions update the GUI instantly via `sendUpdate()` without page reload

### ⏰ Game Day System
- All time-based mechanics (interest, loan payments, deposit terms) run on **configurable game days**
- Default: 1 game day = 2880 seconds (48 minutes real time)
- Fully adjustable via config or admin GUI

### 🌍 Localization
- Full **Russian** and **English** support
- Per-player language switching (`/b langru` / `/b langen`)
- All GUI elements, error messages, transaction history — fully translated

---

## 📦 Requirements

| Dependency | Version | Required | Description |
|:-----------|:--------|:--------:|:------------|
| [Ecotale](https://curseforge.com/hytale/mods/ecotale) | ≥ 1.0.0 | ✅ | Economy & currency system |

---

## 🚀 Getting Started

1. Download the latest release
2. Drop `EcoTaleBanking-1.2.0.jar` into your server's `mods/` folder
3. Make sure **Ecotale** is also in `mods/`
4. Start the server — config & lang files are created automatically
5. (Optional) Edit the config at `mods/com.crystalrealm_EcoTaleBanking/EcoTaleBanking.json`

**That's it.** Three deposit plans and a default loan configuration work out of the box.

---

## 🎮 Commands

### Player Commands

| Command | Description | Permission |
|:--------|:------------|:-----------|
| `/b` | Open bank GUI panel | `ecotale.bank.use` |
| `/b balance` | Account overview & balance | `ecotale.bank.use` |
| `/b deposit <plan> <amount>` | Open a deposit | `ecotale.bank.deposit` |
| `/b withdraw <id>` | Close / withdraw a deposit | `ecotale.bank.deposit` |
| `/b deposits` | List your active deposits | `ecotale.bank.use` |
| `/b plans` | View available deposit plans | `ecotale.bank.use` |
| `/b loan <amount>` | Take a loan | `ecotale.bank.loan` |
| `/b repay <id> <amount>` | Repay a loan (partial or full) | `ecotale.bank.loan` |
| `/b loans` | List your active loans | `ecotale.bank.use` |
| `/b info` | Credit score, inflation, loan terms | `ecotale.bank.use` |
| `/b history` | Transaction history | `ecotale.bank.use` |
| `/b lang` | Show language usage hint | `ecotale.bank.use` |
| `/b langen` | Switch to English | `ecotale.bank.use` |
| `/b langru` | Switch to Russian | `ecotale.bank.use` |
| `/b help` | Command reference | — |

> **Alias:** `/bank` also works for all commands.

### Admin Commands

| Command | Description | Permission |
|:--------|:------------|:-----------|
| `/b admin gui` | Admin monitoring panel | `ecotale.bank.admin` |
| `/b admin freeze <uuid> <reason>` | Freeze a player's account | `ecotale.bank.admin` |
| `/b admin unfreeze <uuid>` | Unfreeze a player's account | `ecotale.bank.admin` |
| `/b admin reload` | Reload configuration | `ecotale.bank.admin` |

---

## 🔐 Permissions

| Permission | Description |
|:-----------|:------------|
| `ecotale.bank.use` | Basic player commands (balance, deposits list, loans list, info, history, gui, lang) |
| `ecotale.bank.deposit` | Create and withdraw deposits |
| `ecotale.bank.loan` | Take and repay loans |
| `ecotale.bank.admin` | Admin commands (freeze, unfreeze, reload, admin gui) |

---

## ⚙️ Configuration

Config file: `mods/com.crystalrealm_EcoTaleBanking/EcoTaleBanking.json`

### General

| Setting | Default | Description |
|:--------|:--------|:------------|
| language | `"ru"` | Default language (`ru` / `en`) |
| debugMode | `false` | Enable debug logging |
| autoSaveMinutes | `5` | Auto-save interval |
| secondsPerGameDay | `2880` | Real seconds per game day (48 min) |

### 💳 Deposit Plans

| Plan | Term | Rate | Min | Max |
|:-----|:-----|:-----|:----|:----|
| Short-Term | 7 days | 3% | 100 | 10,000 |
| Medium-Term | 14 days | 6% | 500 | 50,000 |
| Long-Term | 30 days | 12% | 1,000 | 100,000 |

### 💸 Loans

| Setting | Default | Description |
|:--------|:--------|:------------|
| BaseInterestRate | 10% | Base rate (modified by credit + inflation) |
| MinAmount / MaxAmount | 100 / 50,000 | Loan amount bounds |
| MaxActiveLoans | 2 | Concurrent loans limit |
| DefaultTermDays | 30 | Loan term in game days |
| CollateralRate | 20% | Locked as collateral on loan issue |
| OverduePenaltyRate | 2%/day | Daily penalty after due date |
| DefaultAfterDays | 14 | Days overdue before default |
| MinCreditScoreForLoan | 200 | Minimum credit score to borrow |
| MinLoanDaysForCreditBonus | 3 | Minimum days a loan must be held before repayment boosts credit score |

### 🏅 Credit Rating Tiers

| Rating | Score | Rate Modifier | Max Loan Multiplier |
|:-------|:------|:--------------|:-------------------|
| Excellent | 800–1000 | -3% | ×2.0 |
| Good | 600–799 | -1.5% | ×1.5 |
| Fair | 400–599 | ±0% | ×1.0 |
| Poor | 200–399 | +2.5% | ×0.5 |
| Bad | 0–199 | +5% | ×0.25 |

### 🏛️ Taxes

| Tax Type | Default | Description |
|:---------|:--------|:------------|
| Balance Tax | Off | Progressive tax on balance above threshold |
| Interest Tax | 13% | Flat tax on deposit interest payouts |
| Transaction Tax | Off | Tax on deposits and loan operations |

### 🛡️ Protection

| Setting | Default | Description |
|:--------|:--------|:------------|
| MaxOperationsPerHour | 30 | Rate limit per player |
| DepositCooldownSeconds | 60 | Cooldown between deposits |
| LoanCooldownSeconds | 300 | Cooldown between loans |
| MinAccountAgeDaysForLoan | 1 | Account must be at least this old |
| AuditLogEnabled | true | Record all transactions |
| MaxAuditLogEntries | 1000 | Max entries per player |

---

## 🏗️ Building from Source

**Prerequisites:** Java 17+, Gradle

```bash
git clone https://github.com/CrystalRealm/EcoTaleBanking.git
cd EcoTaleBanking
./gradlew build
```

Output: `build/libs/EcoTaleBanking-1.2.0.jar`

> The project uses compile-only stubs for Hytale Server API and Ecotale (located in `src/stubs/java/`). No external JAR downloads needed.

---

## 📁 Project Structure

```
EcoTaleBanking/
├── model/          — Data classes: BankAccount, Deposit, Loan, CreditScore, AuditLog, TransactionType
├── config/         — BankingConfig (7 sections) + ConfigManager with hot-reload & save
├── storage/        — BankStorage interface + JsonBankStorage (per-player JSON files)
├── service/        — 6 services: Credit, Inflation, Tax, Deposit, Loan, BankService (facade)
├── protection/     — AbuseGuard: rate limiting, cooldowns, suspicion logging
├── commands/       — BankCommandCollection: 14 subcommands
├── gui/            — PlayerBankGui (4 tabs) + AdminBankGui (4 tabs), native Hytale CustomUI
├── scheduler/      — BankScheduler: auto-save, daily processing, inflation updates
├── lang/           — LangManager: RU/EN with per-player switching
└── util/           — MiniMessageParser, MessageUtil, GameTime, PluginLogger
```

---

## 🤝 The EcoTale Vision

The EcoTale family of plugins is built to work as one interconnected ecosystem:

- **Ecotale** — the economic engine (wallets, currency, transfers)
- **EcoTaleIncome** — players *earn* money through gameplay (kills, mining, farming)
- **EcoTaleBanking** — players *manage* money (save in deposits, borrow via loans, build credit)
- **EcoTaleQuests** — players *spend* money on goals and earn quest rewards

Each plugin stands on its own, but together they create a rich, immersive economic game loop that keeps players engaged and invested.

---

**Developed by [CrystalRealm](https://hytale-server.pro-gamedev.ru)** for the Crystal Realm Hytale server — `hytale.pro-gamedev.ru`

---

## 📋 Changelog

### v1.2.0 — 2026-02-10

**Language commands**
- Replaced `/b lang <code>` with `/b langen` and `/b langru` subcommands (no arguments needed)
- `/b lang` now shows usage hint with new syntax
- Fixed `NoSuchFieldError: ArgTypes.STRING` crash that prevented plugin from loading

### v1.1.0 — 2026-02-10

**GUI — Migration to native Hytale CustomUI**
- Full migration from HyUI to native `InteractiveCustomUIPage` API
- Removed all HyUI dependencies — plugin is fully standalone
- All actions (deposits, withdrawals, loans, repayments) update GUI in-place via `sendUpdate()` — no Loading screens
- Slot-based event binding — events bound to slots during `build()`, data resolved at runtime in `handleDataEvent()`

**GUI — Visual improvements**
- Fixed title centering in both GUIs
- Widened loan repay buttons: 10%/25%/50% → 140px, Full → 180px
- Widened freeze/unfreeze button in accounts: 80 → 120px
- Widened settings subsection buttons: 125 → 140px
- Fixed "?" instead of "✔" in success banners — `stripForUI()` removes non-renderable characters
- Fixed truncated settings subsection labels — `stripDecorators()` + `shortLabel()`

**Commands**
- Main command shortened from `/bank` to `/b` (alias `/bank` preserved)
- `/b gui` — opens player bank GUI
- `/b admin gui` — opens admin bank GUI
- Updated help texts in both language files (RU/EN)

**Other**
- Updated manifest: `Authors` field for correct display in plugin list
- Version: 1.0.0 → 1.1.0

### v1.0.0 — 2026-02-08

- Initial release
- Bank deposits (3 plans), loans, credit rating system
- Inflation engine, taxes
- Player GUI (4 tabs) and Admin GUI (4 tabs)
- Full RU/EN localization
- Audit log, anti-abuse protection
- Ecotale API integration
