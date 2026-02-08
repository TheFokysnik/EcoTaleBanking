package com.crystalrealm.ecotalebanking.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Player's credit score (0–1000).
 * Affects available interest rates, loan limits,
 * and collateral requirements.
 *
 * <p>Initial score: 500 (neutral).</p>
 *
 * @author CrystalRealm
 * @version 1.0.0
 */
public final class CreditScore {

    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 1000;
    public static final int INITIAL_SCORE = 500;

    private final UUID playerUuid;
    private int score;
    private int totalLoansCompleted;
    private int totalLoansDefaulted;
    private int totalDepositsCompleted;
    private int onTimePayments;
    private int latePayments;
    private Instant lastUpdated;

    public CreditScore(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.score = INITIAL_SCORE;
        this.totalLoansCompleted = 0;
        this.totalLoansDefaulted = 0;
        this.totalDepositsCompleted = 0;
        this.onTimePayments = 0;
        this.latePayments = 0;
        this.lastUpdated = Instant.now();
    }

    // ── Getters ─────────────────────────────────────────────

    public UUID getPlayerUuid() { return playerUuid; }
    public int getScore() { return score; }
    public int getTotalLoansCompleted() { return totalLoansCompleted; }
    public int getTotalLoansDefaulted() { return totalLoansDefaulted; }
    public int getTotalDepositsCompleted() { return totalDepositsCompleted; }
    public int getOnTimePayments() { return onTimePayments; }
    public int getLatePayments() { return latePayments; }
    public Instant getLastUpdated() { return lastUpdated; }

    // ── Modifiers ───────────────────────────────────────────

    /**
     * Adjusts the score by delta (can be negative).
     * Clamps the result to the range [0, 1000].
     */
    public void adjustScore(int delta) {
        this.score = Math.max(MIN_SCORE, Math.min(MAX_SCORE, score + delta));
        this.lastUpdated = Instant.now();
    }

    public void setScore(int score) {
        this.score = Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));
        this.lastUpdated = Instant.now();
    }

    public void incrementLoansCompleted() {
        totalLoansCompleted++;
        lastUpdated = Instant.now();
    }

    public void incrementLoansDefaulted() {
        totalLoansDefaulted++;
        lastUpdated = Instant.now();
    }

    public void incrementDepositsCompleted() {
        totalDepositsCompleted++;
        lastUpdated = Instant.now();
    }

    public void incrementOnTimePayments() {
        onTimePayments++;
        lastUpdated = Instant.now();
    }

    public void incrementLatePayments() {
        latePayments++;
        lastUpdated = Instant.now();
    }

    // ── Computed ─────────────────────────────────────────────

    /**
     * Text-based score rating.
     *
     * @return "Excellent" (800+), "Good" (600+), "Fair" (400+),
     *         "Poor" (200+), "Bad" (<200)
     */
    public String getRating() {
        if (score >= 800) return "Excellent";
        if (score >= 600) return "Good";
        if (score >= 400) return "Fair";
        if (score >= 200) return "Poor";
        return "Bad";
    }

    @Override
    public String toString() {
        return "CreditScore{uuid=" + playerUuid + ", score=" + score +
                ", rating=" + getRating() + "}";
    }
}
