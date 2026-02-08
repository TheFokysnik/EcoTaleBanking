package com.crystalrealm.ecotalebanking.storage;

import com.crystalrealm.ecotalebanking.model.*;
import com.crystalrealm.ecotalebanking.util.PluginLogger;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * JSON file-based banking data storage.
 *
 * <p>On-disk structure:</p>
 * <pre>
 *   data/
 *     accounts/
 *       {uuid}.json        — BankAccount (deposits + loans)
 *     credit/
 *       {uuid}.json        — CreditScore
 *     audit/
 *       {uuid}.json        — List&lt;AuditLog&gt;
 * </pre>
 *
 * <p>Uses ConcurrentHashMap for thread-safe in-memory
 * caching. Periodic saveAll() flushes to disk.</p>
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public class JsonBankStorage implements BankStorage {

    private static final PluginLogger LOGGER = PluginLogger.forEnclosingClass();

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>)
                    (src, type, ctx) -> new JsonPrimitive(src.toEpochMilli()))
            .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>)
                    (json, type, ctx) -> Instant.ofEpochMilli(json.getAsLong()))
            .registerTypeAdapter(BigDecimal.class, (JsonSerializer<BigDecimal>)
                    (src, type, ctx) -> new JsonPrimitive(src.toPlainString()))
            .registerTypeAdapter(BigDecimal.class, (JsonDeserializer<BigDecimal>)
                    (json, type, ctx) -> new BigDecimal(json.getAsString()))
            .create();

    private final Path dataDirectory;
    private final Path accountsDir;
    private final Path creditDir;
    private final Path auditDir;

    private final Map<UUID, BankAccount> accountCache = new ConcurrentHashMap<>();
    private final Map<UUID, CreditScore> creditCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<AuditLog>> auditCache = new ConcurrentHashMap<>();

    private final int maxAuditEntries;

    /**
     * @param dataDirectory  root data directory of the plugin
     * @param maxAuditEntries max audit entries per player
     */
    public JsonBankStorage(@Nonnull Path dataDirectory, int maxAuditEntries) {
        this.dataDirectory = dataDirectory;
        this.accountsDir = dataDirectory.resolve("accounts");
        this.creditDir = dataDirectory.resolve("credit");
        this.auditDir = dataDirectory.resolve("audit");
        this.maxAuditEntries = maxAuditEntries;
    }

    // ═════════════════════════════════════════════════════════
    //  ACCOUNTS
    // ═════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public BankAccount loadOrCreateAccount(@Nonnull UUID playerUuid) {
        return accountCache.computeIfAbsent(playerUuid, uuid -> {
            BankAccount loaded = readJson(accountsDir, uuid, BankAccount.class);
            if (loaded != null) return loaded;
            BankAccount fresh = new BankAccount(uuid);
            LOGGER.debug("Created new bank account for {}", uuid);
            return fresh;
        });
    }

    @Override
    @Nullable
    public BankAccount loadAccount(@Nonnull UUID playerUuid) {
        BankAccount cached = accountCache.get(playerUuid);
        if (cached != null) return cached;
        BankAccount loaded = readJson(accountsDir, playerUuid, BankAccount.class);
        if (loaded != null) {
            accountCache.put(playerUuid, loaded);
        }
        return loaded;
    }

    @Override
    public void saveAccount(@Nonnull BankAccount account) {
        accountCache.put(account.getPlayerUuid(), account);
        writeJson(accountsDir, account.getPlayerUuid(), account);
    }

    // ═════════════════════════════════════════════════════════
    //  CREDIT SCORES
    // ═════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public CreditScore loadOrCreateCreditScore(@Nonnull UUID playerUuid) {
        return creditCache.computeIfAbsent(playerUuid, uuid -> {
            CreditScore loaded = readJson(creditDir, uuid, CreditScore.class);
            if (loaded != null) return loaded;
            return new CreditScore(uuid);
        });
    }

    @Override
    public void saveCreditScore(@Nonnull CreditScore score) {
        creditCache.put(score.getPlayerUuid(), score);
        writeJson(creditDir, score.getPlayerUuid(), score);
    }

    // ═════════════════════════════════════════════════════════
    //  AUDIT LOG
    // ═════════════════════════════════════════════════════════

    @Override
    public void addAuditLog(@Nonnull AuditLog entry) {
        List<AuditLog> logs = auditCache.computeIfAbsent(
                entry.getPlayerUuid(), k -> loadAuditList(k));
        logs.add(entry);

        // Trim to maxAuditEntries
        while (logs.size() > maxAuditEntries) {
            logs.remove(0);
        }
    }

    @Override
    @Nonnull
    public List<AuditLog> getAuditLogs(@Nonnull UUID playerUuid, int limit) {
        List<AuditLog> logs = auditCache.computeIfAbsent(
                playerUuid, this::loadAuditList);
        int start = Math.max(0, logs.size() - limit);
        return new ArrayList<>(logs.subList(start, logs.size()));
    }

    private List<AuditLog> loadAuditList(UUID uuid) {
        Type listType = new TypeToken<List<AuditLog>>() {}.getType();
        Path file = auditDir.resolve(uuid.toString() + ".json");
        if (!Files.exists(file)) return new ArrayList<>();

        try (Reader reader = new InputStreamReader(
                Files.newInputStream(file), StandardCharsets.UTF_8)) {
            List<AuditLog> list = GSON.fromJson(reader, listType);
            return list != null ? new ArrayList<>(list) : new ArrayList<>();
        } catch (Exception e) {
            LOGGER.error("Failed to load audit log for {}: {}", uuid, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ═════════════════════════════════════════════════════════
    //  COLLECTIONS (admin panel)
    // ═════════════════════════════════════════════════════════

    @Override
    @Nonnull
    public java.util.Collection<BankAccount> getAllAccounts() {
        return java.util.Collections.unmodifiableCollection(accountCache.values());
    }

    @Override
    @Nonnull
    public java.util.Collection<CreditScore> getAllCreditScores() {
        return java.util.Collections.unmodifiableCollection(creditCache.values());
    }

    // ═════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═════════════════════════════════════════════════════════

    @Override
    public void saveAll() {
        // Accounts
        for (Map.Entry<UUID, BankAccount> entry : accountCache.entrySet()) {
            writeJson(accountsDir, entry.getKey(), entry.getValue());
        }
        // Credit scores
        for (Map.Entry<UUID, CreditScore> entry : creditCache.entrySet()) {
            writeJson(creditDir, entry.getKey(), entry.getValue());
        }
        // Audit logs
        for (Map.Entry<UUID, List<AuditLog>> entry : auditCache.entrySet()) {
            writeJson(auditDir, entry.getKey(), entry.getValue());
        }
        LOGGER.debug("All bank data saved. Accounts: {}, Credits: {}, Audits: {}",
                accountCache.size(), creditCache.size(), auditCache.size());
    }

    @Override
    public void loadAll() {
        try {
            Files.createDirectories(accountsDir);
            Files.createDirectories(creditDir);
            Files.createDirectories(auditDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create storage directories: {}", e.getMessage());
        }

        // Pre-load accounts
        int loaded = 0;
        try {
            if (Files.isDirectory(accountsDir)) {
                for (Path file : Files.list(accountsDir)
                        .filter(f -> f.toString().endsWith(".json"))
                        .collect(Collectors.toList())) {
                    String name = file.getFileName().toString().replace(".json", "");
                    try {
                        UUID uuid = UUID.fromString(name);
                        BankAccount account = readJsonDirect(file, BankAccount.class);
                        if (account != null) {
                            accountCache.put(uuid, account);
                            loaded++;
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Not a UUID filename
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list account files: {}", e.getMessage());
        }

        LOGGER.info("Loaded {} bank accounts from disk.", loaded);
    }

    // ═════════════════════════════════════════════════════════
    //  JSON I/O
    // ═════════════════════════════════════════════════════════

    private <T> T readJson(Path dir, UUID uuid, Class<T> type) {
        Path file = dir.resolve(uuid.toString() + ".json");
        return readJsonDirect(file, type);
    }

    private <T> T readJsonDirect(Path file, Class<T> type) {
        if (!Files.exists(file)) return null;
        try (Reader reader = new InputStreamReader(
                Files.newInputStream(file), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (Exception e) {
            LOGGER.error("Failed to read {}: {}", file, e.getMessage());
            return null;
        }
    }

    private void writeJson(Path dir, UUID uuid, Object obj) {
        Path file = dir.resolve(uuid.toString() + ".json");
        writeJsonDirect(file, obj);
    }

    private void writeJsonDirect(Path file, Object obj) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = new OutputStreamWriter(
                    Files.newOutputStream(file), StandardCharsets.UTF_8)) {
                GSON.toJson(obj, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to write {}: {}", file, e.getMessage());
        }
    }
}
