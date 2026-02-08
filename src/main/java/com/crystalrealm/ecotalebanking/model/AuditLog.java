package com.crystalrealm.ecotalebanking.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Audit log entry for a transaction.
 * All operations are logged for history and anti-abuse purposes.
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public final class AuditLog {

    private final String id;
    private final UUID playerUuid;
    private final TransactionType type;
    private final BigDecimal amount;
    private final String description;
    private final Instant timestamp;

    public AuditLog(String id, UUID playerUuid, TransactionType type,
                    BigDecimal amount, String description) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.timestamp = Instant.now();
    }

    public AuditLog(String id, UUID playerUuid, TransactionType type,
                    BigDecimal amount, String description, Instant timestamp) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public UUID getPlayerUuid() { return playerUuid; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public Instant getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "AuditLog{" + timestamp + " " + type + " " + amount + " : " + description + "}";
    }
}
